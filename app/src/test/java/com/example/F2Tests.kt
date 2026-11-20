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
class F2Tests {

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

    @Test
    fun `T46 AUTO happy path matches exact status history and hub scan-out verification`() {
        val store = BlueGasViewModel().store
        val (updOk, updMsg) = store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST))
        assertTrue(updMsg, updOk)

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

        val (payOk, payMsg) = store.payOnline(order.id, "SUCCESS")
        assertTrue(payMsg, payOk)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertEquals(DispatchMode.AUTO_RIDER_FIRST, order.dispatchModeAtConfirm)
        assertNull(order.allocatedSerial)

        val offeredRiderId = order.assignedRiderId
        assertNotNull("Expected an auto-offered rider at ORD_ASGN", offeredRiderId)
        assertEquals(RiderTaskStatus.OFFERED, order.riderTaskStatus)
        val offeredRider = store.riders.first { it.id == offeredRiderId }
        assertEquals("NODE-LHR-1", offeredRider.pointId)

        // Rider accepts -> Accepted
        val (acptOk, acptMsg) = store.riderAcceptTask(order.id, offeredRiderId)
        assertTrue(acptMsg, acptOk)
        assertEquals(OrderStatus.ORD_ACPT, order.status)
        assertEquals(RiderTaskStatus.ACCEPTED, order.riderTaskStatus)
        assertNull(order.allocatedSerial)

        // Rider arrives at hub
        val (atHubOk, atHubMsg) = store.riderAtHub(order.id, offeredRiderId)
        assertTrue(atHubMsg, atHubOk)
        assertNotNull(order.riderAtHubAt)
        assertNull(order.allocatedSerial)

        // Hub prepares + binds -> riderCylinderVerified automatically true + notification
        val (prepOk, prepMsg) = store.startPreparing(order.id)
        assertTrue(prepMsg, prepOk)
        assertNull(order.allocatedSerial)

        val (allocOk, allocMsg) = store.allocateCylinder(order.id, "BG-LHR-118-002")
        assertTrue(allocMsg, allocOk)
        assertEquals("BG-LHR-118-002", order.allocatedSerial)
        assertTrue(order.riderCylinderVerified)
        assertTrue(
            store.notifications.any {
                it.surface == AppSurface.RIDER &&
                    it.orderId == order.id &&
                    it.message == "Cylinder BG-LHR-118-002 scanned out to you by the hub"
            }
        )

        // Start trip -> Arrive -> Complete handover
        val (tripOk, tripMsg) = store.riderStartTrip(order.id, offeredRiderId)
        assertTrue(tripMsg, tripOk)
        val (arrOk, arrMsg) = store.riderArrive(order.id, offeredRiderId)
        assertTrue(arrMsg, arrOk)
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

        val fromConfirmed = store.orderStatusHistory
            .filter { it.orderId == order.id }
            .map { it.toStatus }
            .dropWhile { it != OrderStatus.ORD_CONF }
        assertEquals(
            listOf(
                OrderStatus.ORD_CONF,
                OrderStatus.ORD_RSVD,
                OrderStatus.ORD_ASGN,
                OrderStatus.ORD_ACPT,
                OrderStatus.ORD_PREP,
                OrderStatus.ORD_DISP,
                OrderStatus.ORD_ATHO,
                OrderStatus.ORD_HOC,
                OrderStatus.ORD_COMP
            ),
            fromConfirmed
        )
    }

    @Test
    fun `T47 batching prefers rider in same zone with overlapping window up to riderBatchMax then spills to second rider`() {
        val store = BlueGasViewModel().store
        assertTrue(
            store.updateSettings(
                store.settings.copy(
                    dispatchMode = DispatchMode.AUTO_RIDER_FIRST,
                    riderBatchMax = 2
                )
            ).first
        )

        val now = karachiTomorrowMillis(9, 0)
        store.clock = { now }
        val winStart = karachiTomorrowMillis(10, 0)
        val winEnd = karachiTomorrowMillis(12, 0)

        fun confirmDeliveryOrder(addressId: String): Order {
            val (o, err) = store.createOrder(
                customerId = "CUST-001",
                addressId = addressId,
                fulfilmentMode = FulfilmentMode.DELIVERY,
                sku = "SKU-LPG-11-8",
                quantity = 1,
                includeDeposit = false,
                paymentMethod = PaymentMethod.ONLINE_HOSTED,
                deliveryWindowLabel = "Today 10:00–12:00",
                windowStartAt = winStart,
                windowEndAt = winEnd
            )
            assertNull(err)
            requireNotNull(o)
            assertTrue(store.payOnline(o.id, "SUCCESS").first)
            return o
        }

        val first = confirmDeliveryOrder("ADDR-001")
        val second = confirmDeliveryOrder("ADDR-002")
        val third = confirmDeliveryOrder("ADDR-001")

        assertNotNull(first.assignedRiderId)
        assertEquals(first.assignedRiderId, second.assignedRiderId)
        assertNotNull(third.assignedRiderId)
        assertNotEquals(first.assignedRiderId, third.assignedRiderId)
    }

    @Test
    fun `T48 decline re-offers to second rider and exhausting riders notifies Operations for manual assignment`() {
        val store = BlueGasViewModel().store
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST)).first)

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

        val firstRider = order.assignedRiderId
        requireNotNull(firstRider)
        val (dec1Ok, dec1Msg) = store.riderDeclineTask(order.id, firstRider, "Too far")
        assertTrue(dec1Msg, dec1Ok)

        val secondRider = order.assignedRiderId
        assertNotNull(secondRider)
        assertNotEquals(firstRider, secondRider)

        val (dec2Ok, dec2Msg) = store.riderDeclineTask(order.id, secondRider!!, "Vehicle issue")
        assertTrue(dec2Msg, dec2Ok)
        assertNull(order.assignedRiderId)
        assertNull(order.riderTaskStatus)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertTrue(
            store.notifications.any {
                it.surface == AppSurface.STAFF &&
                    it.staffRole == StaffSubRole.OPERATIONS &&
                    it.message.contains("No free rider at") &&
                    it.message.contains("needs a rider")
            }
        )

        // Manual assignRider at ORD_ASGN without allocatedSerial works for AUTO order
        val (manOk, manMsg) = store.assignRider(order.id, "RIDER-001")
        assertTrue(manMsg, manOk)
        assertEquals("RIDER-001", order.assignedRiderId)
        assertEquals(RiderTaskStatus.OFFERED, order.riderTaskStatus)
    }

    @Test
    fun `T49 switching settings dispatchMode while orders are live preserves dispatchModeAtConfirm`() {
        val store = BlueGasViewModel().store

        // Confirm first order under HUB_FIRST
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.HUB_FIRST)).first)
        val (hubOrder, err1) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err1)
        requireNotNull(hubOrder)
        assertTrue(store.payOnline(hubOrder.id, "SUCCESS").first)
        assertEquals(DispatchMode.HUB_FIRST, hubOrder.dispatchModeAtConfirm)
        assertNull(hubOrder.assignedRiderId)

        // Switch setting to AUTO_RIDER_FIRST and confirm second order
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST)).first)
        val (autoOrder, err2) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-002",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err2)
        requireNotNull(autoOrder)
        assertTrue(store.payOnline(autoOrder.id, "SUCCESS").first)
        assertEquals(DispatchMode.AUTO_RIDER_FIRST, autoOrder.dispatchModeAtConfirm)
        assertNotNull(autoOrder.assignedRiderId)

        // Switch setting back to HUB_FIRST -> live AUTO order still completes via AUTO flow
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.HUB_FIRST)).first)
        assertTrue(store.riderAcceptTask(autoOrder.id, autoOrder.assignedRiderId).first)
        assertEquals(OrderStatus.ORD_ACPT, autoOrder.status)

        // And live HUB_FIRST order still follows HUB_FIRST flow (hubAccept -> startPreparing -> allocate -> assignRider)
        assertTrue(store.hubAccept(hubOrder.id).first)
        assertTrue(store.startPreparing(hubOrder.id).first)
        assertTrue(store.allocateCylinder(hubOrder.id, "BG-LHR-118-002").first)
        assertFalse(hubOrder.riderCylinderVerified)
    }

    @Test
    fun `T50 HUB_FIRST flows match Chunk2 T1 and Chunk7 T31 unchanged`() {
        // Explicit re-verification of T1 stock lifecycle and T31 promise rule under HUB_FIRST
        Chunk2StoreTests().`T1 stock flow consumes filled and reserved on dispatch and clears outWithRiders on handover`()
        Chunk7StoreTests().`T31 C7_0 ASAP Standard promise uses standardSameDayByHour before and after cutoff`()
    }

    @Test
    fun `T51 AUTO not-home reschedule returns order to ORD_ASGN with rider auto-offered`() {
        val store = BlueGasViewModel().store
        val now = karachiTomorrowMillis(10, 0)
        store.clock = { now }
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST)).first)

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
        val riderId = order.assignedRiderId!!
        assertTrue(store.riderAcceptTask(order.id, riderId).first)
        assertTrue(store.riderAtHub(order.id, riderId).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, "BG-LHR-118-002").first)
        assertTrue(store.riderStartTrip(order.id, riderId).first)
        assertTrue(store.riderLogCallAttempt(order.id, riderId).first)

        val (nhOk, nhMsg) = store.riderMarkNotHome(
            orderId = order.id,
            riderId = riderId,
            callAttemptedAt = order.lastCallAttemptAt,
            waitStartedAt = now,
            demoSkip = true
        )
        assertTrue(nhMsg, nhOk)
        assertEquals(OrderStatus.ORD_EXC, order.status)
        assertTrue(order.awaitingCustomerReschedule)

        val nextWin = DeliveryWindowOption(
            label = "Tomorrow 14:00–16:00",
            startAt = karachiTomorrowMillis(14, 0) + 86_400_000L,
            endAt = karachiTomorrowMillis(16, 0) + 86_400_000L,
            isAsap = false
        )
        val (resOk, resMsg) = store.customerRescheduleAfterNotHome(order.id, nextWin)
        assertTrue(resMsg, resOk)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertNotNull(order.assignedRiderId)
        assertEquals(RiderTaskStatus.OFFERED, order.riderTaskStatus)
        assertNull(order.riderAtHubAt)
    }

    @Test
    fun `T52 AUTO offline rider accept queues and replays once to Accepted`() {
        val store = BlueGasViewModel().store
        assertTrue(store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST)).first)

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
        val riderId = order.assignedRiderId!!

        store.setRiderOffline(true)
        val (qOk1, qMsg1) = store.riderAcceptTask(order.id, riderId)
        assertTrue(qMsg1, qOk1)
        val (qOk2, _) = store.riderAcceptTask(order.id, riderId)
        assertTrue(qOk2)
        assertEquals(1, store.riderOutbox.count { it.orderId == order.id && it.action == "ACCEPT_TASK" })
        assertEquals(OrderStatus.ORD_ASGN, order.status)

        store.setRiderOffline(false)
        val (repOk, repMsg) = store.replayRiderOutbox()
        assertTrue(repMsg, repOk)
        assertEquals(OrderStatus.ORD_ACPT, order.status)
        assertEquals(RiderTaskStatus.ACCEPTED, order.riderTaskStatus)
        assertEquals("APPLIED", store.riderOutbox.first { it.orderId == order.id && it.action == "ACCEPT_TASK" }.syncStatus)
    }
}
