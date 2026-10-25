/**
 * Live order tracking, dispatch progress, handover authentication, and delivery verification.
 *
 * Implements:
 * - Status hero banner with visual urgency cues and plain-language operational status
 * - Handover PIN security card (visible from Dispatched to Handover completion)
 * - Depot pickup location guide with arrival notification trigger ([BlueGasStore.notifyPickupArrival])
 * - Simulated real-time delivery map with animated rider vehicle progression
 * - Assigned delivery rider credentials with direct phone dialer intent
 * - Factual cylinder serial tracking and doorstep installation verification
 * - 6-stage lifecycle progression stepper with terminal exception handling
 * - Payment, hold reservation, and refund reconciliation status card
 * - Order cancellation modal with reason capture ([BlueGasStore.cancelOrder])
 * - Delivery rating and formal receipt viewing
 *
 * Reads: [SharedAppState.orders], [SharedAppState.payments], [SharedAppState.refunds],
 *        [SharedAppState.orderStatusHistory], [SharedAppState.nodes], [SharedAppState.riders]
 * Calls: [BlueGasStore.cancelOrder], [BlueGasStore.notifyPickupArrival]
 */
package com.example.bluegas.ui.customer

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FactCheck
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary
import kotlin.math.min

/**
 * Maps internal exception codes to clear, reassuring customer-facing descriptions.
 * Appends " Operations is working on it." for all recoverable exceptions.
 */
fun formatCustomerExceptionMessage(reason: ExceptionReason?): String {
    val base = when (reason) {
        ExceptionReason.VALIDATION_OUT_OF_AREA -> "We couldn't confirm your address or delivery area."
        ExceptionReason.PAYMENT_ISSUE -> "There's a problem with your payment."
        ExceptionReason.NO_ACCEPTANCE -> "We're finding a delivery partner for your order."
        ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE -> "Your order needs a little more time to prepare."
        ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE -> "We couldn't complete the handover."
        ExceptionReason.GOODS_REJECTED_AT_DOOR -> "The cylinder was not accepted at handover."
        ExceptionReason.ADJUSTED_AMOUNT_REJECTION -> "The adjusted amount was not accepted."
        ExceptionReason.UNRECOVERABLE_AFTER_RETRIES -> "We couldn't complete this order."
        null -> "There's an issue with your order."
    }
    return if (reason == ExceptionReason.UNRECOVERABLE_AFTER_RETRIES) {
        base
    } else {
        "$base Operations is working on it."
    }
}

