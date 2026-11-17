package com.example.bluegas.model

/**
 * Operations, Supply Chain, Finance, Case and Phase 2 models for Blue Gas v0.5.
 */

// --- Stock & Transfer Models ---

data class StockBalance(
    val pointId: String,
    val skuId: String,
    var filledQty: Int,
    var emptyQty: Int,
    var heldQty: Int = 0,
    var reservedQty: Int = 0,
    val openingFilledQty: Int = filledQty,
    val openingEmptyQty: Int = emptyQty,
    var outWithRidersQty: Int = 0
) {
    val availableQty: Int get() = (filledQty - heldQty - reservedQty).coerceAtLeast(0)
}

data class TransferLine(
    val skuId: String,
    val qtySent: Int,
    var qtyReceived: Int? = null
)

data class StockTransfer(
    val id: String,
    val direction: String, // "PLANT_TO_POINT" or "EMPTIES_TO_PLANT"
    val fromPointId: String,
    val toPointId: String,
    var status: String, // "REQUESTED", "DISPATCHED", "RECEIVED", "RECEIVED_WITH_DIFFERENCE"
    var gatePassNo: String? = null,
    var vehicleNo: String? = null,
    var driverName: String? = null,
    var driverPhone: String? = null,
    val lines: List<TransferLine>,
    val cylinderIds: List<String> = emptyList(),
    val cmrNo: String? = null,
    val filledBy: String? = null,
    val requestedBy: String = "Hub Staff",
    val requestedAt: Long = System.currentTimeMillis(),
    var dispatchedAt: Long? = null,
    var receivedAt: Long? = null,
    val tonnesEquivalent: Double = 0.0
)

data class PlantDailyReport(
    val date: String,
    val openingTonnes: Double,
    val purchasesTonnes: Double,
    val issuedTonnes: Double,
    val closingTonnes: Double,
    val cylindersIssuedBySku: Map<String, Int> = emptyMap(),
    val gatePassesToBlueGas: List<String> = emptyList(),
    val reference118Rate: Long? = null,
    val cashReceivedPaisa: Long? = null,
    val source: String = "MANUAL" // "MANUAL" or "FILE_IMPORT"
)

data class ReconLine(
    val area: String,
    val skuId: String? = null,
    val expected: String,
    val actual: String,
    val difference: String,
    val note: String? = null
)

data class DailyReconciliation(
    val id: String,
    val date: String,
    val runAt: Long = System.currentTimeMillis(),
    val runBy: String = "Inventory Controller",
    val lines: List<ReconLine>,
    val status: String, // "MATCHED", "DIFFERENCES_FOUND"
    val exceptionIds: List<String> = emptyList()
)

data class ReconciliationException(
    val id: String,
    val code: String, // "EXC-18", "EXC-20", "EXC-21", "EXC-28"
    val description: String,
    val ownerRole: AccountableRole,
    var status: String = "OPEN", // "OPEN", "RESOLVED"
    var resolution: String? = null,
    val createdAt: Long = System.currentTimeMillis()
)

// --- Pricing & Commercial Models ---

data class PriceList(
    val version: Int,
    val method: PriceMethod = PriceMethod.REFERENCE_11_8_BY_WEIGHT,
    val reference118Paisa: Long = 445000L,
    val roundingRule: RoundingRule = RoundingRule.NEAREST,
    val perSizePaisa: Map<String, Long> = mapOf(
        "SKU-LPG-11-8" to 445000L,
        "SKU-LPG-45-4" to 1712100L
    ),
    val depositPaisa: Map<String, Long> = mapOf(
        "SKU-LPG-11-8" to 1000000L,
        "SKU-LPG-45-4" to 2750000L
    ),
    val accessoryPaisa: Map<String, Long> = mapOf(
        "SKU-REG-HOSE" to 145000L
    ),
    val deliveryFeePaisa: Long = 30000L,
    val platformFeePaisa: Long = 15000L,
    val effectiveFrom: Long = System.currentTimeMillis(),
    val publishedBy: String = "Commercial Manager",
    val ograNotificationRef: String? = "OGRA-LPG-OCT-2026 (demo)",
    val reviewedAt: Long? = System.currentTimeMillis()
)

data class OgraNotice(
    val id: String,
    val reference: String,
    val receivedAt: Long = System.currentTimeMillis(),
    var reviewed: Boolean = false,
    var reviewedAt: Long? = null
)

// --- Finance & Tax Models ---

