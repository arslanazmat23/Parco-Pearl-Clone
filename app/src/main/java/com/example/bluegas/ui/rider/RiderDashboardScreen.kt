package com.example.bluegas.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlinx.coroutines.delay

@Composable
fun RiderDashboardScreen(
    store: BlueGasStore,
    onAcceptOrder: (String) -> Unit,
    onDeclineOrder: (String) -> Unit,
    onNavigateToActive: () -> Unit,
    onSyncOutbox: () -> Unit,
    onSimulateConflict: () -> Unit,
    onActionMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRider = store.getActiveRider() ?: store.riders.first()
    val isRiderActive = activeRider.status == "ACTIVE"

    val riderOrders = store.orders.filter {
        it.assignedRiderId == activeRider.id && it.status.ordinal >= OrderStatus.ORD_ASGN.ordinal
    }
    val pendingOrders = riderOrders.filter { it.status == OrderStatus.ORD_ASGN }
    val completedOrders = riderOrders.filter { it.status == OrderStatus.ORD_COMP }
    val todayEarningsPaisa = completedOrders.size * 25000L // PKR 250 per delivery

    val pendingOutboxCount = store.riderOutbox.count { it.syncStatus == "PENDING" }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("rider_dashboard_screen"),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // 1. Rider Header
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = BlueGasDark),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("rider_header_card")
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(BlueGasPrimary),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = activeRider.name.split(" ").mapNotNull { it.firstOrNull()?.toString() }.take(2).joinToString(""),
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = activeRider.name,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                                Text(
                                    text = "${activeRider.zoneName} • ${activeRider.vehicleNumber}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF93C5FD)
                                )
                            }
                        }

                        // Shift status chip + button
                        Surface(
                            color = if (isRiderActive) Color(0xFF166534) else Color(0xFF991B1B),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.clickable {
                                store.toggleRiderShiftStatus(activeRider.id)
                                onActionMessage(
                                    if (!isRiderActive) "Shift Started! You are now ONLINE." else "Shift Ended. You are now OFFLINE.",
                                    false
                                )
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isRiderActive) Color(0xFF86EFAC) else Color(0xFFFCA5A5))
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = if (isRiderActive) "ON SHIFT" else "OFF SHIFT",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            }
                        }
                    }
                }
            }
        }

        // 2. Connectivity & Offline Warning Banner
        item {
            if (store.isRiderOffline) {
                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("offline_warning_banner")
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.WifiOff,
                                contentDescription = null,
                                tint = Color(0xFFD97706),
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "OFFLINE MODE ACTIVE",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF92400E)
                                )
                                Text(
                                    text = "Actions queued locally in Outbox ($pendingOutboxCount pending events).",
                                    fontSize = 10.sp,
                                    color = Color(0xFFB45309)
                                )
                            }
                        }

                        // Toggle offline switch
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Switch(
                                checked = store.isRiderOffline,
                                onCheckedChange = {
                                    store.isRiderOffline = it
                                    store.notifyChanged()
                                    onActionMessage(if (it) "Offline simulation enabled" else "Online reconnected", false)
                                },
                                modifier = Modifier.testTag("offline_toggle_switch")
                            )
                        }
                    }
                }
            } else if (pendingOutboxCount > 0) {
                // Online but has pending events in outbox
                Surface(
                    color = Color(0xFFEFF6FF),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "$pendingOutboxCount offline event(s) ready to sync",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color(0xFF1E3A8A)
                            )
                        }
                        Button(
                            onClick = onSyncOutbox,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier.height(32.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp)
                        ) {
                            Text("Sync Now", fontSize = 11.sp)
                        }
                    }
                }
            }
        }

        // If Shift is Inactive, show "Start Shift" Gate
        if (!isRiderActive) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(14.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFFEF2F2)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsBike,
                                contentDescription = null,
                                tint = Color(0xFFDC2626),
                                modifier = Modifier.size(32.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "You are Currently Off Shift",
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            color = Color(0xFF0F172A)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Start your shift to receive delivery assignments, view customer addresses, and log cylinder handovers.",
                            fontSize = 11.sp,
                            color = Color(0xFF64748B),
                            textAlign = TextAlign.Center,
                            lineHeight = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                store.toggleRiderShiftStatus(activeRider.id)
                                onActionMessage("Welcome on shift, ${activeRider.name}!", false)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                                .testTag("start_shift_button")
                        ) {
                            Icon(Icons.Default.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Start Today's Shift", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }
                }
            }
        } else {
            // 3. Today's Stats Cards
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    StatCard(
                        title = "Completed",
                        value = "${completedOrders.size}",
                        subtitle = "Today",
                        icon = Icons.Default.CheckCircle,
                        color = Color(0xFF16A34A),
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "Earnings",
                        value = formatPaisaToPkr(todayEarningsPaisa).replace(".00", ""),
                        subtitle = "@ 250/deliv",
                        icon = Icons.Default.Payments,
                        color = BlueGasPrimary,
                        modifier = Modifier.weight(1f)
                    )
                    StatCard(
                        title = "New Tasks",
                        value = "${pendingOrders.size}",
                        subtitle = "Pending",
                        icon = Icons.Default.Assignment,
                        color = if (pendingOrders.isNotEmpty()) Color(0xFFD97706) else Color(0xFF64748B),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 4. Pending Assignments Section (ORD_ASGN)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "New Assignments",
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = BlueGasDark
                        )
                        if (pendingOrders.isNotEmpty()) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Surface(
                                color = Color(0xFFFEF3C7),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Text(
                                    text = "${pendingOrders.size} Action Required",
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFB45309),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                }
            }

            if (pendingOrders.isEmpty()) {
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.LocalShipping,
                                contentDescription = null,
                                tint = Color(0xFF94A3B8),
                                modifier = Modifier.size(44.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No new assignments right now",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp,
                                color = Color(0xFF475569)
                            )
                            Text(
                                text = "Keep app open. Orders assigned by distributor dispatchers will appear here in real-time.",
                                fontSize = 11.sp,
                                color = Color(0xFF94A3B8),
                                textAlign = TextAlign.Center,
                                lineHeight = 15.sp
                            )
                        }
                    }
                }
            } else {
                items(pendingOrders, key = { it.id }) { order ->
                    PendingAssignmentCard(
                        order = order,
                        store = store,
                        onAccept = { onAcceptOrder(order.id) },
                        onDecline = { onDeclineOrder(order.id) }
                    )
                }
            }

            // 5. Quick Sync & Demo Utilities Section
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Field Sync & Network Controls",
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp,
                                color = Color(0xFF0F172A)
                            )
                            Surface(
                                color = Color(0xFFF1F5F9),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = if (store.isRiderOffline) "SIMULATED OFFLINE" else "NETWORK ONLINE",
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (store.isRiderOffline) Color(0xFFD97706) else Color(0xFF16A34A),
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onSyncOutbox,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("sync_outbox_button"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Sync, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Replay Outbox", fontSize = 11.sp)
                            }

                            OutlinedButton(
                                onClick = onSimulateConflict,
                                modifier = Modifier
                                    .weight(1f)
                                    .height(34.dp)
                                    .testTag("simulate_conflict_button"),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Demo Conflict", fontSize = 11.sp, color = Color(0xFFD97706))
                            }
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun StatCard(
    title: String,
    value: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(10.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = title, fontSize = 11.sp, color = Color(0xFF64748B), fontWeight = FontWeight.Medium)
                Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Color(0xFF0F172A))
            Text(text = subtitle, fontSize = 9.sp, color = Color(0xFF94A3B8))
        }
    }
}

