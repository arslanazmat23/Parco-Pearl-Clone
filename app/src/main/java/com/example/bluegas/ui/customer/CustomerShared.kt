/**
 * Shared models, UI utilities, and presentation helpers for the customer application.
 *
 * Provides cross-cutting primitives including the unified banner messaging model,
 * transparent demonstration value pills for open client decisions (e.g. Q03, Q14),
 * low-overhead lifecycle-aware millisecond tickers, and plain-language payment status mappers.
 *
 * This file contains pure presentation logic and does not directly mutate store state.
 */
package com.example.bluegas.ui.customer

import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.bluegas.model.FulfilmentMode
import com.example.bluegas.model.Order
import com.example.bluegas.model.OrderLine
import com.example.bluegas.model.OrderType
import com.example.bluegas.model.PaymentMethod
import com.example.bluegas.model.PaymentState
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

/**
 * Representation of a transient banner message displayed globally in the customer application shell.
 *
 * Enforces the customer app message contract where [isError] controls visual hierarchy
 * (amber warning banner for errors, green banner for successful operations).
 *
 * @property text Human-readable message copy to present to the user.
 * @property isError True if the message represents an error, failure, or rejected action.
 */
data class BannerMsg(
    val text: String,
    val isError: Boolean = false
)

/**
 * Visual badge indicating an undecided pilot configuration or pending operational decision.
 *
 * Explicitly surfaces unresolved parameters (e.g., Q03 deposit refund terms, Q14 opening hours)
 * in accordance with honest disclosure rules without inventing mock data.
 *
 * @param text The specific question reference and description (e.g. "Demo value · Q03").
 * @param modifier Optional layout modifier.
 */
@Composable
fun DemoValuePill(text: String, modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFE2E8F0),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.Medium,
            color = Color(0xFF475569),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Coroutine-backed state producer providing the current epoch millisecond timestamp.
 *
 * Ticks periodically only while [enabled] is true. When disabled (e.g. order has no active
 * stock hold or has transitioned past dispatch), the background ticker halts to eliminate
 * unnecessary recompositions and conserve battery.
 *
 * @param tickMs Interval between clock ticks in milliseconds.
 * @param enabled Controls whether the ticking coroutine is active.
 * @return The latest epoch timestamp in milliseconds.
 */
@Composable
fun rememberNowMillis(tickMs: Long = 1000L, enabled: Boolean = true): Long {
    return produceState(initialValue = System.currentTimeMillis(), key1 = enabled, key2 = tickMs) {
        if (!enabled) return@produceState
        while (isActive) {
            value = System.currentTimeMillis()
            delay(tickMs)
        }
    }.value
}

/**
 * Formats a remaining time duration in milliseconds into a concise "m:ss" countdown string.
 *
 * @param remainingMs Duration remaining until expiry in milliseconds.
 * @return Formatted countdown string, clamping at "0:00" if remaining time is non-positive.
 */
fun formatCountdown(remainingMs: Long): String {
    if (remainingMs <= 0L) return "0:00"
    val totalSeconds = remainingMs / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return String.format("%d:%02d", minutes, seconds)
}

/**
 * Immutable carrier for reorder form prefill values transferred from order history to the checkout form.
 *
 * Keyed by a unique [nonce] to prevent duplicate consumption. The checkout screen re-validates
 * address ownership, SKU eligibility, and delivery zone serviceability before applying the prefill.
 *
 * @property sku Cylinder SKU to select.
 * @property quantity Number of cylinders to order.
 * @property orderType Whether refill exchange or new cylinder.
 * @property addressId Saved customer address ID to prefill.
 * @property mode Doorstep delivery or depot pickup.
 * @property paymentMethod Selected payment method.
 * @property pickupPointId Depot node ID if pickup mode.
 * @property addAccessory Whether regulator and hose add-on is included.
 * @property nonce Unique timestamp identifier indicating when the prefill was generated.
 */
data class ReorderPrefill(
    val sku: String,
    val quantity: Int,
    val orderType: OrderType,
    val addressId: String?,
    val mode: FulfilmentMode,
    val paymentMethod: PaymentMethod,
    val pickupPointId: String?,
    val addAccessory: Boolean,
    val nonce: Long
)

/**
 * Maps the internal payment method and state into plain-language customer status text.
 *
 * Implements business rules preventing leakage of technical state machine enum names:
 * - Settled COD orders display "Paid."
 * - Collected COD undergoing reconciliation displays "Cash received — Finance is reconciling it."
 * - Captured online orders display "Paid online."
 * - Pre-authorized online holds display "Payment authorised. The amount is taken after delivery."
 * - Failed or timed out attempts notify "Your last payment attempt didn't go through."
 *
 * @param method The selected payment method (Cash on delivery or Online hosted).
 * @param state The current payment state, or null if no payment attempt exists yet.
 * @param formattedTotal Formatted rupee amount due for cash collection.
 * @return Human-readable payment status summary.
 */
fun formatCustomerPaymentStatus(
    method: PaymentMethod,
    state: PaymentState?,
    formattedTotal: String
): String {
    return when (method) {
        PaymentMethod.CASH_ON_DELIVERY -> when (state) {
            PaymentState.SETTLED -> "Paid."
            PaymentState.CASH_COLLECTED -> "Cash received — Finance is reconciling it."
            else -> "Cash on delivery — $formattedTotal due at handover."
        }
        PaymentMethod.ONLINE_HOSTED -> when (state) {
            PaymentState.SETTLED -> "Paid."
            PaymentState.INITIAL_AMOUNT_CAPTURED,
            PaymentState.CAPTURED,
            PaymentState.FINAL_AMOUNT_DETERMINED,
            PaymentState.NO_ADJUSTMENT,
            PaymentState.ADDITIONAL_COLLECTION_COMPLETED,
            PaymentState.PARTIAL_REFUND_COMPLETED -> "Paid online."
            PaymentState.PREAUTHORIZED -> "Payment authorised. The amount is taken after delivery."
            PaymentState.PAYMENT_FAILED_CLOSED,
            PaymentState.AUTHORIZATION_FAILED,
            PaymentState.PAYMENT_TIMEOUT -> "Your last payment attempt didn't go through."
            null -> "Online payment pending."
            else -> state.displayName
        }
    }
}

/** Order lines a customer should see as items. The DEPOSIT line is excluded because the deposit is shown on its own labelled row. */
fun Order.customerVisibleItems(): List<OrderLine> = items.filter { it.lineType != "DEPOSIT" }

