# BLUE GAS — Customer App Fix Pass, Round 2 (after the Oct-22 build)

**Scope:** Customer App only. This is a short, targeted pass. Round 1 (F0–F7) landed well; this round fixes what the review of the Oct-22 build found, and adds the code-comment standard.
**Do the sections in order.** G1 and G2 change visible behaviour; G5 (comments) must change **no behaviour**.

## 0. Rules (unchanged from round 1, restated)
1. Frozen: `OrderStateMachine`, `PaymentStateMachine`, status names, paisa `Long` money, `PricingEngine.buildQuote` signature.
2. `ui/customer` never writes store fields and never calls `notifyChanged()`. Reads come from `snapshot`.
3. No invented claims. Every factual customer-facing sentence comes from a table in this document or round 1. Undecided values carry `DemoValuePill("Demo value · Qnn")`.
4. Theme import is `com.example.ui.theme`. Check names in `Models.kt` before using them.
5. If you run out of room, stop at the end of a section and print `STOPPED AFTER G<n>`.

---

## G1 — Banner colours are inverted (fix first)

**Problem.** The shell banner takes `(message, isError)`. The shared dialogs report `(message, ok)`. Several call sites forward `ok` straight into `isError`, so a **successful** payment or rating shows the amber warning banner and a **declined** payment shows the green success banner. Home and the Orders dialogs happen to negate it (`!ok`) and are correct; the others are not.

**One contract for the whole customer app:** every message callback is `(message: String, isError: Boolean)`. Nothing in `ui/customer` passes a success flag as the second argument.

Make exactly these changes together (changing only some will double-invert):

| File | Change |
|---|---|
| `PaymentActions.kt` | In `DemoPaymentDialog` (3 buttons) and `PaymentActionsRow` (Retry and COD buttons) change `onResult(msg, ok)` to `onResult(msg, !ok)`. Rename the parameter to `onResult: (message: String, isError: Boolean) -> Unit` and document it. |
| `RateOrderDialog.kt` | `onResult(msg, ok)` → `onResult(msg, !ok)`; same parameter rename. |
| `HomeScreen.kt` (the `DemoPaymentDialog` `onResult` lambda) | Remove the negation: `onActionMessage(msg, isError)`. Rename lambda params `{ msg, isError -> … }`. |
| `OrdersScreen.kt` (the `DemoPaymentDialog` and `RateOrderDialog` `onResult` lambdas) | Remove the negation: `onActionMessage(msg, isError)`. |
| `OrdersScreen.kt` `PaymentActionsRow(onResult = onActionMessage)` | Leave as is (now correct). |
| `TrackingScreen.kt` (`onResult = postMessage` ×3) | Leave as is (now correct). |
| `ProfileScreen.kt` rating lambda | Already `{ msg, isError -> onActionMessage(msg, isError) }` — leave (now correct). |

Calls made directly from a screen after a store call (`cancelOrder`, `raiseComplaint`, `addAddress`, pickup arrival, safety concern) already pass `!ok` — do not touch them.

**Check:** decline a payment → amber banner with the decline message; approve → green; submit a rating → green; **Retry payment** → green "New payment attempt created."; rating the same order twice → amber.

---

## G2 — `HomeScreen.kt` and shell

**G2.1 Add-on price label is 100× too high.** `roundToRupees` already returns **paisa**, but the label does `formatRupees(accessoryPriceRupees * 100L)`, so Rs 1,450 shows as "Rs 145,000".
- Rename `accessoryPriceRupees` → `accessoryPricePaisa`. Compute it with the price list's own rule: `PricingEngine.roundToRupees(rawPaisa, snapshot.currentPriceList.roundingRule)` (the quote uses that rule; the label must match it).
- Label: `"Add regulator & hose (+${formatRupees(accessoryPricePaisa)})"`.
- Remove the hard-coded `?: 145000L` fallback. If `accessoryPaisa["SKU-REG-HOSE"]` is null, hide the add-on card.

**G2.2 Form choices are reset every time you leave Home.** Two causes: (a) tabs are switched with a plain `when`, so Home leaves composition and its `rememberSaveable` state is dropped; (b) `LaunchedEffect(activeCustomer.id)` runs again on every re-entry and overwrites the form with defaults.
- In `CustomerAppScreen`, create `val saveableHolder = rememberSaveableStateHolder()` and wrap each tab's content: `saveableHolder.SaveableStateProvider(currentTab.name) { … }`.
- In `HomeScreen`, make the reset fire **only when the customer actually changes**: keep `var lastCustomerId by rememberSaveable { mutableStateOf(activeCustomer.id) }` and inside the effect `if (lastCustomerId != activeCustomer.id) { reset the form; lastCustomerId = activeCustomer.id }`.
- Keep the reorder-prefill effect as is.

