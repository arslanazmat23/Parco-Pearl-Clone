package com.example.bluegas.ui.rider

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

@Composable
fun RiderAppScreen(store: BlueGasStore) {
    var actionBanner by remember { mutableStateOf<String?>(null) }
    var showPodDialog by remember { mutableStateOf(false) }
    var targetOrderIdForPod by remember { mutableStateOf<String?>(null) }
    var leakCheckPassed by remember { mutableStateOf(true) }
    var valveIntact by remember { mutableStateOf(true) }
    var customerNotes by remember { mutableStateOf("") }
    var collectedCashPaisa by remember { mutableStateOf(0L) }

    val activeRiderOrders = store.orders.filter {
        it.assignedRiderId == store.activeRiderId || it.status == OrderStatus.ORD_DISP || it.status == OrderStatus.ORD_ATHO
    }

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
                // Header with OFFLINE TOGGLE
                Surface(
                    color = if (store.isRiderOffline) Color(0xFF7F1D1D) else BlueGasDark,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text("RIDER DELIVERY CONSOLE", fontSize = 11.sp, color = Color(0xFF93C5FD), fontWeight = FontWeight.Bold)
                                Text("Tariq Mahmood (Rider 01)", fontSize = 15.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("Vehicle: Suzuki Bolan (LEC-8812) • Lahore", fontSize = 10.sp, color = Color(0xFFCBD5E1))
                            }

                            // Offline Toggle
                            Surface(
                                color = if (store.isRiderOffline) Color(0xFFDC2626) else Color(0xFF334155),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (store.isRiderOffline) Icons.Default.CloudOff else Icons.Default.CloudQueue,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        if (store.isRiderOffline) "OFFLINE" else "ONLINE",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Switch(
                                        checked = store.isRiderOffline,
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

                        // Offline status notification banner
                        if (store.isRiderOffline) {
                            Spacer(modifier = Modifier.height(6.dp))
                            Surface(
                                color = Color(0xFF991B1B),
                                shape = RoundedCornerShape(4.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text(
                                    "⚠️ Offline Mode: Deliveries will queue to local Outbox with Idempotency Keys. Store will reconcile on sync.",
                                    fontSize = 10.sp,
                                    color = Color(0xFFFEF2F2),
                                    modifier = Modifier.padding(6.dp)
                                )
                            }
                        }
                    }
                }

                // Demo Controls Bar: Conflict Simulation & Outbox Replay
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
                        // Demo Conflict Button
                        Button(
                            onClick = {
                                val msg = store.simulateSerialConflict()
                                actionBanner = msg
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1.1f)
                                .height(32.dp)
                        ) {
                            Icon(Icons.Default.ElectricBolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Simulate Conflict", fontSize = 10.sp)
                        }

                        // Replay Outbox Button
                        Button(
                            onClick = {
                                val report = store.replayRiderOutbox()
                                actionBanner = report
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0F766E)),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(32.dp),
                            enabled = store.riderOutbox.isNotEmpty() && !store.isRiderOffline
                        ) {
                            Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync Outbox (${store.riderOutbox.size})", fontSize = 10.sp)
                        }
                    }
                }

                if (actionBanner != null) {
                    Surface(color = Color(0xFFFEF3C7), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            Text(actionBanner ?: "", fontSize = 11.sp, color = Color(0xFF78350F), modifier = Modifier.weight(1f))
                            IconButton(onClick = { actionBanner = null }, modifier = Modifier.size(20.dp)) {
                                Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(12.dp))
                            }
                        }
                    }
                }

                // Scrollable Body
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(12.dp)
                ) {
                    // Local Outbox Section
                    if (store.riderOutbox.isNotEmpty()) {
                        item {
                            Text("Offline Outbox Queue (${store.riderOutbox.size} events):", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFB45309))
                            Spacer(modifier = Modifier.height(4.dp))
                        }
                        items(store.riderOutbox) { evt ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFFBEB)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 3.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Seq #${evt.sequenceNumber}: ${evt.action}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                        Text("Order: ${evt.orderId} • Serial: ${evt.scannedSerial ?: "N/A"}", fontSize = 10.sp)
                                        Text(evt.idempotencyKey, fontSize = 9.sp, fontFamily = FontFamily.Monospace, color = Color.Gray)
                                    }
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
                                        Text(
                                            evt.syncStatus,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(4.dp, 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                        item {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        }
                    }

                    // Assigned Deliveries
                    item {
                        Text("Active Assigned Deliveries:", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    if (activeRiderOrders.isEmpty()) {
                        item {
                            Text("No dispatched deliveries assigned to your van currently.", fontSize = 11.sp, color = Color.Gray)
                        }
                    } else {
                        items(activeRiderOrders) { ord ->
                            val addr = store.addresses.find { it.id == ord.addressId }
                            val cust = store.customers.find { it.id == ord.customerId }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 6.dp)
                                    .clickable {
                                        store.selectedOrderIdForDrawer = ord.id
                                        store.notifyChanged()
                                    }
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                        CanonicalStatusChip(ord.status)
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text("Customer: ${cust?.name} (${cust?.phone})", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                    Text("Address: ${addr?.street}, ${addr?.area}, ${addr?.city}", fontSize = 10.sp, color = Color(0xFF334155))

                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(4.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 4.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(6.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF0284C7), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                "Cylinder Serial: ${ord.allocatedSerial ?: "PENDING SERIAL"}",
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp,
                                                color = Color(0xFF0369A1)
                                            )
                                        }
                                    }

                                    Text(
                                        "Payment: ${ord.paymentMethod.name} (${formatPaisaToPkr(ord.totalAmountPaisa)})",
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = if (ord.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFD97706) else Color(0xFF16A34A)
                                    )

                                    // Action buttons for rider
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 8.dp),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        if (ord.status == OrderStatus.ORD_DISP) {
                                            Button(
                                                onClick = {
                                                    val (success, msg) = store.arriveAtHandover(ord.id)
                                                    actionBanner = msg
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Arrive at Doorstep", fontSize = 11.sp)
                                            }
                                        }

                                        if (ord.status == OrderStatus.ORD_ATHO) {
                                            Button(
                                                onClick = {
                                                    targetOrderIdForPod = ord.id
                                                    collectedCashPaisa = ord.totalAmountPaisa
                                                    showPodDialog = true
                                                },
                                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Handover & POD", fontSize = 11.sp)
                                            }
                                        }

                                        // Exception button
                                        OutlinedButton(
                                            onClick = {
                                                val (s, m) = store.raiseFulfilmentException(
                                                    orderId = ord.id,
                                                    reason = ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
                                                    details = "Rider unable to reach customer after 3 call attempts."
                                                )
                                                actionBanner = m
                                            },
                                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                            modifier = Modifier.height(38.dp)
                                        ) {
                                            Text("Fail / Issue", fontSize = 10.sp)
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

    // Proof of Delivery & Safety Checklist Dialog
    if (showPodDialog && targetOrderIdForPod != null) {
        val ord = store.orders.find { it.id == targetOrderIdForPod }

        AlertDialog(
            onDismissRequest = { showPodDialog = false },
            confirmButton = {
                Button(
                    onClick = {
                        val (s, m) = store.completeHandover(
                            orderId = targetOrderIdForPod ?: "",
                            nonTechInstallationPassed = leakCheckPassed && valveIntact,
                            proofOfDeliveryNote = "Safety check: LeakPass=$leakCheckPassed ValvePass=$valveIntact. $customerNotes",
                            collectedCashPaisa = if (ord?.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) collectedCashPaisa else null
                        )
                        actionBanner = m
                        showPodDialog = false
                    },
                    enabled = leakCheckPassed && valveIntact
                ) {
                    Text("Confirm Handover Complete")
                }
            },
            dismissButton = {
                TextButton(onClick = { showPodDialog = false }) { Text("Cancel") }
            },
            title = {
                Text("Safety Inspection & Proof of Handover", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold)
                    Text("Serial Bound: ${ord?.allocatedSerial ?: "N/A"}", fontSize = 11.sp, color = BlueGasPrimary)
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Non-Technical Installation & Safety Checklist:", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = leakCheckPassed, onCheckedChange = { leakCheckPassed = it })
                        Text("Regulator connection & Soapy water bubble test PASSED", fontSize = 11.sp)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = valveIntact, onCheckedChange = { valveIntact = it })
                        Text("Safety valve intact & protective plastic seal verified", fontSize = 11.sp)
                    }

                    if (ord?.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            "Collect Cash: ${formatPaisaToPkr(ord.totalAmountPaisa)}",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFFD97706)
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = customerNotes,
                        onValueChange = { customerNotes = it },
                        placeholder = { Text("Customer handover notes...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2
                    )
                }
            }
        )
    }
}
