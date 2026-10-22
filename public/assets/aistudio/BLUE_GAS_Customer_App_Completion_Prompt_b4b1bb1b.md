# BLUE GAS — Customer App Completion & Clean-up (single prompt for Google AI Studio)

**Project:** BLUE GAS Digital LPG Platform prototype (Kotlin + Jetpack Compose; one shared in-memory `BlueGasStore` behind the Customer App, Rider App and Staff Portal).
**Baseline:** the build exported on 20 Oct 2026. **Source documents:** BLUE GAS To-Be Concept v0.4, Leave Behind v0.4, Business Solution Overview.
**Scope of THIS prompt:** the **Customer App only** (`ui/customer/*` + the customer-facing store functions and shared helpers it needs). Do **not** change the Rider App or the Staff Portal screens except the two shared helpers named in S0. Rider and Staff have their own prompts.

Work S0 → S7 in order. No TODOs, no dead buttons, no fake toasts. If you run out of output, stop at the end of a step and write `CONTINUE FROM S<n>`.

---

## 0. Why this prompt exists (what is wrong today)

The Staff Portal was rebuilt in the last pass, but the **Customer App is still the old pre-v0.4 version**. Reading the Oct-20 code:

1. **The price the customer sees is not the price that gets created.** `HomeScreen` computes its own total from `priceBookConfig.productPricesPaisa` (11.8 kg ≈ Rs 3,250) and `taxRateBps`, while `store.createOrder(...)` prices the real order with `PricingEngine.buildQuote(...)` and **PriceList v1** (11.8 kg = Rs 4,450). So the "Confirm & Place Order (Rs …)" button, the quote card, and the order that appears in Orders/receipt **disagree**. This is the most visible demo bug.
2. **Invented claims are all over the customer UI** — forbidden by v0.4 §2.2. Exact strings below (S1).
3. **Developer jargon is shown to customers:** "Paisa Precision", "Demonstrator Control … order state machine transitions", "Idempotency: …".
4. **Money shows paisa decimals** (`formatPaisaToPkr` → "Rs 4,148.31") on customer screens; customers must see **whole rupees** (`formatRupees` → "Rs 4,148", BR-PRC-03).
5. **Dead buttons:** Profile "Add New Address" (`/* TODO */`), "Call" (`/* TODO */`), "WhatsApp" (`/* TODO */`); Orders receipt "Download PDF" only flips a flag and shows fake "saved to phone storage" text.
6. **UI mutates the store directly:** `notif.isRead = true; store.notifyChanged()` in Profile; same pattern in the notification centre. Reads also hit `store.xxx` directly in places instead of the collected `snapshot`.
7. **No customer store functions exist** for: add address, set default address, rate a completed order, mark a notification read.
8. **Out-of-area addresses only disable the button** — there is no "choose depot pickup" path (Q14).
9. **Fee and deposit values are hard-coded** in labels ("+PKR 300", `?: 750000L`, `?: 325000L`) instead of coming from the current price list / settings, and use the prefix "PKR" instead of "Rs".

Everything the store side needs already exists: `createOrder` already uses `PricingEngine.buildQuote` + `currentPriceList`; `PricingEngine.unitPriceExact/unitPricePublished/buildQuote`, `PriceList`, `PlatformSettings`, `formatRupees`, `formatPaisaToPkr` are all present. The Customer App just isn't using them.

---

## 1. Ground rules (apply to every change)

- **No invented facts, claims, or safety/regulatory statements.** Remove every one listed in S1. Do not replace them with a reworded claim. Undecided values show a small grey pill **`Demo value · Qnn`** (reuse the staff `DemoValuePill` if present, else a small grey `Surface`+`Text`).
- **No developer jargon on the Customer App.** No "Paisa", "idempotency", "state machine", "Demonstrator", "ORD_" codes. A simulated gateway result is allowed but only inside a clearly labelled small **"Demo"** area.
- **Money:** customer screens use `formatRupees(paisa)` ("Rs 4,148") everywhere. Never `formatPaisaToPkr` on a customer screen.
- **One source of price truth:** the customer preview and the created order must use the **same** current price list. Build the preview with `PricingEngine.buildQuote(...)` and `appState.currentPriceList` (same call `createOrder` makes), so the number on the button equals the order total. Never recompute prices from `priceBookConfig` in the UI.
- **Reactivity:** read everything in composables from the collected `snapshot` (`store.appState.collectAsStateWithLifecycle()`), never from `store.orders` / `store.notifications` / `store.cylinders` directly. Every write goes through a `BlueGasStore` function that ends in `notifyChanged()`. The UI never sets `notif.isRead`, `order.*`, or calls `notifyChanged()` itself.
- **Tabs:** keep the customer bottom-nav tab in the hoisted store state so it survives surface switches. Use `store.setCustomerTab(...)` / `snapshot.customerTab` (the field and setter already exist) instead of the local `var currentTab by remember { … }`. Deep-links from Home ("Track", "View orders") set the tab through the store.
- **Every button does something real.** Destructive/irreversible actions confirm first; actions needing a reason require it before confirm. Every list has an empty state; every dialog scrolls and uses `imePadding()`.
- Keep the phone-frame width, light theme, and `testTag`s. Package root `com.example.bluegas`; theme import `com.example.ui.theme`.

