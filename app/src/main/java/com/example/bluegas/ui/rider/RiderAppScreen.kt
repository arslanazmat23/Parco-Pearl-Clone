/**
 * Primary delivery console and task management dashboard for BLUE GAS riders.
 *
 * Implements:
 * - Real-time task feeds partitioned into Pending, Active, and Completed tabs
 * - Strict state preservation across surface switches using rememberSaveable
 * - Honest acceptance countdown with timeout enforcement (no fake timestamps)
 * - Depot-constrained cylinder serial binding and safe doorstep handover
 * - Full offline operation with idempotency-keyed outbox replay
 * - Cash on Delivery custody tracking and formal handover to depot/finance
 *
 * Reads: [SharedAppState.orders], [SharedAppState.riders], [SharedAppState.cylinders], [SharedAppState.settings]
 * Calls: [BlueGasStore.riderAcceptOrder], [BlueGasStore.riderDeclineOrder], [BlueGasStore.riderStartPreparing],
 *        [BlueGasStore.riderSelectAndBindSerial], [BlueGasStore.riderStartDeliveryOrStagePickup],
 *        [BlueGasStore.arriveAtHandover], [BlueGasStore.riderReportIssue], [BlueGasStore.riderSubmitCashHandover],
 *        [BlueGasStore.replayRiderOutbox], [BlueGasStore.simulateSerialConflict], [BlueGasStore.setRiderOffline]
 */
package com.example.bluegas.ui.rider

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.*
import com.example.bluegas.ui.customer.DemoValuePill
import com.example.bluegas.ui.customer.ReceiptDialog
import com.example.bluegas.ui.customer.customerVisibleItems
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.delay

/**
 * Filter tabs partitioning rider orders into distinct operational stages.
 */
enum class RiderTab {
    PENDING,
    ACTIVE,
    COMPLETED
}

/**
 * Invariant: Every order assigned to this rider belongs to exactly one of the three tab lists
 * (Pending + Active + Completed == total assigned orders).
 */
private fun isPendingForRider(order: Order, riderId: String): Boolean {
    return order.assignedRiderId == riderId &&
            order.status == OrderStatus.ORD_ASGN &&
            order.riderTaskStatus != RiderTaskStatus.ACCEPTED
}

private fun isActiveForRider(order: Order, riderId: String): Boolean {
    return order.assignedRiderId == riderId &&
            !order.status.isTerminal &&
            order.status != OrderStatus.ORD_ASGN &&
            order.status != OrderStatus.ORD_COMP
}

private fun isCompletedForRider(order: Order, riderId: String): Boolean {
    return order.assignedRiderId == riderId && order.status == OrderStatus.ORD_COMP
}

