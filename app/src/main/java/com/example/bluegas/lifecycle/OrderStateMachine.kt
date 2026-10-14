package com.example.bluegas.lifecycle

import com.example.bluegas.model.AccountableRole
import com.example.bluegas.model.ExceptionReason
import com.example.bluegas.model.FulfilmentMode
import com.example.bluegas.model.Order
import com.example.bluegas.model.OrderStatus

sealed class TransitionResult {
    data class Success(
        val order: Order,
        val fromStatus: OrderStatus,
        val toStatus: OrderStatus,
        val actor: String,
        val role: AccountableRole
    ) : TransitionResult()

    data class Failure(
        val fromStatus: OrderStatus,
        val attemptedStatus: OrderStatus,
        val errorMessage: String,
        val violatorRole: AccountableRole
    ) : TransitionResult()
}

object OrderStateMachine {

    /**
     * Complete and only 42 allowed static edges in the state machine graph.
     */
    val allowedTransitionsMap: Map<OrderStatus, Set<OrderStatus>> = mapOf(
        OrderStatus.ORD_DRAFT to setOf(OrderStatus.ORD_VALPEND, OrderStatus.ORD_CANC),
        OrderStatus.ORD_VALPEND to setOf(OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CANC, OrderStatus.ORD_EXC),
        OrderStatus.ORD_PAYPEND to setOf(OrderStatus.ORD_CONF, OrderStatus.ORD_CANC, OrderStatus.ORD_EXC),
        OrderStatus.ORD_CONF to setOf(OrderStatus.ORD_RSVD, OrderStatus.ORD_CANC, OrderStatus.ORD_SUPS),
        OrderStatus.ORD_RSVD to setOf(OrderStatus.ORD_ASGN, OrderStatus.ORD_CANC, OrderStatus.ORD_SUPS),
        OrderStatus.ORD_ASGN to setOf(OrderStatus.ORD_ACPT, OrderStatus.ORD_EXC, OrderStatus.ORD_CANC),
        OrderStatus.ORD_ACPT to setOf(OrderStatus.ORD_PREP, OrderStatus.ORD_CANC),
        OrderStatus.ORD_PREP to setOf(OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU, OrderStatus.ORD_EXC, OrderStatus.ORD_CANC),
        OrderStatus.ORD_DISP to setOf(OrderStatus.ORD_ATHO, OrderStatus.ORD_EXC),
        OrderStatus.ORD_RDYPU to setOf(OrderStatus.ORD_ATHO, OrderStatus.ORD_EXC),
        OrderStatus.ORD_ATHO to setOf(OrderStatus.ORD_INSP, OrderStatus.ORD_HOC, OrderStatus.ORD_EXC),
        OrderStatus.ORD_INSP to setOf(OrderStatus.ORD_ADJ, OrderStatus.ORD_HOC, OrderStatus.ORD_EXC),
        OrderStatus.ORD_ADJ to setOf(OrderStatus.ORD_HOC, OrderStatus.ORD_EXC),
        OrderStatus.ORD_HOC to setOf(OrderStatus.ORD_COMP),
        OrderStatus.ORD_EXC to setOf(
            OrderStatus.ORD_PAYPEND,
            OrderStatus.ORD_ASGN,
            OrderStatus.ORD_PREP,
            OrderStatus.ORD_ADJ,
            OrderStatus.ORD_CANC,
            OrderStatus.ORD_FCLO
        ),
        // Terminal states have no outgoing transitions:
        OrderStatus.ORD_COMP to emptySet(),
        OrderStatus.ORD_CANC to emptySet(),
        OrderStatus.ORD_SUPS to emptySet(),
        OrderStatus.ORD_FCLO to emptySet()
    )

    /**
     * Get legal next transitions for the given order, respecting fulfilment mode and exception recovery constraints.
     */
    fun getLegalNextTransitions(order: Order): List<OrderStatus> {
        val standard = allowedTransitionsMap[order.status] ?: emptySet()
        return standard.filter { target ->
            validateGuards(order, target).isEmpty()
        }
    }

