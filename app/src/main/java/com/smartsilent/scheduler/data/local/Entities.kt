package com.smartsilent.scheduler.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Framework Section 32. One-time rules (oneTimeAt != null) reuse startTime/
 * endTime as the time-of-day window on that single date - a v7.0
 * implementation decision: v6.0 gave one-time rules a start moment but never
 * defined how their end is represented, and a fifth time-related column for
 * a case fully covered by the existing two seemed worse than reusing them.
 * See RuleResolver.expand.
 */
@Entity(tableName = "rules")
data class RuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val type: String,               // "WEEKLY" or "BEDTIME" - Section 3.1 / 3.4
    val enabled: Boolean,
    val mode: String,                // NORMAL, VIBRATE, SILENT
    val startTime: String,           // local time-of-day, "HH:mm"
    val endTime: String,             // local time-of-day, "HH:mm"; end <= start means overnight
    val oneTimeAt: Long?,            // epoch millis; null for recurring rules
    val weekdayMask: Int,            // bit 0 = Sunday .. bit 6 = Saturday; 0 = every day / not applicable
    val timezoneId: String,
    val exceptionProfileId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long
)

@Entity(tableName = "temporary_overrides")
data class TemporaryOverrideEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val mode: String,
    val createdAt: Long,
    val expiresAt: Long,
    val enabled: Boolean,
    val revision: Long,
    val creationSource: String? = null
)

@Entity(tableName = "calendar_rules")
data class CalendarRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val enabled: Boolean,
    val calendarId: Long,
    // v7.0 backup fix (CalendarIdResolver): calendarId is NOT stable across
    // devices/reinstalls, so the account/display name are stored alongside
    // it purely so a restored backup can re-resolve the right calendar
    // instead of silently pointing at whatever ID happens to exist.
    val calendarAccountName: String,
    val calendarDisplayName: String,
    val matchType: String,           // CONTAINS, EXACT, STARTS_WITH
    val keywords: String,
    val mode: String,
    val exceptionProfileId: Long?,
    val createdAt: Long,
    val updatedAt: Long,
    val revision: Long
)

@Entity(tableName = "exception_profiles")
data class ExceptionProfileEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val contactPolicy: String,          // ANYONE, CONTACTS, STARRED, UNSUPPORTED - Section 6.1
    val systemAppExceptionMode: String, // SUPPORTED, UNAVAILABLE - Section 6.2
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "app_settings")
data class AppSettingEntity(
    @PrimaryKey val key: String,
    val value: String
)

/** Framework Section 20 - a deliberate singleton row (id is always 1).
 * preAutomationDndMode/preAutomationNotificationPolicy store the raw
 * pre-automation SystemState as strings (Int.toString()) so Section 22's
 * restore has something to read back - see OwnershipManager/RestoreManager. */
@Entity(tableName = "automation_state")
data class AutomationStateEntity(
    @PrimaryKey val id: Int = 1,
    val lastAppliedPolicy: String?,
    val lastAppliedStateHash: String?,
    val automationSessionId: String,
    val generation: Long,
    val lastReconciliation: Long?,
    val preAutomationDndMode: String? = null,
    val preAutomationNotificationPolicy: String? = null
)

@Entity(tableName = "execution_log")
data class ExecutionLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    val trigger: String,
    val winnerRuleId: Long?,
    val result: String,             // APPLIED, FAILED, NO_ACTION
    val errorCode: String? = null
)
