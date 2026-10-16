package com.example.bluegas.ui.components

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
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.model.*
import com.example.ui.theme.*

/**
 * Status is always shown as a labelled chip using the exact canonical display name, never a paraphrase.
 */
@Composable
fun CanonicalStatusChip(status: OrderStatus, modifier: Modifier = Modifier) {
    val (bgColor, textColor) = when (status) {
        OrderStatus.ORD_COMP, OrderStatus.ORD_HOC -> Color(0xFFDCFCE7) to Color(0xFF166534)
        OrderStatus.ORD_CONF, OrderStatus.ORD_RSVD, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU, OrderStatus.ORD_ATHO, OrderStatus.ORD_INSP, OrderStatus.ORD_ADJ -> Color(0xFFFEF3C7) to Color(0xFF92400E)
        OrderStatus.ORD_EXC -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
        OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO -> Color(0xFFF1F5F9) to Color(0xFF475569)
        OrderStatus.ORD_SUPS -> Color(0xFFEDE9FE) to Color(0xFF5B21B6)
        OrderStatus.ORD_DRAFT, OrderStatus.ORD_VALPEND, OrderStatus.ORD_PAYPEND -> Color(0xFFF3F4F6) to Color(0xFF374151)
    }

    Surface(
        color = bgColor,
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, textColor.copy(alpha = 0.3f)),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(textColor)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = status.canonicalLabel,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                color = textColor
            )
        }
    }
}

/**
 * Open Sequencing Decision badge on Staff Portal.
 */
@Composable
fun OpenSequencingDecisionBadge() {
    Surface(
        color = Color(0xFFFFFBEB),
        shape = RoundedCornerShape(4.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFF59E0B)),
        modifier = Modifier.padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 3.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = Color(0xFFB45309),
                modifier = Modifier.size(13.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = "OPEN SEQUENCING DECISION (Under Technical Review)",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFFB45309)
            )
        }
    }
}

/**
 * Persistent Top Bar with Surface Switcher & Identity.
 */
