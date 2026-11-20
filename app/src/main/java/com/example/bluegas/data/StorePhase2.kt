package com.example.bluegas.data

import com.example.bluegas.lifecycle.ClaimStateMachine
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Phase 2 Preview Store Operations (Switch & Save Route A/B, inspection gates, and disposition).
 */

/**
 * Plain-language customer and staff description for a Switch & Save claim's selected route.
 */
fun claimRouteDescription(claim: ReplacementClaim): String {
    return if (claim.route == "A") {
        "You keep your old ${claim.declaredBrand} cylinder and return it to ${claim.declaredBrand} to claim your old deposit. BLUE GAS does not collect it."
    } else {
        "BLUE GAS collects your old cylinder at the door after inspection and safety checks."
    }
}

private fun BlueGasStore.isRouteBPossible(claim: ReplacementClaim): Boolean =
    claim.declaredBrand in settings.rivalReturnAgreements || claim.ownershipProofProvided

fun BlueGasStore.assessEligibility(
    claimId: String,
    eligible: Boolean,
    reason: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val opsActor = AccountableRole.OPERATIONS.displayName
    if (claim.status == ClaimStatus.EVIDENCE_SUBMITTED) {
        ClaimStateMachine.transition(claim, ClaimStatus.ELIGIBILITY_ASSESSED, opsActor, AccountableRole.OPERATIONS, "Eligibility check started")
    }

    val routeBOk = isRouteBPossible(claim)
    val effectiveEligible = if (eligible && !routeBOk && !settings.switchSaveRouteAEnabled) false else eligible
    val effectiveReason = if (eligible && !effectiveEligible) {
        "No return agreement with ${claim.declaredBrand}"
    } else {
        reason
    }

    if (effectiveEligible) {
        claim.route = if (routeBOk) "B" else "A"
    }

    val target = if (effectiveEligible) ClaimStatus.PROVISIONALLY_ELIGIBLE else ClaimStatus.INELIGIBLE
    val (ok, msg) = ClaimStateMachine.transition(claim, target, opsActor, AccountableRole.OPERATIONS, effectiveReason)
    if (!ok) return Pair(false, msg)

    if (!effectiveEligible) {
        ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, opsActor, AccountableRole.OPERATIONS, effectiveReason)
        claim.outcomeCode = "INELIGIBLE"
        claim.closureReason = effectiveReason
    }

    logAudit("ReplacementClaim", claimId, "CTL-ELIG", opsActor, AccountableRole.OPERATIONS, "Eligible: $effectiveEligible - $effectiveReason")
    notifyChanged()
    return if (effectiveEligible) {
        Pair(true, "Eligibility assessed: ${claim.status} (Route ${claim.route})")
    } else {
        Pair(true, "Eligibility assessed: ${claim.status} ($effectiveReason)")
    }
}

fun BlueGasStore.issueProvisionalOffer(
    claimId: String,
    route: String? = null
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val routeBOk = isRouteBPossible(claim)
    val chosenRoute = (route ?: if (routeBOk) "B" else "A").trim().uppercase()
    if (chosenRoute !in setOf("A", "B")) {
        return Pair(false, "Route must be A or B.")
    }
    if (chosenRoute == "B" && !routeBOk) {
        return Pair(false, "Route B is not possible without a return agreement with ${claim.declaredBrand} or ownership proof.")
    }
    if (chosenRoute == "A" && !settings.switchSaveRouteAEnabled) {
        return Pair(false, "Route A is currently disabled in settings.")
    }

    claim.route = chosenRoute
    val offerAmount = settings.phase2DiscountValue
    claim.provisionalOfferPaisa = offerAmount

    val commActor = AccountableRole.COMMERCIAL.displayName
    val routeText = claimRouteDescription(claim)
    val offerSummary = if (offerAmount > 0) {
        "Provisional discount offer: ${formatPaisaToPkr(offerAmount)}"
    } else {
        "Rs 0 provisional offer (Benefit Q01/Q02 client decision pending)"
    }

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.PROVISIONAL_OFFER_ISSUED,
        actor = commActor,
        role = AccountableRole.COMMERCIAL,
        reason = "$offerSummary — Route $chosenRoute: $routeText"
    )
    if (!ok) return Pair(false, msg)

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Switch & Save offer ready",
        message = "$offerSummary. $routeText",
        canonicalStatus = claim.status.name,
        orderId = claim.linkedOrderId,
        code = "NTF-10"
    )

    logAudit(
        "ReplacementClaim",
        claimId,
        "ISSUE_PROVISIONAL_OFFER",
        commActor,
        AccountableRole.COMMERCIAL,
        "Route $chosenRoute · Offer: ${formatPaisaToPkr(offerAmount)}"
    )
    notifyChanged()
    return Pair(true, "$offerSummary issued (Route $chosenRoute). $routeText")
}

/**
 * Customer confirms the provisional offer and schedules the linked replacement cylinder order
 * (`NEW_CYLINDER`) with `discountPaisa` applied to the gas/fees subtotal (deposit stays full).
 */
