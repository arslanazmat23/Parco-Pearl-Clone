package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class SerialConflictItem(
    val id: String,
    val serialNumber: String,
    val orderId1: String,
    val orderId2: String,
    val timestamp: Long = System.currentTimeMillis(),
    val reasonCode: String = "DUPLICATE_OFFLINE_SCAN_CONFLICT",
    var status: String = "PENDING_OPS_RESOLUTION"
)

class BlueGasStore {

    // Version counter for reliable reactivity
    var stateVersionCounter: Long = 0L

    // Core Entity Collections
    val customers = mutableListOf<Customer>()
    val addresses = mutableListOf<Address>()
    val serviceZones = mutableListOf<ServiceZone>()
    val distributors = mutableListOf<Distributor>()
    val nodes = mutableListOf<Node>()
    val riders = mutableListOf<Rider>()
    val productVariants = mutableListOf<ProductVariant>()
    var priceBookConfig = PriceBookConfig()

    val orders = mutableListOf<Order>()
    val orderStatusHistory = mutableListOf<OrderStatusHistory>()
    val orderAssignments = mutableListOf<OrderAssignment>()
    val deliveries = mutableListOf<Delivery>()
    val stockHolds = mutableListOf<StockHold>()
    val stockReservations = mutableListOf<StockReservation>()
    val cylinders = mutableListOf<Cylinder>()
    val cylinderCustodyEvents = mutableListOf<CylinderCustodyEvent>()
    val customerHoldings = mutableListOf<CustomerCylinderHolding>()
    val payments = mutableListOf<Payment>()
    val paymentHistory = mutableListOf<PaymentStatusHistory>()
    val paymentAttempts = mutableListOf<PaymentAttempt>()
    val codCollections = mutableListOf<CodCollection>()
    val refunds = mutableListOf<Refund>()
    val ledgerEntries = mutableListOf<LedgerEntry>()
    val supportCases = mutableListOf<SupportCase>()
    val partnerApplications = mutableListOf<PartnerApplication>()
    val notifications = mutableListOf<NotificationItem>()
    val auditLogs = mutableListOf<AuditLog>()
    val evidenceAccessLogs = mutableListOf<EvidenceAccessLog>()
    val idempotencyKeys = mutableSetOf<String>()

    // Operations & Supply Chain v0.4 Collections
    val stockBalances = mutableListOf<StockBalance>()
    val stockTransfers = mutableListOf<StockTransfer>()
    val plantReports = mutableListOf<PlantDailyReport>()
    val reconciliations = mutableListOf<DailyReconciliation>()
    val reconExceptions = mutableListOf<ReconciliationException>()
    val priceLists = mutableListOf<PriceList>(PriceList(version = 1))
    val tradePriceLists = mutableListOf<TradePriceList>(TradePriceList(version = 1))
    val ograNotices = mutableListOf<OgraNotice>()
    var taxConfig = TaxConfig()
    val fbrPostings = mutableListOf<FbrPosting>()
    val cashHandovers = mutableListOf<CashHandover>()
    val distributorAccountEntries = mutableListOf<DistributorAccountEntry>()
    val returnCases = mutableListOf<ReturnRefundCase>()
    val safetyIncidents = mutableListOf<SafetyIncident>()
    val openDecisions = mutableListOf<OpenDecision>()
    var settings = PlatformSettings()

    // Phase 2 Preview Collections
    val replacementClaims = mutableListOf<ReplacementClaim>()
    val surrenderedCylinders = mutableListOf<SurrenderedCylinder>()
    val troubleshootingRequests = mutableListOf<TroubleshootingRequest>()

    // Rider offline outbox & Ops conflict queue
    val riderOutbox = mutableListOf<OfflineOutboxEvent>()
    val opsConflictQueue = mutableListOf<SerialConflictItem>()

    // Hoisted UI State (WP0.6)
    var activeSurface = AppSurface.CUSTOMER
    var activeStaffSubRole = StaffSubRole.OPERATIONS
    var activeCustomerId = "CUST-001"
    var activeRiderId = "RIDER-001"
    var isRiderOffline = false
    var selectedOrderIdForDrawer: String? = null
    var outboxSequenceCounter = 1

    var customerTab = "HOME"
    var trackedOrderId: String? = null
    var riderTab = "TASKS"
    var selectedDistributorId = "DIST-001"
    var isCustomerSignedIn = true

    private val _appState = MutableStateFlow(buildSnapshot())
    val appState: StateFlow<SharedAppState> = _appState.asStateFlow()

    init {
        seedInitialData()
    }

