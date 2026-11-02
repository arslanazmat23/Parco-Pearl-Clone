package com.example.bluegas.data

import com.example.bluegas.lifecycle.CaseLifecycles
import com.example.bluegas.model.*
import java.util.UUID

/**
 * StoreReturns: L6 Cylinder Return & Deposit Refund flow (BR-RET-01..10).
 */

fun BlueGasStore.requestCylinderReturn(
    customerId: String,
    cylinderSerial: String,
    reason: String = "No longer needed"
): Pair<Boolean, String> {
    val holding = customerHoldings.firstOrNull {
        it.customerId == customerId && it.cylinderSerial == cylinderSerial && it.status == "ACTIVE"
    } ?: return Pair(false, "No active holding found for cylinder $cylinderSerial.")

    val deposit = depositFor(customerId, cylinderSerial)
    val depositAmount = deposit?.amountPaisa ?: 0L

    if (deposit == null || depositAmount == 0L || holding.noDepositOnRecord) {
        // Customer requested deposit refund on cylinder with no deposit on record
        reconExceptions.add(
            ReconciliationException(
                id = "EXC-" + UUID.randomUUID().toString().take(6),
                code = "EXC-26",
                description = "Customer $customerId requested deposit refund on cylinder $cylinderSerial with no deposit on record (e.g. dealer exchange history).",
                ownerRole = AccountableRole.FINANCE
            )
        )
    }

    val caseId = "CASE-RET-" + UUID.randomUUID().toString().take(6)
    val rrc = ReturnRefundCase(
        id = caseId,
        orderId = deposit?.orderId ?: "",
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
            actor = "Customer",
            role = AccountableRole.CUSTOMER,
            reason = "Cylinder return requested: $reason"
        )
    )
    returnCases.add(0, rrc)

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Cylinder return requested",
        message = "Your return request for cylinder $cylinderSerial has been received. Our team will arrange collection.",
        canonicalStatus = "RETURN_REQUESTED",
        orderId = deposit?.orderId,
        code = "NTF-20"
    )

    addNotification(
        surface = AppSurface.STAFF,
        title = "New Cylinder Return Request",
        message = "Customer $customerId requested return for cylinder $cylinderSerial.",
        canonicalStatus = "CASE_OPENED",
        orderId = deposit?.orderId,
        staffRole = StaffSubRole.OPERATIONS
    )

    logAudit("ReturnRefundCase", caseId, "REQUEST_CYLINDER_RETURN", "Customer", AccountableRole.CUSTOMER, "Return requested for cylinder $cylinderSerial")
    notifyChanged()
    return Pair(true, caseId)
}

fun BlueGasStore.offerReturnCollection(
    caseId: String,
    riderId: String,
    actor: String = AccountableRole.OPERATIONS.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.CASE_OPENED) {
        return Pair(false, "Collection can only be offered for cases in CASE_OPENED state.")
    }

    caseItem.collectionRiderId = riderId
    caseItem.collectionTask = "OFFERED"

    addNotification(
        surface = AppSurface.RIDER,
        title = "Cylinder Collection Task",
        message = "Collect cylinder ${caseItem.cylinderSerial} from customer.",
        canonicalStatus = "COLLECTION_OFFERED",
        orderId = caseItem.orderId.ifBlank { null }
    )

    logAudit("ReturnRefundCase", caseId, "OFFER_RETURN_COLLECTION", actor, AccountableRole.OPERATIONS, "Offered collection of ${caseItem.cylinderSerial} to rider $riderId")
    notifyChanged()
    return Pair(true, "Collection task offered to rider $riderId.")
}

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
    return moveCase(
        caseId = caseId,
        target = L6Status.RETURN_AUTHORIZED,
        reason = "Rider accepted collection task",
        actor = "Rider",
        role = AccountableRole.RIDER
    )
}

fun BlueGasStore.riderDeclineReturn(
    caseId: String,
    riderId: String,
    reason: String
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.collectionRiderId != riderId) {
        return Pair(false, "Task is not assigned to rider $riderId.")
    }

    caseItem.collectionTask = "DECLINED"
    caseItem.collectionRiderId = null

    logAudit("ReturnRefundCase", caseId, "DECLINE_RETURN_COLLECTION", "Rider", AccountableRole.RIDER, "Declined: $reason")
    notifyChanged()
    return Pair(true, "Collection task declined.")
}

