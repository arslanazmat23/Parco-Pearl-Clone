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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.model.*
import com.example.bluegas.ui.components.CanonicalStatusChip
import com.example.ui.theme.BlueGasDark
import com.example.ui.theme.BlueGasPrimary

@Composable
fun RiderActiveOrdersScreen(
    store: BlueGasStore,
    onSelectSerial: (String, Boolean) -> Unit, // orderId, shouldStartPreparingFirst
    onStartDeliveryOrPickup: (String) -> Unit,
    onArriveAtHandover: (String) -> Unit,
    onCompletePod: (String) -> Unit,
    onReportIssue: (String) -> Unit,
    onActionMessage: (String, Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRider = store.getActiveRider() ?: store.riders.first()

    // Filter orders currently active for this rider
    val activeOrders = store.orders.filter {
        it.assignedRiderId == activeRider.id &&
                it.status in listOf(
            OrderStatus.ORD_ACPT,
            OrderStatus.ORD_PREP,
            OrderStatus.ORD_DISP,
            OrderStatus.ORD_RDYPU,
            OrderStatus.ORD_ATHO,
            OrderStatus.ORD_HOC
        )
    }.sortedWith(compareByDescending { ord ->
        when (ord.status) {
            OrderStatus.ORD_ATHO -> 4
            OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> 3
            OrderStatus.ORD_PREP -> 2
            OrderStatus.ORD_ACPT -> 1
            else -> 0
        }
    })

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("rider_active_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Active Deliveries",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BlueGasDark
                )
                Text(
                    text = "Sorted by field urgency (Doorstep > Transit > Prep)",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Surface(
                color = Color(0xFFDBEAFE),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${activeOrders.size} In-Flight",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = BlueGasPrimary,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (activeOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.DirectionsBike,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No active orders in-flight",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Accept assigned tasks from the Dashboard tab to begin preparation and delivery.",
                        fontSize = 11.sp,
                        color = Color(0xFF94A3B8),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 24.dp)
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(activeOrders, key = { it.id }) { order ->
                    ActiveOrderCard(
                        order = order,
                        store = store,
                        onSelectSerial = { shouldStartPreparing ->
                            onSelectSerial(order.id, shouldStartPreparing)
                        },
                        onStartDeliveryOrPickup = { onStartDeliveryOrPickup(order.id) },
                        onArriveAtHandover = { onArriveAtHandover(order.id) },
                        onCompletePod = { onCompletePod(order.id) },
                        onReportIssue = { onReportIssue(order.id) }
                    )
                }
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}

@Composable
private fun ActiveOrderCard(
    order: Order,
    store: BlueGasStore,
    onSelectSerial: (Boolean) -> Unit,
    onStartDeliveryOrPickup: () -> Unit,
    onArriveAtHandover: () -> Unit,
    onCompletePod: () -> Unit,
    onReportIssue: () -> Unit
) {
    val customer = store.customers.find { it.id == order.customerId }
    val address = store.addresses.find { it.id == order.addressId }

    // Card border color matches urgency
    val borderColor = when (order.status) {
        OrderStatus.ORD_ATHO -> Color(0xFF16A34A) // Green - at door!
        OrderStatus.ORD_DISP, OrderStatus.ORD_RDYPU -> BlueGasPrimary // Blue - en route
        OrderStatus.ORD_PREP -> Color(0xFFD97706) // Amber - preparing
        OrderStatus.ORD_ACPT -> Color(0xFF3B82F6) // Light blue - accepted
        else -> Color(0xFFE2E8F0)
    }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.5.dp, borderColor),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("active_order_card_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: Order ID + Status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = order.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Surface(
                        color = Color(0xFFF1F5F9),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "🚴 Delivery" else "🏪 Pickup",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF475569),
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                        )
                    }
                }

                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer Name & Phone with quick call trigger
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = customer?.name ?: "Customer",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        color = Color(0xFF1E293B)
                    )
                    Text(
                        text = "Phone: ${customer?.phone ?: ""}",
                        fontSize = 11.sp,
                        color = BlueGasPrimary,
                        fontFamily = FontFamily.Monospace
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

            // Delivery Address
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Place,
                    contentDescription = null,
                    tint = Color(0xFF64748B),
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${address?.label ?: "Address"}: ${address?.area ?: ""}, ${address?.city ?: ""}",
                    fontSize = 11.sp,
                    color = Color(0xFF475569),
                    maxLines = 1
                )
            }

            // Products list
            Spacer(modifier = Modifier.height(6.dp))
            order.items.forEach { line ->
                Text(
                    text = "• ${line.quantity}x ${line.productName}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF334155)
                )
            }

            // Allocated Serial Badge
            Spacer(modifier = Modifier.height(6.dp))
            if (order.allocatedSerial != null) {
                Surface(
                    color = Color(0xFFECFDF5),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.QrCode, contentDescription = null, tint = Color(0xFF047857), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "Bound Cylinder: ${order.allocatedSerial}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = FontFamily.Monospace,
                            color = Color(0xFF065F46)
                        )
                    }
                }
            } else {
                Surface(
                    color = Color(0xFFFFFBEB),
                    shape = RoundedCornerShape(6.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFFDE68A)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color(0xFFD97706), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "No Cylinder Serial Bound Yet (Required for dispatch)",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF92400E)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Action Buttons based on status
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                when (order.status) {
                    OrderStatus.ORD_ACPT -> {
                        Button(
                            onClick = { onSelectSerial(true) }, // true: also starts preparing
                            colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("prep_and_bind_${order.id}")
                        ) {
                            Icon(Icons.Default.Build, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Prepare & Bind Serial", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OrderStatus.ORD_PREP -> {
                        if (order.allocatedSerial == null) {
                            Button(
                                onClick = { onSelectSerial(false) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD97706)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("select_serial_${order.id}")
                            ) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Select Serial", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            Button(
                                onClick = onStartDeliveryOrPickup,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(38.dp)
                                    .testTag("start_dispatch_${order.id}")
                            ) {
                                Icon(
                                    imageVector = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) Icons.Default.DirectionsBike else Icons.Default.Store,
                                    contentDescription = null,
                                    modifier = Modifier.size(14.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Start Delivery Trip" else "Stage for Pickup",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    OrderStatus.ORD_DISP -> {
                        Button(
                            onClick = onArriveAtHandover,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("arrive_doorstep_${order.id}")
                        ) {
                            Icon(Icons.Default.PinDrop, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Arrived at Doorstep", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OrderStatus.ORD_RDYPU -> {
                        Button(
                            onClick = onArriveAtHandover,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF047857)),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                                .testTag("arrive_counter_${order.id}")
                        ) {
                            Icon(Icons.Default.Store, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Customer at Counter", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OrderStatus.ORD_ATHO -> {
                        Button(
                            onClick = onCompletePod,
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                            modifier = Modifier
                                .weight(1.3f)
                                .height(40.dp)
                                .testTag("complete_pod_${order.id}")
                        ) {
                            Icon(Icons.Default.FactCheck, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Safety Check & POD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    OrderStatus.ORD_HOC -> {
                        Surface(
                            color = Color(0xFFDCFCE7),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(38.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxSize(),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF16A34A), modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Handover Done — Finalizing", color = Color(0xFF166534), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {}
                }

                // Secondary Issue / Refusal Action
                if (order.status != OrderStatus.ORD_HOC) {
                    OutlinedButton(
                        onClick = onReportIssue,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFDC2626)),
                        modifier = Modifier
                            .height(38.dp)
                            .testTag("report_issue_${order.id}"),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Icon(Icons.Default.ReportProblem, contentDescription = null, tint = Color(0xFFDC2626), modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = if (order.status == OrderStatus.ORD_ATHO) "Refused" else "Issue",
                            fontSize = 11.sp,
                            color = Color(0xFFDC2626)
                        )
                    }
                }
            }
        }
    }
}
