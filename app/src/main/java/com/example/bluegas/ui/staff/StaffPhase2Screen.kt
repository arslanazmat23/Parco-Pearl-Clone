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
                roleName = "Phase 2 Preview (Brand Swap & Assistance)",
                description = "Competitor Cylinder Exchange, Provisional Settlement Offers & Self-Help Diagnostics"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = if (isEnabled) Color(0xFFEFF6FF) else Color(0xFFFEF2F2),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, if (isEnabled) Color(0xFF93C5FD) else Color(0xFFFCA5A5)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (isEnabled) Icons.Default.CheckCircle else Icons.Default.Info,
                        contentDescription = null,
                        tint = if (isEnabled) Color(0xFF1E40AF) else Color(0xFFDC2626),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isEnabled) "Phase 2 preview is active. Client settlement decisions & inspection gates available."
                        else "Phase 2 preview flag is disabled in Admin Settings. Controls are running in read-only sandbox mode.",
                        fontSize = 10.sp,
                        color = if (isEnabled) Color(0xFF1E40AF) else Color(0xFF991B1B)
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
                    Text("L8 Replacement / Brand Swap Claims (${snapshot.replacementClaims.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.replacementClaims.isEmpty()) {
                    item { Text("No replacement claims logged.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.replacementClaims) { claim ->
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
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Claim ${claim.id} • ${claim.brand}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (claim.status) {
                                            ClaimStatus.CLOSED, ClaimStatus.EXCHANGE_COMPLETED -> Color(0xFFDCFCE7)
                                            ClaimStatus.INELIGIBLE, ClaimStatus.CANCELLED -> Color(0xFFFEE2E2)
                                            else -> Color(0xFFDBEAFE)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = claim.status.name,
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
                                Text("Customer: ${claim.customerId} • Serial: ${claim.serialNumber}", fontSize = 11.sp)
                                Text("Condition: ${claim.conditionDescription} • Tare Weight: ${claim.tareWeightKg}kg", fontSize = 10.sp, color = Color(0xFF475569))

                                if (claim.provisionalOfferPaisa > 0) {
                                    Text("Provisional Offer: ${formatPaisaToPkr(claim.provisionalOfferPaisa)}", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
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
                                                    val (ok, msg) = store.assessEligibility(claim.id, true, "Competitor brand and tare weight proof verified")
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Assess Eligible", fontSize = 10.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val (ok, msg) = store.assessEligibility(claim.id, false, "Competitor cylinder severely damaged / uncertified")
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Ineligible", fontSize = 10.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                    ClaimStatus.PROVISIONALLY_ELIGIBLE -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.issueProvisionalOffer(claim.id)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Issue Provisional Discount Offer", fontSize = 10.sp)
                                        }
                                    }
                                    ClaimStatus.PROVISIONAL_OFFER_ISSUED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.startInspection(claim.id, "Inspector Aslam")
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Start Physical Inspection", fontSize = 10.sp)
                                        }
                                    }
                                    ClaimStatus.AT_HANDOVER_INSPECTION -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.completeInspection(
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
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Pass Inspection", fontSize = 10.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val (ok, msg) = store.completeInspection(
                                                        claimId = claim.id,
                                                        passed = false,
                                                        notes = "Foot ring rusted through, failed safety inspection",
                                                        measuredWeightKg = 13.0,
                                                        valveIntact = false,
                                                        tareWeightReadable = false
                                                    )
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Fail Inspection", fontSize = 10.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                    ClaimStatus.SETTLEMENT_BENEFIT_APPLIED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.issueSettlementPayout(claim.id)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Issue Settlement Payout", fontSize = 10.sp)
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
                                    Text("Asset ${sur.id} (${sur.brand})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Text(sur.status.name, fontSize = 9.sp, fontWeight = FontWeight.Bold, color = BlueGasPrimary)
                                }
                                Text("Serial: ${sur.serialNumber} • Location: ${sur.holdingLocationLabel}", fontSize = 10.sp)

                                if (sur.status == SurrenderStatus.COLLECTED || sur.status == SurrenderStatus.RECEIVED_AT_DEPOT) {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            val (ok, msg) = store.authorizeSurrenderDisposition(sur.id, "RECYCLING_DESTRUCTION", "Legal Compliance Officer")
                                            onBanner(msg)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(28.dp)
                                    ) {
                                        Text("Authorize Recycling Disposition", fontSize = 9.sp)
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
                                Text("Issue: ${tb.issueType} • Outcome: ${tb.outcome}", fontSize = 10.sp, color = Color(0xFF1E40AF))
                                Text(tb.notes ?: "", fontSize = 10.sp, color = Color(0xFF475569))
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
