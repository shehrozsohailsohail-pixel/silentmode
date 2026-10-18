package com.smartsilent.scheduler.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.smartsilent.scheduler.ui.theme.SmartSilentTheme
import dagger.hilt.android.AndroidEntryPoint

object Routes {
    const val SETTINGS = "settings"
    const val RULES = "rules"
    const val RULE_EDITOR = "rule_editor/{ruleId}"
    const val OVERRIDE = "override"
    const val BEDTIME = "bedtime"
    const val DIAGNOSTICS = "diagnostics"
    const val ADMIN = "admin"
    const val CALENDAR_RULES = "calendar_rules"
    const val BACKUP = "backup"

    fun ruleEditor(ruleId: Long) = "rule_editor/$ruleId"
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SmartSilentTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    val navController = rememberNavController()
                    NavHost(navController = navController, startDestination = Routes.SETTINGS) {
                        composable(Routes.SETTINGS) { SettingsScreen(navController) }
                        composable(Routes.RULES) { RuleListScreen(navController) }
                        composable(Routes.RULE_EDITOR) { backStackEntry ->
                            val ruleId = backStackEntry.arguments?.getString("ruleId")?.toLongOrNull() ?: 0L
                            RuleEditorScreen(navController, ruleId)
                        }
                        composable(Routes.OVERRIDE) { OverrideScreen(navController) }
                        composable(Routes.BEDTIME) { BedtimeScreen(navController) }
                        composable(Routes.DIAGNOSTICS) { DiagnosticsScreen(navController) }
                        composable(Routes.ADMIN) { AdminScreen(navController) }
                        composable(Routes.CALENDAR_RULES) { CalendarRuleScreen(navController) }
                        composable(Routes.BACKUP) { BackupScreen(navController) }
                    }
                }
            }
        }
    }
}
