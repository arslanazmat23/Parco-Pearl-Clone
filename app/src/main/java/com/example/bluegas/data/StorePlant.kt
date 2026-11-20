package com.example.bluegas.data

import com.example.bluegas.model.*
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlin.math.abs
import kotlin.math.max

private val karachiZone: ZoneId = ZoneId.of("Asia/Karachi")
private val isoDateFmt: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE

/**
 * Returns today's date string (yyyy-MM-dd) in Asia/Karachi based on the store clock.
 */
internal fun BlueGasStore.todayKarachiDate(): String {
    return Instant.ofEpochMilli(clock()).atZone(karachiZone).toLocalDate().format(isoDateFmt)
}

/**
 * Returns tomorrow's date string (yyyy-MM-dd) in Asia/Karachi based on [nowMillis].
 */
internal fun defaultTomorrowDate(nowMillis: Long): String {
    return Instant.ofEpochMilli(nowMillis).atZone(karachiZone).toLocalDate().plusDays(1).format(isoDateFmt)
}

/**
 * Builds or replaces a proposed next-day plant fill plan across every hub and household cylinder
 * size using each hub's configured minimum filled level, open orders (reserved), and open
 * plant-to-hub transfers already in flight (To-Be P1).
 *
 * @param forDate Target ISO date string (`yyyy-MM-dd`), defaulting to tomorrow in Asia/Karachi.
 * @param actor Execution actor string; defaults to platform automation with Plant Manager accountable.
 * @return Success flag and summary message with generated plan ID.
 */
fun BlueGasStore.proposeFillPlan(
    forDate: String = defaultTomorrowDate(clock()),
    actor: String = "BLUE GAS platform (system)"
): Pair<Boolean, String> {
    val hubs = nodes.filter { it.type == FulfilmentPointType.HUB }
    val householdSkus = productVariants.filter { it.isCylinder && it.isHouseholdApproved }.map { it.sku }
    val lines = mutableListOf<FillPlanLine>()

    for (hub in hubs) {
        val hubMins = settings.hubMinimumFilled[hub.id] ?: emptyMap()
        for (sku in householdSkus) {
            val stock = stockBalances.find { it.pointId == hub.id && it.skuId == sku }
            val filled = stock?.filledQty ?: 0
            val reserved = stock?.reservedQty ?: 0
            val minimum = hubMins[sku] ?: 0
            val inTransit = stockTransfers
                .filter {
                    it.direction == "PLANT_TO_POINT" &&
                        it.toPointId == hub.id &&
                        (it.status == "REQUESTED" || it.status == "DISPATCHED")
                }
                .sumOf { trf -> trf.lines.find { it.skuId == sku }?.qtySent ?: 0 }
            val proposed = max(0, minimum - (filled - reserved) - inTransit)
            lines.add(
                FillPlanLine(
                    pointId = hub.id,
                    sku = sku,
                    filled = filled,
                    reserved = reserved,
                    inTransit = inTransit,
                    minimum = minimum,
                    proposedQty = proposed,
                    approvedQty = proposed
                )
            )
        }
    }

    fillPlans.removeAll { it.forDate == forDate && it.status == "PROPOSED" }
    val plan = FillPlan(
        id = "PLAN-" + UUID.randomUUID().toString().take(6).uppercase(),
        forDate = forDate,
        lines = lines,
        status = "PROPOSED",
        proposedAt = clock()
    )
    fillPlans.add(0, plan)

    logAudit(
        entityType = "FillPlan",
        entityId = plan.id,
        action = "PROPOSE_FILL_PLAN",
        actor = actor,
        role = AccountableRole.PLANT,
        details = "Proposed fill plan for $forDate across ${lines.size} hub lines"
    )
    notifyChanged()
    return Pair(true, "Fill plan ${plan.id} proposed for $forDate.")
}