data class TaxConfig(
    val gstBps: Int = 500, // 5.0%
    val furtherTaxBps: Int = 0,
    val furtherTaxApplies: Boolean = false,
    val fbrPostingEnabled: Boolean = true,
    val simulateFbrOutage: Boolean = false
)

data class FbrPosting(
    val orderId: String,
    var status: String, // "POSTED", "QUEUED", "FAILED", "NOT_REQUIRED"
    var invoiceNo: String? = null,
    var attempts: Int = 1,
    var lastAttemptAt: Long = System.currentTimeMillis()
)

data class CashHandover(
    val id: String,
    val collectorType: String, // "RIDER" or "DEPOT"
    val collectorId: String,
    val recipient: String, // "HUB" or "FINANCE"
    val orderIds: List<String>,
    val expectedPaisa: Long,
    val declaredPaisa: Long,
    var countedPaisa: Long? = null,
    var differencePaisa: Long? = null,
    var reason: String? = null,
    var approverName: String? = null,
    var status: String = "SUBMITTED", // "SUBMITTED", "RECEIVED_BY_HUB", "RECONCILED", "RECONCILED_WITH_DIFFERENCE"
    val submittedAt: Long = System.currentTimeMillis(),
    var hubReceivedPaisa: Long? = null,
    var hubReceivedBy: String? = null,
    var hubReceivedAt: Long? = null,
    var reconciledAt: Long? = null,
    var reconciledBy: String? = null
)

// --- Deposit Models ---

enum class DepositStatus(val label: String) {
    HELD("Held"),
    REFUND_IN_PROGRESS("Refund in progress"),
    REFUNDED("Refunded"),
    REDUCED_OR_FORFEITED("Reduced or forfeited")
}

data class DepositAccount(
    val id: String,                       // "DEP-" + 4 digits
    val customerId: String,
    val sku: String,
    var cylinderSerial: String?,          // moves to the new cylinder on empty exchange (BR-DEP-05)
    val amountPaisa: Long,
    val paidAt: Long,
    val orderId: String,
    val paymentId: String?,
    var status: DepositStatus = DepositStatus.HELD,
    var returnCaseId: String? = null,
    var refundId: String? = null,
    var deductionPaisa: Long = 0L,
    var deductionReason: String? = null,
    val noDepositOnRecord: Boolean = false // dealer-sourced cylinder (Q30); amountPaisa = 0
) {
    val skuId: String get() = sku
    val createdAt: Long get() = paidAt
}

// --- Lifecycle 6: Return/Refund Cases ---

enum class ReturnCondition(val label: String) {
    GOOD("Good condition"),
    DAMAGED("Damaged"),
    NOT_HANDED_OVER("Not handed over")
}

