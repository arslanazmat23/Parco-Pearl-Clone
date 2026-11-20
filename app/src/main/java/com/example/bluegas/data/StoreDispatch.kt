package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Automatic rider-first dispatch logic for To-Be P3 / BRD v0.5 §10.4 (RACI-06).
 */

/**
 * Returns true if this order was confirmed under [DispatchMode.AUTO_RIDER_FIRST] and requires
 * doorstep delivery. Uses [Order.dispatchModeAtConfirm] so switching settings never strands live orders.
 */
internal fun Order.isAutoDispatchDelivery(): Boolean =
    dispatchModeAtConfirm == DispatchMode.AUTO_RIDER_FIRST &&
        fulfilmentMode == FulfilmentMode.DELIVERY

internal fun BlueGasStore.orderLabel(orderId: String): String {
    val shortId = orderId.takeLast(4)
    val order = orders.find { it.id == orderId }
    val name = customers.find { it.id == order?.customerId }?.name.orEmpty()
    return if (name.isNotBlank()) "Order #$shortId · $name" else "Order #$shortId"
}

private fun deliveryWindowsOverlap(a: Order, b: Order): Boolean {
    val aStart = a.windowStartAt
    val aEnd = a.windowEndAt
    val bStart = b.windowStartAt
    val bEnd = b.windowEndAt
    if (aStart == null || aEnd == null || bStart == null || bEnd == null) return true
    return aStart < bEnd && bStart < aEnd
}

/**
 * Automatically selects an eligible, trained, on-shift rider at the order's fulfilment hub
 * and offers them the delivery task.
 *
 * Candidate eligibility:
 * - [Rider.pointId] matches [Order.fulfilmentPointId]
 * - [Rider.onboardingStatus] == "APPROVED", [Rider.status] == "ACTIVE", [Rider.installationTrained] == true
 * - [Rider.id] not in [Order.declinedRiderIds]
 *
 * Scoring priority:
 * 1. Riders already holding an offered/accepted non-completed delivery in the same delivery zone
 *    whose window overlaps (ASAP null windows overlap everything), while active job count is below
 *    [PlatformSettings.riderBatchMax] (area batching).
 * 2. Fewest active offered/accepted jobs.
 * 3. Alphabetical by [Rider.name].
 *
 * When no candidate is available, clears rider assignment fields and alerts Operations.
 *
 * @param orderId Target delivery order identifier.
 * @return Pair of success flag and human-readable status message.
 */
fun BlueGasStore.autoOfferRider(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    val hubId = order.fulfilmentPointId ?: return Pair(false, "Order has no assigned hub.")
    val orderAddress = addresses.find { it.id == order.addressId }
    val orderZoneId = orderAddress?.zoneId
    val areaName = orderAddress?.area?.takeIf { it.isNotBlank() }
        ?: serviceZones.find { it.id == orderZoneId }?.name
        ?: orderAddress?.city
        ?: "assigned area"

    val candidates = riders.filter { r ->
        r.pointId == hubId &&
            r.onboardingStatus == "APPROVED" &&
            r.status == "ACTIVE" &&
            r.installationTrained &&
            r.id !in order.declinedRiderIds
    }

    if (candidates.isEmpty()) {
        order.assignedRiderId = null
        order.riderTaskStatus = null
        order.riderOfferedAt = null
        order.riderCylinderVerified = false
        val hubName = nodes.find { it.id == hubId }?.name ?: hubId
        val label = orderLabel(order.id)
        val alertMessage = "No free rider at $hubName — needs a rider: $label"
        addNotification(
            surface = AppSurface.STAFF,
            title = "No free rider",
            message = alertMessage,
            canonicalStatus = order.status.canonicalLabel,
            orderId = order.id,
            code = "NTF-03",
            staffRole = StaffSubRole.OPERATIONS
        )
        logAudit(
            entityType = "Order",
            entityId = order.id,
            action = "AUTO_OFFER_NONE",
            actor = "BLUE GAS platform (system)",
            role = AccountableRole.OPERATIONS,
            details = alertMessage
        )
        notifyChanged()
        return Pair(false, alertMessage)
    }

    data class CandidateScore(
        val rider: Rider,
        val hasBatchMatch: Boolean,
        val matchingBatchOrders: Int,
        val activeJobsCount: Int
    )

    val scored = candidates.map { candidate ->
        val activeJobs = orders.filter { o ->
            o.id != order.id &&
                o.assignedRiderId == candidate.id &&
                !o.status.isTerminal &&
                o.status != OrderStatus.ORD_COMP &&
                o.status != OrderStatus.ORD_EXC &&
                (o.riderTaskStatus == RiderTaskStatus.OFFERED || o.riderTaskStatus == RiderTaskStatus.ACCEPTED)
        }
        val batchMatches = if (orderZoneId == null) {
            0
        } else {
            activeJobs.count { activeOrd ->
                activeOrd.fulfilmentMode == FulfilmentMode.DELIVERY &&
                    addresses.find { it.id == activeOrd.addressId }?.zoneId == orderZoneId &&
                    deliveryWindowsOverlap(order, activeOrd)
            }
        }
        val canBatch = batchMatches > 0 && activeJobs.size < settings.riderBatchMax
        CandidateScore(
            rider = candidate,
            hasBatchMatch = canBatch,
            matchingBatchOrders = batchMatches,
            activeJobsCount = activeJobs.size
        )
    }

    val winnerScore = scored.sortedWith(
        compareByDescending<CandidateScore> { it.hasBatchMatch }
            .thenBy { it.activeJobsCount }
            .thenBy { it.rider.name }
    ).first()

    val winner = winnerScore.rider
    order.assignedRiderId = winner.id
    order.riderTaskStatus = RiderTaskStatus.OFFERED
    order.riderOfferedAt = clock()
    order.riderCylinderVerified = false

    val auditDetails = if (winnerScore.hasBatchMatch) {
        "Platform offered to ${winner.name} (batched with ${winnerScore.matchingBatchOrders} orders in $areaName)"
    } else {
        "Platform offered to ${winner.name}"
    }
    logAudit(
        entityType = "Order",
        entityId = order.id,
        action = "AUTO_OFFER_RIDER",
        actor = "BLUE GAS platform (system)",
        role = AccountableRole.OPERATIONS,
        details = auditDetails
    )
    addNotification(
        surface = AppSurface.RIDER,
        title = "New Delivery Task",
        message = "New delivery task for Order ${order.id} offered.",
        canonicalStatus = order.status.canonicalLabel,
        orderId = order.id,
        code = "NTF-03"
    )
    notifyChanged()
    return Pair(true, "Offered delivery to ${winner.name}.")
}
