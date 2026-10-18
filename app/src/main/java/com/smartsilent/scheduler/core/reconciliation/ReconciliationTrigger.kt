package com.smartsilent.scheduler.core.reconciliation

/**
 * Framework Section 8 - every trigger converges into the same reconcile()
 * path (Section 7: "No alarm, receiver, widget, or tile may independently
 * implement its own business-mode logic").
 */
sealed class ReconciliationTrigger {
    data object AppLaunch : ReconciliationTrigger()
    data object DeviceBoot : ReconciliationTrigger()
    data object DeviceUnlocked : ReconciliationTrigger()
    data object TimeChanged : ReconciliationTrigger()
    data object TimezoneChanged : ReconciliationTrigger()
    data object PackageUpdated : ReconciliationTrigger()
    data class AlarmFired(val generation: Long) : ReconciliationTrigger()
    data object TemporaryOverrideChanged : ReconciliationTrigger()
    data class RuleMutated(val ruleId: Long) : ReconciliationTrigger()
    data object PermissionStateChanged : ReconciliationTrigger()
    data object GlobalAutomationToggled : ReconciliationTrigger()
    data object ManualUserRequest : ReconciliationTrigger()
}
