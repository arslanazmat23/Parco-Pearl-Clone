package com.example.bluegas.model

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 19 Canonical Order Status Codes and Display Names.
 */
enum class OrderStatus(val code: String, val displayName: String, val isTerminal: Boolean = false) {
    ORD_DRAFT("ORD_DRAFT", "Draft"),
    ORD_VALPEND("ORD_VALPEND", "Validation Pending"),
    ORD_PAYPEND("ORD_PAYPEND", "Payment Pending"),
    ORD_CONF("ORD_CONF", "Confirmed"),
    ORD_RSVD("ORD_RSVD", "Reserved"),
    ORD_ASGN("ORD_ASGN", "Assigned"),
    ORD_ACPT("ORD_ACPT", "Accepted"),
    ORD_PREP("ORD_PREP", "Preparing"),
    ORD_DISP("ORD_DISP", "Dispatched"),
    ORD_RDYPU("ORD_RDYPU", "Ready for Pickup"),
    ORD_ATHO("ORD_ATHO", "At Handover"),
    ORD_INSP("ORD_INSP", "Inspection Pending"),
    ORD_ADJ("ORD_ADJ", "Adjustment Pending"),
    ORD_HOC("ORD_HOC", "Handover Complete"),
    ORD_EXC("ORD_EXC", "Fulfilment Exception"),
    ORD_COMP("ORD_COMP", "Completed", isTerminal = true),
    ORD_CANC("ORD_CANC", "Cancelled", isTerminal = true),
    ORD_SUPS("ORD_SUPS", "Superseded", isTerminal = true),
    ORD_FCLO("ORD_FCLO", "Failed Closed", isTerminal = true);

    val canonicalLabel: String get() = "$code $displayName"
}

/**
 * Constrained reasons for raising Fulfilment Exception.
 */
enum class ExceptionReason(val description: String) {
    VALIDATION_OUT_OF_AREA("Validation / Out-of-area address"),
    PAYMENT_ISSUE("Payment authorization or timeout issue"),
    NO_ACCEPTANCE("No distributor/rider acceptance in time window"),
    SERIAL_UNAVAILABLE_OR_PREP_FAILURE("Cylinder serial unavailable or preparation failure"),
    DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE("Delivery route failure or customer unavailable"),
    ADJUSTED_AMOUNT_REJECTION("Adjusted final amount rejected by customer"),
    UNRECOVERABLE_AFTER_RETRIES("Max retries exceeded / unrecoverable failure")
}

/**
 * Fulfilment modes: an order is either Delivery or Pickup, never both.
 */
enum class FulfilmentMode {
    DELIVERY,
    PICKUP
}

/**
 * Inventory States.
 */
enum class InventoryState(val code: String, val label: String) {
    INV_AVAIL("INV_AVAIL", "Available Stock"),
    INV_HOLD("INV_HOLD", "Temporary Hold (Pre-Payment)"),
    INV_RSVD("INV_RSVD", "Firm Reservation (Post-Confirmation)"),
    INV_SERIAL("INV_SERIAL", "Exact Serial Bound"),
    INV_COMMIT("INV_COMMIT", "Committed to Order"),
    INV_TRANSIT("INV_TRANSIT", "In Transit (Rider Custody)"),
    INV_RELEASED("INV_RELEASED", "Released Back to Stock"),
    INV_CONSUMED("INV_CONSUMED", "Consumed at Handover"),
    INV_WRITEOFF("INV_WRITEOFF", "Write-Off / Damaged")
}

/**
 * Cylinder Ownership & Custody are separate concepts.
 */
enum class CylinderOwnership {
    OWNED_BY_CUSTOMER,
    DEPOSIT_HELD,
    LOANED,
    RETURNED
}

enum class CylinderCustody {
    DISTRIBUTOR_NODE,
    RIDER_VAN,
    CUSTOMER_PREMISES
}

/**
 * Payment Methods & Separate Lifecycle.
 */
enum class PaymentMethod {
    CASH_ON_DELIVERY,
    ONLINE_HOSTED
}

enum class PaymentState(val displayName: String) {
    // COD states
    COD_PENDING("COD Pending"),
    FINAL_AMOUNT_DETERMINED("Final Amount Determined"),
    CASH_COLLECTION_PENDING("Cash Collection Pending"),
    CASH_COLLECTED("Cash Collected"),
    SETTLED("Settled"),
    PAYMENT_CANCELLED("Payment Cancelled"),

