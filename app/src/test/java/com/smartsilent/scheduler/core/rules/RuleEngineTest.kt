package com.smartsilent.scheduler.core.rules

import com.smartsilent.scheduler.calendar.CalendarOccurrence
import com.smartsilent.scheduler.core.policy.PolicyResolver
import com.smartsilent.scheduler.core.policy.PolicyType
import com.smartsilent.scheduler.core.policy.TargetRingerMode
import com.smartsilent.scheduler.data.local.RuleEntity
import com.smartsilent.scheduler.data.local.TemporaryOverrideEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneOffset

/**
 * Operationalizes framework Section 51's adversarial scenarios and Section
 * 52's rule/time test rows as plain, fast JUnit tests, made possible by
 * Section 61's time abstraction (RuleEngine takes `now` as a parameter, so
 * no test here needs a real or mocked device clock at all).
 *
 * Honesty note (see project README): this file has not been compiled or run
 * in the environment that produced it - there is no Android SDK / Gradle
 * available there. Read it as "should pass once built", checked by hand-
 * tracing each assertion against RuleEngine/ConflictResolver's logic, not as
 * "has been run and passed".
 */
class RuleEngineTest {

    private val engine = RuleEngine()

    private fun manualRule(
        id: Long, start: String, end: String, mode: String,
        weekdayMask: Int = 0, revision: Long = 1
    ) = RuleEntity(
        id = id, type = "WEEKLY", enabled = true, mode = mode,
        startTime = start, endTime = end, oneTimeAt = null,
        weekdayMask = weekdayMask, timezoneId = "UTC",
        exceptionProfileId = null, createdAt = 0, updatedAt = 0, revision = revision
    )

    @Test
    fun `temporary override beats calendar and manual while active, then expires`() {
        val day = LocalDate.of(2026, 1, 5)
        val manual = engine.expandOccurrences(
            manualRule(1, "22:00", "07:00", "SILENT"), RuleType.MANUAL_SCHEDULE, day.minusDays(1), day.plusDays(1)
        )
        val override = TemporaryOverrideEntity(
            id = 1, mode = "NORMAL",
            createdAt = day.atTime(23, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            expiresAt = day.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC).toEpochMilli(),
            enabled = true, revision = 1
        )
        val calendarMatch = CalendarOccurrence(
            calendarRuleId = 9, revision = 1,
            start = day.plusDays(1).atTime(0, 0).toInstant(ZoneOffset.UTC),
            end = day.plusDays(1).atTime(6, 0).toInstant(ZoneOffset.UTC),
            mode = TargetRingerMode.SILENT, exceptionProfileId = null
        )

        var now = day.atTime(22, 0).toInstant(ZoneOffset.UTC)
        var winner = engine.resolveWinner(now, null, emptyList(), manual, emptyList())
        assertEquals(RuleType.MANUAL_SCHEDULE, winner?.ruleType)

        now = day.atTime(23, 0).toInstant(ZoneOffset.UTC)
        winner = engine.resolveWinner(now, override, emptyList(), manual, emptyList())
        assertEquals(RuleType.TEMPORARY_OVERRIDE, winner?.ruleType)
        assertEquals(TargetRingerMode.NORMAL, winner?.mode)

        now = day.plusDays(1).atTime(0, 0).toInstant(ZoneOffset.UTC)
        winner = engine.resolveWinner(now, override, listOf(calendarMatch), manual, emptyList())
        assertEquals(RuleType.TEMPORARY_OVERRIDE, winner?.ruleType)

        now = day.plusDays(1).atTime(1, 0).toInstant(ZoneOffset.UTC)
        winner = engine.resolveWinner(now, override, listOf(calendarMatch), manual, emptyList())
        assertEquals(RuleType.CALENDAR_EVENT, winner?.ruleType)
        assertEquals(TargetRingerMode.SILENT, winner?.mode)
    }