    /**
     * Guarded transition function.
     * Every status change in the app goes through here.
     */
    fun transition(
        order: Order,
        targetStatus: OrderStatus,
        actor: String,
        role: AccountableRole,
        trigger: String,
        exceptionReason: ExceptionReason? = null,
        exceptionDetails: String? = null
    ): TransitionResult {
        val currentStatus = order.status

        // Check if current is terminal
        if (currentStatus.isTerminal) {
            return TransitionResult.Failure(
                fromStatus = currentStatus,
                attemptedStatus = targetStatus,
                errorMessage = "Illegal transition: Order is in terminal state ${currentStatus.canonicalLabel} and cannot transition.",
                violatorRole = role
            )
        }

        // Check if transition is in the static allowed transitions table
        val allowedTargets = allowedTransitionsMap[currentStatus] ?: emptySet()
        if (!allowedTargets.contains(targetStatus)) {
            return TransitionResult.Failure(
                fromStatus = currentStatus,
                attemptedStatus = targetStatus,
                errorMessage = "Invalid transition path: ${currentStatus.canonicalLabel} -> ${targetStatus.canonicalLabel} is strictly forbidden by the 42-rule lifecycle table.",
                violatorRole = role
            )
        }

        // Check contextual guard conditions
        val guardError = validateGuards(order, targetStatus, exceptionReason)
        if (guardError.isNotEmpty()) {
            return TransitionResult.Failure(
                fromStatus = currentStatus,
                attemptedStatus = targetStatus,
                errorMessage = guardError,
                violatorRole = role
            )
        }

        // Apply transition
        val fromStatus = order.status
        order.status = targetStatus

        if (targetStatus == OrderStatus.ORD_EXC) {
            order.exceptionReason = exceptionReason ?: ExceptionReason.UNRECOVERABLE_AFTER_RETRIES
            order.exceptionDetails = exceptionDetails ?: "Fulfilment exception raised by $actor ($trigger)"
        } else if (fromStatus == OrderStatus.ORD_EXC) {
            // Clearing exception on recovery
            order.exceptionReason = null
            order.exceptionDetails = null
        }

        return TransitionResult.Success(
            order = order,
            fromStatus = fromStatus,
            toStatus = targetStatus,
            actor = actor,
            role = role
        )
    }

    private fun validateGuards(
        order: Order,
        target: OrderStatus,
        exceptionReason: ExceptionReason? = null
    ): String {
        // Mode-specific guards: Dispatched vs Ready for Pickup
        if (target == OrderStatus.ORD_DISP && order.fulfilmentMode != FulfilmentMode.DELIVERY) {
            return "ORD_DISP (Dispatched) is only permitted for Delivery orders. This order is Pickup mode."
        }
        if (target == OrderStatus.ORD_RDYPU && order.fulfilmentMode != FulfilmentMode.PICKUP) {
            return "ORD_RDYPU (Ready for Pickup) is only permitted for Pickup orders. This order is Delivery mode."
        }

        // Serial binding requirement: Exact outgoing cylinder serial bound during Preparing before Dispatched / Ready for Pickup
        if ((target == OrderStatus.ORD_DISP || target == OrderStatus.ORD_RDYPU) && order.allocatedSerial == null) {
            return "Serial allocation invariant violated: Outgoing cylinder serial must be bound before dispatching or marking ready for pickup."
        }

        // Fulfilment Exception recovery target constraints
        if (order.status == OrderStatus.ORD_EXC) {
            val reason = order.exceptionReason ?: ExceptionReason.UNRECOVERABLE_AFTER_RETRIES
            val allowedRecoveryTargets = when (reason) {
                ExceptionReason.VALIDATION_OUT_OF_AREA -> listOf(OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CANC)
                ExceptionReason.PAYMENT_ISSUE -> listOf(OrderStatus.ORD_PAYPEND)
                ExceptionReason.NO_ACCEPTANCE -> listOf(OrderStatus.ORD_ASGN)
                ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE -> listOf(OrderStatus.ORD_PREP)
                ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE -> listOf(OrderStatus.ORD_ASGN, OrderStatus.ORD_CANC)
                ExceptionReason.ADJUSTED_AMOUNT_REJECTION -> listOf(OrderStatus.ORD_ADJ, OrderStatus.ORD_CANC)
                ExceptionReason.UNRECOVERABLE_AFTER_RETRIES -> listOf(OrderStatus.ORD_FCLO)
            }
            if (!allowedRecoveryTargets.contains(target)) {
                return "Recovery constraint violated: Exception reason '${reason.description}' allows recovery only to: ${allowedRecoveryTargets.joinToString { it.canonicalLabel }}, but attempted ${target.canonicalLabel}."
            }
        }

        return ""
    }
}
