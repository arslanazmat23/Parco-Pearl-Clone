/**
 * Formal order delivery receipt view and breakdown generator for the customer application.
 *
 * Implements a compliant financial receipt display:
 * - Order and customer identifiers, dates, and delivery address or depot pickup location
 * - Itemized line breakdown (Gas refill, security deposit, platform fee, delivery charge, GST, discounts)
 * - Factual cylinder serial tracking and doorstep installation record
 * - Arithmetic footing check ensuring sub-lines and taxes reconcile with the total charge
 * - Native Android share intent export ([Intent.ACTION_SEND])
 *
 * Reads: [SharedAppState.customers], [SharedAppState.addresses], [SharedAppState.nodes], [SharedAppState.payments]
 */
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

/**
 * Single item row in the itemized delivery receipt table.
 *
 * @property label Field description or charge item name.
 * @property value Formatted financial amount or descriptive string.
 * @property emphasis True if the row represents a total or summary requiring visual prominence.
 */
data class ReceiptRow(
    val label: String,
    val value: String,
    val emphasis: Boolean = false
)

/**
 * Builds the ordered sequence of key-value receipt rows for an order.
 *
 * Reconciles items, pricing list versions, regulatory taxes, and allocated hardware identifiers.
 * Performs an internal arithmetic footing verification to ensure displayed lines sum to the total.
 *
 * @param order Order entity being summarized.
 * @param snapshot Reactive snapshot containing related node, customer, and payment records.
 * @return Ordered list of [ReceiptRow] entries.
 */