---

## S0 — Shared helpers the customer app needs (only these two shared edits)

1. In `ui/components/CommonComponents.kt` **NotificationCenterDialog**: the "Mark all read" action currently does `store.notifications.forEach { it.isRead = true }; store.notifyChanged()`. Replace with a store call: add `fun BlueGasStore.markNotificationsRead(surface: AppSurface, role: StaffSubRole? = null)` (if the Staff prompt has not already added it) that flips `isRead` on the matching notifications and calls `notifyChanged()` once; call it here with the active surface. Do not change staff behaviour.
2. Confirm `formatRupees` and `formatPaisaToPkr` exist in `model/Models.kt` (they do). Do not change them.

Everything else in this prompt is inside `ui/customer/` and new customer functions in `data/StoreCustomer.kt`.

---

## S1 — Remove invented claims and dev jargon (exact edits)

Delete or replace each of these. Replacements must be plain and true (no new claims).

**HomeScreen.kt**
- Hero card: remove the `"OGRA & PESSI COMPLIANT"` pill entirely. Change the hero title `"Order Certified Safe LPG"` → `"Order LPG in one tap"`. Replace the subtitle `"Hydro-tested cylinders with tamper-evident digital seal delivered in 45 mins."` → `"Clear pricing, doorstep delivery and live tracking."` (no time promise).
- Greeting subtitle `"Order safe, certified LPG delivered to your doorstep"` → `"LPG delivered to your doorstep"`.
- Section 1 helper `"All cylinders filled strictly according to calibrated weights & safety seals."` → `"Prices are shown before you confirm."`.
- Product row `"Certified Net Weight: X kg LPG"` → `"Cylinder size: X kg"`.
- Quote card: remove the `"Paisa Precision"` pill.
- Payment dialog: remove `"Idempotency: …"` and the `"Demonstrator Control: Simulate gateway authorization outcome to verify order state machine transitions:"` text. Replace the dialog's framing with a plain "Demo payment" note (S4).

**TrackingScreen.kt**
- `ORD_PREP` stage label `"Preparing & Hydro-Testing"` / subtitle `"Binding certified cylinder serial & verifying safety valve"` → title `"Preparing your order"`, subtitle `"The distributor is getting your cylinder ready."`.
- Remove `"Certified for PESSI Non-Technical Installations"`.
- Remove the `"OGRA Certified"` badge.
- Remove the block `"✓ Tamper-evident holographic neck seal intact\n✓ Calibrated tare weight verified\n✓ Pressure regulator joint inspection included"`. If a cylinder detail line is wanted, show only factual fields that exist on the model (allocated cylinder ID, size).

**ProfileScreen.kt**
- `"CNIC: … • NADRA Verified"` → `"CNIC: <masked>"` (keep masking; drop "NADRA Verified"), and remove the `contentDescription = "NADRA Verified"`.
- `"No open support disputes. Your safety is guaranteed."` → `"No open complaints."`.

**Rider/Staff files are out of scope here** — their invented claims are handled in their own prompts. Do not touch them.

After this step, a grep for `OGRA cert`, `PESSI`, `hydro`, `NADRA`, `holographic`, `tamper`, `45 min`, `guaranteed`, `legally mandated`, `Paisa Precision`, `Idempotency`, `state machine`, `Demonstrator` in `ui/customer/` must return nothing.

---

## S2 — One-tap ordering, priced from the real price list (HomeScreen.kt)

Rebuild the pricing section so the preview equals the created order.

