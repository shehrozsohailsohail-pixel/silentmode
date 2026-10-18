package com.smartsilent.scheduler.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.navigation.NavController
import com.smartsilent.scheduler.security.AdminAuthManager
import com.smartsilent.scheduler.ui.components.AppScreenScaffold
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class AdminViewModel @Inject constructor(private val adminAuthManager: AdminAuthManager) : ViewModel() {
    fun isPasswordSet() = adminAuthManager.isPasswordSet()
    fun setPassword(password: String) = adminAuthManager.setPassword(password)
    fun verifyPassword(password: String) = adminAuthManager.verifyPassword(password)
    fun clearPassword() = adminAuthManager.clearPassword()
}

@Composable
fun AdminScreen(navController: NavController, viewModel: AdminViewModel = hiltViewModel()) {
    var password by remember { mutableStateOf("") }
    var status by remember { mutableStateOf("") }
    var passwordIsSet by remember { mutableStateOf(viewModel.isPasswordSet()) }

    AppScreenScaffold(title = "Admin lock", onBack = { navController.popBackStack() }) { innerModifier ->
        Column(modifier = innerModifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            // Framework v7.0 Section 67 - shown directly, not only documented
            // in code, since this is exactly the expectation that needs
            // setting before someone relies on it.
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
                Row(modifier = Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Icon(Icons.Filled.Info, contentDescription = null)
                    Text(
                        "This password only locks SmartSilentScheduler's own screens. It can't stop " +
                            "anyone from changing ringer or Do Not Disturb directly from Android's " +
                            "system Settings or volume buttons - no app can lock that on this phone.",
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text(if (passwordIsSet) "Enter password" else "Set a password") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )

            if (passwordIsSet) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(onClick = {
                        status = if (viewModel.verifyPassword(password)) "Correct" else "Incorrect"
                    }) { Text("Verify") }
                    OutlinedButton(onClick = {
                        viewModel.clearPassword()
                        passwordIsSet = false
                        status = "Password removed"
                    }) { Text("Remove password") }
                }
            } else {
                Button(onClick = {
                    if (password.isNotBlank()) {
                        viewModel.setPassword(password)
                        passwordIsSet = true
                        status = "Password set"
                    }
                }) { Text("Set password") }
            }

            if (status.isNotEmpty()) Text(status, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
