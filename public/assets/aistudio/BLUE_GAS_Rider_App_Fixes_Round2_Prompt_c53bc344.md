# BLUE GAS — Rider App Round 2: Workflow Realignment & Polish (S1–S7)

**Target:** the current AI Studio project (Kotlin / Jetpack Compose, one in-memory `BlueGasStore`, three surfaces).
**Scope:** Rider app (`ui/rider/*`), rider-facing store functions (`data/StoreFulfilment.kt`, `data/StoreOrders.kt`, `data/SeedData.kt`, `model/*`), and **one small control in the Staff Distributor screen** (S1.6) that is needed so an order can reach a rider at all.
**Do not touch:** the frozen order state machine (19 statuses / 42 transitions), the payment state machine's transition table, the customer app's behaviour, or any other Staff Portal screen.

Round R1 (the previous prompt) is mostly done and good: surface-switch state is preserved, the PIN / installation / cash steps exist, COD now stops at *Cash Collected*, the cash hand-over works, banned text is gone. **Keep all of that.** This round fixes what that round exposed: the rider workflow does not match the agreed spec (v0.4 §3.5, RACI-05/06/07), so a live order never reaches a rider.

If you run out of room, stop at a section boundary and say "Stopped after S<n>". The user will reply "Continue from S<n+1>".

---

## 0. Ground rules (unchanged, still apply)

1. No invented content: no fake numbers, legal claims, safety procedures, serials or timestamps. Unavoidable demo values use `DemoValuePill`.
2. No raw enum names, status codes or internal ids as *labels* in the UI (order numbers like `ORD-1042` are fine).
3. The UI never mutates the store or calls `notifyChanged()`; it calls store extension functions or `BlueGasViewModel` methods.
4. Screens read from `appState`. Callbacks are `(message, isError)`; success green, error amber; banners auto-dismiss at 4 s.
5. Money is integer paisa displayed with `formatRupees`.
6. Never invent emergency procedures (L7). Say "HSE has been alerted. Follow the approved BLUE GAS HSE procedure."
7. Open decisions stay configurable (installation checklist Q27, cash-handover hours Q28, refill mechanics Q20, cylinder ID mode Q10). New numeric settings go in `PlatformSettings` and are editable in the existing Admin settings screen.
8. Every new or changed function gets KDoc (purpose, parameters, side effects, failure results). Every non-obvious branch gets a *why* comment.
9. Dialogs scroll; action buttons stay pinned.

---

## S1. Realign the rider workflow to the agreed spec  ← most important

### What the spec says (v0.4 §3.5, RACI-05/06/07; L1, L4)
1. The **Distributor** accepts the order, starts **Preparing**, and **allocates the exact cylinder** (RACI-05). Serial allocation is *not* a rider action.
2. After allocation, the Distributor **assigns a rider** (`assignRider`: order is in *Preparing*, cylinder allocated). The rider's task becomes `OFFERED`.
3. The rider **accepts or declines the task** (`riderAcceptTask` / `riderDeclineTask`). This is **task-level only: the order status does not change.**
4. The rider **verifies the cylinder** (`riderVerifyCylinder`): the scanned/entered ID must equal the allocated one (Option B "untagged" excepted).
5. **Start trip** (`riderStartTrip`) requires: task *Accepted* **and** cylinder verified. It moves Preparing → Dispatched, custody In-Transit, creates the `Delivery`, notifies the customer.
6. **Arrived** → At Handover. **Complete delivery** → Handover Complete → Completed.
7. **Report a problem** is available when the order is *Dispatched* or *At Handover* only.

### What the code does today (all verified by reading)
- `assignRider` is never called from any screen, and the rider app expects orders in **Assigned** status. So a freshly placed order can never appear for a rider; only seed orders (which are set up the old way) show up.
- The rider app has its own **Begin Preparation** and **Choose cylinder** steps. Those belong to the Distributor. Both roles do the same job today.
- `riderAcceptTask` changes order status (Assigned → Accepted). The spec says it must not.
- `riderStartTrip` checks neither the task status nor cylinder verification; `riderVerifyCylinder` exists but nothing calls it.
- The Staff Distributor screen has a **"Mark Dispatched with Rider"** button that starts the trip *for* the rider, with no rider assigned.
- The acceptance countdown counts from `assignedAt` (the time the order was assigned to the *distributor*) using the *distributor* window, so a normal demo shows "Acceptance time is over" before the rider has even been offered the job.
- `ORD-SEED-105` is Assigned with a rider already set; `ORD-SEED-106` is Accepted with a rider and no cylinder. Both contradict the spec. The seeds also pre-create `Delivery` rows that `riderStartTrip` then duplicates.

