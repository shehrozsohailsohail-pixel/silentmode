package com.smartsilent.scheduler.core.rules

import com.smartsilent.scheduler.calendar.CalendarOccurrence
import com.smartsilent.scheduler.data.local.RuleEntity
import com.smartsilent.scheduler.data.local.TemporaryOverrideEntity
import com.smartsilent.scheduler.time.BoundaryCalculator
import com.smartsilent.scheduler.time.LocalTimeResolver
import com.smartsilent.scheduler.time.TimezoneResolver
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

/** Framework Section 32 - validates a rule's stored fields are internally
 * consistent before it's ever handed to the engine (used by the repository
 * layer at write time, and again defensively at read/expand time). */
object RuleValidator {
    fun isValid(rule: RuleEntity): Boolean {
        val mode = runCatching { com.smartsilent.scheduler.core.policy.TargetRingerMode.valueOf(rule.mode) }.getOrNull()
            ?: return false
        val start = LocalTimeResolver.parseOrNull(rule.startTime) ?: return false
        val end = LocalTimeResolver.parseOrNull(rule.endTime) ?: return false
        // Bug found by actually running the engine (not by re-reading code):
        // this used to only reject start==end for every-day rules
        // (weekdayMask == 0, oneTimeAt == null). A specific-weekday rule
        // with identical start/end slipped through instead, and silently
        // became a full 24-hour rule - "end not after start" is treated as
        // "wraps to the next day" by BoundaryCalculator, so equal times wrap
        // all the way around. Same bad input, two different behaviors
        // depending on an unrelated field. Rejected unconditionally now.
        if (start == end) return false
        return true
    }
}

/**
 * Framework Section 4's tie-break chain, isolated so it can be reasoned
 * about (and tested) independently of occurrence expansion: rule-type
 * priority, then latest effective start, then latest revision, then a
 * stable ID. The ID-direction choice isn't specified by Section 4 beyond
 * "deterministic" - this picks the higher ID; nothing downstream depends on
 * which direction, only that it's fixed.
 */
object ConflictResolver {
    fun pickWinner(candidates: List<RuleOccurrence>): RuleOccurrence? {
        if (candidates.isEmpty()) return null
        return candidates.sortedWith(
            compareBy<RuleOccurrence> { it.ruleType.ordinal }
                .thenByDescending { it.start }
                .thenByDescending { it.revision }
                .thenByDescending { it.ruleId }
        ).first()
    }
}

/**
 * Expands rule rows into concrete RuleOccurrence instances over a local-date
 * window. One-time rules (oneTimeAt != null) reuse startTime/endTime as the
 * time-of-day window on the single date derived from oneTimeAt - a v7.0
 * implementation decision recorded on RuleEntity, since v6.0 gave one-time
 * rules a start moment but never defined how their end is represented.
 */
object RuleResolver {
    fun expand(
        rule: RuleEntity,
        ruleType: RuleType,
        windowStart: LocalDate,
        windowEnd: LocalDate
    ): List<RuleOccurrence> {
        if (!rule.enabled || !RuleValidator.isValid(rule)) return emptyList()
        val zone = TimezoneResolver.resolveOrDefault(rule.timezoneId)
        val mode = com.smartsilent.scheduler.core.policy.TargetRingerMode.valueOf(rule.mode)
        val startTime = LocalTimeResolver.parseOrNull(rule.startTime)!!
        val endTime = LocalTimeResolver.parseOrNull(rule.endTime)!!

        val applicableDates: List<LocalDate> = if (rule.oneTimeAt != null) {
            val date = Instant.ofEpochMilli(rule.oneTimeAt).atZone(zone).toLocalDate()
            if (!date.isBefore(windowStart) && !date.isAfter(windowEnd)) listOf(date) else emptyList()
        } else {
            generateSequence(windowStart) { it.plusDays(1) }
                .takeWhile { !it.isAfter(windowEnd) }
                .filter { rule.weekdayMask == 0 || isWeekdaySelected(rule.weekdayMask, it.dayOfWeek) }
                .toList()
        }

        return applicableDates.map { day ->
            val (start, end) = BoundaryCalculator.computeBoundary(day, startTime, endTime, zone)
            RuleOccurrence(ruleType, rule.id, rule.revision, start, end, mode, rule.exceptionProfileId)
        }
    }

    private fun isWeekdaySelected(mask: Int, day: DayOfWeek): Boolean {
        // bit 0 = Sunday .. bit 6 = Saturday (Section 32). DayOfWeek.value is
        // Mon=1..Sun=7, so `% 7` remaps Sun(7)->0, Mon(1)->1, ..., Sat(6)->6.
        val bitIndex = day.value % 7
        return (mask shr bitIndex) and 1 == 1
    }
}

/**
 * Facade over RuleResolver/ConflictResolver - this is what ReconciliationEngine
 * actually calls. Kept as a thin class (rather than folding callers directly
 * onto the two objects above) so it has one place to inject/mock in tests
 * that want to stay at this level instead of the finer-grained objects.
 */
class RuleEngine @Inject constructor() {

    fun expandOccurrences(rule: RuleEntity, ruleType: RuleType, windowStart: LocalDate, windowEnd: LocalDate) =
        RuleResolver.expand(rule, ruleType, windowStart, windowEnd)

    /** Section 4 + Section 35: the single winning candidate active at [now],
     * across every rule type, or null when nothing is active (which is
     * NO_AUTOMATION, never a forced NORMAL - Section 51 Scenario B). */
    fun resolveWinner(
        now: Instant,
        temporaryOverride: TemporaryOverrideEntity?,
        calendarMatches: List<CalendarOccurrence>,
        manualOccurrences: List<RuleOccurrence>,
        bedtimeOccurrences: List<RuleOccurrence>
    ): RuleOccurrence? {
        val candidates = mutableListOf<RuleOccurrence>()

        if (temporaryOverride != null && temporaryOverride.enabled &&
            now.toEpochMilli() >= temporaryOverride.createdAt &&
            now.toEpochMilli() < temporaryOverride.expiresAt
        ) {
            runCatching { com.smartsilent.scheduler.core.policy.TargetRingerMode.valueOf(temporaryOverride.mode) }
                .getOrNull()?.let { mode ->
                    candidates += RuleOccurrence(
                        RuleType.TEMPORARY_OVERRIDE, temporaryOverride.id, temporaryOverride.revision,
                        Instant.ofEpochMilli(temporaryOverride.createdAt), Instant.ofEpochMilli(temporaryOverride.expiresAt),
                        mode, null
                    )
                }
        }

        calendarMatches.filter { now >= it.start && now < it.end }.mapTo(candidates) {
            RuleOccurrence(RuleType.CALENDAR_EVENT, it.calendarRuleId, it.revision, it.start, it.end, it.mode, it.exceptionProfileId)
        }
        manualOccurrences.filterTo(candidates) { now >= it.start && now < it.end }
        bedtimeOccurrences.filterTo(candidates) { now >= it.start && now < it.end }

        return ConflictResolver.pickWinner(candidates)
    }
}
