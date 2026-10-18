package com.smartsilent.scheduler.logging

import com.smartsilent.scheduler.data.local.ExecutionLogEntity
import com.smartsilent.scheduler.data.repository.ExecutionLogRepository
import com.smartsilent.scheduler.time.TimeManager
import java.time.temporal.ChronoUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Framework Section 46 - never log more than a rule/calendar ID and a
 * result code; event titles, keywords, and anything else that could be
 * personally identifying stay out of the log by construction (nothing in
 * ExecutionLogEntity even has a field for them - see the entity itself). */
object LogSanitizer {
    private const val MAX_TRIGGER_NAME_LENGTH = 64
    fun sanitizeTriggerName(raw: String): String = raw.take(MAX_TRIGGER_NAME_LENGTH)
}

/** Thin wrapper so callers depend on a logging-shaped API rather than the
 * repository/DAO directly - ReconciliationEngine used to build
 * ExecutionLogEntity inline; this is that logic, named and testable on its own. */
@Singleton
class ExecutionLogger @Inject constructor(
    private val repository: ExecutionLogRepository,
    private val timeManager: TimeManager
) {
    suspend fun log(triggerName: String, winnerRuleId: Long?, result: String, errorCode: String?) {
        repository.insert(
            ExecutionLogEntity(
                timestamp = timeManager.nowInstant().toEpochMilli(),
                trigger = LogSanitizer.sanitizeTriggerName(triggerName),
                winnerRuleId = winnerRuleId,
                result = result,
                errorCode = errorCode
            )
        )
    }
}

/** Framework Section 46 - bounded retention, called once per boot via
 * BootReceiver.afterReconcile(). Also callable manually (e.g. a future
 * "clear old logs now" diagnostics action) since it takes no state beyond
 * an optional day count. */
@Singleton
class LogRetentionManager @Inject constructor(
    private val repository: ExecutionLogRepository,
    private val timeManager: TimeManager
) {
    suspend fun pruneOlderThanDays(days: Long = 30) {
        val cutoff = timeManager.nowInstant().minus(days, ChronoUnit.DAYS)
        repository.pruneOlderThan(cutoff.toEpochMilli())
    }
}
