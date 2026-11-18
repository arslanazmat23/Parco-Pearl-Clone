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
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
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
        orderId = orderId,
        actor = customers.find { it.id == customerId }?.name ?: customerId
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

/**
 * Returns true if placing an order of [orderType] for [customerId] requires capturing
 * and recording the customer's CNIC against the deposit policy.
 */
fun BlueGasStore.needsCnicForDeposit(
    customerId: String,
    orderType: OrderType
): Boolean {
    if (orderType != OrderType.NEW_CYLINDER) return false
    if (!settings.cnicRequiredForDeposit) return false
    val cust = customers.find { it.id == customerId } ?: return false
    return cust.cnicCapturedAt == null
}

/**
 * Validates a 13-digit Pakistani CNIC (#####-#######-#), persists it on the customer record,
 * stamps [Customer.cnicCapturedAt], and writes a governance audit entry.
 */
fun BlueGasStore.captureCnic(
    customerId: String,
    cnic: String
): Pair<Boolean, String> {
    val trimmed = cnic.trim()
    val cnicPattern = Regex("^\\d{5}-\\d{7}-\\d$")
    if (!cnicPattern.matches(trimmed)) {
        return Pair(false, "Enter your 13-digit CNIC in 12345-1234567-1 format.")
    }
    val idx = customers.indexOfFirst { it.id == customerId }
    if (idx == -1) {
        return Pair(false, "Customer profile not found.")
    }
    val existing = customers[idx]
    customers[idx] = existing.copy(
        cnic = trimmed,
        isCnicVerified = true,
        cnicCapturedAt = System.currentTimeMillis()
    )
    logAudit(
        entityType = "Customer",
        entityId = customerId,
        action = "CAPTURE_CNIC",
        actor = existing.name,
        role = AccountableRole.SUPPORT,
        details = "Captured CNIC for cylinder deposit"
    )
    notifyChanged()
    return Pair(true, "CNIC saved.")
}

/** Single selectable delivery or collection window option in Asia/Karachi time. */
data class DeliveryWindowOption(
    val label: String,
    val startAt: Long?,
    val endAt: Long?,
    val isAsap: Boolean
)

/** Due refill reminder summary for a customer based on completed order cadence. */
data class RefillReminder(
    val sku: String,
    val sizeLabel: String,
    val expectedDate: Long,
    val lastCompletedAt: Long
)

private val KARACHI_TZ: TimeZone = TimeZone.getTimeZone("Asia/Karachi")

/** Formats a 12-hour hour range like `"10 am–12 pm"` or `"4–6 pm"`. */
internal fun formatHourRange12h(startHour: Int, endHour: Int): String {
    val startPeriod = if (startHour < 12) "am" else "pm"
    val endPeriod = if (endHour < 12 || endHour == 24) "am" else "pm"
    val start12 = if (startHour % 12 == 0) 12 else startHour % 12
    val end12 = if (endHour % 12 == 0) 12 else endHour % 12
    return if (startPeriod == endPeriod) {
        "$start12–$end12 $endPeriod"
    } else {
        "$start12 $startPeriod–$end12 $endPeriod"
    }
}

/** Formats an epoch timestamp in Asia/Karachi 12-hour clock time (e.g. `"4:30 pm"` or `"11 am"`). */
internal fun formatKarachiTime12h(millis: Long): String {
    val cal = Calendar.getInstance(KARACHI_TZ, Locale.US).apply { timeInMillis = millis }
    val hour24 = cal.get(Calendar.HOUR_OF_DAY)
    val minute = cal.get(Calendar.MINUTE)
    val period = if (hour24 < 12) "am" else "pm"
    val hour12 = if (hour24 % 12 == 0) 12 else hour24 % 12
    return if (minute == 0) {
        "$hour12 $period"
    } else {
        String.format(Locale.US, "%d:%02d %s", hour12, minute, period)
    }
}

/** Computes epoch millis for a given [hour]:00 in Asia/Karachi on today + [addDays]. */
internal fun karachiDayHourMillis(baseMillis: Long, addDays: Int, hour: Int): Long {
    val cal = Calendar.getInstance(KARACHI_TZ, Locale.US).apply {
        timeInMillis = baseMillis
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
        if (addDays != 0) add(Calendar.DAY_OF_YEAR, addDays)
        set(Calendar.HOUR_OF_DAY, hour)
    }
    return cal.timeInMillis
}

/**
 * Pure delivery window builder used across checkout, quick-reorder, and cylinder return scheduling.
 * Filters out today's windows whose end time has already passed as of [nowMillis] in Asia/Karachi.
 */
