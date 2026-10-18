package com.smartsilent.scheduler.time

import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import javax.inject.Inject

/**
 * Framework v7.0 Section 61: every "now" in this app goes through this
 * interface - no class calls System.currentTimeMillis() / Instant.now() /
 * LocalDateTime.now() directly. That discipline is what makes the DST/
 * midnight/timezone rows in Section 52's test matrix runnable as plain, fast
 * JVM unit tests instead of only reachable by manipulating a real device.
 */
interface TimeManager {
    fun nowInstant(): Instant
    fun zoneId(): ZoneId
    fun nowZoned(): ZonedDateTime = ZonedDateTime.ofInstant(nowInstant(), zoneId())
}

/** Real implementation, backed by the device's actual clock and timezone. */
class SystemTimeManager @Inject constructor() : TimeManager {
    override fun nowInstant(): Instant = Instant.now()
    override fun zoneId(): ZoneId = ZoneId.systemDefault()
}

/** Deterministic test double - drives time explicitly for overnight/DST/midnight tests. */
class MutableTimeManager(
    private var instant: Instant,
    private var zone: ZoneId = ZoneId.of("UTC")
) : TimeManager {
    override fun nowInstant(): Instant = instant
    override fun zoneId(): ZoneId = zone
    fun setInstant(newInstant: Instant) { instant = newInstant }
    fun setZone(newZone: ZoneId) { zone = newZone }
    fun advanceBy(seconds: Long) { instant = instant.plusSeconds(seconds) }
}
