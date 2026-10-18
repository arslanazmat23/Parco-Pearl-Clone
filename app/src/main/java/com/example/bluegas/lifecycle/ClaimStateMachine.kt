package com.example.bluegas.lifecycle

import com.example.bluegas.model.*

object ClaimStateMachine {

    val allowedClaimTransitions: Map<ClaimStatus, Set<ClaimStatus>> = mapOf(
        ClaimStatus.DRAFT to setOf(ClaimStatus.EVIDENCE_SUBMITTED, ClaimStatus.CANCELLED),
        ClaimStatus.EVIDENCE_SUBMITTED to setOf(ClaimStatus.ELIGIBILITY_ASSESSED, ClaimStatus.CANCELLED),
        ClaimStatus.ELIGIBILITY_ASSESSED to setOf(ClaimStatus.PROVISIONALLY_ELIGIBLE, ClaimStatus.INELIGIBLE, ClaimStatus.MANUAL_REVIEW, ClaimStatus.CANCELLED),
        ClaimStatus.MANUAL_REVIEW to setOf(ClaimStatus.PROVISIONALLY_ELIGIBLE, ClaimStatus.INELIGIBLE, ClaimStatus.CANCELLED),
        ClaimStatus.PROVISIONALLY_ELIGIBLE to setOf(ClaimStatus.PROVISIONAL_OFFER_ISSUED, ClaimStatus.CANCELLED),
        ClaimStatus.PROVISIONAL_OFFER_ISSUED to setOf(ClaimStatus.CUSTOMER_CONFIRMED, ClaimStatus.CANCELLED),
        ClaimStatus.CUSTOMER_CONFIRMED to setOf(ClaimStatus.SCHEDULED_FOR_HANDOVER, ClaimStatus.CANCELLED),
        ClaimStatus.SCHEDULED_FOR_HANDOVER to setOf(ClaimStatus.AT_HANDOVER_INSPECTION, ClaimStatus.CANCELLED),
        ClaimStatus.AT_HANDOVER_INSPECTION to setOf(ClaimStatus.PHYSICALLY_ACCEPTED, ClaimStatus.PHYSICALLY_REJECTED, ClaimStatus.CANCELLED),
        ClaimStatus.PHYSICALLY_ACCEPTED to setOf(ClaimStatus.HSE_ASSESSMENT),
        ClaimStatus.HSE_ASSESSMENT to setOf(ClaimStatus.HSE_CLEARED, ClaimStatus.HSE_HOLD_UNSAFE),
        ClaimStatus.HSE_CLEARED to setOf(ClaimStatus.FINAL_OFFER_CONFIRMED),
        ClaimStatus.HSE_HOLD_UNSAFE to setOf(ClaimStatus.CLOSED),
        ClaimStatus.FINAL_OFFER_CONFIRMED to setOf(ClaimStatus.EXCHANGE_COMPLETED, ClaimStatus.CANCELLED),
        ClaimStatus.EXCHANGE_COMPLETED to setOf(ClaimStatus.CLOSED, ClaimStatus.REVERSED),
        ClaimStatus.PHYSICALLY_REJECTED to setOf(ClaimStatus.CLOSED),
        ClaimStatus.INELIGIBLE to setOf(ClaimStatus.CLOSED),
        ClaimStatus.CLOSED to emptySet(),
        ClaimStatus.CANCELLED to emptySet(),
        ClaimStatus.REVERSED to emptySet()
    )

    fun transition(
        claim: ReplacementClaim,
        target: ClaimStatus,
        actor: String,
        role: AccountableRole,
        reason: String
    ): Pair<Boolean, String> {
        val current = claim.status
        if (current == ClaimStatus.CLOSED || current == ClaimStatus.CANCELLED || current == ClaimStatus.REVERSED) {
            return Pair(false, "Claim ${claim.id} is in terminal status $current.")
        }
        val allowed = allowedClaimTransitions[current] ?: emptySet()
        if (!allowed.contains(target)) {
            return Pair(false, "Invalid claim transition $current -> $target. Legal transitions: ${allowed.joinToString()}")
        }

        claim.status = target
        claim.history.add(
            ClaimHistory(
                fromStatus = current,
                toStatus = target,
                actor = actor,
                role = role,
                reason = reason
            )
        )
        return Pair(true, "Claim advanced to $target")
    }
}
