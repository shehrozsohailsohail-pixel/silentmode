package com.smartsilent.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.diagnostics.DiagnosticsManager
import com.smartsilent.scheduler.diagnostics.DiagnosticsSnapshot
import com.smartsilent.scheduler.diagnostics.HealthStatus
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class DiagnosticsViewModel @Inject constructor(
    private val diagnosticsManager: DiagnosticsManager,
    private val reconciliationEngine: ReconciliationEngine
) : ViewModel() {

    private val _snapshot = MutableStateFlow<DiagnosticsSnapshot?>(null)
    val snapshot = _snapshot.asStateFlow()

    fun refresh() {
        viewModelScope.launch { _snapshot.value = diagnosticsManager.currentSnapshot() }
    }

    fun reconcileNow() {
        viewModelScope.launch {
            reconciliationEngine.reconcile(ReconciliationTrigger.ManualUserRequest)
            refresh()
        }
    }
}

@Composable
fun DiagnosticsScreen(navController: NavController, viewModel: DiagnosticsViewModel = hiltViewModel()) {
    val snapshot by viewModel.snapshot.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { viewModel.refresh() }

    AppScreenScaffold(title = "Diagnostics", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            snapshot?.let { s ->
                val (icon, tint, label) = when (s.healthStatus) {
                    HealthStatus.OK -> Triple(Icons.Filled.CheckCircle, Color(0xFF2E7D32), "Everything looks good")
                    HealthStatus.DEGRADED -> Triple(Icons.Filled.Warning, Color(0xFFB26A00), "Working, with limits")
                    HealthStatus.BROKEN -> Triple(Icons.Filled.Warning, MaterialTheme.colorScheme.error, "Can't apply changes right now")
                }
                Card(colors = CardDefaults.cardColors(containerColor = tint.copy(alpha = 0.12f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(icon, contentDescription = null, tint = tint)
                        Column {
                            Text(label, style = MaterialTheme.typography.titleMedium)
                            Text(
                                "Automation is ${if (s.automationEnabled) "on" else "off"} \u00b7 last applied: ${s.lastAppliedPolicy ?: "never"}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }

                Text("Permissions", style = MaterialTheme.typography.titleSmall)
                s.capabilities.forEach { (capability, status) ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(capability.name.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                        Text(status.name.lowercase().replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall)
                    }
                }

                if (s.activeErrorCodes.isNotEmpty()) {
                    Text("Needs attention", style = MaterialTheme.typography.titleSmall)
                    s.activeErrorCodes.forEach { Text("\u2022 ${it.name.lowercase().replace('_', ' ')}") }
                }

                Text("Reconciliation generation: ${s.generation}", style = MaterialTheme.typography.bodySmall)
            } ?: Text("Loading...")

            Button(onClick = { viewModel.reconcileNow() }) {
                Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Reconcile now")
            }
        }
    }
}
