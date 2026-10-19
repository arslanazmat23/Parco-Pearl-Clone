package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Operations Management Store Operations (Operations Manager — RACI-01, 06, 07, 12, 19)
 */

fun BlueGasStore.reserveAndAssign(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId }
        ?: return Pair(false, "Order $orderId not found.")

    if (order.status != OrderStatus.ORD_CONF) {
        return Pair(false, "Order $orderId is in status ${order.status.canonicalLabel}; only Confirmed orders can be reserved and assigned.")
    }

    onConfirmed(order)

    return if (order.status == OrderStatus.ORD_RSVD || order.status == OrderStatus.ORD_ASGN) {
        Pair(true, "Order $orderId successfully advanced to ${order.status.canonicalLabel}.")
    } else {
        Pair(false, "Could not reserve stock or assign order (serving point inventory exhausted).")
    }
}

fun BlueGasStore.resolveSerialConflict(
    conflictId: String,
    winningOrderId: String,
    reason: String,
    actor: String = "Operations Lead"
): Pair<Boolean, String> {
    if (reason.isBlank()) {
        return Pair(false, "A resolution reason must be provided.")
    }

    val conflict = opsConflictQueue.find { it.id == conflictId }
        ?: return Pair(false, "Conflict item $conflictId not found.")

    if (conflict.status == "RESOLVED") {
        return Pair(false, "Conflict $conflictId is already resolved.")
    }

    val losingOrderId = if (conflict.orderId1 == winningOrderId) conflict.orderId2 else conflict.orderId1
    val loser = orders.find { it.id == losingOrderId }

    if (loser != null && !loser.status.isTerminal) {
        raiseException(
            orderId = losingOrderId,
            reason = ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE,
            details = "Lost offline serial conflict on ${conflict.serialNumber}: $reason",
            actor = actor
        )
    }

    conflict.status = "RESOLVED"

    addNotification(
        surface = AppSurface.STAFF,
        title = "Serial Conflict Resolved (EXC-13)",
        message = "Conflict on ${conflict.serialNumber} resolved in favour of $winningOrderId. Reason: $reason",
        canonicalStatus = "RESOLVED",
        orderId = winningOrderId,
        code = "NTF-12",
        staffRole = StaffSubRole.OPERATIONS
    )

    logAudit(
        entityType = "SerialConflict",
        entityId = conflictId,
        action = "RESOLVE_SERIAL_CONFLICT",
        actor = actor,
        role = AccountableRole.OPERATIONS,
        details = "Winner: $winningOrderId, Loser: $losingOrderId, Serial: ${conflict.serialNumber}, Reason: $reason"
    )

    notifyChanged()
    return Pair(true, "Conflict resolved. $winningOrderId retained serial ${conflict.serialNumber}.")
}
