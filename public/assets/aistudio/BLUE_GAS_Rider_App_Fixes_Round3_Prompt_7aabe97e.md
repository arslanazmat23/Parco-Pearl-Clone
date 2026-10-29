# BLUE GAS — Rider App Fixes, Round 3 (final rider polish)

You are continuing the existing BLUE GAS Kotlin / Jetpack Compose project. Round 2 is implemented correctly. This round fixes **eight small defects** found in review. Do **not** restructure anything, do **not** touch the Customer app, and do **not** change the L1 state machine (19 statuses, 42 transitions) or any lifecycle names. Make only the changes below. Keep every existing KDoc/comment; add KDoc to anything new.

Rules that still apply: money is integer paisa (`Long`, shown with `formatRupees`); the UI never mutates store fields; every store write ends in `notifyChanged()`; callbacks are `(message, isError)`; no status codes (`ORD_*`) or invented text on screen; open decisions stay configurable.

---

## T1 — Restore "Demo Conflict" (regression from Round 2)

**Problem.** `simulateSerialConflict()` in `data/StoreFulfilment.kt` targets `ORD-SEED-105` and uses that order's own allocated serial (`BG-LHR-002`). Replay only reports a collision when **another order in `ORD_COMP`** holds the same serial, and none does now, so the button queues an event that never conflicts. Nothing visible happens on the demo.

**Fix.** Rewrite it so it always produces a real collision:
1. Pick the *colliding serial* from a completed order that has an `allocatedSerial` (seed `ORD-SEED-108`, serial `BG-LHR-118-002`, is the intended one). Find it dynamically: `orders.firstOrNull { it.status == OrderStatus.ORD_COMP && it.allocatedSerial != null }`.
2. Pick the *target order* as an in-flight order assigned to the active rider (status `ORD_DISP` or `ORD_ATHO`, else `ORD_PREP`) that is **not** the completed order. If none exists, return `"No active delivery to demonstrate with."`.
3. Queue a `COMPLETE_HANDOVER` outbox event for the target order with `scannedSerial = <completed order's serial>`, a fresh idempotency key and sequence number, then `notifyChanged()`.
4. Return `"Duplicate offline completion queued for serial <serial>."` (or the not-found messages above).

No seed changes. After tapping Demo Conflict and then Sync, the outbox shows a conflict and the Staff Operations exception queue gets a `DUPLICATE_OFFLINE_SCAN_CONFLICT` item, as in Round 1.

## T2 — Offer window: remove the "stuck forever" state

**Problem.** `riderAcceptTask` always enforces `riderAcceptanceWindowMins`, but the timer that resets expired offers only runs when `autoRaiseNoAcceptance` is true (default false). With the default, an offer older than the window can never be accepted, yet it also never expires. The rider sees "Acceptance time is over." forever and Staff still sees "Offered to X — waiting". In a live demo this strands the seeded offer ten minutes after launch.

**Fix (one rule, applied everywhere).** The acceptance window is only an SLA when `settings.autoRaiseNoAcceptance` is true.
1. Extract the body of the section 2b expiry loop in `processTimers` (`data/StoreOrders.kt`) into a private helper `BlueGasStore.expireRiderOffer(order: Order)` (reset `riderTaskStatus = DECLINED`, clear `assignedRiderId`, `riderOfferedAt`, `riderCylinderVerified`, audit `RIDER_OFFER_EXPIRED`, NTF-03 to Staff Distributor). `processTimers` calls it; behaviour is unchanged.
2. In `riderAcceptTask`: apply the window check **only if** `settings.autoRaiseNoAcceptance`. If it is on and the window has passed, call `expireRiderOffer(order)`, `notifyChanged()` and return `Pair(false, "This offer has expired and was returned to the distributor.")`.
3. In the rider Pending card (`RiderAppScreen.kt`, the "Acceptance Countdown Chip"): when `autoRaiseNoAcceptance` is **false**, do not show a countdown; show a calm neutral chip "Please accept or decline this delivery." and never show an "expired" state. When it is **true**, keep the existing countdown.
4. Staff Distributor card already shows "Reassign & Offer to Rider" for `DECLINED`. No change needed there, but confirm that an expired offer shows the red "Task declined by rider — Reassign rider:" line (the label is fine for expiry too).
5. Seed `ORD-SEED-105`: set `riderOfferedAt = now` (not `now - 120000L`).

## T3 — Verify dialog must not reveal the answer

In the "Verify cylinder" dialog (`RiderAppScreen.kt`, around line 820) the text-field placeholder is `e.g. ${ord?.allocatedSerial ?: "BG-LHR-001"}`, which shows the correct serial to anyone looking at the screen and defeats the check. Replace the placeholder with the fixed neutral text `"e.g. BG-LHR-0000"` (never derived from the order). Keep the existing "Demo: scan" `DemoValuePill`; that pill is the only place the allocated serial may be offered, and it must stay visibly labelled as a demo helper.

## T4 — Wrong terminal names and missing KDoc (rider scope only)

