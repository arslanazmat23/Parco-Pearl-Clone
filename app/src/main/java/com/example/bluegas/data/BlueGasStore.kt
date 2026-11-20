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

    // Overridable clock for deterministic delivery window, cutoff, and refill reminder tests
    internal var clock: () -> Long = { System.currentTimeMillis() }

    // Core Entity Collections
    val customers = mutableListOf<Customer>()
    val addresses = mutableListOf<Address>()
    val serviceZones = mutableListOf<ServiceZone>()
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
    val depositAccounts = mutableListOf<DepositAccount>()
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

    // Operations & Supply Chain v0.5 Collections
    val stockBalances = mutableListOf<StockBalance>()
    val stockTransfers = mutableListOf<StockTransfer>()
    val fillPlans = mutableListOf<FillPlan>()
    val plantReports = mutableListOf<PlantDailyReport>()
    val reconciliations = mutableListOf<DailyReconciliation>()
    val reconExceptions = mutableListOf<ReconciliationException>()
    val priceLists = mutableListOf<PriceList>(PriceList(version = 1))
    val currentPriceList: PriceList
        get() = priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    var draftPriceList: PriceList? = null
    val ograNotices = mutableListOf<OgraNotice>()
    var taxConfig = TaxConfig()
    val fbrPostings = mutableListOf<FbrPosting>()
    val cashHandovers = mutableListOf<CashHandover>()
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

    // Hoisted UI State
    var activeSurface = AppSurface.CUSTOMER
    var activeStaffSubRole = StaffSubRole.OPERATIONS
    val staffSectionByRole = mutableMapOf<StaffSubRole, String>()
    var staffDetailOrderId: String? = null
    var selectedHubId: String? = null
    var activeCustomerId = "CUST-001"
    var activeRiderId = ""
    var isRiderOffline = false
    var selectedOrderIdForDrawer: String? = null
    var lastRiderApplicationId: String? = null
    var outboxSequenceCounter = 1

    var customerTab: String = "HOME"
        private set
    var trackedOrderId: String? = null
        private set
    var riderTab: String = "TASKS"
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
            isCustomerSignedIn = isCustomerSignedIn,
            customers = customers.toList(),
            addresses = addresses.toList(),
            serviceZones = serviceZones.toList(),
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
            depositAccounts = depositAccounts.toList(),
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
            fillPlans = fillPlans.toList(),
            plantReports = plantReports.toList(),
            reconciliations = reconciliations.toList(),
            reconExceptions = reconExceptions.toList(),
            priceLists = priceLists.toList(),
            draftPriceList = draftPriceList,
            ograNotices = ograNotices.toList(),
            taxConfig = taxConfig,
            fbrPostings = fbrPostings.toList(),
            cashHandovers = cashHandovers.toList(),
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

    /**
     * Selects the active fulfilment hub in the Hub Staff workspace.
     *
     * @param pointId Identifier of the selected hub node.
     */
    fun setSelectedHub(pointId: String) {
        selectedHubId = pointId
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

    fun depositFor(customerId: String, serial: String): DepositAccount? =
        depositAccounts.firstOrNull { it.customerId == customerId && it.cylinderSerial == serial && it.status == DepositStatus.HELD }

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
        staffRole: StaffSubRole? = null,
        channels: List<NotifyChannel>? = null
    ) {
        val smsCodes = setOf("NTF-01", "NTF-04", "NTF-06", "NTF-13", "NTF-17", "NTF-19", "NTF-20", "NTF-21")
        val baseChannels = channels ?: if (surface == AppSurface.STAFF) {
            listOf(NotifyChannel.INTERNAL)
        } else {
            listOf(NotifyChannel.PUSH)
        }
        val resolvedChannels = if (surface == AppSurface.CUSTOMER && settings.smsEnabled && code in smsCodes && NotifyChannel.SMS !in baseChannels) {
            baseChannels + NotifyChannel.SMS
        } else {
            baseChannels
        }
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
                staffRole = staffRole,
                channels = resolvedChannels
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
        val now = clock()
        if (c.firstResponseAt == null) c.firstResponseAt = now
        c.status = "RESOLVED"
        c.resolutionNotes = notes
        c.resolvedAt = now
        logAudit("SupportCase", caseId, "RESOLVE_CASE", actor, AccountableRole.SUPPORT, notes)
        addNotification(AppSurface.STAFF, "Case Resolved", "Case $caseId resolved: $notes", "RESOLVED", c.orderId, staffRole = StaffSubRole.SUPPORT)
        notifyChanged()
        return Pair(true, "Case $caseId resolved successfully.")
    }

    fun raiseComplaint(orderId: String, issueType: String, description: String, actor: String = "Customer"): Pair<Boolean, String> =
        raiseComplaint(
            orderId = orderId,
            issueType = issueType,
            description = description,
            photoAttached = false,
            actor = actor
        )

    /**
     * Registers a formal customer complaint against an order with optional demo photo flag,
     * generating a customer-facing CMP-#### reference and sending NTF-17 (with SMS when enabled).
     *
     * @param orderId Target order identifier.
     * @param issueType Complaint category code (e.g. UNDERWEIGHT, LEAKING_VALVE, DELIVERY_DELAY).
     * @param description Customer description of the complaint.
     * @param photoAttached Whether a demo photo was attached by the customer.
     * @param actor Actor initiating the complaint record.
     * @return Pair of success flag and ticket/reference confirmation message.
     */
    fun raiseComplaint(
        orderId: String,
        issueType: String,
        description: String,
        photoAttached: Boolean,
        actor: String = "Customer"
    ): Pair<Boolean, String> {
        val ord = orders.find { it.id == orderId } ?: return Pair(false, "Order not found.")
        val custId = ord.customerId
        val existingRefs = supportCases.mapNotNull { it.reference }.toSet()
        var ref: String
        do {
            ref = "CMP-" + (1000..9999).random()
        } while (ref in existingRefs)

        val newCase = SupportCase(
            id = "CASE-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            customerId = custId,
            issueType = issueType,
            description = description,
            status = "OPEN",
            raisedAt = clock(),
            reference = ref,
            photoAttached = photoAttached
        )
        ord.complaintReference = ref
        supportCases.add(0, newCase)
        logAudit(
            entityType = "SupportCase",
            entityId = newCase.id,
            action = "RAISE_COMPLAINT",
            actor = actor,
            role = AccountableRole.SUPPORT,
            details = "Complaint $ref on order $orderId ($issueType, photo=$photoAttached): $description"
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "New Customer Complaint ($ref)",
            message = "Complaint $ref registered for ${orderLabel(orderId)}: $issueType",
            canonicalStatus = "OPEN",
            orderId = orderId,
            code = "NTF-17",
            staffRole = StaffSubRole.SUPPORT
        )
        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Complaint Received ($ref)",
            message = "Your complaint $ref for ${orderLabel(orderId)} has been received. Support will review it shortly.",
            canonicalStatus = "OPEN",
            orderId = orderId,
            code = "NTF-17"
        )
        notifyChanged()
        return Pair(true, "Complaint registered ($ref). Ticket: ${newCase.id}")
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
        depositAccounts.clear()
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
        fillPlans.clear()
        plantReports.clear()
        reconciliations.clear()
        reconExceptions.clear()

        // Reset commercial, finance, governance, and preview structures
        priceLists.clear()
        priceLists.add(PriceList(version = 1))
        draftPriceList = null
        ograNotices.clear()
        taxConfig = TaxConfig()
        fbrPostings.clear()
        cashHandovers.clear()
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
        lastRiderApplicationId = null

        seedAll()
        selectedHubId = nodes.firstOrNull { it.type == FulfilmentPointType.HUB }?.id
        notifyChanged()
    }
}
