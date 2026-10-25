/**
 * Payment processing operations for BLUE GAS orders.
 *
 * Implements the payment state machine transitions, online gateway demo outcomes,
 * payment retry attempts, and cash on delivery confirmation. Reads order and payment
 * records from the store and advances order status to confirmed upon successful
 * payment authorization or COD selection.
 */
package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.PaymentStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Simulates online payment gateway processing for an order awaiting payment.
 *
 * Validates that the order is in ORD_PAYPEND status and that its inventory reservation
 * hold window has not expired. Transitions the payment record through the payment state
 * machine (preauthorization under Branch A or initial capture under Branch B), advances
 * the order status to ORD_CONF on success, triggers stock assignment, and generates notifications.
 *
 * @param orderId Identifier of the order being paid.
 * @param demoOutcome Simulated gateway response: "SUCCESS", "DECLINED", or "TIMEOUT".
 * @return Pair indicating whether the operation succeeded and a customer-facing message.
 */
fun BlueGasStore.payOnline(
    orderId: String,
    demoOutcome: String = "SUCCESS" // "SUCCESS", "DECLINED", "TIMEOUT"
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    // Guard against stale payments: if the reservation payment window expired, reject the attempt
    // and let processTimers transition the order to ORD_EXP.
    if (order.holdExpiresAt != null && System.currentTimeMillis() > order.holdExpiresAt) {
        processTimers()
        return Pair(false, "The payment window has ended. Please place a new order.")
    }
    if (order.status != OrderStatus.ORD_PAYPEND) {
        return Pair(false, "Order is not awaiting payment (Status: ${order.status.displayName}).")
    }

    val payment = activePaymentFor(orderId) ?: return Pair(false, "No active payment record found for order.")

    // Verify idempotency key to prevent double-charging or processing duplicate gateway callbacks.
    if (idempotencyKeys.contains(payment.idempotencyKey)) {
        return Pair(true, "Duplicate gateway message ignored (Already processed).")
    }

    when (demoOutcome) {
        "SUCCESS" -> {
            idempotencyKeys.add(payment.idempotencyKey)
            if (payment.branch == "A") {
                payment.authorizedAmountPaisa = payment.baseAmountPaisa
                PaymentStateMachine.transition(payment, PaymentState.PREAUTHORIZED, "Payment Gateway", "Pre-authorization successful", paymentHistory)
            } else {
                payment.capturedAmountPaisa = payment.baseAmountPaisa
                PaymentStateMachine.transition(payment, PaymentState.INITIAL_AMOUNT_CAPTURED, "Payment Gateway", "Initial capture successful", paymentHistory)
            }

            // Order: Payment Pending -> Confirmed
            val tConf = OrderStateMachine.transition(
                order = order,
                targetStatus = OrderStatus.ORD_CONF,
                actor = "Payment Gateway Callback",
                role = AccountableRole.FINANCE,
                trigger = "Online payment authorized & captured"
            )
            if (tConf is TransitionResult.Failure) {
                return Pair(false, tConf.errorMessage)
            }
            recordHistory(tConf, "PAID_ONLINE")

            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Payment Received",
                message = "Payment of ${formatRupees(payment.baseAmountPaisa)} received. Order ${order.id} is confirmed.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = order.id,
                code = "NTF-02"
            )
            addNotification(
                surface = AppSurface.STAFF,
                title = "Payment Settled / Captured",
                message = "Online payment of ${formatRupees(payment.baseAmountPaisa)} captured for order ${order.id}.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = order.id,
                code = "NTF-02",
                staffRole = StaffSubRole.FINANCE
            )

            // Auto-advance: Reserved -> Assigned
            onConfirmed(order)

            notifyChanged()
            return Pair(true, "Payment successful. Order ${order.id} is confirmed!")
        }
        "DECLINED" -> {
            PaymentStateMachine.transition(payment, PaymentState.AUTHORIZATION_FAILED, "Payment Gateway", "Gateway declined transaction", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.PAYMENT_FAILED_CLOSED, "System", "Attempt marked failed closed", paymentHistory)

            notifyChanged()
            return Pair(false, "Payment declined. Try again or switch to cash on delivery.")
        }
        "TIMEOUT" -> {
            PaymentStateMachine.transition(payment, PaymentState.PAYMENT_TIMEOUT, "Payment Gateway", "Gateway timeout", paymentHistory)
            PaymentStateMachine.transition(payment, PaymentState.PAYMENT_FAILED_CLOSED, "System", "Attempt marked failed closed", paymentHistory)

            notifyChanged()
            return Pair(false, "Payment timed out. Please retry or choose Cash on Delivery.")
        }
        else -> return Pair(false, "Unknown payment outcome: $demoOutcome")
    }
}

