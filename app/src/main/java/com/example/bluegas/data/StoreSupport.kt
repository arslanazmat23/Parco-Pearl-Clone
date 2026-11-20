package com.example.bluegas.data

import com.example.bluegas.lifecycle.CaseLifecycles
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Customer Support & Return/Refund Case Operations (RACI-17, 30)
 */

fun BlueGasStore.startComplaint(
    caseId: String,
    actor: String = "Customer Support"
): Pair<Boolean, String> {
    val sc = supportCases.find { it.id == caseId }
        ?: return Pair(false, "Support case $caseId not found.")

    if (sc.status != "OPEN") {
        return Pair(false, "Complaint is already ${sc.status}.")
    }

    if (sc.firstResponseAt == null) sc.firstResponseAt = clock()
    sc.status = "IN_PROGRESS"
    logAudit("SupportCase", caseId, "START_COMPLAINT", actor, AccountableRole.SUPPORT, "Investigation started")

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Support Investigation Underway",
        message = "We are looking into your complaint ($caseId). Our team will update you shortly.",
        canonicalStatus = "IN_PROGRESS",
        orderId = sc.orderId,
        code = "NTF-17"
    )

    notifyChanged()
    return Pair(true, "Complaint $caseId marked in progress.")
}

/**
 * Resolves a customer complaint case with a structured outcome: REFUND (opens a linked L6
 * REFUND_ONLY case while keeping the order Completed), REPLACE (records outcome and alerts
 * Operations to arrange a replacement without auto-creating an order), or EXPLAIN (records note).
 * Sets [SupportCase.firstResponseAt] if not yet recorded.
 *
 * @param caseId Support case identifier.
 * @param type Resolution outcome code: "REFUND", "REPLACE", or "EXPLAIN".
 * @param note Required staff explanation or resolution note.
 * @param refundAmountPaisa Required refund amount in paisa when [type] is "REFUND".
 * @param actor Staff actor resolving the complaint.
 * @return Pair of success flag and outcome summary message.
 */
fun BlueGasStore.resolveComplaint(
    caseId: String,
    type: String,
    note: String,
    refundAmountPaisa: Long? = null,
    actor: String = AccountableRole.SUPPORT.displayName
): Pair<Boolean, String> {
    val sc = supportCases.find { it.id == caseId }
        ?: return Pair(false, "Support case $caseId not found.")

    if (sc.status == "RESOLVED") {
        return Pair(false, "Complaint $caseId is already resolved.")
    }
    val cleanNote = note.trim()
    if (cleanNote.isEmpty()) {
        return Pair(false, "A resolution note is required.")
    }

    val normalizedType = type.uppercase()
    val now = clock()
    val refDisplay = sc.reference ?: sc.id

    when (normalizedType) {
        "REFUND" -> {
            val ordId = sc.orderId
                ?: return Pair(false, "Refund requires a linked order on the complaint.")
            val order = orders.find { it.id == ordId }
                ?: return Pair(false, "Linked order $ordId not found.")
            if (order.status != OrderStatus.ORD_COMP) {
                return Pair(false, "Refund cases can only be opened for completed orders.")
            }
            val amt = refundAmountPaisa ?: 0L
            if (amt <= 0L || amt > order.totalAmountPaisa) {
                return Pair(false, "Refund amount must be between Re 1 and order total ${formatRupees(order.totalAmountPaisa)}.")
            }
            val (caseOk, caseMsg) = openReturnCase(
                orderId = ordId,
                type = "REFUND_ONLY",
                reason = "Complaint $refDisplay: $cleanNote",
                requestedAmountPaisa = amt,
                actor = actor
            )
            if (!caseOk) return Pair(false, caseMsg)
            val createdCase = returnCases.firstOrNull { it.orderId == ordId && it.type == "REFUND_ONLY" }
            sc.linkedReturnCaseId = createdCase?.id
            sc.resolutionType = "REFUND"
            sc.resolutionNotes = "Refund case ${createdCase?.id ?: ""} opened (${formatRupees(amt)}): $cleanNote"
        }

        "REPLACE" -> {
            val targetLabel = sc.orderId?.let { orderLabel(it) } ?: refDisplay
            sc.resolutionType = "REPLACE"
            sc.resolutionNotes = "Replacement requested: $cleanNote"
            addNotification(
                surface = AppSurface.STAFF,
                title = "Replacement Requested",
                message = "Arrange replacement for $targetLabel",
                canonicalStatus = "RESOLVED",
                orderId = sc.orderId,
                code = "NTF-17",
                staffRole = StaffSubRole.OPERATIONS
            )
        }

        "EXPLAIN" -> {
            sc.resolutionType = "EXPLAIN"
            sc.resolutionNotes = cleanNote
        }

        else -> return Pair(false, "Unknown complaint resolution type: $type.")
    }

    if (sc.firstResponseAt == null) {
        sc.firstResponseAt = now
    }
    sc.resolvedAt = now
    sc.status = "RESOLVED"

    logAudit(
        entityType = "SupportCase",
        entityId = caseId,
        action = "RESOLVE_COMPLAINT_$normalizedType",
        actor = actor,
        role = AccountableRole.SUPPORT,
        details = sc.resolutionNotes ?: cleanNote
    )
    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Complaint Resolved ($refDisplay)",
        message = "Your complaint $refDisplay has been resolved: ${sc.resolutionNotes ?: cleanNote}",
        canonicalStatus = "RESOLVED",
        orderId = sc.orderId,
        code = "NTF-17"
    )
    notifyChanged()
    return Pair(true, "Complaint $refDisplay resolved ($normalizedType).")
}

