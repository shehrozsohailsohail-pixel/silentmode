package com.smartsilent.scheduler.widget

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import com.smartsilent.scheduler.R
import com.smartsilent.scheduler.data.repository.AutomationStateRepository
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
 * Framework Section 31. AppWidgetProvider extends BroadcastReceiver, so the
 * same execution-time-limit concern from v7.0 Section 61 applies here too -
 * onUpdate() calls goAsync() and does its DB read on a coroutine rather than
 * blocking. Widgets use classic RemoteViews (not Compose directly - that
 * needs the separate androidx.glance:glance-appwidget library, not pulled in
 * here to avoid one more version to pin in an environment that can't
 * compile-check any of them).
 */
@AndroidEntryPoint
class SmartSilentWidgetProvider : AppWidgetProvider() {

    @Inject lateinit var automationStateRepository: AutomationStateRepository
    @Inject lateinit var settings: KeyValueSettings

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val pendingResult = goAsync()
        scope.launch {
            try {
                val statusText = statusTextFor(automationEnabled = settings.getBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, true))
                for (id in appWidgetIds) {
                    val views = RemoteViews(context.packageName, R.layout.widget_smartsilent)
                    views.setTextViewText(R.id.widget_status, statusText)
                    appWidgetManager.updateAppWidget(id, views)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }

    private suspend fun statusTextFor(automationEnabled: Boolean): String {
        if (!automationEnabled) return "Off"
        val state = automationStateRepository.get()
        return state?.lastAppliedPolicy?.replace('_', ' ') ?: "Not run yet"
    }
}

/**
 * Called after a reconciliation pass (or a settings change) so the widget
 * doesn't have to wait for its next updatePeriodMillis tick to reflect
 * reality - kept separate from the provider itself so any caller (Settings
 * screen, ReconciliationEngine) can push a refresh without needing an
 * AppWidgetProvider instance.
 */
@Singleton
class WidgetUpdater @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val context: Context
) {
    fun requestRefresh() {
        val manager = AppWidgetManager.getInstance(context)
        val component = ComponentName(context, SmartSilentWidgetProvider::class.java)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isNotEmpty()) {
            context.sendBroadcast(
                android.content.Intent(context, SmartSilentWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                }
            )
        }
    }
}
