package com.example.bluegas.data

import com.example.bluegas.model.*
import kotlin.math.ceil
import kotlin.math.roundToLong

data class QuoteResult(
    val lines: List<OrderLine>,
    val subtotalPaisa: Long,
    val depositTotalPaisa: Long,
    val platformFeePaisa: Long,
    val deliveryFeePaisa: Long,
    val discountPaisa: Long,
    val gstPaisa: Long,
    val furtherTaxPaisa: Long,
    val taxPaisa: Long,
    val totalAmountPaisa: Long,
    val reference118PricePaisa: Long?,
    val priceListVersion: Int
)

object PricingEngine {

    /**
     * Exact unit price in paisa with half-up rounding (UAT-19, BR-PRC-01).
     */
    fun unitPriceExact(sku: String, priceList: PriceList): Long {
        if (priceList.method == PriceMethod.PRICE_PER_SIZE) {
            return priceList.perSizePaisa[sku] ?: 0L
        }

        // REFERENCE_11_8_BY_WEIGHT: reference * sizeKg / 11.8
        val sizeKg = when (sku) {
            "SKU-LPG-11-8" -> 11.8
            "SKU-LPG-11-0" -> 11.0
            "SKU-LPG-6-0"  -> 6.0
            "SKU-LPG-45-4" -> 45.4
            else -> return priceList.perSizePaisa[sku] ?: 0L
        }

        if (sizeKg == 11.8) {
            return priceList.reference118Paisa
        }

        val num = priceList.reference118Paisa * (sizeKg * 10.0).roundToLong()
        val den = 118L
        // Round half up to nearest integer paisa: (2*num + den) / (2*den)
        return (2L * num + den) / (2L * den)
    }

    /**
     * Published unit price rounded to whole rupees (BR-PRC-03).
     */
    fun unitPricePublished(sku: String, priceList: PriceList): Long {
        val exact = unitPriceExact(sku, priceList)
        return roundToRupees(exact, priceList.roundingRule)
    }

    fun roundToRupees(amountPaisa: Long, rule: RoundingRule = RoundingRule.NEAREST): Long {
        return when (rule) {
            RoundingRule.NEAREST -> Math.round(amountPaisa / 100.0) * 100L
            RoundingRule.UP -> ceil(amountPaisa / 100.0).toLong() * 100L
            RoundingRule.DOWN -> (amountPaisa / 100L) * 100L
        }
    }

