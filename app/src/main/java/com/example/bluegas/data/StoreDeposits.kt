package com.example.bluegas.data

import com.example.bluegas.model.*
import java.util.UUID

/**
 * On handover of a cylinder to a customer, update deposit records according to order type.
 */
internal fun BlueGasStore.onCylinderHandedToCustomer(
    order: Order,
    newSerial: String,
    emptySerial: String?
) {
    when (order.orderType) {
        OrderType.NEW_CYLINDER -> {
            val depositAmount = order.lines.firstOrNull { it.lineType == "DEPOSIT" }?.depositTotalPaisa ?: 0L
            val depId = "DEP-" + UUID.randomUUID().toString().take(6)
            val deposit = DepositAccount(
                id = depId,
                customerId = order.customerId,
                cylinderSerial = newSerial,
                skuId = order.skuId,
                amountPaisa = depositAmount,
                orderId = order.id,
                status = DepositStatus.HELD
            )
            depositAccounts.add(deposit)

            val existingHolding = customerHoldings.find { it.customerId == order.customerId && it.cylinderSerial == newSerial && it.status == "ACTIVE" }
            if (existingHolding != null) {
                existingHolding.depositPaisa = depositAmount
                existingHolding.depositStatus = DepositStatus.HELD
                existingHolding.noDepositOnRecord = false
                existingHolding.ownership = CylinderOwnership.DEPOSIT_HELD
            } else {
                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.DEPOSIT_HELD,
                        depositPaisa = depositAmount,
                        acquiredAt = System.currentTimeMillis(),
                        noDepositOnRecord = false,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )
            }

            addNotification(
                surface = AppSurface.CUSTOMER,
                title = "Security deposit recorded",
                message = "Rs. ${formatRupees(depositAmount)} security deposit held for cylinder $newSerial. Refundable when returned in good condition.",
                canonicalStatus = "DEPOSIT_HELD",
                orderId = order.id,
                code = "NTF-19"
            )
        }

        OrderType.REFILL -> {
            val existingDeposit = if (emptySerial != null) {
                depositAccounts.firstOrNull { it.customerId == order.customerId && it.cylinderSerial == emptySerial && it.status == DepositStatus.HELD }
            } else null

            val oldHolding = if (emptySerial != null) {
                customerHoldings.find { it.customerId == order.customerId && it.cylinderSerial == emptySerial && it.status == "ACTIVE" }
            } else null

            if (oldHolding != null) {
                oldHolding.status = "EXCHANGED"
            }

            if (existingDeposit != null) {
                existingDeposit.cylinderSerial = newSerial
                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.DEPOSIT_HELD,
                        depositPaisa = existingDeposit.amountPaisa,
                        acquiredAt = System.currentTimeMillis(),
                        noDepositOnRecord = false,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )
            } else {
                val depId = "DEP-" + UUID.randomUUID().toString().take(6)
                depositAccounts.add(
                    DepositAccount(
                        id = depId,
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        skuId = order.skuId,
                        amountPaisa = 0L,
                        orderId = order.id,
                        status = DepositStatus.HELD
                    )
                )
                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.LOANED,
                        depositPaisa = 0L,
                        acquiredAt = System.currentTimeMillis(),
                        noDepositOnRecord = true,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )
            }
        }

        OrderType.DEALER_EXCHANGE -> {
            val oldHolding = if (emptySerial != null) {
                customerHoldings.find { it.customerId == order.customerId && it.cylinderSerial == emptySerial && it.status == "ACTIVE" }
            } else null
            if (oldHolding != null) {
                oldHolding.status = "EXCHANGED"
            }

            val depId = "DEP-" + UUID.randomUUID().toString().take(6)
            depositAccounts.add(
                DepositAccount(
                    id = depId,
                    customerId = order.customerId,
                    cylinderSerial = newSerial,
                    skuId = order.skuId,
                    amountPaisa = 0L,
                    orderId = order.id,
                    status = DepositStatus.HELD
                )
            )
            customerHoldings.add(
                CustomerCylinderHolding(
                    id = "HOLD-" + UUID.randomUUID().toString().take(6),
                    customerId = order.customerId,
                    cylinderSerial = newSerial,
                    ownership = CylinderOwnership.LOANED,
                    depositPaisa = 0L,
                    acquiredAt = System.currentTimeMillis(),
                    noDepositOnRecord = true,
                    depositStatus = DepositStatus.HELD,
                    status = "ACTIVE"
                )
            )
        }

        OrderType.ACCESSORY_ONLY -> {
            // No cylinder handed over
        }
    }
}
