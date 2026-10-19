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
fun StaffSafetyHseScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var selectedSafetyTab by remember { mutableStateOf("INCIDENTS") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    val heldCylinders = snapshot.cylinders.filter { it.hseHold }

    // Log incident dialog state
    var showRaiseIncidentDialog by remember { mutableStateOf(false) }
    var incidentSourceInput by remember { mutableStateOf("Rider delivery inspection") }
    var incidentDescInput by remember { mutableStateOf("Gas smell and defective regulator seal detected upon delivery attempt") }
    var incidentOrderIdInput by remember { mutableStateOf(snapshot.orders.firstOrNull()?.id ?: "") }
    var incidentCylinderSerialInput by remember { mutableStateOf("BG-LHR-002") }
    var incidentLocationInput by remember { mutableStateOf("Gulberg III, Lahore") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("safety_hse_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "HSE & Safety Officer",
                description = "L7 Safety Incident Investigation, Cylinder HSE Hold Quarantines & OGRA Compliance"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "INCIDENTS" to "Safety Incidents (${snapshot.safetyIncidents.size})",
                    "HSE_HOLD" to "Quarantined Inventory (${heldCylinders.size})"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedSafetyTab == id,
                        onClick = { selectedSafetyTab = id },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (selectedSafetyTab) {
            "INCIDENTS" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "INCIDENT_RAISED", "UNDER_HSE_ASSESSMENT", "INCIDENT_CLOSED").forEach { tab ->
                                FilterChip(
                                    selected = selectedFilter == tab,
                                    onClick = { selectedFilter = tab },
                                    label = { Text(tab.replace("_", " "), fontSize = 8.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { showRaiseIncidentDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Incident", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                val filteredIncidents = when (selectedFilter) {
                    "ALL" -> snapshot.safetyIncidents
                    else -> snapshot.safetyIncidents.filter { it.status.name == selectedFilter }
                }

                if (filteredIncidents.isEmpty()) {
                    item { Text("No safety incidents matching filter.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(filteredIncidents) { inc ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
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
                                    Text("Incident ${inc.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFB91C1C))
                                    Surface(
                                        color = when (inc.status) {
                                            L7Status.INCIDENT_CLOSED -> Color(0xFFDCFCE7)
                                            L7Status.INCIDENT_RAISED -> Color(0xFFFEE2E2)
                                            else -> Color(0xFFFEF3C7)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = inc.status.name,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (inc.status) {
                                                L7Status.INCIDENT_CLOSED -> Color(0xFF166534)
                                                L7Status.INCIDENT_RAISED -> Color(0xFF991B1B)
                                                else -> Color(0xFF92400E)
                                            },
                                            modifier = Modifier.padding(4.dp, 2.dp)
                                        )
                                    }
                                }
                                Text("Source: ${inc.source} • Location: ${inc.location}", fontSize = 10.sp, color = Color(0xFF475569))
                                Text(inc.description, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))

                                if (inc.cylinderId != null) {
                                    Text("Quarantined Cylinder Serial: ${inc.cylinderId} (HSE Hold Active)", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFDC2626))
                                }

                                // Lifecycle Action buttons
                                Spacer(modifier = Modifier.height(6.dp))
                                when (inc.status) {
                                    L7Status.INCIDENT_RAISED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveIncident(inc.id, L7Status.UNDER_HSE_ASSESSMENT, "Formal assessment begun by HSE Inspector", actor = "HSE Officer")
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Begin Formal HSE Assessment", fontSize = 10.sp)
                                        }
                                    }
                                    L7Status.UNDER_HSE_ASSESSMENT -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveIncident(inc.id, L7Status.AWAITING_AUTHORIZED_DECISION, "Assessment findings submitted to Head of HSE", actor = "HSE Officer")
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Submit for Authorized Disposition Decision", fontSize = 10.sp)
                                        }
                                    }
                                    L7Status.AWAITING_AUTHORIZED_DECISION -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.moveIncident(
                                                        incidentId = inc.id,
                                                        target = L7Status.AUTHORIZED_ACTION_TAKEN,
                                                        notes = "Cylinder passed pressure & ultrasonic re-inspection. Cleared.",
                                                        authorizedBy = "Head of HSE",
                                                        cylinderOutcome = "RELEASE",
                                                        actor = "HSE Officer"
                                                    )
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Release Cylinder", fontSize = 9.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.moveIncident(
                                                        incidentId = inc.id,
                                                        target = L7Status.AUTHORIZED_ACTION_TAKEN,
                                                        notes = "Cylinder condemned due to pit corrosion. Scrapped.",
                                                        authorizedBy = "Head of HSE",
                                                        cylinderOutcome = "WRITE_OFF",
                                                        actor = "HSE Officer"
                                                    )
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Condemn & Scrap", fontSize = 9.sp)
                                            }
                                        }
                                    }
                                    L7Status.AUTHORIZED_ACTION_TAKEN -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveIncident(
                                                    incidentId = inc.id,
                                                    target = L7Status.INCIDENT_CLOSED,
                                                    notes = "Audit trail signed off and closed.",
                                                    actor = "HSE Officer"
                                                )
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Close Incident Record", fontSize = 10.sp)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
                    }
                }
            }

            "HSE_HOLD" -> {
                item {
                    Text("Physical Cylinders on HSE Hold Quarantine (${heldCylinders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Rule: Cylinders on HSE hold are strictly blocked from allocation, dispatch, or customer delivery until cleared by authorized disposition.",
                        fontSize = 10.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (heldCylinders.isEmpty()) {
                    item { Text("No cylinders currently quarantined on HSE hold.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(heldCylinders) { cyl ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
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
                                    Text(cyl.serialNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = Color(0xFFFEE2E2),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text("HSE HOLD", fontSize = 9.sp, fontWeight = FontWeight.Bold, color = Color(0xFF991B1B), modifier = Modifier.padding(4.dp, 2.dp))
                                    }
                                }
                                Text("SKU: ${cyl.sku} • Location: ${cyl.currentLocationLabel}", fontSize = 10.sp)
                                Text("Custody: ${cyl.custody.name} • Status: ${cyl.status.label}", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }
        }
    }

    // Log Incident Dialog
    if (showRaiseIncidentDialog) {
        AlertDialog(
            onDismissRequest = { showRaiseIncidentDialog = false },
            title = { Text("Log Safety & HSE Incident", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = incidentSourceInput,
                        onValueChange = { incidentSourceInput = it },
                        label = { Text("Source (e.g. Rider, Depot, Customer)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = incidentDescInput,
                        onValueChange = { incidentDescInput = it },
                        label = { Text("Incident Description / Gas Defect") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = incidentCylinderSerialInput,
                        onValueChange = { incidentCylinderSerialInput = it },
                        label = { Text("Affected Cylinder Serial (Placed on HSE Hold)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = incidentLocationInput,
                        onValueChange = { incidentLocationInput = it },
                        label = { Text("Incident Location") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.raiseIncident(
                            source = incidentSourceInput,
                            description = incidentDescInput,
                            orderId = incidentOrderIdInput.ifBlank { null },
                            cylinderId = incidentCylinderSerialInput.ifBlank { null },
                            location = incidentLocationInput,
                            evidenceCaptured = true,
                            actor = "HSE Officer"
                        )
                        onBanner(msg)
                        showRaiseIncidentDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Log & Quarantine")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRaiseIncidentDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
