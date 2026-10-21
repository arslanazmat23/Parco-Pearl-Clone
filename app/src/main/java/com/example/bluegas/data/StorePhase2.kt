package com.example.bluegas.data

import com.example.bluegas.lifecycle.ClaimStateMachine
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Phase 2 Preview Store Operations (Claim & Troubleshooting gates)
 */

fun BlueGasStore.assessEligibility(
    claimId: String,
    eligible: Boolean,
    reason: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    if (claim.status == ClaimStatus.EVIDENCE_SUBMITTED) {
        ClaimStateMachine.transition(claim, ClaimStatus.ELIGIBILITY_ASSESSED, "Operations Lead", AccountableRole.OPERATIONS, "Eligibility check started")
    }

    val target = if (eligible) ClaimStatus.PROVISIONALLY_ELIGIBLE else ClaimStatus.INELIGIBLE
    val (ok, msg) = ClaimStateMachine.transition(claim, target, "Operations Lead", AccountableRole.OPERATIONS, reason)
    if (!ok) return Pair(false, msg)

    if (!eligible) {
        ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, "Operations Lead", AccountableRole.OPERATIONS, "Ineligible claim closed")
        claim.closureReason = reason
    }

    logAudit("ReplacementClaim", claimId, "CTL-ELIG", "Operations Lead", AccountableRole.OPERATIONS, "Eligible: $eligible - $reason")
    notifyChanged()
    return Pair(true, "Eligibility assessed: ${claim.status}")
}

fun BlueGasStore.issueProvisionalOffer(claimId: String): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val offerAmount = settings.phase2DiscountValue
    claim.provisionalOfferPaisa = offerAmount

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.PROVISIONAL_OFFER_ISSUED,
        actor = "Commercial Lead",
        role = AccountableRole.COMMERCIAL,
        reason = if (offerAmount > 0) "Provisional discount offer: ${formatPaisaToPkr(offerAmount)}" else "Rs 0 provisional offer (Benefit Q01/Q02 client decision pending)"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "ISSUE_PROVISIONAL_OFFER", "Commercial Lead", AccountableRole.COMMERCIAL, "Offer: ${formatPaisaToPkr(offerAmount)}")
    notifyChanged()
    return Pair(true, "Provisional offer of ${formatPaisaToPkr(offerAmount)} issued.")
}

fun BlueGasStore.startInspection(
    claimId: String,
    inspectorName: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    claim.inspectorName = inspectorName

    if (claim.status == ClaimStatus.PROVISIONAL_OFFER_ISSUED) {
        ClaimStateMachine.transition(claim, ClaimStatus.CUSTOMER_CONFIRMED, "Customer", AccountableRole.OPERATIONS, "Customer accepted offer")
    }
    if (claim.status == ClaimStatus.CUSTOMER_CONFIRMED) {
        ClaimStateMachine.transition(claim, ClaimStatus.SCHEDULED_FOR_HANDOVER, "Operations Lead", AccountableRole.OPERATIONS, "Scheduled for inspection")
    }

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.AT_HANDOVER_INSPECTION,
        actor = inspectorName,
        role = AccountableRole.INSPECTOR,
        reason = "Physical inspection started by $inspectorName"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "START_INSPECTION", inspectorName, AccountableRole.INSPECTOR, "Handover inspection initiated")
    notifyChanged()
    return Pair(true, "Inspection started by $inspectorName.")
}

fun BlueGasStore.recordPhysicalResult(
    claimId: String,
    accepted: Boolean,
    reason: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val target = if (accepted) ClaimStatus.PHYSICALLY_ACCEPTED else ClaimStatus.PHYSICALLY_REJECTED
    claim.physicalResult = if (accepted) "ACCEPTED" else "REJECTED"

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = target,
        actor = claim.inspectorName ?: "Authorized Inspector",
        role = AccountableRole.INSPECTOR,
        reason = reason
    )
    if (!ok) return Pair(false, msg)

    if (!accepted) {
        ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, claim.inspectorName ?: "Authorized Inspector", AccountableRole.INSPECTOR, "Physical inspection failed: $reason")
        claim.closureReason = reason
    }

    logAudit("ReplacementClaim", claimId, "CTL-PACC", claim.inspectorName ?: "Authorized Inspector", AccountableRole.INSPECTOR, "Accepted: $accepted - $reason")
    notifyChanged()
    return Pair(true, "Physical result recorded: ${claim.status}")
}

