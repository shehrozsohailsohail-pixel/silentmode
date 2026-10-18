package com.smartsilent.scheduler.lifecycle

import android.content.Intent
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.logging.LogRetentionManager
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/** Framework Section 15 - rebuild scheduling after a reboot. Also the
 * trigger for Section 46 log retention (once per boot is infrequent enough
 * to be cheap and frequent enough that the log table never grows unbounded
 * between real device restarts). */
@AndroidEntryPoint
class BootReceiver : BaseReconciliationReceiver() {
    @Inject lateinit var logRetentionManager: LogRetentionManager

    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) ReconciliationTrigger.DeviceBoot else null

    override suspend fun afterReconcile() {
        logRetentionManager.pruneOlderThanDays()
    }
}

/** Framework Section 17 - a manual time change can move "now" across a rule
 * boundary without any alarm firing. */
@AndroidEntryPoint
class TimeChangedReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == Intent.ACTION_TIME_CHANGED) ReconciliationTrigger.TimeChanged else null
}

/** Framework Section 17/18 - a timezone change can shift every rule's local
 * boundary at once. */
@AndroidEntryPoint
class TimezoneChangedReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) ReconciliationTrigger.TimezoneChanged else null
}

/**
 * Framework Section 16 (App Update Recovery) - not built in Increment 1.
 * ACTION_MY_PACKAGE_REPLACED fires only for this app's own update and,
 * unlike most implicit broadcasts, remains manifest-declarable even on
 * API 26+, so no dynamic-registration workaround is needed here the way
 * UnlockReceiver needs one below.
 */
@AndroidEntryPoint
class PackageReplacedReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == Intent.ACTION_MY_PACKAGE_REPLACED) ReconciliationTrigger.PackageUpdated else null
}

/** Framework v7.0 Section 60 - re-reconcile the moment the user grants (or
 * revokes) exact-alarm scheduling from system Settings. */
@AndroidEntryPoint
class ExactAlarmPermissionReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED") {
            ReconciliationTrigger.PermissionStateChanged
        } else {
            null
        }
}

/**
 * Framework Section 15 (boot/unlock recovery) - the "unlock" half, which
 * Increment 1 omitted entirely. ACTION_USER_PRESENT is one of the implicit
 * broadcasts Android has disallowed as a *manifest*-declared receiver since
 * API 26 (to stop apps waking up in the background just to listen for
 * device-wide events) - so unlike every other receiver in this file, this
 * one is never declared in AndroidManifest.xml. SmartSilentApplication
 * registers one instance of it dynamically in onCreate() instead. That
 * means it only fires while this app's process happens to be alive - a
 * real, honest limitation (matching Section 13's exact-vs-best-effort
 * spirit), not a bug: BOOT_COMPLETED plus the next alarm/app-launch already
 * cover the same ground shortly after unlock in the common case.
 */
@AndroidEntryPoint
class UnlockReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == Intent.ACTION_USER_PRESENT) ReconciliationTrigger.DeviceUnlocked else null
}
