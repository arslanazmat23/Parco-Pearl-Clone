# BLUE GAS — Customer App Fix Pass (after the Oct-21 revamp)

**Surface:** Customer App only (plus the small store / ViewModel additions listed in F0).
**Spec baseline:** BLUE GAS To-Be Concept v0.4 and the v0.4 Revamp Prompt (§5.3 Receipt, §5.4 Track).
**Why this pass exists:** a line-by-line review of the Oct-21 build found the defects below. Work through the sections in order. F0 and F1 create things that F2–F5 use.

---

## 0. Rules for this pass (read first, apply everywhere)

1. **Scope.** Change only the customer surface and what F0 lists. Do not edit Rider or Staff screens except to fix a compile error caused by an F0 change (for example `SupportCase.orderId` becoming nullable).
2. **Frozen — do not change:** `OrderStateMachine` (19 statuses, 42 transitions), `PaymentStateMachine`, status and state names, money as integer paisa `Long`, `formatRupees` for customer-facing money (whole rupees, "Rs 4,148"), and the signature of `PricingEngine.buildQuote`. You may *add* helper functions.
3. **UI never writes to the store.** No `store.someField = …` and no `store.notifyChanged()` anywhere in `ui/customer`. Writes go through store functions (or the ViewModel). Reads come from `val snapshot by store.appState.collectAsStateWithLifecycle()`.
4. **No invented claims.** Every user-visible sentence that states a fact about safety, refunds, inspection, timings, delivery, policy or compliance must come from a table in this document. If a string is not in a table and says something factual, do not write it. Undecided business values carry a `DemoValuePill("Demo value · Qnn")`.
5. **Customer wording.** No developer words on screen: TTL, SupportCase, idempotency, serial bound, state machine, "Online Hosted", "PKR". Use "Rs".
6. **Verify names before use.** Check field and enum names in `Models.kt` / `OperationsModels.kt` before you reference them. Do not invent fields. If a needed value is missing from `SharedAppState`, add it as a read-only snapshot field.
7. **Comments.** Keep the current commenting style: a one-line KDoc on each composable and store function, and a `// F<n>` marker where a change from this document was made.
8. **Theme import** is `com.example.ui.theme` (not `com.example.bluegas.ui.theme`).
9. **If you run out of room**, stop at the end of a section and print `STOPPED AFTER F<n>`. The next message will be "Continue from F<n+1>".

---

## F0 — Store and ViewModel hardening (build first)

All in `data/` unless stated. Each function returns `Pair<Boolean, String>`, validates, audits, and calls `notifyChanged()` once.

**F0.1 `cancelOrder` must validate before it changes anything** (`StoreOrders.kt`). Today it releases stock holds, reservations and the allocated serial *before* it asks the state machine whether the cancel is legal; a rejected cancel would leave an order with its stock and serial gone. Fix: at the top, `if (ORD_CANC !in OrderStateMachine.allowedTransitionsMap[order.status].orEmpty()) return Pair(false, "This order can no longer be cancelled.")`. All side effects come after that check.

**F0.2 `raiseComplaint` must not guess the customer** (`BlueGasStore.kt`). Remove the `?: "CUST-001"` fallback. If the order is not found, return `Pair(false, "Order not found.")` and create nothing.

**F0.3 New `raiseSafetyConcern(customerId, description, orderId: String?)`** in `StoreCustomer.kt`.
- Change `SupportCase.orderId` to `String?` (default `null`). Fix every usage compile-wise with `orderId ?: "General"` for display.
- Create a `SupportCase` with `issueType = "SAFETY_CONCERN"`, an audit entry, and STAFF notifications to `StaffSubRole.HSE` (if that role exists) and `StaffSubRole.OPERATIONS`, code `NTF-09`, priority.
- If the store already has a function that opens an L7 safety incident, call it. If not, create only the case and the notifications. **Do not** write containment or emergency instructions anywhere.

**F0.4 New `setActiveCustomer(customerId)`** in `StoreCustomer.kt`: validates the customer exists, sets `activeCustomerId`, clears nothing else, and calls `notifyChanged()`. The UI will call this instead of writing the field.

**F0.5 `notifyPickupArrival` becomes idempotent** (`StoreCustomer.kt`). If an arrival notification for this order already exists, return `Pair(true, "The depot has already been told you're here.")` and add nothing.

