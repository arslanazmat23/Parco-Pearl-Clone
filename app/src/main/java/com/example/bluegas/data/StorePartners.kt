package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Partner Onboarding Operations (Operations Manager — RACI-29, §8.11, UAT-26)
 */

fun BlueGasStore.approvePartner(
    appId: String,
    zoneId: String?,
    linkedDistributorId: String?,
    trainingDone: Boolean,
    actor: String = "Operations Lead"
): Pair<Boolean, String> {
    val app = partnerApplications.find { it.id == appId }
        ?: return Pair(false, "Application $appId not found.")

    if (app.status != "PENDING_APPROVAL") {
        return Pair(false, "Application is not in PENDING_APPROVAL status (currently ${app.status}).")
    }

    val missingDocs = app.documents.filter { !it.isProvided }
    if (missingDocs.isNotEmpty()) {
        return Pair(false, "Documents missing — use Return as incomplete")
    }

    if (app.type == "DISTRIBUTOR") {
        if (zoneId.isNullOrBlank()) {
            return Pair(false, "Service zone selection is required for distributor onboarding.")
        }
        val distCount = distributors.size + 1
        val newDistId = String.format("DIST-%03d", distCount)
        val newNodeId = "NODE-" + app.city.take(3).uppercase() + "-$distCount"

        val newDist = Distributor(
            id = newDistId,
            name = app.applicantName,
            city = app.city,
            address = app.vehicleOrShopDetails,
            phone = app.phone,
            status = "ACTIVE",
            zoneIds = listOf(zoneId),
            pointId = newNodeId,
            approvedAt = System.currentTimeMillis()
        )
        distributors.add(newDist)

        val newNode = Node(
            id = newNodeId,
            distributorId = newDistId,
            name = "${app.applicantName} Terminal",
            zoneId = zoneId,
            capacity = 150,
            type = FulfilmentPointType.DISTRIBUTOR,
            address = app.vehicleOrShopDetails,
            pickupEnabled = true,
            servesZoneIds = listOf(zoneId),
            city = app.city
        )
        nodes.add(newNode)

        val zone = serviceZones.find { it.id == zoneId }
        if (zone != null && !zone.servingPointIds.contains(newNodeId)) {
            val zIdx = serviceZones.indexOf(zone)
            serviceZones[zIdx] = zone.copy(servingPointIds = zone.servingPointIds + newNodeId)
        }

        // Initialize zero stock balances for household approved cylinders
        productVariants.filter { it.isCylinder && it.isHouseholdApproved }.forEach { pv ->
            stockBalances.add(
                StockBalance(
                    pointId = newNodeId,
                    skuId = pv.sku,
                    filledQty = 0,
                    emptyQty = 0
                )
            )
        }

        app.status = "APPROVED"
        app.decidedBy = actor
        app.decidedAt = System.currentTimeMillis()

        addNotification(
            surface = AppSurface.STAFF,
            title = "Distributor Partner Approved",
            message = "Distributor ${app.applicantName} approved and active at $newNodeId.",
            canonicalStatus = "APPROVED",
            orderId = null,
            code = "NTF-14",
            staffRole = StaffSubRole.OPERATIONS
        )
        logAudit("PartnerApplication", appId, "APPROVE_DISTRIBUTOR", actor, AccountableRole.OPERATIONS, "Approved distributor $newDistId ($newNodeId)")
        notifyChanged()
        return Pair(true, "Distributor ${app.applicantName} ($newDistId) approved and active.")
    } else {
        // RIDER
        if (linkedDistributorId.isNullOrBlank()) {
            return Pair(false, "Linked distributor is required for rider onboarding.")
        }
        if (!trainingDone) {
            return Pair(false, "Record installation training first (Q27)")
        }

        val dist = distributors.find { it.id == linkedDistributorId }
            ?: return Pair(false, "Linked distributor $linkedDistributorId not found.")

        val riderCount = riders.size + 1
        val newRiderId = String.format("RIDER-%03d", riderCount)
        val zone = dist.zoneIds.firstOrNull() ?: "ZONE-LHR-1"
        val zoneName = serviceZones.find { it.id == zone }?.name ?: "Pilot Service Zone"

        val newRider = Rider(
            id = newRiderId,
            name = app.applicantName,
            phone = app.phone,
            cnic = app.cnic,
            zoneId = zone,
            zoneName = zoneName,
            nodeId = dist.pointId,
            vehicleNumber = app.vehicleOrShopDetails,
            status = "ACTIVE",
            onboardingStatus = "APPROVED",
            distributorId = linkedDistributorId,
            installationTrained = true
        )
        riders.add(newRider)

        app.status = "APPROVED"
        app.decidedBy = actor
        app.decidedAt = System.currentTimeMillis()

        addNotification(
            surface = AppSurface.STAFF,
            title = "Rider Partner Approved",
            message = "Rider ${app.applicantName} approved for $linkedDistributorId.",
            canonicalStatus = "APPROVED",
            orderId = null,
            code = "NTF-14",
            staffRole = StaffSubRole.OPERATIONS
        )
        logAudit("PartnerApplication", appId, "APPROVE_RIDER", actor, AccountableRole.OPERATIONS, "Approved rider $newRiderId for distributor $linkedDistributorId")
        notifyChanged()
        return Pair(true, "Rider ${app.applicantName} ($newRiderId) approved and active.")
    }
}

