package com.smartsilent.scheduler.core.ownership

import com.smartsilent.scheduler.android.capability.AndroidStateReader
import com.smartsilent.scheduler.android.capability.SystemState
import com.smartsilent.scheduler.android.ringerdnd.RingerDndManager
import com.smartsilent.scheduler.data.repository.AutomationStateRepository
import javax.inject.Inject
import javax.inject.Singleton

/** Framework Section 20 - the ringer/DND state the user had *before*
 * automation ever touched the device, captured once so Section 22 can put
 * it back exactly when automation is turned off. */
data class AutomationOwnership(val ringerMode: Int, val interruptionFilter: Int)

/**
 * Framework Section 20 - captures the real pre-automation state exactly
 * once (the first time this app is about to apply a non-NO_AUTOMATION
 * policy), never overwriting an existing capture. Not built in Increment 1;
 * its absence is exactly the kind of gap that only shows up once you ask
 * "what does turning automation back off actually restore?" - see
 * RestoreManager below.
 */
@Singleton
class OwnershipManager @Inject constructor(
    private val stateReader: AndroidStateReader,
    private val automationStateRepository: AutomationStateRepository
) {
    suspend fun captureBeforeFirstApply() {
        val state = automationStateRepository.get()
        val alreadyCaptured = state?.preAutomationDndMode != null || state?.preAutomationNotificationPolicy != null
        if (alreadyCaptured) return
        val current = stateReader.readCurrentState()
        automationStateRepository.recordPreAutomationSnapshot(
            ringerMode = current.ringerMode,
            interruptionFilter = current.interruptionFilter
        )
    }
}

/**
 * Framework Section 22 (Safe Restore Logic) - when the user turns global
 * automation off, put the device back to whatever it was before automation
 * ever ran, then clear the snapshot so a future re-enable captures fresh.
 * If nothing was ever captured (automation was enabled but never actually
 * applied anything), there is nothing to restore - that's success, not a
 * missing-data error.
 */
@Singleton
class RestoreManager @Inject constructor(
    private val automationStateRepository: AutomationStateRepository,
    private val ringerDndManager: RingerDndManager
) {
    suspend fun restoreAndClear(): Boolean {
        val state = automationStateRepository.get() ?: return true
        val ringerMode = state.preAutomationDndMode?.toIntOrNull()
        val filter = state.preAutomationNotificationPolicy?.toIntOrNull()
        if (ringerMode == null || filter == null) {
            return true // nothing was ever captured - correctly a no-op, not a failure
        }
        val restored = ringerDndManager.applyRawState(SystemState(ringerMode, filter))
        if (restored) {
            automationStateRepository.clearPreAutomationSnapshot()
        }
        return restored
    }
}
