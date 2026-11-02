# BLUE GAS — v0.5 Alignment, Stage A of C: Data Model and Store Foundation

You are continuing the existing BLUE GAS Kotlin / Jetpack Compose project. The governing BRD is now **v0.5 (28 Oct 2026)**. It replaces v0.3.2 and v0.4. This is **Stage A of three**:

- **Stage A (this file):** data model, store logic and seed data. Screens are touched **only to keep the project compiling**.
- Stage B: Staff Portal screens (Hub Staff, Operations, Plant, Commercial, Finance, Support, Safety, Admin).
- Stage C: Customer and Rider screens.

Do **not** change the L1 order state machine (19 statuses, 42 transitions) or any lifecycle names. Do not add features that are not listed here.

**Standing rules:** money is integer paisa (`Long`, shown with `formatRupees`); UI never mutates store fields; every store write ends in `notifyChanged()`; store functions return `Pair<Boolean, String>`; every new or changed function has KDoc; actor strings are role names (`AccountableRole.displayName`), never invented people; no `ORD_*` codes or invented text on screen. Values the client has not decided (deposit amounts, refund conditions, prices) stay **configurable settings** — the numbers in this file are demo seed values only.

**Compile rule for this stage.** When a screen uses an API this stage removes, delete that UI block (or the whole tab if it is only that) and leave a one-line `// Rebuilt in Stage B` comment. Do not redesign it. The Distributor screen is replaced by a minimal placeholder (A1).

---

## What changed in v0.5 (why this stage exists)

- **Phase 1 uses BLUE GAS's own riders** working from BLUE GAS-run fulfilment points (the plant counter or BLUE GAS hubs in Lahore and Islamabad). **Distributors are not in Phase 1** (they come later as "Option C"). A new role **Hub Staff (HUB)** replaces the Distributor.
- **First-time customers get the cylinder against a refundable security deposit.** The cylinder stays BLUE GAS property. **Deposit ≠ ownership.** Selling or lending new cylinders is not used.
- Households buy only **11.8 kg and 45.4 kg**.
- New: **deposit account**, **"Return my cylinder" → deposit refund** (L6, §8.16), **daily deposit check**, exceptions EXC-25 to EXC-28, notifications NTF-19/20/21.
- Riders hand cash (including deposits) to the **hub cashier or Finance** (BR-CSH-04).

---

## A1 — Replace "Distributor" with "Hub" everywhere

**Models (`model/Models.kt`, `model/OperationsModels.kt`)**

