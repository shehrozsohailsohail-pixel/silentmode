package com.smartsilent.scheduler.core.reconciliation

import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.capability.SystemStateHasher
import com.smartsilent.scheduler.android.capability.AndroidStateReader
import com.smartsilent.scheduler.android.ringerdnd.RingerDndManager
import com.smartsilent.scheduler.core.ownership.OwnershipManager
import com.smartsilent.scheduler.core.ownership.RestoreManager
import com.smartsilent.scheduler.core.policy.EffectivePolicy
import com.smartsilent.scheduler.core.policy.PolicyApplicationResult
import com.smartsilent.scheduler.core.policy.PolicyResolver
import com.smartsilent.scheduler.core.policy.PolicyType
import com.smartsilent.scheduler.core.rules.RuleEngine
import com.smartsilent.scheduler.core.rules.RuleType
import com.smartsilent.scheduler.calendar.CalendarOccurrence
import com.smartsilent.scheduler.data.repository.AutomationStateRepository
import com.smartsilent.scheduler.data.repository.CalendarRuleRepository
import com.smartsilent.scheduler.data.repository.OverrideRepository
import com.smartsilent.scheduler.data.repository.RuleRepository
import com.smartsilent.scheduler.logging.ExecutionLogger
import com.smartsilent.scheduler.scheduling.AlarmScheduler
import com.smartsilent.scheduler.scheduling.NextBoundaryCalculator
import com.smartsilent.scheduler.tile.TileRefresher
import com.smartsilent.scheduler.time.TimeManager
import com.smartsilent.scheduler.util.SettingsKeys
import com.smartsilent.scheduler.widget.WidgetUpdater
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves calendar_rules into concrete matches for a time window - the real
 * implementation is CalendarManager (calendar/); this interface lives here,
 * not there, so this package never has to depend downward on the calendar
 * package, only the other way around.
 */
interface CalendarRuleResolver {
    suspend fun resolveMatches(windowStart: LocalDate, windowEnd: LocalDate): List<CalendarOccurrence>
}

/** One reconciliation pass's full result - framework Section 7's output. */
data class ReconciliationResult(
    val applicationResult: PolicyApplicationResult,
    val nextBoundaryEpochMillis: Long?,
    val automationEnabled: Boolean
)

/**
 * Framework Section 7 - the single authoritative decision path. Every
 * trigger in Section 8 ends up calling reconcile(); nothing else in the app
 * is allowed to change ringer/DND state on its own. This increment adds
 * Section 47 (global automation switch) and Sections 20/22 (ownership
 * capture/restore) on top of Increment 1's core loop.
 */