fun BlueGasStore.scheduleClaimHandover(
    claimId: String,
    addressId: String,
    paymentMethod: PaymentMethod,
    actor: String = AccountableRole.OPERATIONS.displayName
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")
    if (claim.status != ClaimStatus.PROVISIONAL_OFFER_ISSUED) {
        return Pair(false, "Claim $claimId must be at PROVISIONAL_OFFER_ISSUED before scheduling handover.")
    }

    val sku = if (claim.declaredSize.contains("45.4")) "SKU-LPG-45-4" else "SKU-LPG-11-8"
    val discount = claim.provisionalOfferPaisa.coerceAtLeast(0L)
    val (createdOrder, orderErr) = createOrder(
        customerId = claim.customerId,
        addressId = addressId,
        fulfilmentMode = FulfilmentMode.DELIVERY,
        sku = sku,
        quantity = 1,
        includeDeposit = true,
        paymentMethod = paymentMethod,
        orderType = OrderType.NEW_CYLINDER,
        discountPaisa = discount
    )
    if (createdOrder == null) {
        return Pair(false, orderErr ?: "Could not create replacement order.")
    }

    val (custOk, custMsg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.CUSTOMER_CONFIRMED,
        actor = "Customer",
        role = AccountableRole.COMMERCIAL,
        reason = "Customer confirmed Route ${claim.route} replacement offer"
    )
    if (!custOk) return Pair(false, custMsg)

    val (schedOk, schedMsg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.SCHEDULED_FOR_HANDOVER,
        actor = actor,
        role = AccountableRole.OPERATIONS,
        reason = "Linked replacement order ${createdOrder.id} created (discount ${formatPaisaToPkr(discount)})"
    )
    if (!schedOk) return Pair(false, schedMsg)

    claim.linkedOrderId = createdOrder.id

    logAudit(
        "ReplacementClaim",
        claimId,
        "SCHEDULE_CLAIM_HANDOVER",
        actor,
        AccountableRole.OPERATIONS,
        "Route ${claim.route} scheduled with order ${createdOrder.id}"
    )
    notifyChanged()
    return Pair(true, "Replacement order ${createdOrder.id} created and claim $claimId scheduled.")
}

fun BlueGasStore.startInspection(
    claimId: String,
    inspectorName: String
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    if (claim.route == "A") {
        return Pair(false, "Route A has no BLUE GAS inspection or custody.")
    }

    claim.inspectorName = inspectorName

    if (claim.status == ClaimStatus.PROVISIONAL_OFFER_ISSUED) {
        ClaimStateMachine.transition(claim, ClaimStatus.CUSTOMER_CONFIRMED, "Customer", AccountableRole.OPERATIONS, "Customer accepted offer")
    }
    if (claim.status == ClaimStatus.CUSTOMER_CONFIRMED) {
        ClaimStateMachine.transition(claim, ClaimStatus.SCHEDULED_FOR_HANDOVER, AccountableRole.OPERATIONS.displayName, AccountableRole.OPERATIONS, "Scheduled for inspection")
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
    logAudit("ReplacementClaim", claimId, "EXC-12_POLICY_DECISION", AccountableRole.OPERATIONS.displayName, AccountableRole.OPERATIONS, "Test date policy decision: ${claim.testDatePolicyDecision}")
    notifyChanged()
    return Pair(true, "Test date decision recorded: ${claim.testDatePolicyDecision}")
}

fun BlueGasStore.startHseAssessment(claimId: String): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    val (ok, msg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.HSE_ASSESSMENT,
        actor = AccountableRole.HSE.displayName,
        role = AccountableRole.HSE,
        reason = "HSE integrity assessment started"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "START_HSE", AccountableRole.HSE.displayName, AccountableRole.HSE, "HSE assessment underway")
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
        actor = AccountableRole.HSE.displayName,
        role = AccountableRole.HSE,
        reason = notes
    )
    if (!ok) return Pair(false, msg)

    if (!cleared) {
        raiseIncident(
            source = "CLAIM",
            description = "Surrendered cylinder unsafe during replacement inspection for claim $claimId: $notes",
            location = "Handover staging hub",
            peopleInvolved = "Inspector & HSE Officer",
            actor = AccountableRole.HSE.displayName
        )
        ClaimStateMachine.transition(claim, ClaimStatus.CLOSED, AccountableRole.HSE.displayName, AccountableRole.HSE, "Unsafe cylinder condemned")
        claim.closureReason = "Unsafe cylinder: $notes"
    }

    logAudit("ReplacementClaim", claimId, "CTL-HSE", AccountableRole.HSE.displayName, AccountableRole.HSE, "HSE cleared: $cleared - $notes")
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
        actor = AccountableRole.COMMERCIAL.displayName,
        role = AccountableRole.COMMERCIAL,
        reason = "Final Switch & Save offer: ${formatPaisaToPkr(amountPaisa)}"
    )
    if (!ok) return Pair(false, msg)

    logAudit("ReplacementClaim", claimId, "CONFIRM_FINAL_OFFER", AccountableRole.COMMERCIAL.displayName, AccountableRole.COMMERCIAL, "Final amount: ${formatPaisaToPkr(amountPaisa)}")
    notifyChanged()
    return Pair(true, "Final offer of ${formatPaisaToPkr(amountPaisa)} confirmed.")
}

