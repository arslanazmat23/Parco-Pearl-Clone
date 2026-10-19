package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
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
fun StaffSupportScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    var selectedSupportTab by remember { mutableStateOf("COMPLAINTS") }
    var selectedFilter by remember { mutableStateOf("ALL") }

    // Resolve Complaint Dialog state
    var showResolveDialog by remember { mutableStateOf(false) }
    var caseToResolve by remember { mutableStateOf<SupportCase?>(null) }
    var resolutionNotesInput by remember { mutableStateOf("Inspected per OGRA safety guidelines. Resolved satisfactorily.") }

    // New Complaint Dialog state
    var showNewCaseDialog by remember { mutableStateOf(false) }
    var newCaseOrderId by remember { mutableStateOf(snapshot.orders.firstOrNull()?.id ?: "") }
    var newCaseIssueType by remember { mutableStateOf("LEAKAGE_SAFETY") }
    var newCaseDescription by remember { mutableStateOf("Customer reported suspect regulator seal after delivery.") }

    // Open Return/Refund Case Dialog state
    var showNewReturnCaseDialog by remember { mutableStateOf(false) }
    val completedOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_COMP }
    var returnOrderId by remember { mutableStateOf(completedOrders.firstOrNull()?.id ?: "") }
    var returnTypeInput by remember { mutableStateOf("RETURN") }
    var returnReasonInput by remember { mutableStateOf("Damaged valve seal discovered upon customer unboxing") }
    var requestedRefundAmountInput by remember { mutableStateOf("4450") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("support_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Customer Support Lead",
                description = "Dispute Cases, L6 Return/Refund Cases & Post-Completion Inquiry Tracking"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rule: Completed orders stay Completed (ORD_COMP). Post-completion disputes are tracked through immutable Support Cases and L6 Return/Refund Cases.",
                    fontSize = 10.sp,
                    color = Color(0xFF1E40AF),
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Sub-tabs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                listOf(
                    "COMPLAINTS" to "Complaints (${snapshot.supportCases.size})",
                    "RETURN_CASES" to "Return/Refund L6 (${snapshot.returnCases.size})"
                ).forEach { (id, label) ->
                    FilterChip(
                        selected = selectedSupportTab == id,
                        onClick = { selectedSupportTab = id },
                        label = { Text(label, fontSize = 10.sp) },
                        modifier = Modifier.height(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        when (selectedSupportTab) {
            "COMPLAINTS" -> {
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("ALL", "OPEN", "IN_PROGRESS", "RESOLVED").forEach { tab ->
                                FilterChip(
                                    selected = selectedFilter == tab,
                                    onClick = { selectedFilter = tab },
                                    label = { Text(tab, fontSize = 9.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                        Button(
                            onClick = { showNewCaseDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("New Complaint", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))

                    val filtered = when (selectedFilter) {
                        "OPEN" -> snapshot.supportCases.filter { it.status == "OPEN" }
                        "IN_PROGRESS" -> snapshot.supportCases.filter { it.status == "IN_PROGRESS" }
                        "RESOLVED" -> snapshot.supportCases.filter { it.status == "RESOLVED" }
                        else -> snapshot.supportCases
                    }

                    if (filtered.isEmpty()) {
                        Text("No complaints found.", fontSize = 11.sp, color = Color.Gray)
                    }
                }

                val filteredCases = when (selectedFilter) {
                    "OPEN" -> snapshot.supportCases.filter { it.status == "OPEN" }
                    "IN_PROGRESS" -> snapshot.supportCases.filter { it.status == "IN_PROGRESS" }
                    "RESOLVED" -> snapshot.supportCases.filter { it.status == "RESOLVED" }
                    else -> snapshot.supportCases
                }

                items(filteredCases) { c ->
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
                                Text("Case ${c.id} • ${c.issueType}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
                            Text("Order ID: ${c.orderId} (Customer: ${c.customerId})", fontSize = 10.sp, color = Color(0xFF1E40AF))
                            Text(c.description, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))

                            if (c.status == "RESOLVED" && c.resolutionNotes != null) {
                                Text(
                                    text = "Resolution: ${c.resolutionNotes}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF166534),
                                    fontWeight = FontWeight.SemiBold
                                )
                            }

                            if (c.status == "OPEN") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            val (ok, msg) = store.startComplaint(c.id, "Support Lead")
                                            onBanner(msg)
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Text("Start Investigation", fontSize = 10.sp)
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            caseToResolve = c
                                            showResolveDialog = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.weight(1f).height(30.dp)
                                    ) {
                                        Text("Resolve Directly", fontSize = 10.sp)
                                    }
                                }
                            } else if (c.status == "IN_PROGRESS") {
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        caseToResolve = c
                                        showResolveDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.fillMaxWidth().height(30.dp)
                                ) {
                                    Text("Mark Resolved", fontSize = 10.sp)
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
                        Text("L6 Return & Refund Cases (${snapshot.returnCases.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Button(
                            onClick = { showNewReturnCaseDialog = true },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Open Return Case", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (snapshot.returnCases.isEmpty()) {
                    item { Text("No L6 return or refund cases on record.", fontSize = 11.sp, color = Color.Gray) }
                } else {
                    items(snapshot.returnCases) { rc ->
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
                                    Text("Case ${rc.id} (${rc.type})", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(
                                        color = when (rc.status) {
                                            L6Status.CASE_CLOSED -> Color(0xFFDCFCE7)
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
                                                L6Status.CASE_CLOSED -> Color(0xFF166534)
                                                L6Status.RETURN_REJECTED -> Color(0xFF991B1B)
                                                else -> Color(0xFF1E40AF)
                                            },
                                            modifier = Modifier.padding(4.dp, 2.dp)
                                        )
                                    }
                                }
                                Text("Order: ${rc.orderId} • Claim Amount: ${formatPaisaToPkr(rc.requestedAmountPaisa)}", fontSize = 11.sp)
                                Text("Reason: ${rc.reason}", fontSize = 10.sp, color = Color(0xFF475569))

                                // Lifecycle Action Buttons
                                Spacer(modifier = Modifier.height(8.dp))
                                when (rc.status) {
                                    L6Status.CASE_OPENED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.UNDER_REVIEW, "Support team reviewing claim", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Begin Formal Review", fontSize = 10.sp)
                                        }
                                    }
                                    L6Status.UNDER_REVIEW -> {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.moveReturnCase(rc.id, L6Status.RETURN_AUTHORIZED, "Physical item return approved", "Support Lead", AccountableRole.SUPPORT)
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Authorize Return", fontSize = 9.sp)
                                            }
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.moveReturnCase(rc.id, L6Status.REFUND_REQUESTED, "Direct refund queued", "Support Lead", AccountableRole.SUPPORT)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier.weight(1f).height(30.dp)
                                            ) {
                                                Text("Direct Refund", fontSize = 9.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val (ok, msg) = store.moveReturnCase(rc.id, L6Status.RETURN_REJECTED, "Claim rejected upon review", "Support Lead", AccountableRole.SUPPORT)
                                                    onBanner(msg)
                                                },
                                                modifier = Modifier.weight(0.9f).height(30.dp)
                                            ) {
                                                Text("Reject", fontSize = 9.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                    L6Status.RETURN_AUTHORIZED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.AWAITING_ITEM_RETURN, "Pickup scheduled with rider", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Schedule Pickup (Awaiting Return)", fontSize = 10.sp)
                                        }
                                    }
                                    L6Status.AWAITING_ITEM_RETURN -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.ITEM_RETURNED, "Item received and inspected at depot", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Confirm Item Received at Depot", fontSize = 10.sp)
                                        }
                                    }
                                    L6Status.ITEM_RETURNED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.REFUND_REQUESTED, "Item verified; refund requested from Finance", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Request Finance Refund Queue", fontSize = 10.sp)
                                        }
                                    }
                                    L6Status.REFUND_CONFIRMED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.CASE_CLOSED, "Refund verified by customer; case closed", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Conclude & Close Case", fontSize = 10.sp)
                                        }
                                    }
                                    L6Status.RETURN_REJECTED -> {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.moveReturnCase(rc.id, L6Status.CASE_CLOSED, "Case concluded after rejection", "Support Lead", AccountableRole.SUPPORT)
                                                onBanner(msg)
                                            },
                                            modifier = Modifier.fillMaxWidth().height(30.dp)
                                        ) {
                                            Text("Close Rejected Record", fontSize = 10.sp)
                                        }
                                    }
                                    else -> {}
                                }
                            }
                        }
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
                Column {
                    Text("Enter resolution notes for Case ${caseToResolve?.id}:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = resolutionNotesInput,
                        onValueChange = { resolutionNotesInput = it },
                        label = { Text("Resolution Notes") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val (ok, msg) = store.resolveSupportCase(caseToResolve!!.id, resolutionNotesInput, "Support Lead")
                    onBanner(msg)
                    showResolveDialog = false
                    caseToResolve = null
                }) {
                    Text("Confirm Resolution")
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
            title = { Text("Raise New Complaint Case", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select Order to file dispute for:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(snapshot.orders.take(6)) { ord ->
                            FilterChip(
                                selected = newCaseOrderId == ord.id,
                                onClick = { newCaseOrderId = ord.id },
                                label = { Text(ord.id, fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Issue Type:", fontSize = 12.sp)
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

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newCaseDescription,
                        onValueChange = { newCaseDescription = it },
                        label = { Text("Description") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.raiseComplaint(newCaseOrderId, newCaseIssueType, newCaseDescription)
                        onBanner(msg)
                        showNewCaseDialog = false
                    },
                    enabled = newCaseOrderId.isNotEmpty() && newCaseDescription.isNotEmpty()
                ) {
                    Text("Create Case")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewCaseDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // New Return/Refund Case Dialog
    if (showNewReturnCaseDialog) {
        AlertDialog(
            onDismissRequest = { showNewReturnCaseDialog = false },
            title = { Text("Open Return / Refund Case (L6)", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select Completed Order:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(completedOrders) { ord ->
                            FilterChip(
                                selected = returnOrderId == ord.id,
                                onClick = { returnOrderId = ord.id },
                                label = { Text(ord.id, fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Claim Type:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("RETURN" to "Physical Return + Refund", "REFUND_ONLY" to "Direct Refund Only").forEach { (type, lbl) ->
                            FilterChip(
                                selected = returnTypeInput == type,
                                onClick = { returnTypeInput = type },
                                label = { Text(lbl, fontSize = 9.sp) }
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = requestedRefundAmountInput,
                        onValueChange = { requestedRefundAmountInput = it },
                        label = { Text("Requested Amount (PKR)") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = returnReasonInput,
                        onValueChange = { returnReasonInput = it },
                        label = { Text("Return Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val pkr = requestedRefundAmountInput.toLongOrNull() ?: 4450L
                        val (ok, msg) = store.openReturnCase(
                            orderId = returnOrderId,
                            type = returnTypeInput,
                            reason = returnReasonInput,
                            requestedAmountPaisa = pkr * 100L,
                            actor = "Support Lead"
                        )
                        onBanner(msg)
                        showNewReturnCaseDialog = false
                    },
                    enabled = returnOrderId.isNotBlank() && returnReasonInput.isNotBlank()
                ) {
                    Text("Open Case")
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
