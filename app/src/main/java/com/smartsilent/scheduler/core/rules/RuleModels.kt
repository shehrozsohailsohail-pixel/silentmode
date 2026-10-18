package com.smartsilent.scheduler.core.rules

import com.smartsilent.scheduler.core.policy.TargetRingerMode
import java.time.Instant

/**
 * Framework Section 4 - default priority, HIGHEST FIRST. Enum ordinal order
 * IS the priority order end to end (ConflictResolver sorts on .ordinal
 * directly) - never re-derive or duplicate this ordering elsewhere.
 */
enum class RuleType {
    TEMPORARY_OVERRIDE,
    CALENDAR_EVENT,
    MANUAL_SCHEDULE,
    BEDTIME,
    NONE
}

/**
 * One resolved occurrence of a rule, already converted to absolute instants
 * for a specific calendar day, in the rule's own timezone. Nothing above
 * this layer ever compares raw rule rows against each other - framework
 * Section 35.
 */
data class RuleOccurrence(
    val ruleType: RuleType,
    val ruleId: Long,
    val revision: Long,
    val start: Instant,
    val end: Instant,               // half-open: active on [start, end) - Section 35
    val mode: TargetRingerMode,
    val exceptionProfileId: Long?
)
