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
class Chunk2StoreTests {

    @Test
    fun `T1 stock flow consumes filled and reserved on dispatch and clears outWithRiders on handover`() {
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

        val (payOk, payMsg) = store.payOnline(order.id, "SUCCESS")
        assertTrue(payMsg, payOk)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertEquals(startReserved + 1, stock.reservedQty)

        val (acptOk, acptMsg) = store.hubAccept(order.id)
        assertTrue(acptMsg, acptOk)

        val (prepOk, prepMsg) = store.startPreparing(order.id)
        assertTrue(prepMsg, prepOk)

        val (allocOk, allocMsg) = store.allocateCylinder(order.id, "BG-LHR-118-002")
        assertTrue(allocMsg, allocOk)

        val (assignOk, assignMsg) = store.assignRider(order.id, "RIDER-001")
        assertTrue(assignMsg, assignOk)

        store.switchRider("RIDER-001")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, "BG-LHR-118-002", "RIDER-001").first)

        val (dispOk, dispMsg) = store.riderStartTrip(order.id, "RIDER-001")
        assertTrue(dispMsg, dispOk)
        assertEquals(OrderStatus.ORD_DISP, order.status)
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startReserved, stock.reservedQty)
        assertEquals(startOutWithRiders + 1, stock.outWithRidersQty)

        val (arrOk, arrMsg) = store.riderArrive(order.id, "RIDER-001")
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
        assertEquals(OrderStatus.ORD_COMP, order.status)
        assertEquals(startOutWithRiders, stock.outWithRidersQty)
        assertEquals(startFilled - 1, stock.filledQty)
    }

    @Test
    fun `T2 bind block rejects test-due cylinder G-42`() {
        val store = BlueGasViewModel().store

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

        val (ok, msg) = store.allocateCylinder(order.id, "BG-LHR-118-004")
        assertFalse(ok)
        assertTrue("Expected safety test block message, got: $msg", msg.contains("safety test", ignoreCase = true))
        assertNull(order.allocatedSerial)
    }

    @Test
    fun `T3 rider training check rejects untrained rider G-BUG-2`() {
        val store = BlueGasViewModel().store

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

        store.riders.add(
            Rider(
                id = "RIDER-UNTRAINED",
                name = "Kamran Untrained",
                phone = "+92-300-1112233",
                cnic = "35201-0000000-1",
                zoneId = "ZONE-LHR-1",
                zoneName = "Gulberg & Cantt Central",
                nodeId = "NODE-LHR-1",
                vehicleNumber = "LEZ-111",
                status = "ACTIVE",
                onboardingStatus = "APPROVED",
                pointId = "NODE-LHR-1",
                installationTrained = false
            )
        )

        val (ok, msg) = store.assignRider(order.id, "RIDER-UNTRAINED")
        assertFalse(ok)
        assertEquals("This rider has not completed installation training.", msg)
        assertNull(order.assignedRiderId)
    }

    @Test
    fun `T4 exception recovery from van sets pendingHubScanIn and hub scan-in restores stock`() {
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

        val (excOk, excMsg) = store.riderReportIssue(
            orderId = order.id,
            reason = ExceptionReason.DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE,
            details = "Customer unavailable at doorstep",
            riderId = "RIDER-001"
        )
        assertTrue(excMsg, excOk)
        assertEquals(OrderStatus.ORD_EXC, order.status)

        val (recOk, recMsg) = store.recoverException(order.id, OrderStatus.ORD_ASGN, "Customer unavailable")
        assertTrue(recMsg, recOk)
        assertEquals(OrderStatus.ORD_ASGN, order.status)
        assertNull(order.allocatedSerial)
        assertNull(order.assignedRiderId)
        assertNull(order.riderTaskStatus)
        assertFalse(order.riderCylinderVerified)
        assertNull(order.riderOfferedAt)

        val cylAfterRecovery = store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }
        assertTrue(cylAfterRecovery.pendingHubScanIn)
        assertNull(cylAfterRecovery.allocatedOrderId)
        assertEquals(CylinderCustody.RIDER_VAN, cylAfterRecovery.custody)
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startReserved, stock.reservedQty)
        assertEquals(startOutWithRiders + 1, stock.outWithRidersQty)

        val (wrongHubOk, wrongHubMsg) = store.hubScanInReturnedCylinder("NODE-ISB-1", "BG-LHR-118-002")
        assertFalse(wrongHubMsg, wrongHubOk)

        val (scanOk, scanMsg) = store.hubScanInReturnedCylinder("NODE-LHR-1", "BG-LHR-118-002")
        assertTrue(scanMsg, scanOk)

        val cylAfterScan = store.cylinders.first { it.serialNumber == "BG-LHR-118-002" }
        assertFalse(cylAfterScan.pendingHubScanIn)
        assertEquals(CylinderCustody.HUB, cylAfterScan.custody)
        assertEquals(InventoryState.INV_AVAIL, cylAfterScan.status)
        assertEquals(startOutWithRiders, stock.outWithRidersQty)
        assertEquals(startFilled, stock.filledQty)
        assertEquals(startReserved + 1, stock.reservedQty)
    }

    @Test
    fun `T5 rider decline allows Operations to re-offer to another rider`() {
        val store = BlueGasViewModel().store
        val order = store.orders.first { it.id == "ORD-SEED-112" }

        val (offer1Ok, offer1Msg) = store.assignRider("ORD-SEED-112", "RIDER-001")
        assertTrue(offer1Msg, offer1Ok)

        store.switchRider("RIDER-001")
        val (decOk, decMsg) = store.riderDeclineTask("ORD-SEED-112", "Flat tire on route")
        assertTrue(decMsg, decOk)
        assertEquals(RiderTaskStatus.DECLINED, order.riderTaskStatus)
        assertNull(order.assignedRiderId)
        assertEquals(OrderStatus.ORD_PREP, order.status)

        val (offer2Ok, offer2Msg) = store.assignRider("ORD-SEED-112", "RIDER-002")
        assertTrue(offer2Msg, offer2Ok)
        assertEquals(RiderTaskStatus.OFFERED, order.riderTaskStatus)
        assertEquals("RIDER-002", order.assignedRiderId)
    }

    @Test
    fun `T6 pickup counter handover validates bound cylinder and avoids outWithRiders G-BUG-1`() {
        val store = BlueGasViewModel().store
        val order = store.orders.first { it.id == "ORD-SEED-107" }
        val stock = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        val startFilled = stock.filledQty
        val startOutWithRiders = stock.outWithRidersQty

        val (arrOk, arrMsg) = store.customerArrivedAtDepot("ORD-SEED-107", "COUNTER")
        assertTrue(arrMsg, arrOk)
        assertEquals(OrderStatus.ORD_ATHO, order.status)

        val (wrongOk, wrongMsg) = store.completeCounterHandover(
            orderId = "ORD-SEED-107",
            pinOrRefChecked = true,
            scannedCylinderId = "BG-WRONG-SERIAL",
            amountReceivedPaisa = order.totalAmountPaisa
        )
        assertFalse(wrongOk)
        assertEquals("This is not the cylinder bound to the order.", wrongMsg)
        assertEquals(OrderStatus.ORD_ATHO, order.status)

        val (hoOk, hoMsg) = store.completeCounterHandover(
            orderId = "ORD-SEED-107",
            pinOrRefChecked = true,
            scannedCylinderId = "BG-LHR-118-001",
            amountReceivedPaisa = order.totalAmountPaisa
        )
        assertTrue(hoMsg, hoOk)
        assertEquals(OrderStatus.ORD_COMP, order.status)
        assertEquals(startFilled - 1, stock.filledQty)
        assertEquals(startOutWithRiders, stock.outWithRidersQty)
    }
}
