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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffAdminScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var selectedAdminTab by remember { mutableStateOf("SETTINGS") }

    val currentSettings = snapshot.settings
    var holdTtl by remember(currentSettings.holdTtlMinutes) { mutableStateOf(currentSettings.holdTtlMinutes.toString()) }
    var acceptanceWindow by remember(currentSettings.distributorAcceptanceWindowMins) { mutableStateOf(currentSettings.distributorAcceptanceWindowMins.toString()) }
    var promisedHandoverSla by remember(currentSettings.promisedHandoverMinutes) { mutableStateOf(currentSettings.promisedHandoverMinutes.toString()) }
    var cashHandoverHours by remember(currentSettings.cashHandoverHours) { mutableStateOf(currentSettings.cashHandoverHours.toString()) }
    var evidenceRetentionDays by remember(currentSettings.evidenceRetentionDays) { mutableStateOf(currentSettings.evidenceRetentionDays.toString()) }
    var maxQtyPerOrder by remember(currentSettings.maxQuantityPerOrder) { mutableStateOf(currentSettings.maxQuantityPerOrder.toString()) }
    var newCylinderEnabled by remember(currentSettings.newCylinderModel) { mutableStateOf(currentSettings.newCylinderModel) }
    var refillModelEnabled by remember(currentSettings.refillModel) { mutableStateOf(currentSettings.refillModel) }
    var phase2PreviewEnabled by remember(currentSettings.phase2PreviewEnabled) { mutableStateOf(currentSettings.phase2PreviewEnabled) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "System Administrator",
                description = "Live Business Parameters, Service Zones Coverage & Immutable System Audit Logs"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "SETTINGS" to "Settings",
                    "COVERAGE" to "Service Zones (${snapshot.serviceZones.size})",
                    "AUDIT" to "Audit Trail (${snapshot.auditLogs.size})",
                    "TOOLS" to "Demo Controls"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedAdminTab == id,
                        onClick = { selectedAdminTab = id },
                        label = { Text(label, fontSize = 9.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (selectedAdminTab) {
            "SETTINGS" -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Dynamic Business Parameters (Immediate Live Effect):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            OutlinedTextField(
                                value = holdTtl,
                                onValueChange = { holdTtl = it },
                                label = { Text("Stock Hold TTL (Minutes) [1-120]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = acceptanceWindow,
                                onValueChange = { acceptanceWindow = it },
                                label = { Text("Distributor Acceptance Window (Minutes) [1-240]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = promisedHandoverSla,
                                onValueChange = { promisedHandoverSla = it },
                                label = { Text("Promised Handover SLA (Minutes) [15-1440]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = cashHandoverHours,
                                onValueChange = { cashHandoverHours = it },
                                label = { Text("Cash Handover Window (Hours) [1-168]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = evidenceRetentionDays,
                                onValueChange = { evidenceRetentionDays = it },
                                label = { Text("Evidence Retention Period (Days) [30-3650]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = maxQtyPerOrder,
                                onValueChange = { maxQtyPerOrder = it },
                                label = { Text("Max Cylinders Per Household Order [1-10]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            Text("Commercial Model & Feature Flags:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = newCylinderEnabled,
                                    onCheckedChange = { newCylinderEnabled = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("New Cylinder Package Sales Enabled", fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = refillModelEnabled,
                                    onCheckedChange = { refillModelEnabled = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Refill Exchange Service Enabled", fontSize = 11.sp)
                            }
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Checkbox(
                                    checked = phase2PreviewEnabled,
                                    onCheckedChange = { phase2PreviewEnabled = it }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Phase 2 Brand Swap Preview Enabled", fontSize = 11.sp)
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val newSettings = currentSettings.copy(
                                        holdTtlMinutes = holdTtl.toIntOrNull() ?: currentSettings.holdTtlMinutes,
                                        distributorAcceptanceWindowMins = acceptanceWindow.toIntOrNull() ?: currentSettings.distributorAcceptanceWindowMins,
                                        promisedHandoverMinutes = promisedHandoverSla.toIntOrNull() ?: currentSettings.promisedHandoverMinutes,
                                        cashHandoverHours = cashHandoverHours.toIntOrNull() ?: currentSettings.cashHandoverHours,
                                        evidenceRetentionDays = evidenceRetentionDays.toIntOrNull() ?: currentSettings.evidenceRetentionDays,
                                        maxQuantityPerOrder = maxQtyPerOrder.toIntOrNull() ?: currentSettings.maxQuantityPerOrder,
                                        newCylinderModel = newCylinderEnabled,
                                        refillModel = refillModelEnabled,
                                        phase2PreviewEnabled = phase2PreviewEnabled
                                    )
                                    val (ok, msg) = store.updateSettings(newSettings, "System Administrator")
                                    onBanner(msg)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Platform Parameters")
                            }
                        }
                    }
                }
            }

            "COVERAGE" -> {
                item {
                    Text("Service Zones & Polygon Dispatch Status (${snapshot.serviceZones.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(snapshot.serviceZones) { zone ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${zone.name} (${zone.city})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Zone ID: ${zone.id} • Postal Codes: ${zone.postalCodes.joinToString(", ")}", fontSize = 10.sp, color = Color(0xFF475569))
                                Text("Serving Point IDs: ${zone.servingPointIds.joinToString(", ")}", fontSize = 9.sp, color = Color.Gray)
                            }
                            Switch(
                                checked = zone.isServiceable,
                                onCheckedChange = { isChecked ->
                                    val (ok, msg) = store.setZoneServiceable(zone.id, isChecked)
                                    onBanner(msg)
                                }
                            )
                        }
                    }
                }
            }

            "AUDIT" -> {
                item {
                    Text("Immutable Security & Governance Audit Logs (${snapshot.auditLogs.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                items(snapshot.auditLogs.reversed()) { audit ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 2.dp)
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("[${audit.action}] ${audit.entityType}:${audit.entityId}", fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(audit.role.displayName, fontSize = 9.sp, color = BlueGasPrimary, fontWeight = FontWeight.SemiBold)
                            }
                            Text("Actor: ${audit.actor} • ${audit.details}", fontSize = 10.sp, color = Color(0xFF334155))
                        }
                    }
                }
            }

            "TOOLS" -> {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Demonstration & QA Diagnostic Triggers:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))

                            Button(
                                onClick = {
                                    store.processTimers()
                                    onBanner("Timer cycle executed: checked stock hold TTLs, SLA windows and queued FBR retries.")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Schedule, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Trigger Immediate Timer SLA Cycle")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    store.resetToSeed()
                                    onBanner("Demo platform state reset to clean initial seed data.")
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Reset to Clean Seed Dataset")
                            }
                        }
                    }
                }
            }
        }
    }
}
