package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
fun StaffPlantInventoryScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var selectedSectionTab by remember { mutableStateOf("TRANSFERS") }

    // Dispatch Dialog state
    var showDispatchDialog by remember { mutableStateOf(false) }
    var selectedTransferForDispatch by remember { mutableStateOf<StockTransfer?>(null) }
    var gatePassInput by remember { mutableStateOf("GP-LHR-26-001") }
    var vehicleInput by remember { mutableStateOf("LZB-9842 (Mazda Titan)") }
    var driverNameInput by remember { mutableStateOf("Muhammad Ramzan") }
    var driverPhoneInput by remember { mutableStateOf("0300-1234567") }

    // Bulk LPG Receipt Dialog state
    var showBulkReceiptDialog by remember { mutableStateOf(false) }
    var supplierInput by remember { mutableStateOf("PARCO Mid-Country Refinery") }
    var blNumberInput by remember { mutableStateOf("BL-PARCO-8821") }
    var tankerNumberInput by remember { mutableStateOf("LXP-5544 (LPG Bowzer)") }
    var netWeightTonnesInput by remember { mutableStateOf("25.5") }

    // Daily Plant Dip Dialog state
    var showDipDialog by remember { mutableStateOf(false) }
    var tankIdInput by remember { mutableStateOf("TANK-01 (Mounded Bullet)") }
    var liquidLevelCmInput by remember { mutableStateOf("240.5") }
    var pressureBarInput by remember { mutableStateOf("6.8") }
    var tempCInput by remember { mutableStateOf("28.0") }

    // Cylinder Retest / Scrap Dialog state
    var showRetestDialog by remember { mutableStateOf(false) }
    var testSerialInput by remember { mutableStateOf("BG-LHR-118-002") }
    var testCertInput by remember { mutableStateOf("HYD-OGRA-2026-99") }
    var nextRetestDateInput by remember { mutableStateOf("2031-10-15") }

    var showScrapDialog by remember { mutableStateOf(false) }
    var scrapSerialInput by remember { mutableStateOf("BG-LHR-U01") }
    var scrapCertInput by remember { mutableStateOf("SCRAP-DISP-01") }
    var scrapReasonInput by remember { mutableStateOf("Deep collar dent and pit corrosion exceeding OGRA limits") }

    // Physical Audit Dialog state
    var showAuditDialog by remember { mutableStateOf(false) }
    var auditPointId by remember { mutableStateOf("PLANT-01") }
    var audit118Qty by remember { mutableStateOf("12") }
    var audit110Qty by remember { mutableStateOf("8") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("plant_inventory_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Plant & Inventory Controller",
                description = "Primary Stock Transfers, Gate Passes, Bulk LPG Receipts, Hydro-testing & Audits"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Section Filter Tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "TRANSFERS" to "Transfers",
                    "BULK_LPG" to "Bulk Receipts",
                    "TANK_DIP" to "Tank Dips",
                    "MAINTENANCE" to "Hydro-Test",
                    "AUDITS" to "Physical Audits"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedSectionTab == id,
                        onClick = { selectedSectionTab = id },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(10.dp))
        }

        when (selectedSectionTab) {
            "TRANSFERS" -> {
                item {
                    Text("Stock Transfers & Gate Pass Dispatches (${snapshot.stockTransfers.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                }

                if (snapshot.stockTransfers.isEmpty()) {
                    item { Text("No active stock transfers.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.stockTransfers) { trf ->
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
                                    Text("Transfer ${trf.id} (${trf.direction})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (trf.status) {
                                            "REQUESTED" -> Color(0xFFFEF3C7)
                                            "DISPATCHED" -> Color(0xFFDBEAFE)
                                            "RECEIVED" -> Color(0xFFDCFCE7)
                                            else -> Color(0xFFF1F5F9)
                                        },
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = trf.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (trf.status) {
                                                "REQUESTED" -> Color(0xFF92400E)
                                                "DISPATCHED" -> Color(0xFF1E40AF)
                                                "RECEIVED" -> Color(0xFF166534)
                                                else -> Color.DarkGray
                                            },
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("From: ${trf.fromPointId} -> To: ${trf.toPointId}", fontSize = 11.sp)
                                val linesDesc = trf.lines.joinToString(", ") { "${it.skuId}: ${it.qtySent} full" }
                                Text("Lines: $linesDesc", fontSize = 10.sp, color = Color(0xFF475569))

                                if (trf.status == "REQUESTED" && trf.direction == "PLANT_TO_POINT") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            selectedTransferForDispatch = trf
                                            showDispatchDialog = true
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Issue Gate Pass & Dispatch", fontSize = 10.sp)
                                    }
                                } else if (trf.status == "DISPATCHED") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Gate Pass: ${trf.gatePassNo ?: "N/A"} • Driver: ${trf.driverName ?: "N/A"}", fontSize = 10.sp, color = Color.Gray)
                                        Button(
                                            onClick = {
                                                val receivedMap = trf.lines.associate { it.skuId to it.qtySent }
                                                val (ok, msg) = store.receiveStockAtPoint(trf.id, receivedMap, emptyMap(), null, "Inventory Controller")
                                                onBanner(msg)
                                            },
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text("Receive at Destination", fontSize = 10.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            "BULK_LPG" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Bulk LPG Decanting Receipts:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showBulkReceiptDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Log Bowzer Receipt", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.plantReports.none { it.notes?.contains("BULK_RECEIPT") == true }) {
                    item { Text("No recent bowzer bulk receipts logged.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.plantReports.filter { it.notes?.contains("BULK_RECEIPT") == true }) { rpt ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Receipt ID: ${rpt.id} • ${rpt.reportDate}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Bulk LPG Received: ${rpt.bulkLpgReceivedTonnes} Tonnes", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                Text("Notes: ${rpt.notes}", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }

            "TANK_DIP" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Plant Daily Storage Tank Dips:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showDipDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Record Tank Dip", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.plantReports.none { it.notes?.contains("DAILY_DIP") == true }) {
                    item { Text("No tank dip readings recorded today.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.plantReports.filter { it.notes?.contains("DAILY_DIP") == true }) { dip ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text("Dip Record: ${dip.id} • ${dip.reportDate}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Text("Calculated Tonnes: ${dip.bulkLpgClosingStockTonnes} T (Plant Opening: ${dip.bulkLpgOpeningStockTonnes} T)", fontSize = 11.sp)
                                Text(dip.notes ?: "", fontSize = 10.sp, color = Color(0xFF475569))
                            }
                        }
                    }
                }
            }

            "MAINTENANCE" -> {
                item {
                    Text("Cylinder Maintenance, Hydro-testing & Scrapping:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { showRetestDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Hydro-Test Recertify", fontSize = 10.sp)
                        }
                        Button(
                            onClick = { showScrapDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Condemn & Scrap", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))

                    Text("Sample Plant Stock Cylinders:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                }

                items(snapshot.cylinders.filter { it.custody == CylinderCustody.PLANT || it.custody == CylinderCustody.DISTRIBUTOR_NODE }.take(10)) { cyl ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 3.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("${cyl.serialNumber} (${cyl.sku})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                Text("Custody: ${cyl.custody.name} • Location: ${cyl.currentLocationLabel}", fontSize = 10.sp, color = Color.Gray)
                            }
                            if (!cyl.isTagged) {
                                Button(
                                    onClick = {
                                        val (ok, msg) = store.tagCylinder(cyl.serialNumber)
                                        onBanner(msg)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(26.dp)
                                ) {
                                    Text("Affix QR Tag", fontSize = 9.sp)
                                }
                            }
                        }
                    }
                }
            }

            "AUDITS" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Physical Stock Audits & Variances:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showAuditDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Conduct Count", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.reconciliations.isEmpty()) {
                    item { Text("No physical stock audits recorded.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.reconciliations) { recon ->
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
                                    Text("Audit: ${recon.id} • Node: ${recon.pointId}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = if (recon.status == "PENDING_APPROVAL") Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = recon.status,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = if (recon.status == "PENDING_APPROVAL") Color(0xFF92400E) else Color(0xFF166534),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text("Variance Paisa: ${formatPaisaToPkr(recon.totalVariancePaisa)} • Notes: ${recon.notes}", fontSize = 10.sp, color = Color(0xFF475569))

                                if (recon.status == "PENDING_APPROVAL") {
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Button(
                                        onClick = {
                                            val (ok, msg) = store.reconcileStockAudit(recon.id, "Approved variance adjustment", "Plant Manager")
                                            onBanner(msg)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text("Reconcile & Post to Ledger", fontSize = 10.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Gate Pass Dispatch Dialog
    if (showDispatchDialog && selectedTransferForDispatch != null) {
        val trf = selectedTransferForDispatch!!
        AlertDialog(
            onDismissRequest = {
                showDispatchDialog = false
                selectedTransferForDispatch = null
            },
            title = { Text("Plant Gate Pass & Dispatch", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Transfer ID: ${trf.id} -> ${trf.toPointId}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = gatePassInput,
                        onValueChange = { gatePassInput = it },
                        label = { Text("Gate Pass Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = vehicleInput,
                        onValueChange = { vehicleInput = it },
                        label = { Text("Vehicle Registration No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = driverNameInput,
                        onValueChange = { driverNameInput = it },
                        label = { Text("Driver Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = driverPhoneInput,
                        onValueChange = { driverPhoneInput = it },
                        label = { Text("Driver Contact Phone") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val availableSerials = snapshot.cylinders
                            .filter { it.status == InventoryState.INV_AVAIL && !it.hseHold }
                            .map { it.serialNumber }
                            .take(trf.lines.sumOf { it.qtySent })

                        val (ok, msg) = store.recordPlantDispatch(
                            transferId = trf.id,
                            gatePassNo = gatePassInput,
                            vehicleNo = vehicleInput,
                            driverName = driverNameInput,
                            driverPhone = driverPhoneInput,
                            cylinderIds = availableSerials,
                            cmrNo = "CMR-26-001",
                            filledBy = "Plant Operator Tariq",
                            actor = "Plant Manager"
                        )
                        onBanner(msg)
                        showDispatchDialog = false
                        selectedTransferForDispatch = null
                    }
                ) {
                    Text("Issue Gate Pass")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDispatchDialog = false
                    selectedTransferForDispatch = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Bulk Receipt Dialog
    if (showBulkReceiptDialog) {
        AlertDialog(
            onDismissRequest = { showBulkReceiptDialog = false },
            title = { Text("Log Bulk LPG Decanting", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = supplierInput,
                        onValueChange = { supplierInput = it },
                        label = { Text("Supplier Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = blNumberInput,
                        onValueChange = { blNumberInput = it },
                        label = { Text("Bill of Lading No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tankerNumberInput,
                        onValueChange = { tankerNumberInput = it },
                        label = { Text("Tanker Registration") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = netWeightTonnesInput,
                        onValueChange = { netWeightTonnesInput = it },
                        label = { Text("Net Weight (Metric Tonnes)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val tonnes = netWeightTonnesInput.toDoubleOrNull() ?: 25.0
                        val (ok, msg) = store.logBulkLpgReceipt(
                            supplier = supplierInput,
                            blNumber = blNumberInput,
                            tankerNumber = tankerNumberInput,
                            netWeightTonnes = tonnes,
                            specificGravity = 0.510,
                            density = 510.0,
                            temperatureC = 25.0,
                            actor = "Plant Manager"
                        )
                        onBanner(msg)
                        showBulkReceiptDialog = false
                    }
                ) {
                    Text("Confirm Decant")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showBulkReceiptDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Daily Dip Dialog
    if (showDipDialog) {
        AlertDialog(
            onDismissRequest = { showDipDialog = false },
            title = { Text("Record Daily Storage Tank Dip", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = tankIdInput,
                        onValueChange = { tankIdInput = it },
                        label = { Text("Tank Identification") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = liquidLevelCmInput,
                        onValueChange = { liquidLevelCmInput = it },
                        label = { Text("Liquid Level (cm)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = pressureBarInput,
                        onValueChange = { pressureBarInput = it },
                        label = { Text("Pressure Gauge (Bar)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = tempCInput,
                        onValueChange = { tempCInput = it },
                        label = { Text("Temperature (°C)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val lvl = liquidLevelCmInput.toDoubleOrNull() ?: 200.0
                        val bar = pressureBarInput.toDoubleOrNull() ?: 6.5
                        val temp = tempCInput.toDoubleOrNull() ?: 25.0
                        val (ok, msg) = store.recordDailyPlantDip(
                            tankId = tankIdInput,
                            liquidLevelCm = lvl,
                            pressureBar = bar,
                            temperatureC = temp,
                            calculatedTonnes = lvl * 0.15,
                            actor = "Plant Shift Supervisor"
                        )
                        onBanner(msg)
                        showDipDialog = false
                    }
                ) {
                    Text("Record Dip")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDipDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Retest Dialog
    if (showRetestDialog) {
        AlertDialog(
            onDismissRequest = { showRetestDialog = false },
            title = { Text("Cylinder Hydro-Test Certification", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = testSerialInput,
                        onValueChange = { testSerialInput = it },
                        label = { Text("Cylinder Serial Number") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = testCertInput,
                        onValueChange = { testCertInput = it },
                        label = { Text("OGRA Test Certificate No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = nextRetestDateInput,
                        onValueChange = { nextRetestDateInput = it },
                        label = { Text("Next Re-test Due Date (YYYY-MM-DD)") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.recordCylinderTesting(
                            serialNumber = testSerialInput,
                            nextRetestDate = nextRetestDateInput,
                            certificateNo = testCertInput,
                            testPressureBar = 30.0,
                            actor = "Certified HSE Tester"
                        )
                        onBanner(msg)
                        showRetestDialog = false
                    }
                ) {
                    Text("Certify Cylinder")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRetestDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Scrap Dialog
    if (showScrapDialog) {
        AlertDialog(
            onDismissRequest = { showScrapDialog = false },
            title = { Text("Condemn & Scrap Unsafe Cylinder", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = scrapSerialInput,
                        onValueChange = { scrapSerialInput = it },
                        label = { Text("Cylinder Serial") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = scrapCertInput,
                        onValueChange = { scrapCertInput = it },
                        label = { Text("Scrap Certificate No") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = scrapReasonInput,
                        onValueChange = { scrapReasonInput = it },
                        label = { Text("Condemnation Failure Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.recordCylinderScrap(
                            serialNumber = scrapSerialInput,
                            reason = scrapReasonInput,
                            scrapCertificateNo = scrapCertInput,
                            actor = "HSE Inspection Lead"
                        )
                        onBanner(msg)
                        showScrapDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Condemn & Scrap")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showScrapDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Physical Audit Dialog
    if (showAuditDialog) {
        AlertDialog(
            onDismissRequest = { showAuditDialog = false },
            title = { Text("Submit Physical Inventory Audit", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter physical count of full cylinders:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = audit118Qty,
                        onValueChange = { audit118Qty = it },
                        label = { Text("11.8kg Physical Count") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = audit110Qty,
                        onValueChange = { audit110Qty = it },
                        label = { Text("11.0kg Physical Count") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val c118 = audit118Qty.toIntOrNull() ?: 10
                        val c110 = audit110Qty.toIntOrNull() ?: 5
                        val map = mapOf("SKU-LPG-11-8" to c118, "SKU-LPG-11-0" to c110)
                        val (ok, msg) = store.submitPhysicalStockAudit(auditPointId, map, "Audit Controller")
                        onBanner(msg)
                        showAuditDialog = false
                    }
                ) {
                    Text("Submit Count")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showAuditDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
