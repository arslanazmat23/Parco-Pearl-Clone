package com.example.bluegas.data

import com.example.bluegas.lifecycle.CaseLifecycles
import com.example.bluegas.lifecycle.PaymentStateMachine
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Finance Store Operations (RACI-02, 03, 11, 18, 20, 27, 28)
 */

fun BlueGasStore.processRefund(
    refundId: String,
    succeed: Boolean,
    actor: String = "Finance Lead"
): Pair<Boolean, String> {
    val refund = refunds.find { it.id == refundId }
        ?: return Pair(false, "Refund $refundId not found.")

    if (refund.state != PaymentState.FULL_REFUND_PENDING && refund.state != PaymentState.PARTIAL_REFUND_PENDING) {
        return Pair(false, "Refund $refundId is in state ${refund.state.displayName} and cannot be processed.")
    }

    val isFull = refund.kind == "FULL" || refund.state == PaymentState.FULL_REFUND_PENDING
    val targetState = if (succeed) {
        if (isFull) PaymentState.FULLY_REFUNDED else PaymentState.PARTIAL_REFUND_COMPLETED
    } else {
        if (isFull) PaymentState.FULL_REFUND_FAILED else PaymentState.PARTIAL_REFUND_FAILED
    }

    val refIdx = refunds.indexOf(refund)
    refunds[refIdx] = refund.copy(
        state = targetState,
        executedBy = actor,
        completedAt = if (succeed) System.currentTimeMillis() else null
    )

    val payment = payments.find { it.id == refund.paymentId }
        ?: orders.find { it.id == refund.orderId }?.let { activePaymentFor(it.id) }

    if (succeed) {
        // If payment was in FULL_REFUND_PENDING (e.g. from cancellation)
        if (payment != null && payment.state == PaymentState.FULL_REFUND_PENDING) {
            PaymentStateMachine.transition(payment, PaymentState.FULLY_REFUNDED, actor, "Refund processed successfully", paymentHistory)
        }

        // If linked to an L6 Return/Refund case
        if (!refund.linkedCaseId.isNullOrBlank()) {
            val caseItem = returnCases.find { it.id == refund.linkedCaseId }
            if (caseItem != null && caseItem.status == L6Status.REFUND_REQUESTED) {
                CaseLifecycles.transitionL6(
                    caseItem,
                    L6Status.REFUND_CONFIRMED,
                    "Platform",
                    AccountableRole.FINANCE,
                    "Refund completed by Finance"
                )
            }
        }

        postLedger(refund.orderId, "REFUND", debitPaisa = refund.amountPaisa, description = "Refund processed: ${refund.reason ?: "Approved refund"}")
        logAudit("Refund", refundId, "PROCESS_REFUND_SUCCESS", actor, AccountableRole.FINANCE, "Refund of ${formatPaisaToPkr(refund.amountPaisa)} processed successfully")

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Refund Processed",
            message = "Refund of ${formatPaisaToPkr(refund.amountPaisa)} completed for order ${refund.orderId}.",
            canonicalStatus = targetState.displayName,
            orderId = refund.orderId,
            code = "NTF-11"
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Refund Completed",
            message = "Refund $refundId for order ${refund.orderId} completed by $actor.",
            canonicalStatus = targetState.displayName,
            orderId = refund.orderId,
            code = "NTF-11",
            staffRole = StaffSubRole.FINANCE
        )
        notifyChanged()
        return Pair(true, "Refund of ${formatPaisaToPkr(refund.amountPaisa)} completed successfully.")
    } else {
        if (payment != null && payment.state == PaymentState.FULL_REFUND_PENDING) {
            PaymentStateMachine.transition(payment, PaymentState.FULL_REFUND_FAILED, actor, "Gateway declined refund", paymentHistory)
        }
        logAudit("Refund", refundId, "PROCESS_REFUND_FAILED", actor, AccountableRole.FINANCE, "Gateway declined refund of ${formatPaisaToPkr(refund.amountPaisa)}")
        notifyChanged()
        return Pair(true, "Gateway declined the refund (demo). It stays in the queue for retry.")
    }
}