/**
 * Root rider application console displaying task lists, status banners, and offline outbox controls.
 *
 * @param store Application data store reference.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderAppScreen(store: BlueGasStore) {
    val context = LocalContext.current
    val appState by store.appState.collectAsStateWithLifecycle()

    var selectedTabName by rememberSaveable { mutableStateOf(RiderTab.PENDING.name) }
    val selectedTab = remember(selectedTabName) {
        runCatching { RiderTab.valueOf(selectedTabName) }.getOrDefault(RiderTab.PENDING)
    }

    var actionBanner by remember { mutableStateOf<String?>(null) }
    var isErrorBanner by remember { mutableStateOf(false) }

    // Auto-dismiss banner after 4 seconds
    LaunchedEffect(actionBanner) {
        if (actionBanner != null) {
            delay(4000L)
            actionBanner = null
        }
    }

    // Dialog states
    var showDeclineOrderId by remember { mutableStateOf<String?>(null) }
    var showSerialPickerOrderId by remember { mutableStateOf<String?>(null) }
    var showIssueOrderId by remember { mutableStateOf<String?>(null) }
    var showPodOrderId by remember { mutableStateOf<String?>(null) }
    var showOutboxDialog by remember { mutableStateOf(false) }
    var showCashHandoverDialog by remember { mutableStateOf(false) }
    var showReceiptOrderId by remember { mutableStateOf<String?>(null) }
    var showDemoControls by rememberSaveable { mutableStateOf(false) }

    val activeRider = appState.riders.find { it.id == appState.activeRiderId } ?: appState.riders.firstOrNull()
    if (activeRider == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No riders configured in the system.", fontWeight = FontWeight.Bold)
        }
        return
    }

    // Partition orders for this rider
    val pendingOrders = remember(appState.orders, activeRider.id) {
        appState.orders.filter { isPendingForRider(it, activeRider.id) }
            .sortedByDescending { it.createdAt }
    }

    val activeOrders = remember(appState.orders, activeRider.id) {
        appState.orders.filter { isActiveForRider(it, activeRider.id) }
            .sortedWith(compareByDescending { ord ->
                when (ord.status) {
                    OrderStatus.ORD_EXC -> 6
                    OrderStatus.ORD_HOC -> 5
                    OrderStatus.ORD_ATHO -> 4
                    OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> 3
                    OrderStatus.ORD_PREP -> 2
                    OrderStatus.ORD_ACPT -> 1
                    else -> 0
                }
            })
    }

    val completedOrders = remember(appState.orders, activeRider.id) {
        appState.orders.filter { isCompletedForRider(it, activeRider.id) }
            .sortedByDescending { it.completedAt ?: it.createdAt }
    }

    // Calculate pending COD cash to hand over
    val pendingCashPaisa = remember(appState.orders, appState.cashHandovers, activeRider.id) {
        val completedCodOrders = appState.orders.filter {
            it.assignedRiderId == activeRider.id &&
                    it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                    it.status == OrderStatus.ORD_COMP &&
                    appState.payments.find { p -> p.orderId == it.id }?.state == PaymentState.CASH_COLLECTED &&
                    appState.cashHandovers.none { ch -> ch.orderIds.contains(it.id) }
        }
        completedCodOrders.sumOf { it.amountReceivedPaisa ?: it.totalAmountPaisa }
    }

    // Count orders delivered on today's calendar date
    val deliveredTodayCount = remember(completedOrders) {
        completedOrders.count { it.completedAt != null && isToday(it.completedAt!!) }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9)),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 600.dp)
                .fillMaxSize(),
            shape = RoundedCornerShape(0.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.background)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. TOP BAR: RIDER IDENTITY & OFFLINE TOGGLE
                Surface(
                    color = if (appState.isRiderOffline) Color(0xFF7F1D1D) else BlueGasDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "RIDER CONSOLE",
                                    fontSize = 10.sp,
                                    color = Color(0xFF93C5FD),
                                    fontWeight = FontWeight.Bold
                                )

                                var profileDropdownExpanded by remember { mutableStateOf(false) }
                                Box {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier
                                            .clickable { profileDropdownExpanded = true }
                                            .padding(vertical = 2.dp)
                                    ) {
                                        Text(
                                            text = activeRider.name,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Switch profile",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = profileDropdownExpanded,
                                        onDismissRequest = { profileDropdownExpanded = false }
                                    ) {
                                        appState.riders.forEach { r ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text(
                                                            "${r.zoneName} · ${if (r.status == "ACTIVE") "On shift" else "Off shift"}",
                                                            fontSize = 11.sp,
                                                            color = Color.Gray
                                                        )
                                                    }
                                                },
                                                onClick = {
                                                    store.switchRider(r.id)
                                                    profileDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "${activeRider.zoneName} · ${if (activeRider.status == "ACTIVE") "On shift" else "Off shift"}",
                                    fontSize = 11.sp,
                                    color = Color(0xFFCBD5E1)
                                )
                            }

                            // Offline Toggle
                            Surface(
                                color = if (appState.isRiderOffline) Color(0xFFDC2626) else Color(0xFF334155),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (appState.isRiderOffline) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = if (appState.isRiderOffline) "Offline" else "Online",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = appState.isRiderOffline,
                                        onCheckedChange = { store.setRiderOffline(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFB91C1C)
                                        ),
                                        modifier = Modifier.height(24.dp)
                                    )
                                }
                            }
                        }

                        // Offline Banner
                        if (appState.isRiderOffline) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Surface(
                                color = Color(0xFF991B1B),
                                shape = RoundedCornerShape(6.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    text = "You are offline. Actions are saved on this phone and sent when you are back online.",
                                    fontSize = 11.sp,
                                    color = Color(0xFFFEF2F2),
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        // Stats Tiles Bar
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Tile 1: Cash to hand over (clickable to open hand-over dialog)
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { showCashHandoverDialog = true }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Cash to hand over", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = formatRupees(pendingCashPaisa),
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (pendingCashPaisa > 0) Color(0xFF38BDF8) else Color.White
                                    )
                                }
                            }

                            // Tile 2: Delivered Today
                            Surface(
                                color = Color(0xFF1E293B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Delivered Today", fontSize = 10.sp, color = Color(0xFF94A3B8))
                                    Text(
                                        text = "$deliveredTodayCount",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }

                            // Tile 3: Shift Status Toggle
                            Surface(
                                color = if (activeRider.status == "ACTIVE") Color(0xFF166534) else Color(0xFF991B1B),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { store.toggleRiderShiftStatus(activeRider.id) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Text("Shift", fontSize = 10.sp, color = Color(0xFFE2E8F0))
                                    Text(
                                        text = if (activeRider.status == "ACTIVE") "On Shift" else "Off Duty",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                        }
                    }
                }

                // 2. DEMO CONTROLS (Collapsible, explicitly labelled)
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                    shape = RoundedCornerShape(0.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showDemoControls = !showDemoControls },
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                DemoValuePill("Demo only")
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Demo & Outbox Controls",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF475569)
                                )
                            }
                            Icon(
                                imageVector = if (showDemoControls) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                                tint = Color(0xFF64748B)
                            )
                        }

                        if (showDemoControls) {
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Button(
                                    onClick = {
                                        val msg = store.simulateSerialConflict()
                                        actionBanner = msg
                                        isErrorBanner = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1.1f)
                                        .height(34.dp)
                                ) {
                                    Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Demo Conflict", fontSize = 10.sp)
                                }

                                Button(
                                    onClick = {
                                        val report = store.replayRiderOutbox()
                                        actionBanner = report
                                        isErrorBanner = false
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(34.dp),
                                    enabled = appState.riderOutbox.isNotEmpty() && !appState.isRiderOffline
                                ) {
                                    Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Sync Now (${appState.riderOutbox.size})", fontSize = 10.sp)
                                }

                                OutlinedButton(
                                    onClick = { showOutboxDialog = true },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                    modifier = Modifier
                                        .weight(0.9f)
                                        .height(34.dp)
                                ) {
                                    Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(13.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text("Outbox (${appState.riderOutbox.size})", fontSize = 10.sp)
                                }
                            }
                            if (appState.riderOutbox.isEmpty()) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Offline outbox is currently empty. Actions taken while offline will appear here.",
                                    fontSize = 10.sp,
                                    color = Color(0xFF94A3B8)
                                )
                            }
                        }
                    }
                }

                // 3. AUTO-DISMISSING ACTION BANNER (Green=success, Amber=error)
                if (actionBanner != null) {
                    Surface(
                        color = if (isErrorBanner) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Icon(
                                imageVector = if (isErrorBanner) Icons.Default.WarningAmber else Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = if (isErrorBanner) Color(0xFF92400E) else Color(0xFF166534),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = actionBanner ?: "",
                                fontSize = 12.sp,
                                color = if (isErrorBanner) Color(0xFF92400E) else Color(0xFF166534),
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { actionBanner = null }, modifier = Modifier.size(24.dp)) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "Dismiss banner",
                                    modifier = Modifier.size(14.dp),
                                    tint = if (isErrorBanner) Color(0xFF92400E) else Color(0xFF166534)
                                )
                            }
                        }
                    }
                }

                // 4. THREE DASHBOARD TABS: Pending, Active, Completed
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = BlueGasPrimary
                ) {
                    Tab(
                        selected = selectedTab == RiderTab.PENDING,
                        onClick = { selectedTabName = RiderTab.PENDING.name },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Pending", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = if (pendingOrders.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF94A3B8)) {
                                    Text("${pendingOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == RiderTab.ACTIVE,
                        onClick = { selectedTabName = RiderTab.ACTIVE.name },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Active", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = if (activeOrders.isNotEmpty()) BlueGasPrimary else Color(0xFF94A3B8)) {
                                    Text("${activeOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == RiderTab.COMPLETED,
                        onClick = { selectedTabName = RiderTab.COMPLETED.name },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Completed", fontSize = 13.sp)
                                Spacer(modifier = Modifier.width(6.dp))
                                Badge(containerColor = Color(0xFF166534)) {
                                    Text("${completedOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                }

                // 5. TAB CONTENT LIST
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    contentPadding = PaddingValues(bottom = 64.dp)
                ) {
                    when (selectedTab) {
                        RiderTab.PENDING -> {
                            if (pendingOrders.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.AssignmentLate,
                                        title = "No Pending Orders",
                                        subtitle = "No new deliveries assigned to you right now."
                                    )
                                }
                            } else {
                                items(pendingOrders) { ord ->
                                    PendingOrderCard(
                                        order = ord,
                                        snapshot = appState,
                                        onAccept = {
                                            val (ok, msg) = store.riderAcceptOrder(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onDecline = {
                                            showDeclineOrderId = ord.id
                                        }
                                    )
                                }
                            }
                        }

                        RiderTab.ACTIVE -> {
                            if (activeOrders.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.DeliveryDining,
                                        title = "No Active Deliveries",
                                        subtitle = "No deliveries in progress."
                                    )
                                }
                            } else {
                                items(activeOrders) { ord ->
                                    ActiveOrderCard(
                                        order = ord,
                                        snapshot = appState,
                                        onStartPrep = {
                                            val (ok, msg) = store.riderStartPreparing(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onScanSerial = {
                                            showSerialPickerOrderId = ord.id
                                        },
                                        onStartDelivery = {
                                            val (ok, msg) = store.riderStartDeliveryOrStagePickup(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onArrive = {
                                            val (ok, msg) = store.arriveAtHandover(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onOpenPod = {
                                            showPodOrderId = ord.id
                                        },
                                        onReportIssue = {
                                            showIssueOrderId = ord.id
                                        },
                                        onActionMessage = { msg, isErr ->
                                            actionBanner = msg
                                            isErrorBanner = isErr
                                        }
                                    )
                                }
                            }
                        }

                        RiderTab.COMPLETED -> {
                            if (completedOrders.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.CheckCircleOutline,
                                        title = "No Completed Deliveries",
                                        subtitle = "No completed deliveries yet."
                                    )
                                }
                            } else {
                                items(completedOrders) { ord ->
                                    CompletedOrderCard(
                                        order = ord,
                                        snapshot = appState,
                                        onViewReceipt = {
                                            showReceiptOrderId = ord.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // --- DIALOG 1: DECLINE ORDER DIALOG (R2.2) ---
    if (showDeclineOrderId != null) {
        val ord = appState.orders.find { it.id == showDeclineOrderId }
        var chosenReason by remember { mutableStateOf("Too far") }
        var customReason by remember { mutableStateOf("") }

        val declineOptions = listOf("Too far", "Vehicle problem", "Off shift", "Other")

        AlertDialog(
            onDismissRequest = { showDeclineOrderId = null },
            title = {
                Text("Decline Delivery Task", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        text = "This delivery will go back to your distributor to assign to another rider.",
                        fontSize = 12.sp,
                        color = Color(0xFF475569),
                        lineHeight = 16.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Select reason:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(6.dp))

                    declineOptions.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { chosenReason = r }
                                .padding(vertical = 4.dp)
                        ) {
                            RadioButton(selected = chosenReason == r, onClick = { chosenReason = r })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(r, fontSize = 13.sp)
                        }
                    }

                    if (chosenReason == "Other") {
                        Spacer(modifier = Modifier.height(6.dp))
                        OutlinedTextField(
                            value = customReason,
                            onValueChange = { customReason = it },
                            label = { Text("Please explain reason *") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            },
            confirmButton = {
                val effectiveReason = if (chosenReason == "Other") customReason.trim() else chosenReason
                Button(
                    onClick = {
                        val (ok, msg) = store.riderDeclineOrder(ord?.id ?: "", effectiveReason)
                        actionBanner = msg
                        isErrorBanner = !ok
                        showDeclineOrderId = null
                    },
                    enabled = chosenReason != "Other" || customReason.trim().isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Decline Task")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineOrderId = null }) {
                    Text("Keep Delivery")
                }
            }
        )
    }

    // --- DIALOG 2: SERIAL NUMBER PICKER (R3.3) ---
    if (showSerialPickerOrderId != null) {
        val ord = appState.orders.find { it.id == showSerialPickerOrderId }
        val targetSku = ord?.items?.firstOrNull { it.lineType == "GAS" }?.sku ?: ord?.items?.firstOrNull()?.sku ?: "SKU-LPG-11-8"

        // Filter: SKU, available, not on HSE hold, pointId matches order's fulfilmentPointId
        val depotCylinders = appState.cylinders.filter {
            it.sku == targetSku &&
                    (it.status == InventoryState.INV_AVAIL || it.allocatedOrderId == ord?.id) &&
                    !it.hseHold &&
                    (ord?.fulfilmentPointId == null || it.pointId == ord.fulfilmentPointId)
        }

        AlertDialog(
            onDismissRequest = { showSerialPickerOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Choose the cylinder you are loading", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (depotCylinders.isEmpty()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "No suitable cylinder is available at this depot. Ask Operations.",
                                    color = Color(0xFF991B1B),
                                    fontSize = 12.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = {
                                        showSerialPickerOrderId = null
                                        showIssueOrderId = ord?.id
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                                ) {
                                    Text("Report a problem", fontSize = 11.sp)
                                }
                            }
                        }
                    } else {
                        depotCylinders.forEach { cyl ->
                            val isSelected = ord?.allocatedSerial == cyl.serialNumber
                            Card(
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                                ),
                                border = BorderStroke(
                                    if (isSelected) 2.dp else 1.dp,
                                    if (isSelected) BlueGasPrimary else Color(0xFFCBD5E1)
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        val (ok, msg) = store.riderSelectAndBindSerial(ord?.id ?: "", cyl.serialNumber)
                                        actionBanner = msg
                                        isErrorBanner = !ok
                                        showSerialPickerOrderId = null
                                    }
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(cyl.serialNumber, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        Text("${cyl.weightKg} kg cylinder", fontSize = 11.sp, color = Color(0xFF64748B))
                                    }
                                    if (isSelected) {
                                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSerialPickerOrderId = null }) { Text("Close") }
            }
        )
    }

    // --- DIALOG 3: REPORT A PROBLEM (R4) ---
    if (showIssueOrderId != null) {
        val ord = appState.orders.find { it.id == showIssueOrderId }
        var issueReason by remember { mutableStateOf(ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE) }
        var issueDetails by remember { mutableStateOf("") }
        var isSafetyConcern by remember { mutableStateOf(false) }

        val permittedReasons = listOf(
            ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
            ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE,
            ExceptionReason.VALIDATION_OUT_OF_AREA,
            ExceptionReason.UNRECOVERABLE_AFTER_RETRIES
        )

        AlertDialog(
            onDismissRequest = { showIssueOrderId = null },
            title = {
                Text("Report a problem", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Text("Select reason:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(4.dp))

                    permittedReasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { issueReason = r }
                                .padding(vertical = 3.dp)
                        ) {
                            RadioButton(selected = issueReason == r, onClick = { issueReason = r })
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(r.description, fontSize = 12.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { isSafetyConcern = !isSafetyConcern }
                    ) {
                        Checkbox(
                            checked = isSafetyConcern,
                            onCheckedChange = { isSafetyConcern = it }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "This is a safety concern (for example a smell of gas, a leak, or damage)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFFB91C1C)
                        )
                    }

                    if (isSafetyConcern) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "HSE will be alerted. Follow the approved BLUE GAS HSE procedure.",
                                fontSize = 11.sp,
                                color = Color(0xFF991B1B),
                                modifier = Modifier.padding(8.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = issueDetails,
                        onValueChange = { issueDetails = it },
                        label = { Text("Details") },
                        placeholder = { Text("Explain what happened...") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                }
            },
            confirmButton = {
                val canSubmit = isSafetyConcern || issueDetails.trim().length >= 5
                Button(
                    onClick = {
                        val (ok, msg) = store.riderReportIssue(
                            orderId = ord?.id ?: "",
                            reason = issueReason,
                            details = issueDetails.trim().ifBlank { "Safety concern reported at doorstep." },
                            safetyConcern = isSafetyConcern
                        )
                        actionBanner = msg
                        isErrorBanner = !ok
                        showIssueOrderId = null
                    },
                    enabled = canSubmit,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Submit Report")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueOrderId = null }) { Text("Cancel") }
            }
        )
    }

    // --- DIALOG 4: CASH HANDOVER (R5.3) ---
    if (showCashHandoverDialog) {
        val completedCodOrders = appState.orders.filter {
            it.assignedRiderId == activeRider.id &&
                    it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                    it.status == OrderStatus.ORD_COMP &&
                    appState.payments.find { p -> p.orderId == it.id }?.state == PaymentState.CASH_COLLECTED &&
                    appState.cashHandovers.none { ch -> ch.orderIds.contains(it.id) }
        }
        val expectedPaisa = completedCodOrders.sumOf { it.amountReceivedPaisa ?: it.totalAmountPaisa }

        var declaredRupees by remember(expectedPaisa) {
            mutableStateOf((expectedPaisa / 100).toString())
        }
        var recipient by remember { mutableStateOf("Distributor Lead") }
        var differenceReason by remember { mutableStateOf("") }

        val declaredPaisa = (declaredRupees.trim().toLongOrNull() ?: 0L) * 100L
        val hasDifference = declaredPaisa != expectedPaisa
        val canSubmit = completedCodOrders.isNotEmpty() && (!hasDifference || differenceReason.trim().isNotBlank())

        AlertDialog(
            onDismissRequest = { showCashHandoverDialog = false },
            title = {
                Text("Cash Hand-Over", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (completedCodOrders.isEmpty()) {
                        Text(
                            text = "No cash waiting to be handed over.",
                            fontSize = 13.sp,
                            color = Color(0xFF64748B)
                        )
                    } else {
                        Text(
                            text = "Orders in pending collection (${completedCodOrders.size}):",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Spacer(modifier = Modifier.height(6.dp))

                        completedCodOrders.forEach { ord ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 2.dp),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(ord.id, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(
                                    formatRupees(ord.amountReceivedPaisa ?: ord.totalAmountPaisa),
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text("Expected Total:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(formatRupees(expectedPaisa), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text("Hand over to:", fontSize = 12.sp, fontWeight = FontWeight.Medium)
                        Spacer(modifier = Modifier.height(4.dp))
                        listOf("Distributor Lead", "Depot Cashier", "Finance Officer").forEach { r ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { recipient = r }
                            ) {
                                RadioButton(selected = recipient == r, onClick = { recipient = r })
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(r, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        OutlinedTextField(
                            value = declaredRupees,
                            onValueChange = { if (it.all { c -> c.isDigit() }) declaredRupees = it },
                            label = { Text("Declared Amount (Rupees)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (hasDifference) {
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = differenceReason,
                                onValueChange = { differenceReason = it },
                                label = { Text("Reason for amount difference *") },
                                placeholder = { Text("Explain discrepancy") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Text(
                            text = "Hand over your cash within ${appState.settings.cashHandoverHours} hours.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            },
            confirmButton = {
                if (completedCodOrders.isNotEmpty()) {
                    Button(
                        onClick = {
                            val (ok, msg) = store.riderSubmitCashHandover(
                                riderId = activeRider.id,
                                recipient = recipient,
                                declaredPaisa = declaredPaisa,
                                reason = differenceReason.ifBlank { null }
                            )
                            actionBanner = msg
                            isErrorBanner = !ok
                            showCashHandoverDialog = false
                        },
                        enabled = canSubmit
                    ) {
                        Text("Submit Hand-Over")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showCashHandoverDialog = false }) { Text("Close") }
            }
        )
    }

    // --- DIALOG 5: PROOF OF DELIVERY (POD) FLOW ---
    if (showPodOrderId != null) {
        val ord = appState.orders.find { it.id == showPodOrderId }
        if (ord != null) {
            PodCaptureDialog(
                order = ord,
                store = store,
                onDismiss = { showPodOrderId = null },
                onCompleted = { ok, msg ->
                    showPodOrderId = null
                    actionBanner = msg
                    isErrorBanner = !ok
                }
            )
        }
    }

    // --- DIALOG 6: OUTBOX VIEWER (R7) ---
    if (showOutboxDialog) {
        AlertDialog(
            onDismissRequest = { showOutboxDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Inbox, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Offline Outbox Queue", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    if (appState.riderOutbox.isEmpty()) {
                        Text("Offline outbox is currently empty.", fontSize = 12.sp, color = Color(0xFF64748B))
                    } else {
                        appState.riderOutbox.forEach { evt ->
                            val humanAction = when (evt.action) {
                                "START_TRIP" -> "Start trip"
                                "ARRIVE_AT_DOORSTEP" -> "Arrived"
                                "REPORT_ISSUE" -> "Report problem"
                                "COMPLETE_HANDOVER" -> "Complete delivery"
                                "START_PREPARING" -> "Start preparation"
                                else -> evt.action
                            }

                            val humanStatus = when (evt.syncStatus) {
                                "PENDING" -> "Waiting"
                                "APPLIED" -> "Sent"
                                "REJECTED" -> "Rejected"
                                "CONFLICT_QUEUED" -> "Needs Operations review"
                                "DUPLICATE_IGNORED" -> "Duplicate ignored"
                                else -> evt.syncStatus
                            }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
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
                                        Text(humanAction, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                        Surface(
                                            color = when (evt.syncStatus) {
                                                "APPLIED" -> Color(0xFFDCFCE7)
                                                "REJECTED" -> Color(0xFFFEE2E2)
                                                "CONFLICT_QUEUED" -> Color(0xFFFEF3C7)
                                                else -> Color(0xFFE2E8F0)
                                            },
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                text = humanStatus,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text("Order: ${evt.orderId}", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                    if (evt.rejectionReason != null) {
                                        Text("Reason: ${evt.rejectionReason}", fontSize = 10.sp, color = Color(0xFFDC2626))
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (appState.riderOutbox.isNotEmpty() && !appState.isRiderOffline) {
                    Button(
                        onClick = {
                            val report = store.replayRiderOutbox()
                            actionBanner = report
                            isErrorBanner = false
                            showOutboxDialog = false
                        }
                    ) {
                        Text("Sync All Outbox")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { showOutboxDialog = false }) { Text("Close") }
            }
        )
    }

    // --- DIALOG 7: OFFICIAL RECEIPT MODAL (R6 - Reuses Customer ReceiptDialog) ---
    if (showReceiptOrderId != null) {
        val ord = appState.orders.find { it.id == showReceiptOrderId }
        if (ord != null) {
            ReceiptDialog(
                order = ord,
                snapshot = appState,
                onDismiss = { showReceiptOrderId = null }
            )
        }
    }
}

/**
 * Card representing a newly assigned order awaiting rider acceptance or decline.
 */
