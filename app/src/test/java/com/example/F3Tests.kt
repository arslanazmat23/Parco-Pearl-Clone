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
class F3Tests {

    @Test
    fun `T53 raiseComplaint overload generates CMP reference and NTF-17 SMS while old signature still works`() {
        val store = BlueGasViewModel().store
        val initialSmsCount = store.notifications.count {
            it.surface == AppSurface.CUSTOMER && it.code == "NTF-17" && NotifyChannel.SMS in it.channels
        }

        // 5-arg overload with photoAttached
        val (ok1, msg1) = store.raiseComplaint(
            orderId = "ORD-SEED-108",
            issueType = "UNDERWEIGHT",
            description = "Cylinder feels lighter than 11.8 kg",
            photoAttached = true
        )
        assertTrue(msg1, ok1)
        val c1 = store.supportCases.first { it.orderId == "ORD-SEED-108" }
        assertNotNull(c1.reference)
        assertTrue(c1.reference!!.matches(Regex("^CMP-\\d{4}$")))
        assertTrue(c1.photoAttached)
        val smsNotifs = store.notifications.filter {
            it.surface == AppSurface.CUSTOMER &&
                it.code == "NTF-17" &&
                it.orderId == "ORD-SEED-108" &&
                NotifyChannel.SMS in it.channels
        }
        assertTrue("Expected NTF-17 with SMS channel", smsNotifs.size >= 1)
        assertTrue(
            store.notifications.count {
                it.surface == AppSurface.CUSTOMER && it.code == "NTF-17" && NotifyChannel.SMS in it.channels
            } > initialSmsCount
        )

        // Old 4-arg signature (default actor) still works
        val (ok2, msg2) = store.raiseComplaint(
            orderId = "ORD-SEED-101",
            issueType = "DELIVERY_DELAY",
            description = "Arrived after window"
        )
        assertTrue(msg2, ok2)
        val c2 = store.supportCases.first { it.orderId == "ORD-SEED-101" && it.issueType == "DELIVERY_DELAY" }
        assertFalse(c2.photoAttached)
        assertNotNull(c2.reference)
    }

    @Test
    fun `T54 resolveComplaint REFUND creates linked REFUND_ONLY case keeping order Completed and REPLACE creates no order`() {
        val store = BlueGasViewModel().store

        // Open a complaint on ORD-SEED-108 (Completed, no existing L6 return case)
        val (cmp1Ok, cmp1Msg) = store.raiseComplaint(
            orderId = "ORD-SEED-108",
            issueType = "OVERCHARGE",
            description = "Extra fee charged",
            photoAttached = false
        )
        assertTrue(cmp1Msg, cmp1Ok)
        val c1 = store.supportCases.first { it.orderId == "ORD-SEED-108" }

        // Exceeding order total is refused
        val ord108 = store.orders.first { it.id == "ORD-SEED-108" }
        val (badRefOk, _) = store.resolveComplaint(
            caseId = c1.id,
            type = "REFUND",
            note = "Too high",
            refundAmountPaisa = ord108.totalAmountPaisa + 100L
        )
        assertFalse(badRefOk)

        val (refOk, refMsg) = store.resolveComplaint(
            caseId = c1.id,
            type = "REFUND",
            note = "Approved delivery fee refund",
            refundAmountPaisa = 30000L
        )
        assertTrue(refMsg, refOk)
        assertEquals("RESOLVED", c1.status)
        assertEquals("REFUND", c1.resolutionType)
        assertNotNull(c1.firstResponseAt)
        assertNotNull(c1.linkedReturnCaseId)
        val linkedCase = store.returnCases.find { it.id == c1.linkedReturnCaseId }
        assertNotNull(linkedCase)
        assertEquals("REFUND_ONLY", linkedCase!!.type)
        assertEquals(30000L, linkedCase.requestedAmountPaisa)
        assertEquals(OrderStatus.ORD_COMP, ord108.status)

        // REPLACE records outcome + Operations alert without creating any new order
        val ordersBeforeReplace = store.orders.size
        val (cmp2Ok, _) = store.raiseComplaint(
            orderId = "ORD-SEED-101",
            issueType = "LEAKING_VALVE",
            description = "Valve washer damaged",
            photoAttached = true
        )
        assertTrue(cmp2Ok)
        val c2 = store.supportCases.first { it.orderId == "ORD-SEED-101" && it.issueType == "LEAKING_VALVE" }
        val (repOk, repMsg) = store.resolveComplaint(
            caseId = c2.id,
            type = "REPLACE",
            note = "Operations to dispatch replacement cylinder"
        )
        assertTrue(repMsg, repOk)
        assertEquals("RESOLVED", c2.status)
        assertEquals("REPLACE", c2.resolutionType)
        assertEquals(ordersBeforeReplace, store.orders.size)
        assertTrue(
            store.notifications.any {
                it.surface == AppSurface.STAFF &&
                    it.staffRole == StaffSubRole.OPERATIONS &&
                    it.message.startsWith("Arrange replacement for ")
            }
        )
    }

