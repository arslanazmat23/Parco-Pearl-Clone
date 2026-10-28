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
import com.example.bluegas.data.*
import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.bluegas.ui.components.OpenSequencingDecisionBadge
import com.example.ui.theme.BlueGasPrimary

@Composable
fun StaffDistributorScreen(
    store: BlueGasStore,
    snapshot: SharedAppState,
    onBanner: (String) -> Unit,
    onSelectOrder: (Order) -> Unit
) {
    val assignedOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_ASGN }
    val acceptedOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_ACPT }
    val preparingOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_PREP }
    val readyPickupOrders = snapshot.orders.filter { it.status == OrderStatus.ORD_RDYPU }
    val availableCylinders = snapshot.cylinders.filter { it.status == InventoryState.INV_AVAIL && !it.hseHold }

    var showRequestStockDialog by remember { mutableStateOf(false) }
    var requested118Qty by remember { mutableStateOf("10") }
    var requested110Qty by remember { mutableStateOf("5") }
    var requested60Qty by remember { mutableStateOf("2") }
    var selectedRiderForOrder by remember { mutableStateOf(mapOf<String, String>()) }
    var expandedRiderDropdownForOrder by remember { mutableStateOf<String?>(null) }

    val unhandedCodOrders = snapshot.orders.filter { ord ->
        ord.status == OrderStatus.ORD_COMP &&
                ord.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                ord.fulfilmentMode == FulfilmentMode.PICKUP
    }
    val unhandedCashTotal = unhandedCodOrders.sumOf { it.totalAmountPaisa }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("distributor_screen")
    ) {
        item {
            AccountableHeader(
                roleName = "Distributor Lead",
                description = "Order Acceptance, Exact Serial Binding, Stock Commit & Cash Reconciliation"
            )
            Spacer(modifier = Modifier.height(10.dp))

            OpenSequencingDecisionBadge()
            Spacer(modifier = Modifier.height(8.dp))

            // Stock Overview & Action Row
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.White),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Node Inventory & Stock Level", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Button(
                            onClick = { showRequestStockDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(12.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Request Stock from Plant", fontSize = 10.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        snapshot.stockBalances.filter { it.pointId == "NODE-LHR-1" }.forEach { sb ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF1F5F9)),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(6.dp)) {
                                    Text(sb.skuId.replace("SKU-LPG-", ""), fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                    Text("Full: ${sb.filledQty}", fontSize = 11.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                                    Text("Empty: ${sb.emptyQty}", fontSize = 9.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                }
            }

            // Cash Handover Box
            if (unhandedCashTotal > 0) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCD34D)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Pending Counter Cash Collection", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF92400E))
                            Text("Unhanded Cash: ${formatPaisaToPkr(unhandedCashTotal)} across ${unhandedCodOrders.size} pickup orders", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                val (ok, msg) = store.depotSubmitCashHandover("NODE-LHR-1", unhandedCashTotal)
                                onBanner(msg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("Submit Handover", fontSize = 10.sp)
                        }
                    }
                }
            }

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
                            Text("SLA Accepted • Ready to start physical staging", fontSize = 10.sp, color = Color(0xFF166534))
                        }
                        Button(
                            onClick = {
                                val (ok, msg) = store.startPreparing(ord.id)
                                if (ok) {
                                    onBanner("Order ${ord.id} is now Preparing (ORD_PREP).")
                                } else {
                                    onBanner(msg)
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
                            Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            CanonicalStatusChip(ord.status)
                        }
                        Text("Mode: ${ord.fulfilmentMode.name} • Allocated Serial: ${ord.allocatedSerial ?: "None"}", fontSize = 11.sp)

                        if (ord.allocatedSerial == null) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Text("Bind Exact Scanned Serial:", fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                            LazyRow(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                items(availableCylinders.take(6)) { cyl ->
                                    FilterChip(
                                        selected = false,
                                        onClick = {
                                            val (s, m) = store.allocateCylinder(ord.id, cyl.serialNumber)
                                            onBanner(m)
                                        },
                                        label = { Text(cyl.serialNumber, fontSize = 9.sp) }
                                    )
                                }
                            }
                        } else {
                            Spacer(modifier = Modifier.height(8.dp))
                            if (ord.fulfilmentMode == FulfilmentMode.PICKUP) {
                                Button(
                                    onClick = {
                                        val (ok, msg) = store.dispatchOrReadyForPickup(ord.id)
                                        if (ok) {
                                            onBanner("Order ${ord.id} is Ready for Customer Pickup.")
                                        } else {
                                            onBanner(msg)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Mark Ready for Counter Pickup", fontSize = 11.sp)
                                }
                            } else {
                                val distId = ord.assignedDistributorId ?: snapshot.distributors.firstOrNull()?.id
                                val eligibleRiders = snapshot.riders.filter {
                                    it.distributorId == distId &&
                                            it.onboardingStatus == "APPROVED" &&
                                            it.status == "ACTIVE" &&
                                            it.installationTrained
                                }

                                val hasAssignedRider = ord.assignedRiderId != null && ord.riderTaskStatus != RiderTaskStatus.DECLINED

                                if (hasAssignedRider) {
                                    val assignedRider = snapshot.riders.find { it.id == ord.assignedRiderId }
                                    val riderName = assignedRider?.name ?: ord.assignedRiderId ?: "Rider"
                                    val isOffered = ord.riderTaskStatus == RiderTaskStatus.OFFERED
                                    Surface(
                                        color = if (isOffered) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                                        shape = RoundedCornerShape(6.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                if (isOffered) Icons.Default.Schedule else Icons.Default.CheckCircle,
                                                contentDescription = null,
                                                tint = if (isOffered) Color(0xFFD97706) else Color(0xFF166534),
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (isOffered) "Offered to $riderName — waiting" else "Accepted by $riderName",
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                color = if (isOffered) Color(0xFF92400E) else Color(0xFF166534)
                                            )
                                        }
                                    }
                                } else {
                                    val isDeclined = ord.riderTaskStatus == RiderTaskStatus.DECLINED
                                    if (isDeclined) {
                                        Text(
                                            "Task declined by rider — Reassign rider:",
                                            fontSize = 10.sp,
                                            color = Color(0xFFDC2626),
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    } else {
                                        Text(
                                            "Assign Rider for Delivery:",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Spacer(modifier = Modifier.height(4.dp))
                                    }

                                    if (eligibleRiders.isEmpty()) {
                                        Text(
                                            "No approved, on-shift, trained riders available.",
                                            fontSize = 10.sp,
                                            color = Color.Gray
                                        )
                                    } else {
                                        val currentSelectedRiderId = selectedRiderForOrder[ord.id] ?: eligibleRiders.first().id
                                        val selectedRider = eligibleRiders.find { it.id == currentSelectedRiderId } ?: eligibleRiders.first()
                                        val activeCount = snapshot.orders.count { it.assignedRiderId == selectedRider.id && !it.status.isTerminal && it.status != OrderStatus.ORD_COMP }

                                        Box(modifier = Modifier.fillMaxWidth()) {
                                            OutlinedButton(
                                                onClick = { expandedRiderDropdownForOrder = ord.id },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .height(42.dp),
                                                shape = RoundedCornerShape(6.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        "${selectedRider.name} · ${selectedRider.zoneName} · $activeCount active",
                                                        fontSize = 11.sp,
                                                        fontWeight = FontWeight.Medium,
                                                        color = Color(0xFF0F172A)
                                                    )
                                                    Icon(Icons.Default.ArrowDropDown, contentDescription = "Select rider", modifier = Modifier.size(16.dp))
                                                }
                                            }
                                            DropdownMenu(
                                                expanded = expandedRiderDropdownForOrder == ord.id,
                                                onDismissRequest = { expandedRiderDropdownForOrder = null }
                                            ) {
                                                eligibleRiders.forEach { r ->
                                                    val rActive = snapshot.orders.count { it.assignedRiderId == r.id && !it.status.isTerminal && it.status != OrderStatus.ORD_COMP }
                                                    DropdownMenuItem(
                                                        text = {
                                                            Text("${r.name} · ${r.zoneName} · $rActive active", fontSize = 11.sp)
                                                        },
                                                        onClick = {
                                                            selectedRiderForOrder = selectedRiderForOrder + (ord.id to r.id)
                                                            expandedRiderDropdownForOrder = null
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(6.dp))
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.assignRider(ord.id, selectedRider.id)
                                                onBanner(msg)
                                            },
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(38.dp),
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            Text(if (isDeclined) "Reassign & Offer to Rider" else "Offer to rider", fontSize = 11.sp)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Ready for pickup section
        if (readyPickupOrders.isNotEmpty()) {
            item {
                Spacer(modifier = Modifier.height(10.dp))
                Text("Ready for Customer Depot Pickup (${readyPickupOrders.size}):", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Spacer(modifier = Modifier.height(6.dp))
            }

            items(readyPickupOrders) { ord ->
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
                            Text("Pickup on ${ord.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("Customer: ${ord.customerId} • Serial: ${ord.allocatedSerial}", fontSize = 10.sp)
                        }
                        Button(
                            onClick = {
                                val (ok, msg) = store.completeDepotPickup(ord.id)
                                onBanner(msg)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier.height(32.dp)
                        ) {
                            Text("Complete Handover", fontSize = 10.sp)
                        }
                    }
                }
            }
        }
    }

    // Request Stock Dialog
    if (showRequestStockDialog) {
        AlertDialog(
            onDismissRequest = { showRequestStockDialog = false },
            title = { Text("Request Stock from Central Plant", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("Select quantities of approved household cylinders:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = requested118Qty,
                        onValueChange = { requested118Qty = it },
                        label = { Text("11.8kg Household Cylinders") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = requested110Qty,
                        onValueChange = { requested110Qty = it },
                        label = { Text("11.0kg Compact Cylinders") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = requested60Qty,
                        onValueChange = { requested60Qty = it },
                        label = { Text("6.0kg Small Cylinders") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val q118 = requested118Qty.toIntOrNull() ?: 0
                        val q110 = requested110Qty.toIntOrNull() ?: 0
                        val q60 = requested60Qty.toIntOrNull() ?: 0
                        val lines = mapOf(
                            "SKU-LPG-11-8" to q118,
                            "SKU-LPG-11-0" to q110,
                            "SKU-LPG-6-0" to q60
                        ).filter { it.value > 0 }

                        val (ok, msg) = store.requestStock("NODE-LHR-1", lines, "Distributor Lead")
                        onBanner(msg)
                        showRequestStockDialog = false
                    }
                ) {
                    Text("Submit Request")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { showRequestStockDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}
