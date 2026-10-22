package com.example.bluegas.ui.customer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TrackingScreen(
    store: BlueGasStore,
    initialOrderId: String?,
    onSelectOrderToTrack: (String) -> Unit,
    onBackToOrders: () -> Unit,
    modifier: Modifier = Modifier
) {
    val snapshot by store.appState.collectAsStateWithLifecycle()
    val activeCustomer = snapshot.activeCustomer ?: return
    val customerOrders = snapshot.orders.filter { it.customerId == activeCustomer.id }.sortedByDescending { it.createdAt }

    var selectedOrderId by remember(initialOrderId, customerOrders.size) {
        mutableStateOf(
            if (initialOrderId != null && customerOrders.any { it.id == initialOrderId }) initialOrderId
            else customerOrders.firstOrNull { !it.status.isTerminal }?.id ?: customerOrders.firstOrNull()?.id
        )
    }

    val currentOrder = customerOrders.find { it.id == selectedOrderId }
    val assignedRider = snapshot.riders.find { it.id == currentOrder?.assignedRiderId }
    val assignedNode = snapshot.nodes.find { it.id == assignedRider?.nodeId }
    val deliveryInfo = snapshot.deliveries.find { it.orderId == currentOrder?.id }

    var showCallRiderDialog by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }
    var cancelReason by remember { mutableStateOf("Changed my mind") }
    var notificationBannerMessage by remember { mutableStateOf<String?>(null) }

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
                        text = "Real-time dispatch & doorstep telemetry",
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

        Spacer(modifier = Modifier.height(10.dp))

        // Notification / action feedback banner
        AnimatedVisibility(
            visible = notificationBannerMessage != null,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Surface(
                color = Color(0xFFFEF3C7),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFFB45309),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = notificationBannerMessage ?: "",
                        fontSize = 11.sp,
                        color = Color(0xFF78350F),
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = { notificationBannerMessage = null },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = Color(0xFF78350F),
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (currentOrder == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
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
                // SECTION 1: Status Hero Banner
                item {
                    val (bannerBg, bannerIcon, bannerTitle, bannerSubtitle) = when (currentOrder.status) {
                        OrderStatus.ORD_DRAFT, OrderStatus.ORD_VALPEND -> Quadruple(
                            Color(0xFFEFF6FF), Icons.Default.PendingActions, "Validating Order & Zone", "Checking safety compliance and distributor pilot coverage"
                        )
                        OrderStatus.ORD_PAYPEND -> Quadruple(
                            Color(0xFFFFFBEB), Icons.Default.Timer, "Awaiting Payment Authorization", "Stock held for 15 minutes. Authorize online or select COD"
                        )
                        OrderStatus.ORD_CONF, OrderStatus.ORD_RSVD -> Quadruple(
                            Color(0xFFEFF6FF), Icons.Default.CheckCircle, "Order Confirmed & Stock Reserved", "Distributor node is allocating an inspection-passed cylinder"
                        )
                        OrderStatus.ORD_ASGN -> Quadruple(
                            Color(0xFFF0FDF4), Icons.Default.PersonSearch, "Rider Assigned", "Awaiting rider shift acceptance & pickup confirmation"
                        )
                        OrderStatus.ORD_ACPT -> Quadruple(
                            Color(0xFFEFF6FF), Icons.Default.ThumbUp, "Rider Accepted Order", "Heading to distributor hub for vehicle loading"
                        )
                        OrderStatus.ORD_PREP -> Quadruple(
                            Color(0xFFFEF3C7), Icons.Default.Build, "Preparing your order", "The distributor is getting your cylinder ready."
                        )
                        OrderStatus.ORD_DISP -> Quadruple(
                            Color(0xFF0284C7), Icons.Default.DeliveryDining, "Dispatched & On the Way!", "Rider is en route to your delivery address"
                        )
                        OrderStatus.ORD_RDYPU -> Quadruple(
                            Color(0xFF047857), Icons.Default.Store, "Ready for Self Pickup", "Your cylinder package is ready at the distributor hub"
                        )
                        OrderStatus.ORD_ATHO -> Quadruple(
                            Color(0xFF16A34A), Icons.Default.PinDrop, "Rider Arrived at Doorstep!", "Conducting bubble leak test & non-technical installation"
                        )
                        OrderStatus.ORD_INSP, OrderStatus.ORD_ADJ -> Quadruple(
                            Color(0xFFD97706), Icons.AutoMirrored.Filled.FactCheck, "Safety Inspection & Handover", "Leak test passed. Verifying digital signature & payment"
                        )
                        OrderStatus.ORD_HOC, OrderStatus.ORD_COMP -> Quadruple(
                            Color(0xFF15803D), Icons.Default.Verified, "Handover Complete & Delivered", "Safe LPG cylinder delivered and verified with official POD"
                        )
                        OrderStatus.ORD_EXC -> Quadruple(
                            Color(0xFFDC2626), Icons.Default.Warning, "Fulfilment Exception", currentOrder.exceptionReason?.description ?: "Under Ops review"
                        )
                        OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO -> Quadruple(
                            Color(0xFF64748B), Icons.Default.Cancel, "Order Cancelled / Closed", "This order was cancelled or failed closed"
                        )
                        OrderStatus.ORD_SUPS -> Quadruple(
                            Color(0xFF7C3AED), Icons.Default.ChangeCircle, "Order Superseded", "Replaced by updated order request"
                        )
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = bannerBg),
                        shape = RoundedCornerShape(12.dp),
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
                                    .background(Color(0x33000000)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = bannerIcon,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = bannerTitle,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    color = Color.White
                                )
                                Text(
                                    text = bannerSubtitle,
                                    fontSize = 11.sp,
                                    color = Color(0xFFF1F5F9),
                                    lineHeight = 15.sp
                                )
                            }
                        }
                    }
                }

                // Show "I've Arrived" button for pickup orders at ORD_RDYPU
                if (currentOrder.fulfilmentMode == FulfilmentMode.PICKUP && currentOrder.status == OrderStatus.ORD_RDYPU) {
                    item {
                        Button(
                            onClick = {
                                val (_, msg) = store.notifyPickupArrival(currentOrder.id)
                                notificationBannerMessage = msg
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                                .height(48.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857))
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, tint = Color.White)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("I've Arrived at Hub", fontWeight = FontWeight.Bold, color = Color.White)
                        }
                    }
                }

                // If Order is Cancelled, show dedicated cancellation card
                if (currentOrder.status == OrderStatus.ORD_CANC) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFEF2F2)),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFECACA)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancelled_order_notice_card")
                        ) {
                            Column(modifier = Modifier.padding(14.dp)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.Cancel,
                                        contentDescription = null,
                                        tint = Color(0xFFDC2626),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Order Cancelled",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp,
                                        color = Color(0xFF991B1B)
                                    )
                                }
                                Spacer(modifier = Modifier.height(6.dp))
                                Text(
                                    text = "This order was cancelled. Reserved cylinders, distributor allocations, and temporary stock holds have been returned to inventory. Any online payments have been refunded.",
                                    fontSize = 11.sp,
                                    color = Color(0xFF7F1D1D),
                                    lineHeight = 15.sp
                                )
                                Spacer(modifier = Modifier.height(10.dp))
                                Button(
                                    onClick = onBackToOrders,
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(36.dp)
                                        .testTag("back_to_orders_from_cancelled"),
                                    contentPadding = PaddingValues(vertical = 4.dp)
                                ) {
                                    Text("Back to Orders List", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                // Cancel Order CTA if order is in a cancellable status
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
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("cancel_order_tracking_card")
                        ) {
                            Row(
                                modifier = Modifier
                                    .padding(12.dp)
                                    .fillMaxWidth(),
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
                                        text = "Orders can be cancelled before dispatch without penalty.",
                                        fontSize = 10.sp,
                                        color = Color(0xFF7F1D1D)
                                    )
                                }
                                OutlinedButton(
                                    onClick = { showCancelDialog = true },
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                                    modifier = Modifier
                                        .height(34.dp)
                                        .testTag("tracking_cancel_button")
                                ) {
                                    Icon(Icons.Default.Cancel, contentDescription = null, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Cancel Order", fontSize = 11.sp, color = Color(0xFFDC2626))
                                }
                            }
                        }
                    }
                }

                // SECTION 2: Interactive Simulated Vector Map
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("tracking_map_view")
                    ) {
                        Column {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(160.dp)
                                    .background(Color(0xFFE2E8F0))
                            ) {
                                // Stylized canvas route
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    val startX = size.width * 0.18f
                                    val startY = size.height * 0.75f
                                    val endX = size.width * 0.82f
                                    val endY = size.height * 0.25f

                                    // Road grid
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
                                            Text(assignedNode?.name ?: "Distributor Hub", fontSize = 9.sp, color = Color.White, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }

                                // Rider Moving Pin
                                val riderProgress = when (currentOrder.status) {
                                    OrderStatus.ORD_DISP -> 0.55f
                                    OrderStatus.ORD_ATHO, OrderStatus.ORD_INSP, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP -> 0.88f
                                    OrderStatus.ORD_PREP, OrderStatus.ORD_ACPT -> 0.25f
                                    else -> 0.15f
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth(riderProgress)
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

                                // Customer Home Marker
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

                                // Live ETA Tag
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
                                            text = when (currentOrder.status) {
                                                OrderStatus.ORD_DISP -> "ETA: 18 - 25 minutes"
                                                OrderStatus.ORD_ATHO -> "Rider is outside your gate!"
                                                OrderStatus.ORD_COMP -> "Handover Completed"
                                                else -> "Dispatching from ${assignedNode?.name ?: "Lahore Central"}"
                                            },
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                    }
                                }
                            }

                            // Route telemetry details
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Fulfilment Route", fontSize = 10.sp, color = Color.Gray)
                                    Text("Direct Node-to-Consumer Van", fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                }
                                Surface(
                                    color = Color(0xFFEFF6FF),
                                    shape = RoundedCornerShape(4.dp)
                                ) {
                                    Text(
                                        text = "GPS Trace Active",
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

                // SECTION 3: Assigned Rider Card
                if (assignedRider != null) {
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            shape = RoundedCornerShape(12.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("assigned_rider_card")
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
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Default.Verified,
                                            contentDescription = "Verified Rider",
                                            tint = BlueGasPrimary,
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                    Text(
                                        text = "Vehicle: ${assignedRider.vehicleNumber} • BlueGas Van",
                                        fontSize = 11.sp,
                                        color = Color(0xFF475569)
                                    )
                                }

                                FilledTonalIconButton(
                                    onClick = { showCallRiderDialog = true },
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Icon(Icons.Default.Phone, contentDescription = "Call Rider", tint = BlueGasPrimary)
                                }
                            }
                        }
                    }
                }

                // SECTION 4: Cylinder Safety & Traceability Card
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8FAFC)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("cylinder_safety_traceability_card")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.QrCodeScanner,
                                        contentDescription = null,
                                        tint = Color(0xFF047857),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Cylinder Safety & Traceability",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp,
                                        color = Color(0xFF065F46)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text("Bound Cylinder Serial:", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        text = currentOrder.allocatedSerial ?: "Allocating Serial...",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = Color(0xFF0F172A)
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text("Leak Inspection:", fontSize = 10.sp, color = Color.Gray)
                                    Text(
                                        text = if (deliveryInfo?.nonTechnicalInstallationPassed == true) "PASSED (Bubble Test)" else "Scheduled at Handover",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = if (deliveryInfo?.nonTechnicalInstallationPassed == true) Color(0xFF16A34A) else Color(0xFFD97706)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))
                            HorizontalDivider()
                            Spacer(modifier = Modifier.height(6.dp))

                            val productLine = currentOrder.items.firstOrNull { it.lineType == "GAS" } ?: currentOrder.items.firstOrNull()
                            if (productLine != null) {
                                Text(
                                    text = "Order Item: ${productLine.quantity}x ${productLine.productName}",
                                    fontSize = 11.sp,
                                    color = Color(0xFF334155)
                                )
                            }
                        }
                    }
                }

                // SECTION 5: Customer Doorstep Verification PIN
                item {
                    val handoverPin = remember(currentOrder.id) {
                        val num = (currentOrder.id.hashCode() and 0x7FFFFFFF) % 9000 + 1000
                        num.toString()
                    }

                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFEFF6FF)),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("doorstep_pin_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Doorstep Handover Verification PIN",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = BlueGasDark
                            )
                            Text(
                                text = "Share this 4-digit code with the rider upon arrival to authorize cylinder release.",
                                fontSize = 10.sp,
                                color = Color(0xFF1E40AF),
                                lineHeight = 14.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                handoverPin.forEach { digit ->
                                    Surface(
                                        color = Color.White,
                                        shape = RoundedCornerShape(8.dp),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, BlueGasPrimary),
                                        modifier = Modifier.size(38.dp)
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

                // SECTION 6: Canonical Lifecycle Stepper
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color.White),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE2E8F0)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("canonical_lifecycle_stepper")
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Order Lifecycle Progression",
                                fontWeight = FontWeight.Bold,
                                fontSize = 13.sp,
                                color = BlueGasDark
                            )
                            Spacer(modifier = Modifier.height(10.dp))

                            val orderHistory = snapshot.orderStatusHistory.filter { it.orderId == currentOrder.id }

                            val steps = if (currentOrder.fulfilmentMode == FulfilmentMode.PICKUP) {
                                listOf(
                                    Triple("1. Order Placed & Confirmed", OrderStatus.ORD_CONF, "Validation passed, stock reserved"),
                                    Triple("2. Distributor Assigned", OrderStatus.ORD_ACPT, "Pickup hub confirmed"),
                                    Triple("3. Cylinder Prepared", OrderStatus.ORD_PREP, "Inspected & ready for collection"),
                                    Triple("4. Ready for Pickup", OrderStatus.ORD_RDYPU, "Awaiting your arrival at hub"),
                                    Triple("5. Customer at Hub", OrderStatus.ORD_ATHO, "Handover & safety check"),
                                    Triple("6. Pickup Complete", OrderStatus.ORD_COMP, "Official POD & receipt generated")
                                )
                            } else {
                                listOf(
                                    Triple("1. Order Placed & Confirmed", OrderStatus.ORD_CONF, "Validation passed, stock reserved"),
                                    Triple("2. Rider Assigned & Accepted", OrderStatus.ORD_ACPT, "Rider accepted, heading to hub"),
                                    Triple("3. Cylinder Serial Bound", OrderStatus.ORD_PREP, "Inspected & allocated to vehicle"),
                                    Triple("4. Dispatched & In Transit", OrderStatus.ORD_DISP, "En route to delivery premises"),
                                    Triple("5. Arrived at Handover", OrderStatus.ORD_ATHO, "Leak test & regulator check"),
                                    Triple("6. Delivered & Handover Complete", OrderStatus.ORD_COMP, "Official POD & receipt generated")
                                )
                            }

                            steps.forEachIndexed { idx, (label, statusThreshold, defaultDesc) ->
                                val isDone = isStepCompleted(currentOrder.status, statusThreshold)
                                val isCurrent = isStepCurrent(currentOrder.status, statusThreshold)

                                // Pull real timestamp if this step has been reached
                                val historyEntry = orderHistory.find { it.toStatus == statusThreshold }
                                val displayDesc = if (historyEntry != null) {
                                    "${defaultDesc} • ${formatTimestamp(historyEntry.timestamp)}"
                                } else {
                                    defaultDesc
                                }

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
                                        if (idx < steps.size - 1) {
                                            Box(
                                                modifier = Modifier
                                                    .width(2.dp)
                                                    .height(26.dp)
                                                    .background(if (isDone) Color(0xFF16A34A) else Color(0xFFE2E8F0))
                                            )
                                        }
                                    }

                                    Spacer(modifier = Modifier.width(10.dp))

                                    Column(modifier = Modifier.padding(bottom = if (idx < steps.size - 1) 12.dp else 0.dp)) {
                                        Text(
                                            text = label,
                                            fontWeight = if (isCurrent || isDone) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp,
                                            color = if (isCurrent) BlueGasPrimary else if (isDone) Color(0xFF0F172A) else Color(0xFF94A3B8)
                                        )
                                        Text(
                                            text = displayDesc,
                                            fontSize = 10.sp,
                                            color = if (isCurrent || isDone) Color(0xFF475569) else Color(0xFF94A3B8)
                                        )
                                    }
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

    // Call Rider Dialog simulation
    if (showCallRiderDialog && assignedRider != null) {
        AlertDialog(
            onDismissRequest = { showCallRiderDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Phone, contentDescription = null, tint = BlueGasPrimary)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Call Delivery Rider", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                }
            },
            text = {
                Column {
                    Text("Rider: ${assignedRider.name}", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                    Text("Phone: ${assignedRider.phone}", fontSize = 12.sp, color = BlueGasPrimary, fontFamily = FontFamily.Monospace)
                    Text("Vehicle: ${assignedRider.vehicleNumber}", fontSize = 11.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Connecting directly to rider van hands-free system...",
                        fontSize = 11.sp,
                        color = Color(0xFF475569)
                    )
                }
            },
            confirmButton = {
                Button(onClick = { showCallRiderDialog = false }) {
                    Text("Simulate Call Connected")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCallRiderDialog = false }) { Text("Dismiss") }
            }
        )
    }

    // Cancel Order Dialog simulation
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
                Column {
                    Text(
                        "Are you sure you want to cancel this order? Active stock holds and allocated cylinders will be returned to inventory. Any online payments will be refunded.",
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
                        if (cancelSuccess) {
                            notificationBannerMessage = cancelMsg ?: "Order cancelled successfully"
                        } else {
                            notificationBannerMessage = cancelMsg ?: "Unable to cancel order"
                        }
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

private fun isStepCompleted(current: OrderStatus, target: OrderStatus): Boolean {
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

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)
