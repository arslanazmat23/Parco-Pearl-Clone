package com.example.bluegas.data

import com.example.bluegas.lifecycle.CaseLifecycles
import com.example.bluegas.model.*
import java.util.UUID

/**
 * StoreReturns: L6 Cylinder Return & Deposit Refund flow (v0.5, BR-RET-01..10, BR-DEP-06).
 */

/**
 * Customer requests cylinder return.
 * Rules:
 * - Customer must hold that serial as DEPOSIT_HELD or LOANED.
 * - Reason required.
 * - No other open case for that serial.
 * - orderId = deposit account's orderId, or for a loaned cylinder the customer's most recent completed order.
 * - Opens CYLINDER_RETURN at CASE_OPENED; requestedAmountPaisa = deposit amount.
 * - Notify Support (NTF-10) and Customer (NTF-20).
 */
fun BlueGasStore.requestCylinderReturn(
    customerId: String,
    cylinderSerial: String,
    reason: String = "No longer needed",
    actor: String = "Customer"
): Pair<Boolean, String> {
    if (reason.isBlank()) {
        return Pair(false, "Reason is required.")
    }

    val holding = customerHoldings.firstOrNull {
        it.customerId == customerId && it.cylinderSerial == cylinderSerial &&
            (it.ownership == CylinderOwnership.DEPOSIT_HELD || it.ownership == CylinderOwnership.LOANED)
    } ?: return Pair(false, "Customer does not hold cylinder $cylinderSerial on deposit or loan.")

    // Check no other open case for that serial
    val hasOpenCase = returnCases.any {
        it.cylinderSerial == cylinderSerial &&
            it.status != L6Status.CASE_CLOSED &&
            it.status != L6Status.RETURN_REJECTED &&
            it.status != L6Status.REFUND_CONFIRMED
    }
    if (hasOpenCase) {
        return Pair(false, "An active return or refund case already exists for cylinder $cylinderSerial.")
    }

    val deposit = depositFor(customerId, cylinderSerial)
    val depositAmount = deposit?.amountPaisa ?: 0L

    val caseOrderId = deposit?.orderId?.ifBlank { null }
        ?: orders.filter { it.customerId == customerId && it.status == OrderStatus.ORD_COMP }
            .maxByOrNull { it.createdAt }?.id
        ?: ""

    if (deposit == null || depositAmount == 0L || holding.noDepositOnRecord) {
        if (reconExceptions.none { it.code == "EXC-26" && it.description.contains(cylinderSerial) }) {
            reconExceptions.add(
                ReconciliationException(
                    id = "EXC-" + UUID.randomUUID().toString().take(6),
                    code = "EXC-26",
                    description = "Customer $customerId requested deposit refund on cylinder $cylinderSerial with no deposit on record (e.g. dealer exchange history).",
                    ownerRole = AccountableRole.FINANCE
                )
            )
        }
    }

    val caseId = "CASE-RET-" + UUID.randomUUID().toString().take(6)
    val rrc = ReturnRefundCase(
        id = caseId,
        orderId = caseOrderId,
        customerId = customerId,
        cylinderSerial = cylinderSerial,
        depositId = deposit?.id,
        type = "CYLINDER_RETURN",
        reason = reason,
        requestedAmountPaisa = depositAmount,
        status = L6Status.CASE_OPENED
    )
    rrc.history.add(
        CaseHistory(
            fromStatus = L6Status.CASE_OPENED,
            toStatus = L6Status.CASE_OPENED,
            actor = actor,
            role = AccountableRole.SUPPORT,
            reason = "Cylinder return requested: $reason"
        )
    )
    returnCases.add(0, rrc)

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Cylinder return requested",
        message = "Your return request for cylinder $cylinderSerial has been received. Our team will arrange collection.",
        canonicalStatus = "RETURN_REQUESTED",
        orderId = caseOrderId.ifBlank { null },
        code = "NTF-20"
    )

    addNotification(
        surface = AppSurface.STAFF,
        title = "New Cylinder Return Request",
        message = "Customer $customerId requested return for cylinder $cylinderSerial.",
        canonicalStatus = "CASE_OPENED",
        orderId = caseOrderId.ifBlank { null },
        staffRole = StaffSubRole.SUPPORT,
        code = "NTF-10"
    )

    logAudit("ReturnRefundCase", caseId, "REQUEST_CYLINDER_RETURN", actor, AccountableRole.SUPPORT, "Return requested for cylinder $cylinderSerial")
    notifyChanged()
    return Pair(true, caseId)
}

