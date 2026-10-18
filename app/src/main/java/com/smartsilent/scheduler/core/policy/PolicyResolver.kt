package com.smartsilent.scheduler.core.policy

import com.smartsilent.scheduler.core.rules.RuleOccurrence
import com.smartsilent.scheduler.core.rules.RuleType

/**
 * Framework Section 5 (translate a winner into EffectivePolicy) + Sections
 * 27/40 (never report a policy the platform can't actually deliver right
 * now as though it were in effect). Deliberately takes a plain Boolean
 * rather than the Android CapabilityManager class, so this stays a pure,
 * platform-free unit like RuleEngine.
 */
object PolicyResolver {

    fun resolve(winner: RuleOccurrence?, canApplyRingerPolicy: Boolean): EffectivePolicy {
        val base = toEffectivePolicy(winner)
        if (base.policyType == PolicyType.NO_AUTOMATION) return base
        return if (!canApplyRingerPolicy) {
            base.copy(policyType = PolicyType.UNAVAILABLE, reason = "DND/notification policy access is not granted")
        } else {
            base
        }
    }

    private fun toEffectivePolicy(winner: RuleOccurrence?): EffectivePolicy {
        if (winner == null) {
            return EffectivePolicy(
                policyType = PolicyType.NO_AUTOMATION,
                targetRingerMode = null,
                dndStrategy = null,
                exceptionProfileId = null,
                winningRuleId = null,
                winningRuleType = RuleType.NONE,
                effectiveUntil = null,
                reason = "No active rule at reconciliation time"
            )
        }
        val policyType = when (winner.mode) {
            TargetRingerMode.NORMAL -> PolicyType.NORMAL
            TargetRingerMode.VIBRATE -> PolicyType.VIBRATE
            TargetRingerMode.SILENT -> PolicyType.TOTAL_SILENT
        }
        return EffectivePolicy(
            policyType = policyType,
            targetRingerMode = winner.mode,
            dndStrategy = null,
            exceptionProfileId = winner.exceptionProfileId,
            winningRuleId = winner.ruleId,
            winningRuleType = winner.ruleType,
            effectiveUntil = winner.end,
            reason = "${winner.ruleType} #${winner.ruleId} is the highest-priority active rule"
        )
    }
}

/** A couple of cheap sanity invariants, useful in tests and as a guard
 * before logging - not a full schema validator. */
object PolicyValidator {
    fun isInternallyConsistent(policy: EffectivePolicy): Boolean = when (policy.policyType) {
        PolicyType.NO_AUTOMATION, PolicyType.UNAVAILABLE -> true // no ringer-mode constraint either way
        else -> policy.targetRingerMode != null
    }
}
