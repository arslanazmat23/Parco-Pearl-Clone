# BLUE GAS — Customer App Fix Pass, Round 3 (final customer polish, after the Oct-23 build)

**Scope:** Customer App only, plus three one-line data-layer string/format fixes named below. This is the last customer pass before the Rider App.
Round 2 landed well (banner colours, add-on price, tab state, payment wording, cancel/exception wording are all correct now — **do not touch them**). This round fixes what the Oct-23 review still found.

**Do the sections in order.** H5 (comments) must change **no behaviour**. If you run out of room, stop at the end of a section and print `STOPPED AFTER H<n>`.

## 0. Rules (unchanged)
1. Frozen: `OrderStateMachine`, `PaymentStateMachine`, status names, paisa `Long` money, the `PricingEngine.buildQuote` signature and maths.
2. `ui/customer` never writes store fields and never calls `notifyChanged()`. Reads come from `snapshot`.
3. No invented claims. Undecided values carry `DemoValuePill("Demo value · Qnn")`. Deposit refundability is an **open decision (Q03)** — the word "refundable" must not appear anywhere customer-facing.
4. Theme import is `com.example.ui.theme`. Check names in `Models.kt` before using them.
5. Every message callback stays `(message, isError)` — nothing new may pass a success flag.

---

## H1 — Price summary must add up, and zero-rupee deposit lines must not leak (fix first)

**Problem A — Home "Price Summary" does not foot.** `quote.subtotalPaisa` already includes the accessory (and, for the "sold" cylinder model, the cylinder body). Home prints that subtotal as "LPG Refill Gas" **and then prints the accessory again** on its own row, so with the add-on ticked the rows add up to more than "Total Payable".

**Problem B — deposit/loan lines leak into customer lists.** `createOrder` stores every `quote.lines` entry as `order.items`, including the `DEPOSIT` line (unit price 0, name "Security Deposit (Refundable per policy · Q03)"). The default cylinder model is DEPOSIT, so a **New cylinder** order shows, in Orders and in the Receipt, a row `1x Security Deposit (Refundable per policy · Q03) — Rs 0` **plus** the separate "Cylinder security deposit" row, and the forbidden word "Refundable".

