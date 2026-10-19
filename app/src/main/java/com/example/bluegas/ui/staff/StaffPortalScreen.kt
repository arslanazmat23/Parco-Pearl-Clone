package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.bluegas.ui.components.OpenSequencingDecisionBadge
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffPortalScreen(store: BlueGasStore) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    var bannerMessage by remember { mutableStateOf<String?>(null) }
    var selectedOrderForDetail by remember { mutableStateOf<Order?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // Staff Header & Sub-role Selector Bar
        Surface(
            color = Color(0xFF0F172A),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AdminPanelSettings,
                            contentDescription = "Staff Portal Icon",
                            tint = Color(0xFF38BDF8)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "BLUE GAS STAFF CONTROL PORTAL",
                                fontWeight = FontWeight.Black,
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            Text(
                                text = "Accountable Role: ${snapshot.activeStaffSubRole.displayName}",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    // Open sequencing decision notice
                    OpenSequencingDecisionBadge()
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-Role Selector Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StaffSubRole.entries.forEach { role ->
                        val isSelected = snapshot.activeStaffSubRole == role
                        Surface(
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
                                .testTag("staff_subrole_${role.name.lowercase()}")
                                .clickable {
                                    store.activeStaffSubRole = role
                                    store.notifyChanged()
                                }
                        ) {
                            Text(
                                text = role.displayName,
                                fontSize = 11.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }

        // Action banner
        if (bannerMessage != null) {
            Surface(
                color = Color(0xFFFEF3C7),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("staff_action_banner")
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = bannerMessage ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFF92400E),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { bannerMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss banner",
                            modifier = Modifier.size(14.dp),
                            tint = Color(0xFF92400E)
                        )
                    }
                }
            }
        }

        // Role-Scoped Content
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            when (snapshot.activeStaffSubRole) {
                StaffSubRole.OPERATIONS -> OperationsScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it },
                    onSelectOrder = { selectedOrderForDetail = it }
                )
                StaffSubRole.FINANCE -> FinanceScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it },
                    onSelectOrder = { selectedOrderForDetail = it }
                )
                StaffSubRole.DISTRIBUTOR -> DistributorScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it },
                    onSelectOrder = { selectedOrderForDetail = it }
                )
                StaffSubRole.SUPPORT -> SupportScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it },
                    onSelectOrder = { selectedOrderForDetail = it }
                )
                StaffSubRole.ADMIN -> AdminScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it }
                )
                else -> OperationsScreen(
                    store = store,
                    snapshot = snapshot,
                    onBanner = { bannerMessage = it },
                    onSelectOrder = { selectedOrderForDetail = it }
                )
            }
        }
    }

    // Order detail modal dialog
    if (selectedOrderForDetail != null) {
        StaffOrderDetailDialog(
            order = selectedOrderForDetail!!,
            snapshot = snapshot,
            onDismiss = { selectedOrderForDetail = null }
        )
    }
}

