package com.example.bluegas.data

import com.example.bluegas.lifecycle.CaseLifecycles
import com.example.bluegas.model.*
import java.util.UUID

/**
 * Safety & HSE Incident Operations (HSE Officer — RACI-09)
 */

fun BlueGasStore.raiseIncident(
    source: String,
    description: String,
    orderId: String? = null,
    cylinderId: String? = null,
    location: String = "On site",
    peopleInvolved: String = "Customer & staff",
    evidenceCaptured: Boolean = false,
    actor: String = "HSE Officer"
): Pair<Boolean, String> {
    if (description.isBlank()) {
        return Pair(false, "Incident description is required.")
    }

    val id = "INC-" + (1000..9999).random()

    // Place linked cylinder on HSE hold
    val targetCyl = cylinderId ?: orderId?.let { orders.find { o -> o.id == it }?.allocatedSerial }
    if (!targetCyl.isNullOrBlank()) {
        val cyl = cylinders.find { it.serialNumber == targetCyl }
        if (cyl != null) {
            val idx = cylinders.indexOf(cyl)
            cylinders[idx] = cyl.copy(hseHold = true)
            cylinderCustodyEvents.add(
                CylinderCustodyEvent(
                    id = "EVT-" + UUID.randomUUID().toString().take(6),
                    serialNumber = targetCyl,
                    fromCustody = cyl.custody,
                    toCustody = cyl.custody,
                    orderId = orderId,
                    actor = actor
                )
            )
        }
    }

    val incident = SafetyIncident(
        id = id,
        source = source,
        orderId = orderId,
        cylinderId = targetCyl,
        description = description,
        location = location,
        peopleInvolved = peopleInvolved,
        evidenceCaptured = evidenceCaptured,
        status = L7Status.INCIDENT_RAISED
    )
    incident.history.add(
        IncidentHistory(
            fromStatus = L7Status.INCIDENT_RAISED,
            toStatus = L7Status.INCIDENT_RAISED,
            actor = actor,
            role = AccountableRole.HSE,
            notes = "Incident raised: $description"
        )
    )
    safetyIncidents.add(0, incident)

    addNotification(
        surface = AppSurface.STAFF,
        title = "Safety Incident Raised ($id)",
        message = "Safety alert from $source: $description. Cylinder $targetCyl on HSE hold.",
        canonicalStatus = "INCIDENT_RAISED",
        orderId = orderId,
        code = "NTF-09",
        staffRole = StaffSubRole.SAFETY_HSE
    )
    addNotification(
        surface = AppSurface.STAFF,
        title = "Safety Alert Logged",
        message = "Incident $id logged for order ${orderId ?: "N/A"}.",
        canonicalStatus = "INCIDENT_RAISED",
        orderId = orderId,
        code = "NTF-09",
        staffRole = StaffSubRole.OPERATIONS
    )

    logAudit("SafetyIncident", id, "RAISE_INCIDENT", actor, AccountableRole.HSE, description)
    notifyChanged()
    return Pair(true, "Incident $id raised. Cylinder placed on HSE hold.")
}

fun BlueGasStore.moveIncident(
    incidentId: String,
    target: L7Status,
    notes: String,
    authorizedBy: String? = null,
    cylinderOutcome: String? = null, // "RELEASE" or "WRITE_OFF"
    actor: String = "HSE Officer"
): Pair<Boolean, String> {
    val inc = safetyIncidents.find { it.id == incidentId }
        ?: return Pair(false, "Incident $incidentId not found.")

    if (notes.isBlank()) {
        return Pair(false, "Notes/procedure reference must be provided.")
    }

    if (target == L7Status.AUTHORIZED_ACTION_TAKEN && authorizedBy.isNullOrBlank()) {
        return Pair(false, "Authorizer name is required for authorized action.")
    }

    if (target == L7Status.INCIDENT_CLOSED && !inc.cylinderId.isNullOrBlank()) {
        if (cylinderOutcome.isNullOrBlank()) {
            return Pair(false, "Cylinder outcome ('RELEASE' or 'WRITE_OFF') is required to close this incident.")
        }
        val cyl = cylinders.find { it.serialNumber == inc.cylinderId }
        if (cyl != null) {
            val idx = cylinders.indexOf(cyl)
            if (cylinderOutcome == "RELEASE") {
                cylinders[idx] = cyl.copy(hseHold = false)
                cylinderCustodyEvents.add(
                    CylinderCustodyEvent(
                        id = "EVT-" + UUID.randomUUID().toString().take(6),
                        serialNumber = cyl.serialNumber,
                        fromCustody = cyl.custody,
                        toCustody = cyl.custody,
                        orderId = inc.orderId,
                        actor = actor
                    )
                )
            } else if (cylinderOutcome == "WRITE_OFF") {
                cylinders[idx] = cyl.copy(
                    status = InventoryState.INV_WRITEOFF,
                    hseHold = false,
                    currentLocationLabel = "Decommissioned / Written Off"
                )
                cylinderCustodyEvents.add(
                    CylinderCustodyEvent(
                        id = "EVT-" + UUID.randomUUID().toString().take(6),
                        serialNumber = cyl.serialNumber,
                        fromCustody = cyl.custody,
                        toCustody = CylinderCustody.PLANT,
                        orderId = inc.orderId,
                        actor = actor
                    )
                )
            }
        }
    }

    val (ok, msg) = CaseLifecycles.transitionL7(
        incident = inc,
        target = target,
        actor = actor,
        role = AccountableRole.HSE,
        notes = notes,
        authorizedBy = authorizedBy
    )
    if (!ok) return Pair(false, msg)

    logAudit("SafetyIncident", incidentId, "MOVE_INCIDENT_${target.name}", actor, AccountableRole.HSE, notes)
    notifyChanged()
    return Pair(true, "Incident $incidentId transitioned to ${target.name}.")
}