/**
 * Offer return collection to a rider.
 * Rules:
 * - Case must be in RETURN_AUTHORIZED.
 * - Rider must be approved and active.
 * - Sets collectionRiderId, collectionTask = "OFFERED", moves case to AWAITING_ITEM_RETURN.
 * - Notify rider and customer (NTF-20: "Cylinder collection booked").
 */
fun BlueGasStore.offerReturnCollection(
    caseId: String,
    riderId: String,
    actor: String = AccountableRole.OPERATIONS.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.RETURN_AUTHORIZED) {
        return Pair(false, "Collection can only be offered for cases in RETURN_AUTHORIZED state (current: ${caseItem.status}).")
    }

    val rider = riders.find { it.id == riderId }
    if (rider == null || rider.status != "ACTIVE" || rider.onboardingStatus != "APPROVED") {
        return Pair(false, "Rider must be active and approved.")
    }

    caseItem.collectionRiderId = riderId
    caseItem.collectionTask = "OFFERED"

    val (moveOk, moveMsg) = moveCase(
        caseId = caseId,
        target = L6Status.AWAITING_ITEM_RETURN,
        reason = "Collection offered to rider ${rider.name} ($riderId)",
        actor = actor,
        role = AccountableRole.OPERATIONS
    )
    if (!moveOk) return Pair(false, moveMsg)

    addNotification(
        surface = AppSurface.RIDER,
        title = "Cylinder collection task",
        message = "Collect cylinder ${caseItem.cylinderSerial} from customer.",
        canonicalStatus = "COLLECTION_OFFERED",
        orderId = caseItem.orderId.ifBlank { null }
    )

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Cylinder collection booked",
        message = "Rider ${rider.name} will collect your return cylinder ${caseItem.cylinderSerial}.",
        canonicalStatus = "AWAITING_ITEM_RETURN",
        orderId = caseItem.orderId.ifBlank { null },
        code = "NTF-20"
    )

    logAudit("ReturnRefundCase", caseId, "OFFER_RETURN_COLLECTION", actor, AccountableRole.OPERATIONS, "Offered collection of ${caseItem.cylinderSerial} to rider $riderId")
    notifyChanged()
    return Pair(true, "Collection offered to rider.")
}

/**
 * Rider accepts return collection task.
 * Rules: OFFERED -> ACCEPTED.
 */
fun BlueGasStore.riderAcceptReturn(
    caseId: String,
    riderId: String
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionRiderId != riderId || caseItem.collectionTask != "OFFERED") {
        return Pair(false, "Collection task is not offered to rider $riderId.")
    }

    caseItem.collectionTask = "ACCEPTED"
    logAudit("ReturnRefundCase", caseId, "RIDER_ACCEPT_RETURN", "Rider", AccountableRole.OPERATIONS, "Rider $riderId accepted collection task")
    notifyChanged()
    return Pair(true, "Collection task accepted.")
}

/**
 * Rider declines return collection task.
 * Rules: Reason required. Clears the rider, collectionTask = null, notifies Operations. Case stays AWAITING_ITEM_RETURN.
 */
