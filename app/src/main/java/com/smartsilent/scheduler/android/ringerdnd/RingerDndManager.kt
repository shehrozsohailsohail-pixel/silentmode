package com.smartsilent.scheduler.android.ringerdnd

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import com.smartsilent.scheduler.android.capability.AndroidStateReader
import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.capability.SystemState
import com.smartsilent.scheduler.core.policy.EffectivePolicy
import com.smartsilent.scheduler.core.policy.PolicyApplicationResult
import com.smartsilent.scheduler.core.policy.PolicyType
import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.data.local.ExceptionProfileEntity
import com.smartsilent.scheduler.data.repository.ExceptionProfileRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework Section 7 ("apply only necessary changes"), Section 40 ("no
 * false success"), and Section 22 (restore a captured pre-automation state
 * verbatim - not the same operation as applying a rule-derived policy).
 *
 * Section 6.1/6.2 exception profiles ARE wired in this pass: when a
 * winning rule carries an exceptionProfileId and the mode is SILENT, the
 * interruption filter used is PRIORITY (not NONE), with a
 * NotificationManager.Policy built from the profile's contact policy -
 * "silent except starred contacts" is a real, different OS state from
 * total silence, not just total silence with a label attached.
 */
object DndPolicyMapper {
    /** VIBRATE never engages DND - only SILENT blocks anything, and even
     * then only down to NONE when there is no exception profile to honor. */
    fun interruptionFilterFor(mode: TargetRingerMode, hasExceptionProfile: Boolean): Int = when (mode) {
        TargetRingerMode.NORMAL, TargetRingerMode.VIBRATE -> NotificationManager.INTERRUPTION_FILTER_ALL
        TargetRingerMode.SILENT ->
            if (hasExceptionProfile) NotificationManager.INTERRUPTION_FILTER_PRIORITY
            else NotificationManager.INTERRUPTION_FILTER_NONE
    }

    fun ringerModeFor(mode: TargetRingerMode): Int = when (mode) {
        TargetRingerMode.NORMAL -> AudioManager.RINGER_MODE_NORMAL
        TargetRingerMode.VIBRATE -> AudioManager.RINGER_MODE_VIBRATE
        TargetRingerMode.SILENT -> AudioManager.RINGER_MODE_SILENT
    }

    /** Framework Section 6.1/6.2 - maps a profile to a concrete OS Policy.
     * Returns null for "UNSUPPORTED" contact policy: the honest behavior for
     * a profile the OS/OEM combination can't reliably honor is to fall back
     * to plain total silence, never to silently pretend the exception
     * applied when it didn't. */
    fun policyFor(profile: ExceptionProfileEntity): NotificationManager.Policy? {
        val senders = when (profile.contactPolicy) {
            "ANYONE" -> NotificationManager.Policy.PRIORITY_SENDERS_ANY
            "CONTACTS" -> NotificationManager.Policy.PRIORITY_SENDERS_CONTACTS
            "STARRED" -> NotificationManager.Policy.PRIORITY_SENDERS_STARRED
            else -> return null // "UNSUPPORTED"
        }
        var categories = NotificationManager.Policy.PRIORITY_CATEGORY_CALLS or
            NotificationManager.Policy.PRIORITY_CATEGORY_MESSAGES or
            NotificationManager.Policy.PRIORITY_CATEGORY_REPEAT_CALLERS
        // Section 6.2 - letting alarms through during silence is a safety-
        // relevant default when the profile marks it supported.
        if (profile.systemAppExceptionMode == "SUPPORTED") {
            categories = categories or NotificationManager.Policy.PRIORITY_CATEGORY_ALARMS
        }
        return NotificationManager.Policy(categories, senders, senders)
    }
}

interface RingerDndManager {
    suspend fun apply(policy: EffectivePolicy): PolicyApplicationResult
    suspend fun applyRawState(state: SystemState): Boolean
}

@Singleton
class AndroidRingerDndManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val capabilityManager: CapabilityManager,
    private val stateReader: AndroidStateReader,
    private val exceptionProfileRepository: ExceptionProfileRepository
) : RingerDndManager {

    private val notificationManager by lazy {
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    }
    private val audioManager by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }

    override suspend fun apply(policy: EffectivePolicy): PolicyApplicationResult {
        if (policy.policyType == PolicyType.NO_AUTOMATION) {
            // Section 23: touch nothing - this IS success, by definition.
            return PolicyApplicationResult(succeeded = true, appliedPolicy = policy)
        }
        if (policy.policyType == PolicyType.UNAVAILABLE) {
            return PolicyApplicationResult(false, policy, "Policy resolved to UNAVAILABLE before reaching the applier")
        }
        if (!capabilityManager.canApplyRingerPolicy()) {
            return PolicyApplicationResult(false, policy, "DND/notification policy access is not granted")
        }
        val mode = policy.targetRingerMode
            ?: return PolicyApplicationResult(false, policy, "Policy had no target ringer mode")

        val exceptionProfile = policy.exceptionProfileId?.let { exceptionProfileRepository.getById(it) }
        val notificationPolicy = exceptionProfile?.let { DndPolicyMapper.policyFor(it) }
        val desiredFilter = DndPolicyMapper.interruptionFilterFor(mode, hasExceptionProfile = notificationPolicy != null)
        val desiredRingerMode = DndPolicyMapper.ringerModeFor(mode)

        // Section 9 idempotency: skip the OS call entirely if already correct -
        // including the notification Policy itself when one is in play, not
        // just the filter/ringer-mode pair, since two different exception
        // profiles can both resolve to the same PRIORITY filter.
        val current = stateReader.readCurrentState()
        val policyAlreadyCorrect = notificationPolicy == null || notificationManager.notificationPolicy == notificationPolicy
        if (current.ringerMode == desiredRingerMode && current.interruptionFilter == desiredFilter && policyAlreadyCorrect) {
            return PolicyApplicationResult(true, policy)
        }

        val applied = runCatching {
            notificationPolicy?.let { notificationManager.notificationPolicy = it }
            notificationManager.setInterruptionFilter(desiredFilter)
            audioManager.ringerMode = desiredRingerMode
            // Section 38/40: verify by reading state back rather than trusting
            // that the setter calls above didn't throw.
            val after = stateReader.readCurrentState()
            val afterPolicyOk = notificationPolicy == null || notificationManager.notificationPolicy == notificationPolicy
            after.ringerMode == desiredRingerMode && after.interruptionFilter == desiredFilter && afterPolicyOk
        }.getOrDefault(false)

        return if (applied) {
            PolicyApplicationResult(true, policy)
        } else {
            PolicyApplicationResult(false, policy, "OS rejected or did not confirm the requested ringer/DND state")
        }
    }

    /** Section 22 - restores an exact captured (ringerMode, interruptionFilter)
     * pair, bypassing policy mapping entirely. Used only when automation is
     * globally disabled and a pre-automation snapshot exists. */
    override suspend fun applyRawState(state: SystemState): Boolean {
        if (!capabilityManager.canApplyRingerPolicy()) return false
        return runCatching {
            notificationManager.setInterruptionFilter(state.interruptionFilter)
            audioManager.ringerMode = state.ringerMode
            true
        }.getOrDefault(false)
    }
}