/**
 * Screen providing comprehensive real-time tracking for customer orders.
 *
 * @param store Application data store reference.
 * @param initialOrderId Optional order ID targeted when navigating to tracking.
 * @param onSelectOrderToTrack Callback when user picks a different order from dropdown.
 * @param onBackToOrders Callback to navigate back to the Orders list tab.
 * @param onActionMessage Customer notification banner callback receiving (message: String, isError: Boolean).
 * @param modifier Optional layout modifier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    store: BlueGasStore,
    initialOrderId: String?,
    onSelectOrderToTrack: (String) -> Unit,
    onBackToOrders: () -> Unit,
    onActionMessage: ((String, Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerOrders = snapshot.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }

    // Order selection priority: prefers the requested order from navigation, then the most recent active non-terminal order.
    // Terminal states (Completed, Cancelled, Closed) fall back to the newest order in history.
    var selectedOrderId by remember(initialOrderId, customerOrders.size) {
        mutableStateOf(
            if (initialOrderId != null && customerOrders.any { it.id == initialOrderId }) initialOrderId
            else customerOrders.firstOrNull { !it.status.isTerminal }?.id ?: customerOrders.firstOrNull()?.id
        )
    }

    // Resolves assigned delivery rider, dispatch node facility, and active delivery transit records.
    val currentOrder = customerOrders.find { it.id == selectedOrderId }
    val assignedRider = snapshot.riders.find { it.id == currentOrder?.assignedRiderId }
    val depotNode = snapshot.nodes.find { it.id == (currentOrder?.pickupPointId ?: currentOrder?.fulfilmentPointId ?: assignedRider?.nodeId) }
    val deliveryInfo = snapshot.deliveries.find { it.orderId == currentOrder?.id }

    // Dialog & Banner state
    var localBanner by remember { mutableStateOf<BannerMsg?>(null) }
    val postMessage: (String, Boolean) -> Unit = { msg, isErr ->
        if (onActionMessage != null) {
            onActionMessage(msg, isErr)
        } else {
            localBanner = BannerMsg(msg, isErr)
        }
    }

    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("Changed my mind") }
    var showPaymentGatewayDialog by remember { mutableStateOf(false) }
    var showReceiptDialog by remember { mutableStateOf(false) }
    var showRateDialog by remember { mutableStateOf(false) }

    // Pickup arrival confirmation dialog state
    var depotNotifiedLocal by remember(currentOrder?.id) {
        mutableStateOf(
            snapshot.notifications.any {
                it.orderId == currentOrder?.id && it.title == "Customer Arrived for Pickup"
            }
        )
    }

    // Live ticker for animated delivery progress and countdown
    val nowMillis = rememberNowMillis(enabled = currentOrder?.status == OrderStatus.ORD_DISP || currentOrder?.status == OrderStatus.ORD_PAYPEND)

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("customer_tracking_screen")
    ) {
        // Top Bar / Order Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBackToOrders, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Orders")
                }
                Spacer(modifier = Modifier.width(4.dp))
                Column {
                    Text(
                        text = "Live Order Tracking",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = BlueGasDark
                    )
                    Text(
                        text = "Follow your order step by step",
                        fontSize = 10.sp,
                        color = Color(0xFF64748B)
                    )
                }
            }

            // Order Selector Dropdown if multiple orders
            if (customerOrders.size > 1) {
                var expanded by remember { mutableStateOf(false) }
                Box {
                    OutlinedButton(
                        onClick = { expanded = true },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                        modifier = Modifier.height(30.dp)
                    ) {
                        Text(currentOrder?.id ?: "Select", fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                        customerOrders.forEach { ord ->
                            DropdownMenuItem(
                                text = {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(ord.id, fontWeight = FontWeight.Bold, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        CanonicalStatusChip(ord.status)
                                    }
                                },
                                onClick = {
                                    selectedOrderId = ord.id
                                    onSelectOrderToTrack(ord.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Local fallback banner if shell banner not attached
        AnimatedVisibility(
            visible = localBanner != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            localBanner?.let { banner ->
                Surface(
                    color = if (banner.isError) Color(0xFFFEE2E2) else Color(0xFFDCFCE7),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = if (banner.isError) Icons.Default.Error else Icons.Default.CheckCircle,
                            contentDescription = null,
                            tint = if (banner.isError) Color(0xFFDC2626) else Color(0xFF16A34A),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = banner.text,
                            fontSize = 11.sp,
                            color = if (banner.isError) Color(0xFF991B1B) else Color(0xFF166534),
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { localBanner = null },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Dismiss", modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }
        }

        if (currentOrder == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.LocationOff,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(48.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "No order selected for tracking",
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp,
                        color = Color(0xFF64748B)
                    )
                    Text(
                        text = "Place an order on the Home tab to track live delivery.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Section 1: Order Status Hero Banner
                item {
                    val isPickup = currentOrder.fulfilmentMode == FulfilmentMode.PICKUP
                    val depotName = depotNode?.name ?: "Depot"
                    val remainingMs = (currentOrder.holdExpiresAt ?: 0L) - nowMillis
                    val countdownStr = formatCountdown(remainingMs)

                    // Hero banner mapping: maps each status to plain customer language.
                    // Internal operational diagnostics and staff exception details are never exposed to customers.
                    val (tintColor, accentColor, iconVec, titleText, subtitleText) = when (currentOrder.status) {
                        OrderStatus.ORD_DRAFT, OrderStatus.ORD_VALPEND -> HeroBannerConfig(
                            tint = Color(0xFFEFF6FF),
                            accent = Color(0xFF3B82F6),
                            icon = Icons.Default.PendingActions,
                            title = "Checking your order",
                            subtitle = "We're confirming your address and stock."
                        )
                        OrderStatus.ORD_PAYPEND -> HeroBannerConfig(
                            tint = Color(0xFFFFFBEB),
                            accent = Color(0xFFF59E0B),
                            icon = Icons.Default.Timer,
                            title = "Waiting for payment",
                            subtitle = "Pay within $countdownStr to keep your cylinder reserved."
                        )
                        OrderStatus.ORD_CONF, OrderStatus.ORD_RSVD -> HeroBannerConfig(
                            tint = Color(0xFFEFF6FF),
                            accent = Color(0xFF3B82F6),
                            icon = Icons.Default.CheckCircle,
                            title = "Order confirmed",
                            subtitle = if (isPickup) "Your depot is being assigned." else "Your cylinder is reserved."
                        )
                        OrderStatus.ORD_ASGN -> HeroBannerConfig(
                            tint = Color(0xFFEFF6FF),
                            accent = Color(0xFF3B82F6),
                            icon = Icons.Default.PersonSearch,
                            title = "Assigned",
                            subtitle = if (isPickup) "Your depot has been assigned." else "A delivery partner has been assigned."
                        )
                        OrderStatus.ORD_ACPT -> HeroBannerConfig(
                            tint = Color(0xFFEFF6FF),
                            accent = Color(0xFF3B82F6),
                            icon = Icons.Default.ThumbUp,
                            title = "Order accepted",
                            subtitle = "Your order is being organised."
                        )
                        OrderStatus.ORD_PREP -> HeroBannerConfig(
                            tint = Color(0xFFFFFBEB),
                            accent = Color(0xFFF59E0B),
                            icon = Icons.Default.Build,
                            title = "Preparing your order",
                            subtitle = "Your cylinder is being made ready."
                        )
                        OrderStatus.ORD_DISP -> HeroBannerConfig(
                            tint = Color(0xFFEFF6FF),
                            accent = Color(0xFF3B82F6),
                            icon = Icons.Default.DeliveryDining,
                            title = "On the way",
                            subtitle = "Your rider is heading to your address."
                        )
                        OrderStatus.ORD_RDYPU -> HeroBannerConfig(
                            tint = Color(0xFFF0FDF4),
                            accent = Color(0xFF10B981),
                            icon = Icons.Default.Store,
                            title = "Ready for pickup",
                            subtitle = "Your cylinder is ready at $depotName."
                        )
                        OrderStatus.ORD_ATHO -> HeroBannerConfig(
                            tint = Color(0xFFF0FDF4),
                            accent = Color(0xFF10B981),
                            icon = Icons.Default.PinDrop,
                            title = if (isPickup) "You're at the depot" else "Rider has arrived",
                            subtitle = if (isPickup) "Show your handover code at the counter." else "Please share your handover code with the rider."
                        )
                        OrderStatus.ORD_INSP, OrderStatus.ORD_ADJ -> HeroBannerConfig(
                            tint = Color(0xFFFFFBEB),
                            accent = Color(0xFFF59E0B),
                            icon = Icons.AutoMirrored.Filled.FactCheck,
                            title = "Handover in progress",
                            subtitle = "Final checks and payment are being completed."
                        )
                        OrderStatus.ORD_HOC -> HeroBannerConfig(
                            tint = Color(0xFFF0FDF4),
                            accent = Color(0xFF10B981),
                            icon = Icons.Default.Verified,
                            title = "Handover complete",
                            subtitle = "We're finalising your receipt."
                        )
                        OrderStatus.ORD_COMP -> HeroBannerConfig(
                            tint = Color(0xFFF0FDF4),
                            accent = Color(0xFF10B981),
                            icon = Icons.Default.Verified,
                            title = if (isPickup) "Collected" else "Delivered",
                            subtitle = "Thank you for choosing BLUE GAS."
                        )
                        OrderStatus.ORD_EXC -> HeroBannerConfig(
                            tint = Color(0xFFFEF2F2),
                            accent = Color(0xFFEF4444),
                            icon = Icons.Default.Warning,
                            title = "We're sorting something out",
                            subtitle = formatCustomerExceptionMessage(currentOrder.exceptionReason)
                        )
                        OrderStatus.ORD_CANC -> HeroBannerConfig(
                            tint = Color(0xFFF8FAFC),
                            accent = Color(0xFF94A3B8),
                            icon = Icons.Default.Cancel,
                            title = "Order cancelled",
                            subtitle = currentOrder.cancelReason ?: "This order was cancelled."
                        )
                        OrderStatus.ORD_FCLO -> HeroBannerConfig(
                            tint = Color(0xFFF8FAFC),
                            accent = Color(0xFF94A3B8),
                            icon = Icons.Default.Cancel,
                            title = "Order closed",
                            subtitle = "This order could not be completed."
                        )
                        OrderStatus.ORD_SUPS -> HeroBannerConfig(
                            tint = Color(0xFFF5F3FF),
                            accent = Color(0xFF8B5CF6),
                            icon = Icons.Default.ChangeCircle,
                            title = "Order replaced",
                            subtitle = "A newer order replaced this one."
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = tintColor),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, accentColor),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tracking_status_hero")
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(accentColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconVec,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = titleText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color(0xFF0F172A)
                                )
                                Text(
                                    text = subtitleText,
                                    fontSize = 11.sp,
                                    color = Color(0xFF475569),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Section 2: Payment Status and Actions Card
                item {
                    val orderPayments = snapshot.payments.filter { it.orderId == currentOrder.id }
                    val latestPayment = orderPayments.maxByOrNull { it.attemptNo }
                    val orderRefunds: List<Refund> = snapshot.refunds.filter { it.orderId == currentOrder.id }
                    val latestRefund = orderRefunds.maxByOrNull { it.timestamp }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().testTag("tracking_payment_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Payment", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = BlueGasDark)
                                if (currentOrder.status == OrderStatus.ORD_PAYPEND) {
                                    Surface(color = Color(0xFFFFFBEB), shape = RoundedCornerShape(4.dp)) {
                                        Text(
                                            "Payment Pending",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color(0xFFB45309),
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))

                            // Payment description logic using formatCustomerPaymentStatus
                            if (currentOrder.status == OrderStatus.ORD_CANC) {
                                val refundText = when {
                                    latestRefund != null && (latestRefund.state == PaymentState.FULL_REFUND_PENDING || latestRefund.state == PaymentState.PARTIAL_REFUND_PENDING) ->
                                        "Refund of ${formatRupees(latestRefund.amountPaisa)} is being processed by Finance."
                                    latestRefund != null && (latestRefund.state == PaymentState.FULLY_REFUNDED || latestRefund.state == PaymentState.PARTIAL_REFUND_COMPLETED) ->
                                        "Refund of ${formatRupees(latestRefund.amountPaisa)} completed."
                                    latestPayment?.state == PaymentState.VOIDED ->
                                        "Your payment hold was released."
                                    else ->
                                        "No payment was taken."
                                }
                                Text(refundText, fontSize = 12.sp, color = Color(0xFF475569))
                            } else if (currentOrder.status == OrderStatus.ORD_PAYPEND) {
                                PaymentActionsRow(
                                    order = currentOrder,
                                    snapshot = snapshot,
                                    store = store,
                                    onResult = postMessage,
                                    onOpenGateway = { showPaymentGatewayDialog = true }
                                )
                            } else {
                                // Payment card wording delegates to formatCustomerPaymentStatus.
                                // Note that pre-authorized payments are confirmed but not captured until physical delivery handover.
                                val statusText = formatCustomerPaymentStatus(
                                    method = currentOrder.paymentMethod,
                                    state = latestPayment?.state,
                                    formattedTotal = formatRupees(currentOrder.totalAmountPaisa)
                                )
                                val textColor = when {
                                    statusText.startsWith("Paid") -> Color(0xFF16A34A)
                                    statusText.contains("didn't go through", ignoreCase = true) -> Color(0xFFDC2626)
                                    else -> Color(0xFF475569)
                                }
                                Text(
                                    text = statusText,
                                    fontSize = 12.sp,
                                    color = textColor,
                                    fontWeight = if (statusText.startsWith("Paid")) FontWeight.Medium else FontWeight.Normal
                                )
                            }
                        }
                    }
                }

                // Section 3: Secure Handover Verification PIN Card
                // Handover PIN verification: prevents mistaken cylinder deliveries and ensures custody
                // transfers only when the authorized customer physically shares their 4-digit token with the rider.
                val showPinStatuses = setOf(
                    OrderStatus.ORD_DISP,
                    OrderStatus.ORD_RDYPU,
                    OrderStatus.ORD_ATHO,
                    OrderStatus.ORD_INSP,
                    OrderStatus.ORD_ADJ
                )
                if (currentOrder.status in showPinStatuses) {
                    item {
                        val isPickup = currentOrder.fulfilmentMode == FulfilmentMode.PICKUP
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                            modifier = Modifier.fillMaxWidth().testTag("doorstep_pin_card")
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    text = "Handover Code",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = BlueGasDark
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (isPickup) "Show this code at the counter." else "Share this 4-digit code with the rider at handover.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF1E40AF)
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    currentOrder.handoverPin.forEach { digit ->
                                        Surface(
                                            color = Color.White,
                                            shape = RoundedCornerShape(8.dp),
                                            border = androidx.compose.foundation.BorderStroke(1.dp, BlueGasPrimary),
                                            modifier = Modifier.size(40.dp)
                                        ) {
                                            Box(contentAlignment = Alignment.Center) {
                                                Text(
                                                    text = digit.toString(),
                                                    fontSize = 18.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = BlueGasPrimary
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // Pickup Orders: Depot Information & Arrival Notification Button
                // Depot pickup guide: displays operating hours and contact details for the customer's chosen
                // pickup node, enabling arrival notifications so depot staff can stage the cylinder for counter pickup.
                if (currentOrder.fulfilmentMode == FulfilmentMode.PICKUP) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth().testTag("pickup_depot_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Store, contentDescription = null, tint = BlueGasPrimary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(depotNode?.name ?: "Pickup Depot", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(depotNode?.address ?: "Hub address", fontSize = 12.sp, color = Color(0xFF475569))
                                Spacer(modifier = Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("Opening hours: ", fontSize = 11.sp, color = Color(0xFF64748B))
                                    DemoValuePill("Demo value · Q14")
                                }

                                if (currentOrder.status == OrderStatus.ORD_RDYPU) {
                                    Spacer(modifier = Modifier.height(12.dp))
                                    if (depotNotifiedLocal) {
                                        Surface(
                                            color = Color(0xFFDCFCE7),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(10.dp),
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.Center
                                            ) {
                                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(18.dp))
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Depot notified", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF166534))
                                            }
                                        }
                                    } else {
                                        Button(
                                            onClick = {
                                                val (ok, msg) = store.notifyPickupArrival(currentOrder.id)
                                                postMessage(msg, !ok)
                                                if (ok) depotNotifiedLocal = true
                                            },
                                            modifier = Modifier.fillMaxWidth().height(44.dp).testTag("im_at_depot_button"),
                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                                        ) {
                                            Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color.White)
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text("I'm at the depot", fontWeight = FontWeight.Bold, color = Color.White)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    // Delivery Orders: Live Tracking Map with Moving Rider Marker
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                            modifier = Modifier.fillMaxWidth().testTag("tracking_map_view")
                        ) {
                            Column {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(160.dp)
                                        .background(Color(0xFFE2E8F0))
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        val startX = size.width * 0.18f
                                        val startY = size.height * 0.75f
                                        val endX = size.width * 0.82f
                                        val endY = size.height * 0.25f

                                        drawLine(
                                            color = Color(0xFFCBD5E1),
                                            start = Offset(0f, size.height * 0.5f),
                                            end = Offset(size.width, size.height * 0.5f),
                                            strokeWidth = 32f
                                        )
                                        drawLine(
                                            color = Color(0xFF94A3B8),
                                            start = Offset(startX, startY),
                                            end = Offset(endX, endY),
                                            strokeWidth = 14f,
                                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(20f, 10f), 0f)
                                        )
                                    }

                                    // Hub Marker
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomStart)
                                            .padding(start = 16.dp, bottom = 16.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF1E293B),
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 4.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Store, contentDescription = null, tint = Color(0xFF93C5FD), modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text(depotNode?.name ?: "Depot Hub", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Animated rider pin: progress is simulated (advancing smoothly over ~60 s from dispatch timestamp).
                                    // Live rider location coordinates synchronize with the dedicated Rider application; upon reaching the customer door
                                    // the pin automatically parks at 88% along the route.
                                    val orderHistory = snapshot.orderStatusHistory.filter { it.orderId == currentOrder.id }
                                    val dispatchedTimestamp = orderHistory.find { it.toStatus == OrderStatus.ORD_DISP }?.timestamp ?: currentOrder.createdAt
                                    val elapsedSinceDispatch = (nowMillis - dispatchedTimestamp).coerceAtLeast(0L)

                                    val targetProgress = when (currentOrder.status) {
                                        OrderStatus.ORD_DISP -> 0.20f + 0.65f * min(1f, elapsedSinceDispatch / 60_000f)
                                        OrderStatus.ORD_ATHO, OrderStatus.ORD_INSP, OrderStatus.ORD_ADJ, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP -> 0.88f
                                        else -> 0.15f
                                    }
                                    val animatedProgress by animateFloatAsState(targetValue = targetProgress, animationSpec = tween(1200))

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(animatedProgress)
                                            .align(Alignment.CenterStart)
                                            .wrapContentWidth(Alignment.End)
                                            .padding(bottom = 10.dp)
                                    ) {
                                        Surface(
                                            color = BlueGasPrimary,
                                            shape = CircleShape,
                                            shadowElevation = 6.dp
                                        ) {
                                            Box(modifier = Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                                                Icon(
                                                    imageVector = Icons.Default.DeliveryDining,
                                                    contentDescription = "Rider Van",
                                                    tint = Color.White,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }

                                    // Doorstep Marker
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 16.dp, top = 16.dp)
                                    ) {
                                        Surface(
                                            color = Color(0xFF16A34A),
                                            shape = RoundedCornerShape(8.dp),
                                            shadowElevation = 4.dp
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(Icons.Default.Home, contentDescription = null, tint = Color.White, modifier = Modifier.size(12.dp))
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Text("Your Doorstep", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }

                                    // Delivery status badge on map
                                    val mapPillText = when (currentOrder.status) {
                                        OrderStatus.ORD_DISP -> "On the way"
                                        OrderStatus.ORD_ATHO -> "At your door"
                                        OrderStatus.ORD_COMP -> "Delivered"
                                        else -> "Preparing at ${depotNode?.name ?: "Depot"}"
                                    }
                                    Surface(
                                        color = Color(0xDD000000),
                                        shape = RoundedCornerShape(20.dp),
                                        modifier = Modifier
                                            .align(Alignment.BottomCenter)
                                            .padding(bottom = 8.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(6.dp)
                                                    .clip(CircleShape)
                                                    .background(Color(0xFF22C55E))
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                text = mapPillText,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = Color.White
                                            )
                                        }
                                    }
                                }

                                // Route details footer
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text("Fulfilment Route", fontSize = 10.sp, color = Color.Gray)
                                        Text("Delivery from ${depotNode?.name ?: "Depot"}", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Surface(
                                        color = Color(0xFFEFF6FF),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Live tracking (simulated for demo)",
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = BlueGasPrimary,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Assigned Delivery Rider Details Card
                    val showRiderFromDisp = setOf(
                        OrderStatus.ORD_DISP,
                        OrderStatus.ORD_ATHO,
                        OrderStatus.ORD_INSP,
                        OrderStatus.ORD_ADJ,
                        OrderStatus.ORD_HOC,
                        OrderStatus.ORD_COMP
                    )
                    if (assignedRider != null && currentOrder.status in showRiderFromDisp) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = Color.White),
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                                modifier = Modifier.fillMaxWidth().testTag("assigned_rider_card")
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(42.dp)
                                            .clip(CircleShape)
                                            .background(Color(0xFFDBEAFE)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = BlueGasPrimary,
                                            modifier = Modifier.size(24.dp)
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text(
                                                text = assignedRider.name,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 13.sp,
                                                color = Color(0xFF0F172A)
                                            )
                                            if (assignedRider.status == "ACTIVE" || assignedRider.onboardingStatus == "APPROVED") {
                                                Spacer(modifier = Modifier.width(4.dp))
                                                Icon(
                                                    imageVector = Icons.Default.Verified,
                                                    contentDescription = "Verified Rider",
                                                    tint = BlueGasPrimary,
                                                    modifier = Modifier.size(13.dp)
                                                )
                                            }
                                        }
                                        Text(
                                            text = "Vehicle: ${assignedRider.vehicleNumber}",
                                            fontSize = 11.sp,
                                            color = Color(0xFF475569)
                                        )
                                    }

                                    FilledTonalIconButton(
                                        onClick = {
                                            val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:${assignedRider.phone}"))
                                            context.startActivity(dialIntent)
                                        },
                                        modifier = Modifier.size(38.dp).testTag("tracking_call_rider_button")
                                    ) {
                                        Icon(Icons.Default.Phone, contentDescription = "Call Rider", tint = BlueGasPrimary)
                                    }
                                }
                            }
                        }
                    }
                }

                // Allocated Cylinder Serial & Hardware Card
                // Allocated cylinder hardware badge: displays the physical tracked serial number once assigned
                // by the fulfillment node, ensuring transparency and cylinder authenticity prior to handover.
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().testTag("your_cylinder_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Your cylinder", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF065F46))
                            }
                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Cylinder ID", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        text = currentOrder.allocatedSerial ?: "Assigned while your order is prepared",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        fontFamily = if (currentOrder.allocatedSerial != null) FontFamily.Monospace else FontFamily.Default,
                                        color = Color(0xFF0F172A)
                                    )
                                }

                                if (currentOrder.fulfilmentMode == FulfilmentMode.DELIVERY) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text("Installation", fontSize = 10.sp, color = Color.Gray)
                                        val installText = when (currentOrder.installationResult) {
                                            InstallationResult.INSTALLED -> "Installed by rider"
                                            InstallationResult.NOT_INSTALLED -> "Not installed — ${currentOrder.installationNotDoneReason ?: "Declined"}"
                                            InstallationResult.NOT_APPLICABLE -> "Not applicable"
                                            null -> "At handover"
                                        }
                                        Text(
                                            text = installText,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 11.sp,
                                            color = when (currentOrder.installationResult) {
                                                InstallationResult.INSTALLED -> Color(0xFF16A34A)
                                                InstallationResult.NOT_INSTALLED -> Color(0xFFDC2626)
                                                else -> Color(0xFF475569)
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Stepper displaying order lifecycle progression
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier.fillMaxWidth().testTag("canonical_lifecycle_stepper")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Order progress",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BlueGasDark
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val orderHistory = snapshot.orderStatusHistory.filter { it.orderId == currentOrder.id }
                            val isPickup = currentOrder.fulfilmentMode == FulfilmentMode.PICKUP

                            val standardSteps = listOf(
                                StepperStep(OrderStatus.ORD_CONF, "Confirmed"),
                                StepperStep(OrderStatus.ORD_ACPT, "Partner assigned"),
                                StepperStep(OrderStatus.ORD_PREP, "Being prepared"),
                                StepperStep(
                                    if (isPickup) OrderStatus.ORD_RDYPU else OrderStatus.ORD_DISP,
                                    if (isPickup) "Ready to collect" else "On the way"
                                ),
                                StepperStep(OrderStatus.ORD_ATHO, if (isPickup) "At the counter" else "Rider arrived"),
                                StepperStep(OrderStatus.ORD_COMP, if (isPickup) "Collected" else "Delivered")
                            )

                            val isCompleted = currentOrder.status == OrderStatus.ORD_COMP
                            val isExceptionOrCancelled = currentOrder.status in setOf(
                                OrderStatus.ORD_EXC, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO, OrderStatus.ORD_SUPS
                            )

                            // Find highest reached step in history
                            val reachedTargets = orderHistory.map { it.toStatus }.toSet()

                            standardSteps.forEachIndexed { idx, step ->
                                val isDone = if (isCompleted) {
                                    true
                                } else if (isExceptionOrCancelled) {
                                    reachedTargets.contains(step.status) || isStepCompleted(currentOrder.status, step.status)
                                } else {
                                    isStepCompleted(currentOrder.status, step.status)
                                }
                                val isCurrent = !isCompleted && !isExceptionOrCancelled && isStepCurrent(currentOrder.status, step.status)

                                val histEntry = orderHistory.find { it.toStatus == step.status }
                                val stepTimeText = histEntry?.let { " • ${formatTimestamp(it.timestamp)}" } ?: ""

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(
                                                    when {
                                                        isDone -> Color(0xFF16A34A)
                                                        isCurrent -> BlueGasPrimary
                                                        else -> Color(0xFFE2E8F0)
                                                    }
                                                ),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            if (isDone) {
                                                Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                            } else {
                                                Text(
                                                    text = (idx + 1).toString(),
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold,
                                                    color = if (isCurrent) Color.White else Color(0xFF94A3B8)
                                                )
                                            }
                                        }
                                        if (idx < standardSteps.size - 1 || isExceptionOrCancelled) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(24.dp)
                                                    .background(if (isDone) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.padding(bottom = 10.dp)) {
                                        Text(
                                            text = step.label,
                                            fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = if (isCurrent) BlueGasPrimary else if (isDone) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                        )
                                        if (stepTimeText.isNotEmpty()) {
                                            Text(
                                                text = stepTimeText.removePrefix(" • "),
                                                fontSize = 10.sp,
                                                color = Color(0xFF64748B)
                                            )
                                        }
                                    }
                                }
                            }

                            // Terminal row for exception/cancelled/failed/superseded
                            if (isExceptionOrCancelled) {
                                val isExc = currentOrder.status == OrderStatus.ORD_EXC
                                val terminalHist = orderHistory.find { it.toStatus == currentOrder.status }
                                val terminalTime = terminalHist?.let { " • ${formatTimestamp(it.timestamp)}" } ?: ""
                                val terminalColor = if (isExc) Color(0xFFDC2626) else Color(0xFF64748B)
                                val terminalTitle = when (currentOrder.status) {
                                    OrderStatus.ORD_EXC -> "Fulfilment Exception"
                                    OrderStatus.ORD_CANC -> "Order Cancelled"
                                    OrderStatus.ORD_FCLO -> "Order Closed"
                                    OrderStatus.ORD_SUPS -> "Order Superseded"
                                    else -> currentOrder.status.displayName
                                }
                                val terminalDesc = when (currentOrder.status) {
                                    OrderStatus.ORD_EXC -> formatCustomerExceptionMessage(currentOrder.exceptionReason)
                                    OrderStatus.ORD_CANC -> currentOrder.cancelReason ?: "This order was cancelled."
                                    else -> "Order terminated"
                                }

                                Row(modifier = Modifier.fillMaxWidth()) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Box(
                                            modifier = Modifier
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(terminalColor),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = if (isExc) Icons.Default.Warning else Icons.Default.Close,
                                                contentDescription = null,
                                                tint = Color.White,
                                                modifier = Modifier.size(14.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.width(10.dp))
                                    Column {
                                        Text(
                                            text = "$terminalTitle$terminalTime",
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp,
                                            color = terminalColor
                                        )
                                        Text(
                                            text = terminalDesc,
                                            fontSize = 10.sp,
                                            color = terminalColor
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Completed Order Actions: View Receipt and Rate Experience
                // Post-fulfillment actions: provides access to the formal itemized electronic receipt and
                // allows submitting delivery satisfaction ratings to improve logistics service quality.
                if (currentOrder.status == OrderStatus.ORD_COMP) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                OutlinedButton(
                                    onClick = { showReceiptDialog = true },
                                    modifier = Modifier.weight(1f).testTag("tracking_view_receipt_button")
                                ) {
                                    Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("View receipt", fontSize = 12.sp)
                                }

                                if (currentOrder.ratingStars != null) {
                                    Surface(
                                        color = Color(0xFFFEF3C7),
                                        shape = RoundedCornerShape(8.dp),
                                        modifier = Modifier.weight(1f).height(40.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxSize(),
                                            horizontalArrangement = Arrangement.Center,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Default.Star, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Rated ${currentOrder.ratingStars}/5", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFF92400E))
                                        }
                                    }
                                } else {
                                    Button(
                                        onClick = { showRateDialog = true },
                                        colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                                        modifier = Modifier.weight(1f).testTag("tracking_rate_order_button")
                                    ) {
                                        Icon(Icons.Default.Star, contentDescription = null, modifier = Modifier.size(14.dp))
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Rate delivery", fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Order Cancellation Call-to-Action for eligible pre-dispatch statuses
                val isCancellableInTracking = currentOrder.status in setOf(
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

                if (isCancellableInTracking) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFCA5A5)),
                            modifier = Modifier.fillMaxWidth().testTag("cancel_order_tracking_card")
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp).fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Need to cancel this order?",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                    Text(
                                        text = "You can cancel until your order is dispatched.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    modifier = Modifier.height(34.dp).testTag("tracking_cancel_button")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel Order", fontSize = 11.sp, color = Color(0xFFDC2626))
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

    // Online Payment Dialog
    if (showPaymentGatewayDialog && currentOrder != null) {
        DemoPaymentDialog(
            orderId = currentOrder.id,
            payableLabel = formatRupees(currentOrder.totalAmountPaisa),
            store = store,
            onResult = postMessage,
            onDismiss = { showPaymentGatewayDialog = false }
        )
    }

    // Itemized Electronic Order Receipt Dialog
    if (showReceiptDialog && currentOrder != null) {
        ReceiptDialog(
            order = currentOrder,
            snapshot = snapshot,
            onDismiss = { showReceiptDialog = false }
        )
    }

    // Post-Delivery Feedback and Rating Dialog
    if (showRateDialog && currentOrder != null) {
        RateOrderDialog(
            orderId = currentOrder.id,
            store = store,
            onResult = postMessage,
            onDismiss = { showRateDialog = false }
        )
    }

    // Customer Order Cancellation Confirmation Dialog
    if (showCancelDialog && currentOrder != null) {
        val cancelReasons = listOf(
            "Changed my mind",
            "Ordered wrong cylinder size",
            "Delivery taking too long",
            "Need delivery to different address",
            "Emergency / other reason"
        )

        AlertDialog(
            onDismissRequest = { showCancelDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Cancel, contentDescription = null, tint = Color(0xFFDC2626))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Cancel Order ${currentOrder.id}", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                    Text(
                        "Your reserved cylinder will be released. If you already paid online, the refund is handled by our Finance team.",
                        fontSize = 11.sp,
                        color = Color(0xFF64748B),
                        lineHeight = 15.sp
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text("Reason for cancellation:", fontWeight = FontWeight.SemiBold, fontSize = 11.sp)
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
                        val (cancelSuccess, cancelMsg) = store.cancelOrder(currentOrder.id, cancelReason)
                        postMessage(cancelMsg, !cancelSuccess)
                        showCancelDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.testTag("confirm_cancel_tracking_button")
                ) {
                    Text("Confirm Cancellation")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCancelDialog = false }) {
                    Text("Keep Order")
                }
            }
        )
    }
}

/**
 * Visual styling and text payload for the status hero banner card.
 *
 * Encapsulates background tint, accent highlight colors, representative iconography,
 * and customer-friendly plain-language status title and subtitle.
 */