fun BlueGasStore.retryRefund(refundId: String): Pair<Boolean, String> {
    val refund = refunds.find { it.id == refundId }
        ?: return Pair(false, "Refund $refundId not found.")

    if (refund.state != PaymentState.FULL_REFUND_FAILED && refund.state != PaymentState.PARTIAL_REFUND_FAILED) {
        return Pair(false, "Refund $refundId is not in a failed state.")
    }

    val isFull = refund.kind == "FULL" || refund.state == PaymentState.FULL_REFUND_FAILED
    val pendingState = if (isFull) PaymentState.FULL_REFUND_PENDING else PaymentState.PARTIAL_REFUND_PENDING

    val refIdx = refunds.indexOf(refund)
    refunds[refIdx] = refund.copy(
        state = pendingState,
        attempts = refund.attempts + 1
    )

    val payment = payments.find { it.id == refund.paymentId }
        ?: orders.find { it.id == refund.orderId }?.let { activePaymentFor(it.id) }

    if (payment != null && payment.state == PaymentState.FULL_REFUND_FAILED) {
        PaymentStateMachine.transition(payment, PaymentState.FULL_REFUND_PENDING, "Finance", "Retrying refund", paymentHistory)
    }

    logAudit("Refund", refundId, "RETRY_REFUND", "Finance Lead", AccountableRole.FINANCE, "Refund retry queued (attempt ${refund.attempts + 1})")
    notifyChanged()
    return Pair(true, "Refund queued for retry.")
}

fun BlueGasStore.reconcileCashHandover(
    handoverId: String,
    countedPaisa: Long,
    reason: String?,
    actor: String = "Finance Lead"
): Pair<Boolean, String> {
    val handover = cashHandovers.find { it.id == handoverId }
        ?: return Pair(false, "Cash handover $handoverId not found.")

    if (handover.status != "SUBMITTED") {
        return Pair(false, "Handover $handoverId is already ${handover.status}.")
    }

    val difference = countedPaisa - handover.expectedPaisa
    if (difference != 0L && reason.isNullOrBlank()) {
        return Pair(false, "A non-blank reason is required when counted cash differs from expected.")
    }

    handover.countedPaisa = countedPaisa
    handover.differencePaisa = difference
    handover.reason = reason
    handover.reconciledAt = System.currentTimeMillis()
    handover.reconciledBy = actor
    handover.status = if (difference == 0L) "RECONCILED" else "RECONCILED_WITH_DIFFERENCE"

    // Settle every order in the handover whose payment is CASH_COLLECTED
    for (ordId in handover.orderIds) {
        val pay = activePaymentFor(ordId)
        if (pay != null && pay.state == PaymentState.CASH_COLLECTED) {
            PaymentStateMachine.transition(pay, PaymentState.SETTLED, actor, "COD settled after cash reconciliation ($handoverId)", paymentHistory)
            val order = orders.find { it.id == ordId }
            val amount = order?.amountReceivedPaisa ?: order?.totalAmountPaisa ?: pay.finalAmountPaisa
            pay.settledAmountPaisa = amount
            postLedger(ordId, "SETTLEMENT", debitPaisa = amount, description = "COD settled after cash reconciliation ($handoverId)")
        }
    }

    if (difference != 0L) {
        postLedger(null, "CASH_SHORT_OVER", debitPaisa = if (difference < 0) -difference else 0L, creditPaisa = if (difference > 0) difference else 0L, description = "Cash reconciliation difference on $handoverId: ${formatPaisaToPkr(difference)}")
        val excId = "EXC21-" + UUID.randomUUID().toString().take(6)
        reconExceptions.add(
            ReconciliationException(
                id = excId,
                code = "EXC-21",
                description = "Cash difference on hand-over $handoverId: ${formatPaisaToPkr(difference)}. Reason: ${reason ?: "Count difference"}",
                ownerRole = AccountableRole.FINANCE
            )
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Cash Hand-over Difference (EXC-21)",
            message = "Hand-over $handoverId reconciled with difference ${formatPaisaToPkr(difference)}.",
            canonicalStatus = "RECONCILED_WITH_DIFFERENCE",
            orderId = handover.orderIds.firstOrNull(),
            code = "NTF-15",
            staffRole = StaffSubRole.FINANCE
        )
    }

    logAudit("CashHandover", handoverId, "RECONCILE_CASH", actor, AccountableRole.FINANCE, "Reconciled with counted ${formatPaisaToPkr(countedPaisa)} (diff: ${formatPaisaToPkr(difference)})")
    notifyChanged()
    return Pair(true, "Handover $handoverId ${handover.status}. ${handover.orderIds.size} orders settled.")
}

