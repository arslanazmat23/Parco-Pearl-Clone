package com.example.bluegas.ui.customer

import android.content.Intent
import android.util.Log
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.SharedAppState
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

// F1.3
data class ReceiptRow(
    val label: String,
    val value: String,
    val emphasis: Boolean = false
)

// F1.3
fun buildReceiptRows(order: Order, snapshot: SharedAppState): List<ReceiptRow> {
    val rows = mutableListOf<ReceiptRow>()

    // Order, date
    rows.add(ReceiptRow("Order ID", order.id))
    rows.add(ReceiptRow("Date", formatTimestamp(order.createdAt)))

    // Customer, address or depot
    val customer = snapshot.customers.find { it.id == order.customerId }
    if (customer != null) {
        rows.add(ReceiptRow("Customer", customer.name))
    }
    if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
        val address = snapshot.addresses.find { it.id == order.addressId }
        if (address != null) {
            rows.add(ReceiptRow("Delivery address", "${address.street}, ${address.area}, ${address.city}"))
        }
    } else {
        val depotId = order.pickupPointId ?: order.fulfilmentPointId
        val depot = snapshot.nodes.find { it.id == depotId }
        if (depot != null) {
            rows.add(ReceiptRow("Pickup depot", "${depot.name}, ${depot.address}, ${depot.city}"))
        }
    }

    // Fulfilment: Delivery / Depot pickup + fulfilment point name
    val pointName = snapshot.nodes.find { it.id == order.fulfilmentPointId }?.name ?: order.fulfilmentPointId ?: "Depot"
    val fulfilmentText = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Delivery — $pointName" else "Depot pickup — $pointName"
    rows.add(ReceiptRow("Fulfilment", fulfilmentText))

    // Each quote line as charged: for GAS, CYLINDER, ACCESSORY lines: "${qty}x ${productName}" -> formatRupees(subtotalPaisa). Skip DEPOSIT line itself (subtotal is 0).
    var linesSum = 0L
    order.items.forEach { line ->
        if (line.lineType != "DEPOSIT") {
            rows.add(ReceiptRow("${line.quantity}x ${line.productName}", formatRupees(line.subtotalPaisa)))
            linesSum += line.subtotalPaisa
        }
    }

    // Security deposit: one row from depositTotalPaisa when > 0, labelled "Security deposit (held; does not transfer ownership)"
    if (order.depositTotalPaisa > 0L) {
        rows.add(ReceiptRow("Security deposit (held; does not transfer ownership)", formatRupees(order.depositTotalPaisa)))
    }

    // Platform fee: platformFeePaisa
    if (order.platformFeePaisa > 0L) {
        rows.add(ReceiptRow("Platform fee", formatRupees(order.platformFeePaisa)))
    }

    // Delivery fee: deliveryFeePaisa
    if (order.deliveryFeePaisa > 0L) {
        rows.add(ReceiptRow("Delivery fee", formatRupees(order.deliveryFeePaisa)))
    }

    // Discount: - + discountPaisa when > 0
    if (order.discountPaisa > 0L) {
        rows.add(ReceiptRow("Discount", "-${formatRupees(order.discountPaisa)}"))
    }

    // GST: gstPaisa, and "Further tax" when furtherTaxPaisa > 0
    if (order.gstPaisa > 0L) {
        rows.add(ReceiptRow("GST", formatRupees(order.gstPaisa)))
    }
    if (order.furtherTaxPaisa > 0L) {
        rows.add(ReceiptRow("Further tax", formatRupees(order.furtherTaxPaisa)))
    }

    // Total: totalAmountPaisa (label "Total paid" only for a settled/paid order, else "Total")
    val isPaidOrSettled = order.status == OrderStatus.ORD_COMP || order.status == OrderStatus.ORD_HOC ||
        (order.paymentMethod == PaymentMethod.ONLINE_HOSTED && order.status != OrderStatus.ORD_PAYPEND && order.status != OrderStatus.ORD_DRAFT && order.status != OrderStatus.ORD_VALPEND && order.status != OrderStatus.ORD_CANC && order.status != OrderStatus.ORD_FCLO)
    val totalLabel = if (isPaidOrSettled) "Total paid" else "Total"
    rows.add(ReceiptRow(totalLabel, formatRupees(order.totalAmountPaisa), emphasis = true))

    // Price list: "Price list v${priceListVersion}" and, when reference118PricePaisa != null, "11.8 kg reference ${formatRupees(...)}"
    val priceListRef = if (order.reference118PricePaisa != null) " · 11.8 kg reference ${formatRupees(order.reference118PricePaisa)}" else ""
    rows.add(ReceiptRow("Price list", "v${order.priceListVersion}$priceListRef"))

    // Payment: method in plain words (Online payment / Cash on delivery) and status in plain words
    val methodWords = when (order.paymentMethod) {
        PaymentMethod.ONLINE_HOSTED -> "Online payment"
        PaymentMethod.CASH_ON_DELIVERY -> "Cash on delivery"
    }
    val paymentObj = snapshot.payments.filter { it.orderId == order.id }.maxByOrNull { it.attemptNo }
    val paymentStatusWords = when {
        paymentObj?.state == PaymentState.SETTLED -> "Settled"
        paymentObj?.state == PaymentState.CASH_COLLECTED -> "Cash collected"
        paymentObj?.state == PaymentState.INITIAL_AMOUNT_CAPTURED -> "Paid"
        paymentObj?.state == PaymentState.PREAUTHORIZED -> "Authorized"
        paymentObj?.state == PaymentState.COD_PENDING -> "Due at handover"
        paymentObj?.state == PaymentState.PAYMENT_PENDING -> "Pending"
        paymentObj?.state == PaymentState.PAYMENT_FAILED_CLOSED -> "Failed"
        else -> order.status.displayName
    }
    rows.add(ReceiptRow("Payment", "$methodWords · $paymentStatusWords"))

    // Cylinder ID: allocatedSerial only if non-null
    if (order.allocatedSerial != null) {
        rows.add(ReceiptRow("Cylinder ID", order.allocatedSerial ?: ""))
    }

    // Installation: from installationResult / installationNotDoneReason if present
    val installRes = order.installationResult
    if (installRes != null) {
        val installText = when (installRes) {
            InstallationResult.INSTALLED -> "Installed by rider"
            InstallationResult.NOT_INSTALLED -> "Not installed — ${order.installationNotDoneReason ?: "Declined by customer"}"
            InstallationResult.NOT_APPLICABLE -> "Not applicable"
        }
        rows.add(ReceiptRow("Installation", installText))
    }

    // Proof of delivery: podReference only if non-null
    if (order.podReference != null) {
        rows.add(ReceiptRow("Proof of delivery", order.podReference ?: ""))
    }

    // Footing check
    val calculatedSum = linesSum + order.depositTotalPaisa + order.platformFeePaisa + order.deliveryFeePaisa - order.discountPaisa + order.gstPaisa + order.furtherTaxPaisa
    if (calculatedSum != order.totalAmountPaisa) {
        Log.w("Receipt", "Receipt line sum mismatch: lines=$linesSum deposit=${order.depositTotalPaisa} plat=${order.platformFeePaisa} del=${order.deliveryFeePaisa} disc=${order.discountPaisa} gst=${order.gstPaisa} furtherTax=${order.furtherTaxPaisa} sum=$calculatedSum vs total=${order.totalAmountPaisa}")
    }

    return rows
}