    @Test
    fun `T55 submitRiderApplication approve without training blocks dispatch until markRiderTrainingDone`() {
        val store = BlueGasViewModel().store
        val (subOk, subMsg) = store.submitRiderApplication(
            name = "Tariq Mahmood",
            phone = "+92 301 5551234",
            cnic = "35202-1234567-1",
            city = "Lahore",
            vehicleNumber = "LEB-4421",
            cnicCopy = true,
            drivingLicence = true,
            vehicleRegistration = true
        )
        assertTrue(subMsg, subOk)
        val appId = store.lastRiderApplicationId
        assertNotNull(appId)
        val app = store.partnerApplications.first { it.id == appId }
        assertEquals("RIDER", app.type)
        assertEquals("NODE-LHR-1", app.homePointId)
        assertTrue(
            store.notifications.any {
                it.surface == AppSurface.RIDER &&
                    it.code == "NTF-14" &&
                    NotifyChannel.SMS in it.channels &&
                    it.message.contains(appId!!)
            }
        )

        // Approve with trainingDone = false
        val (apprOk, apprMsg) = store.approvePartner(
            appId = appId!!,
            homePointId = "NODE-LHR-1",
            trainingDone = false,
            actor = AccountableRole.OPERATIONS.displayName
        )
        assertTrue(apprMsg, apprOk)
        val newRider = store.riders.first { it.cnic == "35202-1234567-1" }
        assertEquals("APPROVED", newRider.onboardingStatus)
        assertFalse(newRider.installationTrained)

        // Prepare order ORD-SEED-112 -> assignRider must refuse untrained rider
        val (badAssignOk, badAssignMsg) = store.assignRider("ORD-SEED-112", newRider.id)
        assertFalse(badAssignOk)
        assertTrue(badAssignMsg.contains("training", ignoreCase = true))

        // AUTO dispatch must also exclude the untrained rider if existing trained Lahore riders decline
        store.updateSettings(store.settings.copy(dispatchMode = DispatchMode.AUTO_RIDER_FIRST))
        val (autoOrd, autoErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED
        )
        assertNull(autoErr)
        requireNotNull(autoOrd)
        assertTrue(store.payOnline(autoOrd.id, "SUCCESS").first)
        assertNotEquals(newRider.id, autoOrd.assignedRiderId)
        // Decline all other offered riders
        var guard = 0
        while (autoOrd.assignedRiderId != null && guard < 10) {
            guard++
            assertNotEquals(newRider.id, autoOrd.assignedRiderId)
            store.riderDeclineTask(autoOrd.id, autoOrd.assignedRiderId!!, "Busy")
        }
        assertNull(autoOrd.assignedRiderId)

        // Blank note refused by markRiderTrainingDone
        assertFalse(store.markRiderTrainingDone(newRider.id, "   ").first)

        // Record training -> assignRider succeeds now
        val (trOk, trMsg) = store.markRiderTrainingDone(newRider.id, "Passed leak & regulator check")
        assertTrue(trMsg, trOk)
        val updatedRider = store.riders.first { it.id == newRider.id }
        assertTrue(updatedRider.installationTrained)

        val (okAssign, okAssignMsg) = store.assignRider("ORD-SEED-112", newRider.id)
        assertTrue(okAssignMsg, okAssign)
        assertEquals(newRider.id, store.orders.first { it.id == "ORD-SEED-112" }.assignedRiderId)

        // And autoOfferRider can now offer to newRider as well
        val (autoOfferOk, _) = store.autoOfferRider(autoOrd.id)
        assertTrue(autoOfferOk)
        assertEquals(newRider.id, autoOrd.assignedRiderId)
    }

    @Test
    fun `T56 riderPerformance matches hand count for seeded rider RIDER-001`() {
        val store = BlueGasViewModel().store
        val perf = store.riderPerformance("RIDER-001", days = 30)

        // In SeedData: RIDER-001 has 2 completed deliveries (ORD-SEED-101, ORD-SEED-108),
        // both with nonTechnicalInstallationPassed = true and promisedByAt = null (100% on-time, 100% checklist),
        // 0 cash differences, and 1 linked complaint (CASE-SEED-01 on ORD-SEED-101).
        assertEquals("RIDER-001", perf.riderId)
        assertEquals(2, perf.completedDeliveries)
        assertEquals(2, perf.onTimeHandovers)
        assertEquals(100, perf.onTimePercent)
        assertEquals(2, perf.checklistCompletedCount)
        assertEquals(100, perf.checklistPercent)
        assertEquals(0, perf.cashDifferenceCount)
        assertEquals(0L, perf.cashDifferenceSumPaisa)
        assertEquals(1, perf.complaintCount)
    }
}
