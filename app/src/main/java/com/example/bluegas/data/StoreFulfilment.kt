package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.PaymentStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import java.util.UUID

// --- Distributor Fulfilment Functions ---

fun BlueGasStore.distributorAccept(orderId: String, distributorId: String? = null): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_ASGN) {
        return Pair(false, "Order is not in Assigned status (Status: ${order.status.displayName}).")
    }

    order.acceptedAt = System.currentTimeMillis()
    val tAcpt = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_ACPT,
        actor = "Distributor Lead",
        role = AccountableRole.DISTRIBUTOR,
        trigger = "Distributor accepted SLA"
    )
    if (tAcpt is TransitionResult.Failure) {
        return Pair(false, tAcpt.errorMessage)
    }
    recordHistory(tAcpt, "DISTRIBUTOR_ACCEPTED")
    notifyChanged()
    return Pair(true, "Order ${order.id} accepted. Ready to start preparation.")
}

fun BlueGasStore.distributorAcceptOrder(orderId: String, actor: String = "Distributor"): Pair<Boolean, String> =
    distributorAccept(orderId)

fun BlueGasStore.distributorDecline(orderId: String, reason: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_ASGN) {
        return Pair(false, "Order is not in Assigned status.")
    }
    return raiseException(orderId, ExceptionReason.NO_ACCEPTANCE, "Distributor declined order: $reason")
}

fun BlueGasStore.startPreparing(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_ACPT) {
        return Pair(false, "Order must be in Accepted state before starting preparation (Status: ${order.status.displayName}).")
    }

    val tPrep = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_PREP,
        actor = "Distributor Lead",
        role = AccountableRole.DISTRIBUTOR,
        trigger = "Distributor initiated cylinder preparation"
    )
    if (tPrep is TransitionResult.Failure) {
        return Pair(false, tPrep.errorMessage)
    }
    recordHistory(tPrep, "START_PREPARING")
    notifyChanged()
    return Pair(true, "Order is now in Preparing. Allocate cylinder to proceed.")
}

fun BlueGasStore.allocateCylinder(orderId: String, cylinderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_PREP) {
        return Pair(false, "Cylinder allocation is only permitted during Preparing (Current: ${order.status.displayName}).")
    }

    val cyl = cylinders.find { it.serialNumber == cylinderId }
        ?: return Pair(false, "Cylinder $cylinderId does not exist in master registry.")

    if (cyl.hseHold) {
        return Pair(false, "Cylinder $cylinderId is on HSE safety hold and cannot be allocated.")
    }

    if (cyl.allocatedOrderId != null && cyl.allocatedOrderId != orderId) {
        return Pair(false, "Collision: Serial $cylinderId is already allocated to order ${cyl.allocatedOrderId}.")
    }

    // Release old cylinder if any
    order.allocatedSerial?.let { oldSerial ->
        if (oldSerial != cylinderId) {
            val oldCyl = cylinders.find { it.serialNumber == oldSerial }
            oldCyl?.let {
                val idx = cylinders.indexOf(it)
                cylinders[idx] = it.copy(status = InventoryState.INV_AVAIL, allocatedOrderId = null)
            }
        }
    }

    val idx = cylinders.indexOf(cyl)
    cylinders[idx] = cyl.copy(
        status = InventoryState.INV_SERIAL,
        allocatedOrderId = orderId
    )
    order.allocatedSerial = cylinderId

    logAudit("Cylinder", cylinderId, "ALLOCATE_SERIAL", "Distributor Lead", AccountableRole.INVENTORY, "Bound physical cylinder $cylinderId to order $orderId")
    notifyChanged()
    return Pair(true, "Cylinder $cylinderId allocated to order $orderId.")
}

fun BlueGasStore.allocateCylinderSerial(orderId: String, serialNumber: String, actor: String = "Distributor"): Pair<Boolean, String> =
    allocateCylinder(orderId, serialNumber)

fun BlueGasStore.reportNoSuitableCylinder(orderId: String, untaggedOnly: Boolean = false): Pair<Boolean, String> {
    val reason = ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE
    val details = if (untaggedOnly) "Only untagged cylinders available at depot (EXC-22)" else "No suitable cylinder in stock (EXC-06)"
    return raiseException(orderId, reason, details)
}

fun BlueGasStore.assignRider(orderId: String, riderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_PREP) {
        return Pair(false, "Order must be in Preparing to assign rider.")
    }
    if (order.allocatedSerial == null) {
        return Pair(false, "Cylinder must be allocated before assigning a rider.")
    }

    val rider = riders.find { it.id == riderId } ?: return Pair(false, "Rider not found.")
    order.assignedRiderId = riderId
    order.riderTaskStatus = RiderTaskStatus.OFFERED

    addNotification(
        surface = AppSurface.RIDER,
        title = "New Delivery Task",
        message = "New delivery task for Order ${order.id} offered. Cylinder: ${order.allocatedSerial}.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-03"
    )

    notifyChanged()
    return Pair(true, "Task offered to rider ${rider.name}.")
}

fun BlueGasStore.markReadyForPickup(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.fulfilmentMode != FulfilmentMode.PICKUP) {
        return Pair(false, "Only pickup orders can be marked ready for pickup.")
    }
    if (order.allocatedSerial == null) {
        return Pair(false, "Cylinder must be allocated before staging for pickup.")
    }

    val tRdy = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_RDYPU,
        actor = "Distributor Lead",
        role = AccountableRole.DISTRIBUTOR,
        trigger = "Staged at counter for pickup"
    )
    if (tRdy is TransitionResult.Failure) {
        return Pair(false, tRdy.errorMessage)
    }
    recordHistory(tRdy, "READY_FOR_PICKUP")

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Ready for Collection",
        message = "Your cylinder is ready for collection at the depot counter.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-18"
    )

    notifyChanged()
    return Pair(true, "Order is staged and ready for pickup.")
}