fun BlueGasStore.writeOffCashDifference(
    handoverId: String,
    approverName: String,
    reason: String
): Pair<Boolean, String> {
    val handover = cashHandovers.find { it.id == handoverId }
        ?: return Pair(false, "Cash handover $handoverId not found.")

    if (handover.status != "RECONCILED_WITH_DIFFERENCE") {
        return Pair(false, "Handover is not in RECONCILED_WITH_DIFFERENCE state.")
    }
    if (!handover.approverName.isNullOrBlank()) {
        return Pair(false, "Difference on $handoverId has already been approved/written off.")
    }
    if (approverName.isBlank() || reason.isBlank()) {
        return Pair(false, "Approver name and reason are both required.")
    }

    handover.approverName = approverName

    val exc = reconExceptions.find { it.description.contains(handoverId) && it.status == "OPEN" }
    if (exc != null) {
        exc.status = "RESOLVED"
        exc.resolution = "Written off by $approverName: $reason"
    }

    logAudit("CashHandover", handoverId, "MANUAL_FINANCIAL_OVERRIDE", approverName, AccountableRole.FINANCE, "Difference written off: $reason")
    notifyChanged()
    return Pair(true, "Difference on $handoverId written off by $approverName.")
}

fun BlueGasStore.retryFbr(orderId: String): Pair<Boolean, String> {
    val posting = fbrPostings.find { it.orderId == orderId }
        ?: return Pair(false, "FBR posting record not found for order $orderId.")

    if (posting.status != "QUEUED" && posting.status != "FAILED") {
        return Pair(false, "Posting for order $orderId is already ${posting.status}.")
    }

    posting.attempts += 1
    posting.lastAttemptAt = System.currentTimeMillis()

    if (taxConfig.simulateFbrOutage) {
        posting.status = "FAILED"
        logAudit("FbrPosting", orderId, "RETRY_FBR_OUTAGE", "Finance Lead", AccountableRole.FINANCE, "FBR outage simulated; kept in queue")
        notifyChanged()
        return Pair(true, "FBR still unavailable — kept in queue (EXC-20)")
    } else {
        posting.status = "POSTED"
        val invoice = "FBR-26-" + (100000..999999).random()
        posting.invoiceNo = invoice
        val order = orders.find { it.id == orderId }
        order?.fbrStatus = "POSTED"
        order?.fbrInvoiceNo = invoice

        // Resolve any EXC-20 exceptions for this order
        reconExceptions.filter { it.code == "EXC-20" && it.description.contains(orderId) && it.status == "OPEN" }
            .forEach {
                it.status = "RESOLVED"
                it.resolution = "Successfully posted to FBR: $invoice"
            }

        logAudit("FbrPosting", orderId, "RETRY_FBR_SUCCESS", "Finance Lead", AccountableRole.FINANCE, "Invoice posted: $invoice")
        notifyChanged()
        return Pair(true, "Posted to FBR: $invoice")
    }
}

fun BlueGasStore.retryAllFbr(): Pair<Boolean, String> {
    val queued = fbrPostings.filter { it.status == "QUEUED" || it.status == "FAILED" }
    if (queued.isEmpty()) {
        return Pair(true, "No queued or failed FBR postings.")
    }

    var posted = 0
    var stillQueued = 0

    for (p in queued) {
        val (ok, msg) = retryFbr(p.orderId)
        if (msg.startsWith("Posted to FBR")) {
            posted++
        } else {
            stillQueued++
        }
    }

    return Pair(true, "$posted posted, $stillQueued still queued")
}

fun BlueGasStore.setTaxConfig(
    gstBps: Int,
    furtherTaxBps: Int,
    furtherTaxApplies: Boolean,
    fbrPostingEnabled: Boolean,
    simulateFbrOutage: Boolean,
    actor: String = "Finance Lead"
): Pair<Boolean, String> {
    if (gstBps !in 0..10000 || furtherTaxBps !in 0..10000) {
        return Pair(false, "Tax rates must be between 0 and 10000 basis points (0-100%).")
    }

    val oldConfig = taxConfig
    taxConfig = TaxConfig(
        gstBps = gstBps,
        furtherTaxBps = furtherTaxBps,
        furtherTaxApplies = furtherTaxApplies,
        fbrPostingEnabled = fbrPostingEnabled,
        simulateFbrOutage = simulateFbrOutage
    )

    logAudit("TaxConfig", "GLOBAL", "UPDATE_TAX_CONFIG", actor, AccountableRole.FINANCE, "GST: ${oldConfig.gstBps} -> $gstBps bps, FurtherTax: ${oldConfig.furtherTaxBps} -> $furtherTaxBps bps, Outage: $simulateFbrOutage")
    notifyChanged()
    return Pair(true, "Tax settings saved. New orders use the new rates; existing orders keep theirs.")
}

