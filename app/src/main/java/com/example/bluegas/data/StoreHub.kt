package com.example.bluegas.data

import com.example.bluegas.model.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Returns today's date formatted as ISO yyyy-MM-dd for safety test due checks.
 */
internal fun todayIsoDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())

/**
 * Returns true if the cylinder's next periodic safety test date is on or before today.
 *
 * @param today Reference ISO date string (yyyy-MM-dd).
 */
internal fun Cylinder.isTestDue(today: String = todayIsoDate()): Boolean =
    nextTestDueDate != null && nextTestDueDate <= today

/**
 * Consumes hub filled stock and firm reservation when a cylinder physically leaves the hub.
 *
 * For delivery trips ([FulfilmentMode.DELIVERY]), increments [StockBalance.outWithRidersQty].
 * For depot counter pickups ([FulfilmentMode.PICKUP]), the cylinder goes straight to the customer
 * so [StockBalance.outWithRidersQty] remains unchanged. Guarded by committed [StockReservation]
 * status so replays or duplicate calls cannot double-subtract stock.
 *
 * @param order The order whose bound cylinder is leaving the hub.
 */
internal fun BlueGasStore.stockLeavesHub(order: Order) {
    if (order.orderType == OrderType.ACCESSORY_ONLY) return
    val gasItem = order.items.firstOrNull {
        it.lineType == "GAS" || it.lineType == "CYLINDER" || it.sku.startsWith("SKU-LPG")
    } ?: return

    // Idempotency guard: already consumed if a committed reservation exists for this order
    if (stockReservations.any { it.orderId == order.id && it.isCommitted }) return

    val res = stockReservations.find { it.orderId == order.id && !it.isCommitted }
    val qty = res?.quantity ?: gasItem.quantity
    val pointId = order.pickupPointId ?: order.fulfilmentPointId ?: return
    val stock = stockBalances.find { it.pointId == pointId && it.skuId == gasItem.sku } ?: return

    stock.filledQty = (stock.filledQty - qty).coerceAtLeast(0)
    stock.reservedQty = (stock.reservedQty - qty).coerceAtLeast(0)
    if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
        stock.outWithRidersQty += qty
    }

    if (res != null) {
        res.isCommitted = true
    } else {
        stockReservations.add(
            StockReservation(
                id = "RSV-COM-" + UUID.randomUUID().toString().take(6),
                orderId = order.id,
                sku = gasItem.sku,
                quantity = qty,
                reservedAt = System.currentTimeMillis(),
                isCommitted = true
            )
        )
    }
}

/**
 * Decrements [StockBalance.outWithRidersQty] when a rider completes doorstep delivery handover.
 * Never called for counter pickup orders.
 *
 * @param order The completed delivery order.
 */
internal fun BlueGasStore.stockHandedOver(order: Order) {
    if (order.fulfilmentMode != FulfilmentMode.DELIVERY) return
    if (order.orderType == OrderType.ACCESSORY_ONLY) return
    val gasItem = order.items.firstOrNull {
        it.lineType == "GAS" || it.lineType == "CYLINDER" || it.sku.startsWith("SKU-LPG")
    } ?: return

    val qty = stockReservations.find { it.orderId == order.id }?.quantity ?: gasItem.quantity
    val pointId = order.fulfilmentPointId ?: return
    val stock = stockBalances.find { it.pointId == pointId && it.skuId == gasItem.sku } ?: return

    stock.outWithRidersQty = (stock.outWithRidersQty - qty).coerceAtLeast(0)
}

/**
 * Scans a returned cylinder back into a hub after an order exception or failed trip.
 *
 * Requires [Cylinder.pendingHubScanIn] to be true and verifies that the cylinder belongs to an
 * order from [pointId]. Restores custody to [CylinderCustody.HUB], status to [InventoryState.INV_AVAIL],
 * decrements [StockBalance.outWithRidersQty], increments [StockBalance.filledQty], and restores the
 * order's firm reservation if the originating order is still active (non-terminal).
 *
 * @param pointId Target hub node identifier receiving the returned cylinder.
 * @param serial Scanned or typed cylinder serial number.
 * @param actor Accountable role display name recording the scan-in.
 * @return Pair of success flag and human-readable status message.
 */
fun BlueGasStore.hubScanInReturnedCylinder(
    pointId: String,
    serial: String,
    actor: String = AccountableRole.HUB.displayName
): Pair<Boolean, String> {
    val cleanSerial = serial.trim()
    if (cleanSerial.isEmpty()) {
        return Pair(false, "Enter or scan a cylinder ID.")
    }

    val cyl = cylinders.find { it.serialNumber.equals(cleanSerial, ignoreCase = true) }
        ?: return Pair(false, "Cylinder $cleanSerial was not found.")

    if (!cyl.pendingHubScanIn) {
        return Pair(false, "Cylinder ${cyl.serialNumber} is not waiting to be scanned back in.")
    }

    val lastOrderId = cylinderCustodyEvents
        .lastOrNull { it.serialNumber == cyl.serialNumber && it.orderId != null }
        ?.orderId
    val prevOrder = lastOrderId?.let { oid -> orders.find { it.id == oid } }
    val expectedPointId = prevOrder?.let { it.pickupPointId ?: it.fulfilmentPointId } ?: cyl.pointId

    if (expectedPointId != null && expectedPointId != pointId) {
        val ownerHubName = nodes.find { it.id == expectedPointId }?.name ?: expectedPointId
        return Pair(false, "Cylinder ${cyl.serialNumber} belongs to $ownerHubName, not this hub.")
    }

    val hubName = nodes.find { it.id == pointId }?.name ?: pointId
    val idx = cylinders.indexOf(cyl)
    cylinders[idx] = cyl.copy(
        status = InventoryState.INV_AVAIL,
        custody = CylinderCustody.HUB,
        currentLocationLabel = hubName,
        pointId = pointId,
        allocatedOrderId = null,
        pendingHubScanIn = false
    )

    val stock = stockBalances.find { it.pointId == pointId && it.skuId == cyl.sku }
    if (stock != null) {
        stock.outWithRidersQty = (stock.outWithRidersQty - 1).coerceAtLeast(0)
        stock.filledQty += 1
        if (prevOrder != null && !prevOrder.status.isTerminal) {
            stock.reservedQty += 1
            val res = stockReservations.find { it.orderId == prevOrder.id }
            if (res != null) {
                res.isCommitted = false
            } else {
                stockReservations.add(
                    StockReservation(
                        id = "RSV-" + UUID.randomUUID().toString().take(6),
                        orderId = prevOrder.id,
                        sku = cyl.sku,
                        quantity = 1,
                        reservedAt = System.currentTimeMillis(),
                        isCommitted = false
                    )
                )
            }
        }
    }

    cylinderCustodyEvents.add(
        CylinderCustodyEvent(
            id = "EVT-" + UUID.randomUUID().toString().take(6),
            serialNumber = cyl.serialNumber,
            fromCustody = CylinderCustody.RIDER_VAN,
            toCustody = CylinderCustody.HUB,
            orderId = prevOrder?.id,
            actor = actor
        )
    )
    logAudit(
        entityType = "Cylinder",
        entityId = cyl.serialNumber,
        action = "HUB_SCAN_IN_RETURN",
        actor = actor,
        role = AccountableRole.HUB,
        details = "Scanned returned cylinder ${cyl.serialNumber} back into $hubName"
    )
    notifyChanged()
    return Pair(true, "Cylinder ${cyl.serialNumber} scanned back into $hubName.")
}
