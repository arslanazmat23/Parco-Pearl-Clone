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

    fun customerLabel(mode: FulfilmentMode): String = when (this) {
        ORD_DRAFT -> "Draft"
        ORD_VALPEND -> "Checking your order"
        ORD_PAYPEND -> "Awaiting payment"
        ORD_CONF, ORD_RSVD -> "Order confirmed"
        ORD_ASGN -> "Distributor assigned"
        ORD_ACPT, ORD_PREP -> "Being prepared"
        ORD_DISP -> "On the way"
        ORD_RDYPU -> "Ready to collect"
        ORD_ATHO -> if (mode == FulfilmentMode.DELIVERY) "Rider has arrived" else "At the counter"
        ORD_INSP -> "Inspection in progress"
        ORD_ADJ -> "Please confirm the updated amount"
        ORD_HOC -> "Delivered"
        ORD_EXC -> "We're sorting out a problem with your order"
        ORD_COMP -> "Completed"
        ORD_CANC -> "Cancelled"
        ORD_SUPS -> "Replaced by order"
        ORD_FCLO -> "Could not be completed"
    }

    val stage: Int get() = when (this) {
        ORD_DRAFT, ORD_VALPEND, ORD_PAYPEND -> 0
        ORD_CONF, ORD_RSVD -> 1
        ORD_ASGN -> 2
        ORD_ACPT, ORD_PREP -> 3
        ORD_DISP, ORD_RDYPU -> 4
        ORD_ATHO, ORD_INSP, ORD_ADJ -> 5
        ORD_HOC, ORD_COMP -> 6
        ORD_EXC -> -1
        ORD_CANC, ORD_SUPS, ORD_FCLO -> -2
    }
}

/**
 * Constrained reasons for raising Fulfilment Exception with mapped recovery targets.
 */
