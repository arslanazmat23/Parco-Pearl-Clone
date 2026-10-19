package com.example.bluegas.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.delay

enum class RiderTab {
    PENDING,
    ACTIVE,
    COMPLETED
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RiderAppScreen(store: BlueGasStore) {
    val appState by store.appState.collectAsStateWithLifecycle()
    var selectedTab by remember { mutableStateOf(RiderTab.PENDING) }
    var actionBanner by remember { mutableStateOf<String?>(null) }
    var isErrorBanner by remember { mutableStateOf(false) }

    // Dialogs
    var showDeclineDialog by remember { mutableStateOf(false) }
    var orderToDeclineId by remember { mutableStateOf<String?>(null) }
    var declineReason by remember { mutableStateOf("Vehicle breakdown / maintenance issue") }

    var showSerialPicker by remember { mutableStateOf(false) }
    var orderForSerialId by remember { mutableStateOf<String?>(null) }

    var showIssueDialog by remember { mutableStateOf(false) }
    var orderForIssueId by remember { mutableStateOf<String?>(null) }
    var issueReason by remember { mutableStateOf(ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE) }
    var issueDetailText by remember { mutableStateOf("Customer unavailable after 3 call attempts.") }

    var showPodDialog by remember { mutableStateOf(false) }
    var orderForPodId by remember { mutableStateOf<String?>(null) }

    var showOutboxDialog by remember { mutableStateOf(false) }
    var showReceiptOrderId by remember { mutableStateOf<String?>(null) }

    val activeRider = store.getActiveRider() ?: store.riders.firstOrNull()
    if (activeRider == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No riders configured. Please contact admin.", fontWeight = FontWeight.Bold)
        }
        return
    }

    // Filter orders: Rider sees ONLY orders assigned to them, reaching ORD_ASGN or beyond
    val riderOrders = appState.orders.filter {
        it.assignedRiderId == activeRider.id && it.status.ordinal >= OrderStatus.ORD_ASGN.ordinal
    }

    val pendingOrders = riderOrders.filter { it.status == OrderStatus.ORD_ASGN }
    val activeOrders = riderOrders.filter {
        it.status in listOf(
            OrderStatus.ORD_ACPT,
            OrderStatus.ORD_PREP,
            OrderStatus.ORD_DISP,
            OrderStatus.ORD_RDYPU,
            OrderStatus.ORD_ATHO,
            OrderStatus.ORD_HOC
        )
    }.sortedWith(compareByDescending { ord ->
        when (ord.status) {
            OrderStatus.ORD_HOC -> 5
            OrderStatus.ORD_ATHO -> 4
            OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> 3
            OrderStatus.ORD_PREP -> 2
            OrderStatus.ORD_ACPT -> 1
            else -> 0
        }
    })
    val completedOrders = riderOrders.filter { it.status == OrderStatus.ORD_COMP }

    // Today's stats calculation
    val todayEarningsPaisa = completedOrders.size * 25000L

