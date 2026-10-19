package com.example.bluegas.data

import com.example.bluegas.model.*
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

/**
 * Supply Chain Store Operations (Plant Manager RACI-23/24; Inventory Controller RACI-04/25/32/35)
 */

fun BlueGasStore.requestStock(
    pointId: String,
    lines: Map<String, Int>,
    actor: String = "Distributor Lead"
): Pair<Boolean, String> {
    val validLines = lines.filter { it.value > 0 }
    if (validLines.isEmpty()) {
        return Pair(false, "At least one SKU quantity must be greater than zero.")
    }

    val transferLines = mutableListOf<TransferLine>()
    for ((sku, qty) in validLines) {
        val pv = productVariants.find { it.sku == sku }
        if (pv == null || !pv.isCylinder || !pv.isHouseholdApproved) {
            return Pair(false, "SKU $sku is not an approved household cylinder SKU.")
        }
        transferLines.add(TransferLine(skuId = sku, qtySent = qty))
    }

    val id = "TRF-" + (1000..9999).random()
    val transfer = StockTransfer(
        id = id,
        direction = "PLANT_TO_POINT",
        fromPointId = "PLANT-01",
        toPointId = pointId,
        status = "REQUESTED",
        lines = transferLines,
        requestedBy = actor,
        requestedAt = System.currentTimeMillis()
    )
    stockTransfers.add(transfer)

    addNotification(
        surface = AppSurface.STAFF,
        title = "Stock Transfer Requested",
        message = "Transfer $id requested for node $pointId (${transferLines.sumOf { it.qtySent }} cylinders).",
        canonicalStatus = "REQUESTED",
        orderId = null,
        code = "NTF-15",
        staffRole = StaffSubRole.PLANT_INVENTORY
    )
    logAudit("StockTransfer", id, "REQUEST_STOCK", actor, AccountableRole.DISTRIBUTOR, "Requested stock transfer for $pointId")
    notifyChanged()
    return Pair(true, "Stock request $id created.")
}

fun BlueGasStore.recordPlantDispatch(
    transferId: String,
    gatePassNo: String,
    vehicleNo: String,
    driverName: String,
    driverPhone: String,
    cylinderIds: List<String>,
    cmrNo: String?,
    filledBy: String?,
    actor: String = "Plant Manager"
): Pair<Boolean, String> {
    val trf = stockTransfers.find { it.id == transferId }
        ?: return Pair(false, "Transfer $transferId not found.")

    if (trf.status != "REQUESTED" || trf.direction != "PLANT_TO_POINT") {
        return Pair(false, "Transfer is not an active REQUESTED plant-to-point transfer.")
    }
    if (gatePassNo.isBlank()) return Pair(false, "Gate pass number is required.")
    if (vehicleNo.isBlank()) return Pair(false, "Vehicle number is required.")
    if (driverName.isBlank()) return Pair(false, "Driver name is required.")

    if (stockTransfers.any { it.id != transferId && it.gatePassNo.equals(gatePassNo, ignoreCase = true) }) {
        return Pair(false, "Gate pass number $gatePassNo is already in use by another transfer.")
    }

    var totalTonnes = 0.0
    for (line in trf.lines) {
        val pv = productVariants.find { it.sku == line.skuId }
        val sizeKg = pv?.cylinderSizeKg ?: 11.8
        totalTonnes += (line.qtySent * sizeKg) / 1000.0

        val plantStock = stockBalances.find { it.pointId == "PLANT-01" && it.skuId == line.skuId }
        if (plantStock != null) {
            plantStock.filledQty = (plantStock.filledQty - line.qtySent).coerceAtLeast(0)
        }
    }

    val assignedCylinders = mutableListOf<String>()
    for (cid in cylinderIds) {
        val cyl = cylinders.find { it.serialNumber == cid }
        if (cyl != null && cyl.status == InventoryState.INV_AVAIL && !cyl.hseHold) {
            val cIdx = cylinders.indexOf(cyl)
            cylinders[cIdx] = cyl.copy(
                status = InventoryState.INV_TRANSIT,
                custody = CylinderCustody.RIDER_VAN,
                currentLocationLabel = "On gate pass $gatePassNo (In transit)"
            )
            cylinderCustodyEvents.add(
                CylinderCustodyEvent(
                    id = "EVT-" + UUID.randomUUID().toString().take(6),
                    serialNumber = cid,
                    fromCustody = CylinderCustody.PLANT,
                    toCustody = CylinderCustody.RIDER_VAN,
                    orderId = null,
                    actor = actor
                )
            )
            assignedCylinders.add(cid)
        }
    }

    val trfIdx = stockTransfers.indexOf(trf)
    stockTransfers[trfIdx] = trf.copy(
        status = "DISPATCHED",
        gatePassNo = gatePassNo,
        vehicleNo = vehicleNo,
        driverName = driverName,
        driverPhone = driverPhone,
        cylinderIds = assignedCylinders,
        cmrNo = cmrNo,
        filledBy = filledBy,
        dispatchedAt = System.currentTimeMillis(),
        tonnesEquivalent = totalTonnes
    )

    addNotification(
        surface = AppSurface.STAFF,
        title = "Stock Transfer Dispatched",
        message = "Transfer $transferId on GP $gatePassNo dispatched to ${trf.toPointId}.",
        canonicalStatus = "DISPATCHED",
        orderId = null,
        code = "NTF-15",
        staffRole = StaffSubRole.DISTRIBUTOR
    )
    logAudit("StockTransfer", transferId, "DISPATCH_TRANSFER", actor, AccountableRole.PLANT, "Dispatched on GP $gatePassNo with ${assignedCylinders.size} cylinders scanned")
    notifyChanged()
    return Pair(true, "Transfer $transferId dispatched on Gate Pass $gatePassNo.")
}