**G2.3 "Refundable" claim.** Deposit refundability is an open decision. Change:
- `"Held against a refundable deposit — you don't own it"` → `"Held against a security deposit — you don't own it"`.
- `"Security deposit — refundable per BLUE GAS policy (Q03). A deposit does not make you the owner."` → `"Security deposit — refund terms follow the BLUE GAS deposit policy (Q03). A deposit does not make you the owner."`

**G2.4 Reorder should keep the pickup depot.** In the prefill effect, if `p.mode == PICKUP` and `p.pickupPointId` is a depot in the selected address's city (`cityPickupNodes`), set `selectedPickupNodeId = p.pickupPointId`; otherwise leave the default.

**G2.5 The double-tap guard does nothing.** `placing` is set true and false in the same click handler, so it never takes effect. Delete `placing` and the progress spinner. Instead disable **Place order** while `showPaymentGatewayDialog` is true. (COD navigates away on success, so no extra guard is needed.)

**G2.6 Header wording.** In the shell header chip replace `"Non-Serviceable"` with `"Outside delivery area"`; `"Active Zone"` stays.

---

## G3 — `TrackingScreen.kt` and `ReceiptDialog.kt` wording

**G3.1 Payment card — never print a raw state name; settled COD must say paid.** Today `else -> "Online payment: ${latestPayment?.state?.name}"` can show text like `FINAL_CAPTURE_PENDING`, and a COD order that is **Settled** still says "Finance is reconciling it". Add one function in `TrackingScreen.kt` (and reuse it in `ReceiptDialog.kt` for the payment status) with this mapping; for any state not listed use `state.displayName`:

| Method | State | Customer text |
|---|---|---|
| COD | `SETTLED` | Paid. |
| COD | `CASH_COLLECTED` | Cash received — Finance is reconciling it. |
| COD | anything else | Cash on delivery — {total} due at handover. |
| Online | `SETTLED` | Paid. |
| Online | `INITIAL_AMOUNT_CAPTURED`, `CAPTURED`, and the post-capture states (`FINAL_AMOUNT_DETERMINED`, `NO_ADJUSTMENT`, `ADDITIONAL_COLLECTION_COMPLETED`, `PARTIAL_REFUND_COMPLETED`) | Paid online. |
| Online | `PREAUTHORIZED` | Payment authorised. The amount is taken after delivery. *(Authorised is not captured — do not say "Paid".)* |
| Online | failed / timed-out / failed-closed | Your last payment attempt didn't go through. |

In the receipt change the `"Settled"` label to `"Paid"` and `PREAUTHORIZED` to `"Authorised"`.

**G3.2 Cancelled orders show the wrong reason.** The hero subtitle and the stepper's terminal row read `exceptionDetails`, which is a staff-side field (it can hold text like "No active distributor serving node…"). Use `currentOrder.cancelReason ?: "This order was cancelled."` in both places.

**G3.3 Exception wording in customer language.** The hero and terminal row print `exceptionReason.description`, which is staff wording. Map it in one function and use it in both places; then add " Operations is working on it." (omit it for `UNRECOVERABLE_AFTER_RETRIES`):

| ExceptionReason | Customer sentence |
|---|---|
| VALIDATION_OUT_OF_AREA | We couldn't confirm your address or delivery area. |
| PAYMENT_ISSUE | There's a problem with your payment. |
| NO_ACCEPTANCE | We're finding a delivery partner for your order. |
| SERIAL_UNAVAILABLE_OR_PREP_FAILURE | Your order needs a little more time to prepare. |
| DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE | We couldn't complete the handover. |
| GOODS_REJECTED_AT_DOOR | The cylinder was not accepted at handover. |
| ADJUSTED_AMOUNT_REJECTION | The adjusted amount was not accepted. |
| UNRECOVERABLE_AFTER_RETRIES | We couldn't complete this order. |
| null | There's an issue with your order. |

**G3.4 Small wording fixes.**
- Tracking header subtitle `"Real-time dispatch & doorstep telemetry"` → `"Follow your order step by step"`.
- Stepper card title `"Order Lifecycle Progression"` → `"Order progress"`.
- `OrdersScreen.kt`: complaint option `"Damaged or Tampered Valve Seal"` → `"Cylinder condition problem"` (key `CYLINDER_CONDITION`). When the description box is empty, submit the selected option's label as the description (not an empty string).
- `ProfileScreen.kt` holdings: when the cylinder record is not found, **hide** the size chip instead of showing a made-up `"11.8 kg"`.
- `PaymentActions.kt` `DemoPaymentDialog`: stop reading `store.orders`. Take `payableLabel: String` (already formatted) and `orderId` as parameters; callers pass them from their `snapshot`.

