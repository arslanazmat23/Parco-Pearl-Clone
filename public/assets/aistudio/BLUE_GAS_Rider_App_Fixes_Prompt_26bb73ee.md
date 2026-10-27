# BLUE GAS — Rider App Fix & Hardening Prompt (Round R)

**Target:** the current project in AI Studio (Kotlin / Jetpack Compose, three surfaces on one in-memory `BlueGasStore`).
**Scope of this round:** the **Rider app** (`ui/rider/*`), the **host shell** (`MainActivity`, `ui/components/CommonComponents.kt`), the rider-facing **store functions** (`data/StoreFulfilment.kt`), plus a short list of customer-app leftovers (R0).
**Out of scope:** the Staff Portal (next round). Do not redesign it. Do not touch the frozen order state machine (19 statuses / 42 transitions) or the payment state machine's transition table.

The customer app passed its last review. Do **not** change its behaviour. Every item below was found by reading the current code. Do all of it, in order. If you run out of room, stop at a section boundary and say "Stopped after R<n>". The user will reply "Continue from R<n+1>".

---

## 0. Ground rules (apply to every section)

1. **No invented content.** No fake numbers, fake legal claims, fake safety procedures, fake serials, fake timestamps. If a value is not in the store or in `PlatformSettings`, either read it from there or show nothing. Where a demo value is unavoidable, show it with the existing `DemoValuePill`.
2. **No raw enum or internal text in the UI.** Never show `ORD_ASGN`, `ORD_EXC`, `CASH_ON_DELIVERY`, `PICKUP`, `.name` of any enum, or a status code to a user. Use plain words ("Assigned", "Cash on delivery", "Pickup", "Needs attention").
3. **UI never mutates the store.** The UI must not assign store fields or call `notifyChanged()`. Every write goes through a store extension function (which ends in `notifyChanged()`) or a `BlueGasViewModel` method.
4. **Read from the snapshot.** Screens read `val appState by store.appState.collectAsStateWithLifecycle()` and derive lists from it. Do not read `store.orders`, `store.riders`, `store.cylinders` etc. directly inside composables.
5. **Message callbacks are `(message, isError)`.** Same contract as the customer app: success = green, error = amber/red, auto-dismiss after about 4 seconds.
6. **Money** is integer paisa (`Long`). Display with `formatRupees` ("Rs 4,148"). Replace every `formatPaisaToPkr` call inside `ui/rider/*` with `formatRupees`.
7. **Never invent emergency or containment procedures (L7).** The app may say "Report a safety concern" and that HSE has been alerted. It must not tell the rider or customer what to physically do beyond "follow the approved BLUE GAS HSE procedure".
8. **Open decisions stay configurable:** installation checklist (Q27, `settings.installationChecklistItems`), cash handover hours (Q28, `settings.cashHandoverHours`), refill mechanics (Q20), deposit refundability (Q03). Do not hardcode their values in UI text.
9. **Comments:** every new or changed file gets a KDoc header (what it is, which screen/flow, which store functions it calls). Every non-obvious branch gets a *why* comment. No tag-only comments such as `// Q27` or `// BR-CSH-01` with nothing else.
10. **Dialogs and long forms scroll.** Any dialog or sheet whose content can exceed a small phone height uses `verticalScroll(rememberScrollState())` on the body with the action buttons pinned outside the scroll area.

---

## R0. Customer-app leftovers and the host-level state loss

### R0.1 Surface switching resets everything (most important)
`MainActivity.BlueGasAppRoot` switches surfaces with a plain `when (appState.activeSurface) { … }`. Compose discards all `remember` / `rememberSaveable` state of the surface that leaves the composition. That is why tabs, search text, selected tracking order and half-filled forms reset when the presenter switches Customer → Rider → Staff and back.

