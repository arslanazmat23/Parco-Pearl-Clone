package com.example.bluegas.data

import com.example.bluegas.model.*
import java.util.UUID

/**
 * Customer App Store Operations
 */

fun BlueGasStore.addAddress(
    customerId: String,
    label: String,
    street: String,
    area: String,
    city: String,
    zoneId: String,
    makeDefault: Boolean
): Pair<Boolean, String> {
    if (label.isBlank() || street.isBlank()) {
        return Pair(false, "Label and street address cannot be empty.")
    }

    val zone = serviceZones.find { it.id == zoneId }
    val isServiceable = zone?.isServiceable ?: false
    val servingPointId = zone?.servingPointIds?.firstOrNull() ?: "NODE-LHR-1"

    val newId = "ADDR-" + UUID.randomUUID().toString().take(6).uppercase()

    if (makeDefault) {
        addresses.filter { it.customerId == customerId }.forEachIndexed { idx, addr ->
            if (addr.isDefault) {
                val realIdx = addresses.indexOf(addr)
                if (realIdx != -1) {
                    addresses[realIdx] = addr.copy(isDefault = false)
                }
            }
        }
    }

    val newAddress = Address(
        id = newId,
        customerId = customerId,
        label = label.trim(),
        street = street.trim(),
        area = area.trim(),
        city = city.trim(),
        zoneId = zoneId,
        isServiceable = isServiceable,
        servingPointId = servingPointId,
        isDefault = makeDefault || addresses.none { it.customerId == customerId }
    )

    addresses.add(newAddress)
    logAudit("Address", newId, "ADD_ADDRESS", customerId, AccountableRole.SUPPORT, "Added address '$label' in $city ($zoneId), serviceable: $isServiceable")
    notifyChanged()
    return Pair(true, newId)
}

fun BlueGasStore.setDefaultAddress(
    customerId: String,
    addressId: String
): Pair<Boolean, String> {
    val target = addresses.find { it.id == addressId && it.customerId == customerId }
        ?: return Pair(false, "Address not found.")

    addresses.filter { it.customerId == customerId }.forEach { addr ->
        val idx = addresses.indexOf(addr)
        if (idx != -1) {
            addresses[idx] = addr.copy(isDefault = (addr.id == addressId))
        }
    }

    logAudit("Address", addressId, "SET_DEFAULT", customerId, AccountableRole.SUPPORT, "Set ${target.label} as default address")
    notifyChanged()
    return Pair(true, "Default address updated.")
}

fun BlueGasStore.rateOrder(
    orderId: String,
    stars: Int,
    comment: String
): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId }
        ?: return Pair(false, "Order not found.")

    if (order.status != OrderStatus.ORD_COMP) {
        return Pair(false, "Only completed orders can be rated.")
    }

    if (order.ratingStars != null) {
        return Pair(false, "This order has already been rated.")
    }

    if (stars !in 1..5) {
        return Pair(false, "Rating must be between 1 and 5 stars.")
    }

    order.ratingStars = stars
    order.ratingComment = comment.trim().ifEmpty { null }

    logAudit("Order", orderId, "RATE_ORDER", order.customerId, AccountableRole.SUPPORT, "Rated $stars stars: $comment")
    notifyChanged()
    return Pair(true, "Thank you for rating your delivery!")
}

fun BlueGasStore.markNotificationRead(
    notificationId: String
): Pair<Boolean, String> {
    val notif = notifications.find { it.id == notificationId }
        ?: return Pair(false, "Notification not found.")

    if (!notif.isRead) {
        notif.isRead = true
        notifyChanged()
    }
    return Pair(true, "Notification marked as read.")
}

fun BlueGasStore.notifyPickupArrival(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_RDYPU) {
        return Pair(false, "Order is not ready for pickup.")
    }
    val alreadyNotified = notifications.any {
        it.orderId == orderId && it.title == "Customer Arrived for Pickup"
    }
    if (alreadyNotified) {
        return Pair(true, "Pickup counter already notified. Please present your handover PIN.")
    }
    addNotification(
        surface = AppSurface.STAFF,
        title = "Customer Arrived for Pickup",
        message = "Customer has arrived at hub for order $orderId",
        canonicalStatus = "READY_FOR_PICKUP",
        orderId = orderId,
        staffRole = StaffSubRole.OPERATIONS
    )
    addNotification(
        surface = AppSurface.CUSTOMER,
        title = "Arrival Confirmed",
        message = "Your arrival has been notified. Please proceed to the collection counter.",
        canonicalStatus = "READY_FOR_PICKUP",
        orderId = orderId
    )
    logAudit("Order", orderId, "NOTIFY_PICKUP_ARRIVAL", order.customerId, AccountableRole.SUPPORT, "Customer notified arrival at depot for pickup order $orderId")
    notifyChanged()
    return Pair(true, "Arrival notified. Please show your PIN at the counter.")
}

fun BlueGasStore.raiseSafetyConcern(
    customerId: String,
    description: String,
    orderId: String? = null
): Pair<Boolean, String> {
    if (description.isBlank()) {
        return Pair(false, "Description cannot be empty.")
    }
    val caseId = "CASE-" + UUID.randomUUID().toString().take(6).uppercase()
    val newCase = SupportCase(
        id = caseId,
        orderId = orderId,
        customerId = customerId,
        issueType = "SAFETY_CONCERN",
        description = description,
        status = "OPEN",
        raisedAt = System.currentTimeMillis()
    )
    supportCases.add(newCase)
    logAudit("SupportCase", caseId, "SAFETY_CONCERN", customerId, AccountableRole.SUPPORT, "Safety concern reported: $description")
    addNotification(
        surface = AppSurface.STAFF,
        title = "Safety Concern",
        message = "Customer safety concern reported ($caseId): $description",
        canonicalStatus = "SAFETY_CONCERN",
        orderId = orderId,
        staffRole = StaffSubRole.SAFETY_HSE
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Safety Concern",
        message = "Customer safety concern reported ($caseId): $description",
        canonicalStatus = "SAFETY_CONCERN",
        orderId = orderId,
        staffRole = StaffSubRole.OPERATIONS
    )
    raiseIncident(
        source = "CUSTOMER_REPORT",
        description = description,
        orderId = orderId
    )
    notifyChanged()
    return Pair(true, "Safety concern registered. Ticket: $caseId. Our safety team has been alerted.")
}

fun BlueGasStore.setActiveCustomer(customerId: String): Pair<Boolean, String> {
    val cust = customers.find { it.id == customerId }
        ?: return Pair(false, "Customer not found.")
    activeCustomerId = customerId
    notifyChanged()
    return Pair(true, "Switched to ${cust.name}")
}

