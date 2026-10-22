package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.PaymentStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import java.util.UUID

fun BlueGasStore.payOnline(
    orderId: String,
    demoOutcome: String = "SUCCESS" // "SUCCESS", "DECLINED", "TIMEOUT"
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.holdExpiresAt != null && System.currentTimeMillis() > order.holdExpiresAt) {
        processTimers()
        return Pair(false, "The payment window has ended. Please place a new order.")
    }
    if (order.status != OrderStatus.ORD_PAYPEND) {
        return Pair(false, "Order is not awaiting payment (Status: ${order.status.displayName}).")
    }

    val payment = activePaymentFor(orderId) ?: return Pair(false, "No active payment record found for order.")

    // Idempotency check (EXC-04, UAT-15)
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
        message = "Your COD order ${order.id} is confirmed. Rs ${order.totalAmountPaisa / 100} payable at delivery.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-01"
    )

    onConfirmed(order)

    notifyChanged()
    return Pair(true, "Order ${order.id} confirmed for Cash on Delivery.")
}

fun BlueGasStore.confirmPaymentAndAdvance(orderId: String, forcedOutcome: String = "SUCCESS", actor: String = "Customer"): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
    if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
        return confirmCod(orderId)
    }
    return payOnline(orderId, forcedOutcome)
}