Fix:
- In `BlueGasAppRoot` create `val stateHolder = rememberSaveableStateHolder()`.
- Wrap each branch: `stateHolder.SaveableStateProvider(key = surface.name) { CustomerAppScreen(store) }` (same for Rider and Staff).
- Inside each surface, any state that must survive a surface switch must use `rememberSaveable`, not `remember`. At minimum:
  - Rider: `selectedTab` (use `rememberSaveable { mutableStateOf(RiderTab.PENDING) }`; the enum is saveable by name via the default `Saver` only if you store `.name`; store the name string and map it back, or use `listSaver`).
  - Customer: `selectedTrackingOrderId`, the Orders filter and the Orders search text.
- Add a one-line KDoc above the provider block explaining why it exists.

### R0.2 Surface switching and notification bell go through the ViewModel
`ui/components/CommonComponents.kt` writes `store.activeSurface = …; store.notifyChanged()` at about lines 245–246, 255–256, 265–266, calls `store.notifyChanged()` at about line 377, and calls `markNotificationsRead` directly at about line 499.
- Replace the surface writes with `viewModel.switchSurface(surface)` (it already exists in `BlueGasViewModel`). Pass the ViewModel or a `(AppSurface) -> Unit` lambda into the component instead of the store.
- Replace the other direct calls with ViewModel/store-extension calls that end in `notifyChanged()` inside the store, not in the UI.
- Add `BlueGasViewModel.setRiderOffline(offline: Boolean)` that sets the flag inside a store extension `setRiderOffline(...)` and calls `notifyChanged()` there.

### R0.3 Customer cosmetic leftovers
- `ProfileScreen.kt` `maskCnic`: seed CNICs are dashed ("37405-8899001-1"), so the current code returns "37405-8XXXXX1". Replace with a digit-based mask: strip non-digits; if fewer than 13 digits return the input unchanged; otherwise show the first 5 digits, then `-XXXXXXX-`, then the last digit → **"37405-XXXXXXX-1"**.
- `RateOrderDialog.kt` header KDoc: replace `[Order.ratingFeedback]` with `[Order.ratingComment]` (the model field is `ratingComment`).
- `PricingEngine.buildQuote`: remove the `?: 145000L` accessory price fallback. If an accessory has no price in `priceBookConfig`, skip that line and do not crash.
- `ui/staff/StaffCommercialScreen.kt` line ~137: change "Deposit Liabilities (Refundable):" to "Deposit Liabilities:" (refundability is open decision Q03).
- Re-read the KDoc of `TrackingScreen.kt`, `CustomerShared.kt`, `OrdersScreen.kt` and delete any sentence that describes a feature the code does not implement.

---

## R1. Rider home screen (`RiderAppScreen.kt`)

### R1.1 State and data
- Use `appState` for riders, orders, deliveries, cylinders, customers, addresses, price book. Remove direct `store.*` list reads from composables.
- The offline switch (lines ~209–212) calls `viewModel.setRiderOffline(it)`; no direct field write.
- `selectedTab` is hoisted and `rememberSaveable` (R0.1).
- Derive the rider's orders once per snapshot with `remember(appState)` and name the predicates (`isPendingForRider`, `isActiveForRider`, `isCompletedForRider`) in a small private helper file section with KDoc.

### R1.2 Tab coverage (orders must never vanish)
Today `activeOrders` omits `ORD_INSP`, `ORD_ADJ` and `ORD_EXC`, so those orders appear on no tab. Define:
- **Pending:** assigned to this rider, status `ORD_ASGN`, rider task not yet accepted.
- **Active:** assigned to this rider and status is any non-terminal status after `ORD_ASGN` (accepted, preparing, dispatched / ready for pickup, at handover, inspection pending, adjustment pending, exception). Use the real `OrderStatus` enum names from `Models.kt`; do not list statuses by hand if `isTerminal` and `ordinal` can express it, and make sure `ORD_INSP`, `ORD_ADJ` and `ORD_EXC` are included.
- **Completed:** `ORD_COMP`.
- An order in `ORD_EXC` shows on Active with an amber "Needs attention — Operations has been alerted" strip and **no** rider action buttons except "View details". An order in `ORD_INSP` or `ORD_ADJ` shows "Waiting for the inspection / price adjustment to be completed" and no rider completion button (the rider cannot approve inspection — separation of duties).
- Add a debug-free invariant: the sum of the three tab lists must equal the rider's orders. Do not add UI for it; add a KDoc note.

