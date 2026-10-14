package com.example.bluegas.ui.staff

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
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.bluegas.ui.components.OpenSequencingDecisionBadge
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffPortalScreen(store: BlueGasStore) {
    var bannerMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFF1F5F9))
    ) {
        // Staff Header & Sub-role Bar
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
                        Icon(Icons.Default.AdminPanelSettings, contentDescription = null, tint = Color(0xFF38BDF8))
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text("BLUE GAS STAFF CONTROL PORTAL", fontWeight = FontWeight.Black, fontSize = 14.sp, color = Color.White)
                            Text("Accountable Role: ${store.activeStaffSubRole.displayName}", fontSize = 11.sp, color = Color(0xFF94A3B8))
                        }
                    }

                    // Open sequencing decision notice
                    OpenSequencingDecisionBadge()
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Sub-Role Selector
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StaffSubRole.values().forEach { role ->
                        val isSelected = store.activeStaffSubRole == role
                        Surface(
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .weight(1f)
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
            Surface(color = Color(0xFFFEF3C7), modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text(bannerMessage ?: "", fontSize = 11.sp, color = Color(0xFF92400E), modifier = Modifier.weight(1f))
                    IconButton(onClick = { bannerMessage = null }, modifier = Modifier.size(22.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
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
            when (store.activeStaffSubRole) {
                StaffSubRole.OPERATIONS -> OperationsScreen(store) { bannerMessage = it }
                StaffSubRole.FINANCE -> FinanceScreen(store) { bannerMessage = it }
                StaffSubRole.DISTRIBUTOR -> DistributorScreen(store) { bannerMessage = it }
                StaffSubRole.SUPPORT -> SupportScreen(store) { bannerMessage = it }
                StaffSubRole.ADMIN -> AdminScreen(store) { bannerMessage = it }
            }
        }
    }
}

// ----------------------------------------------------
// 1. OPERATIONS SUB-ROLE
// ----------------------------------------------------
@Composable
private fun OperationsScreen(store: BlueGasStore, onBanner: (String) -> Unit) {
    val exceptionOrders = store.orders.filter { it.status == OrderStatus.ORD_EXC }
    val pendingAssignments = store.orders.filter { it.status == OrderStatus.ORD_CONF || it.status == OrderStatus.ORD_RSVD }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AccountableHeader("Operations Lead", "Fulfilment Exceptions, Dispatch Routing & Serial Collision Triage")
            Spacer(modifier = Modifier.height(10.dp))
        }

        // Serial Conflict Queue
        if (store.opsConflictQueue.isNotEmpty()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF87171)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFDC2626))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Ops Conflict Queue (${store.opsConflictQueue.size} items)", fontWeight = FontWeight.Bold, color = Color(0xFFB91C1C), fontSize = 13.sp)
                        }
                        Text("Duplicate serial scans detected from offline devices. Human resolution required:", fontSize = 11.sp)
                        Spacer(modifier = Modifier.height(6.dp))

                        store.opsConflictQueue.forEach { conf ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Conflicting Serial: ${conf.serialNumber}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Text("Orders: ${conf.orderId1} vs ${conf.orderId2} • ${conf.reasonCode}", fontSize = 10.sp)
                                }
                                Button(
                                    onClick = {
                                        store.opsConflictQueue.remove(conf)
                                        store.logAudit("ConflictQueue", conf.id, "RESOLVE_CONFLICT", "Operations Officer", AccountableRole.OPERATIONS, "Resolved serial conflict for ${conf.serialNumber}")
                                        store.notifyChanged()
                                        onBanner("Conflict for serial ${conf.serialNumber} resolved by Ops.")
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text("Resolve & Reassign", fontSize = 10.sp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Fulfilment Exceptions
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
                        Text("Constrained Recovery Action:", fontWeight = FontWeight.Bold, fontSize = 10.sp)
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
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Route to Payment Pending", fontSize = 9.sp) }
                                    OutlinedButton(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_CANC)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Cancel Order", fontSize = 9.sp) }
                                }
                                ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_PREP)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Recover to Preparing", fontSize = 9.sp) }
                                }
                                ExceptionReason.PAYMENT_ISSUE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_PAYPEND)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Retry Payment Pending", fontSize = 9.sp) }
                                }
                                ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_ASGN)
                                            onBanner(m)
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Reassign to Assigned", fontSize = 9.sp) }
                                }
                                else -> {
                                    Button(
                                        onClick = {
                                            val (s, m) = store.recoverFulfilmentException(ord.id, OrderStatus.ORD_FCLO)
                                            onBanner(m)
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF475569)),
                                        modifier = Modifier.weight(1f)
                                    ) { Text("Fail Closed (Terminal)", fontSize = 9.sp) }
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
            Text("All Platform Orders (${store.orders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        items(store.orders) { ord ->
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clickable {
                        store.selectedOrderIdForDrawer = ord.id
                        store.notifyChanged()
                    }
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text("${ord.customerId} • ${formatPaisaToPkr(ord.totalAmountPaisa)} • Serial: ${ord.allocatedSerial ?: "None"}", fontSize = 10.sp, color = Color.Gray)
                    }
                    CanonicalStatusChip(ord.status)
                }
            }
        }
    }
}

