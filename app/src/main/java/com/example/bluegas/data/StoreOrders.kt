package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.PaymentStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import java.util.UUID

fun BlueGasStore.createOrder(
    customerId: String,
    addressId: String,
    fulfilmentMode: FulfilmentMode,
    sku: String,
    quantity: Int,
    includeDeposit: Boolean = false,
    paymentMethod: PaymentMethod = PaymentMethod.ONLINE_HOSTED,
    orderType: OrderType = OrderType.REFILL_EXCHANGE,
    pickupPointId: String? = null,
    accessories: List<Pair<String, Int>> = emptyList()
): Pair<Order?, String?> {
    val customer = customers.find { it.id == customerId }
        ?: return Pair(null, "Customer profile not found.")
    val address = addresses.find { it.id == addressId }
        ?: return Pair(null, "Selected address not found.")

    // 1. Validate serviceability / pickup
    if (fulfilmentMode == FulfilmentMode.DELIVERY && !address.isServiceable) {
        val pickupOption = nodes.find { it.pickupEnabled && it.city == address.city }
        val hint = if (pickupOption != null) " Depot pickup is available at ${pickupOption.name}." else ""
        return Pair(null, "Address is outside the delivery pilot area.$hint")
    }

    // 2. Validate product household approval
    val variant = productVariants.find { it.sku == sku }
    if (variant != null && !variant.isHouseholdApproved) {
        return Pair(null, "Size ${variant.name} is reserved for commercial supply (Phase 3).")
    }

    if (quantity > settings.maxQuantityPerOrder) {
        return Pair(null, "Maximum order quantity is ${settings.maxQuantityPerOrder} cylinders.")
    }

    // 3. Check stock availability at serving point
    val servingPointId = if (fulfilmentMode == FulfilmentMode.PICKUP) {
        pickupPointId ?: nodes.firstOrNull { it.pickupEnabled }?.id ?: "NODE-LHR-1"
    } else {
        address.servingPointId ?: serviceZones.find { it.id == address.zoneId }?.servingPointIds?.firstOrNull() ?: "NODE-LHR-1"
    }

    val stock = stockBalances.find { it.pointId == servingPointId && it.skuId == sku }
    val available = stock?.availableQty ?: 0
    if (orderType != OrderType.ACCESSORY_ONLY && available < quantity) {
        val otherSize = stockBalances.filter { it.pointId == servingPointId && it.skuId != sku && it.availableQty >= quantity }
            .mapNotNull { b -> productVariants.find { it.sku == b.skuId && it.isHouseholdApproved }?.name }
            .firstOrNull()
        val altHint = if (otherSize != null) " Consider $otherSize or depot pickup." else " Please check back shortly."
        return Pair(null, "Size currently out of stock at this depot.$altHint")
    }

    // 4. Build Quote
    val currentPriceList = priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    val quote = PricingEngine.buildQuote(
        orderType = orderType,
        sku = sku,
        quantity = quantity,
        accessories = accessories,
        mode = fulfilmentMode,
        priceList = currentPriceList,
        tax = taxConfig,
        settings = settings
    )

    val orderId = "ORD-" + UUID.randomUUID().toString().take(6).uppercase()

    val initialOrder = Order(
        id = orderId,
        customerId = customerId,
        addressId = addressId,
        fulfilmentMode = fulfilmentMode,
        status = OrderStatus.ORD_DRAFT,
        items = quote.lines,
        subtotalPaisa = quote.subtotalPaisa,
        depositTotalPaisa = quote.depositTotalPaisa,
        platformFeePaisa = quote.platformFeePaisa,
        deliveryFeePaisa = quote.deliveryFeePaisa,
        taxPaisa = quote.taxPaisa,
        totalAmountPaisa = quote.totalAmountPaisa,
        paymentMethod = paymentMethod,
        orderType = orderType,
        fulfilmentPointId = servingPointId,
        pickupPointId = if (fulfilmentMode == FulfilmentMode.PICKUP) servingPointId else null,
        priceListVersion = quote.priceListVersion,
        reference118PricePaisa = quote.reference118PricePaisa,
        discountPaisa = quote.discountPaisa,
        gstPaisa = quote.gstPaisa,
        furtherTaxPaisa = quote.furtherTaxPaisa,
        newCylinderModel = if (orderType == OrderType.NEW_CYLINDER) settings.newCylinderModel else null,
        holdExpiresAt = System.currentTimeMillis() + (settings.holdTtlMinutes * 60000L),
        handoverPin = (1000..9999).random().toString()
    )

    // Draft -> Validation Pending
    val tVal = OrderStateMachine.transition(
        order = initialOrder,
        targetStatus = OrderStatus.ORD_VALPEND,
        actor = customer.name,
        role = AccountableRole.OPERATIONS,
        trigger = "Order submitted for validation"
    )
    if (tVal is TransitionResult.Failure) {
        return Pair(null, tVal.errorMessage)
    }
    recordHistory(tVal)

    // Validation Pending -> Payment Pending (with Temporary Hold)
    val tPay = OrderStateMachine.transition(
        order = initialOrder,
        targetStatus = OrderStatus.ORD_PAYPEND,
        actor = "Blue Gas Engine",
        role = AccountableRole.OPERATIONS,
        trigger = "Validation passed, stock hold placed"
    )
    if (tPay is TransitionResult.Failure) {
        return Pair(null, tPay.errorMessage)
    }
    recordHistory(tPay)

    // Place Temporary Stock Hold
    if (orderType != OrderType.ACCESSORY_ONLY) {
        val hold = StockHold(
            id = "HOLD-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            sku = sku,
            quantity = quantity,
            expiresAt = initialOrder.holdExpiresAt ?: (System.currentTimeMillis() + 900000L)
        )
        stockHolds.add(hold)
        stock?.let { it.heldQty += quantity }
    }

    orders.add(0, initialOrder)

    // Create Initial Payment Attempt
    val paymentId = "PAY-" + UUID.randomUUID().toString().take(8).uppercase()
    val isCod = paymentMethod == PaymentMethod.CASH_ON_DELIVERY
    val initialPayState = if (isCod) PaymentState.COD_PENDING else PaymentState.PAYMENT_PENDING
    val branch = if (isCod) "C" else if (settings.onlinePaymentMode == OnlinePaymentMode.BRANCH_A_AUTHORIZE_THEN_CAPTURE) "A" else "B"

    payments.add(
        Payment(
            id = paymentId,
            orderId = orderId,
            method = paymentMethod,
            state = initialPayState,
            baseAmountPaisa = quote.totalAmountPaisa,
            finalAmountPaisa = quote.totalAmountPaisa,
            idempotencyKey = "IDEMP-" + UUID.randomUUID().toString().take(8),
            attemptNo = 1,
            branch = branch
        )
    )

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Order Created",
        message = "Order $orderId created. Stock held for ${settings.holdTtlMinutes} min.",
        canonicalStatus = initialOrder.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-01"
    )

    notifyChanged()
    return Pair(initialOrder, null)
}