data class CaseHistory(
    val fromStatus: L6Status,
    val toStatus: L6Status,
    val actor: String,
    val role: AccountableRole,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReturnRefundCase(
    val id: String,
    val orderId: String,
    val customerId: String,
    val type: String, // "RETURN", "REFUND_ONLY", "CYLINDER_RETURN"
    val reason: String,
    val requestedAmountPaisa: Long,
    var status: L6Status = L6Status.CASE_OPENED,
    val history: MutableList<CaseHistory> = mutableListOf(),
    var linkedRefundId: String? = null,
    var cylinderSerial: String? = null,
    var depositId: String? = null,
    var conditionResult: ReturnCondition? = null,
    var conditionNote: String? = null,
    var hazardSuspected: Boolean = false,
    var deductionPaisa: Long = 0L,
    var deductionReason: String? = null,
    var rejectionReason: String? = null,
    var collectionRiderId: String? = null,
    var collectionTask: String? = null, // "OFFERED", "ACCEPTED", "DECLINED", "COMPLETED"
    val openedAt: Long = System.currentTimeMillis(),
    var closedAt: Long? = null
)

// --- Lifecycle 7: Safety (HSE) Incidents ---

data class IncidentHistory(
    val fromStatus: L7Status,
    val toStatus: L7Status,
    val actor: String,
    val role: AccountableRole,
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SafetyIncident(
    val id: String,
    val source: String, // "DELIVERY", "INSTALLATION", "CUSTOMER_REPORT", "DEPOT", "CLAIM", "TROUBLESHOOTING"
    val orderId: String? = null,
    val claimId: String? = null,
    val cylinderId: String? = null,
    val description: String,
    val location: String = "On site",
    val peopleInvolved: String = "Customer & Staff",
    val evidenceCaptured: Boolean = false,
    var status: L7Status = L7Status.INCIDENT_RAISED,
    var authorizedDecision: String? = null,
    var authorizedBy: String? = null,
    var cylinderOutcome: String? = null, // "CONDEMNED", "QUARANTINED", "RETURNED_TO_POOL", "NOT_APPLICABLE"
    val history: MutableList<IncidentHistory> = mutableListOf(),
    val raisedAt: Long = System.currentTimeMillis(),
    var closedAt: Long? = null
)

// --- Governance: Confirmed & Open Decisions ---

data class ConfirmedDecision(val id: String, val title: String)

val CONFIRMED_DECISIONS = listOf(
    ConfirmedDecision("D3", "All of Lahore and Islamabad served"),
    ConfirmedDecision("D4", "Households buy only 11.8 kg and 45.4 kg"),
    ConfirmedDecision("D5", "Plant system connects to the platform"),
    ConfirmedDecision("D6", "BLUE GAS sets its own household price"),
    ConfirmedDecision("D7", "Phase 1 uses BLUE GAS riders from BLUE GAS-run points"),
    ConfirmedDecision("D8", "Refundable security deposit, cylinder stays BLUE GAS property"),
    ConfirmedDecision("D9", "All BLUE GAS cylinders in the market belong to BLUE GAS")
)

data class OpenDecision(
    val id: String, // "Q01".."Q33"
    val title: String,
    val phase: String = "Phase 1",
    val blockingPhase1: Boolean = false,
    var status: String = "OPEN", // "OPEN", "DECIDED"
    var owner: String? = null,
    var targetDate: String? = null,
    var note: String? = null
)

// --- Platform Settings ---

data class PlatformSettings(
    val verificationLevel: String = "PHONE_OTP", // "NONE", "PHONE_OTP", "CNIC" (Q18)
    val enabledPaymentMethods: List<PaymentMethod> = listOf(PaymentMethod.CASH_ON_DELIVERY, PaymentMethod.ONLINE_HOSTED), // Q13
    val onlinePaymentMode: OnlinePaymentMode = OnlinePaymentMode.BRANCH_B_CAPTURE_UPFRONT, // Q15
    val refillModel: RefillModel = RefillModel.EMPTY_EXCHANGE, // Q20
    val cylinderIdMode: CylinderIdMode = CylinderIdMode.OPTION_A_TAGGED, // Q10
    val holdTtlMinutes: Int = 15,
    val hubAcceptanceWindowMins: Int = 20,
    val riderAcceptanceWindowMins: Int = 10, // Configurable demo default for rider task acceptance
    val autoRaiseNoAcceptance: Boolean = false,
    val promisedHandoverMinutes: Int = 120, // KPI-02 demo
    val installationChecklistItems: List<String> = listOf(
        "Connect the regulator",
        "Perform the approved leak check",
        "Demonstrate safe use to the customer"
    ), // Q27
    val cashHandoverHours: Int = 24, // Q28
    val evidenceRetentionDays: Int = 365, // Q09
    val approvedEmptyBrands: List<String> = listOf("BLUE GAS"), // BR-STK-07
    val supportPhone: String = "+92 42 111 000 000 (demo)",
    val phase2PreviewEnabled: Boolean = true,
    val maxQuantityPerOrder: Int = 5,
    // Phase 2 preview settings (Q01, Q02, Q05, Q06, Q12)
    val phase2DiscountType: String = "NONE", // "PERCENT", "FIXED", "NONE"
    val phase2DiscountValue: Long = 0L,
    val phase2DiscountAppliesTo: String = "NOT_DECIDED", // Q02
    val phase2EligibleBrands: List<String> = listOf("Pak LPG", "Burshane", "Fon Gas", "Shell Gas", "Other"),
    val phase2MaxClaimsPerCustomer: Int = 2,
    val phase2RiderMayInspect: Boolean = false, // Q12
    // To-Be options (Chunk 1)
    val dispatchMode: DispatchMode = DispatchMode.HUB_FIRST,                  // To-Be P3 [Recommended]
    val riderBatchMax: Int = 3,                                               // To-Be P3 [Recommended]
    val asapEnabled: Boolean = true,                                          // To-Be P3 step 4 [Recommended]
    val deliveryWindowsHours: List<Pair<Int, Int>> = listOf(10 to 12, 12 to 14, 14 to 16, 16 to 18, 18 to 20), // To-Be P3 [Recommended]
    val tomorrowWindowsEnabled: Boolean = true,                               // To-Be P3 [Recommended]
    val standardCutoffHour: Int = 17,                                         // delivery promise [To decide]
    val expressEnabled: Boolean = false,                                      // [To decide]
    val expressFeePaisa: Long = 20000L,                                       // [To decide]
    val expressHours: Int = 2,                                                // [To decide]
    val notHomeWaitMinutes: Int = 10,                                         // To-Be P5 step 2 [To decide]
    val freeRedeliveries: Int = 1,                                            // To-Be P5 step 2 [To decide]
    val cnicRequiredForDeposit: Boolean = true,                               // Q18 [Recommended]
    val backupCylinderAllowed: Boolean = false,                               // To-Be P4 [To decide]
    val maxDepositCylindersPerSize: Int = 2,                                  // used only when backupCylinderAllowed; otherwise the limit is 1
    val refillReminderEnabled: Boolean = true,                                // To-Be P3 [Recommended]
    val refillReminderLeadDays: Int = 3,                                      // To-Be P3 [Recommended]
    val smsEnabled: Boolean = true,                                           // demo: records "SMS sent"
    val hubMinimumFilled: Map<String, Map<String, Int>> = mapOf(              // To-Be P2 step 1 [levels to decide after pilot]
        "NODE-LHR-1" to mapOf("SKU-LPG-11-8" to 20, "SKU-LPG-45-4" to 5),
        "NODE-ISB-1" to mapOf("SKU-LPG-11-8" to 12, "SKU-LPG-45-4" to 3)
    ),
    val fillPlanHour: Int = 19,                                               // To-Be P1 step 1 [Recommended]
    val switchSaveRouteAEnabled: Boolean = true,                              // To-Be P9 [Recommended, new]
    val rivalReturnAgreements: List<String> = emptyList()                     // To-Be P9 Route B [To decide]
)

// --- Phase 2: Lifecycle 2 Replacement Claim & Assets ---

data class ClaimHistory(
    val fromStatus: ClaimStatus,
    val toStatus: ClaimStatus,
    val actor: String,
    val role: AccountableRole,
    val reason: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class ReplacementClaim(
    val id: String,
    val customerId: String,
    val declaredBrand: String,
    val declaredSize: String,
    val declaredSerial: String? = null,
    val declaredCondition: String = "USED_INTACT",
    val consentGiven: Boolean = true,
    val evidenceCaptured: Boolean = true,
    var status: ClaimStatus = ClaimStatus.DRAFT,
    var outcomeCode: String? = null,
    var closureReason: String? = null,
    var provisionalOfferPaisa: Long = 0L,
    var finalOfferPaisa: Long = 0L,
    var linkedOrderId: String? = null,
    var inspectorName: String? = null,
    var physicalResult: String? = null, // "ACCEPTED", "REJECTED", "EXPIRED_TEST_DATE"
    var hseResult: String? = null,      // "CLEARED", "UNSAFE"
    var testDatePolicyDecision: String? = null, // "ACCEPTED_UNDER_POLICY", "REJECTED_UNDER_POLICY"
    val history: MutableList<ClaimHistory> = mutableListOf(),
    val createdAt: Long = System.currentTimeMillis()
)

data class SurrenderHistory(
    val fromStatus: SurrenderStatus,
    val toStatus: SurrenderStatus,
    val actor: String,
    val notes: String,
    val timestamp: Long = System.currentTimeMillis()
)

data class SurrenderedCylinder(
    val id: String,
    val claimId: String,
    val brand: String,
    val serialText: String,
    var status: SurrenderStatus = SurrenderStatus.COLLECTED,
    var dispositionAuthorizedBy: String? = null,
    var dispositionType: String? = null,
    val history: MutableList<SurrenderHistory> = mutableListOf(),
    val collectedAt: Long = System.currentTimeMillis()
)

data class TroubleshootingRequest(
    val id: String,
    val customerId: String,
    val orderId: String? = null,
    val channel: String = "ONLINE", // "ONLINE", "VISIT"
    val description: String,
    var status: String = "REQUESTED", // "REQUESTED", "SCHEDULED", "IN_PROGRESS", "RESOLVED", "SAFETY_INCIDENT_OPENED"
    var technicianName: String? = null,
    var chargeOrderId: String? = null,
    val requestedAt: Long = System.currentTimeMillis()
)

data class EvidenceAccessLog(
    val id: String,
    val orderId: String,
    val evidenceType: String, // "PHOTO", "SIGNATURE"
    val accessedBy: String,
    val role: AccountableRole,
    val timestamp: Long = System.currentTimeMillis()
)