fun BlueGasStore.riderCollectCylinder(
    caseId: String,
    serialScanned: String,
    riderId: String
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.RETURN_AUTHORIZED) {
        return Pair(false, "Case is not authorized for return (current status: ${caseItem.status}).")
    }
    if (caseItem.collectionRiderId != riderId) {
        return Pair(false, "Rider $riderId is not assigned to this collection task.")
    }

    val cyl = cylinders.find { it.serialNumber == serialScanned }
    if (cyl != null) {
        cylinderCustodyEvents.add(
            CylinderCustodyEvent(
                id = "CUST-" + UUID.randomUUID().toString().take(6),
                serialNumber = serialScanned,
                fromCustody = cyl.custody,
                toCustody = CylinderCustody.RIDER,
                orderId = caseItem.orderId.ifBlank { null },
                actor = "Rider"
            )
        )
    }

    return moveCase(
        caseId = caseId,
        target = L6Status.RETURN_IN_TRANSIT,
        reason = "Cylinder $serialScanned collected by rider $riderId",
        actor = "Rider",
        role = AccountableRole.RIDER
    )
}

fun BlueGasStore.riderHandInReturn(
    caseId: String,
    pointId: String,
    riderId: String
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.RETURN_IN_TRANSIT) {
        return Pair(false, "Case is not in transit (current status: ${caseItem.status}).")
    }

    caseItem.collectionTask = "COMPLETED"

    val serial = caseItem.cylinderSerial
    if (serial != null) {
        val cyl = cylinders.find { it.serialNumber == serial }
        if (cyl != null) {
            cylinderCustodyEvents.add(
                CylinderCustodyEvent(
                    id = "CUST-" + UUID.randomUUID().toString().take(6),
                    serialNumber = serial,
                    fromCustody = cyl.custody,
                    toCustody = CylinderCustody.HUB,
                    orderId = caseItem.orderId.ifBlank { null },
                    actor = "Rider"
                )
            )
        }
    }

    return moveCase(
        caseId = caseId,
        target = L6Status.RETURN_RECEIVED,
        reason = "Cylinder handed in at hub $pointId",
        actor = "Rider",
        role = AccountableRole.RIDER
    )
}

fun BlueGasStore.hubRecordReturnCondition(
    caseId: String,
    condition: ReturnCondition,
    deductionPaisa: Long = 0L,
    deductionReason: String? = null,
    hazardSuspected: Boolean = false,
    note: String? = null,
    actor: String = AccountableRole.HUB.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (caseItem.status != L6Status.RETURN_RECEIVED) {
        return Pair(false, "Return condition can only be recorded when cylinder is in RETURN_RECEIVED status.")
    }

    caseItem.conditionResult = condition
    caseItem.conditionNote = note
    caseItem.hazardSuspected = hazardSuspected
    caseItem.deductionPaisa = deductionPaisa
    caseItem.deductionReason = deductionReason

    if (hazardSuspected) {
        val incidentId = "INC-" + UUID.randomUUID().toString().take(6)
        val incident = SafetyIncident(
            id = incidentId,
            source = "DEPOT",
            cylinderId = caseItem.cylinderSerial,
            description = "Hazard suspected on returned cylinder ${caseItem.cylinderSerial}: ${note ?: "Physical anomaly noted on hub receipt"}",
            status = L7Status.INCIDENT_RAISED
        )
        safetyIncidents.add(incident)
    }

    return when (condition) {
        ReturnCondition.GOOD -> {
            requestDepositRefund(caseId, actor = actor)
        }
        ReturnCondition.DAMAGED -> {
            requestDepositRefund(caseId, actor = actor)
        }
        ReturnCondition.NOT_HANDED_OVER -> {
            caseItem.rejectionReason = note ?: "Cylinder was not handed over"
            moveCase(
                caseId = caseId,
                target = L6Status.RETURN_REJECTED,
                reason = note ?: "Cylinder was not handed over",
                actor = actor,
                role = AccountableRole.HUB
            )
        }
    }
}

fun BlueGasStore.requestDepositRefund(
    caseId: String,
    actor: String = AccountableRole.HUB.displayName
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    val netRefund = (caseItem.requestedAmountPaisa - caseItem.deductionPaisa).coerceAtLeast(0L)

    return moveCase(
        caseId = caseId,
        target = L6Status.REFUND_REQUESTED,
        reason = "Hub inspected cylinder condition (${caseItem.conditionResult?.label ?: "Inspected"}). Net refund: ${formatRupees(netRefund)}",
        refundAmountPaisa = netRefund,
        actor = actor,
        role = AccountableRole.HUB
    )
}
