package com.smartsilent.scheduler.scheduling

import android.content.Intent
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.lifecycle.BaseReconciliationReceiver
import dagger.hilt.android.AndroidEntryPoint

/** Framework Sections 11-12 - fires when the single next-boundary alarm
 * goes off. The generation extra is carried through to the trigger for
 * logging only; ReconciliationEngine never trusts it for anything else -
 * see AlarmScheduler's class doc. */
@AndroidEntryPoint
class AlarmReceiver : BaseReconciliationReceiver() {
    override fun triggerFor(intent: Intent): ReconciliationTrigger? =
        if (intent.action == AndroidAlarmScheduler.ACTION_ALARM_FIRED) {
            ReconciliationTrigger.AlarmFired(intent.getLongExtra(AndroidAlarmScheduler.EXTRA_GENERATION, -1))
        } else {
            null
        }
}