/**
 * Approves a proposed fill plan, saves the Plant Manager's approved quantities, and creates one
 * `REQUESTED` plant-to-hub transfer per hub with approved quantity > 0 (`source = "FILL_PLAN"`).
 * Refuses re-approving an already approved plan.
 *
 * @param planId Identifier of the proposed [FillPlan].
 * @param approved Map of `"pointId|sku"` keys to approved quantities.
 * @param actor Accountable Plant Manager actor string.
 * @return Success flag and confirmation or validation failure reason.
 */
fun BlueGasStore.approveFillPlan(
    planId: String,
    approved: Map<String, Int>,
    actor: String = AccountableRole.PLANT.displayName
): Pair<Boolean, String> {
    val plan = fillPlans.find { it.id == planId }
        ?: return Pair(false, "Fill plan $planId not found.")
    if (plan.status == "APPROVED") {
        return Pair(false, "Fill plan $planId is already approved.")
    }
    if (approved.values.any { it < 0 }) {
        return Pair(false, "Approved quantities must be zero or greater.")
    }

    plan.lines.forEach { line ->
        val key = "${line.pointId}|${line.sku}"
        val qty = approved[key] ?: line.proposedQty
        line.approvedQty = qty.coerceAtLeast(0)
    }
    plan.status = "APPROVED"
    plan.approvedBy = actor
    plan.approvedAt = clock()

    val createdTransfers = mutableListOf<String>()
    val byHub = plan.lines.groupBy { it.pointId }
    for ((hubId, hubLines) in byHub) {
        val positiveMap = hubLines.filter { it.approvedQty > 0 }.associate { it.sku to it.approvedQty }
        if (positiveMap.isNotEmpty()) {
            val (ok, msg) = requestStock(
                pointId = hubId,
                lines = positiveMap,
                actor = actor,
                source = "FILL_PLAN"
            )
            if (ok) createdTransfers.add(msg)
        }
    }

    logAudit(
        entityType = "FillPlan",
        entityId = plan.id,
        action = "APPROVE_FILL_PLAN",
        actor = actor,
        role = AccountableRole.PLANT,
        details = "Approved fill plan ${plan.id} for ${plan.forDate}; created ${createdTransfers.size} transfer(s)"
    )
    notifyChanged()
    return Pair(true, "Fill plan ${plan.id} approved (${createdTransfers.size} transfer(s) requested).")
}

/**
 * Raises an urgent hub replenishment request (`source = "URGENT"`) and notifies the Plant Manager.
 *
 * @param pointId Requesting hub identifier.
 * @param lines Map of approved cylinder SKU to requested quantity.
 * @param actor Accountable Hub Staff actor string.
 * @return Success flag and transfer request confirmation or validation error.
 */
fun BlueGasStore.urgentTopUp(
    pointId: String,
    lines: Map<String, Int>,
    actor: String = AccountableRole.HUB.displayName
): Pair<Boolean, String> {
    val (ok, msg) = requestStock(
        pointId = pointId,
        lines = lines,
        actor = actor,
        source = "URGENT"
    )
    if (!ok) return Pair(false, msg)

    val hubName = nodes.find { it.id == pointId }?.name ?: pointId
    addNotification(
        surface = AppSurface.STAFF,
        title = "Urgent top-up requested",
        message = "Urgent top-up requested by $hubName",
        canonicalStatus = "URGENT",
        orderId = null,
        code = "NTF-15",
        staffRole = StaffSubRole.PLANT_INVENTORY
    )
    logAudit(
        entityType = "StockTransfer",
        entityId = pointId,
        action = "URGENT_TOP_UP",
        actor = actor,
        role = AccountableRole.HUB,
        details = "Urgent top-up requested by $hubName"
    )
    notifyChanged()
    return Pair(true, "Urgent top-up sent to Plant Manager.")
}

/**
 * Records filling an empty cylinder at the plant after verifying its safety test due date and
 * weight tolerance (±0.2 kg requires a reason). Transitions an `EMPTY_AT_PLANT` cylinder into
 * filled available plant stock (`INV_AVAIL`) and updates `PLANT-01` balances.
 *
 * @param serial Unique cylinder serial number.
 * @param weightKg Actual measured fill weight in kg.
 * @param reason Required note if `|weightKg - nominalSize| > 0.2`.
 * @param actor Accountable Plant Manager actor string.
 * @return Success flag and message.
 */