    fun buildSnapshot(): SharedAppState {
        return SharedAppState(
            version = stateVersionCounter,
            activeSurface = activeSurface,
            activeStaffSubRole = activeStaffSubRole,
            activeCustomerId = activeCustomerId,
            activeRiderId = activeRiderId,
            isRiderOffline = isRiderOffline,
            selectedOrderIdForDrawer = selectedOrderIdForDrawer,
            customerTab = customerTab,
            trackedOrderId = trackedOrderId,
            riderTab = riderTab,
            selectedDistributorId = selectedDistributorId,
            isCustomerSignedIn = isCustomerSignedIn,
            customers = customers.toList(),
            addresses = addresses.toList(),
            serviceZones = serviceZones.toList(),
            distributors = distributors.toList(),
            nodes = nodes.toList(),
            riders = riders.toList(),
            productVariants = productVariants.toList(),
            priceBookConfig = priceBookConfig,
            orders = orders.toList(),
            orderStatusHistory = orderStatusHistory.toList(),
            orderAssignments = orderAssignments.toList(),
            deliveries = deliveries.toList(),
            stockHolds = stockHolds.toList(),
            stockReservations = stockReservations.toList(),
            cylinders = cylinders.toList(),
            cylinderCustodyEvents = cylinderCustodyEvents.toList(),
            customerHoldings = customerHoldings.toList(),
            payments = payments.toList(),
            paymentHistory = paymentHistory.toList(),
            paymentAttempts = paymentAttempts.toList(),
            codCollections = codCollections.toList(),
            refunds = refunds.toList(),
            ledgerEntries = ledgerEntries.toList(),
            supportCases = supportCases.toList(),
            partnerApplications = partnerApplications.toList(),
            notifications = notifications.toList(),
            auditLogs = auditLogs.toList(),
            evidenceAccessLogs = evidenceAccessLogs.toList(),
            stockBalances = stockBalances.toList(),
            stockTransfers = stockTransfers.toList(),
            plantReports = plantReports.toList(),
            reconciliations = reconciliations.toList(),
            reconExceptions = reconExceptions.toList(),
            priceLists = priceLists.toList(),
            tradePriceLists = tradePriceLists.toList(),
            ograNotices = ograNotices.toList(),
            taxConfig = taxConfig,
            fbrPostings = fbrPostings.toList(),
            cashHandovers = cashHandovers.toList(),
            distributorAccountEntries = distributorAccountEntries.toList(),
            returnCases = returnCases.toList(),
            safetyIncidents = safetyIncidents.toList(),
            openDecisions = openDecisions.toList(),
            settings = settings,
            replacementClaims = replacementClaims.toList(),
            surrenderedCylinders = surrenderedCylinders.toList(),
            troubleshootingRequests = troubleshootingRequests.toList(),
            riderOutbox = riderOutbox.toList(),
            opsConflictQueue = opsConflictQueue.toList()
        )
    }

    fun notifyChanged() {
        stateVersionCounter++
        _appState.value = buildSnapshot()
    }

    // --- Internal Helpers ---

    internal fun logAudit(
        entityType: String,
        entityId: String,
        action: String,
        actor: String,
        role: AccountableRole,
        details: String
    ) {
        auditLogs.add(
            AuditLog(
                id = "AUD-" + UUID.randomUUID().toString().take(8),
                entityType = entityType,
                entityId = entityId,
                action = action,
                actor = actor,
                role = role,
                details = details,
                timestamp = System.currentTimeMillis()
            )
        )
    }

    internal fun addNotification(
        surface: AppSurface,
        title: String,
        message: String,
        canonicalStatus: String,
        orderId: String?,
        code: String = "NTF-01",
        staffRole: StaffSubRole? = null
    ) {
        notifications.add(
            0,
            NotificationItem(
                id = "NOTIF-" + UUID.randomUUID().toString().take(6),
                surface = surface,
                title = title,
                message = message,
                canonicalStatus = canonicalStatus,
                orderId = orderId,
                timestamp = System.currentTimeMillis(),
                code = code,
                staffRole = staffRole
            )
        )
    }

    internal fun recordHistory(result: TransitionResult, reasonCode: String? = null) {
        if (result is TransitionResult.Success) {
            val hist = OrderStatusHistory(
                id = "HIST-" + UUID.randomUUID().toString().take(8),
                orderId = result.order.id,
                fromStatus = result.fromStatus,
                toStatus = result.toStatus,
                trigger = result.order.status.displayName,
                actor = result.actor,
                role = result.role,
                reasonCode = reasonCode
            )
            orderStatusHistory.add(hist)
            logAudit("Order", result.order.id, "STATUS_TRANSITION", result.actor, result.role, "${result.fromStatus.canonicalLabel} -> ${result.toStatus.canonicalLabel}")
        }
    }

    internal fun postLedger(
        orderId: String?,
        entryType: String,
        debitPaisa: Long = 0L,
        creditPaisa: Long = 0L,
        description: String
    ) {
        ledgerEntries.add(
            LedgerEntry(
                id = "LED-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                entryType = entryType,
                debitPaisa = debitPaisa,
                creditPaisa = creditPaisa,
                description = description
            )
        )
    }

    fun activePaymentFor(orderId: String): Payment? {
        return payments.filter { it.orderId == orderId && it.state != PaymentState.PAYMENT_FAILED_CLOSED }
            .maxByOrNull { it.attemptNo }
    }

    fun getActiveRider(): Rider? {
        return riders.find { it.id == activeRiderId } ?: riders.firstOrNull()
    }

    fun switchRider(riderId: String) {
        activeRiderId = riderId
        notifyChanged()
    }

    fun setCustomerTab(tab: String) {
        customerTab = tab
        notifyChanged()
    }

    fun setTrackedOrderId(orderId: String?) {
        trackedOrderId = orderId
        notifyChanged()
    }

    fun setRiderTab(tab: String) {
        riderTab = tab
        notifyChanged()
    }

    fun setSelectedDistributorId(id: String) {
        selectedDistributorId = id
        notifyChanged()
    }

    fun logEvidenceAccess(orderId: String, evidenceType: String, staffName: String, role: AccountableRole) {
        evidenceAccessLogs.add(
            EvidenceAccessLog(
                id = "EVACC-" + UUID.randomUUID().toString().take(6),
                orderId = orderId,
                evidenceType = evidenceType,
                accessedBy = staffName,
                role = role
            )
        )
        logAudit("Order", orderId, "EVIDENCE_ACCESS", staffName, role, "Viewed $evidenceType evidence")
        notifyChanged()
    }
}
