package com.example.bluegas.data

import com.example.bluegas.model.*

/**
 * Immutable centralized snapshot representing the single source of truth for Blue Gas.
 * Shared across Customer App, Rider App, and Staff Portal.
 */
data class SharedAppState(
    val activeSurface: AppSurface = AppSurface.CUSTOMER,
    val activeStaffSubRole: StaffSubRole = StaffSubRole.OPERATIONS,
    val activeCustomerId: String = "CUST-001",
    val activeRiderId: String = "RIDER-001",
    val isRiderOffline: Boolean = false,
    val selectedOrderIdForDrawer: String? = null,

    // Entities
    val customers: List<Customer> = emptyList(),
    val addresses: List<Address> = emptyList(),
    val serviceZones: List<ServiceZone> = emptyList(),
    val distributors: List<Distributor> = emptyList(),
    val nodes: List<Node> = emptyList(),
    val riders: List<Rider> = emptyList(),
    val productVariants: List<ProductVariant> = emptyList(),
    val priceBookConfig: PriceBookConfig = PriceBookConfig(
        productPricesPaisa = mapOf(
            "SKU-LPG-11-8" to 325000L,
            "SKU-LPG-45-4" to 1240000L,
            "SKU-LPG-6-0"  to 185000L,
            "SKU-REG-HOSE" to 145000L
        ),
        depositAmountPaisa = mapOf(
            "SKU-LPG-11-8" to 750000L,
            "SKU-LPG-45-4" to 1800000L,
            "SKU-LPG-6-0"  to 450000L,
            "SKU-REG-HOSE" to 0L
        )
    ),
    val orders: List<Order> = emptyList(),
    val orderStatusHistory: List<OrderStatusHistory> = emptyList(),
    val orderAssignments: List<OrderAssignment> = emptyList(),
    val deliveries: List<Delivery> = emptyList(),
    val stockHolds: List<StockHold> = emptyList(),
    val stockReservations: List<StockReservation> = emptyList(),
    val cylinders: List<Cylinder> = emptyList(),
    val customerHoldings: List<CustomerCylinderHolding> = emptyList(),
    val payments: List<Payment> = emptyList(),
    val ledgerEntries: List<LedgerEntry> = emptyList(),
    val supportCases: List<SupportCase> = emptyList(),
    val partnerApplications: List<PartnerApplication> = emptyList(),
    val notifications: List<NotificationItem> = emptyList(),
    val auditLogs: List<AuditLog> = emptyList(),

    // Rider offline outbox & Ops conflict queue
    val riderOutbox: List<OfflineOutboxEvent> = emptyList(),
    val opsConflictQueue: List<SerialConflictItem> = emptyList()
) {
    val unreadNotificationCount: Int get() = notifications.count { !it.isRead }
    val selectedOrder: Order? get() = orders.find { it.id == selectedOrderIdForDrawer } ?: orders.firstOrNull()
    val activeCustomer: Customer? get() = customers.find { it.id == activeCustomerId } ?: customers.firstOrNull()
    val activeRider: Rider? get() = riders.find { it.id == activeRiderId } ?: riders.firstOrNull()
}