    @Test
    fun `end boundary is exclusive, start boundary is inclusive`() {
        val day = LocalDate.of(2026, 1, 5)
        val occurrences = engine.expandOccurrences(manualRule(1, "22:00", "23:00", "SILENT"), RuleType.MANUAL_SCHEDULE, day, day)
        val atStart = day.atTime(22, 0).toInstant(ZoneOffset.UTC)
        val atEnd = day.atTime(23, 0).toInstant(ZoneOffset.UTC)
        assertEquals(RuleType.MANUAL_SCHEDULE, engine.resolveWinner(atStart, null, emptyList(), occurrences, emptyList())?.ruleType)
        assertNull(engine.resolveWinner(atEnd, null, emptyList(), occurrences, emptyList()))
    }

    @Test
    fun `overnight rule is active just after midnight`() {
        val day = LocalDate.of(2026, 1, 5)
        val occurrences = engine.expandOccurrences(
            manualRule(1, "22:00", "07:00", "SILENT"), RuleType.MANUAL_SCHEDULE, day.minusDays(1), day.plusDays(1)
        )
        val justAfterMidnight = day.plusDays(1).atTime(0, 30).toInstant(ZoneOffset.UTC)
        assertEquals(RuleType.MANUAL_SCHEDULE, engine.resolveWinner(justAfterMidnight, null, emptyList(), occurrences, emptyList())?.ruleType)
    }

    @Test
    fun `no active rule resolves to NO_AUTOMATION, not a forced NORMAL`() {
        val policy = PolicyResolver.resolve(null, canApplyRingerPolicy = true)
        assertEquals(PolicyType.NO_AUTOMATION, policy.policyType)
        assertNull(policy.targetRingerMode)
    }

    @Test
    fun `missing DND capability downgrades a real winner to UNAVAILABLE`() {
        val day = LocalDate.of(2026, 1, 5)
        val occurrences = engine.expandOccurrences(manualRule(1, "09:00", "10:00", "SILENT"), RuleType.MANUAL_SCHEDULE, day, day)
        val now = day.atTime(9, 30).toInstant(ZoneOffset.UTC)
        val winner = engine.resolveWinner(now, null, emptyList(), occurrences, emptyList())

        val policyWithCapability = PolicyResolver.resolve(winner, canApplyRingerPolicy = true)
        val policyWithoutCapability = PolicyResolver.resolve(winner, canApplyRingerPolicy = false)

        assertEquals(PolicyType.TOTAL_SILENT, policyWithCapability.policyType)
        assertEquals(PolicyType.UNAVAILABLE, policyWithoutCapability.policyType)
    }

    @Test
    fun `same start time, higher revision wins within the same rule type`() {
        val day = LocalDate.of(2026, 1, 5)
        val older = manualRule(id = 1, start = "09:00", end = "10:00", mode = "SILENT", revision = 1)
        val newer = manualRule(id = 2, start = "09:00", end = "10:00", mode = "VIBRATE", revision = 5)
        val occurrences = engine.expandOccurrences(older, RuleType.MANUAL_SCHEDULE, day, day) +
            engine.expandOccurrences(newer, RuleType.MANUAL_SCHEDULE, day, day)
        val now = day.atTime(9, 30).toInstant(ZoneOffset.UTC)
        val winner = engine.resolveWinner(now, null, emptyList(), occurrences, emptyList())
        assertEquals(2L, winner?.ruleId)
        assertEquals(TargetRingerMode.VIBRATE, winner?.mode)
    }

