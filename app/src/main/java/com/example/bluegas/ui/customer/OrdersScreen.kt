/**
 * Order history, lifecycle status review, and post-order interactions for the customer.
 *
 * Implements:
 * - Filtered order lists (All, Active non-terminal orders, Completed, and Cancelled/Terminated)
 * - In-place online payment actions with reservation countdowns ([PaymentActionsRow])
 * - Reorder trigger to prefill checkout form for fast repurchase
 * - Detailed formal receipt viewing with breakdown verification ([ReceiptDialog])
 * - Order cancellation with state validation and finance refund notices
 * - Structured complaint filing with emergency 1122 dispatch integration
 * - Delivery rating and customer satisfaction feedback ([RateOrderDialog])
 *
 * Reads: [SharedAppState.orders], [SharedAppState.payments], [SharedAppState.activeCustomer]
 * Calls: [BlueGasStore.cancelOrder], [BlueGasStore.raiseComplaint]
 */
package com.example.bluegas.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.SharedAppState
import com.example.bluegas.data.cancelOrder
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

/**
 * Filter categories dividing orders into logical stages based on their terminal status.
 *
 * @property label User-facing tab title.
 */
enum class OrdersFilterTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled")
}

/**
 * Screen displaying the customer's historical and ongoing LPG cylinder orders.
 *
 * Allows filtering by status, search by order ID, viewing tracking details,
 * launching demo payment gateways, downloading receipts, and lodging formal complaints.
 *
 * @param store Application data store reference.
 * @param onTrackOrder Navigates to the live tracking view for a specific order.
 * @param onActionMessage Customer banner callback receiving (message: String, isError: Boolean).
 * @param onReorder Populates the checkout form with the items and fulfilment mode of an existing order.
 * @param onNavigateToHome Switches active tab to the Home ordering screen.
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    store: BlueGasStore,
    onTrackOrder: (String) -> Unit,
    onActionMessage: (String, Boolean) -> Unit,
    onReorder: (Order) -> Unit = {},
    onNavigateToHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var selectedFilter by remember { mutableStateOf(OrdersFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog states
    var selectedOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showReceiptOrderId by remember { mutableStateOf<String?>(null) }
    var showRateOrderId by remember { mutableStateOf<String?>(null) }
    var showComplaintOrderId by remember { mutableStateOf<String?>(null) }
    var complaintType by remember { mutableStateOf("LEAKAGE_SAFETY") }
    var complaintDetails by remember { mutableStateOf("") }
    var showCancelOrderId by remember { mutableStateOf<String?>(null) }
    var cancelReason by remember { mutableStateOf("Changed my mind") }

    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val allCustomerOrders = snapshot.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }

    // Filter orders by search text and status tab
    val filteredOrders = allCustomerOrders.filter { order ->
        val matchesSearch = searchQuery.isBlank() ||
                order.id.contains(searchQuery, ignoreCase = true) ||
                (order.allocatedSerial?.contains(searchQuery, ignoreCase = true) == true)

        // Filter tab partitioning: CANCELLED includes customer/staff cancelled (ORD_CANC),
        // failed closed after payment failure or expiration (ORD_FCLO), and superseded attempts (ORD_SUPS).
        val matchesTab = when (selectedFilter) {
            OrdersFilterTab.ALL -> true
            OrdersFilterTab.ACTIVE -> !order.status.isTerminal
            OrdersFilterTab.COMPLETED -> order.status == OrderStatus.ORD_COMP
            OrdersFilterTab.CANCELLED -> order.status in setOf(OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO, OrderStatus.ORD_SUPS)
        }
        matchesSearch && matchesTab
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_orders_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "My LPG Orders",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueGasDark
                )
                Text(
                    text = "Total ${allCustomerOrders.size} orders recorded",
                    fontSize = 11.sp,
                    color = Color(0xFF64748B)
                )
            }

            Surface(
                color = Color(0xFFDBEAFE),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${allCustomerOrders.count { !it.status.isTerminal }} Active",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueGasPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Search bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by Order ID or Cylinder ID", fontSize = 11.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(16.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier.fillMaxWidth().testTag("orders_search_input")
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Filter Tabs
        ScrollableTabRow(
            selectedTabIndex = selectedFilter.ordinal,
            edgePadding = 0.dp,
            divider = {},
            containerColor = Color.Transparent,
            modifier = Modifier.fillMaxWidth().testTag("orders_filter_tabs")
        ) {
            OrdersFilterTab.entries.forEach { tab ->
                val count = when (tab) {
                    OrdersFilterTab.ALL -> allCustomerOrders.size
                    OrdersFilterTab.ACTIVE -> allCustomerOrders.count { !it.status.isTerminal }
                    OrdersFilterTab.COMPLETED -> allCustomerOrders.count { it.status == OrderStatus.ORD_COMP }
                    OrdersFilterTab.CANCELLED -> allCustomerOrders.count { it.status in setOf(OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO, OrderStatus.ORD_SUPS) }
                }
                Tab(
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab },
                    text = {
                        Text(
                            text = "${tab.label} ($count)",
                            fontSize = 11.sp,
                            fontWeight = if (selectedFilter == tab) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Empty state illustration when no orders match current filter
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.Inbox,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (allCustomerOrders.isEmpty()) {
                        Text(
                            text = "No orders yet",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = onNavigateToHome,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier.testTag("orders_empty_order_now_button")
                        ) {
                            Text("Order now", fontSize = 12.sp)
                        }
                    } else {
                        Text(
                            text = "No ${selectedFilter.label.lowercase()} orders",
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color(0xFF64748B)
                        )
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredOrders, key = { it.id }) { order ->
                    CustomerOrderCard(
                        order = order,
                        store = store,
                        snapshot = snapshot,
                        onTrackOrder = { onTrackOrder(order.id) },
                        onOpenPaymentGateway = { selectedOrderIdForPayment = order.id },
                        onViewReceipt = { showReceiptOrderId = order.id },
                        onRateOrder = { showRateOrderId = order.id },
                        onReportComplaint = {
                            complaintDetails = ""
                            complaintType = "LEAKAGE_SAFETY"
                            showComplaintOrderId = order.id
                        },
                        onCancelOrder = {
                            cancelReason = "Changed my mind"
                            showCancelOrderId = order.id
                        },
                        onReorder = { onReorder(order) },
                        onNavigateToHome = onNavigateToHome,
                        onActionMessage = onActionMessage
                    )
                }
            }
        }
    }

    // Online Payment Dialog
    if (selectedOrderIdForPayment != null) {
        val ord = snapshot.orders.find { it.id == selectedOrderIdForPayment }
        DemoPaymentDialog(
            orderId = selectedOrderIdForPayment!!,
            payableLabel = formatRupees(ord?.totalAmountPaisa ?: 0L),
            store = store,
            onResult = { msg, isError ->
                onActionMessage(msg, isError)
                selectedOrderIdForPayment = null
            },
            onDismiss = { selectedOrderIdForPayment = null }
        )
    }

    // Receipt Dialog
    if (showReceiptOrderId != null) {
        val ord = snapshot.orders.find { it.id == showReceiptOrderId }
        if (ord != null) {
            ReceiptDialog(
                order = ord,
                snapshot = snapshot,
                onDismiss = { showReceiptOrderId = null }
            )
        }
    }

    // Delivery Feedback & Rating Dialog
    if (showRateOrderId != null) {
        RateOrderDialog(
            orderId = showRateOrderId!!,
            store = store,
            onResult = { msg, isError ->
                onActionMessage(msg, isError)
                showRateOrderId = null
            },
            onDismiss = { showRateOrderId = null }
        )
    }

    // Support Complaint Dialog
    if (showComplaintOrderId != null) {
        val complaintTypes = listOf(
            "LEAKAGE_SAFETY" to "Cylinder Leakage / Safety Concern",
            "CYLINDER_CONDITION" to "Cylinder condition problem",
            "DELAYED_DELIVERY" to "Unacceptable Delivery Delay",
            "WRONG_CYLINDER_SIZE" to "Incorrect Cylinder Size Delivered",
            "PAYMENT_DISPUTE" to "Payment Discrepancy",
            "OTHER" to "General Support Inquiry"
        )

        AlertDialog(
            onDismissRequest = { showComplaintOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report a problem", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Order: $showComplaintOrderId", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF0F172A))
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "We'll open a support case. Your order stays as it is.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    if (complaintType == "LEAKAGE_SAFETY") {
                        Surface(
                            color = Color(0xFFFEF2F2),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Text(
                                    text = "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF991B1B),
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(6.dp))
                                Button(
                                    onClick = {
                                        val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:1122"))
                                        context.startActivity(dialIntent)
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("call_1122_button")
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Call 1122", fontSize = 12.sp)
                                }
                            }
                        }
                    }

                    Text("Issue Type:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    complaintTypes.forEach { (typeKey, typeLabel) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { complaintType = typeKey }
                        ) {
                            RadioButton(selected = complaintType == typeKey, onClick = { complaintType = typeKey })
                            Text(typeLabel, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = complaintDetails,
                        onValueChange = { complaintDetails = it },
                        label = { Text("Describe the issue (optional)", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val finalDesc = if (complaintDetails.isBlank()) {
                            complaintTypes.find { it.first == complaintType }?.second ?: complaintType
                        } else {
                            complaintDetails.trim()
                        }
                        val (ok, msg) = store.raiseComplaint(showComplaintOrderId ?: "", complaintType, finalDesc)
                        onActionMessage(msg, !ok)
                        showComplaintOrderId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("submit_complaint_button")
                ) {
                    Text("Submit")
                }
            },
            dismissButton = {
                TextButton(onClick = { showComplaintOrderId = null }) { Text("Cancel") }
            }
        )
    }

    // Modal dialog for customer-initiated order cancellation
    if (showCancelOrderId != null) {
        val ord = snapshot.orders.find { it.id == showCancelOrderId }
        val cancelReasons = listOf(
            "Changed my mind",
            "Ordered wrong cylinder size",
            "Delivery taking too long",
            "Need delivery to different address",
            "Financial / emergency reason"
        )

        AlertDialog(
            onDismissRequest = { showCancelOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Order", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text("Order ID: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Your reserved cylinder will be released. If you already paid online, the refund is handled by our Finance team.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Select cancellation reason:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    cancelReasons.forEach { r ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { cancelReason = r }
                        ) {
                            RadioButton(selected = cancelReason == r, onClick = { cancelReason = r })
                            Text(r, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (ok, msg) = store.cancelOrder(showCancelOrderId ?: "", cancelReason)
                        onActionMessage(msg, !ok)
                        showCancelOrderId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_cancel_order_button")
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelOrderId = null }) {
                    Text("Keep Order")
                }
            }
        )
    }
}

/**
 * Visual card representing a single order in the customer's history list.
 *
 * Displays order ID, date, status chip, item summaries, total amount due or paid,
 * bound cylinder serial if allocated, in-place payment actions, and context-dependent
 * action buttons (Track, Receipt, Cancel, Rate, Report a problem, Reorder).
 */
