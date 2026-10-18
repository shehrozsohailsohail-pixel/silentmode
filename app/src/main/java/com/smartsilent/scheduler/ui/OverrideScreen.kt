package com.smartsilent.scheduler.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.core.overrides.TemporaryOverrideManager
import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.data.local.TemporaryOverrideEntity
import com.smartsilent.scheduler.data.repository.OverrideRepository
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import com.smartsilent.scheduler.ui.theme.AccentAmber
import com.smartsilent.scheduler.util.DateTimeUtils
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OverrideViewModel @Inject constructor(
    private val overrideManager: TemporaryOverrideManager,
    private val reconciliationEngine: ReconciliationEngine,
    overrideRepository: OverrideRepository
) : ViewModel() {

    val activeOverride = overrideRepository.observeActiveOverride()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null as TemporaryOverrideEntity?)

    fun start(mode: TargetRingerMode, minutes: Long) {
        viewModelScope.launch {
            overrideManager.createOverride(mode, minutes)
            // Section 66: the manager already guarantees only one override is
            // active; reconciling now is what actually applies it immediately
            // rather than waiting for the next scheduled boundary.
            reconciliationEngine.reconcile(ReconciliationTrigger.TemporaryOverrideChanged)
        }
    }

    fun cancel() {
        viewModelScope.launch {
            overrideManager.cancelActiveOverride()
            reconciliationEngine.reconcile(ReconciliationTrigger.TemporaryOverrideChanged)
        }
    }
}

@Composable
fun OverrideScreen(navController: NavController, viewModel: OverrideViewModel = hiltViewModel()) {
    val active by viewModel.activeOverride.collectAsStateWithLifecycle()

    AppScreenScaffold(title = "Quick override", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            if (active != null) {
                Card(colors = CardDefaults.cardColors(containerColor = AccentAmber.copy(alpha = 0.16f))) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("Override active", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "${active?.mode?.lowercase()?.replaceFirstChar { it.uppercase() }} until " +
                                    DateTimeUtils.formatTimeOnly(active?.expiresAt ?: 0L),
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Box(
                            modifier = Modifier.size(12.dp).clip(CircleShape)
                                .background(AccentAmber)
                        )
                    }
                }
                OutlinedButton(onClick = { viewModel.cancel() }) { Text("Cancel override") }
            } else {
                Text(
                    "No override active - your manual schedules and calendar rules are in control.",
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            Text("Silence for", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf(15L to "15 min", 30L to "30 min", 60L to "1 hour").forEach { (minutes, label) ->
                    Button(onClick = { viewModel.start(TargetRingerMode.SILENT, minutes) }) {
                        Icon(Icons.Filled.NotificationsOff, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text(" $label")
                    }
                }
            }

            Text("Or", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { viewModel.start(TargetRingerMode.VIBRATE, 60L) }) {
                    Icon(Icons.Filled.Vibration, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Vibrate for 1 hour")
                }
                OutlinedButton(onClick = { viewModel.start(TargetRingerMode.NORMAL, 60L) }) {
                    Icon(Icons.Filled.NotificationsActive, contentDescription = null, modifier = Modifier.size(18.dp))
                    Text(" Force normal for 1 hour")
                }
            }
        }
    }
}
