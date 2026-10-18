package com.smartsilent.scheduler.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.smartsilent.scheduler.android.capability.CapabilityManager
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework Section 11 (single next-boundary alarm) and Section 12
 * (stale-alarm protection via a generation token). A single fixed
 * PendingIntent identity (same request code, same action) means every call
 * REPLACES the app's one outstanding alarm rather than stacking a second
 * one - extras (the generation number) aren't part of PendingIntent
 * identity, so FLAG_UPDATE_CURRENT correctly refreshes them on the existing
 * alarm. The exact-alarm capability check (v7.0 Section 60) is delegated to
 * CapabilityManager rather than duplicated here as a separate
 * ExactAlarmManager - one owner for that check, this class only consumes it.
 *
 * Why a stale-generation alarm needs no special "ignore it" branch:
 * ReconciliationEngine.reconcile() always recomputes the winner from current
 * DB state and clock time - it never trusts anything carried in the alarm's
 * payload - so an old alarm can only ever trigger one extra, harmless,
 * idempotent reconciliation pass, after which it reschedules against
 * current truth anyway.
 */
interface AlarmScheduler {
    fun scheduleNextReconciliation(atInstant: Instant, generation: Long)
    fun cancel()
}

@Singleton
class AndroidAlarmScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CapabilityManager
) : AlarmScheduler {

    companion object {
        const val ACTION_ALARM_FIRED = "com.smartsilent.scheduler.action.ALARM_FIRED"
        const val EXTRA_GENERATION = "generation"
        private const val REQUEST_CODE = 1001 // fixed on purpose - see class doc
    }

    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }

    private fun pendingIntent(generation: Long): PendingIntent {
        val intent = Intent(ACTION_ALARM_FIRED).apply {
            setPackage(context.packageName)
            putExtra(EXTRA_GENERATION, generation)
        }
        return PendingIntent.getBroadcast(
            context, REQUEST_CODE, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun scheduleNextReconciliation(atInstant: Instant, generation: Long) {
        val triggerAtMillis = atInstant.toEpochMilli()
        val pi = pendingIntent(generation)
        if (capabilityManager.canScheduleExactAlarms()) {
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        } else {
            // Section 13: best-effort, windowed delivery when exact timing
            // isn't available - never silently skip scheduling altogether.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, pi)
        }
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(generation = 0))
    }
}