    // Online states
    PAYMENT_PENDING("Payment Pending"),
    INITIAL_AMOUNT_CAPTURED("Initial Amount Captured"),
    NO_ADJUSTMENT("No Adjustment Needed"),
    ADDITIONAL_COLLECTION_PENDING("Additional Collection Pending"),
    PARTIAL_REFUND_PENDING("Partial Refund Pending"),
    PAYMENT_FAILED_CLOSED("Payment Failed Closed"),

    // Exceptions
    AUTHORIZATION_FAILED("Authorization Failed"),
    PAYMENT_TIMEOUT("Payment Timeout"),
    RETRY_PENDING("Retry Pending"),
    FULL_REFUND_PENDING("Full Refund Pending"),
    FULLY_REFUNDED("Fully Refunded")
}

/**
 * Accountable human roles for decisions, overrides, and approvals.
 */
enum class AccountableRole(val displayName: String) {
    OPERATIONS("Operations Team"),
    FINANCE("Finance Team"),
    DISTRIBUTOR("Distributor Lead"),
    SUPPORT("Customer Support"),
    INVENTORY("Inventory Controller"),
    ADMIN("System Administrator")
}

enum class AppSurface {
    CUSTOMER,
    RIDER,
    STAFF
}

enum class StaffSubRole(val displayName: String) {
    OPERATIONS("Operations"),
    FINANCE("Finance"),
    DISTRIBUTOR("Distributor"),
    SUPPORT("Customer Support"),
    ADMIN("Admin & Config")
}

/**
 * Customer model.
 */
data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val cnic: String,
    val defaultAddressId: String,
    val holdings: List<String> = emptyList()
)

data class Address(
    val id: String,
    val customerId: String,
    val label: String,
    val street: String,
    val area: String,
    val city: String, // "Lahore" or "Islamabad"
    val zoneId: String,
    val isServiceable: Boolean
)

data class ServiceZone(
    val id: String,
    val city: String,
    val name: String,
    val isServiceable: Boolean,
    val postalCodes: List<String>
)

data class Distributor(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val phone: String,
    val status: String // "ACTIVE", "PENDING_APPROVAL"
)

data class Node(
    val id: String,
    val distributorId: String,
    val name: String,
    val zoneId: String,
    val capacity: Int
)

data class ProductVariant(
    val id: String,
    val sku: String,
    val name: String,
    val category: String, // "CYLINDER_REFILL", "NEW_PACKAGE", "ACCESSORY"
    val cylinderSizeKg: Double?,
    val isCylinder: Boolean,
    val description: String
)

/**
 * Configurable Pricing in Paisa (1 PKR = 100 Paisa).
 */
data class PriceBookConfig(
    val productPricesPaisa: Map<String, Long>, // sku -> price in paisa
    val depositAmountPaisa: Map<String, Long>, // sku -> deposit in paisa
    val platformFeePaisa: Long = 15000L,        // 150 PKR
    val deliveryFeePaisa: Long = 30000L,        // 300 PKR
    val taxRateBps: Int = 500,                  // 5.0% = 500 basis points
    val holdTtlMinutes: Int = 15,
    val distributorAcceptanceWindowMins: Int = 20,
    val verificationLevel: String = "PHONE_OTP", // "NONE", "PHONE_OTP", "CNIC"
    val enabledPaymentMethods: List<PaymentMethod> = listOf(PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.ONLINE_HOSTED)
)

data class OrderLine(
    val sku: String,
    val productName: String,
    val quantity: Int,
    val unitPricePaisa: Long,
    val unitDepositPaisa: Long,
    val subtotalPaisa: Long,
    val depositTotalPaisa: Long
)

data class OrderStatusHistory(
    val id: String,
    val orderId: String,
    val fromStatus: OrderStatus,
    val toStatus: OrderStatus,
    val trigger: String,
    val actor: String,
    val role: AccountableRole,
    val timestamp: Long = System.currentTimeMillis()
)

data class OrderAssignment(
    val orderId: String,
    val distributorId: String,
    val riderId: String?,
    val assignedAt: Long,
    val acceptedAt: Long? = null
)

data class Delivery(
    val id: String,
    val orderId: String,
    val riderId: String,
    val dispatchedAt: Long? = null,
    val arrivedAtHandoverAt: Long? = null,
    val completedAt: Long? = null,
    val nonTechnicalInstallationPassed: Boolean = false,
    val proofOfDeliveryNote: String? = null
)

data class StockBalance(
    val nodeId: String,
    val sku: String,
    var availableQty: Int,
    var holdQty: Int,
    var reservedQty: Int,
    var committedQty: Int
)

data class StockHold(
    val id: String,
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val expiresAt: Long,
    var isReleased: Boolean = false
)

data class StockReservation(
    val id: String,
    val orderId: String,
    val sku: String,
    val quantity: Int,
    val reservedAt: Long,
    var isCommitted: Boolean = false
)

