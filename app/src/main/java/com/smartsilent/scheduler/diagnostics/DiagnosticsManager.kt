package com.smartsilent.scheduler.diagnostics

import com.smartsilent.scheduler.android.capability.Capability
import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.capability.CapabilityStatus
import com.smartsilent.scheduler.data.repository.AutomationStateRepository
import com.smartsilent.scheduler.util.KeyValueSettings
import com.smartsilent.scheduler.util.SettingsKeys
import javax.inject.Inject
import javax.inject.Singleton

/** Maps a capability gap to the error code the UI should show for it -
 * framework Section 39. */
object ErrorClassifier {
    fun classify(capabilities: Map<Capability, CapabilityStatus>): List<ErrorCode> = buildList {
        if (capabilities[Capability.DND_ACCESS] == CapabilityStatus.DENIED) add(ErrorCode.DND_PERMISSION_MISSING)
        if (capabilities[Capability.EXACT_ALARM] == CapabilityStatus.DENIED) add(ErrorCode.EXACT_ALARM_UNAVAILABLE)
        if (capabilities[Capability.CALENDAR_PERMISSION] == CapabilityStatus.DENIED) add(ErrorCode.CALENDAR_PERMISSION_MISSING)
    }
}

@Singleton
class DiagnosticsManager @Inject constructor(
    private val capabilityManager: CapabilityManager,
    private val automationStateRepository: AutomationStateRepository,
    private val settings: KeyValueSettings
) {
    suspend fun currentSnapshot(): DiagnosticsSnapshot {
        val capabilities = Capability.entries.associateWith { capabilityManager.status(it) }
        val errors = ErrorClassifier.classify(capabilities)
        val automationEnabled = settings.getBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, default = true)
        val state = automationStateRepository.get()

        val health = when {
            !automationEnabled -> HealthStatus.OK // deliberately off is not "broken"
            capabilities[Capability.DND_ACCESS] != CapabilityStatus.GRANTED -> HealthStatus.BROKEN // can't apply anything at all
            errors.isNotEmpty() -> HealthStatus.DEGRADED
            else -> HealthStatus.OK
        }

        return DiagnosticsSnapshot(
            healthStatus = health,
            capabilities = capabilities,
            automationEnabled = automationEnabled,
            lastAppliedPolicy = state?.lastAppliedPolicy,
            lastReconciliationEpochMillis = state?.lastReconciliation,
            generation = state?.generation ?: 0,
            activeErrorCodes = errors
        )
    }
}
