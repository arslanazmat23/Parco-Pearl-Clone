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
    var cnicRequiredForDeposit by remember(currentSettings.cnicRequiredForDeposit) { mutableStateOf(currentSettings.cnicRequiredForDeposit) }
    var backupCylinderAllowed by remember(currentSettings.backupCylinderAllowed) { mutableStateOf(currentSettings.backupCylinderAllowed) }
    var maxDepositCylindersPerSize by remember(currentSettings.maxDepositCylindersPerSize) { mutableStateOf(currentSettings.maxDepositCylindersPerSize.toString()) }
    var asapEnabled by remember(currentSettings.asapEnabled) { mutableStateOf(currentSettings.asapEnabled) }
    var tomorrowWindowsEnabled by remember(currentSettings.tomorrowWindowsEnabled) { mutableStateOf(currentSettings.tomorrowWindowsEnabled) }
    var deliveryWindowsText by remember(currentSettings.deliveryWindowsHours) {
        mutableStateOf(currentSettings.deliveryWindowsHours.joinToString(", ") { "${it.first}-${it.second}" })
    }
    var standardCutoffHour by remember(currentSettings.standardCutoffHour) { mutableStateOf(currentSettings.standardCutoffHour.toString()) }
    var standardSameDayByHour by remember(currentSettings.standardSameDayByHour) { mutableStateOf(currentSettings.standardSameDayByHour.toString()) }
    var notHomeWaitMinutes by remember(currentSettings.notHomeWaitMinutes) { mutableStateOf(currentSettings.notHomeWaitMinutes.toString()) }
    var freeRedeliveries by remember(currentSettings.freeRedeliveries) { mutableStateOf(currentSettings.freeRedeliveries.toString()) }
    var expressEnabled by remember(currentSettings.expressEnabled) { mutableStateOf(currentSettings.expressEnabled) }
    var expressFeeRs by remember(currentSettings.expressFeePaisa) { mutableStateOf((currentSettings.expressFeePaisa / 100L).toString()) }
    var expressHours by remember(currentSettings.expressHours) { mutableStateOf(currentSettings.expressHours.toString()) }
    var refillReminderEnabled by remember(currentSettings.refillReminderEnabled) { mutableStateOf(currentSettings.refillReminderEnabled) }
    var refillReminderLeadDays by remember(currentSettings.refillReminderLeadDays) { mutableStateOf(currentSettings.refillReminderLeadDays.toString()) }

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

                            Spacer(modifier = Modifier.height(8.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(8.dp))

                            // 1. CNIC required for cylinder deposit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "CNIC required for cylinder deposit",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Recommended — awaiting approval",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = cnicRequiredForDeposit,
                                    onCheckedChange = { cnicRequiredForDeposit = it },
                                    modifier = Modifier.testTag("admin_cnic_deposit_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // 2. Backup cylinder on deposit
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text(
                                        text = "Backup cylinder on deposit",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(color = Color(0xFFE2E8F0), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Demo value",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF475569),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = backupCylinderAllowed,
                                    onCheckedChange = { backupCylinderAllowed = it },
                                    modifier = Modifier.testTag("admin_backup_cylinder_switch")
                                )
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // 3. Max deposit cylinders per size
                            OutlinedTextField(
                                value = maxDepositCylindersPerSize,
                                onValueChange = { maxDepositCylindersPerSize = it },
                                label = { Text("Max deposit cylinders per size [1-5]") },
                                supportingText = {
                                    Text(
                                        text = if (backupCylinderAllowed) {
                                            "Active limit per cylinder size (Demo value)"
                                        } else {
                                            "Used when backup cylinder on deposit is enabled; otherwise limit is 1"
                                        },
                                        fontSize = 10.sp
                                    )
                                },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_max_deposit_per_size_input")
                            )

                            Spacer(modifier = Modifier.height(10.dp))
                            HorizontalDivider(color = Color(0xFFE2E8F0))
                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = "Delivery Windows, Express & Refill Reminders",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A)
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            // ASAP delivery option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("ASAP delivery option", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Recommended — awaiting approval",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = asapEnabled,
                                    onCheckedChange = { asapEnabled = it },
                                    modifier = Modifier.testTag("admin_asap_enabled_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Tomorrow windows option
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Tomorrow delivery windows", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Recommended — awaiting approval",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = tomorrowWindowsEnabled,
                                    onCheckedChange = { tomorrowWindowsEnabled = it },
                                    modifier = Modifier.testTag("admin_tomorrow_windows_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = deliveryWindowsText,
                                onValueChange = { deliveryWindowsText = it },
                                label = { Text("Delivery window slots (24h, comma-separated)") },
                                placeholder = { Text("10-12, 12-14, 14-16, 16-18, 18-20") },
                                supportingText = {
                                    Text("Hours 7–22, 1–4 hour length, non-overlapping (Recommended — awaiting approval)", fontSize = 10.sp)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("admin_delivery_windows_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = standardCutoffHour,
                                onValueChange = { standardCutoffHour = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Same-day standard cut-off hour [8-22]") },
                                supportingText = { Text("Demo value · delivery promise", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_standard_cutoff_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = standardSameDayByHour,
                                onValueChange = { standardSameDayByHour = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Same-day standard promise hour [9-23]") },
                                supportingText = { Text("Demo value · delivery promise (default 21 = 9 pm)", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_standard_same_day_by_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = notHomeWaitMinutes,
                                onValueChange = { notHomeWaitMinutes = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Not-home doorstep wait time (minutes) [1-30]") },
                                supportingText = { Text("Demo value", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_not_home_wait_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = freeRedeliveries,
                                onValueChange = { freeRedeliveries = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Free re-deliveries allowed [0-3]") },
                                supportingText = { Text("Demo value", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_free_redeliveries_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Express delivery
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Express delivery", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "Recommended — awaiting approval",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF1D4ED8),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Surface(color = Color(0xFFE2E8F0), shape = RoundedCornerShape(4.dp)) {
                                            Text(
                                                text = "Demo value · delivery promise",
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = Color(0xFF475569),
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                                Switch(
                                    checked = expressEnabled,
                                    onCheckedChange = { expressEnabled = it },
                                    modifier = Modifier.testTag("admin_express_enabled_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = expressFeeRs,
                                onValueChange = { expressFeeRs = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Express delivery fee (Rs)") },
                                supportingText = { Text("Demo value · delivery promise", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_express_fee_input")
                            )
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = expressHours,
                                onValueChange = { expressHours = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Express target hours [1-6]") },
                                supportingText = { Text("Demo value · delivery promise", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_express_hours_input")
                            )
                            Spacer(modifier = Modifier.height(8.dp))

                            // Household refill reminder
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                    Text("Household refill reminder", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            text = "Recommended — awaiting approval",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF1D4ED8),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Switch(
                                    checked = refillReminderEnabled,
                                    onCheckedChange = { refillReminderEnabled = it },
                                    modifier = Modifier.testTag("admin_refill_reminder_switch")
                                )
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            OutlinedTextField(
                                value = refillReminderLeadDays,
                                onValueChange = { refillReminderLeadDays = it.filter { ch -> ch.isDigit() } },
                                label = { Text("Refill reminder lead days [0-14]") },
                                supportingText = { Text("Recommended — awaiting approval", fontSize = 10.sp) },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth().testTag("admin_refill_lead_days_input")
                            )

                            Spacer(modifier = Modifier.height(12.dp))
                            Button(
                                onClick = {
                                    val parsedMaxDeposits = maxDepositCylindersPerSize.trim().toIntOrNull()
                                    if (parsedMaxDeposits == null || parsedMaxDeposits !in 1..5) {
                                        onBanner("Max deposit cylinders per size must be between 1 and 5.")
                                        return@Button
                                    }
                                    val parsedWindows = parseDeliveryWindowsInput(deliveryWindowsText)
                                    if (parsedWindows == null) {
                                        onBanner("Delivery windows must be comma-separated start-end hours, e.g. 10-12, 12-14.")
                                        return@Button
                                    }
                                    val parsedCutoff = standardCutoffHour.trim().toIntOrNull()
                                    if (parsedCutoff == null) {
                                        onBanner("Enter a valid same-day cut-off hour (8–22).")
                                        return@Button
                                    }
                                    val parsedSameDayBy = standardSameDayByHour.trim().toIntOrNull()
                                    if (parsedSameDayBy == null) {
                                        onBanner("Enter a valid same-day promise hour (9–23).")
                                        return@Button
                                    }
                                    val parsedWaitMinutes = notHomeWaitMinutes.trim().toIntOrNull()
                                    if (parsedWaitMinutes == null) {
                                        onBanner("Enter a valid not-home wait time (1–30 minutes).")
                                        return@Button
                                    }
                                    val parsedFreeRedeliveries = freeRedeliveries.trim().toIntOrNull()
                                    if (parsedFreeRedeliveries == null) {
                                        onBanner("Enter a valid free re-deliveries count (0–3).")
                                        return@Button
                                    }
                                    val parsedExpressFeeRs = expressFeeRs.trim().toLongOrNull()
                                    if (parsedExpressFeeRs == null || parsedExpressFeeRs < 0L) {
                                        onBanner("Enter a valid non-negative Express delivery fee in Rs.")
                                        return@Button
                                    }
                                    val parsedExpressHours = expressHours.trim().toIntOrNull()
                                    if (parsedExpressHours == null) {
                                        onBanner("Enter valid Express delivery target hours (1–6).")
                                        return@Button
                                    }
                                    val parsedLeadDays = refillReminderLeadDays.trim().toIntOrNull()
                                    if (parsedLeadDays == null) {
                                        onBanner("Enter valid refill reminder lead days (0–14).")
                                        return@Button
                                    }
                                    val newSettings = currentSettings.copy(
                                        holdTtlMinutes = holdTtl.toIntOrNull() ?: currentSettings.holdTtlMinutes,
                                        hubAcceptanceWindowMins = acceptanceWindow.toIntOrNull() ?: currentSettings.hubAcceptanceWindowMins,
                                        riderAcceptanceWindowMins = riderAcceptanceWindow.toIntOrNull() ?: currentSettings.riderAcceptanceWindowMins,
                                        promisedHandoverMinutes = promisedHandoverSla.toIntOrNull() ?: currentSettings.promisedHandoverMinutes,
                                        cashHandoverHours = cashHandoverHours.toIntOrNull() ?: currentSettings.cashHandoverHours,
                                        evidenceRetentionDays = evidenceRetentionDays.toIntOrNull() ?: currentSettings.evidenceRetentionDays,
                                        maxQuantityPerOrder = maxQtyPerOrder.toIntOrNull() ?: currentSettings.maxQuantityPerOrder,
                                        refillModel = refillModelEnabled,
                                        phase2PreviewEnabled = phase2PreviewEnabled,
                                        cnicRequiredForDeposit = cnicRequiredForDeposit,
                                        backupCylinderAllowed = backupCylinderAllowed,
                                        maxDepositCylindersPerSize = parsedMaxDeposits,
                                        asapEnabled = asapEnabled,
                                        tomorrowWindowsEnabled = tomorrowWindowsEnabled,
                                        deliveryWindowsHours = parsedWindows,
                                        standardCutoffHour = parsedCutoff,
                                        standardSameDayByHour = parsedSameDayBy,
                                        notHomeWaitMinutes = parsedWaitMinutes,
                                        freeRedeliveries = parsedFreeRedeliveries,
                                        expressEnabled = expressEnabled,
                                        expressFeePaisa = parsedExpressFeeRs * 100L,
                                        expressHours = parsedExpressHours,
                                        refillReminderEnabled = refillReminderEnabled,
                                        refillReminderLeadDays = parsedLeadDays
                                    )
                                    val (ok, msg) = store.updateSettings(newSettings, "System Administrator")
                                    onBanner(msg)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("admin_save_parameters_button")
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

private fun parseDeliveryWindowsInput(raw: String): List<Pair<Int, Int>>? {
    val parts = raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.isEmpty()) return null
    val result = mutableListOf<Pair<Int, Int>>()
    for (part in parts) {
        val cleaned = part.replace(":00", "").replace('–', '-')
        val tokens = cleaned.split("-").map { it.trim().toIntOrNull() }
        if (tokens.size != 2 || tokens[0] == null || tokens[1] == null) return null
        result.add(tokens[0]!! to tokens[1]!!)
    }
    return result
}