fun BlueGasStore.customerArrivedAtDepot(orderId: String, by: String = "CUSTOMER"): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_RDYPU) {
        return Pair(false, "Order is not ready for pickup (Status: ${order.status.displayName}).")
    }

    val tAtHo = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_ATHO,
        actor = if (by == "CUSTOMER") "Customer" else "Counter Staff",
        role = AccountableRole.DISTRIBUTOR,
        trigger = "Customer arrived at depot counter"
    )
    if (tAtHo is TransitionResult.Failure) {
        return Pair(false, tAtHo.errorMessage)
    }
    recordHistory(tAtAtHo = tAtHo)
    notifyChanged()
    return Pair(true, "Customer arrived at counter.")
}

private fun BlueGasStore.recordHistory(tAtAtHo: TransitionResult) {
    recordHistory(tAtAtHo, "AT_HANDOVER")
}

fun BlueGasStore.completeCounterHandover(
    orderId: String,
    pinOrRefChecked: Boolean,
    scannedCylinderId: String,
    amountReceivedPaisa: Long? = null,
    emptyBrand: String? = null,
    emptySize: String? = null,
    emptyCylinderId: String? = null
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_ATHO) {
        return Pair(false, "Order is not at counter handover.")
    }

    // Handover Complete -> Completed
    val tHoc = OrderStateMachine.transition(order, OrderStatus.ORD_HOC, "Counter Staff", AccountableRole.DISTRIBUTOR, "Counter handover checks passed")
    if (tHoc is TransitionResult.Failure) return Pair(false, tHoc.errorMessage)
    recordHistory(tHoc, "HANDOVER_COMPLETE")

    val tComp = OrderStateMachine.transition(order, OrderStatus.ORD_COMP, "Counter Staff", AccountableRole.DISTRIBUTOR, "Counter collection finalized")
    if (tComp is TransitionResult.Failure) return Pair(false, tComp.errorMessage)
    recordHistory(tComp, "COMPLETED")

    order.completedAt = System.currentTimeMillis()
    order.installationResult = InstallationResult.NOT_APPLICABLE
    order.podReference = "DEPOT-POD-" + UUID.randomUUID().toString().take(6)

    // Handle Empty exchange
    if (order.orderType == OrderType.REFILL_EXCHANGE && emptyBrand != null) {
        if (settings.approvedEmptyBrands.contains(emptyBrand)) {
            order.emptyCollectedBrand = emptyBrand
            order.emptyCollectedCylinderId = emptyCylinderId
            val targetSku = order.items.firstOrNull { it.lineType == "GAS" }?.sku ?: order.items.firstOrNull()?.sku
            if (targetSku != null) {
                val stock = stockBalances.find { it.pointId == order.fulfilmentPointId && it.skuId == targetSku }
                stock?.let { it.emptyQty++ }
            }
        }
    }

    // Payment Handling
    val payment = activePaymentFor(orderId)
    if (payment != null) {
        if (payment.method == PaymentMethod.CASH_ON_DELIVERY) {
            val rec = amountReceivedPaisa ?: order.totalAmountPaisa
            order.amountReceivedPaisa = rec
            order.cashDifferencePaisa = rec - order.totalAmountPaisa
            PaymentStateMachine.transition(payment, PaymentState.FINAL_AMOUNT_DETERMINED, "Counter Staff", "Final counter amount confirmed", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.CASH_COLLECTION_PENDING, "Counter Staff", "Cash pending", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.CASH_COLLECTED, "Counter Staff", "Counter cash collected", paymentHistory)
            codCollections.add(
                CodCollection(
                    id = "COD-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    riderId = "DEPOT-COUNTER",
                    expectedAmountPaisa = order.totalAmountPaisa,
                    collectedAmountPaisa = rec
                )
            )
        } else {
            // Online settled
            PaymentStateMachine.transition(payment, PaymentState.FINAL_AMOUNT_DETERMINED, "Counter Staff", "Final counter amount confirmed", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.NO_ADJUSTMENT, "Counter Staff", "No counter adjustment", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.SETTLED, "Payment Gateway", "Settled at counter handover", paymentHistory)
            postLedger(orderId, "SETTLEMENT", debitPaisa = payment.baseAmountPaisa, description = "Online counter settlement")
        }
    }

    // Update cylinder custody
    order.allocatedSerial?.let { s ->
        val cyl = cylinders.find { it.serialNumber == s }
        cyl?.let {
            val idx = cylinders.indexOf(it)
            cylinders[idx] = it.copy(
                status = InventoryState.INV_CONSUMED,
                custody = CylinderCustody.CUSTOMER_PREMISES,
                currentLocationLabel = "With Customer (${order.customerId})"
            )
        }
    }

    // Post Revenue Ledger
    postLedger(orderId, "REVENUE_GAS", debitPaisa = order.subtotalPaisa, description = "Counter gas sale revenue")
    if (order.depositTotalPaisa > 0) {
        postLedger(orderId, "DEPOSIT_LIABILITY", creditPaisa = order.depositTotalPaisa, description = "Counter deposit liability")
    }

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Pickup Completed",
        message = "Your order ${order.id} has been collected. Thank you for choosing BLUE GAS!",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-06"
    )

    notifyChanged()
    return Pair(true, "Pickup handover completed successfully.")
}

