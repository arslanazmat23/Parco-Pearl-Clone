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
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffOperationsScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    val exceptionOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_EXC }
    val pendingPartners = snapshot.partnerApplications.filter { it.status == "PENDING_APPROVAL" }

    var selectedPartnerTab by remember { mutableStateOf("PENDING") }
    var showApprovePartnerDialog by remember { mutableStateOf(false) }
    var showRejectPartnerDialog by remember { mutableStateOf(false) }
    var showReturnPartnerDialog by remember { mutableStateOf(false) }
    var selectedPartnerApp by remember { mutableStateOf<PartnerApplication?>(null) }
    var selectedZoneId by remember { mutableStateOf(snapshot.serviceZones.firstOrNull()?.id ?: "ZONE-LHR-GUL") }
    var selectedLinkedDistId by remember { mutableStateOf(snapshot.distributors.firstOrNull()?.id ?: "DIST-001") }
    var trainingCompleted by remember { mutableStateOf(true) }
    var rejectionReasonInput by remember { mutableStateOf("Incomplete documentation / verification failed") }
    var missingDocsInput by remember { mutableStateOf("Missing CNIC or driving license copy") }

    var showResolveConflictDialog by remember { mutableStateOf(false) }
    var selectedConflict by remember { mutableStateOf<SerialConflictItem?>(null) }
    var winningOrderId by remember { mutableStateOf("") }
    var conflictResolutionReason by remember { mutableStateOf("Verified physical cylinder barcode matches initial booking") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("operations_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Operations Lead",
                description = "Fulfilment Exceptions, Partner Onboarding, Dispatch Routing & Serial Collision Triage"
            )
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Serial Conflict Queue
        if (snapshot.opsConflictQueue.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .testTag("ops_conflict_queue_card")
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Ops Conflict Queue (${snapshot.opsConflictQueue.size} items)",
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFB91C1C),
                                fontSize = 13.sp
                            )
                        }
                        Text(
                            text = "Duplicate serial scans detected from offline devices. Human resolution required:",
                            fontSize = 11.sp
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        snapshot.opsConflictQueue.forEach { conf ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Conflicting Serial: ${conf.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Orders: ${conf.orderId1} vs ${conf.orderId2} • ${conf.reasonCode}", fontSize = 10.sp, color = Color(0xFF475569))
                                }
                                Button(
                                    onClick = {
                                        selectedConflict = conf
                                        winningOrderId = conf.orderId1
                                        showResolveConflictDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Resolve Conflict", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Partner Onboarding Queue (UC-PRT-02, Accountable: Operations)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("partner_onboarding_section")
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Partner Onboarding Queue (${pendingPartners.size} Pending)",
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color(0xFF0F172A)
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            listOf("PENDING", "APPROVED", "REJECTED", "ALL").forEach { tab ->
                                FilterChip(
                                    selected = selectedPartnerTab == tab,
                                    onClick = { selectedPartnerTab = tab },
                                    label = { Text(tab, fontSize = 9.sp) },
                                    modifier = Modifier.height(26.dp)
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    val displayPartners = when (selectedPartnerTab) {
                        "PENDING" -> snapshot.partnerApplications.filter { it.status == "PENDING_APPROVAL" }
                        "APPROVED" -> snapshot.partnerApplications.filter { it.status == "APPROVED" }
                        "REJECTED" -> snapshot.partnerApplications.filter { it.status == "REJECTED" }
                        else -> snapshot.partnerApplications
                    }

                    if (displayPartners.isEmpty()) {
                        Text(
                            text = if (selectedPartnerTab == "PENDING") "No pending partner onboarding applications." else "No partner applications found in category.",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(vertical = 4.dp)
                        )
                    } else {
                        displayPartners.forEach { app ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                            ) {
                                Column(modifier = Modifier.padding(8.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "${app.applicantName} (${app.type})",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                        Surface(
                                            color = when (app.status) {
                                                "APPROVED" -> Color(0xFFDCFCE7)
                                                "REJECTED" -> Color(0xFFFEE2E2)
                                                "INCOMPLETE" -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFDBEAFE)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = app.status,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = when (app.status) {
                                                    "APPROVED" -> Color(0xFF166534)
                                                    "REJECTED" -> Color(0xFF991B1B)
                                                    "INCOMPLETE" -> Color(0xFF92400E)
                                                    else -> Color(0xFF1E40AF)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "City: ${app.city} • CNIC: ${app.cnic} • Phone: ${app.phone}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569)
                                    )
                                    Text(
                                        text = "Details: ${app.vehicleOrShopDetails}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF64748B)
                                    )

                                    if (app.status == "PENDING_APPROVAL") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    selectedPartnerApp = app
                                                    showApprovePartnerDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1.2f)
                                                    .height(30.dp)
                                            ) {
                                                Text("Approve Partner", fontSize = 10.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    selectedPartnerApp = app
                                                    showReturnPartnerDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                            ) {
                                                Text("Return Incomplete", fontSize = 9.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    selectedPartnerApp = app
                                                    showRejectPartnerDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(0.9f)
                                                    .height(30.dp)
                                            ) {
                                                Text("Reject", fontSize = 9.sp, color = Color(0xFFDC2626))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fulfilment Exceptions Section
        item {
            Text("Active Fulfilment Exceptions (${exceptionOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (exceptionOrders.isEmpty()) {
            item {
                Text("No active fulfilment exceptions.", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            items(exceptionOrders) { ord ->
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
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Exception on ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            CanonicalStatusChip(ord.status)
                        }
                        Text("Reason: ${ord.exceptionReason?.description}", fontWeight = FontWeight.SemiBold, fontSize = 11.sp, color = Color(0xFFDC2626))
                        Text("Details: ${ord.exceptionDetails}", fontSize = 10.sp, color = Color(0xFF475569))

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Constrained Recovery Actions (OrderStateMachine):", fontWeight = FontWeight.Bold, fontSize = 10.sp)
                        Spacer(modifier = Modifier.height(4.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            val reason = ord.exceptionReason ?: ExceptionReason.UNRECOVERABLE_AFTER_RETRIES
                            when (reason) {
                                ExceptionReason.VALIDATION_OUT_OF_AREA -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_PAYPEND)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("To Payment Pending", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_PREP)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Recover to Preparing", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                ExceptionReason.PAYMENT_ISSUE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_PAYPEND)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Retry Payment Pending", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE, ExceptionReason.NO_ACCEPTANCE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_ASGN)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Reassign to Assigned", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                ExceptionReason.ADJUSTED_AMOUNT_REJECTION -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_ADJ)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Recover to Adjustment", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_FCLO)
                                            onBanner(m)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Fail Closed (Terminal)", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f),
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                            }
                        }
                    }
                }
            }
        }

        // All system orders overview
        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text("All Platform Orders (${snapshot.orders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(snapshot.orders) { ord ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        store.selectedOrderIdForDrawer = ord.id
                        store.notifyChanged()
                        onSelectOrder(ord)
                    }
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(
                            text = "${ord.customerId} • ${formatPaisaToPkr(ord.totalAmountPaisa)} • Serial: ${ord.allocatedSerial ?: "None"}",
                            fontSize = 10.sp,
                            color = Color.Gray
                        )
                    }
                    CanonicalStatusChip(ord.status)
                }
            }
        }
    }

    // Approve Partner Dialog
    if (showApprovePartnerDialog && selectedPartnerApp != null) {
        val app = selectedPartnerApp!!
        AlertDialog(
            onDismissRequest = {
                showApprovePartnerDialog = false
                selectedPartnerApp = null
            },
            title = { Text("Approve ${app.type} Partner", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Applicant: ${app.applicantName} • CNIC: ${app.cnic}", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (app.type == "DISTRIBUTOR") {
                        Text("Select Service Zone Assignment:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        snapshot.serviceZones.forEach { zone ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedZoneId = zone.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedZoneId == zone.id,
                                    onClick = { selectedZoneId = zone.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${zone.name} (${zone.city})", fontSize = 12.sp)
                            }
                        }
                    } else {
                        Text("Select Parent Distributor Terminal:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        snapshot.distributors.forEach { dist ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedLinkedDistId = dist.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedLinkedDistId == dist.id,
                                    onClick = { selectedLinkedDistId = dist.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${dist.name} (${dist.city})", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = trainingCompleted,
                                onCheckedChange = { trainingCompleted = it }
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Safety & Installation Training Certified", fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.approvePartner(
                            appId = app.id,
                            zoneId = if (app.type == "DISTRIBUTOR") selectedZoneId else null,
                            linkedDistributorId = if (app.type == "RIDER") selectedLinkedDistId else null,
                            trainingDone = trainingCompleted,
                            actor = "Operations Lead"
                        )
                        onBanner(msg)
                        showApprovePartnerDialog = false
                        selectedPartnerApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Approve & Activate")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showApprovePartnerDialog = false
                    selectedPartnerApp = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Return Incomplete Dialog
    if (showReturnPartnerDialog && selectedPartnerApp != null) {
        val app = selectedPartnerApp!!
        AlertDialog(
            onDismissRequest = {
                showReturnPartnerDialog = false
                selectedPartnerApp = null
            },
            title = { Text("Return Application as Incomplete", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Specify missing documentation or requirements for ${app.applicantName}:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = missingDocsInput,
                        onValueChange = { missingDocsInput = it },
                        label = { Text("Missing Details / Actions Needed") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.returnPartnerApplication(app.id, missingDocsInput, "Operations Lead")
                        onBanner(msg)
                        showReturnPartnerDialog = false
                        selectedPartnerApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706))
                ) {
                    Text("Return Application")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showReturnPartnerDialog = false
                    selectedPartnerApp = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Reject Partner Dialog
    if (showRejectPartnerDialog && selectedPartnerApp != null) {
        val app = selectedPartnerApp!!
        AlertDialog(
            onDismissRequest = {
                showRejectPartnerDialog = false
                selectedPartnerApp = null
            },
            title = { Text("Reject Partner Application", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Enter rejection reason for ${app.applicantName}:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReasonInput,
                        onValueChange = { rejectionReasonInput = it },
                        label = { Text("Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.rejectPartner(app.id, rejectionReasonInput, "Operations Lead")
                        onBanner(msg)
                        showRejectPartnerDialog = false
                        selectedPartnerApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Rejection")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRejectPartnerDialog = false
                    selectedPartnerApp = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Resolve Conflict Dialog
    if (showResolveConflictDialog && selectedConflict != null) {
        val conf = selectedConflict!!
        AlertDialog(
            onDismissRequest = {
                showResolveConflictDialog = false
                selectedConflict = null
            },
            title = { Text("Resolve Offline Serial Conflict", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Conflicting Serial: ${conf.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Select which order is awarded this serial:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    listOf(conf.orderId1, conf.orderId2).forEach { ordId ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { winningOrderId = ordId }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(
                                selected = winningOrderId == ordId,
                                onClick = { winningOrderId = ordId }
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Order $ordId (Winner)", fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = conflictResolutionReason,
                        onValueChange = { conflictResolutionReason = it },
                        label = { Text("Resolution Reason / Physical Audit Note") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.resolveSerialConflict(conf.id, winningOrderId, conflictResolutionReason, "Operations Lead")
                        onBanner(msg)
                        showResolveConflictDialog = false
                        selectedConflict = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Resolve & Award Serial")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showResolveConflictDialog = false
                    selectedConflict = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}