// ----------------------------------------------------
// 1. OPERATIONS SUB-ROLE
// ----------------------------------------------------
@Composable
private fun OperationsScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    val exceptionOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_EXC }
    val pendingPartners = snapshot.partnerApplications.filter { it.status == "PENDING_APPROVAL" }

    var selectedPartnerTab by remember { mutableStateOf("PENDING") }
    var showRejectDialog by remember { mutableStateOf(false) }
    var appToReject by remember { mutableStateOf<PartnerApplication?>(null) }
    var rejectionReason by remember { mutableStateOf("Incomplete documentation / verification failed") }

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
                                        store.opsConflictQueue.remove(conf)
                                        store.logAudit(
                                            "ConflictQueue",
                                            conf.id,
                                            "RESOLVE_CONFLICT",
                                            "Operations Officer",
                                            AccountableRole.OPERATIONS,
                                            "Resolved serial conflict for ${conf.serialNumber}"
                                        )
                                        store.notifyChanged()
                                        onBanner("Conflict for serial ${conf.serialNumber} resolved by Ops.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Resolve & Clear", fontSize = 10.sp)
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
                            listOf("PENDING", "ALL").forEach { tab ->
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

                    val displayPartners = if (selectedPartnerTab == "PENDING") {
                        snapshot.partnerApplications.filter { it.status == "PENDING_APPROVAL" }
                    } else {
                        snapshot.partnerApplications
                    }

                    if (displayPartners.isEmpty()) {
                        Text(
                            text = if (selectedPartnerTab == "PENDING") "No pending partner onboarding applications." else "No partner applications on record.",
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
                                                else -> Color(0xFFFEF3C7)
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
                                                    else -> Color(0xFF92400E)
                                                },
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "City: ${app.city} • CNIC: ${app.cnic} • Details: ${app.vehicleOrShopDetails}",
                                        fontSize = 10.sp,
                                        color = Color(0xFF475569)
                                    )

                                    if (app.status == "PENDING_APPROVAL") {
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Button(
                                                onClick = {
                                                    val (ok, msg) = store.approvePartnerApplication(app.id)
                                                    onBanner(msg)
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                            ) {
                                                Text("Approve Partner", fontSize = 10.sp)
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    appToReject = app
                                                    showRejectDialog = true
                                                },
                                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(30.dp)
                                            ) {
                                                Text("Reject", fontSize = 10.sp, color = Color(0xFFDC2626))
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

    if (showRejectDialog && appToReject != null) {
        AlertDialog(
            onDismissRequest = {
                showRejectDialog = false
                appToReject = null
            },
            title = { Text("Reject Partner Application", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select or enter rejection reason for ${appToReject?.applicantName}:", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = rejectionReason,
                        onValueChange = { rejectionReason = it },
                        label = { Text("Rejection Reason") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.rejectPartnerApplication(appToReject!!.id, rejectionReason)
                        onBanner(msg)
                        showRejectDialog = false
                        appToReject = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Rejection")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showRejectDialog = false
                    appToReject = null
                }) {
                    Text("Cancel")
                }
            }
        )
    }
}

// ----------------------------------------------------
// 2. FINANCE SUB-ROLE
// ----------------------------------------------------
@Composable
private fun FinanceScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    val totalRevenue = snapshot.ledgerEntries.filter { it.entryType == "REVENUE" }.sumOf { it.debitPaisa }
    val totalDepositsHeld = snapshot.ledgerEntries.filter { it.entryType == "DEPOSIT_LIABILITY" }.sumOf { it.creditPaisa }
    val totalRefunds = snapshot.ledgerEntries.filter { it.entryType == "REFUND" }.sumOf { it.creditPaisa }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("finance_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Finance Lead",
                description = "Payment Gateway Auditing, Refunds, Settlements & Deposit Liability"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Financial Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Gross Refill Volume", fontSize = 9.sp, color = Color(0xFF1E40AF))
                        Text(formatPaisaToPkr(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = BlueGasPrimary)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Deposit Liability", fontSize = 9.sp, color = Color(0xFFB45309))
                        Text(formatPaisaToPkr(totalDepositsHeld), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFD97706))
                        Text("Balance Sheet Item", fontSize = 7.sp, color = Color.Gray)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Total Refunds", fontSize = 9.sp, color = Color(0xFF991B1B))
                        Text(formatPaisaToPkr(totalRefunds), fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFDC2626))
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Payment Records & Settlement Audit:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(snapshot.payments) { pay ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
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
                        Text("Pay ID: ${pay.id} (${pay.orderId})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Surface(
                            color = when (pay.state) {
                                PaymentState.SETTLED -> Color(0xFFDCFCE7)
                                PaymentState.FULLY_REFUNDED -> Color(0xFFFEE2E2)
                                else -> Color(0xFFDBEAFE)
                            },
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = pay.state.displayName,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = when (pay.state) {
                                    PaymentState.SETTLED -> Color(0xFF166534)
                                    PaymentState.FULLY_REFUNDED -> Color(0xFF991B1B)
                                    else -> Color(0xFF1E40AF)
                                },
                                modifier = Modifier.padding(4.dp, 2.dp)
                            )
                        }
                    }
                    Text("Method: ${pay.method.name} • Base: ${formatPaisaToPkr(pay.baseAmountPaisa)} • Settled: ${formatPaisaToPkr(pay.settledAmountPaisa)}", fontSize = 10.sp)
                    Text("Idempotency: ${pay.idempotencyKey}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)

                    // Refund execution button for Finance
                    if (pay.state != PaymentState.FULLY_REFUNDED && (pay.settledAmountPaisa > 0 || pay.capturedAmountPaisa > 0)) {
                        Button(
                            onClick = {
                                val (s, m) = store.executeRefund(
                                    orderId = pay.orderId,
                                    amountPaisa = pay.baseAmountPaisa,
                                    reason = "Finance manual refund override",
                                    actor = "Finance Officer"
                                )
                                onBanner(m)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .height(28.dp)
                                .padding(top = 4.dp)
                        ) {
                            Text("Execute Full Refund (${formatPaisaToPkr(pay.baseAmountPaisa)})", fontSize = 9.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(12.dp))
            Text("Immutable Ledger Entries (${snapshot.ledgerEntries.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(4.dp))
        }

        items(snapshot.ledgerEntries.reversed()) { led ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 3.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "[${led.entryType}] ${led.description}",
                    fontSize = 10.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = if (led.debitPaisa > 0) "+${formatPaisaToPkr(led.debitPaisa)}" else "-${formatPaisaToPkr(led.creditPaisa)}",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (led.debitPaisa > 0) Color(0xFF16A34A) else Color(0xFFD97706)
                )
            }
        }
    }
}

// ----------------------------------------------------
// 3. DISTRIBUTOR SUB-ROLE
// ----------------------------------------------------
@Composable
private fun DistributorScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    val assignedOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_ASGN }
    val acceptedOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_ACPT }
    val preparingOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_PREP }
    val readyPickupOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_RDYPU }
    val availableCylinders = snapshot.cylinders.filter { it.status == InventoryState.INV_AVAIL }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("distributor_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Distributor Lead",
                description = "Order Acceptance, Exact Serial Binding & Stock Commit"
            )
            Spacer(modifier = Modifier.height(10.dp))

            // Open sequencing decision badge
            OpenSequencingDecisionBadge()
            Spacer(modifier = Modifier.height(8.dp))

            Text("Orders Awaiting Acceptance (${assignedOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (assignedOrders.isEmpty()) {
            item {
                Text("No orders waiting in acceptance window.", fontSize = 11.sp, color = Color.Gray)
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            items(assignedOrders) { ord ->
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
                            Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Customer: ${ord.customerId} • Mode: ${ord.fulfilmentMode.name}", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                val (s, m) = store.distributorAcceptOrder(ord.id)
                                onBanner(m)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Accept Order (SLA)", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        // Accepted orders awaiting preparation initiation
        if (acceptedOrders.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Accepted SLA Orders Awaiting Preparation (${acceptedOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(acceptedOrders) { ord ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
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
                            Text("Order ${ord.id} (Accepted)", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("SLA Accepted • Ready to start physical filling & staging", fontSize = 10.sp, color = Color(0xFF166534))
                        }
                        Button(
                            onClick = {
                                val tPrep = OrderStateMachine.transition(
                                    order = ord,
                                    targetStatus = OrderStatus.ORD_PREP,
                                    actor = "Distributor Lead",
                                    role = AccountableRole.DISTRIBUTOR,
                                    trigger = "Distributor initiated preparation"
                                )
                                if (tPrep is TransitionResult.Success) {
                                    store.notifyChanged()
                                    onBanner("Order ${ord.id} is now Preparing (ORD_PREP).")
                                } else if (tPrep is TransitionResult.Failure) {
                                    onBanner(tPrep.errorMessage)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Start Preparing", fontSize = 10.sp)
                        }
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(10.dp))
            Text("Preparing & Exact Serial Allocation (${preparingOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (preparingOrders.isEmpty()) {
            item {
                Text("No orders currently in preparation.", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            items(preparingOrders) { ord ->
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
                            Text("Order ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            CanonicalStatusChip(ord.status)
                        }
                        Text(
                            text = "Allocated Serial: ${ord.allocatedSerial ?: "NOT YET ALLOCATED"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = BlueGasPrimary
                        )

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Select Outgoing Cylinder Serial to Bind:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)

                        // Filter cylinders matching order item SKU
                        val orderSku = ord.items.firstOrNull()?.sku
                        val matchingCylinders = availableCylinders.filter { orderSku == null || it.sku == orderSku }
                        val displayCylinders = if (matchingCylinders.isNotEmpty()) matchingCylinders else availableCylinders

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            displayCylinders.take(4).forEach { cyl ->
                                OutlinedButton(
                                    onClick = {
                                        val (s, m) = store.allocateCylinderSerial(ord.id, cyl.serialNumber)
                                        onBanner(m)
                                    },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                                    modifier = Modifier.height(28.dp)
                                ) {
                                    Text(cyl.serialNumber, fontSize = 9.sp)
                                }
                            }
                        }

                        // Dispatch or Ready button
                        Button(
                            onClick = {
                                val (s, m) = store.dispatchOrReadyForPickup(ord.id)
                                onBanner(m)
                            },
                            enabled = ord.allocatedSerial != null,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp)
                        ) {
                            Text(
                                text = if (ord.fulfilmentMode == FulfilmentMode.DELIVERY) "Dispatch to Rider (ORD_DISP)" else "Mark Ready for Pickup (ORD_RDYPU)",
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }
        }

        // Ready for Pickup section
        if (readyPickupOrders.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                Text("Staged Counter Pickup Orders (${readyPickupOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(readyPickupOrders) { ord ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF9C3)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE047)),
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
                            Text("Pickup ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Serial: ${ord.allocatedSerial ?: "N/A"} • Customer: ${ord.customerId}", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                store.arriveAtHandover(ord.id, actor = "Counter Staff")
                                val (ok, msg) = store.completeHandover(
                                    orderId = ord.id,
                                    nonTechInstallationPassed = true,
                                    proofOfDeliveryNote = "Customer collected at counter. Non-tech inspection passed.",
                                    collectedCashPaisa = if (ord.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) ord.totalAmountPaisa else null,
                                    customerSignature = "Counter Signature"
                                )
                                onBanner(msg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Complete Handover", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 4. SUPPORT SUB-ROLE
// ----------------------------------------------------
@Composable
private fun SupportScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    var selectedFilter by remember { mutableStateOf("ALL") }
    var showResolveDialog by remember { mutableStateOf(false) }
    var caseToResolve by remember { mutableStateOf<SupportCase?>(null) }
    var resolutionNotesInput by remember { mutableStateOf("Inspected per OGRA safety guidelines. Resolved satisfactorily.") }

    var showNewCaseDialog by remember { mutableStateOf(false) }
    var newCaseOrderId by remember { mutableStateOf(snapshot.orders.firstOrNull()?.id ?: "") }
    var newCaseIssueType by remember { mutableStateOf("LEAKAGE_SAFETY") }
    var newCaseDescription by remember { mutableStateOf("Customer reported suspect regulator seal after delivery.") }

    val filteredCases = when (selectedFilter) {
        "OPEN" -> snapshot.supportCases.filter { it.status == "OPEN" }
        "RESOLVED" -> snapshot.supportCases.filter { it.status == "RESOLVED" }
        else -> snapshot.supportCases
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("support_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Customer Support Lead",
                description = "Dispute Cases, Safety Inquiries & Post-Completion Incidents"
            )
            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                color = Color(0xFFEFF6FF),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF93C5FD)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Rule: Completed orders stay Completed (ORD_COMP). Post-completion disputes are tracked through immutable Support Cases.",
                    fontSize = 10.sp,
                    color = Color(0xFF1E40AF),
                    modifier = Modifier.padding(8.dp)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))

            // Action row & filter
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("ALL", "OPEN", "RESOLVED").forEach { tab ->
                        FilterChip(
                            selected = selectedFilter == tab,
                            onClick = { selectedFilter = tab },
                            label = { Text(tab, fontSize = 10.sp) },
                            modifier = Modifier.height(28.dp)
                        )
                    }
                }
                Button(
                    onClick = { showNewCaseDialog = true },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New Case", fontSize = 10.sp)
                }
            }
            Spacer(modifier = Modifier.height(8.dp))

            Text("Support Complaint Cases (${filteredCases.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (filteredCases.isEmpty()) {
            item {
                Text("No support dispute cases found.", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
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
                                color = if (c.status == "OPEN") Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                                shape = RoundedCornerShape(4.dp)
                            ) {
                                Text(
                                    text = c.status,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (c.status == "OPEN") Color(0xFF991B1B) else Color(0xFF166534),
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
                            Button(
                                onClick = {
                                    caseToResolve = c
                                    showResolveDialog = true
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier
                                    .height(30.dp)
                                    .padding(top = 4.dp)
                            ) {
                                Text("Mark Case Resolved", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }

    // Resolve Case Dialog calling Part C resolveSupportCase
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
}

// ----------------------------------------------------
// 5. ADMIN CONFIGURATION SUB-ROLE
// ----------------------------------------------------
@Composable
private fun AdminScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit
) {
    var holdTtl by remember(snapshot.priceBookConfig.holdTtlMinutes) {
        mutableStateOf(snapshot.priceBookConfig.holdTtlMinutes.toString())
    }
    var platFee by remember(snapshot.priceBookConfig.platformFeePaisa) {
        mutableStateOf((snapshot.priceBookConfig.platformFeePaisa / 100).toString())
    }
    var delFee by remember(snapshot.priceBookConfig.deliveryFeePaisa) {
        mutableStateOf((snapshot.priceBookConfig.deliveryFeePaisa / 100).toString())
    }
    var taxBps by remember(snapshot.priceBookConfig.taxRateBps) {
        mutableStateOf(snapshot.priceBookConfig.taxRateBps.toString())
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("admin_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "System Administrator",
                description = "Live Business Parameters, Service Zones & System Audit Logs"
            )
            Spacer(modifier = Modifier.height(10.dp))

            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text("Dynamic Business Parameters (Immediate Live Effect):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = holdTtl,
                            onValueChange = {
                                holdTtl = it
                                val num = it.toIntOrNull() ?: 15
                                store.priceBookConfig = store.priceBookConfig.copy(holdTtlMinutes = num)
                                store.notifyChanged()
                            },
                            label = { Text("Hold TTL (M)", fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = platFee,
                            onValueChange = {
                                platFee = it
                                val p = (it.toLongOrNull() ?: 150) * 100
                                store.priceBookConfig = store.priceBookConfig.copy(platformFeePaisa = p)
                                store.notifyChanged()
                            },
                            label = { Text("Platform (PKR)", fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = delFee,
                            onValueChange = {
                                delFee = it
                                val d = (it.toLongOrNull() ?: 300) * 100
                                store.priceBookConfig = store.priceBookConfig.copy(deliveryFeePaisa = d)
                                store.notifyChanged()
                            },
                            label = { Text("Delivery (PKR)", fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = taxBps,
                            onValueChange = {
                                taxBps = it
                                val t = it.toIntOrNull() ?: 500
                                store.priceBookConfig = store.priceBookConfig.copy(taxRateBps = t)
                                store.notifyChanged()
                            },
                            label = { Text("Tax (bps)", fontSize = 9.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Identity Verification Level at Sign-up:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("NONE", "PHONE_OTP", "CNIC").forEach { level ->
                            FilterChip(
                                selected = snapshot.priceBookConfig.verificationLevel == level,
                                onClick = {
                                    store.priceBookConfig = store.priceBookConfig.copy(verificationLevel = level)
                                    store.notifyChanged()
                                    onBanner("Verification level set to $level")
                                },
                                label = { Text(level, fontSize = 10.sp) }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))
            Text("Service Zones & Serviceability Toggles:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
                    Column {
                        Text("${zone.city}: ${zone.name}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("Zone ID: ${zone.id} • Status: ${if (zone.isServiceable) "ACTIVE PILOT" else "UNSERVICEABLE"}", fontSize = 10.sp)
                    }
                    Switch(
                        checked = zone.isServiceable,
                        onCheckedChange = { active ->
                            val idx = store.serviceZones.indexOfFirst { it.id == zone.id }
                            if (idx != -1) {
                                store.serviceZones[idx] = zone.copy(isServiceable = active)
                            }
                            store.addresses.filter { it.zoneId == zone.id }.forEach { a ->
                                val aIdx = store.addresses.indexOfFirst { it.id == a.id }
                                if (aIdx != -1) {
                                    store.addresses[aIdx] = a.copy(isServiceable = active)
                                }
                            }
                            store.notifyChanged()
                            onBanner("Updated serviceability for ${zone.name}")
                        }
                    )
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(14.dp))
            Text("System Audit Logs (${snapshot.auditLogs.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(snapshot.auditLogs.takeLast(10).reversed()) { log ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
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
                        Text("[${log.role.displayName}] ${log.action}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Text(formatTimestamp(log.timestamp), fontSize = 9.sp, color = Color.Gray)
                    }
                    Text("Entity: ${log.entityType}:${log.entityId} • Actor: ${log.actor}", fontSize = 10.sp, color = Color(0xFF475569))
                    Text(log.details, fontSize = 10.sp)
                }
            }
        }
    }
}

@Composable
private fun AccountableHeader(roleName: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text("ACCOUNTABLE HUMAN ROLE: $roleName", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF0F172A))
            Text(description, fontSize = 10.sp, color = Color(0xFF475569))
        }
    }
}

@Composable
private fun StaffOrderDetailDialog(
    order: Order,
    snapshot: SharedAppState,
    onDismiss: () -> Unit
) {
    val history = snapshot.orderStatusHistory.filter { it.orderId == order.id }
    val payment = snapshot.payments.find { it.orderId == order.id }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close") }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Order Details: ${order.id}", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                CanonicalStatusChip(order.status)
            }
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 450.dp)) {
                item {
                    Text("Customer ID: ${order.customerId}", fontSize = 11.sp)
                    Text("Address ID: ${order.addressId} • Fulfilment Mode: ${order.fulfilmentMode.name}", fontSize = 11.sp)
                    Text("Allocated Serial: ${order.allocatedSerial ?: "NOT ALLOCATED"}", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    if (order.assignedRiderId != null) {
                        Text("Assigned Rider: ${order.assignedRiderId}", fontSize = 11.sp)
                    }
                    if (order.podReference != null) {
                        Text("POD Reference: ${order.podReference}", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Financial Summary:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Text("Subtotal: ${formatPaisaToPkr(order.subtotalPaisa)}", fontSize = 10.sp)
                    Text("Deposit (Liability): ${formatPaisaToPkr(order.depositTotalPaisa)}", fontSize = 10.sp, color = Color(0xFFB45309))
                    Text("Platform Fee: ${formatPaisaToPkr(order.platformFeePaisa)}", fontSize = 10.sp)
                    Text("Delivery Fee: ${formatPaisaToPkr(order.deliveryFeePaisa)}", fontSize = 10.sp)
                    Text("Sales Tax: ${formatPaisaToPkr(order.taxPaisa)}", fontSize = 10.sp)
                    Text("Total Amount: ${formatPaisaToPkr(order.totalAmountPaisa)}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    if (payment != null) {
                        Text("Payment State: ${payment.state.displayName} (${payment.method.name})", fontSize = 10.sp, color = BlueGasPrimary)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Text("Status Transition Audit History (${history.size}):", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                }

                if (history.isEmpty()) {
                    item {
                        Text("No transitions recorded yet.", fontSize = 10.sp, color = Color.Gray)
                    }
                } else {
                    items(history) { hist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${hist.fromStatus.code} ➔ ${hist.toStatus.code}", fontWeight = FontWeight.Bold, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
                                Text(formatTimestamp(hist.timestamp), fontSize = 9.sp, color = Color.Gray)
                            }
                            Text("Actor: ${hist.actor} [${hist.role.displayName}]", fontSize = 9.sp)
                        }
                    }
                }
            }
        }
    )
}