// --- Rider Fulfilment Functions ---

fun BlueGasStore.riderAcceptTask(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    order.riderTaskStatus = RiderTaskStatus.ACCEPTED
    if (order.status == OrderStatus.ORD_ASGN) {
        order.acceptedAt = System.currentTimeMillis()
        val tAcpt = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ACPT,
            actor = getActiveRider()?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = "Rider accepted delivery task"
        )
        if (tAcpt is TransitionResult.Failure) return Pair(false, tAcpt.errorMessage)
        recordHistory(tAcpt, "RIDER_ACCEPTED")
    }
    notifyChanged()
    return Pair(true, "Delivery task accepted.")
}

fun BlueGasStore.riderDeclineTask(orderId: String, reason: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    order.riderTaskStatus = RiderTaskStatus.DECLINED
    order.assignedRiderId = null
    logAudit("Order", orderId, "RIDER_DECLINE_TASK", getActiveRider()?.name ?: "Rider", AccountableRole.DISTRIBUTOR, "Rider declined task: $reason")

    addNotification(
        surface = AppSurface.STAFF,
        title = "Rider Declined Task",
        message = "Rider declined task for order ${order.id} ($reason). Please reassign.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-03",
        staffRole = StaffSubRole.DISTRIBUTOR
    )

    notifyChanged()
    return Pair(true, "Task declined. Distributor notified to reassign.")
}

fun BlueGasStore.riderVerifyCylinder(orderId: String, scannedId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    val allocated = order.allocatedSerial ?: return Pair(false, "No cylinder allocated to this order.")

    if (scannedId != allocated && settings.cylinderIdMode != CylinderIdMode.OPTION_B_PHASE_IN) {
        return Pair(false, "Scanned serial $scannedId does not match allocated serial $allocated!")
    }
    return Pair(true, "Cylinder $scannedId verified successfully.")
}

fun BlueGasStore.riderStartTrip(orderId: String, isReplay: Boolean = false): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")

    if (isRiderOffline && !isReplay) {
        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
            orderId = orderId,
            action = "START_TRIP",
            fromStatus = order.status,
            toStatus = OrderStatus.ORD_DISP,
            scannedSerial = order.allocatedSerial
        )
        riderOutbox.add(event)
        notifyChanged()
        return Pair(true, "Trip start queued in Offline Outbox.")
    }

    val rider = getActiveRider()
    val tDisp = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_DISP,
        actor = rider?.name ?: "Rider",
        role = AccountableRole.OPERATIONS,
        trigger = "Rider commenced delivery trip"
    )
    if (tDisp is TransitionResult.Failure) {
        return Pair(false, tDisp.errorMessage)
    }
    recordHistory(tDisp, "DISPATCHED")

    // Update custody to Rider Van
    order.allocatedSerial?.let { s ->
        val cyl = cylinders.find { it.serialNumber == s }
        cyl?.let {
            val idx = cylinders.indexOf(it)
            cylinders[idx] = it.copy(status = InventoryState.INV_TRANSIT, custody = CylinderCustody.RIDER_VAN)
        }
    }

    deliveries.add(
        Delivery(
            id = "DEL-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            riderId = rider?.id ?: "RIDER-001",
            dispatchedAt = System.currentTimeMillis()
        )
    )

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Order on the Way!",
        message = "Rider ${rider?.name ?: ""} is en route with your cylinder.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-03"
    )

    notifyChanged()
    return Pair(true, "Delivery trip started.")
}

fun BlueGasStore.riderArrive(orderId: String, isReplay: Boolean = false): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")

    if (isRiderOffline && !isReplay) {
        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
            orderId = orderId,
            action = "ARRIVE_AT_DOORSTEP",
            fromStatus = order.status,
            toStatus = OrderStatus.ORD_ATHO
        )
        riderOutbox.add(event)
        notifyChanged()
        return Pair(true, "Arrival queued in Offline Outbox.")
    }

    val tAtHo = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_ATHO,
        actor = getActiveRider()?.name ?: "Rider",
        role = AccountableRole.OPERATIONS,
        trigger = "Rider arrived at customer doorstep"
    )
    if (tAtHo is TransitionResult.Failure) {
        return Pair(false, tAtHo.errorMessage)
    }
    recordHistory(tAtHo, "AT_HANDOVER")

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Rider Has Arrived",
        message = "Rider is at your doorstep. Please share your handover PIN.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-04"
    )

    notifyChanged()
    return Pair(true, "Rider arrived at doorstep.")
}

fun BlueGasStore.riderReportIssue(
    orderId: String,
    reason: ExceptionReason,
    details: String,
    safetyConcern: Boolean = false,
    isReplay: Boolean = false
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")

    if (isRiderOffline && !isReplay) {
        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
            orderId = orderId,
            action = "REPORT_ISSUE",
            reasonCode = reason.name,
            payloadNotes = details,
            handoverPayload = if (safetyConcern) mapOf("safetyConcern" to "true") else null
        )
        riderOutbox.add(event)
        notifyChanged()
        return Pair(true, "Issue reported and queued in Offline Outbox.")
    }

    // Safety incident escalation (v0.4 §16.1 & EXC-10)
    if (safetyConcern || details.contains("unsafe", ignoreCase = true) || details.contains("leak", ignoreCase = true) || details.contains("smell", ignoreCase = true) || (reason == ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE && (details.contains("gas", ignoreCase = true) || details.contains("hazard", ignoreCase = true)))) {
        raiseIncident(
            source = "DELIVERY",
            description = "Doorstep safety alert: $details",
            orderId = orderId,
            cylinderId = order.allocatedSerial,
            location = "Customer doorstep",
            peopleInvolved = "Customer & Rider (${getActiveRider()?.name ?: "Rider"})",
            evidenceCaptured = true,
            actor = getActiveRider()?.name ?: "Rider"
        )
    }

    return raiseException(orderId, reason, details, actor = getActiveRider()?.name ?: "Rider")
}

