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
    var acceptanceWindow by remember(currentSettings.hubAcceptanceWindowMins) { mutableStateOf(currentSettings.hubAcceptanceWindowMins.toString()) }
    var riderAcceptanceWindow by remember(currentSettings.riderAcceptanceWindowMins) { mutableStateOf(currentSettings.riderAcceptanceWindowMins.toString()) }
    var promisedHandoverSla by remember(currentSettings.promisedHandoverMinutes) { mutableStateOf(currentSettings.promisedHandoverMinutes.toString()) }
    var cashHandoverHours by remember(currentSettings.cashHandoverHours) { mutableStateOf(currentSettings.cashHandoverHours.toString()) }
    var evidenceRetentionDays by remember(currentSettings.evidenceRetentionDays) { mutableStateOf(currentSettings.evidenceRetentionDays.toString()) }
    var maxQtyPerOrder by remember(currentSettings.maxQuantityPerOrder) { mutableStateOf(currentSettings.maxQuantityPerOrder.toString()) }
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
                                label = { Text("Hub Acceptance Window (Minutes) [1-240]") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = riderAcceptanceWindow,
                                onValueChange = { riderAcceptanceWindow = it },
                                label = { Text("Rider Acceptance Window (mins) [1-240]") },
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

                            Text("Refill Model:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                RefillModel.entries.forEach { model ->
                                    FilterChip(
                                        selected = refillModelEnabled == model,
                                        onClick = { refillModelEnabled = model },
                                        label = { Text(model.name, fontSize = 10.sp) },
                                        modifier = Modifier.height(28.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(8.dp))

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
                                        hubAcceptanceWindowMins = acceptanceWindow.toIntOrNull() ?: currentSettings.hubAcceptanceWindowMins,
                                        riderAcceptanceWindowMins = riderAcceptanceWindow.toIntOrNull() ?: currentSettings.riderAcceptanceWindowMins,
                                        promisedHandoverMinutes = promisedHandoverSla.toIntOrNull() ?: currentSettings.promisedHandoverMinutes,
                                        cashHandoverHours = cashHandoverHours.toIntOrNull() ?: currentSettings.cashHandoverHours,
                                        evidenceRetentionDays = evidenceRetentionDays.toIntOrNull() ?: currentSettings.evidenceRetentionDays,
                                        maxQuantityPerOrder = maxQtyPerOrder.toIntOrNull() ?: currentSettings.maxQuantityPerOrder,
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

                    Spacer(modifier = Modifier.height(12.dp))
                    ToBeOptionsReadOnlyCard(
                        settings = currentSettings,
                        snapshot = snapshot
                    )
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
                                    val (ok, msg) = store.simulateDepositMismatch()
                                    onBanner(msg)
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Simulate Deposit Mismatch (EXC-28)")
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedButton(
                                onClick = {
                                    store.seedInitialData()
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

/**
 * Displays all To-Be configuration options read-only under Admin Settings with rule 7 status pills.
 *
 * @param settings Current [PlatformSettings] snapshot.
 * @param snapshot Current [SharedAppState] used to resolve human-readable hub and product names.
 */
@Composable
private fun ToBeOptionsReadOnlyCard(
    settings: PlatformSettings,
    snapshot: SharedAppState
) {
    val windowsText = buildString {
        append("ASAP: ")
        append(if (settings.asapEnabled) "On" else "Off")
        append(" • Slots: ")
        append(settings.deliveryWindowsHours.joinToString(", ") { "${it.first}:00–${it.second}:00" })
        append(" • Tomorrow: ")
        append(if (settings.tomorrowWindowsEnabled) "On" else "Off")
    }

    val expressText = "${if (settings.expressEnabled) "Enabled" else "Disabled"} • Fee: ${formatRupees(settings.expressFeePaisa)} • Target: ${settings.expressHours} hours"

    val backupText = if (settings.backupCylinderAllowed) {
        "Allowed (up to ${settings.maxDepositCylindersPerSize} per size)"
    } else {
        "Not allowed (limit 1 per size)"
    }

    val hubMinimumsText = if (settings.hubMinimumFilled.isEmpty()) {
        "None configured"
    } else {
        settings.hubMinimumFilled.entries.joinToString("; ") { (pointId, skus) ->
            val pointName = snapshot.nodes.find { it.id == pointId }?.name ?: pointId
            val skuText = skus.entries.joinToString(", ") { (skuId, qty) ->
                val sizeName = snapshot.productVariants.find { it.sku == skuId }?.let { v ->
                    v.cylinderSizeKg?.let { "$it kg" } ?: v.name
                } ?: skuId
                "$sizeName: $qty"
            }
            "$pointName ($skuText)"
        }
    }

    val dispatchText = when (settings.dispatchMode) {
        DispatchMode.HUB_FIRST -> "Hub first"
        DispatchMode.AUTO_RIDER_FIRST -> "Auto rider first"
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("to_be_options_card")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text("To-Be options", fontWeight = FontWeight.Bold, fontSize = 12.sp)
            Text(
                "Read-only upcoming policy and pilot configuration values.",
                fontSize = 10.sp,
                color = Color(0xFF64748B)
            )
            Spacer(modifier = Modifier.height(8.dp))

            ToBeSettingRow(
                label = "Dispatch mode",
                value = dispatchText,
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Maximum orders per rider trip",
                value = "${settings.riderBatchMax} orders",
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Delivery windows",
                value = windowsText,
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Same-day standard cut-off",
                value = "${settings.standardCutoffHour}:00",
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "Express delivery",
                value = expressText,
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "Not-home doorstep wait time",
                value = "${settings.notHomeWaitMinutes} minutes",
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "Free re-deliveries allowed",
                value = "${settings.freeRedeliveries}",
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "CNIC required for cylinder deposit",
                value = if (settings.cnicRequiredForDeposit) "Yes" else "No",
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Backup cylinder on deposit",
                value = backupText,
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "Refill reminder",
                value = "${if (settings.refillReminderEnabled) "Enabled" else "Disabled"} (${settings.refillReminderLeadDays} days ahead)",
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Customer SMS alerts",
                value = if (settings.smsEnabled) "Enabled (shows \"SMS sent\" tag)" else "Disabled"
            )
            ToBeSettingRow(
                label = "Hub minimum filled stock",
                value = hubMinimumsText,
                isDemoValue = true
            )
            ToBeSettingRow(
                label = "Daily plant fill plan time",
                value = "${settings.fillPlanHour}:00",
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Switch & Save (Route A)",
                value = if (settings.switchSaveRouteAEnabled) "Enabled" else "Disabled",
                isRecommended = true
            )
            ToBeSettingRow(
                label = "Rival cylinder return agreements (Route B)",
                value = if (settings.rivalReturnAgreements.isEmpty()) "None configured" else settings.rivalReturnAgreements.joinToString(", "),
                isDemoValue = true,
                showDivider = false
            )
        }
    }
}

/**
 * Renders one read-only To-Be setting row with human-readable wording and optional status pill.
 *
 * @param label Human-readable setting label.
 * @param value Formatted setting value in words.
 * @param isRecommended True to display the blue "Recommended — awaiting approval" pill.
 * @param isDemoValue True to display the grey "Demo value" pill.
 * @param showDivider Whether to draw a divider below the row.
 */
@Composable
private fun ToBeSettingRow(
    label: String,
    value: String,
    isRecommended: Boolean = false,
    isDemoValue: Boolean = false,
    showDivider: Boolean = true
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontWeight = FontWeight.SemiBold,
                fontSize = 11.sp,
                color = Color(0xFF1E293B),
                modifier = Modifier.weight(1f)
            )
        }
        if (isRecommended || isDemoValue) {
            Spacer(modifier = Modifier.height(2.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (isRecommended) {
                    Surface(
                        color = Color(0xFFDBEAFE),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Recommended — awaiting approval",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF1D4ED8),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                if (isDemoValue) {
                    Surface(
                        color = Color(0xFFE2E8F0),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = "Demo value",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = value,
            fontSize = 11.sp,
            color = Color(0xFF475569)
        )
        if (showDivider) {
            Spacer(modifier = Modifier.height(4.dp))
            HorizontalDivider(color = Color(0xFFF1F5F9))
        }
    }
}