fun BlueGasStore.confirmTransferReceipt(
    transferId: String,
    received: Map<String, Int>,
    actor: String = "Distributor Lead"
): Pair<Boolean, String> {
    val trf = stockTransfers.find { it.id == transferId }
        ?: return Pair(false, "Transfer $transferId not found.")

    if (trf.status != "DISPATCHED" || trf.direction != "PLANT_TO_POINT") {
        return Pair(false, "Transfer is not in DISPATCHED state.")
    }

    var hasDifference = false
    var totalReceivedTradePaisa = 0L
    val diffDetails = mutableListOf<String>()

    val updatedLines = trf.lines.map { line ->
        val recQty = received[line.skuId] ?: line.qtySent
        if (recQty < 0 || recQty > line.qtySent) {
            return Pair(false, "Received quantity for ${line.skuId} must be between 0 and sent ${line.qtySent}.")
        }
        if (recQty != line.qtySent) {
            hasDifference = true
            diffDetails.add("${line.skuId}: sent ${line.qtySent}, received $recQty")
        }

        val destStock = stockBalances.find { it.pointId == trf.toPointId && it.skuId == line.skuId }
        if (destStock != null) {
            destStock.filledQty += recQty
        } else {
            stockBalances.add(StockBalance(trf.toPointId, line.skuId, filledQty = recQty, emptyQty = 0))
        }

        val tradePrice = currentTradePriceList.perSizePaisa[line.skuId] ?: 0L
        totalReceivedTradePaisa += (recQty * tradePrice)

        line.copy(qtyReceived = recQty)
    }

    val nodeName = nodes.find { it.id == trf.toPointId }?.name ?: trf.toPointId
    var allocatedToDestCount = 0
    val totalSent = trf.lines.sumOf { it.qtySent }
    val totalRec = updatedLines.sumOf { it.qtyReceived ?: 0 }

    for (cid in trf.cylinderIds) {
        val cyl = cylinders.find { it.serialNumber == cid }
        if (cyl != null) {
            val cIdx = cylinders.indexOf(cyl)
            if (allocatedToDestCount < totalRec) {
                cylinders[cIdx] = cyl.copy(
                    status = InventoryState.INV_AVAIL,
                    custody = CylinderCustody.DISTRIBUTOR_NODE,
                    currentLocationLabel = nodeName
                )
                cylinderCustodyEvents.add(
                    CylinderCustodyEvent(
                        id = "EVT-" + UUID.randomUUID().toString().take(6),
                        serialNumber = cid,
                        fromCustody = CylinderCustody.RIDER_VAN,
                        toCustody = CylinderCustody.DISTRIBUTOR_NODE,
                        orderId = null,
                        actor = actor
                    )
                )
                allocatedToDestCount++
            } else {
                cylinders[cIdx] = cyl.copy(
                    status = InventoryState.INV_TRANSIT,
                    custody = CylinderCustody.RIDER_VAN,
                    currentLocationLabel = "Missing on ${trf.gatePassNo ?: trf.id} — under investigation"
                )
            }
        }
    }

    val newStatus = if (hasDifference) "RECEIVED_WITH_DIFFERENCE" else "RECEIVED"
    val trfIdx = stockTransfers.indexOf(trf)
    stockTransfers[trfIdx] = trf.copy(
        status = newStatus,
        lines = updatedLines,
        receivedAt = System.currentTimeMillis()
    )

    val distId = nodes.find { it.id == trf.toPointId }?.distributorId ?: distributors.firstOrNull()?.id ?: "DIST-001"
    distributorAccountEntries.add(
        DistributorAccountEntry(
            id = "ENTRY-" + UUID.randomUUID().toString().take(6),
            distributorId = distId,
            type = "STOCK_RECEIVED",
            transferId = transferId,
            debitPaisa = totalReceivedTradePaisa,
            note = "Stock received on transfer $transferId ($newStatus)"
        )
    )

    if (hasDifference) {
        val excId = "EXC18-" + UUID.randomUUID().toString().take(6)
        reconExceptions.add(
            ReconciliationException(
                id = excId,
                code = "EXC-18",
                description = "Transfer $transferId short by: ${diffDetails.joinToString(", ")}",
                ownerRole = AccountableRole.INVENTORY
            )
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Transfer Receipt Shortfall (EXC-18)",
            message = "Transfer $transferId received with difference at ${trf.toPointId}.",
            canonicalStatus = "RECEIVED_WITH_DIFFERENCE",
            orderId = null,
            code = "NTF-15",
            staffRole = StaffSubRole.PLANT_INVENTORY
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Transfer Receipt Shortfall",
            message = "Transfer $transferId received with difference. Variance exception logged.",
            canonicalStatus = "RECEIVED_WITH_DIFFERENCE",
            orderId = null,
            code = "NTF-15",
            staffRole = StaffSubRole.FINANCE
        )
    }

    logAudit("StockTransfer", transferId, "CONFIRM_RECEIPT", actor, AccountableRole.INVENTORY, "Confirmed receipt: $newStatus")
    notifyChanged()
    return Pair(true, if (hasDifference) "Transfer received with difference. Shortfall exception logged." else "Transfer $transferId received in full.")
}