@Composable
private fun PendingOrderCard(
    order: Order,
    snapshot: SharedAppState,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val cust = snapshot.customers.find { it.id == order.customerId }
    val addr = snapshot.addresses.find { it.id == order.addressId }

    val customerFirstName = cust?.name?.split(" ")?.firstOrNull() ?: cust?.name ?: "Customer"
    val areaText = addr?.area ?: addr?.city ?: "Local area"

    // Honest acceptance countdown
    val nowMillis = rememberNowMillis(1000L)
    val assignedTime = order.assignedAt ?: order.createdAt
    val windowMs = snapshot.settings.distributorAcceptanceWindowMins * 60 * 1000L
    val deadline = assignedTime + windowMs
    val remainingMs = (deadline - nowMillis).coerceAtLeast(0L)
    val isExpired = remainingMs <= 0L
    val isExpiringSoon = remainingMs in 1L..300000L

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("pending_order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.id,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BlueGasDark
                )
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Acceptance Countdown Chip
            Surface(
                color = when {
                    isExpired -> Color(0xFFFEF3C7)
                    isExpiringSoon -> Color(0xFFFEE2E2)
                    else -> Color(0xFFEFF6FF)
                },
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isExpired || isExpiringSoon) Color(0xFFDC2626) else BlueGasPrimary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isExpired) "Acceptance time is over" else "Acceptance Window: ${formatCountdown(remainingMs)} remaining",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpired || isExpiringSoon) Color(0xFFDC2626) else BlueGasDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (order.assignedAt != null) {
                        Text(
                            text = "Assigned ${formatTimeOnly(order.assignedAt!!)}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Text(
                text = "$customerFirstName · $areaText",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlueGasDark
            )

            Text(
                text = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Home delivery" else "Depot pickup",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(6.dp))
            order.customerVisibleItems().forEach { itm ->
                Text(
                    text = "• ${itm.quantity}x ${itm.productName}",
                    fontSize = 12.sp,
                    color = Color(0xFF334155)
                )
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
                    "Cash on delivery — collect ${formatRupees(order.totalAmountPaisa)}"
                } else {
                    "Paid online"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFD97706) else Color(0xFF16A34A)
            )

            // Buttons: Accept & Decline
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    enabled = !isExpired,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                        .testTag("accept_order_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept Order", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    border = BorderStroke(1.dp, Color(0xFFDC2626)),
                    modifier = Modifier
                        .weight(0.9f)
                        .height(48.dp)
                        .testTag("decline_order_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Card representing an active delivery task progressing from preparation to doorstep handover.
 */
@Composable
private fun ActiveOrderCard(
    order: Order,
    snapshot: SharedAppState,
    onStartPrep: () -> Unit,
    onScanSerial: () -> Unit,
    onStartDelivery: () -> Unit,
    onArrive: () -> Unit,
    onOpenPod: () -> Unit,
    onReportIssue: () -> Unit,
    onActionMessage: (String, Boolean) -> Unit
) {
    val context = LocalContext.current
    val cust = snapshot.customers.find { it.id == order.customerId }
    val addr = snapshot.addresses.find { it.id == order.addressId }
    var addressExpanded by remember { mutableStateOf(false) }

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
            .testTag("active_order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.id,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BlueGasDark
                )
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = cust?.name ?: "Customer",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = BlueGasDark
            )

            // Tap-to-expand address
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { addressExpanded = !addressExpanded }
                    .padding(vertical = 2.dp)
            ) {
                Text(
                    text = "${addr?.area ?: ""}, ${addr?.city ?: ""}" + if (!addressExpanded) " (Tap to view full address)" else "",
                    fontSize = 12.sp,
                    color = Color(0xFF475569)
                )
                if (addressExpanded) {
                    Text(
                        text = "${addr?.street ?: ""}, ${addr?.area ?: ""}, ${addr?.city ?: ""}",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Customer Actions: Call & Navigate
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        val phone = cust?.phone
                        if (!phone.isNullOrBlank()) {
                            try {
                                val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phone"))
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onActionMessage("No phone dialer application available.", true)
                            }
                        } else {
                            onActionMessage("Customer phone number not available.", true)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Call customer", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = {
                        val addressStr = listOfNotNull(addr?.street, addr?.area, addr?.city).filter { it.isNotBlank() }.joinToString(", ")
                        if (addressStr.isNotBlank()) {
                            try {
                                val uri = Uri.parse("geo:0,0?q=${Uri.encode(addressStr)}")
                                val intent = Intent(Intent.ACTION_VIEW, uri)
                                context.startActivity(intent)
                            } catch (_: Exception) {
                                onActionMessage("No map application available.", true)
                            }
                        } else {
                            onActionMessage("Delivery address not available.", true)
                        }
                    },
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp)
                ) {
                    Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Navigate", fontSize = 12.sp)
                }
            }

            // Serial Allocation Strip
            Spacer(modifier = Modifier.height(10.dp))
            Surface(
                color = if (order.allocatedSerial != null) Color(0xFFEFF6FF) else Color(0xFFFFFBEB),
                shape = RoundedCornerShape(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (order.allocatedSerial != null) Icons.Default.QrCodeScanner else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (order.allocatedSerial != null) Color(0xFF0284C7) else Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Serial: ${order.allocatedSerial ?: "Choose cylinder first"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = if (order.allocatedSerial != null) Color(0xFF0369A1) else Color(0xFFB45309),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    if (order.status == OrderStatus.ORD_PREP) {
                        TextButton(
                            onClick = onScanSerial,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text(if (order.allocatedSerial != null) "Change" else "Choose", fontSize = 11.sp)
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))
            order.customerVisibleItems().forEach { itm ->
                Text("• ${itm.quantity}x ${itm.productName}", fontSize = 12.sp, color = Color(0xFF334155))
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
                    "Cash on delivery — collect ${formatRupees(order.totalAmountPaisa)}"
                } else {
                    "Paid online"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFD97706) else Color(0xFF16A34A)
            )

            // Handover PIN reminder (R3.1: never show the PIN itself on the rider side)
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Ask the customer for their 4-digit handover code.",
                fontSize = 11.sp,
                color = Color(0xFF64748B),
                fontWeight = FontWeight.Medium
            )

            // ACTION CONTROLS PER STATUS (R3)
            Spacer(modifier = Modifier.height(12.dp))

            when (order.status) {
                OrderStatus.ORD_EXC -> {
                    // Amber strip for exceptions, no action buttons
                    Surface(
                        color = Color(0xFFFEF3C7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.WarningAmber, contentDescription = null, tint = Color(0xFFB45309))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Needs attention — Operations has been alerted",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF92400E)
                            )
                        }
                    }
                }

                OrderStatus.ORD_INSP, OrderStatus.ORD_ADJ -> {
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.HourglassEmpty, contentDescription = null, tint = Color(0xFF475569))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Waiting for the inspection / price adjustment to be completed",
                                fontSize = 12.sp,
                                color = Color(0xFF334155)
                            )
                        }
                    }
                }

                OrderStatus.ORD_ACPT -> {
                    Button(
                        onClick = onStartPrep,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp)
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Begin Preparation & Select Serial")
                    }
                }

                OrderStatus.ORD_PREP -> {
                    Column(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = onStartDelivery,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                enabled = order.allocatedSerial != null,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(48.dp)
                            ) {
                                Icon(Icons.Default.DepartureBoard, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Start Delivery")
                            }

                            OutlinedButton(
                                onClick = onReportIssue,
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("Report issue")
                            }
                        }

                        if (order.allocatedSerial == null) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Choose the cylinder to deliver first.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8)
                            )
                        } else if (snapshot.isRiderOffline) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "You are offline — this will be saved and sent later.",
                                fontSize = 11.sp,
                                color = Color(0xFFB45309)
                            )
                        }
                    }
                }

                OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onArrive,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Arrived at location")
                        }

                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Report issue")
                        }
                    }
                }

                OrderStatus.ORD_ATHO -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onOpenPod,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Complete delivery")
                        }

                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(48.dp)
                        ) {
                            Text("Report issue")
                        }
                    }
                }

                OrderStatus.ORD_HOC -> {
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF166534))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Delivery is being finalised.",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 12.sp,
                                color = Color(0xFF166534)
                            )
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