    // Phone frame centered container (390x844 format)
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE2E8F0)),
        contentAlignment = Alignment.TopCenter
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxHeight()
                .padding(vertical = 8.dp),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {

                // 1. HEADER WITH OFFLINE TOGGLE & PROFILE SWITCHER
                Surface(
                    color = if (appState.isRiderOffline) Color(0xFF7F1D1D) else BlueGasDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("RIDER DELIVERY CONSOLE", fontSize = 10.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold)

                                // Profile Switcher Dropdown
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
                                            fontWeight = FontWeight.Bold
                                        )
                                        Icon(
                                            Icons.Default.ArrowDropDown,
                                            contentDescription = "Switch Rider Profile",
                                            tint = Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }

                                    DropdownMenu(
                                        expanded = profileDropdownExpanded,
                                        onDismissRequest = { profileDropdownExpanded = false }
                                    ) {
                                        store.riders.forEach { r ->
                                            DropdownMenuItem(
                                                text = {
                                                    Column {
                                                        Text(r.name, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                                        Text("${r.zoneName} • ${r.status}", fontSize = 11.sp, color = Color.Gray)
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
                                    "${activeRider.vehicleNumber} • ${activeRider.zoneName}",
                                    fontSize = 10.sp,
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
                                        if (appState.isRiderOffline) "OFFLINE" else "ONLINE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = appState.isRiderOffline,
                                        onCheckedChange = {
                                            store.isRiderOffline = it
                                            store.notifyChanged()
                                        },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = Color(0xFFB91C1C)
                                        ),
                                        modifier = Modifier.height(20.dp)
                                    )
                                }
                            }
                        }

                        // Offline status warning banner
                        if (appState.isRiderOffline) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF991B1B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "⚠️ Offline Mode: Actions queued to Local Outbox with idempotency keys. Replay on reconnect.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFEF2F2),
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        // Today's Stats Bar
                        Surface(
                            color = Color(0xFF1E293B),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Delivered Today", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text("${completedOrders.size} Orders", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Rider Earnings", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Text(formatPaisaToPkr(todayEarningsPaisa), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color(0xFF38BDF8))
                                }
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Shift Status", fontSize = 9.sp, color = Color(0xFF94A3B8))
                                    Surface(
                                        color = if (activeRider.status == "ACTIVE") Color(0xFF166534) else Color(0xFF7F1D1D),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier.clickable {
                                            store.toggleRiderShiftStatus(activeRider.id)
                                        }
                                    ) {
                                        Text(
                                            if (activeRider.status == "ACTIVE") "ON SHIFT" else "OFF DUTY",
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // 2. DEMO CONTROLS BAR: Simulate Conflict & Outbox Sync
                Surface(
                    color = Color(0xFFF1F5F9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 6.dp),
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
                                .height(32.dp)
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
                                .height(32.dp),
                            enabled = appState.riderOutbox.isNotEmpty() && !appState.isRiderOffline
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Sync (${appState.riderOutbox.size})", fontSize = 10.sp)
                        }

                        OutlinedButton(
                            onClick = { showOutboxDialog = true },
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(0.9f)
                                .height(32.dp)
                        ) {
                            Icon(Icons.Default.Inbox, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Outbox (${appState.riderOutbox.size})", fontSize = 10.sp)
                        }
                    }
                }

                // 3. ACTION / ERROR BANNER
                if (actionBanner != null) {
                    Surface(
                        color = if (isErrorBanner) Color(0xFFFEE2E2) else Color(0xFFFEF3C7),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                actionBanner ?: "",
                                fontSize = 11.sp,
                                color = if (isErrorBanner) Color(0xFF991B1B) else Color(0xFF78350F),
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { actionBanner = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // If Rider is inactive, show inactive notice
                if (activeRider.status != "ACTIVE") {
                    Surface(
                        color = Color(0xFFFEF2F2),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Rider Off-Duty", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C), fontSize = 12.sp)
                                Text("${activeRider.name} is currently off-duty. Start shift to begin receiving deliveries.", fontSize = 10.sp, color = Color(0xFF7F1D1D))
                            }
                            Button(
                                onClick = { store.toggleRiderShiftStatus(activeRider.id) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Start Shift", fontSize = 10.sp)
                            }
                        }
                    }
                }

                // 4. THREE DASHBOARD TABS: Pending, Active, Completed
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = Color.White,
                    contentColor = BlueGasPrimary
                ) {
                    Tab(
                        selected = selectedTab == RiderTab.PENDING,
                        onClick = { selectedTab = RiderTab.PENDING },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Pending", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = if (pendingOrders.isNotEmpty()) Color(0xFFDC2626) else Color(0xFF94A3B8)) {
                                    Text("${pendingOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == RiderTab.ACTIVE,
                        onClick = { selectedTab = RiderTab.ACTIVE },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Active", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = if (activeOrders.isNotEmpty()) Color(0xFF0284C7) else Color(0xFF94A3B8)) {
                                    Text("${activeOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == RiderTab.COMPLETED,
                        onClick = { selectedTab = RiderTab.COMPLETED },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("Completed", fontSize = 12.sp)
                                Spacer(modifier = Modifier.width(4.dp))
                                Badge(containerColor = Color(0xFF16A34A)) {
                                    Text("${completedOrders.size}", color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    )
                }

                // 5. TAB CONTENT
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(10.dp)
                ) {
                    when (selectedTab) {
                        RiderTab.PENDING -> {
                            if (pendingOrders.isEmpty()) {
                                item {
                                    EmptyStateView(
                                        icon = Icons.Default.AssignmentLate,
                                        title = "No Pending Orders",
                                        subtitle = "You have no orders in ORD_ASGN awaiting acceptance."
                                    )
                                }
                            } else {
                                items(pendingOrders) { ord ->
                                    PendingOrderCard(
                                        order = ord,
                                        store = store,
                                        onAccept = {
                                            val (ok, msg) = store.riderAcceptOrder(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onDecline = {
                                            orderToDeclineId = ord.id
                                            showDeclineDialog = true
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
                                        subtitle = "Accept pending assignments to begin preparation & dispatch."
                                    )
                                }
                            } else {
                                items(activeOrders) { ord ->
                                    ActiveOrderCard(
                                        order = ord,
                                        store = store,
                                        onStartPrep = {
                                            val (ok, msg) = store.riderStartPreparing(ord.id)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                        },
                                        onScanSerial = {
                                            orderForSerialId = ord.id
                                            showSerialPicker = true
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
                                            orderForPodId = ord.id
                                            showPodDialog = true
                                        },
                                        onReportIssue = {
                                            orderForIssueId = ord.id
                                            showIssueDialog = true
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
                                        title = "No Completed Deliveries Today",
                                        subtitle = "Orders with completed handover and safety verification appear here."
                                    )
                                }
                            } else {
                                items(completedOrders) { ord ->
                                    CompletedOrderCard(
                                        order = ord,
                                        store = store,
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

    // --- DIALOG 1: DECLINE ORDER DIALOG ---
    if (showDeclineDialog && orderToDeclineId != null) {
        val ord = appState.orders.find { it.id == orderToDeclineId }
        AlertDialog(
            onDismissRequest = { showDeclineDialog = false },
            title = { Text("Decline Order Assignment", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Declining moves order to ORD_EXC with reason 'no_acceptance' for Ops reassignment.", fontSize = 11.sp, color = Color(0xFFB45309))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select Reason:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    listOf(
                        "Vehicle breakdown / maintenance issue",
                        "Out of cargo capacity",
                        "Outside shift operating hours",
                        "Traffic gridlock / unreachable route"
                    ).forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { declineReason = r }
                        ) {
                            RadioButton(selected = declineReason == r, onClick = { declineReason = r })
                            Text(r, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.riderDeclineOrder(orderToDeclineId ?: "", declineReason)
                        actionBanner = msg
                        isErrorBanner = !ok
                        showDeclineDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Confirm Decline")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeclineDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- DIALOG 2: SERIAL NUMBER SCANNER / ALLOCATION ---
    if (showSerialPicker && orderForSerialId != null) {
        val ord = appState.orders.find { it.id == orderForSerialId }
        val availableCylinders = store.cylinders.filter {
            (it.status == InventoryState.INV_AVAIL || it.allocatedOrderId == ord?.id) &&
            it.sku == (ord?.items?.firstOrNull()?.sku ?: "SKU-LPG-11-8")
        }

        AlertDialog(
            onDismissRequest = { showSerialPicker = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Scan / Allocate Exact Serial", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Select from physical inventory at assigned terminal:", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (availableCylinders.isEmpty()) {
                        Text("No hydro-tested cylinders available for this SKU in inventory!", color = Color.Red, fontSize = 11.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(availableCylinders) { cyl ->
                                val isSelected = ord?.allocatedSerial == cyl.serialNumber
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (isSelected) Color(0xFFEFF6FF) else Color(0xFFF8FAFC)
                                    ),
                                    border = androidx.compose.foundation.BorderStroke(
                                        if (isSelected) 2.dp else 1.dp,
                                        if (isSelected) BlueGasPrimary else Color(0xFFCBD5E1)
                                    ),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp)
                                        .clickable {
                                            val (ok, msg) = store.riderSelectAndBindSerial(ord?.id ?: "", cyl.serialNumber)
                                            actionBanner = msg
                                            isErrorBanner = !ok
                                            showSerialPicker = false
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(cyl.serialNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                            Text("${cyl.weightKg}kg • Hydro-test: ${cyl.lastInspectionDate}", fontSize = 10.sp, color = Color.Gray)
                                        }
                                        if (isSelected) {
                                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showSerialPicker = false }) { Text("Close") }
            }
        )
    }

    // --- DIALOG 3: REPORT ISSUE / EXCEPTION ---
    if (showIssueDialog && orderForIssueId != null) {
        val ord = appState.orders.find { it.id == orderForIssueId }
        AlertDialog(
            onDismissRequest = { showIssueDialog = false },
            title = { Text("Report Fulfilment Issue", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text("Reporting an issue transitions this order to ORD_EXC for Operations recovery.", fontSize = 11.sp, color = Color(0xFFB45309))
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select Exception:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)

                    val reasons: List<Pair<ExceptionReason, String>> = listOf(
                        ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE to "Customer Unavailable / Door Closed",
                        ExceptionReason.VALIDATION_OUT_OF_AREA to "Wrong Address / Access Denied",
                        ExceptionReason.UNRECOVERABLE_AFTER_RETRIES to "Customer Refused Delivery / Cancel Requested",
                        ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE to "Damaged Cylinder Valve / Leakage Detected"
                    )
                    reasons.forEach { (r, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { issueReason = r }
                        ) {
                            RadioButton(selected = issueReason == r, onClick = { issueReason = r })
                            Text(label, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = issueDetailText,
                        onValueChange = { issueDetailText = it },
                        placeholder = { Text("Enter additional notes...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.riderReportIssue(
                            orderId = orderForIssueId ?: "",
                            reason = issueReason,
                            details = issueDetailText
                        )
                        actionBanner = msg
                        isErrorBanner = !ok
                        showIssueDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Report Exception")
                }
            },
            dismissButton = {
                TextButton(onClick = { showIssueDialog = false }) { Text("Cancel") }
            }
        )
    }

    // --- DIALOG 4: MULTI-STEP PROOF OF DELIVERY (POD) FLOW ---
    if (showPodDialog && orderForPodId != null) {
        val ord = appState.orders.find { it.id == orderForPodId }
        if (ord != null) {
            PodCaptureDialog(
                order = ord,
                store = store,
                onDismiss = { showPodDialog = false },
                onCompleted = { ok, msg ->
                    actionBanner = msg
                    isErrorBanner = !ok
                    showPodDialog = false
                }
            )
        }
    }

    // --- DIALOG 5: OUTBOX DRAWER / QUEUE VIEW ---
    if (showOutboxDialog) {
        AlertDialog(
            onDismissRequest = { showOutboxDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.CloudQueue, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Rider Offline Outbox (${appState.riderOutbox.size} events)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Events queue in local storage and replay in sequential order on reconnect:", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    if (appState.riderOutbox.isEmpty()) {
                        Text("Outbox is empty.", fontSize = 11.sp, color = Color.Gray)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
                            items(appState.riderOutbox) { evt ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text("Seq #${evt.sequenceNumber}: ${evt.action}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                            Surface(
                                                color = when (evt.syncStatus) {
                                                    "APPLIED" -> Color(0xFFDCFCE7)
                                                    "DUPLICATE" -> Color(0xFFE0E7FF)
                                                    "CONFLICT_QUEUED" -> Color(0xFFFEE2E2)
                                                    "REJECTED" -> Color(0xFFFEE2E2)
                                                    else -> Color(0xFFFEF3C7)
                                                },
                                                shape = RoundedCornerShape(4.dp)
                                            ) {
                                                Text(evt.syncStatus, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp, 1.dp))
                                            }
                                        }
                                        Text("Order: ${evt.orderId} • Serial: ${evt.scannedSerial ?: "N/A"}", fontSize = 10.sp)
                                        Text(evt.idempotencyKey, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
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

    // --- DIALOG 6: OFFICIAL RECEIPT MODAL ---
    if (showReceiptOrderId != null) {
        val ord = appState.orders.find { it.id == showReceiptOrderId }
        val cust = store.customers.find { it.id == ord?.customerId }
        val addr = store.addresses.find { it.id == ord?.addressId }

        AlertDialog(
            onDismissRequest = { showReceiptOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Official Blue Gas Delivery Receipt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order ID: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("POD Reference: ${ord?.podReference ?: "POD-VERIFIED"}", fontSize = 10.sp, color = BlueGasPrimary, fontFamily = FontFamily.Monospace)
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Customer: ${cust?.name} (${cust?.phone})", fontSize = 11.sp)
                    Text("Delivered to: ${addr?.street}, ${addr?.area}, ${addr?.city}", fontSize = 10.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(4.dp))
                    ord?.items?.forEach { itm ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${itm.quantity}x ${itm.productName}", fontSize = 11.sp)
                            Text(formatPaisaToPkr(itm.subtotalPaisa), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    if ((ord?.depositTotalPaisa ?: 0L) > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Security Deposit (Liability)", fontSize = 11.sp, color = Color(0xFFB45309))
                            Text(formatPaisaToPkr(ord?.depositTotalPaisa ?: 0L), fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Delivery & Facilitation", fontSize = 11.sp)
                        Text(formatPaisaToPkr((ord?.deliveryFeePaisa ?: 0L) + (ord?.platformFeePaisa ?: 0L)), fontSize = 11.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Tax (5%)", fontSize = 11.sp)
                        Text(formatPaisaToPkr(ord?.taxPaisa ?: 0L), fontSize = 11.sp)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Settled:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(formatPaisaToPkr(ord?.totalAmountPaisa ?: 0L), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasPrimary)
                    }
                    Text("Cylinder Serial Delivered: ${ord?.allocatedSerial ?: "N/A"}", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF047857))
                    Text("Safety status: Bubble leak test PASSED. Valve seal intact.", fontSize = 10.sp, color = Color(0xFF0F766E))
                    val deliveryRecord = store.deliveries.find { it.orderId == ord?.id }
                    if (deliveryRecord != null) {
                        if (deliveryRecord.signerName != null) {
                            Text("Received & Signed by: ${deliveryRecord.signerName}", fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = BlueGasDark)
                        }
                        if (deliveryRecord.photoEvidenceCaptured) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(12.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("OGRA Doorstep Photo Geotagged & Verified", fontSize = 9.sp, color = Color(0xFF15803D), fontWeight = FontWeight.Bold)
                            }
                        }
                        if (deliveryRecord.changeReturnedPaisa != null && deliveryRecord.changeReturnedPaisa > 0) {
                            Text("Change Returned: ${formatPaisaToPkr(deliveryRecord.changeReturnedPaisa)}", fontSize = 10.sp, color = Color(0xFF0284C7))
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { showReceiptOrderId = null }) { Text("Done") }
            }
        )
    }
}

// ----------------------------------------------------------------------------
// COMPONENT: PENDING ORDER CARD (ORD_ASGN)
// ----------------------------------------------------------------------------
@Composable
private fun PendingOrderCard(
    order: Order,
    store: BlueGasStore,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val cust = store.customers.find { it.id == order.customerId }
    val addr = store.addresses.find { it.id == order.addressId }

    // Acceptance countdown timer based on distributorAcceptanceWindowMins
    val assignedTime = order.assignedAt ?: order.createdAt
    val windowMs = store.priceBookConfig.distributorAcceptanceWindowMins * 60 * 1000L
    val deadline = assignedTime + windowMs

    var remainingSeconds by remember { mutableStateOf(((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)) }

    LaunchedEffect(deadline) {
        while (remainingSeconds > 0) {
            delay(1000L)
            remainingSeconds = ((deadline - System.currentTimeMillis()) / 1000L).coerceAtLeast(0)
        }
    }

    val mins = remainingSeconds / 60
    val secs = remainingSeconds % 60
    val isExpiringSoon = remainingSeconds < 300

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with status & timer
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Acceptance Countdown Chip
            Surface(
                color = if (isExpiringSoon) Color(0xFFFEE2E2) else Color(0xFFFEF3C7),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Default.Timer,
                            contentDescription = null,
                            tint = if (isExpiringSoon) Color(0xFFDC2626) else Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Acceptance Window: %02d:%02d remaining".format(mins, secs),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isExpiringSoon) Color(0xFFDC2626) else Color(0xFFB45309)
                        )
                    }
                    Text(
                        text = "Assigned 5m ago",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
            Text("Customer: ${cust?.name} (${cust?.phone})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Address: ${addr?.street}, ${addr?.area}, ${addr?.city}", fontSize = 10.sp, color = Color(0xFF334155))
            Text("Zone: ${addr?.zoneId} • Mode: ${order.fulfilmentMode.name}", fontSize = 10.sp, color = Color.Gray)

            Spacer(modifier = Modifier.height(4.dp))
            order.items.forEach { itm ->
                Text("• ${itm.quantity}x ${itm.productName}", fontSize = 11.sp)
            }
            Text(
                "Payment: ${order.paymentMethod.name} (${formatPaisaToPkr(order.totalAmountPaisa)})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFD97706) else Color(0xFF16A34A)
            )

            // Buttons: Accept & Decline
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept Order", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFDC2626)),
                    modifier = Modifier.weight(0.9f)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline", fontSize = 11.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// COMPONENT: ACTIVE ORDER CARD (ORD_ACPT, ORD_PREP, ORD_DISP, ORD_ATHO)
// ----------------------------------------------------------------------------
@Composable
private fun ActiveOrderCard(
    order: Order,
    store: BlueGasStore,
    onStartPrep: () -> Unit,
    onScanSerial: () -> Unit,
    onStartDelivery: () -> Unit,
    onArrive: () -> Unit,
    onOpenPod: () -> Unit,
    onReportIssue: () -> Unit
) {
    val cust = store.customers.find { it.id == order.customerId }
    val addr = store.addresses.find { it.id == order.addressId }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            when (order.status) {
                OrderStatus.ORD_HOC -> Color(0xFF16A34A)
                OrderStatus.ORD_ATHO -> Color(0xFF16A34A)
                OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> Color(0xFF0284C7)
                OrderStatus.ORD_PREP -> Color(0xFFD97706)
                else -> Color(0xFFCBD5E1)
            }
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Header with status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(order.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Customer: ${cust?.name} (${cust?.phone})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Address: ${addr?.street}, ${addr?.area}, ${addr?.city}", fontSize = 10.sp, color = Color(0xFF334155))

            // Serial Banner
            Surface(
                color = if (order.allocatedSerial != null) Color(0xFFEFF6FF) else Color(0xFFFFFBEB),
                shape = RoundedCornerShape(4.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = if (order.allocatedSerial != null) Icons.Default.QrCodeScanner else Icons.Default.WarningAmber,
                            contentDescription = null,
                            tint = if (order.allocatedSerial != null) Color(0xFF0284C7) else Color(0xFFB45309),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Serial: ${order.allocatedSerial ?: "NOT ALLOCATED YET"}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = if (order.allocatedSerial != null) Color(0xFF0369A1) else Color(0xFFB45309)
                        )
                    }

                    if (order.status == OrderStatus.ORD_PREP) {
                        TextButton(
                            onClick = onScanSerial,
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(if (order.allocatedSerial != null) "Change" else "Scan Serial", fontSize = 10.sp)
                        }
                    }
                }
            }

            Text(
                "Payment: ${order.paymentMethod.name} (${formatPaisaToPkr(order.totalAmountPaisa)})",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFD97706) else Color(0xFF16A34A)
            )

            // DEDICATED ACTION BUTTONS PER STATUS
            Spacer(modifier = Modifier.height(8.dp))

            when (order.status) {
                OrderStatus.ORD_ACPT -> {
                    Button(
                        onClick = onStartPrep,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Begin Preparation & Select Serial", fontSize = 11.sp)
                    }
                }

                OrderStatus.ORD_PREP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onStartDelivery,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            enabled = order.allocatedSerial != null,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.DepartureBoard, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Start Delivery" else "Stage for Pickup", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Issue", fontSize = 10.sp)
                        }
                    }
                }

                OrderStatus.ORD_DISP -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onArrive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Arrived at Location", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Report Issue", fontSize = 10.sp)
                        }
                    }
                }

                OrderStatus.ORD_RDYPU -> {
                    // Pickup mode: customer has arrived at counter
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onArrive,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Storefront, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Customer at Counter", fontSize = 11.sp)
                        }
                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Issue", fontSize = 10.sp)
                        }
                    }
                }

                OrderStatus.ORD_ATHO -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Button(
                            onClick = onOpenPod,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(15.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Safety Check & Complete POD", fontSize = 11.sp)
                        }

                        OutlinedButton(
                            onClick = onReportIssue,
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                            modifier = Modifier.height(38.dp)
                        ) {
                            Text("Refused at Door", fontSize = 10.sp)
                        }
                    }
                }

                OrderStatus.ORD_HOC -> {
                    // Handover complete — finalizing, no more rider actions needed
                    Surface(
                        color = Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text("Handover Complete", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF166534))
                                Text("Awaiting system finalization to ORD_COMP.", fontSize = 10.sp, color = Color(0xFF15803D))
                            }
                        }
                    }
                }

                else -> {}
            }
        }
    }
}

// ----------------------------------------------------------------------------
// COMPONENT: COMPLETED ORDER CARD (ORD_COMP)
// ----------------------------------------------------------------------------
@Composable
private fun CompletedOrderCard(
    order: Order,
    store: BlueGasStore,
    onViewReceipt: () -> Unit
) {
    val cust = store.customers.find { it.id == order.customerId }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF0FDF4)),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBBF7D0)),
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
                Text(order.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(4.dp))
            Text("Customer: ${cust?.name}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            Text("Serial Delivered: ${order.allocatedSerial ?: "N/A"}", fontSize = 11.sp, color = Color(0xFF047857), fontWeight = FontWeight.Bold)
            Text(
                "Settled: ${formatPaisaToPkr(order.totalAmountPaisa)} • ${order.paymentMethod.name}",
                fontSize = 11.sp,
                color = Color(0xFF166534)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Delivered Today",
                    fontSize = 10.sp,
                    color = Color.Gray
                )
                TextButton(
                    onClick = onViewReceipt,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(28.dp)
                ) {
                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(13.dp))
                    Spacer(modifier = Modifier.width(3.dp))
                    Text("View Receipt", fontSize = 10.sp)
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------
// COMPONENT: EMPTY STATE
// ----------------------------------------------------------------------------
@Composable
private fun EmptyStateView(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 36.dp, horizontal = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, tint = Color(0xFF94A3B8), modifier = Modifier.size(48.dp))
        Spacer(modifier = Modifier.height(10.dp))
        Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF475569))
        Spacer(modifier = Modifier.height(4.dp))
        Text(subtitle, fontSize = 11.sp, color = Color(0xFF64748B), textAlign = androidx.compose.ui.text.style.TextAlign.Center)
    }
}