enum class ExceptionReason(
    val description: String,
    val excIds: List<String> = emptyList()
) {
    VALIDATION_OUT_OF_AREA(
        "Address or validation problem",
        listOf("EXC-01")
    ),
    PAYMENT_ISSUE(
        "Payment problem that can be fixed",
        listOf("EXC-02", "EXC-03")
    ),
    NO_ACCEPTANCE(
        "Not accepted by distributor or rider in time",
        listOf("EXC-05")
    ),
    SERIAL_UNAVAILABLE_OR_PREP_FAILURE(
        "No suitable cylinder, or cylinder has no readable ID, at Preparing",
        listOf("EXC-06", "EXC-22")
    ),
    DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE(
        "Delivery or pickup failed (customer unavailable, not collected in time, handover could not be completed, safety stop)",
        listOf("EXC-07")
    ),
    GOODS_REJECTED_AT_DOOR(
        "Goods rejected at the door",
        listOf("EXC-08")
    ),
    ADJUSTED_AMOUNT_REJECTION(
        "Customer rejected the adjusted amount (Phase 2)",
        listOf("EXC-09")
    ),
    UNRECOVERABLE_AFTER_RETRIES(
        "Cannot be recovered after the allowed retries",
        listOf("EXC-16")
    );

    val allowedRecoveryTargets: Set<OrderStatus> get() = when (this) {
        VALIDATION_OUT_OF_AREA -> setOf(OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        PAYMENT_ISSUE -> setOf(OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        NO_ACCEPTANCE -> setOf(OrderStatus.ORD_ASGN, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        SERIAL_UNAVAILABLE_OR_PREP_FAILURE -> setOf(OrderStatus.ORD_PREP, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE -> setOf(OrderStatus.ORD_ASGN, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        GOODS_REJECTED_AT_DOOR -> setOf(OrderStatus.ORD_ASGN, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        ADJUSTED_AMOUNT_REJECTION -> setOf(OrderStatus.ORD_ADJ, OrderStatus.ORD_CANC, OrderStatus.ORD_FCLO)
        UNRECOVERABLE_AFTER_RETRIES -> setOf(OrderStatus.ORD_FCLO)
    }
}

enum class FulfilmentMode {
    DELIVERY,
    PICKUP
}

enum class OrderType {
    REFILL_EXCHANGE,
    REFILL_REORDER,
    NEW_CYLINDER,
    ACCESSORY_ONLY,
    SWITCH_AND_SAVE
}

enum class NewCylinderModel {
    SOLD,
    DEPOSIT,
    LOAN
}

enum class RefillModel {
    EMPTY_EXCHANGE,
    REORDER,
    BOTH
}

enum class OnlinePaymentMode {
    BRANCH_A_AUTHORIZE_THEN_CAPTURE,
    BRANCH_B_CAPTURE_UPFRONT
}

enum class CylinderIdMode {
    OPTION_A_TAGGED,
    OPTION_B_PHASE_IN
}

enum class PriceMethod {
    REFERENCE_11_8_BY_WEIGHT,
    PRICE_PER_SIZE
}

enum class RoundingRule {
    NEAREST,
    UP,
    DOWN
}

enum class InstallationResult {
    INSTALLED,
    NOT_INSTALLED,
    NOT_APPLICABLE
}

enum class FulfilmentPointType {
    PLANT,
    DEPOT,
    DISTRIBUTOR
}

enum class RiderTaskStatus {
    OFFERED,
    ACCEPTED,
    DECLINED
}

enum class L6Status {
    CASE_OPENED,
    UNDER_REVIEW,
    RETURN_AUTHORIZED,
    RETURN_REJECTED,
    AWAITING_ITEM_RETURN,
    ITEM_RETURNED,
    REFUND_REQUESTED,
    REFUND_CONFIRMED,
    CASE_CLOSED
}

enum class L7Status {
    INCIDENT_RAISED,
    UNDER_HSE_ASSESSMENT,
    AWAITING_AUTHORIZED_DECISION,
    AUTHORIZED_ACTION_TAKEN,
    INCIDENT_CLOSED
}

enum class ClaimStatus {
    DRAFT,
    EVIDENCE_SUBMITTED,
    ELIGIBILITY_ASSESSED,
    MANUAL_REVIEW,
    PROVISIONALLY_ELIGIBLE,
    INELIGIBLE,
    PROVISIONAL_OFFER_ISSUED,
    CUSTOMER_CONFIRMED,
    SCHEDULED_FOR_HANDOVER,
    AT_HANDOVER_INSPECTION,
    PHYSICALLY_REJECTED,
    PHYSICALLY_ACCEPTED,
    HSE_ASSESSMENT,
    HSE_CLEARED,
    HSE_HOLD_UNSAFE,
    FINAL_OFFER_CONFIRMED,
    EXCHANGE_COMPLETED,
    CLOSED,
    CANCELLED,
    REVERSED
}

enum class SurrenderStatus {
    COLLECTED,
    IN_TRANSIT,
    RECEIVED_AT_DEPOT,
    SECONDARY_ASSESSMENT,
    HSE_HOLD,
    DISPOSITION_PENDING,
    DISPOSITION_AUTHORIZED,
    RETURNED_TO_CUSTOMER,
    RELEASED_TO_AUTHORIZED_PARTY,
    RECYCLING_OR_DESTRUCTION,
    RECORD_CLOSED,
    DISPOSED
}

enum class InventoryState(val code: String, val label: String) {
    INV_AVAIL("INV_AVAIL", "Available"),
    INV_HOLD("INV_HOLD", "Temporary Hold"),
    INV_RSVD("INV_RSVD", "Firm Reservation"),
    INV_SERIAL("INV_SERIAL", "Serial Allocated"),
    INV_COMMIT("INV_COMMIT", "Stock Committed"),
    INV_TRANSIT("INV_TRANSIT", "In-Transit Custody"),
    INV_RELEASED("INV_RELEASED", "Released"),
    INV_CONSUMED("INV_CONSUMED", "Consumed/Handed Over"),
    INV_WRITEOFF("INV_WRITEOFF", "Written-Off")
}

enum class CylinderOwnership(val label: String) {
    OWNED_BY_CUSTOMER("Owned by customer"),
    DEPOSIT_HELD("Deposit held"),
    LOANED("Loaned"),
    RETURNED("Returned/Released"),
    SURRENDERED_ASSET("Surrendered asset — pending disposition")
}

enum class CylinderCustody(val label: String) {
    DISTRIBUTOR_NODE("With BLUE GAS / distributor"),
    RIDER_VAN("In transit"),
    CUSTOMER_PREMISES("With customer"),
    PLANT("At plant"),
    SURRENDERED_HELD("Surrendered — held separately")
}

enum class CustodyRecordStatus {
    ACTIVE,
    RECORD_CLOSED,
    DISPOSED
}

enum class PaymentMethod {
    CASH_ON_DELIVERY,
    ONLINE_HOSTED
}

enum class PaymentState(val displayName: String, val isTerminal: Boolean = false) {
    PAYMENT_PENDING("Payment Pending"),
    COD_PENDING("COD Pending"),
    PREAUTHORIZED("Preauthorized"),
    INITIAL_AMOUNT_CAPTURED("Initial Amount Captured"),
    FINAL_AMOUNT_DETERMINED("Final Amount Determined"),
    FINAL_CAPTURE_PENDING("Final Capture Pending"),
    CAPTURED("Captured"),
    NO_ADJUSTMENT("No Adjustment Needed"),
    ADDITIONAL_COLLECTION_PENDING("Additional Collection Pending"),
    ADDITIONAL_COLLECTION_COMPLETED("Additional Collection Completed"),
    ADDITIONAL_COLLECTION_FAILED("Additional Collection Failed"),
    PARTIAL_REFUND_PENDING("Partial Refund Pending"),
    PARTIAL_REFUND_COMPLETED("Partial Refund Completed"),
    PARTIAL_REFUND_FAILED("Partial Refund Failed"),
    FULL_REFUND_PENDING("Full Refund Pending"),
    FULL_REFUND_FAILED("Full Refund Failed"),
    CASH_COLLECTION_PENDING("Cash Collection Pending"),
    CASH_COLLECTED("Cash Collected"),
    ADJUSTMENT_FAILED("Adjustment Failed"),
    AUTHORIZATION_FAILED("Authorization Failed"),
    PAYMENT_TIMEOUT("Payment Timeout"),

    // Terminal statuses (never leave these)
    SETTLED("Settled", isTerminal = true),
    VOIDED("Voided", isTerminal = true),
    FULLY_REFUNDED("Fully Refunded", isTerminal = true),
    PAYMENT_CANCELLED("Payment Cancelled", isTerminal = true),
    PAYMENT_FAILED_CLOSED("Payment Failed Closed", isTerminal = true)
}

enum class AccountableRole(val displayName: String) {
    OPERATIONS("Operations Lead"),
    FINANCE("Finance Lead"),
    DISTRIBUTOR("Distributor Lead"),
    SUPPORT("Customer Support"),
    INVENTORY("Inventory Controller"),
    ADMIN("System Administrator"),
    PLANT("Plant Manager"),
    COMMERCIAL("Commercial Manager"),
    HSE("HSE Officer"),
    LEGAL("Legal/Compliance Reviewer"),
    INSPECTOR("Authorized Inspector"),
    TECHNICIAN("Technician")
}

enum class AppSurface {
    CUSTOMER,
    RIDER,
    STAFF
}

enum class StaffSubRole(val displayName: String) {
    OPERATIONS("Operations"),
    DISTRIBUTOR("Distributor"),
    PLANT_INVENTORY("Plant & Stock"),
    FINANCE("Finance"),
    COMMERCIAL("Pricing"),
    SUPPORT("Support"),
    SAFETY_HSE("Safety (HSE)"),
    ADMIN("Admin"),
    PHASE2_PREVIEW("Phase 2")
}

// --- Domain Models ---

data class Customer(
    val id: String,
    val name: String,
    val phone: String,
    val cnic: String,
    val defaultAddressId: String,
    val holdings: List<String> = emptyList(),
    val createdAt: Long = System.currentTimeMillis() - 90L * 86400000L,
    val isPhoneVerified: Boolean = true,
    val isCnicVerified: Boolean = true,
    val customerType: String = "HOUSEHOLD"
)

data class Address(
    val id: String,
    val customerId: String,
    val label: String,
    val street: String,
    val area: String,
    val city: String, // "Lahore" or "Islamabad"
    val zoneId: String,
    val isServiceable: Boolean,
    val servingPointId: String? = null,
    val isDefault: Boolean = false
)

data class ServiceZone(
    val id: String,
    val city: String,
    val name: String,
    val isServiceable: Boolean,
    val postalCodes: List<String>,
    val servingPointIds: List<String> = emptyList()
)

data class Distributor(
    val id: String,
    val name: String,
    val city: String,
    val address: String,
    val phone: String,
    val status: String = "ACTIVE", // "ACTIVE", "PENDING_APPROVAL"
    val zoneIds: List<String> = emptyList(),
    val pointId: String = "NODE-LHR-1",
    val isExistingDealer: Boolean = true,
    val creditLimitPaisa: Long = 0L, // Q25: Credit off by default
    val approvedAt: Long? = System.currentTimeMillis()
)

data class Node(
    val id: String,
    val distributorId: String,
    val name: String,
    val zoneId: String,
    val capacity: Int,
    val type: FulfilmentPointType = FulfilmentPointType.DISTRIBUTOR,
    val address: String = "Plot 14, Industrial Area",
    val pickupEnabled: Boolean = true,
    val servesZoneIds: List<String> = emptyList(),
    val city: String = "Lahore"
)

data class Rider(
    val id: String,
    val name: String,
    val phone: String,
    val cnic: String,
    val zoneId: String,
    val zoneName: String,
    val nodeId: String,
    val vehicleNumber: String,
    val status: String, // "ACTIVE", "INACTIVE"
    val onboardingStatus: String = "APPROVED",
    val distributorId: String? = "DIST-001",
    val installationTrained: Boolean = true,
    val deviceId: String = "DEV-RIDER-01"
)

data class ProductVariant(
    val id: String,
    val sku: String,
    val name: String,
    val category: String, // "CYLINDER_REFILL", "NEW_PACKAGE", "ACCESSORY"
    val cylinderSizeKg: Double?,
    val isCylinder: Boolean,
    val description: String,
    val brand: String = "BLUE GAS",
    val isHouseholdApproved: Boolean = true,
    val taxable: Boolean = true,
    val nominalFillTonnes: Double = (cylinderSizeKg ?: 0.0) / 1000.0
)

data class PriceBookConfig(
    val productPricesPaisa: Map<String, Long> = mapOf(
        "SKU-LPG-11-8" to 445000L,
        "SKU-LPG-11-0" to 414800L,
        "SKU-LPG-6-0"  to 226300L,
        "SKU-LPG-45-4" to 1712100L,
        "SKU-REG-HOSE" to 145000L
    ),
    val depositAmountPaisa: Map<String, Long> = mapOf(
        "SKU-LPG-11-8" to 750000L,
        "SKU-LPG-11-0" to 700000L,
        "SKU-LPG-6-0"  to 450000L,
        "SKU-LPG-45-4" to 1800000L,
        "SKU-REG-HOSE" to 0L
    ),
    val platformFeePaisa: Long = 15000L,
    val deliveryFeePaisa: Long = 30000L,
    val taxRateBps: Int = 500,
    val holdTtlMinutes: Int = 15,
    val distributorAcceptanceWindowMins: Int = 20,
    val verificationLevel: String = "PHONE_OTP",
    val enabledPaymentMethods: List<PaymentMethod> = listOf(PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.ONLINE_HOSTED)
)

data class OrderLine(
    val sku: String,
    val productName: String,
    val quantity: Int,
    val unitPricePaisa: Long,
    val unitDepositPaisa: Long,
    val subtotalPaisa: Long,
    val depositTotalPaisa: Long,
    val lineType: String = "GAS", // "GAS", "CYLINDER", "ACCESSORY"
    val unitPriceExactPaisa: Long = unitPricePaisa
)

data class OrderStatusHistory(
    val id: String,
    val orderId: String,
    val fromStatus: OrderStatus,
    val toStatus: OrderStatus,
    val trigger: String,
    val actor: String,
    val role: AccountableRole,
    val reasonCode: String? = null,
    val deviceId: String? = null,
    val location: String? = null,
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
    val proofOfDeliveryNote: String? = null,
    val podReference: String? = null,
    val customerSignatureCaptured: Boolean = false,
    val signatureUnavailableReason: String? = null,
    val photoEvidenceCaptured: Boolean = false,
    val photoEvidenceTimestamp: Long? = null,
    val signerName: String? = null,
    val changeReturnedPaisa: Long? = null,
    val installationChecklist: Map<String, Boolean> = emptyMap(),
    val installationResult: InstallationResult? = null,
    val notInstalledReason: String? = null,
    val pinVerified: Boolean = false,
    val evidenceConsent: Boolean = true,
    val deviceId: String? = null,
    val location: String? = null,
    val evidenceHash: String? = null
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
    val lastInspectionDate: String = "2026-09-15",
    val brand: String = "BLUE GAS",
    val isTagged: Boolean = true,
    val pointId: String? = "NODE-LHR-1",
    val ownership: CylinderOwnership? = null,
    val custodyRecord: CustodyRecordStatus = CustodyRecordStatus.ACTIVE,
    val hseHold: Boolean = false
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

data class PaymentStatusHistory(
    val id: String,
    val paymentId: String,
    val fromState: PaymentState,
    val toState: PaymentState,
    val actor: String,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class Payment(
    val id: String,
    val orderId: String,
    var method: PaymentMethod,
    var state: PaymentState,
    val baseAmountPaisa: Long,
    var finalAmountPaisa: Long,
    var capturedAmountPaisa: Long = 0L,
    var settledAmountPaisa: Long = 0L,
    val currency: String = "PKR",
    val idempotencyKey: String,
    val createdAt: Long = System.currentTimeMillis(),
    val attemptNo: Int = 1,
    var branch: String = if (method == PaymentMethod.CASH_ON_DELIVERY) "C" else "B",
    var authorizedAmountPaisa: Long = 0L,
    var closedReason: String? = null
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
    val timestamp: Long = System.currentTimeMillis(),
    var state: PaymentState = PaymentState.FULL_REFUND_PENDING,
    val kind: String = "FULL", // "FULL", "PARTIAL"
    val linkedCaseId: String? = null,
    var attempts: Int = 1,
    var completedAt: Long? = null
)

data class LedgerEntry(
    val id: String,
    val orderId: String?,
    val entryType: String, // "REVENUE_GAS", "REVENUE_CYLINDER", "ACCESSORY_REVENUE", "DELIVERY_FEE", "PLATFORM_FEE", "TAX_PAYABLE", "DEPOSIT_LIABILITY", "REFUND", "SETTLEMENT", "CASH_SHORT_OVER"
    val debitPaisa: Long,
    val creditPaisa: Long,
    val description: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SupportCase(
    val id: String,
    val orderId: String? = null,
    val customerId: String,
    val issueType: String, // "DELIVERY_DELAY", "OVERCHARGE", "CYLINDER_CONDITION", "RIDER_CONDUCT", "APP_PAYMENT", "OTHER"
    val description: String,
    var status: String = "OPEN", // "OPEN", "IN_PROGRESS", "RESOLVED"
    var resolutionNotes: String? = null,
    val raisedAt: Long = System.currentTimeMillis(),
    var resolvedAt: Long? = null
)

data class PartnerDocument(
    val name: String,
    val isProvided: Boolean = true
)

data class PartnerApplication(
    val id: String,
    val type: String, // "DISTRIBUTOR", "RIDER"
    val applicantName: String,
    val city: String,
    val cnic: String,
    val vehicleOrShopDetails: String,
    var status: String = "PENDING_APPROVAL", // "PENDING_APPROVAL", "RETURNED_INCOMPLETE", "APPROVED", "REJECTED"
    val phone: String = "+92 300 0000000",
    val appliedAt: Long = System.currentTimeMillis() - 86400000L * 2L,
    val documents: List<PartnerDocument> = listOf(
        PartnerDocument("CNIC Copy", true),
        PartnerDocument("Shop Licence / Vehicle Registration", true),
        PartnerDocument("Premises / Vehicle Photo", true)
    ),
    val zoneId: String? = null,
    val linkedDistributorId: String? = null,
    var decisionReason: String? = null,
    var decidedAt: Long? = null,
    var decidedBy: String? = null
)

data class NotificationItem(
    val id: String,
    val surface: AppSurface,
    val title: String,
    val message: String,
    val canonicalStatus: String,
    val orderId: String?,
    val timestamp: Long = System.currentTimeMillis(),
    var isRead: Boolean = false,
    val code: String = "NTF-01",
    val staffRole: StaffSubRole? = null
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
    val fromStatus: OrderStatus? = null,
    val toStatus: OrderStatus? = null,
    val scannedSerial: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val payloadNotes: String? = null,
    var syncStatus: String = "PENDING", // "PENDING", "APPLIED", "DUPLICATE", "REJECTED", "CONFLICT_QUEUED"
    var rejectionReason: String? = null,
    val deviceId: String = "DEV-RIDER-01",
    val actorRole: String = "Rider",
    val location: String = "On delivery route",
    val reasonCode: String? = null,
    val handoverPayload: Map<String, String>? = null
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
    val createdAt: Long = System.currentTimeMillis(),
    var assignedAt: Long? = null,
    var acceptedAt: Long? = null,
    var podReference: String? = null,
    // v0.4 properties
    val orderType: OrderType = OrderType.REFILL_EXCHANGE,
    var fulfilmentPointId: String? = "NODE-LHR-1",
    var pickupPointId: String? = null,
    val priceListVersion: Int = 1,
    val reference118PricePaisa: Long? = 445000L,
    val discountPaisa: Long = 0L,
    val gstPaisa: Long = taxPaisa,
    val furtherTaxPaisa: Long = 0L,
    val newCylinderModel: NewCylinderModel? = null,
    val handoverPin: String = "4821",
    var riderTaskStatus: RiderTaskStatus? = null,
    var installationResult: InstallationResult? = null,
    var installationNotDoneReason: String? = null,
    var emptyCollectedSkuId: String? = null,
    var emptyCollectedCylinderId: String? = null,
    var emptyCollectedBrand: String? = null,
    var amountReceivedPaisa: Long? = null,
    var changeReturnedPaisa: Long? = null,
    var cashDifferencePaisa: Long? = null,
    var cashDifferenceReason: String? = null,
    var fbrStatus: String? = "POSTED",
    var fbrInvoiceNo: String? = null,
    var ratingStars: Int? = null,
    var ratingComment: String? = null,
    var cancelReason: String? = null,
    var supersededByOrderId: String? = null,
    var supersedesOrderId: String? = null,
    var linkedClaimId: String? = null,
    var exceptionRetryCount: Int = 0,
    var confirmedAt: Long? = null,
    var completedAt: Long? = null,
    var promisedByAt: Long? = null,
    var adjustmentAccepted: Boolean? = null
)

/**
 * Format money in integer paisa to customer-facing whole rupees (BR-PRC-03).
 * Example: 414831L -> "Rs 4,148"
 */
fun formatRupees(paisa: Long): String {
    val rupees = Math.round(paisa / 100.0)
    return String.format(Locale.US, "Rs %,d", rupees)
}

/**
 * Format money in integer paisa with exact rupee and paisa decimals for Staff Finance.
 * Example: 414831L -> "Rs 4,148.31"
 */
fun formatPaisaToPkr(paisa: Long): String {
    val rupees = paisa / 100.0
    return String.format(Locale.US, "Rs %,.2f", rupees)
}

fun formatTimestamp(millis: Long): String {
    val sdf = SimpleDateFormat("dd MMM, HH:mm:ss", Locale.US)
    return sdf.format(Date(millis))
}
