package com.smartsilent.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.HealthAndSafety
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import com.smartsilent.scheduler.util.KeyValueSettings
import com.smartsilent.scheduler.util.SettingsKeys
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settings: KeyValueSettings,
    private val reconciliationEngine: ReconciliationEngine
) : ViewModel() {

    val automationEnabled = settings.observeBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, default = true)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun setAutomationEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settings.setBoolean(SettingsKeys.GLOBAL_AUTOMATION_ENABLED, enabled)
            // Section 7: the switch never applies a ringer/DND change itself -
            // it flips the setting and asks for a real reconciliation, which
            // is what actually restores or resumes automation (Sections 22/47).
            reconciliationEngine.reconcile(ReconciliationTrigger.GlobalAutomationToggled)
        }
    }
}

private data class NavEntry(val title: String, val subtitle: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String)

@Composable
fun SettingsScreen(navController: NavController, viewModel: SettingsViewModel = hiltViewModel()) {
    val automationEnabled by viewModel.automationEnabled.collectAsStateWithLifecycle()

    val entries = listOf(
        NavEntry("Manual schedules", "Recurring or one-time silent windows", Icons.Filled.Schedule, Routes.RULES),
        NavEntry("Bedtime", "A simple nightly silent window", Icons.Filled.NightsStay, Routes.BEDTIME),
        NavEntry("Quick override", "Silence or restore ringer right now", Icons.Filled.Timer, Routes.OVERRIDE),
        NavEntry("Calendar rules", "Silence during matching calendar events", Icons.Filled.CalendarMonth, Routes.CALENDAR_RULES),
        NavEntry("Diagnostics", "Automation health and recent activity", Icons.Filled.HealthAndSafety, Routes.DIAGNOSTICS),
        NavEntry("Admin lock", "Password-protect this app's screens", Icons.Filled.AdminPanelSettings, Routes.ADMIN),
        NavEntry("Backup & restore", "Export or import your schedules", Icons.Filled.CloudSync, Routes.BACKUP)
    )

    AppScreenScaffold(title = "SmartSilent Scheduler", onBack = null) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("Automation", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (automationEnabled) "Rules and overrides are in control" else "Everything is back to how you left it",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Switch(checked = automationEnabled, onCheckedChange = { viewModel.setAutomationEnabled(it) })
                }
            }

            entries.forEach { entry ->
                Card(onClick = { navController.navigate(entry.route) }) {
                    ListItem(
                        headlineContent = { Text(entry.title) },
                        supportingContent = { Text(entry.subtitle) },
                        leadingContent = { Icon(entry.icon, contentDescription = null, modifier = Modifier.size(28.dp)) }
                    )
                }
            }
        }
    }
}
