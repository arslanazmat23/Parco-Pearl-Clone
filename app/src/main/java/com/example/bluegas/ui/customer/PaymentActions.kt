package com.example.bluegas.ui.customer

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.data.BlueGasStore
import com.example.bluegas.data.SharedAppState
import com.example.bluegas.data.confirmPaymentAndAdvance
import com.example.bluegas.data.retryPayment
import com.example.bluegas.model.*
import com.example.ui.theme.BlueGasPrimary

/**
 * Interactive demo payment gateway dialog allowing simulation of pilot payment outcomes.
 *
 * @param orderId Unique identifier of the order to process.
 * @param payableLabel Formatted rupee string of the total amount due passed from snapshot state.
 * @param store Reference to BlueGasStore for executing advance transitions.
 * @param onResult Callback invoked with (message, isError) matching the global customer banner contract.
 * @param onDismiss Invoked when the dialog is dismissed without selecting an outcome.
 */
@Composable
fun DemoPaymentDialog(
    orderId: String,
    payableLabel: String,
    store: BlueGasStore,
    onResult: (message: String, isError: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CreditCard, contentDescription = null, tint = BlueGasPrimary)
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pay now (demo)", fontWeight = FontWeight.Bold, fontSize = 15.sp)
            }
        },
        text = {
            Column {
                Text("Order: $orderId", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                Text(
                    "Payable: $payableLabel",
                    fontSize = 14.sp,
                    color = BlueGasPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "This is a demo gateway for the pilot. Choose an outcome:",
                    fontSize = 11.sp,
                    color = Color(0xFF475569)
                )
                Spacer(modifier = Modifier.height(10.dp))

                Button(
                    onClick = {
                        val (ok, msg) = store.confirmPaymentAndAdvance(orderId, "SUCCESS")
                        onResult(msg, !ok)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF16A34A)),
                    modifier = Modifier.fillMaxWidth().testTag("payment_dialog_success_button")
                ) {
                    Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Payment approved")
                }
                Spacer(modifier = Modifier.height(6.dp))
                Button(
                    onClick = {
                        val (ok, msg) = store.confirmPaymentAndAdvance(orderId, "DECLINED")
                        onResult(msg, !ok)
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                    modifier = Modifier.fillMaxWidth().testTag("payment_dialog_decline_button")
                ) {
                    Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Payment declined")
                }
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedButton(
                    onClick = {
                        val (ok, msg) = store.confirmPaymentAndAdvance(orderId, "TIMEOUT")
                        onResult(msg, !ok)
                        onDismiss()
                    },
                    modifier = Modifier.fillMaxWidth().testTag("payment_dialog_timeout_button")
                ) {
                    Icon(Icons.Default.Timer, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Connection lost")
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * Payment actions row presented when an order is in [OrderStatus.ORD_PAYPEND].
 *
 * Implements the payment attempt decision table:
 * - Active unexpired attempt present -> Direct "Pay now" gateway action.
 * - Previous attempt failed/closed/timed-out -> Failure prompt with "Retry payment" (online)
 *   or switch to "Pay cash on delivery instead".
 *
 * @param order The target pending order.
 * @param snapshot Reactive snapshot containing system payment history.
 * @param store Store reference for initiating payment retries.
 * @param onResult Callback receiving (message: String, isError: Boolean).
 * @param onOpenGateway Callback to trigger the demo payment gateway modal.
 * @param modifier Optional modifier for the row container.
 */
@Composable
fun PaymentActionsRow(
    order: Order,
    snapshot: SharedAppState,
    store: BlueGasStore,
    onResult: (message: String, isError: Boolean) -> Unit,
    onOpenGateway: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (order.status != OrderStatus.ORD_PAYPEND) return

    val nowMillis = rememberNowMillis(enabled = order.holdExpiresAt != null)
    val remainingMs = (order.holdExpiresAt ?: 0L) - nowMillis
    val isExpired = order.holdExpiresAt != null && remainingMs <= 0L

    // Latest attempt lookup
    val orderPayments = snapshot.payments.filter { it.orderId == order.id }
    val latestPayment = orderPayments.maxByOrNull { it.attemptNo }
    val hasActiveAttempt = latestPayment != null &&
        latestPayment.state != PaymentState.PAYMENT_FAILED_CLOSED &&
        !latestPayment.state.isTerminal

    Column(modifier = modifier.fillMaxWidth()) {
        // Countdown / Expiration text
        if (order.holdExpiresAt != null) {
            val countdownText = if (isExpired) {
                "The payment window has ended."
            } else {
                "Pay within ${formatCountdown(remainingMs)} to keep your cylinder reserved."
            }
            Text(
                text = countdownText,
                fontSize = 12.sp,
                color = if (isExpired) MaterialTheme.colorScheme.error else Color(0xFFB45309),
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(6.dp))
        }

        if (hasActiveAttempt && order.paymentMethod != PaymentMethod.CASH_ON_DELIVERY) {
            Button(
                onClick = onOpenGateway,
                enabled = !isExpired,
                colors = ButtonDefaults.buttonColors(containerColor = BlueGasPrimary),
                modifier = Modifier.fillMaxWidth().testTag("payment_actions_pay_now_button")
            ) {
                Icon(Icons.Default.CreditCard, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Pay now")
            }
        } else {
            // No active attempt (last was declined or timed out, or reset)
            Text(
                text = "Your last payment attempt didn't go through.",
                fontSize = 12.sp,
                color = Color(0xFFDC2626),
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        val (ok, msg) = store.retryPayment(order.id, PaymentMethod.ONLINE_HOSTED)
                        onResult(msg, !ok)
                    },
                    enabled = !isExpired,
                    modifier = Modifier.weight(1f).testTag("payment_actions_retry_button")
                ) {
                    Text("Retry payment", fontSize = 12.sp)
                }
                OutlinedButton(
                    onClick = {
                        val (ok, msg) = store.retryPayment(order.id, PaymentMethod.CASH_ON_DELIVERY)
                        onResult(msg, !ok)
                    },
                    enabled = !isExpired,
                    modifier = Modifier.weight(1f).testTag("payment_actions_cod_button")
                ) {
                    Text("Pay cash on delivery instead", fontSize = 11.sp)
                }
            }
        }
    }
}
