package com.example.bluegas.data

import com.example.bluegas.model.*
import java.text.SimpleDateFormat
import java.util.*

/**
 * Seed Data for Blue Gas Prototype v0.4 (S3)
 */
internal fun BlueGasStore.seedAll() {
    val now = System.currentTimeMillis()

    // 1. Service Zones
    serviceZones.add(ServiceZone("ZONE-LHR-1", "Lahore", "Lahore - Gulberg & Cantt", true, listOf("54000"), listOf("NODE-LHR-1")))
    serviceZones.add(ServiceZone("ZONE-LHR-2", "Lahore", "Lahore - DHA & Phase V", true, listOf("54792"), listOf("NODE-LHR-1")))
    serviceZones.add(ServiceZone("ZONE-ISB-1", "Islamabad", "Islamabad - Blue Area & F-6/F-7", true, listOf("44000"), listOf("NODE-ISB-1")))

    // 2. Nodes
    nodes.add(Node("NODE-LHR-1", "DIST-001", "Gulberg Dispatch Terminal", "ZONE-LHR-1", 150, FulfilmentPointType.DISTRIBUTOR, "Main Gulberg III, Lahore", true, listOf("ZONE-LHR-1", "ZONE-LHR-2"), "Lahore"))
    nodes.add(Node("NODE-ISB-1", "DIST-002", "Blue Area Depot", "ZONE-ISB-1", 100, FulfilmentPointType.DISTRIBUTOR, "Blue Area, Sector F-6, Islamabad", true, listOf("ZONE-ISB-1"), "Islamabad"))
    nodes.add(Node("PLANT-01", "BLUE-GAS", "BLUE GAS Plant", "ZONE-LHR-1", 5000, FulfilmentPointType.PLANT, "Plant site (demo)", false, emptyList(), "Lahore"))

    // 3. Distributors
    distributors.add(Distributor("DIST-001", "Lahore Central LPG Gas Distribution", "Lahore", "Main Gulberg III, Lahore", "+92-300-1122334", "ACTIVE", listOf("ZONE-LHR-1", "ZONE-LHR-2"), "NODE-LHR-1"))
    distributors.add(Distributor("DIST-002", "Islamabad Capital Gas Partners", "Islamabad", "Blue Area, Sector F-6, Islamabad", "+92-300-5566778", "ACTIVE", listOf("ZONE-ISB-1"), "NODE-ISB-1"))

    // 4. Riders
    riders.add(Rider("RIDER-001", "Bilal Ahmed", "+92-321-4455667", "35201-1234567-1", "ZONE-LHR-1", "Gulberg & Cantt Central", "NODE-LHR-1", "LEN-4821", "ACTIVE", "APPROVED", "DIST-001", installationTrained = true))
    riders.add(Rider("RIDER-002", "Usman Tariq", "+92-322-8899001", "35201-7654321-3", "ZONE-LHR-1", "Gulberg & Cantt Central", "NODE-LHR-1", "LEA-9012", "ACTIVE", "APPROVED", "DIST-001", installationTrained = true))
    riders.add(Rider("RIDER-003", "Hamza Sheikh", "+92-333-1122334", "37405-3344556-5", "ZONE-ISB-1", "Islamabad Central", "NODE-ISB-1", "ICT-3312", "ACTIVE", "APPROVED", "DIST-002", installationTrained = true))

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

    // 7. Products
    productVariants.add(ProductVariant("PV-1", "SKU-LPG-11-8", "11.8kg Domestic Cylinder", "CYLINDER_REFILL", 11.8, true, "Standard domestic LPG cylinder", "BLUE GAS", true))
    productVariants.add(ProductVariant("PV-2", "SKU-LPG-6-0", "6.0kg Compact Cylinder", "CYLINDER_REFILL", 6.0, true, "Compact portable cylinder", "BLUE GAS", true))
    productVariants.add(ProductVariant("PV-3", "SKU-LPG-45-4", "45.4kg Commercial Cylinder", "CYLINDER_REFILL", 45.4, true, "Commercial catering cylinder", "BLUE GAS", false))
    productVariants.add(ProductVariant("PV-4", "SKU-REG-HOSE", "Regulator & Steel Hose", "ACCESSORY", null, false, "Regulator and steel hose", "BLUE GAS", true))
    productVariants.add(ProductVariant("PV-5", "SKU-LPG-11-0", "11 kg Domestic Cylinder", "CYLINDER_REFILL", 11.0, true, "Difference from 11.8 kg to be confirmed (Q23)", "BLUE GAS", true))

    // 8. Stock Balances (filled, empty, held, reserved, openingFilled, openingEmpty)
    stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-11-8", 12, 4, 0, 0, 12, 4))
    stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-11-0", 6, 2, 0, 0, 6, 2))
    stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-6-0", 5, 1, 0, 0, 5, 1))
    stockBalances.add(StockBalance("NODE-LHR-1", "SKU-LPG-45-4", 0, 0, 0, 0, 0, 0))

    stockBalances.add(StockBalance("NODE-ISB-1", "SKU-LPG-11-8", 6, 1, 0, 0, 6, 1))
    stockBalances.add(StockBalance("NODE-ISB-1", "SKU-LPG-11-0", 0, 0, 0, 0, 0, 0))
    stockBalances.add(StockBalance("NODE-ISB-1", "SKU-LPG-6-0", 4, 0, 0, 0, 4, 0))

    stockBalances.add(StockBalance("PLANT-01", "SKU-LPG-11-8", 40, 10, 0, 0, 40, 10))
    stockBalances.add(StockBalance("PLANT-01", "SKU-LPG-11-0", 30, 6, 0, 0, 30, 6))
    stockBalances.add(StockBalance("PLANT-01", "SKU-LPG-6-0", 20, 4, 0, 0, 20, 4))
    stockBalances.add(StockBalance("PLANT-01", "SKU-LPG-45-4", 8, 2, 0, 0, 8, 2))

    // 9. Cylinders
    // NODE-LHR-1
    cylinders.add(Cylinder("BG-LHR-001", "SKU-LPG-11-8", 11.8, InventoryState.INV_CONSUMED, CylinderCustody.CUSTOMER_PREMISES, "With Customer (CUST-001)"))
    cylinders.add(Cylinder("BG-LHR-002", "SKU-LPG-11-8", 11.8, InventoryState.INV_SERIAL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", allocatedOrderId = "ORD-SEED-105"))
    cylinders.add(Cylinder("BG-LHR-INC-001", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", hseHold = true, allocatedOrderId = "ORD-SEED-111"))
    cylinders.add(Cylinder("BG-LHR-003", "SKU-LPG-11-8", 11.8, InventoryState.INV_SERIAL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", allocatedOrderId = "ORD-SEED-112"))
    cylinders.add(Cylinder("BG-LHR-118-001", "SKU-LPG-11-8", 11.8, InventoryState.INV_SERIAL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", allocatedOrderId = "ORD-SEED-107"))
    cylinders.add(Cylinder("BG-LHR-118-002", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
    cylinders.add(Cylinder("BG-LHR-118-003", "SKU-LPG-11-8", 11.8, InventoryState.INV_SERIAL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", allocatedOrderId = "ORD-SEED-106"))
    cylinders.add(Cylinder("BG-LHR-110-001", "SKU-LPG-11-0", 11.0, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
    cylinders.add(Cylinder("BG-LHR-110-002", "SKU-LPG-11-0", 11.0, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
    cylinders.add(Cylinder("BG-LHR-060-001", "SKU-LPG-6-0", 6.0, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal"))
    // 2 untagged cylinders at NODE-LHR-1 (EXC-22)
    cylinders.add(Cylinder("BG-LHR-U01", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", isTagged = false))
    cylinders.add(Cylinder("BG-LHR-U02", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Gulberg Dispatch Terminal", isTagged = false))

    // NODE-ISB-1
    cylinders.add(Cylinder("BG-ISB-014", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Blue Area Depot"))
    cylinders.add(Cylinder("BG-ISB-118-001", "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Blue Area Depot"))
    cylinders.add(Cylinder("BG-ISB-060-001", "SKU-LPG-6-0", 6.0, InventoryState.INV_AVAIL, CylinderCustody.DISTRIBUTOR_NODE, "Blue Area Depot"))

    // PLANT-01
    for (i in 1..12) {
        val s = String.format("BG-PLT-118-%03d", i)
        cylinders.add(Cylinder(s, "SKU-LPG-11-8", 11.8, InventoryState.INV_AVAIL, CylinderCustody.PLANT, "Plant Site"))
    }
    for (i in 1..8) {
        val s = String.format("BG-PLT-110-%03d", i)
        cylinders.add(Cylinder(s, "SKU-LPG-11-0", 11.0, InventoryState.INV_AVAIL, CylinderCustody.PLANT, "Plant Site"))
    }

    // Customer holding for Ahmed Bilal
    customerHoldings.add(
        CustomerCylinderHolding(
            id = "HOLD-001",
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            ownership = CylinderOwnership.DEPOSIT_HELD,
            depositPaisa = 750000L,
            acquiredAt = now - 86400000L * 7L
        )
    )

    // 10. Partner Applications
    partnerApplications.add(
        PartnerApplication(
            id = "APP-001",
            type = "DISTRIBUTOR",
            applicantName = "Rawal Gas Agencies",
            city = "Islamabad",
            cnic = "37405-8899001-1",
            vehicleOrShopDetails = "Shop 14, Commercial Center, G-9",
            status = "PENDING_APPROVAL",
            phone = "+92 300 5544332",
            documents = listOf(
                PartnerDocument("CNIC Copy", true),
                PartnerDocument("Shop/premises licence", true),
                PartnerDocument("NTN Certificate", true),
                PartnerDocument("Storage licence", true),
                PartnerDocument("Bank details", true)
            )
        )
    )
    partnerApplications.add(
        PartnerApplication(
            id = "APP-002",
            type = "RIDER",
            applicantName = "Kamran Akhtar",
            city = "Lahore",
            cnic = "35201-6677889-3",
            vehicleOrShopDetails = "Honda CG 125, Plate LEA-5544",
            status = "PENDING_APPROVAL",
            phone = "+92 321 4433221",
            linkedDistributorId = "DIST-001",
            documents = listOf(
                PartnerDocument("CNIC Copy", true),
                PartnerDocument("Driving licence", true),
                PartnerDocument("Vehicle registration", true),
                PartnerDocument("Photo", true),
                PartnerDocument("Police character certificate", true)
            )
        )
    )
    partnerApplications.add(
        PartnerApplication(
            id = "APP-003",
            type = "DISTRIBUTOR",
            applicantName = "Shahid Traders",
            city = "Lahore",
            cnic = "35201-9988776-5",
            vehicleOrShopDetails = "Plot 24, Badami Bagh Industrial Area",
            status = "PENDING_APPROVAL",
            phone = "+92 301 7766554",
            documents = listOf(
                PartnerDocument("CNIC Copy", true),
                PartnerDocument("Shop/premises licence", false), // missing
                PartnerDocument("NTN Certificate", true),
                PartnerDocument("Storage licence", true),
                PartnerDocument("Bank details", true)
            )
        )
    )
    partnerApplications.add(
        PartnerApplication(
            id = "APP-004",
            type = "RIDER",
            applicantName = "Imran Ali",
            city = "Islamabad",
            cnic = "37405-1122334-7",
            vehicleOrShopDetails = "Yamaha YBR, Plate ICT-8812 (Driving licence expired)",
            status = "PENDING_APPROVAL",
            phone = "+92 333 9988776",
            linkedDistributorId = "DIST-002",
            documents = listOf(
                PartnerDocument("CNIC Copy", true),
                PartnerDocument("Driving licence", true),
                PartnerDocument("Vehicle registration", true),
                PartnerDocument("Photo", true),
                PartnerDocument("Police character certificate", true)
            )
        )
    )

    // 11. Seed Orders
    val p1 = currentPriceList
    val tax = taxConfig

    fun quote(sku: String, qty: Int, mode: FulfilmentMode, type: OrderType = OrderType.REFILL_EXCHANGE): QuoteResult {
        return PricingEngine.buildQuote(type, sku, qty, emptyList(), mode, p1, tax, settings)
    }

    // Helper to seed history
    fun seedOrderHistory(orderId: String, path: List<OrderStatus>, startMinsAgo: Int) {
        var t = now - (startMinsAgo * 60000L)
        path.zipWithNext().forEach { (from, to) ->
            orderStatusHistory.add(
                OrderStatusHistory(
                    id = "HIST-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    fromStatus = from,
                    toStatus = to,
                    trigger = to.displayName,
                    actor = when (to) {
                        OrderStatus.ORD_COMP, OrderStatus.ORD_ATHO, OrderStatus.ORD_DISP -> "Bilal Ahmed"
                        OrderStatus.ORD_ASGN, OrderStatus.ORD_PREP, OrderStatus.ORD_ACPT -> "Distributor Lead"
                        else -> "Customer"
                    },
                    role = when (to) {
                        OrderStatus.ORD_COMP, OrderStatus.ORD_ATHO, OrderStatus.ORD_DISP -> AccountableRole.DISTRIBUTOR
                        OrderStatus.ORD_ASGN, OrderStatus.ORD_PREP, OrderStatus.ORD_ACPT -> AccountableRole.DISTRIBUTOR
                        else -> AccountableRole.OPERATIONS
                    },
                    timestamp = t
                )
            )
            t += 180000L
        }
    }

    // ORD-SEED-101 (Completed)
    val q101 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o101 = Order(
        id = "ORD-SEED-101",
        customerId = "CUST-001",
        addressId = "ADDR-001",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_COMP,
        items = q101.lines,
        subtotalPaisa = q101.subtotalPaisa,
        depositTotalPaisa = q101.depositTotalPaisa,
        platformFeePaisa = q101.platformFeePaisa,
        deliveryFeePaisa = q101.deliveryFeePaisa,
        taxPaisa = q101.taxPaisa,
        totalAmountPaisa = q101.totalAmountPaisa,
        paymentMethod = PaymentMethod.ONLINE_HOSTED,
        allocatedSerial = "BG-LHR-001",
        assignedDistributorId = "DIST-001",
        assignedRiderId = "RIDER-001",
        podReference = "POD-SEED-981",
        fbrStatus = "POSTED",
        fbrInvoiceNo = "FBR-26-881294",
        confirmedAt = now - 7200000L,
        completedAt = now - 3600000L
    )
    orders.add(o101)
    payments.add(Payment("PAY-SEED-101", "ORD-SEED-101", PaymentMethod.ONLINE_HOSTED, PaymentState.SETTLED, o101.totalAmountPaisa, o101.totalAmountPaisa, o101.totalAmountPaisa, o101.totalAmountPaisa, "PKR", "IDEMP-PAY-101"))
    deliveries.add(Delivery("DEL-SEED-101", "ORD-SEED-101", "RIDER-001", completedAt = now - 3600000L, nonTechnicalInstallationPassed = true, podReference = "POD-SEED-981", customerSignatureCaptured = true, photoEvidenceCaptured = true, signerName = "Ahmed Bilal"))
    fbrPostings.add(FbrPosting("ORD-SEED-101", "POSTED", "FBR-26-881294"))
    seedOrderHistory("ORD-SEED-101", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_ATHO, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP), 120)

    // Open complaint and L6 case on 101
    supportCases.add(
        SupportCase(
            id = "CASE-SEED-01",
            orderId = "ORD-SEED-101",
            customerId = "CUST-001",
            issueType = "CYLINDER_CONDITION",
            description = "Cylinder had a dent on the collar",
            status = "OPEN",
            raisedAt = now - 1800000L
        )
    )
    val rrc1 = ReturnRefundCase(
        id = "RRC-SEED-01",
        orderId = "ORD-SEED-101",
        customerId = "CUST-001",
        type = "REFUND_ONLY",
        reason = "Charged delivery fee twice",
        requestedAmountPaisa = 30000L,
        status = L6Status.UNDER_REVIEW
    )
    rrc1.history.add(CaseHistory(L6Status.CASE_OPENED, L6Status.CASE_OPENED, "Customer Support", AccountableRole.SUPPORT, "Customer claimed double delivery fee", now - 3600000L))
    rrc1.history.add(CaseHistory(L6Status.CASE_OPENED, L6Status.UNDER_REVIEW, "Customer Support", AccountableRole.SUPPORT, "Reviewing payment receipts", now - 1800000L))
    returnCases.add(rrc1)

    // ORD-SEED-102 (Exception)
    val q102 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o102 = Order(
        id = "ORD-SEED-102",
        customerId = "CUST-002",
        addressId = "ADDR-003",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_EXC,
        items = q102.lines,
        subtotalPaisa = q102.subtotalPaisa,
        depositTotalPaisa = q102.depositTotalPaisa,
        platformFeePaisa = q102.platformFeePaisa,
        deliveryFeePaisa = q102.deliveryFeePaisa,
        taxPaisa = q102.taxPaisa,
        totalAmountPaisa = q102.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        assignedDistributorId = "DIST-001",
        exceptionReason = ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE,
        exceptionDetails = "Valve seal leak detected during pre-delivery inspection",
        confirmedAt = now - 5400000L
    )
    orders.add(o102)
    payments.add(Payment("PAY-SEED-102", "ORD-SEED-102", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o102.totalAmountPaisa, o102.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-102"))
    seedOrderHistory("ORD-SEED-102", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_EXC), 90)

    // ORD-SEED-103 (Confirmed - ready to reserve & assign)
    val q103 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o103 = Order(
        id = "ORD-SEED-103",
        customerId = "CUST-003",
        addressId = "ADDR-004",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_CONF,
        items = q103.lines,
        subtotalPaisa = q103.subtotalPaisa,
        depositTotalPaisa = q103.depositTotalPaisa,
        platformFeePaisa = q103.platformFeePaisa,
        deliveryFeePaisa = q103.deliveryFeePaisa,
        taxPaisa = q103.taxPaisa,
        totalAmountPaisa = q103.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        assignedDistributorId = "DIST-002",
        confirmedAt = now - 1800000L
    )
    orders.add(o103)
    payments.add(Payment("PAY-SEED-103", "ORD-SEED-103", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o103.totalAmountPaisa, o103.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-103"))
    seedOrderHistory("ORD-SEED-103", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF), 30)

    // ORD-SEED-104 (Payment Pending)
    val q104 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o104 = Order(
        id = "ORD-SEED-104",
        customerId = "CUST-001",
        addressId = "ADDR-001",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_PAYPEND,
        items = q104.lines,
        subtotalPaisa = q104.subtotalPaisa,
        depositTotalPaisa = q104.depositTotalPaisa,
        platformFeePaisa = q104.platformFeePaisa,
        deliveryFeePaisa = q104.deliveryFeePaisa,
        taxPaisa = q104.taxPaisa,
        totalAmountPaisa = q104.totalAmountPaisa,
        paymentMethod = PaymentMethod.ONLINE_HOSTED,
        holdExpiresAt = now + 600000L
    )
    orders.add(o104)
    payments.add(Payment("PAY-SEED-104", "ORD-SEED-104", PaymentMethod.ONLINE_HOSTED, PaymentState.PAYMENT_PENDING, o104.totalAmountPaisa, o104.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-104"))
    seedOrderHistory("ORD-SEED-104", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_PAYPEND), 10)

    // ORD-SEED-105 (Offered to Bilal Ahmed)
    val q105 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o105 = Order(
        id = "ORD-SEED-105",
        customerId = "CUST-001",
        addressId = "ADDR-001",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_PREP,
        items = q105.lines,
        subtotalPaisa = q105.subtotalPaisa,
        depositTotalPaisa = q105.depositTotalPaisa,
        platformFeePaisa = q105.platformFeePaisa,
        deliveryFeePaisa = q105.deliveryFeePaisa,
        taxPaisa = q105.taxPaisa,
        totalAmountPaisa = q105.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        allocatedSerial = "BG-LHR-002",
        assignedDistributorId = "DIST-001",
        assignedRiderId = "RIDER-001",
        riderTaskStatus = RiderTaskStatus.OFFERED,
        riderOfferedAt = now - 120000L,
        assignedAt = now - 900000L,
        acceptedAt = now - 600000L
    )
    orders.add(o105)
    payments.add(Payment("PAY-SEED-105", "ORD-SEED-105", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o105.totalAmountPaisa, o105.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-105"))
    seedOrderHistory("ORD-SEED-105", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP), 20)

    // ORD-SEED-106 (Accepted task by Bilal Ahmed)
    val q106 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o106 = Order(
        id = "ORD-SEED-106",
        customerId = "CUST-001",
        addressId = "ADDR-001",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_PREP,
        items = q106.lines,
        subtotalPaisa = q106.subtotalPaisa,
        depositTotalPaisa = q106.depositTotalPaisa,
        platformFeePaisa = q106.platformFeePaisa,
        deliveryFeePaisa = q106.deliveryFeePaisa,
        taxPaisa = q106.taxPaisa,
        totalAmountPaisa = q106.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        allocatedSerial = "BG-LHR-118-003",
        assignedDistributorId = "DIST-001",
        assignedRiderId = "RIDER-001",
        riderTaskStatus = RiderTaskStatus.ACCEPTED,
        riderCylinderVerified = false,
        assignedAt = now - 900000L,
        acceptedAt = now - 600000L
    )
    orders.add(o106)
    payments.add(Payment("PAY-SEED-106", "ORD-SEED-106", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o106.totalAmountPaisa, o106.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-106"))
    seedOrderHistory("ORD-SEED-106", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP), 15)

    // ORD-SEED-107 (Ready for pickup at counter)
    val q107 = quote("SKU-LPG-11-8", 1, FulfilmentMode.PICKUP)
    val o107 = Order(
        id = "ORD-SEED-107",
        customerId = "CUST-002",
        addressId = "ADDR-003",
        fulfilmentMode = FulfilmentMode.PICKUP,
        pickupPointId = "NODE-LHR-1",
        status = OrderStatus.ORD_RDYPU,
        items = q107.lines,
        subtotalPaisa = q107.subtotalPaisa,
        depositTotalPaisa = q107.depositTotalPaisa,
        platformFeePaisa = q107.platformFeePaisa,
        deliveryFeePaisa = q107.deliveryFeePaisa,
        taxPaisa = q107.taxPaisa,
        totalAmountPaisa = q107.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        assignedDistributorId = "DIST-001",
        allocatedSerial = "BG-LHR-118-001",
        handoverPin = "4821"
    )
    orders.add(o107)
    payments.add(Payment("PAY-SEED-107", "ORD-SEED-107", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o107.totalAmountPaisa, o107.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-107"))
    deliveries.add(Delivery("DEL-SEED-107", "ORD-SEED-107", "DIST-001"))
    seedOrderHistory("ORD-SEED-107", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_RDYPU), 40)

    // ORD-SEED-108 (Completed today, Cash Collected, not handed over)
    val q108 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o108 = Order(
        id = "ORD-SEED-108",
        customerId = "CUST-001",
        addressId = "ADDR-001",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_COMP,
        items = q108.lines,
        subtotalPaisa = q108.subtotalPaisa,
        depositTotalPaisa = q108.depositTotalPaisa,
        platformFeePaisa = q108.platformFeePaisa,
        deliveryFeePaisa = q108.deliveryFeePaisa,
        taxPaisa = q108.taxPaisa,
        totalAmountPaisa = q108.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        amountReceivedPaisa = q108.totalAmountPaisa,
        assignedDistributorId = "DIST-001",
        assignedRiderId = "RIDER-001",
        allocatedSerial = "BG-LHR-118-002",
        podReference = "POD-SEED-108",
        completedAt = now - 1800000L
    )
    orders.add(o108)
    payments.add(Payment("PAY-SEED-108", "ORD-SEED-108", PaymentMethod.CASH_ON_DELIVERY, PaymentState.CASH_COLLECTED, o108.totalAmountPaisa, o108.totalAmountPaisa, o108.totalAmountPaisa, 0L, "PKR", "IDEMP-PAY-108"))
    codCollections.add(CodCollection("COD-SEED-108", "ORD-SEED-108", "RIDER-001", o108.totalAmountPaisa, o108.totalAmountPaisa))
    deliveries.add(Delivery("DEL-SEED-108", "ORD-SEED-108", "RIDER-001", completedAt = now - 1800000L, nonTechnicalInstallationPassed = true, podReference = "POD-SEED-108", customerSignatureCaptured = true, photoEvidenceCaptured = true, signerName = "Ahmed Bilal"))
    seedOrderHistory("ORD-SEED-108", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_ATHO, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP), 60)

    // ORD-SEED-109 (Cancelled before dispatch, refund pending)
    val q109 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o109 = Order(
        id = "ORD-SEED-109",
        customerId = "CUST-003",
        addressId = "ADDR-004",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_CANC,
        items = q109.lines,
        subtotalPaisa = q109.subtotalPaisa,
        depositTotalPaisa = q109.depositTotalPaisa,
        platformFeePaisa = q109.platformFeePaisa,
        deliveryFeePaisa = q109.deliveryFeePaisa,
        taxPaisa = q109.taxPaisa,
        totalAmountPaisa = q109.totalAmountPaisa,
        paymentMethod = PaymentMethod.ONLINE_HOSTED,
        cancelReason = "Customer requested cancellation before dispatch",
        confirmedAt = now - 7200000L
    )
    orders.add(o109)
    val pay109 = Payment("PAY-SEED-109", "ORD-SEED-109", PaymentMethod.ONLINE_HOSTED, PaymentState.FULL_REFUND_PENDING, o109.totalAmountPaisa, o109.totalAmountPaisa, o109.totalAmountPaisa, 0L, "PKR", "IDEMP-PAY-109")
    payments.add(pay109)
    refunds.add(
        Refund(
            id = "REF-SEED-109",
            paymentId = pay109.id,
            orderId = "ORD-SEED-109",
            amountPaisa = o109.totalAmountPaisa,
            reason = "Cancellation refund: Customer requested cancellation before dispatch",
            executedBy = "System",
            state = PaymentState.FULL_REFUND_PENDING
        )
    )
    seedOrderHistory("ORD-SEED-109", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_CANC), 120)

    // ORD-SEED-110 (Completed, Settled, FBR QUEUED - EXC-20)
    val q110 = quote("SKU-REG-HOSE", 1, FulfilmentMode.DELIVERY, OrderType.ACCESSORY_ONLY)
    val o110 = Order(
        id = "ORD-SEED-110",
        customerId = "CUST-004",
        addressId = "ADDR-005",
        orderType = OrderType.ACCESSORY_ONLY,
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_COMP,
        items = q110.lines,
        subtotalPaisa = q110.subtotalPaisa,
        depositTotalPaisa = q110.depositTotalPaisa,
        platformFeePaisa = q110.platformFeePaisa,
        deliveryFeePaisa = q110.deliveryFeePaisa,
        taxPaisa = q110.taxPaisa,
        totalAmountPaisa = q110.totalAmountPaisa,
        paymentMethod = PaymentMethod.ONLINE_HOSTED,
        fbrStatus = "QUEUED",
        completedAt = now - 3600000L
    )
    orders.add(o110)
    payments.add(Payment("PAY-SEED-110", "ORD-SEED-110", PaymentMethod.ONLINE_HOSTED, PaymentState.SETTLED, o110.totalAmountPaisa, o110.totalAmountPaisa, o110.totalAmountPaisa, o110.totalAmountPaisa, "PKR", "IDEMP-PAY-110"))
    fbrPostings.add(FbrPosting("ORD-SEED-110", "QUEUED", attempts = 1))
    reconExceptions.add(
        ReconciliationException(
            id = "EXC20-110",
            code = "EXC-20",
            description = "FBR tax invoice posting queued for order ORD-SEED-110 (gateway timeout)",
            ownerRole = AccountableRole.FINANCE
        )
    )
    deliveries.add(Delivery("DEL-SEED-110", "ORD-SEED-110", "RIDER-003", completedAt = now - 3600000L, podReference = "POD-SEED-110"))
    seedOrderHistory("ORD-SEED-110", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_PAYPEND, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_ATHO, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP), 60)

    // ORD-SEED-111 (Exception: gas smell at handover, INC-0001 raised)
    val q111 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o111 = Order(
        id = "ORD-SEED-111",
        customerId = "CUST-004",
        addressId = "ADDR-005",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_EXC,
        items = q111.lines,
        subtotalPaisa = q111.subtotalPaisa,
        depositTotalPaisa = q111.depositTotalPaisa,
        platformFeePaisa = q111.platformFeePaisa,
        deliveryFeePaisa = q111.deliveryFeePaisa,
        taxPaisa = q111.taxPaisa,
        totalAmountPaisa = q111.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        assignedDistributorId = "DIST-001",
        assignedRiderId = "RIDER-001",
        allocatedSerial = "BG-LHR-INC-001",
        exceptionReason = ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
        exceptionDetails = "Rider stopped at handover — suspected gas smell"
    )
    orders.add(o111)
    payments.add(Payment("PAY-SEED-111", "ORD-SEED-111", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o111.totalAmountPaisa, o111.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-111"))
    seedOrderHistory("ORD-SEED-111", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_ATHO, OrderStatus.ORD_EXC), 45)

    val inc1 = SafetyIncident(
        id = "INC-0001",
        source = "DELIVERY",
        orderId = "ORD-SEED-111",
        cylinderId = "BG-LHR-INC-001",
        description = "Rider stopped at handover — suspected gas smell",
        location = "House 12, School Road, F-6",
        peopleInvolved = "Hamza Sheikh & Bilal Ahmed",
        evidenceCaptured = true,
        status = L7Status.INCIDENT_RAISED
    )
    inc1.history.add(IncidentHistory(L7Status.INCIDENT_RAISED, L7Status.INCIDENT_RAISED, "Bilal Ahmed", AccountableRole.HSE, "Reported odor during bubble leak test"))
    safetyIncidents.add(inc1)

    // ORD-SEED-112 (Preparing, new cylinder deposit, no rider yet)
    val q112 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY, OrderType.NEW_CYLINDER)
    val o112 = Order(
        id = "ORD-SEED-112",
        customerId = "CUST-002",
        addressId = "ADDR-003",
        orderType = OrderType.NEW_CYLINDER,
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_PREP,
        items = q112.lines,
        subtotalPaisa = q112.subtotalPaisa,
        depositTotalPaisa = q112.depositTotalPaisa,
        platformFeePaisa = q112.platformFeePaisa,
        deliveryFeePaisa = q112.deliveryFeePaisa,
        taxPaisa = q112.taxPaisa,
        totalAmountPaisa = q112.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        assignedDistributorId = "DIST-001",
        allocatedSerial = "BG-LHR-003"
    )
    orders.add(o112)
    payments.add(Payment("PAY-SEED-112", "ORD-SEED-112", PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, o112.totalAmountPaisa, o112.totalAmountPaisa, 0L, 0L, "PKR", "IDEMP-PAY-112"))
    seedOrderHistory("ORD-SEED-112", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP), 25)

    // ORD-SEED-113 (Completed yesterday, settled via CSH-SEED-01)
    val q113 = quote("SKU-LPG-11-8", 1, FulfilmentMode.DELIVERY)
    val o113 = Order(
        id = "ORD-SEED-113",
        customerId = "CUST-003",
        addressId = "ADDR-004",
        fulfilmentMode = FulfilmentMode.DELIVERY,
        status = OrderStatus.ORD_COMP,
        items = q113.lines,
        subtotalPaisa = q113.subtotalPaisa,
        depositTotalPaisa = q113.depositTotalPaisa,
        platformFeePaisa = q113.platformFeePaisa,
        deliveryFeePaisa = q113.deliveryFeePaisa,
        taxPaisa = q113.taxPaisa,
        totalAmountPaisa = q113.totalAmountPaisa,
        paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
        amountReceivedPaisa = q113.totalAmountPaisa,
        assignedDistributorId = "DIST-002",
        assignedRiderId = "RIDER-003",
        podReference = "POD-SEED-113",
        completedAt = now - 86400000L
    )
    orders.add(o113)
    payments.add(Payment("PAY-SEED-113", "ORD-SEED-113", PaymentMethod.CASH_ON_DELIVERY, PaymentState.SETTLED, o113.totalAmountPaisa, o113.totalAmountPaisa, o113.totalAmountPaisa, o113.totalAmountPaisa, "PKR", "IDEMP-PAY-113"))
    deliveries.add(Delivery("DEL-SEED-113", "ORD-SEED-113", "RIDER-003", completedAt = now - 86400000L, podReference = "POD-SEED-113"))
    seedOrderHistory("ORD-SEED-113", listOf(OrderStatus.ORD_DRAFT, OrderStatus.ORD_CONF, OrderStatus.ORD_ASGN, OrderStatus.ORD_ACPT, OrderStatus.ORD_PREP, OrderStatus.ORD_DISP, OrderStatus.ORD_ATHO, OrderStatus.ORD_HOC, OrderStatus.ORD_COMP), 1440)

    // Resolved complaint on 113
    supportCases.add(
        SupportCase(
            id = "CASE-SEED-02",
            orderId = "ORD-SEED-113",
            customerId = "CUST-003",
            issueType = "DELIVERY_DELAY",
            description = "Rider arrived 20 minutes past window",
            status = "RESOLVED",
            resolutionNotes = "Explained traffic block on Blue Area; credited Rs 150 goodwill voucher",
            raisedAt = now - 80000000L,
            resolvedAt = now - 75000000L
        )
    )

    // 12. Cash Handovers
    cashHandovers.add(
        CashHandover(
            id = "CSH-SEED-01",
            collectorType = "RIDER",
            collectorId = "RIDER-003",
            recipient = "FINANCE",
            orderIds = listOf("ORD-SEED-113"),
            expectedPaisa = o113.totalAmountPaisa,
            declaredPaisa = o113.totalAmountPaisa,
            countedPaisa = o113.totalAmountPaisa,
            differencePaisa = 0L,
            status = "RECONCILED",
            submittedAt = now - 80000000L,
            reconciledAt = now - 72000000L,
            reconciledBy = "Finance Lead"
        )
    )
    cashHandovers.add(
        CashHandover(
            id = "CSH-SEED-02",
            collectorType = "RIDER",
            collectorId = "RIDER-001",
            recipient = "FINANCE",
            orderIds = listOf("ORD-SEED-108"),
            expectedPaisa = o108.totalAmountPaisa,
            declaredPaisa = o108.totalAmountPaisa - 20000L, // declared Rs 200 less
            status = "SUBMITTED",
            submittedAt = now - 900000L
        )
    )

    // Ledger entries
    postLedger("ORD-SEED-101", "REVENUE_GAS", debitPaisa = o101.subtotalPaisa, description = "LPG refill gas revenue")
    postLedger("ORD-SEED-101", "TAX_PAYABLE", creditPaisa = o101.taxPaisa, description = "Sales tax payable")
    postLedger("ORD-SEED-101", "SETTLEMENT", debitPaisa = o101.totalAmountPaisa, description = "Online gateway settlement")
    postLedger("ORD-SEED-113", "REVENUE_GAS", debitPaisa = o113.subtotalPaisa, description = "LPG refill gas revenue")
    postLedger("ORD-SEED-113", "SETTLEMENT", debitPaisa = o113.totalAmountPaisa, description = "COD settled after cash reconciliation (CSH-SEED-01)")

    // Distributor Account Entries
    distributorAccountEntries.add(
        DistributorAccountEntry(
            id = "ENTRY-SEED-01",
            distributorId = "DIST-001",
            type = "STOCK_RECEIVED",
            transferId = "TRF-0001",
            debitPaisa = 4000000L, // 10 x Rs 4,000
            note = "Stock received transfer TRF-0001",
            timestamp = now - 86400000L * 2L
        )
    )
    distributorAccountEntries.add(
        DistributorAccountEntry(
            id = "ENTRY-SEED-02",
            distributorId = "DIST-001",
            type = "PAYMENT",
            creditPaisa = 2500000L,
            note = "Bank transfer ref HBL-9921",
            timestamp = now - 86400000L
        )
    )
    distributorAccountEntries.add(
        DistributorAccountEntry(
            id = "ENTRY-SEED-03",
            distributorId = "DIST-002",
            type = "STOCK_RECEIVED",
            transferId = "TRF-0005",
            debitPaisa = 2400000L,
            note = "Opening stock allocation",
            timestamp = now - 86400000L * 3L
        )
    )

    // 13. Stock Transfers
    stockTransfers.add(
        StockTransfer(
            id = "TRF-0001",
            direction = "PLANT_TO_POINT",
            fromPointId = "PLANT-01",
            toPointId = "NODE-LHR-1",
            status = "RECEIVED",
            gatePassNo = "GP-10411",
            vehicleNo = "LEA-2231",
            driverName = "Asif",
            driverPhone = "+92 321 0011223",
            lines = listOf(TransferLine("SKU-LPG-11-8", 10, 10)),
            requestedAt = now - 86400000L * 2L,
            dispatchedAt = now - 86400000L * 2L + 3600000L,
            receivedAt = now - 86400000L * 2L + 7200000L,
            tonnesEquivalent = 0.118
        )
    )
    stockTransfers.add(
        StockTransfer(
            id = "TRF-0002",
            direction = "PLANT_TO_POINT",
            fromPointId = "PLANT-01",
            toPointId = "NODE-LHR-1",
            status = "DISPATCHED",
            gatePassNo = "GP-10427",
            vehicleNo = "LEB-7719",
            driverName = "Naveed",
            driverPhone = "+92 322 9988112",
            lines = listOf(
                TransferLine("SKU-LPG-11-8", 8),
                TransferLine("SKU-LPG-11-0", 4)
            ),
            cylinderIds = listOf("BG-PLT-118-001", "BG-PLT-118-002", "BG-PLT-118-003", "BG-PLT-118-004", "BG-PLT-110-001", "BG-PLT-110-002"),
            dispatchedAt = now - 3600000L,
            tonnesEquivalent = (8 * 11.8 + 4 * 11.0) / 1000.0
        )
    )
    stockTransfers.add(
        StockTransfer(
            id = "TRF-0003",
            direction = "PLANT_TO_POINT",
            fromPointId = "PLANT-01",
            toPointId = "NODE-ISB-1",
            status = "REQUESTED",
            lines = listOf(
                TransferLine("SKU-LPG-11-8", 6),
                TransferLine("SKU-LPG-6-0", 4)
            ),
            requestedAt = now - 1800000L
        )
    )
    stockTransfers.add(
        StockTransfer(
            id = "TRF-0004",
            direction = "EMPTIES_TO_PLANT",
            fromPointId = "NODE-LHR-1",
            toPointId = "PLANT-01",
            status = "DISPATCHED",
            gatePassNo = "GP-E-2210",
            vehicleNo = "LEA-9012",
            driverName = "Usman Tariq",
            lines = listOf(TransferLine("SKU-LPG-11-8", 4)),
            dispatchedAt = now - 1800000L
        )
    )

    // Plant Daily Report 2026-10-13
    plantReports.add(
        PlantDailyReport(
            date = "2026-10-13",
            openingTonnes = 49.9690,
            purchasesTonnes = 0.0000,
            issuedTonnes = 2.1248,
            closingTonnes = 47.8442,
            cylindersIssuedBySku = mapOf(
                "SKU-LPG-11-0" to 116,
                "SKU-LPG-11-8" to 45,
                "SKU-LPG-45-4" to 7
            ),
            gatePassesToBlueGas = listOf("GP-10411", "GP-10427"),
            reference118Rate = 408200L,
            cashReceivedPaisa = 73501800L,
            source = "MANUAL"
        )
    )

    // Past reconciliation run
    reconciliations.add(
        DailyReconciliation(
            id = "RECON-SEED-01",
            date = "2026-10-17",
            runAt = now - 86400000L,
            runBy = "Inventory Controller",
            lines = listOf(
                ReconLine("1. Household Sales", "ALL", "3 orders", "3 orders", "0", "All reconciled"),
                ReconLine("2. Gate Pass Match", "GP-10411", "Gate Pass GP-10411", "In Plant Report", "Matched", "Verified"),
                ReconLine("3. Physical Stock Count", "NODE-LHR-1", "System Stock", "Counted Stock", "0", "All matched")
            ),
            status = "MATCHED"
        )
    )

    // 14. Open Decisions Q01-Q28
    val decisionTitles = mapOf(
        "Q01" to "Discount value for replacement cylinder claim",
        "Q02" to "What the replacement discount applies to",
        "Q03" to "New-cylinder model, deposit amounts and refundability",
        "Q04" to "Delivery, platform and campaign fees",
        "Q05" to "Switch & Save limits and budget",
        "Q06" to "Eligibility criteria for trade-in program",
        "Q07" to "HSE procedure and definition of unsafe",
        "Q08" to "Legal disposition and title of surrendered assets",
        "Q09" to "Evidence retention and access policy",
        "Q10" to "Cylinder identification (tag before launch or phase in)",
        "Q11" to "Fulfilment model (own staff, distributors, mix; plant as point)",
        "Q12" to "Can a rider be the authorized inspector",
        "Q13" to "Payment methods enabled for customers",
        "Q14" to "Service area and out-of-area handling",
        "Q15" to "Payment gateway and pre-authorization capabilities",
        "Q16" to "System of record, hosting and disaster recovery",
        "Q17" to "Offline conflict resolution rule",
        "Q18" to "Identity check at registration and partner documents",
        "Q19" to "Reporting frequency and regulatory reports",
        "Q20" to "Routine refill model (exchange vs filling)",
        "Q21" to "Plant system integration method",
        "Q22" to "Tax on household sales and FBR automated invoice",
        "Q23" to "Household sizes, 11 kg vs 11.8 kg distinction",
        "Q24" to "Household price calculation method and rounding",
        "Q25" to "Existing dealers as distributors, trade prices and credit",
        "Q26" to "Meaning of plant fields (CMR No., Hospitality weights, Filled By)",
        "Q27" to "Installation standard and rider safety training",
        "Q28" to "Cash handling and daily handover rules"
    )

    val blockingP1 = setOf("Q03", "Q10", "Q11", "Q15", "Q16", "Q21", "Q22", "Q23", "Q24", "Q27")
    val phase2Decisions = setOf("Q01", "Q02", "Q05", "Q06", "Q08", "Q12")

    for (i in 1..28) {
        val qId = String.format("Q%02d", i)
        openDecisions.add(
            OpenDecision(
                id = qId,
                title = decisionTitles[qId] ?: "Decision $qId",
                phase = if (phase2Decisions.contains(qId)) "Phase 2" else "Phase 1",
                blockingPhase1 = blockingP1.contains(qId),
                status = "OPEN"
            )
        )
    }

    // 15. Phase 2 Preview Seeds
    val claim1 = ReplacementClaim(
        id = "CLM-SEED-01",
        customerId = "CUST-001",
        declaredBrand = "Pak LPG",
        declaredSize = "11.8 kg",
        declaredSerial = "PAK-99120",
        declaredCondition = "USED_INTACT",
        status = ClaimStatus.EVIDENCE_SUBMITTED
    )
    claim1.history.add(ClaimHistory(ClaimStatus.DRAFT, ClaimStatus.EVIDENCE_SUBMITTED, "Ahmed Bilal", AccountableRole.COMMERCIAL, "Photo evidence and serial submitted"))
    replacementClaims.add(claim1)

    troubleshootingRequests.add(
        TroubleshootingRequest(
            id = "TRB-SEED-01",
            customerId = "CUST-003",
            orderId = "ORD-SEED-113",
            channel = "VISIT",
            description = "Stove flame sputtering after new cylinder connection",
            status = "REQUESTED"
        )
    )

    // 16. Staff Notifications (Badge = 1 for each of the 9 roles)
    addNotification(AppSurface.CUSTOMER, "Welcome to Blue Gas", "Your profile is active with verified delivery service in Gulberg.", "CONFIRMED", null)

    addNotification(AppSurface.STAFF, "Order in Fulfilment Exception", "1 order in Fulfilment Exception requires dispatch resolution.", "ORD_EXC", "ORD-SEED-102", code = "NTF-07", staffRole = StaffSubRole.OPERATIONS)
    addNotification(AppSurface.STAFF, "Transfer TRF-0002 In Transit", "Stock transfer TRF-0002 is on the way to Gulberg Terminal.", "DISPATCHED", null, code = "NTF-15", staffRole = StaffSubRole.DISTRIBUTOR)
    addNotification(AppSurface.STAFF, "Transfer Dispatch Pending", "Transfer TRF-0003 is waiting for plant dispatch.", "REQUESTED", null, code = "NTF-15", staffRole = StaffSubRole.PLANT_INVENTORY)
    addNotification(AppSurface.STAFF, "Cash Hand-over Pending", "Cash hand-over CSH-SEED-02 submitted by rider waiting for reconciliation.", "SUBMITTED", null, code = "NTF-15", staffRole = StaffSubRole.FINANCE)
    addNotification(AppSurface.STAFF, "Price List v1 Active", "Price list v1 is currently active for all pilot zones.", "PUBLISHED", null, code = "NTF-16", staffRole = StaffSubRole.COMMERCIAL)
    addNotification(AppSurface.STAFF, "New Customer Dispute", "New complaint CASE-SEED-01 registered on cylinder condition.", "OPEN", "ORD-SEED-101", code = "NTF-17", staffRole = StaffSubRole.SUPPORT)
    addNotification(AppSurface.STAFF, "Safety Alert Raised", "Incident INC-0001 raised on doorstep safety smell alert.", "INCIDENT_RAISED", "ORD-SEED-111", code = "NTF-09", staffRole = StaffSubRole.SAFETY_HSE)
    addNotification(AppSurface.STAFF, "10 Open Decisions Block Phase 1", "Regulatory and operational architecture decisions require client input.", "OPEN", null, code = "NTF-14", staffRole = StaffSubRole.ADMIN)
    addNotification(AppSurface.STAFF, "Phase 2 Claim Waiting", "Replacement claim CLM-SEED-01 submitted evidence for eligibility review.", "EVIDENCE_SUBMITTED", null, code = "NTF-10", staffRole = StaffSubRole.PHASE2_PREVIEW)
}