fun BlueGasStore.recordDistributorPayment(
    distributorId: String,
    amountPaisa: Long,
    note: String
): Pair<Boolean, String> {
    if (amountPaisa <= 0L) {
        return Pair(false, "Payment amount must be greater than zero.")
    }
    val dist = distributors.find { it.id == distributorId }
        ?: return Pair(false, "Distributor $distributorId not found.")

    val entry = DistributorAccountEntry(
        id = "ENTRY-" + UUID.randomUUID().toString().take(6),
        distributorId = distributorId,
        type = "PAYMENT",
        creditPaisa = amountPaisa,
        note = note,
        timestamp = System.currentTimeMillis()
    )
    distributorAccountEntries.add(entry)
    logAudit("DistributorAccount", distributorId, "RECORD_PAYMENT", "Finance Lead", AccountableRole.FINANCE, "Payment of ${formatPaisaToPkr(amountPaisa)} recorded: $note")
    notifyChanged()
    return Pair(true, "Payment of ${formatPaisaToPkr(amountPaisa)} recorded for ${dist.name}.")
}

fun BlueGasStore.retryCapture(paymentId: String): Pair<Boolean, String> {
    val payment = payments.find { it.id == paymentId }
        ?: return Pair(false, "Payment $paymentId not found.")

    if (payment.state != PaymentState.ADJUSTMENT_FAILED) {
        return Pair(false, "Payment is not in ADJUSTMENT_FAILED state.")
    }

    PaymentStateMachine.transition(payment, PaymentState.FINAL_CAPTURE_PENDING, "Finance Lead", "Retrying capture", paymentHistory)
    PaymentStateMachine.transition(payment, PaymentState.CAPTURED, "Payment Gateway", "Capture succeeded on retry", paymentHistory)
    PaymentStateMachine.transition(payment, PaymentState.SETTLED, "Payment Gateway", "Settled after capture", paymentHistory)

    payment.settledAmountPaisa = payment.finalAmountPaisa
    payment.capturedAmountPaisa = payment.finalAmountPaisa
    postLedger(payment.orderId, "SETTLEMENT", debitPaisa = payment.settledAmountPaisa, description = "Online capture retry settlement")

    logAudit("Payment", paymentId, "RETRY_CAPTURE_SUCCESS", "Finance Lead", AccountableRole.FINANCE, "Capture retried and settled for ${formatPaisaToPkr(payment.settledAmountPaisa)}")
    notifyChanged()
    return Pair(true, "Capture retried and payment settled.")
}

fun BlueGasStore.approveCashHandover(handoverId: String, note: String = "Approved by Finance", actor: String = "Finance Lead"): Pair<Boolean, String> {
    val handover = cashHandovers.find { it.id == handoverId } ?: return Pair(false, "Handover not found")
    return reconcileCashHandover(handoverId, handover.declaredPaisa, if (handover.declaredPaisa != handover.expectedPaisa) note else null, actor)
}

fun BlueGasStore.disputeCashHandover(handoverId: String, reason: String, actor: String = "Finance Lead"): Pair<Boolean, String> {
    val handover = cashHandovers.find { it.id == handoverId } ?: return Pair(false, "Handover not found")
    handover.status = "DISPUTED"
    handover.reason = reason
    handover.reconciledAt = System.currentTimeMillis()
    handover.reconciledBy = actor
    val diff = handover.declaredPaisa - handover.expectedPaisa
    handover.differencePaisa = diff
    reconExceptions.add(
        ReconciliationException(
            id = "EXC-CSH-" + java.util.UUID.randomUUID().toString().take(6),
            code = "EXC-21",
            description = "Disputed cash handover $handoverId: $reason",
            ownerRole = AccountableRole.FINANCE
        )
    )
    logAudit("CashHandover", handoverId, "DISPUTE_CASH", actor, AccountableRole.FINANCE, "Disputed: $reason")
    notifyChanged()
    return Pair(true, "Handover $handoverId marked as DISPUTED.")
}

fun BlueGasStore.reconcileGatewayPout(
    gateway: String,
    batchRef: String,
    expectedPaisa: Long,
    settledPaisa: Long,
    feePaisa: Long,
    actor: String = "Finance Lead"
): Pair<Boolean, String> {
    val diff = (expectedPaisa - feePaisa) - settledPaisa
    logAudit("GatewayPayout", batchRef, "RECONCILE_PAYOUT", actor, AccountableRole.FINANCE, "Reconciled $gateway batch $batchRef: Expected=${formatPaisaToPkr(expectedPaisa)}, Settled=${formatPaisaToPkr(settledPaisa)}, Fee=${formatPaisaToPkr(feePaisa)}, Diff=${formatPaisaToPkr(diff)}")
    notifyChanged()
    return Pair(true, "Gateway payout batch $batchRef reconciled successfully.")
}
