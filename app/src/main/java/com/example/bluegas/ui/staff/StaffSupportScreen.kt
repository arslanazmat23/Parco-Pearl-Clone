package com.example.bluegas.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

/**
 * Customer Support workspace covering customer complaints and L6 Return & Refund cases
 * (including cylinder deposit return requests, 8-step lifecycle progress, and blank-input triage).
 *
 * @param store Shared in-memory store instance.
 * @param snapshot Immutable state snapshot for rendering.
 * @param onBanner Banner callback reporting success or error feedback.
 * @param onSelectOrder Callback to open the order detail dialog.
 */
@Composable
fun StaffSupportScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    var selectedSupportTab by remember { mutableStateOf("COMPLAINTS") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Resolve Complaint Dialog state (blank inputs)
    var showResolveDialog by remember { mutableStateOf(false) }
    var caseToResolve by remember { mutableStateOf<SupportCase?>(null) }
    var resolutionNotesInput by remember { mutableStateOf("") }

    // New Complaint Dialog state (blank description)
    var showNewCaseDialog by remember { mutableStateOf(false) }
    var newCaseOrderId by remember(snapshot.version) { mutableStateOf(snapshot.orders.firstOrNull()?.id ?: "") }
    var newCaseIssueType by remember { mutableStateOf("LEAKAGE_SAFETY") }
    var newCaseDescription by remember { mutableStateOf("") }

    // Open Return/Refund Case Dialog state (blank inputs)
    val completedOrders = remember(snapshot.version) {
        snapshot.orders.filter { it.status == OrderStatus.ORD_COMP }
    }
    var showNewReturnCaseDialog by remember { mutableStateOf(false) }
    var returnOrderId by remember(snapshot.version) { mutableStateOf(completedOrders.firstOrNull()?.id ?: "") }
    var returnTypeInput by remember { mutableStateOf("CYLINDER_RETURN") }
    var returnCustomerId by remember(snapshot.version) {
        mutableStateOf(snapshot.customers.firstOrNull()?.id ?: "")
    }
    var returnHoldingSerial by remember { mutableStateOf("") }
    var returnReasonInput by remember { mutableStateOf("") }
    var requestedRefundAmountInput by remember { mutableStateOf("") }

    val filteredComplaints = remember(snapshot.version, selectedFilter) {
        when (selectedFilter) {
            "OPEN" -> snapshot.supportCases.filter { it.status == "OPEN" }
            "IN_PROGRESS" -> snapshot.supportCases.filter { it.status == "IN_PROGRESS" }
            "RESOLVED" -> snapshot.supportCases.filter { it.status == "RESOLVED" }
            else -> snapshot.supportCases
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("support_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        item {
            AccountableHeader(
                roleName = AccountableRole.SUPPORT.displayName,
                description = "Dispute Cases, L6 Cylinder Returns & Deposit Refund Support"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(6.dp),
                border = BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rule: Completed orders stay Completed. Post-completion returns and deposit refunds are tracked through L6 Return/Refund cases.",
                    fontSize = 10.sp,
                    color = Color(0xFF1E40AF),
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "COMPLAINTS" to "Complaints (${snapshot.supportCases.size})",
                    "RETURN_CASES" to "Return & deposit cases (${snapshot.returnCases.size})"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedSupportTab == id,
                        onClick = { selectedSupportTab = id },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.testTag("support_tab_${id.lowercase()}")
                    )
                }
            }
        }

        when (selectedSupportTab) {
            "COMPLAINTS" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(listOf("ALL", "OPEN", "IN_PROGRESS", "RESOLVED")) { tab ->
                                FilterChip(
                                    selected = selectedFilter == tab,
                                    onClick = { selectedFilter = tab },
                                    label = { Text(tab, fontSize = 9.sp) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Button(
                            onClick = {
                                newCaseDescription = ""
                                showNewCaseDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier.heightIn(min = 48.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New complaint", fontSize = 10.sp)
                        }
                    }

                    if (filteredComplaints.isEmpty()) {
                        Text("No complaints found.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    }
                }

                items(filteredComplaints, key = { it.id }) { c ->
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Case ${c.id} · ${c.issueType}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Surface(
                                    color = when (c.status) {
                                        "OPEN" -> Color(0xFFFEE2E2)
                                        "IN_PROGRESS" -> Color(0xFFFEF3C7)
                                        else -> Color(0xFFDCFCE7)
                                    },
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = c.status,
                                        fontSize = 9.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = when (c.status) {
                                            "OPEN" -> Color(0xFF991B1B)
                                            "IN_PROGRESS" -> Color(0xFF92400E)
                                            else -> Color(0xFF166534)
                                        },
                                        modifier = Modifier.padding(4.dp, 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Order: ${c.orderId?.let { snapshot.orderLabel(it) } ?: "General"} (${snapshot.customerName(c.customerId)})",
                                fontSize = 10.sp,
                                color = Color(0xFF1E40AF)
                            )
                            Text(c.description, fontSize = 11.sp)

                            if (c.status == "RESOLVED" && c.resolutionNotes != null) {
                                Text(
                                    text = "Resolution: ${c.resolutionNotes}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (c.status == "OPEN") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            onBanner.report(
                                                store.startComplaint(c.id, AccountableRole.SUPPORT.displayName)
                                            )
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text("Start review", fontSize = 11.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            caseToResolve = c
                                            resolutionNotesInput = ""
                                            showResolveDialog = true
                                        },
                                        modifier = Modifier
                                            .weight(1f)
                                            .heightIn(min = 48.dp)
                                    ) {
                                        Text("Resolve", fontSize = 11.sp)
                                    }
                                }
                            } else if (c.status == "IN_PROGRESS") {
                                Spacer(modifier = Modifier.height(4.dp))
                                Button(
                                    onClick = {
                                        caseToResolve = c
                                        resolutionNotesInput = ""
                                        showResolveDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                ) {
                                    Text("Mark resolved", fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            }

            "RETURN_CASES" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("L6 Return & deposit cases (${snapshot.returnCases.size})", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = {
                                returnReasonInput = ""
                                requestedRefundAmountInput = ""
                                returnHoldingSerial = ""
                                showNewReturnCaseDialog = true
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp),
                            modifier = Modifier
                                .heightIn(min = 48.dp)
                                .testTag("support_open_case_btn")
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open case", fontSize = 11.sp)
                        }
                    }
                }

                if (snapshot.returnCases.isEmpty()) {
                    item {
                        Text("No L6 return or refund cases on record.", fontSize = 11.sp, color = Color.Gray, modifier = Modifier.padding(vertical = 8.dp))
                    }
                } else {
                    items(snapshot.returnCases, key = { it.id }) { rc ->
                        SupportReturnCaseCard(
                            rc = rc,
                            store = store,
                            snapshot = snapshot,
                            onBanner = onBanner,
                            onSelectOrder = onSelectOrder
                        )
                    }
                }
            }
        }
    }

    // Resolve Case Dialog
    if (showResolveDialog && caseToResolve != null) {
        AlertDialog(
            onDismissRequest = {
                showResolveDialog = false
                caseToResolve = null
            },
            title = { Text("Resolve Support Case", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Enter resolution notes for Case ${caseToResolve?.id}:", fontSize = 12.sp)
                    OutlinedTextField(
                        value = resolutionNotesInput,
                        onValueChange = { resolutionNotesInput = it },
                        label = { Text("Resolution notes") },
                        placeholder = { Text("Explain how the issue was resolved", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (resolutionNotesInput.isBlank()) {
                            onBanner("Please enter resolution notes before resolving.", true)
                            return@Button
                        }
                        onBanner.report(
                            store.resolveSupportCase(
                                caseToResolve!!.id,
                                resolutionNotesInput.trim(),
                                AccountableRole.SUPPORT.displayName
                            )
                        )
                        showResolveDialog = false
                        caseToResolve = null
                    }
                ) {
                    Text("Confirm resolution")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showResolveDialog = false
                    caseToResolve = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Support Case Dialog
    if (showNewCaseDialog) {
        AlertDialog(
            onDismissRequest = { showNewCaseDialog = false },
            title = { Text("Raise new complaint case", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Select order:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(snapshot.orders.take(6)) { ord ->
                            FilterChip(
                                selected = newCaseOrderId == ord.id,
                                onClick = { newCaseOrderId = ord.id },
                                label = { Text(snapshot.orderLabel(ord.id), fontSize = 9.sp) }
                            )
                        }
                    }

                    Text("Issue type:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(listOf("LEAKAGE_SAFETY", "DELAYED_DELIVERY", "OVERCHARGING", "CYLINDER_DENT")) { type ->
                            FilterChip(
                                selected = newCaseIssueType == type,
                                onClick = { newCaseIssueType = type },
                                label = { Text(type, fontSize = 9.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = newCaseDescription,
                        onValueChange = { newCaseDescription = it },
                        label = { Text("Description") },
                        placeholder = { Text("Describe the issue reported", color = Color.Gray) },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newCaseOrderId.isBlank() || newCaseDescription.isBlank()) {
                            onBanner("Select an order and enter a description.", true)
                            return@Button
                        }
                        val res = store.raiseComplaint(newCaseOrderId, newCaseIssueType, newCaseDescription.trim())
                        onBanner.report(res)
                        if (res.first) showNewCaseDialog = false
                    }
                ) {
                    Text("Create case")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewCaseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open Return / Deposit Case Dialog
    if (showNewReturnCaseDialog) {
        val customerHoldings = remember(snapshot.version, returnCustomerId) {
            snapshot.customerHoldings.filter {
                it.customerId == returnCustomerId &&
                    (it.ownership == CylinderOwnership.DEPOSIT_HELD || it.ownership == CylinderOwnership.LOANED)
            }
        }
        AlertDialog(
            onDismissRequest = { showNewReturnCaseDialog = false },
            title = { Text("Open return / deposit case", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Case type:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(
                            listOf(
                                "CYLINDER_RETURN" to "Return my cylinder (deposit)",
                                "RETURN" to "Physical return + refund",
                                "REFUND_ONLY" to "Direct refund only"
                            )
                        ) { (type, lbl) ->
                            FilterChip(
                                selected = returnTypeInput == type,
                                onClick = { returnTypeInput = type },
                                label = { Text(lbl, fontSize = 10.sp) },
                                modifier = Modifier.testTag("support_case_type_${type.lowercase()}")
                            )
                        }
                    }

                    if (returnTypeInput == "CYLINDER_RETURN") {
                        Text("Customer:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            items(snapshot.customers) { cust ->
                                FilterChip(
                                    selected = returnCustomerId == cust.id,
                                    onClick = {
                                        returnCustomerId = cust.id
                                        returnHoldingSerial = ""
                                    },
                                    label = { Text(cust.name, fontSize = 10.sp) }
                                )
                            }
                        }

                        Text("Cylinder on deposit or loan:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        if (customerHoldings.isEmpty()) {
                            Text(
                                text = "This customer has no active cylinders on deposit or loan.",
                                fontSize = 11.sp,
                                color = Color(0xFF64748B)
                            )
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                customerHoldings.forEach { holding ->
                                    val sku = snapshot.cylinders.find { it.serialNumber == holding.cylinderSerial }?.sku
                                        ?: snapshot.depositAccounts.find { it.cylinderSerial == holding.cylinderSerial && it.customerId == holding.customerId }?.sku
                                    val sizeLabel = if (sku != null) snapshot.skuLabel(sku) else "11.8 kg"
                                    val depText = if (holding.depositPaisa > 0L && !holding.noDepositOnRecord) {
                                        "Deposit ${formatRupees(holding.depositPaisa)}"
                                    } else {
                                        "No deposit on record"
                                    }
                                    val rowText = "${holding.cylinderSerial} · $sizeLabel · $depText"
                                    FilterChip(
                                        selected = returnHoldingSerial == holding.cylinderSerial,
                                        onClick = { returnHoldingSerial = holding.cylinderSerial },
                                        label = { Text(rowText, fontSize = 10.sp) },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .testTag("support_holding_${holding.cylinderSerial}")
                                    )
                                }
                            }
                        }
                    } else {
                        Text("Completed order:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        LazyRow(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(completedOrders) { ord ->
                                FilterChip(
                                    selected = returnOrderId == ord.id,
                                    onClick = { returnOrderId = ord.id },
                                    label = { Text(snapshot.orderLabel(ord.id), fontSize = 9.sp) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = requestedRefundAmountInput,
                            onValueChange = { requestedRefundAmountInput = it.filter { ch -> ch.isDigit() } },
                            label = { Text("Requested amount (Rs)") },
                            placeholder = { Text("Enter amount in rupees", color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    OutlinedTextField(
                        value = returnReasonInput,
                        onValueChange = { returnReasonInput = it },
                        label = { Text("Reason (required)") },
                        placeholder = { Text("Enter why the customer is requesting this", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("support_open_case_reason")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val cleanReason = returnReasonInput.trim()
                        if (cleanReason.isEmpty()) {
                            onBanner("Please enter a reason.", true)
                            return@Button
                        }
                        if (returnTypeInput == "CYLINDER_RETURN") {
                            if (returnCustomerId.isBlank() || returnHoldingSerial.isBlank()) {
                                onBanner("Select a customer and one of their cylinders.", true)
                                return@Button
                            }
                            val res = store.requestCylinderReturn(
                                customerId = returnCustomerId,
                                cylinderSerial = returnHoldingSerial,
                                reason = cleanReason,
                                actor = AccountableRole.SUPPORT.displayName
                            )
                            onBanner.report(res)
                            if (res.first) showNewReturnCaseDialog = false
                        } else {
                            val pkr = requestedRefundAmountInput.trim().toLongOrNull()
                            if (pkr == null || pkr <= 0L) {
                                onBanner("Enter a valid requested refund amount in rupees.", true)
                                return@Button
                            }
                            val res = store.openReturnCase(
                                orderId = returnOrderId,
                                type = returnTypeInput,
                                reason = cleanReason,
                                requestedAmountPaisa = pkr * 100L,
                                actor = AccountableRole.SUPPORT.displayName
                            )
                            onBanner.report(res)
                            if (res.first) showNewReturnCaseDialog = false
                        }
                    },
                    modifier = Modifier.testTag("support_submit_open_case_btn")
                ) {
                    Text("Open case")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewReturnCaseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

/**
 * Renders an L6 Return/Refund case card, displaying the 8-step stepper for cylinder returns,
 * deposit and condition details, blank required reason input, and role-scoped next actions.
 */
@Composable
private fun SupportReturnCaseCard(
    rc: ReturnRefundCase,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    val isCylinderReturn = rc.type == "CYLINDER_RETURN"
    val deposit = remember(snapshot.version, rc.depositId, rc.cylinderSerial, rc.customerId) {
        snapshot.depositAccounts.find { it.id == rc.depositId }
            ?: snapshot.depositAccounts.find { it.customerId == rc.customerId && it.cylinderSerial == rc.cylinderSerial }
    }
    val cyl = remember(snapshot.version, rc.cylinderSerial) {
        snapshot.cylinders.find { it.serialNumber == rc.cylinderSerial }
    }
    val sizeLabel = remember(snapshot.version, cyl?.sku, deposit?.sku) {
        val sku = cyl?.sku ?: deposit?.sku
        if (sku != null) snapshot.skuLabel(sku) else "11.8 kg"
    }
    val linkedRefund = remember(snapshot.version, rc.linkedRefundId, rc.id) {
        snapshot.refunds.find { it.id == rc.linkedRefundId || it.linkedCaseId == rc.id }
    }
    val hasNoDepositOnRecord = isCylinderReturn && (deposit == null || deposit.noDepositOnRecord || deposit.amountPaisa == 0L)

    var reasonInput by remember(rc.id) { mutableStateOf("") }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_return_card_${rc.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${rc.id} · ${if (isCylinderReturn) "Cylinder deposit return" else rc.type}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    modifier = Modifier.weight(1f)
                )
                Surface(
                    color = when (rc.status) {
                        L6Status.CASE_CLOSED, L6Status.REFUND_CONFIRMED -> Color(0xFFDCFCE7)
                        L6Status.RETURN_REJECTED -> Color(0xFFFEE2E2)
                        else -> Color(0xFFDBEAFE)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = rc.status.name,
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = when (rc.status) {
                            L6Status.CASE_CLOSED, L6Status.REFUND_CONFIRMED -> Color(0xFF166534)
                            L6Status.RETURN_REJECTED -> Color(0xFF991B1B)
                            else -> Color(0xFF1E40AF)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Text(
                text = "Customer: ${snapshot.customerName(rc.customerId)}" +
                    (rc.cylinderSerial?.let { " · Cylinder $it ($sizeLabel)" } ?: ""),
                fontSize = 11.sp,
                color = Color(0xFF0F172A)
            )
            if (rc.orderId.isNotBlank()) {
                val orderObj = snapshot.orders.find { it.id == rc.orderId }
                Text(
                    text = snapshot.orderLabel(rc.orderId),
                    fontSize = 11.sp,
                    color = BlueGasPrimary,
                    modifier = if (orderObj != null) Modifier else Modifier
                )
            }
            Text("Reason: ${rc.reason}", fontSize = 11.sp, color = Color(0xFF475569))

            if (isCylinderReturn) {
                val depositSummary = if (hasNoDepositOnRecord) {
                    "Deposit: No deposit on record"
                } else {
                    "Deposit: ${formatRupees(deposit!!.amountPaisa)} (${deposit.status.label})"
                }
                Text(depositSummary, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF0F172A))

                if (rc.deductionPaisa > 0L) {
                    Text(
                        text = "Deduction: ${formatRupees(rc.deductionPaisa)} (${rc.deductionReason ?: "Damage"})",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                }
                rc.conditionResult?.let { cond ->
                    Text(
                        text = "Hub check: ${cond.label}${rc.conditionNote?.let { " · $it" } ?: ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF334155)
                    )
                }

                CylinderReturnStepper(rc = rc)
            } else {
                Text(
                    text = "Requested refund: ${formatRupees(rc.requestedAmountPaisa)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }

            val rejectionText = rc.rejectionReason
                ?: rc.history.lastOrNull { it.toStatus == L6Status.RETURN_REJECTED }?.reason
            if (rc.status == L6Status.RETURN_REJECTED && !rejectionText.isNullOrBlank()) {
                Surface(
                    color = Color(0xFFFEE2E2),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "Rejected: $rejectionText",
                        fontSize = 11.sp,
                        color = Color(0xFF991B1B),
                        modifier = Modifier.padding(8.dp)
                    )
                }
            }

            if (isCylinderReturn) {
                when (rc.status) {
                    L6Status.CASE_OPENED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) {
                                    onBanner("Please enter a reason before starting review.", true)
                                } else {
                                    onBanner.report(
                                        store.moveCase(
                                            caseId = rc.id,
                                            target = L6Status.UNDER_REVIEW,
                                            reason = reasonInput.trim(),
                                            actor = AccountableRole.SUPPORT.displayName,
                                            role = AccountableRole.SUPPORT
                                        )
                                    )
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("support_start_review_${rc.id}")
                        ) {
                            Text("Start review", fontSize = 12.sp)
                        }
                    }

                    L6Status.UNDER_REVIEW -> {
                        if (hasNoDepositOnRecord) {
                            Surface(
                                color = Color(0xFFFFFBEB),
                                shape = RoundedCornerShape(6.dp),
                                border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "No deposit on record — record the proof you saw before authorizing (open decision Q30).",
                                    fontSize = 11.sp,
                                    color = Color(0xFF92400E),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    if (reasonInput.isBlank()) {
                                        onBanner("Enter a reason (and proof if applicable) to authorize.", true)
                                    } else {
                                        onBanner.report(
                                            store.moveCase(
                                                caseId = rc.id,
                                                target = L6Status.RETURN_AUTHORIZED,
                                                reason = reasonInput.trim(),
                                                actor = AccountableRole.SUPPORT.displayName,
                                                role = AccountableRole.SUPPORT
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("support_authorize_${rc.id}")
                            ) {
                                Text("Authorize", fontSize = 12.sp)
                            }
                            OutlinedButton(
                                onClick = {
                                    if (reasonInput.isBlank()) {
                                        onBanner("Enter a rejection reason.", true)
                                    } else {
                                        onBanner.report(
                                            store.moveCase(
                                                caseId = rc.id,
                                                target = L6Status.RETURN_REJECTED,
                                                reason = reasonInput.trim(),
                                                actor = AccountableRole.SUPPORT.displayName,
                                                role = AccountableRole.SUPPORT
                                            )
                                        )
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp)
                                    .testTag("support_reject_${rc.id}")
                            ) {
                                Text("Reject", fontSize = 12.sp, color = Color(0xFFDC2626))
                            }
                        }
                    }

                    L6Status.RETURN_AUTHORIZED -> {
                        SupportReadOnlyStatus("Waiting for Operations to book a rider")
                    }

                    L6Status.AWAITING_ITEM_RETURN -> {
                        val msg = when (rc.collectionTask) {
                            "AT_HUB" -> "Hub is checking"
                            "COLLECTED" -> "Rider is returning cylinder to hub"
                            "OFFERED", "ACCEPTED" -> "Rider is collecting"
                            else -> "Waiting for Operations to assign a rider"
                        }
                        SupportReadOnlyStatus(msg)
                    }

                    L6Status.ITEM_RETURNED -> {
                        SupportReadOnlyStatus("Finance decides the refund")
                    }

                    L6Status.REFUND_REQUESTED -> {
                        val failed = linkedRefund?.state == PaymentState.PARTIAL_REFUND_FAILED ||
                            linkedRefund?.state == PaymentState.FULL_REFUND_FAILED
                        SupportReadOnlyStatus(
                            message = if (failed) {
                                "Payment failed — Finance will retry (EXC-27)"
                            } else {
                                "Finance is paying the refund"
                            },
                            isWarning = failed
                        )
                    }

                    L6Status.REFUND_CONFIRMED, L6Status.RETURN_REJECTED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) {
                                    onBanner("Enter a closing reason.", true)
                                } else {
                                    onBanner.report(
                                        store.moveCase(
                                            caseId = rc.id,
                                            target = L6Status.CASE_CLOSED,
                                            reason = reasonInput.trim(),
                                            actor = AccountableRole.SUPPORT.displayName,
                                            role = AccountableRole.SUPPORT
                                        )
                                    )
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("support_close_case_${rc.id}")
                        ) {
                            Text("Close case", fontSize = 12.sp)
                        }
                    }

                    L6Status.CASE_CLOSED -> {
                        SupportReadOnlyStatus("Closed")
                    }
                }
            } else {
                when (rc.status) {
                    L6Status.CASE_OPENED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) {
                                    onBanner("Please enter a review reason.", true)
                                } else {
                                    onBanner.report(
                                        store.moveCase(rc.id, L6Status.UNDER_REVIEW, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT)
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) {
                            Text("Start review", fontSize = 11.sp)
                        }
                    }
                    L6Status.UNDER_REVIEW -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Button(
                                onClick = {
                                    if (reasonInput.isBlank()) onBanner("Enter a reason.", true)
                                    else onBanner.report(store.moveCase(rc.id, L6Status.RETURN_AUTHORIZED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                                },
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Authorize", fontSize = 10.sp) }
                            Button(
                                onClick = {
                                    if (reasonInput.isBlank()) onBanner("Enter a reason.", true)
                                    else onBanner.report(store.moveCase(rc.id, L6Status.REFUND_REQUESTED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp)
                            ) { Text("Refund", fontSize = 10.sp) }
                            OutlinedButton(
                                onClick = {
                                    if (reasonInput.isBlank()) onBanner("Enter a rejection reason.", true)
                                    else onBanner.report(store.moveCase(rc.id, L6Status.RETURN_REJECTED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                                },
                                modifier = Modifier.weight(0.9f).heightIn(min = 48.dp)
                            ) { Text("Reject", fontSize = 10.sp, color = Color(0xFFDC2626)) }
                        }
                    }
                    L6Status.RETURN_AUTHORIZED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) onBanner("Enter a reason.", true)
                                else onBanner.report(store.moveCase(rc.id, L6Status.AWAITING_ITEM_RETURN, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Mark awaiting return", fontSize = 11.sp) }
                    }
                    L6Status.AWAITING_ITEM_RETURN -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) onBanner("Enter inspection details.", true)
                                else onBanner.report(store.moveCase(rc.id, L6Status.ITEM_RETURNED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Confirm item received at hub", fontSize = 11.sp) }
                    }
                    L6Status.ITEM_RETURNED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) onBanner("Enter a reason.", true)
                                else onBanner.report(store.moveCase(rc.id, L6Status.REFUND_REQUESTED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Request Finance refund", fontSize = 11.sp) }
                    }
                    L6Status.REFUND_CONFIRMED, L6Status.RETURN_REJECTED -> {
                        SupportReasonInput(reasonInput, { reasonInput = it }, rc.id)
                        Button(
                            onClick = {
                                if (reasonInput.isBlank()) onBanner("Enter a closing reason.", true)
                                else onBanner.report(store.moveCase(rc.id, L6Status.CASE_CLOSED, reasonInput.trim(), actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT))
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)
                        ) { Text("Close case", fontSize = 11.sp) }
                    }
                    else -> {}
                }
            }
        }
    }
}

/**
 * Displays the 8-step cylinder return lifecycle stepper (Opened through Closed).
 */
@Composable
private fun CylinderReturnStepper(rc: ReturnRefundCase) {
    val steps = listOf(
        "Opened",
        "Review",
        "Collection booked",
        "Collected",
        "Checked at hub",
        "Refund requested",
        "Refund paid",
        "Closed"
    )
    val activeIdx = when (rc.status) {
        L6Status.CASE_OPENED -> 0
        L6Status.UNDER_REVIEW -> 1
        L6Status.RETURN_AUTHORIZED -> 2
        L6Status.AWAITING_ITEM_RETURN -> when (rc.collectionTask) {
            "COLLECTED", "AT_HUB" -> 3
            else -> 2
        }
        L6Status.ITEM_RETURNED -> 4
        L6Status.REFUND_REQUESTED -> 5
        L6Status.REFUND_CONFIRMED -> 6
        L6Status.CASE_CLOSED -> 7
        L6Status.RETURN_REJECTED -> -1
    }

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
    ) {
        items(steps.indices.toList()) { idx ->
            val completedOrCurrent = activeIdx >= 0 && idx <= activeIdx
            val isCurrent = activeIdx == idx
            val bg = when {
                isCurrent -> Color(0xFF1D4ED8)
                completedOrCurrent -> Color(0xFFDCFCE7)
                else -> Color(0xFFF1F5F9)
            }
            val fg = when {
                isCurrent -> Color.White
                completedOrCurrent -> Color(0xFF166534)
                else -> Color(0xFF64748B)
            }
            Surface(color = bg, shape = RoundedCornerShape(4.dp)) {
                Text(
                    text = "${idx + 1}. ${steps[idx]}",
                    fontSize = 9.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                    color = fg,
                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
                )
            }
        }
    }
}

/**
 * Blank-by-default required reason input field used on Support L6 action cards.
 */
@Composable
private fun SupportReasonInput(
    value: String,
    onValueChange: (String) -> Unit,
    caseId: String
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text("Reason (required)", fontSize = 11.sp) },
        placeholder = { Text("Enter reason or notes for this step", color = Color.Gray, fontSize = 11.sp) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("support_reason_input_$caseId")
    )
}

/**
 * Read-only status box showing which team owns the next action on a return case.
 */
@Composable
private fun SupportReadOnlyStatus(message: String, isWarning: Boolean = false) {
    Surface(
        color = if (isWarning) Color(0xFFFEF3C7) else Color(0xFFF1F5F9),
        shape = RoundedCornerShape(6.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isWarning) Color(0xFF92400E) else Color(0xFF334155),
            modifier = Modifier.padding(8.dp)
        )
    }
}
