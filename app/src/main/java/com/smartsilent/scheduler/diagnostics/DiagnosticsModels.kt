package com.smartsilent.scheduler.diagnostics

import com.smartsilent.scheduler.android.capability.Capability
import com.smartsilent.scheduler.android.capability.CapabilityStatus

/** Framework Section 39 - machine-readable codes; UI copy is derived from
 * these at the presentation layer, never surfaced raw. */
enum class ErrorCode {
    DND_PERMISSION_MISSING,
    CALENDAR_PERMISSION_MISSING,
    EXACT_ALARM_UNAVAILABLE,
    RULE_INVALID,
    CALENDAR_PROVIDER_ERROR,
    OS_POLICY_REJECTED,
    STALE_ALARM,
    DATABASE_MIGRATION_ERROR,
    IMPORT_INVALID,
    UNSUPPORTED_POLICY
}

enum class HealthStatus { OK, DEGRADED, BROKEN }

data class DiagnosticsSnapshot(
    val healthStatus: HealthStatus,
    val capabilities: Map<Capability, CapabilityStatus>,
    val automationEnabled: Boolean,
    val lastAppliedPolicy: String?,
    val lastReconciliationEpochMillis: Long?,
    val generation: Long,
    val activeErrorCodes: List<ErrorCode>
)