fun BlueGasStore.onConfirmed(order: Order) {
    order.confirmedAt = System.currentTimeMillis()
    order.promisedByAt = System.currentTimeMillis() + (settings.promisedHandoverMinutes * 60000L)

    // 1. Convert Temporary Hold to Firm Reservation (Confirmed -> Reserved)
    val tRsvd = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_RSVD,
        actor = "Blue Gas Engine",
        role = AccountableRole.INVENTORY,
        trigger = "Payment confirmed, firm stock reservation locked"
    )
    if (tRsvd is TransitionResult.Success) {
        recordHistory(tRsvd)
        // Convert hold in stock balance
        val hold = stockHolds.find { it.orderId == order.id && !it.isReleased }
        if (hold != null) {
            hold.isReleased = true
            val stock = stockBalances.find { it.pointId == order.fulfilmentPointId && it.skuId == hold.sku }
            stock?.let {
                it.heldQty = (it.heldQty - hold.quantity).coerceAtLeast(0)
                it.reservedQty += hold.quantity
            }
            stockReservations.add(
                StockReservation(
                    id = "RSV-" + UUID.randomUUID().toString().take(6),
                    orderId = order.id,
                    sku = hold.sku,
                    quantity = hold.quantity,
                    reservedAt = System.currentTimeMillis()
                )
            )
        }
    }

    // 2. Assign to Distributor (Reserved -> Assigned, RACI-06)
    val servingPointId = order.fulfilmentPointId ?: "NODE-LHR-1"
    val distributor = distributors.find { it.pointId == servingPointId && it.status == "ACTIVE" }
        ?: distributors.firstOrNull { it.status == "ACTIVE" }

    if (distributor != null) {
        order.assignedDistributorId = distributor.id
        order.assignedAt = System.currentTimeMillis()

        val tAsgn = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ASGN,
            actor = "Auto Dispatch Engine",
            role = AccountableRole.OPERATIONS,
            trigger = "Assigned to distributor ${distributor.name}"
        )
        if (tAsgn is TransitionResult.Success) {
            recordHistory(tAsgn)
            orderAssignments.add(
                OrderAssignment(
                    orderId = order.id,
                    distributorId = distributor.id,
                    riderId = null,
                    assignedAt = System.currentTimeMillis()
                )
            )

            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Distributor Assigned",
                message = "Your order has been assigned to ${distributor.name}.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = order.id,
                code = "NTF-03"
            )
            addNotification(
                surface = AppSurface.STAFF,
                title = "New Order Assigned",
                message = "Order ${order.id} assigned to ${distributor.name}. Acceptance SLA active.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = order.id,
                code = "NTF-03",
                staffRole = StaffSubRole.DISTRIBUTOR
            )
        }
    } else {
        // No distributor found -> Fulfilment Exception EXC-05
        raiseException(
            orderId = order.id,
            reason = ExceptionReason.NO_ACCEPTANCE,
            details = "No active distributor serving node $servingPointId available for assignment"
        )
    }
}