### S1.1 Model and settings
- `Order`: add `var riderOfferedAt: Long? = null` and `var riderCylinderVerified: Boolean = false`.
- `PlatformSettings`: add `riderAcceptanceWindowMins: Int = 10` with a comment that it is a configurable demo default, and expose it in `StaffAdminScreen` next to the distributor window (validate 1..240 in `StoreAdmin`, include it in the change log line). Do **not** reuse `distributorAcceptanceWindowMins` for riders.

### S1.2 Store: `assignRider`
Keep the existing rules (status *Preparing*, cylinder allocated) and add:
- rider must be approved (`onboardingStatus == "APPROVED"`), on shift (`status == "ACTIVE"`), installation-trained, and linked to the order's distributor (`rider.distributorId == order.assignedDistributorId`). Each failure returns a plain-word error ("Rider is off shift.", "Rider is not trained for installation.", "Rider belongs to another distributor.").
- set `order.riderOfferedAt = now`, `order.riderCylinderVerified = false`, `order.riderTaskStatus = OFFERED`; keep the NTF-03 notification to the Rider; add `logAudit`.
- Add a KDoc stating the preconditions.

### S1.3 Store: rider task functions
- `riderAcceptTask`: validate the order is assigned to the active rider, status is *Preparing*, task is `OFFERED`, and the offer has not expired (`now - riderOfferedAt <= settings.riderAcceptanceWindowMins` minutes). Set `ACCEPTED`. **Remove the Assigned → Accepted transition** (no order status change). Audit it. Do not set the task status before validation succeeds.
- `riderDeclineTask`: validate task is `OFFERED`. Set `DECLINED`, clear `assignedRiderId`, `riderOfferedAt`, `riderCylinderVerified`. Keep the distributor notification and audit with the reason.
- `riderVerifyCylinder(orderId, scannedId)`: keep the match rule (Option B allows a mismatch only for an untagged cylinder, exactly as today). On success set `order.riderCylinderVerified = true`. Return clear messages: "Cylinder verified." / "This is not the cylinder allocated to this order. Contact your distributor." It works offline (it compares against data already on the phone) and records the flag immediately.
- `riderStartTrip`: require `riderTaskStatus == ACCEPTED`, `riderCylinderVerified`, and status *Preparing*; otherwise return a plain-word error ("Accept the delivery first.", "Verify the cylinder first."). **Do not add a new `Delivery` if one already exists for the order**; update it instead.
- **Remove** `riderStartPreparing`, `riderSelectAndBindSerial` and the `"START_PREPARING"` outbox action (and its `replayRiderOutbox` branch). Remove `riderAcceptOrder` / `riderDeclineOrder` aliases only if nothing else uses them; otherwise keep them as one-line KDoc'd wrappers.
- `riderReportIssue`: reject unless status is *Dispatched* or *At Handover* ("A problem can be reported once you have started the delivery."). Reason mapping per spec: no answer / cannot access address → `DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE`; customer refused the goods → `GOODS_REJECTED_AT_DOOR`; safety concern → `DELIVERY_FAILURE_OR_CUSTOMER_UNAVAILABLE` **and** the L7 incident (existing behaviour, which also puts the cylinder on HSE hold).
- `riderAcceptTask`, `riderDeclineTask`, `riderVerifyCylinder` work offline immediately (they are local task decisions); `riderStartTrip`, `riderArrive`, `riderReportIssue`, `completeHandover` keep queuing when offline.

### S1.4 Timer: offer expiry
In `processTimers()` (already run every 5 s): for each order whose task is `OFFERED` and `now - riderOfferedAt > riderAcceptanceWindowMins`, if `settings.autoRaiseNoAcceptance` is true do what a decline does (clear the rider, `DECLINED`, notify the distributor "Rider did not accept in time", audit "System Timer"); if it is false, leave the task alone (the UI shows "Acceptance time is over" and disables Accept; the rider can still Decline).