fun BlueGasStore.plantRecordFill(
    serial: String,
    weightKg: Double,
    reason: String? = null,
    actor: String = AccountableRole.PLANT.displayName
): Pair<Boolean, String> {
    val cyl = cylinders.find { it.serialNumber == serial }
        ?: return Pair(false, "Cylinder $serial not found.")
    if (cyl.custody != CylinderCustody.PLANT) {
        return Pair(false, "Cylinder $serial is not at the plant.")
    }
    if (cyl.status == InventoryState.INV_WRITEOFF) {
        return Pair(false, "Cylinder $serial has been written off.")
    }
    val today = todayKarachiDate()
    if (cyl.nextTestDueDate != null && cyl.nextTestDueDate <= today) {
        return Pair(false, "Due for its safety test — send to re-testing.")
    }
    if (weightKg <= 0.0) {
        return Pair(false, "Enter a positive fill weight.")
    }
    if (abs(weightKg - cyl.weightKg) > 0.2 && reason.isNullOrBlank()) {
        return Pair(false, "Reason is required when weight differs from ${cyl.weightKg} kg by more than 0.2 kg.")
    }

    val wasEmptyAtPlant = cyl.plantStage == "EMPTY_AT_PLANT"
    if (wasEmptyAtPlant) {
        val plantStock = stockBalances.find { it.pointId == "PLANT-01" && it.skuId == cyl.sku }
        if (plantStock != null) {
            plantStock.emptyQty = (plantStock.emptyQty - 1).coerceAtLeast(0)
            plantStock.filledQty += 1
        } else {
            stockBalances.add(StockBalance("PLANT-01", cyl.sku, filledQty = 1, emptyQty = 0))
        }
    }

    val idx = cylinders.indexOf(cyl)
    cylinders[idx] = cyl.copy(
        status = InventoryState.INV_AVAIL,
        lastFillDate = today,
        lastFillWeightKg = weightKg,
        plantStage = "FILLED",
        pointId = "PLANT-01",
        currentLocationLabel = "Plant Site"
    )
    cylinderCustodyEvents.add(
        CylinderCustodyEvent(
            id = "EVT-" + UUID.randomUUID().toString().take(6),
            serialNumber = serial,
            fromCustody = CylinderCustody.PLANT,
            toCustody = CylinderCustody.PLANT,
            orderId = null,
            actor = actor,
            timestamp = clock()
        )
    )
    logAudit(
        entityType = "Cylinder",
        entityId = serial,
        action = "PLANT_RECORD_FILL",
        actor = actor,
        role = AccountableRole.PLANT,
        details = "Filled $serial at plant (${weightKg} kg)${if (!reason.isNullOrBlank()) "; reason: $reason" else ""}"
    )
    notifyChanged()
    return Pair(true, "Cylinder $serial recorded as filled (${weightKg} kg).")
}

/**
 * Moves a plant cylinder to `SENT_TO_RETEST` so its safety test can be recorded.
 *
 * @param serial Unique cylinder serial number.
 * @param actor Accountable Plant Manager actor string.
 * @return Success flag and message.
 */