/**
 * Compares customer-provided handover PIN against the assigned PIN on the order.
 *
 * @param orderId Identifier of the order being delivered.
 * @param enteredPin The 4-digit code provided by the customer at the doorstep.
 * @return True if the code matches order.handoverPin exactly, false otherwise.
 */
fun BlueGasStore.verifyHandoverPin(orderId: String, enteredPin: String): Boolean {
    val order = orders.find { it.id == orderId } ?: return false
    val expected = order.handoverPin ?: return false
    return expected.trim() == enteredPin.trim()
}

/**
 * Completes final doorstep delivery, validates handover proof, records physical cylinder custody,
 * updates COD / online payment lifecycle, and posts financial ledger transactions.
 *
 * Per BRD L3, Cash on Delivery payments transition to CASH_COLLECTED (not SETTLED),
 * and settle only when Finance reconciles cash via [reconcileCashHandover].
 */
fun BlueGasStore.completeHandover(
    orderId: String,
    checklistAnswers: Map<String, Boolean> = emptyMap(),
    installationResult: InstallationResult = InstallationResult.INSTALLED,
    notInstalledReason: String? = null,
    pinVerified: Boolean = false,
    pinNotVerifiedReason: String? = null,
    photoCaptured: Boolean = false,
    signatureCaptured: Boolean = false,
    signatureUnavailableReason: String? = null,
    evidenceConsent: Boolean = false,
    signerName: String? = "Customer",
    amountReceivedPaisa: Long? = null,
    changeReturnedPaisa: Long? = null,
    cashDifferenceReason: String? = null,
    emptyCollected: Boolean = false,
    emptyBrand: String? = null,
    emptySize: String? = null,
    emptySerial: String? = null,
    isReplay: Boolean = false,
    proofOfDeliveryNote: String? = null,
    actor: String? = null,
    nonTechInstallationPassed: Boolean = false,
    collectedCashPaisa: Long? = null,
    customerSignature: String? = null,
    photoEvidenceCaptured: Boolean = false,
    emptyCylinderCollected: Boolean = false,
    emptyCylinderSerial: String? = null
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")

    if (!pinVerified && pinNotVerifiedReason.isNullOrBlank()) {
        return Pair(false, "A reason is needed when the handover code was not checked.")
    }
    if (installationResult == InstallationResult.NOT_INSTALLED && notInstalledReason.isNullOrBlank()) {
        return Pair(false, "A reason is needed when installation is marked Not installed.")
    }

    if (isRiderOffline && !isReplay) {
        val checklistJoined = checklistAnswers.entries.joinToString(";") { "${it.key}=${it.value}" }
        val payloadMap = mutableMapOf<String, String>(
            "pinVerified" to pinVerified.toString(),
            "installationResult" to installationResult.name,
            "photoCaptured" to photoCaptured.toString(),
            "evidenceConsent" to evidenceConsent.toString(),
            "signatureCaptured" to signatureCaptured.toString(),
            "emptyCollected" to emptyCollected.toString()
        )
        pinNotVerifiedReason?.let { payloadMap["pinReason"] = it }
        notInstalledReason?.let { payloadMap["notInstalledReason"] = it }
        if (checklistJoined.isNotEmpty()) {
            payloadMap["checklist"] = checklistJoined
        }
        emptyBrand?.let { payloadMap["emptyBrand"] = it }
        emptySerial?.let { payloadMap["emptySerial"] = it }
        amountReceivedPaisa?.let { payloadMap["amountReceivedPaisa"] = it.toString() }
        changeReturnedPaisa?.let { payloadMap["changeReturnedPaisa"] = it.toString() }
        cashDifferenceReason?.let { payloadMap["cashDifferenceReason"] = it }
        signatureUnavailableReason?.let { payloadMap["signatureUnavailableReason"] = it }
        signerName?.let { payloadMap["signerName"] = it }

        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
            orderId = orderId,
            action = "COMPLETE_HANDOVER",
            fromStatus = order.status,
            toStatus = OrderStatus.ORD_COMP,
            scannedSerial = order.allocatedSerial,
            handoverPayload = payloadMap
        )
        riderOutbox.add(event)
        notifyChanged()
        return Pair(true, "Handover completion queued in Offline Outbox.")
    }

    val actualActor = actor ?: getActiveRider()?.name ?: "Rider"
    val actualPhotoCaptured = photoCaptured || photoEvidenceCaptured
    val actualSignatureCaptured = signatureCaptured || (customerSignature != null)
    val actualEmptyCollected = emptyCollected || emptyCylinderCollected
    val actualEmptySerial = emptySerial ?: emptyCylinderSerial
    val actualAmountReceived = amountReceivedPaisa ?: collectedCashPaisa

    // Handover Complete -> Completed
    val tHoc = OrderStateMachine.transition(order, OrderStatus.ORD_HOC, actualActor, AccountableRole.OPERATIONS, "POD checklist and checks verified")
    if (tHoc is TransitionResult.Failure) return Pair(false, tHoc.errorMessage)
    recordHistory(tHoc, "HANDOVER_COMPLETE")

    val tComp = OrderStateMachine.transition(order, OrderStatus.ORD_COMP, actualActor, AccountableRole.OPERATIONS, "Handover completed with POD")
    if (tComp is TransitionResult.Failure) return Pair(false, tComp.errorMessage)
    recordHistory(tComp, "COMPLETED")

    order.completedAt = System.currentTimeMillis()
    order.podReference = "POD-" + UUID.randomUUID().toString().take(8).uppercase()
    order.installationResult = installationResult
    order.installationNotDoneReason = notInstalledReason

    // Record Delivery
    val delivery = deliveries.find { it.orderId == orderId } ?: Delivery(
        id = "DEL-" + UUID.randomUUID().toString().take(6),
        orderId = orderId,
        riderId = order.assignedRiderId ?: "RIDER-001"
    )
    val delIdx = deliveries.indexOf(delivery)
    val installationPassed = installationResult == InstallationResult.INSTALLED &&
            (if (checklistAnswers.isNotEmpty()) checklistAnswers.values.all { it } else nonTechInstallationPassed)

    val updatedDelivery = delivery.copy(
        completedAt = System.currentTimeMillis(),
        nonTechnicalInstallationPassed = installationPassed,
        proofOfDeliveryNote = proofOfDeliveryNote ?: "Delivered. Installation: ${installationResult.displayName}.",
        podReference = order.podReference,
        customerSignatureCaptured = actualSignatureCaptured,
        signatureUnavailableReason = signatureUnavailableReason,
        photoEvidenceCaptured = actualPhotoCaptured,
        signerName = signerName,
        changeReturnedPaisa = changeReturnedPaisa,
        installationChecklist = checklistAnswers,
        installationResult = installationResult,
        notInstalledReason = notInstalledReason,
        pinVerified = pinVerified,
        pinNotVerifiedReason = pinNotVerifiedReason,
        evidenceConsent = evidenceConsent
    )
    if (delIdx != -1) deliveries[delIdx] = updatedDelivery else deliveries.add(updatedDelivery)

    // Handle Empty exchange (BR-STK-07, UAT-32)
    if (order.orderType == OrderType.REFILL_EXCHANGE && actualEmptyCollected) {
        val brand = emptyBrand ?: "BLUE GAS"
        if (settings.approvedEmptyBrands.contains(brand)) {
            order.emptyCollectedBrand = brand
            order.emptyCollectedCylinderId = actualEmptySerial
            val targetSku = order.items.firstOrNull { it.lineType == "GAS" }?.sku ?: order.items.firstOrNull()?.sku
            if (targetSku != null) {
                val stock = stockBalances.find { it.pointId == order.fulfilmentPointId && it.skuId == targetSku }
                stock?.let { it.emptyQty++ }
            }
        } else {
            // Declined
            order.emptyCollectedBrand = "DECLINED_OTHER_BRAND"
        }
    }

    // Payment Handling (BR-CSH-01/02)
    val payment = activePaymentFor(orderId)
    if (payment != null) {
        if (payment.method == PaymentMethod.CASH_ON_DELIVERY) {
            val rec = actualAmountReceived ?: order.totalAmountPaisa
            order.amountReceivedPaisa = rec
            order.cashDifferencePaisa = rec - order.totalAmountPaisa
            order.cashDifferenceReason = cashDifferenceReason

            PaymentStateMachine.transition(payment, PaymentState.FINAL_AMOUNT_DETERMINED, actualActor, "Final COD amount determined", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.CASH_COLLECTION_PENDING, actualActor, "Cash collection pending", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.CASH_COLLECTED, actualActor, "Cash collected by rider", paymentHistory)
            // Per BRD L3: COD payments stop at CASH_COLLECTED. Settle only when Finance reconciles cash.
            payment.capturedAmountPaisa = rec

            codCollections.add(
                CodCollection(
                    id = "COD-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    riderId = order.assignedRiderId ?: "RIDER-001",
                    expectedAmountPaisa = order.totalAmountPaisa,
                    collectedAmountPaisa = rec
                )
            )

            // Short/difference collection creates EXC-21 (UAT-24)
            if (rec != order.totalAmountPaisa) {
                reconExceptions.add(
                    ReconciliationException(
                        id = "EXC21-" + UUID.randomUUID().toString().take(6),
                        code = "EXC-21",
                        description = "Cash difference on order $orderId: Expected ${formatRupees(order.totalAmountPaisa)}, Collected ${formatRupees(rec)}. Reason: ${cashDifferenceReason ?: "Cash received differs from the amount due"}",
                        ownerRole = AccountableRole.FINANCE
                    )
                )
            }
        } else {
            // Online: Settled
            PaymentStateMachine.transition(payment, PaymentState.FINAL_AMOUNT_DETERMINED, actualActor, "Final amount confirmed", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.NO_ADJUSTMENT, actualActor, "No adjustment", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.SETTLED, "Payment Gateway", "Settled at doorstep handover", paymentHistory)
            postLedger(orderId, "SETTLEMENT", debitPaisa = payment.baseAmountPaisa, description = "Online gateway settlement")
        }
    }

    // Update cylinder custody
    order.allocatedSerial?.let { s ->
        val cyl = cylinders.find { it.serialNumber == s }
        cyl?.let {
            val idx = cylinders.indexOf(it)
            cylinders[idx] = it.copy(
                status = InventoryState.INV_CONSUMED,
                custody = CylinderCustody.CUSTOMER_PREMISES,
                currentLocationLabel = "With Customer (${order.customerId})"
            )
        }
        customerHoldings.add(
            CustomerCylinderHolding(
                id = "HOLDING-" + UUID.randomUUID().toString().take(6),
                customerId = order.customerId,
                cylinderSerial = s,
                ownership = if (order.orderType == OrderType.NEW_CYLINDER && settings.newCylinderModel == NewCylinderModel.SOLD) CylinderOwnership.OWNED_BY_CUSTOMER else CylinderOwnership.DEPOSIT_HELD,
                depositPaisa = order.depositTotalPaisa,
                acquiredAt = System.currentTimeMillis()
            )
        )
    }

    // Post Revenue Ledger
    postLedger(orderId, "REVENUE_GAS", debitPaisa = order.subtotalPaisa, description = "LPG refill gas revenue")
    postLedger(orderId, "DELIVERY_FEE", debitPaisa = order.deliveryFeePaisa, description = "Doorstep delivery fee")
    postLedger(orderId, "PLATFORM_FEE", debitPaisa = order.platformFeePaisa, description = "Platform convenience fee")
    if (order.taxPaisa > 0) {
        postLedger(orderId, "TAX_PAYABLE", creditPaisa = order.taxPaisa, description = "GST and sales tax payable")
    }
    if (order.depositTotalPaisa > 0) {
        postLedger(orderId, "DEPOSIT_LIABILITY", creditPaisa = order.depositTotalPaisa, description = "Customer cylinder deposit liability")
    }

    // FBR Posting (BR-TAX-03/04)
    if (taxConfig.fbrPostingEnabled) {
        if (taxConfig.simulateFbrOutage) {
            order.fbrStatus = "QUEUED"
            fbrPostings.add(FbrPosting(orderId = orderId, status = "QUEUED"))
        } else {
            val inv = "FBR-26-" + (100000..999999).random()
            order.fbrStatus = "POSTED"
            order.fbrInvoiceNo = inv
            fbrPostings.add(FbrPosting(orderId = orderId, status = "POSTED", invoiceNo = inv))
        }
    } else {
        order.fbrStatus = "NOT_REQUIRED"
    }

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Order Delivered!",
        message = "Your cylinder has been delivered and verified. View your tax invoice & receipt.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-06"
    )

    notifyChanged()
    return Pair(true, "Handover completed successfully.")
}