### S1.5 Rider app: tabs and cards
- **Pending** = assigned to me, status *Preparing*, `riderTaskStatus == OFFERED`.
- **Active** = assigned to me, non-terminal, not Pending (task accepted, or the order is in Dispatched / At Handover / Inspection / Adjustment / Exception).
- **Completed** = assigned to me, status *Completed*.
- Fix the KDoc: the three lists do **not** contain Cancelled / Superseded / Failed Closed orders; say so.
- Pending card: countdown from `order.riderOfferedAt` using `settings.riderAcceptanceWindowMins`; show "Offered HH:mm"; keep Accept disabled when expired; show the allocated cylinder ID ("Cylinder BG-LHR-118-002 ready at the depot") and the depot name (from the fulfilment point, not a hard-coded string).
- Active card actions by state:
  - *Preparing*, task accepted, cylinder **not verified** → **"Verify cylinder"** (opens S1.5a). Show "Start delivery" **disabled** with the reason "Verify the cylinder first."
  - *Preparing*, task accepted, verified → **"Start delivery"** enabled.
  - *Dispatched* → "Arrived at location" + "Report a problem".
  - *At Handover* → "Complete delivery" + "Report a problem".
  - *Handover Complete* → "Delivery is being finalised."
  - *Inspection Pending / Adjustment Pending* → the existing waiting strip.
  - *Exception* → the existing amber strip. For these three states **hide** the serial strip and the "Ask the customer for their handover code" line.
- Remove: the "Begin Preparation & Select Serial" button, the serial picker dialog, the serial strip's "Choose / Change" button, the `ORD_RDYPU` branch (pickup orders are handled at the depot counter, never by a rider), and the "Report issue" button on the Preparing card.
- **S1.5a Verify-cylinder dialog:** field "Enter or scan the cylinder ID" (uppercase, no spaces), a "Verify" button calling `riderVerifyCylinder`, the result in the banner. Add a small **"Demo: scan"** button, labelled with `DemoValuePill`, that fills in the allocated ID so the presenter does not have to type. On mismatch show the store message inside the dialog and keep it open.
- Report-a-problem dialog reasons (radio, plain words): "Customer not answering or not available", "Cannot reach the address", "Customer refused the cylinder", plus the existing safety checkbox. Map as in S1.3. Remove the "Address or validation problem" and "Unrecoverable after retries" options.

### S1.6 Staff Distributor screen (minimum change)
In the **Preparing** card, after a cylinder is allocated and the order is a delivery order, show an **"Assign rider"** control: a dropdown of this distributor's riders that are approved, on shift and trained (show name · zone · active-task count), and an "Offer to rider" button calling `store.assignRider`. When a rider is already assigned show "Offered to <name> — waiting" or "Accepted by <name>" (from `riderTaskStatus`), and a "Reassign rider" control if the task was declined. **Remove** the "Mark Dispatched with Rider" button (the rider starts the trip). Keep "Mark Ready for Counter Pickup" for pickup orders. Use `appState` for the rider list.

### S1.7 Seeds
- `ORD-SEED-105`: status *Preparing*, `allocatedSerial` set to an available seed cylinder at the same depot (mark that cylinder `INV_SERIAL` with `allocatedOrderId`), `riderTaskStatus = OFFERED`, `riderOfferedAt = now - 120000L`, history `DRAFT → CONF → ASGN → ACPT → PREP`. Remove its pre-created `Delivery`.
- `ORD-SEED-106`: status *Preparing*, serial allocated, `riderTaskStatus = ACCEPTED`, `riderCylinderVerified = false`, rider `RIDER-001`. Remove its pre-created `Delivery`.
- Check that no other seed relies on the old rider path; keep `ORD-SEED-111` (Exception) and the completed seeds as they are.

---

## S2. Offline queue: no duplicate or ghost actions

Today, while offline, tapping Start delivery / Arrived queues an event but the order does not change, the button stays active, and each tap adds another event with a new idempotency key. The extra events later show as "Rejected".