1. `FulfilmentPointType` becomes `{ PLANT, HUB }` (remove `DEPOT`, `DISTRIBUTOR`). Fix every `when`.
2. `AccountableRole.DISTRIBUTOR("Distributor Lead")` → `HUB("Hub Staff")`.
3. `StaffSubRole.DISTRIBUTOR("Distributor")` → `HUB("Hub")`.
4. `CylinderCustody.DISTRIBUTOR_NODE` → `HUB("At BLUE GAS hub")`.
5. Delete `data class Distributor`, `Node.distributorId`, `Order.assignedDistributorId`, `DistributorAccountEntry`, `TradePriceList`, and everything that only served them: `BlueGasStore.distributors`, `distributorAccountEntries`, `selectedDistributorId`, `setSelectedDistributorId`, `SharedAppState.distributors`, `distributorAccountEntries`, `distributorBalance()`, `recordDistributorPayment`, `tradePriceLists`, `currentTradePriceList`, `updateTradePrice`. These are "Later – Option C" (BR-CSH-03). Remove their `seedInitialData` lines too.
6. `Rider.distributorId` → `Rider.pointId: String` (the rider's home fulfilment point).
7. `model/I18n.kt`: `"role_distributor" to "Distributor"` → `"role_hub" to "Hub Staff"`.
8. `ExceptionReason.NO_ACCEPTANCE` description: `"Not accepted by the hub or rider in time"`.
9. `OrderStatus.customerLabel`: compare every label with the v0.5 table below and change **only mismatches** (today `ORD_ASGN` says "Distributor assigned"):

| Status | Customer label |
|---|---|
| Validation Pending | Checking your order |
| Payment Pending | Awaiting payment |
| Confirmed, Reserved | Order confirmed |
| Assigned | Rider assigned |
| Accepted, Preparing | Being prepared |
| Dispatched | On the way |
| Ready for Pickup | Ready to collect |
| At Handover | Rider has arrived (pickup: At the counter) |
| Inspection Pending | Inspection in progress |
| Adjustment Pending | Please confirm the updated amount |
| Handover Complete | Delivered |
| Fulfilment Exception | We're sorting out a problem with your order |
| Completed / Cancelled | Completed / Cancelled |
| Superseded | Replaced by order no. … |
| Failed Closed | Could not be completed |

**Seed (`data/SeedData.kt`, `BlueGasStore.seedInitialData`)**

- Nodes: keep ids `NODE-LHR-1` and `NODE-ISB-1`, rename to **"Lahore Hub"** and **"Islamabad Hub"** (type `HUB`; add a KDoc note "demo names; real fulfilment points are open decision Q29"). `PLANT-01` stays, named "BLUE GAS Plant" (type `PLANT`).
- Remove `DIST-001`, `DIST-002` and any `DISTRIBUTOR` partner application. Riders: `RIDER-001`, `RIDER-002` → `pointId = "NODE-LHR-1"`, `RIDER-003` → `"NODE-ISB-1"`.
- Cylinders with `DISTRIBUTOR_NODE` custody → `HUB`.

**Store renames (`data/StoreFulfilment.kt`, `StoreOrders.kt`, `StoreSupplyChain.kt`, `StorePartners.kt`, `StoreAdmin.kt`, `StoreCommercial.kt`, `viewmodel/BlueGasViewModel.kt`, `ai/GeminiAdvisorService.kt`)**

| Old | New |
|---|---|
| `distributorAccept` / `distributorAcceptOrder` / `distributorDecline` | `hubAccept` / `hubAcceptOrder` / `hubDecline` |
| `BlueGasViewModel.distributorAcceptOrder` | `hubAcceptOrder` |
| `PlatformSettings.distributorAcceptanceWindowMins` | `hubAcceptanceWindowMins` (default stays 20; update validation and the change-log text in `StoreAdmin.updateSettings`) |
| `depotSubmitCashHandover(distributorId, …)` | `hubSubmitCashHandover(pointId: String, declaredPaisa: Long)` — filters pickup COD orders by `(pickupPointId ?: fulfilmentPointId) == pointId`; `collectorType = "HUB"`, `collectorId = pointId` |

- Every actor string `"Distributor Lead"` / `"Distributor"` → parameter `actor: String = AccountableRole.HUB.displayName`; every `role = AccountableRole.DISTRIBUTOR` → `AccountableRole.HUB`. Exception: `allocateCylinder` audit keeps `AccountableRole.INVENTORY` (RACI-05: Inventory Controller is Accountable, Platform and Hub Staff are Responsible).
- `onConfirmed` (StoreOrders): the order is assigned to the serving **fulfilment point** (`order.fulfilmentPointId`), not to a distributor. If the point is missing or inactive, raise `NO_ACCEPTANCE` with details `"No active BLUE GAS hub serves this address"`. Customer notification NTF-03: title `"Order assigned"`, message `"Your order is with the ${hub.name} team and will be prepared shortly."` Staff notification goes to `StaffSubRole.HUB`.
- `processTimers`: hub window uses `hubAcceptanceWindowMins`; unchanged otherwise.
- `assignRider` (RACI-06: **Operations** is Accountable for assignment): check `rider.pointId == order.fulfilmentPointId` (message `"Rider belongs to another fulfilment point."`), add parameter `actor: String = AccountableRole.OPERATIONS.displayName`, audit role `OPERATIONS`.
- Messages: "Contact your distributor" and "Distributor notified to reassign" → "Contact Operations" and "Operations notified to reassign".
- `StorePartners.approvePartner`: replace parameter `linkedDistributorId` with `homePointId: String?`. `RIDER`: required, must be a `HUB` node. `DISTRIBUTOR` applications are no longer approvable: return `Pair(false, "Distributor onboarding is planned for a later phase.")`. Delete the distributor-creation branch.
- `StoreCommercial` line ~80: staff notification role `DISTRIBUTOR` → `OPERATIONS`.

**Placeholder screen.** Delete `ui/staff/StaffDistributorScreen.kt`. Create `ui/staff/StaffHubScreen.kt` with `@Composable fun StaffHubScreen(...)` using the **same parameters** the old screen had (so `StaffPortalScreen` still compiles) that shows one centred card: `"Hub Staff workspace"` / `"Built in Stage B."` Route `StaffSubRole.HUB` to it. Chip order: Operations, Hub, Plant & Stock, Finance, Commercial, Support, Safety & HSE, Admin, Phase 2.

## A2 — Catalogue: 11.8 kg and 45.4 kg only (BR-STK-06)

- Remove variants `PV-2` (6.0 kg) and `PV-5` (11.0 kg), their cylinders, stock balances, price-list map entries and SKU cases in `PricingEngine.unitPriceExact` / `buildQuote`. After this, `SKU-LPG-6-0` and `SKU-LPG-11-0` appear nowhere.
- `PV-3` becomes `"45.4 kg Cylinder"`, `isHouseholdApproved = true`, product name `"45.4 kg Domestic LPG Cylinder Refill"`. Keep the regulator-and-hose accessory.
- Keep `isHouseholdApproved` on the model but it is `true` for both sizes. Remove `setHouseholdApproved` and any UI that toggles it (Commercial tab; the sizes are fixed by BR-STK-06).

## A3 — Deposit-only cylinder model (D8)

- Delete `enum NewCylinderModel` and `PlatformSettings.newCylinderModel`. In `PricingEngine.buildQuote`, `OrderType.NEW_CYLINDER` always adds the `DEPOSIT` line (product name `"Refundable cylinder security deposit"`). Remove the `SOLD` and `LOAN` branches and `PriceList.cylinderSalePaisa`. Remove `OrderLine.lineType` values `"CYLINDER"` and `"LOAN"`.
- `CylinderOwnership`: remove `OWNED_BY_CUSTOMER`. Keep `DEPOSIT_HELD`, `LOANED` (means "no deposit on record", Q30), `RETURNED` ("Returned/Released"), `SURRENDERED_ASSET` (Phase 2). Fix every `when`.
- Deposit amounts per size stay in `PriceList.depositPaisa`. Demo seed values: 11.8 kg = 750,000 paisa, 45.4 kg = 1,800,000 paisa. KDoc: "demo values; amounts and refund conditions are open decision Q03".
- Line order and footing are unchanged: the deposit is a separate line, outside the taxable base, added to the total (BR-PRC-05).
- `StoreAdmin`, `HomeScreen` and `StaffAdminScreen` reference `newCylinderModel`: remove those lines/blocks (compile rule). `HomeScreen` line ~389: replace the `when` with the fixed text `"Held against a refundable security deposit — you don't own it."`

## A4 — Deposit account (new)

Add to `model/OperationsModels.kt`:

```kotlin
/** Status of one customer's deposit (v0.5 §22.4). */
enum class DepositStatus(val label: String) {
    HELD("Held"),
    REFUND_IN_PROGRESS("Refund in progress"),
    REFUNDED("Refunded"),
    REDUCED_OR_FORFEITED("Reduced or forfeited")
}

/** One refundable security deposit, per customer and per cylinder (BR-DEP-02). A liability, never revenue. */
data class DepositAccount(
    val id: String,                       // "DEP-" + 4 digits
    val customerId: String,
    val sku: String,
    var cylinderSerial: String?,          // moves to the new cylinder on empty exchange (BR-DEP-05)
    val amountPaisa: Long,
    val paidAt: Long,
    val orderId: String,
    val paymentId: String?,
    var status: DepositStatus = DepositStatus.HELD,
    var returnCaseId: String? = null,
    var refundId: String? = null,
    var deductionPaisa: Long = 0L,
    var deductionReason: String? = null,
    val noDepositOnRecord: Boolean = false // dealer-sourced cylinder (Q30); amountPaisa = 0
)
```

- Add `val depositAccounts = mutableListOf<DepositAccount>()` to `BlueGasStore`, `depositAccounts: List<DepositAccount>` to `SharedAppState` (and its `toList()` in the snapshot builder), and `depositAccounts.clear()` in `seedInitialData`. Also add `fun SharedAppState.depositFor(customerId: String, serial: String): DepositAccount?`.
- **Seed:** inspect the seeded `customerHoldings`. For every seeded `DEPOSIT_HELD` holding create a matching `DepositAccount` (same customer, serial, amount; `orderId` = that customer's seeded completed order, or a synthetic seed id if none). For one seeded customer, add a `LOANED` holding with a zero-amount account (`noDepositOnRecord = true`) so the "no deposit" case can be shown. The seeded data must pass the deposit check in A7 with **zero** difference.

New file `data/StoreDeposits.kt`:

```kotlin
/** Called when a filled cylinder is handed to the customer (doorstep or counter). Creates or moves the deposit (BR-DEP-05) and the holding. */
internal fun BlueGasStore.onCylinderHandedToCustomer(order: Order, newSerial: String, emptySerial: String?)
```

Behaviour:

1. **First cylinder** (`order.orderType == NEW_CYLINDER`, `order.depositTotalPaisa > 0`): add `CustomerCylinderHolding(ownership = DEPOSIT_HELD, depositPaisa = order.depositTotalPaisa)` and a `DepositAccount(status = HELD, orderId, paymentId = activePaymentFor(order.id)?.id)`. Customer notification **NTF-19**: title `"Deposit recorded"`, message `"Rs X deposit recorded for cylinder <serial>. It is refundable when you return the cylinder."`
2. **Refill by empty exchange** (`REFILL_EXCHANGE`, no new deposit line, BR-DEP-05):
   - Find the customer's `DepositAccount` with `cylinderSerial == emptySerial` and `status == HELD`. If found: set `cylinderSerial = newSerial`; mark the old holding `RETURNED`; add a new `DEPOSIT_HELD` holding for `newSerial` with the **same** `depositPaisa`; audit `DEPOSIT_MOVED`.
   - If `emptySerial` is blank and the customer has exactly one `HELD` account for that `sku`, use that account.
   - If an empty was collected but **no deposit account/holding exists** for it (dealer cylinder): register the new holding as `LOANED` with `depositPaisa = 0`, add a zero-amount `DepositAccount(noDepositOnRecord = true)`, and add `ReconciliationException(code = "EXC-26", ownerRole = INVENTORY, description = "Cylinder <id> offered for exchange has no deposit on record. Registered as Loaned (open decision Q30).")`.
   - Only brands in `settings.approvedEmptyBrands` are accepted (BR-STK-07); that check already exists, keep it.
3. Replace the inline `customerHoldings.add(...)` in `completeHandover` (line ~1134) with a call to this function, and **add the call** to `completeCounterHandover` (it currently creates no holding at all). Keep the existing `DEPOSIT_LIABILITY` ledger credit exactly once per deposit.

## A5 — Cylinder return and deposit refund (L6, §8.16)

**Model.** In `ReturnRefundCase` update the `type` comment to `"RETURN" | "REFUND_ONLY" | "CYLINDER_RETURN"` and add:

```kotlin
val cylinderSerial: String? = null,
val depositId: String? = null,
var conditionResult: ReturnCondition? = null,
var conditionNote: String? = null,
var hazardSuspected: Boolean = false,
var deductionPaisa: Long = 0L,
var deductionReason: String? = null,
var rejectionReason: String? = null,
var collectionRiderId: String? = null,
var collectionTask: String? = null   // null | "OFFERED" | "ACCEPTED" | "COLLECTED" | "AT_HUB"
```

```kotlin
enum class ReturnCondition(val label: String) { GOOD("Good condition"), DAMAGED("Damaged"), NOT_HANDED_OVER("Not handed over") }
```

**Store (new file `data/StoreReturns.kt`).** The L6 machine in `CaseLifecycles.kt` already has all statuses including `RETURN_REJECTED`; do not change it. Only the `v0.4` comments should say `v0.5`.

| Function | Rules |
|---|---|
| `requestCylinderReturn(customerId, cylinderSerial, reason, actor = "Customer")` | Customer must hold that serial as `DEPOSIT_HELD` or `LOANED`; reason required; no other open case for that serial. `orderId` = the deposit account's `orderId`, or for a loaned cylinder the customer's most recent completed order. Opens `CYLINDER_RETURN` at `CASE_OPENED`; `requestedAmountPaisa` = deposit amount. Notify Support (NTF-10). |
| `moveCase` (existing) | For `CYLINDER_RETURN`: `RETURN_REJECTED` stores `rejectionReason` and tells the customer; `RETURN_AUTHORIZED` on a `noDepositOnRecord` cylinder needs a non-blank reason (the reviewer records the proof seen, Q30); `REFUND_REQUESTED` is **not** allowed through `moveCase` for this type (return `"Finance requests the deposit refund."`). Everything else unchanged. |
| `offerReturnCollection(caseId, riderId, actor = OPERATIONS)` | Case must be `RETURN_AUTHORIZED`; rider approved and active. Sets `collectionRiderId`, `collectionTask = "OFFERED"`, moves case to `AWAITING_ITEM_RETURN`. Notify rider and customer (**NTF-20**: `"Cylinder collection booked"`). |
| `riderAcceptReturn(caseId, riderId)` | `OFFERED` → `ACCEPTED`. |
| `riderDeclineReturn(caseId, riderId, reason)` | Reason required. Clears the rider, `collectionTask = null`, and notifies Operations. Case stays `AWAITING_ITEM_RETURN`. |
| `riderCollectCylinder(caseId, riderId, scannedSerial, photoCaptured)` | `ACCEPTED` only. `scannedSerial` must equal `cylinderSerial` (else `"This is not the cylinder on the return request."`). `photoCaptured` must be true. Cylinder custody → `RIDER_VAN`/`INV_TRANSIT` with a custody event. `collectionTask = "COLLECTED"`. |
| `riderHandInReturn(caseId, riderId)` | `COLLECTED` → `AT_HUB`; custody → `HUB` at `rider.pointId`; notify Hub staff. |
| `hubRecordReturnCondition(caseId, condition, note, hazardSuspected, actor = HUB)` | `AT_HUB` only. Note required unless `GOOD`. Moves the case `AWAITING_ITEM_RETURN` → `ITEM_RETURNED`. Not `GOOD` → add `ReconciliationException("EXC-25", FINANCE, "Returned cylinder <id>: <condition label>. <note>")`. If `hazardSuspected`, call `raiseIncident(source = "DEPOT", description = note, cylinderId = serial, actor = "Hub Staff")` (L7; the cylinder goes on HSE hold). Record the condition on the case. |
| `requestDepositRefund(caseId, deductionPaisa, deductionReason, actor = FINANCE)` | Case `ITEM_RETURNED`, type `CYLINDER_RETURN`. Deposit account must be `HELD` with amount > 0, else `"No deposit on record for this cylinder (EXC-26). No refund without proof."` Original payment must be `SETTLED`, else `"The original payment for this deposit is not settled yet. The refund waits until Finance reconciles the cash."` (BR-DEP-06, UAT-37). `0 <= deduction < deposit`. Deduction > 0, or a condition other than `GOOD`, needs a reason. Creates `Refund(kind = "DEPOSIT", state = PARTIAL_REFUND_PENDING, linkedCaseId, amount = deposit − deduction)`, moves the case to `REFUND_REQUESTED` (role FINANCE), sets deposit `REFUND_IN_PROGRESS`, `refundId`, `deductionPaisa`, `deductionReason`. Notify Finance (NTF-11). |

**`processRefund` (StoreFinance).** `kind == "DEPOSIT"` is never "full" (treat as partial). On **success** for a refund linked to a `CYLINDER_RETURN` case, in addition to the existing transitions:
- deposit status → `REFUNDED` (or `REDUCED_OR_FORFEITED` if `deductionPaisa > 0`);
- the customer's holding for that serial → `ownership = RETURNED`;
- the cylinder: `status = INV_RELEASED`, custody `HUB`, label `"Returned — empty at hub"`, and `emptyQty++` on the hub's stock balance for its sku;
- ledger: `DEPOSIT_REFUND` debit = refund amount; if deduction > 0, also `DEPOSIT_DEDUCTION` debit = deduction (description "Deposit liability released under the agreed deduction");
- customer notification **NTF-21**: title `"Deposit refund paid"`, message `"Rs X has been refunded to you."`
On **failure**: deposit stays `REFUND_IN_PROGRESS`, the case stays `REFUND_REQUESTED`, and add a Finance notification `"Deposit refund payment failed (EXC-27)"`. `retryRefund` must keep working for these refunds (it does not touch the payment when the payment is not in a refund state).

Limit to document in KDoc: a deduction equal to the full deposit is rejected with `"Enter a deduction below the deposit amount."` (full forfeit handling is a Q03 decision).

## A6 — Cash goes to the hub cashier or Finance (BR-CSH-04)

- `CashHandover`: `recipient` is `"HUB"` or `"FINANCE"`; `collectorType` is `"RIDER"` or `"HUB"`. Add `var hubReceivedPaisa: Long? = null`, `var hubReceivedBy: String? = null`, `var hubReceivedAt: Long? = null`. Add status `"RECEIVED_BY_HUB"`.
- `riderSubmitCashHandover`: default recipient `"HUB"`; map the input (`contains("FINANCE")` → `FINANCE`, else `HUB`); audit role `HUB`/`FINANCE` accordingly (never `DISTRIBUTOR`). Notification goes to Finance only when recipient is `FINANCE`; for `HUB` notify `StaffSubRole.HUB`.
- New `hubReceiveRiderCash(handoverId, countedPaisa, reason, actor = HUB)`: only `recipient == "HUB"` and `status == "SUBMITTED"`; reason required when counted differs from declared; sets the three hub fields and status `RECEIVED_BY_HUB`; notifies Finance (NTF-15).
- `reconcileCashHandover` (Finance): accept `SUBMITTED` (recipient `FINANCE`) or `RECEIVED_BY_HUB`; a `HUB` hand-over still `SUBMITTED` returns `"Waiting for the hub cashier to confirm receipt."` Settlement of COD payments still happens **only** here (L3).
- Deposits are inside the order total, so they are inside the cash; no extra code, but the hand-over text must say "including deposits" wherever amounts are explained (Stage B/C).

## A7 — Daily deposit check (BR-DEP-07, EXC-28)

- `fun BlueGasStore.runDepositCheck(actor: String = FINANCE): Pair<Boolean, String>`: `held = depositAccounts` with status `HELD` or `REFUND_IN_PROGRESS` summed; `onCylinders = customerHoldings` with ownership `DEPOSIT_HELD` summed `depositPaisa`. Equal → `Pair(true, "Deposit ledger matches cylinders held on deposit: Rs X across N cylinders.")`. Different → one `ReconciliationException("EXC-28", FINANCE, "Deposit ledger Rs A does not match Rs B held on cylinders.")` (do not add a second one while an EXC-28 is OPEN) and `Pair(false, <same text>)`.
- Read `runDailyReconciliation` and add the same comparison as one more line of its result. Do not change its other lines.
- Demo helper for Admin (Stage B button): `simulateDepositMismatch()` reduces one `DEPOSIT_HELD` holding's `depositPaisa` by 50,000 paisa and returns a clear message. If there is none, return `Pair(false, "No deposit-held cylinder to change.")`.

## A8 — Supply chain and plant (store only)

- `requestStock`, `confirmTransferReceipt`, `requestEmptiesReturn`: default actor `"Hub Staff"`, role `HUB`; notifications to `StaffSubRole.HUB`; cylinders received go to `CylinderCustody.HUB`. In `confirmTransferReceipt` delete the trade-price and `DistributorAccountEntry` block. The partial-receipt logic and EXC-18 stay as they are.
- `StockTransfer.requestedBy` default `"Hub Staff"`.
- `recordPlantDispatch`: remove the hard-coded `cmrNo = "CMR-26-001"` and `filledBy = "Plant Operator Tariq"`. `cmrNo` and `filledBy` become optional parameters; blank → `null`.
- Delete `logBulkLpgReceipt`, `recordDailyPlantDip`, `recordCylinderTesting`, `recordCylinderScrap` and the model classes only they use. The plant system is the record for bulk stock and tank balance (D5, Q21): BLUE GAS only imports the **daily plant report** (`savePlantDailyReport`, `importPlantFileDemo` stay). If `runDailyReconciliation` reads a removed list, read the same figure from `PlantDailyReport` instead. Written-off cylinders (`INV_WRITEOFF`) stay a valid L4 outcome via the safety flow.
- `submitPhysicalStockAudit` / `reconcileStockAudit`: keep, but only SKUs 11.8 kg and 45.4 kg.

## A9 — Safety close bug (L7) — store half

**Defect.** `moveIncident` applies `cylinderOutcome` only when the target is `INCIDENT_CLOSED`, yet the UI collects the outcome at the `AUTHORIZED_ACTION_TAKEN` step. Closing a cylinder incident therefore always fails with "Cylinder outcome … is required".

**Fix.**
- Add `var cylinderOutcome: String? = null` to `SafetyIncident` (`"RELEASE"` or `"WRITE_OFF"`).
- In `moveIncident`: when the target is `AUTHORIZED_ACTION_TAKEN` and the incident has a `cylinderId`, require `cylinderOutcome` (`RELEASE` or `WRITE_OFF`) and **store it on the incident** (do not apply it yet). When the target is `INCIDENT_CLOSED`, apply the **stored** outcome (the existing release / write-off code) and ignore the parameter. Incidents without a cylinder need no outcome.
- Do not add any default decision text or authorizer name anywhere. The platform records that an authorized decision was carried out per the approved BLUE GAS HSE SOP; it never invents a procedure.

## A10 — Settings and the open-decisions register

- `PlatformSettings`: remove `newCylinderModel`; rename the acceptance window (A1); keep `riderAcceptanceWindowMins`, `autoRaiseNoAcceptance`, `approvedEmptyBrands = ["BLUE GAS"]`.
- `OpenDecision` seed: extend from Q01–Q28 to **Q01–Q33**. Add Q29 "Fulfilment points and pickup", Q30 "BLUE GAS cylinders already in homes (dealers)", Q31 "Rider employment model", Q32 "When distributors join (Option C)", Q33 "Branding". Mark Q03, Q10, Q20, Q24, Q28, Q29, Q30, Q31, Q33 "needed for the build". Update the `"Q01".."Q28"` comments.
- Add a read-only list of confirmed decisions D3–D9 as constants (`ConfirmedDecision(id, title)`) for Stage B: D3 all of Lahore and Islamabad served; D4 households buy only 11.8 kg and 45.4 kg; D5 plant system connects to the platform; D6 BLUE GAS sets its own household price; D7 Phase 1 uses BLUE GAS riders from BLUE GAS-run points; D8 refundable security deposit, cylinder stays BLUE GAS property; D9 all BLUE GAS cylinders in the market belong to BLUE GAS.

## A11 — KDoc and comment pass

Every file you touched: replace "v0.3.2" / "v0.4" in KDoc with "v0.5" **only where the statement is still true**, delete KDoc that describes removed behaviour, and add KDoc to all new functions (inputs, rule references such as `BR-DEP-06`, return meaning).

---

## Self-check (run each and report the result in one line)

1. `grep -rni "distributor" app/src` → zero hits, except comments containing "Later – Option C" and the one decline message in `approvePartner`.
2. `grep -rn "SKU-LPG-6-0\|SKU-LPG-11-0\|NewCylinderModel\|OWNED_BY_CUSTOMER\|cylinderSalePaisa\|DISTRIBUTOR_NODE\|TradePriceList\|logBulkLpgReceipt\|recordDailyPlantDip\|recordCylinderTesting\|recordCylinderScrap" app/src` → zero hits.
3. `grep -rn "Plant Operator Tariq\|CMR-26-001\|Head of HSE" app/src` → zero hits.
4. Every `when` over `FulfilmentPointType`, `CylinderOwnership`, `CylinderCustody`, `StaffSubRole`, `AccountableRole` is exhaustive (no stray `else` hiding a missing case).
5. Every function in A4–A7 is declared as an extension of `BlueGasStore`, ends in `notifyChanged()` on success and returns `Pair<Boolean, String>`.
6. Brace balance in each edited file. List any file that now fails to compile, if you can tell.
7. Walkthrough on the running app (state the expected result of each step; Staff screens are placeholders or trimmed, so use the Customer and Rider apps plus the Staff Operations, Finance and Support screens that still compile):
   a. App starts with no crash; the Staff chip bar shows "Hub" instead of "Distributor"; the Hub tab shows the placeholder card.
   b. Customer: Home shows only 11.8 kg and 45.4 kg. A new-cylinder quote lists gas, **Refundable cylinder security deposit**, delivery fee, platform fee, tax and total; the deposit is outside the taxable base.
   c. Place and confirm an order. The customer notification reads "Order assigned … the Lahore Hub team"; the tracking label for Assigned reads "Rider assigned".
   d. Complete a first-cylinder delivery (any method). The customer gets "Deposit recorded"; Profile shows one new deposit-held holding; no "Owned" label anywhere.
   e. Complete a refill exchange for a customer with a seeded deposit cylinder: the deposit amount on the new cylinder equals the old one, the old holding is Returned/Released, and **no** new deposit line was charged.
   f. Rider app: cash hand-over still works and records recipient HUB (shown as "HUB" until Stage C relabels it).
   g. Admin: settings save without the cylinder-model chips; the acceptance window field is labelled for the hub.

> **If you run out of output before finishing**, stop at a clean boundary (finish the current A-section and make sure the project compiles). I will reply "Continue from A<n>".