**F0.6 Payment-window guard** (`StorePayments.kt`). At the top of `payOnline` and `retryPayment`: if `order.holdExpiresAt != null && System.currentTimeMillis() > order.holdExpiresAt`, call `processTimers()` and return `Pair(false, "The payment window has ended. Please place a new order.")`.

**F0.7 Defensive serial lookup** (`StoreFulfilment.kt`, the two `order.items.first().sku` lines). Replace with `order.items.firstOrNull { it.lineType == "GAS" }?.sku ?: order.items.firstOrNull()?.sku` and make the stock lookup null-safe. An order with no lines must never crash dispatch.

**F0.8 Expiry ticker** (`viewmodel/BlueGasViewModel.kt`). `processTimers()` is only called from a Staff Admin button today, so unpaid holds never expire. Add in `init`: `viewModelScope.launch { while (isActive) { delay(5_000); store.processTimers() } }`. (`processTimers` only notifies when something changed.) Add a one-line comment: "Stands in for the scheduled hold-expiry job."

**F0.9 Snapshot additions (read-only).** Make sure `SharedAppState` exposes `refunds`, `payments` and `paymentHistory` (for the Track payment card). Add any that are missing in the snapshot builder.

---

## F1 — Shared customer composables (new files in `ui/customer/`)

These remove three duplicated dialogs and give Orders, Tracking and Profile one behaviour.

### F1.1 `CustomerShared.kt`
- Move `DemoValuePill` here (out of HomeScreen).
- `@Composable fun rememberNowMillis(tickMs: Long = 1000L, enabled: Boolean = true): Long` built on `produceState`. It replaces the `countdownTick * 0L` trick in `OrdersScreen`.
- `fun formatCountdown(remainingMs: Long): String` returns `"mm:ss"`, or `"0:00"` when `<= 0`.
- `data class BannerMsg(val text: String, val isError: Boolean = false)`.

### F1.2 `PaymentActions.kt`
**`DemoPaymentDialog(orderId, store, onResult: (String, Boolean) -> Unit, onDismiss)`**
- Same look as today: title "Pay now (demo)", body "This is a demo gateway for the pilot. Choose an outcome:", buttons **Payment approved / Payment declined / Connection lost**.
- Each button calls `store.confirmPaymentAndAdvance(orderId, "SUCCESS" | "DECLINED" | "TIMEOUT")` and reports **the store's returned message and success flag** through `onResult`. No hard-coded success text anywhere.

**`PaymentActionsRow(order, snapshot, store, onResult, onOpenGateway)`** — renders only when `order.status == ORD_PAYPEND`:
- Find the latest payment attempt for the order (`attemptNo` highest). It is *active* if its state is not `PAYMENT_FAILED_CLOSED` and not terminal.
- Online order with an active attempt → one button **Pay now** → `onOpenGateway()`.
- No active attempt (the last one was declined or timed out) → a line "Your last payment attempt didn't go through." and two buttons: **Retry payment** → `store.retryPayment(order.id, PaymentMethod.ONLINE_HOSTED)`, and **Pay cash on delivery instead** → `store.retryPayment(order.id, PaymentMethod.CASH_ON_DELIVERY)`. Report each returned `Pair` through `onResult`.
- Beside the buttons show "Pay within mm:ss to keep your cylinder reserved" from `holdExpiresAt` using `rememberNowMillis`. If expired show "The payment window has ended."

### F1.3 `ReceiptDialog.kt` — one receipt, built once
- `fun buildReceiptRows(order, snapshot): List<ReceiptRow>` where `ReceiptRow(label, value, emphasis)`. The on-screen dialog **and** the share text are both rendered from this one list, so they can never disagree.
- Title **"Delivery Receipt"** (not "Official").
- Rows, in this order, each only if the data exists (otherwise skip the row; never print a placeholder or a made-up value):