    /**
     * Build itemized quote in exact compliance with BR-PRC-05.
     */
    fun buildQuote(
        orderType: OrderType,
        sku: String,
        quantity: Int,
        accessories: List<Pair<String, Int>> = emptyList(), // sku to qty
        mode: FulfilmentMode,
        priceList: PriceList,
        tax: TaxConfig,
        settings: PlatformSettings,
        discountPaisa: Long = 0L
    ): QuoteResult {
        val lines = mutableListOf<OrderLine>()

        // 1. Gas Line (if not accessory only)
        if (orderType != OrderType.ACCESSORY_ONLY) {
            val exactPrice = unitPriceExact(sku, priceList)
            val pubPrice = unitPricePublished(sku, priceList)
            val lineSubtotal = pubPrice * quantity
            val productName = when (sku) {
                "SKU-LPG-11-8" -> "11.8 kg Domestic LPG Cylinder Refill"
                "SKU-LPG-11-0" -> "11.0 kg Domestic LPG Cylinder Refill"
                "SKU-LPG-6-0"  -> "6.0 kg Compact LPG Cylinder Refill"
                "SKU-LPG-45-4" -> "45.4 kg Commercial LPG Cylinder Refill"
                else -> "LPG Refill"
            }
            lines.add(
                OrderLine(
                    sku = sku,
                    productName = productName,
                    quantity = quantity,
                    unitPricePaisa = pubPrice,
                    unitDepositPaisa = 0L,
                    subtotalPaisa = lineSubtotal,
                    depositTotalPaisa = 0L,
                    lineType = "GAS",
                    unitPriceExactPaisa = exactPrice
                )
            )
        }

        // 2. Cylinder Line (if NEW_CYLINDER)
        var depositTotal = 0L
        if (orderType == OrderType.NEW_CYLINDER) {
            val model = settings.newCylinderModel
            when (model) {
                NewCylinderModel.SOLD -> {
                    val salePrice = roundToRupees(priceList.cylinderSalePaisa[sku] ?: 0L, priceList.roundingRule)
                    lines.add(
                        OrderLine(
                            sku = "$sku-BODY",
                            productName = "Cylinder Body (Purchase - Customer Owned)",
                            quantity = quantity,
                            unitPricePaisa = salePrice,
                            unitDepositPaisa = 0L,
                            subtotalPaisa = salePrice * quantity,
                            depositTotalPaisa = 0L,
                            lineType = "CYLINDER",
                            unitPriceExactPaisa = salePrice
                        )
                    )
                }
                NewCylinderModel.DEPOSIT -> {
                    val depPrice = roundToRupees(priceList.depositPaisa[sku] ?: 0L, priceList.roundingRule)
                    depositTotal = depPrice * quantity
                    lines.add(
                        OrderLine(
                            sku = "$sku-DEP",
                            productName = "Cylinder Security Deposit",
                            quantity = quantity,
                            unitPricePaisa = 0L,
                            unitDepositPaisa = depPrice,
                            subtotalPaisa = 0L,
                            depositTotalPaisa = depositTotal,
                            lineType = "DEPOSIT",
                            unitPriceExactPaisa = depPrice
                        )
                    )
                }
                NewCylinderModel.LOAN -> {
                    lines.add(
                        OrderLine(
                            sku = "$sku-LOAN",
                            productName = "Loaned Cylinder (BLUE GAS Asset)",
                            quantity = quantity,
                            unitPricePaisa = 0L,
                            unitDepositPaisa = 0L,
                            subtotalPaisa = 0L,
                            depositTotalPaisa = 0L,
                            lineType = "LOAN",
                            unitPriceExactPaisa = 0L
                        )
                    )
                }
            }
        }

        // 3. Accessories
        for ((accSku, accQty) in accessories) {
            val rawAccPrice = priceList.accessoryPaisa[accSku] ?: continue
            val accPrice = roundToRupees(rawAccPrice, priceList.roundingRule)
            lines.add(
                OrderLine(
                    sku = accSku,
                    productName = if (accSku == "SKU-REG-HOSE") "Low-Pressure Regulator & Safety Hose" else "Accessory",
                    quantity = accQty,
                    unitPricePaisa = accPrice,
                    unitDepositPaisa = 0L,
                    subtotalPaisa = accPrice * accQty,
                    depositTotalPaisa = 0L,
                    lineType = "ACCESSORY",
                    unitPriceExactPaisa = accPrice
                )
            )
        }

        val subtotalPaisa = lines.filter { it.lineType != "DEPOSIT" }.sumOf { it.subtotalPaisa }
        val platformFee = roundToRupees(priceList.platformFeePaisa, priceList.roundingRule)
        val deliveryFee = if (mode == FulfilmentMode.DELIVERY) roundToRupees(priceList.deliveryFeePaisa, priceList.roundingRule) else 0L

        // Taxable base includes gas, cylinder sale, accessories, platform fee, delivery fee (DEPOSIT EXCLUDED)
        val taxableBase = (subtotalPaisa + platformFee + deliveryFee - discountPaisa).coerceAtLeast(0L)
        val gst = roundToRupees((taxableBase * tax.gstBps) / 10000L, priceList.roundingRule)
        val furtherTax = if (tax.furtherTaxApplies) roundToRupees((taxableBase * tax.furtherTaxBps) / 10000L, priceList.roundingRule) else 0L
        val totalTax = gst + furtherTax
        val totalAmount = taxableBase + totalTax + depositTotal

        return QuoteResult(
            lines = lines,
            subtotalPaisa = subtotalPaisa,
            depositTotalPaisa = depositTotal,
            platformFeePaisa = platformFee,
            deliveryFeePaisa = deliveryFee,
            discountPaisa = discountPaisa,
            gstPaisa = gst,
            furtherTaxPaisa = furtherTax,
            taxPaisa = totalTax,
            totalAmountPaisa = totalAmount,
            reference118PricePaisa = priceList.reference118Paisa,
            priceListVersion = priceList.version
        )
    }
}