fun BlueGasStore.riderSubmitCashHandover(
    riderId: String,
    recipient: String,
    declaredPaisa: Long,
    reason: String? = null
): Pair<Boolean, String> {
    val unhandedOrders = orders.filter {
        it.assignedRiderId == riderId && it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                it.status == OrderStatus.ORD_COMP &&
                activePaymentFor(it.id)?.state == PaymentState.CASH_COLLECTED &&
                cashHandovers.none { ch -> ch.orderIds.contains(it.id) }
    }
    if (unhandedOrders.isEmpty()) {
        return Pair(false, "No rider cash waiting for hand-over.")
    }
    val expected = unhandedOrders.sumOf { it.amountReceivedPaisa ?: it.totalAmountPaisa }
    val id = "CSH-HO-" + UUID.randomUUID().toString().take(6)
    cashHandovers.add(
        CashHandover(
            id = id,
            collectorType = "RIDER",
            collectorId = riderId,
            recipient = recipient,
            orderIds = unhandedOrders.map { it.id },
            expectedPaisa = expected,
            declaredPaisa = declaredPaisa,
            reason = reason
        )
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Cash hand-over submitted",
        message = "Cash hand-over $id (${formatRupees(declaredPaisa)}) submitted by rider $riderId.",
        canonicalStatus = "SUBMITTED",
        orderId = unhandedOrders.firstOrNull()?.id,
        code = "NTF-15",
        staffRole = StaffSubRole.FINANCE
    )
    logAudit("CashHandover", id, "SUBMIT_CASH_HANDOVER", riderId, AccountableRole.DISTRIBUTOR, "Submitted cash handover of ${formatRupees(declaredPaisa)} for ${unhandedOrders.size} orders")
    notifyChanged()
    return Pair(true, "Cash handover $id submitted for reconciliation.")
}

