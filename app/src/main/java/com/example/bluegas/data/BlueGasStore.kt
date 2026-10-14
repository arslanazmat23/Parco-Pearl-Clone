package com.example.bluegas.data

import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.PaymentResult
import com.example.bluegas.lifecycle.PaymentStateMachine
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
    var status: String = "PENDING_OPS_RESOLUTION" // "PENDING_OPS_RESOLUTION", "RESOLVED"
)

class BlueGasStore {

    // Primary observable state
    val customers = mutableListOf<Customer>()
    val addresses = mutableListOf<Address>()
    val serviceZones = mutableListOf<ServiceZone>()
    val distributors = mutableListOf<Distributor>()
    val nodes = mutableListOf<Node>()
    val riders = mutableListOf<Rider>()
    val productVariants = mutableListOf<ProductVariant>()
    var priceBookConfig = PriceBookConfig(
        productPricesPaisa = mapOf(
            "SKU-LPG-11-8" to 325000L, // 3,250 PKR
            "SKU-LPG-45-4" to 1240000L, // 12,400 PKR
            "SKU-LPG-6-0"  to 185000L,  // 1,850 PKR
            "SKU-REG-HOSE" to 145000L   // 1,450 PKR
        ),
        depositAmountPaisa = mapOf(
            "SKU-LPG-11-8" to 750000L, // 7,500 PKR
            "SKU-LPG-45-4" to 1800000L, // 18,000 PKR
            "SKU-LPG-6-0"  to 450000L,  // 4,500 PKR
            "SKU-REG-HOSE" to 0L
        )
    )

    val orders = mutableListOf<Order>()
    val orderStatusHistory = mutableListOf<OrderStatusHistory>()
    val orderAssignments = mutableListOf<OrderAssignment>()
    val deliveries = mutableListOf<Delivery>()
    val stockBalances = mutableListOf<StockBalance>()
    val stockHolds = mutableListOf<StockHold>()
    val stockReservations = mutableListOf<StockReservation>()
    val cylinders = mutableListOf<Cylinder>()
    val cylinderCustodyEvents = mutableListOf<CylinderCustodyEvent>()
    val customerHoldings = mutableListOf<CustomerCylinderHolding>()
    val payments = mutableListOf<Payment>()
    val paymentAttempts = mutableListOf<PaymentAttempt>()
    val codCollections = mutableListOf<CodCollection>()
    val refunds = mutableListOf<Refund>()
    val ledgerEntries = mutableListOf<LedgerEntry>()
    val supportCases = mutableListOf<SupportCase>()
    val partnerApplications = mutableListOf<PartnerApplication>()
    val notifications = mutableListOf<NotificationItem>()
    val auditLogs = mutableListOf<AuditLog>()
    val idempotencyKeys = mutableSetOf<String>()

    // Rider offline outbox & Ops conflict queue
    val riderOutbox = mutableListOf<OfflineOutboxEvent>()
    val opsConflictQueue = mutableListOf<SerialConflictItem>()

    // UI state
    var activeSurface = AppSurface.CUSTOMER
    var activeStaffSubRole = StaffSubRole.OPERATIONS
    var activeCustomerId = "CUST-001"
    var activeRiderId = "RIDER-001"
    var isRiderOffline = false
    var selectedOrderIdForDrawer: String? = null
    var outboxSequenceCounter = 1

    private val _stateVersion = MutableStateFlow(0)
    val stateVersion: StateFlow<Int> = _stateVersion.asStateFlow()

    private val _appState = MutableStateFlow(buildSnapshot())
    val appState: StateFlow<SharedAppState> = _appState.asStateFlow()

    init {
        seedInitialData()
    }

    fun buildSnapshot(): SharedAppState {
        return SharedAppState(
            activeSurface = activeSurface,
            activeStaffSubRole = activeStaffSubRole,
            activeCustomerId = activeCustomerId,
            activeRiderId = activeRiderId,
            isRiderOffline = isRiderOffline,
            selectedOrderIdForDrawer = selectedOrderIdForDrawer,
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
            customerHoldings = customerHoldings.toList(),
            payments = payments.toList(),
            ledgerEntries = ledgerEntries.toList(),
            supportCases = supportCases.toList(),
            partnerApplications = partnerApplications.toList(),
            notifications = notifications.toList(),
            auditLogs = auditLogs.toList(),
            riderOutbox = riderOutbox.toList(),
            opsConflictQueue = opsConflictQueue.toList()
        )
    }

    fun notifyChanged() {
        _appState.value = buildSnapshot()
        _stateVersion.value += 1
    }

    fun logAudit(entityType: String, entityId: String, action: String, actor: String, role: AccountableRole, details: String) {
        val entry = AuditLog(
            id = "AUD-" + UUID.randomUUID().toString().take(8),
            entityType = entityType,
            entityId = entityId,
            action = action,
            actor = actor,
            role = role,
            details = details
        )
        auditLogs.add(entry)
    }

    fun addNotification(surface: AppSurface, title: String, message: String, canonicalStatus: String, orderId: String?) {
        val item = NotificationItem(
            id = "NOTIF-" + UUID.randomUUID().toString().take(8),
            surface = surface,
            title = title,
            message = message,
            canonicalStatus = canonicalStatus,
            orderId = orderId
        )
        notifications.add(0, item)
    }

    // --- Order Placement Flow ---

    fun createOrder(
        customerId: String,
        addressId: String,
        fulfilmentMode: FulfilmentMode,
        sku: String,
        quantity: Int,
        includeDeposit: Boolean,
        paymentMethod: PaymentMethod
    ): Pair<Order?, String?> {
        val address = addresses.find { it.id == addressId }
            ?: return Pair(null, "Address not found")
        if (!address.isServiceable) {
            return Pair(null, "Address in ${address.area}, ${address.city} is currently OUT OF SERVICE AREA.")
        }

        val product = productVariants.find { it.sku == sku }
            ?: return Pair(null, "Product variant $sku not found")

        val unitPrice = priceBookConfig.productPricesPaisa[sku] ?: 300000L
        val unitDeposit = if (includeDeposit && product.isCylinder) {
            priceBookConfig.depositAmountPaisa[sku] ?: 750000L
        } else 0L

        val subtotal = unitPrice * quantity
        val depositTotal = unitDeposit * quantity
        val platformFee = priceBookConfig.platformFeePaisa
        val deliveryFee = if (fulfilmentMode == FulfilmentMode.DELIVERY) priceBookConfig.deliveryFeePaisa else 0L
        val taxableSubtotal = subtotal + platformFee + deliveryFee
        val tax = (taxableSubtotal * priceBookConfig.taxRateBps) / 10000L
        val total = taxableSubtotal + tax + depositTotal

        val orderId = "ORD-" + UUID.randomUUID().toString().take(6).uppercase()
        val line = OrderLine(
            sku = sku,
            productName = product.name,
            quantity = quantity,
            unitPricePaisa = unitPrice,
            unitDepositPaisa = unitDeposit,
            subtotalPaisa = subtotal,
            depositTotalPaisa = depositTotal
        )

        val holdExpires = System.currentTimeMillis() + (priceBookConfig.holdTtlMinutes * 60 * 1000L)

        val order = Order(
            id = orderId,
            customerId = customerId,
            addressId = addressId,
            fulfilmentMode = fulfilmentMode,
            status = OrderStatus.ORD_DRAFT,
            items = listOf(line),
            subtotalPaisa = subtotal,
            depositTotalPaisa = depositTotal,
            platformFeePaisa = platformFee,
            deliveryFeePaisa = deliveryFee,
            taxPaisa = tax,
            totalAmountPaisa = total,
            paymentMethod = paymentMethod,
            holdExpiresAt = holdExpires
        )

        // 1. Availability check & Temporary stock hold BEFORE payment
        val hold = StockHold(
            id = "HOLD-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            sku = sku,
            quantity = quantity,
            expiresAt = holdExpires
        )
        stockHolds.add(hold)

        orders.add(0, order)
        selectedOrderIdForDrawer = orderId

        // Transition Draft -> Validation Pending
        val t1 = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_VALPEND,
            actor = "System (Customer Cart)",
            role = AccountableRole.OPERATIONS,
            trigger = "Order placement initiated"
        )
        recordHistory(t1)

