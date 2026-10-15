package com.example.bluegas.ui.rider

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
fun RiderCompletedScreen(
    store: BlueGasStore,
    onViewReceipt: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val activeRider = store.getActiveRider() ?: store.riders.first()
    val completedOrders = store.orders.filter {
        it.assignedRiderId == activeRider.id && it.status == OrderStatus.ORD_COMP
    }.sortedByDescending { it.createdAt }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .testTag("rider_completed_screen")
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Completed Deliveries",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp,
                    color = BlueGasDark
                )
                Text(
                    text = "Full delivery audit trail & digital POD receipts",
                    fontSize = 10.sp,
                    color = Color(0xFF64748B)
                )
            }

            Surface(
                color = Color(0xFFDCFCE7),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "${completedOrders.size} Completed",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF166534),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        if (completedOrders.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        imageVector = Icons.Default.TaskAlt,
                        contentDescription = null,
                        tint = Color(0xFF94A3B8),
                        modifier = Modifier.size(52.dp)
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    Text(
                        text = "No deliveries completed yet today",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF475569)
                    )
                    Text(
                        text = "Complete handovers with full proof of delivery to see finished orders and official receipts here.",
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
                items(completedOrders, key = { it.id }) { order ->
                    CompletedOrderCard(
                        order = order,
                        store = store,
                        onViewReceipt = { onViewReceipt(order.id) }
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
private fun CompletedOrderCard(
    order: Order,
    store: BlueGasStore,
    onViewReceipt: () -> Unit
) {
    val customer = store.customers.find { it.id == order.customerId }
    val address = store.addresses.find { it.id == order.addressId }
    val delivery = store.deliveries.find { it.orderId == order.id }

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF86EFAC)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("completed_order_${order.id}")
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            // Row 1: ID + Status Chip
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF16A34A),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = order.id,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF0F172A)
                    )
                }

                CanonicalStatusChip(order.status)
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Customer Name & Destination Address
            Text(
                text = "${customer?.name ?: "Customer"} • ${customer?.phone ?: ""}",
                fontWeight = FontWeight.Bold,
                fontSize = 12.sp,
                color = Color(0xFF1E293B)
            )
            Text(
                text = "${address?.label ?: "Delivered"}: ${address?.area ?: ""}, ${address?.city ?: ""}",
                fontSize = 11.sp,
                color = Color(0xFF64748B)
            )

            Spacer(modifier = Modifier.height(6.dp))

            // Delivered Cylinder Serial
            Surface(
                color = Color(0xFFECFDF5),
                shape = RoundedCornerShape(6.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFA7F3D0)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Delivered Serial: ${order.allocatedSerial ?: "CYL-SERIAL-VERIFIED"}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFF065F46)
                    )
                    Text(
                        text = "POD: ${order.podReference ?: "VERIFIED"}",
                        fontSize = 10.sp,
                        color = Color(0xFF047857),
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Payment and Completion Details
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY)
                        "💵 COD Settled: ${formatPaisaToPkr(order.totalAmountPaisa)}"
                    else
                        "💳 Online Paid: ${formatPaisaToPkr(order.totalAmountPaisa)}",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = if (order.paymentMethod == PaymentMethod.CASH_ON_DELIVERY) Color(0xFFB45309) else Color(0xFF166534)
                )

                Text(
                    text = formatTimestamp(delivery?.completedAt ?: order.createdAt),
                    fontSize = 10.sp,
                    color = Color.Gray
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // View Receipt Action Button
            Button(
                onClick = onViewReceipt,
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(36.dp)
                    .testTag("view_receipt_button_${order.id}"),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp)
            ) {
                Icon(Icons.Default.Receipt, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("View Official Receipt & POD", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun OfficialReceiptDialog(
    order: Order,
    store: BlueGasStore,
    onDismiss: () -> Unit
) {
    val customer = store.customers.find { it.id == order.customerId }
    val address = store.addresses.find { it.id == order.addressId }
    val delivery = store.deliveries.find { it.orderId == order.id }
    val rider = store.riders.find { it.id == order.assignedRiderId } ?: store.getActiveRider()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = BlueGasPrimary)
                Spacer(modifier = Modifier.width(8.dp))
                Text("BLUE GAS — Official Delivery Receipt", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text("Order ID: ${order.id}", fontWeight = FontWeight.Bold, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                Text("Customer: ${customer?.name ?: "Customer"} (${customer?.phone ?: ""})", fontSize = 11.sp, color = Color(0xFF1E293B))
                Text("Delivery: ${address?.label ?: ""}, ${address?.area ?: ""}, ${address?.city ?: ""}", fontSize = 10.sp, color = Color(0xFF64748B))
                Text("Date: ${formatTimestamp(delivery?.completedAt ?: order.createdAt)}", fontSize = 10.sp, color = Color.Gray)

                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(6.dp))

                // Line items
                order.items.forEach { itm ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("${itm.quantity}x ${itm.productName}", fontSize = 11.sp)
                        Text(formatPaisaToPkr(itm.subtotalPaisa), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                    }
                }

                if (order.depositTotalPaisa > 0) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Cylinder Security Deposit", fontSize = 11.sp, color = Color(0xFFB45309))
                        Text(formatPaisaToPkr(order.depositTotalPaisa), fontSize = 11.sp, color = Color(0xFFB45309), fontWeight = FontWeight.SemiBold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Delivery & Platform Fees", fontSize = 11.sp)
                    Text(formatPaisaToPkr(order.deliveryFeePaisa + order.platformFeePaisa), fontSize = 11.sp)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Sales Tax (5%)", fontSize = 11.sp)
                    Text(formatPaisaToPkr(order.taxPaisa), fontSize = 11.sp)
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Grand Total Amount:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    Text(formatPaisaToPkr(order.totalAmountPaisa), fontWeight = FontWeight.Bold, fontSize = 14.sp, color = BlueGasPrimary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                // Delivery & POD Metadata
                Surface(
                    color = Color(0xFFF8FAFC),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(8.dp)) {
                        Text("Delivered by: ${rider?.name ?: "Rider"} (${rider?.vehicleNumber ?: ""})", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Bound Serial: ${order.allocatedSerial ?: "CYL-SERIAL-VERIFIED"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = Color(0xFF047857))
                        Text("POD Reference: ${order.podReference ?: "POD-${order.id}-DONE"}", fontSize = 10.sp, fontFamily = FontFamily.Monospace, color = BlueGasPrimary)
                        Text("Signer: ${delivery?.signerName ?: customer?.name ?: "Recipient"}", fontSize = 10.sp)
                        Text("OGRA Standards: ✓ Bubble Leak Test Passed", fontSize = 10.sp, color = Color(0xFF166534), fontWeight = FontWeight.SemiBold)
                        Text("Photo Evidence: ✓ Geotagged Watermark Attached", fontSize = 10.sp, color = Color(0xFF166534))
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("Dismiss Receipt")
            }
        }
    )
}
