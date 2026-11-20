package com.example.bluegas.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.customer.RecommendedPill
import com.example.ui.theme.BlueGasPrimary
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.abs

private enum class PlantTab(val label: String) {
    PLANT_REPORT("Plant report"),
    FILL_PLAN("Fill plan"),
    FILL_TEST("Fill & test"),
    TRANSFERS("Transfers"),
    EMPTIES_FROM_HUBS("Empties from hubs"),
    REGISTRY("Cylinder registry"),
    OGRA_LIST("OGRA serial list"),
    STOCK_AUDIT("Stock audit"),
    RECONCILIATION("Reconciliation")
}

/**
 * Staff Plant & Inventory workspace (To-Be P1, P2; BRD §8.1–8.3, §8.12, §13, §21.4).
 * Uses the imported plant daily report as the record for bulk LPG and provides next-day fill
 * planning, cylinder fill/re-test recording, oldest-fill-first plant dispatch, hub empty intake,
 * cylinder registry, OGRA serial CSV export, physical audits, and daily reconciliation.
 *
 * @param store Shared in-memory store instance.
 * @param snapshot Immutable state snapshot keyed for recomposition.
 * @param onBanner Callback to surface operation result messages.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StaffPlantInventoryScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    val todayKarachi = remember(snapshot.version) {
        Instant.ofEpochMilli(store.clock()).atZone(ZoneId.of("Asia/Karachi")).toLocalDate().toString()
    }
    var selectedTab by remember { mutableStateOf(PlantTab.PLANT_REPORT) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("plant_inventory_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AccountableHeader(
                roleName = "${AccountableRole.PLANT.displayName} & ${AccountableRole.INVENTORY.displayName}",
                description = "The plant system stays the record for bulk LPG and the tank balance. BLUE GAS imports the daily plant report (D5, Q21)."
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                PlantTab.entries.forEach { tab ->
                    FilterChip(
                        selected = selectedTab == tab,
                        onClick = { selectedTab = tab },
                        label = { Text(tab.label, fontSize = 11.sp, maxLines = 1) },
                        modifier = Modifier.testTag("plant_tab_${tab.name.lowercase()}")
                    )
                }
            }
        }

        when (selectedTab) {
            PlantTab.PLANT_REPORT -> item {
                PlantReportTabContent(store = store, snapshot = snapshot, today = todayKarachi, onBanner = onBanner)
            }
            PlantTab.FILL_PLAN -> item {
                FillPlanTabContent(store = store, snapshot = snapshot, onBanner = onBanner)
            }
            PlantTab.FILL_TEST -> item {
                FillAndTestTabContent(store = store, snapshot = snapshot, today = todayKarachi, onBanner = onBanner)
            }
            PlantTab.TRANSFERS -> item {
                PlantTransfersTabContent(store = store, snapshot = snapshot, today = todayKarachi, onBanner = onBanner)
            }
            PlantTab.EMPTIES_FROM_HUBS -> item {
                EmptiesFromHubsTabContent(store = store, snapshot = snapshot, onBanner = onBanner)
            }
            PlantTab.REGISTRY -> item {
                CylinderRegistryTabContent(snapshot = snapshot, today = todayKarachi)
            }
            PlantTab.OGRA_LIST -> item {
                OgraSerialListTabContent(store = store, snapshot = snapshot, onBanner = onBanner)
            }
            PlantTab.STOCK_AUDIT -> item {
                StockAuditTabContent(store = store, snapshot = snapshot, onBanner = onBanner)
            }
            PlantTab.RECONCILIATION -> item {
                ReconciliationTabContent(store = store, snapshot = snapshot, onBanner = onBanner)
            }
        }
    }
}

@Composable
private fun PlantEmptyNotice(text: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = text,
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            modifier = Modifier.padding(14.dp)
        )
    }
}

/**
 * Plant report tab displaying latest imported/entered [PlantDailyReport] records and actions to
 * import a demo file or enter a balanced daily report manually.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlantReportTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    today: String,
    onBanner: (String) -> Unit
) {
    var showManualDialog by remember { mutableStateOf(false) }
    val reports = remember(snapshot.version) { snapshot.plantReports }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Accountable: ${AccountableRole.PLANT.displayName}",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Button(
                onClick = { onBanner(store.importPlantFileDemo().second) },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier.heightIn(min = 48.dp).testTag("plant_import_demo_btn")
            ) {
                Text("Import plant file (demo)", fontSize = 11.sp)
            }
            OutlinedButton(
                onClick = { showManualDialog = true },
                modifier = Modifier.heightIn(min = 48.dp).testTag("plant_manual_report_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Enter report manually", fontSize = 11.sp)
            }
        }

        if (reports.isEmpty()) {
            PlantEmptyNotice("No daily plant reports recorded yet.")
        } else {
            reports.forEachIndexed { idx, rpt ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (idx == 0) BlueGasPrimary else Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (idx == 0) "Latest report · ${rpt.date}" else "Report · ${rpt.date}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                            Surface(color = Color(0xFFF1F5F9), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = if (rpt.source == "FILE_IMPORT") "File import" else "Manual",
                                    fontSize = 10.sp,
                                    color = Color(0xFF334155),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "Opening: ${rpt.openingTonnes} t · Purchases: ${rpt.purchasesTonnes} t · Issued: ${rpt.issuedTonnes} t · Closing: ${rpt.closingTonnes} t",
                            fontSize = 11.sp,
                            color = Color(0xFF334155)
                        )
                        val cylSummary = if (rpt.cylindersIssuedBySku.isEmpty()) {
                            "None"
                        } else {
                            rpt.cylindersIssuedBySku.entries.joinToString(", ") { (sku, qty) ->
                                "${snapshot.skuLabel(sku)}: $qty"
                            }
                        }
                        Text("Cylinders issued: $cylSummary", fontSize = 11.sp, color = Color(0xFF475569))
                        Text(
                            text = "Gate passes: ${if (rpt.gatePassesToBlueGas.isEmpty()) "None" else rpt.gatePassesToBlueGas.joinToString(", ")}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }

    if (showManualDialog) {
        var dateInput by remember { mutableStateOf("") }
        var openingInput by remember { mutableStateOf("") }
        var purchasesInput by remember { mutableStateOf("") }
        var issuedInput by remember { mutableStateOf("") }
        var closingInput by remember { mutableStateOf("") }
        var cyl118Input by remember { mutableStateOf("") }
        var cyl454Input by remember { mutableStateOf("") }
        var gatePassesInput by remember { mutableStateOf("") }

        val dateValid = runCatching { LocalDate.parse(dateInput.trim()) }.isSuccess
        val opening = openingInput.trim().toDoubleOrNull()
        val purchases = purchasesInput.trim().toDoubleOrNull()
        val issued = issuedInput.trim().toDoubleOrNull()
        val closing = closingInput.trim().toDoubleOrNull()
        val numsValid = opening != null && opening >= 0 &&
            purchases != null && purchases >= 0 &&
            issued != null && issued >= 0 &&
            closing != null && closing >= 0
        val balanceMatches = numsValid && abs((opening!! + purchases!! - issued!!) - closing!!) < 0.00005
        val canSave = dateValid && balanceMatches

        AlertDialog(
            onDismissRequest = { showManualDialog = false },
            title = { Text("Enter daily plant report", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = dateInput,
                        onValueChange = { dateInput = it },
                        label = { Text("Report date (yyyy-MM-dd) *", fontSize = 11.sp) },
                        placeholder = { Text(today, color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = openingInput,
                        onValueChange = { openingInput = it },
                        label = { Text("Opening tonnes *", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 47.8442", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = purchasesInput,
                        onValueChange = { purchasesInput = it },
                        label = { Text("Purchases tonnes *", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 10.0000", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = issuedInput,
                        onValueChange = { issuedInput = it },
                        label = { Text("Issued tonnes *", fontSize = 11.sp) },
                        placeholder = { Text("e.g. 1.9170", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = closingInput,
                        onValueChange = { closingInput = it },
                        label = { Text("Closing tonnes *", fontSize = 11.sp) },
                        placeholder = { Text("Must equal opening + purchases − issued", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    if (numsValid && !balanceMatches) {
                        Text(
                            text = "Closing must equal opening + purchases − issued.",
                            fontSize = 10.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                    OutlinedTextField(
                        value = cyl118Input,
                        onValueChange = { cyl118Input = it.filter { ch -> ch.isDigit() } },
                        label = { Text("11.8 kg cylinders issued", fontSize = 11.sp) },
                        placeholder = { Text("Optional count", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = cyl454Input,
                        onValueChange = { cyl454Input = it.filter { ch -> ch.isDigit() } },
                        label = { Text("45.4 kg cylinders issued", fontSize = 11.sp) },
                        placeholder = { Text("Optional count", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = gatePassesInput,
                        onValueChange = { gatePassesInput = it },
                        label = { Text("Gate passes", fontSize = 11.sp) },
                        placeholder = { Text("Comma-separated gate pass numbers", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSave,
                    onClick = {
                        val issuedMap = buildMap {
                            val c118 = cyl118Input.trim().toIntOrNull() ?: 0
                            val c454 = cyl454Input.trim().toIntOrNull() ?: 0
                            if (c118 > 0) put("SKU-LPG-11-8", c118)
                            if (c454 > 0) put("SKU-LPG-45-4", c454)
                        }
                        val gps = gatePassesInput.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                        val report = PlantDailyReport(
                            date = dateInput.trim(),
                            openingTonnes = opening!!,
                            purchasesTonnes = purchases!!,
                            issuedTonnes = issued!!,
                            closingTonnes = closing!!,
                            cylindersIssuedBySku = issuedMap,
                            gatePassesToBlueGas = gps,
                            source = "MANUAL"
                        )
                        val (_, msg) = store.savePlantDailyReport(report, AccountableRole.PLANT.displayName)
                        onBanner(msg)
                        showManualDialog = false
                    }
                ) {
                    Text("Save report")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showManualDialog = false }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Fill plan tab allowing proposing tomorrow's hub replenishment plan, reviewing calculated line
 * figures, editing approved quantities per hub and cylinder size, and approving into transfer requests.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillPlanTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    val plans = remember(snapshot.version) { snapshot.fillPlans }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            RecommendedPill(modifier = Modifier.align(Alignment.CenterVertically))
            Button(
                onClick = { onBanner(store.proposeFillPlan().second) },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier.heightIn(min = 48.dp).testTag("propose_fill_plan_btn")
            ) {
                Text("Propose tomorrow's plan", fontSize = 11.sp)
            }
        }
        Text(
            text = "Built from hub stock, open orders and hub minimums (To-Be P1) · Accountable: ${AccountableRole.PLANT.displayName}",
            fontSize = 11.sp,
            color = Color(0xFF475569)
        )

        if (plans.isEmpty()) {
            PlantEmptyNotice("No fill plans proposed yet.")
        } else {
            plans.forEach { plan ->
                val qtyEdits = remember(plan.id, snapshot.version) {
                    mutableStateMapOf<String, String>().apply {
                        plan.lines.forEach { l ->
                            put("${l.pointId}|${l.sku}", l.approvedQty.toString())
                        }
                    }
                }
                val isProposed = plan.status == "PROPOSED"
                val allQuantitiesValid = plan.lines.all { l ->
                    val raw = qtyEdits["${l.pointId}|${l.sku}"]?.trim()
                    val v = raw?.toIntOrNull()
                    v != null && v >= 0
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${plan.id} · For ${plan.forDate}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = Color(0xFF0F172A)
                            )
                            Surface(
                                color = if (isProposed) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = if (isProposed) "Proposed" else "Approved",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isProposed) Color(0xFF92400E) else Color(0xFF166534),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        plan.lines.forEach { line ->
                            val key = "${line.pointId}|${line.sku}"
                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${snapshot.pointName(line.pointId)} · ${snapshot.skuLabel(line.sku)}",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Filled ${line.filled} · Reserved ${line.reserved} · In transit ${line.inTransit} · Min ${line.minimum} · Proposed ${line.proposedQty}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569)
                                    )
                                    if (isProposed) {
                                        OutlinedTextField(
                                            value = qtyEdits[key] ?: "",
                                            onValueChange = { qtyEdits[key] = it.filter { ch -> ch.isDigit() } },
                                            label = { Text("Approve qty", fontSize = 10.sp) },
                                            placeholder = { Text("0", color = Color.Gray) },
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                            singleLine = true,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    } else {
                                        Text(
                                            text = "Approved qty: ${line.approvedQty}",
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = Color(0xFF166534)
                                        )
                                    }
                                }
                            }
                        }

                        if (isProposed) {
                            Button(
                                enabled = allQuantitiesValid,
                                onClick = {
                                    val approvedMap = plan.lines.associate { l ->
                                        val key = "${l.pointId}|${l.sku}"
                                        key to (qtyEdits[key]?.trim()?.toIntOrNull() ?: l.proposedQty)
                                    }
                                    val (_, msg) = store.approveFillPlan(
                                        planId = plan.id,
                                        approved = approvedMap,
                                        actor = AccountableRole.PLANT.displayName
                                    )
                                    onBanner(msg)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .testTag("approve_fill_plan_btn_${plan.id}")
                            ) {
                                Text("Approve plan", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Fill & test tab managing empty cylinders at the plant (`EMPTY_AT_PLANT`) and cylinders sent
 * for safety re-testing (`SENT_TO_RETEST`).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FillAndTestTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    today: String,
    onBanner: (String) -> Unit
) {
    val plantCylinders = remember(snapshot.version) {
        snapshot.cylinders.filter {
            it.custody == CylinderCustody.PLANT && it.status != InventoryState.INV_WRITEOFF
        }
    }
    val emptyAtPlant = remember(plantCylinders) {
        plantCylinders.filter { it.plantStage == "EMPTY_AT_PLANT" }
    }
    val sentToRetest = remember(plantCylinders) {
        plantCylinders.filter { it.plantStage == "SENT_TO_RETEST" }
    }

    var fillTarget by remember { mutableStateOf<Cylinder?>(null) }
    var passTarget by remember { mutableStateOf<Cylinder?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Accountable: ${AccountableRole.PLANT.displayName}",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )

        Text("Empty at plant (${emptyAtPlant.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (emptyAtPlant.isEmpty()) {
            PlantEmptyNotice("No empty cylinders waiting for fill at the plant.")
        } else {
            emptyAtPlant.forEach { cyl ->
                val isTestDue = cyl.nextTestDueDate != null && cyl.nextTestDueDate <= today
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (isTestDue) Color(0xFFF59E0B) else Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("${cyl.serialNumber} · ${cyl.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Test due: ${cyl.nextTestDueDate ?: "—"}", fontSize = 10.sp, color = Color(0xFF64748B))
                        }
                        if (isTestDue) {
                            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = "Test due — send to re-testing",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = { fillTarget = cyl },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Record fill", fontSize = 11.sp)
                            }
                            if (isTestDue) {
                                OutlinedButton(
                                    onClick = {
                                        val (_, msg) = store.plantSendToRetest(
                                            cyl.serialNumber,
                                            AccountableRole.PLANT.displayName
                                        )
                                        onBanner(msg)
                                    },
                                    modifier = Modifier.heightIn(min = 48.dp)
                                ) {
                                    Text("Send to re-test", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("Sent to re-test (${sentToRetest.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (sentToRetest.isEmpty()) {
            PlantEmptyNotice("No cylinders currently sent to safety re-testing.")
        } else {
            sentToRetest.forEach { cyl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${cyl.serialNumber} · ${cyl.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Last due: ${cyl.nextTestDueDate ?: "—"}", fontSize = 10.sp, color = Color(0xFF64748B))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = { passTarget = cyl },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Pass", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    val (_, msg) = store.plantRecordRetest(
                                        serial = cyl.serialNumber,
                                        passed = false,
                                        nextTestDueDate = null,
                                        actor = AccountableRole.PLANT.displayName
                                    )
                                    onBanner(msg)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Fail", fontSize = 11.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }

    val targetFill = fillTarget
    if (targetFill != null) {
        var weightInput by remember(targetFill.serialNumber) { mutableStateOf("") }
        var reasonInput by remember(targetFill.serialNumber) { mutableStateOf("") }
        val parsedWeight = weightInput.trim().toDoubleOrNull()
        val outsideTolerance = parsedWeight != null && abs(parsedWeight - targetFill.weightKg) > 0.2
        val canSubmit = parsedWeight != null && parsedWeight > 0.0 && (!outsideTolerance || reasonInput.isNotBlank())

        AlertDialog(
            onDismissRequest = { fillTarget = null },
            title = { Text("Record fill · ${targetFill.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Nominal weight: ${targetFill.weightKg} kg (±0.2 kg without variance reason).", fontSize = 11.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = weightInput,
                        onValueChange = { weightInput = it },
                        label = { Text("Measured weight (kg) *", fontSize = 11.sp) },
                        placeholder = { Text("e.g. ${targetFill.weightKg}", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = {
                            Text(if (outsideTolerance) "Variance reason *" else "Variance reason (optional)", fontSize = 11.sp)
                        },
                        placeholder = { Text("Required if weight differs by more than 0.2 kg", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSubmit,
                    onClick = {
                        val (_, msg) = store.plantRecordFill(
                            serial = targetFill.serialNumber,
                            weightKg = parsedWeight!!,
                            reason = reasonInput.trim().takeIf { it.isNotEmpty() },
                            actor = AccountableRole.PLANT.displayName
                        )
                        onBanner(msg)
                        fillTarget = null
                    }
                ) {
                    Text("Confirm fill")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { fillTarget = null }) { Text("Cancel") }
            }
        )
    }

    val targetPass = passTarget
    if (targetPass != null) {
        var nextDueInput by remember(targetPass.serialNumber) { mutableStateOf("") }
        val todayLocal = remember(today) { LocalDate.parse(today) }
        val parsedDate = runCatching { LocalDate.parse(nextDueInput.trim()) }.getOrNull()
        val isValidFuture = parsedDate != null && parsedDate.isAfter(todayLocal)

        AlertDialog(
            onDismissRequest = { passTarget = null },
            title = { Text("Pass safety re-test · ${targetPass.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter the new future test due date after today ($today).", fontSize = 11.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = nextDueInput,
                        onValueChange = { nextDueInput = it },
                        label = { Text("Next test due date (yyyy-MM-dd) *", fontSize = 11.sp) },
                        placeholder = { Text("yyyy-MM-dd", color = Color.Gray) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = isValidFuture,
                    onClick = {
                        val (_, msg) = store.plantRecordRetest(
                            serial = targetPass.serialNumber,
                            passed = true,
                            nextTestDueDate = nextDueInput.trim(),
                            actor = AccountableRole.PLANT.displayName
                        )
                        onBanner(msg)
                        passTarget = null
                    }
                ) {
                    Text("Record pass")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { passTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Transfers tab displaying stock transfers with source chips (`Hub request` / `Fill plan` / `Urgent`)
 * and oldest-fill-first cylinder scanning in the gate pass dispatch dialog.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlantTransfersTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    today: String,
    onBanner: (String) -> Unit
) {
    var dispatchTarget by remember { mutableStateOf<StockTransfer?>(null) }
    val transfers = remember(snapshot.version) { snapshot.stockTransfers }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Stock transfers (${transfers.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp
        )
        if (transfers.isEmpty()) {
            PlantEmptyNotice("No stock transfers recorded.")
        } else {
            transfers.forEach { trf ->
                val sourceLabel = when (trf.source) {
                    "FILL_PLAN" -> "Fill plan"
                    "URGENT" -> "Urgent"
                    else -> "Hub request"
                }
                val sourceBg = when (trf.source) {
                    "URGENT" -> Color(0xFFFEE2E2)
                    "FILL_PLAN" -> Color(0xFFDBEAFE)
                    else -> Color(0xFFF1F5F9)
                }
                val sourceFg = when (trf.source) {
                    "URGENT" -> Color(0xFFB91C1C)
                    "FILL_PLAN" -> Color(0xFF1D4ED8)
                    else -> Color(0xFF334155)
                }
                val dirLabel = if (trf.direction == "EMPTIES_TO_PLANT") "Empties to plant" else "Plant to hub"

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        FlowRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "${trf.id} · $dirLabel",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                modifier = Modifier.align(Alignment.CenterVertically)
                            )
                            Surface(color = sourceBg, shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = sourceLabel,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = sourceFg,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                            Surface(
                                color = when (trf.status) {
                                    "REQUESTED" -> Color(0xFFFEF3C7)
                                    "DISPATCHED" -> Color(0xFFDBEAFE)
                                    "RECEIVED" -> Color(0xFFDCFCE7)
                                    else -> Color(0xFFFEE2E2)
                                },
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = trf.status.replace('_', ' ').lowercase().replaceFirstChar { it.uppercase() },
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        Text(
                            text = "${snapshot.pointName(trf.fromPointId)} -> ${snapshot.pointName(trf.toPointId)}",
                            fontSize = 11.sp,
                            color = Color(0xFF334155)
                        )
                        val linesDesc = trf.lines.joinToString(", ") {
                            "${snapshot.skuLabel(it.skuId)}: ${it.qtySent}"
                        }
                        Text("Lines: $linesDesc", fontSize = 11.sp, color = Color(0xFF475569))

                        if (trf.status == "REQUESTED" && trf.direction == "PLANT_TO_POINT") {
                            Button(
                                onClick = { dispatchTarget = trf },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Issue gate pass & dispatch", fontSize = 11.sp)
                            }
                        } else if (trf.status == "DISPATCHED" && trf.direction == "PLANT_TO_POINT") {
                            Text(
                                text = "Gate pass: ${trf.gatePassNo ?: "—"} · Driver: ${trf.driverName ?: "—"}",
                                fontSize = 10.sp,
                                color = Color(0xFF64748B)
                            )
                            Button(
                                onClick = {
                                    val recMap = trf.lines.associate { it.skuId to it.qtySent }
                                    val (_, msg) = store.confirmTransferReceipt(
                                        transferId = trf.id,
                                        received = recMap,
                                        actor = AccountableRole.HUB.displayName
                                    )
                                    onBanner(msg)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Receive at destination", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    val targetTrf = dispatchTarget
    if (targetTrf != null) {
        var gatePassInput by remember(targetTrf.id) { mutableStateOf("") }
        var vehicleInput by remember(targetTrf.id) { mutableStateOf("") }
        var driverInput by remember(targetTrf.id) { mutableStateOf("") }
        var phoneInput by remember(targetTrf.id) { mutableStateOf("") }
        val selectedSerials = remember(targetTrf.id) { mutableStateListOf<String>() }

        val neededSkus = targetTrf.lines.map { it.skuId }.toSet()
        val plantEligible = remember(snapshot.version, targetTrf.id) {
            snapshot.cylinders
                .filter { c ->
                    c.custody == CylinderCustody.PLANT &&
                        c.status == InventoryState.INV_AVAIL &&
                        !c.hseHold &&
                        c.sku in neededSkus &&
                        (c.nextTestDueDate == null || c.nextTestDueDate > today)
                }
                .sortedWith(compareBy<Cylinder> { it.lastFillDate ?: "9999-99-99" }.thenBy { it.serialNumber })
        }
        val canConfirm = gatePassInput.isNotBlank() && vehicleInput.isNotBlank() && driverInput.isNotBlank()

        AlertDialog(
            onDismissRequest = { dispatchTarget = null },
            title = { Text("Gate pass & dispatch · ${targetTrf.id}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        Text("Destination: ${snapshot.pointName(targetTrf.toPointId)}", fontSize = 11.sp, color = Color(0xFF475569))
                        OutlinedTextField(
                            value = gatePassInput,
                            onValueChange = { gatePassInput = it },
                            label = { Text("Gate pass number *", fontSize = 11.sp) },
                            placeholder = { Text("Enter gate pass number", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = vehicleInput,
                            onValueChange = { vehicleInput = it },
                            label = { Text("Vehicle registration *", fontSize = 11.sp) },
                            placeholder = { Text("Enter vehicle registration", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = driverInput,
                            onValueChange = { driverInput = it },
                            label = { Text("Driver name *", fontSize = 11.sp) },
                            placeholder = { Text("Enter driver name", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = phoneInput,
                            onValueChange = { phoneInput = it },
                            label = { Text("Driver phone (optional)", fontSize = 11.sp) },
                            placeholder = { Text("Optional phone number", color = Color.Gray) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "Scan plant cylinders onto vehicle (oldest fill first):",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 11.sp
                        )
                    }
                    if (plantEligible.isEmpty()) {
                        item {
                            Text("No filled eligible cylinders currently at plant.", fontSize = 11.sp, color = Color(0xFF64748B))
                        }
                    } else {
                        items(plantEligible) { cyl ->
                            val isChecked = cyl.serialNumber in selectedSerials
                            Surface(
                                color = if (isChecked) Color(0xFFEFF6FF) else Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, if (isChecked) BlueGasPrimary else Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        if (isChecked) selectedSerials.remove(cyl.serialNumber)
                                        else selectedSerials.add(cyl.serialNumber)
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                                        Checkbox(
                                            checked = isChecked,
                                            onCheckedChange = { checked ->
                                                if (checked && cyl.serialNumber !in selectedSerials) {
                                                    selectedSerials.add(cyl.serialNumber)
                                                } else if (!checked) {
                                                    selectedSerials.remove(cyl.serialNumber)
                                                }
                                            }
                                        )
                                        Column {
                                            Text("${cyl.serialNumber} (${cyl.weightKg} kg)", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                            Text("Filled: ${cyl.lastFillDate ?: "—"}", fontSize = 10.sp, color = Color(0xFF64748B))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = canConfirm,
                    onClick = {
                        val (_, msg) = store.recordPlantDispatch(
                            transferId = targetTrf.id,
                            gatePassNo = gatePassInput.trim(),
                            vehicleNo = vehicleInput.trim(),
                            driverName = driverInput.trim(),
                            driverPhone = phoneInput.trim(),
                            cylinderIds = selectedSerials.toList(),
                            actor = AccountableRole.PLANT.displayName
                        )
                        onBanner(msg)
                        dispatchTarget = null
                    }
                ) {
                    Text("Issue gate pass")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { dispatchTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Empties from hubs tab: confirms incoming hub-to-plant empty returns (with editable per-size
 * received quantities and shortfall warning) and records plant empty dispositions (Refill/Re-test/Scrap).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptiesFromHubsTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    val incomingEmpties = remember(snapshot.version) {
        snapshot.stockTransfers.filter { it.direction == "EMPTIES_TO_PLANT" && it.status == "DISPATCHED" }
    }
    val returnedEmpties = remember(snapshot.version) {
        snapshot.cylinders.filter {
            it.custody == CylinderCustody.PLANT &&
                it.plantStage == "EMPTY_AT_PLANT" &&
                it.status != InventoryState.INV_WRITEOFF
        }
    }
    var scrapTarget by remember { mutableStateOf<Cylinder?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = "Accountable: ${AccountableRole.PLANT.displayName}",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
        Text("Incoming empty transfers (${incomingEmpties.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (incomingEmpties.isEmpty()) {
            PlantEmptyNotice("No empty cylinder returns currently in transit from hubs.")
        } else {
            incomingEmpties.forEach { trf ->
                val receivedEdits = remember(trf.id, snapshot.version) {
                    mutableStateMapOf<String, String>().apply {
                        trf.lines.forEach { put(it.skuId, it.qtySent.toString()) }
                    }
                }
                val parsedMap = trf.lines.associate { line ->
                    line.skuId to receivedEdits[line.skuId]?.trim()?.toIntOrNull()
                }
                val allValid = trf.lines.all { line ->
                    val rec = parsedMap[line.skuId]
                    rec != null && rec in 0..line.qtySent
                }
                val hasShortfall = trf.lines.any { line ->
                    val rec = parsedMap[line.skuId]
                    rec != null && rec < line.qtySent
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "${trf.id} · From ${snapshot.pointName(trf.fromPointId)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                        Text(
                            text = "Gate pass: ${trf.gatePassNo ?: "—"} · Driver: ${trf.driverName ?: "—"}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                        trf.lines.forEach { line ->
                            OutlinedTextField(
                                value = receivedEdits[line.skuId] ?: "",
                                onValueChange = { receivedEdits[line.skuId] = it.filter { ch -> ch.isDigit() } },
                                label = { Text("${snapshot.skuLabel(line.skuId)} received (sent ${line.qtySent})", fontSize = 11.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                        if (hasShortfall) {
                            Surface(color = Color(0xFFFEF3C7), shape = RoundedCornerShape(4.dp)) {
                                Row(
                                    modifier = Modifier.padding(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFB45309), modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Shortfall detected — receiving fewer than sent logs shortage variance (EXC-18).",
                                        fontSize = 10.sp,
                                        color = Color(0xFF92400E)
                                    )
                                }
                            }
                        }
                        Button(
                            enabled = allValid,
                            onClick = {
                                val finalMap = trf.lines.associate { it.skuId to (parsedMap[it.skuId] ?: it.qtySent) }
                                val (_, msg) = store.confirmEmptiesReceivedAtPlant(
                                    transferId = trf.id,
                                    actor = AccountableRole.PLANT.displayName,
                                    received = finalMap
                                )
                                onBanner(msg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Confirm received at plant", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        Text("Tagged empties at plant (${returnedEmpties.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (returnedEmpties.isEmpty()) {
            PlantEmptyNotice("No tagged empty cylinders waiting for disposition at plant.")
        } else {
            returnedEmpties.forEach { cyl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("${cyl.serialNumber} · ${cyl.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Next test due: ${cyl.nextTestDueDate ?: "—"}", fontSize = 10.sp, color = Color(0xFF64748B))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Button(
                                onClick = {
                                    onBanner(store.plantDecideEmpty(cyl.serialNumber, "REFILL", "", AccountableRole.PLANT.displayName).second)
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Refill", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    onBanner(store.plantDecideEmpty(cyl.serialNumber, "RETEST", "", AccountableRole.PLANT.displayName).second)
                                },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Re-test", fontSize = 11.sp)
                            }
                            OutlinedButton(
                                onClick = { scrapTarget = cyl },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Scrap", fontSize = 11.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }
                }
            }
        }
    }

    val targetScrap = scrapTarget
    if (targetScrap != null) {
        var reasonInput by remember(targetScrap.serialNumber) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { scrapTarget = null },
            title = { Text("Scrap cylinder · ${targetScrap.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Scrapping writes off the cylinder permanently.", fontSize = 11.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Reason *", fontSize = 11.sp) },
                        placeholder = { Text("Enter reason for scrapping", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = reasonInput.isNotBlank(),
                    onClick = {
                        val (_, msg) = store.plantDecideEmpty(
                            serial = targetScrap.serialNumber,
                            decision = "SCRAP",
                            reason = reasonInput.trim(),
                            actor = AccountableRole.PLANT.displayName
                        )
                        onBanner(msg)
                        scrapTarget = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm scrap")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { scrapTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Filterable cylinder registry showing every BLUE GAS cylinder's status, custody, ownership,
 * last fill date/weight, and next safety test date.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CylinderRegistryTabContent(
    snapshot: SharedAppState,
    today: String
) {
    var locationFilter by remember { mutableStateOf("ALL") }
    var statusFilter by remember { mutableStateOf<InventoryState?>(null) }
    var testDueOnly by remember { mutableStateOf(false) }

    val filtered = remember(snapshot.version, locationFilter, statusFilter, testDueOnly, today) {
        snapshot.cylinders.filter { cyl ->
            val locOk = when (locationFilter) {
                "PLANT" -> cyl.custody == CylinderCustody.PLANT
                "HUB" -> cyl.custody == CylinderCustody.HUB
                "RIDER" -> cyl.custody == CylinderCustody.RIDER_VAN
                "CUSTOMER" -> cyl.custody == CylinderCustody.CUSTOMER_PREMISES
                else -> true
            }
            val statusOk = statusFilter == null || cyl.status == statusFilter
            val dueOk = !testDueOnly || (cyl.nextTestDueDate != null && cyl.nextTestDueDate <= today)
            locOk && statusOk && dueOk
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Location filter", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("ALL" to "All", "PLANT" to "Plant", "HUB" to "Hub", "RIDER" to "With rider", "CUSTOMER" to "With customer").forEach { (id, label) ->
                FilterChip(
                    selected = locationFilter == id,
                    onClick = { locationFilter = id },
                    label = { Text(label, fontSize = 10.sp) }
                )
            }
        }
        Text("Status & safety test filter", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            FilterChip(
                selected = statusFilter == null,
                onClick = { statusFilter = null },
                label = { Text("All statuses", fontSize = 10.sp) }
            )
            InventoryState.entries.forEach { state ->
                FilterChip(
                    selected = statusFilter == state,
                    onClick = { statusFilter = if (statusFilter == state) null else state },
                    label = { Text(state.label, fontSize = 10.sp) }
                )
            }
            FilterChip(
                selected = testDueOnly,
                onClick = { testDueOnly = !testDueOnly },
                label = { Text("Test due", fontSize = 10.sp) }
            )
        }

        if (filtered.isEmpty()) {
            PlantEmptyNotice("No cylinders match the selected filters.")
        } else {
            filtered.forEach { cyl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("${cyl.serialNumber} · ${cyl.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = "Status: ${cyl.status.label} · Custody: ${cyl.custody.label} · Ownership: ${cyl.ownership?.label ?: "Company pool"}",
                            fontSize = 10.sp,
                            color = Color(0xFF334155)
                        )
                        Text(
                            text = "Filled: ${cyl.lastFillDate ?: "—"} (${cyl.lastFillWeightKg?.let { "$it kg" } ?: "—"}) · Next test: ${cyl.nextTestDueDate ?: "—"}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Yearly OGRA serial list tab showing read-only BLUE GAS cylinder rows and a CSV clipboard copy action.
 */
