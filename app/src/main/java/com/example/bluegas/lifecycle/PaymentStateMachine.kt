package com.example.bluegas.lifecycle

import com.example.bluegas.model.Payment
import com.example.bluegas.model.PaymentState
import com.example.bluegas.model.PaymentStatusHistory
import java.util.UUID

object PaymentStateMachine {

    // Allowed transition map per branch
    private val branchATransitions: Map<PaymentState, Set<PaymentState>> = mapOf(
        PaymentState.PAYMENT_PENDING to setOf(PaymentState.PREAUTHORIZED, PaymentState.AUTHORIZATION_FAILED, PaymentState.PAYMENT_TIMEOUT, PaymentState.PAYMENT_CANCELLED),
        PaymentState.PREAUTHORIZED to setOf(PaymentState.FINAL_AMOUNT_DETERMINED, PaymentState.VOIDED),
        PaymentState.FINAL_AMOUNT_DETERMINED to setOf(PaymentState.FINAL_CAPTURE_PENDING, PaymentState.VOIDED),
        PaymentState.FINAL_CAPTURE_PENDING to setOf(PaymentState.CAPTURED, PaymentState.ADJUSTMENT_FAILED),
        PaymentState.ADJUSTMENT_FAILED to setOf(PaymentState.FINAL_CAPTURE_PENDING, PaymentState.VOIDED),
        PaymentState.CAPTURED to setOf(PaymentState.SETTLED),
        PaymentState.AUTHORIZATION_FAILED to setOf(PaymentState.PAYMENT_FAILED_CLOSED),
        PaymentState.PAYMENT_TIMEOUT to setOf(PaymentState.PAYMENT_FAILED_CLOSED)
    )

    private val branchBTransitions: Map<PaymentState, Set<PaymentState>> = mapOf(
        PaymentState.PAYMENT_PENDING to setOf(PaymentState.INITIAL_AMOUNT_CAPTURED, PaymentState.AUTHORIZATION_FAILED, PaymentState.PAYMENT_TIMEOUT, PaymentState.PAYMENT_CANCELLED),
        PaymentState.INITIAL_AMOUNT_CAPTURED to setOf(PaymentState.FINAL_AMOUNT_DETERMINED, PaymentState.FULL_REFUND_PENDING),
        PaymentState.FINAL_AMOUNT_DETERMINED to setOf(PaymentState.NO_ADJUSTMENT, PaymentState.ADDITIONAL_COLLECTION_PENDING, PaymentState.PARTIAL_REFUND_PENDING),
        PaymentState.NO_ADJUSTMENT to setOf(PaymentState.SETTLED),
        PaymentState.ADDITIONAL_COLLECTION_PENDING to setOf(PaymentState.ADDITIONAL_COLLECTION_COMPLETED, PaymentState.ADDITIONAL_COLLECTION_FAILED),
        PaymentState.ADDITIONAL_COLLECTION_COMPLETED to setOf(PaymentState.SETTLED),
        PaymentState.ADDITIONAL_COLLECTION_FAILED to setOf(PaymentState.ADDITIONAL_COLLECTION_PENDING, PaymentState.SETTLED),
        PaymentState.PARTIAL_REFUND_PENDING to setOf(PaymentState.PARTIAL_REFUND_COMPLETED, PaymentState.PARTIAL_REFUND_FAILED),
        PaymentState.PARTIAL_REFUND_COMPLETED to setOf(PaymentState.SETTLED),
        PaymentState.PARTIAL_REFUND_FAILED to setOf(PaymentState.PARTIAL_REFUND_PENDING, PaymentState.SETTLED),
        PaymentState.FULL_REFUND_PENDING to setOf(PaymentState.FULLY_REFUNDED, PaymentState.FULL_REFUND_FAILED),
        PaymentState.FULL_REFUND_FAILED to setOf(PaymentState.FULL_REFUND_PENDING),
        PaymentState.AUTHORIZATION_FAILED to setOf(PaymentState.PAYMENT_FAILED_CLOSED),
        PaymentState.PAYMENT_TIMEOUT to setOf(PaymentState.PAYMENT_FAILED_CLOSED)
    )

    private val branchCTransitions: Map<PaymentState, Set<PaymentState>> = mapOf(
        PaymentState.COD_PENDING to setOf(PaymentState.FINAL_AMOUNT_DETERMINED, PaymentState.PAYMENT_CANCELLED),
        PaymentState.FINAL_AMOUNT_DETERMINED to setOf(PaymentState.CASH_COLLECTION_PENDING, PaymentState.PAYMENT_CANCELLED),
        PaymentState.CASH_COLLECTION_PENDING to setOf(PaymentState.CASH_COLLECTED, PaymentState.PAYMENT_CANCELLED),
        PaymentState.CASH_COLLECTED to setOf(PaymentState.SETTLED)
    )

    fun canTransition(payment: Payment, target: PaymentState): Boolean {
        if (payment.state.isTerminal) return false
        val map = when (payment.branch) {
            "A" -> branchATransitions
            "C" -> branchCTransitions
            else -> branchBTransitions
        }
        val allowed = map[payment.state] ?: emptySet()
        return allowed.contains(target)
    }

    fun transition(
        payment: Payment,
        target: PaymentState,
        actor: String,
        reason: String,
        historyList: MutableList<PaymentStatusHistory>? = null
    ): Pair<Boolean, String> {
        val current = payment.state
        if (current.isTerminal) {
            return Pair(false, "Payment ${payment.id} is in terminal state ${current.displayName} and cannot transition.")
        }

        if (!canTransition(payment, target)) {
            return Pair(false, "Illegal payment transition from ${current.displayName} to ${target.displayName} on branch ${payment.branch}.")
        }

        payment.state = target
        historyList?.add(
            PaymentStatusHistory(
                id = "PAYHIST-" + UUID.randomUUID().toString().take(6),
                paymentId = payment.id,
                fromState = current,
                toState = target,
                actor = actor,
                reason = reason,
                timestamp = System.currentTimeMillis()
            )
        )
        return Pair(true, "Payment advanced to ${target.displayName}")
    }
}