@Composable
private fun PendingAssignmentCard(
    order: Order,
    store: BlueGasStore,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    val customer = store.customers.find { it.id == order.customerId }
    val address = store.addresses.find { it.id == order.addressId }
    val windowMinutes = store.priceBookConfig.distributorAcceptanceWindowMins
    val assignedAt = order.assignedAt ?: order.createdAt
    val deadlineMs = assignedAt + (windowMinutes * 60 * 1000L)

    var remainingMs by remember(deadlineMs) {
        mutableLongStateOf((deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L))
    }

    LaunchedEffect(deadlineMs) {
        while (remainingMs > 0) {
            delay(1000L)
            remainingMs = (deadlineMs - System.currentTimeMillis()).coerceAtLeast(0L)
        }
        if (remainingMs <= 0) {
            // Auto-decline / timeout expired
            store.riderDeclineOrder(order.id, "Acceptance window expired (SLA_TIMEOUT)")
        }
    }

    val minutes = (remainingMs / 1000) / 60
    val seconds = (remainingMs / 1000) % 60
    val timerColor = when {
        remainingMs > 600000L -> Color(0xFF16A34A) // > 10m: Green
        remainingMs > 300000L -> Color(0xFFD97706) // 5-10m: Amber
        else -> Color(0xFFDC2626) // < 5m: Red
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("pending_order_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Top Row: ID, Timer & Fulfilment Mode
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
                    color = Color(0xFF0F172A)
                )

                // Acceptance Countdown Timer
                Surface(
                    color = timerColor.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Timer, contentDescription = null, tint = timerColor, modifier = Modifier.size(12.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = String.format("%02d:%02d", minutes, seconds),
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = timerColor,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer & Address Info
            Text(
                text = "${customer?.name ?: "Customer"} • ${customer?.phone ?: ""}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "${address?.label ?: "Address"}: ${address?.area ?: ""}, ${address?.city ?: ""}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Products list
            order.items.forEach { itm ->
                Text(
                    text = "${itm.quantity}x ${itm.productName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Badges & Total
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        color = Color(0xFFEFF6FF),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "🚴 Delivery" else "🏪 Pickup",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = BlueGasPrimary,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }

                    Surface(
                        color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFFEF3C7) else Color(0xFFDCFCE7),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) "💵 COD: ${formatPaisaToPkr(order.totalAmountPaisa)}" else "💳 Prepaid Online",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFB45309) else Color(0xFF166534),
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Actions: Accept / Decline
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onDecline,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("decline_order_${order.id}"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Decline", fontSize = 11.sp, color = Color(0xFFDC2626))
                }

                Button(
                    onClick = onAccept,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier
                        .weight(1f)
                        .height(36.dp)
                        .testTag("accept_order_${order.id}"),
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Accept SLA", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