fun BlueGasStore.processTimers() {
    val now = System.currentTimeMillis()
    var changed = false

    // 1. Expire unconfirmed holds on Payment Pending orders
    val expiredOrders = orders.filter {
        it.status == OrderStatus.ORD_PAYPEND && it.holdExpiresAt != null && it.holdExpiresAt < now
    }
    for (ord in expiredOrders) {
        val activePay = activePaymentFor(ord.id)
        if (activePay != null && !activePay.state.isTerminal) {
            PaymentStateMachine.transition(activePay, PaymentState.PAYMENT_TIMEOUT, "System Timer", "Hold window expired", paymentHistory)
            PaymentStateMachine.transition(activePay, PaymentState.PAYMENT_FAILED_CLOSED, "System Timer", "Payment attempt closed on expiry", paymentHistory)
        }

        // Release holds
        stockHolds.filter { it.orderId == ord.id && !it.isReleased }.forEach { h ->
            h.isReleased = true
            val stock = stockBalances.find { it.pointId == ord.fulfilmentPointId && it.skuId == h.sku }
            stock?.let { it.heldQty = (it.heldQty - h.quantity).coerceAtLeast(0) }
        }

        val tCanc = OrderStateMachine.transition(
            order = ord,
            targetStatus = OrderStatus.ORD_CANC,
            actor = "System Timer",
            role = AccountableRole.OPERATIONS,
            trigger = "Stock hold expired before payment confirmation"
        )
        if (tCanc is TransitionResult.Success) {
            recordHistory(tCanc, "EXPIRED_HOLD")
            ord.cancelReason = "Payment window expired"
            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Order Cancelled",
                message = "Payment hold window expired for order ${ord.id}. You can place a new order anytime.",
                canonicalStatus = ord.status.canonicalLabel,
                orderId = ord.id,
                code = "NTF-13"
            )
            changed = true
        }
    }

    // 2. Acceptance SLA check
    if (settings.autoRaiseNoAcceptance) {
        val overdueAssigned = orders.filter {
            it.status == OrderStatus.ORD_ASGN && it.assignedAt != null &&
                    (now - it.assignedAt!!) > (settings.distributorAcceptanceWindowMins * 60000L)
        }
        for (ord in overdueAssigned) {
            raiseException(ord.id, ExceptionReason.NO_ACCEPTANCE, "Distributor SLA expired without acceptance")
            changed = true
        }

        // 2b. Rider Offer Expiry check (S1.4, T2)
        val overdueRiderOffers = orders.filter {
            it.riderTaskStatus == RiderTaskStatus.OFFERED &&
                    it.riderOfferedAt != null &&
                    (now - it.riderOfferedAt!!) > (settings.riderAcceptanceWindowMins * 60000L)
        }
        for (ord in overdueRiderOffers) {
            expireRiderOffer(ord)
            changed = true
        }
    }

    // 3. FBR auto-posting removed (S0.2). Re-posting happens only from Finance (retryFbr).

    if (changed) {
        notifyChanged()
    }
}

