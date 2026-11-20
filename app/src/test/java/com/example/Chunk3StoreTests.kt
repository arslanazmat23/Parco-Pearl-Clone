package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Chunk3StoreTests {

    private fun createAndDispatchNewCylinderOrder(
        store: BlueGasStore,
        paymentMethod: PaymentMethod,
        serial: String = "BG-LHR-118-002",
        sku: String = "SKU-LPG-11-8"
    ): Order {
        assertTrue(store.captureCnic("CUST-001", "35201-1122334-1").first)
        store.updateSettings(store.settings.copy(backupCylinderAllowed = true, maxDepositCylindersPerSize = 2))
        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = sku,
            quantity = 1,
            includeDeposit = true,
            paymentMethod = paymentMethod,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(err)
        requireNotNull(order)

        if (paymentMethod == PaymentMethod.ONLINE_HOSTED) {
            val (pOk, pMsg) = store.payOnline(order.id, "SUCCESS")
            assertTrue(pMsg, pOk)
        } else {
            val (cOk, cMsg) = store.confirmCod(order.id)
            assertTrue(cMsg, cOk)
        }

        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, serial).first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, serial, "RIDER-001").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-001").first)
        assertTrue(store.riderArrive(order.id, "RIDER-001").first)

        val (hoOk, hoMsg) = store.completeHandover(
            orderId = order.id,
            pinVerified = true,
            installationResult = InstallationResult.INSTALLED,
            checklistAnswers = mapOf("Leak check" to true),
            photoCaptured = true,
            signatureCaptured = true,
            evidenceConsent = true,
            amountReceivedPaisa = if (paymentMethod == PaymentMethod.CASH_ON_DELIVERY) order.totalAmountPaisa else null
        )
        assertTrue(hoMsg, hoOk)
        assertEquals(OrderStatus.ORD_COMP, order.status)
        return order
    }

    private fun advanceReturnToHubConditionCheck(
        store: BlueGasStore,
        customerId: String,
        serial: String,
        condition: ReturnCondition,
        note: String? = null,
        hazardSuspected: Boolean = false
    ): String {
        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = customerId,
            cylinderSerial = serial,
            reason = "Customer moving residence",
            actor = AccountableRole.SUPPORT.displayName
        )
        assertTrue(caseId, reqOk)

        val (revOk, revMsg) = store.moveCase(
            caseId = caseId,
            target = L6Status.UNDER_REVIEW,
            reason = "Reviewing customer holding and deposit",
            actor = AccountableRole.SUPPORT.displayName,
            role = AccountableRole.SUPPORT
        )
        assertTrue(revMsg, revOk)

        val (authOk, authMsg) = store.moveCase(
            caseId = caseId,
            target = L6Status.RETURN_AUTHORIZED,
            reason = "Authorized cylinder collection",
            actor = AccountableRole.SUPPORT.displayName,
            role = AccountableRole.SUPPORT
        )
        assertTrue(authMsg, authOk)

        val (offOk, offMsg) = store.offerReturnCollection(caseId, "RIDER-001", AccountableRole.OPERATIONS.displayName)
        assertTrue(offMsg, offOk)

        assertTrue(store.riderAcceptReturn(caseId, "RIDER-001").first)
        assertTrue(store.riderCollectCylinder(caseId, "RIDER-001", serial, photoCaptured = true).first)
        assertTrue(store.riderHandInReturn(caseId, "RIDER-001").first)

        val (condOk, condMsg) = store.hubRecordReturnCondition(
            caseId = caseId,
            condition = condition,
            note = note,
            hazardSuspected = hazardSuspected,
            actor = AccountableRole.HUB.displayName
        )
        assertTrue(condMsg, condOk)
        return caseId
    }

    @Test
    fun `T2b offline outbox replay commits stock once and increments outWithRiders`() {
        val store = BlueGasViewModel().store
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val startFilled = stock.filledQty
        val startReserved = stock.reservedQty
        val startOutWithRiders = stock.outWithRidersQty

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        requireNotNull(order)

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertEquals(startReserved + 1, stock.reservedQty)

        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-001").first)

        store.setRiderOffline(true)
        val (queuedOk, queuedMsg) = store.riderStartTrip(order.id, "RIDER-001")
        assertTrue(queuedMsg, queuedOk)
        assertEquals(OrderStatus.ORD_PREP, order.status)
        assertEquals(1, store.riderOutbox.size)
        assertEquals("PENDING", store.riderOutbox[0].syncStatus)
        // Stock has not physically left hub yet while queued offline
        assertEquals(startFilled, stock.filledQty)
        assertEquals(startReserved + 1, stock.reservedQty)
        assertEquals(startOutWithRiders, stock.outWithRidersQty)

        store.setRiderOffline(false)
        val (replayOk, replayMsg) = store.replayRiderOutbox()
        assertTrue(replayMsg, replayOk)
        assertEquals("APPLIED", store.riderOutbox[0].syncStatus)
        assertEquals(OrderStatus.ORD_DISP, order.status)
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startReserved, stock.reservedQty)
        assertEquals(startOutWithRiders + 1, stock.outWithRidersQty)
        val reservation = store.stockReservations.first { it.orderId == order.id }
        assertTrue(reservation.isCommitted)

        // Second replay call must not double-subtract stock
        store.replayRiderOutbox()
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startOutWithRiders + 1, stock.outWithRidersQty)
    }

    @Test
    fun `R5 simulate serial conflict puts one action in Operations conflict queue on replay`() {
        val store = BlueGasViewModel().store
        store.switchRider("RIDER-001")
        val targetOrder = store.orders.first { it.id == "ORD-SEED-105" }
        assertTrue(store.riderAcceptTask(targetOrder.id, "RIDER-001").first)

        val queueMsg = store.simulateSerialConflict()
        assertTrue(queueMsg, store.riderOutbox.any { it.action == "COMPLETE_HANDOVER" && it.syncStatus == "PENDING" })

        val (replayOk, _) = store.replayRiderOutbox()
        assertFalse(replayOk)
        assertEquals(1, store.opsConflictQueue.size)
        assertEquals("CONFLICT_QUEUED", store.riderOutbox.last().syncStatus)
    }

    @Test
    fun `T4b exception recovery second run completes order with net minus 1 filled stock`() {
        val store = BlueGasViewModel().store
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val startFilled = stock.filledQty
        val startReserved = stock.reservedQty
        val startOutWithRiders = stock.outWithRidersQty

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        requireNotNull(order)

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-001").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-001").first)

        assertTrue(
            store.riderReportIssue(
                orderId = order.id,
                reason = ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
                details = "Goods rejected at the door",
                riderId = "RIDER-001"
            ).first
        )
        assertTrue(store.recoverException(order.id, OrderStatus.ORD_ASGN, "Second delivery run").first)
        assertTrue(store.hubScanInReturnedCylinder("NODE-LHR-1", "BG-LHR-118-002").first)

        // Second run to completion
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.assignRider(order.id, "RIDER-002").first)

        store.switchRider("RIDER-002")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-002").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-002").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-002").first)
        assertTrue(store.riderArrive(order.id, "RIDER-002").first)

        val (hoOk, hoMsg) = store.completeHandover(
            orderId = order.id,
            pinVerified = true,
            installationResult = InstallationResult.INSTALLED,
            checklistAnswers = mapOf("Leak check" to true),
            photoCaptured = true,
            signatureCaptured = true,
            evidenceConsent = true
        )
        assertTrue(hoMsg, hoOk)
        assertEquals(OrderStatus.ORD_COMP, order.status)
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startReserved, stock.reservedQty)
        assertEquals(startOutWithRiders, stock.outWithRidersQty)
    }

    @Test
    fun `T7 45_4 kg delivery completes cleanly without touching 11_8 kg stock`() {
        val store = BlueGasViewModel().store
        val stock454 = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-45-4" }
        val stock118 = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val start454Filled = stock454.filledQty
        val start118Filled = stock118.filledQty

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-45-4",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        requireNotNull(order)

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-454-001").first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-454-001", "RIDER-001").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-001").first)
        assertTrue(store.riderArrive(order.id, "RIDER-001").first)
        assertTrue(
            store.completeHandover(
                orderId = order.id,
                pinVerified = true,
                installationResult = InstallationResult.INSTALLED,
                checklistAnswers = mapOf("Leak check" to true),
                photoCaptured = true,
                signatureCaptured = true,
                evidenceConsent = true
            ).first
        )

        assertEquals(OrderStatus.ORD_COMP, order.status)
        assertEquals(start454Filled - 1, stock454.filledQty)
        assertEquals(0, stock454.outWithRidersQty)
        assertEquals(start118Filled, stock118.filledQty)
    }

    @Test
    fun `T8 online deposit return happy path refunds deposit and keeps order completed`() {
        val store = BlueGasViewModel().store
        val order = createAndDispatchNewCylinderOrder(store, PaymentMethod.ONLINE_HOSTED, serial = "BG-LHR-118-002")
        assertEquals(PaymentState.SETTLED, store.activePaymentFor(order.id)?.state)

        val caseId = advanceReturnToHubConditionCheck(
            store = store,
            customerId = "CUST-001",
            serial = "BG-LHR-118-002",
            condition = ReturnCondition.GOOD
        )

        val (refReqOk, refReqMsg) = store.requestDepositRefund(
            caseId = caseId,
            deductionPaisa = 0L,
            deductionReason = null,
            actor = AccountableRole.FINANCE.displayName
        )
        assertTrue(refReqMsg, refReqOk)

        val returnCase = store.returnCases.first { it.id == caseId }
        val deposit = store.depositAccounts.first { it.cylinderSerial == "BG-LHR-118-002" }
        assertEquals(DepositStatus.REFUND_IN_PROGRESS, deposit.status)

        val refundId = deposit.refundId!!
        val (procOk, procMsg) = store.processRefund(refundId, succeed = true, actor = AccountableRole.FINANCE.displayName)
        assertTrue(procMsg, procOk)

        val (closeOk, closeMsg) = store.moveCase(
            caseId = caseId,
            target = L6Status.CASE_CLOSED,
            reason = "Deposit refunded cleanly",
            actor = AccountableRole.SUPPORT.displayName,
            role = AccountableRole.SUPPORT
        )
        assertTrue(closeMsg, closeOk)

        assertEquals(DepositStatus.REFUNDED, deposit.status)
        val holding = store.customerHoldings.first { it.cylinderSerial == "BG-LHR-118-002" }
        assertEquals("RETURNED", holding.status)
        assertEquals(L6Status.CASE_CLOSED, returnCase.status)
        assertEquals(OrderStatus.ORD_COMP, order.status)
    }

    @Test
    fun `T9 COD deposit refund blocked before cash settlement then succeeds after reconciliation`() {
        val store = BlueGasViewModel().store
        val order = createAndDispatchNewCylinderOrder(store, PaymentMethod.CASH_ON_DELIVERY, serial = "BG-LHR-118-002")
        assertEquals(PaymentState.CASH_COLLECTED, store.activePaymentFor(order.id)?.state)

        val deposit = store.depositAccounts.first { it.orderId == order.id && it.cylinderSerial == "BG-LHR-118-002" }
        assertEquals(DepositStatus.HELD, deposit.status)

        val caseId = advanceReturnToHubConditionCheck(
            store = store,
            customerId = "CUST-001",
            serial = "BG-LHR-118-002",
            condition = ReturnCondition.GOOD
        )

        val (blockedOk, blockedMsg) = store.requestDepositRefund(caseId = caseId, actor = AccountableRole.FINANCE.displayName)
        assertFalse(blockedOk)
        assertTrue(blockedMsg, blockedMsg.contains("not settled yet", ignoreCase = true))

        // Hand cash from rider -> hub -> Finance
        val (subOk, subMsg) = store.riderSubmitCashHandover(
            riderId = "RIDER-001",
            recipient = "HUB",
            declaredPaisa = order.totalAmountPaisa
        )
        assertTrue(subMsg, subOk)
        val handover = store.cashHandovers.last { it.orderIds.contains(order.id) }

        val (hubOk, hubMsg) = store.hubReceiveRiderCash(handover.id, countedPaisa = order.totalAmountPaisa)
        assertTrue(hubMsg, hubOk)
        assertEquals("RECEIVED_BY_HUB", handover.status)

        val (recOk, recMsg) = store.reconcileCashHandover(
            handoverId = handover.id,
            countedPaisa = order.totalAmountPaisa,
            reason = null,
            actor = AccountableRole.FINANCE.displayName
        )
        assertTrue(recMsg, recOk)
        assertEquals(PaymentState.SETTLED, store.activePaymentFor(order.id)?.state)

        val (allowedOk, allowedMsg) = store.requestDepositRefund(caseId = caseId, actor = AccountableRole.FINANCE.displayName)
        assertTrue(allowedMsg, allowedOk)
        assertEquals(L6Status.REFUND_REQUESTED, store.returnCases.first { it.id == caseId }.status)
    }

    @Test
    fun `T10 failed deposit refund payout raises EXC-27 and succeeds on retry`() {
        val store = BlueGasViewModel().store
        createAndDispatchNewCylinderOrder(store, PaymentMethod.ONLINE_HOSTED, serial = "BG-LHR-118-002")
        val caseId = advanceReturnToHubConditionCheck(store, "CUST-001", "BG-LHR-118-002", ReturnCondition.GOOD)

        assertTrue(store.requestDepositRefund(caseId = caseId, actor = AccountableRole.FINANCE.displayName).first)
        val deposit = store.depositAccounts.first { it.cylinderSerial == "BG-LHR-118-002" }
        val refundId = deposit.refundId!!
        val returnCase = store.returnCases.first { it.id == caseId }

        val (failOk, failMsg) = store.processRefund(refundId, succeed = false, actor = AccountableRole.FINANCE.displayName)
        assertTrue(failMsg, failOk)
        assertEquals(PaymentState.FULL_REFUND_FAILED, store.refunds.first { it.id == refundId }.state)
        assertEquals(L6Status.REFUND_REQUESTED, returnCase.status)
        assertTrue(store.reconExceptions.any { it.code == "EXC-27" && it.description.contains(caseId) })

        val (retryOk, retryMsg) = store.retryRefund(refundId)
        assertTrue(retryMsg, retryOk)
        assertEquals(PaymentState.FULL_REFUND_PENDING, store.refunds.first { it.id == refundId }.state)

        val (compOk, compMsg) = store.processRefund(refundId, succeed = true, actor = AccountableRole.FINANCE.displayName)
        assertTrue(compMsg, compOk)
        assertEquals(PaymentState.FULLY_REFUNDED, store.refunds.first { it.id == refundId }.state)
        assertEquals(L6Status.REFUND_CONFIRMED, returnCase.status)
        assertEquals(DepositStatus.REFUNDED, deposit.status)
    }

    @Test
    fun `T11 damaged return raises EXC-25 and reduces deposit refund by deduction`() {
        val store = BlueGasViewModel().store
        createAndDispatchNewCylinderOrder(store, PaymentMethod.ONLINE_HOSTED, serial = "BG-LHR-118-002")
        val deposit = store.depositAccounts.first { it.cylinderSerial == "BG-LHR-118-002" }
        val originalDepositPaisa = deposit.amountPaisa

        val caseId = advanceReturnToHubConditionCheck(
            store = store,
            customerId = "CUST-001",
            serial = "BG-LHR-118-002",
            condition = ReturnCondition.DAMAGED,
            note = "Dent on collar"
        )
        assertTrue(store.reconExceptions.any { it.code == "EXC-25" && it.description.contains("Dent on collar") })

        val deduction = 250000L // Rs 2,500
        val (reqOk, reqMsg) = store.requestDepositRefund(
            caseId = caseId,
            deductionPaisa = deduction,
            deductionReason = "Collar repair",
            actor = AccountableRole.FINANCE.displayName
        )
        assertTrue(reqMsg, reqOk)

        val returnCase = store.returnCases.first { it.id == caseId }
        val refund = store.refunds.first { it.id == deposit.refundId }
        assertEquals(originalDepositPaisa - deduction, refund.amountPaisa)
        assertEquals(deduction, returnCase.deductionPaisa)
        assertEquals("Collar repair", returnCase.deductionReason)
        assertEquals(deduction, deposit.deductionPaisa)
        assertEquals("Collar repair", deposit.deductionReason)

        assertTrue(store.processRefund(refund.id, succeed = true, actor = AccountableRole.FINANCE.displayName).first)
        assertEquals(DepositStatus.REDUCED_OR_FORFEITED, deposit.status)
    }

    @Test
    fun `T12 online captured order cancelled before dispatch auto-refunds without Finance notification`() {
        val store = BlueGasViewModel().store
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val startReserved = stock.reservedQty

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        requireNotNull(order)

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        val payment = store.activePaymentFor(order.id)!!
        assertEquals(PaymentState.INITIAL_AMOUNT_CAPTURED, payment.state)
        assertEquals(startReserved + 1, stock.reservedQty)

        val financeNotifIdsBefore = store.notifications
            .filter { it.surface == AppSurface.STAFF && it.staffRole == StaffSubRole.FINANCE }
            .map { it.id }
            .toSet()

        val (cancOk, cancMsg) = store.cancelOrder(order.id, reason = "Changed delivery plans")
        assertTrue(cancMsg, cancOk)

        assertEquals(OrderStatus.ORD_CANC, order.status)
        assertEquals(startReserved, stock.reservedQty)
        assertEquals(PaymentState.FULLY_REFUNDED, payment.state)
        val refund = store.refunds.first { it.orderId == order.id }
        assertEquals(PaymentState.FULLY_REFUNDED, refund.state)
        assertEquals("BLUE GAS platform (system)", refund.executedBy)

        val customerCancelNotif = store.notifications.firstOrNull {
            it.surface == AppSurface.CUSTOMER && it.orderId == order.id && it.title == "Order Cancelled"
        }
        assertNotNull(customerCancelNotif)
        assertTrue(
            customerCancelNotif!!.message,
            customerCancelNotif.message.contains("has been sent back to your payment method")
        )

        val newFinanceNotifs = store.notifications.filter {
            it.surface == AppSurface.STAFF &&
                it.staffRole == StaffSubRole.FINANCE &&
                it.id !in financeNotifIdsBefore
        }
        assertTrue("Expected zero Finance notifications on auto-refund success, found: $newFinanceNotifs", newFinanceNotifs.isEmpty())
    }

    @Test
    fun `R4 online Branch A preauthorized cancellation voids payment before dispatch`() {
        val store = BlueGasViewModel().store
        store.settings = store.settings.copy(onlinePaymentMode = OnlinePaymentMode.BRANCH_A_AUTHORIZE_THEN_CAPTURE)
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val startReserved = stock.reservedQty

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        requireNotNull(order)
        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertEquals(PaymentState.PREAUTHORIZED, store.activePaymentFor(order.id)?.state)

        val (cancOk, cancMsg) = store.cancelOrder(order.id, reason = "Cancel preauth order")
        assertTrue(cancMsg, cancOk)
        assertEquals(OrderStatus.ORD_CANC, order.status)
        assertEquals(PaymentState.VOIDED, store.activePaymentFor(order.id)?.state)
        assertEquals(startReserved, stock.reservedQty)
    }

    @Test
    fun `T13 and R7 deposit check matches cleanly then creates single deduplicated EXC-28 on mismatch`() {
        val store = BlueGasViewModel().store

        val (reconOk, reconMsg) = store.runDailyReconciliation(emptyMap())
        assertTrue(reconMsg, reconOk)
        assertEquals("MATCHED", store.reconciliations.first().status)

        val cleanCheck = store.runDepositCheck()
        assertEquals("Rs 0", cleanCheck.difference)
        assertEquals(0, store.reconExceptions.count { it.code == "EXC-28" })

        val (simOk, simMsg) = store.simulateDepositMismatch()
        assertTrue(simMsg, simOk)

        val mismatch1 = store.runDepositCheck()
        assertNotEquals("Rs 0", mismatch1.difference)
        assertEquals(1, store.reconExceptions.count { it.code == "EXC-28" && it.status == "OPEN" })

        // Running a second time must not add a duplicate open EXC-28
        store.runDepositCheck()
        assertEquals(1, store.reconExceptions.count { it.code == "EXC-28" && it.status == "OPEN" })
    }

    @Test
    fun `R8 HSE incident lifecycle moves to closed and releases or writes off cylinder`() {
        val store = BlueGasViewModel().store
        val (raiseOk, raiseMsg) = store.raiseIncident(
            source = "RIDER",
            description = "Suspected valve leak on inspection",
            cylinderId = "BG-LHR-118-002",
            actor = AccountableRole.HSE.displayName
        )
        assertTrue(raiseMsg, raiseOk)
        val inc = store.safetyIncidents.first { it.cylinderId == "BG-LHR-118-002" }
        assertTrue(store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }.hseHold)

        assertTrue(store.moveIncident(inc.id, L7Status.UNDER_HSE_ASSESSMENT, "Investigating leak").first)
        assertTrue(store.moveIncident(inc.id, L7Status.AWAITING_AUTHORIZED_DECISION, "Ready for sign-off").first)
        assertTrue(
            store.moveIncident(
                incidentId = inc.id,
                target = L7Status.AUTHORIZED_ACTION_TAKEN,
                notes = "Condemn cylinder valve",
                authorizedBy = "Chief HSE Officer",
                cylinderOutcome = "WRITE_OFF"
            ).first
        )
        assertTrue(store.moveIncident(inc.id, L7Status.INCIDENT_CLOSED, "Closed with write-off").first)

        assertEquals(L7Status.INCIDENT_CLOSED, inc.status)
        val cyl = store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }
        assertEquals(InventoryState.INV_WRITEOFF, cyl.status)
        assertFalse(cyl.hseHold)
    }

    @Test
    fun `R9 Switch and Save claim reaches Exchange Completed and Closed`() {
        val store = BlueGasViewModel().store
        val claim = store.replacementClaims.first { it.id == "CLM-SEED-01" }
        assertEquals("B", claim.route)
        assertEquals(ClaimStatus.SCHEDULED_FOR_HANDOVER, claim.status)
        assertNotNull(claim.linkedOrderId)

        assertTrue(store.startInspection(claim.id, "Inspector Tariq").first)
        assertTrue(store.recordPhysicalResult(claim.id, accepted = true, reason = "Shell & valve intact").first)
        assertTrue(store.startHseAssessment(claim.id).first)
        assertTrue(store.recordHseResult(claim.id, cleared = true, notes = "HSE cleared").first)
        assertTrue(store.confirmFinalOffer(claim.id, claim.provisionalOfferPaisa).first)
        val (exOk, exMsg) = store.completeExchange(claim.id)
        assertTrue(exMsg, exOk)

        assertEquals(ClaimStatus.CLOSED, claim.status)
        assertEquals("Eligible-Exchanged", claim.outcomeCode)
        assertTrue(claim.history.any { it.toStatus == ClaimStatus.EXCHANGE_COMPLETED })
        assertTrue(claim.history.any { it.toStatus == ClaimStatus.CLOSED })
    }

    @Test
    fun `R10 reset demo data restores seed state without changing active surface`() {
        val vm = BlueGasViewModel()
        val store = vm.store
        val seededOrderCount = store.orders.size

        // Mutate surface, customer tab, and domain collections
        vm.switchSurface(AppSurface.RIDER)
        store.setCustomerTab("ORDERS")
        store.createOrder("CUST-001", "ADDR-001", FulfilmentMode.DELIVERY, "SKU-LPG-11-8", 1)
        store.setRiderOffline(true)
        assertEquals(seededOrderCount + 1, store.orders.size)

        vm.resetDemoData()

        assertEquals(seededOrderCount, store.orders.size)
        assertEquals(AppSurface.RIDER, store.activeSurface)
        assertEquals("ORDERS", store.customerTab)
        assertEquals(StaffSubRole.OPERATIONS, store.activeStaffSubRole)
        assertEquals("NODE-LHR-1", store.selectedHubId)
    }
}