@Composable
private fun CustomerOrderCard(
    order: Order,
    store: BlueGasStore,
    snapshot: SharedAppState,
    onTrackOrder: () -> Unit,
    onOpenPaymentGateway: () -> Unit,
    onViewReceipt: () -> Unit,
    onRateOrder: () -> Unit,
    onReportComplaint: () -> Unit,
    onCancelOrder: () -> Unit,
    onReorder: () -> Unit,
    onNavigateToHome: () -> Unit,
    onActionMessage: (String, Boolean) -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}")
            .then(
                if (!order.status.isTerminal) {
                    Modifier.clickable { onTrackOrder() }
                } else Modifier
            )
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: ID + Date + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "• ${formatTimestamp(order.createdAt)}",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(6.dp))

            // Items Summary
            order.customerVisibleItems().forEach { itm ->
                Text(
                    text = "${itm.quantity}x ${itm.productName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Price summary and payment method badge
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${formatRupees(order.totalAmountPaisa)}",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueGasPrimary
                )
                Surface(
                    color = Color(0xFFF1F5F9),
                    shape = RoundedCornerShape(4.dp)
                ) {
                    Text(
                        text = when (order.paymentMethod) {
                            PaymentMethod.ONLINE_HOSTED -> "Online payment"
                            PaymentMethod.CASH_ON_DELIVERY -> "Cash on delivery"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Hardware serial badge for allocated cylinder
            if (order.allocatedSerial != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(6.dp),
                    border = BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.QrCode,
                            contentDescription = null,
                            tint = Color(0xFF047857),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Cylinder ID: ${order.allocatedSerial}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }

            // Contextual actions row when payment is pending
            if (order.status == OrderStatus.ORD_PAYPEND) {
                Spacer(modifier = Modifier.height(8.dp))
                PaymentActionsRow(
                    order = order,
                    snapshot = snapshot,
                    store = store,
                    onResult = onActionMessage,
                    onOpenGateway = onOpenPaymentGateway
                )
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Track button (for active orders)
                if (!order.status.isTerminal) {
                    OutlinedButton(
                        onClick = onTrackOrder,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("track_order_${order.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Navigation, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Track Order", fontSize = 11.sp)
                    }
                }

                // Completed orders: Receipt and Rate
                if (order.status == OrderStatus.ORD_COMP) {
                    FilledTonalButton(
                        onClick = onViewReceipt,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("view_receipt_${order.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Receipt", fontSize = 11.sp)
                    }

                    if (order.ratingStars != null) {
                        Surface(
                            color = Color(0xFFFEF3C7),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.weight(1f).height(34.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Rated ${order.ratingStars}/5", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color(0xFF92400E))
                            }
                        }
                    } else {
                        Button(
                            onClick = onRateOrder,
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(34.dp)
                                .testTag("rate_order_${order.id}"),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                        ) {
                            Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(13.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Rate", fontSize = 11.sp)
                        }
                    }

                    OutlinedButton(
                        onClick = onReportComplaint,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("dispute_button_${order.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Dispute", fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }

                // Cancellation eligibility: mirrors OrderStateMachine allowed transitions. Customers may
                // cancel pre-dispatch orders (draft through prep and recoverable exceptions), but not after
                // physical dispatch, vehicle transit, or handover execution has begun.
                val isCancellable = order.status in setOf(
                    OrderStatus.ORD_DRAFT,
                    OrderStatus.ORD_VALPEND,
                    OrderStatus.ORD_PAYPEND,
                    OrderStatus.ORD_CONF,
                    OrderStatus.ORD_RSVD,
                    OrderStatus.ORD_ASGN,
                    OrderStatus.ORD_ACPT,
                    OrderStatus.ORD_PREP,
                    OrderStatus.ORD_EXC
                )

                if (isCancellable) {
                    OutlinedButton(
                        onClick = onCancelOrder,
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("cancel_order_${order.id}"),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(13.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Cancel", fontSize = 11.sp, color = Color(0xFFDC2626))
                    }
                }
            }

            // Quick Reorder from Completed Orders
            if (order.status == OrderStatus.ORD_COMP) {
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = onReorder,
                    modifier = Modifier.fillMaxWidth().height(36.dp).testTag("reorder_button_${order.id}"),
                    colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(Icons.Default.Replay, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reorder", fontSize = 12.sp)
                }
            }

            // Place New Order CTA on Terminal-State Cards
            if (order.status == OrderStatus.ORD_CANC || order.status == OrderStatus.ORD_SUPS || order.status == OrderStatus.ORD_FCLO) {
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = onNavigateToHome,
                    modifier = Modifier.fillMaxWidth().height(36.dp),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, BlueGasPrimary)
                ) {
                    Icon(Icons.Default.AddShoppingCart, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Place New Order", fontSize = 12.sp, color = BlueGasPrimary)
                }
            }
        }
    }
}