@Composable
private fun OgraSerialListTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val blueGasCylinders = remember(snapshot.version) {
        snapshot.cylinders.filter { it.brand.equals("BLUE GAS", ignoreCase = true) }.distinctBy { it.serialNumber }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Yearly serial list for OGRA (LPG Policy 2016, cl. 3.3.7)",
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            color = Color(0xFF0F172A)
        )
        Button(
            onClick = {
                clipboard.setText(AnnotatedString(store.ograSerialListCsv()))
                onBanner("OGRA serial list copied as CSV.")
            },
            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
            modifier = Modifier.heightIn(min = 48.dp).testTag("ogra_copy_csv_btn")
        ) {
            Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
            Spacer(modifier = Modifier.width(6.dp))
            Text("Copy as CSV", fontSize = 11.sp)
        }

        if (blueGasCylinders.isEmpty()) {
            PlantEmptyNotice("No BLUE GAS cylinders registered.")
        } else {
            blueGasCylinders.forEach { cyl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("${cyl.serialNumber} · ${cyl.weightKg} kg", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Status: ${cyl.status.label}", fontSize = 10.sp, color = Color(0xFF475569))
                        }
                        Text("Due ${cyl.nextTestDueDate ?: "—"}", fontSize = 10.sp, color = Color(0xFF64748B))
                    }
                }
            }
        }
    }
}

