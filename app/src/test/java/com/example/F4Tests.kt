package com.example

import com.example.bluegas.data.*
import com.example.bluegas.lifecycle.ClaimStateMachine
import com.example.bluegas.model.*
import com.example.bluegas.ui.customer.buildReceiptRows
import com.example.bluegas.ui.customer.customerVisibleItems
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class F4Tests {

    @Test
    fun `T57 Route A assess offer scheduleClaimHandover order completes closes claim without surrendered cylinder and refuses inspection`() {
        val store = BlueGasViewModel().store
        // Configure an offer value and add a Route A claim (SSGC LPG has no return agreement)
        store.updateSettings(store.settings.copy(phase2DiscountValue = 120000L))
        assertTrue(store.captureCnic("CUST-004", "37405-4455667-7").first)

        val claim = ReplacementClaim(
            id = "CLM-ROUTE-A-01",
            customerId = "CUST-004",
            declaredBrand = "SSGC LPG",
            declaredSize = "11.8 kg",
            declaredSerial = "SSGC-7721",
            status = ClaimStatus.EVIDENCE_SUBMITTED
        )
        store.replacementClaims.add(claim)

        // 1. Assess -> automatically Route A because no return agreement and no ownership proof
        val (eligOk, eligMsg) = store.assessEligibility(claim.id, true, "Eligible for Route A")
        assertTrue(eligMsg, eligOk)
        assertEquals(ClaimStatus.PROVISIONALLY_ELIGIBLE, claim.status)

        // Choosing B when Route B is not possible is refused
        val (badRouteBOk, _) = store.issueProvisionalOffer(claim.id, route = "B")
        assertFalse(badRouteBOk)

        // Issue offer A
        val (offerOk, offerMsg) = store.issueProvisionalOffer(claim.id, route = "A")
        assertTrue(offerMsg, offerOk)
        assertEquals("A", claim.route)
        assertTrue(claimRouteDescription(claim).contains("return it to SSGC LPG to claim your old deposit"))

        // 2. scheduleClaimHandover creates replacement NEW_CYLINDER order and advances to SCHEDULED_FOR_HANDOVER
        val (schedOk, schedMsg) = store.scheduleClaimHandover(claim.id, "ADDR-005", PaymentMethod.ONLINE_HOSTED)
        assertTrue(schedMsg, schedOk)
        assertEquals(ClaimStatus.SCHEDULED_FOR_HANDOVER, claim.status)
        val linkedOrderId = claim.linkedOrderId
        assertNotNull(linkedOrderId)

        // Moving to AT_HANDOVER_INSPECTION on Route A must be refused
        val (inspOk, inspMsg) = ClaimStateMachine.transition(
            claim = claim,
            target = ClaimStatus.AT_HANDOVER_INSPECTION,
            actor = "Inspector",
            role = AccountableRole.INSPECTOR,
            reason = "Test"
        )
        assertFalse(inspOk)
        assertEquals("Route A has no BLUE GAS inspection or custody.", inspMsg)
        assertFalse(store.startInspection(claim.id, "Inspector").first)

        // 3. Complete the replacement order through hub -> rider -> POD
        assertTrue(store.payOnline(linkedOrderId!!, "SUCCESS").first)
        assertTrue(store.hubAcceptOrder(linkedOrderId).first)
        assertTrue(store.startPreparing(linkedOrderId).first)
        assertTrue(store.allocateCylinder(linkedOrderId, "BG-ISB-118-001").first)
        assertTrue(store.assignRider(linkedOrderId, "RIDER-003").first)
        store.switchRider("RIDER-003")
        assertTrue(store.riderAcceptTask(linkedOrderId).first)
        assertTrue(store.riderVerifyCylinder(linkedOrderId, "BG-ISB-118-001", "RIDER-003").first)
        assertTrue(store.riderStartTrip(linkedOrderId).first)
        assertTrue(store.riderArrive(linkedOrderId).first)
        val ord = store.orders.first { it.id == linkedOrderId }
        val (hocOk, hocMsg) = store.completeHandover(
            orderId = linkedOrderId,
            checklistAnswers = mapOf("Connect the regulator" to true, "Perform the approved leak check" to true, "Demonstrate safe use to the customer" to true),
            pinVerified = true,
            photoCaptured = true,
            evidenceConsent = true
        )
        assertTrue(hocMsg, hocOk)
        assertEquals(OrderStatus.ORD_COMP, ord.status)

        // 4. Route A claim auto-closed without physical/HSE checks or surrendered cylinder
        assertEquals(ClaimStatus.CLOSED, claim.status)
        assertEquals("Switched-Customer-Keeps-Old", claim.outcomeCode)
        assertEquals("Old cylinder stays with the customer for return to SSGC LPG", claim.closureReason)
        assertNull(claim.physicalResult)
        assertNull(claim.hseResult)
        assertTrue(store.surrenderedCylinders.none { it.claimId == claim.id })

        // 5. If switchSaveRouteAEnabled is false and brand has no agreement, assessEligibility closes INELIGIBLE
        store.updateSettings(store.settings.copy(switchSaveRouteAEnabled = false))
        val blockedClaim = ReplacementClaim(
            id = "CLM-ROUTE-A-OFF",
            customerId = "CUST-003",
            declaredBrand = "SSGC LPG",
            declaredSize = "11.8 kg",
            status = ClaimStatus.EVIDENCE_SUBMITTED
        )
        store.replacementClaims.add(blockedClaim)
        store.assessEligibility(blockedClaim.id, true, "Attempt")
        assertEquals(ClaimStatus.CLOSED, blockedClaim.status)
        assertEquals("INELIGIBLE", blockedClaim.outcomeCode)
        assertEquals("No return agreement with SSGC LPG", blockedClaim.closureReason)
    }

    @Test
    fun `T58 Route B completeExchange refused before gates and closes cleanly after all gates and order completion`() {
        val store = BlueGasViewModel().store
        store.updateSettings(store.settings.copy(phase2DiscountValue = 150000L))
        assertTrue(store.captureCnic("CUST-004", "37405-4455667-7").first)

        val claim = ReplacementClaim(
            id = "CLM-ROUTE-B-01",
            customerId = "CUST-004",
            declaredBrand = "PARCO Pearl Gas",
            declaredSize = "11.8 kg",
            declaredSerial = "PARCO-11223",
            status = ClaimStatus.EVIDENCE_SUBMITTED
        )
        store.replacementClaims.add(claim)

        assertTrue(store.assessEligibility(claim.id, true, "Agreement active").first)
        assertTrue(store.issueProvisionalOffer(claim.id, route = null).first)
        assertEquals("B", claim.route)

        // Premature completeExchange is refused
        assertFalse(store.completeExchange(claim.id).first)

        assertTrue(store.scheduleClaimHandover(claim.id, "ADDR-005", PaymentMethod.ONLINE_HOSTED).first)
        val linkedOrderId = claim.linkedOrderId!!

        assertTrue(store.startInspection(claim.id, "Inspector Tariq").first)
        assertTrue(store.recordPhysicalResult(claim.id, accepted = true, reason = "Shell intact").first)
        assertTrue(store.startHseAssessment(claim.id).first)
        assertTrue(store.recordHseResult(claim.id, cleared = true, notes = "Leak free").first)
        assertTrue(store.confirmFinalOffer(claim.id, 150000L).first)

        // Order is still ORD_PAYPEND -> completeExchange must refuse
        val (noOrderDoneOk, noOrderDoneMsg) = store.completeExchange(claim.id)
        assertFalse(noOrderDoneOk)
        assertTrue(noOrderDoneMsg.contains("Handover Complete or Completed"))

        // Advance order to Completed
        assertTrue(store.payOnline(linkedOrderId, "SUCCESS").first)
        assertTrue(store.hubAcceptOrder(linkedOrderId).first)
        assertTrue(store.startPreparing(linkedOrderId).first)
        assertTrue(store.allocateCylinder(linkedOrderId, "BG-ISB-118-002").first)
        assertTrue(store.assignRider(linkedOrderId, "RIDER-003").first)
        store.switchRider("RIDER-003")
        assertTrue(store.riderAcceptTask(linkedOrderId).first)
        assertTrue(store.riderVerifyCylinder(linkedOrderId, "BG-ISB-118-002", "RIDER-003").first)
        assertTrue(store.riderStartTrip(linkedOrderId).first)
        assertTrue(store.riderArrive(linkedOrderId).first)
        assertTrue(
            store.completeHandover(
                orderId = linkedOrderId,
                pinVerified = true,
                photoCaptured = true,
                evidenceConsent = true
            ).first
        )

        // Now completeExchange succeeds
        val (exOk, exMsg) = store.completeExchange(claim.id)
        assertTrue(exMsg, exOk)
        assertEquals(ClaimStatus.CLOSED, claim.status)
        assertEquals("Eligible-Exchanged", claim.outcomeCode)
        val sur = store.surrenderedCylinders.firstOrNull { it.claimId == claim.id }
        assertNotNull(sur)
        assertEquals("PARCO Pearl Gas", sur!!.brand)
    }

    @Test
    fun `T59 cancel works at PHYSICALLY_ACCEPTED and HSE_CLEARED and is refused at HSE_ASSESSMENT`() {
        val store = BlueGasViewModel().store

        val cPhys = ReplacementClaim(
            id = "CLM-CAN-PHYS",
            customerId = "CUST-001",
            declaredBrand = "PARCO Pearl Gas",
            declaredSize = "11.8 kg",
            status = ClaimStatus.PHYSICALLY_ACCEPTED
        )
        val (okPhys, _) = ClaimStateMachine.transition(cPhys, ClaimStatus.CANCELLED, "Customer", AccountableRole.COMMERCIAL, "Customer changed mind")
        assertTrue(okPhys)
        assertEquals(ClaimStatus.CANCELLED, cPhys.status)

        val cHseCleared = ReplacementClaim(
            id = "CLM-CAN-HSEC",
            customerId = "CUST-001",
            declaredBrand = "PARCO Pearl Gas",
            declaredSize = "11.8 kg",
            status = ClaimStatus.HSE_CLEARED
        )
        val (okCleared, _) = ClaimStateMachine.transition(cHseCleared, ClaimStatus.CANCELLED, "Customer", AccountableRole.COMMERCIAL, "Cancelled after HSE")
        assertTrue(okCleared)
        assertEquals(ClaimStatus.CANCELLED, cHseCleared.status)

        val cHseAssess = ReplacementClaim(
            id = "CLM-CAN-HSEA",
            customerId = "CUST-001",
            declaredBrand = "PARCO Pearl Gas",
            declaredSize = "11.8 kg",
            status = ClaimStatus.HSE_ASSESSMENT
        )
        val (okAssess, _) = ClaimStateMachine.transition(cHseAssess, ClaimStatus.CANCELLED, "Customer", AccountableRole.COMMERCIAL, "Try cancel during HSE")
        assertFalse(okAssess)
        assertEquals(ClaimStatus.HSE_ASSESSMENT, cHseAssess.status)
    }

    @Test
    fun `T60 disposition refuses return and recycle when brand has no agreement and no ownership proof`() {
        val store = BlueGasViewModel().store
        val claim = ReplacementClaim(
            id = "CLM-DISP-01",
            customerId = "CUST-001",
            declaredBrand = "Noor LPG",
            declaredSize = "11.8 kg",
            ownershipProofProvided = false,
            status = ClaimStatus.CLOSED
        )
        store.replacementClaims.add(claim)
        val sur = SurrenderedCylinder(
            id = "SUR-DISP-01",
            claimId = claim.id,
            brand = "Noor LPG",
            serialText = "NOOR-777",
            status = SurrenderStatus.COLLECTED
        )
        store.surrenderedCylinders.add(sur)

        assertFalse(store.authorizeDisposition(sur.id, "Return to Noor LPG under the written agreement").first)
        assertFalse(store.authorizeDisposition(sur.id, "Recycle through an authorised route").first)
        assertFalse(store.authorizeDisposition(sur.id, "scrap").first)
        assertFalse(store.authorizeDisposition(sur.id, "resell").first)
        assertEquals(SurrenderStatus.COLLECTED, sur.status)

        // When ownership proof IS provided, recycling is allowed while written return still requires agreement
        claim.ownershipProofProvided = true
        assertFalse(store.authorizeDisposition(sur.id, "Return to Noor LPG under the written agreement").first)
        val (recOk, recMsg) = store.authorizeDisposition(sur.id, "Recycle through an authorised route")
        assertTrue(recMsg, recOk)
        assertEquals(SurrenderStatus.DISPOSITION_AUTHORIZED, sur.status)
    }

    @Test
    fun `T61 replacement order keeps full deposit applies offer discount and receipt foots`() {
        val store = BlueGasViewModel().store
        store.updateSettings(store.settings.copy(phase2DiscountValue = 150000L))
        assertTrue(store.captureCnic("CUST-003", "35201-3344556-5").first)

        val claim = ReplacementClaim(
            id = "CLM-QUOTE-01",
            customerId = "CUST-003",
            declaredBrand = "Noor LPG",
            declaredSize = "11.8 kg",
            declaredSerial = "NOOR-500",
            status = ClaimStatus.PROVISIONALLY_ELIGIBLE
        )
        store.replacementClaims.add(claim)
        assertTrue(store.issueProvisionalOffer(claim.id, "A").first)
        assertEquals(150000L, claim.provisionalOfferPaisa)

        assertTrue(store.scheduleClaimHandover(claim.id, "ADDR-004", PaymentMethod.ONLINE_HOSTED).first)
        val ord = store.orders.first { it.id == claim.linkedOrderId }

        val fullDepositExpected = PricingEngine.roundToRupees(
            store.priceLists.maxByOrNull { it.version }!!.depositPaisa["SKU-LPG-11-8"]!!
        )
        assertEquals(fullDepositExpected, ord.depositTotalPaisa)
        assertEquals(150000L, ord.discountPaisa)

        val rows = buildReceiptRows(ord, store.appState.value)
        assertTrue(rows.any { it.label == "Discount (Switch & Save benefit · Demo value Q01/Q02)" })

        val linesSum = ord.customerVisibleItems().sumOf { it.unitPricePaisa * it.quantity }
        val recomputed = linesSum + ord.depositTotalPaisa + ord.platformFeePaisa + ord.deliveryFeePaisa +
            ord.expressFeePaisa - ord.discountPaisa + ord.gstPaisa + ord.furtherTaxPaisa
        assertEquals(ord.totalAmountPaisa, recomputed)
    }

    @Test
    fun `T62 seeded Route B claim CLM-SEED-01 passes all gates via completeExchange`() {
        val store = BlueGasViewModel().store
        val claim = store.replacementClaims.first { it.id == "CLM-SEED-01" }
        assertEquals("PARCO Pearl Gas", claim.declaredBrand)
        assertEquals("B", claim.route)
        assertEquals(ClaimStatus.SCHEDULED_FOR_HANDOVER, claim.status)
        assertNotNull(claim.linkedOrderId)

        assertTrue(store.startInspection(claim.id, "Inspector Tariq").first)
        assertTrue(store.recordPhysicalResult(claim.id, accepted = true, reason = "OK").first)
        assertTrue(store.startHseAssessment(claim.id).first)
        assertTrue(store.recordHseResult(claim.id, cleared = true, notes = "Cleared").first)
        assertTrue(store.confirmFinalOffer(claim.id, claim.provisionalOfferPaisa).first)
        val (exOk, exMsg) = store.completeExchange(claim.id)
        assertTrue(exMsg, exOk)
        assertEquals(ClaimStatus.CLOSED, claim.status)
        assertEquals("Eligible-Exchanged", claim.outcomeCode)
    }
}
