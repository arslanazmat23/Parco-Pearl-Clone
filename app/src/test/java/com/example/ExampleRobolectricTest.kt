package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bluegas.data.*
import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    @Test
    fun `read string from context`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("Blue Gas", appName)
    }

    @Test
    fun `order lifecycle validates legal draft to valpend transition`() {
        val order = Order(
            id = "ORD-TEST-1",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_DRAFT,
            items = emptyList(),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 750000L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 1138500L,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )

        val result = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_VALPEND,
            actor = "Customer Cart",
            role = AccountableRole.OPERATIONS,
            trigger = "Validation requested"
        )

        assertTrue(result is TransitionResult.Success)
        assertEquals(OrderStatus.ORD_VALPEND, order.status)
    }

    @Test
    fun `order lifecycle strictly rejects illegal transition`() {
        val order = Order(
            id = "ORD-TEST-2",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_DRAFT,
            items = emptyList(),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY
        )

        // Attempt illegal jump from DRAFT to COMPLETED
        val result = OrderStateMachine.transition(
            order = order,
            targetStatus = OrderStatus.ORD_COMP,
            actor = "Test Actor",
            role = AccountableRole.OPERATIONS,
            trigger = "Direct completion"
        )

        assertTrue(result is TransitionResult.Failure)
        assertEquals(OrderStatus.ORD_DRAFT, order.status)
    }

    @Test
    fun `completed terminal status has zero outgoing transitions`() {
        val order = Order(
            id = "ORD-TEST-3",
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            status = OrderStatus.ORD_COMP,
            items = emptyList(),
            subtotalPaisa = 325000L,
            depositTotalPaisa = 0L,
            platformFeePaisa = 15000L,
            deliveryFeePaisa = 30000L,
            taxPaisa = 18500L,
            totalAmountPaisa = 388500L,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY
        )

        val legal = OrderStateMachine.getLegalNextTransitions(order)
        assertTrue(legal.isEmpty())
    }

    @Test
    fun `SharedAppState StateFlow in ViewModel provides cross-surface single source of truth`() {
        val viewModel = BlueGasViewModel()
        val initialState = viewModel.appState.value

        assertEquals(AppSurface.CUSTOMER, initialState.activeSurface)
        assertFalse(initialState.isRiderOffline)
        val initialOrderCount = initialState.orders.size

        // 1. Customer surface places an order
        val (newOrder, err) = viewModel.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = true,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(err)
        assertNotNull(newOrder)

        // StateFlow immediately reflects updated SharedAppState
        val stateAfterOrder = viewModel.appState.value
        assertEquals(initialOrderCount + 1, stateAfterOrder.orders.size)
        assertTrue(stateAfterOrder.orders.any { it.id == newOrder?.id })

        // 2. Rider surface toggles offline
        viewModel.toggleRiderOffline(true)
        val stateAfterOffline = viewModel.appState.value
        assertTrue(stateAfterOffline.isRiderOffline)

        // 3. Staff surface switches active role to Finance
        viewModel.switchStaffSubRole(StaffSubRole.FINANCE)
        val stateAfterRoleSwitch = viewModel.appState.value
        assertEquals(StaffSubRole.FINANCE, stateAfterRoleSwitch.activeStaffSubRole)

        // 4. Switch surface to Staff Portal
        viewModel.switchSurface(AppSurface.STAFF)
        assertEquals(AppSurface.STAFF, viewModel.appState.value.activeSurface)
    }

    @Test
    fun `rider lifecycle transitions order from assigned through to completed with exact serial and POD`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        // Switch to Bilal Ahmed (RIDER-001)
        store.switchRider("RIDER-001")
        val activeRider = store.getActiveRider()
        assertNotNull(activeRider)
        assertEquals("Bilal Ahmed", activeRider?.name)

        // Find the seeded pending order ORD-SEED-105 in ORD_PREP with OFFERED task
        val pendingOrder = store.orders.find { it.id == "ORD-SEED-105" }
        assertNotNull(pendingOrder)
        assertEquals(OrderStatus.ORD_PREP, pendingOrder?.status)
        assertEquals(RiderTaskStatus.OFFERED, pendingOrder?.riderTaskStatus)

        // 1. Rider accepts task (task-level acceptance, order remains ORD_PREP)
        val (acceptOk, acceptMsg) = store.riderAcceptTask("ORD-SEED-105", "RIDER-001")
        assertTrue(acceptOk)
        assertEquals(OrderStatus.ORD_PREP, pendingOrder?.status)
        assertEquals(RiderTaskStatus.ACCEPTED, pendingOrder?.riderTaskStatus)

        // 2. Rider verifies cylinder serial against allocated serial
        val (verifyOk, verifyMsg) = store.riderVerifyCylinder("ORD-SEED-105", "BG-LHR-002", "RIDER-001")
        assertTrue(verifyOk)
        assertTrue(pendingOrder?.riderCylinderVerified == true)

        // 3. Rider starts delivery trip (ORD_PREP -> ORD_DISP)
        val (dispOk, dispMsg) = store.riderStartTrip("ORD-SEED-105", "RIDER-001")
        assertTrue(dispOk)
        assertEquals(OrderStatus.ORD_DISP, pendingOrder?.status)

        // 4. Rider arrives at customer doorstep (ORD_DISP -> ORD_ATHO)
        val (arriveOk, arriveMsg) = store.riderArrive("ORD-SEED-105", "RIDER-001")
        assertTrue(arriveOk)
        assertEquals(OrderStatus.ORD_ATHO, pendingOrder?.status)

        // 5. Rider completes safety check, COD collection, and POD (ORD_ATHO -> ORD_HOC -> ORD_COMP)
        val (compOk, compMsg) = store.completeHandover(
            orderId = "ORD-SEED-105",
            pinVerified = true,
            installationResult = InstallationResult.INSTALLED,
            checklistAnswers = mapOf("Checklist step" to true),
            proofOfDeliveryNote = "Delivered. Installation verified.",
            amountReceivedPaisa = pendingOrder?.totalAmountPaisa,
            signatureCaptured = true,
            signerName = "Customer Sig",
            photoCaptured = true,
            evidenceConsent = true
        )
        assertTrue(compOk)
        assertEquals(OrderStatus.ORD_COMP, pendingOrder?.status)
        assertNotNull(pendingOrder?.podReference)

        // Verify cylinder custody moved to customer premises
        val deliveredCyl = store.cylinders.find { it.serialNumber == "BG-LHR-002" }
        assertEquals(InventoryState.INV_CONSUMED, deliveredCyl?.status)
        assertEquals(CylinderCustody.CUSTOMER_PREMISES, deliveredCyl?.custody)

        // Verify payment collected by rider (settles when Finance reconciles under BRD L3)
        val payment = store.payments.find { it.orderId == "ORD-SEED-105" }
        assertEquals(PaymentState.CASH_COLLECTED, payment?.state)
    }

    @Test
    fun `rider offline queue records events with idempotency keys and replays sequentially`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        store.switchRider("RIDER-001")
        val order = store.orders.find { it.id == "ORD-SEED-106" }
        assertNotNull(order)
        assertEquals(OrderStatus.ORD_PREP, order?.status)

        // Verify cylinder serial first
        val (vOk, _) = store.riderVerifyCylinder("ORD-SEED-106", "BG-LHR-118-003", "RIDER-001")
        assertTrue(vOk)

        // Turn offline mode ON
        store.isRiderOffline = true

        // Queue trip start while offline
        val (offlineTripOk, offlineTripMsg) = store.riderStartTrip("ORD-SEED-106", "RIDER-001")
        assertTrue(offlineTripOk)
        assertTrue(offlineTripMsg.contains("queued in Offline Outbox"))
        assertEquals(1, store.riderOutbox.size)
        assertEquals("START_TRIP", store.riderOutbox[0].action)
        assertNotNull(store.riderOutbox[0].idempotencyKey)

        // Turn offline mode OFF and replay
        store.isRiderOffline = false
        val (replayOk, replayReport) = store.replayRiderOutbox()
        assertTrue(replayOk)
        assertTrue(replayReport.contains("APPLIED"))
        assertEquals(OrderStatus.ORD_DISP, order?.status)
        assertEquals("APPLIED", store.riderOutbox[0].syncStatus)
    }

    @Test
    fun `invalid rider transition is rejected and cites legal transitions`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        val order = store.orders.find { it.id == "ORD-SEED-106" }
        assertNotNull(order)
        assertEquals(OrderStatus.ORD_PREP, order?.status)

        // Attempt invalid skip from PREP directly to ATHO
        val (arriveOk, errMsg) = store.riderArrive("ORD-SEED-106", "RIDER-001")
        assertFalse(arriveOk)
        assertTrue(errMsg.contains("cannot mark arrived"))
    }

    @Test
    fun `rider multi-step POD flow records safety checklist, COD cash, photo evidence, and digital signature`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        store.switchRider("RIDER-001")
        val order = store.orders.find { it.id == "ORD-SEED-105" }
        assertNotNull(order)

        // Advance order to ATHO
        store.riderAcceptTask("ORD-SEED-105", "RIDER-001")
        store.riderVerifyCylinder("ORD-SEED-105", "BG-LHR-002", "RIDER-001")
        store.riderStartTrip("ORD-SEED-105", "RIDER-001")
        store.riderArrive("ORD-SEED-105", "RIDER-001")

        assertEquals(OrderStatus.ORD_ATHO, order?.status)

        // Complete multi-step POD flow
        val (ok, msg) = store.completeHandover(
            orderId = "ORD-SEED-105",
            pinVerified = true,
            installationResult = InstallationResult.INSTALLED,
            checklistAnswers = mapOf("Checklist step" to true),
            proofOfDeliveryNote = "Delivered. Installation completed.",
            amountReceivedPaisa = order?.totalAmountPaisa,
            signatureCaptured = true,
            signerName = "Bilal Customer",
            photoCaptured = true,
            evidenceConsent = true,
            changeReturnedPaisa = 11500L,
            emptyCollected = true,
            emptyBrand = "BLUE GAS",
            emptySerial = "BG-LHR-OLD-812"
        )

        assertTrue(ok)
        assertEquals(OrderStatus.ORD_COMP, order?.status)

        val delivery = store.deliveries.find { it.orderId == "ORD-SEED-105" }
        assertNotNull(delivery)
        assertTrue(delivery?.nonTechnicalInstallationPassed == true)
        assertTrue(delivery?.customerSignatureCaptured == true)
        assertTrue(delivery?.photoEvidenceCaptured == true)
        assertEquals("Bilal Customer", delivery?.signerName)
        assertEquals(11500L, delivery?.changeReturnedPaisa)
    }

    @Test
    fun `staff operations lead approves and rejects partner applications with notifications and audit trail`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        // Find a pending application
        val pendingApp = store.partnerApplications.find { it.status == "PENDING_APPROVAL" }
        assertNotNull(pendingApp)
        val appId = pendingApp!!.id

        // Approve partner application
        val (apprOk, apprMsg) = store.approvePartnerApplication(appId, "Operations Lead")
        assertTrue(apprOk)
        assertEquals("APPROVED", pendingApp.status)
        assertTrue(store.auditLogs.any { it.action == "APPROVE_PARTNER" && it.entityId == appId })

        // Add a second application and reject it
        val rejectApp = PartnerApplication(
            id = "PART-TEST-99",
            type = "RIDER",
            applicantName = "Test Rider Applicant",
            city = "Lahore",
            cnic = "35201-9999999-1",
            vehicleOrShopDetails = "Honda 125, Plate ABC-999",
            status = "PENDING_APPROVAL"
        )
        store.partnerApplications.add(rejectApp)

        val (rejOk, rejMsg) = store.rejectPartnerApplication(rejectApp.id, "Invalid driving license", "Operations Lead")
        assertTrue(rejOk)
        assertEquals("REJECTED", rejectApp.status)
        assertTrue(store.auditLogs.any { it.action == "REJECT_PARTNER" && it.entityId == rejectApp.id })
    }

    @Test
    fun `staff support lead resolves dispute case without modifying order terminal status`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        // Seed a complaint for completed order ORD-SEED-101
        val (raiseOk, raiseMsg) = store.raiseComplaint(
            orderId = "ORD-SEED-101",
            issueType = "LEAKAGE_SAFETY",
            description = "Smell of gas detected near regulator nozzle."
        )
        assertTrue(raiseOk)

        val case = store.supportCases.find { it.orderId == "ORD-SEED-101" }
        assertNotNull(case)
        assertEquals("OPEN", case?.status)

        // Resolve case using Part C method
        val (resOk, resMsg) = store.resolveSupportCase(
            caseId = case!!.id,
            notes = "Technician dispatched. Replacement washer installed. Safety inspection passed.",
            actor = "Support Lead"
        )
        assertTrue(resOk)
        assertEquals("RESOLVED", case.status)
        assertNotNull(case.resolutionNotes)

        // Verify order remained ORD_COMP
        val order = store.orders.find { it.id == "ORD-SEED-101" }
        assertEquals(OrderStatus.ORD_COMP, order?.status)
    }

    @Test
    fun `staff finance lead executes full refund and updates ledger liabilities`() {
        val viewModel = BlueGasViewModel()
        val store = viewModel.store

        val order = store.orders.find { it.id == "ORD-SEED-101" }
        assertNotNull(order)
        val initialRefunds = store.refunds.size

        val (refundOk, refundMsg) = store.executeRefund(
            orderId = "ORD-SEED-101",
            amountPaisa = order!!.subtotalPaisa,
            reason = "Customer refund approval by Finance Lead",
            actor = "Finance Lead"
        )
        assertTrue(refundOk)
        assertEquals(initialRefunds + 1, store.refunds.size)

        val payment = store.payments.find { it.orderId == "ORD-SEED-101" }
        assertEquals(PaymentState.FULLY_REFUNDED, payment?.state)
        assertTrue(store.ledgerEntries.any { it.entryType == "REFUND" && it.orderId == "ORD-SEED-101" })
    }
}