private data class HeroBannerConfig(
    val tint: Color,
    val accent: Color,
    val icon: ImageVector,
    val title: String,
    val subtitle: String
)

/**
 * Single progression milestone definition for the visual lifecycle stepper.
 *
 * @property status Canonical order status represented by this stepper milestone.
 * @property label Customer-facing text label describing the stage.
 */
private data class StepperStep(
    val status: OrderStatus,
    val label: String
)

/**
 * Determines whether a target lifecycle milestone has already been completed.
 *
 * Implements linear progression ranking with domain-specific milestone equivalence:
 * - Completed orders (ORD_COMP) mark every historical milestone done because the handover is fully satisfied.
 * - ORD_CONF and ORD_RSVD share rank 3 because inventory reservation occurs immediately and synchronously upon confirmation.
 * - ORD_DISP and ORD_RDYPU share rank 7 as parallel fulfillment transit milestones for delivery and depot pickup respectively.
 * - Inspection and adjustment sub-states (ORD_INSP, ORD_ADJ) share rank 9 prior to handover completion.
 *
 * @param current Current lifecycle status of the order.
 * @param target Milestone status to evaluate.
 * @return True if the current status strictly outranks the target status.
 */
private fun isStepCompleted(current: OrderStatus, target: OrderStatus): Boolean {
    if (current == OrderStatus.ORD_COMP) return true
    val rank = mapOf(
        OrderStatus.ORD_DRAFT to 0,
        OrderStatus.ORD_VALPEND to 1,
        OrderStatus.ORD_PAYPEND to 2,
        OrderStatus.ORD_CONF to 3,
        OrderStatus.ORD_RSVD to 3,
        OrderStatus.ORD_ASGN to 4,
        OrderStatus.ORD_ACPT to 5,
        OrderStatus.ORD_PREP to 6,
        OrderStatus.ORD_DISP to 7,
        OrderStatus.ORD_RDYPU to 7,
        OrderStatus.ORD_ATHO to 8,
        OrderStatus.ORD_INSP to 9,
        OrderStatus.ORD_ADJ to 9,
        OrderStatus.ORD_HOC to 10,
        OrderStatus.ORD_COMP to 11
    )
    val curRank = rank[current] ?: 0
    val tarRank = rank[target] ?: 0
    return curRank > tarRank
}