fun BlueGasStore.requestEmptiesReturn(
    pointId: String,
    lines: Map<String, Int>,
    gatePassNo: String,
    vehicleNo: String,
    driverName: String,
    actor: String = "Distributor Lead"
): Pair<Boolean, String> {
    val validLines = lines.filter { it.value > 0 }
    if (validLines.isEmpty()) {
        return Pair(false, "At least one SKU empties quantity must be greater than zero.")
    }

    val openReturns = stockTransfers.filter {
        it.direction == "EMPTIES_TO_PLANT" && it.fromPointId == pointId && it.status == "DISPATCHED"
    }

    val transferLines = mutableListOf<TransferLine>()
    for ((sku, qty) in validLines) {
        val stock = stockBalances.find { it.pointId == pointId && it.skuId == sku }
        val pointEmpties = stock?.emptyQty ?: 0
        val committedEmpties = openReturns.sumOf { r -> r.lines.find { it.skuId == sku }?.qtySent ?: 0 }
        val availableEmpties = (pointEmpties - committedEmpties).coerceAtLeast(0)

        if (qty > availableEmpties) {
            return Pair(false, "Cannot return $qty of $sku; only $availableEmpties available at point.")
        }
        transferLines.add(TransferLine(skuId = sku, qtySent = qty))
    }

    val id = "TRF-E-" + (1000..9999).random()
    val transfer = StockTransfer(
        id = id,
        direction = "EMPTIES_TO_PLANT",
        fromPointId = pointId,
        toPointId = "PLANT-01",
        status = "DISPATCHED",
        gatePassNo = gatePassNo,
        vehicleNo = vehicleNo,
        driverName = driverName,
        lines = transferLines,
        dispatchedAt = System.currentTimeMillis()
    )
    stockTransfers.add(transfer)

    addNotification(
        surface = AppSurface.STAFF,
        title = "Empties Dispatched to Plant",
        message = "Empties return $id (${transferLines.sumOf { it.qtySent }} cylinders) dispatched from $pointId.",
        canonicalStatus = "DISPATCHED",
        orderId = null,
        code = "NTF-15",
        staffRole = StaffSubRole.PLANT_INVENTORY
    )
    logAudit("StockTransfer", id, "RETURN_EMPTIES", actor, AccountableRole.DISTRIBUTOR, "Dispatched empties return to plant on GP $gatePassNo")
    notifyChanged()
    return Pair(true, "Empties return transfer $id dispatched.")
}