fun BlueGasStore.riderDeclineReturn(
    caseId: String,
    riderId: String,
    reason: String
): Pair<Boolean, String> {
    if (reason.isBlank()) {
        return Pair(false, "Reason is required.")
    }

    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionRiderId != riderId) {
        return Pair(false, "Task is not assigned to rider $riderId.")
    }

    caseItem.collectionRiderId = null
    caseItem.collectionTask = null

    addNotification(
        surface = AppSurface.STAFF,
        title = "Return collection declined",
        message = "Rider $riderId declined collection for case $caseId: $reason",
        canonicalStatus = "COLLECTION_DECLINED",
        orderId = caseItem.orderId.ifBlank { null },
        staffRole = StaffSubRole.OPERATIONS
    )

    logAudit("ReturnRefundCase", caseId, "DECLINE_RETURN_COLLECTION", "Rider", AccountableRole.OPERATIONS, "Declined: $reason")
    notifyChanged()
    return Pair(true, "Collection task declined.")
}

/**
 * Rider collects cylinder from customer.
 * Rules:
 * - ACCEPTED only.
 * - scannedSerial must equal cylinderSerial.
 * - photoCaptured must be true.
 * - Cylinder custody -> RIDER_VAN / INV_TRANSIT with custody event.
 * - collectionTask = "COLLECTED".
 */
fun BlueGasStore.riderCollectCylinder(
    caseId: String,
    riderId: String,
    scannedSerial: String,
    photoCaptured: Boolean
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionTask != "ACCEPTED") {
        return Pair(false, "Collection task must be ACCEPTED first.")
    }
    if (caseItem.collectionRiderId != riderId) {
        return Pair(false, "Rider $riderId is not assigned to this collection task.")
    }
    if (scannedSerial != caseItem.cylinderSerial) {
        return Pair(false, "This is not the cylinder on the return request.")
    }
    if (!photoCaptured) {
        return Pair(false, "Photo evidence of return cylinder is required.")
    }

    val cyl = cylinders.find { it.serialNumber == scannedSerial }
    if (cyl != null) {
        val idx = cylinders.indexOf(cyl)
        cylinders[idx] = cyl.copy(
            custody = CylinderCustody.RIDER_VAN,
            status = InventoryState.INV_TRANSIT,
            currentLocationLabel = "With Rider ($riderId)"
        )
        cylinderCustodyEvents.add(
            CylinderCustodyEvent(
                id = "CUST-" + UUID.randomUUID().toString().take(6),
                serialNumber = scannedSerial,
                fromCustody = cyl.custody,
                toCustody = CylinderCustody.RIDER_VAN,
                orderId = caseItem.orderId.ifBlank { null },
                actor = "Rider $riderId"
            )
        )
    }

    caseItem.collectionTask = "COLLECTED"
    logAudit("ReturnRefundCase", caseId, "RIDER_COLLECT_CYLINDER", "Rider", AccountableRole.OPERATIONS, "Cylinder $scannedSerial collected with photo verification")
    notifyChanged()
    return Pair(true, "Cylinder collected.")
}

/**
 * Rider hands in cylinder at hub.
 * Rules:
 * - COLLECTED -> AT_HUB.
 * - custody -> HUB at rider.pointId.
 * - notify Hub staff.
 */
fun BlueGasStore.riderHandInReturn(
    caseId: String,
    riderId: String
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionTask != "COLLECTED") {
        return Pair(false, "Collection task must be COLLECTED first (current: ${caseItem.collectionTask}).")
    }

    val rider = riders.find { it.id == riderId }
    val pointId = rider?.pointId ?: "NODE-LHR-1"

    val serial = caseItem.cylinderSerial
    if (!serial.isNullOrBlank()) {
        val cyl = cylinders.find { it.serialNumber == serial }
        if (cyl != null) {
            val idx = cylinders.indexOf(cyl)
            cylinders[idx] = cyl.copy(
                custody = CylinderCustody.HUB,
                currentLocationLabel = "At Hub ($pointId)"
            )
            cylinderCustodyEvents.add(
                CylinderCustodyEvent(
                    id = "CUST-" + UUID.randomUUID().toString().take(6),
                    serialNumber = serial,
                    fromCustody = cyl.custody,
                    toCustody = CylinderCustody.HUB,
                    orderId = caseItem.orderId.ifBlank { null },
                    actor = "Rider $riderId"
                )
            )
        }
    }

    caseItem.collectionTask = "AT_HUB"

    addNotification(
        surface = AppSurface.STAFF,
        title = "Returned cylinder received at hub",
        message = "Rider $riderId handed in returned cylinder ${caseItem.cylinderSerial} at hub $pointId.",
        canonicalStatus = "CYLINDER_AT_HUB",
        orderId = caseItem.orderId.ifBlank { null },
        staffRole = StaffSubRole.HUB
    )

    logAudit("ReturnRefundCase", caseId, "RIDER_HAND_IN_RETURN", "Rider", AccountableRole.HUB, "Cylinder handed in at hub $pointId")
    notifyChanged()
    return Pair(true, "Cylinder handed in at hub.")
}

