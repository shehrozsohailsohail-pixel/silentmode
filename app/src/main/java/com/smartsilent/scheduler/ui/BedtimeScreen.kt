package com.smartsilent.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.data.local.RuleEntity
import com.smartsilent.scheduler.data.repository.RuleRepository
import com.smartsilent.scheduler.time.TimeManager
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import com.smartsilent.scheduler.ui.components.AppTimePickerDialog
import com.smartsilent.scheduler.ui.components.formatHourMinute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class BedtimeViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val reconciliationEngine: ReconciliationEngine,
    private val timeManager: TimeManager
) : ViewModel() {

    val bedtimeRules = ruleRepository.observeAll()
        .map { list -> list.filter { it.type == "BEDTIME" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(startTime: String, endTime: String) {
        viewModelScope.launch {
            val now = timeManager.nowInstant().toEpochMilli()
            val entity = RuleEntity(
                type = "BEDTIME", enabled = true, mode = "SILENT",
                startTime = startTime, endTime = endTime, oneTimeAt = null,
                weekdayMask = 0, timezoneId = ZoneId.systemDefault().id,
                exceptionProfileId = null, createdAt = now, updatedAt = now, revision = 1
            )
            val id = ruleRepository.save(entity)
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
        }
    }

    fun toggle(rule: RuleEntity) {
        viewModelScope.launch {
            ruleRepository.save(rule.copy(enabled = !rule.enabled, revision = rule.revision + 1))
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(rule.id))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            ruleRepository.delete(id)
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
        }
    }
}

@Composable
fun BedtimeScreen(navController: NavController, viewModel: BedtimeViewModel = hiltViewModel()) {
    val rules by viewModel.bedtimeRules.collectAsStateWithLifecycle()
    var startTime by remember { mutableStateOf("22:30") }
    var endTime by remember { mutableStateOf("06:30") }
    var editingField by remember { mutableStateOf<String?>(null) }

    editingField?.let { field ->
        val (h, m) = (if (field == "start") startTime else endTime).split(":").map { it.toInt() }
        AppTimePickerDialog(
            initialHour = h, initialMinute = m,
            onDismiss = { editingField = null },
            onConfirm = { hour, minute ->
                val formatted = formatHourMinute(hour, minute)
                if (field == "start") startTime = formatted else endTime = formatted
                editingField = null
            }
        )
    }

    AppScreenScaffold(title = "Bedtime", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (rules.isEmpty()) {
                Text(
                    "No bedtime window yet. Add one below to silence the ringer every night automatically.",
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(rules, key = { it.id }) { rule ->
                        Card {
                            ListItem(
                                leadingContent = { Icon(Icons.Filled.NightsStay, contentDescription = null) },
                                headlineContent = { Text("${rule.startTime} \u2013 ${rule.endTime}") },
                                trailingContent = {
                                    Row {
                                        Switch(checked = rule.enabled, onCheckedChange = { viewModel.toggle(rule) })
                                        IconButton(onClick = { viewModel.delete(rule.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Text("Add a bedtime window", style = MaterialTheme.typography.titleSmall)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editingField = "start" }) { Text("Starts $startTime") }
                OutlinedButton(onClick = { editingField = "end" }) { Text("Ends $endTime") }
            }
            Button(onClick = { viewModel.add(startTime, endTime) }) { Text("Add") }
        }
    }
}