fun computeDeliveryWindows(
    settings: PlatformSettings,
    nowMillis: Long,
    includeAsap: Boolean = true
): List<DeliveryWindowOption> {
    val out = mutableListOf<DeliveryWindowOption>()
    if (includeAsap && settings.asapEnabled) {
        out.add(
            DeliveryWindowOption(
                label = "As soon as possible",
                startAt = null,
                endAt = null,
                isAsap = true
            )
        )
    }
    val sortedSlots = settings.deliveryWindowsHours.sortedBy { it.first }
    sortedSlots.forEach { (startH, endH) ->
        val startAt = karachiDayHourMillis(nowMillis, addDays = 0, hour = startH)
        val endAt = karachiDayHourMillis(nowMillis, addDays = 0, hour = endH)
        if (endAt > nowMillis) {
            out.add(
                DeliveryWindowOption(
                    label = "Today ${formatHourRange12h(startH, endH)}",
                    startAt = startAt,
                    endAt = endAt,
                    isAsap = false
                )
            )
        }
    }
    if (settings.tomorrowWindowsEnabled) {
        sortedSlots.forEach { (startH, endH) ->
            val startAt = karachiDayHourMillis(nowMillis, addDays = 1, hour = startH)
            val endAt = karachiDayHourMillis(nowMillis, addDays = 1, hour = endH)
            out.add(
                DeliveryWindowOption(
                    label = "Tomorrow ${formatHourRange12h(startH, endH)}",
                    startAt = startAt,
                    endAt = endAt,
                    isAsap = false
                )
            )
        }
    }
    return out
}

/**
 * Returns selectable delivery time windows for the customer based on [PlatformSettings]
 * and the current [Asia/Karachi] clock.
 */
fun BlueGasStore.availableDeliveryWindows(nowMillis: Long = clock()): List<DeliveryWindowOption> =
    computeDeliveryWindows(settings, nowMillis, includeAsap = true)

/**
 * Returns the customer's most recent completed gas order suitable for one-tap refill reorder,
 * or null if none exists.
 */
fun BlueGasStore.lastReorderableOrder(customerId: String): Order? {
    return orders
        .filter {
            it.customerId == customerId &&
                it.status == OrderStatus.ORD_COMP &&
                it.orderType != OrderType.ACCESSORY_ONLY &&
                it.items.any { line -> line.lineType == "GAS" }
        }
        .maxByOrNull { it.completedAt ?: it.createdAt }
}

/**
 * Estimates whether a household refill reminder is due for [customerId] based on past completed
 * orders, [PlatformSettings.refillReminderEnabled], and [PlatformSettings.refillReminderLeadDays].
 */
fun BlueGasStore.refillReminderDue(
    customerId: String,
    nowMillis: Long = clock()
): RefillReminder? {
    if (!settings.refillReminderEnabled) return null
    val customer = customers.find { it.id == customerId } ?: return null

    val completedGasOrders = orders
        .filter {
            it.customerId == customerId &&
                it.status == OrderStatus.ORD_COMP &&
                it.orderType != OrderType.ACCESSORY_ONLY &&
                it.items.any { line -> line.lineType == "GAS" }
        }
        .sortedBy { it.completedAt ?: it.createdAt }

    if (completedGasOrders.isEmpty()) return null

    val lastOrder = completedGasOrders.last()
    val lastCompletedAt = lastOrder.completedAt ?: lastOrder.createdAt
    if (customer.reminderDismissedForCompletedAt == lastCompletedAt) return null

    val lastGasSku = lastOrder.items.firstOrNull { it.lineType == "GAS" }?.sku ?: "SKU-LPG-11-8"
    val sizeKg = productVariants.find { it.sku == lastGasSku }?.cylinderSizeKg
    val sizeLabel = sizeKg?.let { "$it kg" } ?: "LPG"

    val oneDayMs = 86_400_000L
    val defaultCycleMs = 30L * oneDayMs

    val sameSkuCompleted = completedGasOrders.filter { ord ->
        ord.items.any { it.lineType == "GAS" && it.sku == lastGasSku }
    }
    val series = if (sameSkuCompleted.size >= 2) sameSkuCompleted else completedGasOrders

    val dayGaps = series.zipWithNext { a, b ->
        (b.completedAt ?: b.createdAt) - (a.completedAt ?: a.createdAt)
    }.filter { it >= oneDayMs }

    val cycleMs = if (dayGaps.isNotEmpty()) {
        dayGaps.sum() / dayGaps.size
    } else {
        defaultCycleMs
    }

    val expectedDate = lastCompletedAt + cycleMs
    val leadMs = settings.refillReminderLeadDays * oneDayMs
    if (nowMillis < expectedDate - leadMs) return null

    return RefillReminder(
        sku = lastGasSku,
        sizeLabel = sizeLabel,
        expectedDate = expectedDate,
        lastCompletedAt = lastCompletedAt
    )
}

/**
 * Dismisses the refill reminder for [customerId]'s latest completed order so it stays hidden
 * until a newer order completes.
 */
fun BlueGasStore.dismissRefillReminder(customerId: String): Pair<Boolean, String> {
    val lastCompletedAt = orders
        .filter {
            it.customerId == customerId &&
                it.status == OrderStatus.ORD_COMP &&
                it.orderType != OrderType.ACCESSORY_ONLY &&
                it.items.any { line -> line.lineType == "GAS" }
        }
        .maxOfOrNull { it.completedAt ?: it.createdAt }
        ?: return Pair(false, "No completed order found.")

    val cust = customers.find { it.id == customerId }
        ?: return Pair(false, "Customer not found.")
    cust.reminderDismissedForCompletedAt = lastCompletedAt
    notifyChanged()
    return Pair(true, "Reminder dismissed.")
}