### R1.3 Header stats
- Remove `todayEarningsPaisa = completedOrders.size * 25000L` (invented Rs 250 per order). Replace the earnings tile with **"Cash to hand over"**: sum of `amountReceivedPaisa ?: totalAmountPaisa` over this rider's COD orders in `ORD_COMP` whose payment state is `CASH_COLLECTED` and that are not in any `cashHandovers` entry. Show it with `formatRupees`. Tapping it opens the cash hand-over dialog (R5.3).
- "Delivered Today" must count only orders whose `completedAt` is on today's calendar date in the device time zone. If `completedAt` is null, do not count it. Label the other tile "Delivered (all)" if you keep a lifetime count, or remove it.
- Add the zone line as plain text: "${zoneName} · On shift / Off shift" (map `ACTIVE`/`INACTIVE`). Never print the raw status.

### R1.4 Empty states (plain words)
- Pending empty: "No new deliveries assigned to you right now."
- Active empty: "No deliveries in progress."
- Completed empty: "No completed deliveries yet."
Never mention `ORD_ASGN` or any code.

### R1.5 Banner
One `BannerMsg`-style banner at the top of the tab content. Success = green, error = amber, auto-dismiss at 4 seconds via `LaunchedEffect(message)`. Today success is amber and error red — fix that.

---

## R2. Pending card, acceptance countdown, decline

### R2.1 Pending card (`PendingOrderCard`)
- Remove the hard-coded "Assigned 5m ago". If the order has an assignment timestamp, show "Assigned HH:mm" from it. If the model has no such field, show nothing. (Do not add a fake.)
- Show: order number, customer first name + area (not the full address until accepted), items via `order.customerVisibleItems()`-style plain lines (gas size × qty; do not show DEPOSIT lines to the rider as products), payment as "Cash on delivery — collect Rs X" or "Paid online", fulfilment as "Home delivery" / "Pickup".
- Countdown: use the existing `rememberNowMillis` / `formatCountdown` helpers from `CustomerShared.kt` (move them to a neutral shared file such as `ui/components/TimeHelpers.kt` if needed, update imports). When the window reaches 0:
  - show "Acceptance time is over" in amber,
  - **disable the Accept button** and keep Decline enabled,
  - do not change store state from the UI. The store's `processTimers()` already handles expiry.

### R2.2 Decline dialog
- The current text claims the order moves to a Fulfilment Exception. It does not: `riderDeclineTask` sets the rider task to DECLINED, clears the assigned rider and notifies the Distributor. Rewrite the copy to exactly that: "This delivery will go back to your distributor to assign to another rider."
- Reason: a short list of radio choices (Too far, Vehicle problem, Off shift, Other) plus a free-text box that is **empty by default** and only required for Other. Pass the chosen reason text to `riderDeclineOrder`.
- Dialog body scrolls (ground rule 10).

---

## R3. Active card (`ActiveOrderCard`) and actions

### R3.1 Information
Show, in this order: order number and a plain status chip; customer name; tap-to-expand address with the area and landmark; a "Call customer" button; a "Navigate" button; items; payment line (as in R2.1); handover PIN reminder text "Ask the customer for their 4-digit handover code" (never display the code itself on the rider side).
- "Call customer" and "Navigate": if the app has no real intent wiring, implement `ACTION_DIAL` with the customer's phone and a `geo:` intent from the address coordinates using `LocalContext`. Wrap in try/catch and report "No app available" via the banner on failure. If coordinates/phone are missing, disable the button and show a one-line reason.

### R3.2 Start Delivery
- When disabled, show the reason under the button in muted text: "Choose the cylinder to deliver first." / "You are offline — this will be saved and sent later." (offline does not disable it; it queues).
- Remove `Mode:` / `Payment:` lines that print `.name` (ground rule 2).

