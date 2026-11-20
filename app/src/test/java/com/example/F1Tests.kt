package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class F1Tests {

    @Test
    fun t40_proposeAndApproveFillPlanRespectsFormulaAndRefusesReapprove() {
        val store = BlueGasStore()
        // Seed Lahore has hubMinimumFilled: 11.8 kg = 20, 45.4 kg = 5.
        // Push Lahore 11.8 kg below minimum by adjusting filledQty down to 5 (reserved = 0).
        val lhr118 = store.stockBalances.first { it.pointId == "NODE-LHR-1" && it.skuId == "SKU-LPG-11-8" }
        lhr118.filledQty = 5
        lhr118.reservedQty = 0
        val inTransitLhr118 = store.stockTransfers
            .filter {
                it.direction == "PLANT_TO_POINT" &&
                    it.toPointId == "NODE-LHR-1" &&
                    (it.status == "REQUESTED" || it.status == "DISPATCHED")
            }
            .sumOf { trf -> trf.lines.find { it.skuId == "SKU-LPG-11-8" }?.qtySent ?: 0 }
        val expectedProposed = maxOf(0, 20 - (5 - 0) - inTransitLhr118)

        val (propOk, propMsg) = store.proposeFillPlan(forDate = "2026-11-21")
        assertTrue(propMsg, propOk)

        val plan = store.fillPlans.first()
        val lineLhr = plan.lines.first { it.pointId == "NODE-LHR-1" && it.sku == "SKU-LPG-11-8" }
        assertEquals(expectedProposed, lineLhr.proposedQty)
        assertTrue(lineLhr.proposedQty > 0)

        val transfersBefore = store.stockTransfers.size
        // Approve with 0 for all other lines so only Lahore creates 1 transfer
        val approvedMap = plan.lines.associate { l ->
            val key = "${l.pointId}|${l.sku}"
            key to if (l.pointId == "NODE-LHR-1" && l.sku == "SKU-LPG-11-8") lineLhr.proposedQty else 0
        }
        val (appOk, appMsg) = store.approveFillPlan(plan.id, approvedMap)
        assertTrue(appMsg, appOk)
        assertEquals("APPROVED", plan.status)
        assertEquals(transfersBefore + 1, store.stockTransfers.size)
        val created = store.stockTransfers.last()
        assertEquals("REQUESTED", created.status)
        assertEquals("FILL_PLAN", created.source)
        assertEquals("NODE-LHR-1", created.toPointId)
        assertEquals(expectedProposed, created.lines.first { it.skuId == "SKU-LPG-11-8" }.qtySent)

        val (reappOk, reappMsg) = store.approveFillPlan(plan.id, approvedMap)
        assertFalse(reappMsg, reappOk)
    }

    @Test
    fun t41_plantRecordFillRefusesTestDueThenSucceedsAfterRetestPassAndUpdatesPlantStock() {
        val store = BlueGasStore()
        val dueCyl = store.cylinders.first {
            it.custody == CylinderCustody.PLANT &&
                it.plantStage == "EMPTY_AT_PLANT" &&
                it.nextTestDueDate != null &&
                it.nextTestDueDate!! <= "2026-01-01"
        }

        val (failFillOk, failFillMsg) = store.plantRecordFill(dueCyl.serialNumber, dueCyl.weightKg)
        assertFalse(failFillMsg, failFillOk)
        assertTrue(failFillMsg.contains("re-testing", ignoreCase = true))

        val (sentOk, sentMsg) = store.plantSendToRetest(dueCyl.serialNumber)
        assertTrue(sentMsg, sentOk)
        assertEquals("SENT_TO_RETEST", store.cylinders.first { it.serialNumber == dueCyl.serialNumber }.plantStage)

        val (passOk, passMsg) = store.plantRecordRetest(dueCyl.serialNumber, passed = true, nextTestDueDate = "2031-12-31")
        assertTrue(passMsg, passOk)
        assertEquals("EMPTY_AT_PLANT", store.cylinders.first { it.serialNumber == dueCyl.serialNumber }.plantStage)

        val plantBal = store.stockBalances.first { it.pointId == "PLANT-01" && it.skuId == dueCyl.sku }
        val filledBefore = plantBal.filledQty
        val emptyBefore = plantBal.emptyQty

        val (fillOk, fillMsg) = store.plantRecordFill(dueCyl.serialNumber, dueCyl.weightKg)
        assertTrue(fillMsg, fillOk)
        assertEquals(filledBefore + 1, plantBal.filledQty)
        assertEquals(emptyBefore - 1, plantBal.emptyQty)

        val updated = store.cylinders.first { it.serialNumber == dueCyl.serialNumber }
        assertEquals("FILLED", updated.plantStage)
        assertEquals(InventoryState.INV_AVAIL, updated.status)
    }

    @Test
    fun t42_plantDecideEmptyScrapRequiresReasonAndExcludesFromBindList() {
        val store = BlueGasStore()
        val emptyCyl = store.cylinders.first {
            it.custody == CylinderCustody.PLANT && it.plantStage == "EMPTY_AT_PLANT"
        }

        val (noReasonOk, noReasonMsg) = store.plantDecideEmpty(emptyCyl.serialNumber, "SCRAP", "")
        assertFalse(noReasonMsg, noReasonOk)

        val (scrapOk, scrapMsg) = store.plantDecideEmpty(emptyCyl.serialNumber, "SCRAP", "Base ring corrosion")
        assertTrue(scrapMsg, scrapOk)

        val scrapped = store.cylinders.first { it.serialNumber == emptyCyl.serialNumber }
        assertEquals(InventoryState.INV_WRITEOFF, scrapped.status)
        assertEquals(CustodyRecordStatus.RECORD_CLOSED, scrapped.custodyRecord)

        // Confirm absent from any eligible hub bind list
        val bindCandidates = store.cylinders.filter {
            it.status == InventoryState.INV_AVAIL && !it.hseHold && !it.pendingHubScanIn
        }.map { it.serialNumber }
        assertFalse(bindCandidates.contains(emptyCyl.serialNumber))
    }

    @Test
    fun t43_ograSerialListCsvIncludesBlueGasCylindersAndExcludesSurrendered() {
        val store = BlueGasStore()
        store.surrenderedCylinders.add(
            SurrenderedCylinder(
                id = "SUR-999",
                claimId = "CLM-999",
                brand = "PARCO Pearl Gas",
                serialText = "RIVAL-SERIAL-999"
            )
        )

        val csv = store.ograSerialListCsv()
        val lines = csv.lines()
        assertEquals("serial,size_kg,status,next_test_due", lines.first())

        val blueGasSerials = store.cylinders.filter { it.brand.equals("BLUE GAS", ignoreCase = true) }.map { it.serialNumber }.toSet()
        val dataLines = lines.drop(1)
        assertEquals(blueGasSerials.size, dataLines.size)
        val csvSerials = dataLines.map { it.substringBefore(',') }
        assertEquals(blueGasSerials.size, csvSerials.toSet().size)
        assertTrue(csvSerials.toSet().containsAll(blueGasSerials))
        assertFalse(csv.contains("RIVAL-SERIAL-999"))
    }

    @Test
    fun t44_confirmEmptiesReceivedAtPlantMarksCylindersEmptyAtPlant() {
        val store = BlueGasStore()
        val trf = store.stockTransfers.first { it.direction == "EMPTIES_TO_PLANT" && it.status == "DISPATCHED" }
        assertTrue(trf.cylinderIds.isNotEmpty())

        val (ok, msg) = store.confirmEmptiesReceivedAtPlant(trf.id)
        assertTrue(msg, ok)

        trf.cylinderIds.forEach { cid ->
            val cyl = store.cylinders.first { it.serialNumber == cid }
            assertEquals("EMPTY_AT_PLANT", cyl.plantStage)
            assertEquals(CylinderCustody.PLANT, cyl.custody)
            assertEquals("PLANT-01", cyl.pointId)
        }
    }

    @Test
    fun t45_urgentTopUpCreatesUrgentTransferAndPlantNotification() {
        val store = BlueGasStore()
        val transfersBefore = store.stockTransfers.size
        val notifsBefore = store.notifications.size

        val (ok, msg) = store.urgentTopUp("NODE-LHR-1", mapOf("SKU-LPG-11-8" to 4))
        assertTrue(msg, ok)

        assertEquals(transfersBefore + 1, store.stockTransfers.size)
        val trf = store.stockTransfers.last()
        assertEquals("URGENT", trf.source)
        assertEquals("NODE-LHR-1", trf.toPointId)
        assertEquals("REQUESTED", trf.status)

        val newNotifs = store.notifications.take(store.notifications.size - notifsBefore)
        assertTrue(
            newNotifs.any {
                it.staffRole == StaffSubRole.PLANT_INVENTORY &&
                    it.message.contains("Urgent top-up requested by", ignoreCase = true)
            }
        )
    }
}