| Row | Source |
|---|---|
| Order, date | `order.id`, `formatTimestamp(createdAt)` |
| Customer, address or depot | customer name; the order's address, or the pickup depot name and address |
| Fulfilment | Delivery / Depot pickup + fulfilment point name |
| Each quote line as charged | for `GAS`, `CYLINDER`, `ACCESSORY` lines: `"${qty}x ${productName}"` → `formatRupees(subtotalPaisa)`. **Skip the `DEPOSIT` line itself** (its subtotal is 0). |
| Security deposit | one row from `depositTotalPaisa` when `> 0`, labelled "Security deposit (held; does not transfer ownership)" |
| Platform fee | `platformFeePaisa` |
| Delivery fee | `deliveryFeePaisa` |
| Discount | `-` + `discountPaisa` when `> 0` |
| GST | `gstPaisa`, and "Further tax" when `furtherTaxPaisa > 0` |
| **Total** | `totalAmountPaisa` (label "Total paid" only for a settled/paid order, else "Total") |
| Price list | "Price list v${priceListVersion}" and, when `reference118PricePaisa != null`, "11.8 kg reference ${formatRupees(...)}" |
| Payment | method in plain words (**Online payment** / **Cash on delivery**) and status in plain words |
| Cylinder ID | `allocatedSerial` only if non-null |
| Installation | from `installationResult` / `installationNotDoneReason` if present |
| Proof of delivery | `podReference` only if non-null |

- **Remove** the fallbacks `"CYL-SERIAL-VERIFIED"` and `"POD-xxxx-DONE"`, and **remove** the line "Status: Handover Complete & Safety Inspected" (there is no inspection in Phase 1).
- Footnote (keep): *"Prices, fees and tax are demo settings pending client decisions (Q03, Q04, Q22, Q24)."*
- Footing rule: the rows above must add up to `totalAmountPaisa` (lines + deposit + platform + delivery − discount + tax). After building the rows, compute the sum and, if it differs, add a `Log.w("Receipt", …)` line so a mismatch is visible in Logcat.
- Buttons: **Share receipt** (existing `ACTION_SEND` text intent, built from the same rows) and **Close**.

### F1.4 `RateOrderDialog.kt`
`RateOrderDialog(orderId, store, onResult: (String, Boolean) -> Unit, onDismiss)`: five stars, optional comment, **Submit rating** → `store.rateOrder(...)`. Report the store's message and flag **on both success and failure** (today a failed rating is swallowed silently). Stars reset to 5 and the comment to empty each time the dialog opens.

---

## F2 — `TrackingScreen.kt`

### F2.1 Hero banner is unreadable — fix first (P0)
The card colour is a light tint but the title, subtitle and icon are hard-coded white, so Payment Pending, Confirmed, Assigned, Accepted and Preparing show white text on near-white. Replace the `Quadruple(bg, icon, title, subtitle)` with this: card container = the light tint, a 1 dp border in the accent colour, an accent-coloured circle holding a **white** icon, title colour `0xFF0F172A`, subtitle colour `0xFF475569`.

Use exactly this copy (delivery / pickup where they differ). `{mm:ss}` is the live countdown to `holdExpiresAt`; `{depot}` is the pickup depot name.

| Status | Tint → accent | Title | Subtitle |
|---|---|---|---|
| Draft, Validation Pending | blue | Checking your order | We're confirming your address and stock. |
| Payment Pending | amber | Waiting for payment | Pay within {mm:ss} to keep your cylinder reserved. |
| Confirmed, Reserved | blue | Order confirmed | Your cylinder is reserved. / Your depot is being assigned. |
| Assigned | blue | Assigned | A delivery partner has been assigned. / Your depot has been assigned. |
| Accepted | blue | Order accepted | Your order is being organised. |
| Preparing | amber | Preparing your order | Your cylinder is being made ready. |
| Dispatched | blue | On the way | Your rider is heading to your address. |
| Ready for Pickup | green | Ready for pickup | Your cylinder is ready at {depot}. |
| At Handover | green | Rider has arrived / You're at the depot | Please share your handover code with the rider. / Show your handover code at the counter. |
| Inspection Pending, Adjustment Pending | amber | Handover in progress | Final checks and payment are being completed. |
| Handover Complete | green | Handover complete | We're finalising your receipt. |
| Completed | green | Delivered / Collected | Thank you for choosing BLUE GAS. |
| Fulfilment Exception | red | We're sorting something out | {exception reason in plain words}. Operations is working on it. |
| Cancelled | grey | Order cancelled | {cancelReason, else "This order was cancelled."} |
| Failed Closed | grey | Order closed | This order could not be completed. |
| Superseded | purple | Order replaced | A newer order replaced this one. |

Delete the old strings: "bubble leak test", "inspection-passed cylinder", "Leak test passed", "Safe LPG cylinder delivered and verified", "Stock held for 15 minutes", "Heading to distributor hub for vehicle loading", "Checking safety compliance and distributor pilot coverage".