/**
 * Completes a Route B Switch & Save exchange after all inspection, HSE, final-offer,
 * and linked-order handover gates have cleared, closing the claim and recording the surrendered cylinder.
 */
fun BlueGasStore.completeExchange(
    claimId: String,
    actor: String = AccountableRole.OPERATIONS.displayName
): Pair<Boolean, String> {
    val claim = replacementClaims.find { it.id == claimId }
        ?: return Pair(false, "Claim $claimId not found.")

    if (claim.route == "A") {
        return Pair(false, "Route A claims close automatically when the replacement order completes.")
    }
    if (claim.status != ClaimStatus.FINAL_OFFER_CONFIRMED) {
        return Pair(false, "Claim $claimId must be at FINAL_OFFER_CONFIRMED before completing exchange (currently ${claim.status}).")
    }
    if (claim.physicalResult != "ACCEPTED") {
        return Pair(false, "Physical inspection must be ACCEPTED before completing exchange.")
    }
    if (claim.hseResult != "CLEARED") {
        return Pair(false, "HSE check must be CLEARED before completing exchange.")
    }
    val linkedOrder = claim.linkedOrderId?.let { oid -> orders.find { it.id == oid } }
    if (linkedOrder == null || (linkedOrder.status != OrderStatus.ORD_HOC && linkedOrder.status != OrderStatus.ORD_COMP)) {
        return Pair(false, "Linked replacement order must be Handover Complete or Completed before completing exchange.")
    }

    val (exOk, exMsg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.EXCHANGE_COMPLETED,
        actor = actor,
        role = AccountableRole.OPERATIONS,
        reason = "Route B exchange completed for order ${linkedOrder.id}"
    )
    if (!exOk) return Pair(false, exMsg)

    val (clOk, clMsg) = ClaimStateMachine.transition(
        claim = claim,
        target = ClaimStatus.CLOSED,
        actor = actor,
        role = AccountableRole.OPERATIONS,
        reason = "Eligible exchange completed and closed"
    )
    if (!clOk) return Pair(false, clMsg)

    claim.outcomeCode = "Eligible-Exchanged"
    claim.closureReason = "Route B exchange completed with surrendered ${claim.declaredBrand} cylinder"

    if (surrenderedCylinders.none { it.claimId == claim.id }) {
        surrenderedCylinders.add(
            SurrenderedCylinder(
                id = "SUR-" + UUID.randomUUID().toString().take(6).uppercase(),
                claimId = claim.id,
                brand = claim.declaredBrand,
                serialText = claim.declaredSerial?.takeIf { it.isNotBlank() } ?: claim.id,
                status = SurrenderStatus.COLLECTED
            )
        )
    }

    logAudit("ReplacementClaim", claimId, "COMPLETE_EXCHANGE", actor, AccountableRole.OPERATIONS, "Route B exchange completed and claim closed")
    notifyChanged()
    return Pair(true, "Exchange completed for claim $claimId.")
}

fun BlueGasStore.authorizeDisposition(
    surrenderId: String,
    type: String,
    authorizedBy: String = AccountableRole.LEGAL.displayName
): Pair<Boolean, String> {
    val sc = surrenderedCylinders.find { it.id == surrenderId }
        ?: return Pair(false, "Surrendered cylinder $surrenderId not found.")

    val returnChoice = "Return to ${sc.brand} under the written agreement"
    val recycleChoice = "Recycle through an authorised route"
    val linkedClaim = replacementClaims.find { it.id == sc.claimId }

    when (type) {
        returnChoice -> {
            if (sc.brand !in settings.rivalReturnAgreements) {
                return Pair(false, "No written return agreement with ${sc.brand} is active in settings.")
            }
        }
        recycleChoice -> {
            if (linkedClaim?.ownershipProofProvided != true) {
                return Pair(false, "Customer proof of ownership is required to authorise recycling.")
            }
        }
        else -> {
            return Pair(false, "Disposition choice not allowed. Choose return under written agreement or authorised recycling.")
        }
    }

    val prevStatus = sc.status
    sc.dispositionAuthorizedBy = authorizedBy
    sc.dispositionType = type
    sc.status = SurrenderStatus.DISPOSITION_AUTHORIZED

    sc.history.add(
        SurrenderHistory(
            fromStatus = prevStatus,
            toStatus = SurrenderStatus.DISPOSITION_AUTHORIZED,
            actor = authorizedBy,
            notes = "Disposition: $type"
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

fun BlueGasStore.issueSettlementPayout(claimId: String): Pair<Boolean, String> = completeExchange(claimId)

fun BlueGasStore.authorizeSurrenderDisposition(
    surrenderId: String,
    type: String,
    authorizedBy: String = AccountableRole.LEGAL.displayName
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
