package com.smartsilent.scheduler.calendar

import com.smartsilent.scheduler.core.policy.TargetRingerMode
import java.time.Instant

/** One raw row from CalendarContract.Instances - not yet matched against any rule. */
data class CalendarEvent(
    val eventId: Long,
    val calendarId: Long,
    val title: String,
    val beginMillis: Long,
    val endMillis: Long
)

/** One calendar_rules row's event match, already resolved to instants -
 * this is what RuleEngine actually compares against [now], mirroring
 * RuleOccurrence's shape for the calendar case (framework Section 19). */
data class CalendarOccurrence(
    val calendarRuleId: Long,
    val revision: Long,
    val start: Instant,
    val end: Instant,
    val mode: TargetRingerMode,
    val exceptionProfileId: Long?
)
