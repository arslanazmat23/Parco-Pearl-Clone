package com.example.bluegas.ui.staff

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private enum class HubTab(val label: String) {
    ORDERS("Orders"),
    STOCK("Stock"),
    RETURNS("Returns & empties"),
    CASH("Cash")
}

/**
 * Hub Staff workspace screen for managing hub order acceptance, cylinder preparation and serial
 * binding (RACI-05), counter pickups, hub stock transfers, and returned cylinder scan-ins.
 *
 * @param store Shared in-memory store instance.
 * @param snapshot Immutable state snapshot for rendering.
 * @param onBanner Banner callback reporting success or error feedback.
 * @param onSelectOrder Callback to open the order detail dialog.
 */
@Composable
fun StaffHubScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit
) {
    val hubNodes = remember(snapshot.version) {
        snapshot.nodes.filter { it.type == FulfilmentPointType.HUB }
    }
    val hubId = store.selectedHubId?.takeIf { id -> hubNodes.any { it.id == id } }
        ?: hubNodes.firstOrNull()?.id
        ?: ""

    var selectedTab by remember { mutableStateOf(HubTab.ORDERS) }

    // Dialog states
    var declineOrderTarget by remember { mutableStateOf<Order?>(null) }
    var declineReasonInput by remember { mutableStateOf("") }

    var handoverOrderTarget by remember { mutableStateOf<Order?>(null) }
    var showRequestStockDialog by remember { mutableStateOf(false) }

    val hubOrders = remember(snapshot.version, hubId) {
        snapshot.orders
            .filter { it.fulfilmentPointId == hubId || it.pickupPointId == hubId }
            .sortedWith(compareBy<Order> { it.status.isTerminal }.thenByDescending { it.createdAt })
    }
    val activeOrdersCount = remember(snapshot.version, hubId) {
        hubOrders.count { !it.status.isTerminal }
    }
    val incomingTransfers = remember(snapshot.version, hubId) {
        snapshot.stockTransfers.filter { it.status == "DISPATCHED" && it.toPointId == hubId }
    }
    val pendingReturnCylinders = remember(snapshot.version, hubId) {
        snapshot.cylinders.filter { it.pendingHubScanIn && (it.pointId == null || it.pointId == hubId) }
    }
    val stockBadgeCount = incomingTransfers.size + pendingReturnCylinders.size

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("hub_screen"),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Accountable header
        item {
            AccountableHeader(
                roleName = "${AccountableRole.INVENTORY.displayName} & ${AccountableRole.HUB.displayName}",
                description = "Accountable: ${AccountableRole.INVENTORY.displayName} for exact cylinder allocation (RACI-05) · ${AccountableRole.HUB.displayName} responsible"
            )
        }

        // Hub selector chips
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(hubNodes) { hub ->
                    val selected = hub.id == hubId
                    FilterChip(
                        selected = selected,
                        onClick = { store.setSelectedHub(hub.id) },
                        label = {
                            Text(
                                text = hub.name,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        leadingIcon = if (selected) {
                            { Icon(Icons.Default.Warehouse, contentDescription = null, modifier = Modifier.size(14.dp)) }
                        } else null,
                        modifier = Modifier.testTag("hub_selector_${hub.id}")
                    )
                }
            }
        }

        // Workspace tabs with count badges
        item {
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(HubTab.entries) { tab ->
                    val count = when (tab) {
                        HubTab.ORDERS -> activeOrdersCount
                        HubTab.STOCK -> stockBadgeCount
                        HubTab.RETURNS, HubTab.CASH -> 0
                    }
                    val selected = selectedTab == tab
                    Surface(
                        color = if (selected) BlueGasPrimary else Color.White,
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, if (selected) BlueGasPrimary else Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .testTag("hub_tab_${tab.name.lowercase()}")
                            .clickable { selectedTab = tab }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)
                        ) {
                            Text(
                                text = tab.label,
                                fontSize = 11.sp,
                                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                color = if (selected) Color.White else Color(0xFF1E293B),
                                maxLines = 1
                            )
                            if (count > 0) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clip(CircleShape)
                                        .background(if (selected) Color.White.copy(alpha = 0.25f) else Color(0xFFE2E8F0))
                                ) {
                                    Text(
                                        text = count.toString(),
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (selected) Color.White else Color(0xFF0F172A)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        when (selectedTab) {
            HubTab.ORDERS -> {
                if (hubOrders.isEmpty()) {
                    item {
                        HubEmptyCard("No orders at ${snapshot.pointName(hubId).ifBlank { "this hub" }} right now.")
                    }
                } else {
                    items(hubOrders, key = { it.id }) { ord ->
                        HubOrderCard(
                            order = ord,
                            hubId = hubId,
                            store = store,
                            snapshot = snapshot,
                            onBanner = onBanner,
                            onSelectOrder = onSelectOrder,
                            onOpenDecline = {
                                declineOrderTarget = ord
                                declineReasonInput = ""
                            },
                            onOpenHandover = {
                                handoverOrderTarget = ord
                            }
                        )
                    }
                }
            }

            HubTab.STOCK -> {
                item {
                    HubStockTabContent(
                        hubId = hubId,
                        store = store,
                        snapshot = snapshot,
                        incomingTransfers = incomingTransfers,
                        pendingReturnCylinders = pendingReturnCylinders,
                        onBanner = onBanner,
                        onOpenRequestStock = { showRequestStockDialog = true }
                    )
                }
            }

            HubTab.RETURNS, HubTab.CASH -> {
                item {
                    HubEmptyCard("Available in the next update")
                }
            }
        }
    }

    // Decline assigned order dialog
    val declineTarget = declineOrderTarget
    if (declineTarget != null) {
        AlertDialog(
            onDismissRequest = { declineOrderTarget = null },
            title = { Text("Decline ${snapshot.orderLabel(declineTarget.id)}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
            text = {
                Column {
                    Text("Please enter why the hub cannot accept this order.", fontSize = 12.sp, color = Color(0xFF475569))
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = declineReasonInput,
                        onValueChange = { declineReasonInput = it },
                        placeholder = { Text("Reason for declining", color = Color.Gray) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hub_decline_reason_input")
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = declineReasonInput.trim()
                        if (trimmed.isEmpty()) {
                            onBanner("Please enter a reason before declining.", true)
                        } else {
                            onBanner.report(store.hubDecline(declineTarget.id, trimmed))
                            declineOrderTarget = null
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("hub_decline_confirm_btn")
                ) {
                    Text("Confirm decline")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { declineOrderTarget = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Counter pickup handover dialog
    val handoverTarget = handoverOrderTarget
    if (handoverTarget != null) {
        HubCounterHandoverDialog(
            order = handoverTarget,
            store = store,
            snapshot = snapshot,
            onBanner = onBanner,
            onDismiss = { handoverOrderTarget = null }
        )
    }

    // Request stock dialog
    if (showRequestStockDialog) {
        HubRequestStockDialog(
            hubId = hubId,
            store = store,
            snapshot = snapshot,
            onBanner = onBanner,
            onDismiss = { showRequestStockDialog = false }
        )
    }
}

/**
 * Displays a single order card in the Hub Orders tab with stage-appropriate actions.
 */
@Composable
private fun HubOrderCard(
    order: Order,
    hubId: String,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onSelectOrder: (Order) -> Unit,
    onOpenDecline: () -> Unit,
    onOpenHandover: () -> Unit
) {
    val itemSummary = remember(snapshot.version, order.id) {
        order.items.joinToString(", ") { "${it.quantity} × ${snapshot.skuLabel(it.sku)}" }
    }
    val modeLabel = if (order.fulfilmentMode == FulfilmentMode.PICKUP) "Counter pickup" else "Delivery"
    val locationText = if (order.fulfilmentMode == FulfilmentMode.PICKUP) {
        "Collect at ${snapshot.pointName(order.pickupPointId ?: hubId)}"
    } else {
        snapshot.addressLine(order.addressId)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hub_order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelectOrder(order) },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = snapshot.orderLabel(order.id),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Text(
                        text = "$modeLabel · $itemSummary · ${formatRupees(order.totalAmountPaisa)}",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
                Spacer(modifier = Modifier.width(6.dp))
                HubOrderStatusPill(order.status)
            }

            if (locationText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(text = locationText, fontSize = 11.sp, color = Color(0xFF64748B))
            }

            Spacer(modifier = Modifier.height(8.dp))

            when (order.status) {
                OrderStatus.ORD_ASGN -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { onBanner.report(store.hubAccept(order.id, hubId)) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("hub_accept_btn_${order.id}")
                        ) {
                            Text("Accept", fontSize = 12.sp)
                        }
                        OutlinedButton(
                            onClick = onOpenDecline,
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp)
                                .testTag("hub_decline_btn_${order.id}")
                        ) {
                            Text("Decline", fontSize = 12.sp, color = Color(0xFFDC2626))
                        }
                    }
                }

                OrderStatus.ORD_ACPT -> {
                    Button(
                        onClick = { onBanner.report(store.startPreparing(order.id)) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("hub_start_prep_btn_${order.id}")
                    ) {
                        Text("Start preparing", fontSize = 12.sp)
                    }
                }

                OrderStatus.ORD_PREP -> {
                    if (order.allocatedSerial == null) {
                        HubBindCylinderSection(
                            order = order,
                            hubId = hubId,
                            store = store,
                            snapshot = snapshot,
                            onBanner = onBanner
                        )
                    } else {
                        Surface(
                            color = Color(0xFFF8FAFC),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Text(
                                    text = "Bound cylinder: ${order.allocatedSerial}",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
                                    val riderLine = when (order.riderTaskStatus) {
                                        null -> "Waiting for Operations to offer a rider"
                                        RiderTaskStatus.OFFERED -> "Offered to ${snapshot.riderName(order.assignedRiderId)}"
                                        RiderTaskStatus.ACCEPTED -> "Accepted by ${snapshot.riderName(order.assignedRiderId)}"
                                        RiderTaskStatus.DECLINED -> {
                                            val who = snapshot.riderName(order.assignedRiderId).ifBlank { "rider" }
                                            "Declined by $who — Operations will re-offer"
                                        }
                                    }
                                    Text(
                                        text = riderLine,
                                        fontSize = 11.sp,
                                        color = if (order.riderTaskStatus == RiderTaskStatus.DECLINED) Color(0xFFB45309) else Color(0xFF1E40AF),
                                        fontWeight = FontWeight.Medium
                                    )
                                } else {
                                    Button(
                                        onClick = { onBanner.report(store.markReadyForPickup(order.id)) },
                                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 48.dp)
                                            .testTag("hub_ready_pickup_btn_${order.id}")
                                    ) {
                                        Text("Mark ready for pickup", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                OrderStatus.ORD_RDYPU -> {
                    Text(
                        text = "Bound cylinder: ${order.allocatedSerial ?: "None"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = { onBanner.report(store.customerArrivedAtDepot(order.id, by = "COUNTER")) },
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 48.dp)
                            .testTag("hub_arrived_btn_${order.id}")
                    ) {
                        Text("Customer arrived at depot", fontSize = 12.sp)
                    }
                }

                OrderStatus.ORD_ATHO -> {
                    Text(
                        text = "Bound cylinder: ${order.allocatedSerial ?: "None"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF334155)
                    )
                    if (order.fulfilmentMode == FulfilmentMode.PICKUP) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Button(
                            onClick = onOpenHandover,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp)
                                .testTag("hub_handover_btn_${order.id}")
                        ) {
                            Text("Hand over", fontSize = 12.sp)
                        }
                    }
                }

                else -> {
                    if (order.allocatedSerial != null) {
                        Text(
                            text = "Cylinder: ${order.allocatedSerial}",
                            fontSize = 11.sp,
                            color = Color(0xFF475569)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Renders the cylinder selection and binding panel for an order in Preparing state without a bound serial.
 * Sorts eligible hub cylinders oldest fill date first and lists test-due cylinders unselectable at bottom.
 */
@Composable
private fun HubBindCylinderSection(
    order: Order,
    hubId: String,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner
) {
    var serialInput by remember(order.id) { mutableStateOf("") }
    val today = remember(snapshot.version) {
        SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    }
    val neededSku = remember(snapshot.version, order.id) {
        order.items.firstOrNull {
            it.lineType == "GAS" || it.lineType == "CYLINDER" || it.sku.startsWith("SKU-LPG")
        }?.sku
    }

    val hubCandidates = remember(snapshot.version, hubId, neededSku) {
        snapshot.cylinders.filter { c ->
            c.custody == CylinderCustody.HUB &&
                c.pointId == hubId &&
                c.status == InventoryState.INV_AVAIL &&
                !c.hseHold &&
                !c.pendingHubScanIn &&
                (neededSku == null || c.sku == neededSku)
        }
    }
    val eligibleCylinders = remember(snapshot.version, hubCandidates, today) {
        hubCandidates
            .filter { c -> c.nextTestDueDate == null || c.nextTestDueDate > today }
            .sortedWith(compareBy(nullsLast()) { it.lastFillDate })
    }
    val testDueCylinders = remember(snapshot.version, hubCandidates, today) {
        hubCandidates.filter { c -> c.nextTestDueDate != null && c.nextTestDueDate <= today }
    }

    Surface(
        color = Color(0xFFF8FAFC),
        shape = RoundedCornerShape(8.dp),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Bind physical cylinder (${neededSku?.let { snapshot.skuLabel(it) } ?: "Gas"})",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF0F172A)
            )

            if (eligibleCylinders.isEmpty()) {
                Text(
                    text = "No eligible filled cylinders at this hub.",
                    fontSize = 11.sp,
                    color = Color(0xFF92400E)
                )
            } else {
                eligibleCylinders.forEachIndexed { idx, cyl ->
                    val isSuggested = idx == 0
                    val isSelected = serialInput.equals(cyl.serialNumber, ignoreCase = true)
                    Surface(
                        color = if (isSelected) Color(0xFFEFF6FF) else Color.White,
                        shape = RoundedCornerShape(6.dp),
                        border = BorderStroke(1.dp, if (isSelected) BlueGasPrimary else Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { serialInput = cyl.serialNumber }
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = cyl.serialNumber,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = "Filled ${cyl.lastFillDate ?: "—"}",
                                    fontSize = 10.sp,
                                    color = Color(0xFF64748B)
                                )
                            }
                            if (isSuggested) {
                                Spacer(modifier = Modifier.height(4.dp))
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Oldest fill — suggested",
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color(0xFF166534)
                                    )
                                    RecommendedPill()
                                }
                            }
                            if (!cyl.isTagged) {
                                Text("Untagged cylinder", fontSize = 10.sp, color = Color(0xFFB45309))
                            }
                        }
                    }
                }
            }

            // Greyed test-due cylinders (unselectable)
            testDueCylinders.forEach { cyl ->
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .alpha(0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = cyl.serialNumber,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 11.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Test due — send to plant",
                                fontSize = 10.sp,
                                color = Color(0xFFB45309),
                                fontWeight = FontWeight.Medium
                            )
                        }
                        Text(
                            text = "Due ${cyl.nextTestDueDate ?: ""}",
                            fontSize = 10.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = serialInput,
                onValueChange = { serialInput = it },
                label = { Text("Scan or type cylinder ID", fontSize = 11.sp) },
                placeholder = { Text("Enter cylinder serial ID", color = Color.Gray, fontSize = 11.sp) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("hub_bind_serial_input_${order.id}")
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val trimmed = serialInput.trim()
                        if (trimmed.isEmpty()) {
                            onBanner("Scan or enter a cylinder ID to bind.", true)
                        } else {
                            onBanner.report(store.allocateCylinder(order.id, trimmed))
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("hub_bind_btn_${order.id}")
                ) {
                    Text("Bind", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = { onBanner.report(store.reportNoSuitableCylinder(order.id, untaggedOnly = false)) },
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 48.dp)
                        .testTag("hub_no_cyl_btn_${order.id}")
                ) {
                    Text("No suitable stock", fontSize = 11.sp)
                }
            }
        }
    }
}

/**
 * Dialog for completing a counter pickup handover with bound cylinder ID check, COD cash entry,
 * and approved empty exchange brand verification.
 */
@Composable
private fun HubCounterHandoverDialog(
    order: Order,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onDismiss: () -> Unit
) {
    var cylinderIdInput by remember(order.id) { mutableStateOf("") }
    var pinChecked by remember(order.id) { mutableStateOf(true) }
    var codAmountInput by remember(order.id) { mutableStateOf("") }
    var selectedEmptyBrand by remember(order.id) {
        mutableStateOf(snapshot.settings.approvedEmptyBrands.firstOrNull() ?: "BLUE GAS")
    }
    var emptySerialInput by remember(order.id) { mutableStateOf("") }

    val isCod = order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY
    val isExchange = order.orderType == OrderType.REFILL_EXCHANGE
    val isOtherBrandSelected = isExchange && selectedEmptyBrand == "Other brand"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text("Hand over · ${snapshot.orderLabel(order.id)}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = cylinderIdInput,
                    onValueChange = { cylinderIdInput = it },
                    label = { Text("Bound cylinder ID", fontSize = 11.sp) },
                    placeholder = { Text("Enter ${order.allocatedSerial ?: "cylinder ID"}", color = Color.Gray, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_handover_serial_input")
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { pinChecked = !pinChecked }
                ) {
                    Checkbox(checked = pinChecked, onCheckedChange = { pinChecked = it })
                    Text("Pickup code verified (${order.handoverPin})", fontSize = 12.sp)
                }

                if (isCod) {
                    OutlinedTextField(
                        value = codAmountInput,
                        onValueChange = { codAmountInput = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Cash collected (Rs)", fontSize = 11.sp) },
                        placeholder = { Text("Cash due ${formatRupees(order.totalAmountPaisa)}", color = Color.Gray, fontSize = 11.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("hub_handover_cod_input")
                    )
                }

                if (isExchange) {
                    Text("Returned empty brand:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        val brandChoices = snapshot.settings.approvedEmptyBrands + "Other brand"
                        items(brandChoices) { brand ->
                            FilterChip(
                                selected = selectedEmptyBrand == brand,
                                onClick = { selectedEmptyBrand = brand },
                                label = { Text(brand, fontSize = 11.sp) }
                            )
                        }
                    }
                    if (isOtherBrandSelected) {
                        Text(
                            text = "Only BLUE GAS cylinders can be accepted as empties (BR-STK-07).",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626),
                            fontWeight = FontWeight.Medium
                        )
                    } else {
                        OutlinedTextField(
                            value = emptySerialInput,
                            onValueChange = { emptySerialInput = it },
                            label = { Text("Returned empty ID (optional)", fontSize = 11.sp) },
                            placeholder = { Text("Scan or type empty ID", color = Color.Gray, fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isOtherBrandSelected,
                onClick = {
                    if (!pinChecked) {
                        onBanner("Confirm the customer pickup code first.", true)
                        return@Button
                    }
                    if (cylinderIdInput.isBlank()) {
                        onBanner("Enter the bound cylinder ID.", true)
                        return@Button
                    }
                    val amountPaisa = if (isCod) {
                        val rs = codAmountInput.trim().toLongOrNull()
                        if (rs == null) {
                            onBanner("Enter the cash amount received.", true)
                            return@Button
                        }
                        rs * 100L
                    } else null

                    val res = store.completeCounterHandover(
                        orderId = order.id,
                        pinOrRefChecked = pinChecked,
                        scannedCylinderId = cylinderIdInput.trim(),
                        amountReceivedPaisa = amountPaisa,
                        emptyBrand = if (isExchange) selectedEmptyBrand else null,
                        emptyCylinderId = emptySerialInput.trim().takeIf { it.isNotEmpty() }
                    )
                    onBanner.report(res)
                    if (res.first) {
                        onDismiss()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier.testTag("hub_handover_confirm_btn")
            ) {
                Text("Confirm handover")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/**
 * Displays the Stock tab for the selected hub: balances per size, incoming transfer receipts,
 * stock request trigger, and scan-in for returned cylinders awaiting hub intake.
 */
@Composable
private fun HubStockTabContent(
    hubId: String,
    store: BlueGasStore,
    snapshot: SharedAppState,
    incomingTransfers: List<StockTransfer>,
    pendingReturnCylinders: List<Cylinder>,
    onBanner: StaffBanner,
    onOpenRequestStock: () -> Unit
) {
    val hubBalances = remember(snapshot.version, hubId) {
        listOf("SKU-LPG-11-8", "SKU-LPG-45-4").map { sku ->
            sku to snapshot.stockBalances.find { it.pointId == hubId && it.skuId == sku }
        }
    }
    var returnScanInput by remember(hubId) { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Stock balances", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF0F172A))
            Button(
                onClick = onOpenRequestStock,
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("hub_request_stock_btn")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Request stock", fontSize = 12.sp)
            }
        }

        hubBalances.forEach { (sku, bal) ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = snapshot.skuLabel(sku),
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        StockMetricColumn("Filled", bal?.filledQty ?: 0)
                        StockMetricColumn("Empty", bal?.emptyQty ?: 0)
                        StockMetricColumn("Out w/ riders", bal?.outWithRidersQty ?: 0)
                        StockMetricColumn("Reserved", bal?.reservedQty ?: 0)
                        StockMetricColumn("Available", bal?.availableQty ?: 0)
                    }
                }
            }
        }

        // Incoming transfers
        Text(
            text = "Incoming transfers (${incomingTransfers.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF0F172A)
        )
        if (incomingTransfers.isEmpty()) {
            HubEmptyCard("No incoming transfers waiting at this hub.")
        } else {
            incomingTransfers.forEach { trf ->
                HubIncomingTransferCard(
                    transfer = trf,
                    store = store,
                    snapshot = snapshot,
                    onBanner = onBanner
                )
            }
        }

        // Pending return cylinders
        Text(
            text = "Pending return cylinders (${pendingReturnCylinders.size})",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = Color(0xFF0F172A)
        )
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                if (pendingReturnCylinders.isEmpty()) {
                    Text("No returned cylinders waiting to be scanned in.", fontSize = 11.sp, color = Color(0xFF64748B))
                } else {
                    pendingReturnCylinders.forEach { cyl ->
                        Surface(
                            color = Color(0xFFFFFBEB),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, Color(0xFFFCD34D)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { returnScanInput = cyl.serialNumber }
                        ) {
                            Row(
                                modifier = Modifier.padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(cyl.serialNumber, fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                                Text(snapshot.skuLabel(cyl.sku), fontSize = 11.sp, color = Color(0xFF78350F))
                            }
                        }
                    }
                }
                OutlinedTextField(
                    value = returnScanInput,
                    onValueChange = { returnScanInput = it },
                    label = { Text("Scan or type cylinder ID", fontSize = 11.sp) },
                    placeholder = { Text("Enter returned cylinder ID", color = Color.Gray, fontSize = 11.sp) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("hub_return_scan_input")
                )
                Button(
                    onClick = {
                        val res = store.hubScanInReturnedCylinder(hubId, returnScanInput)
                        onBanner.report(res)
                        if (res.first) returnScanInput = ""
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp)
                        .testTag("hub_scan_in_btn")
                ) {
                    Text("Scan in", fontSize = 12.sp)
                }
            }
        }
    }
}

/**
 * Displays a single incoming stock transfer card with editable received quantities defaulting to sent.
 */
@Composable
private fun HubIncomingTransferCard(
    transfer: StockTransfer,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner
) {
    val receivedInputs = remember(snapshot.version, transfer.id) {
        mutableStateMapOf<String, String>().apply {
            transfer.lines.forEach { line -> put(line.skuId, line.qtySent.toString()) }
        }
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFCBD5E1)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("hub_transfer_card_${transfer.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Transfer ${transfer.id} · Gate pass ${transfer.gatePassNo ?: "—"}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF0F172A)
            )
            if (!transfer.driverName.isNullOrBlank()) {
                Text(
                    text = "Driver: ${transfer.driverName} (${transfer.vehicleNo ?: ""})",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
            }

            transfer.lines.forEach { line ->
                val raw = receivedInputs[line.skuId] ?: line.qtySent.toString()
                val recQty = raw.toIntOrNull()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(snapshot.skuLabel(line.skuId), fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                        Text("Sent ${line.qtySent}", fontSize = 11.sp, color = Color(0xFF475569))
                    }
                    OutlinedTextField(
                        value = raw,
                        onValueChange = { receivedInputs[line.skuId] = it.filter { ch -> ch.isDigit() } },
                        label = { Text("Received", fontSize = 10.sp) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true,
                        modifier = Modifier.width(100.dp)
                    )
                }
                if (recQty != null && recQty < line.qtySent) {
                    Text(
                        text = "Short by ${line.qtySent - recQty} — shortfall exception (EXC-18) will be logged",
                        fontSize = 11.sp,
                        color = Color(0xFFB45309),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            Button(
                onClick = {
                    val parsedMap = mutableMapOf<String, Int>()
                    for (line in transfer.lines) {
                        val qty = receivedInputs[line.skuId]?.toIntOrNull()
                        if (qty == null) {
                            onBanner("Enter a valid received quantity.", true)
                            return@Button
                        }
                        parsedMap[line.skuId] = qty
                    }
                    onBanner.report(store.confirmTransferReceipt(transfer.id, parsedMap, AccountableRole.HUB.displayName))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("hub_confirm_receipt_btn_${transfer.id}")
            ) {
                Text("Confirm receipt", fontSize = 12.sp)
            }
        }
    }
}

/**
 * Dialog allowing Hub Staff to request stock transfers from the plant with blank starting inputs.
 */
@Composable
private fun HubRequestStockDialog(
    hubId: String,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: StaffBanner,
    onDismiss: () -> Unit
) {
    var qty118Input by remember { mutableStateOf("") }
    var qty454Input by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Request stock for ${snapshot.pointName(hubId)}", fontWeight = FontWeight.Bold, fontSize = 15.sp) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = qty118Input,
                    onValueChange = { qty118Input = it.filter { ch -> ch.isDigit() } },
                    label = { Text("${snapshot.skuLabel("SKU-LPG-11-8")} quantity", fontSize = 11.sp) },
                    placeholder = { Text("0", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = qty454Input,
                    onValueChange = { qty454Input = it.filter { ch -> ch.isDigit() } },
                    label = { Text("${snapshot.skuLabel("SKU-LPG-45-4")} quantity", fontSize = 11.sp) },
                    placeholder = { Text("0", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val lines = buildMap {
                        val q118 = qty118Input.trim().toIntOrNull() ?: 0
                        val q454 = qty454Input.trim().toIntOrNull() ?: 0
                        if (q118 > 0) put("SKU-LPG-11-8", q118)
                        if (q454 > 0) put("SKU-LPG-45-4", q454)
                    }
                    val res = store.requestStock(hubId, lines, AccountableRole.HUB.displayName)
                    onBanner.report(res)
                    if (res.first) onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary)
            ) {
                Text("Submit request")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Displays a compact labelled metric column for stock balances.
 */
@Composable
private fun StockMetricColumn(label: String, value: Int) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = value.toString(), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF0F172A))
        Text(text = label, fontSize = 10.sp, color = Color(0xFF64748B))
    }
}

/**
 * Displays an order status pill using plain human-readable display text without raw ORD_* codes.
 */
@Composable
private fun HubOrderStatusPill(status: OrderStatus) {
    val (bg, fg) = when (status) {
        OrderStatus.ORD_COMP, OrderStatus.ORD_HOC -> Color(0xFFDCFCE7) to Color(0xFF166534)
        OrderStatus.ORD_EXC -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO, OrderStatus.ORD_SUPS -> Color(0xFFF1F5F9) to Color(0xFF475569)
        else -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
    }
    Surface(color = bg, shape = RoundedCornerShape(6.dp)) {
        Text(
            text = status.displayName,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

/**
 * Displays the blue "Recommended — awaiting approval" pill next to suggested features.
 */
@Composable
private fun RecommendedPill() {
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

/**
 * Displays a standardized empty-state card inside the Hub workspace.
 */
@Composable
private fun HubEmptyCard(message: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(text = message, fontSize = 12.sp, color = Color(0xFF64748B))
        }
    }
}