@Singleton
class ReconciliationEngine @Inject constructor(
    private val timeManager: TimeManager,
    private val ruleEngine: RuleEngine,
    private val ruleRepository: RuleRepository,
    private val overrideRepository: OverrideRepository,
    private val calendarRuleRepository: CalendarRuleRepository,
    private val calendarRuleResolver: CalendarRuleResolver,
    private val automationStateRepository: AutomationStateRepository,
    private val executionLogger: ExecutionLogger,
    private val capabilityManager: CapabilityManager,
    private val stateReader: AndroidStateReader,
    private val ringerDndManager: RingerDndManager,
    private val alarmScheduler: AlarmScheduler,
    private val ownershipManager: OwnershipManager,
    private val restoreManager: RestoreManager,
    private val widgetUpdater: WidgetUpdater,
    private val tileRefresher: TileRefresher,
    private val settings: com.smartsilent.scheduler.util.KeyValueSettings
) {

    suspend fun reconcile(trigger: ReconciliationTrigger): ReconciliationResult {
        // Framework Section 47 - the global switch is checked before anything
        // else. Disabled means: restore whatever was there before automation
        // ever ran, cancel the standing alarm, and stop - no rule evaluation
        // happens at all while automation is off.
        val automationEnabled = settings.getBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, default = true)
        if (!automationEnabled) {
            restoreManager.restoreAndClear()
            alarmScheduler.cancel()
            logResult(trigger, winnerRuleId = null, result = "NO_ACTION", errorCode = null)
            refreshSurfaces()
            return ReconciliationResult(
                PolicyApplicationResult(true, noAutomationPolicy("Global automation is off")),
                nextBoundaryEpochMillis = null,
                automationEnabled = false
            )
        }

        val now = timeManager.nowInstant()
        val zone = timeManager.zoneId()
        val today = now.atZone(zone).toLocalDate()
        // Section 51 Scenario D / Section 18: a day either side so an
        // overnight rule that started yesterday, or one whose local date
        // shifts under a DST transition, is never missed at the window edge.
        val windowStart = today.minusDays(1)
        val windowEnd = today.plusDays(1)

        val enabledRules = ruleRepository.getEnabled()
        val manualOccurrences = enabledRules
            .filter { it.type != "BEDTIME" }
            .flatMap { ruleEngine.expandOccurrences(it, RuleType.MANUAL_SCHEDULE, windowStart, windowEnd) }
        val bedtimeOccurrences = enabledRules
            .filter { it.type == "BEDTIME" }
            .flatMap { ruleEngine.expandOccurrences(it, RuleType.BEDTIME, windowStart, windowEnd) }

        val activeOverride = overrideRepository.getActiveOverride()
        val calendarMatches = if (calendarRuleRepository.getEnabled().isEmpty()) {
            emptyList()
        } else {
            calendarRuleResolver.resolveMatches(windowStart, windowEnd)
        }

        val winner = ruleEngine.resolveWinner(now, activeOverride, calendarMatches, manualOccurrences, bedtimeOccurrences)
        val effectivePolicy = PolicyResolver.resolve(winner, capabilityManager.canApplyRingerPolicy())

        if (effectivePolicy.policyType != PolicyType.NO_AUTOMATION && effectivePolicy.policyType != PolicyType.UNAVAILABLE) {
            // Section 20 - capture the real pre-automation state the first
            // time (and only the first time) this app is about to apply
            // anything.
            ownershipManager.captureBeforeFirstApply()
        }

        val applicationResult = ringerDndManager.apply(effectivePolicy)

        val allOccurrences = manualOccurrences + bedtimeOccurrences
        val nextBoundary = NextBoundaryCalculator.next(now, allOccurrences)

        val automationState = automationStateRepository.get()
        val newGeneration = (automationState?.generation ?: 0) + 1
        val stateHash = SystemStateHasher.hash(stateReader.readCurrentState())
        automationStateRepository.upsertAfterReconciliation(
            lastAppliedPolicy = effectivePolicy.policyType.name,
            lastAppliedStateHash = stateHash,
            newGeneration = newGeneration,
            reconciledAtEpochMillis = now.toEpochMilli()
        )

        logResult(
            trigger,
            winnerRuleId = effectivePolicy.winningRuleId,
            result = if (applicationResult.succeeded) "APPLIED" else "FAILED",
            errorCode = if (!applicationResult.succeeded) "OS_POLICY_REJECTED" else null
        )

        if (nextBoundary != null) {
            alarmScheduler.scheduleNextReconciliation(nextBoundary, newGeneration)
        } else {
            alarmScheduler.cancel()
        }
        refreshSurfaces()

        return ReconciliationResult(applicationResult, nextBoundary?.toEpochMilli(), automationEnabled = true)
    }

    /** Section 30/31 - the tile and widget both display state derived from
     * the same automation_state/settings this method just wrote, never a
     * parallel state of their own; this just prompts them to re-read it
     * promptly instead of waiting for their own refresh schedules. */
    private fun refreshSurfaces() {
        widgetUpdater.requestRefresh()
        tileRefresher.requestRefresh()
    }

    private suspend fun logResult(trigger: ReconciliationTrigger, winnerRuleId: Long?, result: String, errorCode: String?) {
        executionLogger.log(
            triggerName = trigger::class.simpleName ?: "UNKNOWN",
            winnerRuleId = winnerRuleId,
            result = result,
            errorCode = errorCode
        )
    }

    private fun noAutomationPolicy(reason: String): EffectivePolicy = EffectivePolicy(
        policyType = PolicyType.NO_AUTOMATION,
        targetRingerMode = null,
        dndStrategy = null,
        exceptionProfileId = null,
        winningRuleId = null,
        winningRuleType = RuleType.NONE,
        effectiveUntil = null,
        reason = reason
    )
}