- Remove the local price math (`price`, `depositPerUnit`, `subtotalPaisa`, `platformFeePaisa`, `deliveryFeePaisa`, `taxableBase`, `taxPaisa`, `estimatedTotalPaisa` from `priceBookConfig`). Instead compute a preview quote with the **same engine the store uses**:
  ```kotlin
  val quote = remember(selectedSku, quantity, selectedMode, orderType, includeDeposit, snapshot.currentPriceList, snapshot.taxConfig, snapshot.settings) {
      PricingEngine.buildQuote(
          orderType = orderType,
          sku = selectedSku,
          quantity = quantity,
          accessories = emptyList(),
          mode = selectedMode,
          priceList = snapshot.currentPriceList,
          tax = snapshot.taxConfig,
          settings = snapshot.settings,
          discountPaisa = 0L
      )
  }
  ```
- The **Itemized quote card** renders `quote.lines` in order with `formatRupees`, then Delivery fee (if delivery), Platform fee, Deposit (amber, with the note "Security deposit — refundable per BLUE GAS policy (Q03). A deposit does not make you the owner."), Discount (only if non-zero), GST (with its %), Further tax (only if `quote.furtherTaxPaisa > 0`), and **Total payable = `formatRupees(quote.totalAmountPaisa)`**. Footnote: *"Prices, fees and tax are demo settings pending client decisions (Q03, Q04, Q22, Q24)."*
- The Place Order button label uses `formatRupees(quote.totalAmountPaisa)` — the same number the order will carry.
- Fulfilment chips: read the delivery fee from `snapshot.currentPriceList` (don't hard-code "+PKR 300"); label `"Doorstep delivery (+Rs <fee>)"` and `"Depot pickup (free)"`. Use "Rs", never "PKR".
- **Order type (Q03/Q20):** add a small segmented control "What do you need?" with **Refill (empty exchange)**, **New cylinder**, **Accessory only**, driving an `orderType: OrderType`. For **New cylinder**, show the model from `snapshot.settings.newCylinderModel` (SOLD → "You will own the cylinder"; DEPOSIT → "Held against a refundable deposit — you don't own it"; LOAN → "Loaned to you") with a `Demo value · Q03` pill, and set `includeDeposit` accordingly (deposit only when DEPOSIT). For **Refill**, keep the "I'll hand back an empty BLUE GAS cylinder" toggle but drive it from `settings.refillModel` and the empty-exchange flag. Keep quantity 1..`settings.maxQuantityPerOrder`.

---

## S3 — Serviceability and depot pickup (Q14) (HomeScreen.kt)

- Replace the hard-coded serviceability text. Serviceable: `"This area is in the delivery zone."`. Out of area: `"This address is outside the current delivery area."` plus a **"Switch to depot pickup"** button that sets `selectedMode = PICKUP` and picks the nearest pickup point (`snapshot.nodes.firstOrNull { it.pickupEnabled && it.type != FulfilmentPointType.PLANT }`). When pickup is selected for an out-of-area address, enable the Place Order button and show the chosen pickup point's name and address.
- Keep "Change" address dropdown. (Add-address is handled in S5.)

---

## S4 — Payment handoff without jargon (HomeScreen.kt)

- **COD:** on place-order, keep calling the store (`createOrder` → then advance). Show a success snackbar "Order placed — pay cash on delivery." and navigate to Orders via `store.setCustomerTab("ORDERS")`.
- **Online:** keep the simulated gateway, but reframe the dialog as **"Demo payment"**: title "Pay now (demo)", body "This is a demo gateway for the pilot. Choose an outcome:", and three plain buttons **Payment approved** / **Payment declined** / **Connection lost**, each calling the existing `store.confirmPaymentAndAdvance(orderId, "SUCCESS"|"DECLINED"|"TIMEOUT")`. No "idempotency", no "state machine" text. On approved → snackbar "Payment received — order confirmed." On declined/lost → snackbar with the store's message and leave the order in its Payment-Pending state so Orders shows "Retry payment".

---

## S5 — Customer store functions (new file `data/StoreCustomer.kt`)

All are `fun BlueGasStore.xxx(...): Pair<Boolean, String>` unless noted, validate, write an audit entry, and call `notifyChanged()` once.

- `addAddress(customerId, label, street, area, city, zoneId, makeDefault: Boolean)` — resolves serviceability and the serving point from the zone (`serviceZones.find { it.id == zoneId }`); appends an `Address`; if `makeDefault`, clears the old default for that customer. Returns the new address id in the message.
- `setDefaultAddress(customerId, addressId)`.
- `rateOrder(orderId, stars: Int, comment: String)` — only `ORD_COMP` and not already rated; 1..5; sets `ratingStars` / `ratingComment` (fields already exist on `Order`).
- `markNotificationRead(notificationId)` — flips one notification's `isRead`.
Wire these into `BlueGasViewModel` as thin pass-throughs for consistency with the other actions.

---

## S6 — Wire the dead buttons and fix direct mutations (ProfileScreen.kt, OrdersScreen.kt)

- **Profile → Add New Address:** open an Add-address dialog (label, street, area, city dropdown, zone/area dropdown from `snapshot.serviceZones` of that city, "Set as default" switch). On save → `store.addAddress(...)`; show the serviceability result. Remove the `/* TODO */`.
- **Profile → Call:** `Intent(Intent.ACTION_DIAL, Uri.parse("tel:" + snapshot.settings.supportPhone))` via `LocalContext`. Label it from `settings.supportPhone`, not "0800-BLUEGAS".
- **Profile → WhatsApp:** this channel is Phase 3. Replace the dead button with a disabled chip labelled "WhatsApp (coming soon)" or remove it; do not fake it.
- **Profile → notifications:** replace `notif.isRead = true; store.notifyChanged()` with `store.markNotificationRead(notif.id)`.
- **Profile → ratings:** on a completed order (here or in Orders) add a "Rate your order" control (1–5 stars + optional comment) → `store.rateOrder(...)`, shown once.
- **Orders → receipt "Download PDF":** replace the fake flag with an Android **Share** intent (`ACTION_SEND`, `text/plain`) containing the receipt as text (order id, date, line items with `formatRupees`, fees, deposit, tax, total, and the demo footnote). Label it "Share receipt". Use `formatRupees` for every amount in the receipt (it currently uses `formatPaisaToPkr`).
- **Orders → Reorder:** ensure "Reorder" pre-fills Home (size, quantity, order type, address, mode, payment) and switches to the Home tab via `store.setCustomerTab("HOME")`, not just navigates.

---

## S7 — Formatting, reactivity and tab-state sweep (all customer files)

- Replace every `formatPaisaToPkr(...)` in `ui/customer/` with `formatRupees(...)`.
- Replace the local `var currentTab by remember { mutableStateOf(CustomerTab.HOME) }` in `CustomerAppScreen.kt` with `snapshot.customerTab` + `store.setCustomerTab(...)`; map the existing `CustomerTab` enum to/from the stored string. All in-app navigations (Home → Orders/Tracking, Reorder, etc.) go through the setter so the tab survives a surface switch.
- Any composable read of `store.orders`, `store.notifications`, `store.customers`, `store.addresses`, `store.deliveries` → read from `snapshot` instead.
- Remove the "PKR" prefix anywhere it still appears in customer text; use "Rs".

---

## Verification (do before reporting done)

Run the customer demo path end-to-end after **Reset demo data**:
1. Home: 11.8 kg refill, delivery, online. The quote card total **equals** the Place Order button amount **and** equals the order total shown in Orders and on the receipt (all in whole rupees, 11.8 kg = Rs 4,450 gas + fees + GST).
2. Switch size to 11 kg → total updates to the published 11 kg price (Rs 4,148) + fees.
3. New cylinder (deposit) → deposit line appears with the Q03 note; New cylinder (sold, if settings say so) → cylinder sale line, no deposit.
4. Out-of-area address → "Switch to depot pickup" enables checkout; delivery fee drops to 0.
5. Online → "Demo payment" dialog (no jargon) → Payment approved → order Confirmed; Payment declined → Orders shows Retry payment.
6. COD → order placed, advances, Orders shows it.
7. Profile: Add address (serviceability shown); Call opens the dialer with the support number; notification tap marks it read through the store; rate a completed order once.
8. Orders: Share receipt opens the share sheet with whole-rupee amounts; Reorder pre-fills Home and switches tab.
9. Switch Customer → Rider → Customer: the customer tab is where you left it (not reset to Home).
10. Grep `ui/customer/` for the banned strings in S1 → nothing; grep for `formatPaisaToPkr` and `priceBookConfig` in `ui/customer/` → nothing.

**Report format:** list S0–S7 as Done with the files changed, then the 10 verification results. If anything is not done, say exactly what and why — do not mark it done.
