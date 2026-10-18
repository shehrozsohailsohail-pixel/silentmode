package com.smartsilent.scheduler.lifecycle

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Framework v7.0 Section 61: every trigger receiver calls goAsync() and does
 * the real work on a coroutine, then always finishes the pending result -
 * onReceive() itself never blocks, since reconcile() does DB and Calendar
 * Provider I/O that would not reliably fit inside a receiver's normal
 * execution budget. Each concrete receiver in SystemReceivers.kt (and
 * scheduling/AlarmReceiver.kt) only has to say *which* trigger it maps to;
 * this class owns *how* that trigger safely reaches ReconciliationEngine.
 *
 * Hilt does field injection on each concrete receiver subclass separately
 * (that's how @AndroidEntryPoint works on a BroadcastReceiver), so
 * `reconciliationEngine` is declared here but injected once per subclass
 * instance by Hilt at dispatch time - it is not itself constructor-injected
 * on this abstract class.
 */
abstract class BaseReconciliationReceiver : BroadcastReceiver() {

    @Inject lateinit var reconciliationEngine: ReconciliationEngine

    private val receiverScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Return the trigger this receiver represents, or null to ignore the intent. */
    protected abstract fun triggerFor(intent: Intent): ReconciliationTrigger?

    /** Optional extra work after a successful reconcile(), run on the same
     * coroutine before the pending result finishes. Default no-op; only
     * BootReceiver currently overrides this (Section 46 log retention). */
    protected open suspend fun afterReconcile() {}

    override fun onReceive(context: Context, intent: Intent) {
        val trigger = triggerFor(intent) ?: return
        val pendingResult = goAsync()
        receiverScope.launch {
            try {
                // Section 12: even a stale-generation alarm reconciles fresh
                // against current DB/clock state - see AlarmScheduler's class
                // doc for why that needs no separate "is this stale?" branch.
                reconciliationEngine.reconcile(trigger)
                afterReconcile()
            } finally {
                pendingResult.finish()
            }
        }
    }
}
