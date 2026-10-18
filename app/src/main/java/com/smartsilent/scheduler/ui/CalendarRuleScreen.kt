package com.smartsilent.scheduler.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import com.smartsilent.scheduler.android.capability.Capability
import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.capability.CapabilityStatus
import com.smartsilent.scheduler.calendar.CalendarProviderReader
import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.data.local.CalendarRuleEntity
import com.smartsilent.scheduler.data.repository.CalendarRuleRepository
import com.smartsilent.scheduler.time.TimeManager
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/** (calendarId, accountName, displayName) - matches CalendarProviderReader.listAvailableCalendars(). */
private typealias CalendarChoice = Triple<Long, String, String>

@HiltViewModel
class CalendarRuleViewModel @Inject constructor(
    private val calendarRuleRepository: CalendarRuleRepository,
    private val calendarProviderReader: CalendarProviderReader,
    private val capabilityManager: CapabilityManager,
    private val reconciliationEngine: ReconciliationEngine,
    private val timeManager: TimeManager
) : ViewModel() {

    val rules = calendarRuleRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _availableCalendars = MutableStateFlow<List<CalendarChoice>>(emptyList())
    val availableCalendars = _availableCalendars.asStateFlow()

    fun hasCalendarPermission(): Boolean =
        capabilityManager.status(Capability.CALENDAR_PERMISSION) == CapabilityStatus.GRANTED

    fun loadCalendars() {
        if (!hasCalendarPermission()) return
        viewModelScope.launch { _availableCalendars.value = calendarProviderReader.listAvailableCalendars() }
    }

    fun addRule(calendar: CalendarChoice, keywords: String, matchType: String, mode: TargetRingerMode) {
        if (keywords.isBlank()) return
        viewModelScope.launch {
            val now = timeManager.nowInstant().toEpochMilli()
            val id = calendarRuleRepository.save(
                CalendarRuleEntity(
                    enabled = true, calendarId = calendar.first,
                    calendarAccountName = calendar.second, calendarDisplayName = calendar.third,
                    matchType = matchType, keywords = keywords, mode = mode.name,
                    exceptionProfileId = null, createdAt = now, updatedAt = now, revision = 1
                )
            )
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            calendarRuleRepository.delete(id)
            reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(id))
        }
    }
}

@Composable
fun CalendarRuleScreen(navController: NavController, viewModel: CalendarRuleViewModel = hiltViewModel()) {
    val rules by viewModel.rules.collectAsStateWithLifecycle()
    val calendars by viewModel.availableCalendars.collectAsStateWithLifecycle()
    var hasPermission by remember { mutableStateOf(viewModel.hasCalendarPermission()) }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasPermission = granted
        if (granted) viewModel.loadCalendars()
    }
    LaunchedEffect(hasPermission) { if (hasPermission) viewModel.loadCalendars() }

    var selectedCalendar by remember { mutableStateOf<CalendarChoice?>(null) }
    var showCalendarPicker by remember { mutableStateOf(false) }
    var keywords by remember { mutableStateOf("") }
    var matchType by remember { mutableStateOf("CONTAINS") }
    var mode by remember { mutableStateOf(TargetRingerMode.SILENT) }

    if (showCalendarPicker) {
        AlertDialog(
            onDismissRequest = { showCalendarPicker = false },
            confirmButton = { TextButton(onClick = { showCalendarPicker = false }) { Text("Close") } },
            title = { Text("Choose a calendar") },
            text = {
                if (calendars.isEmpty()) {
                    Text("No calendars found on this device.")
                } else {
                    Column {
                        calendars.forEach { calendar ->
                            Text(
                                calendar.third.ifBlank { "(unnamed calendar)" },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp)
                                    .then(
                                        Modifier.clickableSelectCalendar { selectedCalendar = calendar; showCalendarPicker = false }
                                    )
                            )
                        }
                    }
                }
            }
        )
    }

    AppScreenScaffold(title = "Calendar rules", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            if (!hasPermission) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Calendar access needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "SmartSilentScheduler only reads event start/end times and titles to match " +
                                "against your rules - nothing leaves this device.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(onClick = { permissionLauncher.launch(Manifest.permission.READ_CALENDAR) }) {
                            Text("Grant calendar access")
                        }
                    }
                }
            } else {
                if (rules.isNotEmpty()) {
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(rules, key = { it.id }) { rule ->
                            Card {
                                ListItem(
                                    headlineContent = { Text("\"${rule.keywords}\" on ${rule.calendarDisplayName}") },
                                    supportingContent = {
                                        Text("${rule.matchType.lowercase()} \u00b7 ${rule.mode.lowercase()}")
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { viewModel.delete(rule.id) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete")
                                        }
                                    }
                                )
                            }
                        }
                    }
                } else {
                    Text(
                        "No calendar rules yet. Add one below to silence the ringer whenever a matching event is happening.",
                        style = MaterialTheme.typography.bodyMedium
                    )
                }

                Text("Add a calendar rule", style = MaterialTheme.typography.titleSmall)

                OutlinedButton(onClick = { showCalendarPicker = true }) {
                    Text(selectedCalendar?.third ?: "Choose a calendar")
                }

                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text("Event title contains") },
                    modifier = Modifier.fillMaxWidth()
                )

                Column {
                    Text("Match type", style = MaterialTheme.typography.titleSmall)
                    listOf("CONTAINS", "EXACT", "STARTS_WITH").forEach { type ->
                        androidx.compose.foundation.layout.Row {
                            RadioButton(selected = matchType == type, onClick = { matchType = type })
                            Text(type.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Column {
                    Text("Silence to", style = MaterialTheme.typography.titleSmall)
                    TargetRingerMode.entries.forEach { candidate ->
                        androidx.compose.foundation.layout.Row {
                            RadioButton(selected = mode == candidate, onClick = { mode = candidate })
                            Text(candidate.name.lowercase().replaceFirstChar { it.uppercase() })
                        }
                    }
                }

                Button(
                    onClick = {
                        selectedCalendar?.let { calendar ->
                            viewModel.addRule(calendar, keywords, matchType, mode)
                            keywords = ""
                        }
                    },
                    enabled = selectedCalendar != null && keywords.isNotBlank()
                ) { Text("Add rule") }
            }
        }
    }
}

/** Small named helper so the picker dialog's row-click reads clearly at the
 * call site above. Uses the plain single-lambda clickable() overload, which
 * has been stable across Compose versions, rather than one of the overloads
 * with explicit indication/interactionSource parameters whose exact shape
 * has changed release to release. */
private fun Modifier.clickableSelectCalendar(onClick: () -> Unit): Modifier =
    this.then(Modifier.clickable(onClick = onClick))