/**
 * Evaluates whether an order is actively progressing within a given stepper milestone stage.
 *
 * Groups closely related operational substates under their parent customer milestone:
 * - Confirmation milestone (ORD_CONF) encompasses validation, payment pending, confirmation, and reservation.
 * - Partner assignment milestone (ORD_ACPT) groups distributor assignment and acceptance.
 * - In-transit milestone encompasses road dispatch (ORD_DISP) or ready for customer pickup (ORD_RDYPU).
 * - Doorstep arrival milestone encompasses doorstep arrival, inspection, and price adjustments.
 * - Completed milestone encompasses final handover completion and terminal closure.
 *
 * @param current Current lifecycle status of the order.
 * @param target Stepper milestone being rendered.
 * @return True if the order is currently inside this milestone phase.
 */
private fun isStepCurrent(current: OrderStatus, target: OrderStatus): Boolean {
    return when (target) {
        OrderStatus.ORD_CONF -> current == OrderStatus.ORD_CONF || current == OrderStatus.ORD_RSVD || current == OrderStatus.ORD_PAYPEND || current == OrderStatus.ORD_VALPEND
        OrderStatus.ORD_ACPT -> current == OrderStatus.ORD_ASGN || current == OrderStatus.ORD_ACPT
        OrderStatus.ORD_PREP -> current == OrderStatus.ORD_PREP
        OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> current == OrderStatus.ORD_DISP || current == OrderStatus.ORD_RDYPU
        OrderStatus.ORD_ATHO -> current == OrderStatus.ORD_ATHO || current == OrderStatus.ORD_INSP || current == OrderStatus.ORD_ADJ
        OrderStatus.ORD_COMP -> current == OrderStatus.ORD_HOC || current == OrderStatus.ORD_COMP
        else -> false
    }
}
