# BLUE GAS — Prototype App

A clickable prototype of the **BLUE GAS household LPG delivery platform** (Pakistan). One Android app contains three surfaces:

| Surface | What it shows |
|---|---|
| **Customer app** | Order 11.8 kg / 45.4 kg cylinders, choose a delivery time, track the rider, receipts, My cylinders, return a cylinder, complaints |
| **Rider app** | Job offers, hub pickup, doorstep delivery and installation checklist, proof of delivery, cash hand-over, cylinder collections, offline queue |
| **Staff portal** | Hub, Operations / Dispatch, Plant & Stock, Finance, Commercial, Support, Safety (HSE), Admin, Switch & Save preview |

It was built for client presentations during the proposal stage. It runs entirely on the device, with an in-memory store and demo data, and has **no backend**.

## Tech stack

- Kotlin, Jetpack Compose (Material 3)
- Android Gradle Plugin 9, Gradle 9.3.1, JDK 17 or newer
- Unit tests: JUnit and Robolectric (they run on the JVM, no emulator needed)

## Project structure

```
app/src/main/java/com/example/bluegas/
├── model/       # Domain models, enums, platform settings
├── lifecycle/   # State machines: Order, Payment, Claim, Return/Refund case, Safety incident
├── data/        # In-memory store (BlueGasStore + Store*.kt by domain), pricing, seed data
├── ui/
│   ├── customer/
│   ├── rider/
│   ├── staff/
│   └── components/
└── ai/          # Optional AI advisor (needs your own Gemini/Firebase setup)
app/src/test/    # Store and lifecycle unit tests
```

## Getting started

### 1. Prerequisites
- **Android Studio** (it brings the Android SDK, the emulator and a JDK). Open it once so the SDK finishes installing, then create an emulator in Device Manager.
- **VS Code** with the **Claude Code** extension (optional, for AI-assisted development).

### 2. Clone
```bash
git clone https://github.com/arslanazmat23/Parco-Pearl-Clone.git
cd Parco-Pearl-Clone
```

### 3. One-time local setup
```bash
./scripts/setup.sh          # creates debug.keystore, local.properties and .env
```
> If `gradlew` or `scripts/setup.sh` is not in the repo yet, add them from the local-dev kit first. The original Google AI Studio export did not include the Gradle wrapper.

### 4. Build, test, run
```bash
./gradlew :app:testDebugUnitTest   # run all unit tests
./gradlew :app:assembleDebug       # build the debug APK
./gradlew :app:installDebug        # install on a running emulator or device
```
Or open the folder in Android Studio and press **Run**.

### 5. Optional: AI advisor
Copy `.env.example` to `.env` and add your own Gemini API key. The rest of the app works without it.

## Development rules (short version)

- All state changes go through the store and the lifecycle state machines. UI code never changes data directly.
- Order, payment, stock, cylinder custody, return/refund, Switch & Save claim and safety incident each have their **own** status lifecycle. Their statuses are never mixed.
- Business values that are still open (prices, deposits, fees, time windows, limits) are **settings**, never hard-coded.
- Every behaviour change comes with a unit test, and the full test suite must pass before a commit.

The full rules for contributors and for Claude Code are in `CLAUDE.md`. Upcoming work is listed in `docs/NEXT_TASKS.md`.

## Never commit

`.env`, `debug.keystore`, `local.properties`, `*.jks`, build output.

## Status

Prototype for client review. It is not production software and contains demo data only.
