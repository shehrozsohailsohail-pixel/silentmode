package com.smartsilent.scheduler.ui

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.NavController
import com.smartsilent.scheduler.backup.BackupImportResult
import com.smartsilent.scheduler.backup.BackupManager
import com.smartsilent.scheduler.core.reconciliation.ReconciliationEngine
import com.smartsilent.scheduler.core.reconciliation.ReconciliationTrigger
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import javax.inject.Inject

@HiltViewModel
class BackupViewModel @Inject constructor(
    private val backupManager: BackupManager,
    private val reconciliationEngine: ReconciliationEngine
) : ViewModel() {

    /**
     * Writing/reading the chosen SAF Uri happens here rather than in
     * BackupManager on purpose - BackupManager only ever produces/consumes a
     * plain string, which is what keeps it unit-testable without any
     * ContentResolver/Uri involved (see its class doc). This ViewModel is
     * the one place that bridges the two.
     */
    fun exportTo(context: Context, uri: Uri, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            val json = backupManager.exportToJson()
            val succeeded = runCatching {
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    OutputStreamWriter(stream).use { it.write(json) }
                } ?: error("Could not open output stream")
            }.isSuccess
            onDone(succeeded)
        }
    }

    fun importFrom(context: Context, uri: Uri, onDone: (BackupImportResult) -> Unit) {
        viewModelScope.launch {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    BufferedReader(InputStreamReader(stream)).readText()
                }
            }.getOrNull()

            val result = if (text == null) {
                BackupImportResult(false, 0, 0, emptyList(), "Could not read the selected file")
            } else {
                backupManager.importFromJson(text)
            }
            if (result.succeeded) {
                reconciliationEngine.reconcile(ReconciliationTrigger.RuleMutated(0))
            }
            onDone(result)
        }
    }
}

@Composable
fun BackupScreen(navController: NavController, viewModel: BackupViewModel = hiltViewModel()) {
    val context = LocalContext.current
    var status by remember { mutableStateOf("") }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Exporting..."
        viewModel.exportTo(context, uri) { succeeded ->
            status = if (succeeded) "Exported successfully" else "Export failed - could not write to the chosen location"
        }
    }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        status = "Importing..."
        viewModel.importFrom(context, uri) { result ->
            status = if (result.succeeded) {
                val unmatched = if (result.calendarRulesUnmatched.isNotEmpty()) {
                    " (${result.calendarRulesUnmatched.size} calendar rule(s) skipped - matching calendar not found on this device)"
                } else ""
                "Imported ${result.rulesImported} schedule(s) and ${result.calendarRulesImported} calendar rule(s)$unmatched"
            } else {
                "Import failed: ${result.failureReason}"
            }
        }
    }

    AppScreenScaffold(title = "Backup & restore", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Text(
                "Exports your manual schedules and calendar rules as a JSON file you choose the " +
                    "location for. The admin password is never included in an export.",
                style = MaterialTheme.typography.bodySmall
            )

            Button(onClick = { exportLauncher.launch("smartsilent-backup.json") }) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Export backup")
            }

            Button(onClick = { importLauncher.launch(arrayOf("application/json")) }) {
                Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(" Import backup")
            }

            Text(
                "Calendar rules are matched to a calendar on this device by name after import, " +
                    "since the identifiers a calendar uses can differ between devices.",
                style = MaterialTheme.typography.bodySmall
            )

            if (status.isNotEmpty()) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                    Text(status, modifier = Modifier.padding(16.dp))
                }
            }
        }
    }
}
