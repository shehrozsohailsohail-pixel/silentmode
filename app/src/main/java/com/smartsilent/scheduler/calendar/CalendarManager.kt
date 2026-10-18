package com.smartsilent.scheduler.calendar

import android.content.Context
import android.provider.CalendarContract
import com.smartsilent.scheduler.android.capability.Capability
import com.smartsilent.scheduler.android.capability.CapabilityManager
import com.smartsilent.scheduler.android.capability.CapabilityStatus
import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.core.reconciliation.CalendarRuleResolver
import com.smartsilent.scheduler.data.repository.CalendarRuleRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Framework Section 19's match rules (CONTAINS/EXACT/STARTS_WITH), applied
 * to an event title against a calendar_rules row's stored keyword. */
object CalendarRuleMatcher {
    fun matches(matchType: String, keyword: String, eventTitle: String): Boolean {
        val trimmedKeyword = keyword.trim()
        if (trimmedKeyword.isEmpty()) return false
        val title = eventTitle.trim()
        return when (matchType) {
            "EXACT" -> title.equals(trimmedKeyword, ignoreCase = true)
            "STARTS_WITH" -> title.startsWith(trimmedKeyword, ignoreCase = true)
            else -> title.contains(trimmedKeyword, ignoreCase = true) // CONTAINS, and the safe default
        }
    }
}

/** Thin wrapper over CalendarContract.Instances - the only class in this app
 * that talks to the Calendar Provider directly. */
@Singleton
class CalendarProviderReader @Inject constructor(
    @ApplicationContext private val context: Context
) {
    /** Framework v7.0 Section 68: on any provider error (including a
     * SecurityException if permission was revoked between the capability
     * check and this call), return an empty list rather than throwing -
     * "no matches this pass", never a crash. */
    suspend fun queryInstances(beginMillis: Long, endMillis: Long): List<CalendarEvent> =
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                CalendarContract.Instances.EVENT_ID,
                CalendarContract.Instances.CALENDAR_ID,
                CalendarContract.Instances.TITLE,
                CalendarContract.Instances.BEGIN,
                CalendarContract.Instances.END
            )
            runCatching {
                val results = mutableListOf<CalendarEvent>()
                CalendarContract.Instances.query(context.contentResolver, projection, beginMillis, endMillis)
                    ?.use { cursor ->
                        val idxEventId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.EVENT_ID)
                        val idxCalId = cursor.getColumnIndexOrThrow(CalendarContract.Instances.CALENDAR_ID)
                        val idxTitle = cursor.getColumnIndexOrThrow(CalendarContract.Instances.TITLE)
                        val idxBegin = cursor.getColumnIndexOrThrow(CalendarContract.Instances.BEGIN)
                        val idxEnd = cursor.getColumnIndexOrThrow(CalendarContract.Instances.END)
                        while (cursor.moveToNext()) {
                            results += CalendarEvent(
                                eventId = cursor.getLong(idxEventId),
                                calendarId = cursor.getLong(idxCalId),
                                title = cursor.getString(idxTitle) ?: "",
                                beginMillis = cursor.getLong(idxBegin),
                                endMillis = cursor.getLong(idxEnd)
                            )
                        }
                    }
                results
            }.getOrDefault(emptyList())
        }

    /** Used by the rule-editor UI to let the user pick which calendar a rule
     * watches, and by CalendarIdResolver (backup/) to re-match calendars by
     * name after a restore. */
    suspend fun listAvailableCalendars(): List<Triple<Long, String, String>> = withContext(Dispatchers.IO) {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        runCatching {
            val results = mutableListOf<Triple<Long, String, String>>()
            context.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, projection, null, null, null)
                ?.use { cursor ->
                    val idxId = cursor.getColumnIndexOrThrow(CalendarContract.Calendars._ID)
                    val idxAccount = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.ACCOUNT_NAME)
                    val idxDisplay = cursor.getColumnIndexOrThrow(CalendarContract.Calendars.CALENDAR_DISPLAY_NAME)
                    while (cursor.moveToNext()) {
                        results += Triple(
                            cursor.getLong(idxId),
                            cursor.getString(idxAccount) ?: "",
                            cursor.getString(idxDisplay) ?: ""
                        )
                    }
                }
            results
        }.getOrDefault(emptyList())
    }
}

/**
 * Framework Section 3.2/19 - the real implementation of the interface
 * ReconciliationEngine depends on (defined in core.reconciliation so that
 * package doesn't need to depend on this one). Increment-1 shipped
 * NoOpCalendarRuleResolver here; this replaces it for real, one provider
 * query per reconciliation pass shared across every enabled calendar rule
 * rather than one query per rule.
 */
@Singleton
class CalendarManager @Inject constructor(
    private val providerReader: CalendarProviderReader,
    private val calendarRuleRepository: CalendarRuleRepository,
    private val capabilityManager: CapabilityManager
) : CalendarRuleResolver {

    override suspend fun resolveMatches(windowStart: LocalDate, windowEnd: LocalDate): List<CalendarOccurrence> {
        if (capabilityManager.status(Capability.CALENDAR_PERMISSION) != CapabilityStatus.GRANTED) return emptyList()
        val enabledRules = calendarRuleRepository.getEnabled()
        if (enabledRules.isEmpty()) return emptyList()

        val zone = ZoneId.systemDefault()
        val beginMillis = windowStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMillis = windowEnd.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val events = providerReader.queryInstances(beginMillis, endMillis)
        if (events.isEmpty()) return emptyList()

        val occurrences = mutableListOf<CalendarOccurrence>()
        for (rule in enabledRules) {
            val mode = runCatching { TargetRingerMode.valueOf(rule.mode) }.getOrNull() ?: continue
            events
                .filter { it.calendarId == rule.calendarId }
                .filter { CalendarRuleMatcher.matches(rule.matchType, rule.keywords, it.title) }
                .forEach { event ->
                    occurrences += CalendarOccurrence(
                        calendarRuleId = rule.id,
                        revision = rule.revision,
                        start = java.time.Instant.ofEpochMilli(event.beginMillis),
                        end = java.time.Instant.ofEpochMilli(event.endMillis),
                        mode = mode,
                        exceptionProfileId = rule.exceptionProfileId
                    )
                }
        }
        return occurrences
    }
}
