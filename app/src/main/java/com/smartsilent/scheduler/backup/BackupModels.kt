package com.smartsilent.scheduler.backup

import org.json.JSONObject

const val BACKUP_FORMAT_VERSION = 1

/** Result of an import attempt - counts plus which calendar rules, if any,
 * could not be matched to a calendar on this device (v7.0 CalendarIdResolver:
 * calendarId alone is not portable across devices/reinstalls). */
data class BackupImportResult(
    val succeeded: Boolean,
    val rulesImported: Int,
    val calendarRulesImported: Int,
    val calendarRulesUnmatched: List<String>, // display names that couldn't be re-resolved
    val failureReason: String? = null
)

/** Framework Section 43 - checks the top-level shape/version of a parsed
 * export before any of it is trusted, rather than discovering a corrupt or
 * foreign JSON file three fields deep into importing it. */
object BackupValidator {
    fun isValidShape(json: JSONObject): Boolean {
        if (!json.has("formatVersion") || !json.has("rules") || !json.has("calendarRules")) return false
        val version = json.optInt("formatVersion", -1)
        // Only the current version is accepted for now; a future version
        // bump needs an explicit migration step here, not a silent guess.
        return version == BACKUP_FORMAT_VERSION
    }
}

/**
 * Framework v7.0 (backup fix): calendarId is a device-local identifier and
 * is NOT guaranteed to mean the same calendar (or exist at all) after a
 * restore to a different device or a fresh reinstall. Export stores the
 * calendar's account name + display name alongside its ID (see
 * CalendarRuleEntity); import uses this to re-resolve the right ID on the
 * current device rather than trusting the old one.
 */
object CalendarIdResolver {
    fun resolve(
        accountName: String,
        displayName: String,
        availableCalendars: List<Triple<Long, String, String>> // (id, accountName, displayName)
    ): Long? = availableCalendars
        .firstOrNull { it.second == accountName && it.third == displayName }
        ?.first
        ?: availableCalendars.firstOrNull { it.third == displayName }?.first // fall back to name-only match
}

/**
 * Framework v7.0 Section 64's principle, applied to the export path
 * specifically rather than only the OS-level Auto Backup path: BackupManager
 * is only ever built from RuleRepository/CalendarRuleRepository/etc, never
 * from SecureStorage, so admin-auth secrets are never in scope to leak in
 * the first place. This function is a cheap, explicit runtime check of that
 * invariant - a safety net against a future change accidentally adding a
 * sensitive field, not a substitute for actual encryption of secret data
 * (there is none here to encrypt).
 */
object SensitiveFieldFilter {
    private val forbiddenKeyFragments = listOf("password", "admin_secret", "salt")

    fun assertNoSensitiveKeys(json: JSONObject) {
        val keys = json.keys().asSequence().toList()
        val leaked = keys.filter { key -> forbiddenKeyFragments.any { key.contains(it, ignoreCase = true) } }
        check(leaked.isEmpty()) { "Backup export must never contain admin-auth fields, found: $leaked" }
    }
}