data class Cylinder(
    val serialNumber: String,
    val sku: String,
    val weightKg: Double,
    val status: InventoryState,
    val custody: CylinderCustody,
    val currentLocationLabel: String,
    val allocatedOrderId: String? = null,
    val lastInspectionDate: String = "2026-09-15"
)

data class CylinderCustodyEvent(
    val id: String,
    val serialNumber: String,
    val fromCustody: CylinderCustody,
    val toCustody: CylinderCustody,
    val orderId: String?,
    val actor: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class CustomerCylinderHolding(
    val id: String,
    val customerId: String,
    val cylinderSerial: String,
    val ownership: CylinderOwnership,
    val depositPaisa: Long,
    val acquiredAt: Long
)

data class Payment(
    val id: String,
    val orderId: String,
    val method: PaymentMethod,
    var state: PaymentState,
    val baseAmountPaisa: Long,
    var finalAmountPaisa: Long,
    var capturedAmountPaisa: Long = 0L,
    var settledAmountPaisa: Long = 0L,
    val currency: String = "PKR",
    val idempotencyKey: String,
    val createdAt: Long = System.currentTimeMillis()
)

data class PaymentAttempt(
    val id: String,
    val paymentId: String,
    val amountPaisa: Long,
    val gatewayStatus: String, // "SUCCESS", "DECLINED", "TIMEOUT"
    val timestamp: Long = System.currentTimeMillis()
)

data class CodCollection(
    val id: String,
    val orderId: String,
    val riderId: String,
    val expectedAmountPaisa: Long,
    val collectedAmountPaisa: Long,
    val collectedAt: Long = System.currentTimeMillis()
)

data class Refund(
    val id: String,
    val paymentId: String,
    val orderId: String,
    val amountPaisa: Long,
    val reason: String,
    val executedBy: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class LedgerEntry(
    val id: String,
    val orderId: String?,
    val entryType: String, // "REVENUE", "DEPOSIT_LIABILITY", "PLATFORM_FEE", "REFUND", "SETTLEMENT"
    val debitPaisa: Long,
    val creditPaisa: Long,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SupportCase(
    val id: String,
    val orderId: String,
    val customerId: String,
    val issueType: String, // "LEAKAGE_SAFETY", "DELAYED_DELIVERY", "OVERCHARGING", "CYLINDER_DENT"
    val description: String,
    var status: String, // "OPEN", "INVESTIGATING", "RESOLVED"
    var resolutionNotes: String? = null,
    val raisedAt: Long = System.currentTimeMillis()
)

data class PartnerApplication(
    val id: String,
    val type: String, // "DISTRIBUTOR", "RIDER"
    val applicantName: String,
    val city: String,
    val cnic: String,
    val vehicleOrShopDetails: String,
    var status: String // "PENDING_APPROVAL", "APPROVED", "REJECTED"
)

data class NotificationItem(
    val id: String,
    val surface: AppSurface,
    val title: String,
    val message: String,
    val canonicalStatus: String,
    val orderId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false
)

data class AuditLog(
    val id: String,
    val entityType: String,
    val entityId: String,
    val action: String,
    val actor: String,
    val role: AccountableRole,
    val details: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class IdempotencyRecord(
    val key: String,
    val action: String,
    val responseStatus: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class OfflineOutboxEvent(
    val sequenceNumber: Int,
    val idempotencyKey: String,
    val orderId: String,
    val action: String,
    val scannedSerial: String?,
    val timestamp: Long,
    var syncStatus: String // "PENDING", "APPLIED", "DUPLICATE", "REJECTED", "CONFLICT_QUEUED"
)

data class Order(
    val id: String,
    val customerId: String,
    val addressId: String,
    val fulfilmentMode: FulfilmentMode,
    var status: OrderStatus,
    val items: List<OrderLine>,
    val subtotalPaisa: Long,
    val depositTotalPaisa: Long, // Distinct liability line
    val platformFeePaisa: Long,
    val deliveryFeePaisa: Long,
    val taxPaisa: Long,
    val totalAmountPaisa: Long,
    var paymentMethod: PaymentMethod,
    var allocatedSerial: String? = null,
    var assignedDistributorId: String? = null,
    var assignedRiderId: String? = null,
    var exceptionReason: ExceptionReason? = null,
    var exceptionDetails: String? = null,
    val holdExpiresAt: Long? = null,
    val createdAt: Long = System.currentTimeMillis()
)

fun formatPaisaToPkr(paisa: Long): String {
    val rupees = paisa / 100.0
    return String.format(Locale.US, "PKR %,.2f", rupees)
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.US)
    return sdf.format(Date(millis))
}