fun BlueGasStore.confirmEmptiesReceivedAtPlant(
    transferId: String,
    actor: String = "Plant Manager"
): Pair<Boolean, String> {
    val trf = stockTransfers.find { it.id == transferId }
        ?: return Pair(false, "Transfer $transferId not found.")

    if (trf.status != "DISPATCHED" || trf.direction != "EMPTIES_TO_PLANT") {
        return Pair(false, "Transfer is not an active dispatched empties return.")
    }

    for (line in trf.lines) {
        val pointStock = stockBalances.find { it.pointId == trf.fromPointId && it.skuId == line.skuId }
        pointStock?.let { it.emptyQty = (it.emptyQty - line.qtySent).coerceAtLeast(0) }

        val plantStock = stockBalances.find { it.pointId == "PLANT-01" && it.skuId == line.skuId }
        if (plantStock != null) {
            plantStock.emptyQty += line.qtySent
        } else {
            stockBalances.add(StockBalance("PLANT-01", line.skuId, filledQty = 0, emptyQty = line.qtySent))
        }
    }

    val trfIdx = stockTransfers.indexOf(trf)
    stockTransfers[trfIdx] = trf.copy(
        status = "RECEIVED",
        receivedAt = System.currentTimeMillis()
    )

    logAudit("StockTransfer", transferId, "CONFIRM_EMPTIES_RECEIVED", actor, AccountableRole.PLANT, "Empty receive recorded (Add Empty Receive)")
    notifyChanged()
    return Pair(true, "Empty receive recorded (Add Empty Receive)")
}

fun BlueGasStore.savePlantDailyReport(
    report: PlantDailyReport,
    actor: String = "Plant Manager"
): Pair<Boolean, String> {
    if (report.openingTonnes < 0 || report.purchasesTonnes < 0 || report.issuedTonnes < 0 || report.closingTonnes < 0) {
        return Pair(false, "Tonnage figures must be non-negative.")
    }

    val expectedClosing = report.openingTonnes + report.purchasesTonnes - report.issuedTonnes
    if (abs(expectedClosing - report.closingTonnes) >= 0.00005) {
        return Pair(false, "Closing must equal opening + purchases − issued")
    }

    val existing = plantReports.find { it.date == report.date }
    if (existing != null) {
        val idx = plantReports.indexOf(existing)
        plantReports[idx] = report
    } else {
        plantReports.add(0, report)
    }

    logAudit("PlantDailyReport", report.date, "SAVE_REPORT", actor, AccountableRole.PLANT, "Saved daily plant report: ${report.closingTonnes}t closing")
    notifyChanged()
    return Pair(true, "Plant daily report for ${report.date} saved.")
}

fun BlueGasStore.importPlantFileDemo(): Pair<Boolean, String> {
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val todayGatePasses = stockTransfers.filter {
        it.direction == "PLANT_TO_POINT" && it.status != "REQUESTED" && !it.gatePassNo.isNullOrBlank()
    }.mapNotNull { it.gatePassNo }.toMutableList()

    if (!todayGatePasses.contains("GP-10427")) todayGatePasses.add("GP-10427")

    val report = PlantDailyReport(
        date = dateStr,
        openingTonnes = 47.8442,
        purchasesTonnes = 10.0000,
        issuedTonnes = 1.9170,
        closingTonnes = 55.9272,
        cylindersIssuedBySku = mapOf(
            "SKU-LPG-11-0" to 100,
            "SKU-LPG-11-8" to 50,
            "SKU-LPG-45-4" to 5
        ),
        gatePassesToBlueGas = todayGatePasses,
        reference118Rate = 445000L,
        cashReceivedPaisa = 60000000L,
        source = "FILE_IMPORT"
    )

    savePlantDailyReport(report, "Plant Manager")
    return Pair(true, "Demo plant file imported (Q21 — real link not decided)")
}

