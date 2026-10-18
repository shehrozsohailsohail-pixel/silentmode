package com.smartsilent.scheduler.core.policy

import com.smartsilent.scheduler.core.rules.RuleType
import java.time.Instant

/** Framework Section 5. UNAVAILABLE must never be reported to the user as
 * successfully applied (Section 40, "No False Success"). */
enum class PolicyType {
    NORMAL,
    VIBRATE,
    TOTAL_SILENT,
    DND_PRIORITY,               // reserved for exception-profile-aware rules -
    SYSTEM_MANAGED_EXCEPTION,   // PolicyResolver does not produce these yet; see its doc
    NO_AUTOMATION,
    UNAVAILABLE
}

enum class TargetRingerMode { NORMAL, VIBRATE, SILENT }

/** Framework Section 5 - the engine's actual output; nothing downstream acts
 * on a bare ringer mode without going through this. */
data class EffectivePolicy(
    val policyType: PolicyType,
    val targetRingerMode: TargetRingerMode?,
    val dndStrategy: String?,
    val exceptionProfileId: Long?,
    val winningRuleId: Long?,
    val winningRuleType: RuleType,
    val effectiveUntil: Instant?,
    val reason: String
)

/** Result of actually applying an EffectivePolicy to the OS - separate from
 * EffectivePolicy itself so "what we decided" and "what actually happened
 * when we tried" can never be conflated (Section 40). */
data class PolicyApplicationResult(
    val succeeded: Boolean,
    val appliedPolicy: EffectivePolicy,
    val failureReason: String? = null
)