/**
 * Hub records condition of returned cylinder.
 * Rules:
 * - AT_HUB only.
 * - Note required unless GOOD.
 * - Moves case AWAITING_ITEM_RETURN -> ITEM_RETURNED.
 * - Not GOOD -> add EXC-25 ReconciliationException.
 * - If hazardSuspected, call raiseIncident (L7, HSE hold).
 * - Record condition on the case.
 */
fun BlueGasStore.hubRecordReturnCondition(
    caseId: String,
    condition: ReturnCondition,
    note: String? = null,
    hazardSuspected: Boolean = false,
    actor: String = AccountableRole.HUB.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionTask != "AT_HUB") {
        return Pair(false, "Return condition can only be recorded when cylinder is at hub (current task: ${caseItem.collectionTask}).")
    }
    if (condition != ReturnCondition.GOOD && note.isNullOrBlank()) {
        return Pair(false, "Note is required when condition is not Good.")
    }

    val (moveOk, moveMsg) = moveCase(
        caseId = caseId,
        target = L6Status.ITEM_RETURNED,
        reason = "Return condition recorded: ${condition.label}",
        actor = actor,
        role = AccountableRole.HUB
    )
    if (!moveOk) return Pair(false, moveMsg)

    caseItem.conditionResult = condition
    caseItem.conditionNote = note
    caseItem.hazardSuspected = hazardSuspected

    if (condition != ReturnCondition.GOOD) {
        reconExceptions.add(
            ReconciliationException(
                id = "EXC-" + UUID.randomUUID().toString().take(6),
                code = "EXC-25",
                ownerRole = AccountableRole.FINANCE,
                description = "Returned cylinder ${caseItem.cylinderSerial}: ${condition.label}. ${note ?: ""}".trim()
            )
        )
    }

    if (hazardSuspected) {
        raiseIncident(
            source = "DEPOT",
            description = note ?: "Hazard suspected on returned cylinder ${caseItem.cylinderSerial}",
            cylinderId = caseItem.cylinderSerial,
            actor = "Hub Staff"
        )
    }

    logAudit("ReturnRefundCase", caseId, "HUB_RECORD_CONDITION", actor, AccountableRole.HUB, "Condition recorded: ${condition.name}, note: ${note ?: "None"}")
    notifyChanged()
    return Pair(true, "Return condition recorded.")
}

/**
 * Finance requests deposit refund.
 * Rules (BR-DEP-06, UAT-37):
 * - Case ITEM_RETURNED, type CYLINDER_RETURN.
 * - Deposit account must be HELD with amount > 0, else "No deposit on record for this cylinder (EXC-26). No refund without proof."
 * - Original payment must be SETTLED, else "The original payment for this deposit is not settled yet. The refund waits until Finance reconciles the cash."
 * - 0 <= deduction < deposit. Deduction == deposit rejected with "Enter a deduction below the deposit amount."
 * - Deduction > 0, or condition != GOOD, needs reason.
 * - Creates Refund(kind = "DEPOSIT", state = PARTIAL_REFUND_PENDING, linkedCaseId, amount = deposit - deduction).
 * - Moves case to REFUND_REQUESTED (role FINANCE).
 * - Sets deposit REFUND_IN_PROGRESS, refundId, deductionPaisa, deductionReason.
 * - Notify Finance (NTF-11).
 */