### F2.2 Handover code must be the order's real code (P0)
The screen shows `hashCode % 9000 + 1000`, which is **not** `order.handoverPin` (created in `createOrder`, `4821` on seeded orders). Replace the computed value with `currentOrder.handoverPin`.
- Show the card only while status is Dispatched, Ready for Pickup, At Handover, Inspection Pending or Adjustment Pending.
- Delivery text: "Share this 4-digit code with the rider at handover." Pickup text: "Show this code at the counter."
- Remove "to authorize cylinder release".
(The rider-side check of this code is in the next pass, the Rider app.)

### F2.3 Pickup orders have no map and no rider
- If `fulfilmentMode == PICKUP`: **no map, no rider card, no "Direct Node-to-Consumer Van" box.** Show a depot card instead: depot name, address, and "Opening hours: Demo value · Q14" with a `DemoValuePill`.
- At Ready for Pickup keep the big button, relabelled **"I'm at the depot"**, calling `store.notifyPickupArrival` (now idempotent, F0.5). After a successful tap replace the button with a green chip "Depot notified" (local `remember` flag, also true when the store says already notified).

### F2.4 Delivery map — honest labels, moving pin
- Label the map **"Live tracking (simulated for demo)"** (replaces "GPS Trace Active").
- Delete the hard-coded `"ETA: 18 - 25 minutes"` and the `"Lahore Central"` fallback. The pill text is a plain status: Dispatched → "On the way", At Handover → "At your door", Completed → "Delivered", otherwise "Preparing at {depot}".
- Replace "Direct Node-to-Consumer Van" with "Delivery from {depot name}".
- Animate the rider pin with `animateFloatAsState(progress, tween(1200))`. While status is Dispatched, `progress = 0.20f + 0.65f * min(1f, elapsedSinceDispatch / 60_000f)` where `elapsedSinceDispatch` uses the Dispatched timestamp from `orderStatusHistory` and `rememberNowMillis(enabled = status == ORD_DISP)`. At Handover and later, `0.88f`. Before dispatch, `0.15f`.

### F2.5 Rider card and call button
- Show the rider card only from Dispatched onward. Content: rider name and vehicle number. **Remove** the "• BlueGas Van" suffix. Show the verified tick only if the rider record carries an approved/active flag; otherwise omit it.
- The phone button opens the dialer: `Intent(Intent.ACTION_DIAL, Uri.parse("tel:${assignedRider.phone}"))`. **Delete** the "Call Delivery Rider" dialog, "Simulate Call Connected" and "Connecting directly to rider van hands-free system…".

### F2.6 "Your cylinder" card (rename from "Cylinder Safety & Traceability")

| Old | New |
|---|---|
| Cylinder Safety & Traceability | Your cylinder |
| Bound Cylinder Serial / "Allocating Serial..." | Cylinder ID / "Assigned while your order is prepared" |
| Leak Inspection: "PASSED (Bubble Test)" / "Scheduled at Handover" | **Installation** (delivery orders only): `Installed by rider` / `Not installed — {installationNotDoneReason}` from `installationResult`; until then "At handover". Hidden for pickup. |

### F2.7 Stepper
- Replace the descriptions with neutral ones: 1 "Confirmed", 2 "Partner assigned", 3 "Being prepared", 4 "On the way" / "Ready to collect", 5 "Rider arrived" / "At the counter", 6 "Delivered" / "Collected". Each shows its timestamp from `orderStatusHistory` once reached. Remove "Inspected & …", "Leak test & regulator check", "Validation passed".
- **Completed orders: every step shows a green tick** (today step 6 stays blue). Fix `isStepCompleted`/`isStepCurrent` so `ORD_COMP` marks all six done.
- **Exception, Cancelled, Failed Closed, Superseded:** derive the furthest reached step from `orderStatusHistory` (ignore the exception and cancel entries) and tick up to it; then add a red (exception) or grey (others) terminal row with the reason and its timestamp. No more empty grey stepper.

