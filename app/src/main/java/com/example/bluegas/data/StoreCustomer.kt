/**
 * Data store operations and state transitions specific to the customer application.
 *
 * Implements:
 * - Address book modifications, default address resolution, and zone serviceability tagging
 * - Customer post-delivery star ratings and feedback logging
 * - Customer notification read state toggling
 * - Depot pickup counter arrival signaling with idempotent notification safeguards
 * - Safety concern reporting linking into support ticketing and the HSE incident response engine
 * - Customer persona switching for pilot testing and identity simulation
 *
 * Modifies: [BlueGasStore.addresses], [BlueGasStore.orders], [BlueGasStore.notifications],
 *           [BlueGasStore.supportCases], [BlueGasStore.activeCustomerId]
 */
package com.example.bluegas.data

import com.example.bluegas.model.*
import java.util.UUID

/**
 * Adds a new delivery address for a customer.
 *
 * Checks delivery zone serviceability against the system zone registry.
 * If [makeDefault] is true or if this is the customer's first address, sets it as default
 * and clears default status from any existing addresses.
 *
 * @param customerId Target customer ID.
 * @param label Friendly name (e.g., "Home", "Office").
 * @param street Street address line or house number.
 * @param area Sector, neighborhood, or phase.
 * @param city City name (e.g., "Lahore", "Islamabad").
 * @param zoneId Operational service zone identifier.
 * @param makeDefault Whether to designate this address as the customer's default.
 * @return Pair of success flag and new address ID (or error message if validation failed).
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
        addresses.filter { it.customerId == customerId }.forEachIndexed { _, addr ->
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

/**
 * Designates an existing customer address as their default delivery address.
 *
 * Clears default designation from all other addresses owned by the customer.
 *
 * @param customerId Customer ID owning the address.
 * @param addressId Unique address identifier.
 * @return Pair of success boolean and confirmation or error message.
 */
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

/**
 * Records customer star rating and feedback comment for a completed order.
 *
 * Verifies that the order has reached terminal completion ([OrderStatus.ORD_COMP])
 * and has not been rated previously.
 *
 * @param orderId Target order ID.
 * @param stars Rating score from 1 to 5.
 * @param comment Optional text commentary describing the delivery experience.
 * @return Pair of success boolean and customer confirmation message.
 */
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

/**
 * Marks an unread notification as read for the active surface.
 *
 * @param notificationId Unique notification identifier.
 * @return Pair of success boolean and status string.
 */
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

/**
 * Signals to depot counter staff that the customer has arrived to collect a pickup order.
 *
 * Implements idempotent arrival signaling: validates order is [OrderStatus.ORD_RDYPU],
 * and alerts staff without prematurely advancing order state (handover occurs when staff
 * verifies the customer PIN).
 *
 * @param orderId Identifier of the pickup order.
 * @return Pair of success boolean and instructions for the customer.
 */
fun BlueGasStore.notifyPickupArrival(orderId: String): Pair<Boolean, String> {
    val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
    if (order.status != OrderStatus.ORD_RDYPU) {
        return Pair(false, "Order is not ready for pickup.")
    }
    // Prevent duplicate alerts if the customer taps arrival multiple times
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

/**
 * Registers an emergency safety concern from a customer, creating a support case and HSE incident.
 *
 * Connects directly to the Level 7 HSE Incident escalation engine ([raiseIncident]).
 * Relies on the incident manager to dispatch staff alerts, avoiding redundant duplicate notifications.
 *
 * @param customerId Reporting customer ID.
 * @param description Detailed text of the hazard or safety concern.
 * @param orderId Optional related order ID if the concern pertains to a specific delivery.
 * @return Pair of success boolean and case ticket confirmation message.
 */
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

    // Link into HSE incident management engine which publishes unified staff alert
    raiseIncident(
        source = "CUSTOMER_REPORT",
        description = description,
        orderId = orderId
    )
    notifyChanged()
    return Pair(true, "Safety concern registered. Ticket: $caseId. Our safety team has been alerted.")
}

/**
 * Switches the active customer identity across the application without reloading or losing state.
 *
 * @param customerId Target customer ID to switch to.
 * @return Pair of success boolean and confirmation message with customer name.
 */
fun BlueGasStore.setActiveCustomer(customerId: String): Pair<Boolean, String> {
    val cust = customers.find { it.id == customerId }
        ?: return Pair(false, "Customer not found.")
    activeCustomerId = customerId
    notifyChanged()
    return Pair(true, "Switched to ${cust.name}")
}