    @Test
    fun `one-time rule only fires on its own date`() {
        val theDay = LocalDate.of(2026, 3, 10)
        val rule = manualRule(1, "20:00", "21:00", "SILENT").copy(
            oneTimeAt = theDay.atTime(0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        val occurrences = engine.expandOccurrences(rule, RuleType.MANUAL_SCHEDULE, theDay.minusDays(3), theDay.plusDays(3))
        assertEquals(1, occurrences.size)
        val dayBefore = theDay.minusDays(1).atTime(20, 30).toInstant(ZoneOffset.UTC)
        val onDay = theDay.atTime(20, 30).toInstant(ZoneOffset.UTC)
        assertNull(engine.resolveWinner(dayBefore, null, emptyList(), occurrences, emptyList()))
        assertEquals(RuleType.MANUAL_SCHEDULE, engine.resolveWinner(onDay, null, emptyList(), occurrences, emptyList())?.ruleType)
    }

    // --- Everything below was added after actually compiling and running this
    // engine outside the app for the first time (no Android SDK is needed for
    // any of it - see the project README's "Verified by real execution"
    // section). One real bug was found and fixed this way; it's locked in as
    // the first test below so it can never silently reappear. ---

    @Test
    fun `identical start and end time is rejected regardless of weekday mask`() {
        // Bug found by execution: this used to only be rejected for every-day
        // rules (weekdayMask == 0). A specific-weekday rule with start==end
        // slipped through instead and silently became a full 24-hour rule,
        // since "end not after start" is treated as "wraps to the next day".
        val day = LocalDate.of(2026, 6, 15)
        val everyDay = manualRule(1, "10:00", "10:00", "SILENT", weekdayMask = 0)
        val mondayOnly = manualRule(2, "10:00", "10:00", "SILENT", weekdayMask = 1 shl 1)
        assertEquals(0, engine.expandOccurrences(everyDay, RuleType.MANUAL_SCHEDULE, day, day).size)
        assertEquals(0, engine.expandOccurrences(mondayOnly, RuleType.MANUAL_SCHEDULE, day, day).size)
    }

    @Test
    fun `spring-forward DST gap shortens an overnight-adjacent rule by the skipped hour`() {
        // Real 2026 US transition: 2026-03-08, 02:00 to 03:00 does not exist.
        val springForwardDay = LocalDate.of(2026, 3, 8)
        val rule = manualRule(1, "01:00", "04:00", "SILENT").copy(timezoneId = "America/New_York")
        val occurrence = engine.expandOccurrences(rule, RuleType.MANUAL_SCHEDULE, springForwardDay, springForwardDay).single()
        assertEquals(120, java.time.Duration.between(occurrence.start, occurrence.end).toMinutes())
    }

    @Test
    fun `fall-back DST fold lengthens a rule spanning the repeated hour`() {
        // Real 2026 US transition: 2026-11-01, 01:00-02:00 happens twice.
        val fallBackDay = LocalDate.of(2026, 11, 1)
        val rule = manualRule(1, "01:00", "02:00", "SILENT").copy(timezoneId = "America/New_York")
        val occurrence = engine.expandOccurrences(rule, RuleType.MANUAL_SCHEDULE, fallBackDay, fallBackDay).single()
        assertEquals(120, java.time.Duration.between(occurrence.start, occurrence.end).toMinutes())
    }

    @Test
    fun `half and three-quarter hour timezone offsets resolve correctly`() {
        val day = LocalDate.of(2026, 6, 15)
        val kolkata = manualRule(1, "09:00", "09:15", "SILENT").copy(timezoneId = "Asia/Kolkata") // UTC+5:30
        val kathmandu = manualRule(2, "09:00", "09:15", "SILENT").copy(timezoneId = "Asia/Kathmandu") // UTC+5:45
        val kolkataOcc = engine.expandOccurrences(kolkata, RuleType.MANUAL_SCHEDULE, day, day).single()
        val kathmanduOcc = engine.expandOccurrences(kathmandu, RuleType.MANUAL_SCHEDULE, day, day).single()
        assertEquals(day.atTime(3, 30).toInstant(ZoneOffset.UTC), kolkataOcc.start)
        assertEquals(day.atTime(3, 15).toInstant(ZoneOffset.UTC), kathmanduOcc.start)
    }

    @Test
    fun `leap-day one-time rule fires only on Feb 29`() {
        val leapDay = LocalDate.of(2028, 2, 29)
        val rule = manualRule(1, "10:00", "11:00", "SILENT").copy(
            oneTimeAt = leapDay.atTime(0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        )
        val occurrences = engine.expandOccurrences(rule, RuleType.MANUAL_SCHEDULE, leapDay.minusDays(1), leapDay.plusDays(1))
        assertEquals(1, occurrences.size)
    }

    @Test
    fun `overnight rule correctly spans the new year boundary`() {
        val nye = LocalDate.of(2026, 12, 31)
        val rule = manualRule(1, "23:00", "01:00", "SILENT")
        val occurrences = engine.expandOccurrences(rule, RuleType.MANUAL_SCHEDULE, nye, nye.plusDays(1))
        val justAfterMidnight = LocalDate.of(2027, 1, 1).atTime(0, 30).toInstant(ZoneOffset.UTC)
        assertEquals(RuleType.MANUAL_SCHEDULE, engine.resolveWinner(justAfterMidnight, null, emptyList(), occurrences, emptyList())?.ruleType)
    }

    @Test
    fun `three-way same-priority same-start same-revision tie breaks on highest ID`() {
        val day = LocalDate.of(2026, 6, 15)
        val a = manualRule(10, "09:00", "10:00", "SILENT", revision = 5)
        val b = manualRule(20, "09:00", "10:00", "SILENT", revision = 5)
        val c = manualRule(15, "09:00", "10:00", "SILENT", revision = 5)
        val occurrences = listOf(a, b, c).flatMap { engine.expandOccurrences(it, RuleType.MANUAL_SCHEDULE, day, day) }
        val winner = engine.resolveWinner(day.atTime(9, 30).toInstant(ZoneOffset.UTC), null, emptyList(), occurrences, emptyList())
        assertEquals(20L, winner?.ruleId)
    }

    @Test
    fun `override and manual rule starting at the exact same instant - override wins`() {
        val day = LocalDate.of(2026, 6, 15)
        val exactInstant = day.atTime(9, 0).toInstant(ZoneOffset.UTC)
        val override = TemporaryOverrideEntity(
            id = 1, mode = "NORMAL",
            createdAt = exactInstant.toEpochMilli(), expiresAt = exactInstant.plusSeconds(3600).toEpochMilli(),
            enabled = true, revision = 1
        )
        val manual = engine.expandOccurrences(manualRule(1, "09:00", "10:00", "SILENT"), RuleType.MANUAL_SCHEDULE, day, day)
        val winner = engine.resolveWinner(exactInstant, override, emptyList(), manual, emptyList())
        assertEquals(RuleType.TEMPORARY_OVERRIDE, winner?.ruleType)
    }

    @Test
    fun `disabled rule and garbage mode string both produce zero occurrences without throwing`() {
        val day = LocalDate.of(2026, 6, 15)
        val disabled = manualRule(1, "09:00", "10:00", "SILENT").copy(enabled = false)
        val garbageMode = manualRule(2, "09:00", "10:00", "LOUD_AND_PROUD")
        assertEquals(0, engine.expandOccurrences(disabled, RuleType.MANUAL_SCHEDULE, day, day).size)
        assertEquals(0, engine.expandOccurrences(garbageMode, RuleType.MANUAL_SCHEDULE, day, day).size)
    }

    @Test
    fun `calendar priority beats bedtime even when bedtime started earlier`() {
        val day = LocalDate.of(2026, 6, 15)
        val bedtime = engine.expandOccurrences(manualRule(1, "20:00", "23:59", "SILENT"), RuleType.BEDTIME, day, day)
        val calendarMatch = CalendarOccurrence(
            calendarRuleId = 2, revision = 1,
            start = day.atTime(21, 0).toInstant(ZoneOffset.UTC), end = day.atTime(22, 0).toInstant(ZoneOffset.UTC),
            mode = TargetRingerMode.VIBRATE, exceptionProfileId = null
        )
        val winner = engine.resolveWinner(day.atTime(21, 30).toInstant(ZoneOffset.UTC), null, listOf(calendarMatch), emptyList(), bedtime)
        assertEquals(RuleType.CALENDAR_EVENT, winner?.ruleType)
    }

    @Test
    fun `two overlapping calendar matches tie-break on revision like any other rule type`() {
        val day = LocalDate.of(2026, 6, 15)
        val older = CalendarOccurrence(10, revision = 1, start = day.atTime(9, 0).toInstant(ZoneOffset.UTC), end = day.atTime(11, 0).toInstant(ZoneOffset.UTC), mode = TargetRingerMode.SILENT, exceptionProfileId = null)
        val newer = CalendarOccurrence(11, revision = 9, start = day.atTime(9, 0).toInstant(ZoneOffset.UTC), end = day.atTime(11, 0).toInstant(ZoneOffset.UTC), mode = TargetRingerMode.VIBRATE, exceptionProfileId = null)
        val winner = engine.resolveWinner(day.atTime(10, 0).toInstant(ZoneOffset.UTC), null, listOf(older, newer), emptyList(), emptyList())
        assertEquals(11L, winner?.ruleId)
    }
}