fun BlueGasStore.plantSendToRetest(
    serial: String,
    actor: String = AccountableRole.PLANT.displayName
): Pair<Boolean, String> {
    val cyl = cylinders.find { it.serialNumber == serial }
        ?: return Pair(false, "Cylinder $serial not found.")
    if (cyl.custody != CylinderCustody.PLANT) {
        return Pair(false, "Cylinder $serial is not at the plant.")
    }
    if (cyl.status == InventoryState.INV_WRITEOFF) {
        return Pair(false, "Cylinder $serial is already written off.")
    }

    val idx = cylinders.indexOf(cyl)
    cylinders[idx] = cyl.copy(
        status = InventoryState.INV_RELEASED,
        plantStage = "SENT_TO_RETEST"
    )
    cylinderCustodyEvents.add(
        CylinderCustodyEvent(
            id = "EVT-" + UUID.randomUUID().toString().take(6),
            serialNumber = serial,
            fromCustody = CylinderCustody.PLANT,
            toCustody = CylinderCustody.PLANT,
            orderId = null,
            actor = actor,
            timestamp = clock()
        )
    )
    logAudit(
        entityType = "Cylinder",
        entityId = serial,
        action = "PLANT_SEND_TO_RETEST",
        actor = actor,
        role = AccountableRole.PLANT,
        details = "Sent cylinder $serial for periodic safety re-test"
    )
    notifyChanged()
    return Pair(true, "Cylinder $serial marked as sent to re-testing.")
}

/**
 * Records the pass or fail outcome of a plant safety re-test.
 * On pass, requires a future ISO date (`yyyy-MM-dd`) and sets `plantStage = "EMPTY_AT_PLANT"`.
 * On fail, writes off the cylinder, closes its custody record, and decrements plant inventory.
 *
 * @param serial Unique cylinder serial number.
 * @param passed True if cylinder passed re-testing, false if failed/condemned.
 * @param nextTestDueDate Future ISO date (`yyyy-MM-dd`), required when [passed] is true.
 * @param actor Accountable Plant Manager actor string.
 * @return Success flag and outcome message.
 */
fun BlueGasStore.plantRecordRetest(
    serial: String,
    passed: Boolean,
    nextTestDueDate: String? = null,
    actor: String = AccountableRole.PLANT.displayName
): Pair<Boolean, String> {
    val cyl = cylinders.find { it.serialNumber == serial }
        ?: return Pair(false, "Cylinder $serial not found.")
    if (cyl.custody != CylinderCustody.PLANT) {
        return Pair(false, "Cylinder $serial is not at the plant.")
    }
    if (cyl.status == InventoryState.INV_WRITEOFF) {
        return Pair(false, "Cylinder $serial is already written off.")
    }

    val todayStr = todayKarachiDate()
    val todayDate = LocalDate.parse(todayStr, isoDateFmt)
    val idx = cylinders.indexOf(cyl)

    if (passed) {
        val rawDate = nextTestDueDate?.trim().orEmpty()
        val parsed = runCatching { LocalDate.parse(rawDate, isoDateFmt) }.getOrNull()
            ?: return Pair(false, "Enter a valid next test date (yyyy-MM-dd).")
        if (!parsed.isAfter(todayDate)) {
            return Pair(false, "Next test due date must be after today ($todayStr).")
        }
        cylinders[idx] = cyl.copy(
            status = InventoryState.INV_RELEASED,
            plantStage = "EMPTY_AT_PLANT",
            nextTestDueDate = rawDate,
            lastInspectionDate = todayStr
        )
        logAudit(
            entityType = "Cylinder",
            entityId = serial,
            action = "PLANT_RETEST_PASS",
            actor = actor,
            role = AccountableRole.PLANT,
            details = "Retest passed; next due $rawDate; ready for fill"
        )
        notifyChanged()
        return Pair(true, "Cylinder $serial passed re-testing (next due $rawDate).")
    } else {
        val plantStock = stockBalances.find { it.pointId == "PLANT-01" && it.skuId == cyl.sku }
        if (plantStock != null) {
            if (cyl.status == InventoryState.INV_AVAIL) {
                plantStock.filledQty = (plantStock.filledQty - 1).coerceAtLeast(0)
            } else {
                plantStock.emptyQty = (plantStock.emptyQty - 1).coerceAtLeast(0)
            }
        }
        cylinders[idx] = cyl.copy(
            status = InventoryState.INV_WRITEOFF,
            custodyRecord = CustodyRecordStatus.RECORD_CLOSED,
            plantStage = null,
            allocatedOrderId = null
        )
        logAudit(
            entityType = "Cylinder",
            entityId = serial,
            action = "PLANT_RETEST_FAIL",
            actor = actor,
            role = AccountableRole.PLANT,
            details = "Failed safety re-test; written off and custody record closed"
        )
        notifyChanged()
        return Pair(true, "Cylinder $serial failed re-testing and was written off.")
    }
}