**G3.5 Safety concern notifications.** `raiseSafetyConcern` sends two STAFF notifications and then `raiseIncident` sends its own, so staff get three alerts for one report. Remove the two notifications in `raiseSafetyConcern` and rely on the one from `raiseIncident`. Keep the `SupportCase` and the audit entry.

---

## G4 — Code comments (no behaviour change)

**Why:** the code must be readable by a developer who has never seen this prompt. Today comment density is 1–7 % per file and almost no function has a doc comment; many comments are bare tags such as `// F5 HomeScreen`, `// F1.2`, `// F2.8` that point to a document the next developer won't have.

**Standard to apply in every file under `ui/customer/`, plus `data/StoreCustomer.kt` and `data/StorePayments.kt`:**
1. **File header:** a `/** … */` block at the top: what the file is for, which screen or flow it implements, which store state it reads, which store functions it calls.
2. **Every top-level `fun`, `@Composable`, `class`, `data class`, `enum`:** a KDoc of one to three sentences — what it does, what the important parameters mean, and any side effect (for example "Calls `store.cancelOrder`; never mutates the order directly"). Where it implements a business rule, name it (L3 payment lifecycle, BR-PRC-05, deposit ≠ ownership, Q03 demo value).
3. **Non-obvious blocks get a "why" comment**, not a "what" comment. At minimum cover these:
   - `rememberNowMillis` (why a ticker, and that it only ticks while `enabled`);
   - `PaymentActionsRow` (a short decision table: active attempt → Pay now; no active attempt → Retry / COD);
   - the message-callback contract from G1 (`isError`, not success) on every callback parameter;
   - the Reorder prefill (why it is consumed once and cleared; why the address and depot are re-validated);
   - why pickup is forced for a non-serviceable address, and why pickup arrival only sends a notification;
   - the stepper ranking (`isStepCompleted`, `isStepCurrent`) and why terminal orders use the history;
   - the animated rider pin (progress over ~60 s from the Dispatched timestamp, simulated);
   - the receipt rows and the footing check (rows must add up to the total);
   - the payment-card mapping from G3.1 (including "authorised is not captured");
   - in the store: `cancelOrder` validates before it mutates; the payment-window guard; `raiseSafetyConcern` links to an L7 incident.
4. **Replace every tag-only comment** (`// F1.2`, `// F2.8`, `// F5.6`, "per F2.8", "per spec") with a real description, or delete it when the KDoc already says it. No comment may refer to "F", "S" or "G" section numbers.
5. **Do not** comment pure layout (`// Spacer`), do not leave commented-out code, and do not restate the code. Aim for roughly one comment line per 10–12 lines of logic; layout-only blocks need none.
6. Change **no** code in this section except comments. After the pass the app must behave exactly as after G3.

---

## G5 — Final self-check

**A. Search (case-insensitive) in `ui/customer`, report counts:**
`// *[FSG][0-9]|per spec|tamper|telemetry|refundable|PKR|TTL|bubble|leak test|inspected|turn off|evacuat|hands-free|simulate call|11\.8 kg"` — expect zero hits for everything except legitimate uses (a receipt "11.8 kg reference" row is fine).

**B. Every function has a KDoc:** list any `fun` in the files named in G4 that has no `/** */` directly above it. Expect an empty list.

**C. Walk-through (confirm each):**
1. Decline a payment from **Tracking**, **Orders** and **Home**: amber banner each time, then **Retry payment** → approve → green banner and the order is Confirmed.
2. Rate a completed order from Orders, Tracking and Profile: green banner; rate it again: amber banner.
3. Refill + "Add regulator & hose": the card says "+Rs 1,450" (the price-list value), the quote line, the order and the receipt show the same amount and the same total.
4. Choose 2 cylinders, Cash on delivery, tick the add-on → go to Orders → return to Home: choices are still there. Switch customer in Profile: Home resets once. Switch tabs again: no reset.
5. A settled COD order's payment card says "Paid."; a pre-authorised online order says "Payment authorised…"; no raw capital-letter state names appear anywhere.
6. Cancel an order yourself: the hero says "Order cancelled" with the reason you chose. Force an exception from Staff: the hero shows the customer sentence from G3.3, not staff wording.
7. Reorder a pickup order: the same depot is selected.
8. Submit a safety concern linked to an order: Staff see exactly one new notification.

When done, list per section what changed in one line each, and report the comment-density per file (comment lines ÷ total lines) before and after.