/**
 * Expires an offered delivery task when the acceptance window has elapsed without rider acceptance.
 * Resets task status to DECLINED, unassigns the rider, notifies the distributor, and logs an audit trail.
 *
 * @param order Target order whose offer has timed out.
 */
fun BlueGasStore.expireRiderOffer(order: Order) {
    order.riderTaskStatus = RiderTaskStatus.DECLINED
    order.assignedRiderId = null
    order.riderOfferedAt = null
    order.riderCylinderVerified = false
    logAudit(
        "Order",
        order.id,
        "RIDER_OFFER_EXPIRED",
        "System Timer",
        AccountableRole.DISTRIBUTOR,
        "Rider offer expired without acceptance within ${settings.riderAcceptanceWindowMins}m window"
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Rider Offer Expired",
        message = "Rider did not accept in time for order ${order.id}. Please reassign.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-03",
        staffRole = StaffSubRole.DISTRIBUTOR
    )
}

fun BlueGasStore.cancelOrder(
    orderId: String,
    reason: String = "Cancelled by Customer",
    actor: String = "Customer",
    role: AccountableRole = AccountableRole.OPERATIONS
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

    // F0.1: Validate state machine permission before releasing stock or serials
    if (OrderStatus.ORD_CANC !in OrderStateMachine.allowedTransitionsMap[order.status].orEmpty()) {
        return Pair(false, "This order can no longer be cancelled.")
    }

    // Release stock holds
    stockHolds.filter { it.orderId == orderId && !it.isReleased }.forEach { h ->
        h.isReleased = true
        val stock = stockBalances.find { it.pointId == order.fulfilmentPointId && it.skuId == h.sku }
        stock?.let { it.heldQty = (it.heldQty - h.quantity).coerceAtLeast(0) }
    }

    // Release stock reservations
    stockReservations.filter { it.orderId == orderId && !it.isCommitted }.forEach { r ->
        r.isCommitted = true
        val stock = stockBalances.find { it.pointId == order.fulfilmentPointId && it.skuId == r.sku }
        stock?.let { it.reservedQty = (it.reservedQty - r.quantity).coerceAtLeast(0) }
    }

    // Release allocated cylinder
    order.allocatedSerial?.let { serial ->
        val cyl = cylinders.find { it.serialNumber == serial }
        cyl?.let {
            val idx = cylinders.indexOf(it)
            cylinders[idx] = it.copy(status = InventoryState.INV_AVAIL, allocatedOrderId = null)
        }
        order.allocatedSerial = null
    }

    val tCanc = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_CANC,
        actor = actor,
        role = role,
        trigger = "Cancellation: $reason"
    )
    if (tCanc is TransitionResult.Failure) {
        return Pair(false, tCanc.errorMessage)
    }
    recordHistory(tCanc, "CANCELLED")
    order.cancelReason = reason

    // Payment handling per v0.4 §12 & §10.6
    val payment = activePaymentFor(orderId)
    var paymentOutcome = "No payment taken"

    if (payment != null && !payment.state.isTerminal) {
        if (payment.method == PaymentMethod.CASH_ON_DELIVERY) {
            val (ok, _) = PaymentStateMachine.transition(payment, PaymentState.PAYMENT_CANCELLED, actor, reason, paymentHistory)
            paymentOutcome = if (ok) "COD cancelled - no payment due" else "Payment already terminal"
        } else {
            // Online Hosted
            when (payment.state) {
                PaymentState.PREAUTHORIZED -> {
                    PaymentStateMachine.transition(payment, PaymentState.VOIDED, actor, "Order cancelled before capture", paymentHistory)
                    paymentOutcome = "Authorization voided"
                }
                PaymentState.INITIAL_AMOUNT_CAPTURED -> {
                    PaymentStateMachine.transition(payment, PaymentState.FULL_REFUND_PENDING, actor, "Order cancelled post-capture", paymentHistory)
                    val refId = "REF-" + UUID.randomUUID().toString().take(6)
                    refunds.add(
                        Refund(
                            id = refId,
                            paymentId = payment.id,
                            orderId = orderId,
                            amountPaisa = payment.capturedAmountPaisa,
                            reason = "Cancellation refund: $reason",
                            executedBy = "System",
                            state = PaymentState.FULL_REFUND_PENDING
                        )
                    )
                    addNotification(
                        surface = AppSurface.STAFF,
                        title = "Refund waiting — $orderId",
                        message = "Order $orderId cancelled post-capture. Refund of ${formatPaisaToPkr(payment.capturedAmountPaisa)} waiting in Finance queue.",
                        canonicalStatus = "PENDING",
                        orderId = orderId,
                        code = "NTF-11",
                        staffRole = StaffSubRole.FINANCE
                    )
                    paymentOutcome = "Refund of ${formatRupees(payment.capturedAmountPaisa)} pending Finance processing"
                }
                PaymentState.PAYMENT_PENDING -> {
                    PaymentStateMachine.transition(payment, PaymentState.PAYMENT_CANCELLED, actor, "Order cancelled while payment pending", paymentHistory)
                    paymentOutcome = "Payment request cancelled"
                }
                else -> {}
            }
        }
    }

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Order Cancelled",
        message = "Order $orderId cancelled ($reason). $paymentOutcome.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-13"
    )

    notifyChanged()
    return Pair(true, "Order $orderId cancelled. $paymentOutcome.")
}

