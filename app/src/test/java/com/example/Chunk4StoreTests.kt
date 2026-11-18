package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.customer.customerDepositStatusLabel
import com.example.bluegas.ui.customer.customerReturnWindowOptions
import com.example.bluegas.ui.customer.mapCustomerReturnProgress
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Chunk4StoreTests {

    @Test
    fun `C4_0 Islamabad hub has 5 bindable 11_8 kg cylinders while BG-ISB-014 remains test-due`() {
        val store = BlueGasViewModel().store
        val isb118 = store.cylinders.filter { it.pointId == "NODE-ISB-1" && it.sku == "SKU-LPG-11-8" }
        assertEquals(6, isb118.size)

        val overdue = isb118.first { it.serialNumber == "BG-ISB-014" }
        assertEquals("2025-08-10", overdue.nextTestDueDate)

        val bindable = isb118.filter { it.status == InventoryState.INV_AVAIL && it.serialNumber != "BG-ISB-014" }
        assertEquals(5, bindable.size)
        assertEquals(
            listOf(
                "BG-ISB-118-001",
                "BG-ISB-118-002",
                "BG-ISB-118-003",
                "BG-ISB-118-004",
                "BG-ISB-118-005"
            ),
            bindable.map { it.serialNumber }.sorted()
        )
        assertTrue(bindable.all { it.lastFillWeightKg == 11.8 && it.custody == CylinderCustody.HUB })

        // Verify an Islamabad order can bind BG-ISB-118-002 but refuses overdue BG-ISB-014
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
        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)

        val (overdueOk, overdueMsg) = store.allocateCylinder(order.id, "BG-ISB-014")
        assertFalse(overdueOk)
        assertTrue(overdueMsg, overdueMsg.contains("safety test", ignoreCase = true))

        val (ok, msg) = store.allocateCylinder(order.id, "BG-ISB-118-002")
        assertTrue(msg, ok)
        assertEquals("BG-ISB-118-002", order.allocatedSerial)
    }

    @Test
    fun `T14 CNIC required for first deposit order and capture validates format and logs audit`() {
        val store = BlueGasViewModel().store

        // Before capture: deposit requires CNIC, refill does not
        assertTrue(store.needsCnicForDeposit("CUST-003", OrderType.NEW_CYLINDER))
        assertFalse(store.needsCnicForDeposit("CUST-003", OrderType.REFILL_EXCHANGE))

        val (blockedOrder, blockedErr) = store.createOrder(
            customerId = "CUST-003",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(blockedOrder)
        assertEquals("Please add your CNIC to take a cylinder on deposit.", blockedErr)

        // Malformed CNIC rejected
        val (badOk, badMsg) = store.captureCnic("CUST-003", "12345")
        assertFalse(badOk)
        assertTrue(badMsg.contains("13-digit CNIC"))
        assertNull(store.customers.first { it.id == "CUST-003" }.cnicCapturedAt)

        // Valid CNIC captured + audited
        val (goodOk, goodMsg) = store.captureCnic("CUST-003", "35201-3344556-5")
        assertTrue(goodMsg, goodOk)
        val updatedCust = store.customers.first { it.id == "CUST-003" }
        assertEquals("35201-3344556-5", updatedCust.cnic)
        assertTrue(updatedCust.isCnicVerified)
        assertNotNull(updatedCust.cnicCapturedAt)
        assertFalse(store.needsCnicForDeposit("CUST-003", OrderType.NEW_CYLINDER))
        assertTrue(store.auditLogs.any { it.entityId == "CUST-003" && it.action == "CAPTURE_CNIC" })

        // Now deposit order succeeds
        val (allowedOrder, allowedErr) = store.createOrder(
            customerId = "CUST-003",
            addressId = "ADDR-004",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(allowedErr)
        assertNotNull(allowedOrder)
        assertTrue((allowedOrder?.depositTotalPaisa ?: 0L) > 0L)
    }

    @Test
    fun `T15 backup cylinder deposit limit blocks duplicate size when disallowed and honors maxDepositCylindersPerSize`() {
        val store = BlueGasViewModel().store
        assertTrue(store.captureCnic("CUST-001", "35201-1122334-1").first)

        // Default: backupCylinderAllowed = false; CUST-001 already holds 1x 11.8 kg DEPOSIT_HELD (HOLD-001)
        assertFalse(store.settings.backupCylinderAllowed)
        val (blockedSameSize, blockedMsg) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(blockedSameSize)
        assertEquals(
            "You already hold the maximum number of 11.8 kg cylinders on deposit. Order a refill instead.",
            blockedMsg
        )

        // Different size (45.4 kg) is still allowed
        val (allowedOtherSize, otherErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-45-4",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(otherErr)
        assertNotNull(allowedOtherSize)

        // Enabling backupCylinderAllowed with limit 2 allows 1 more 11.8 kg deposit, but blocks qty=2
        store.updateSettings(store.settings.copy(backupCylinderAllowed = true, maxDepositCylindersPerSize = 2))
        val (allowedSecond, secondErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(secondErr)
        assertNotNull(allowedSecond)

        val (exceedsTwo, exceedsMsg) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 2,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(exceedsTwo)
        assertEquals(
            "You already hold the maximum number of 11.8 kg cylinders on deposit. Order a refill instead.",
            exceedsMsg
        )
    }

    @Test
    fun `T16 customer return request captures preferred window blocks duplicate and maps 7-step progress copy`() {
        val store = BlueGasViewModel().store

        // Window options come dynamically from PlatformSettings
        val windows = customerReturnWindowOptions(store.settings)
        assertTrue(windows.isNotEmpty())
        assertTrue(windows.any { it.startsWith("Tomorrow ") })

        // Blank reason blocked
        val (blankOk, blankMsg) = store.requestCylinderReturn(
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            reason = "   ",
            preferredWindow = windows.first()
        )
        assertFalse(blankOk)
        assertEquals("Reason is required.", blankMsg)

        // Valid request with preferred collection window
        val chosenWindow = windows.last()
        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            reason = "Moving house",
            preferredWindow = chosenWindow
        )
        assertTrue(caseId, reqOk)

        val rc = store.returnCases.first { it.id == caseId }
        assertEquals(chosenWindow, rc.preferredCollectionWindow)
        assertEquals(L6Status.CASE_OPENED, rc.status)
        assertTrue(store.notifications.any { it.surface == AppSurface.CUSTOMER && it.code == "NTF-20" })
        assertTrue(store.notifications.any { it.surface == AppSurface.STAFF && it.code == "NTF-10" })

        // Duplicate request blocked while open
        val (dupOk, dupMsg) = store.requestCylinderReturn(
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            reason = "Duplicate"
        )
        assertFalse(dupOk)
        assertTrue(dupMsg.contains("already exists"))

        // 7-step progress mapping assertions
        val s1Open = mapCustomerReturnProgress(rc.copy(status = L6Status.CASE_OPENED))
        assertEquals(7, s1Open.stepTitles.size)
        assertEquals(0, s1Open.currentStepIndex)
        assertEquals("Request received", s1Open.stepTitles[0])

        val s1Review = mapCustomerReturnProgress(rc.copy(status = L6Status.UNDER_REVIEW))
        assertEquals(0, s1Review.currentStepIndex)

        val s2Auth = mapCustomerReturnProgress(rc.copy(status = L6Status.RETURN_AUTHORIZED))
        assertEquals(1, s2Auth.currentStepIndex)
        assertEquals("Collection being arranged", s2Auth.stepTitles[1])

        val s3Offered = mapCustomerReturnProgress(rc.copy(status = L6Status.AWAITING_ITEM_RETURN, collectionTask = "OFFERED"))
        assertEquals(2, s3Offered.currentStepIndex)
        assertEquals("Rider will collect your cylinder", s3Offered.stepTitles[2])
        assertTrue(s3Offered.statusDetail.contains(chosenWindow))

        val s3Accepted = mapCustomerReturnProgress(rc.copy(status = L6Status.AWAITING_ITEM_RETURN, collectionTask = "ACCEPTED"))
        assertEquals(2, s3Accepted.currentStepIndex)
        assertTrue(s3Accepted.statusDetail.contains(chosenWindow))

        val s4Collected = mapCustomerReturnProgress(rc.copy(status = L6Status.AWAITING_ITEM_RETURN, collectionTask = "COLLECTED"))
        assertEquals(3, s4Collected.currentStepIndex)
        assertEquals("Cylinder collected — being checked", s4Collected.stepTitles[3])

        val s4AtHub = mapCustomerReturnProgress(rc.copy(status = L6Status.AWAITING_ITEM_RETURN, collectionTask = "AT_HUB"))
        assertEquals(3, s4AtHub.currentStepIndex)

        val s5Checked = mapCustomerReturnProgress(rc.copy(status = L6Status.ITEM_RETURNED))
        assertEquals(4, s5Checked.currentStepIndex)
        assertEquals("Checked — refund being prepared", s5Checked.stepTitles[4])

        val s6Pending = mapCustomerReturnProgress(rc.copy(status = L6Status.REFUND_REQUESTED))
        assertEquals(5, s6Pending.currentStepIndex)
        assertEquals("Refund in progress", s6Pending.stepTitles[5])

        val failedRefund = Refund(
            id = "REF-FAIL",
            paymentId = "PAY-1",
            orderId = rc.orderId,
            amountPaisa = rc.requestedAmountPaisa,
            reason = "Deposit refund",
            executedBy = "Finance",
            state = PaymentState.PARTIAL_REFUND_FAILED,
            kind = "DEPOSIT",
            linkedCaseId = rc.id
        )
        val s6Failed = mapCustomerReturnProgress(rc.copy(status = L6Status.REFUND_REQUESTED), linkedRefund = failedRefund)
        assertEquals(5, s6Failed.currentStepIndex)
        assertEquals("We're retrying your refund payment.", s6Failed.statusDetail)

        val s7Full = mapCustomerReturnProgress(
            rc.copy(status = L6Status.CASE_CLOSED, requestedAmountPaisa = 1_000_000L, deductionPaisa = 0L)
        )
        assertEquals(6, s7Full.currentStepIndex)
        assertEquals("Deposit refunded — Rs 10,000", s7Full.stepTitles[6])

        val s7Deducted = mapCustomerReturnProgress(
            rc.copy(
                status = L6Status.CASE_CLOSED,
                requestedAmountPaisa = 1_000_000L,
                deductionPaisa = 250_000L,
                deductionReason = "Dent"
            )
        )
        assertEquals(6, s7Deducted.currentStepIndex)
        assertEquals("Deposit refunded — Rs 7,500", s7Deducted.stepTitles[6])
        assertEquals("Rs 2,500 was kept: Dent", s7Deducted.statusDetail)

        val s7Dealer = mapCustomerReturnProgress(
            rc.copy(status = L6Status.CASE_CLOSED, requestedAmountPaisa = 0L)
        )
        assertEquals(6, s7Dealer.currentStepIndex)
        assertEquals("Return completed", s7Dealer.stepTitles[6])

        val rejected = mapCustomerReturnProgress(
            rc.copy(status = L6Status.RETURN_REJECTED, rejectionReason = "Seal tampered")
        )
        assertTrue(rejected.isRejected)
        assertEquals(-1, rejected.currentStepIndex)
        assertTrue(rejected.statusDetail.contains("Request not accepted"))
        assertTrue(rejected.statusDetail.contains("Seal tampered"))

        // Zero leaked enum strings across all stages
        val forbiddenTokens = listOf("CASE_", "RETURN_", "AWAITING_", "CYLINDER_RETURN", "ORD_")
        val allMapped = listOf(
            s1Open, s1Review, s2Auth, s3Offered, s3Accepted, s4Collected, s4AtHub,
            s5Checked, s6Pending, s6Failed, s7Full, s7Deducted, s7Dealer, rejected
        )
        allMapped.forEach { mapped ->
            assertTrue(mapped.statusDetail.isNotBlank())
            forbiddenTokens.forEach { token ->
                assertFalse("Found $token in ${mapped.statusDetail}", mapped.statusDetail.contains(token))
                mapped.stepTitles.forEach { title ->
                    assertFalse("Found $token in $title", title.contains(token))
                }
            }
        }
        DepositStatus.entries.forEach { ds ->
            assertTrue(customerDepositStatusLabel(ds).isNotBlank())
        }
    }

    @Test
    fun `T17 rejected return shows Request not accepted and allows requesting return again for same cylinder`() {
        val store = BlueGasViewModel().store

        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            reason = "Moving away",
            preferredWindow = "Tomorrow 10:00–12:00"
        )
        assertTrue(caseId, reqOk)

        assertTrue(
            store.moveCase(
                caseId = caseId,
                target = L6Status.UNDER_REVIEW,
                reason = "Checking return eligibility",
                actor = AccountableRole.SUPPORT.displayName,
                role = AccountableRole.SUPPORT
            ).first
        )
        assertTrue(
            store.moveCase(
                caseId = caseId,
                target = L6Status.RETURN_REJECTED,
                reason = "Customer asked to reschedule later",
                actor = AccountableRole.SUPPORT.displayName,
                role = AccountableRole.SUPPORT
            ).first
        )

        val rcRejected = store.returnCases.first { it.id == caseId }
        val rejectedProgress = mapCustomerReturnProgress(rcRejected)
        assertTrue(rejectedProgress.isRejected)
        assertTrue(rejectedProgress.statusDetail.contains("Request not accepted"))
        assertTrue(rejectedProgress.statusDetail.contains("Customer asked to reschedule later"))

        assertTrue(
            store.moveCase(
                caseId = caseId,
                target = L6Status.CASE_CLOSED,
                reason = "Closed after rejection notice",
                actor = AccountableRole.SUPPORT.displayName,
                role = AccountableRole.SUPPORT
            ).first
        )

        val rcClosedAfterReject = store.returnCases.first { it.id == caseId }
        val closedRejectedProgress = mapCustomerReturnProgress(rcClosedAfterReject)
        assertTrue(closedRejectedProgress.isRejected)
        assertTrue(closedRejectedProgress.statusDetail.contains("Request not accepted"))

        // Customer can open a new return request for the same cylinder once previous is rejected/closed
        val (reReqOk, newCaseId) = store.requestCylinderReturn(
            customerId = "CUST-001",
            cylinderSerial = "BG-LHR-001",
            reason = "Ready for pickup now",
            preferredWindow = "Tomorrow 14:00–16:00"
        )
        assertTrue(newCaseId, reReqOk)
        assertNotEquals(caseId, newCaseId)
    }

    @Test
    fun `T18 moving house and changing default address leaves holdings and deposit accounts untouched`() {
        val store = BlueGasViewModel().store

        val holdingsBefore = store.customerHoldings.filter { it.customerId == "CUST-001" }.map { it.copy() }
        val depositsBefore = store.depositAccounts.filter { it.customerId == "CUST-001" }.map { it.copy() }
        val totalHeldBefore = depositsBefore.filter { it.status == DepositStatus.HELD }.sumOf { it.amountPaisa }

        assertTrue(holdingsBefore.isNotEmpty())
        assertTrue(totalHeldBefore > 0L)

        // Add new home address in Lahore and switch default
        val (addOk, newAddrId) = store.addAddress(
            customerId = "CUST-001",
            label = "New Home",
            street = "House 88, Block C",
            area = "DHA Phase 6",
            city = "Lahore",
            zoneId = "ZONE-LHR-1",
            makeDefault = true
        )
        assertTrue(newAddrId, addOk)

        // Switch default explicitly to verify setDefaultAddress too
        val (setOk, setMsg) = store.setDefaultAddress("CUST-001", newAddrId)
        assertTrue(setMsg, setOk)

        val holdingsAfter = store.customerHoldings.filter { it.customerId == "CUST-001" }
        val depositsAfter = store.depositAccounts.filter { it.customerId == "CUST-001" }
        val totalHeldAfter = depositsAfter.filter { it.status == DepositStatus.HELD }.sumOf { it.amountPaisa }

        assertEquals(holdingsBefore, holdingsAfter)
        assertEquals(depositsBefore, depositsAfter)
        assertEquals(totalHeldBefore, totalHeldAfter)
    }

    @Test
    fun `T17a loaned dealer cylinder return logs EXC-26 and blocks refund without proof`() {
        val store = BlueGasViewModel().store

        val (reqOk, caseId) = store.requestCylinderReturn(
            customerId = "CUST-002",
            cylinderSerial = "BG-LHR-004",
            reason = "Closing account",
            preferredWindow = "Today 10:00–12:00"
        )
        assertTrue(caseId, reqOk)
        assertTrue(store.reconExceptions.any { it.code == "EXC-26" && it.description.contains("BG-LHR-004") })

        assertTrue(store.moveCase(caseId = caseId, target = L6Status.UNDER_REVIEW, reason = "Checking custody", actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT).first)
        assertTrue(store.moveCase(caseId = caseId, target = L6Status.RETURN_AUTHORIZED, reason = "Authorized", actor = AccountableRole.SUPPORT.displayName, role = AccountableRole.SUPPORT).first)
        assertTrue(store.offerReturnCollection(caseId, "RIDER-001", AccountableRole.OPERATIONS.displayName).first)
        assertTrue(store.riderAcceptReturn(caseId, "RIDER-001").first)
        assertTrue(store.riderCollectCylinder(caseId, "RIDER-001", "BG-LHR-004", photoCaptured = true).first)
        assertTrue(store.riderHandInReturn(caseId, "RIDER-001").first)
        assertTrue(store.hubRecordReturnCondition(caseId, ReturnCondition.GOOD, actor = AccountableRole.HUB.displayName).first)

        val (refOk, refMsg) = store.requestDepositRefund(caseId = caseId, actor = AccountableRole.FINANCE.displayName)
        assertFalse(refOk)
        assertEquals("No deposit on record for this cylinder (EXC-26). No refund without proof.", refMsg)
    }

    @Test
    fun `T18a Admin toggles on CNIC requirement and backup cylinder limits take effect live`() {
        val store = BlueGasViewModel().store

        // CUST-004 starts without captured CNIC
        assertTrue(store.needsCnicForDeposit("CUST-004", OrderType.NEW_CYLINDER))

        // Admin disables CNIC requirement -> uncaptured customer can place deposit order immediately
        val (updOk, updMsg) = store.updateSettings(
            store.settings.copy(
                cnicRequiredForDeposit = false,
                backupCylinderAllowed = true,
                maxDepositCylindersPerSize = 2
            )
        )
        assertTrue(updMsg, updOk)
        assertFalse(store.needsCnicForDeposit("CUST-004", OrderType.NEW_CYLINDER))

        val (cust4Order, cust4Err) = store.createOrder(
            customerId = "CUST-004",
            addressId = "ADDR-005",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 2,
            includeDeposit = false,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            orderType = OrderType.NEW_CYLINDER
        )
        assertNull(cust4Err)
        assertNotNull(cust4Order)
    }
}
