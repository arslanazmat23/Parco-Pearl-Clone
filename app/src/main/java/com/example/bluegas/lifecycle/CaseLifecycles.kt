package com.example.bluegas.lifecycle

import com.example.bluegas.model.*

object CaseLifecycles {

    // --- Lifecycle 6: Return/Refund Cases (v0.4 §9.4) ---
    val allowedL6Transitions: Map<L6Status, Set<L6Status>> = mapOf(
        L6Status.CASE_OPENED to setOf(L6Status.UNDER_REVIEW),
        L6Status.UNDER_REVIEW to setOf(L6Status.RETURN_AUTHORIZED, L6Status.RETURN_REJECTED, L6Status.REFUND_REQUESTED),
        L6Status.RETURN_AUTHORIZED to setOf(L6Status.AWAITING_ITEM_RETURN),
        L6Status.AWAITING_ITEM_RETURN to setOf(L6Status.ITEM_RETURNED),
        L6Status.ITEM_RETURNED to setOf(L6Status.REFUND_REQUESTED),
        L6Status.REFUND_REQUESTED to setOf(L6Status.REFUND_CONFIRMED),
        L6Status.REFUND_CONFIRMED to setOf(L6Status.CASE_CLOSED),
        L6Status.RETURN_REJECTED to setOf(L6Status.CASE_CLOSED),
        L6Status.CASE_CLOSED to emptySet()
    )

    fun transitionL6(
        returnCase: ReturnRefundCase,
        target: L6Status,
        actor: String,
        role: AccountableRole,
        reason: String
    ): Pair<Boolean, String> {
        val current = returnCase.status
        if (current == L6Status.CASE_CLOSED) {
            return Pair(false, "Case ${returnCase.id} is closed.")
        }
        val allowed = allowedL6Transitions[current] ?: emptySet()
        if (!allowed.contains(target)) {
            return Pair(false, "Cannot transition case from $current to $target. Legal targets: ${allowed.joinToString()}")
        }

        returnCase.status = target
        returnCase.history.add(
            CaseHistory(
                fromStatus = current,
                toStatus = target,
                actor = actor,
                role = role,
                reason = reason
            )
        )
        if (target == L6Status.CASE_CLOSED) {
            returnCase.closedAt = System.currentTimeMillis()
        }
        return Pair(true, "Case ${returnCase.id} updated to $target")
    }

    // --- Lifecycle 7: Safety (HSE) Incidents (v0.4 §16) ---
    val allowedL7Transitions: Map<L7Status, Set<L7Status>> = mapOf(
        L7Status.INCIDENT_RAISED to setOf(L7Status.UNDER_HSE_ASSESSMENT),
        L7Status.UNDER_HSE_ASSESSMENT to setOf(L7Status.AWAITING_AUTHORIZED_DECISION),
        L7Status.AWAITING_AUTHORIZED_DECISION to setOf(L7Status.AUTHORIZED_ACTION_TAKEN),
        L7Status.AUTHORIZED_ACTION_TAKEN to setOf(L7Status.INCIDENT_CLOSED),
        L7Status.INCIDENT_CLOSED to emptySet()
    )

    fun transitionL7(
        incident: SafetyIncident,
        target: L7Status,
        actor: String,
        role: AccountableRole,
        notes: String,
        authorizedBy: String? = null
    ): Pair<Boolean, String> {
        val current = incident.status
        if (current == L7Status.INCIDENT_CLOSED) {
            return Pair(false, "Incident ${incident.id} is closed.")
        }
        val allowed = allowedL7Transitions[current] ?: emptySet()
        if (!allowed.contains(target)) {
            return Pair(false, "Cannot transition incident from $current to $target. Legal targets: ${allowed.joinToString()}")
        }

        incident.status = target
        if (authorizedBy != null) {
            incident.authorizedBy = authorizedBy
            incident.authorizedDecision = notes
        }
        incident.history.add(
            IncidentHistory(
                fromStatus = current,
                toStatus = target,
                actor = actor,
                role = role,
                notes = notes
            )
        )
        if (target == L7Status.INCIDENT_CLOSED) {
            incident.closedAt = System.currentTimeMillis()
        }
        return Pair(true, "Incident ${incident.id} updated to $target")
    }
}