fun BlueGasStore.requestDepositRefund(
    caseId: String,
    deductionPaisa: Long = 0L,
    deductionReason: String? = null,
    actor: String = AccountableRole.FINANCE.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.ITEM_RETURNED || caseItem.type != "CYLINDER_RETURN") {
        return Pair(false, "Case must be in ITEM_RETURNED state and type CYLINDER_RETURN (current: ${caseItem.status}, ${caseItem.type}).")
    }

    val deposit = depositAccounts.firstOrNull { it.id == caseItem.depositId }
        ?: depositAccounts.firstOrNull { it.customerId == caseItem.customerId && it.cylinderSerial == caseItem.cylinderSerial && it.status == DepositStatus.HELD }

    if (deposit == null || deposit.status != DepositStatus.HELD || deposit.amountPaisa <= 0L) {
        return Pair(false, "No deposit on record for this cylinder (EXC-26). No refund without proof.")
    }

    val originalPayment = payments.find { it.id == deposit.paymentId }
        ?: payments.find { it.orderId == deposit.orderId }

    if (originalPayment != null && originalPayment.state != PaymentState.SETTLED) {
        return Pair(false, "The original payment for this deposit is not settled yet. The refund waits until Finance reconciles the cash.")
    }

    if (deductionPaisa < 0L) {
        return Pair(false, "Deduction cannot be negative.")
    }
    if (deductionPaisa >= deposit.amountPaisa) {
        return Pair(false, "Enter a deduction below the deposit amount.")
    }

    if ((deductionPaisa > 0L || caseItem.conditionResult != ReturnCondition.GOOD) && deductionReason.isNullOrBlank()) {
        return Pair(false, "Deduction reason is required.")
    }

    val refundId = "REF-DEP-" + UUID.randomUUID().toString().take(6)
    val refundAmount = deposit.amountPaisa - deductionPaisa
    val refund = Refund(
        id = refundId,
        paymentId = deposit.paymentId ?: "",
        orderId = deposit.orderId,
        amountPaisa = refundAmount,
        reason = "Deposit refund for cylinder ${caseItem.cylinderSerial}" + (if (!deductionReason.isNullOrBlank()) " (Deduction: $deductionReason)" else ""),
        executedBy = actor,
        state = PaymentState.PARTIAL_REFUND_PENDING,
        kind = "DEPOSIT",
        linkedCaseId = caseId
    )
    refunds.add(refund)

    caseItem.status = L6Status.REFUND_REQUESTED
    caseItem.history.add(
        CaseHistory(
            fromStatus = L6Status.ITEM_RETURNED,
            toStatus = L6Status.REFUND_REQUESTED,
            actor = actor,
            role = AccountableRole.FINANCE,
            reason = "Deposit refund requested: ${formatRupees(refundAmount)}"
        )
    )

    deposit.status = DepositStatus.REFUND_IN_PROGRESS
    deposit.refundId = refundId
    deposit.deductionPaisa = deductionPaisa
    deposit.deductionReason = deductionReason

    caseItem.deductionPaisa = deductionPaisa
    caseItem.deductionReason = deductionReason

    addNotification(
        surface = AppSurface.STAFF,
        title = "Deposit refund pending approval",
        message = "Deposit refund of ${formatRupees(refundAmount)} for case $caseId requires approval.",
        canonicalStatus = "REFUND_REQUESTED",
        orderId = deposit.orderId.ifBlank { null },
        staffRole = StaffSubRole.FINANCE,
        code = "NTF-11"
    )

    logAudit("ReturnRefundCase", caseId, "REQUEST_DEPOSIT_REFUND", actor, AccountableRole.FINANCE, "Deposit refund requested: net ${formatRupees(refundAmount)}, deduction ${formatRupees(deductionPaisa)}")
    notifyChanged()
    return Pair(true, "Deposit refund requested.")
}
