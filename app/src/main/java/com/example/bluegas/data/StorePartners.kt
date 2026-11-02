package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Partner Onboarding Operations (Operations Manager — RACI-29, §8.11, UAT-26)
 */

fun BlueGasStore.approvePartner(
    appId: String,
    zoneId: String? = null,
    homePointId: String? = null,
    trainingDone: Boolean = true,
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
        return Pair(false, "Distributor onboarding is planned for a later phase.")
    } else {
        // RIDER
        val targetPointId = homePointId ?: app.homePointId
        if (targetPointId.isNullOrBlank()) {
            return Pair(false, "Home hub selection is required for rider onboarding.")
        }
        if (!trainingDone) {
            return Pair(false, "Record installation training first (Q27)")
        }

        val hub = nodes.find { it.id == targetPointId && it.type == FulfilmentPointType.HUB }
            ?: return Pair(false, "Serving hub $targetPointId not found or is not a hub.")

        val riderCount = riders.size + 1
        val newRiderId = String.format("RIDER-%03d", riderCount)
        val zone = hub.servesZoneIds.firstOrNull() ?: zoneId ?: "ZONE-LHR-1"
        val zoneName = serviceZones.find { it.id == zone }?.name ?: "Pilot Service Zone"

        val newRider = Rider(
            id = newRiderId,
            name = app.applicantName,
            phone = app.phone,
            cnic = app.cnic,
            zoneId = zone,
            zoneName = zoneName,
            nodeId = hub.id,
            pointId = hub.id,
            vehicleNumber = app.vehicleOrShopDetails,
            status = "ACTIVE",
            onboardingStatus = "APPROVED",
            installationTrained = true
        )
        riders.add(newRider)

        app.status = "APPROVED"
        app.decidedBy = actor
        app.decidedAt = System.currentTimeMillis()

        addNotification(
            surface = AppSurface.STAFF,
            title = "Rider Partner Approved",
            message = "Rider ${app.applicantName} approved for ${hub.name}.",
            canonicalStatus = "APPROVED",
            orderId = null,
            code = "NTF-14",
            staffRole = StaffSubRole.OPERATIONS
        )
        logAudit("PartnerApplication", appId, "APPROVE_RIDER", actor, AccountableRole.OPERATIONS, "Approved rider $newRiderId for hub ${hub.id}")
        notifyChanged()
        return Pair(true, "Rider ${app.applicantName} ($newRiderId) approved and active at ${hub.name}.")
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
