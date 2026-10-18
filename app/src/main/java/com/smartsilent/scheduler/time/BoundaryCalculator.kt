package com.smartsilent.scheduler.time

import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Parses a stored "HH:mm" string safely - never throws. */
object LocalTimeResolver {
    fun parseOrNull(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()
}

/** Resolves a stored timezone ID safely, falling back to the device's zone
 * rather than crashing on a corrupt/unknown ID (e.g. after a restore from
 * an export created on a different Android build). */
object TimezoneResolver {
    fun resolveOrDefault(timezoneId: String): ZoneId =
        runCatching { ZoneId.of(timezoneId) }.getOrDefault(ZoneId.systemDefault())
}

/**
 * Framework Section 18 - DST gap/fold handling. `ZonedDateTime.of(date, time,
 * zone)` already pushes a nonexistent local time (spring-forward gap)
 * forward to the first valid instant, and resolves a repeated local time
 * (fall-back fold) to the earlier of the two offsets - both match Section
 * 18's intent by construction. This object exists so that behavior is named
 * and tested (see RuleEngineTest's DST cases) rather than an unexplained
 * side effect of calling the JDK API directly inline elsewhere.
 */
object DstResolver {
    fun resolveLocal(date: LocalDate, time: LocalTime, zone: ZoneId): ZonedDateTime =
        ZonedDateTime.of(date, time, zone)
}

/**
 * Framework Section 3.1 - given a start/end time-of-day and a single
 * calendar date, produces the absolute [start, end) instants, expanding an
 * overnight span (end <= start) into the following local date.
 */
object BoundaryCalculator {
    fun computeBoundary(
        date: LocalDate,
        startTime: LocalTime,
        endTime: LocalTime,
        zone: ZoneId
    ): Pair<Instant, Instant> {
        val overnight = !endTime.isAfter(startTime)
        val start = DstResolver.resolveLocal(date, startTime, zone)
        val endDate = if (overnight) date.plusDays(1) else date
        val end = DstResolver.resolveLocal(endDate, endTime, zone)
        return start.toInstant() to end.toInstant()
    }
}
