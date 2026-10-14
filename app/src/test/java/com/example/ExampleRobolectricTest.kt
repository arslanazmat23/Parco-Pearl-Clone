package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.bluegas.lifecycle.OrderStateMachine
import com.example.bluegas.lifecycle.TransitionResult
import com.example.bluegas.model.*
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
}