fun BlueGasStore.depotSubmitCashHandover(distributorId: String, declaredPaisa: Long): Pair<Boolean, String> {
    val dist = distributors.find { it.id == distributorId }
    val unhandedOrders = orders.filter {
        it.fulfilmentMode == FulfilmentMode.PICKUP && it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                it.status == OrderStatus.ORD_COMP &&
                (dist == null || (it.pickupPointId ?: it.fulfilmentPointId) == dist.pointId) &&
                activePaymentFor(it.id)?.state == PaymentState.CASH_COLLECTED &&
                cashHandovers.none { ch -> ch.orderIds.contains(it.id) }
    }
    if (unhandedOrders.isEmpty()) {
        return Pair(false, "No counter cash waiting for hand-over.")
    }
    val expected = unhandedOrders.sumOf { it.amountReceivedPaisa ?: it.totalAmountPaisa }
    val id = "CSH-DEP-" + UUID.randomUUID().toString().take(6)
    cashHandovers.add(
        CashHandover(
            id = id,
            collectorType = "DEPOT",
            collectorId = distributorId,
            recipient = "FINANCE",
            orderIds = unhandedOrders.map { it.id },
            expectedPaisa = expected,
            declaredPaisa = declaredPaisa
        )
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Cash hand-over submitted",
        message = "Depot counter cash hand-over $id (${formatRupees(declaredPaisa)}) submitted for $distributorId.",
        canonicalStatus = "SUBMITTED",
        orderId = unhandedOrders.firstOrNull()?.id,
        code = "NTF-15",
        staffRole = StaffSubRole.FINANCE
    )
    logAudit("CashHandover", id, "SUBMIT_CASH_HANDOVER", distributorId, AccountableRole.DISTRIBUTOR, "Submitted depot cash handover of ${formatRupees(declaredPaisa)} for ${unhandedOrders.size} orders")
    notifyChanged()
    return Pair(true, "Depot cash handover $id submitted to Finance.")
}