### F2.8 Payment card (separate lifecycle, plain words)
Add a "Payment" card under the hero for every order, from the latest payment attempt and refund record:
- COD, before cash collected: "Cash on delivery — Rs {total} due at handover."
- COD, cash collected (before settlement): "Cash received — Finance is reconciling it."
- Online, awaiting payment: "Waiting for payment" and show `PaymentActionsRow` (F1.2) plus `DemoPaymentDialog`.
- Online, paid: "Paid online."
- Attempt declined / timed out: "Your last payment attempt didn't go through." (with the retry buttons from F1.2).
- Settled: "Paid."
- Cancelled order: look at `snapshot.refunds` for this order. Refund pending → "Refund of Rs {amount} is being processed by Finance." Refund completed → "Refund of Rs {amount} completed." Payment voided → "Your payment hold was released." No payment taken → "No payment was taken."

### F2.9 Cancel wording and result
- Remove "Orders can be cancelled before dispatch without penalty." Use "You can cancel until your order is dispatched."
- Both cancel dialogs (Tracking and Orders) use: "Your reserved cylinder will be released. If you already paid online, the refund is handled by our Finance team." Remove "Any online payments have been refunded" and "a full refund will be processed".
- The cancelled-order notice card uses the payment card's refund line (F2.8) instead of the fixed sentence.
- After cancelling, show the store's returned message through the shell banner (F3.1). If the store returns `false`, show it as an error.

### F2.10 Completed orders
On a Completed order show two buttons: **Receipt** (opens the shared `ReceiptDialog`) and **Rate delivery** (opens `RateOrderDialog`; hidden once rated).

### F2.11 Banner
Delete the screen's local `notificationBannerMessage` and its banner UI. `TrackingScreen` takes `onActionMessage: (String, Boolean) -> Unit` and uses the shell banner (F3.1).

---

## F3 — `CustomerAppScreen.kt` (shell)

**F3.1 One banner for the whole customer app.** Hold `var banner by remember { mutableStateOf<BannerMsg?>(null) }` in the shell, show it under the app bar on every tab (amber if `isError`, green otherwise), dismiss button, and auto-dismiss after 5 s with `LaunchedEffect(banner)`. Pass `onActionMessage = { text, isError -> banner = BannerMsg(text, isError) }` to Home, Orders, Tracking and Profile. Delete Home's `feedbackSnackbarMessage`, Profile's `feedbackMessage`, and the shell's `notificationBannerMessage`. Messages must survive tab changes (the message after "Place order" now appears on the Orders tab).

**F3.2 Reorder prefill.** Replace the six `prefill*` variables with one object:
`data class ReorderPrefill(val sku: String, val quantity: Int, val orderType: OrderType, val addressId: String?, val mode: FulfilmentMode, val paymentMethod: PaymentMethod, val pickupPointId: String?, val addAccessory: Boolean, val nonce: Long)`.
The shell holds `var reorderPrefill: ReorderPrefill?` and passes it to Home with `onPrefillConsumed = { reorderPrefill = null }`. Key it on the active customer: `LaunchedEffect(activeCustomer.id) { reorderPrefill = null }` so one customer's reorder never leaks into another's.

---

## F4 — `OrdersScreen.kt`

1. **Payment actions.** In `CustomerOrderCard` replace the old "Pay Online" button with `PaymentActionsRow` (F1.2). Own one `DemoPaymentDialog` at screen level, driven by `selectedOrderIdForPayment`. After the dialog closes, report the store's message through `onActionMessage` (no hard-coded text).
2. **Hold box.** Replace "Stock Hold TTL: … remaining before auto-release" with "Pay within {mm:ss} to keep your cylinder reserved" (via `rememberNowMillis`). Delete the `countdownTick * 0L` code.
3. **Tabs.** Active = every non-terminal order (so Fulfilment Exception orders are *Active* and show their red status chip). Replace the "Exceptions" tab with **Cancelled** = Cancelled, Failed Closed and Superseded. The header badge "N Active" uses the same predicate as the Active tab.
4. **Wording.** "Online Hosted" → **Online payment**. "Bound Cylinder Serial" → **Cylinder ID**. Delete the unused `val payment = …` in the card. Empty state: if the customer has no orders at all show "No orders yet" with an **Order now** button (`onNavigateToHome`); otherwise "No {filter} orders".
5. **Receipt.** Delete the local receipt dialog; use the shared `ReceiptDialog` (F1.3).
6. **Problem report dialog.** Title **"Report a problem"**. Replace the sentence "Raising a complaint creates an accountable SupportCase…" with "We'll open a support case. Your order stays as it is." Button **Submit**. When "Cylinder Leakage / Safety Concern" is selected show this exact block above the form: "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122." with a **Call 1122** button (`ACTION_DIAL tel:1122`). Show the store's message through `onActionMessage` for success and failure.
7. **Rate.** On a Completed order that is not yet rated show **Rate** (opens `RateOrderDialog`); once rated show the stars.
8. **Cancel dialog** uses the F2.9 wording; show the store's message through `onActionMessage`, error styling when it returns `false`.

