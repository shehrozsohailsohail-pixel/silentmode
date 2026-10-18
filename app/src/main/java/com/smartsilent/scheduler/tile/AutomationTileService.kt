package com.smartsilent.scheduler.tile

import android.graphics.drawable.Icon
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.smartsilent.scheduler.R
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.util.KeyValueSettings
import com.smartsilent.scheduler.util.SettingsKeys
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework Section 30 - a Quick Settings tile toggling the same global
 * automation switch as the Settings screen (Section 47), never a separate
 * parallel on/off state of its own. TileService's practical floor is API 24;
 * this app's minSdk is 23 (v7.0 Section 58), so the one API level of gap
 * (23) simply never binds this service - no crash, the tile just isn't
 * offered there, which is the correct behavior for a feature the OS itself
 * didn't have yet.
 */
@AndroidEntryPoint
class AutomationTileService : TileService() {

    @Inject lateinit var settings: KeyValueSettings
    @Inject lateinit var reconciliationEngine: ReconciliationEngine

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onStartListening() {
        super.onStartListening()
        scope.launch { refreshTile() }
    }

    override fun onClick() {
        super.onClick()
        scope.launch {
            val current = settings.getBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, default = true)
            settings.setBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, !current)
            // Section 7: the tile never applies a ringer/DND change itself -
            // it only flips the switch and asks for a real reconciliation.
            reconciliationEngine.reconcile(ReconciliationTrigger.GlobalAutomationToggled)
            refreshTile()
        }
    }

    private suspend fun refreshTile() {
        val enabled = settings.getBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, default = true)
        qsTile?.apply {
            state = if (enabled) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            icon = Icon.createWithResource(this@AutomationTileService, R.drawable.ic_launcher_foreground)
            updateTile()
        }
    }
}

/** Lets ReconciliationEngine ask the system to re-invoke the tile's
 * onStartListening (if it's currently visible in the Quick Settings panel)
 * without the engine itself depending on TileService/qsTile directly. */
@Singleton
class TileRefresher @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: android.content.Context
) {
    fun requestRefresh() {
        TileService.requestListeningState(
            context,
            android.content.ComponentName(context, AutomationTileService::class.java)
        )
    }
}
