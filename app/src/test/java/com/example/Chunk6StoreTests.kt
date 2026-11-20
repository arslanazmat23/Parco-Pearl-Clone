package com.example

import com.example.bluegas.data.*
import com.example.bluegas.model.*
import com.example.bluegas.ui.customer.buildReceiptRows
import com.example.bluegas.ui.customer.customerReturnWindowOptions
import com.example.bluegas.viewmodel.BlueGasViewModel
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Calendar
import java.util.TimeZone

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class Chunk6StoreTests {

    private fun karachiFutureMillis(hour24: Int, minute: Int = 0): Long {
        val cal = Calendar.getInstance(TimeZone.getTimeZone("Asia/Karachi"))
        cal.timeInMillis = System.currentTimeMillis()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        cal.set(Calendar.HOUR_OF_DAY, hour24)
        cal.set(Calendar.MINUTE, minute)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    @Test
    fun `T24 delivery windows filter expired today slots format 12h cleanly and reject past window on createOrder`() {
        val store = BlueGasViewModel().store
        val afternoonPkt = karachiFutureMillis(15, 30) // 3:30 pm PKT
        store.clock = { afternoonPkt }

        val windows = store.availableDeliveryWindows()
        // At 15:30 PKT, today slots 10-12 and 12-14 have ended; 14-16 (ends 16:00), 16-18, 18-20 remain.
        assertEquals("As soon as possible", windows.first().label)
        assertTrue(windows.first().isAsap)
        val nonAsap = windows.drop(1).map { it.label }
        assertEquals(
            listOf(
                "Today 2–4 pm",
                "Today 4–6 pm",
                "Today 6–8 pm",
                "Tomorrow 10 am–12 pm",
                "Tomorrow 12–2 pm",
                "Tomorrow 2–4 pm",
                "Tomorrow 4–6 pm",
                "Tomorrow 6–8 pm"
            ),
            nonAsap
        )

        // Return window options reuse the exact same single source without ASAP
        assertEquals(nonAsap, customerReturnWindowOptions(store.appState.value.settings, afternoonPkt))

        // Creating an order with an expired window (12-2 pm ended at 14:00 PKT < 15:30 PKT) is refused
        val expiredStart = karachiFutureMillis(12, 0)
        val expiredEnd = karachiFutureMillis(14, 0)
        val (rejectedOrder, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            deliveryWindowLabel = "Today 12–2 pm",
            windowStartAt = expiredStart,
            windowEndAt = expiredEnd
        )
        assertNull(rejectedOrder)
        assertEquals("That delivery time window has passed. Please pick another.", err)
    }

    @Test
    fun `T25 express toggle pricing receipt footing and ledger entry behave cleanly`() {
        val store = BlueGasViewModel().store
        val nowPkt = karachiFutureMillis(11, 0)
        store.clock = { nowPkt }

        // 1. Express disabled -> rejected cleanly
        val disabledSettings = store.appState.value.settings.copy(expressEnabled = false)
        assertTrue(store.updateSettings(disabledSettings, "System Administrator").first)
        val (blockedOrd, blockedErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            serviceLevel = ServiceLevel.EXPRESS
        )
        assertNull(blockedOrd)
        assertEquals("Express delivery is not available right now.", blockedErr)

        // 2. Enable Express with Rs 350 surcharge, verify quote + receipt + ledger entry at completion
        val enabledSettings = disabledSettings.copy(expressEnabled = true, expressFeePaisa = 35_000L, expressHours = 2)
        assertTrue(store.updateSettings(enabledSettings, "System Administrator").first)

        val stdQuote = PricingEngine.buildQuote(
            orderType = OrderType.REFILL_EXCHANGE,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            accessories = emptyList(),
            mode = FulfilmentMode.DELIVERY,
            priceList = store.currentPriceList,
            tax = store.taxConfig,
            settings = enabledSettings,
            serviceLevel = ServiceLevel.STANDARD
        )
        val expQuote = PricingEngine.buildQuote(
            orderType = OrderType.REFILL_EXCHANGE,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            accessories = emptyList(),
            mode = FulfilmentMode.DELIVERY,
            priceList = store.currentPriceList,
            tax = store.taxConfig,
            settings = enabledSettings,
            serviceLevel = ServiceLevel.EXPRESS
        )
        assertEquals(0L, stdQuote.expressFeePaisa)
        assertEquals(35_000L, expQuote.expressFeePaisa)
        assertEquals(stdQuote.deliveryFeePaisa, expQuote.deliveryFeePaisa)
        assertTrue(expQuote.totalAmountPaisa > stdQuote.totalAmountPaisa)

        val (order, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            serviceLevel = ServiceLevel.EXPRESS
        )
        assertNull(err)
        requireNotNull(order)
        assertEquals(ServiceLevel.EXPRESS, order.serviceLevel)
        assertEquals(35_000L, order.expressFeePaisa)
        assertEquals(expQuote.totalAmountPaisa, order.totalAmountPaisa)

        assertTrue(store.payOnline(order.id, "SUCCESS").first)
        assertTrue(order.promiseLabel!!.startsWith("Express · within 2 hours"))

        val serial = "BG-LHR-118-002"
        assertTrue(store.hubAccept(order.id).first)
        assertTrue(store.startPreparing(order.id).first)
        assertTrue(store.allocateCylinder(order.id, serial).first)
        assertTrue(store.assignRider(order.id, "RIDER-001").first)
        assertTrue(store.riderAcceptTask(order.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(order.id, serial, "RIDER-001").first)
        assertTrue(store.riderStartTrip(order.id, "RIDER-001").first)
        assertTrue(store.riderArrive(order.id, "RIDER-001").first)
        assertTrue(
            store.completeHandover(
                orderId = order.id,
                pinVerified = true,
                installationResult = InstallationResult.INSTALLED,
                checklistAnswers = mapOf("Leak test passed" to true),
                photoCaptured = true,
                signatureCaptured = true,
                signerName = "Ali Khan"
            ).first
        )

        val rows = buildReceiptRows(order, store.appState.value)
        assertTrue(rows.any { it.label == "Delivery time" && it.value.startsWith("Express · within 2 hours") })
        assertTrue(rows.any { it.label == "Service" && it.value == "Express" })
        assertTrue(rows.any { it.label == "Express delivery" && it.value == formatRupees(35_000L) })

        val linesSum = order.items.filter { it.lineType != "DEPOSIT" }.sumOf { it.unitPricePaisa * it.quantity }
        val footed = linesSum + order.depositTotalPaisa + order.platformFeePaisa +
            order.deliveryFeePaisa + order.expressFeePaisa - order.discountPaisa + order.gstPaisa + order.furtherTaxPaisa
        assertEquals(order.totalAmountPaisa, footed)

        val expressLedger = store.ledgerEntries.firstOrNull { it.orderId == order.id && it.entryType == "EXPRESS_FEE" }
        assertNotNull(expressLedger)
        assertEquals(35_000L, expressLedger!!.debitPaisa)
    }

    @Test
    fun `T26 cutoff rolls ASAP standard order to tomorrow first window and updates NTF-01 notification`() {
        val store = BlueGasViewModel().store
        val beforeCutoffPkt = karachiFutureMillis(15, 0) // 3 pm < 5 pm cutoff
        store.clock = { beforeCutoffPkt }

        val (dayOrder, dayErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY,
            deliveryWindowLabel = "As soon as possible"
        )
        assertNull(dayErr)
        requireNotNull(dayOrder)
        assertTrue(store.confirmPaymentAndAdvance(dayOrder.id, "CUST-001").first)
        assertTrue("Expected Today by promise, got ${dayOrder.promiseLabel}", dayOrder.promiseLabel!!.startsWith("Today by "))

        val dayNotif = store.notifications.last { it.orderId == dayOrder.id && it.title == "Order Confirmed" }
        assertTrue(dayNotif.message.contains(dayOrder.promiseLabel!!))
        assertTrue(dayNotif.channels.contains(NotifyChannel.SMS))

        // After 17:00 cut-off (18:00 PKT) -> ASAP Standard rolls promise to Tomorrow's first slot
        val afterCutoffPkt = karachiFutureMillis(18, 0)
        store.clock = { afterCutoffPkt }
        val (lateOrder, lateErr) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            deliveryWindowLabel = "As soon as possible"
        )
        assertNull(lateErr)
        requireNotNull(lateOrder)
        val (payOk, payMsg) = store.payOnline(lateOrder.id, "SUCCESS")
        assertTrue(payMsg, payOk)
        assertEquals("Tomorrow by 9 pm", lateOrder.promiseLabel)

        val lateNotif = store.notifications.last { it.orderId == lateOrder.id && it.title == "Order Confirmed" }
        assertTrue(lateNotif.message.contains("Tomorrow by 9 pm"))
        assertTrue(lateNotif.channels.contains(NotifyChannel.SMS))
    }

    @Test
    fun `T27 pickup orders ignore delivery windows and Express level`() {
        val store = BlueGasViewModel().store
        val nowPkt = karachiFutureMillis(11, 0)
        store.clock = { nowPkt }

        val (pickupOrd, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.PICKUP,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.ONLINE_HOSTED,
            pickupPointId = "NODE-LHR-1",
            deliveryWindowLabel = "Today 4–6 pm",
            windowStartAt = karachiFutureMillis(16, 0),
            windowEndAt = karachiFutureMillis(18, 0),
            serviceLevel = ServiceLevel.EXPRESS
        )
        assertNull(err)
        requireNotNull(pickupOrd)
        assertEquals(ServiceLevel.STANDARD, pickupOrd.serviceLevel)
        assertEquals(0L, pickupOrd.expressFeePaisa)
        assertNull(pickupOrd.deliveryWindowLabel)
        assertNull(pickupOrd.windowStartAt)
        assertNull(pickupOrd.windowEndAt)

        val (payOk, payMsg) = store.payOnline(pickupOrd.id, "SUCCESS")
        assertTrue(payMsg, payOk)
        assertTrue(pickupOrd.promiseLabel!!.startsWith("Ready by "))
    }

    @Test
    fun `T28 one-tap reorder selects latest completed gas order and uses live price list`() {
        val store = BlueGasViewModel().store
        val latest = store.lastReorderableOrder("CUST-001")
        assertNotNull(latest)
        assertEquals(OrderStatus.ORD_COMP, latest!!.status)
        val oldTotal = latest.totalAmountPaisa

        // Commercial publishes updated price list v2 with higher 11.8 kg gas tariff
        val v1 = store.currentPriceList
        assertTrue(store.startPriceDraft().first)
        val draftV2 = store.draftPriceList!!.copy(
            reference118Paisa = v1.reference118Paisa + 20_000L,
            perSizePaisa = v1.perSizePaisa + mapOf("SKU-LPG-11-8" to ((v1.perSizePaisa["SKU-LPG-11-8"] ?: 0L) + 20_000L))
        )
        assertTrue(store.updateDraftPriceList(draftV2).first)
        assertTrue(store.publishPriceList("Commercial Manager", "OGRA-TEST-2026").first)

        val liveQuote = PricingEngine.buildQuote(
            orderType = OrderType.REFILL_EXCHANGE,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            accessories = emptyList(),
            mode = FulfilmentMode.DELIVERY,
            priceList = store.currentPriceList,
            tax = store.taxConfig,
            settings = store.appState.value.settings
        )
        assertTrue(liveQuote.totalAmountPaisa > oldTotal)

        val (reordered, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = latest.addressId ?: "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = latest.paymentMethod,
            orderType = OrderType.REFILL_EXCHANGE
        )
        assertNull(err)
        requireNotNull(reordered)
        assertEquals(v1.version + 1, reordered.priceListVersion)
        assertEquals(liveQuote.totalAmountPaisa, reordered.totalAmountPaisa)
    }

    @Test
    fun `T29 refill reminder appears inside lead window hides on dismiss and re-triggers after new completion`() {
        val store = BlueGasViewModel().store
        val baseOrderCompletedAt = store.orders
            .filter { it.customerId == "CUST-001" && it.status == OrderStatus.ORD_COMP }
            .maxOf { it.completedAt ?: it.createdAt }
        val dayMs = 86_400_000L

        // Day 1 after completion -> no reminder yet (30d cycle, 3d lead => due from day 27)
        store.clock = { baseOrderCompletedAt + 1 * dayMs }
        assertNull(store.refillReminderDue("CUST-001"))

        // Day 28 after completion -> reminder due!
        store.clock = { baseOrderCompletedAt + 28 * dayMs }
        val reminder = store.refillReminderDue("CUST-001")
        assertNotNull(reminder)
        assertEquals("SKU-LPG-11-8", reminder!!.sku)
        assertEquals("11.8 kg", reminder.sizeLabel)

        // Dismiss -> suppressed for that completed order
        assertTrue(store.dismissRefillReminder("CUST-001").first)
        assertNull(store.refillReminderDue("CUST-001"))

        // Complete a newer order at current wall clock, then advance 28 days -> reminder re-triggers
        store.clock = { System.currentTimeMillis() }
        val (ord2, err) = store.createOrder(
            customerId = "CUST-001",
            addressId = "ADDR-001",
            fulfilmentMode = FulfilmentMode.DELIVERY,
            sku = "SKU-LPG-11-8",
            quantity = 1,
            includeDeposit = false,
            paymentMethod = PaymentMethod.CASH_ON_DELIVERY
        )
        assertNull(err)
        requireNotNull(ord2)
        val serial = "BG-LHR-118-002"
        assertTrue(store.confirmPaymentAndAdvance(ord2.id, "CUST-001").first)
        assertTrue(store.hubAccept(ord2.id).first)
        assertTrue(store.startPreparing(ord2.id).first)
        assertTrue(store.allocateCylinder(ord2.id, serial).first)
        assertTrue(store.assignRider(ord2.id, "RIDER-001").first)
        assertTrue(store.riderAcceptTask(ord2.id, "RIDER-001").first)
        assertTrue(store.riderVerifyCylinder(ord2.id, serial, "RIDER-001").first)
        assertTrue(store.riderStartTrip(ord2.id, "RIDER-001").first)
        assertTrue(store.riderArrive(ord2.id, "RIDER-001").first)
        assertTrue(
            store.completeHandover(
                orderId = ord2.id,
                pinVerified = true,
                installationResult = InstallationResult.INSTALLED,
                checklistAnswers = mapOf("Leak test passed" to true),
                photoCaptured = true,
                signatureCaptured = true,
                signerName = "Ali Khan"
            ).first
        )

        val ord2DoneAt = ord2.completedAt ?: ord2.createdAt
        store.clock = { ord2DoneAt + 28 * dayMs }
        val nextReminder = store.refillReminderDue("CUST-001")
        assertNotNull(nextReminder)
        assertEquals(ord2DoneAt, nextReminder!!.lastCompletedAt)
    }

    @Test
    fun `T30 admin delivery settings validation rejects bad inputs and audits valid updates`() {
        val store = BlueGasViewModel().store
        val base = store.appState.value.settings

        // Overlapping slots rejected
        assertFalse(
            store.updateSettings(
                base.copy(deliveryWindowsHours = listOf(10 to 14, 12 to 16)),
                "System Administrator"
            ).first
        )
        // Slot outside 7..22 rejected
        assertFalse(
            store.updateSettings(
                base.copy(deliveryWindowsHours = listOf(6 to 9)),
                "System Administrator"
            ).first
        )
        // Slot longer than 4h rejected
        assertFalse(
            store.updateSettings(
                base.copy(deliveryWindowsHours = listOf(10 to 16)),
                "System Administrator"
            ).first
        )
        // Out-of-range cutoff, negative Express fee, invalid Express hours, invalid lead days rejected
        assertFalse(store.updateSettings(base.copy(standardCutoffHour = 23), "System Administrator").first)
        assertFalse(store.updateSettings(base.copy(expressFeePaisa = -100L), "System Administrator").first)
        assertFalse(store.updateSettings(base.copy(expressHours = 8), "System Administrator").first)
        assertFalse(store.updateSettings(base.copy(refillReminderLeadDays = 15), "System Administrator").first)

        // Valid update succeeds and sorts slots + logs audit entry
        val valid = base.copy(
            asapEnabled = false,
            tomorrowWindowsEnabled = false,
            deliveryWindowsHours = listOf(16 to 18, 10 to 12),
            standardCutoffHour = 18,
            expressEnabled = true,
            expressFeePaisa = 30_000L,
            expressHours = 3,
            refillReminderEnabled = false,
            refillReminderLeadDays = 5
        )
        val (ok, msg) = store.updateSettings(valid, "System Administrator")
        assertTrue(msg, ok)
        val saved = store.appState.value.settings
        assertEquals(listOf(10 to 12, 16 to 18), saved.deliveryWindowsHours)
        assertEquals(18, saved.standardCutoffHour)
        assertEquals(30_000L, saved.expressFeePaisa)
        assertEquals(3, saved.expressHours)
        assertFalse(saved.asapEnabled)
        assertFalse(saved.tomorrowWindowsEnabled)
        assertFalse(saved.refillReminderEnabled)
        assertEquals(5, saved.refillReminderLeadDays)

        val latestAudit = store.auditLogs.last()
        assertEquals("UPDATE_SETTINGS", latestAudit.action)
        assertTrue(latestAudit.details.contains("expressFeePaisa"))
        assertTrue(latestAudit.details.contains("standardCutoffHour"))
    }
}