/**
 * Structured representation of offline handover completion inputs stored in [OfflineOutboxEvent.handoverPayload].
 */
data class ParsedHandoverData(
    val pinVerified: Boolean,
    val pinReason: String?,
    val installationResult: InstallationResult,
    val notInstalledReason: String?,
    val checklistAnswers: Map<String, Boolean>,
    val emptyCollected: Boolean,
    val emptyBrand: String?,
    val emptySerial: String?,
    val amountReceivedPaisa: Long?,
    val changeReturnedPaisa: Long?,
    val cashDifferenceReason: String?,
    val photoCaptured: Boolean,
    val evidenceConsent: Boolean,
    val signatureCaptured: Boolean,
    val signatureUnavailableReason: String?,
    val signerName: String?
)

/**
 * Parses raw key-value string map from an offline handover outbox payload into typed fields.
 *
 * @param payload Key-value pairs captured during offline doorstep delivery.
 * @return Parsed handover parameters with safe defaults.
 */
fun parseHandoverPayload(payload: Map<String, String>?): ParsedHandoverData {
    val p = payload ?: emptyMap()
    val pinVerified = p["pinVerified"]?.toBooleanStrictOrNull() ?: false
    val pinReason = p["pinReason"]
    val instResult = runCatching { InstallationResult.valueOf(p["installationResult"] ?: "INSTALLED") }
        .getOrDefault(InstallationResult.INSTALLED)
    val notInstalledReason = p["notInstalledReason"]

    val checklistAnswers = mutableMapOf<String, Boolean>()
    p["checklist"]?.split(";")?.forEach { pair ->
        val parts = pair.split("=")
        if (parts.size == 2) {
            checklistAnswers[parts[0]] = parts[1].toBooleanStrictOrNull() ?: false
        }
    }

    val emptyCollected = p["emptyCollected"]?.toBooleanStrictOrNull() ?: false
    val emptyBrand = p["emptyBrand"]
    val emptySerial = p["emptySerial"]
    val amountReceivedPaisa = p["amountReceivedPaisa"]?.toLongOrNull()
    val changeReturnedPaisa = p["changeReturnedPaisa"]?.toLongOrNull()
    val cashDifferenceReason = p["cashDifferenceReason"]
    val photoCaptured = p["photoCaptured"]?.toBooleanStrictOrNull() ?: false
    val evidenceConsent = p["evidenceConsent"]?.toBooleanStrictOrNull() ?: false
    val signatureCaptured = p["signatureCaptured"]?.toBooleanStrictOrNull() ?: false
    val signatureUnavailableReason = p["signatureUnavailableReason"]
    val signerName = p["signerName"]

    return ParsedHandoverData(
        pinVerified = pinVerified,
        pinReason = pinReason,
        installationResult = instResult,
        notInstalledReason = notInstalledReason,
        checklistAnswers = checklistAnswers,
        emptyCollected = emptyCollected,
        emptyBrand = emptyBrand,
        emptySerial = emptySerial,
        amountReceivedPaisa = amountReceivedPaisa,
        changeReturnedPaisa = changeReturnedPaisa,
        cashDifferenceReason = cashDifferenceReason,
        photoCaptured = photoCaptured,
        evidenceConsent = evidenceConsent,
        signatureCaptured = signatureCaptured,
        signatureUnavailableReason = signatureUnavailableReason,
        signerName = signerName
    )
}