/**
 * Physical stock audit tab restricted to household sizes (11.8 kg and 45.4 kg).
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun StockAuditTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var showAuditDialog by remember { mutableStateOf(false) }
    var reconcileTarget by remember { mutableStateOf<DailyReconciliation?>(null) }

    val audits = remember(snapshot.version) {
        snapshot.reconciliations.filter { it.id.startsWith("AUDIT-") }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = "Physical stock audits (${audits.size})",
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
            Button(
                onClick = { showAuditDialog = true },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier.heightIn(min = 48.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Conduct count", fontSize = 11.sp)
            }
        }

        if (audits.isEmpty()) {
            PlantEmptyNotice("No physical stock audits recorded yet.")
        } else {
            audits.forEach { audit ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("${audit.id} · ${audit.date}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        audit.lines.forEach { line ->
                            Text(
                                text = "${snapshot.skuLabel(line.skuId ?: "")}: system ${line.expected}, counted ${line.actual} (diff ${line.difference})",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                        }
                        if (audit.status == "DIFFERENCES_FOUND") {
                            Button(
                                onClick = { reconcileTarget = audit },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Reconcile & acknowledge", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAuditDialog) {
        var pointId by remember { mutableStateOf("PLANT-01") }
        var qty118 by remember { mutableStateOf("") }
        var qty454 by remember { mutableStateOf("") }
        val c118 = qty118.trim().toIntOrNull()
        val c454 = qty454.trim().toIntOrNull()
        val canSubmit = c118 != null && c118 >= 0 && c454 != null && c454 >= 0

        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Physical inventory count", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        snapshot.nodes.forEach { node ->
                            FilterChip(
                                selected = pointId == node.id,
                                onClick = { pointId = node.id },
                                label = { Text(node.name, fontSize = 10.sp) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = qty118,
                        onValueChange = { qty118 = it.filter { ch -> ch.isDigit() } },
                        label = { Text("11.8 kg full count *", fontSize = 11.sp) },
                        placeholder = { Text("Enter counted 11.8 kg cylinders", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = qty454,
                        onValueChange = { qty454 = it.filter { ch -> ch.isDigit() } },
                        label = { Text("45.4 kg full count *", fontSize = 11.sp) },
                        placeholder = { Text("Enter counted 45.4 kg cylinders", color = Color.Gray) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = canSubmit,
                    onClick = {
                        val map = mapOf("SKU-LPG-11-8" to c118!!, "SKU-LPG-45-4" to c454!!)
                        val (_, msg) = store.submitPhysicalStockAudit(pointId, map, AccountableRole.INVENTORY.displayName)
                        onBanner(msg)
                        showAuditDialog = false
                    }
                ) {
                    Text("Submit count")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAuditDialog = false }) { Text("Cancel") }
            }
        )
    }

    val recAudit = reconcileTarget
    if (recAudit != null) {
        var noteInput by remember(recAudit.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { reconcileTarget = null },
            title = { Text("Acknowledge audit variance", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                OutlinedTextField(
                    value = noteInput,
                    onValueChange = { noteInput = it },
                    label = { Text("Variance note *", fontSize = 11.sp) },
                    placeholder = { Text("Enter acknowledgement note", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    enabled = noteInput.isNotBlank(),
                    onClick = {
                        val (_, msg) = store.reconcileStockAudit(
                            reconId = recAudit.id,
                            notes = noteInput.trim(),
                            actor = AccountableRole.INVENTORY.displayName
                        )
                        onBanner(msg)
                        reconcileTarget = null
                    }
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { reconcileTarget = null }) { Text("Cancel") }
            }
        )
    }
}

/**
 * Daily reconciliation tab running 6-area daily checks and resolving open reconciliation
 * exceptions (`EXC-18`, `EXC-19`, `EXC-20`, `EXC-22`, `EXC-25`, `EXC-26`, `EXC-28`).
 */