fun buildReceiptRows(order: Order, snapshot: SharedAppState): List<ReceiptRow> {
    val rows = mutableListOf<ReceiptRow>()

    // Order identification and creation timestamp
    rows.add(ReceiptRow("Order ID", order.id))
    rows.add(ReceiptRow("Date", formatTimestamp(order.createdAt)))

    // Customer name and destination details
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

    // Fulfilment node servicing the order
    val pointName = snapshot.nodes.find { it.id == order.fulfilmentPointId }?.name ?: order.fulfilmentPointId ?: "Depot"
    val modeLabel = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Doorstep delivery" else "Depot pickup"
    rows.add(ReceiptRow("Fulfilment", "$modeLabel · $pointName"))

    // Itemized lines
    var linesSum = 0L
    order.customerVisibleItems().forEach { item ->
        val desc = "${item.quantity}x ${item.productName}"
        val lineTotal = item.unitPricePaisa * item.quantity
        linesSum += lineTotal
        rows.add(ReceiptRow(desc, formatRupees(lineTotal)))
    }

    // Deposit: cylinder security deposit if present
    if (order.depositTotalPaisa > 0) {
        rows.add(ReceiptRow("Cylinder security deposit", formatRupees(order.depositTotalPaisa)))
    }

    // Platform fee if non-zero
    if (order.platformFeePaisa > 0) {
        rows.add(ReceiptRow("Platform fee", formatRupees(order.platformFeePaisa)))
    }

    // Delivery fee if delivery mode and non-zero
    if (order.deliveryFeePaisa > 0) {
        rows.add(ReceiptRow("Delivery fee", formatRupees(order.deliveryFeePaisa)))
    }

    // Discount if any
    if (order.discountPaisa > 0) {
        rows.add(ReceiptRow("Discount", "-${formatRupees(order.discountPaisa)}"))
    }

    // GST / Tax if non-zero
    if (order.gstPaisa > 0) {
        rows.add(ReceiptRow("GST", formatRupees(order.gstPaisa)))
    }
    if (order.furtherTaxPaisa > 0) {
        rows.add(ReceiptRow("Further tax", formatRupees(order.furtherTaxPaisa)))
    }

    // Total: Total paid for settled/completed orders, or Total for pending
    val isPaidOrSettled = order.status == OrderStatus.ORD_COMP || order.status == OrderStatus.ORD_HOC ||
        (order.paymentMethod == PaymentMethod.ONLINE_HOSTED && order.status != OrderStatus.ORD_PAYPEND && order.status != OrderStatus.ORD_DRAFT && order.status != OrderStatus.ORD_VALPEND && order.status != OrderStatus.ORD_CANC && order.status != OrderStatus.ORD_FCLO)
    val totalLabel = if (isPaidOrSettled) "Total paid" else "Total"
    rows.add(ReceiptRow(totalLabel, formatRupees(order.totalAmountPaisa), emphasis = true))

    // Pricing schedule reference
    val priceListRef = if (order.reference118PricePaisa != null) " · 11.8 kg reference ${formatRupees(order.reference118PricePaisa)}" else ""
    rows.add(ReceiptRow("Price list", "v${order.priceListVersion}$priceListRef"))

    // Payment method and status in customer language (Settled becomes Paid, PREAUTHORIZED becomes Authorised)
    val methodWords = when (order.paymentMethod) {
        PaymentMethod.ONLINE_HOSTED -> "Online payment"
        PaymentMethod.CASH_ON_DELIVERY -> "Cash on delivery"
    }
    val paymentObj = snapshot.payments.filter { it.orderId == order.id }.maxByOrNull { it.attemptNo }
    val paymentStatusWords = when {
        paymentObj?.state == PaymentState.SETTLED -> "Paid"
        paymentObj?.state == PaymentState.CASH_COLLECTED -> "Cash collected"
        paymentObj?.state == PaymentState.INITIAL_AMOUNT_CAPTURED ||
            paymentObj?.state == PaymentState.CAPTURED ||
            paymentObj?.state == PaymentState.FINAL_AMOUNT_DETERMINED ||
            paymentObj?.state == PaymentState.NO_ADJUSTMENT ||
            paymentObj?.state == PaymentState.ADDITIONAL_COLLECTION_COMPLETED ||
            paymentObj?.state == PaymentState.PARTIAL_REFUND_COMPLETED -> "Paid online"
        paymentObj?.state == PaymentState.PREAUTHORIZED -> "Authorised"
        paymentObj?.state == PaymentState.COD_PENDING -> "Due at handover"
        paymentObj?.state == PaymentState.PAYMENT_PENDING -> "Pending"
        paymentObj?.state == PaymentState.PAYMENT_FAILED_CLOSED -> "Failed"
        else -> paymentObj?.state?.displayName ?: order.status.displayName
    }
    rows.add(ReceiptRow("Payment", "$methodWords · $paymentStatusWords"))

    // Allocated cylinder hardware identifier
    if (order.allocatedSerial != null) {
        rows.add(ReceiptRow("Cylinder ID", order.allocatedSerial ?: ""))
    }

    // Doorstep installation outcome
    val installRes = order.installationResult
    if (installRes != null) {
        val installText = when (installRes) {
            InstallationResult.INSTALLED -> "Installed by rider"
            InstallationResult.NOT_INSTALLED -> "Not installed — ${order.installationNotDoneReason ?: "Declined by customer"}"
            InstallationResult.NOT_APPLICABLE -> "Not applicable"
        }
        rows.add(ReceiptRow("Installation", installText))
    }

    // Proof of delivery reference if captured
    if (order.podReference != null) {
        rows.add(ReceiptRow("Proof of delivery", order.podReference ?: ""))
    }

    // Footing verification: confirm lines and taxes add up exactly to the total charge
    val calculatedSum = linesSum + order.depositTotalPaisa + order.platformFeePaisa + order.deliveryFeePaisa - order.discountPaisa + order.gstPaisa + order.furtherTaxPaisa
    if (calculatedSum != order.totalAmountPaisa) {
        Log.w("Receipt", "Receipt line sum mismatch: lines=$linesSum deposit=${order.depositTotalPaisa} plat=${order.platformFeePaisa} del=${order.deliveryFeePaisa} disc=${order.discountPaisa} gst=${order.gstPaisa} furtherTax=${order.furtherTaxPaisa} sum=$calculatedSum vs total=${order.totalAmountPaisa}")
    }

    return rows
}

/**
 * Modal dialog displaying the detailed formal receipt for a completed or active order.
 *
 * Provides a button to share the plaintext receipt via the standard system share sheet.
 *
 * @param order Order to generate the receipt for.
 * @param snapshot Reactive application state containing node and customer information.
 * @param onDismiss Invoked when the dialog is dismissed.
 */
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