// F1.3
@Composable
fun ReceiptDialog(
    order: Order,
    snapshot: SharedAppState,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val rows = remember(order, snapshot) { buildReceiptRows(order, snapshot) }

    val shareText = remember(rows) {
        buildString {
            appendLine("BLUE GAS — Delivery Receipt")
            appendLine("================================")
            rows.forEach { row ->
                appendLine("${row.label}: ${row.value}")
            }
            appendLine("================================")
            appendLine("Prices, fees and tax are demo settings pending client decisions (Q03, Q04, Q22, Q24).")
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.Receipt, contentDescription = null, tint = BlueGasPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Delivery Receipt", fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(rows) { row ->
                        if (row.emphasis) {
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(row.label, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Text(
                                    row.value,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = BlueGasPrimary
                                )
                            }
                            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                        } else {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Text(
                                    row.label,
                                    fontSize = 12.sp,
                                    color = Color(0xFF64748B),
                                    modifier = Modifier.weight(1.2f)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    row.value,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color(0xFF0F172A),
                                    modifier = Modifier.weight(1.5f)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Prices, fees and tax are demo settings pending client decisions (Q03, Q04, Q22, Q24).",
                    fontSize = 9.sp,
                    color = Color.Gray,
                    lineHeight = 12.sp
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_SUBJECT, "Receipt for Order ${order.id}")
                        putExtra(Intent.EXTRA_TEXT, shareText)
                    }
                    context.startActivity(Intent.createChooser(shareIntent, "Share receipt"))
                },
                modifier = Modifier.testTag("receipt_share_button")
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Share receipt", fontSize = 12.sp)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