---

## F5 — `HomeScreen.kt`

**F5.1 Accessory (P0 — the current path creates empty orders).** "Accessory only" builds a quote with no lines and an order with no items; and the accessory (regulator & hose) is also listed as a cylinder size, where it prices as a free "LPG Refill". Fix:
- Remove **Accessory only** from the "What do you need?" control. It becomes two options: **Refill (empty exchange)** and **New cylinder**. (Do not delete the `ACCESSORY_ONLY` enum value.)
- The size list shows only `it.isCylinder && it.isHouseholdApproved`.
- Add an optional add-on card, visible for both order types: "Add regulator & hose (+Rs {price})", a checkbox. Price = `PricingEngine.roundToRupees(snapshot.currentPriceList.accessoryPaisa["SKU-REG-HOSE"] ?: …)`. If that price is missing, hide the card.
- When ticked, pass `accessories = listOf("SKU-REG-HOSE" to 1)` to **both** `PricingEngine.buildQuote` (the preview) and `store.createOrder` (the order), so the preview, button, order and receipt totals are identical.

**F5.2 Delete the dead `includeDeposit`.** Remove the local computation and stop passing it. The deposit comes from `settings.newCylinderModel` inside the quote (NEW_CYLINDER only). Keep the Q03 model text and its `DemoValuePill`. The Refill "I'll hand back an empty BLUE GAS cylinder" switch stays as information with a `DemoValuePill("Demo value · Q20")`; it does not change the price.

**F5.3 Pickup depot choice.**
- Show the depot picker whenever `selectedMode == PICKUP` (serviceable or not): a list of `snapshot.nodes.filter { it.pickupEnabled && it.type != FulfilmentPointType.PLANT }`, restricted to the selected address's city (check the node/zone fields for the city match). Each row: name, address, "Opening hours: Demo value · Q14".
- The Delivery chip is **disabled** when the address is not serviceable, with the helper text "This address is outside the current delivery area. Depot pickup is available."
- When the address changes: if it is not serviceable, force `PICKUP`; reset `selectedPickupNodeId` to the first depot in that city.
- If no depot exists in the city, disable **Place order** and show "No depot is available in your city yet."

**F5.4 Messages come from the store.**
- COD: `val (ok, msg) = store.confirmPaymentAndAdvance(...)`. Show `msg` (error styling when `!ok`) through `onActionMessage`, then go to the Orders tab.
- Online: open the shared `DemoPaymentDialog`. On result show the store's `msg`/`ok` through `onActionMessage`, then go to Orders (so **Retry payment** is there on a decline). Delete the hard-coded "Payment received — order confirmed."
- After a successful create, set the tracking target and use "Order {id} placed." (do not repeat the hold time here).

**F5.5 Reorder.** Take `prefill: ReorderPrefill?` and `onPrefillConsumed`. Apply it once in `LaunchedEffect(prefill?.nonce)`, then call `onPrefillConsumed()`. While applying: use the address only if it belongs to the active customer, else the default address; keep the SKU only if it is in the approved cylinder list, else the first approved one; `quantity.coerceIn(1, settings.maxQuantityPerOrder)`; ignore a pickup depot that is not in the address's city.

**F5.6 Keep choices when switching tabs.** Hold SKU, quantity, order type, fulfilment mode, payment method and the add-on in `rememberSaveable` so leaving Home for Orders and coming back does not reset the form (still reset on a successful order and on customer switch).

**F5.7 Double-tap guard.** Disable **Place order** while the call runs (`var placing`), so a fast double-tap cannot create two orders.

---

## F6 — `ProfileScreen.kt`