@Composable
fun PersistentTopBar(
    store: BlueGasStore,
    activeSurface: AppSurface,
    isRiderOffline: Boolean,
    onOpenDevDrawer: () -> Unit,
    onOpenNotifications: () -> Unit,
    onOpenAiAdvisor: () -> Unit
) {
    val unreadCount = store.notifications.count { !it.isRead }

    Surface(
        color = BlueGasDark,
        shadowElevation = 6.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            // Main row
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                // Brand
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF0052CC)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.LocalFireDepartment,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "BLUE GAS",
                            fontWeight = FontWeight.Black,
                            fontSize = 16.sp,
                            color = Color.White,
                            letterSpacing = 1.sp
                        )
                        Text(
                            text = "LPG Pakistan Pilot",
                            fontSize = 9.sp,
                            color = Color(0xFF93C5FD)
                        )
                    }
                }

                // Action buttons
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // AI Advisor button
                    IconButton(
                        onClick = onOpenAiAdvisor,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Psychology,
                            contentDescription = "AI Advisor",
                            tint = Color(0xFF38BDF8),
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Notification bell
                    BadgedBox(
                        badge = {
                            if (unreadCount > 0) {
                                Badge(containerColor = Color(0xFFDC2626)) {
                                    Text(unreadCount.toString(), color = Color.White, fontSize = 10.sp)
                                }
                            }
                        }
                    ) {
                        IconButton(
                            onClick = onOpenNotifications,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Notifications,
                                contentDescription = "Notifications",
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    // Dev drawer toggle
                    IconButton(
                        onClick = onOpenDevDrawer,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Code,
                            contentDescription = "Dev Drawer",
                            tint = Color(0xFFFCD34D),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Reset Demo Data
                    IconButton(
                        onClick = { store.seedInitialData() },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.RestartAlt,
                            contentDescription = "Reset Demo Data",
                            tint = Color(0xFFCBD5E1),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Surface switcher pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                SurfaceTab(
                    label = "1. Customer App",
                    selected = activeSurface == AppSurface.CUSTOMER,
                    icon = Icons.Default.PhoneAndroid,
                    onClick = {
                        store.activeSurface = AppSurface.CUSTOMER
                        store.notifyChanged()
                    },
                    modifier = Modifier.weight(1f)
                )
                SurfaceTab(
                    label = "2. Rider App",
                    selected = activeSurface == AppSurface.RIDER,
                    icon = Icons.Default.TwoWheeler,
                    onClick = {
                        store.activeSurface = AppSurface.RIDER
                        store.notifyChanged()
                    },
                    modifier = Modifier.weight(1f)
                )
                SurfaceTab(
                    label = "3. Staff Portal",
                    selected = activeSurface == AppSurface.STAFF,
                    icon = Icons.Default.Dashboard,
                    onClick = {
                        store.activeSurface = AppSurface.STAFF
                        store.notifyChanged()
                    },
                    modifier = Modifier.weight(1f)
                )
            }

            // Seeded Demo Identity banner
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val identityText = when (activeSurface) {
                    AppSurface.CUSTOMER -> "Identity: Fatima Khan (Gulberg III, Lahore) • Customer"
                    AppSurface.RIDER -> {
                        val rider = store.getActiveRider()
                        "Identity: ${rider?.name ?: "Unknown Rider"} (${rider?.vehicleNumber ?: "N/A"}, ${rider?.zoneName ?: "N/A"}) • Rider ${if (isRiderOffline) "• [OFFLINE]" else "• [ONLINE]"}"
                    }
                    AppSurface.STAFF -> "Role: Staff Portal (${store.activeStaffSubRole.displayName}) • Accountable Human Authority"
                }
                Text(
                    text = identityText,
                    fontSize = 11.sp,
                    color = if (isRiderOffline && activeSurface == AppSurface.RIDER) Color(0xFFFCA5A5) else Color(0xFFCBD5E1),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SurfaceTab(
    label: String,
    selected: Boolean,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = if (selected) BlueGasPrimary else Color(0xFF1E293B),
        shape = RoundedCornerShape(6.dp),
        border = androidx.compose.foundation.BorderStroke(
            1.dp,
            if (selected) Color(0xFF60A5FA) else Color(0xFF334155)
        ),
        modifier = modifier.clickable(onClick = onClick)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.padding(vertical = 6.dp, horizontal = 4.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) Color.White else Color(0xFF94A3B8),
                modifier = Modifier.size(14.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = label,
                fontSize = 11.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                color = if (selected) Color.White else Color(0xFFCBD5E1),
                maxLines = 1
            )
        }
    }
}

/**
 * Developer Drawer / Inspector.
 */
@Composable
fun DevInspectorDialog(store: BlueGasStore, onDismiss: () -> Unit) {
    val selectedOrder = store.orders.find { it.id == store.selectedOrderIdForDrawer } ?: store.orders.firstOrNull()
    val history = selectedOrder?.let { o -> store.orderStatusHistory.filter { it.orderId == o.id } } ?: emptyList()
    val legalNext = selectedOrder?.let { OrderStateMachine.getLegalNextTransitions(it) } ?: emptyList()

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = onDismiss) { Text("Close Inspector") }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BugReport, contentDescription = null, tint = Color(0xFFD97706))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Developer Inspector & Raw State", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 500.dp)
            ) {
                item {
                    Text("Select Order to Inspect:", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        store.orders.take(5).forEach { ord ->
                            OutlinedButton(
                                onClick = {
                                    store.selectedOrderIdForDrawer = ord.id
                                    store.notifyChanged()
                                },
                                colors = ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (ord.id == selectedOrder?.id) Color(0xFFDBEAFE) else Color.Transparent
                                ),
                                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(ord.id.takeLast(7), fontSize = 10.sp)
                            }
                        }
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }

                if (selectedOrder != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Order ID: ${selectedOrder.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    CanonicalStatusChip(selectedOrder.status)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Mode: ${selectedOrder.fulfilmentMode.name}", fontSize = 11.sp)
                                Text("Customer: ${selectedOrder.customerId} | Address: ${selectedOrder.addressId}", fontSize = 11.sp)
                                Text("Allocated Serial: ${selectedOrder.allocatedSerial ?: "NONE"}", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                                Text("Deposit Line: ${formatPaisaToPkr(selectedOrder.depositTotalPaisa)} (Liability)", fontSize = 11.sp, color = Color(0xFFB45309))
                                Text("Total Amount: ${formatPaisaToPkr(selectedOrder.totalAmountPaisa)}", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                if (selectedOrder.exceptionReason != null) {
                                    Text("Exception Reason: ${selectedOrder.exceptionReason?.description}", fontSize = 11.sp, color = Color(0xFFDC2626))
                                    Text("Exception Details: ${selectedOrder.exceptionDetails}", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                    }

                    item {
                        Text("Legal Next Transitions (Strict 42-Table):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (legalNext.isEmpty()) {
                            Text("No legal outgoing transitions (Terminal status)", fontSize = 11.sp, color = Color.Gray)
                        } else {
                            Column(modifier = Modifier.padding(vertical = 4.dp)) {
                                legalNext.forEach { next ->
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        modifier = Modifier.padding(vertical = 2.dp)
                                    ) {
                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(12.dp), tint = Color(0xFF0284C7))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(next.canonicalLabel, fontSize = 11.sp, fontFamily = FontFamily.Monospace, fontWeight = FontWeight.SemiBold)
                                    }
                                }
                            }
                        }
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    }

                    item {
                        Text("Append-Only Status History (${history.size} events):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        if (history.isEmpty()) {
                            Text("No transitions recorded yet.", fontSize = 11.sp, color = Color.Gray)
                        }
                    }

                    items(history) { hist ->
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .background(Color(0xFFF1F5F9), RoundedCornerShape(4.dp))
                                .padding(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("${hist.fromStatus.code} ➔ ${hist.toStatus.code}", fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                Text(formatTimestamp(hist.timestamp), fontSize = 10.sp, color = Color.Gray)
                            }
                            Text("Actor: ${hist.actor} [${hist.role.displayName}]", fontSize = 10.sp)
                        }
                    }

                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        Text("Recent System Audit Trail (${store.auditLogs.size} logs):", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }

                    items(store.auditLogs.takeLast(6).reversed()) { log ->
                        Text(
                            text = "[${log.role.name}] ${log.action} on ${log.entityType}:${log.entityId} by ${log.actor}",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF334155),
                            modifier = Modifier.padding(vertical = 2.dp)
                        )
                    }
                }
            }
        }
    )
}

/**
 * Notifications Dialog.
 */
@Composable
fun NotificationCenterDialog(store: BlueGasStore, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            Button(onClick = {
                store.notifications.forEach { it.isRead = true }
                store.notifyChanged()
                onDismiss()
            }) {
                Text("Mark All Read")
            }
        },
        title = {
            Text("In-App Notification Center", fontWeight = FontWeight.Bold)
        },
        text = {
            if (store.notifications.isEmpty()) {
                Text("No notifications yet.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                    items(store.notifications) { item ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = if (item.isRead) Color(0xFFF8FAFC) else Color(0xFFEFF6FF)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        ) {
                            Column(modifier = Modifier.padding(8.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(item.title, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                    Surface(color = Color(0xFFDBEAFE), shape = RoundedCornerShape(4.dp)) {
                                        Text(item.canonicalStatus, fontSize = 9.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(4.dp, 2.dp))
                                    }
                                }
                                Text(item.message, fontSize = 11.sp, color = Color(0xFF1E293B))
                                Text(formatTimestamp(item.timestamp), fontSize = 9.sp, color = Color.Gray)
                            }
                        }
                    }
                }
            }
        }
    )
}
