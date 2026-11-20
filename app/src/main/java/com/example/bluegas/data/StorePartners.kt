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
            installationTrained = trainingDone
        )
        riders.add(newRider)

        app.status = "APPROVED"
        app.decidedBy = actor
        app.decidedAt = clock()

        val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
        addNotification(
            surface = AppSurface.STAFF,
            title = "Rider Partner Approved",
            message = "Rider ${app.applicantName} approved for ${hub.name}." +
                if (!trainingDone) " Installation checklist training pending." else "",
            canonicalStatus = "APPROVED",
            orderId = null,
            code = "NTF-14",
            staffRole = StaffSubRole.OPERATIONS
        )
        addNotification(
            surface = AppSurface.RIDER,
            title = "Rider Application Approved",
            message = if (trainingDone) {
                "Your application ($appId) is approved at ${hub.name}. You are ready for jobs."
            } else {
                "Your application ($appId) is approved at ${hub.name}. Checklist training is pending before dispatch."
            },
            canonicalStatus = "APPROVED",
            orderId = null,
            code = "NTF-14",
            channels = ntfChannels
        )
        logAudit(
            entityType = "PartnerApplication",
            entityId = appId,
            action = "APPROVE_RIDER",
            actor = actor,
            role = AccountableRole.OPERATIONS,
            details = "Approved rider $newRiderId for hub ${hub.id} (trainingDone=$trainingDone)"
        )
        notifyChanged()
        return if (trainingDone) {
            Pair(true, "Rider ${app.applicantName} ($newRiderId) approved and active at ${hub.name}.")
        } else {
            Pair(true, "Rider ${app.applicantName} ($newRiderId) approved at ${hub.name}. Checklist training pending before dispatch.")
        }
    }
}

/**
 * Certifies that an approved rider has completed safety & installation checklist training (Q27),
 * allowing them to receive delivery offers and return collection assignments.
 *
 * @param riderId Target rider identifier.
 * @param note Required completion / assessment note.
 * @param actor Staff actor recording training completion.
 * @return Pair of success flag and outcome message.
 */
fun BlueGasStore.markRiderTrainingDone(
    riderId: String,
    note: String,
    actor: String = AccountableRole.OPERATIONS.displayName
): Pair<Boolean, String> {
    val idx = riders.indexOfFirst { it.id == riderId }
    if (idx < 0) return Pair(false, "Rider $riderId not found.")
    val cleanNote = note.trim()
    if (cleanNote.isEmpty()) {
        return Pair(false, "Please enter a training verification note.")
    }
    val rider = riders[idx]
    if (rider.installationTrained) {
        return Pair(false, "${rider.name} is already marked checklist trained.")
    }

    riders[idx] = rider.copy(installationTrained = true)
    val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
    addNotification(
        surface = AppSurface.RIDER,
        title = "Checklist Training Complete",
        message = "Safety and installation checklist training recorded for ${rider.name}. You are ready for jobs.",
        canonicalStatus = "TRAINED",
        orderId = null,
        code = "NTF-14",
        channels = ntfChannels
    )
    logAudit(
        entityType = "Rider",
        entityId = riderId,
        action = "MARK_RIDER_TRAINING_DONE",
        actor = actor,
        role = AccountableRole.OPERATIONS,
        details = cleanNote
    )
    notifyChanged()
    return Pair(true, "Checklist training recorded for ${rider.name}.")
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
    app.decidedAt = clock()

    val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
    addNotification(
        surface = AppSurface.STAFF,
        title = "Partner Application Incomplete (EXC-24)",
        message = "Application $appId returned: ${app.decisionReason}",
        canonicalStatus = "RETURNED_INCOMPLETE",
        orderId = null,
        code = "NTF-14",
        staffRole = StaffSubRole.OPERATIONS
    )
    addNotification(
        surface = AppSurface.RIDER,
        title = "Documents Needed ($appId)",
        message = "Your rider application ($appId) was returned: ${app.decisionReason}",
        canonicalStatus = "RETURNED_INCOMPLETE",
        orderId = null,
        code = "NTF-14",
        channels = ntfChannels
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

    val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
    addNotification(
        surface = AppSurface.STAFF,
        title = "Partner Re-submission Received",
        message = "Applicant ${app.applicantName} ($appId) has uploaded missing documents.",
        canonicalStatus = "PENDING_APPROVAL",
        orderId = null,
        code = "NTF-14",
        staffRole = StaffSubRole.OPERATIONS
    )
    addNotification(
        surface = AppSurface.RIDER,
        title = "Application Re-submitted ($appId)",
        message = "Updated documents for $appId are now under review.",
        canonicalStatus = "PENDING_APPROVAL",
        orderId = null,
        code = "NTF-14",
        channels = ntfChannels
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
    app.decidedAt = clock()

    val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
    addNotification(
        surface = AppSurface.RIDER,
        title = "Application Not Approved ($appId)",
        message = "Your rider application ($appId) was not approved: $reason",
        canonicalStatus = "REJECTED",
        orderId = null,
        code = "NTF-14",
        channels = ntfChannels
    )
    logAudit("PartnerApplication", appId, "REJECT_PARTNER", actor, AccountableRole.OPERATIONS, reason)
    notifyChanged()
    return Pair(true, "Application for ${app.applicantName} rejected.")
}
