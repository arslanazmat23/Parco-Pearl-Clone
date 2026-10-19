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
        val ord = orders.find { it.id == orderId }
        val custId = ord?.customerId ?: "CUST-001"
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
        val ref = Refund(
            id = "REF-" + UUID.randomUUID().toString().take(6),
            paymentId = payId,
            orderId = orderId,
            amountPaisa = amountPaisa,
            reason = reason,
            executedBy = actor,
            state = PaymentState.FULLY_REFUNDED
        )
        refunds.add(ref)
        payment?.state = PaymentState.FULLY_REFUNDED
        postLedger(orderId, "REFUND", debitPaisa = amountPaisa, description = "Refund executed: $reason")
        logAudit("Order", orderId, "REFUND_EXECUTED", actor, AccountableRole.FINANCE, "Refund of Rs. ${amountPaisa / 100} executed: $reason")
        notifyChanged()
        return Pair(true, "Refund of Rs. ${amountPaisa / 100} executed successfully.")
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
        replacementClaims.clear()
        surrenderedCylinders.clear()
        troubleshootingRequests.clear()
        riderOutbox.clear()
        opsConflictQueue.clear()

        // 1. Service Zones
        serviceZones.add(ServiceZone("ZONE-LHR-1", "Lahore", "Lahore - Gulberg & Cantt", true, listOf("54000"), listOf("NODE-LHR-1")))
        serviceZones.add(ServiceZone("ZONE-LHR-2", "Lahore", "Lahore - DHA & Phase V", true, listOf("54792"), listOf("NODE-LHR-1")))
        serviceZones.add(ServiceZone("ZONE-ISB-1", "Islamabad", "Islamabad - Blue Area & F-6/F-7", true, listOf("44000"), listOf("NODE-ISB-1")))

        // 2. Nodes
        nodes.add(Node("NODE-LHR-1", "DIST-001", "Gulberg Dispatch Terminal", "ZONE-LHR-1", 100, FulfilmentPointType.DISTRIBUTOR, "Main Gulberg III, Lahore", true, listOf("ZONE-LHR-1"), "Lahore"))
        nodes.add(Node("NODE-ISB-1", "DIST-002", "Blue Area Depot", "ZONE-ISB-1", 100, FulfilmentPointType.DISTRIBUTOR, "Blue Area, Sector F-6, Islamabad", true, listOf("ZONE-ISB-1"), "Islamabad"))

        // 3. Distributors
        distributors.add(Distributor("DIST-001", "Lahore Central LPG Gas Distribution", "Lahore", "Main Gulberg III, Lahore", "+92-300-1122334", "ACTIVE", listOf("ZONE-LHR-1"), "NODE-LHR-1"))
        distributors.add(Distributor("DIST-002", "Islamabad Capital Gas Partners", "Islamabad", "Blue Area, Sector F-6, Islamabad", "+92-300-5566778", "ACTIVE", listOf("ZONE-ISB-1"), "NODE-ISB-1"))

        // 4. Riders
        riders.add(Rider("RIDER-001", "Bilal Ahmed", "+92-321-4455667", "35201-1234567-1", "ZONE-LHR-1", "Gulberg & Cantt Central", "NODE-LHR-1", "LEN-4821", "ACTIVE", "APPROVED", "DIST-001"))
        riders.add(Rider("RIDER-002", "Usman Tariq", "+92-322-8899001", "35201-7654321-3", "ZONE-LHR-1", "Gulberg & Cantt Central", "NODE-LHR-1", "LEA-9012", "ACTIVE", "APPROVED", "DIST-001"))
        riders.add(Rider("RIDER-003", "Hamza Sheikh", "+92-333-1122334", "37405-3344556-5", "ZONE-ISB-1", "Islamabad Central", "NODE-ISB-1", "ICT-3312", "ACTIVE", "APPROVED", "DIST-002"))

        // 5. Customers
        customers.add(Customer("CUST-001", "Ahmed Bilal", "+92-300-8451234", "35201-1122334-1", "ADDR-001"))
        customers.add(Customer("CUST-002", "Usman Tariq", "+92-301-7654321", "35201-2233445-3", "ADDR-003"))
        customers.add(Customer("CUST-003", "Zainab Raza", "+92-321-9988776", "35201-3344556-5", "ADDR-004"))
        customers.add(Customer("CUST-004", "Hamza Sheikh", "+92-333-5544332", "37405-4455667-7", "ADDR-005"))

        // 6. Addresses
        addresses.add(Address("ADDR-001", "CUST-001", "Home (Gulberg III)", "House 14-B, Street 3", "Main Gulberg III", "Lahore", "ZONE-LHR-1", true, "NODE-LHR-1", true))
        addresses.add(Address("ADDR-002", "CUST-001", "Office (Cantt)", "Plot 88, Commercial Area", "Cantt", "Lahore", "ZONE-LHR-1", true, "NODE-LHR-1", false))
        addresses.add(Address("ADDR-003", "CUST-002", "Home (DHA)", "House 204, Sector C", "DHA Phase 5", "Lahore", "ZONE-LHR-2", true, "NODE-LHR-1", true))
        addresses.add(Address("ADDR-004", "CUST-003", "Apartment (Islamabad)", "Flat 4, Beverly Center", "Blue Area", "Islamabad", "ZONE-ISB-1", true, "NODE-ISB-1", true))
        addresses.add(Address("ADDR-005", "CUST-004", "Residence (F-6)", "House 12, School Road", "Sector F-6/2", "Islamabad", "ZONE-ISB-1", true, "NODE-ISB-1", true))
        addresses.add(Address("ADDR-006", "CUST-001", "Farmhouse (Unserviceable)", "Bedian Road Rural Sector", "Rural Outskirts", "Lahore", "ZONE-RURAL-OUTSIDE", false, null, false))

        // 7. Product Variants
        productVariants.add(ProductVariant("PV-1", "SKU-LPG-11-8", "11.8kg Domestic Cylinder", "CYLINDER_REFILL", 11.8, true, "Standard domestic LPG cylinder", "BLUE GAS", true))
        productVariants.add(ProductVariant("PV-2", "SKU-LPG-6-0", "6.0kg Compact Cylinder", "CYLINDER_REFILL", 6.0, true, "Compact portable cylinder", "BLUE GAS", true))
        productVariants.add(ProductVariant("PV-3", "SKU-LPG-45-4", "45.4kg Commercial Cylinder", "CYLINDER_REFILL", 45.4, true, "Commercial catering cylinder", "BLUE GAS", false))
        productVariants.add(ProductVariant("PV-4", "SKU-REG-HOSE", "Regulator & Steel Hose", "ACCESSORY", null, false, "OGRA certified safety regulator", "BLUE GAS", true))

        // 8. Stock Balances
        stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-11-8", 45, 20, 0, 0))
        stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-6-0", 25, 12, 0, 0))
        stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-45-4", 10, 5, 0, 0))
        stockBalances.add(StockBalance("NODE-ISB-1", "SKU-LPG-11-8", 30, 15, 0, 0))
        stockBalances.add(StockBalance("NODE-ISB-1", "SKU-LPG-6-0", 20, 8, 0, 0))

        // 9. Cylinders
        cylinders.add(Cylinder("BG-LHR-001", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
        cylinders.add(Cylinder("BG-LHR-002", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
        cylinders.add(Cylinder("BG-LHR-003", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
        cylinders.add(Cylinder("BG-ISB-014", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Blue Area Depot"))

        // 10. Partner Applications
        partnerApplications.add(PartnerApplication("PART-APP-001", "RIDER", "Muhammad Farooq", "Lahore", "35201-4455667-9", "Honda CD 70, Plate LEB-4011", "PENDING_APPROVAL"))
        partnerApplications.add(PartnerApplication("PART-APP-002", "DISTRIBUTOR", "Cantt Energy Gas Point", "Lahore", "35201-1122334-5", "Shop 12, Main Commercial Market Cantt", "PENDING_APPROVAL"))

        // 11. Seed Orders
        // ORD-SEED-101 (Completed)
        val o101 = Order(
            id = "ORD-SEED-101",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_COMP,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            allocatedSerial = "BG-LHR-001",
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001",
            podReference = "POD-SEED-981"
        )
        orders.add(o101)
        payments.add(Payment("PAY-SEED-101", "ORD-SEED-101", PaymentMethod.ONLINE_HOSTED, PaymentState.SETTLED, 388500L, 388500L, 388500L, 388500L, "PKR", "IDEMP-PAY-101"))
        deliveries.add(Delivery("DEL-SEED-101", "ORD-SEED-101", "RIDER-001", completedAt = System.currentTimeMillis() - 7200000L, nonTechnicalInstallationPassed = true, podReference = "POD-SEED-981", customerSignatureCaptured = true, photoEvidenceCaptured = true, signerName = "Ahmed Bilal"))

        // ORD-SEED-102 (Exception)
        val o102 = Order(
            id = "ORD-SEED-102",
            customerId = "CUST-002",
            addressId = "ADDR-003",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_EXC,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            assignedDistributorId = "DIST-001",
            exceptionReason = ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE
        )
        orders.add(o102)

        // ORD-SEED-103 (Confirmed)
        val o103 = Order(
            id = "ORD-SEED-103",
            customerId = "CUST-003",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_CONF,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            assignedDistributorId = "DIST-002"
        )
        orders.add(o103)

        // ORD-SEED-104 (Payment Pending)
        val o104 = Order(
            id = "ORD-SEED-104",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_PAYPEND,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        orders.add(o104)

        // ORD-SEED-105 (Assigned to Bilal Ahmed)
        val o105 = Order(
            id = "ORD-SEED-105",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_ASGN,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001"
        )
        orders.add(o105)
        payments.add(Payment("PAY-SEED-105", "ORD-SEED-105", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, 388500L, 388500L, 0L, 0L, "PKR", "IDEMP-PAY-105"))
        deliveries.add(Delivery("DEL-SEED-105", "ORD-SEED-105", "RIDER-001"))

        // ORD-SEED-106 (Accepted)
        val o106 = Order(
            id = "ORD-SEED-106",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_ACPT,
            items = listOf(OrderLine("SKU-LPG-11-8", "11.8kg Domestic Cylinder", 1, 325000L, 0L, 325000L, 0L)),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001"
        )
        orders.add(o106)
        payments.add(Payment("PAY-SEED-106", "ORD-SEED-106", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, 388500L, 388500L, 0L, 0L, "PKR", "IDEMP-PAY-106"))
        deliveries.add(Delivery("DEL-SEED-106", "ORD-SEED-106", "RIDER-001"))

        // Seed notifications
        addNotification(AppSurface.CUSTOMER, "Welcome to Blue Gas", "Your profile is active with verified delivery service in Gulberg.", "CONFIRMED", null)
        addNotification(AppSurface.STAFF, "Morning Shift Initialized", "All distribution hubs report normal opening cylinder stocks.", "SYSTEM_INFO", null, staffRole = StaffSubRole.OPERATIONS)

        notifyChanged()
    }
}