@Composable
private fun ReconciliationTabContent(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    val latestDaily = remember(snapshot.version) {
        snapshot.reconciliations.firstOrNull { !it.id.startsWith("AUDIT-") }
    }
    val exceptions = remember(snapshot.version) { snapshot.reconExceptions }
    var resolveTarget by remember { mutableStateOf<ReconciliationException?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Accountable: ${AccountableRole.INVENTORY.displayName}",
            fontSize = 11.sp,
            color = Color(0xFF475569),
            fontWeight = FontWeight.Medium
        )
        Button(
            onClick = {
                val (_, msg) = store.runDailyReconciliation(emptyMap(), AccountableRole.INVENTORY.displayName)
                onBanner(msg)
            },
            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
            modifier = Modifier.heightIn(min = 48.dp).testTag("run_daily_recon_btn")
        ) {
            Text("Run daily reconciliation", fontSize = 11.sp)
        }

        Text("Latest reconciliation by area", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (latestDaily == null) {
            PlantEmptyNotice("No daily reconciliation has been run yet.")
        } else {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${latestDaily.id} · ${latestDaily.date}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = if (latestDaily.status == "MATCHED") "Matched" else "Differences found",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (latestDaily.status == "MATCHED") Color(0xFF166534) else Color(0xFFB45309)
                        )
                    }
                    latestDaily.lines.forEach { line ->
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(line.area, fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                                Text(
                                    text = "Expected: ${line.expected} · Actual: ${line.actual} · Diff: ${line.difference}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                                if (!line.note.isNullOrBlank()) {
                                    Text(line.note, fontSize = 10.sp, color = Color(0xFF64748B))
                                }
                            }
                        }
                    }
                }
            }
        }

        Text("Reconciliation exceptions (${exceptions.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        if (exceptions.isEmpty()) {
            PlantEmptyNotice("No open or resolved reconciliation exceptions.")
        } else {
            exceptions.forEach { exc ->
                val isOpen = exc.status == "OPEN"
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = BorderStroke(1.dp, if (isOpen) Color(0xFFF59E0B) else Color(0xFFCBD5E1)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(exc.code, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = if (isOpen) "Open" else "Resolved",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isOpen) Color(0xFFB45309) else Color(0xFF166534)
                            )
                        }
                        Text(exc.description, fontSize = 11.sp, color = Color(0xFF334155))
                        Text("Owner role: ${exc.ownerRole.displayName}", fontSize = 10.sp, color = Color(0xFF64748B))
                        if (!exc.resolution.isNullOrBlank()) {
                            Text("Resolution: ${exc.resolution}", fontSize = 10.sp, color = Color(0xFF166534))
                        }
                        if (isOpen) {
                            OutlinedButton(
                                onClick = { resolveTarget = exc },
                                modifier = Modifier.heightIn(min = 48.dp)
                            ) {
                                Text("Resolve", fontSize = 11.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    val targetExc = resolveTarget
    if (targetExc != null) {
        var reasonInput by remember(targetExc.id) { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { resolveTarget = null },
            title = { Text("Resolve ${targetExc.code}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(targetExc.description, fontSize = 11.sp, color = Color(0xFF475569))
                    OutlinedTextField(
                        value = reasonInput,
                        onValueChange = { reasonInput = it },
                        label = { Text("Resolution reason *", fontSize = 11.sp) },
                        placeholder = { Text("Enter resolution explanation", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = reasonInput.isNotBlank(),
                    onClick = {
                        val (_, msg) = store.resolveReconException(
                            exceptionId = targetExc.id,
                            note = reasonInput.trim(),
                            actor = targetExc.ownerRole.displayName
                        )
                        onBanner(msg)
                        resolveTarget = null
                    }
                ) {
                    Text("Confirm resolve")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { resolveTarget = null }) { Text("Cancel") }
            }
        )
    }
}