fun BlueGasStore.recordTestDatePolicy(
    claimId: String,
    accept: Boolean
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    claim.testDatePolicyDecision = if (accept) "ACCEPTED_UNDER_POLICY" else "REJECTED_UNDER_POLICY"
    logAudit("ReplacementClaim", claimId, "EXC-12_POLICY_DECISION", "Operations Lead", AccountableRole.OPERATIONS, "Test date policy decision: ${claim.testDatePolicyDecision}")
    notifyChanged()
    return Pair(true, "Test date decision recorded: ${claim.testDatePolicyDecision}")
}

fun BlueGasStore.startHseAssessment(claimId: String): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.HSE_ASSESSMENT,
        actor = "HSE Officer",
        role = AccountableRole.HSE,
        reason = "HSE integrity assessment started"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "START_HSE", "HSE Officer", AccountableRole.HSE, "HSE assessment underway")
    notifyChanged()
    return Pair(true, "HSE assessment started.")
}

fun BlueGasStore.recordHseResult(
    claimId: String,
    cleared: Boolean,
    notes: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    claim.hseResult = if (cleared) "CLEARED" else "UNSAFE"
    val target = if (cleared) ClaimStatus.HSE_CLEARED else ClaimStatus.HSE_HOLD_UNSAFE

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = target,
        actor = "HSE Officer",
        role = AccountableRole.HSE,
        reason = notes
    )
    if (!ok) return Pair(false, msg)

    if (!cleared) {
        raiseIncident(
            source = "CLAIM",
            description = "Surrendered cylinder unsafe during replacement inspection for claim $claimId: $notes",
            location = "Handover staging depot",
            peopleInvolved = "Inspector & HSE Officer",
            actor = "HSE Officer"
        )
        ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, "HSE Officer", AccountableRole.HSE, "Unsafe cylinder condemned")
        claim.closureReason = "Unsafe cylinder: $notes"
    }

    logAudit("ReplacementClaim", claimId, "CTL-HSE", "HSE Officer", AccountableRole.HSE, "HSE cleared: $cleared - $notes")
    notifyChanged()
    return Pair(true, "HSE result recorded: ${claim.status}")
}

fun BlueGasStore.confirmFinalOffer(
    claimId: String,
    amountPaisa: Long
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    claim.finalOfferPaisa = amountPaisa

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.FINAL_OFFER_CONFIRMED,
        actor = "Commercial Lead",
        role = AccountableRole.COMMERCIAL,
        reason = "Final settlement offer: ${formatPaisaToPkr(amountPaisa)}"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "CONFIRM_FINAL_OFFER", "Commercial Lead", AccountableRole.COMMERCIAL, "Final amount: ${formatPaisaToPkr(amountPaisa)}")
    notifyChanged()
    return Pair(true, "Final offer of ${formatPaisaToPkr(amountPaisa)} confirmed.")
}

fun BlueGasStore.authorizeDisposition(
    surrenderId: String,
    type: String,
    authorizedBy: String
): Pair<Boolean, String> {
    val sc = surrenderedCylinders.find { it.id == surrenderId }
        ?: return Pair(false, "Surrendered asset $surrenderId not found.")

    sc.dispositionAuthorizedBy = authorizedBy
    sc.dispositionType = type
    sc.status = SurrenderStatus.DISPOSITION_AUTHORIZED

    sc.history.add(
        SurrenderHistory(
            fromStatus = SurrenderStatus.COLLECTED,
            toStatus = SurrenderStatus.DISPOSITION_AUTHORIZED,
            actor = authorizedBy,
            notes = "Disposition type: $type"
        )
    )

    logAudit("SurrenderedCylinder", surrenderId, "CTL-DISP", authorizedBy, AccountableRole.LEGAL, "Authorized disposition: $type")
    notifyChanged()
    return Pair(true, "Disposition authorized by $authorizedBy: $type.")
}