1. **Remove "Member since …"** (it is `now − 90 days`, invented). Show the customer's phone only.
2. **Deposits card.** Heading "Security deposits held: Rs {amount}" (no "Refundable"). Delete the sentence "Full refund is processed upon return of undamaged cylinder." Keep "A security deposit does not transfer ownership of the cylinder." and add "Refund terms follow the BLUE GAS deposit policy." with `DemoValuePill("Demo value · Q03")`.
3. **Holdings list.** Besides serial and deposit, show the cylinder size and the ownership state in plain words (Owned / Deposit held / Loaned) using the existing holding fields.
4. **Safety card — replace both the card and the dialog.**
   - Delete "24/7 Gas Leak Emergency Helpline", "In case of suspected leak, turn off regulator and call immediately.", and the whole "Emergency LPG Safety Response" dialog (its bullet list of shutting valves, ignition, ventilation and evacuation is invented safety procedure).
   - New card, title **"Safety & emergencies"**, body exactly: "Follow the approved BLUE GAS safety procedure (to be supplied by the client, Q07). For emergencies call Rescue 1122."
   - Two buttons: **Call 1122** (`ACTION_DIAL tel:1122`) and **Report a safety concern**.
   - **Report a safety concern** opens a dialog: an optional order selector ("Not about a specific order" plus the customer's orders), a required description, **Submit**. It calls `store.raiseSafetyConcern(activeCustomer.id, description, orderId)` (F0.3) and shows the store's message through `onActionMessage`.
5. **Switch customer.** Replace `store.activeCustomerId = cust.id; store.notifyChanged()` with `store.setActiveCustomer(cust.id)` (F0.4).
6. **Add-address dialog.** Remove the duplicate inner `val isServiceableZone` (it shadows the outer one). After saving, the message is "Address added. This area is in the delivery zone." or "Address added. This address is outside the current delivery area — depot pickup is available."
7. **Rating** uses the shared `RateOrderDialog` (F1.4); delete the local copy.
8. **Support card.** Show "BLUE GAS support: {settings.supportPhone}" with a **Call** chip (`ACTION_DIAL`). Keep WhatsApp as a disabled chip labelled "WhatsApp (Phase 3)".

---

## F7 — Final self-check (do this before you finish)

**A. Banned-text search.** Search `ui/customer` and `data/StoreCustomer.kt` (case-insensitive) and remove every hit that is customer-visible:
`bubble|leak test|inspection-passed|inspected|safely|safe lpg|turn off|regulator knob|evacuat|ventilat|ignite|lighter|full refund|refunded|without penalty|undamaged|member since|hands-free|simulate|gps trace|eta:|lahore central|PKR|TTL|SupportCase|idempotency|hydro|legally|24/7|certified|CYL-SERIAL-VERIFIED|-DONE`

**B. Architecture search.** In `ui/customer`: zero occurrences of `notifyChanged(` and zero assignments of the form `store.<field> =`.

**C. Walk-through (confirm each one works):**
1. COD refill → order is Confirmed; the green banner appears on the **Orders** tab; the Home button total = Orders card total = Receipt total.
2. Online order → **Payment declined** → the card shows "Your last payment attempt didn't go through." with **Retry payment** and **Pay cash on delivery instead**. Retry → **Payment approved** → Confirmed. Repeat and choose COD → Confirmed.
3. Online order left unpaid with the hold set to 1 minute in Staff Settings → within about 5 s after expiry it becomes Cancelled with the "payment window expired" notification; stock is released.
4. Every status banner in Tracking is readable (advance one order through Confirmed, Assigned, Preparing, Dispatched, At Handover, Completed using the Rider and Staff surfaces). Pickup orders show no map and no rider.
5. The handover code on Tracking equals the order's stored code (`4821` on seeded orders) and is visible only from Dispatched to At Handover.
6. Completed order: all six steps green; Receipt rows add up to the total; **Rate delivery** works once and a second attempt shows the store's message.
7. Cancel a paid online order after capture: Tracking says the refund is being processed by Finance (not "refunded"). Cancel a COD order: "No payment was taken."
8. Refill + "Add regulator & hose": the quote, order and receipt each contain the accessory line and the same total; the order dispatches without a crash.
9. Reorder: prefill applies once; open Profile and come back — no stale prefill. Switch customer in Profile: Orders, Tracking and Profile show the new customer's data and the tab does not jump.
10. Home form keeps its choices when you visit Orders and come back.
11. Profile safety card shows only the approved sentence, **Call 1122** and **Report a safety concern**; submitting creates a case visible to Staff.

When done, list for each of F0–F6 what you changed, in one line each, and state any field you had to add to the snapshot or to a model.