/**
 * Card representing a successfully delivered and verified order.
 */
@Composable
private fun CompletedOrderCard(
    order: Order,
    snapshot: SharedAppState,
    onViewReceipt: () -> Unit
) {
    val cust = snapshot.customers.find { it.id == order.customerId }
    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY

    // Check cash handover reconciliation status
    val isHandedOver = isCod && snapshot.cashHandovers.any { it.orderIds.contains(order.id) }
    val payment = snapshot.payments.find { it.orderId == order.id }
    val isReconciled = payment?.state == PaymentState.SETTLED

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .testTag("completed_order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = order.id,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    color = BlueGasDark
                )
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = cust?.name ?: "Customer",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = BlueGasDark
            )

            if (order.completedAt != null) {
                Text(
                    text = "Completed: ${formatDateTime(order.completedAt!!)}",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isCod) {
                    "Cash collected: ${formatRupees(order.amountReceivedPaisa ?: order.totalAmountPaisa)}"
                } else {
                    "Paid online"
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = if (isCod) Color(0xFFD97706) else Color(0xFF16A34A)
            )

            Text(
                text = "Installation: ${order.installationResult?.displayName ?: "Completed"}",
                fontSize = 11.sp,
                color = Color(0xFF475569)
            )

            if (order.podReference != null) {
                Text(
                    text = "POD: ${order.podReference}",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFF64748B)
                )
            }

            if (isCod) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = when {
                        isReconciled -> Color(0xFFDCFCE7)
                        isHandedOver -> Color(0xFFEFF6FF)
                        else -> Color(0xFFFEF3C7)
                    },
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when {
                            isReconciled -> "Payment reconciled by Finance"
                            isHandedOver -> "Cash handed over — awaiting Finance"
                            else -> "Cash not yet handed over"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = when {
                            isReconciled -> Color(0xFF166534)
                            isHandedOver -> Color(0xFF1D4ED8)
                            else -> Color(0xFF92400E)
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = onViewReceipt,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(48.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("View receipt", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Standard empty state presentation for tabs with no active orders.
 */
@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(12.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color(0xFF475569))
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = subtitle,
            fontSize = 12.sp,
            color = Color(0xFF64748B),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
    }
}
