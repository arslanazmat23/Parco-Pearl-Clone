package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * System Administration & Governance Store Operations (System Administrator — RACI-22; Legal RACI-21)
 */

fun BlueGasStore.updateSettings(
    new: PlatformSettings,
    actor: String = "System Administrator"
): Pair<Boolean, String> {
    if (new.holdTtlMinutes !in 1..120) {
        return Pair(false, "Hold TTL must be between 1 and 120 minutes.")
    }
    if (new.distributorAcceptanceWindowMins !in 1..240) {
        return Pair(false, "Acceptance window must be between 1 and 240 minutes.")
    }
    if (new.promisedHandoverMinutes !in 15..1440) {
        return Pair(false, "Promised handover SLA must be between 15 and 1440 minutes (24h).")
    }
    if (new.cashHandoverHours !in 1..168) {
        return Pair(false, "Cash handover window must be between 1 and 168 hours (7 days).")
    }
    if (new.evidenceRetentionDays !in 30..3650) {
        return Pair(false, "Evidence retention must be between 30 and 3650 days (10 years).")
    }
    if (new.maxQuantityPerOrder !in 1..10) {
        return Pair(false, "Max quantity per household order must be between 1 and 10.")
    }
    if (new.enabledPaymentMethods.isEmpty()) {
        return Pair(false, "At least one payment method must remain enabled.")
    }
    if (new.installationChecklistItems.isEmpty()) {
        return Pair(false, "Installation checklist must contain at least one safety verification item.")
    }

    val old = settings
    val changedFields = mutableListOf<String>()
    if (old.holdTtlMinutes != new.holdTtlMinutes) changedFields.add("holdTtl: ${old.holdTtlMinutes} -> ${new.holdTtlMinutes}m")
    if (old.distributorAcceptanceWindowMins != new.distributorAcceptanceWindowMins) changedFields.add("acceptanceWindow: ${old.distributorAcceptanceWindowMins} -> ${new.distributorAcceptanceWindowMins}m")
    if (old.promisedHandoverMinutes != new.promisedHandoverMinutes) changedFields.add("promisedHandover: ${old.promisedHandoverMinutes} -> ${new.promisedHandoverMinutes}m")
    if (old.cashHandoverHours != new.cashHandoverHours) changedFields.add("cashHandoverHours: ${old.cashHandoverHours} -> ${new.cashHandoverHours}h")
    if (old.evidenceRetentionDays != new.evidenceRetentionDays) changedFields.add("retentionDays: ${old.evidenceRetentionDays} -> ${new.evidenceRetentionDays}d")
    if (old.newCylinderModel != new.newCylinderModel) changedFields.add("newCylinderModel: ${old.newCylinderModel} -> ${new.newCylinderModel}")
    if (old.refillModel != new.refillModel) changedFields.add("refillModel: ${old.refillModel} -> ${new.refillModel}")
    if (old.cylinderIdMode != new.cylinderIdMode) changedFields.add("cylinderIdMode: ${old.cylinderIdMode} -> ${new.cylinderIdMode}")
    if (old.phase2PreviewEnabled != new.phase2PreviewEnabled) changedFields.add("phase2Preview: ${old.phase2PreviewEnabled} -> ${new.phase2PreviewEnabled}")
    if (old.installationChecklistItems != new.installationChecklistItems) changedFields.add("checklistItems: ${new.installationChecklistItems.size} items")

    settings = new

    val details = if (changedFields.isEmpty()) "Settings re-saved with no changes" else changedFields.joinToString("; ")
    logAudit("PlatformSettings", "GLOBAL", "UPDATE_SETTINGS", actor, AccountableRole.ADMIN, details)
    notifyChanged()
    return Pair(true, "Settings saved successfully.")
}

fun BlueGasStore.setZoneServiceable(zoneId: String, serviceable: Boolean): Pair<Boolean, String> {
    val zone = serviceZones.find { it.id == zoneId }
        ?: return Pair(false, "Zone $zoneId not found.")

    val idx = serviceZones.indexOf(zone)
    serviceZones[idx] = zone.copy(isServiceable = serviceable)

    logAudit("ServiceZone", zoneId, "SET_SERVICEABLE", "System Administrator", AccountableRole.ADMIN, "Serviceable set to $serviceable")
    notifyChanged()
    return Pair(true, "Zone ${zone.name} serviceable set to $serviceable.")
}

fun BlueGasStore.markDecisionDecided(
    id: String,
    note: String,
    owner: String
): Pair<Boolean, String> {
    val decision = openDecisions.find { it.id == id }
        ?: return Pair(false, "Decision $id not found.")

    decision.status = "DECIDED"
    decision.note = note
    decision.owner = owner

    logAudit("OpenDecision", id, "MARK_DECIDED", owner, AccountableRole.ADMIN, note)
    notifyChanged()
    return Pair(true, "Recorded. Update the related setting to apply the decision.")
}

fun BlueGasStore.reopenDecision(id: String): Pair<Boolean, String> {
    val decision = openDecisions.find { it.id == id }
        ?: return Pair(false, "Decision $id not found.")

    decision.status = "OPEN"
    logAudit("OpenDecision", id, "REOPEN_DECISION", "System Administrator", AccountableRole.ADMIN, "Decision reopened for technical review")
    notifyChanged()
    return Pair(true, "Decision $id reopened.")
}

fun BlueGasStore.updateDecisionPlan(
    id: String,
    owner: String,
    targetDate: String
): Pair<Boolean, String> {
    val decision = openDecisions.find { it.id == id }
        ?: return Pair(false, "Decision $id not found.")

    decision.owner = owner
    decision.targetDate = targetDate
    logAudit("OpenDecision", id, "UPDATE_PLAN", "System Administrator", AccountableRole.ADMIN, "Owner: $owner, Target: $targetDate")
    notifyChanged()
    return Pair(true, "Plan updated for ${decision.id}: $owner, $targetDate.")
}