fun BlueGasStore.runDailyReconciliation(
    counted: Map<String, Pair<Int, Int>>,
    actor: String = "Inventory Controller"
): Pair<Boolean, String> {
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
    val lines = mutableListOf<ReconLine>()
    val excIds = mutableListOf<String>()

    // Area 1: Household sales today (info)
    val completedOrders = orders.filter { it.status == OrderStatus.ORD_COMP }
    val cashOrders = completedOrders.filter { it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY }
    val onlineOrders = completedOrders.filter { it.paymentMethod == PaymentMethod.ONLINE_HOSTED }
    val totalCash = cashOrders.sumOf { it.amountReceivedPaisa ?: it.totalAmountPaisa }
    val totalOnline = onlineOrders.sumOf { it.totalAmountPaisa }

    lines.add(
        ReconLine(
            area = "1. Household Sales Today",
            skuId = "ALL",
            expected = "${completedOrders.size} orders",
            actual = "${completedOrders.size} orders",
            difference = "0",
            note = "Cash: ${formatPaisaToPkr(totalCash)}, Online: ${formatPaisaToPkr(totalOnline)}"
        )
    )

    // Area 2: Transfers vs plant gate passes
    val plantGatePasses = plantReports.flatMap { it.gatePassesToBlueGas }.toSet()
    val dispatchedTransfers = stockTransfers.filter { it.direction == "PLANT_TO_POINT" && !it.gatePassNo.isNullOrBlank() }

    for (trf in dispatchedTransfers) {
        val gp = trf.gatePassNo ?: ""
        val matched = plantGatePasses.contains(gp)
        if (!matched) {
            val excId = "EXC18-" + UUID.randomUUID().toString().take(6)
            reconExceptions.add(
                ReconciliationException(
                    id = excId,
                    code = "EXC-18",
                    description = "Transfer ${trf.id} (GP $gp) not matched in plant daily report",
                    ownerRole = AccountableRole.INVENTORY
                )
            )
            excIds.add(excId)
        }
        lines.add(
            ReconLine(
                area = "2. Gate Pass Match",
                skuId = gp,
                expected = "Gate Pass $gp",
                actual = if (matched) "In Plant Report" else "Missing from Plant Report",
                difference = if (matched) "Matched" else "Not in plant report",
                note = if (matched) "Gate pass verified" else "Discrepancy logged (EXC-18)"
            )
        )
    }

    // Area 3: Stock per point and size
    for (stock in stockBalances) {
        val key = "${stock.pointId}|${stock.skuId}"
        val (countedFilled, countedEmpty) = counted[key] ?: Pair(stock.filledQty, stock.emptyQty)

        val filledDiff = countedFilled - stock.filledQty
        val emptyDiff = countedEmpty - stock.emptyQty

        if (filledDiff != 0 || emptyDiff != 0) {
            val excId = "EXC18-" + UUID.randomUUID().toString().take(6)
            reconExceptions.add(
                ReconciliationException(
                    id = excId,
                    code = "EXC-18",
                    description = "Count difference at ${stock.pointId} ${stock.skuId}: system ${stock.filledQty}/${stock.emptyQty}, counted $countedFilled/$countedEmpty",
                    ownerRole = AccountableRole.INVENTORY
                )
            )
            excIds.add(excId)
        }

        lines.add(
            ReconLine(
                area = "3. Physical Stock Count",
                skuId = "${stock.pointId} - ${stock.skuId}",
                expected = "F:${stock.filledQty} E:${stock.emptyQty}",
                actual = "F:$countedFilled E:$countedEmpty",
                difference = if (filledDiff == 0 && emptyDiff == 0) "0" else "F:${if (filledDiff > 0) "+$filledDiff" else "$filledDiff"} E:${if (emptyDiff > 0) "+$emptyDiff" else "$emptyDiff"}",
                note = if (filledDiff == 0 && emptyDiff == 0) "Count matched" else "Physical count variance (EXC-18)"
            )
        )
    }

    // Area 4: Cash reconciliation
    val collectedCod = codCollections.sumOf { it.collectedAmountPaisa }
    val submittedCash = cashHandovers.sumOf { it.declaredPaisa }
    val cashDiff = submittedCash - collectedCod

    val now = System.currentTimeMillis()
    val staleCodOrders = orders.filter {
        it.paymentMethod == PaymentMethod.CASH_ON_DELIVERY &&
                it.status == OrderStatus.ORD_COMP &&
                activePaymentFor(it.id)?.state == PaymentState.CASH_COLLECTED &&
                cashHandovers.none { ch -> ch.orderIds.contains(it.id) } &&
                it.completedAt != null && (now - it.completedAt!!) > (settings.cashHandoverHours * 3600000L)
    }
    for (stale in staleCodOrders) {
        val excId = "EXC21-" + UUID.randomUUID().toString().take(6)
        reconExceptions.add(
            ReconciliationException(
                id = excId,
                code = "EXC-21",
                description = "Cash handover overdue for order ${stale.id} (> ${settings.cashHandoverHours} hrs)",
                ownerRole = AccountableRole.FINANCE
            )
        )
        excIds.add(excId)
    }

    lines.add(
        ReconLine(
            area = "4. Cash Hand-over vs Collections",
            skuId = "COD_CASH",
            expected = formatPaisaToPkr(collectedCod),
            actual = formatPaisaToPkr(submittedCash),
            difference = formatPaisaToPkr(cashDiff),
            note = if (staleCodOrders.isEmpty()) "Cash within SLA" else "${staleCodOrders.size} overdue hand-overs (EXC-21)"
        )
    )

    // Area 5: Tonnes equivalent
    val todayTonnesDispatched = dispatchedTransfers.sumOf { it.tonnesEquivalent }
    val latestReportTonnes = plantReports.firstOrNull()?.issuedTonnes ?: 0.0

    lines.add(
        ReconLine(
            area = "5. Tonnes Dispatched vs Plant",
            skuId = "BULK_TONNES",
            expected = String.format(Locale.US, "%.4f t", todayTonnesDispatched),
            actual = String.format(Locale.US, "%.4f t", latestReportTonnes),
            difference = String.format(Locale.US, "%.4f t", latestReportTonnes - todayTonnesDispatched),
            note = "Plant issues also include non-household sales"
        )
    )

    val overallStatus = if (excIds.isEmpty()) "MATCHED" else "DIFFERENCES_FOUND"
    val recon = DailyReconciliation(
        id = "RECON-" + (1000..9999).random(),
        date = dateStr,
        runBy = actor,
        lines = lines,
        status = overallStatus,
        exceptionIds = excIds
    )
    reconciliations.add(0, recon)

    if (overallStatus == "DIFFERENCES_FOUND") {
        addNotification(
            surface = AppSurface.STAFF,
            title = "Reconciliation Differences Found",
            message = "Daily reconciliation for $dateStr generated ${excIds.size} exceptions.",
            canonicalStatus = "DIFFERENCES_FOUND",
            orderId = null,
            code = "NTF-15",
            staffRole = StaffSubRole.PLANT_INVENTORY
        )
        addNotification(
            surface = AppSurface.STAFF,
            title = "Reconciliation Differences Logged",
            message = "Daily inventory & cash reconciliation variances logged for Finance review.",
            canonicalStatus = "DIFFERENCES_FOUND",
            orderId = null,
            code = "NTF-15",
            staffRole = StaffSubRole.FINANCE
        )
    }

    logAudit("DailyReconciliation", recon.id, "RUN_RECONCILIATION", actor, AccountableRole.INVENTORY, "Status: $overallStatus (${excIds.size} exceptions)")
    notifyChanged()
    return Pair(true, "Reconciliation finished: $overallStatus (${lines.size} check lines, ${excIds.size} exceptions).")
}

