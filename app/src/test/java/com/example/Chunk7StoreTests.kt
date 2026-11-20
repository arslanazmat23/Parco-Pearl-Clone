package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Chunk7StoreTests {

    private val karachiTz = TimeZone.getTimeZone("Asia/Karachi")

    private fun karachiTomorrowMillis(hour24: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance(karachiTz, Locale.US)
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, hour24)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    private fun dispatchOrderToRider(
        store: BlueGasStore,
        serial: String = "BG-LHR-118-002",
        arriveAtDoor: Boolean = false,
        serviceLevel: ServiceLevel = ServiceLevel.STANDARD,
        paymentMethod: PaymentMethod = PaymentMethod.ONLINE_HOSTED
    ): Order {
        if (serviceLevel == ServiceLevel.EXPRESS && !store.settings.expressEnabled) {
            assertTrue(store.updateSettings(store.settings.copy(expressEnabled = true)).first)
        }
        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = paymentMethod,
            deliveryWindowLabel = if (serviceLevel == ServiceLevel.EXPRESS) null else "As soon as possible",
            serviceLevel = serviceLevel
        )
        assertNull(err)
        requireNotNull(order)

        if (paymentMethod == PaymentMethod.ONLINE_HOSTED) {
            val (payOk, payMsg) = store.payOnline(order.id, "SUCCESS")
            assertTrue(payMsg, payOk)
        } else {
            val (codOk, codMsg) = store.confirmPaymentAndAdvance(order.id, "CUST-001")
            assertTrue(codMsg, codOk)
        }

        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        val (allocOk, allocMsg) = store.allocateCylinder(order.id, serial)
        assertTrue(allocMsg, allocOk)
        val (asgnOk, asgnMsg) = store.assignRider(order.id, "RIDER-001")
        assertTrue(asgnMsg, asgnOk)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, serial, "RIDER-001").first)
        val (tripOk, tripMsg) = store.riderStartTrip(order.id, "RIDER-001")
        assertTrue(tripMsg, tripOk)
        if (arriveAtDoor) {
            val (arrOk, arrMsg) = store.riderArrive(order.id, "RIDER-001")
            assertTrue(arrMsg, arrOk)
        }
        return order
    }

    @Test
    fun `T31 C7_0 ASAP Standard promise uses standardSameDayByHour before and after cutoff`() {
        val store = BlueGasViewModel().store
        val beforeCutoff = karachiTomorrowMillis(14, 0) // 2 pm < 5 pm
        store.clock = { beforeCutoff }

        val (earlyOrd, err1) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            deliveryWindowLabel = "As soon as possible"
        )
        assertNull(err1)
        requireNotNull(earlyOrd)
        assertTrue(store.payOnline(earlyOrd.id, "SUCCESS").first)
        assertEquals("Today by 9 pm", earlyOrd.promiseLabel)

        val afterCutoff = karachiTomorrowMillis(18, 0) // 6 pm >= 5 pm
        store.clock = { afterCutoff }
        val (lateOrd, err2) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            deliveryWindowLabel = "As soon as possible"
        )
        assertNull(err2)
        requireNotNull(lateOrd)
        assertTrue(store.payOnline(lateOrd.id, "SUCCESS").first)
        assertEquals("Tomorrow by 9 pm", lateOrd.promiseLabel)

        // Configurable via Admin
        val (badOk, _) = store.updateSettings(store.settings.copy(standardSameDayByHour = 17))
        assertFalse(badOk)
        val (ok22, msg22) = store.updateSettings(store.settings.copy(standardSameDayByHour = 22))
        assertTrue(msg22, ok22)

        store.clock = { beforeCutoff }
        val (customOrd, err3) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            deliveryWindowLabel = "As soon as possible"
        )
        assertNull(err3)
        requireNotNull(customOrd)
        assertTrue(store.payOnline(customOrd.id, "SUCCESS").first)
        assertEquals("Today by 10 pm", customOrd.promiseLabel)
    }

    @Test
    fun `T32 rider must call first and wait full duration unless demoSkip is used`() {
        val store = BlueGasViewModel().store
        var currentNow = karachiTomorrowMillis(11, 0)
        store.clock = { currentNow }

        val order = dispatchOrderToRider(store, arriveAtDoor = false)

        // Cannot mark not home without call attempt
        val (noCallOk, noCallMsg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = null,
            waitStartedAt = currentNow,
            demoSkip = true
        )
        assertFalse(noCallOk)
        assertTrue(noCallMsg.contains("Call the customer first", ignoreCase = true))
        assertEquals(OrderStatus.ORD_DISP, order.status)

        // Log call attempt
        val (callOk, _) = store.riderLogCallAttempt(order.id, "RIDER-001")
        assertTrue(callOk)
        assertNotNull(order.lastCallAttemptAt)
        val waitStart = currentNow

        // Wait 5 minutes (< 10 minutes default) -> rejected when demoSkip = false
        currentNow += 5 * 60_000L
        val (tooSoonOk, _) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = waitStart,
            demoSkip = false
        )
        assertFalse(tooSoonOk)
        assertEquals(OrderStatus.ORD_DISP, order.status)

        // Wait reaches 10 minutes -> accepted even with demoSkip = false
        currentNow += 5 * 60_000L
        val (elapsedOk, elapsedMsg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = waitStart,
            demoSkip = false
        )
        assertTrue(elapsedMsg, elapsedOk)
        assertEquals(OrderStatus.ORD_EXC, order.status)
    }

    @Test
    fun `T33 online not-home sets ORD_EXC pendingHubScanIn leaves payment untouched and sends Push plus SMS`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(12, 0)
        store.clock = { now }

        val order = dispatchOrderToRider(store, serial = "BG-LHR-118-002", arriveAtDoor = true)
        val paymentBefore = store.activePaymentFor(order.id)
        requireNotNull(paymentBefore)
        val stateBefore = paymentBefore.state
        val refundCountBefore = store.refunds.size

        assertTrue(store.riderLogCallAttempt(order.id, "RIDER-001").first)
        val (ok, msg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = now,
            demoSkip = true
        )
        assertTrue(msg, ok)

        assertEquals(OrderStatus.ORD_EXC, order.status)
        assertEquals(ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE, order.exceptionReason)
        assertEquals(1, order.notHomeCount)
        assertTrue(order.awaitingCustomerReschedule)
        assertNull(order.allocatedSerial)
        assertEquals("BG-LHR-118-002", order.returningSerial)
        assertEquals("RIDER-001", order.notHomeRiderId)

        val cyl = store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }
        assertTrue(cyl.pendingHubScanIn)
        assertEquals(CylinderCustody.RIDER_VAN, cyl.custody)
        assertNull(cyl.allocatedOrderId)

        // Payment and refunds untouched
        assertEquals(stateBefore, store.activePaymentFor(order.id)?.state)
        assertEquals(refundCountBefore, store.refunds.size)

        // Customer Push + SMS notification
        val custNotif = store.notifications.firstOrNull {
            it.surface == AppSurface.CUSTOMER && it.orderId == order.id && it.title == "We missed you"
        }
        assertNotNull(custNotif)
        assertTrue(custNotif!!.channels.contains(NotifyChannel.PUSH))
        assertTrue(custNotif.channels.contains(NotifyChannel.SMS))

        // Audit recorded via RIDER_NOT_HOME
        assertTrue(store.auditLogs.any { it.entityId == order.id && it.action == "RIDER_NOT_HOME" })
    }

    @Test
    fun `T34 customer reschedules window without new order or payment and hub scan-in restores stock`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(10, 0)
        store.clock = { now }

        val order = dispatchOrderToRider(
            store,
            serial = "BG-LHR-118-002",
            arriveAtDoor = true,
            serviceLevel = ServiceLevel.EXPRESS
        )
        val initialOrderCount = store.orders.size
        val initialPaymentCount = store.payments.size
        val expressFeeBefore = order.expressFeePaisa
        assertTrue(expressFeeBefore > 0L)

        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val filledAfterDispatch = stock.filledQty
        val reservedAfterDispatch = stock.reservedQty
        val outAfterDispatch = stock.outWithRidersQty

        assertTrue(store.riderLogCallAttempt(order.id, "RIDER-001").first)
        assertTrue(
            store.riderMarkNotHome(
                orderId = order.id,
                riderId = "RIDER-001",
                callAttemptedAt = order.lastCallAttemptAt,
                waitStartedAt = now,
                demoSkip = true
            ).first
        )

        // ASAP option rejected by customerRescheduleAfterNotHome
        val asapOpt = store.availableDeliveryWindows().first { it.isAsap }
        assertFalse(store.customerRescheduleAfterNotHome(order.id, asapOpt).first)

        // Pick a future slotted window
        val futureSlot = store.availableDeliveryWindows().first { !it.isAsap && it.endAt != null && it.endAt > now }
        val (reschOk, reschMsg) = store.customerRescheduleAfterNotHome(order.id, futureSlot)
        assertTrue(reschMsg, reschOk)

        assertEquals(initialOrderCount, store.orders.size)
        assertEquals(initialPaymentCount, store.payments.size)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertFalse(order.awaitingCustomerReschedule)
        assertEquals(1, order.rescheduleCount)
        assertEquals(futureSlot.label, order.promiseLabel)
        assertEquals(futureSlot.label, order.deliveryWindowLabel)
        assertEquals(expressFeeBefore, order.expressFeePaisa)
        assertEquals(ServiceLevel.EXPRESS, order.serviceLevel)

        // Returning cylinder still tracked until hub scans it in
        assertEquals("BG-LHR-118-002", order.returningSerial)
        assertEquals("RIDER-001", order.notHomeRiderId)

        // Hub scans returned cylinder in
        val (scanOk, scanMsg) = store.hubScanInReturnedCylinder("NODE-LHR-1", "BG-LHR-118-002")
        assertTrue(scanMsg, scanOk)
        assertNull(order.returningSerial)
        assertNull(order.notHomeRiderId)

        val cylAfterScan = store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }
        assertFalse(cylAfterScan.pendingHubScanIn)
        assertEquals(CylinderCustody.HUB, cylAfterScan.custody)
        assertEquals(InventoryState.INV_AVAIL, cylAfterScan.status)
        assertEquals(filledAfterDispatch + 1, stock.filledQty)
        assertEquals(reservedAfterDispatch + 1, stock.reservedQty)
        assertEquals(outAfterDispatch - 1, stock.outWithRidersQty)
        assertTrue(store.auditLogs.any { it.action == "HUB_SCAN_IN_RETURN" && it.details.contains("from rider RIDER-001") })

        // Second trip completes cleanly
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-001").first)
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
        assertEquals(PaymentState.SETTLED, store.activePaymentFor(order.id)?.state)
    }

    @Test
    fun `T35 exhausted free re-deliveries alerts Operations and disables customer reschedule`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(10, 0)
        store.clock = { now }
        assertEquals(1, store.settings.freeRedeliveries)

        val order = dispatchOrderToRider(store, serial = "BG-LHR-118-002", arriveAtDoor = true)

        // 1st not-home -> customer reschedules
        assertTrue(store.riderLogCallAttempt(order.id, "RIDER-001").first)
        assertTrue(
            store.riderMarkNotHome(
                orderId = order.id,
                riderId = "RIDER-001",
                callAttemptedAt = order.lastCallAttemptAt,
                waitStartedAt = now,
                demoSkip = true
            ).first
        )
        assertTrue(order.awaitingCustomerReschedule)

        val futureSlot = store.availableDeliveryWindows().first { !it.isAsap && it.endAt != null && it.endAt > now }
        assertTrue(store.customerRescheduleAfterNotHome(order.id, futureSlot).first)
        assertTrue(store.hubScanInReturnedCylinder("NODE-LHR-1", "BG-LHR-118-002").first)

        // 2nd run & 2nd not-home -> freeRedeliveries (1) exhausted
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-001").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-001").first)

        assertTrue(store.riderLogCallAttempt(order.id, "RIDER-001").first)
        assertTrue(
            store.riderMarkNotHome(
                orderId = order.id,
                riderId = "RIDER-001",
                callAttemptedAt = order.lastCallAttemptAt,
                waitStartedAt = now,
                demoSkip = true
            ).first
        )

        assertEquals(OrderStatus.ORD_EXC, order.status)
        assertEquals(2, order.notHomeCount)
        assertFalse(order.awaitingCustomerReschedule)

        val opsAlert = store.notifications.firstOrNull {
            it.surface == AppSurface.STAFF &&
                it.staffRole == StaffSubRole.OPERATIONS &&
                it.orderId == order.id &&
                it.title == "Not home — customer waiting for a call"
        }
        assertNotNull(opsAlert)

        val custNotif = store.notifications.firstOrNull {
            it.surface == AppSurface.CUSTOMER && it.orderId == order.id && it.title == "We missed you"
        }
        assertNotNull(custNotif)
        assertTrue(custNotif!!.message.contains("call you", ignoreCase = true))

        // Direct call to customerRescheduleAfterNotHome is rejected
        assertFalse(store.customerRescheduleAfterNotHome(order.id, futureSlot).first)
    }

    @Test
    fun `T36 offline not-home queues NOT_HOME once and replays cleanly when back online`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(13, 0)
        store.clock = { now }

        val order = dispatchOrderToRider(store, serial = "BG-LHR-118-002", arriveAtDoor = false)
        assertTrue(store.riderLogCallAttempt(order.id, "RIDER-001").first)

        store.setRiderOffline(true)
        val (q1Ok, q1Msg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = now,
            demoSkip = true
        )
        assertTrue(q1Msg, q1Ok)
        assertEquals(1, store.riderOutbox.count { it.orderId == order.id && it.action == "NOT_HOME" && it.syncStatus == "PENDING" })
        // Order remains Dispatched while offline
        assertEquals(OrderStatus.ORD_DISP, order.status)
        assertFalse(store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }.pendingHubScanIn)

        // Duplicate call while offline is idempotent
        val (q2Ok, q2Msg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = "RIDER-001",
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = now,
            demoSkip = true
        )
        assertTrue(q2Ok)
        assertTrue(q2Msg.contains("Already saved"))
        assertEquals(1, store.riderOutbox.count { it.orderId == order.id && it.action == "NOT_HOME" })

        // Back online and replay
        store.setRiderOffline(false)
        val (repOk, repMsg) = store.replayRiderOutbox()
        assertTrue(repMsg, repOk)
        assertEquals("APPLIED", store.riderOutbox.first { it.orderId == order.id && it.action == "NOT_HOME" }.syncStatus)
        assertEquals(OrderStatus.ORD_EXC, order.status)
        assertTrue(order.awaitingCustomerReschedule)
        assertEquals("BG-LHR-118-002", order.returningSerial)
        assertTrue(store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }.pendingHubScanIn)
    }

    @Test
    fun `T37 admin validates and saves notHomeWaitMinutes freeRedeliveries and standardSameDayByHour`() {
        val store = BlueGasViewModel().store
        val base = store.settings

        assertFalse(store.updateSettings(base.copy(notHomeWaitMinutes = 0)).first)
        assertFalse(store.updateSettings(base.copy(notHomeWaitMinutes = 31)).first)
        assertFalse(store.updateSettings(base.copy(freeRedeliveries = -1)).first)
        assertFalse(store.updateSettings(base.copy(freeRedeliveries = 4)).first)
        assertFalse(store.updateSettings(base.copy(standardCutoffHour = 18, standardSameDayByHour = 18)).first)

        val (ok, msg) = store.updateSettings(
            base.copy(
                notHomeWaitMinutes = 5,
                freeRedeliveries = 2,
                standardSameDayByHour = 22
            )
        )
        assertTrue(msg, ok)
        assertEquals(5, store.settings.notHomeWaitMinutes)
        assertEquals(2, store.settings.freeRedeliveries)
        assertEquals(22, store.settings.standardSameDayByHour)

        val audit = store.auditLogs.last { it.action == "UPDATE_SETTINGS" }
        assertTrue(audit.details.contains("notHomeWaitMinutes"))
        assertTrue(audit.details.contains("freeRedeliveries"))
        assertTrue(audit.details.contains("standardSameDayByHour"))
    }

    @Test
    fun `T38 R1 to R10 regression end-to-end verification`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(11, 0)
        store.clock = { now }

        // R3: Pickup Ready for Pickup -> hand-over wrong ID refused, right ID Completed
        val (puOrd, puErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.PICKUP,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            pickupPointId = "NODE-LHR-1"
        )
        assertNull(puErr)
        requireNotNull(puOrd)
        assertTrue(store.payOnline(puOrd.id, "SUCCESS").first)
        assertTrue(store.hubAccept(puOrd.id).first)
        assertTrue(store.startPreparing(puOrd.id).first)
        assertTrue(store.allocateCylinder(puOrd.id, "BG-LHR-118-002").first)
        assertTrue(store.markReadyForPickup(puOrd.id).first)
        assertEquals(OrderStatus.ORD_RDYPU, puOrd.status)
        assertTrue(store.customerArrivedAtDepot(puOrd.id, "COUNTER").first)
        assertFalse(
            store.completeCounterHandover(
                orderId = puOrd.id,
                pinOrRefChecked = true,
                scannedCylinderId = "BG-LHR-118-001",
                amountReceivedPaisa = puOrd.totalAmountPaisa
            ).first
        )
        assertTrue(
            store.completeCounterHandover(
                orderId = puOrd.id,
                pinOrRefChecked = true,
                scannedCylinderId = "BG-LHR-118-002",
                amountReceivedPaisa = puOrd.totalAmountPaisa
            ).first
        )
        assertEquals(OrderStatus.ORD_COMP, puOrd.status)

        // R4: Online-paid order cancelled before dispatch -> reservation released & Fully Refunded
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val rsvBefore = stock.reservedQty
        val (cancOrd, cancErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(cancErr)
        requireNotNull(cancOrd)
        assertTrue(store.payOnline(cancOrd.id, "SUCCESS").first)
        assertEquals(rsvBefore + 1, stock.reservedQty)
        assertTrue(store.cancelOrder(cancOrd.id, "Changed my mind").first)
        assertEquals(OrderStatus.ORD_CANC, cancOrd.status)
        assertEquals(rsvBefore, stock.reservedQty)
        assertEquals(PaymentState.FULLY_REFUNDED, store.refunds.first { it.orderId == cancOrd.id }.state)

        // R10: Complaint opens SupportCase without changing order status
        val compStatusBefore = puOrd.status
        val (cmpOk, cmpMsg) = store.raiseComplaint(puOrd.id, "LEAKAGE_SAFETY", "Checking valve")
        assertTrue(cmpMsg, cmpOk)
        assertEquals(compStatusBefore, puOrd.status)
        assertTrue(store.supportCases.any { it.orderId == puOrd.id })
    }
}
