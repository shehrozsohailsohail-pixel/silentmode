package com.smartsilent.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.core.policy.TargetRingerMode
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.ZoneId
import javax.inject.Inject

@HiltViewModel
class RuleListViewModel @Inject constructor(private val ruleRepository: RuleRepository) : ViewModel() {
    val rules = ruleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun delete(id: Long) {
        viewModelScope.launch { ruleRepository.delete(id) }
    }
}

@Composable
fun RuleListScreen(navController: NavController, viewModel: RuleListViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val manualRules = rules.filter { it.type != "BEDTIME" }

    AppScreenScaffold(title = "Manual schedules", onBack = { navController.popBackStack() }) { innerModifier ->
        Scaffold(
            modifier = innerModifier,
            floatingActionButton = {
                ExtendedFloatingActionButton(
                    onClick = { navController.navigate(Routes.ruleEditor(0)) },
                    icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                    text = { Text("Add schedule") }
                )
            }
        ) { fabPadding ->
            if (manualRules.isEmpty()) {
                Column(modifier = Modifier.fillMaxWidth().padding(fabPadding).padding(24.dp)) {
                    Text(
                        "No schedules yet. Add one to silence the ringer automatically at set times.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().padding(fabPadding).padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(manualRules, key = { it.id }) { rule ->
                        Card {
                            ListItem(
                                headlineContent = { Text("${rule.startTime} \u2013 ${rule.endTime}") },
                                supportingContent = {
                                    val modeLabel = rule.mode.lowercase().replaceFirstChar { it.uppercase() }
                                    Text("$modeLabel \u00b7 ${if (rule.enabled) "Enabled" else "Disabled"}")
                                },
                                trailingContent = {
                                    Row {
                                        IconButton(onClick = { navController.navigate(Routes.ruleEditor(rule.id)) }) {
                                            Icon(Icons.Filled.Edit, contentDescription = "Edit")
                                        }
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
        }
    }
}

@HiltViewModel
class RuleEditorViewModel @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val reconciliationEngine: ReconciliationEngine,
    private val timeManager: TimeManager,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val ruleId: Long = savedStateHandle.get<String>("ruleId")?.toLongOrNull() ?: 0L
    var loaded = false
        private set

    suspend fun load(): RuleEntity? {
        if (ruleId == 0L || loaded) return null
        loaded = true
        return ruleRepository.getById(ruleId)
    }

    fun save(
        startTime: String,
        endTime: String,
        mode: TargetRingerMode,
        weekdayMask: Int,
        enabled: Boolean,
        existing: RuleEntity?,
        onDone: () -> Unit
    ) {
        viewModelScope.launch {
            val now = timeManager.nowInstant().toEpochMilli()
            val entity = (existing ?: RuleEntity(
                type = "WEEKLY", enabled = true, mode = mode.name,
                startTime = startTime, endTime = endTime, oneTimeAt = null,
                weekdayMask = weekdayMask, timezoneId = ZoneId.systemDefault().id,
                exceptionProfileId = null, createdAt = now, updatedAt = now, revision = 1
            )).copy(
                mode = mode.name, startTime = startTime, endTime = endTime,
                weekdayMask = weekdayMask, enabled = enabled, updatedAt = now,
                revision = (existing?.revision ?: 0) + 1
            )
            val id = ruleRepository.save(entity)
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
            onDone()
        }
    }

    fun delete(id: Long, onDone: () -> Unit) {
        viewModelScope.launch {
            ruleRepository.delete(id)
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
            onDone()
        }
    }
}

@Composable
fun RuleEditorScreen(navController: NavController, ruleId: Long, viewModel: RuleEditorViewModel = hiltViewModel()) {
    var existing by remember { mutableStateOf<RuleEntity?>(null) }
    var startTime by remember { mutableStateOf("22:00") }
    var endTime by remember { mutableStateOf("07:00") }
    var mode by remember { mutableStateOf(TargetRingerMode.SILENT) }
    var weekdayMask by remember { mutableStateOf(0) }
    var enabled by remember { mutableStateOf(true) }
    var editingField by remember { mutableStateOf<String?>(null) } // "start" | "end" | null

    LaunchedEffect(ruleId) {
        viewModel.load()?.let {
            existing = it
            startTime = it.startTime
            endTime = it.endTime
            mode = runCatching { TargetRingerMode.valueOf(it.mode) }.getOrDefault(TargetRingerMode.SILENT)
            weekdayMask = it.weekdayMask
            enabled = it.enabled
        }
    }

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

    AppScreenScaffold(
        title = if (ruleId == 0L) "Add schedule" else "Edit schedule",
        onBack = { navController.popBackStack() }
    ) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(onClick = { editingField = "start" }) { Text("Starts $startTime") }
                OutlinedButton(onClick = { editingField = "end" }) { Text("Ends $endTime") }
            }
            Text(
                "An end time earlier than the start time means it runs overnight, into the next day.",
                style = MaterialTheme.typography.bodySmall
            )

            Column {
                Text("Silence to", style = MaterialTheme.typography.titleSmall)
                TargetRingerMode.entries.forEach { candidate ->
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        RadioButton(selected = mode == candidate, onClick = { mode = candidate })
                        Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() })
                    }
                }
            }

            Column {
                Text("Repeats on", style = MaterialTheme.typography.titleSmall)
                Text("Leave all off to repeat every day", style = MaterialTheme.typography.bodySmall)
                Row {
                    listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa").forEachIndexed { bit, label ->
                        Column(horizontalAlignment = androidx.compose.ui.Alignment.CenterHorizontally) {
                            Checkbox(
                                checked = (weekdayMask shr bit) and 1 == 1,
                                onCheckedChange = { checked ->
                                    weekdayMask = if (checked) weekdayMask or (1 shl bit) else weekdayMask and (1 shl bit).inv()
                                }
                            )
                            Text(label, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("Enabled", style = MaterialTheme.typography.titleSmall)
                Switch(checked = enabled, onCheckedChange = { enabled = it })
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                androidx.compose.material3.Button(onClick = {
                    viewModel.save(startTime, endTime, mode, weekdayMask, enabled, existing) { navController.popBackStack() }
                }) { Text("Save") }

                if (ruleId != 0L) {
                    OutlinedButton(onClick = {
                        viewModel.delete(ruleId) { navController.popBackStack() }
                    }) { Text("Delete") }
                }
            }
        }
    }
}