fun BlueGasStore.supersedeOrder(
    orderId: String,
    correctedAddressId: String? = null,
    correctedQty: Int? = null
): Pair<Boolean, String> {
    val oldOrder = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
    if (oldOrder.status != OrderStatus.ORD_CONF && oldOrder.status != OrderStatus.ORD_RSVD) {
        return Pair(false, "Supersede is only permitted for Confirmed or Reserved orders.")
    }

    val newOrderId = "ORD-" + UUID.randomUUID().toString().take(6).uppercase()
    val newAddressId = correctedAddressId ?: oldOrder.addressId
    val newQty = correctedQty ?: oldOrder.items.firstOrNull()?.quantity ?: 1

    // Build quote for new order
    val currentPriceList = priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    val sku = oldOrder.items.firstOrNull()?.sku ?: "SKU-LPG-11-8"
    val quote = PricingEngine.buildQuote(
        orderType = oldOrder.orderType,
        sku = sku,
        quantity = newQty,
        mode = oldOrder.fulfilmentMode,
        priceList = currentPriceList,
        tax = taxConfig,
        settings = settings
    )

    val newOrder = oldOrder.copy(
        id = newOrderId,
        addressId = newAddressId,
        items = quote.lines,
        subtotalPaisa = quote.subtotalPaisa,
        totalAmountPaisa = quote.totalAmountPaisa,
        status = OrderStatus.ORD_CONF,
        supersedesOrderId = oldOrder.id,
        createdAt = System.currentTimeMillis()
    )

    // Old order -> ORD_SUPS
    val tSups = OrderStateMachine.transition(
        order = oldOrder,
        targetStatus = OrderStatus.ORD_SUPS,
        actor = "Operations Lead",
        role = AccountableRole.OPERATIONS,
        trigger = "Superseded by corrected order $newOrderId"
    )
    if (tSups is TransitionResult.Failure) {
        return Pair(false, tSups.errorMessage)
    }
    recordHistory(tSups, "SUPERSEDED")
    oldOrder.supersededByOrderId = newOrderId

    // Re-point payment to new order
    val payment = payments.find { it.orderId == oldOrder.id }
    if (payment != null) {
        payments.add(payment.copy(id = "PAY-" + UUID.randomUUID().toString().take(8), orderId = newOrderId))
    }

    orders.add(0, newOrder)
    recordHistory(
        TransitionResult.Success(
            order = newOrder,
            fromStatus = OrderStatus.ORD_DRAFT,
            toStatus = OrderStatus.ORD_CONF,
            actor = "Operations Lead",
            role = AccountableRole.OPERATIONS
        ),
        "SUPERSEDED_REPLACEMENT"
    )

    // Re-lock firm reservation and assign
    onConfirmed(newOrder)

    notifyChanged()
    return Pair(true, "Order ${oldOrder.id} superseded by $newOrderId.")
}

