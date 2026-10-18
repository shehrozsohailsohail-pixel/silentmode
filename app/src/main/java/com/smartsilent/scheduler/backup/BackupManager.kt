package com.smartsilent.scheduler.backup

import com.smartsilent.scheduler.calendar.CalendarProviderReader
import com.smartsilent.scheduler.data.local.CalendarRuleEntity
import com.smartsilent.scheduler.data.local.RuleEntity
import com.smartsilent.scheduler.data.repository.CalendarRuleRepository
import com.smartsilent.scheduler.data.repository.RuleRepository
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Framework Section 43 (Backup/Restore) + v7.0 Sections 62-64. This class
 * only produces/consumes a JSON *string* - actually writing it to (or
 * reading it from) a user-chosen location via the Storage Access Framework
 * is done by the Backup screen (a later increment's UI), which is the only
 * layer that needs an Activity result launcher. Keeping the boundary here
 * means this class is fully unit-testable without any SAF/Uri machinery.
 */
@Singleton
class BackupManager @Inject constructor(
    private val ruleRepository: RuleRepository,
    private val calendarRuleRepository: CalendarRuleRepository,
    private val calendarProviderReader: CalendarProviderReader
) {

    suspend fun exportToJson(): String {
        val rulesArray = JSONArray()
        // A point-in-time snapshot is what export wants, not a stream, so this
        // reads via the plain suspend fun rather than collecting observeAll()'s
        // Flow. Increment scope: exports enabled rules only; exporting disabled
        // ones too is a one-line change once RuleRepository grows a getAll()
        // suspend fun alongside its existing getEnabled().
        val allRules = ruleRepository.getEnabled()
        for (rule in allRules) rulesArray.put(ruleToJson(rule))

        val calendarRulesArray = JSONArray()
        for (rule in calendarRuleRepository.getEnabled()) calendarRulesArray.put(calendarRuleToJson(rule))

        val root = JSONObject().apply {
            put("formatVersion", BACKUP_FORMAT_VERSION)
            put("exportedAtEpochMillis", System.currentTimeMillis())
            put("rules", rulesArray)
            put("calendarRules", calendarRulesArray)
        }
        SensitiveFieldFilter.assertNoSensitiveKeys(root)
        return root.toString(2)
    }

    suspend fun importFromJson(json: String): BackupImportResult {
        val root = runCatching { JSONObject(json) }.getOrNull()
            ?: return BackupImportResult(false, 0, 0, emptyList(), "Not valid JSON")
        if (!BackupValidator.isValidShape(root)) {
            return BackupImportResult(false, 0, 0, emptyList(), "Unrecognized backup format")
        }

        var rulesImported = 0
        val rulesJson = root.getJSONArray("rules")
        for (i in 0 until rulesJson.length()) {
            runCatching { ruleFromJson(rulesJson.getJSONObject(i)) }
                .onSuccess { ruleRepository.save(it); rulesImported++ }
        }

        val availableCalendars = calendarProviderReader.listAvailableCalendars()
        var calendarRulesImported = 0
        val unmatched = mutableListOf<String>()
        val calendarRulesJson = root.getJSONArray("calendarRules")
        for (i in 0 until calendarRulesJson.length()) {
            val obj = calendarRulesJson.getJSONObject(i)
            val accountName = obj.optString("calendarAccountName", "")
            val displayName = obj.optString("calendarDisplayName", "")
            val resolvedId = CalendarIdResolver.resolve(accountName, displayName, availableCalendars)
            if (resolvedId == null) {
                unmatched += displayName
                continue // Section 68 philosophy applied to import: skip what
                // can't be resolved rather than failing the whole import.
            }
            runCatching { calendarRuleFromJson(obj, resolvedId) }
                .onSuccess { calendarRuleRepository.save(it); calendarRulesImported++ }
        }

        return BackupImportResult(true, rulesImported, calendarRulesImported, unmatched)
    }

    private fun ruleToJson(rule: RuleEntity): JSONObject = JSONObject().apply {
        put("type", rule.type)
        put("enabled", rule.enabled)
        put("mode", rule.mode)
        put("startTime", rule.startTime)
        put("endTime", rule.endTime)
        put("oneTimeAt", rule.oneTimeAt ?: JSONObject.NULL)
        put("weekdayMask", rule.weekdayMask)
        put("timezoneId", rule.timezoneId)
        put("exceptionProfileId", rule.exceptionProfileId ?: JSONObject.NULL)
        put("createdAt", rule.createdAt)
        put("updatedAt", rule.updatedAt)
        put("revision", rule.revision)
    }

    private fun ruleFromJson(json: JSONObject): RuleEntity = RuleEntity(
        id = 0, // always inserted as new - Section 43 import never overwrites by raw ID
        type = json.getString("type"),
        enabled = json.getBoolean("enabled"),
        mode = json.getString("mode"),
        startTime = json.getString("startTime"),
        endTime = json.getString("endTime"),
        oneTimeAt = if (json.isNull("oneTimeAt")) null else json.getLong("oneTimeAt"),
        weekdayMask = json.getInt("weekdayMask"),
        timezoneId = json.getString("timezoneId"),
        exceptionProfileId = if (json.isNull("exceptionProfileId")) null else json.getLong("exceptionProfileId"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
        revision = json.getLong("revision")
    )

    private fun calendarRuleToJson(rule: CalendarRuleEntity): JSONObject = JSONObject().apply {
        put("calendarAccountName", rule.calendarAccountName)
        put("calendarDisplayName", rule.calendarDisplayName)
        put("matchType", rule.matchType)
        put("keywords", rule.keywords)
        put("mode", rule.mode)
        put("enabled", rule.enabled)
        put("exceptionProfileId", rule.exceptionProfileId ?: JSONObject.NULL)
        put("createdAt", rule.createdAt)
        put("updatedAt", rule.updatedAt)
        put("revision", rule.revision)
    }

    private fun calendarRuleFromJson(json: JSONObject, resolvedCalendarId: Long): CalendarRuleEntity = CalendarRuleEntity(
        id = 0,
        enabled = json.getBoolean("enabled"),
        calendarId = resolvedCalendarId,
        calendarAccountName = json.getString("calendarAccountName"),
        calendarDisplayName = json.getString("calendarDisplayName"),
        matchType = json.getString("matchType"),
        keywords = json.getString("keywords"),
        mode = json.getString("mode"),
        exceptionProfileId = if (json.isNull("exceptionProfileId")) null else json.getLong("exceptionProfileId"),
        createdAt = json.getLong("createdAt"),
        updatedAt = json.getLong("updatedAt"),
        revision = json.getLong("revision")
    )
}
