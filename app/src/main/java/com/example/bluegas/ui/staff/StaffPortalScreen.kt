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
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.bluegas.ui.components.OpenSequencingDecisionBadge
import com.example.ui.theme.BlueGasPrimary

/**
 * Callback for staff screens to display a feedback banner with success or error styling.
 */
typealias StaffBanner = (message: String, isError: Boolean) -> Unit

/**
 * Reports a store operation Pair(success, message) to this [StaffBanner].
 *
 * @param r Result pair where first is true on success and second is the message.
 */
fun StaffBanner.report(r: Pair<Boolean, String>) = invoke(r.second, !r.first)

private data class StaffBannerState(
    val message: String,
    val isError: Boolean,
    val id: Long
)

@Composable
fun StaffPortalScreen(store: BlueGasStore) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    var bannerCounter by remember { mutableLongStateOf(0L) }
    var bannerState by remember { mutableStateOf<StaffBannerState?>(null) }
    var selectedOrderForDetail by remember { mutableStateOf<Order?>(null) }

    val showBanner: StaffBanner = { msg, isError ->
        bannerCounter++
        bannerState = StaffBannerState(message = msg, isError = isError, id = bannerCounter)
    }

    LaunchedEffect(bannerState?.id) {
        if (bannerState != null) {
            kotlinx.coroutines.delay(4000L)
            bannerState = null
        }
    }

    // If detail order ID was set from notification or store
    LaunchedEffect(snapshot.staffDetailOrderId) {
        val detailId = snapshot.staffDetailOrderId
        if (detailId != null) {
            val ord = snapshot.orders.find { it.id == detailId }
            if (ord != null) {
                selectedOrderForDetail = ord
            }
        }
    }

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

                // Scrollable Sub-Role Selector Buttons with unread badges
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(StaffSubRole.entries) { role ->
                        val isSelected = snapshot.activeStaffSubRole == role
                        val unreadCount = snapshot.unreadFor(AppSurface.STAFF, role)

                        Surface(
                            color = if (isSelected) Color(0xFF0284C7) else Color(0xFF1E293B),
                            shape = RoundedCornerShape(6.dp),
                            modifier = Modifier
                                .testTag("staff_subrole_${role.name.lowercase()}")
                                .clickable {
                                    store.setStaffRole(role)
                                }
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = role.displayName,
                                    fontSize = 11.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) Color.White else Color(0xFF94A3B8),
                                    maxLines = 1
                                )
                                if (unreadCount > 0) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFEF4444))
                                    ) {
                                        Text(
                                            text = if (unreadCount > 9) "9+" else unreadCount.toString(),
                                            color = Color.White,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Role-Scoped Content (All 9 Sub-Roles Fully Mapped) with overlay banner
        Box(
            modifier = Modifier
                .fillMaxSize()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp)
            ) {
                when (snapshot.activeStaffSubRole) {
                    StaffSubRole.OPERATIONS -> StaffOperationsScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = showBanner,
                        onSelectOrder = { selectedOrderForDetail = it }
                    )
                    StaffSubRole.HUB -> StaffHubScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = showBanner,
                        onSelectOrder = { selectedOrderForDetail = it }
                    )
                    StaffSubRole.PLANT_INVENTORY -> StaffPlantInventoryScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) }
                    )
                    StaffSubRole.FINANCE -> StaffFinanceScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) },
                        onSelectOrder = { selectedOrderForDetail = it }
                    )
                    StaffSubRole.COMMERCIAL -> StaffCommercialScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) }
                    )
                    StaffSubRole.SUPPORT -> StaffSupportScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) },
                        onSelectOrder = { selectedOrderForDetail = it }
                    )
                    StaffSubRole.SAFETY_HSE -> StaffSafetyHseScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) }
                    )
                    StaffSubRole.ADMIN -> StaffAdminScreen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) }
                    )
                    StaffSubRole.PHASE2_PREVIEW -> StaffPhase2Screen(
                        store = store,
                        snapshot = snapshot,
                        onBanner = { msg -> showBanner(msg, false) }
                    )
                }
            }

            // Overlay action banner (never pushes content down)
            val currentBanner = bannerState
            if (currentBanner != null) {
                val bgColor = if (currentBanner.isError) Color(0xFFFEF3C7) else Color(0xFFDCFCE7)
                val fgColor = if (currentBanner.isError) Color(0xFF92400E) else Color(0xFF166534)
                val borderColor = if (currentBanner.isError) Color(0xFFF59E0B) else Color(0xFF22C55E)
                Surface(
                    color = bgColor,
                    shape = RoundedCornerShape(8.dp),
                    shadowElevation = 6.dp,
                    border = androidx.compose.foundation.BorderStroke(1.dp, borderColor),
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                        .testTag("staff_action_banner")
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (currentBanner.isError) Icons.Default.Warning else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = fgColor,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = currentBanner.message,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = fgColor,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { bannerState = null },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Dismiss banner",
                                modifier = Modifier.size(16.dp),
                                tint = fgColor
                            )
                        }
                    }
                }
            }
        }
    }

    // Order detail modal dialog
    if (selectedOrderForDetail != null) {
        StaffOrderDetailDialog(
            order = selectedOrderForDetail!!,
            snapshot = snapshot,
            onDismiss = {
                selectedOrderForDetail = null
                store.openStaffOrderDetail(null)
            }
        )
    }
}

@Composable
fun AccountableHeader(roleName: String, description: String) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFFE2E8F0)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Text(
                text = "ACCOUNTABLE HUMAN ROLE: $roleName",
                fontWeight = FontWeight.Bold,
                fontSize = 11.sp,
                color = Color(0xFF0F172A)
            )
            Text(description, fontSize = 10.sp, color = Color(0xFF475569))
        }
    }
}

@Composable
fun StaffOrderDetailDialog(
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
