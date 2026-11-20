package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Read-only 30-day operational summary for a rider (no ranking or leaderboard).
 */
data class RiderPerformanceSummary(
    val riderId: String,
    val days: Int,
    val completedDeliveries: Int,
    val onTimeHandovers: Int,
    val onTimePercent: Int?,
    val checklistCompletedCount: Int,
    val checklistPercent: Int?,
    val cashDifferenceCount: Int,
    val cashDifferenceSumPaisa: Long,
    val complaintCount: Int
)

private val CNIC_REGEX = Regex("^\\d{5}-\\d{7}-\\d$")

/**
 * Submits a rider onboarding application from the Rider surface, creating a [PartnerApplication]
 * linked to the chosen city's hub and recording [BlueGasStore.lastRiderApplicationId].
 * Sends NTF-14 with SMS channel when [PlatformSettings.smsEnabled] is active.
 *
 * @param name Applicant full name.
 * @param phone Applicant mobile phone number.
 * @param cnic 13-digit CNIC formatted as #####-#######-#.
 * @param city Operating city ("Lahore" or "Islamabad").
 * @param vehicleNumber Motorcycle / delivery vehicle registration and model details.
 * @param cnicCopy Whether a copy of the CNIC is attached.
 * @param drivingLicence Whether a valid driving licence copy is attached.
 * @param vehicleRegistration Whether the vehicle registration book is attached.
 * @return Pair of success flag and user-facing submission message.
 */
fun BlueGasStore.submitRiderApplication(
    name: String,
    phone: String,
    cnic: String,
    city: String,
    vehicleNumber: String,
    cnicCopy: Boolean,
    drivingLicence: Boolean,
    vehicleRegistration: Boolean
): Pair<Boolean, String> {
    val cleanName = name.trim()
    val cleanPhone = phone.trim()
    val cleanCnic = cnic.trim()
    val cleanVehicle = vehicleNumber.trim()

    if (cleanName.isEmpty()) return Pair(false, "Please enter your full name.")
    if (cleanPhone.isEmpty()) return Pair(false, "Please enter your phone number.")
    if (!CNIC_REGEX.matches(cleanCnic)) {
        return Pair(false, "Enter CNIC in #####-#######-# format.")
    }
    if (cleanVehicle.isEmpty()) return Pair(false, "Please enter your vehicle number.")

    val hub = nodes.firstOrNull {
        it.type == FulfilmentPointType.HUB && it.city.equals(city.trim(), ignoreCase = true)
    } ?: return Pair(false, "Please choose Lahore or Islamabad.")

    val appId = "PART-RDR-" + (1000..9999).random()
    val app = PartnerApplication(
        id = appId,
        type = "RIDER",
        applicantName = cleanName,
        city = hub.city,
        cnic = cleanCnic,
        vehicleOrShopDetails = cleanVehicle,
        status = "PENDING_APPROVAL",
        phone = cleanPhone,
        appliedAt = clock(),
        documents = listOf(
            PartnerDocument("CNIC Copy", cnicCopy),
            PartnerDocument("Driving Licence", drivingLicence),
            PartnerDocument("Vehicle Registration", vehicleRegistration)
        ),
        zoneId = hub.servesZoneIds.firstOrNull(),
        homePointId = hub.id
    )

    partnerApplications.add(0, app)
    lastRiderApplicationId = appId

    val ntfChannels = if (settings.smsEnabled) listOf(NotifyChannel.PUSH, NotifyChannel.SMS) else listOf(NotifyChannel.PUSH)
    addNotification(
        surface = AppSurface.STAFF,
        title = "New Rider Application ($appId)",
        message = "$cleanName applied as a rider for ${hub.name} (${hub.city}).",
        canonicalStatus = "PENDING_APPROVAL",
        orderId = null,
        code = "NTF-14",
        staffRole = StaffSubRole.OPERATIONS
    )
    addNotification(
        surface = AppSurface.RIDER,
        title = "Rider Application Received ($appId)",
        message = "Your application ($appId) for ${hub.name} is under review.",
        canonicalStatus = "PENDING_APPROVAL",
        orderId = null,
        code = "NTF-14",
        channels = ntfChannels
    )
    logAudit(
        entityType = "PartnerApplication",
        entityId = appId,
        action = "SUBMIT_RIDER_APPLICATION",
        actor = cleanName,
        role = AccountableRole.OPERATIONS,
        details = "Submitted rider application for ${hub.name} (${hub.city})"
    )
    notifyChanged()
    return Pair(true, "Application $appId submitted — now under review.")
}

/**
 * Pure read-only calculation of a rider's last-[days] operational performance without ranking.
 *
 * @param riderId Target rider identifier.
 * @param days Rolling lookback window in days (default 30).
 * @return [RiderPerformanceSummary] covering on-time handovers, checklist completion, cash differences, and complaints.
 */
fun BlueGasStore.riderPerformance(
    riderId: String,
    days: Int = 30
): RiderPerformanceSummary {
    val cutoff = clock() - days.toLong() * 86_400_000L
    val completedDeliveries = orders.filter { ord ->
        ord.assignedRiderId == riderId &&
            ord.status == OrderStatus.ORD_COMP &&
            ord.fulfilmentMode == FulfilmentMode.DELIVERY &&
            (ord.completedAt ?: ord.createdAt) >= cutoff
    }

    val deliveryByOrderId = deliveries.associateBy { it.orderId }

    val onTimeHandovers = completedDeliveries.count { ord ->
        val handoverAt = deliveryByOrderId[ord.id]?.completedAt ?: ord.completedAt
        val promised = ord.promisedByAt
        promised == null || (handoverAt != null && handoverAt <= promised)
    }

    val checklistCompletedCount = completedDeliveries.count { ord ->
        val del = deliveryByOrderId[ord.id]
        ord.installationResult == InstallationResult.INSTALLED ||
            del?.nonTechnicalInstallationPassed == true
    }

    val total = completedDeliveries.size
    val onTimePercent = if (total > 0) (onTimeHandovers * 100) / total else null
    val checklistPercent = if (total > 0) (checklistCompletedCount * 100) / total else null

    val cashDiffOrders = orders.filter { ord ->
        ord.assignedRiderId == riderId &&
            (ord.completedAt ?: ord.createdAt) >= cutoff &&
            (ord.cashDifferencePaisa ?: 0L) != 0L
    }

    val riderOrderIds = orders.filter { it.assignedRiderId == riderId }.map { it.id }.toSet()
    val complaintCount = supportCases.count { sc ->
        sc.raisedAt >= cutoff && sc.orderId != null && sc.orderId in riderOrderIds
    }

    return RiderPerformanceSummary(
        riderId = riderId,
        days = days,
        completedDeliveries = total,
        onTimeHandovers = onTimeHandovers,
        onTimePercent = onTimePercent,
        checklistCompletedCount = checklistCompletedCount,
        checklistPercent = checklistPercent,
        cashDifferenceCount = cashDiffOrders.size,
        cashDifferenceSumPaisa = cashDiffOrders.sumOf { it.cashDifferencePaisa ?: 0L },
        complaintCount = complaintCount
    )
}