fun BlueGasStore.openReturnCase(
    orderId: String,
    type: String, // "RETURN" or "REFUND_ONLY"
    reason: String,
    requestedAmountPaisa: Long,
    actor: String = "Customer Support"
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId }
        ?: return Pair(false, "Order $orderId not found.")

    if (order.status != OrderStatus.ORD_COMP) {
        return Pair(false, "Return cases can only be opened for completed orders.")
    }
    if (requestedAmountPaisa <= 0 || requestedAmountPaisa > order.totalAmountPaisa) {
        return Pair(false, "Requested refund amount must be between Re. 1 and order total ${formatPaisaToPkr(order.totalAmountPaisa)}.")
    }
    if (reason.isBlank()) {
        return Pair(false, "A reason for the case is required.")
    }

    val existingOpen = returnCases.find { it.orderId == orderId && it.status != L6Status.CASE_CLOSED }
    if (existingOpen != null) {
        return Pair(false, "Order $orderId already has an active return case (${existingOpen.id}).")
    }

    val caseId = "RRC-" + (1000..9999).random()
    val rrc = ReturnRefundCase(
        id = caseId,
        orderId = orderId,
        customerId = order.customerId,
        type = type,
        reason = reason,
        requestedAmountPaisa = requestedAmountPaisa,
        status = L6Status.CASE_OPENED
    )
    rrc.history.add(
        CaseHistory(
            fromStatus = L6Status.CASE_OPENED,
            toStatus = L6Status.CASE_OPENED,
            actor = actor,
            role = AccountableRole.SUPPORT,
            reason = "Case opened: $reason"
        )
    )
    returnCases.add(0, rrc)

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Return Case Registered",
        message = "Your claim for order $orderId has been logged ($caseId).",
        canonicalStatus = L6Status.CASE_OPENED.name,
        orderId = orderId,
        code = "NTF-10"
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Return/Refund Case Opened (EXC-14)",
        message = "New $type case $caseId opened for order $orderId by $actor.",
        canonicalStatus = L6Status.CASE_OPENED.name,
        orderId = orderId,
        code = "NTF-10",
        staffRole = StaffSubRole.SUPPORT
    )

    logAudit("ReturnRefundCase", caseId, "OPEN_RETURN_CASE", actor, AccountableRole.SUPPORT, "$type on $orderId for ${formatPaisaToPkr(requestedAmountPaisa)}: $reason")
    notifyChanged()
    return Pair(true, "Case $caseId opened successfully. Order $orderId remains Completed.")
}