        // Validation Pending -> Payment Pending
        val t2 = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_PAYPEND,
            actor = "System Validator",
            role = AccountableRole.OPERATIONS,
            trigger = "Serviceability verified for zone ${address.zoneId}"
        )
        recordHistory(t2)

        // Create linked payment record
        val payment = Payment(
            id = "PAY-" + UUID.randomUUID().toString().take(6),
            orderId = orderId,
            method = paymentMethod,
            state = if (paymentMethod == PaymentMethod.CASH_ON_DELIVERY) PaymentState.COD_PENDING else PaymentState.PAYMENT_PENDING,
            baseAmountPaisa = total,
            finalAmountPaisa = total,
            idempotencyKey = "IDEMP-" + UUID.randomUUID().toString().take(8)
        )
        payments.add(payment)

        // Ledger liability record for deposit
        if (depositTotal > 0) {
            ledgerEntries.add(
                LedgerEntry(
                    id = "LED-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    entryType = "DEPOSIT_LIABILITY",
                    debitPaisa = 0L,
                    creditPaisa = depositTotal,
                    description = "Customer Security Deposit liability (Ownership retained by Blue Gas)"
                )
            )
        }

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Order Created",
            message = "Order ${order.id} is pending payment with a ${priceBookConfig.holdTtlMinutes}m stock hold.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = order.id
        )

        logAudit("Order", orderId, "CREATE_ORDER", customerId, AccountableRole.OPERATIONS, "Order created in ${order.status.canonicalLabel}")
        notifyChanged()
        return Pair(order, null)
    }

    // --- Payment Confirmation & Confirmation Transition ---

    fun confirmPaymentAndAdvance(
        orderId: String,
        forcedGatewayOutcome: String = "SUCCESS",
        actor: String = "Customer"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
        val payment = payments.find { it.orderId == orderId } ?: return Pair(false, "Payment record not found")

        if (payment.method == PaymentMethod.ONLINE_HOSTED) {
            val payResult = PaymentStateMachine.processOnlinePayment(
                payment = payment,
                forcedOutcome = forcedGatewayOutcome,
                processedKeys = idempotencyKeys,
                actor = actor
            )
            when (payResult) {
                is PaymentResult.Failure -> {
                    // Raise fulfilment exception on payment failure
                    val excRes = OrderStateMachine.transition(
                        order = order,
                        targetStatus = OrderStatus.ORD_EXC,
                        actor = actor,
                        role = AccountableRole.FINANCE,
                        trigger = "Payment Gateway Failure",
                        exceptionReason = ExceptionReason.PAYMENT_ISSUE,
                        exceptionDetails = payResult.errorMessage
                    )
                    recordHistory(excRes)
                    addNotification(
                        surface = AppSurface.CUSTOMER,
                        title = "Payment Failed",
                        message = payResult.errorMessage,
                        canonicalStatus = order.status.canonicalLabel,
                        orderId = orderId
                    )
                    notifyChanged()
                    return Pair(false, payResult.errorMessage)
                }
                is PaymentResult.Success -> {
                    // Payment succeeded -> transition Payment Pending -> Confirmed
                    val tConf = OrderStateMachine.transition(
                        order = order,
                        targetStatus = OrderStatus.ORD_CONF,
                        actor = actor,
                        role = AccountableRole.FINANCE,
                        trigger = "Online Payment Captured"
                    )
                    recordHistory(tConf)
                    onOrderConfirmed(order)
                }
            }
        } else {
            // Cash on Delivery confirmation
            val codRes = PaymentStateMachine.advanceCod(
                payment = payment,
                targetState = PaymentState.FINAL_AMOUNT_DETERMINED,
                actor = actor
            )
            val tConf = OrderStateMachine.transition(
                order = order,
                targetStatus = OrderStatus.ORD_CONF,
                actor = actor,
                role = AccountableRole.FINANCE,
                trigger = "COD Choice Confirmed"
            )
            recordHistory(tConf)
            onOrderConfirmed(order)
        }

        notifyChanged()
        return Pair(true, "Payment processed. Order is now ${order.status.canonicalLabel}")
    }

    private fun onOrderConfirmed(order: Order) {
        // Firm quantity reservation on order Confirmed: ORD_CONF -> ORD_RSVD
        val tRsvd = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_RSVD,
            actor = "System Inventory Controller",
            role = AccountableRole.INVENTORY,
            trigger = "Firm stock reservation placed on confirmation"
        )
        recordHistory(tRsvd)

        order.items.forEach { line ->
            stockReservations.add(
                StockReservation(
                    id = "RSV-" + UUID.randomUUID().toString().take(6),
                    orderId = order.id,
                    sku = line.sku,
                    quantity = line.quantity,
                    reservedAt = System.currentTimeMillis()
                )
            )
        }

        // Auto-assign distributor
        val address = addresses.find { it.id == order.addressId }
        val targetDist = distributors.firstOrNull { it.city == address?.city } ?: distributors.first()
        order.assignedDistributorId = targetDist.id

        // Reserved -> Assigned
        val tAsgn = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ASGN,
            actor = "Operations Routing Engine",
            role = AccountableRole.OPERATIONS,
            trigger = "Assigned to distributor ${targetDist.name}"
        )
        recordHistory(tAsgn)

        orderAssignments.add(
            OrderAssignment(
                orderId = order.id,
                distributorId = targetDist.id,
                riderId = null,
                assignedAt = System.currentTimeMillis()
            )
        )

        addNotification(
            surface = AppSurface.STAFF,
            title = "New Order Assigned",
            message = "Order ${order.id} assigned to ${targetDist.name}.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = order.id
        )
    }

    // --- Distributor Flow ---

    fun distributorAcceptOrder(orderId: String, actor: String = "Distributor"): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

        val tAcpt = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ACPT,
            actor = actor,
            role = AccountableRole.DISTRIBUTOR,
            trigger = "Distributor accepted SLA"
        )
        if (tAcpt is TransitionResult.Failure) {
            return Pair(false, tAcpt.errorMessage)
        }
        recordHistory(tAcpt)

        // Accepted -> Preparing
        val tPrep = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_PREP,
            actor = actor,
            role = AccountableRole.DISTRIBUTOR,
            trigger = "Distributor initiated preparation & serial allocation"
        )
        recordHistory(tPrep)

        notifyChanged()
        return Pair(true, "Order is now ${order.status.canonicalLabel}")
    }

    // --- Exact Cylinder Serial Allocation ---

    fun allocateCylinderSerial(orderId: String, serialNumber: String, actor: String = "Distributor"): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
        if (order.status != OrderStatus.ORD_PREP) {
            return Pair(false, "Serial allocation is strictly performed during ORD_PREP Preparing. Current status is ${order.status.canonicalLabel}.")
        }

        val cyl = cylinders.find { it.serialNumber == serialNumber }
            ?: return Pair(false, "Cylinder serial $serialNumber does not exist in master registry.")

        // Invariant: One serial maps to at most one order at a time!
        if (cyl.allocatedOrderId != null && cyl.allocatedOrderId != orderId) {
            return Pair(false, "Serial allocation collision: Serial $serialNumber is ALREADY allocated to order ${cyl.allocatedOrderId}!")
        }

        // Check if order already had another serial, release previous
        if (order.allocatedSerial != null && order.allocatedSerial != serialNumber) {
            val oldCyl = cylinders.find { it.serialNumber == order.allocatedSerial }
            oldCyl?.let {
                val idx = cylinders.indexOf(it)
                cylinders[idx] = it.copy(status = InventoryState.INV_AVAIL, allocatedOrderId = null)
            }
        }

        val cylIdx = cylinders.indexOf(cyl)
        cylinders[cylIdx] = cyl.copy(
            status = InventoryState.INV_SERIAL,
            allocatedOrderId = orderId
        )
        order.allocatedSerial = serialNumber

        logAudit("Cylinder", serialNumber, "ALLOCATE_SERIAL", actor, AccountableRole.INVENTORY, "Bound exact physical serial $serialNumber to order $orderId")
        notifyChanged()
        return Pair(true, "Serial $serialNumber bound successfully to order $orderId.")
    }

    // --- Dispatch / Pickup Flow ---

    fun dispatchOrReadyForPickup(
        orderId: String,
        riderId: String? = null,
        actor: String = "Distributor"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

        if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
            val assignedRider = riderId ?: activeRiderId
            order.assignedRiderId = assignedRider

            val tDisp = OrderStateMachine.transition(
                order = order,
                targetStatus = OrderStatus.ORD_DISP,
                actor = actor,
                role = AccountableRole.OPERATIONS,
                trigger = "Handed over to Rider $assignedRider for dispatch"
            )
            if (tDisp is TransitionResult.Failure) {
                return Pair(false, tDisp.errorMessage)
            }
            recordHistory(tDisp)

            // Cylinder custody moves to Rider Van
            order.allocatedSerial?.let { s ->
                val cyl = cylinders.find { it.serialNumber == s }
                if (cyl != null) {
                    val idx = cylinders.indexOf(cyl)
                    cylinders[idx] = cyl.copy(
                        status = InventoryState.INV_TRANSIT,
                        custody = CylinderCustody.RIDER_VAN,
                        currentLocationLabel = "Rider Van ($assignedRider)"
                    )
                    cylinderCustodyEvents.add(
                        CylinderCustodyEvent(
                            id = "CUST-" + UUID.randomUUID().toString().take(6),
                            serialNumber = s,
                            fromCustody = CylinderCustody.DISTRIBUTOR_NODE,
                            toCustody = CylinderCustody.RIDER_VAN,
                            orderId = orderId,
                            actor = actor
                        )
                    )
                }
            }

            deliveries.add(
                Delivery(
                    id = "DEL-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    riderId = assignedRider,
                    dispatchedAt = System.currentTimeMillis()
                )
            )

            addNotification(
                surface = AppSurface.RIDER,
                title = "Delivery Dispatched",
                message = "Order $orderId dispatched. Serial ${order.allocatedSerial ?: "N/A"} in custody.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = orderId
            )
            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Order on the way!",
                message = "Rider has been dispatched with your cylinder.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = orderId
            )
        } else {
            // Pickup mode
            val tRdy = OrderStateMachine.transition(
                order = order,
                targetStatus = OrderStatus.ORD_RDYPU,
                actor = actor,
                role = AccountableRole.DISTRIBUTOR,
                trigger = "Staged at node counter for customer pickup"
            )
            if (tRdy is TransitionResult.Failure) {
                return Pair(false, tRdy.errorMessage)
            }
            recordHistory(tRdy)

            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Ready for Pickup",
                message = "Your cylinder is ready for collection at the distributor hub.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = orderId
            )
        }

        notifyChanged()
        return Pair(true, "Order advanced to ${order.status.canonicalLabel}")
    }

    // --- Rider Lifecycle Actions & Offline Outbox Handling ---

    fun getActiveRider(): Rider? {
        return riders.find { it.id == activeRiderId } ?: riders.firstOrNull()
    }

    fun switchRider(riderId: String) {
        val r = riders.find { it.id == riderId }
        if (r != null) {
            activeRiderId = r.id
            notifyChanged()
        }
    }

    fun toggleRiderShiftStatus(riderId: String) {
        val idx = riders.indexOfFirst { it.id == riderId }
        if (idx != -1) {
            val curr = riders[idx]
            val newStatus = if (curr.status == "ACTIVE") "INACTIVE" else "ACTIVE"
            riders[idx] = curr.copy(status = newStatus)
            notifyChanged()
        }
    }

    /**
     * Rider triggers: Assigned -> Accepted (ORD_ASGN -> ORD_ACPT)
     */
    fun riderAcceptOrder(orderId: String, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (order.status != OrderStatus.ORD_ASGN) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Cannot accept order: Current status is ${order.status.canonicalLabel}. Legal transitions: $legalStr.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "RIDER_ACCEPT_ORDER",
                fromStatus = OrderStatus.ORD_ASGN,
                toStatus = OrderStatus.ORD_ACPT,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Order acceptance queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tAcpt = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ACPT,
            actor = rider?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = "Rider accepted delivery assignment"
        )
        if (tAcpt is TransitionResult.Failure) {
            return Pair(false, tAcpt.errorMessage)
        }
        order.acceptedAt = System.currentTimeMillis()
        recordHistory(tAcpt)

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Order Accepted by Rider",
            message = "Rider ${rider?.name ?: ""} has accepted your order and is beginning cylinder prep.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Rider Accepted Order",
            message = "Order $orderId accepted by ${rider?.name ?: ""}.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "RIDER_ACCEPT", rider?.name ?: "Rider", AccountableRole.OPERATIONS, "Rider accepted order $orderId")
        notifyChanged()
        return Pair(true, "Order accepted successfully! Now in ${order.status.canonicalLabel}.")
    }

    /**
     * Rider triggers: Assigned -> Fulfilment Exception, reason: no_acceptance (ORD_ASGN -> ORD_EXC)
     */
    fun riderDeclineOrder(orderId: String, reason: String, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (order.status != OrderStatus.ORD_ASGN) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Cannot decline order: Current status is ${order.status.canonicalLabel}. Legal transitions: $legalStr.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "RIDER_DECLINE_ORDER",
                fromStatus = OrderStatus.ORD_ASGN,
                toStatus = OrderStatus.ORD_EXC,
                payloadNotes = reason,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Order decline queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tExc = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_EXC,
            actor = rider?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = "Rider declined assignment ($reason)",
            exceptionReason = ExceptionReason.NO_ACCEPTANCE,
            exceptionDetails = "Rider ${rider?.name ?: ""} declined assignment: $reason"
        )
        if (tExc is TransitionResult.Failure) {
            return Pair(false, tExc.errorMessage)
        }
        recordHistory(tExc)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Order Declined by Rider",
            message = "Order $orderId declined by ${rider?.name ?: ""} ($reason). Operations reassignment needed.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "RIDER_DECLINE", rider?.name ?: "Rider", AccountableRole.OPERATIONS, "Reason: $reason")
        notifyChanged()
        return Pair(true, "Order declined. Status is now ${order.status.canonicalLabel} (Exception: NO_ACCEPTANCE).")
    }

    /**
     * Rider triggers: Accepted -> Preparing (ORD_ACPT -> ORD_PREP)
     */
    fun riderStartPreparing(orderId: String, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (order.status != OrderStatus.ORD_ACPT) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Cannot begin preparation: Current status is ${order.status.canonicalLabel}. Legal transitions: $legalStr.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "START_PREPARING",
                fromStatus = OrderStatus.ORD_ACPT,
                toStatus = OrderStatus.ORD_PREP,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Preparation start queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tPrep = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_PREP,
            actor = rider?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = "Rider began cylinder preparation & serial verification"
        )
        if (tPrep is TransitionResult.Failure) {
            return Pair(false, tPrep.errorMessage)
        }
        recordHistory(tPrep)

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Cylinder Preparation Started",
            message = "Rider ${rider?.name ?: ""} is preparing your cylinder and hydro-testing seal.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "START_PREP", rider?.name ?: "Rider", AccountableRole.OPERATIONS, "Rider initiated cylinder preparation")
        notifyChanged()
        return Pair(true, "Preparation started! Order is now ${order.status.canonicalLabel}. Please select/scan cylinder serial.")
    }

    /**
     * Rider triggers: Exact cylinder serial allocation during Preparing (ORD_PREP)
     */
    fun riderSelectAndBindSerial(orderId: String, serialNumber: String, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (order.status != OrderStatus.ORD_PREP) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Serial scan only allowed during ORD_PREP. Current status is ${order.status.canonicalLabel}. Legal: $legalStr.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "SELECT_SERIAL",
                scannedSerial = serialNumber,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            order.allocatedSerial = serialNumber
            notifyChanged()
            return Pair(true, "Serial $serialNumber bound locally in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        return allocateCylinderSerial(orderId, serialNumber, actor = rider?.name ?: "Rider")
    }

    /**
     * Rider triggers:
     * - Preparing -> Dispatched (delivery mode)
     * - Preparing -> Ready for Pickup (pickup mode)
     */
    fun riderStartDeliveryOrStagePickup(orderId: String, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (order.allocatedSerial == null) {
            return Pair(false, "Serial allocation required: Outgoing cylinder serial MUST be scanned before starting delivery or staging for pickup.")
        }

        val targetStatus = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) OrderStatus.ORD_DISP else OrderStatus.ORD_RDYPU

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "DISPATCH_ORDER",
                fromStatus = OrderStatus.ORD_PREP,
                toStatus = targetStatus,
                scannedSerial = order.allocatedSerial,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Departure queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tDisp = OrderStateMachine.transition(
            order = order,
            targetStatus = targetStatus,
            actor = rider?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = if (order.fulfilmentMode == FulfilmentMode.DELIVERY) "Rider commenced delivery journey" else "Staged at counter for pickup"
        )
        if (tDisp is TransitionResult.Failure) {
            return Pair(false, tDisp.errorMessage)
        }
        recordHistory(tDisp)

        if (order.fulfilmentMode == FulfilmentMode.DELIVERY) {
            // Custody moves to Rider Van
            order.allocatedSerial?.let { s ->
                val cyl = cylinders.find { it.serialNumber == s }
                if (cyl != null) {
                    val idx = cylinders.indexOf(cyl)
                    cylinders[idx] = cyl.copy(
                        status = InventoryState.INV_TRANSIT,
                        custody = CylinderCustody.RIDER_VAN,
                        currentLocationLabel = "Rider Van (${rider?.name ?: "Rider"})"
                    )
                    cylinderCustodyEvents.add(
                        CylinderCustodyEvent(
                            id = "CUST-" + UUID.randomUUID().toString().take(6),
                            serialNumber = s,
                            fromCustody = CylinderCustody.DISTRIBUTOR_NODE,
                            toCustody = CylinderCustody.RIDER_VAN,
                            orderId = orderId,
                            actor = rider?.name ?: "Rider"
                        )
                    )
                }
            }
            deliveries.add(
                Delivery(
                    id = "DEL-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    riderId = rider?.id ?: activeRiderId,
                    dispatchedAt = System.currentTimeMillis()
                )
            )
            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Rider On The Way!",
                message = "Rider ${rider?.name ?: ""} is en route with cylinder ${order.allocatedSerial}.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = orderId
            )
        } else {
            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Ready for Pickup",
                message = "Your cylinder ${order.allocatedSerial} is ready at distributor node.",
                canonicalStatus = order.status.canonicalLabel,
                orderId = orderId
            )
        }

        logAudit("Order", orderId, "DISPATCH_ORDER", rider?.name ?: "Rider", AccountableRole.OPERATIONS, "Dispatched to ${order.status.canonicalLabel}")
        notifyChanged()
        return Pair(true, "Delivery started! Order is now ${order.status.canonicalLabel}.")
    }

    /**
     * Rider triggers: Dispatched -> At Handover (ORD_DISP -> ORD_ATHO)
     */
    fun arriveAtHandover(orderId: String, actor: String? = null, isReplay: Boolean = false): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val riderName = actor ?: (getActiveRider()?.name ?: "Rider")

        if (order.status != OrderStatus.ORD_DISP && order.status != OrderStatus.ORD_RDYPU) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Cannot arrive at doorstep: Current status is ${order.status.canonicalLabel}. Legal transitions: $legalStr.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "ARRIVE_AT_HANDOVER",
                fromStatus = order.status,
                toStatus = OrderStatus.ORD_ATHO,
                scannedSerial = order.allocatedSerial,
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Arrival queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tAtHo = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_ATHO,
            actor = riderName,
            role = AccountableRole.OPERATIONS,
            trigger = "Arrived at customer doorstep / handover counter"
        )
        if (tAtHo is TransitionResult.Failure) {
            return Pair(false, tAtHo.errorMessage)
        }
        recordHistory(tAtHo)

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Rider Arrived At Location",
            message = "Rider $riderName has arrived at your address for cylinder inspection & handover.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "ARRIVE_AT_HANDOVER", riderName, AccountableRole.OPERATIONS, "Arrived at handover")
        notifyChanged()
        return Pair(true, "Marked arrived at doorstep! Order is now ${order.status.canonicalLabel}.")
    }

    /**
     * Rider triggers:
     * - Dispatched -> Fulfilment Exception (reason: delivery_failure)
     * - At Handover -> Fulfilment Exception (reason: delivery_failure / customer refused)
     */
    fun riderReportIssue(
        orderId: String,
        reason: ExceptionReason,
        details: String,
        isReplay: Boolean = false
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val rider = getActiveRider()

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "REPORT_ISSUE",
                fromStatus = order.status,
                toStatus = OrderStatus.ORD_EXC,
                payloadNotes = "${reason.name}: $details",
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Issue report queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        val tExc = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_EXC,
            actor = rider?.name ?: "Rider",
            role = AccountableRole.OPERATIONS,
            trigger = "Rider reported fulfilment exception: $details",
            exceptionReason = reason,
            exceptionDetails = details
        )
        if (tExc is TransitionResult.Failure) {
            return Pair(false, tExc.errorMessage)
        }
        recordHistory(tExc)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Delivery Issue Reported",
            message = "Order $orderId exception by ${rider?.name ?: ""}: $details",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Delivery Issue Encountered",
            message = "Rider reported issue: $details. Operations will coordinate with you.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "RIDER_EXCEPTION", rider?.name ?: "Rider", AccountableRole.OPERATIONS, "Reason: ${reason.name} - $details")
        notifyChanged()
        return Pair(true, "Issue reported. Order moved to ${order.status.canonicalLabel}.")
    }

    /**
     * Rider triggers: At Handover -> Handover Complete (ORD_ATHO -> ORD_HOC -> ORD_COMP)
     */
    fun completeHandover(
        orderId: String,
        nonTechInstallationPassed: Boolean,
        proofOfDeliveryNote: String,
        collectedCashPaisa: Long? = null,
        actor: String? = null,
        emptyCylinderCollected: Boolean = false,
        emptyCylinderSerial: String? = null,
        customerSignature: String? = null,
        otpCode: String? = null,
        photoEvidenceCaptured: Boolean = false,
        signerName: String? = null,
        changeReturnedPaisa: Long? = null,
        isReplay: Boolean = false
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order $orderId not found.")
        val riderName = actor ?: (getActiveRider()?.name ?: "Rider")

        if (order.status != OrderStatus.ORD_ATHO) {
            val legal = OrderStateMachine.getLegalNextTransitions(order).map { it.canonicalLabel }
            val legalStr = if (legal.isEmpty()) "None" else legal.joinToString(", ")
            return Pair(false, "Cannot complete handover: Current status is ${order.status.canonicalLabel}. Legal transitions: $legalStr.")
        }

        if (!nonTechInstallationPassed) {
            return Pair(false, "Safety requirement: Non-technical installation & leak test checklist must be completed and passed.")
        }

        if (isRiderOffline && !isReplay) {
            val event = OfflineOutboxEvent(
                sequenceNumber = outboxSequenceCounter++,
                idempotencyKey = "IDEMP-OFF-" + UUID.randomUUID().toString().take(8),
                orderId = orderId,
                action = "COMPLETE_HANDOVER",
                fromStatus = OrderStatus.ORD_ATHO,
                toStatus = OrderStatus.ORD_HOC,
                scannedSerial = order.allocatedSerial,
                payloadNotes = "POD: $proofOfDeliveryNote; Cash: $collectedCashPaisa; EmptyCol: $emptyCylinderCollected; EmptySerial: $emptyCylinderSerial; Signer: $signerName; Photo: $photoEvidenceCaptured",
                timestamp = System.currentTimeMillis(),
                syncStatus = "PENDING"
            )
            riderOutbox.add(event)
            notifyChanged()
            return Pair(true, "Complete handover queued in Offline Outbox (Seq #${event.sequenceNumber}).")
        }

        // 1. At Handover -> Handover Complete (ORD_ATHO -> ORD_HOC)
        val tHoc = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_HOC,
            actor = riderName,
            role = AccountableRole.OPERATIONS,
            trigger = "Safety inspection passed & physical handover finished"
        )
        if (tHoc is TransitionResult.Failure) {
            return Pair(false, tHoc.errorMessage)
        }
        recordHistory(tHoc)

        // 2. Handover Complete -> Completed (ORD_HOC -> ORD_COMP)
        val tComp = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_COMP,
            actor = riderName,
            role = AccountableRole.OPERATIONS,
            trigger = "Handover finalised & verified by Rider"
        )
        recordHistory(tComp)

        val podRef = "POD-${order.id}-${System.currentTimeMillis() % 100000}"
        order.podReference = podRef

        // Update delivery record
        val del = deliveries.find { it.orderId == orderId }
        if (del != null) {
            val idx = deliveries.indexOf(del)
            deliveries[idx] = del.copy(
                completedAt = System.currentTimeMillis(),
                nonTechnicalInstallationPassed = true,
                proofOfDeliveryNote = proofOfDeliveryNote,
                podReference = podRef,
                customerSignatureCaptured = customerSignature != null || otpCode != null,
                photoEvidenceCaptured = photoEvidenceCaptured,
                photoEvidenceTimestamp = if (photoEvidenceCaptured) System.currentTimeMillis() else null,
                signerName = signerName,
                changeReturnedPaisa = changeReturnedPaisa
            )
        } else {
            deliveries.add(
                Delivery(
                    id = "DEL-" + UUID.randomUUID().toString().take(6),
                    orderId = orderId,
                    riderId = order.assignedRiderId ?: activeRiderId,
                    completedAt = System.currentTimeMillis(),
                    nonTechnicalInstallationPassed = true,
                    proofOfDeliveryNote = proofOfDeliveryNote,
                    podReference = podRef,
                    customerSignatureCaptured = customerSignature != null || otpCode != null,
                    photoEvidenceCaptured = photoEvidenceCaptured,
                    photoEvidenceTimestamp = if (photoEvidenceCaptured) System.currentTimeMillis() else null,
                    signerName = signerName,
                    changeReturnedPaisa = changeReturnedPaisa
                )
            )
        }

        // Update outgoing cylinder custody & customer holding
        order.allocatedSerial?.let { s ->
            val cyl = cylinders.find { it.serialNumber == s }
            cyl?.let {
                val idx = cylinders.indexOf(it)
                cylinders[idx] = it.copy(
                    status = InventoryState.INV_CONSUMED,
                    custody = CylinderCustody.CUSTOMER_PREMISES,
                    currentLocationLabel = "Customer Premises (${order.customerId})"
                )
            }
            customerHoldings.add(
                CustomerCylinderHolding(
                    id = "HOLDING-" + UUID.randomUUID().toString().take(6),
                    customerId = order.customerId,
                    cylinderSerial = s,
                    ownership = if (order.depositTotalPaisa > 0) CylinderOwnership.DEPOSIT_HELD else CylinderOwnership.OWNED_BY_CUSTOMER,
                    depositPaisa = order.depositTotalPaisa,
                    acquiredAt = System.currentTimeMillis()
                )
            )
        }

        // If COD: settle payment and update ledger
        val payment = payments.find { it.orderId == orderId }
        if (payment != null) {
            if (payment.method == PaymentMethod.CASH_ON_DELIVERY) {
                val cashToCollect = collectedCashPaisa ?: order.totalAmountPaisa
                if (payment.state == PaymentState.COD_PENDING) {
                    PaymentStateMachine.advanceCod(
                        payment = payment,
                        targetState = PaymentState.FINAL_AMOUNT_DETERMINED,
                        actor = riderName
                    )
                }
                if (payment.state == PaymentState.FINAL_AMOUNT_DETERMINED) {
                    PaymentStateMachine.advanceCod(
                        payment = payment,
                        targetState = PaymentState.CASH_COLLECTION_PENDING,
                        actor = riderName
                    )
                }
                if (payment.state == PaymentState.CASH_COLLECTION_PENDING) {
                    PaymentStateMachine.advanceCod(
                        payment = payment,
                        targetState = PaymentState.CASH_COLLECTED,
                        collectedPaisa = cashToCollect,
                        actor = riderName
                    )
                }
                if (payment.state == PaymentState.CASH_COLLECTED) {
                    PaymentStateMachine.advanceCod(
                        payment = payment,
                        targetState = PaymentState.SETTLED,
                        actor = riderName
                    )
                }
                codCollections.add(
                    CodCollection(
                        id = "COD-" + UUID.randomUUID().toString().take(6),
                        orderId = orderId,
                        riderId = order.assignedRiderId ?: activeRiderId,
                        expectedAmountPaisa = order.totalAmountPaisa,
                        collectedAmountPaisa = cashToCollect
                    )
                )
            } else {
                PaymentStateMachine.resolveOnlineAdjustmentAndSettle(payment, "NO_ADJUSTMENT")
            }
        }

        // Ledger settlement entry
        ledgerEntries.add(
            LedgerEntry(
                id = "LED-" + UUID.randomUUID().toString().take(6),
                orderId = orderId,
                entryType = "SETTLEMENT",
                debitPaisa = order.totalAmountPaisa,
                creditPaisa = 0L,
                description = "Final Order Settlement for ${order.id} (POD: $podRef)"
            )
        )

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Order Delivered & Completed!",
            message = "Your cylinder has been delivered safely (POD Ref: $podRef). View receipt now.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Order Handover Complete",
            message = "Order $orderId delivered by $riderName. Ledger updated with settlement.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )
        logAudit("Order", orderId, "COMPLETE_HANDOVER", riderName, AccountableRole.OPERATIONS, "POD verified. Ref: $podRef")
        notifyChanged()
        return Pair(true, "Handover completed! Order is now ${order.status.canonicalLabel} (Receipt Ready).")
    }

    /**
     * Replay Rider Offline Outbox in strict sequential order against shared state machine.
     */
    fun replayRiderOutbox(): String {
        if (isRiderOffline) {
            return "Please turn off Offline mode first before syncing outbox."
        }
        if (riderOutbox.isEmpty()) {
            return "Outbox is empty. All events synced."
        }

        val sb = StringBuilder()
        var applied = 0
        var duplicates = 0
        var rejected = 0
        var conflicts = 0

        val queue = riderOutbox.toList()
        queue.forEach { event ->
            if (idempotencyKeys.contains(event.idempotencyKey)) {
                event.syncStatus = "DUPLICATE"
                duplicates++
                sb.append("Seq #${event.sequenceNumber}: DUPLICATE (Ignored)\n")
                return@forEach
            }

            val order = orders.find { it.id == event.orderId }
            if (order == null && event.action != "CONFLICT_SERIAL_SCAN") {
                event.syncStatus = "REJECTED"
                rejected++
                sb.append("Seq #${event.sequenceNumber}: REJECTED (Order not found)\n")
                return@forEach
            }

            when (event.action) {
                "RIDER_ACCEPT_ORDER" -> {
                    val res = riderAcceptOrder(event.orderId, isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Accept Order\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "RIDER_DECLINE_ORDER" -> {
                    val res = riderDeclineOrder(event.orderId, event.payloadNotes ?: "Declined", isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Decline Order\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "START_PREPARING" -> {
                    val res = riderStartPreparing(event.orderId, isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Start Preparing\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "SELECT_SERIAL" -> {
                    val res = riderSelectAndBindSerial(event.orderId, event.scannedSerial ?: "", isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Serial Bind (${event.scannedSerial})\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "DISPATCH_ORDER" -> {
                    val res = riderStartDeliveryOrStagePickup(event.orderId, isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Departure Dispatched\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "ARRIVE_AT_HANDOVER" -> {
                    val res = arriveAtHandover(event.orderId, isReplay = true)
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Arrive at Handover\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "REPORT_ISSUE" -> {
                    val res = riderReportIssue(
                        event.orderId,
                        ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
                        event.payloadNotes ?: "Offline reported issue",
                        isReplay = true
                    )
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Report Exception\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "COMPLETE_HANDOVER" -> {
                    val res = completeHandover(
                        orderId = event.orderId,
                        nonTechInstallationPassed = true,
                        proofOfDeliveryNote = "Offline synced POD - Safety passed",
                        collectedCashPaisa = order?.totalAmountPaisa,
                        customerSignature = "Offline Signature",
                        isReplay = true
                    )
                    if (res.first) {
                        event.syncStatus = "APPLIED"
                        idempotencyKeys.add(event.idempotencyKey)
                        applied++
                        sb.append("Seq #${event.sequenceNumber}: APPLIED Complete Handover\n")
                    } else {
                        event.syncStatus = "REJECTED"
                        rejected++
                        sb.append("Seq #${event.sequenceNumber}: REJECTED (${res.second})\n")
                    }
                }
                "CONFLICT_SERIAL_SCAN" -> {
                    // Route to Ops conflict queue
                    event.syncStatus = "CONFLICT_QUEUED"
                    conflicts++
                    opsConflictQueue.add(
                        SerialConflictItem(
                            id = "CONF-" + UUID.randomUUID().toString().take(6),
                            serialNumber = event.scannedSerial ?: "UNKNOWN",
                            orderId1 = event.orderId,
                            orderId2 = "ORD-OTHER-VAN",
                            reasonCode = "DUPLICATE_OFFLINE_SCAN_CONFLICT"
                        )
                    )
                    idempotencyKeys.add(event.idempotencyKey)
                    sb.append("Seq #${event.sequenceNumber}: CONFLICT DETECTED -> Routed to Ops Queue\n")
                }
            }
        }

        notifyChanged()
        return "Sync Complete: $applied applied, $duplicates duplicate, $conflicts conflict(s), $rejected rejected.\n$sb"
    }

    fun simulateSerialConflict(): String {
        val targetSerial = "BG-LHR-001"
        val activeOrder = orders.firstOrNull { it.status == OrderStatus.ORD_PREP || it.status == OrderStatus.ORD_DISP }
            ?: orders.first()

        val event = OfflineOutboxEvent(
            sequenceNumber = outboxSequenceCounter++,
            idempotencyKey = "IDEMP-CONF-" + UUID.randomUUID().toString().take(8),
            orderId = activeOrder.id,
            action = "CONFLICT_SERIAL_SCAN",
            scannedSerial = targetSerial,
            timestamp = System.currentTimeMillis(),
            syncStatus = "PENDING"
        )
        riderOutbox.add(event)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Serial Conflict Queued",
            message = "Conflict simulated for serial $targetSerial on Order ${activeOrder.id}.",
            canonicalStatus = "CONFLICT",
            orderId = activeOrder.id
        )

        notifyChanged()
        return "Created offline scan event for serial $targetSerial with competing device. Check Rider Outbox & Staff Ops Queue!"
    }

    // --- Fulfilment Exception & Recovery ---

    fun raiseFulfilmentException(
        orderId: String,
        reason: ExceptionReason,
        details: String,
        actor: String = "Operations"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

        val tExc = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_EXC,
            actor = actor,
            role = AccountableRole.OPERATIONS,
            trigger = "Fulfilment Exception Raised",
            exceptionReason = reason,
            exceptionDetails = details
        )
        if (tExc is TransitionResult.Failure) {
            return Pair(false, tExc.errorMessage)
        }
        recordHistory(tExc)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Fulfilment Exception",
            message = "Order $orderId raised exception: ${reason.description}",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )

        logAudit("Order", orderId, "EXCEPTION_RAISED", actor, AccountableRole.OPERATIONS, "Reason: ${reason.name} - $details")
        notifyChanged()
        return Pair(true, "Order is now ${order.status.canonicalLabel}")
    }

    fun recoverFulfilmentException(
        orderId: String,
        targetStatus: OrderStatus,
        actor: String = "Operations Supervisor"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

        val tRec = OrderStateMachine.transition(
            order = order,
            targetStatus = targetStatus,
            actor = actor,
            role = AccountableRole.OPERATIONS,
            trigger = "Exception Recovery by Ops"
        )
        if (tRec is TransitionResult.Failure) {
            return Pair(false, tRec.errorMessage)
        }
        recordHistory(tRec)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Exception Recovered",
            message = "Order $orderId recovered to ${targetStatus.canonicalLabel}.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )

        notifyChanged()
        return Pair(true, "Order recovered to ${order.status.canonicalLabel}")
    }

    // --- Complaints & Post-Completion (Order remains ORD_COMP) ---

    fun raiseComplaint(
        orderId: String,
        issueType: String,
        description: String,
        customerId: String = "CUST-001"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")

        // Rule: Completed never transitions to return state. Order stays Completed!
        val caseId = "CASE-" + UUID.randomUUID().toString().take(6)
        val supportCase = SupportCase(
            id = caseId,
            orderId = orderId,
            customerId = customerId,
            issueType = issueType,
            description = description,
            status = "OPEN"
        )
        supportCases.add(0, supportCase)

        addNotification(
            surface = AppSurface.STAFF,
            title = "Complaint Case Opened",
            message = "Case $caseId opened for order $orderId ($issueType). Order remains ${order.status.canonicalLabel}.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )

        logAudit("SupportCase", caseId, "OPEN_COMPLAINT", customerId, AccountableRole.SUPPORT, "Opened support dispute: $issueType")
        notifyChanged()
        return Pair(true, "Support case $caseId opened. Order status remains ${order.status.canonicalLabel}.")
    }

    // --- Refunds (Handled in Payment, Order stays ORD_CANC if cancelled) ---

    fun executeRefund(
        orderId: String,
        amountPaisa: Long,
        reason: String,
        actor: String = "Finance Officer"
    ): Pair<Boolean, String> {
        val order = orders.find { it.id == orderId } ?: return Pair(false, "Order not found")
        val payment = payments.find { it.orderId == orderId } ?: return Pair(false, "Payment record not found")

        val refundId = "REF-" + UUID.randomUUID().toString().take(6)
        refunds.add(
            Refund(
                id = refundId,
                paymentId = payment.id,
                orderId = orderId,
                amountPaisa = amountPaisa,
                reason = reason,
                executedBy = actor
            )
        )

        payment.state = PaymentState.FULLY_REFUNDED
        payment.settledAmountPaisa = 0L

        ledgerEntries.add(
            LedgerEntry(
                id = "LED-" + UUID.randomUUID().toString().take(6),
                orderId = orderId,
                entryType = "REFUND",
                debitPaisa = 0L,
                creditPaisa = amountPaisa,
                description = "Refund executed: $reason"
            )
        )

        addNotification(
            surface = AppSurface.CUSTOMER,
            title = "Refund Executed",
            message = "Refund of ${formatPaisaToPkr(amountPaisa)} processed by Finance.",
            canonicalStatus = order.status.canonicalLabel,
            orderId = orderId
        )

        logAudit("Payment", payment.id, "EXECUTE_REFUND", actor, AccountableRole.FINANCE, "Refund amount: ${formatPaisaToPkr(amountPaisa)}")
        notifyChanged()
        return Pair(true, "Refund $refundId processed successfully.")
    }

    private fun recordHistory(result: TransitionResult) {
        if (result is TransitionResult.Success) {
            val hist = OrderStatusHistory(
                id = "HIST-" + UUID.randomUUID().toString().take(8),
                orderId = result.order.id,
                fromStatus = result.fromStatus,
                toStatus = result.toStatus,
                trigger = result.actor,
                actor = result.actor,
                role = result.role
            )
            orderStatusHistory.add(hist)
            logAudit("Order", result.order.id, "STATUS_TRANSITION", result.actor, result.role, "${result.fromStatus.canonicalLabel} -> ${result.toStatus.canonicalLabel}")
        }
    }

    // --- Seed Data Implementation ---

    fun seedInitialData() {
        customers.clear()
        addresses.clear()
        serviceZones.clear()
        distributors.clear()
        nodes.clear()
        productVariants.clear()
        orders.clear()
        orderStatusHistory.clear()
        orderAssignments.clear()
        deliveries.clear()
        stockBalances.clear()
        stockHolds.clear()
        stockReservations.clear()
        cylinders.clear()
        cylinderCustodyEvents.clear()
        customerHoldings.clear()
        payments.clear()
        paymentAttempts.clear()
        codCollections.clear()
        refunds.clear()
        ledgerEntries.clear()
        supportCases.clear()
        partnerApplications.clear()
        notifications.clear()
        auditLogs.clear()
        idempotencyKeys.clear()
        riderOutbox.clear()
        opsConflictQueue.clear()

        // 1. Service Zones (Lahore, Islamabad)
        serviceZones.addAll(
            listOf(
                ServiceZone("ZONE-LHR-01", "Lahore", "Gulberg & Cantt Central", true, listOf("54000", "54660")),
                ServiceZone("ZONE-LHR-02", "Lahore", "DHA & Phase V Hub", true, listOf("54792")),
                ServiceZone("ZONE-ISB-01", "Islamabad", "F-6, F-7 & Blue Area", true, listOf("44000")),
                ServiceZone("ZONE-ISB-02", "Islamabad", "G-11 & Sector Outskirts", false, listOf("44100")) // Out of area for pilot demo!
            )
        )

        // 2. Distributors & Nodes
        distributors.addAll(
            listOf(
                Distributor("DIST-001", "Pak LPG Energy Hub", "Lahore", "Main Gulberg III, Lahore", "+92 42 3578 9011", "ACTIVE"),
                Distributor("DIST-002", "Margalla Gas Distro", "Islamabad", "Blue Area, Sector F-6, Islamabad", "+92 51 2890 123", "ACTIVE")
            )
        )
        nodes.addAll(
            listOf(
                Node("NODE-LHR-1", "DIST-001", "Gulberg Dispatch Terminal", "ZONE-LHR-01", 150),
                Node("NODE-ISB-1", "DIST-002", "Blue Area Storage Depot", "ZONE-ISB-01", 100)
            )
        )

        // 2b. Seeded Riders (Bilal Ahmed, Saima Noor, Usman Tariq)
        riders.clear()
        riders.addAll(
            listOf(
                Rider(
                    id = "RIDER-001",
                    name = "Bilal Ahmed",
                    phone = "+92 300 4455667",
                    cnic = "35201-1122334-1",
                    zoneId = "ZONE-LHR-01",
                    zoneName = "Lahore - Gulberg & Cantt",
                    nodeId = "NODE-LHR-1",
                    vehicleNumber = "LEC-8812 (Suzuki Bolan)",
                    status = "ACTIVE",
                    onboardingStatus = "APPROVED"
                ),
                Rider(
                    id = "RIDER-002",
                    name = "Saima Noor",
                    phone = "+92 333 5566778",
                    cnic = "61101-2233445-2",
                    zoneId = "ZONE-ISB-01",
                    zoneName = "Islamabad - Blue Area & F-6/F-7",
                    nodeId = "NODE-ISB-1",
                    vehicleNumber = "ICT-4421 (Honda Cargo Van)",
                    status = "ACTIVE",
                    onboardingStatus = "APPROVED"
                ),
                Rider(
                    id = "RIDER-003",
                    name = "Usman Tariq",
                    phone = "+92 321 6677889",
                    cnic = "35202-3344556-3",
                    zoneId = "ZONE-LHR-01",
                    zoneName = "Lahore - Gulberg & Cantt",
                    nodeId = "NODE-LHR-1",
                    vehicleNumber = "LEB-1904 (Yamaha Delivery Carrier)",
                    status = "INACTIVE",
                    onboardingStatus = "APPROVED"
                )
            )
        )

        // 3. Customers (6 customers)
        customers.addAll(
            listOf(
                Customer("CUST-001", "Fatima Khan", "+92 300 1234567", "35202-1234567-1", "ADDR-001"),
                Customer("CUST-002", "Ahmed Bilal", "+92 321 9876543", "35201-9876543-3", "ADDR-002"),
                Customer("CUST-003", "Zainab Raza", "+92 333 4567890", "61101-4567890-2", "ADDR-003"),
                Customer("CUST-004", "Hamza Sheikh", "+92 345 5678901", "35202-5678901-5", "ADDR-004"),
                Customer("CUST-005", "Sana Mir", "+92 312 6789012", "61101-6789012-4", "ADDR-005"),
                Customer("CUST-006", "Usman Qureshi", "+92 301 7890123", "35201-7890123-7", "ADDR-006")
            )
        )

        // Addresses
        addresses.addAll(
            listOf(
                Address("ADDR-001", "CUST-001", "Home", "House 42, Street 8, Block K", "Gulberg III", "Lahore", "ZONE-LHR-01", true),
                Address("ADDR-002", "CUST-002", "Apartment", "Flat 12-B, Askari 10", "Cantt", "Lahore", "ZONE-LHR-01", true),
                Address("ADDR-003", "CUST-003", "Residence", "House 15, Street 24", "Sector F-7/2", "Islamabad", "ZONE-ISB-01", true),
                Address("ADDR-004", "CUST-004", "Office", "Commercial Plaza 3", "DHA Phase 5", "Lahore", "ZONE-LHR-02", true),
                Address("ADDR-005", "CUST-005", "Home", "House 88, Street 4", "Sector G-11/3", "Islamabad", "ZONE-ISB-02", false), // unserviceable!
                Address("ADDR-006", "CUST-006", "Villa", "Plot 19, Cavalry Ground", "Cantt", "Lahore", "ZONE-LHR-01", true)
            )
        )

        // 4. Products (3 Cylinder sizes + 1 Accessory)
        productVariants.addAll(
            listOf(
                ProductVariant("SKU-LPG-11-8", "SKU-LPG-11-8", "11.8 kg Domestic LPG Cylinder", "CYLINDER_REFILL", 11.8, true, "Standard domestic cooking cylinder. High safety brass valve."),
                ProductVariant("SKU-LPG-45-4", "SKU-LPG-45-4", "45.4 kg Commercial LPG Cylinder", "CYLINDER_REFILL", 45.4, true, "High capacity cylinder for restaurants, bakeries and cafes."),
                ProductVariant("SKU-LPG-6-0", "SKU-LPG-6-0", "6.0 kg Compact LPG Cylinder", "CYLINDER_REFILL", 6.0, true, "Portable lightweight cylinder for camping or small kitchens."),
                ProductVariant("SKU-REG-HOSE", "SKU-REG-HOSE", "OGRA-Certified Low Pressure Regulator & Hose", "ACCESSORY", null, false, "High safety rated regulator with reinforced 2-meter LPG safety hose.")
            )
        )

        // 5. Cylinders (20 cylinders with exact serials)
        for (i in 1..12) {
            val serial = String.format("BG-LHR-%03d", i)
            cylinders.add(
                Cylinder(
                    serialNumber = serial,
                    sku = "SKU-LPG-11-8",
                    weightKg = 11.8,
                    status = InventoryState.INV_AVAIL,
                    custody = CylinderCustody.DISTRIBUTOR_NODE,
                    currentLocationLabel = "Gulberg Dispatch Terminal (NODE-LHR-1)"
                )
            )
        }
        for (i in 13..20) {
            val serial = String.format("BG-ISB-%03d", i)
            cylinders.add(
                Cylinder(
                    serialNumber = serial,
                    sku = if (i % 2 == 0) "SKU-LPG-45-4" else "SKU-LPG-6-0",
                    weightKg = if (i % 2 == 0) 45.4 else 6.0,
                    status = InventoryState.INV_AVAIL,
                    custody = CylinderCustody.DISTRIBUTOR_NODE,
                    currentLocationLabel = "Blue Area Storage Depot (NODE-ISB-1)"
                )
            )
        }

        // 6. Partner applications
        partnerApplications.addAll(
            listOf(
                PartnerApplication("APP-001", "DISTRIBUTOR", "Rawal Gas Agencies", "Islamabad", "61101-9988776-1", "350 sq.ft licensed yard with fire extinguishers", "PENDING_APPROVAL"),
                PartnerApplication("APP-002", "RIDER", "Kamran Akhtar", "Lahore", "35201-1122334-9", "Suzuki Bolan 2021, Commercial Cargo Permit", "PENDING_APPROVAL")
            )
        )

        // 7. Seed Orders Across States:
        // Order 1: ORD_COMP Completed with open complaint!
        val ord1Id = "ORD-SEED-101"
        val ord1 = Order(
            id = ord1Id,
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_COMP,
            items = listOf(
                OrderLine("SKU-LPG-11-8", "11.8 kg Domestic LPG Cylinder", 1, 325000L, 750000L, 325000L, 750000L)
            ),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 750000L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 1138500L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            allocatedSerial = "BG-LHR-001",
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001"
        )
        orders.add(ord1)
        payments.add(
            Payment("PAY-101", ord1Id, PaymentMethod.ONLINE_HOSTED, PaymentState.SETTLED, 1138500L, 1138500L, 1138500L, 1138500L, "PKR", "IDEMP-101")
        )
        supportCases.add(
            SupportCase("CASE-SEED-01", ord1Id, "CUST-001", "CYLINDER_DENT", "Cylinder delivered has a visible dent near top ring collar. Customer requests inspection.", "OPEN")
        )
        // Mark cylinder as consumed at customer premises
        val c1 = cylinders.find { it.serialNumber == "BG-LHR-001" }
        c1?.let { cylinders[cylinders.indexOf(it)] = it.copy(status = InventoryState.INV_CONSUMED, custody = CylinderCustody.CUSTOMER_PREMISES, allocatedOrderId = ord1Id) }

        // Order 2: ORD_EXC Fulfilment Exception!
        val ord2Id = "ORD-SEED-102"
        val ord2 = Order(
            id = ord2Id,
            customerId = "CUST-002",
            addressId = "ADDR-002",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_EXC,
            items = listOf(
                OrderLine("SKU-LPG-11-8", "11.8 kg Domestic LPG Cylinder", 1, 325000L, 0L, 325000L, 0L)
            ),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            allocatedSerial = null,
            assignedDistributorId = "DIST-001",
            exceptionReason = ExceptionReason.SERIAL_UNAVAILABLE_OR_PREP_FAILURE,
            exceptionDetails = "Distributor ran out of hydro-tested 11.8kg units during morning batch preparation."
        )
        orders.add(ord2)
        payments.add(
            Payment("PAY-102", ord2Id, PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, 388500L, 388500L, 0L, 0L, "PKR", "IDEMP-102")
        )

        // Order 3: ORD_DISP Dispatched
        val ord3Id = "ORD-SEED-103"
        val ord3 = Order(
            id = ord3Id,
            customerId = "CUST-003",
            addressId = "ADDR-003",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_DISP,
            items = listOf(
                OrderLine("SKU-LPG-45-4", "45.4 kg Commercial LPG Cylinder", 1, 1240000L, 1800000L, 1240000L, 1800000L)
            ),
            subtotalPaisa = 1240000L,
            depositTotalPaisa = 1800000L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 64250L,
            totalAmountPaisa = 3149250L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            allocatedSerial = "BG-ISB-014",
            assignedDistributorId = "DIST-002",
            assignedRiderId = "RIDER-002"
        )
        orders.add(ord3)
        payments.add(
            Payment("PAY-103", ord3Id, PaymentMethod.ONLINE_HOSTED, PaymentState.INITIAL_AMOUNT_CAPTURED, 3149250L, 3149250L, 3149250L, 0L, "PKR", "IDEMP-103")
        )
        val c3 = cylinders.find { it.serialNumber == "BG-ISB-014" }
        c3?.let { cylinders[cylinders.indexOf(it)] = it.copy(status = InventoryState.INV_TRANSIT, custody = CylinderCustody.RIDER_VAN, allocatedOrderId = ord3Id) }

        // Order 4: ORD_PREP Preparing
        val ord4Id = "ORD-SEED-104"
        val ord4 = Order(
            id = ord4Id,
            customerId = "CUST-004",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_PREP,
            items = listOf(
                OrderLine("SKU-LPG-6-0", "6.0 kg Compact LPG Cylinder", 1, 185000L, 450000L, 185000L, 450000L)
            ),
            subtotalPaisa = 185000L,
            depositTotalPaisa = 450000L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 11500L,
            totalAmountPaisa = 691500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            allocatedSerial = "BG-LHR-003",
            assignedDistributorId = "DIST-001"
        )
        orders.add(ord4)
        payments.add(
            Payment("PAY-104", ord4Id, PaymentMethod.CASH_ON_DELIVERY, PaymentState.FINAL_AMOUNT_DETERMINED, 691500L, 691500L, 0L, 0L, "PKR", "IDEMP-104")
        )
        val c4 = cylinders.find { it.serialNumber == "BG-LHR-003" }
        c4?.let { cylinders[cylinders.indexOf(it)] = it.copy(status = InventoryState.INV_SERIAL, allocatedOrderId = ord4Id) }

        // Order 5: ORD_ASGN Assigned (Bilal Ahmed - Pending response with countdown timer!)
        val ord5Id = "ORD-SEED-105"
        val ord5 = Order(
            id = ord5Id,
            customerId = "CUST-002",
            addressId = "ADDR-002",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_ASGN,
            items = listOf(
                OrderLine("SKU-LPG-11-8", "11.8 kg Domestic LPG Cylinder", 1, 325000L, 0L, 325000L, 0L)
            ),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            allocatedSerial = null,
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001",
            assignedAt = System.currentTimeMillis() - (5 * 60 * 1000L) // Assigned 5 minutes ago
        )
        orders.add(ord5)
        payments.add(
            Payment("PAY-105", ord5Id, PaymentMethod.CASH_ON_DELIVERY, PaymentState.COD_PENDING, 388500L, 388500L, 0L, 0L, "PKR", "IDEMP-105")
        )

        // Order 6: ORD_ACPT Accepted (Bilal Ahmed - Active order in preparation flow!)
        val ord6Id = "ORD-SEED-106"
        val ord6 = Order(
            id = ord6Id,
            customerId = "CUST-004",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_ACPT,
            items = listOf(
                OrderLine("SKU-LPG-11-8", "11.8 kg Domestic LPG Cylinder", 1, 325000L, 750000L, 325000L, 750000L)
            ),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 750000L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 1138500L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            allocatedSerial = null,
            assignedDistributorId = "DIST-001",
            assignedRiderId = "RIDER-001",
            assignedAt = System.currentTimeMillis() - (15 * 60 * 1000L),
            acceptedAt = System.currentTimeMillis() - (12 * 60 * 1000L)
        )
        orders.add(ord6)
        payments.add(
            Payment("PAY-106", ord6Id, PaymentMethod.ONLINE_HOSTED, PaymentState.INITIAL_AMOUNT_CAPTURED, 1138500L, 1138500L, 1138500L, 0L, "PKR", "IDEMP-106")
        )

        // Initial Ledger entries
        ledgerEntries.addAll(
            listOf(
                LedgerEntry("LED-001", ord1Id, "DEPOSIT_LIABILITY", 0L, 750000L, "Customer Security Deposit (Held liability)"),
                LedgerEntry("LED-002", ord1Id, "REVENUE", 325000L, 0L, "LPG Cylinder Refill revenue"),
                LedgerEntry("LED-003", ord1Id, "PLATFORM_FEE", 15000L, 0L, "Blue Gas digital facilitation fee"),
                LedgerEntry("LED-004", ord3Id, "DEPOSIT_LIABILITY", 0L, 1800000L, "Commercial Cylinder Deposit liability")
            )
        )

        // Seed notifications
        addNotification(AppSurface.CUSTOMER, "Pilot Live in Lahore & Islamabad", "Blue Gas phase 1 ordering active.", "ORD_CONF", null)
        addNotification(AppSurface.STAFF, "Fulfilment Exception Alert", "Order ORD-SEED-102 requires preparation recovery.", "ORD_EXC", ord2Id)

        selectedOrderIdForDrawer = ord3Id
        notifyChanged()
    }
}