// ----------------------------------------------------
// 2. FINANCE SUB-ROLE
// ----------------------------------------------------
@Composable
private fun FinanceScreen(store: BlueGasStore, onBanner: (String) -> Unit) {
    val totalRevenue = store.ledgerEntries.filter { it.entryType == "REVENUE" }.sumOf { it.debitPaisa }
    val totalDepositsHeld = store.ledgerEntries.filter { it.entryType == "DEPOSIT_LIABILITY" }.sumOf { it.creditPaisa }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AccountableHeader("Finance Lead", "Payment Gateway Auditing, Refunds, Settlements & Deposit Liability")
            Spacer(modifier = Modifier.height(10.dp))

            // Financial Summary cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Gross Refill Volume", fontSize = 10.sp, color = Color(0xFF1E40AF))
                        Text(formatPaisaToPkr(totalRevenue), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasPrimary)
                    }
                }
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Cylinder Deposit Liability", fontSize = 10.sp, color = Color(0xFFB45309))
                        Text(formatPaisaToPkr(totalDepositsHeld), fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFFD97706))
                        Text("Balance Sheet Liability Item", fontSize = 8.sp, color = Color.Gray)
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))

            Text("Payment Records & Settlement Audit:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        items(store.payments) { pay ->
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
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Pay ID: ${pay.id} (${pay.orderId})", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                            Text(pay.state.displayName, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp, 2.dp))
                        }
                    }
                    Text("Method: ${pay.method.name} • Base: ${formatPaisaToPkr(pay.baseAmountPaisa)} • Settled: ${formatPaisaToPkr(pay.settledAmountPaisa)}", fontSize = 10.sp)
                    Text("Idempotency: ${pay.idempotencyKey}", fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)

                    // Refund execution button for Finance
                    if (pay.settledAmountPaisa > 0 || pay.capturedAmountPaisa > 0) {
                        Button(
                            onClick = {
                                val (s, m) = store.executeRefund(pay.orderId, pay.baseAmountPaisa, "Finance manual refund override", "Finance Officer")
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
            Text("Immutable Ledger Entries (${store.ledgerEntries.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
        }

        items(store.ledgerEntries.reversed()) { led ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("[${led.entryType}] ${led.description}", fontSize = 10.sp, modifier = Modifier.weight(1f))
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
private fun DistributorScreen(store: BlueGasStore, onBanner: (String) -> Unit) {
    val assignedOrders = store.orders.filter { it.status == OrderStatus.ORD_ASGN }
    val preparingOrders = store.orders.filter { it.status == OrderStatus.ORD_PREP || it.status == OrderStatus.ORD_ACPT }
    val availableCylinders = store.cylinders.filter { it.status == InventoryState.INV_AVAIL }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AccountableHeader("Distributor Lead", "Order Acceptance, Exact Serial Binding & Stock Commit")
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
                        Column {
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
                        Text("Allocated Serial: ${ord.allocatedSerial ?: "NOT YET ALLOCATED"}", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = BlueGasPrimary)

                        Spacer(modifier = Modifier.height(6.dp))
                        Text("Select Outgoing Cylinder Serial to Bind:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            availableCylinders.take(4).forEach { cyl ->
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

                        // Dispatch button
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
                            Text(if (ord.fulfilmentMode == FulfilmentMode.DELIVERY) "Dispatch to Rider" else "Mark Ready for Pickup", fontSize = 11.sp)
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
private fun SupportScreen(store: BlueGasStore, onBanner: (String) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AccountableHeader("Customer Support Lead", "Dispute Cases, Safety Inquiries & Post-Completion Incidents")
            Spacer(modifier = Modifier.height(10.dp))
            Text("Open Complaint Cases (${store.supportCases.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (store.supportCases.isEmpty()) {
            item {
                Text("No open support disputes.", fontSize = 11.sp, color = Color.Gray)
            }
        } else {
            items(store.supportCases) { c ->
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
                            Text("Case ${c.id} • ${c.issueType}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Surface(color = if (c.status == "OPEN") Color(0xFFFEE2E2) else Color(0xFFDCFCE7), shape = RoundedCornerShape(4.dp)) {
                                Text(c.status, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp, 2.dp))
                            }
                        }
                        Text("Order ID: ${c.orderId} (Order remains ORD_COMP Completed)", fontSize = 10.sp, color = Color(0xFF1E40AF))
                        Text(c.description, fontSize = 11.sp, modifier = Modifier.padding(vertical = 4.dp))

                        if (c.status == "OPEN") {
                            Button(
                                onClick = {
                                    c.status = "RESOLVED"
                                    c.resolutionNotes = "Support inspected with OGRA guidelines and satisfied customer."
                                    store.logAudit("SupportCase", c.id, "RESOLVE_CASE", "Support Lead", AccountableRole.SUPPORT, "Dispute marked resolved")
                                    store.notifyChanged()
                                    onBanner("Case ${c.id} resolved.")
                                },
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Mark Case Resolved", fontSize = 10.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------
// 5. ADMIN CONFIGURATION SUB-ROLE
// ----------------------------------------------------
@Composable
private fun AdminScreen(store: BlueGasStore, onBanner: (String) -> Unit) {
    var holdTtl by remember { mutableStateOf(store.priceBookConfig.holdTtlMinutes.toString()) }
    var platFee by remember { mutableStateOf((store.priceBookConfig.platformFeePaisa / 100).toString()) }
    var delFee by remember { mutableStateOf((store.priceBookConfig.deliveryFeePaisa / 100).toString()) }

    LazyColumn(modifier = Modifier.fillMaxSize()) {
        item {
            AccountableHeader("System Administrator", "Live Unconfirmed Business Parameters & Service Zones")
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
                            label = { Text("Hold TTL (Mins)", fontSize = 10.sp) },
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
                            label = { Text("Platform Fee (PKR)", fontSize = 10.sp) },
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
                            label = { Text("Delivery Fee (PKR)", fontSize = 10.sp) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Identity Verification Level at Sign-up:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("NONE", "PHONE_OTP", "CNIC").forEach { level ->
                            FilterChip(
                                selected = store.priceBookConfig.verificationLevel == level,
                                onClick = {
                                    store.priceBookConfig = store.priceBookConfig.copy(verificationLevel = level)
                                    store.notifyChanged()
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

        items(store.serviceZones) { zone ->
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
                            val idx = store.serviceZones.indexOf(zone)
                            store.serviceZones[idx] = zone.copy(isServiceable = active)
                            store.addresses.filter { it.zoneId == zone.id }.forEach { a ->
                                val aIdx = store.addresses.indexOf(a)
                                store.addresses[aIdx] = a.copy(isServiceable = active)
                            }
                            store.notifyChanged()
                            onBanner("Updated serviceability for ${zone.name}")
                        }
                    )
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
