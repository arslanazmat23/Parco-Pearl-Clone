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
    val currentPriceList: PriceList
        get() = priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    var draftPriceList: PriceList? = null
    val tradePriceLists = mutableListOf<TradePriceList>(TradePriceList(version = 1))
    val currentTradePriceList: TradePriceList
        get() = tradePriceLists.maxByOrNull { it.version } ?: TradePriceList(version = 1)
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

    // Hoisted UI State (WP0.6 & S0.6)
    var activeSurface = AppSurface.CUSTOMER
    var activeStaffSubRole = StaffSubRole.OPERATIONS
    val staffSectionByRole = mutableMapOf<StaffSubRole, String>()
    var staffDetailOrderId: String? = null
    var activeCustomerId = "CUST-001"
    var activeRiderId = ""
    var isRiderOffline = false
    var selectedOrderIdForDrawer: String? = null
    var outboxSequenceCounter = 1

    var customerTab: String = "HOME"
        private set
    var trackedOrderId: String? = null
        private set
    var riderTab: String = "TASKS"
        private set
    var selectedDistributorId: String = "DIST-001"
        private set
    var isCustomerSignedIn = true

    private val _appState = MutableStateFlow(buildSnapshot())
    val appState: StateFlow<SharedAppState> = _appState.asStateFlow()

    init {
        seedInitialData()
        activeRiderId = riders.firstOrNull()?.id ?: ""
        _appState.value = buildSnapshot()
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
            draftPriceList = draftPriceList,
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
            staffSectionByRole = staffSectionByRole.toMap(),
            staffDetailOrderId = staffDetailOrderId,
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

    fun setStaffRole(role: StaffSubRole) {
        activeStaffSubRole = role
        notifyChanged()
    }

    fun setStaffSection(role: StaffSubRole, section: String) {
        staffSectionByRole[role] = section
        notifyChanged()
    }

    fun openStaffOrderDetail(orderId: String?) {
        staffDetailOrderId = orderId
        notifyChanged()
    }

    fun setSurface(surface: AppSurface) {
        activeSurface = surface
        notifyChanged()
    }

    @JvmName("updateRiderOffline")
    fun setRiderOffline(offline: Boolean) {
        isRiderOffline = offline
        notifyChanged()
    }

    fun selectOrderForDevDrawer(orderId: String?) {
        selectedOrderIdForDrawer = orderId
        notifyChanged()
    }

    fun markNotificationsRead(surface: AppSurface, role: StaffSubRole? = null) {
        notifications.forEach { item ->
            if (item.surface == surface && (surface != AppSurface.STAFF || role == null || item.staffRole == null || item.staffRole == role)) {
                item.isRead = true
            }
        }
        notifyChanged()
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

    fun approvePartnerApplication(appId: String, actor: String = "Operations Lead"): Pair<Boolean, String> {
        val app = partnerApplications.find { it.id == appId } ?: return Pair(false, "Application $appId not found.")
        app.status = "APPROVED"
        app.decidedBy = actor
        app.decidedAt = System.currentTimeMillis()
        logAudit("PartnerApplication", appId, "APPROVE_PARTNER", actor, AccountableRole.OPERATIONS, "Approved application for ${app.applicantName}")
        addNotification(AppSurface.STAFF, "Partner Approved", "Application $appId for ${app.applicantName} approved.", "APPROVED", null, staffRole = StaffSubRole.OPERATIONS)
        notifyChanged()
        return Pair(true, "Application $appId approved successfully.")
    }

    fun rejectPartnerApplication(appId: String, reason: String, actor: String = "Operations Lead"): Pair<Boolean, String> {
        val app = partnerApplications.find { it.id == appId } ?: return Pair(false, "Application $appId not found.")
        app.status = "REJECTED"
        app.decisionReason = reason
        app.decidedBy = actor
        app.decidedAt = System.currentTimeMillis()
        logAudit("PartnerApplication", appId, "REJECT_PARTNER", actor, AccountableRole.OPERATIONS, "Rejected: $reason")
        addNotification(AppSurface.STAFF, "Partner Rejected", "Application $appId rejected: $reason", "REJECTED", null, staffRole = StaffSubRole.OPERATIONS)
        notifyChanged()
        return Pair(true, "Application $appId rejected.")
    }

    fun resolveSupportCase(caseId: String, notes: String, actor: String = "Support Lead"): Pair<Boolean, String> {
        val c = supportCases.find { it.id == caseId } ?: return Pair(false, "Case $caseId not found.")
        c.status = "RESOLVED"
        c.resolutionNotes = notes
        c.resolvedAt = System.currentTimeMillis()
        logAudit("SupportCase", caseId, "RESOLVE_CASE", actor, AccountableRole.SUPPORT, notes)
        addNotification(AppSurface.STAFF, "Case Resolved", "Case $caseId resolved: $notes", "RESOLVED", c.orderId, staffRole = StaffSubRole.SUPPORT)
        notifyChanged()
        return Pair(true, "Case $caseId resolved successfully.")
    }

    fun raiseComplaint(orderId: String, issueType: String, description: String, actor: String = "Customer"): Pair<Boolean, String> {
        val ord = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
        val custId = ord.customerId
        val newCase = SupportCase(
            id = "CASE-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            customerId = custId,
            issueType = issueType,
            description = description,
            status = "OPEN",
            raisedAt = System.currentTimeMillis()
        )
        supportCases.add(newCase)
        logAudit("SupportCase", newCase.id, "RAISE_COMPLAINT", actor, AccountableRole.SUPPORT, "Complaint on order $orderId: $description")
        addNotification(AppSurface.STAFF, "New Support Dispute", "Complaint registered for order $orderId: $issueType", "COMPLAINT", orderId, staffRole = StaffSubRole.SUPPORT)
        notifyChanged()
        return Pair(true, "Complaint registered. Ticket: ${newCase.id}")
    }

    fun executeRefund(orderId: String, amountPaisa: Long, reason: String, actor: String = "Finance Lead"): Pair<Boolean, String> {
        val payment = activePaymentFor(orderId)
        val payId = payment?.id ?: "PAY-REF-$orderId"
        val refId = "REF-" + UUID.randomUUID().toString().take(6)
        val ref = Refund(
            id = refId,
            paymentId = payId,
            orderId = orderId,
            amountPaisa = amountPaisa,
            reason = reason,
            executedBy = actor,
            state = PaymentState.FULL_REFUND_PENDING
        )
        refunds.add(ref)
        if (payment != null) {
            payment.state = PaymentState.FULL_REFUND_PENDING
        }
        return processRefund(refId, succeed = true, actor = actor)
    }

    fun seedInitialData() {
        customers.clear()
        addresses.clear()
        serviceZones.clear()
        distributors.clear()
        nodes.clear()
        riders.clear()
        productVariants.clear()
        orders.clear()
        orderStatusHistory.clear()
        orderAssignments.clear()
        deliveries.clear()
        stockHolds.clear()
        stockReservations.clear()
        cylinders.clear()
        cylinderCustodyEvents.clear()
        customerHoldings.clear()
        payments.clear()
        paymentHistory.clear()
        paymentAttempts.clear()
        codCollections.clear()
        refunds.clear()
        ledgerEntries.clear()
        supportCases.clear()
        partnerApplications.clear()
        notifications.clear()
        auditLogs.clear()
        evidenceAccessLogs.clear()
        stockBalances.clear()
        stockTransfers.clear()
        plantReports.clear()
        reconciliations.clear()
        reconExceptions.clear()

        // Reset commercial, finance, governance, and preview structures
        priceLists.clear()
        priceLists.add(PriceList(version = 1))
        draftPriceList = null
        tradePriceLists.clear()
        tradePriceLists.add(TradePriceList(version = 1))
        ograNotices.clear()
        taxConfig = TaxConfig()
        fbrPostings.clear()
        cashHandovers.clear()
        distributorAccountEntries.clear()
        returnCases.clear()
        safetyIncidents.clear()
        openDecisions.clear()
        settings = PlatformSettings()

        replacementClaims.clear()
        surrenderedCylinders.clear()
        troubleshootingRequests.clear()
        riderOutbox.clear()
        opsConflictQueue.clear()
        idempotencyKeys.clear()
        outboxSequenceCounter = 1

        // Reset hoisted staff UI state
        activeStaffSubRole = StaffSubRole.OPERATIONS
        staffSectionByRole.clear()
        staffDetailOrderId = null

        seedAll()
        notifyChanged()
    }
}
