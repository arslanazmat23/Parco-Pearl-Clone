package com.example.bluegas.data

import com.example.bluegas.model.*
import java.util.UUID

/**
 * Commercial & Pricing Operations (Commercial Manager — RACI-26)
 */

fun BlueGasStore.startPriceDraft(): Pair<Boolean, String> {
    val current = currentPriceList
    draftPriceList = current.copy(
        version = current.version + 1,
        effectiveFrom = System.currentTimeMillis()
    )
    notifyChanged()
    return Pair(true, "Price list draft v${draftPriceList?.version} started.")
}

fun BlueGasStore.updateDraftPriceList(draft: PriceList): Pair<Boolean, String> {
    if (draft.reference118Paisa <= 0) return Pair(false, "Reference price must be positive.")
    if (draft.perSizePaisa.values.any { it <= 0 }) return Pair(false, "All size prices must be positive.")
    if (draft.depositPaisa.values.any { it <= 0 }) return Pair(false, "All deposit amounts must be positive.")

    draftPriceList = draft
    notifyChanged()
    return Pair(true, "Draft price list updated.")
}

fun BlueGasStore.discardPriceDraft(): Pair<Boolean, String> {
    draftPriceList = null
    notifyChanged()
    return Pair(true, "Price list draft discarded.")
}

fun BlueGasStore.publishPriceList(
    reviewerName: String,
    ograRef: String
): Pair<Boolean, String> {
    if (reviewerName.isBlank() || ograRef.isBlank()) {
        return Pair(false, "Reviewer name and OGRA notification reference are both required.")
    }

    val draft = draftPriceList ?: return Pair(false, "No active draft price list to publish.")
    val maxVersion = priceLists.maxOfOrNull { it.version } ?: 1
    val newVersion = maxVersion + 1

    val published = draft.copy(
        version = newVersion,
        effectiveFrom = System.currentTimeMillis(),
        publishedBy = reviewerName,
        ograNotificationRef = ograRef,
        reviewedAt = System.currentTimeMillis()
    )
    priceLists.add(published)
    draftPriceList = null

    // Mark all OGRA notices reviewed
    ograNotices.forEach {
        it.reviewed = true
        it.reviewedAt = System.currentTimeMillis()
    }

    addNotification(
        surface = AppSurface.STAFF,
        title = "Price List v$newVersion Published",
        message = "New retail price schedule effective immediately. Reference: $ograRef",
        canonicalStatus = "PUBLISHED",
        orderId = null,
        code = "NTF-16",
        staffRole = StaffSubRole.OPERATIONS
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "New Retail Prices Active",
        message = "Price List v$newVersion published by $reviewerName.",
        canonicalStatus = "PUBLISHED",
        orderId = null,
        code = "NTF-16",
        staffRole = StaffSubRole.HUB
    )

    logAudit("PriceList", "v$newVersion", "PUBLISH_PRICE_LIST", reviewerName, AccountableRole.COMMERCIAL, "Published v$newVersion with OGRA ref: $ograRef")
    notifyChanged()
    return Pair(true, "Price list v$newVersion published. New orders use it; existing orders keep their version.")
}

fun BlueGasStore.recordOgraNotice(reference: String): Pair<Boolean, String> {
    if (reference.isBlank()) return Pair(false, "OGRA reference string is required.")

    val id = "OGRA-" + UUID.randomUUID().toString().take(6)
    val notice = OgraNotice(
        id = id,
        reference = reference,
        receivedAt = System.currentTimeMillis(),
        reviewed = false
    )
    ograNotices.add(0, notice)

    addNotification(
        surface = AppSurface.STAFF,
        title = "Price list out of date — review needed (EXC-19)",
        message = "New OGRA pricing gazette received: $reference. Commercial review required.",
        canonicalStatus = "REVIEW_REQUIRED",
        orderId = null,
        code = "NTF-16",
        staffRole = StaffSubRole.COMMERCIAL
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "OGRA Pricing Revision Pending",
        message = "Price schedule pending Commercial review ($reference).",
        canonicalStatus = "EXC-19",
        orderId = null,
        code = "NTF-16",
        staffRole = StaffSubRole.OPERATIONS
    )

    logAudit("OgraNotice", id, "RECEIVE_OGRA_NOTICE", "Commercial Manager", AccountableRole.COMMERCIAL, "Logged OGRA gazette ref $reference (EXC-19 active)")
    notifyChanged()
    return Pair(true, "OGRA notice recorded. Commercial review required (EXC-19).")
}