fun BlueGasStore.moveCase(
    caseId: String,
    target: L6Status,
    reason: String,
    refundAmountPaisa: Long? = null,
    actor: String = "Customer Support",
    role: AccountableRole = AccountableRole.SUPPORT
): Pair<Boolean, String> {
    val caseItem = returnCases.find { it.id == caseId }
        ?: return Pair(false, "Return case $caseId not found.")

    if (reason.isBlank()) {
        return Pair(false, "A reason must be provided for all status transitions.")
    }
    if (target == L6Status.REFUND_CONFIRMED) {
        return Pair(false, "REFUND_CONFIRMED cannot be set manually. It is automatically set when Finance executes the refund.")
    }

    if (target == L6Status.RETURN_AUTHORIZED && caseItem.type == "CYLINDER_RETURN") {
        if (caseItem.cylinderSerial.isNullOrBlank()) {
            return Pair(false, "Cylinder serial is required to authorize cylinder return.")
        }
    }

    if (target == L6Status.REFUND_REQUESTED) {
        val amount = refundAmountPaisa ?: caseItem.requestedAmountPaisa
        if (caseItem.type == "CYLINDER_RETURN") {
            val refId = "REF-" + UUID.randomUUID().toString().take(6)
            val ref = Refund(
                id = refId,
                paymentId = "PAY-DEP-$caseId",
                orderId = caseItem.orderId,
                linkedCaseId = caseId,
                amountPaisa = amount,
                reason = "Deposit refund: $reason",
                executedBy = actor,
                state = PaymentState.FULL_REFUND_PENDING,
                kind = "DEPOSIT"
            )
            refunds.add(ref)
            caseItem.linkedRefundId = refId

            addNotification(
                surface = AppSurface.STAFF,
                title = "Deposit Refund Waiting for Finance",
                message = "Case $caseId requested deposit refund of ${formatRupees(amount)}.",
                canonicalStatus = "PENDING",
                orderId = caseItem.orderId.ifBlank { null },
                code = "NTF-11",
                staffRole = StaffSubRole.FINANCE
            )
        } else {
            val payment = activePaymentFor(caseItem.orderId)
                ?: return Pair(false, "No payment record found for order ${caseItem.orderId}.")

            if (payment.state != PaymentState.SETTLED) {
                return Pair(false, "Cash for this order has not been reconciled yet — refund not allowed before collection (BR).")
            }

            val refId = "REF-" + UUID.randomUUID().toString().take(6)
            val isFull = (amount >= payment.settledAmountPaisa)
            val ref = Refund(
                id = refId,
                paymentId = payment.id,
                orderId = caseItem.orderId,
                linkedCaseId = caseId,
                amountPaisa = amount,
                reason = "L6 case refund: $reason",
                executedBy = actor,
                state = if (isFull) PaymentState.FULL_REFUND_PENDING else PaymentState.PARTIAL_REFUND_PENDING,
                kind = if (isFull) "FULL" else "PARTIAL"
            )
            refunds.add(ref)
            caseItem.linkedRefundId = refId

            addNotification(
                surface = AppSurface.STAFF,
                title = "Refund Waiting for Finance",
                message = "Case $caseId requested refund of ${formatRupees(amount)} for order ${caseItem.orderId}.",
                canonicalStatus = "PENDING",
                orderId = caseItem.orderId,
                code = "NTF-11",
                staffRole = StaffSubRole.FINANCE
            )
        }
    }

    val (ok, msg) = CaseLifecycles.transitionL6(
        returnCase = caseItem,
        target = target,
        actor = actor,
        role = role,
        reason = reason
    )
    if (!ok) return Pair(false, msg)

    if (target == L6Status.CASE_CLOSED || target == L6Status.RETURN_REJECTED) {
        caseItem.closedAt = System.currentTimeMillis()
    }

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Case Status Updated",
        message = "Return case $caseId moved to ${target.name}. $reason",
        canonicalStatus = target.name,
        orderId = caseItem.orderId.ifBlank { null },
        code = "NTF-10"
    )

    logAudit("ReturnRefundCase", caseId, "MOVE_CASE_${target.name}", actor, role, reason)
    notifyChanged()
    return Pair(true, "Case $caseId transitioned to ${target.name}.")
}

fun BlueGasStore.moveReturnCase(
    caseId: String,
    target: L6Status,
    reason: String,
    actor: String = "Customer Support",
    role: AccountableRole = AccountableRole.SUPPORT,
    refundAmountPaisa: Long? = null
): Pair<Boolean, String> = moveCase(caseId, target, reason, refundAmountPaisa, actor, role)