1. `RiderAppScreen.kt` lines ~74–75: the KDoc names `ORD_FAIL` and `ORD_REJ`, which do not exist. The terminals are `ORD_COMP`, `ORD_CANC`, `ORD_SUPS` and `ORD_FCLO`. Correct the text to say cancelled, superseded and failed-closed orders (`ORD_CANC`, `ORD_SUPS`, `ORD_FCLO`) are not shown in any tab.
2. Add KDoc (what, params, return, who calls it) to: `isActiveForRider`, `isCompletedForRider` in `RiderAppScreen.kt`; and in `StoreFulfilment.kt` the overloads `riderDeclineTask(orderId, riderId, reason)`, `riderArrive(orderId, isReplay)` and the **second** `riderReportIssue(...)` overload. For each overload say it delegates to the primary function and why it exists.

## T5 — Safety copy must not invent a procedure (L7)

The safety option's helper text in the Report-a-problem dialog reads "HSE will be alerted immediately. Do not transport or connect an unsafe cylinder." The second sentence is an invented containment instruction. Replace the whole text with:

> "HSE and Operations will be alerted. Follow the approved BLUE GAS safety procedure."

Do not add any other safety instruction anywhere in the rider app.

## T6 — Ownership guard on every rider-side action

Only `riderAcceptTask` checks that the order belongs to the acting rider. Add the same guard, with the same message `"Order is not assigned to this rider."`, to the **start of** these functions in `data/StoreFulfilment.kt`, using the same `effectiveRiderId = riderId ?: getActiveRider()?.id` pattern:
- `riderDeclineTask(orderId, reason)` (use the active rider)
- `riderVerifyCylinder`
- `riderStartTrip`
- `riderArrive` (primary)
- `riderReportIssue` (primary)
- `completeHandover` (use the active rider)
- `verifyHandoverPin`

The guard goes **before** any offline-queueing logic, and **skipped when `isReplay` is true** (replayed events were already validated when queued). Also replace the two fallbacks `order.assignedRiderId ?: "RIDER-001"` in `completeHandover` with `order.assignedRiderId ?: return Pair(false, "Order is not assigned to this rider.")` placed at the top. No hard-coded rider id may remain in the store.

## T7 — Cash hand-over: enforce the reason when the amount differs

`riderSubmitCashHandover` accepts any declared amount and never requires a reason when `declaredPaisa != expected`. After computing `expected`, add:

```kotlin
if (declaredPaisa != expected && effectiveReason.isNullOrBlank()) {
    return Pair(false, "The amount differs from what was collected. Please enter a reason.")
}
```

In the rider cash dialog, show the same hint under the amount field when the entered amount differs from the expected amount, and disable "Submit" until a reason is typed. The reason field is otherwise optional.

## T8 — Small text clean-ups in the Pending card

- `"Depot: ${depotNode?.name ?: "Main Depot"}"` → when the depot node cannot be resolved, show `"Depot: Not available"`. Never invent a depot name.
- `"Cylinder ${order.allocatedSerial ?: "allocated"} ready at the depot"` → `if (order.allocatedSerial != null) "Cylinder ${order.allocatedSerial} ready at the depot" else "Cylinder will be confirmed at the depot"`.

---

## Self-check (do all of these before finishing, and report each result in one line)

1. Grep `ui/rider` and `ui/components` for: `OGRA`, `legally`, `soapy`, `bubble`, `tamper`, `jubilee`, `geotag`, `watermark`, `hydro`, `Total Settled`, `ORD_` inside string literals. Expect zero hits.
2. Grep `data/` for `"RIDER-001"` outside `SeedData.kt`. Expect zero hits.
3. Grep `RiderAppScreen.kt` for `Main Depot` and `ORD_FAIL` and `ORD_REJ`. Expect zero hits.
4. `riderAcceptTask`, `riderStartTrip`, `riderArrive`, `riderReportIssue`, `completeHandover` each contain the ownership guard.
5. Every new function or changed overload has KDoc.
6. Walkthrough (state the expected result for each):
   a. Launch → Rider → Pending shows ORD-SEED-105 with "Please accept or decline this delivery." and no countdown. Wait past the window: Accept still works.
   b. Verify dialog placeholder shows `e.g. BG-LHR-0000`; typing the wrong serial gives an inline error; "Demo: scan" fills the right one; verification succeeds.
   c. Turn `autoRaiseNoAcceptance` on in Staff Admin and set the rider window to 1 minute. A fresh offer shows a countdown; after it expires the card disappears from the rider app and Staff shows "Task declined by rider — Reassign rider:".
   d. Active delivery in transit → Offline on → Demo Conflict → Offline off → Sync: outbox shows a conflict and Staff Operations shows a `DUPLICATE_OFFLINE_SCAN_CONFLICT` item.
   e. Report a problem → safety option: shows the new text, raises one L7 incident and one exception.
   f. Cash tab: declared ≠ expected disables Submit until a reason is entered.

Do not refactor, rename or "improve" anything outside this list.

> **If you run out of output before finishing**, stop at a clean boundary. I will reply "Continue from T<n>".
