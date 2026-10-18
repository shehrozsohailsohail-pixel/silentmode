package com.smartsilent.scheduler.scheduling

import com.smartsilent.scheduler.core.rules.RuleOccurrence
import java.time.Instant

/**
 * Framework Section 11 - the single next boundary the Scheduler should wake
 * up for: the earliest start or end, among all supplied occurrences, that is
 * strictly after [now]. Pure function, no Android dependency - kept separate
 * from RuleEngine per the adopted package structure, though the two are
 * always used together by ReconciliationEngine.
 */
object NextBoundaryCalculator {
    fun next(now: Instant, occurrences: List<RuleOccurrence>): Instant? {
        val candidates = mutableListOf<Instant>()
        for (occurrence in occurrences) {
            if (occurrence.start > now) candidates += occurrence.start
            if (occurrence.end > now) candidates += occurrence.end
        }
        return candidates.minOrNull()
    }
}