### R3.3 Serial picker
- Filter available cylinders by **SKU**, **status = filled & available**, **not on hold (`hseHold == false`)** and **`pointId == order.fulfilmentPointId`** (the order's own node). Today it ignores `pointId`, so a rider can pick a cylinder from another depot.
- Remove "Hydro-test: ${lastInspectionDate}" and the "No hydro-tested cylinders…" text. Empty state: "No suitable cylinder is available at this depot. Ask Operations." with a "Report a problem" button that opens the issue dialog with reason `SERIAL_UNAVAILABLE_OR_PREP_FAILURE`.
- Header copy: "Choose the cylinder you are loading" (remove "assigned terminal").
- On selection call `riderSelectAndBindSerial`, show the result in the banner.

### R3.4 Awaiting finalisation text
Replace "Awaiting system finalization to ORD_COMP" with "Delivery is being finalised." (no code).

---

## R4. Report-a-problem dialog

- Reasons: list the `ExceptionReason` values a rider can legitimately raise (at least `DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE`, `SERIAL_UNAVAILABLE_OR_PREP_FAILURE`) using `reason.description`, never `.name`.
- Details text box is **empty** every time the dialog opens (`remember(orderId, openCount)`); remove the default "Customer unavailable after 3 call attempts." — that is an invented fact.
- Add a checkbox **"This is a safety concern (for example a smell of gas, a leak, or damage)"**. Do not show any instructions beyond: "HSE will be alerted. Follow the approved BLUE GAS HSE procedure."
- Store change: add `safetyConcern: Boolean = false` to `riderReportIssue`. Escalate to `raiseIncident` when `safetyConcern` is true **or** the existing keyword match fires. Pass `safetyConcern` through the offline outbox (`handoverPayload = mapOf("safetyConcern" to "true")`) and read it in `replayRiderOutbox`.
- Dialog scrolls; Submit disabled until the details are at least 5 characters (except when `safetyConcern` is ticked).

---

## R5. Proof-of-delivery flow (`PodCaptureDialog.kt`) — biggest change

Today the dialog only passes the legacy `completeHandover` parameters, so the richer ones (`checklistAnswers`, `installationResult`, `notInstalledReason`, `pinVerified`, `signatureUnavailableReason`, `evidenceConsent`, `cashDifferenceReason`, `amountReceivedPaisa`) are never set. It also contains a lot of invented legal and safety text. Rebuild the flow with these steps (Next/Back, a step indicator, scrollable body, pinned action bar, not dismissible by outside tap, close via X with a "Discard progress?" confirmation):

### R5.1 Steps
1. **Handover code (PIN).** Four digit boxes. The rider types the code the customer reads out. Compare with `order.handoverPin` through a store function `verifyHandoverPin(orderId, entered): Boolean` (no PIN logic in the UI). 3 wrong attempts → show "Code not accepted" and unlock the secondary option **"Code not available"**, which requires a reason (customer has no phone / customer unreachable / other). Result: `pinVerified = true/false` and the reason stored in `signatureUnavailableReason`-style notes (add a `pinNotVerifiedReason` field on `Delivery` if none exists). A rider **cannot** skip the step silently.
2. **Installation.** Three choices: *Installed*, *Not installed* (reason required, free text, empty by default), *Not applicable*. If *Installed*, show the checklist items **from `settings.installationChecklistItems`** (read through `appState`), each as a checkbox; all must be ticked to continue. If *Not installed* or *Not applicable*, no checklist is required. Pass `installationResult`, `notInstalledReason` and `checklistAnswers = items.associateWith { ticked }`.
   - Heading: "Installation". Sub-text: "Complete the steps your company has approved." Remove all of: "OGRA Non-Technical Installation Standards", "legally mandated", soapy-water / bubble-test wording, tamper-seal wording, jubilee-clip wording, and the four hard-coded checks (`leakCheckPassed`, `valveIntact`, `ventilatedArea`, `hoseConnectionSecured`).
3. **Empty cylinder** — show **only** when `order.orderType == OrderType.REFILL_EXCHANGE`. Toggle "Empty cylinder collected"; when on, brand dropdown from `settings.approvedEmptyBrands` (default BLUE GAS, plus an "Other brand" option that the store declines) and an **empty-by-default** serial box (remove the default "BG-LHR-OLD-812"). Pass `emptyCollected`, `emptyBrand`, `emptySerial`. Refill mechanics are open decision Q20, so keep the step conditional and the copy neutral.
4. **Payment** — show only for COD.
   - Show amount due (`formatRupees(order.totalAmountPaisa)`), a "Cash received" amount field (default = amount due, editable, whole rupees), live "Change to return" when over, and "Short by" when under.
   - If received ≠ due, a **reason is required** ("Cash difference reason", empty by default). Pass `amountReceivedPaisa = received`, `changeReturnedPaisa`, `cashDifferenceReason`. **Stop always passing `collectedCashPaisa = order.totalAmountPaisa`** — it hides short and over collections.
   - Checkbox "I have received this cash" must be ticked to continue.
   - Online-paid orders skip this step and show "Paid online — nothing to collect."
5. **Photo (optional by default).** Button "Take delivery photo" with a placeholder capture (prototype). Allow "Skip" with a short reason. Copy: "Photo of the delivered cylinder at the door." Remove "Geotagged", "OGRA", "watermark", "COMPLIANT". Pass `photoCaptured` truthfully (false when skipped) and `evidenceConsent` from a consent checkbox: "The customer agrees to a delivery photo". If consent is not ticked the capture button is disabled.
6. **Signature.** Draw-to-sign canvas or "Customer cannot sign" with required reason (`signatureUnavailableReason`). Keep the existing adopted-signature/touch flow if it works, but `customerSignature` must hold the real signer name or null — not the literal "Touch Verified". Pass `signatureCaptured` truthfully.
7. **Review & complete.** One summary card: PIN verified yes/no, installation result, empty cylinder, cash received/difference, photo yes/no, signature yes/no. Button **"Complete delivery"**. After tapping it, disable the button and show a progress state until the store returns, then close the dialog and show the banner message on the Rider screen.

### R5.2 Store changes for `completeHandover`
- **Do not default the evidence flags to true.** Change defaults: `pinVerified = false`, `photoCaptured = false`, `signatureCaptured = false`, `evidenceConsent = false`. Every existing caller (rider dialog, counter path, outbox replay, seed/demo helpers) must pass them explicitly. Search for all call sites; list them in your final summary.
- Remove the duplicate legacy parameters (`collectedCashPaisa`, `emptyCylinderCollected`, `emptyCylinderSerial`, `photoEvidenceCaptured`, `customerSignature`, `nonTechInstallationPassed`) once all callers use the new ones. Keep `proofOfDeliveryNote` and `actor`. If you cannot remove them safely in this round, mark them `@Deprecated` and do not use them in new code.
- Fix the empty-cylinder bug: `order.emptyCollectedCylinderId = emptySerial` must use the resolved serial (`actualEmptySerial` today), not the raw parameter.
- If `pinVerified` is false and no reason is supplied, return `Pair(false, "A reason is needed when the handover code was not checked.")`.
- If installation is `NOT_INSTALLED` and no reason is supplied, return an error likewise.
- `Delivery.nonTechnicalInstallationPassed` = `installationResult == INSTALLED && every checklist item true`.
- `proofOfDeliveryNote` default text must be neutral: "Delivered. Installation: <plain word>."

### R5.3 COD must not settle at the rider (L3 invariant) — and the cash hand-over must work
Today, `completeHandover` for COD walks the payment `FINAL_AMOUNT_DETERMINED → CASH_COLLECTION_PENDING → CASH_COLLECTED → SETTLED` and posts the COD ledger line immediately. Under BRD L3 a COD payment settles **only after Finance reconciles the cash**. Because of this bug, `riderSubmitCashHandover` (which looks for `CASH_COLLECTED`) never finds a rider's orders, and the rider app has no way to hand cash over at all. The counter-pickup path (`completeCounterHandover`) already does it correctly — copy its approach.

Fix in `completeHandover`:
- For COD, stop the payment at `CASH_COLLECTED`. Do **not** transition to `SETTLED`. Do **not** set `settledAmountPaisa`. Do **not** call `postLedger(… "COD_COLLECTION" …)` here; `reconcileCashHandover` already posts `SETTLEMENT` and the short/over line when Finance confirms. Keep `capturedAmountPaisa = rec`.
- Keep creating the `CodCollection` record and the immediate `EXC-21` only if you decide that is still wanted; since `reconcileCashHandover` raises EXC-21 on counting differences, **keep the rider-side short-collection exception but word it as "Cash received differs from the amount due"**, and use `formatRupees` instead of `formatPaisaToPkr`.
- The customer-side wording for `CASH_COLLECTED` already exists ("Cash received — Finance is reconciling it."); confirm the customer receipt shows "Cash collected" and not "Paid" for this state.

New UI — **Cash hand-over** (Rider app):
- The "Cash to hand over" tile (R1.3) opens a dialog listing the orders in the pending cash total with amounts, a "Hand over to" dropdown (Distributor / Depot cashier), and a "Declared amount" field defaulting to the expected total. If declared ≠ expected, require a reason. Call `riderSubmitCashHandover(riderId, recipient, declaredPaisa)` (add an optional `reason` parameter and store it on `CashHandover`).
- Show a note using `settings.cashHandoverHours`: "Hand over your cash within ${hours} hours." Do not hardcode 24.
- Disable the button when there is nothing to hand over; show "No cash waiting to be handed over."
- After success, the tile drops to Rs 0 and a Finance notification (`NTF-15`) already exists in the store.

### R5.4 Offline completion keeps its data
`completeHandover` currently queues an outbox event with **no payload**, so a replay completes with defaults. Fix:
- Store all captured inputs in `OfflineOutboxEvent.handoverPayload` (string map): `pinVerified`, `pinReason`, `installationResult`, `notInstalledReason`, `checklist` (join as `item=true;item2=false`), `emptyCollected`, `emptyBrand`, `emptySerial`, `amountReceivedPaisa`, `changeReturnedPaisa`, `cashDifferenceReason`, `photoCaptured`, `evidenceConsent`, `signatureCaptured`, `signatureUnavailableReason`, `signerName`.
- In `replayRiderOutbox` `"COMPLETE_HANDOVER"`, rebuild the call from the payload (write a small `parseHandoverPayload` helper with KDoc and a unit-testable signature) and pass `isReplay = true`. Keep the existing serial-collision check.
- `simulateSerialConflict` hard-codes `ORD-SEED-105` and serial `BG-LHR-118-002`. Keep it as an explicitly labelled demo control, but guard: if that order or serial does not exist return "Demo order not found" instead of crashing or queuing garbage.

---

## R6. Completed tab and receipt

- Delete the rider-side receipt dialog (about lines 833–908). It prints invented content ("Tax (5%)", "Total Settled", "Bubble leak test PASSED. Valve seal intact.", "OGRA Doorstep Photo Geotagged & Verified", a "POD-VERIFIED" fallback), omits discounts and uses the wrong formatter.
- Reuse the customer `ReceiptDialog` (same composable, same data), opened from the completed card via "View receipt". If the dialog's current signature is customer-specific, extract the shared body into `ui/components/` and keep the customer dialog as a thin wrapper — do not copy the arithmetic.
- `CompletedOrderCard`: show order number, customer name, completed time (`completedAt` formatted "dd MMM, HH:mm"), plain payment line ("Cash collected Rs X" / "Paid online"), installation result in plain words, and the POD reference. Remove "Settled: … • CASH_ON_DELIVERY" and the static "Delivered Today" label. Cash orders awaiting Finance show a small chip "Cash not yet handed over" or "Cash handed over — awaiting Finance", derived from `cashHandovers`.

---

## R7. Offline mode and outbox panel

- Offline banner text: "You are offline. Actions are saved on this phone and sent when you are back online."
- Outbox list: show human labels for actions ("Start trip", "Arrived", "Report problem", "Complete delivery"), the order number, time, and a status chip in plain words: Waiting / Sent / Duplicate ignored / Rejected / Needs Operations review. Rejected rows show `rejectionReason`.
- "Sync now" is disabled and explains why when the outbox is empty. Result goes to the banner (success green, partial amber).
- Demo controls (Sync, Demo Conflict) live in one collapsible "Demo controls" card clearly labelled **Demo only** with `DemoValuePill`. They must not look like production buttons.
- Toggling offline/online must not reset the selected tab (R0.1).

---

## R8. Quality pass, comments, self-check

### R8.1 Layout polish
- Every card uses consistent 16dp padding, 12dp corner radius, text with `maxLines` and `overflow = Ellipsis` where a long name or address could overflow, and `weight(1f)` on the flexible side of any `Row` containing a long text and a chip/button.
- Buttons never wrap onto two lines at 360dp width; use `FlowRow` or a column where needed.
- No nested vertical scroll containers (a `LazyColumn` inside a `verticalScroll`).
- Bottom bar / FAB never covers the last card: add bottom content padding equal to the bar height.

### R8.2 Documentation
- File-level KDoc on: `RiderAppScreen.kt`, `PodCaptureDialog.kt`, any new file. State what the screen does, its inputs, and the store functions it calls.
- Public composables and store functions changed in this round get KDoc describing parameters, side effects and failure results.
- Remove any comment describing behaviour that no longer exists.

### R8.3 Self-check (run these before you reply, and paste the results)
1. Search all of `ui/rider/` and `ui/components/` for these strings and confirm **zero** matches: `OGRA`, `legally`, `soapy`, `bubble`, `tamper`, `jubilee`, `geotag`, `watermark`, `hydro`, `Hydro`, `Tax (5%)`, `Total Settled`, `POD-VERIFIED`, `BG-LHR-OLD`, `3 call attempts`, `5m ago`, `ORD_`, `.name}` (any enum printed with `.name`), `formatPaisaToPkr`, `25000L`.
2. Search `ui/` for `notifyChanged()` and for assignments of the form `store.<field> =`; confirm none remain.
3. Confirm every `completeHandover(` call site passes the evidence flags explicitly.
4. Confirm `grep -n "SETTLED" data/StoreFulfilment.kt` shows no COD transition inside `completeHandover`.
5. List every file you changed.

### R8.4 Walkthrough you must be able to pass (describe the result of each step in your final message)
1. Customer: place a COD order for a 11.8 kg refill. Note the handover code on the tracking screen.
2. Distributor/Staff flow is unchanged — assign the order to Rider 1.
3. Switch to **Rider**. The new order is on **Pending** with a live countdown. Switch to **Customer** and back to **Rider**: the Pending tab is still selected. Switch to Staff and back: still selected.
4. Accept. Order moves to **Active**. Start preparing, choose a cylinder (only ones from this depot appear), Start Delivery.
5. Go offline, press Arrived, go online, Sync: the outbox shows plain-word rows and the order advances.
6. Open Complete delivery: enter a wrong code three times → "Code not accepted" → "Code not available" requires a reason. Then enter the right code instead.
7. Choose *Installed*: only the three settings checklist items appear. Tick them. Skip the empty-cylinder step (refill only appears for refill-exchange orders — confirm it appears for a REFILL_EXCHANGE order and not otherwise).
8. COD: enter received Rs less than due → reason required. Enter exact amount → no reason.
9. Complete. The order is on **Completed**; the rider tile "Cash to hand over" shows the amount; the customer tracking screen says delivered; the customer receipt says "Cash collected" and Finance is reconciling.
10. Tap the tile, hand over the cash. Switch to Staff → Finance: a pending cash hand-over appears; reconcile it; the payment becomes Settled; the customer receipt now says paid/settled.
11. Report a problem on another active order with the safety box ticked: Staff gets the alert and an HSE incident; the order shows "Needs attention" on the Rider Active tab with no rider action buttons.

If any step fails in your reading of the code, fix it and say what you fixed.

---

## Final message format
Reply with: (1) sections completed, (2) files changed, (3) the self-check output, (4) anything you could not do and why. Do not claim the app compiles or runs unless you actually ran it.