- Store: before queuing `START_TRIP`, `ARRIVE_AT_DOORSTEP` or `COMPLETE_HANDOVER`, check for an existing `PENDING` event for the same order and action. If one exists return `Pair(true, "Already saved — it will be sent when you are back online.")` without adding another.
- Card: when an order has a pending outbox event, replace its main action button with a disabled chip **"Saved — waiting to send"** (amber) so the rider can see something happened.
- `replayRiderOutbox()`: if there is nothing pending return the message "Nothing waiting to send." and make the banner **amber**, not green. If the result contains conflicts or rejections make the banner amber; all applied → green. Change the demo "Sync Now (N)" and "Outbox (N)" counts to the number of **pending** events only.
- Outbox dialog: show each row's time (`formatTimeOnly(timestamp)`), the plain action name, the order number and the plain status chip. Add a "Clear sent items" button that removes `APPLIED` and `DUPLICATE` rows (store function, ends in `notifyChanged()`).
- Outbox demo "Sync Now" button explains why it is disabled in small text ("You are offline." / "Nothing waiting.").
- Offline banner copy stays as is. Do not claim that *every* action is saved offline; only the four queued actions are.

---

## S3. Proof-of-delivery (POD) corrections

1. **Dialog must not close on failure.** `onCompleted(ok, msg)` currently closes the dialog even when the store returns an error, which loses everything the rider entered. On failure keep the dialog open and show the message in an inline red strip above the action bar; close only on success.
2. **Checklist data only when installed.** When the result is *Not installed* or *Not applicable*, pass `checklistAnswers = emptyMap()` (today it stores an all-false checklist, which reads as "leak check failed").
3. **PIN reasons.** Replace "SMS not received" (no SMS is promised anywhere) with "Customer cannot find the code". Selecting "Other" must require the rider to type at least 5 characters; do not accept the bare word "Other". Keep the reason in its own state, separate from the radio choice.
4. **Evidence rule (spec):** at least one of *photo* or *signature* must exist, or a signature-unavailable reason. Enforce it in `completeHandover` (return "Capture a photo or a signature, or give a reason the signature is unavailable.") as well as in the dialog. Store the photo-skipped reason in a new `Delivery.photoSkippedReason: String?` and pass it from the dialog (today it is thrown away).
5. **Empty cylinder step:** if the rider picks "Other brand", show a note under the list: "Other brands are not accepted for exchange. Do not take this cylinder." (the store records `DECLINED_OTHER_BRAND` silently today). Keep the serial optional.
6. **Signature canvas:** lifting the finger currently joins strokes with a straight line. Keep a list of strokes (each a list of points) and draw each separately; "Clear signature" clears all strokes.
7. **Review step:** show the cash difference reason and the photo/signature status in plain words; never print "Not verified: " with an empty reason.
8. Add `testTag`s already present; do not remove them.

---

## S4. Cash hand-over details

- `CashHandover.recipient` is documented as `"DISTRIBUTOR"` or `"FINANCE"` but the rider dialog passes "Distributor Lead / Depot Cashier / Finance Officer". Offer exactly two radio choices, **"Distributor"** and **"Finance"**, and pass the matching constants. Update the Staff Finance and Distributor cash lists only if they display the old strings (display labels, not logic).
- Rename the dialog line "Orders in pending collection" to "Orders to hand over".
- Extract the duplicated "COD orders waiting to be handed over" filter (it exists three times: the tile, the dialog, the store) into one store function `ridersCashWaiting(riderId): List<Order>` and use it everywhere.
- Declared amount: reject a blank field (show "Enter the amount you are handing over.") instead of treating it as Rs 0.

---

## S5. Rider screen polish

- Header wording: use one phrase set everywhere: "On shift" / "Off shift" (the third tile currently says "On Shift" / "Off Duty"). The shift tile is a button; add a small `Switch`-like affordance or the text "Tap to change" so it does not look like a read-only tile.
- "Cash to hand over" tile: add a small chevron icon so it reads as tappable.
- Address line: when area and city are both blank show "Address not available" instead of ", ". Keep the "Tap to view full address" hint.
- The serial strip is removed in S1; replace it, for Preparing/Dispatched/At Handover, with a single line "Cylinder: <ID> · Verified" or "· Not verified yet".
- Make sure every `Row` that holds a long text plus a button/chip gives the text `weight(1f)` and `maxLines`/ellipsis (customer name, address, order number + status chip at 360 dp).
- The Decline dialog passes `ord?.id ?: ""`; if the order no longer exists show "This delivery is no longer available." and close.
- Demo controls: keep them collapsed by default, clearly labelled **Demo only**, minimum 11 sp text.

---

## S6. Comments and housekeeping

