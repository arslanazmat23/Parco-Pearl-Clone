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
import com.example.bluegas.data.computeDeliveryWindows
import com.example.bluegas.model.DepositStatus
import com.example.bluegas.model.formatRupees
import com.example.bluegas.model.FulfilmentMode
import com.example.bluegas.model.L6Status
import com.example.bluegas.model.Order
import com.example.bluegas.model.OrderLine
import com.example.bluegas.model.OrderType
import com.example.bluegas.model.PaymentMethod
import com.example.bluegas.model.PaymentState
import com.example.bluegas.model.PlatformSettings
import com.example.bluegas.model.Refund
import com.example.bluegas.model.ReturnRefundCase
import com.example.bluegas.model.ServiceLevel
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
 * Blue disclosure pill indicating a recommended new customer feature awaiting client sign-off.
 */
@Composable
fun RecommendedPill(
    text: String = "Recommended — awaiting approval",
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFDBEAFE),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = text,
            fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
            color = Color(0xFF1D4ED8),
            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
        )
    }
}

/**
 * Compact orange chip identifying Express delivery without exposing raw enum identifiers.
 */
@Composable
fun ExpressChip(modifier: Modifier = Modifier) {
    Surface(
        color = Color(0xFFFFEDD5),
        shape = RoundedCornerShape(4.dp),
        modifier = modifier
    ) {
        Text(
            text = ServiceLevel.EXPRESS.label,
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFC2410C),
            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
        )
    }
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
 * @property mode Doorstep delivery or hub pickup.
 * @property paymentMethod Selected payment method.
 * @property pickupPointId Hub node ID if pickup mode.
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
    val nonce: Long,
    val serviceLevel: ServiceLevel = ServiceLevel.STANDARD
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

/** Plain-language label for deposit account lifecycle status. */
fun customerDepositStatusLabel(status: DepositStatus): String = when (status) {
    DepositStatus.HELD -> "Held"
    DepositStatus.REFUND_IN_PROGRESS -> "Refund in progress"
    DepositStatus.REFUNDED -> "Refunded"
    DepositStatus.REDUCED_OR_FORFEITED -> "Reduced or closed"
}

/** Formats preferred return collection time window options from [PlatformSettings] using the single window generator. */
fun customerReturnWindowOptions(
    settings: PlatformSettings,
    nowMillis: Long = System.currentTimeMillis()
): List<String> {
    return computeDeliveryWindows(
        settings = settings,
        nowMillis = nowMillis,
        includeAsap = false
    ).map { it.label }
}

/** Plain-language summary of a customer's cylinder return progress (7 steps, indices 0..6) with zero enum leakage. */
data class CustomerReturnProgressInfo(
    val currentStepIndex: Int,
    val stepTitles: List<String>,
    val statusDetail: String,
    val isRejected: Boolean = false
)

fun mapCustomerReturnProgress(
    caseItem: ReturnRefundCase,
    linkedRefund: Refund? = null
): CustomerReturnProgressInfo {
    val isDealerNoDeposit = caseItem.requestedAmountPaisa <= 0L
    val netRefundedPaisa = (linkedRefund?.amountPaisa ?: (caseItem.requestedAmountPaisa - caseItem.deductionPaisa))
        .coerceAtLeast(0L)
    val step7Title = if (isDealerNoDeposit) {
        "Return completed"
    } else {
        "Deposit refunded — ${formatRupees(netRefundedPaisa)}"
    }
    val titles = listOf(
        "Request received",
        "Collection being arranged",
        "Rider will collect your cylinder",
        "Cylinder collected — being checked",
        "Checked — refund being prepared",
        "Refund in progress",
        step7Title
    )

    val wasRejected = caseItem.status == L6Status.RETURN_REJECTED ||
        (caseItem.status == L6Status.CASE_CLOSED && caseItem.history.any { it.toStatus == L6Status.RETURN_REJECTED })
    if (wasRejected) {
        val rejReason = caseItem.rejectionReason?.takeIf { it.isNotBlank() }
            ?: caseItem.history.lastOrNull { it.toStatus == L6Status.RETURN_REJECTED }?.reason?.takeIf { it.isNotBlank() }
        return CustomerReturnProgressInfo(
            currentStepIndex = -1,
            stepTitles = titles,
            statusDetail = if (rejReason != null) "Request not accepted: $rejReason" else "Request not accepted",
            isRejected = true
        )
    }

    return when (caseItem.status) {
        L6Status.CASE_OPENED, L6Status.UNDER_REVIEW -> CustomerReturnProgressInfo(
            currentStepIndex = 0,
            stepTitles = titles,
            statusDetail = "Our team is reviewing your return request."
        )
        L6Status.RETURN_AUTHORIZED -> CustomerReturnProgressInfo(
            currentStepIndex = 1,
            stepTitles = titles,
            statusDetail = "Collection is being arranged."
        )
        L6Status.AWAITING_ITEM_RETURN -> {
            if (caseItem.collectionTask == "COLLECTED" || caseItem.collectionTask == "AT_HUB") {
                CustomerReturnProgressInfo(
                    currentStepIndex = 3,
                    stepTitles = titles,
                    statusDetail = if (caseItem.collectionTask == "AT_HUB") {
                        "Cylinder arrived at our hub — being checked."
                    } else {
                        "Cylinder collected — being checked."
                    }
                )
            } else {
                val windowText = caseItem.preferredCollectionWindow?.takeIf { it.isNotBlank() }
                val windowSuffix = if (windowText != null) " Preferred time: $windowText." else ""
                val detail = when (caseItem.collectionTask) {
                    "ACCEPTED" -> "Rider will collect your cylinder.$windowSuffix"
                    "OFFERED" -> "Rider notified to collect your cylinder.$windowSuffix"
                    else -> "Assigning a rider to collect your cylinder.$windowSuffix"
                }
                CustomerReturnProgressInfo(
                    currentStepIndex = 2,
                    stepTitles = titles,
                    statusDetail = detail
                )
            }
        }
        L6Status.ITEM_RETURNED -> CustomerReturnProgressInfo(
            currentStepIndex = 4,
            stepTitles = titles,
            statusDetail = "Cylinder checked — refund is being prepared."
        )
        L6Status.REFUND_REQUESTED -> {
            val payoutFailed = linkedRefund?.state == PaymentState.FULL_REFUND_FAILED ||
                linkedRefund?.state == PaymentState.PARTIAL_REFUND_FAILED
            CustomerReturnProgressInfo(
                currentStepIndex = 5,
                stepTitles = titles,
                statusDetail = if (payoutFailed) {
                    "We're retrying your refund payment."
                } else {
                    "Your refund is in progress."
                }
            )
        }
        L6Status.REFUND_CONFIRMED, L6Status.CASE_CLOSED -> {
            val detail = when {
                isDealerNoDeposit -> "Return completed."
                caseItem.deductionPaisa > 0L -> {
                    val dedReason = caseItem.deductionReason?.takeIf { it.isNotBlank() } ?: "Condition adjustment"
                    "${formatRupees(caseItem.deductionPaisa)} was kept: $dedReason"
                }
                else -> "Deposit refunded — ${formatRupees(netRefundedPaisa)}"
            }
            CustomerReturnProgressInfo(
                currentStepIndex = 6,
                stepTitles = titles,
                statusDetail = detail
            )
        }
        L6Status.RETURN_REJECTED -> CustomerReturnProgressInfo(
            currentStepIndex = -1,
            stepTitles = titles,
            statusDetail = "Request not accepted",
            isRejected = true
        )
    }
}