/**
 * Records the plant disposition decision for a returned empty cylinder at the plant:
 * `"REFILL"`, `"RETEST"`, or `"SCRAP"` (requires non-blank reason).
 *
 * @param serial Unique cylinder serial number.
 * @param decision One of `"REFILL"`, `"RETEST"`, or `"SCRAP"`.
 * @param reason Mandatory note when [decision] is `"SCRAP"`.
 * @param actor Accountable Plant Manager actor string.
 * @return Success flag and message.
 */
fun BlueGasStore.plantDecideEmpty(
    serial: String,
    decision: String,
    reason: String = "",
    actor: String = AccountableRole.PLANT.displayName
): Pair<Boolean, String> {
    val cyl = cylinders.find { it.serialNumber == serial }
        ?: return Pair(false, "Cylinder $serial not found.")
    if (cyl.custody != CylinderCustody.PLANT) {
        return Pair(false, "Cylinder $serial is not at the plant.")
    }
    if (cyl.status == InventoryState.INV_WRITEOFF) {
        return Pair(false, "Cylinder $serial is already written off.")
    }

    val idx = cylinders.indexOf(cyl)
    return when (decision.uppercase()) {
        "REFILL" -> {
            cylinders[idx] = cyl.copy(
                status = InventoryState.INV_RELEASED,
                plantStage = "EMPTY_AT_PLANT"
            )
            logAudit("Cylinder", serial, "PLANT_DECIDE_EMPTY", actor, AccountableRole.PLANT, "Marked for refill at plant")
            notifyChanged()
            Pair(true, "Cylinder $serial queued for refill.")
        }
        "RETEST" -> {
            plantSendToRetest(serial, actor)
        }
        "SCRAP" -> {
            if (reason.isBlank()) {
                return Pair(false, "Reason is required to scrap a cylinder.")
            }
            val plantStock = stockBalances.find { it.pointId == "PLANT-01" && it.skuId == cyl.sku }
            if (plantStock != null) {
                if (cyl.status == InventoryState.INV_AVAIL) {
                    plantStock.filledQty = (plantStock.filledQty - 1).coerceAtLeast(0)
                } else {
                    plantStock.emptyQty = (plantStock.emptyQty - 1).coerceAtLeast(0)
                }
            }
            cylinders[idx] = cyl.copy(
                status = InventoryState.INV_WRITEOFF,
                custodyRecord = CustodyRecordStatus.RECORD_CLOSED,
                plantStage = null,
                allocatedOrderId = null
            )
            logAudit("Cylinder", serial, "PLANT_SCRAP_EMPTY", actor, AccountableRole.PLANT, "Scrapped: $reason")
            notifyChanged()
            Pair(true, "Cylinder $serial scrapped ($reason).")
        }
        else -> Pair(false, "Unknown decision: $decision.")
    }
}

/**
 * Generates the annual OGRA cylinder serial list CSV across all BLUE GAS cylinders
 * (never surrendered competitor cylinders), formatted per LPG Policy 2016, cl. 3.3.7.
 *
 * @return CSV string with header `"serial,size_kg,status,next_test_due"`.
 */
fun BlueGasStore.ograSerialListCsv(): String {
    val sb = StringBuilder()
    sb.append("serial,size_kg,status,next_test_due")
    cylinders
        .filter { it.brand.equals("BLUE GAS", ignoreCase = true) }
        .distinctBy { it.serialNumber }
        .forEach { cyl ->
            sb.append('\n')
            sb.append("${cyl.serialNumber},${cyl.weightKg},${cyl.status.label},${cyl.nextTestDueDate ?: ""}")
        }
    return sb.toString()
}