fun BlueGasStore.reverseExchange(
    claimId: String,
    approver: String,
    reason: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.REVERSED,
        actor = approver,
        role = AccountableRole.FINANCE,
        reason = reason
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "REVERSE_EXCHANGE", approver, AccountableRole.FINANCE, "Reversed: $reason (Physical return is a separate custody action)")
    notifyChanged()
    return Pair(true, "Exchange reversed by $approver. Note: Physical return is a separate custody action.")
}

fun BlueGasStore.scheduleTroubleshooting(
    id: String,
    technician: String
): Pair<Boolean, String> {
    val req = troubleshootingRequests.find { it.id == id }
        ?: return Pair(false, "Troubleshooting request $id not found.")

    req.status = "SCHEDULED"
    req.technicianName = technician
    logAudit("TroubleshootingRequest", id, "SCHEDULE", technician, AccountableRole.TECHNICIAN, "Assigned to $technician")
    notifyChanged()
    return Pair(true, "Technician $technician scheduled for $id.")
}

fun BlueGasStore.resolveTroubleshooting(
    id: String,
    notes: String
): Pair<Boolean, String> {
    val req = troubleshootingRequests.find { it.id == id }
        ?: return Pair(false, "Troubleshooting request $id not found.")

    req.status = "RESOLVED"
    logAudit("TroubleshootingRequest", id, "RESOLVE", req.technicianName ?: "Technician", AccountableRole.TECHNICIAN, notes)
    notifyChanged()
    return Pair(true, "Troubleshooting request $id resolved.")
}

fun BlueGasStore.troubleshootingToIncident(
    id: String,
    description: String
): Pair<Boolean, String> {
    val req = troubleshootingRequests.find { it.id == id }
        ?: return Pair(false, "Troubleshooting request $id not found.")

    req.status = "SAFETY_INCIDENT_OPENED"
    raiseIncident(
        source = "TROUBLESHOOTING",
        description = "Escalated from visit $id: $description",
        orderId = req.orderId,
        actor = req.technicianName ?: "Technician"
    )
    notifyChanged()
    return Pair(true, "Safety incident opened from troubleshooting request $id.")
}

fun BlueGasStore.completeInspection(
    claimId: String,
    passed: Boolean,
    notes: String,
    measuredWeightKg: Double = 15.2,
    valveIntact: Boolean = true,
    tareWeightReadable: Boolean = true
): Pair<Boolean, String> = recordPhysicalResult(claimId, passed, notes)

fun BlueGasStore.issueSettlementPayout(claimId: String): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId } ?: return Pair(false, "Claim not found")
    ClaimStateMachine.transition(claim, ClaimStatus.EXCHANGE_COMPLETED, "Finance Lead", AccountableRole.FINANCE, "Settlement benefit applied and completed")
    ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, "Finance Lead", AccountableRole.FINANCE, "Claim settled and closed")
    logAudit("ReplacementClaim", claimId, "SETTLEMENT_PAYOUT", "Finance Lead", AccountableRole.FINANCE, "Settlement payout executed")
    notifyChanged()
    return Pair(true, "Settlement completed for claim $claimId.")
}

fun BlueGasStore.authorizeSurrenderDisposition(
    surrenderId: String,
    type: String,
    authorizedBy: String
): Pair<Boolean, String> = authorizeDisposition(surrenderId, type, authorizedBy)

fun BlueGasStore.recordTroubleshooting(
    customerId: String,
    issueType: String,
    outcome: String,
    notes: String,
    actor: String = "Self-Service"
): Pair<Boolean, String> {
    val req = TroubleshootingRequest(
        id = "TRB-" + UUID.randomUUID().toString().take(6),
        customerId = customerId,
        channel = issueType,
        description = notes,
        status = outcome
    )
    troubleshootingRequests.add(req)
    logAudit("TroubleshootingRequest", req.id, "RECORD_TROUBLESHOOTING", actor, AccountableRole.TECHNICIAN, "$issueType: $notes")
    notifyChanged()
    return Pair(true, "Troubleshooting diagnostic recorded for $customerId.")
}
