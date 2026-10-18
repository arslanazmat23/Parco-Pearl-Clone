package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Immutable centralized snapshot representing the single source of truth for Blue Gas v0.4.
 * Shared across Customer App, Rider App, and Staff Portal.
 */
data class SharedAppState(
    val version: Long = 0L,
    val activeSurface: AppSurface = AppSurface.CUSTOMER,
    val activeStaffSubRole: StaffSubRole = StaffSubRole.OPERATIONS,
    val activeCustomerId: String = "CUST-001",
    val activeRiderId: String = "RIDER-001",
    val isRiderOffline: Boolean = false,
    val selectedOrderIdForDrawer: String? = null,

    // Hoisted UI state (WP0.6)
    val customerTab: String = "HOME",
    val trackedOrderId: String? = null,
    val riderTab: String = "TASKS",
    val selectedDistributorId: String = "DIST-001",
    val isCustomerSignedIn: Boolean = true,

    // Entities
    val customers: List<Customer> = emptyList(),
    val addresses: List<Address> = emptyList(),
    val serviceZones: List<ServiceZone> = emptyList(),
    val distributors: List<Distributor> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val riders: List<Rider> = emptyList(),
    val productVariants: List<ProductVariant> = emptyList(),
    val priceBookConfig: PriceBookConfig = PriceBookConfig(),
    val orders: List<Order> = emptyList(),
    val orderStatusHistory: List<OrderStatusHistory> = emptyList(),
    val orderAssignments: List<OrderAssignment> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val stockHolds: List<StockHold> = emptyList(),
    val stockReservations: List<StockReservation> = emptyList(),
    val cylinders: List<Cylinder> = emptyList(),
    val cylinderCustodyEvents: List<CylinderCustodyEvent> = emptyList(),
    val customerHoldings: List<CustomerCylinderHolding> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val paymentHistory: List<PaymentStatusHistory> = emptyList(),
    val paymentAttempts: List<PaymentAttempt> = emptyList(),
    val codCollections: List<CodCollection> = emptyList(),
    val refunds: List<Refund> = emptyList(),
    val ledgerEntries: List<LedgerEntry> = emptyList(),
    val supportCases: List<SupportCase> = emptyList(),
    val partnerApplications: List<PartnerApplication> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),
    val evidenceAccessLogs: List<EvidenceAccessLog> = emptyList(),

    // Operations, Supply Chain & Finance (v0.4)
    val stockBalances: List<StockBalance> = emptyList(),
    val stockTransfers: List<StockTransfer> = emptyList(),
    val plantReports: List<PlantDailyReport> = emptyList(),
    val reconciliations: List<DailyReconciliation> = emptyList(),
    val reconExceptions: List<ReconciliationException> = emptyList(),
    val priceLists: List<PriceList> = listOf(PriceList(version = 1)),
    val tradePriceLists: List<TradePriceList> = listOf(TradePriceList(version = 1)),
    val ograNotices: List<OgraNotice> = emptyList(),
    val taxConfig: TaxConfig = TaxConfig(),
    val fbrPostings: List<FbrPosting> = emptyList(),
    val cashHandovers: List<CashHandover> = emptyList(),
    val distributorAccountEntries: List<DistributorAccountEntry> = emptyList(),
    val returnCases: List<ReturnRefundCase> = emptyList(),
    val safetyIncidents: List<SafetyIncident> = emptyList(),
    val openDecisions: List<OpenDecision> = emptyList(),
    val settings: PlatformSettings = PlatformSettings(),

    // Phase 2 Preview Records
    val replacementClaims: List<ReplacementClaim> = emptyList(),
    val surrenderedCylinders: List<SurrenderedCylinder> = emptyList(),
    val troubleshootingRequests: List<TroubleshootingRequest> = emptyList(),

    // Rider offline outbox & Ops conflict queue
    val riderOutbox: List<OfflineOutboxEvent> = emptyList(),
    val opsConflictQueue: List<SerialConflictItem> = emptyList()
) {
    val currentPriceList: PriceList get() = priceLists.maxByOrNull { it.version } ?: PriceList(version = 1)
    val currentTradePriceList: TradePriceList get() = tradePriceLists.maxByOrNull { it.version } ?: TradePriceList(version = 1)
    val unreadNotificationCount: Int get() = notifications.count { !it.isRead }
    val selectedOrder: Order? get() = orders.find { it.id == selectedOrderIdForDrawer } ?: orders.firstOrNull()
    val activeCustomer: Customer? get() = customers.find { it.id == activeCustomerId } ?: customers.firstOrNull()
    val activeRider: Rider? get() = riders.find { it.id == activeRiderId } ?: riders.firstOrNull()

    fun unreadFor(surface: AppSurface, role: StaffSubRole? = null): Int {
        return notifications.count { item ->
            !item.isRead && item.surface == surface && (surface != AppSurface.STAFF || role == null || item.staffRole == null || item.staffRole == role)
        }
    }
}