fun BlueGasStore.resolveReconException(
    exceptionId: String,
    note: String,
    actor: String
): Pair<Boolean, String> {
    if (note.isBlank()) {
        return Pair(false, "Resolution note is required.")
    }
    val exc = reconExceptions.find { it.id == exceptionId }
        ?: return Pair(false, "Exception $exceptionId not found.")

    exc.status = "RESOLVED"
    exc.resolution = note
    logAudit("ReconciliationException", exceptionId, "RESOLVE_EXCEPTION", actor, exc.ownerRole, note)
    notifyChanged()
    return Pair(true, "Exception ${exc.code} resolved.")
}

fun BlueGasStore.tagCylinder(
    serialNumber: String,
    actor: String = "Inventory Controller"
): Pair<Boolean, String> {
    val cyl = cylinders.find { it.serialNumber == serialNumber }
        ?: return Pair(false, "Cylinder $serialNumber not found.")

    if (cyl.isTagged) {
        return Pair(false, "Cylinder $serialNumber is already tagged.")
    }

    val idx = cylinders.indexOf(cyl)
    cylinders[idx] = cyl.copy(isTagged = true)

    cylinderCustodyEvents.add(
        CylinderCustodyEvent(
            id = "EVT-" + UUID.randomUUID().toString().take(6),
            serialNumber = serialNumber,
            fromCustody = cyl.custody,
            toCustody = cyl.custody,
            orderId = null,
            actor = actor
        )
    )

    logAudit("Cylinder", serialNumber, "TAG_CYLINDER", actor, AccountableRole.INVENTORY, "QR barcode tag affixed")
    notifyChanged()
    return Pair(true, "Cylinder $serialNumber tagged with QR identifier.")
}
