package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.customer.mapCustomerReturnProgress
import com.example.bluegas.ui.rider.maskCylinderIdHint
import com.example.bluegas.ui.rider.riderCashStatusLabel
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Chunk5StoreTests {

    private fun prepareAuthorizedReturn(
        store: BlueGasStore,
        customerId: String = "CUST-001",
        serial: String = "BG-LHR-001",
        preferredWindow: String = "Tomorrow 10:00–12:00"
    ): String {
        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = customerId,
            cylinderSerial = serial,
            reason = "Switching to piped gas",
            preferredWindow = preferredWindow
        )
        assertTrue(caseId, reqOk)
        assertTrue(
            store.moveCase(
                caseId = caseId,
                target = L6Status.UNDER_REVIEW,
                reason = "Verified serial ownership",
                actor = AccountableRole.SUPPORT.displayName,
                role = AccountableRole.SUPPORT
            ).first
        )
        assertTrue(
            store.moveCase(
                caseId = caseId,
                target = L6Status.RETURN_AUTHORIZED,
                reason = "Approved for rider collection",
                actor = AccountableRole.SUPPORT.displayName,
                role = AccountableRole.SUPPORT
            ).first
        )
        return caseId
    }

    @Test
    fun `T19 rider decline requires reason clears task and allows Operations to offer another rider`() {
        val store = BlueGasViewModel().store
        val caseId = prepareAuthorizedReturn(store)

        val (offer1Ok, offer1Msg) = store.offerReturnCollection(caseId, "RIDER-001", AccountableRole.OPERATIONS.displayName)
        assertTrue(offer1Msg, offer1Ok)

        // Declining with blank reason is refused
        val (blankOk, blankMsg) = store.riderDeclineReturn(caseId, "RIDER-001", "   ")
        assertFalse(blankOk)
        assertEquals("Reason is required.", blankMsg)

        // Declining with reason clears collectionRiderId & collectionTask, leaves L6 at AWAITING_ITEM_RETURN, notifies Operations
        val (decOk, decMsg) = store.riderDeclineReturn(caseId, "RIDER-001", "Vehicle full")
        assertTrue(decMsg, decOk)

        val declinedCase = store.returnCases.first { it.id == caseId }
        assertNull(declinedCase.collectionRiderId)
        assertNull(declinedCase.collectionTask)
        assertEquals(L6Status.AWAITING_ITEM_RETURN, declinedCase.status)
        assertTrue(
            store.notifications.any {
                it.surface == AppSurface.STAFF &&
                    it.staffRole == StaffSubRole.OPERATIONS &&
                    it.canonicalStatus == "COLLECTION_DECLINED"
            }
        )

        // Operations offers another rider (RIDER-002), who accepts
        val (offer2Ok, offer2Msg) = store.offerReturnCollection(caseId, "RIDER-002", AccountableRole.OPERATIONS.displayName)
        assertTrue(offer2Msg, offer2Ok)
        assertEquals("RIDER-002", declinedCase.collectionRiderId)
        assertEquals("OFFERED", declinedCase.collectionTask)

        val (accOk, accMsg) = store.riderAcceptReturn(caseId, "RIDER-002")
        assertTrue(accMsg, accOk)
        assertEquals("ACCEPTED", declinedCase.collectionTask)
    }

    @Test
    fun `T20 collect guards reject wrong ID and missing photo while mask hides prefix and hand-in sets AT_HUB`() {
        val store = BlueGasViewModel().store
        val caseId = prepareAuthorizedReturn(store, serial = "BG-LHR-001")

        // Masked hint reveals only last 3 characters
        assertEquals("••••-731", maskCylinderIdHint("BG-LHR-731"))
        assertEquals("••••-001", maskCylinderIdHint("BG-LHR-001"))

        assertTrue(store.offerReturnCollection(caseId, "RIDER-001", AccountableRole.OPERATIONS.displayName).first)
        assertTrue(store.riderAcceptReturn(caseId, "RIDER-001").first)

        val rc = store.returnCases.first { it.id == caseId }
        assertEquals("ACCEPTED", rc.collectionTask)

        // Wrong cylinder ID refused; stays ACCEPTED
        val (wrongOk, wrongMsg) = store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-999", photoCaptured = true)
        assertFalse(wrongOk)
        assertEquals("This is not the cylinder on the return request.", wrongMsg)
        assertEquals("ACCEPTED", rc.collectionTask)

        // Photo not captured refused; stays ACCEPTED
        val (noPhotoOk, noPhotoMsg) = store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-001", photoCaptured = false)
        assertFalse(noPhotoOk)
        assertEquals("Photo evidence of return cylinder is required.", noPhotoMsg)
        assertEquals("ACCEPTED", rc.collectionTask)

        // Correct ID + photo captured -> COLLECTED + RIDER_VAN
        val (collectOk, collectMsg) = store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-001", photoCaptured = true)
        assertTrue(collectMsg, collectOk)
        assertEquals("COLLECTED", rc.collectionTask)
        val collectedCyl = store.cylinders.first { it.serialNumber == "BG-LHR-001" }
        assertEquals(CylinderCustody.RIDER_VAN, collectedCyl.custody)

        // Hand in at hub -> AT_HUB + HUB custody
        val (handInOk, handInMsg) = store.riderHandInReturn(caseId, "RIDER-001")
        assertTrue(handInMsg, handInOk)
        assertEquals("AT_HUB", rc.collectionTask)
        val hubCyl = store.cylinders.first { it.serialNumber == "BG-LHR-001" }
        assertEquals(CylinderCustody.HUB, hubCyl.custody)
    }

    @Test
    fun `T21 rider cash status labels reflect hub cashier vs Finance handover lifecycle`() {
        val hubSubmitted = CashHandover(
            id = "CH-1",
            collectorType = "RIDER",
            collectorId = "RIDER-001",
            recipient = "HUB",
            orderIds = listOf("ORD-1"),
            expectedPaisa = 500_000L,
            declaredPaisa = 500_000L,
            status = "SUBMITTED"
        )
        assertEquals("Waiting for hub cashier", riderCashStatusLabel(hubSubmitted))
        assertEquals("Received by hub", riderCashStatusLabel(hubSubmitted.copy(status = "RECEIVED_BY_HUB")))
        assertEquals("Reconciled", riderCashStatusLabel(hubSubmitted.copy(status = "RECONCILED")))
        assertEquals("Reconciled", riderCashStatusLabel(hubSubmitted.copy(status = "RECONCILED_WITH_DIFFERENCE")))

        val financeSubmitted = hubSubmitted.copy(recipient = "Finance", status = "SUBMITTED")
        assertEquals("Waiting for Finance", riderCashStatusLabel(financeSubmitted))
        assertEquals("Reconciled", riderCashStatusLabel(financeSubmitted.copy(status = "RECONCILED")))
        assertEquals("Reconciled", riderCashStatusLabel(financeSubmitted.copy(status = "RECONCILED_WITH_DIFFERENCE")))
    }

    @Test
    fun `T22 rider offline blocks all collection actions and does not queue in outbox`() {
        val store = BlueGasViewModel().store
        val caseId = prepareAuthorizedReturn(store)
        assertTrue(store.offerReturnCollection(caseId, "RIDER-001", AccountableRole.OPERATIONS.displayName).first)

        val expectedMsg = "Collections need a connection. Switch Offline off to continue."
        val initialOutboxSize = store.riderOutbox.size

        // Turn offline ON before accept/decline
        store.setRiderOffline(true)

        val (accOffOk, accOffMsg) = store.riderAcceptReturn(caseId, "RIDER-001")
        assertFalse(accOffOk)
        assertEquals(expectedMsg, accOffMsg)

        val (decOffOk, decOffMsg) = store.riderDeclineReturn(caseId, "RIDER-001", "Cannot reach")
        assertFalse(decOffOk)
        assertEquals(expectedMsg, decOffMsg)

        // Advance to ACCEPTED while online so we can also guard collect & hand-in
        store.setRiderOffline(false)
        assertTrue(store.riderAcceptReturn(caseId, "RIDER-001").first)
        store.setRiderOffline(true)

        val (colOffOk, colOffMsg) = store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-001", photoCaptured = true)
        assertFalse(colOffOk)
        assertEquals(expectedMsg, colOffMsg)

        // Advance to COLLECTED while online so we can guard hand-in
        store.setRiderOffline(false)
        assertTrue(store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-001", photoCaptured = true).first)
        store.setRiderOffline(true)

        val (handOffOk, handOffMsg) = store.riderHandInReturn(caseId, "RIDER-001")
        assertFalse(handOffOk)
        assertEquals(expectedMsg, handOffMsg)

        // Nothing added to offline outbox across all 4 collection calls
        assertEquals(initialOutboxSize, store.riderOutbox.size)
        assertEquals("COLLECTED", store.returnCases.first { it.id == caseId }.collectionTask)
    }

    @Test
    fun `T23 full customer return and refund journey advances cleanly through all 7 progress steps`() {
        val store = BlueGasViewModel().store

        // 1. Customer CUST-003 captures CNIC, places first-cylinder online order at Islamabad Hub, and receives delivery
        assertTrue(store.captureCnic("CUST-003", "35201-3344556-5").first)
        val (order, err) = store.createOrder(
            customerId = "CUST-003",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(err)
        requireNotNull(order)
        val serial = "BG-ISB-118-002"

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, serial).first)
        assertTrue(store.assignRider(order.id, "RIDER-003").first)
        store.switchRider("RIDER-003")
        assertTrue(store.riderAcceptTask(order.id, "RIDER-003").first)
        assertTrue(store.riderVerifyCylinder(order.id, serial, "RIDER-003").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-003").first)
        assertTrue(store.riderArrive(order.id, "RIDER-003").first)
        assertTrue(
            store.completeHandover(
                orderId = order.id,
                pinVerified = true,
                installationResult = InstallationResult.INSTALLED,
                checklistAnswers = mapOf("Leak test passed" to true),
                photoCaptured = true,
                signatureCaptured = true,
                signerName = "Zainab Raza"
            ).first
        )
        assertEquals(OrderStatus.ORD_COMP, order.status)
        val heldDeposit = store.depositAccounts.first { it.customerId == "CUST-003" && it.cylinderSerial == serial }
        assertEquals(DepositStatus.HELD, heldDeposit.status)
        val depositRupees = formatRupees(heldDeposit.amountPaisa)

        // 2. Customer requests return with preferred window -> Step 1
        val preferredWindow = "Tomorrow 14:00–16:00"
        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = "CUST-003",
            cylinderSerial = serial,
            reason = "Moving to another city",
            preferredWindow = preferredWindow
        )
        assertTrue(caseId, reqOk)
        val rc = store.returnCases.first { it.id == caseId }

        var p = mapCustomerReturnProgress(rc)
        assertEquals(0, p.currentStepIndex)
        assertEquals("Request received", p.stepTitles[0])

        // 3. Support reviews -> Step 1, then authorizes -> Step 2
        assertTrue(store.moveCase(caseId, L6Status.UNDER_REVIEW, "Checking custody", actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT).first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(0, p.currentStepIndex)

        assertTrue(store.moveCase(caseId, L6Status.RETURN_AUTHORIZED, "Authorized pickup", actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT).first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(1, p.currentStepIndex)
        assertEquals("Collection being arranged", p.stepTitles[1])

        // 4. Operations offers to RIDER-003, rider accepts -> Step 3 with preferred window
        assertTrue(store.offerReturnCollection(caseId, "RIDER-003", AccountableRole.OPERATIONS.displayName).first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(2, p.currentStepIndex)
        assertEquals("Rider will collect your cylinder", p.stepTitles[2])
        assertTrue(p.statusDetail.contains(preferredWindow))

        assertTrue(store.riderAcceptReturn(caseId, "RIDER-003").first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(2, p.currentStepIndex)
        assertTrue(p.statusDetail.contains(preferredWindow))

        // 5. Rider collects with right ID + photo, hands in at hub -> Step 4
        assertTrue(store.riderCollectCylinder(caseId, "RIDER-003", serial, photoCaptured = true).first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(3, p.currentStepIndex)
        assertEquals("Cylinder collected — being checked", p.stepTitles[3])

        assertTrue(store.riderHandInReturn(caseId, "RIDER-003").first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(3, p.currentStepIndex)

        // 6. Hub records GOOD condition -> Step 5
        assertTrue(store.hubRecordReturnCondition(caseId, ReturnCondition.GOOD, actor = AccountableRole.HUB.displayName).first)
        p = mapCustomerReturnProgress(rc)
        assertEquals(4, p.currentStepIndex)
        assertEquals("Checked — refund being prepared", p.stepTitles[4])

        // 7. Finance requests deposit refund -> Step 6; simulate fail -> retry copy; succeed + close -> Step 7
        assertTrue(store.requestDepositRefund(caseId, deductionPaisa = 0L, actor = AccountableRole.FINANCE.displayName).first)
        val refund = store.refunds.first { it.linkedCaseId == caseId }
        p = mapCustomerReturnProgress(rc, refund)
        assertEquals(5, p.currentStepIndex)
        assertEquals("Refund in progress", p.stepTitles[5])

        assertTrue(store.processRefund(refund.id, succeed = false).first)
        val failedRef = store.refunds.first { it.id == refund.id }
        p = mapCustomerReturnProgress(rc, failedRef)
        assertEquals(5, p.currentStepIndex)
        assertEquals("We're retrying your refund payment.", p.statusDetail)

        assertTrue(store.retryRefund(refund.id).first)
        assertTrue(store.processRefund(refund.id, succeed = true).first)
        val succeededRef = store.refunds.first { it.id == refund.id }
        assertEquals(DepositStatus.REFUNDED, heldDeposit.status)

        assertTrue(store.moveCase(caseId, L6Status.CASE_CLOSED, "Deposit refunded", actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT).first)
        p = mapCustomerReturnProgress(rc, succeededRef)
        assertEquals(6, p.currentStepIndex)
        assertEquals("Deposit refunded — $depositRupees", p.stepTitles[6])
    }
}
