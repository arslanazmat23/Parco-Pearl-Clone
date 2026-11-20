package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffPhase2Screen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var selectedPhase2Tab by remember { mutableStateOf("CLAIMS") }

    val isEnabled = snapshot.settings.phase2PreviewEnabled

    // Troubleshooting Dialog state
    var showTroubleshootingDialog by remember { mutableStateOf(false) }
    var custIdInput by remember { mutableStateOf("CUST-001") }
    var issueTypeInput by remember { mutableStateOf("REGULATOR_CLICK_FAULT") }
    var outcomeInput by remember { mutableStateOf("RESOLVED_STEP_3") }
    var tbNotesInput by remember { mutableStateOf("Customer guided to re-seat safety pin washer.") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("phase2_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Phase 2 Preview (Switch & Save & Assistance)",
                description = "Switch & Save Route A/B Exchanges, Provisional Discount Offers & Self-Help Diagnostics"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("phase2_route_a_banner")
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF1E40AF),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Phase 2 preview — Route A is a recommendation awaiting stakeholder approval and a BRD change.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF1E40AF)
                    )
                }
            }
            if (!isEnabled) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFEF2F2),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Phase 2 preview flag is disabled in Admin Settings.",
                        fontSize = 10.sp,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "CLAIMS" to "Claims (${snapshot.replacementClaims.size})",
                    "SURRENDERED" to "Surrendered (${snapshot.surrenderedCylinders.size})",
                    "TROUBLESHOOT" to "Troubleshooting"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedPhase2Tab == id,
                        onClick = { selectedPhase2Tab = id },
                        label = { Text(label, fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (selectedPhase2Tab) {
            "CLAIMS" -> {
                item {
                    Text("Switch & Save Claims (${snapshot.replacementClaims.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.replacementClaims.isEmpty()) {
                    item { Text("No replacement claims logged.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.replacementClaims) { claim ->
                        val statusLabel = when (claim.status) {
                            ClaimStatus.EVIDENCE_SUBMITTED -> "Evidence submitted"
                            ClaimStatus.ELIGIBILITY_ASSESSED -> "Under eligibility check"
                            ClaimStatus.PROVISIONALLY_ELIGIBLE -> "Provisionally eligible"
                            ClaimStatus.PROVISIONAL_OFFER_ISSUED -> "Provisional offer sent"
                            ClaimStatus.CUSTOMER_CONFIRMED -> "Customer confirmed"
                            ClaimStatus.SCHEDULED_FOR_HANDOVER -> "Scheduled for handover"
                            ClaimStatus.AT_HANDOVER_INSPECTION -> "Physical inspection"
                            ClaimStatus.PHYSICALLY_ACCEPTED -> "Inspection passed"
                            ClaimStatus.HSE_ASSESSMENT -> "HSE check in progress"
                            ClaimStatus.HSE_CLEARED -> "HSE cleared"
                            ClaimStatus.FINAL_OFFER_CONFIRMED -> "Ready to complete exchange"
                            ClaimStatus.EXCHANGE_COMPLETED -> "Exchange completed"
                            ClaimStatus.CLOSED -> "Closed"
                            ClaimStatus.INELIGIBLE -> "Not eligible"
                            ClaimStatus.CANCELLED -> "Cancelled"
                            else -> "In progress"
                        }
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .testTag("claim_card_${claim.id}")
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${claim.id} • ${claim.declaredBrand}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (claim.status) {
                                            ClaimStatus.CLOSED, ClaimStatus.EXCHANGE_COMPLETED -> Color(0xFFDCFCE7)
                                            ClaimStatus.INELIGIBLE, ClaimStatus.CANCELLED -> Color(0xFFFEE2E2)
                                            else -> Color(0xFFDBEAFE)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = statusLabel,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (claim.status) {
                                                ClaimStatus.CLOSED, ClaimStatus.EXCHANGE_COMPLETED -> Color(0xFF166534)
                                                ClaimStatus.INELIGIBLE, ClaimStatus.CANCELLED -> Color(0xFF991B1B)
                                                else -> Color(0xFF1E40AF)
                                            },
                                            modifier = Modifier.padding(4.dp, 2.dp)
                                        )
                                    }
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                // Route chip
                                Surface(
                                    color = if (claim.route == "A") Color(0xFFFEF3C7) else Color(0xFFE0F2FE),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = if (claim.route == "A") "Route A — Customer keeps old cylinder" else "Route B — BLUE GAS collects & checks",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = if (claim.route == "A") Color(0xFF92400E) else Color(0xFF0369A1),
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Customer: ${claim.customerId} • Serial: ${claim.declaredSerial ?: "N/A"}", fontSize = 11.sp)
                                Text("Condition: ${claim.declaredCondition} • Declared Size: ${claim.declaredSize}", fontSize = 10.sp, color = Color(0xFF475569))
                                if (claim.linkedOrderId != null) {
                                    Text("Replacement order: ${claim.linkedOrderId}", fontSize = 10.sp, color = BlueGasPrimary, fontWeight = FontWeight.SemiBold)
                                }
                                if (claim.provisionalOfferPaisa > 0) {
                                    Text("Provisional Offer: ${formatPaisaToPkr(claim.provisionalOfferPaisa)}", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                }
                                if (!claim.closureReason.isNullOrBlank()) {
                                    Text("Outcome: ${claim.closureReason}", fontSize = 10.sp, color = Color(0xFF475569))
                                }

                                // Gate action buttons
                                Spacer(modifier = Modifier.height(8.dp))
                                when (claim.status) {
                                    ClaimStatus.EVIDENCE_SUBMITTED -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.assessEligibility(claim.id, true, "Competitor brand and tare weight proof verified")
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Assess Eligible", fontSize = 10.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val (_, msg) = store.assessEligibility(claim.id, false, "Competitor cylinder severely damaged / uncertified")
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.weight(1f).height(34.dp)
                                            ) {
                                                Text("Ineligible", fontSize = 10.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                    ClaimStatus.PROVISIONALLY_ELIGIBLE -> {
                                        Button(
                                            onClick = {
                                                val (_, msg) = store.issueProvisionalOffer(claim.id)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                            modifier = Modifier.fillMaxWidth().height(34.dp)
                                        ) {
                                            Text("Issue Provisional Discount Offer", fontSize = 10.sp)
                                        }
                                    }
                                    ClaimStatus.PROVISIONAL_OFFER_ISSUED -> {
                                        val defaultAddr = snapshot.addresses.firstOrNull { it.customerId == claim.customerId && it.isServiceable }
                                        if (defaultAddr != null) {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.scheduleClaimHandover(claim.id, defaultAddr.id, PaymentMethod.ONLINE_HOSTED)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                                modifier = Modifier.fillMaxWidth().height(34.dp)
                                            ) {
                                                Text("Confirm & Schedule Replacement Order", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    ClaimStatus.SCHEDULED_FOR_HANDOVER -> {
                                        if (claim.route == "A") {
                                            Surface(
                                                color = Color(0xFFFEF3C7),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = "Closes when the replacement order is completed",
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = Color(0xFF92400E),
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        } else {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.startInspection(claim.id, "Inspector Aslam")
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.fillMaxWidth().height(34.dp)
                                            ) {
                                                Text("Start Physical Inspection", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    ClaimStatus.AT_HANDOVER_INSPECTION -> {
                                        if (claim.route != "A") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val (_, msg) = store.completeInspection(
                                                            claimId = claim.id,
                                                            passed = true,
                                                            notes = "Tare weight verified, zero leakage detected",
                                                            measuredWeightKg = 15.2,
                                                            valveIntact = true,
                                                            tareWeightReadable = true
                                                        )
                                                        onBanner(msg)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Pass Inspection", fontSize = 10.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val (_, msg) = store.completeInspection(
                                                            claimId = claim.id,
                                                            passed = false,
                                                            notes = "Foot ring rusted through, failed safety inspection",
                                                            measuredWeightKg = 13.0,
                                                            valveIntact = false,
                                                            tareWeightReadable = false
                                                        )
                                                        onBanner(msg)
                                                    },
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Fail Inspection", fontSize = 10.sp, color = Color(0xFFDC2626))
                                                }
                                            }
                                        }
                                    }
                                    ClaimStatus.PHYSICALLY_ACCEPTED -> {
                                        if (claim.route != "A") {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.startHseAssessment(claim.id)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                                modifier = Modifier.fillMaxWidth().height(34.dp)
                                            ) {
                                                Text("Start HSE Check", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    ClaimStatus.HSE_ASSESSMENT -> {
                                        if (claim.route != "A") {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                Button(
                                                    onClick = {
                                                        val (_, msg) = store.recordHseResult(claim.id, true, "Shell and valve cleared by HSE")
                                                        onBanner(msg)
                                                    },
                                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Clear HSE", fontSize = 10.sp)
                                                }
                                                OutlinedButton(
                                                    onClick = {
                                                        val (_, msg) = store.recordHseResult(claim.id, false, "Unsafe cylinder held for HSE incident")
                                                        onBanner(msg)
                                                    },
                                                    modifier = Modifier.weight(1f).height(34.dp)
                                                ) {
                                                    Text("Hold Unsafe", fontSize = 10.sp, color = Color(0xFFDC2626))
                                                }
                                            }
                                        }
                                    }
                                    ClaimStatus.HSE_CLEARED -> {
                                        if (claim.route != "A") {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.confirmFinalOffer(claim.id, claim.provisionalOfferPaisa)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                                modifier = Modifier.fillMaxWidth().height(34.dp)
                                            ) {
                                                Text("Confirm Final Offer", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    ClaimStatus.FINAL_OFFER_CONFIRMED -> {
                                        if (claim.route != "A") {
                                            Button(
                                                onClick = {
                                                    val (_, msg) = store.completeExchange(claim.id)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(34.dp)
                                                    .testTag("complete_exchange_btn_${claim.id}")
                                            ) {
                                                Text("Complete exchange", fontSize = 10.sp)
                                            }
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }

            "SURRENDERED" -> {
                item {
                    Text("Surrendered Competitor Cylinders (${snapshot.surrenderedCylinders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.surrenderedCylinders.isEmpty()) {
                    item { Text("No surrendered cylinders in quarantine.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.surrenderedCylinders) { sur ->
                        val linkedClaim = snapshot.replacementClaims.find { it.id == sur.claimId }
                        val hasAgreement = sur.brand in snapshot.settings.rivalReturnAgreements
                        val hasProof = linkedClaim?.ownershipProofProvided == true
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Surrendered — ${sur.brand} · ${sur.serialText}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(
                                        text = if (sur.status == SurrenderStatus.DISPOSITION_AUTHORIZED) "Disposition authorised" else "At hub quarantine",
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = BlueGasPrimary
                                    )
                                }
                                Text("Record ID: ${sur.id} • Claim: ${sur.claimId}", fontSize = 10.sp, color = Color(0xFF475569))
                                if (!sur.dispositionType.isNullOrBlank()) {
                                    Text("Authorised choice: ${sur.dispositionType}", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                }

                                if (sur.status == SurrenderStatus.COLLECTED || sur.status == SurrenderStatus.RECEIVED_AT_DEPOT) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    if (hasAgreement) {
                                        val choice = "Return to ${sur.brand} under the written agreement"
                                        Button(
                                            onClick = {
                                                val (_, msg) = store.authorizeDisposition(sur.id, choice, AccountableRole.LEGAL.displayName)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                        ) {
                                            Text(choice, fontSize = 10.sp)
                                        }
                                    }
                                    if (hasProof) {
                                        if (hasAgreement) Spacer(modifier = Modifier.height(4.dp))
                                        val recycleChoice = "Recycle through an authorised route"
                                        OutlinedButton(
                                            onClick = {
                                                val (_, msg) = store.authorizeDisposition(sur.id, recycleChoice, AccountableRole.LEGAL.displayName)
                                                onBanner(msg)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.fillMaxWidth().height(32.dp)
                                        ) {
                                            Text(recycleChoice, fontSize = 10.sp)
                                        }
                                    }
                                    if (!hasAgreement && !hasProof) {
                                        Text(
                                            text = "Cannot authorise disposition: no written agreement with ${sur.brand} and no ownership proof on record.",
                                            fontSize = 10.sp,
                                            color = Color(0xFFDC2626)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "TROUBLESHOOT" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Self-Help & Guided Troubleshooting Logs:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showTroubleshootingDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Attempt", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.troubleshootingRequests.isEmpty()) {
                    item { Text("No troubleshooting attempts recorded.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.troubleshootingRequests) { tb ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text("Diagnostic ${tb.id} • Customer: ${tb.customerId}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Channel: ${tb.channel} • Status: ${tb.status}", fontSize = 10.sp, color = Color(0xFF1E40AF))
                                Text(tb.description, fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
        }
    }

    // Record Troubleshooting Dialog
    if (showTroubleshootingDialog) {
        AlertDialog(
            onDismissRequest = { showTroubleshootingDialog = false },
            title = { Text("Log Guided Troubleshooting Event", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = custIdInput,
                        onValueChange = { custIdInput = it },
                        label = { Text("Customer ID") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = issueTypeInput,
                        onValueChange = { issueTypeInput = it },
                        label = { Text("Appliance / Cylinder Issue") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = outcomeInput,
                        onValueChange = { outcomeInput = it },
                        label = { Text("Diagnostic Outcome") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tbNotesInput,
                        onValueChange = { tbNotesInput = it },
                        label = { Text("Resolution Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.recordTroubleshooting(
                            customerId = custIdInput,
                            issueType = issueTypeInput,
                            outcome = outcomeInput,
                            notes = tbNotesInput,
                            actor = "Self-Service Mobile Diagnostic"
                        )
                        onBanner(msg)
                        showTroubleshootingDialog = false
                    }
                ) {
                    Text("Record Attempt")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTroubleshootingDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
