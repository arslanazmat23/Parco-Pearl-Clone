package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
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
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

enum class OrdersFilterTab(val label: String) {
    ALL("All"),
    ACTIVE("Active"),
    COMPLETED("Completed"),
    EXCEPTIONS("Exceptions")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrdersScreen(
    store: BlueGasStore,
    onTrackOrder: (String) -> Unit,
    onActionMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var selectedFilter by remember { mutableStateOf(OrdersFilterTab.ALL) }
    var searchQuery by remember { mutableStateOf("") }

    // Dialog state
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var selectedOrderIdForPayment by remember { mutableStateOf<String?>(null) }
    var showReceiptOrderId by remember { mutableStateOf<String?>(null) }
    var showComplaintOrderId by remember { mutableStateOf<String?>(null) }
    var complaintType by remember { mutableStateOf("LEAKAGE_SAFETY") }
    var complaintDetails by remember { mutableStateOf("") }
    var showCancelOrderId by remember { mutableStateOf<String?>(null) }
    var cancelReason by remember { mutableStateOf("Changed my mind") }

    val activeCustomer = store.customers.find { it.id == store.activeCustomerId } ?: store.customers.first()
    val allCustomerOrders = store.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }

    val filteredOrders = allCustomerOrders.filter { order ->
        val matchesSearch = searchQuery.isBlank() ||
                order.id.contains(searchQuery, ignoreCase = true) ||
                (order.allocatedSerial?.contains(searchQuery, ignoreCase = true) == true)

        val matchesTab = when (selectedFilter) {
            OrdersFilterTab.ALL -> true
            OrdersFilterTab.ACTIVE -> !order.status.isTerminal && order.status != OrderStatus.ORD_EXC
            OrdersFilterTab.COMPLETED -> order.status == OrderStatus.ORD_COMP
            OrdersFilterTab.EXCEPTIONS -> order.status == OrderStatus.ORD_EXC || order.status == OrderStatus.ORD_CANC || order.status == OrderStatus.ORD_FCLO
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

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            placeholder = { Text("Search by Order ID or Cylinder Serial...", fontSize = 12.sp) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { searchQuery = "" }) {
                        Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp))
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("orders_search_input")
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Filter Tabs
        SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
            OrdersFilterTab.values().forEachIndexed { index, tab ->
                SegmentedButton(
                    selected = selectedFilter == tab,
                    onClick = { selectedFilter = tab },
                    shape = SegmentedButtonDefaults.itemShape(index = index, count = OrdersFilterTab.values().size)
                ) {
                    Text(tab.label, fontSize = 11.sp, maxLines = 1)
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Orders List
        if (filteredOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ReceiptLong,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No orders found for '${selectedFilter.label}'",
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF64748B),
                        fontSize = 13.sp
                    )
                    Text(
                        text = "Your booking history and active deliveries appear here.",
                        color = Color(0xFF94A3B8),
                        fontSize = 11.sp
                    )
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
                        onTrackOrder = { onTrackOrder(order.id) },
                        onPayOnline = {
                            selectedOrderIdForPayment = order.id
                            showPaymentGatewayDialog = true
                        },
                        onViewReceipt = {
                            showReceiptOrderId = order.id
                        },
                        onReportComplaint = {
                            showComplaintOrderId = order.id
                        },
                        onCancelOrder = {
                            showCancelOrderId = order.id
                            cancelReason = "Changed my mind"
                        }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }

    // Dialog 1: Hosted Payment Gateway Dialog
    if (showPaymentGatewayDialog && selectedOrderIdForPayment != null) {
        val ord = store.orders.find { it.id == selectedOrderIdForPayment }

        AlertDialog(
            onDismissRequest = { showPaymentGatewayDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.AccountBalance, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pay Online (Hosted Gateway)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "Total Amount: ${ord?.totalAmountPaisa?.let { formatPaisaToPkr(it) }}",
                        fontSize = 14.sp,
                        color = BlueGasPrimary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Demonstrator: Choose simulation outcome for payment authorization:", fontSize = 11.sp)
                    Spacer(modifier = Modifier.height(10.dp))

                    Button(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "SUCCESS")
                            onActionMessage(msg)
                            showPaymentGatewayDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simulate Payment SUCCESS")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Button(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "DECLINED")
                            onActionMessage(msg)
                            showPaymentGatewayDialog = false
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simulate Payment DECLINED")
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedButton(
                        onClick = {
                            val (_, msg) = store.confirmPaymentAndAdvance(ord?.id ?: "", "TIMEOUT")
                            onActionMessage(msg)
                            showPaymentGatewayDialog = false
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Simulate Payment TIMEOUT")
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showPaymentGatewayDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog 2: Official Delivery Receipt Dialog
    if (showReceiptOrderId != null) {
        val ord = store.orders.find { it.id == showReceiptOrderId }
        var isDownloaded by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showReceiptOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Receipt, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Official Delivery Receipt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order ID: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text(
                        "POD Reference: ${ord?.podReference ?: "POD-${ord?.id?.takeLast(4)}-DONE"}",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = BlueGasPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Cylinder Serial: ${ord?.allocatedSerial ?: "CYL-SERIAL-VERIFIED"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF047857)
                    )
                    Text(
                        "Status: Handover Complete & Safety Inspected",
                        fontSize = 10.sp,
                        color = Color(0xFF166534),
                        fontWeight = FontWeight.SemiBold
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(6.dp))

                    ord?.items?.forEach { itm ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("${itm.quantity}x ${itm.productName}", fontSize = 11.sp)
                            Text(formatPaisaToPkr(itm.subtotalPaisa), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    if ((ord?.depositTotalPaisa ?: 0L) > 0) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Cylinder Security Deposit", fontSize = 11.sp, color = Color(0xFFB45309))
                            Text(formatPaisaToPkr(ord?.depositTotalPaisa ?: 0L), fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Fulfilment / Delivery Fee", fontSize = 11.sp)
                        Text(formatPaisaToPkr(ord?.deliveryFeePaisa ?: 0L), fontSize = 11.sp)
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Sales Tax (5%)", fontSize = 11.sp)
                        Text(formatPaisaToPkr(ord?.taxPaisa ?: 0L), fontSize = 11.sp)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total Amount Paid:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        Text(formatPaisaToPkr(ord?.totalAmountPaisa ?: 0L), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BlueGasPrimary)
                    }

                    if (isDownloaded) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "✓ PDF Receipt saved to phone storage: receipt_${ord?.id}.pdf",
                                fontSize = 10.sp,
                                color = Color(0xFF166534),
                                modifier = Modifier.padding(6.dp)
                            )
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = { isDownloaded = true }) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Download PDF", fontSize = 11.sp)
                }
            },
            dismissButton = {
                TextButton(onClick = { showReceiptOrderId = null }) { Text("Close") }
            }
        )
    }

    // Dialog 3: Report Dispute / Safety Issue Dialog
    if (showComplaintOrderId != null) {
        AlertDialog(
            onDismissRequest = { showComplaintOrderId = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Report Safety Issue / Dispute", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Order: $showComplaintOrderId", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(
                        "Note: Raising a complaint creates an accountable SupportCase without mutating completed order status.",
                        fontSize = 10.sp,
                        color = Color(0xFFB45309)
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    Text("Issue Type:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    listOf(
                        "LEAKAGE_SAFETY" to "Cylinder Leakage / Safety Concern",
                        "CYLINDER_DENT" to "Dented / Damaged Cylinder Body",
                        "OVERCHARGING" to "Overcharging or Change Dispute",
                        "DELAYED_DELIVERY" to "Severe Delivery Delay"
                    ).forEach { (code, label) ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { complaintType = code }
                        ) {
                            RadioButton(selected = complaintType == code, onClick = { complaintType = code })
                            Text(label, fontSize = 11.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(6.dp))
                    OutlinedTextField(
                        value = complaintDetails,
                        onValueChange = { complaintDetails = it },
                        placeholder = { Text("Provide details or description of safety issue...", fontSize = 11.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val (_, msg) = store.raiseComplaint(
                            orderId = showComplaintOrderId ?: "",
                            issueType = complaintType,
                            description = complaintDetails.ifBlank { "Customer filed $complaintType" }
                        )
                        onActionMessage(msg)
                        showComplaintOrderId = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626))
                ) {
                    Text("Submit Dispute Case")
                }
            },
            dismissButton = {
                TextButton(onClick = { showComplaintOrderId = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog 4: Cancel Order Dialog
    if (showCancelOrderId != null) {
        val ord = store.orders.find { it.id == showCancelOrderId }
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
                Column {
                    Text("Order ID: ${ord?.id}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Are you sure you want to cancel this order? Any active stock holds and allocated cylinders will be released back to inventory. If you already paid online, a full refund will be processed.",
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
                        val (_, msg) = store.cancelOrder(showCancelOrderId ?: "", cancelReason)
                        onActionMessage(msg)
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

@Composable
private fun CustomerOrderCard(
    order: Order,
    store: BlueGasStore,
    onTrackOrder: () -> Unit,
    onPayOnline: () -> Unit,
    onViewReceipt: () -> Unit,
    onReportComplaint: () -> Unit,
    onCancelOrder: () -> Unit
) {
    val payment = store.payments.find { it.orderId == order.id }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: ID + Status Chip
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
            order.items.forEach { itm ->
                Text(
                    text = "${itm.quantity}x ${itm.productName}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Pricing & Payment Method
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Total: ${formatPaisaToPkr(order.totalAmountPaisa)}",
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
                            PaymentMethod.ONLINE_HOSTED -> "Online Hosted"
                            PaymentMethod.CASH_ON_DELIVERY -> "Cash on Delivery"
                        },
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF475569),
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
            }

            // Bound Serial Badge if allocated
            if (order.allocatedSerial != null) {
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
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
                            text = "Bound Cylinder Serial: ${order.allocatedSerial}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            }

            // Hold TTL countdown for ORD_PAYPEND
            if (order.status == OrderStatus.ORD_PAYPEND && order.holdExpiresAt != null) {
                val remainingMs = order.holdExpiresAt - System.currentTimeMillis()
                val remainingMins = (remainingMs / 60000).coerceAtLeast(0)
                Spacer(modifier = Modifier.height(6.dp))
                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Timer,
                            contentDescription = null,
                            tint = Color(0xFFB45309),
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Stock Hold TTL: ~${remainingMins} min remaining before auto-release",
                            fontSize = 10.sp,
                            color = Color(0xFFB45309),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Action Buttons
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Track button
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

                // Pay Online button for ORD_PAYPEND
                if (order.status == OrderStatus.ORD_PAYPEND && order.paymentMethod == PaymentMethod.ONLINE_HOSTED) {
                    Button(
                        onClick = onPayOnline,
                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                        modifier = Modifier
                            .weight(1f)
                            .height(34.dp)
                            .testTag("pay_online_${order.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.Payment, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Pay Online", fontSize = 11.sp)
                    }
                }

                // Receipt and Dispute buttons for completed
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

                // Cancel button for active orders prior to final dispatch
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
        }
    }
}