fun BlueGasStore.replayRiderOutbox(): String {
    val pending = riderOutbox.filter { it.syncStatus == "PENDING" }.sortedBy { it.sequenceNumber }
    if (pending.isEmpty()) return "Offline outbox is empty."

    var applied = 0
    var conflicts = 0
    var rejected = 0

    for (event in pending) {
        val order = orders.find { it.id == event.orderId }
        if (order == null) {
            event.syncStatus = "REJECTED"
            event.rejectionReason = "Order not found"
            rejected++
            continue
        }

        when (event.action) {
            "START_PREPARING" -> {
                val (ok, _) = startPreparing(event.orderId)
                event.syncStatus = if (ok) "APPLIED" else "REJECTED"
                if (ok) applied++ else rejected++
            }
            "START_TRIP" -> {
                val (ok, _) = riderStartTrip(event.orderId, isReplay = true)
                event.syncStatus = if (ok) "APPLIED" else "REJECTED"
                if (ok) applied++ else rejected++
            }
            "ARRIVE_AT_DOORSTEP" -> {
                val (ok, _) = riderArrive(event.orderId, isReplay = true)
                event.syncStatus = if (ok) "APPLIED" else "REJECTED"
                if (ok) applied++ else rejected++
            }
            "REPORT_ISSUE" -> {
                val reason = try { ExceptionReason.valueOf(event.reasonCode ?: "DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE") } catch (_: Exception) { ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE }
                val safetyConcern = event.handoverPayload?.get("safetyConcern") == "true"
                val (ok, _) = riderReportIssue(event.orderId, reason, event.payloadNotes ?: "Offline report", safetyConcern = safetyConcern, isReplay = true)
                event.syncStatus = if (ok) "APPLIED" else "REJECTED"
                if (ok) applied++ else rejected++
            }
            "COMPLETE_HANDOVER" -> {
                // Check if cylinder already completed by another order (serial collision)
                val conflict = orders.find { it.id != event.orderId && it.allocatedSerial == event.scannedSerial && it.status == OrderStatus.ORD_COMP }
                if (conflict != null) {
                    event.syncStatus = "CONFLICT_QUEUED"
                    opsConflictQueue.add(
                        SerialConflictItem(
                            id = "CONF-" + UUID.randomUUID().toString().take(6),
                            serialNumber = event.scannedSerial ?: "UNKNOWN",
                            orderId1 = conflict.id,
                            orderId2 = event.orderId,
                            reasonCode = "DUPLICATE_OFFLINE_SCAN_CONFLICT"
                        )
                    )
                    addNotification(
                        surface = AppSurface.STAFF,
                        title = "Serial Collision Conflict",
                        message = "Duplicate scan on serial ${event.scannedSerial}. Completion on hold.",
                        canonicalStatus = "CONFLICT",
                        orderId = event.orderId,
                        code = "NTF-12",
                        staffRole = StaffSubRole.OPERATIONS
                    )
                    conflicts++
                } else {
                    val parsed = parseHandoverPayload(event.handoverPayload)
                    val (ok, _) = completeHandover(
                        orderId = event.orderId,
                        checklistAnswers = parsed.checklistAnswers,
                        installationResult = parsed.installationResult,
                        notInstalledReason = parsed.notInstalledReason,
                        pinVerified = parsed.pinVerified,
                        pinNotVerifiedReason = parsed.pinReason,
                        photoCaptured = parsed.photoCaptured,
                        signatureCaptured = parsed.signatureCaptured,
                        signatureUnavailableReason = parsed.signatureUnavailableReason,
                        evidenceConsent = parsed.evidenceConsent,
                        signerName = parsed.signerName,
                        amountReceivedPaisa = parsed.amountReceivedPaisa,
                        changeReturnedPaisa = parsed.changeReturnedPaisa,
                        cashDifferenceReason = parsed.cashDifferenceReason,
                        emptyCollected = parsed.emptyCollected,
                        emptyBrand = parsed.emptyBrand,
                        emptySerial = parsed.emptySerial,
                        isReplay = true
                    )
                    event.syncStatus = if (ok) "APPLIED" else "REJECTED"
                    if (ok) applied++ else rejected++
                }
            }
        }
    }

    notifyChanged()
    return "Sync complete: $applied APPLIED, $conflicts CONFLICTS, $rejected REJECTED."
}

fun BlueGasStore.simulateSerialConflict(): String {
    val targetOrder = orders.find { it.id == "ORD-SEED-105" }
    val serial = targetOrder?.allocatedSerial ?: "BG-LHR-118-002"
    val cyl = cylinders.find { it.serialNumber == serial }
    if (targetOrder == null || cyl == null) {
        return "Demo order not found."
    }
    val event = OfflineOutboxEvent(
        sequenceNumber = outboxSequenceCounter++,
        idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
        orderId = targetOrder.id,
        action = "COMPLETE_HANDOVER",
        scannedSerial = serial
    )
    riderOutbox.add(event)
    notifyChanged()
    return "Duplicate offline completion queued for serial $serial."
}

fun BlueGasStore.riderAcceptOrder(orderId: String): Pair<Boolean, String> = riderAcceptTask(orderId)

fun BlueGasStore.riderDeclineOrder(orderId: String, reason: String = "Declined by Rider"): Pair<Boolean, String> = riderDeclineTask(orderId, reason)

fun BlueGasStore.riderStartPreparing(orderId: String): Pair<Boolean, String> {
    if (isRiderOffline) {
        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
            orderId = orderId,
            action = "START_PREPARING",
            fromStatus = orders.find { it.id == orderId }?.status ?: OrderStatus.ORD_ACPT,
            toStatus = OrderStatus.ORD_PREP
        )
        riderOutbox.add(event)
        notifyChanged()
        return Pair(true, "Action queued in Offline Outbox (START_PREPARING)")
    }
    return startPreparing(orderId)
}

fun BlueGasStore.riderSelectAndBindSerial(orderId: String, serialNumber: String): Pair<Boolean, String> {
    return allocateCylinderSerial(orderId, serialNumber, getActiveRider()?.name ?: "Rider")
}

fun BlueGasStore.riderStartDeliveryOrStagePickup(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    return if (order.fulfilmentMode == FulfilmentMode.PICKUP) {
        markReadyForPickup(orderId)
    } else {
        riderStartTrip(orderId)
    }
}

fun BlueGasStore.arriveAtHandover(orderId: String, actor: String = "Rider"): Pair<Boolean, String> = riderArrive(orderId)

fun BlueGasStore.dispatchOrReadyForPickup(orderId: String, riderIdOrActor: String? = null): Pair<Boolean, String> = riderStartDeliveryOrStagePickup(orderId)

fun BlueGasStore.toggleRiderShiftStatus(riderId: String) {
    val r = riders.find { it.id == riderId } ?: return
    val idx = riders.indexOf(r)
    val nextStatus = if (r.status == "ACTIVE") "INACTIVE" else "ACTIVE"
    riders[idx] = r.copy(status = nextStatus)
    notifyChanged()
}

fun BlueGasStore.completeDepotPickup(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    val serial = order.allocatedSerial ?: "SERIAL-DEPOT-01"
    val isPaid = activePaymentFor(orderId)?.state == PaymentState.SETTLED || activePaymentFor(orderId)?.state == PaymentState.CAPTURED
    return completeCounterHandover(
        orderId = orderId,
        pinOrRefChecked = true,
        scannedCylinderId = serial,
        amountReceivedPaisa = if (!isPaid) order.totalAmountPaisa else null
    )
}

