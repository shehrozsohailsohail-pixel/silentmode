package com.smartsilent.scheduler.android.capability

import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Build
import androidx.core.content.ContextCompat
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import javax.inject.Inject
import javax.inject.Singleton

enum class Capability { DND_ACCESS, EXACT_ALARM, CALENDAR_PERMISSION, NOTIFICATION_PERMISSION }
enum class CapabilityStatus { GRANTED, DENIED, NOT_APPLICABLE }

/** Raw OS ringer/DND state, independent of any rule - used both for the
 * idempotency check before applying a policy, and for Section 20/22's
 * pre-automation snapshot/restore. */
data class SystemState(val ringerMode: Int, val interruptionFilter: Int)

/** A stable content hash of a SystemState, used to detect "did anything
 * actually change since last reconciliation" - replaces the Increment-1
 * placeholder that used Kotlin's default (JVM-run-local) hashCode(). */
object SystemStateHasher {
    fun hash(state: SystemState): String {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest("${state.ringerMode}:${state.interruptionFilter}".toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { "%02x".format(it) }
    }
}

/** Framework Section 27 - the UI must show what is actually available right
 * now, never assume a capability exists just because the app has a feature
 * for it. */
interface CapabilityManager {
    fun status(capability: Capability): CapabilityStatus
    fun canApplyRingerPolicy(): Boolean
    fun canScheduleExactAlarms(): Boolean
}

/** Reads the OS's actual current ringer/DND state - used by CapabilityManager
 * consumers that need the raw state rather than a yes/no capability check. */
interface AndroidStateReader {
    fun readCurrentState(): SystemState
}

@Singleton
class AndroidCapabilityManager @Inject constructor(
    @ApplicationContext private val context: Context
) : CapabilityManager, AndroidStateReader {

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val alarmManager by lazy {
        context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    }
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override fun status(capability: Capability): CapabilityStatus = when (capability) {
        Capability.DND_ACCESS ->
            if (notificationManager.isNotificationPolicyAccessGranted) CapabilityStatus.GRANTED else CapabilityStatus.DENIED

        Capability.EXACT_ALARM ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
                CapabilityStatus.NOT_APPLICABLE // no separate runtime grant existed before API 31
            } else if (alarmManager.canScheduleExactAlarms()) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }

        Capability.CALENDAR_PERMISSION ->
            if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.READ_CALENDAR) ==
                PackageManager.PERMISSION_GRANTED
            ) CapabilityStatus.GRANTED else CapabilityStatus.DENIED

        Capability.NOTIFICATION_PERMISSION ->
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
                CapabilityStatus.NOT_APPLICABLE
            } else if (ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            ) {
                CapabilityStatus.GRANTED
            } else {
                CapabilityStatus.DENIED
            }
    }

    override fun canApplyRingerPolicy(): Boolean = status(Capability.DND_ACCESS) == CapabilityStatus.GRANTED

    override fun canScheduleExactAlarms(): Boolean =
        status(Capability.EXACT_ALARM) != CapabilityStatus.DENIED

    override fun readCurrentState(): SystemState =
        SystemState(
            ringerMode = audioManager.ringerMode,
            interruptionFilter = notificationManager.currentInterruptionFilter
        )
}
