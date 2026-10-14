package com.example.bluegas.lifecycle

import com.example.bluegas.model.AccountableRole
import com.example.bluegas.model.Payment
import com.example.bluegas.model.PaymentAttempt
import com.example.bluegas.model.PaymentMethod
import com.example.bluegas.model.PaymentState
import java.util.UUID

sealed class PaymentResult {
    data class Success(val payment: Payment, val attempt: PaymentAttempt?, val message: String) : PaymentResult()
    data class Failure(val payment: Payment, val errorMessage: String) : PaymentResult()
}

object PaymentStateMachine {

    /**
     * Guarded transition for Cash on Delivery lifecycle.
     */
    fun advanceCod(
        payment: Payment,
        targetState: PaymentState,
        collectedPaisa: Long? = null,
        actor: String
    ): PaymentResult {
        if (payment.method != PaymentMethod.CASH_ON_DELIVERY) {
            return PaymentResult.Failure(payment, "Payment is not Cash on Delivery.")
        }
        if (payment.state == PaymentState.SETTLED || payment.state == PaymentState.PAYMENT_CANCELLED) {
            return PaymentResult.Failure(payment, "Payment is already in terminal state: ${payment.state.displayName}.")
        }

        when (payment.state) {
            PaymentState.COD_PENDING -> {
                if (targetState == PaymentState.FINAL_AMOUNT_DETERMINED || targetState == PaymentState.PAYMENT_CANCELLED) {
                    payment.state = targetState
                    return PaymentResult.Success(payment, null, "COD advanced to ${targetState.displayName}")
                }
            }
            PaymentState.FINAL_AMOUNT_DETERMINED -> {
                if (targetState == PaymentState.CASH_COLLECTION_PENDING || targetState == PaymentState.PAYMENT_CANCELLED) {
                    payment.state = targetState
                    return PaymentResult.Success(payment, null, "COD advanced to ${targetState.displayName}")
                }
            }
            PaymentState.CASH_COLLECTION_PENDING -> {
                if (targetState == PaymentState.CASH_COLLECTED) {
                    val collected = collectedPaisa ?: payment.finalAmountPaisa
                    payment.capturedAmountPaisa = collected
                    payment.state = PaymentState.CASH_COLLECTED
                    return PaymentResult.Success(payment, null, "Cash of PKR ${collected / 100.0} collected by rider")
                }
            }
            PaymentState.CASH_COLLECTED -> {
                if (targetState == PaymentState.SETTLED) {
                    payment.settledAmountPaisa = payment.capturedAmountPaisa
                    payment.state = PaymentState.SETTLED
                    return PaymentResult.Success(payment, null, "COD settled in reconciliation")
                }
                if (targetState == PaymentState.FULL_REFUND_PENDING) {
                    payment.state = PaymentState.FULL_REFUND_PENDING
                    return PaymentResult.Success(payment, null, "Refund initiated post-cash collection")
                }
            }
            PaymentState.FULL_REFUND_PENDING -> {
                if (targetState == PaymentState.FULLY_REFUNDED) {
                    payment.state = PaymentState.FULLY_REFUNDED
                    payment.settledAmountPaisa = 0L
                    return PaymentResult.Success(payment, null, "COD cash refunded to customer")
                }
            }
            else -> {}
        }

        return PaymentResult.Failure(
            payment,
            "Illegal COD payment transition from ${payment.state.displayName} to ${targetState.displayName}"
        )
    }

    /**
     * Process Mocked Online Payment with forced outcome (Success, Decline, Timeout) and idempotency check.
     */
    fun processOnlinePayment(
        payment: Payment,
        forcedOutcome: String, // "SUCCESS", "DECLINED", "TIMEOUT"
        processedKeys: MutableSet<String>,
        actor: String
    ): PaymentResult {
        if (payment.method != PaymentMethod.ONLINE_HOSTED) {
            return PaymentResult.Failure(payment, "Payment is not Online Hosted method.")
        }

        // Idempotency check: replaying same confirmation never double-settles
        if (processedKeys.contains(payment.idempotencyKey)) {
            if (payment.state == PaymentState.INITIAL_AMOUNT_CAPTURED || payment.state == PaymentState.SETTLED) {
                return PaymentResult.Success(payment, null, "Idempotent replay: Payment ${payment.id} already captured safely.")
            }
        }

        val attempt = PaymentAttempt(
            id = "ATT-" + UUID.randomUUID().toString().take(8),
            paymentId = payment.id,
            amountPaisa = payment.baseAmountPaisa,
            gatewayStatus = forcedOutcome
        )

        when (forcedOutcome) {
            "SUCCESS" -> {
                payment.state = PaymentState.INITIAL_AMOUNT_CAPTURED
                payment.capturedAmountPaisa = payment.baseAmountPaisa
                processedKeys.add(payment.idempotencyKey)
                return PaymentResult.Success(payment, attempt, "Gateway authorization and initial capture successful.")
            }
            "DECLINED" -> {
                payment.state = PaymentState.AUTHORIZATION_FAILED
                return PaymentResult.Failure(payment, "Gateway declined authorization (Insufficient funds or card decline).")
            }
            "TIMEOUT" -> {
                payment.state = PaymentState.PAYMENT_TIMEOUT
                return PaymentResult.Failure(payment, "Gateway request timed out. New payment attempt required.")
            }
            else -> {
                payment.state = PaymentState.AUTHORIZATION_FAILED
                return PaymentResult.Failure(payment, "Unknown gateway status: $forcedOutcome")
            }
        }
    }

    /**
     * Online settlement resolution after final amount determination.
     */
    fun resolveOnlineAdjustmentAndSettle(
        payment: Payment,
        adjustmentOption: String // "NO_ADJUSTMENT", "ADDITIONAL_COLLECT", "PARTIAL_REFUND"
    ): PaymentResult {
        if (payment.state != PaymentState.INITIAL_AMOUNT_CAPTURED && payment.state != PaymentState.FINAL_AMOUNT_DETERMINED) {
            return PaymentResult.Failure(payment, "Cannot settle: Initial capture has not taken place.")
        }

        payment.state = PaymentState.FINAL_AMOUNT_DETERMINED
        when (adjustmentOption) {
            "NO_ADJUSTMENT" -> {
                payment.state = PaymentState.SETTLED
                payment.settledAmountPaisa = payment.capturedAmountPaisa
                return PaymentResult.Success(payment, null, "Settled without price adjustment.")
            }
            "ADDITIONAL_COLLECT" -> {
                payment.state = PaymentState.ADDITIONAL_COLLECTION_PENDING
                // Simulating secondary capture
                payment.capturedAmountPaisa = payment.finalAmountPaisa
                payment.settledAmountPaisa = payment.finalAmountPaisa
                payment.state = PaymentState.SETTLED
                return PaymentResult.Success(payment, null, "Additional amount collected and settled.")
            }
            "PARTIAL_REFUND" -> {
                payment.state = PaymentState.PARTIAL_REFUND_PENDING
                payment.settledAmountPaisa = payment.finalAmountPaisa
                payment.state = PaymentState.SETTLED
                return PaymentResult.Success(payment, null, "Partial refund credited and remaining amount settled.")
            }
            else -> {
                payment.state = PaymentState.PAYMENT_FAILED_CLOSED
                return PaymentResult.Failure(payment, "Adjustment failed closed.")
            }
        }
    }
}