fun BlueGasStore.raiseException(
    orderId: String,
    reason: ExceptionReason,
    details: String,
    actor: String = "Operations"
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
    if (order.status.isTerminal) {
        return Pair(false, "Cannot raise exception on terminal order ${order.id}.")
    }

    val tExc = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_EXC,
        actor = actor,
        role = AccountableRole.OPERATIONS,
        trigger = "Exception raised: ${reason.description}",
        exceptionReason = reason,
        exceptionDetails = details
    )
    if (tExc is TransitionResult.Failure) {
        return Pair(false, tExc.errorMessage)
    }
    recordHistory(tExc, reason.name)

    addNotification(
        surface = AppSurface.STAFF,
        title = "Fulfilment Exception",
        message = "Order $orderId in exception: ${reason.description}. Action required.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = orderId,
        code = "NTF-13",
        staffRole = StaffSubRole.OPERATIONS
    )

    notifyChanged()
    return Pair(true, "Order $orderId placed into Fulfilment Exception.")
}

fun BlueGasStore.recoverException(
    orderId: String,
    target: OrderStatus,
    reason: String,
    extraDistributorId: String? = null
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
    if (order.status != OrderStatus.ORD_EXC) {
        return Pair(false, "Order is not in Fulfilment Exception.")
    }

    order.exceptionRetryCount++

    if (target == OrderStatus.ORD_CANC) {
        return cancelOrder(orderId, "Cancelled from exception recovery: $reason", "Operations Lead", AccountableRole.OPERATIONS)
    }

    if (target == OrderStatus.ORD_FCLO) {
        // Release all holds, reservations and bound cylinders
        order.allocatedSerial?.let { s ->
            val cyl = cylinders.find { it.serialNumber == s }
            cyl?.let {
                val idx = cylinders.indexOf(it)
                cylinders[idx] = it.copy(status = InventoryState.INV_AVAIL, allocatedOrderId = null)
            }
            order.allocatedSerial = null
        }
        val tFclo = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_FCLO,
            actor = "Operations Lead",
            role = AccountableRole.OPERATIONS,
            trigger = "Failed closed: $reason"
        )
        if (tFclo is TransitionResult.Failure) {
            return Pair(false, tFclo.errorMessage)
        }
        recordHistory(tFclo, "FAILED_CLOSED")
        notifyChanged()
        return Pair(true, "Order $orderId closed as failed.")
    }

    // Recovery to in-progress status
    val tRec = OrderStateMachine.transition(
        order = order,
        targetStatus = target,
        actor = "Operations Lead",
        role = AccountableRole.OPERATIONS,
        trigger = "Recovered: $reason"
    )
    if (tRec is TransitionResult.Failure) {
        return Pair(false, tRec.errorMessage)
    }
    recordHistory(tRec, "RECOVERED")

    if (target == OrderStatus.ORD_ASGN) {
        if (extraDistributorId != null) {
            order.assignedDistributorId = extraDistributorId
        }
        order.assignedAt = System.currentTimeMillis()
    } else if (target == OrderStatus.ORD_PREP) {
        // Release previous cylinder to allow re-allocation
        order.allocatedSerial?.let { s ->
            val cyl = cylinders.find { it.serialNumber == s }
            cyl?.let {
                val idx = cylinders.indexOf(it)
                cylinders[idx] = it.copy(status = InventoryState.INV_AVAIL, allocatedOrderId = null)
            }
            order.allocatedSerial = null
        }
    }

    notifyChanged()
    return Pair(true, "Order $orderId recovered to ${target.canonicalLabel}.")
}

// Backward compatibility helpers
fun BlueGasStore.raiseFulfilmentException(orderId: String, reason: ExceptionReason, details: String) =
    raiseException(orderId, reason, details)

fun BlueGasStore.recoverFulfilmentException(orderId: String, targetStatus: OrderStatus) =
    recoverException(orderId, targetStatus, "Manual resolution by staff")
