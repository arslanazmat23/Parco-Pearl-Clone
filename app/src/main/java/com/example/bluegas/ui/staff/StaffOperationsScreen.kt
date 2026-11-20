package com.example.bluegas.ui.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.bluegas.ui.customer.DemoValuePill
import com.example.ui.theme.BlueGasPrimary

private enum class OperationsTab(val label: String) {
    DISPATCH("Dispatch"),
    EXCEPTIONS("Exceptions"),
    PARTNERS("Partners"),
    RIDERS("Riders"),
    ALL_ORDERS("All orders")
}

/**
 * Staff Operations workspace covering rider dispatch offers (RACI-06), constrained exception
 * recovery, partner onboarding triage, offline serial conflict resolution, and platform order overview.
 *
 * @param store Shared in-memory store instance.
 * @param snapshot Immutable state snapshot for rendering.
 * @param onBanner Banner callback reporting success or error feedback.
 * @param onSelectOrder Callback to open order detail dialog.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StaffOperationsScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    val needsARiderOrders = remember(snapshot.version) {
        snapshot.orders.filter {
            it.fulfilmentMode == FulfilmentMode.DELIVERY &&
                it.dispatchModeAtConfirm == DispatchMode.AUTO_RIDER_FIRST &&
                it.status == OrderStatus.ORD_ASGN &&
                it.assignedRiderId == null
        }
    }
    val dispatchOrders = remember(snapshot.version) {
        snapshot.orders.filter {
            it.fulfilmentMode == FulfilmentMode.DELIVERY &&
                it.dispatchModeAtConfirm != DispatchMode.AUTO_RIDER_FIRST &&
                it.status == OrderStatus.ORD_PREP &&
                it.allocatedSerial != null &&
                it.riderTaskStatus != RiderTaskStatus.ACCEPTED
        }
    }
    val returnCollectionCases = remember(snapshot.version) {
        snapshot.returnCases.filter { rc ->
            rc.type == "CYLINDER_RETURN" &&
                (rc.status == L6Status.RETURN_AUTHORIZED ||
                    (rc.status == L6Status.AWAITING_ITEM_RETURN &&
                        rc.collectionTask != "COLLECTED" &&
                        rc.collectionTask != "AT_HUB"))
        }
    }
    val exceptionOrders = remember(snapshot.version) {
        snapshot.orders.filter { it.status == OrderStatus.ORD_EXC }
    }
    val pendingPartners = remember(snapshot.version) {
        snapshot.partnerApplications.filter { it.status == "PENDING_APPROVAL" }
    }

    var selectedOpsTab by remember { mutableStateOf(OperationsTab.DISPATCH) }
    var selectedPartnerTab by remember { mutableStateOf("PENDING") }
    var showApprovePartnerDialog by remember { mutableStateOf(false) }
    var showRejectPartnerDialog by remember { mutableStateOf(false) }
    var showReturnPartnerDialog by remember { mutableStateOf(false) }
    var selectedPartnerApp by remember { mutableStateOf<PartnerApplication?>(null) }
    var selectedZoneId by remember(snapshot.version) { mutableStateOf(snapshot.serviceZones.firstOrNull()?.id ?: "") }
    var selectedHomePointId by remember(snapshot.version) {
        mutableStateOf(snapshot.nodes.firstOrNull { it.type == FulfilmentPointType.HUB }?.id ?: "")
    }
    var rejectionReasonInput by remember { mutableStateOf("") }
    var missingDocsInput by remember { mutableStateOf("") }

    var riderForTraining by remember { mutableStateOf<Rider?>(null) }
    var trainingNoteInput by remember { mutableStateOf("") }

    var showResolveConflictDialog by remember { mutableStateOf(false) }
    var selectedConflict by remember { mutableStateOf<SerialConflictItem?>(null) }
    var winningOrderId by remember { mutableStateOf("") }
    var conflictResolutionReason by remember { mutableStateOf("") }

    // Exception recovery dialog state
    var recoveryOrderTarget by remember { mutableStateOf<Pair<Order, OrderStatus>?>(null) }
    var recoveryReasonInput by remember { mutableStateOf("") }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("operations_screen")
    ) {
        item {
            AccountableHeader(
                roleName = AccountableRole.OPERATIONS.displayName,
                description = "Fulfilment Exceptions, Partner Onboarding, Dispatch Routing & Serial Collision Triage"
            )
            Spacer(modifier = Modifier.height(8.dp))

            // Section tabs
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(OperationsTab.entries) { tab ->
                    val count = when (tab) {
                        OperationsTab.DISPATCH -> needsARiderOrders.size + dispatchOrders.size + returnCollectionCases.size
                        OperationsTab.EXCEPTIONS -> exceptionOrders.size
                        OperationsTab.PARTNERS -> pendingPartners.size
                        OperationsTab.RIDERS -> snapshot.riders.size
                        OperationsTab.ALL_ORDERS -> snapshot.orders.size
                    }
                    val selected = selectedOpsTab == tab
                    FilterChip(
                        selected = selected,
                        onClick = { selectedOpsTab = tab },
                        label = {
                            Text(
                                text = "${tab.label} ($count)",
                                fontSize = 10.sp,
                                maxLines = 1
                            )
                        },
                        modifier = Modifier.testTag("ops_tab_${tab.name.lowercase()}")
                    )
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
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

        // Dispatch Tab (RACI-06)
        if (selectedOpsTab == OperationsTab.DISPATCH) {
            item {
                AccountableHeader(
                    roleName = "${AccountableRole.OPERATIONS.displayName} (RACI-06)",
                    description = "Assign riders when automatic dispatch has no free rider, and offer prepared deliveries in hub-first mode."
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Needs a rider (${needsARiderOrders.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (needsARiderOrders.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No automatic delivery orders waiting for a rider.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(needsARiderOrders, key = { "auto_needs_rider_${it.id}" }) { ord ->
                    OperationsDispatchCard(
                        order = ord,
                        store = store,
                        snapshot = snapshot,
                        onBanner = onBanner,
                        onSelectOrder = onSelectOrder
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Prepared deliveries (${dispatchOrders.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (dispatchOrders.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No bound delivery orders waiting for a rider offer.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(dispatchOrders, key = { it.id }) { ord ->
                    OperationsDispatchCard(
                        order = ord,
                        store = store,
                        snapshot = snapshot,
                        onBanner = onBanner,
                        onSelectOrder = onSelectOrder
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "Cylinder return collections (${returnCollectionCases.size})",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A)
                )
                Spacer(modifier = Modifier.height(4.dp))
            }
            if (returnCollectionCases.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No authorized cylinder return collections waiting for rider dispatch.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(returnCollectionCases, key = { it.id }) { rc ->
                    OperationsReturnCollectionCard(
                        rc = rc,
                        store = store,
                        snapshot = snapshot,
                        onBanner = onBanner
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(10.dp))
                OperationsHubMinimumsCard(
                    store = store,
                    snapshot = snapshot,
                    onBanner = onBanner
                )
            }
        }

        // Partner Onboarding Queue (UC-PRT-02, Accountable: Operations)
        if (selectedOpsTab == OperationsTab.PARTNERS) {
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
                                                    "RETURNED_INCOMPLETE" -> Color(0xFFFEF3C7)
                                                    else -> Color(0xFFDBEAFE)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(
                                                    text = when (app.status) {
                                                        "PENDING_APPROVAL" -> "Under review"
                                                        "RETURNED_INCOMPLETE" -> "Documents needed"
                                                        "APPROVED" -> "Approved"
                                                        "REJECTED" -> "Not approved"
                                                        else -> app.status
                                                    },
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = when (app.status) {
                                                        "APPROVED" -> Color(0xFF166534)
                                                        "REJECTED" -> Color(0xFF991B1B)
                                                        "RETURNED_INCOMPLETE" -> Color(0xFF92400E)
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
                                                        missingDocsInput = ""
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
                                                        rejectionReasonInput = ""
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
        }

        // Riders Tab: training gate (F3.3) & read-only 30-day performance (F3.4)
        if (selectedOpsTab == OperationsTab.RIDERS) {
            item {
                AccountableHeader(
                    roleName = AccountableRole.OPERATIONS.displayName,
                    description = "Rider readiness, installation checklist training gate, and 30-day operational summary (no ranking)."
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (snapshot.riders.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No riders on record yet.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
            } else {
                items(snapshot.riders, key = { it.id }) { rider ->
                    val perf = remember(snapshot.version, rider.id) {
                        store.riderPerformance(rider.id, days = 30)
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (rider.installationTrained) Color(0xFFCBD5E1) else Color(0xFFF59E0B)
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("ops_rider_card_${rider.id}")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${rider.name} · ${rider.id}",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF0F172A)
                                    )
                                    Text(
                                        text = "${snapshot.pointName(rider.pointId)} · ${rider.zoneName} · ${rider.vehicleNumber}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                }
                                Surface(
                                    color = if (rider.installationTrained) Color(0xFFDCFCE7) else Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(6.dp)
                                ) {
                                    Text(
                                        text = if (rider.installationTrained) "Checklist trained" else "Training pending",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (rider.installationTrained) Color(0xFF166534) else Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }

                            if (!rider.installationTrained) {
                                Button(
                                    onClick = {
                                        riderForTraining = rider
                                        trainingNoteInput = ""
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp)
                                        .testTag("ops_mark_training_${rider.id}")
                                ) {
                                    Text("Mark checklist training done", fontSize = 12.sp)
                                }
                            }

                            Surface(
                                color = Color(0xFFF8FAFC),
                                shape = RoundedCornerShape(6.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(
                                        text = "Last 30 days (read-only)",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF334155)
                                    )
                                    val onTimeText = perf.onTimePercent?.let {
                                        "$it% (${perf.onTimeHandovers}/${perf.completedDeliveries})"
                                    } ?: "No completed deliveries yet"
                                    val checklistText = perf.checklistPercent?.let {
                                        "$it% (${perf.checklistCompletedCount}/${perf.completedDeliveries})"
                                    } ?: "No completed deliveries yet"
                                    Text("On-time handovers: $onTimeText", fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Text("Installation checklist completed: $checklistText", fontSize = 11.sp, color = Color(0xFF1E293B))
                                    Text(
                                        text = "Cash differences: ${perf.cashDifferenceCount} (${formatRupees(perf.cashDifferenceSumPaisa)})",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                    Text(
                                        text = "Complaints linked to rider orders: ${perf.complaintCount}",
                                        fontSize = 11.sp,
                                        color = Color(0xFF1E293B)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fulfilment Exceptions Section
        if (selectedOpsTab == OperationsTab.EXCEPTIONS) {
            item {
                Text("Active Fulfilment Exceptions (${exceptionOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            if (exceptionOrders.isEmpty()) {
                item {
                    Text("No active fulfilment exceptions.", fontSize = 11.sp, color = Color.Gray)
                }
            } else {
                items(exceptionOrders, key = { it.id }) { ord ->
                    val reason = ord.exceptionReason ?: ExceptionReason.UNRECOVERABLE_AFTER_RETRIES
                    val targets = remember(snapshot.version, ord.id) {
                        reason.allowedRecoveryTargets.sortedBy {
                            when (it) {
                                OrderStatus.ORD_CANC -> 1
                                OrderStatus.ORD_FCLO -> 2
                                else -> 0
                            }
                        }
                    }
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .testTag("ops_exception_card_${ord.id}")
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = snapshot.orderLabel(ord.id),
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp
                                )
                                Surface(color = Color(0xFFFEE2E2), shape = RoundedCornerShape(6.dp)) {
                                    Text(
                                        text = ord.status.displayName,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF991B1B),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "Customer: ${snapshot.customerName(ord.customerId)} · Hub: ${snapshot.pointName(ord.fulfilmentPointId)}",
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = reason.description,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFFDC2626)
                            )
                            if (!ord.exceptionDetails.isNullOrBlank()) {
                                Text(
                                    text = ord.exceptionDetails!!,
                                    fontSize = 10.sp,
                                    color = Color(0xFF475569)
                                )
                            }
                            if (ord.awaitingCustomerReschedule) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFFF1F5F9),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Waiting for the customer to pick a new time",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF475569),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            } else if (ord.notHomeCount > 0) {
                                Spacer(modifier = Modifier.height(6.dp))
                                Surface(
                                    color = Color(0xFFFEF3C7),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "Not home — customer waiting for a call",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF92400E),
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))
                            Text("Recovery actions:", fontWeight = FontWeight.Bold, fontSize = 10.sp, color = Color(0xFF334155))
                            Spacer(modifier = Modifier.height(4.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                targets.forEach { target ->
                                    val label = when (target) {
                                        OrderStatus.ORD_CANC -> "Cancel order"
                                        OrderStatus.ORD_FCLO -> "Fail closed (retries used up)"
                                        else -> target.displayName
                                    }
                                    val isTerminalAction = target == OrderStatus.ORD_CANC || target == OrderStatus.ORD_FCLO
                                    if (isTerminalAction) {
                                        OutlinedButton(
                                            onClick = {
                                                recoveryReasonInput = ""
                                                recoveryOrderTarget = ord to target
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("ops_recover_${ord.id}_${target.name.lowercase()}")
                                        ) {
                                            Text(label, fontSize = 11.sp, color = Color(0xFF991B1B))
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                recoveryReasonInput = ""
                                                recoveryOrderTarget = ord to target
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .heightIn(min = 48.dp)
                                                .testTag("ops_recover_${ord.id}_${target.name.lowercase()}")
                                        ) {
                                            Text(label, fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // All system orders overview
        if (selectedOpsTab == OperationsTab.ALL_ORDERS) {
            item {
                Spacer(modifier = Modifier.height(4.dp))
                Text("All Platform Orders (${snapshot.orders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(snapshot.orders, key = { it.id }) { ord ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable {
                            store.selectOrderForDevDrawer(ord.id)
                            onSelectOrder(ord)
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(snapshot.orderLabel(ord.id), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(
                                text = "${snapshot.pointName(ord.fulfilmentPointId)} • ${formatRupees(ord.totalAmountPaisa)} • Serial: ${ord.allocatedSerial ?: "None"}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            if (ord.fulfilmentMode == FulfilmentMode.DELIVERY) {
                                val windowText = ord.promiseLabel?.takeIf { it.isNotBlank() }
                                    ?: ord.deliveryWindowLabel?.takeIf { it.isNotBlank() }
                                if (ord.serviceLevel == ServiceLevel.EXPRESS || windowText != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(top = 3.dp)
                                    ) {
                                        if (ord.serviceLevel == ServiceLevel.EXPRESS) {
                                            Surface(color = Color(0xFFFFEDD5), shape = RoundedCornerShape(4.dp)) {
                                                Text(
                                                    text = ServiceLevel.EXPRESS.label,
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color(0xFFC2410C),
                                                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                                                )
                                            }
                                        }
                                        if (windowText != null) {
                                            Text(
                                                text = "Window: $windowText",
                                                fontSize = 10.sp,
                                                color = Color(0xFF334155),
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                            }
                        }
                        CanonicalStatusChip(ord.status)
                    }
                }
            }
        }
    }

    // Exception Recovery Reason Dialog
    val pendingRecovery = recoveryOrderTarget
    if (pendingRecovery != null) {
        val (recOrd, recTarget) = pendingRecovery
        val targetLabel = when (recTarget) {
            OrderStatus.ORD_CANC -> "Cancel order"
            OrderStatus.ORD_FCLO -> "Fail closed (retries used up)"
            else -> recTarget.displayName
        }
        AlertDialog(
            onDismissRequest = { recoveryOrderTarget = null },
            title = {
                Text("$targetLabel · ${snapshot.orderLabel(recOrd.id)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column {
                    Text(
                        text = "Enter the operational reason for this recovery step.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = recoveryReasonInput,
                        onValueChange = { recoveryReasonInput = it },
                        placeholder = { Text("Reason (required)", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ops_recover_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = recoveryReasonInput.trim()
                        if (trimmed.isEmpty()) {
                            onBanner("Please enter a reason.", true)
                        } else {
                            onBanner.report(store.recoverException(recOrd.id, recTarget, trimmed))
                            recoveryOrderTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier.testTag("ops_recover_confirm_btn")
                ) {
                    Text("Confirm")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { recoveryOrderTarget = null }) {
                    Text("Cancel")
                }
            }
        )
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
                        Text("Distributor onboarding is planned for a later phase (Later – Option C).", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                    } else {
                        Text("Select Serving Hub:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        snapshot.nodes.filter { it.type == FulfilmentPointType.HUB }.forEach { hub ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedHomePointId = hub.id }
                                    .padding(vertical = 4.dp)
                            ) {
                                RadioButton(
                                    selected = selectedHomePointId == hub.id,
                                    onClick = { selectedHomePointId = hub.id }
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("${hub.name} (${hub.city})", fontSize = 12.sp)
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(6.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "New riders are approved with Training pending and must have checklist training recorded on the Riders tab before dispatch.",
                                fontSize = 11.sp,
                                color = Color(0xFF92400E),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val res = store.approvePartner(
                            appId = app.id,
                            homePointId = selectedHomePointId,
                            trainingDone = false,
                            actor = AccountableRole.OPERATIONS.displayName
                        )
                        onBanner.report(res)
                        showApprovePartnerDialog = false
                        selectedPartnerApp = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A))
                ) {
                    Text("Approve partner")
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

    // Mark Rider Checklist Training Done Dialog
    val targetTrainingRider = riderForTraining
    if (targetTrainingRider != null) {
        AlertDialog(
            onDismissRequest = { riderForTraining = null },
            title = { Text("Mark checklist training done · ${targetTrainingRider.name}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Accountable: ${AccountableRole.OPERATIONS.displayName}. Confirm the rider completed installation and safety checklist training.",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                    OutlinedTextField(
                        value = trainingNoteInput,
                        onValueChange = { trainingNoteInput = it },
                        label = { Text("Training verification note (required)") },
                        placeholder = { Text("e.g. Passed leak check & regulator walkthrough", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ops_training_note_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    enabled = trainingNoteInput.isNotBlank(),
                    onClick = {
                        val res = store.markRiderTrainingDone(
                            riderId = targetTrainingRider.id,
                            note = trainingNoteInput.trim(),
                            actor = AccountableRole.OPERATIONS.displayName
                        )
                        onBanner.report(res)
                        if (res.first) {
                            riderForTraining = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.testTag("ops_training_confirm_btn")
                ) {
                    Text("Confirm training done")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { riderForTraining = null }) {
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
                        val res = store.returnPartnerApplication(app.id, missingDocsInput, AccountableRole.OPERATIONS.displayName)
                        onBanner.report(res)
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
                        val res = store.rejectPartner(app.id, rejectionReasonInput, AccountableRole.OPERATIONS.displayName)
                        onBanner.report(res)
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
                        val res = store.resolveSerialConflict(conf.id, winningOrderId, conflictResolutionReason, AccountableRole.OPERATIONS.displayName)
                        onBanner.report(res)
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

/**
 * Renders a single Dispatch card for a prepared delivery order with a bound cylinder awaiting
 * a rider offer from Operations (RACI-06).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationsDispatchCard(
    order: Order,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    val eligibleRiders = remember(snapshot.version, order.fulfilmentPointId) {
        snapshot.riders.filter { r ->
            r.pointId == order.fulfilmentPointId &&
                r.onboardingStatus == "APPROVED" &&
                r.status == "ACTIVE" &&
                r.installationTrained
        }
    }
    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedRiderId by remember(snapshot.version, order.id) {
        mutableStateOf(
            if (order.riderTaskStatus == RiderTaskStatus.OFFERED && order.assignedRiderId != null) {
                order.assignedRiderId!!
            } else {
                ""
            }
        )
    }
    val selectedRider = remember(snapshot.version, selectedRiderId) {
        eligibleRiders.find { it.id == selectedRiderId }
    }
    val activeJobCount: (String) -> Int = { riderId ->
        snapshot.orders.count { o ->
            o.assignedRiderId == riderId && !o.status.isTerminal && o.status != OrderStatus.ORD_EXC
        }
    }
    val declineNote = remember(snapshot.version, order.id) {
        snapshot.auditLogs
            .lastOrNull { it.entityId == order.id && (it.action == "RIDER_DECLINE_TASK" || it.action == "RIDER_OFFER_EXPIRED") }
            ?.details
            ?.removePrefix("Rider declined task: ")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("ops_dispatch_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOrder(order) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = snapshot.orderLabel(order.id),
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )
                val (chipBg, chipFg, chipText) = when (order.riderTaskStatus) {
                    RiderTaskStatus.OFFERED -> Triple(
                        Color(0xFFDBEAFE),
                        Color(0xFF1E40AF),
                        "Offered to ${snapshot.riderName(order.assignedRiderId)}"
                    )
                    RiderTaskStatus.DECLINED -> Triple(
                        Color(0xFFFEF3C7),
                        Color(0xFF92400E),
                        if (declineNote.isNullOrBlank()) "Declined" else "Declined · $declineNote"
                    )
                    else -> Triple(Color(0xFFF1F5F9), Color(0xFF334155), "Awaiting offer")
                }
                Surface(color = chipBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = chipText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = chipFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            val isAuto = order.dispatchModeAtConfirm == DispatchMode.AUTO_RIDER_FIRST
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    color = if (isAuto) Color(0xFFDBEAFE) else Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = if (isAuto) "Automatic" else "Manual",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isAuto) Color(0xFF1D4ED8) else Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Text(
                    text = "Hub: ${snapshot.pointName(order.fulfilmentPointId)} · Area: ${snapshot.addressArea(order.addressId)}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }
            if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
                val windowText = order.promiseLabel?.takeIf { it.isNotBlank() }
                    ?: order.deliveryWindowLabel?.takeIf { it.isNotBlank() }
                if (order.serviceLevel == ServiceLevel.EXPRESS || windowText != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (order.serviceLevel == ServiceLevel.EXPRESS) {
                            Surface(color = Color(0xFFFFEDD5), shape = RoundedCornerShape(4.dp)) {
                                Text(
                                    text = ServiceLevel.EXPRESS.label,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC2410C),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                        if (windowText != null) {
                            Text(
                                text = "Window: $windowText",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E293B)
                            )
                        }
                    }
                }
            }
            Text(
                text = if (isAuto && order.allocatedSerial == null) {
                    "Cylinder will be bound by hub when rider arrives"
                } else {
                    "Bound cylinder: ${order.allocatedSerial ?: "None"}"
                },
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF0F172A)
            )

            if (eligibleRiders.isEmpty()) {
                Text(
                    text = "No qualified on-shift trained riders at ${snapshot.pointName(order.fulfilmentPointId)}.",
                    fontSize = 11.sp,
                    color = Color(0xFFB45309)
                )
            } else {
                ExposedDropdownMenuBox(
                    expanded = dropdownExpanded,
                    onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                ) {
                    val fieldText = selectedRider?.let {
                        "${it.name} · On shift · ${activeJobCount(it.id)} active jobs"
                    } ?: ""
                    OutlinedTextField(
                        value = fieldText,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Select rider", fontSize = 11.sp) },
                        placeholder = { Text("Choose an eligible rider", color = Color.Gray, fontSize = 11.sp) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                            .testTag("ops_rider_dropdown_${order.id}")
                    )
                    ExposedDropdownMenu(
                        expanded = dropdownExpanded,
                        onDismissRequest = { dropdownExpanded = false }
                    ) {
                        eligibleRiders.forEach { rider ->
                            val rowLabel = "${rider.name} · On shift · ${activeJobCount(rider.id)} active jobs"
                            DropdownMenuItem(
                                text = { Text(rowLabel, fontSize = 12.sp) },
                                onClick = {
                                    selectedRiderId = rider.id
                                    dropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                Button(
                    onClick = {
                        if (selectedRiderId.isBlank()) {
                            onBanner("Choose a rider from the list first.", true)
                        } else {
                            onBanner.report(store.assignRider(order.id, selectedRiderId))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("ops_offer_rider_btn_${order.id}")
                ) {
                    Text(
                        text = if (order.riderTaskStatus == RiderTaskStatus.DECLINED) "Offer to another rider" else "Offer to rider",
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/**
 * Renders a Dispatch card for an authorized or pending cylinder return collection case (C3.3),
 * letting Operations offer a qualified on-shift rider of the customer's hub via [offerReturnCollection].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun OperationsReturnCollectionCard(
    rc: ReturnRefundCase,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner
) {
    val customerAddress = remember(snapshot.version, rc.customerId, rc.orderId) {
        val ordAddrId = snapshot.orders.find { it.id == rc.orderId }?.addressId
        snapshot.addresses.find { it.id == ordAddrId }
            ?: snapshot.addresses.find { it.customerId == rc.customerId && it.isDefault }
            ?: snapshot.addresses.firstOrNull { it.customerId == rc.customerId }
    }
    val customerHubId = remember(snapshot.version, rc.id, customerAddress?.id) {
        snapshot.orders.find { it.id == rc.orderId }?.fulfilmentPointId
            ?: customerAddress?.servingPointId
            ?: snapshot.serviceZones.find { it.id == customerAddress?.zoneId }?.servingPointIds?.firstOrNull()
    }
    val eligibleRiders = remember(snapshot.version, customerHubId) {
        snapshot.riders.filter { r ->
            (customerHubId == null || r.pointId == customerHubId) &&
                r.onboardingStatus == "APPROVED" &&
                r.status == "ACTIVE" &&
                r.installationTrained
        }
    }
    val declineReason = remember(snapshot.version, rc.id) {
        snapshot.auditLogs
            .lastOrNull { it.entityId == rc.id && it.action == "DECLINE_RETURN_COLLECTION" }
            ?.details
            ?.removePrefix("Declined: ")
    }

    var dropdownExpanded by remember { mutableStateOf(false) }
    var selectedRiderId by remember(snapshot.version, rc.id) {
        mutableStateOf(rc.collectionRiderId ?: "")
    }
    val selectedRider = remember(snapshot.version, selectedRiderId) {
        eligibleRiders.find { it.id == selectedRiderId }
    }

    val (chipBg, chipFg, chipText) = when {
        rc.collectionTask == "OFFERED" -> Triple(
            Color(0xFFDBEAFE),
            Color(0xFF1E40AF),
            "Offered to ${snapshot.riderName(rc.collectionRiderId)}"
        )
        rc.collectionTask == "ACCEPTED" -> Triple(
            Color(0xFFDCFCE7),
            Color(0xFF166534),
            "Accepted by ${snapshot.riderName(rc.collectionRiderId)}"
        )
        rc.status == L6Status.AWAITING_ITEM_RETURN && rc.collectionTask == null -> Triple(
            Color(0xFFFEF3C7),
            Color(0xFF92400E),
            if (declineReason.isNullOrBlank()) "Declined" else "Declined: $declineReason"
        )
        else -> Triple(Color(0xFFF1F5F9), Color(0xFF334155), "Awaiting rider offer")
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("ops_return_dispatch_card_${rc.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${snapshot.customerName(rc.customerId)} · ${rc.cylinderSerial ?: "No serial"}",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.weight(1f)
                )
                Surface(color = chipBg, shape = RoundedCornerShape(6.dp)) {
                    Text(
                        text = chipText,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = chipFg,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Text(
                text = "Address: ${snapshot.addressLine(customerAddress?.id).ifBlank { "Customer address on file" }}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
            Text(
                text = "Case ${rc.id} · Hub: ${snapshot.pointName(customerHubId)}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )
            if (!rc.preferredCollectionWindow.isNullOrBlank()) {
                Text(
                    text = "Customer prefers: ${rc.preferredCollectionWindow}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF1D4ED8)
                )
            }

            if (rc.status == L6Status.RETURN_AUTHORIZED) {
                if (eligibleRiders.isEmpty()) {
                    Text(
                        text = "No qualified on-shift trained riders at ${snapshot.pointName(customerHubId)}.",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309)
                    )
                } else {
                    ExposedDropdownMenuBox(
                        expanded = dropdownExpanded,
                        onExpandedChange = { dropdownExpanded = !dropdownExpanded }
                    ) {
                        OutlinedTextField(
                            value = selectedRider?.let { "${it.name} · ${snapshot.pointName(it.pointId)}" } ?: "",
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("Select rider", fontSize = 11.sp) },
                            placeholder = { Text("Choose an eligible rider", color = Color.Gray, fontSize = 11.sp) },
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = dropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                                .testTag("ops_return_rider_dropdown_${rc.id}")
                        )
                        ExposedDropdownMenu(
                            expanded = dropdownExpanded,
                            onDismissRequest = { dropdownExpanded = false }
                        ) {
                            eligibleRiders.forEach { rider ->
                                DropdownMenuItem(
                                    text = { Text("${rider.name} · ${snapshot.pointName(rider.pointId)}", fontSize = 12.sp) },
                                    onClick = {
                                        selectedRiderId = rider.id
                                        dropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }

                    Button(
                        onClick = {
                            if (selectedRiderId.isBlank()) {
                                onBanner("Choose a rider from the list first.", true)
                            } else {
                                onBanner.report(
                                    store.offerReturnCollection(
                                        caseId = rc.id,
                                        riderId = selectedRiderId,
                                        actor = AccountableRole.OPERATIONS.displayName
                                    )
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("ops_offer_return_btn_${rc.id}")
                    ) {
                        Text("Offer collection to rider", fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

/**
 * Editable Hub Minimums card (F1.4) allowing Operations Manager to update minimum filled
 * targets per hub and household cylinder size with audit trail logging.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OperationsHubMinimumsCard(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner
) {
    val hubs = remember(snapshot.version) {
        snapshot.nodes.filter { it.type == FulfilmentPointType.HUB }
    }
    val skus = remember(snapshot.version) {
        snapshot.productVariants.filter { it.isCylinder && it.isHouseholdApproved }.map { it.sku }
    }
    val draftValues = remember(snapshot.version, snapshot.settings.hubMinimumFilled) {
        mutableStateMapOf<String, String>().apply {
            hubs.forEach { hub ->
                skus.forEach { sku ->
                    val current = snapshot.settings.hubMinimumFilled[hub.id]?.get(sku) ?: 0
                    put("${hub.id}|$sku", current.toString())
                }
            }
        }
    }
    val allValid = hubs.all { hub ->
        skus.all { sku ->
            val v = draftValues["${hub.id}|$sku"]?.trim()?.toIntOrNull()
            v != null && v >= 0
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ops_hub_minimums_card")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Hub minimums",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = Color(0xFF0F172A),
                    modifier = Modifier.align(Alignment.CenterVertically)
                )
                DemoValuePill("Demo value — levels to decide after the pilot")
            }
            Text(
                text = "Accountable: ${AccountableRole.OPERATIONS.displayName}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )

            hubs.forEach { hub ->
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(hub.name, fontWeight = FontWeight.SemiBold, fontSize = 12.sp, color = Color(0xFF1E293B))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        skus.forEach { sku ->
                            val key = "${hub.id}|$sku"
                            val raw = draftValues[key] ?: ""
                            OutlinedTextField(
                                value = raw,
                                onValueChange = { draftValues[key] = it.filter { ch -> ch.isDigit() } },
                                label = { Text(snapshot.skuLabel(sku), fontSize = 10.sp) },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("ops_min_input_${hub.id}_$sku")
                            )
                        }
                    }
                }
            }

            Button(
                enabled = allValid,
                onClick = {
                    val updatedMap = mutableMapOf<String, Map<String, Int>>()
                    for (hub in hubs) {
                        val perSku = mutableMapOf<String, Int>()
                        for (sku in skus) {
                            val v = draftValues["${hub.id}|$sku"]?.trim()?.toIntOrNull()
                            if (v == null || v < 0) {
                                onBanner("Enter a whole number ≥ 0 for all hub minimums.", true)
                                return@Button
                            }
                            perSku[sku] = v
                        }
                        updatedMap[hub.id] = perSku
                    }
                    val res = store.updateSettings(
                        snapshot.settings.copy(hubMinimumFilled = updatedMap),
                        actor = AccountableRole.OPERATIONS.displayName
                    )
                    if (res.first) {
                        store.logAudit(
                            entityType = "PlatformSettings",
                            entityId = "HUB_MINIMUMS",
                            action = "UPDATE_HUB_MINIMUMS",
                            actor = AccountableRole.OPERATIONS.displayName,
                            role = AccountableRole.OPERATIONS,
                            details = updatedMap.entries.joinToString("; ") { (h, m) -> "$h: $m" }
                        )
                    }
                    onBanner.report(res)
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("ops_save_minimums_btn")
            ) {
                Text("Save hub minimums", fontSize = 12.sp)
            }
        }
    }
}
