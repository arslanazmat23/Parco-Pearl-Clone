package com.example.bluegas.data

import com.example.bluegas.model.*
import java.util.UUID

/**
 * Called when a filled cylinder is handed to the customer (doorstep or counter).
 * Creates or moves the deposit (BR-DEP-05) and updates the holding.
 */
internal fun BlueGasStore.onCylinderHandedToCustomer(
    order: Order,
    newSerial: String,
    emptySerial: String?
) {
    val sku = order.items.firstOrNull { it.lineType != "ACCESSORY" && it.lineType != "DEPOSIT" }?.sku ?: "SKU-LPG-11-8"
    val now = System.currentTimeMillis()

    when (order.orderType) {
        OrderType.NEW_CYLINDER -> {
            if (order.depositTotalPaisa > 0) {
                val depId = "DEP-" + UUID.randomUUID().toString().take(4).uppercase()
                val payment = payments.firstOrNull { it.orderId == order.id }

                val deposit = DepositAccount(
                    id = depId,
                    customerId = order.customerId,
                    sku = sku,
                    cylinderSerial = newSerial,
                    amountPaisa = order.depositTotalPaisa,
                    paidAt = now,
                    orderId = order.id,
                    paymentId = payment?.id,
                    status = DepositStatus.HELD,
                    noDepositOnRecord = false
                )
                depositAccounts.add(deposit)

                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.DEPOSIT_HELD,
                        depositPaisa = order.depositTotalPaisa,
                        acquiredAt = now,
                        noDepositOnRecord = false,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )

                addNotification(
                    surface = AppSurface.CUSTOMER,
                    title = "Deposit recorded",
                    message = "Rs ${formatRupees(order.depositTotalPaisa)} deposit recorded for cylinder $newSerial. It is refundable when you return the cylinder.",
                    canonicalStatus = "DEPOSIT_HELD",
                    orderId = order.id,
                    code = "NTF-19"
                )
            }
        }

        OrderType.REFILL_EXCHANGE, OrderType.REFILL_REORDER -> {
            // Find existing held deposit account
            var matchedAccount: DepositAccount? = null
            if (!emptySerial.isNullOrBlank()) {
                matchedAccount = depositAccounts.firstOrNull {
                    it.customerId == order.customerId && it.cylinderSerial == emptySerial && it.status == DepositStatus.HELD
                }
            }
            if (matchedAccount == null && emptySerial.isNullOrBlank()) {
                val heldForSku = depositAccounts.filter {
                    it.customerId == order.customerId && it.sku == sku && it.status == DepositStatus.HELD
                }
                if (heldForSku.size == 1) {
                    matchedAccount = heldForSku.first()
                }
            }

            if (matchedAccount != null) {
                // Move deposit to new cylinder
                val oldSerial = matchedAccount.cylinderSerial
                matchedAccount.cylinderSerial = newSerial

                // Mark old holding as RETURNED
                customerHoldings.filter {
                    it.customerId == order.customerId && it.cylinderSerial == oldSerial && it.ownership == CylinderOwnership.DEPOSIT_HELD
                }.forEach {
                    it.ownership = CylinderOwnership.RETURNED
                    it.status = "RETURNED"
                }

                // Add new holding for newSerial with the same depositPaisa
                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.DEPOSIT_HELD,
                        depositPaisa = matchedAccount.amountPaisa,
                        acquiredAt = now,
                        noDepositOnRecord = matchedAccount.noDepositOnRecord,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )

                logAudit(
                    entityType = "DepositAccount",
                    entityId = matchedAccount.id,
                    action = "DEPOSIT_MOVED",
                    actor = AccountableRole.HUB.displayName,
                    role = AccountableRole.HUB,
                    details = "Deposit moved from empty cylinder $oldSerial to new cylinder $newSerial (Order ${order.id})"
                )
            } else {
                // Dealer cylinder or no deposit account on record
                val depId = "DEP-" + UUID.randomUUID().toString().take(4).uppercase()
                depositAccounts.add(
                    DepositAccount(
                        id = depId,
                        customerId = order.customerId,
                        sku = sku,
                        cylinderSerial = newSerial,
                        amountPaisa = 0L,
                        paidAt = now,
                        orderId = order.id,
                        paymentId = null,
                        status = DepositStatus.HELD,
                        noDepositOnRecord = true
                    )
                )

                customerHoldings.add(
                    CustomerCylinderHolding(
                        id = "HOLD-" + UUID.randomUUID().toString().take(6),
                        customerId = order.customerId,
                        cylinderSerial = newSerial,
                        ownership = CylinderOwnership.LOANED,
                        depositPaisa = 0L,
                        acquiredAt = now,
                        noDepositOnRecord = true,
                        depositStatus = DepositStatus.HELD,
                        status = "ACTIVE"
                    )
                )

                val emptyId = emptySerial ?: "UNKNOWN"
                reconExceptions.add(
                    ReconciliationException(
                        id = "EXC26-" + UUID.randomUUID().toString().take(6),
                        code = "EXC-26",
                        description = "Cylinder $emptyId offered for exchange has no deposit on record. Registered as Loaned (open decision Q30).",
                        ownerRole = AccountableRole.INVENTORY
                    )
                )
            }
        }

        else -> {
            // Accessories or other order types do not modify cylinder deposits
        }
    }
}
