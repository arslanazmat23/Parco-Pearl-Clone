package com.example.bluegas.ui.staff

import com.example.bluegas.data.SharedAppState

/**
 * Returns the customer display name for the given customer ID, or a safe fallback if unavailable.
 *
 * @param id Customer identifier.
 */
fun SharedAppState.customerName(id: String?): String {
    if (id.isNullOrBlank()) return ""
    return customers.find { it.id == id }?.name ?: id
}

/**
 * Returns the rider display name for the given rider ID, or a safe fallback if unavailable.
 *
 * @param id Rider identifier.
 */
fun SharedAppState.riderName(id: String?): String {
    if (id.isNullOrBlank()) return ""
    return riders.find { it.id == id }?.name ?: id
}

/**
 * Returns the fulfilment point display name (hub or plant) for the given point ID.
 *
 * @param id Fulfilment point identifier.
 */
fun SharedAppState.pointName(id: String?): String {
    if (id.isNullOrBlank()) return ""
    return nodes.find { it.id == id }?.name ?: id
}

/**
 * Returns a human-readable single-line street, area, and city address for the given address ID.
 *
 * @param id Address identifier.
 */
fun SharedAppState.addressLine(id: String?): String {
    if (id.isNullOrBlank()) return ""
    val addr = addresses.find { it.id == id } ?: return ""
    return listOf(addr.street, addr.area, addr.city)
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

/**
 * Returns a concise area and city label for dispatch cards from the given address ID.
 *
 * @param id Address identifier.
 */
fun SharedAppState.addressArea(id: String?): String {
    if (id.isNullOrBlank()) return ""
    val addr = addresses.find { it.id == id } ?: return ""
    return listOf(addr.area, addr.city)
        .filter { it.isNotBlank() }
        .joinToString(", ")
}

/**
 * Formats an order identifier for staff lists as "Order #<last 4 chars> · <customer name>".
 *
 * @param orderId Full order identifier.
 */
fun SharedAppState.orderLabel(orderId: String): String {
    val shortId = orderId.takeLast(4)
    val order = orders.find { it.id == orderId }
    val name = customerName(order?.customerId)
    return if (name.isNotBlank()) "Order #$shortId · $name" else "Order #$shortId"
}

/**
 * Formats a product SKU into its human-readable cylinder weight or product name.
 *
 * @param sku Product SKU identifier.
 */
fun SharedAppState.skuLabel(sku: String): String = when (sku) {
    "SKU-LPG-11-8" -> "11.8 kg"
    "SKU-LPG-45-4" -> "45.4 kg"
    else -> productVariants.find { it.sku == sku }?.let { pv ->
        pv.cylinderSizeKg?.let { "$it kg" } ?: pv.name
    } ?: sku
}