**Fix:**
1. `HomeScreen.kt` Price Summary: build the item rows from `quote.lines`, skipping lines whose `lineType == "DEPOSIT"`. Each row: label `"${line.quantity}x ${line.productName}"`, value `formatRupees(line.subtotalPaisa)`. Delete the hard-coded "LPG Refill Gas (Nx)" row and the separate "Regulator & Hose Add-on" row. Then, in this order: Doorstep Delivery Fee (delivery mode only), Platform Convenience Fee, Sales Tax (GST) — each only when > 0 as today — then Cylinder Security Deposit (when `quote.depositTotalPaisa > 0`, keep the amber note), then Promotional Discount, then the divider and Total. Add a one-line comment above the loop stating the footing rule: *item rows + fees + tax + deposit − discount = total*.
2. `data/PricingEngine.kt`: change **only the string** `"Security Deposit (Refundable per policy · Q03)"` to `"Cylinder Security Deposit"`. Do not change any arithmetic or signature.
3. `model/Models.kt` line ~469: update the `lineType` comment to list all values: `"GAS", "CYLINDER", "ACCESSORY", "DEPOSIT", "LOAN"`.
4. `CustomerShared.kt`: add
   ```kotlin
   /** Order lines a customer should see as items. The DEPOSIT line is excluded because the deposit is shown on its own labelled row. */
   fun Order.customerVisibleItems(): List<OrderLine> = items.filter { it.lineType != "DEPOSIT" }
   ```
   Use it in `OrdersScreen.kt` (the items summary in `CustomerOrderCard`) and in `ReceiptDialog.kt` (the item loop that builds `linesSum`; the footing check is unaffected because the deposit line's unit price is 0).
5. Delete the unused `"lbl_deposit_note"` entry in `model/I18n.kt` only if nothing references it (it currently says "Refundable Security Deposit"); otherwise change it to "Security Deposit (liability item; does NOT transfer cylinder ownership)".

**Check:** with a 11.8 kg refill, quantity 2, delivery, add-on ticked: the visible rows add up (to the rupee) to Total Payable. With **New cylinder** (deposit model): Orders card lists the gas line only; Receipt shows one deposit row and no `Rs 0` row; search the whole project for `Refundable` in `ui/customer`, `data/PricingEngine.kt`, `model/I18n.kt` → no hits.

---

## H2 — Dialogs must scroll (container-break risk on small phones / with keyboard)

No dialog in `ui/customer` scrolls. On a 360×640 screen, or with the keyboard open, the lower fields and the Save/Submit area are clipped.
Wrap the content `Column` of each dialog below with `.verticalScroll(rememberScrollState())` (import `androidx.compose.foundation.rememberScrollState` and `androidx.compose.foundation.verticalScroll`). Keep `.imePadding()` where it exists.
- `ProfileScreen.kt`: Add New Delivery Address dialog; Report a Safety Concern dialog.
- `OrdersScreen.kt`: Report a problem (complaint) dialog; Cancel Order dialog.
- `TrackingScreen.kt`: Cancel Order dialog.
Do **not** add scrolling to `ReceiptDialog` (it already uses a `LazyColumn`), `DemoPaymentDialog` or `RateOrderDialog`.

---

## H3 — Layout polish

1. `PaymentActions.kt` — the two retry buttons have unequal heights because "Pay cash on delivery instead" wraps. Change the labels to **"Retry payment"** and **"Pay by cash instead"**, set both to `fontSize = 12.sp`, `maxLines = 1`, `contentPadding = PaddingValues(horizontal = 8.dp)`, and give both `Modifier.weight(1f).height(40.dp)`.
2. `OrdersScreen.kt` search field: remove `.height(48.dp)` (a fixed 48 dp clips the typed text; the field's natural height is 56 dp). Keep `singleLine = true`.
3. `HomeScreen.kt` payment helper text: replace the hard-coded `"Hold expires in 15 mins if unpaid. Simulated pilot gateway."` with `"Hold expires in ${snapshot.settings.holdTtlMinutes} mins if unpaid. Simulated pilot gateway."` (Admin can change the hold time).
4. `HomeScreen.kt` greeting card: replace `"Licensed LPG doorstep supply in Lahore pilot zone."` with `"Order LPG for doorstep delivery or depot pickup."` (we cannot claim "licensed" in the UI).

---

## H4 — State and wording consistency

1. **No other customer's address may ever appear.**
   - `HomeScreen.kt` `activeAddress`: look up only in `customerAddresses`: `customerAddresses.find { it.id == selectedAddressId } ?: customerAddresses.firstOrNull() ?: <the existing placeholder Address>`. Remove the `snapshot.addresses.find` and `snapshot.addresses.firstOrNull()` fallbacks.
   - `CustomerAppScreen.kt` `activeAddress`: choose from this customer's addresses only, in this order: default address, then serviceable address, then any. Remove the `?: snapshot.addresses.firstOrNull()` fallback (when the customer has none, hide the address strip as it does today).
2. `CustomerAppScreen.kt` header chip: replace the text `"Restricted"` with `"Pickup only"` (the other state stays "Lahore Pilot"). Delete the unused `val isExpanded` line and replace the `BoxWithConstraints` wrapper with a plain `Box(modifier = Modifier.fillMaxSize())`.
3. `HomeScreen.kt` payment dialog: set `payableLabel` from the **created order**, not the live form preview: `formatRupees(snapshot.orders.find { it.id == createdOrderIdForPayment }?.totalAmountPaisa ?: quote.totalAmountPaisa)`.
4. `data/StorePayments.kt` `confirmCod`: in the customer notification message replace `"Rs ${order.totalAmountPaisa / 100}"` with `formatRupees(order.totalAmountPaisa)` (so it reads "Rs 4,148" like every other amount).
5. `data/StoreSafety.kt` `raiseIncident` (staff-visible text only): when no cylinder is linked (`targetCyl` null or blank) the staff alert reads `"Cylinder null on HSE hold."` and the return message claims a cylinder was held. Build both messages conditionally — with a cylinder: `"... Cylinder $targetCyl on HSE hold."` / `"Incident $id raised. Cylinder placed on HSE hold."`; without: `"Safety alert from $source: $description."` / `"Incident $id raised."`. Do not change which cylinders are held.
6. `data/StoreCustomer.kt` `raiseSafetyConcern`: pass `actor = customers.find { it.id == customerId }?.name ?: customerId` into `raiseIncident` so the audit trail shows the customer, not "HSE Officer". Do not change the hold behaviour (see decision note in the review).

---

## H5 — Finish the comment standard (no behaviour change)

Coverage is now good (every public function has a KDoc). What is left:
1. Add a `/** … */` KDoc to `HeroBannerConfig`, `StepperStep`, `isStepCompleted` and `isStepCurrent` in `TrackingScreen.kt` (what they model, and for the two functions: the ranking rule).
2. Replace the comment `// Rider Animated Pin per F2.4` with a real description. Search `ui/customer`, `StoreCustomer.kt`, `StorePayments.kt` for any comment containing a section tag (`F<n>`, `S<n>`, `G<n>`, `H<n>`) — expect none left.
3. Add the missing file header block (purpose, screens/flows, state read, store calls) at the very top of `PaymentActions.kt` and `RateOrderDialog.kt`, in the same style as the other files.
4. Add **why** comments (not "what" comments) at these places, one to three lines each:
   - `TrackingScreen.kt` `isStepCompleted`: why `ORD_CONF`/`ORD_RSVD` share a rank, why `ORD_DISP`/`ORD_RDYPU` share a rank, and why a completed order marks every step done.
   - `TrackingScreen.kt` animated rider pin: progress is simulated (about 60 s from the Dispatched time), real GPS arrives with the Rider app; at the door it parks at 88 %.
   - `TrackingScreen.kt` hero banner mapping: one config per status so wording stays in plain customer language; staff-only fields (`exceptionDetails`) are never shown to customers.
   - `TrackingScreen.kt` payment card: points to `formatCustomerPaymentStatus` and states "authorised is not captured".
   - `ProfileScreen.kt`: why switching customer is done through the store (shared persona for the pilot), how `maskCnic` shows only the first 7 and last digit, how a safety concern becomes a support case plus an L7 incident, and why the zone chosen in Add Address decides serviceability.
   - `OrdersScreen.kt`: which statuses count as "Cancelled" in the filter (Cancelled, Failed Closed, Superseded) and that `isCancellable` mirrors the transitions the state machine allows.
   - `HomeScreen.kt`: why the quote is recomputed only when its inputs change (`remember` keys), and why `includeDeposit = false` is still passed (the deposit comes from the price list/settings model, not this flag).
5. Raise comment density in `HomeScreen`, `ProfileScreen` and `TrackingScreen` from about 4–5 % to roughly 8 % by this method only. Do not split or refactor the large composables now.

---

## H6 — Final self-check

**A. Search (case-insensitive) in `ui/customer`, `data/PricingEngine.kt`, `model/I18n.kt`, `data/StoreCustomer.kt`, `data/StorePayments.kt`; report counts, expect zero real hits:**
`refundable|licensed|tamper|telemetry|PKR|Non-Serviceable|"Restricted"|[FSGH][0-9]+(\.[0-9]+)?\b.*//|// .*per [FSGH][0-9]`

**B. Every top-level `fun`/`class`/`data class`/`enum`/`object` in the customer files (including `private` ones) has a KDoc directly above it.** List any that do not. Expect none.

**C. Walk-through (confirm each, say pass/fail):**
1. Refill, qty 2, Delivery, add-on ticked: add the visible Price Summary rows by hand (item rows + fees + tax − discount); it equals Total Payable. Untick the add-on: still equal.
2. New cylinder (deposit model), qty 1: summary shows gas row, fees, tax, then the deposit row with the amber note; they add up to the total. After placing: Orders card lists only the gas line; Receipt shows exactly one deposit row, no `Rs 0` line, no word "Refundable".
3. Open Profile → Add New Delivery Address on a small emulator (360×640) with the keyboard open: every field is reachable by scrolling and Save is visible. Same for Report a Safety Concern and for Orders → Dispute and Cancel.
4. A Payment-Pending order shows **Retry payment** and **Pay by cash instead** at the same height on one line each.
5. Switch to a customer with no saved address (or delete the addresses of a test customer): neither the header strip nor Home shows another customer's address.
6. Header chip says "Lahore Pilot" for a serviceable address and "Pickup only" for a non-serviceable one.
7. Place a COD order: the notification reads "Rs 4,148 payable at delivery" (with the comma).
8. Report a safety concern **without** choosing an order: the Staff notification does not contain the word "null".
9. Regression: decline a payment → amber banner; Retry → green; rate an order → green; Home form choices survive a tab switch; cancelled order hero shows the reason chosen.

When done, list per section what changed in one line each, and report comment density per file (before → after).