fun BlueGasStore.returnIncomplete(
    appId: String,
    missingDocs: List<String>,
    actor: String = "Operations Lead"
): Pair<Boolean, String> {
    if (missingDocs.isEmpty()) {
        return Pair(false, "At least one missing document must be specified.")
    }
    val app = partnerApplications.find { it.id == appId }
        ?: return Pair(false, "Application $appId not found.")

    app.status = "RETURNED_INCOMPLETE"
    app.decisionReason = "Missing: " + missingDocs.joinToString(", ")
    app.decidedBy = actor
    app.decidedAt = System.currentTimeMillis()

    addNotification(
        surface = AppSurface.STAFF,
        title = "Partner Application Incomplete (EXC-24)",
        message = "Application $appId returned: ${app.decisionReason}",
        canonicalStatus = "RETURNED_INCOMPLETE",
        orderId = null,
        code = "NTF-14",
        staffRole = StaffSubRole.OPERATIONS
    )
    logAudit("PartnerApplication", appId, "RETURN_INCOMPLETE", actor, AccountableRole.OPERATIONS, app.decisionReason ?: "")
    notifyChanged()
    return Pair(true, "Application $appId returned as incomplete.")
}

fun BlueGasStore.simulateApplicantResubmission(appId: String): Pair<Boolean, String> {
    val app = partnerApplications.find { it.id == appId }
        ?: return Pair(false, "Application $appId not found.")

    val idx = partnerApplications.indexOf(app)
    val completeDocs = app.documents.map { it.copy(isProvided = true) }
    partnerApplications[idx] = app.copy(
        status = "PENDING_APPROVAL",
        decisionReason = null,
        documents = completeDocs
    )

    addNotification(
        surface = AppSurface.STAFF,
        title = "Partner Re-submission Received",
        message = "Applicant ${app.applicantName} ($appId) has uploaded missing documents.",
        canonicalStatus = "PENDING_APPROVAL",
        orderId = null,
        code = "NTF-14",
        staffRole = StaffSubRole.OPERATIONS
    )
    logAudit("PartnerApplication", appId, "RESUBMIT_DOCUMENTS", app.applicantName, AccountableRole.OPERATIONS, "Uploaded all requested documents")
    notifyChanged()
    return Pair(true, "Applicant ${app.applicantName} re-submitted application with full documents.")
}

fun BlueGasStore.returnPartnerApplication(
    appId: String,
    missingDetails: String,
    actor: String = "Operations Lead"
): Pair<Boolean, String> {
    val list = if (missingDetails.isBlank()) listOf("Required documents missing") else listOf(missingDetails)
    return returnIncomplete(appId, list, actor)
}

fun BlueGasStore.rejectPartner(
    appId: String,
    reason: String,
    actor: String = "Operations Lead"
): Pair<Boolean, String> {
    val app = partnerApplications.find { it.id == appId } ?: return Pair(false, "Application not found")
    app.status = "REJECTED"
    app.decisionReason = reason
    app.decidedBy = actor
    app.decidedAt = System.currentTimeMillis()
    logAudit("PartnerApplication", appId, "REJECT_PARTNER", actor, AccountableRole.OPERATIONS, reason)
    notifyChanged()
    return Pair(true, "Application for ${app.applicantName} rejected.")
}