/**
 * Creates a new payment attempt for an order that encountered a payment failure or timeout.
 *
 * Increments the attempt counter, registers a new Payment entity with a unique idempotency key,
 * and if switching to Cash on Delivery, immediately confirms the order.
 *
 * @param orderId Target order identifier.
 * @param method Chosen payment method for this attempt.
 * @return Pair indicating success and a status description.
 */
fun BlueGasStore.retryPayment(
    orderId: String,
    method: PaymentMethod
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.holdExpiresAt != null && System.currentTimeMillis() > order.holdExpiresAt) {
        processTimers()
        return Pair(false, "The payment window has ended. Please place a new order.")
    }
    if (order.status != OrderStatus.ORD_PAYPEND) {
        return Pair(false, "Order is not in payment pending state.")
    }

    order.paymentMethod = method
    val lastAttempt = payments.filter { it.orderId == orderId }.maxOfOrNull { it.attemptNo } ?: 1
    val isCod = method == PaymentMethod.CASH_ON_DELIVERY
    val newPayState = if (isCod) PaymentState.COD_PENDING else PaymentState.PAYMENT_PENDING
    val branch = if (isCod) "C" else if (settings.onlinePaymentMode == OnlinePaymentMode.BRANCH_A_AUTHORIZE_THEN_CAPTURE) "A" else "B"

    payments.add(
        Payment(
            id = "PAY-" + UUID.randomUUID().toString().take(8).uppercase(),
            orderId = orderId,
            method = method,
            state = newPayState,
            baseAmountPaisa = order.totalAmountPaisa,
            finalAmountPaisa = order.totalAmountPaisa,
            idempotencyKey = "IDEMP-" + UUID.randomUUID().toString().take(8),
            attemptNo = lastAttempt + 1,
            branch = branch
        )
    )

    if (isCod) {
        return confirmCod(orderId)
    }

    notifyChanged()
    return Pair(true, "New payment attempt created.")
}

/**
 * Confirms an order with Cash on Delivery payment.
 *
 * Sets the active payment state to COD_PENDING (Branch C), advances the order status
 * to ORD_CONF via the order state machine, fires customer notifications, and triggers
 * inventory assignment.
 *
 * @param orderId Identifier of the order to confirm.
 * @return Pair indicating success and a customer-facing confirmation message.
 */
fun BlueGasStore.confirmCod(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_PAYPEND) {
        return Pair(false, "Order is not awaiting payment.")
    }

    val payment = activePaymentFor(orderId)
    if (payment != null) {
        payment.method = PaymentMethod.CASH_ON_DELIVERY
        payment.branch = "C"
        payment.state = PaymentState.COD_PENDING
    }

    val tConf = OrderStateMachine.transition(
        order = order,
        targetStatus = OrderStatus.ORD_CONF,
        actor = "Customer",
        role = AccountableRole.OPERATIONS,
        trigger = "Cash on Delivery chosen and confirmed"
    )
    if (tConf is TransitionResult.Failure) {
        return Pair(false, tConf.errorMessage)
    }
    recordHistory(tConf, "CONFIRMED_COD")

    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Order Confirmed",
        message = "Your COD order ${order.id} is confirmed. ${formatRupees(order.totalAmountPaisa)} payable at delivery.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-01"
    )

    onConfirmed(order)

    notifyChanged()
    return Pair(true, "Order ${order.id} confirmed for Cash on Delivery.")
}

/**
 * Convenience entry point to confirm payment and advance the order.
 *
 * For Cash on Delivery, calls [confirmCod]. For online methods, delegates to [payOnline]
 * using the specified demo gateway outcome.
 *
 * @param orderId Target order identifier.
 * @param forcedOutcome Demo outcome for online payment ("SUCCESS", "DECLINED", or "TIMEOUT").
 * @param actor Name of the triggering actor (defaults to "Customer").
 * @return Pair indicating success and a descriptive message.
 */
fun BlueGasStore.confirmPaymentAndAdvance(orderId: String, forcedOutcome: String = "SUCCESS", actor: String = "Customer"): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
    if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
        return confirmCod(orderId)
    }
    return payOnline(orderId, forcedOutcome)
}