- `data/StoreFulfilment.kt` has **31 functions with no KDoc** (every rider function, `assignRider`, `allocateCylinder`, `startPreparing`, `markReadyForPickup`, `completeCounterHandover`, `replayRiderOutbox`, `simulateSerialConflict`, `toggleRiderShiftStatus`, `depotSubmitCashHandover`, and more). Add KDoc to **all** of them and a file header comment explaining the three groups in the file (distributor, rider, depot counter) and which spec section they follow. Add *why* comments for: COD stopping at Cash Collected (L3), serial allocation at Preparing (L4), the outbox replay ordering and collision check, and the EXC-21 raise.
- `RiderAppScreen.kt`: add KDoc to `isActiveForRider` and `isCompletedForRider`; correct the file header so it only lists what the file really does (it must not mention the serial picker any more).
- `PodCaptureDialog.kt`: add KDoc to `ReviewSummaryRow`.
- `ui/customer/CustomerShared.kt` still declares its own `rememberNowMillis` and `formatCountdown`, duplicating `ui/components/TimeHelpers.kt`. Delete the duplicates from `CustomerShared.kt` and import the shared ones in the customer files that use them.

---

## S7. Self-check and walkthrough (run before replying; paste the results)

### Searches (each must return zero unexpected matches)
1. In `ui/rider/` and `ui/components/`: `OGRA`, `legally`, `soapy`, `bubble`, `tamper`, `jubilee`, `geotag`, `watermark`, `hydro`, `Tax (5%)`, `Total Settled`, `ORD_`, `formatPaisaToPkr`, `Off Duty`, `SMS`.
2. `riderStartPreparing`, `riderSelectAndBindSerial`, `START_PREPARING`, `showSerialPickerOrderId`, `Mark Dispatched with Rider`: zero matches outside comments that explain removal.
3. `notifyChanged()` and `store.<field> =` in `ui/`: zero.
4. Every `riderTaskStatus =` assignment is inside `assignRider`, `riderAcceptTask`, `riderDeclineTask`, `processTimers` or seeds.

### Walkthrough (describe the result of each step; fix anything that fails)
1. **Customer:** place a COD order (11.8 kg refill, home delivery). Note the handover code on the tracking screen.
2. **Staff → Distributor:** Accept → Start preparing → allocate a cylinder → choose a rider → Offer to rider. Staff shows "Offered to <rider> — waiting".
3. **Rider** (same rider): the order is on **Pending** with a live countdown that started when it was offered. Switch to Customer, then Staff, then back to Rider: the Pending tab is still selected.
4. Accept. The order moves to **Active**; its status is still *Preparing* (confirm in Staff). "Start delivery" is disabled and says "Verify the cylinder first."
5. Verify cylinder: a wrong ID is refused with the contact-your-distributor message; "Demo: scan" then succeeds. Start delivery is now enabled. Press it: the customer sees "Order on the Way".
6. Go offline. Press **Arrived**: a "Saved — waiting to send" chip appears; pressing again adds nothing. Go online and press Sync: the order becomes *At Handover*; the outbox row reads "Sent".
7. **Complete delivery:** wrong code three times → "Code not accepted" → "Code not available" needs a reason (typing "Other" alone is refused). Then use the right code. Installed → tick the three checklist items. COD short by Rs 100 → reason required. Photo skipped with a reason and a signature drawn with two separate strokes. Complete.
8. The order is on **Completed**; the tile shows the cash to hand over; the customer receipt says "Cash collected" and that Finance is reconciling.
9. Hand the cash over to **Finance**; in **Staff → Finance** reconcile it; the payment becomes Settled and the receipt now reads paid/settled.
10. Repeat steps 1–5 for a second order, press Start delivery, then **Report a problem → Customer refused the cylinder**: Staff gets the exception alert; the card shows "Needs attention" and no action buttons. A third order with the **safety** box ticked creates an HSE incident and holds the cylinder.
11. Let an offered task sit past the window (temporarily set the window to 1 minute in Admin): Accept is disabled, "Acceptance time is over" shows, and Decline still works. With auto-raise on, the order returns to the distributor with a notification.

---

## Final message format
Reply with: (1) sections completed, (2) files changed, (3) the self-check output, (4) the walkthrough results, (5) anything you could not do and why. Do not claim the app compiles or runs unless you actually ran it.
