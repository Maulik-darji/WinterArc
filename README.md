# WinterArc

**Turn personal goals into visible daily progress.** WinterArc tracks each goal in one of two modes and shows every day as a square in a contribution grid:

- **Consistency**: repeat a fixed target ("walk 5 km every day").
- **Progression**: start comfortably and move toward a milestone ("1 km → 2 km → … → 10 km"). The target only moves up after a *successful completion*. Missed days never advance it, and it never exceeds the final milestone.

Offline-first: goals and history stay on the device. Accounts use **mobile number + SMS one-time code** (Firebase Phone Auth).

> Demo story: *choose a meaningful goal → pick consistency or progression → check in daily → watch effort become an undeniable visual record.*

---

## Tech stack

| Concern | Choice |
|---|---|
| Language / build | **Java 17**, **Groovy DSL** Gradle, version catalog (`gradle/libs.versions.toml`), AGP 9.2 |
| UI | Android Views + Fragments, **Material 3** (Material Components), ViewBinding |
| Architecture | MVVM with unidirectional data flow: Repositories → Use cases → ViewModels (`LiveData` state + one-shot `Event`s) → Fragments |
| Navigation | Navigation Component (`res/navigation/nav_graph.xml`) |
| Persistence | **Room** (exported schemas in `app/schemas`), SharedPreferences for small settings |
| DI | **Hilt** (incl. `@HiltWorker`) |
| Background | **WorkManager** for DST-safe reminders |
| Threading | `AppExecutors` (single IO thread for ordered writes, main-thread delivery) |
| Tests | JUnit 4, Robolectric (Room in-memory), Espresso + Test Orchestrator |

> Jetpack Compose and DataStore are Kotlin-first. Because this project is Java, it uses the View system and SharedPreferences, both idiomatic in Java.

minSdk **26** (native `java.time` for date math). targetSdk **36**, compileSdk **37**.

---

## Architecture

```
com.app.winterarc
├── domain/                 Pure Java: no Android UI dependencies
│   ├── model/              Goal, ProgressEntry, Milestone, PausePeriod, Amount, GoalDraft, enums
│   ├── engine/             ProgressionEngine, StatsCalculator, GoalTimeline/DayKind, Heatmap,
│   │                       SafetyAdvisor, GoalValidator
│   ├── usecase/            CheckInUseCase, GoalUseCases, MilestoneUseCase   (business rules)
│   └── repository/         Interfaces only (Goal/Progress/Milestone/Content/Preferences/Reminders)
├── data/                   Room entities + DAOs + mappers, Room repositories, bundled content, prefs
├── reminders/              WorkManager scheduler + worker, notification channel, DST-safe time math
├── core/                   TimeProvider, AppExecutors, Analytics + CrashReporter seams, Formats
├── ui/                     onboarding, home, editor (wizard), detail (+ sheets, celebration),
│                           stats, archive, settings, legal, common (grid view, picker, helpers)
├── demo/DemoTools          Interface; the debug source set implements it, release binds UNAVAILABLE
└── di/                     Hilt modules (+ build-type-specific BuildTypeModule in src/debug|release)
```

**Cloud-ready:** screens and use cases depend only on repository interfaces. Every syncable row has a `uuid` and `updated_at`. Adding auth plus sync means new repository implementations bound in `BindingsModule`, with no UI rewrite.

### Key rules (all unit-tested)

- **Decimals:** quantities are `Amount` = `long` thousandths (21.1 km → `21100`). Completion checks are exact integer comparisons, never floating-point.
- **Progression:** `onCompleted(target)` → `min(target + hop, final)`. Completing the final target creates a pending milestone. Each day advances at most once. Lowering or undoing a completed day reverts its advancement and any milestone it created.
- **Streaks:** consecutive completed days. Missed or partial scheduled days end a streak. Rest (skipped) days, pauses, unscheduled days and an unlogged *today* are neutral.
- **Dates:** days are `LocalDate`, resolved in the device's current zone at call time. Reminders are one-shot jobs re-armed for the next local wall-clock time, so they stay correct across DST and time-zone changes.
- **History is immutable:** each entry stores the target that applied that day, so plan edits, milestone conversions and restores never rewrite it.

---

## Screen & navigation map

```
Splash ─┬─(first launch)→ Onboarding (3 pages, skippable) ─→ Sign up: mobile number → OTP code ─→ Home
        ├─(signed out)──────────────────────────────────→ Sign up: mobile number → OTP code ─→ Home
        └─(signed in)───────────────────────────────────────────────────────────────────────→ Home
Primary navigation: Home · Community · New goal · Chats · Profile

Home ──→ Goal editor wizard (Activity → Mode → Target → Details → Schedule → Review)
     ──→ Goal detail ──→ Log progress / Skip sheets, Note dialog, Day detail sheet
     │               ──→ Milestone celebration (Maintain | Continue | Complete)
     │               ──→ Progress & stats (12-month grid, monthly breakdown, milestones)
     │               ──→ Edit goal (wizard, opens on Review)
     ├─→ Archive (restore / delete permanently)
     └─→ Settings ──→ Account (signed-in number, Sign out), Privacy policy, Terms & disclaimer,
                      Demo tools (debug only)
Community ──→ Discover/join goal communities + browse, like and create posts in one feed
Chats ──→ Search connections ──→ Conversation (send local messages)
Profile ──→ Personal goal summary, editable display name, Archive and Settings
Reminder notification ──→ Goal detail (deep link)
```

The social screens currently provide the complete local UI experience with sample community and
connection data. A multi-user backend, moderation, reporting, push messaging and cross-device sync
must be connected before these interactions are exposed as a live public social network.

---

## Data model (Room, schema v1)

| Table | Key columns |
|---|---|
| `goals` | id, uuid, title, description, activity_type, tracking_mode, unit, custom_unit_label, current_target_milli, start/final/hop_milli (progression), schedule_mask (7-bit), start_date, end_date, reminder_enabled, reminder_minute_of_day, color, icon, status (active/paused/completed/archived), created_at, updated_at |
| `progress_entries` | id, uuid, goal_id (FK cascade), date (unique per goal), scheduled_target_milli, actual_milli, status (completed/partial/skipped/note_only), skip_reason, note, progression_level, advanced_progression, created_at, updated_at |
| `milestones` | id, goal_id, target_milli, achieved_date, celebration_state (pending/maintained/continued/completed), created_at |
| `pause_periods` | id, goal_id, start_date, end_date (null while paused) |
| `motivational_content` | id, content_type, activity_type, min/max_target_milli, message, attribution, source_name, source_url, locale, content_version |

Enums are stored by stable string key. "Missed" is derived (a scheduled past day with no entry), never stored.

**Migrations:** `WinterArcDatabase.MIGRATIONS` plus exported JSON schemas. Destructive fallback is deliberately disabled. `DatabaseMigrationTest` validates the schema. When you add v2, add `Migration(1, 2)` and a `runMigrationsAndValidate` case.

### Motivational content

Shipped offline in `app/src/main/assets/content/motivation.json`, then copied into Room by content version. **Facts and quotes without an `https` source are dropped at load and never displayed** (this is enforced in code and tested). The current set includes:

- WHO adult activity recommendation, with the WHO fact sheet as its source.
- NHS adult activity guidance, with the NHS page as its source.
- The 1921 standardisation of the 42.195 km marathon distance, with Wikipedia "Marathon" as its source.
- Laozi's "journey of a thousand miles", with Wikiquote as its source.
- A few clearly unattributed encouragement messages.

A backend can later deliver the same JSON format via `BundledContentRepository.replace()`.

---

## Phone sign-up (mobile number + OTP)

Flow: onboarding → **Create your account** (country picker defaulting to the SIM region, mobile number, local validation) → **Enter the code** (6-box OTP field with SMS autofill and paste support, auto-submit on the 6th digit, 30 s resend cooldown, change number, calm error messages) → Home. Settings shows the signed-in number and **Sign out** (local goals stay on the device).

| Layer | Class |
|---|---|
| Contract | `domain/auth/AuthRepository` (send code, verify, auto-verify, sign out), `PhoneNumber` (E.164 normalisation), `Country`, `AuthError` |
| Real provider | `data/auth/FirebasePhoneAuthRepository`: SMS delivery, instant verification, SMS auto-retrieval, resend tokens, error mapping |
| Debug fallback | `src/debug/.../FakePhoneAuthRepository`: no SMS. Code **123456**; numbers ending `0000` simulate "too many requests". Shows a "Test mode" banner |
| Release fallback | `UnavailableAuthRepository`: release builds without Firebase config show "Sign-up isn't available" and never fake a login |
| Selection | `di/AuthModule`: Firebase if `google-services.json` is configured, otherwise the build-type fallback |
| State | `ui/auth/AuthViewModel` (activity-scoped). Number, verification id, resend token and cooldown live in `SavedStateHandle`, so switching to the SMS app and back survives process death |

Analytics: `signup_started`, `otp_requested`, `otp_failed{reason}`, `signup_completed`. **The phone number is never sent to analytics** (this is tested).

### Enabling real SMS (Firebase)

1. Create a project at <https://console.firebase.google.com> → add an Android app with package **`com.app.winterarc`** (and `com.app.winterarc.debug` for debug builds).
2. Add your signing certificates' **SHA-1 and SHA-256** (`./gradlew signingReport`) to the Firebase app. Phone Auth needs them for Play Integrity app verification.
3. **Authentication → Sign-in method → Phone → Enable.** Optionally restrict SMS regions (Authentication → Settings → SMS region policy) to control cost and abuse.
4. Download `google-services.json` into **`app/`**. The Google Services plugin is applied automatically when this file exists. Don't commit it to a public repo.
5. For emulator and CI testing without real SMS, add **test phone numbers** with fixed codes under *Phone → Phone numbers for testing*.
6. Phone Auth is billed per SMS on the Blaze plan after the free allowance. Check current Firebase pricing and quotas.

## Setup

1. Android Studio (latest stable) with JDK 21 available (Gradle toolchain).
2. Open the project. Gradle downloads dependencies, and AGP installs SDK platform 37 if missing.
3. Run the **app** configuration (debug variant, application id `com.app.winterarc.debug`).

```bash
./gradlew assembleDebug
```

### Demo mode (debug builds only)

Settings → *Demo tools* → **Load demo scenario**. This replaces all goals with:

| Goal | Shows |
|---|---|
| Morning walk | Consistency, 5 km daily, ~5 months of history, a live 23-day streak, partial, rest and above-target days |
| Read 20 pages | Weekday consistency, with bonus weekend days |
| Meditation 5 → 30 min | Progression mid-way (77%) |
| **Road to 10K** | Progression **one completion from its milestone**. Today is scheduled, so tap **Complete** to show the celebration, then Maintain, Continue (e.g. 15 km) or Complete |
| Couch to 5K | Completed and archived, with its milestone history |

Demo code lives in `app/src/debug` and **is not compiled into release builds**. Release binds `DemoTools.UNAVAILABLE`, and the Settings section is hidden.

**Suggested 2-minute pitch flow:**
1. Home ("4 targets left today").
2. Open *Road to 10K*: step 15 of 15, the progression bars and the rule "the target only moves up after a completed day".
3. Tap **Complete** to show the celebration, then **Continue progressing** to 15 km, which makes the next target 10.5 km.
4. Back on Home, open *Morning walk* and tap a square to see that day's details.
5. **All stats** shows the 12-month grid and monthly breakdown.

---

## Testing

```bash
./gradlew testDebugUnitTest            # 94 JVM/Robolectric tests
./gradlew connectedDebugAndroidTest    # 5 instrumented tests (emulator/device; orchestrator clears data)
```

| Suite | Covers |
|---|---|
| `ProgressionEngineTest` | Step generation (1→10/1, 2→10/2, clamping 1→10/2), decimal hops, final-target clamp, milestone, level/fraction, rebase on edit, continue-beyond |
| `CheckInUseCaseTest` | Advance only on completion, missed days don't advance, once-per-day, partial/top-up, skip, exceed, undo, lowering a completed day, full journey to milestone, decimal exactness, closed goals, notes, analytics with no free text |
| `MilestoneAndLifecycleTest` | Maintain → consistency, continue (new final/hop), mark complete, pause/resume neutrality, restore gap, plan edits preserve history |
| `StatsCalculatorTest`, `HeatmapTest` | Streak rules, rates, totals, every day classification, locale week start, month labels |
| `ValidationAndSafetyTest`, `AmountTest` | Input validation, safety levels, exact decimal parsing |
| `RoomRepositoriesTest`, `BundledContentRepositoryTest` | Real Room round-trips, one entry per day, cascade delete; unsourced content rejected |
| `ReminderTimesTest` | DST end and DST gap reminder scheduling |
| `GoalEditorViewModelTest`, `HomeStateTest` | Wizard flow, validation, safety confirmation, **state restoration after process death**, edit mode; dashboard grouping |
| `PhoneNumberTest`, `AuthViewModelTest` | Number normalisation (formatting, pasted +CC, trunk zero, length limits), masking; send → verify, wrong/malformed code, resend cooldown + token, auto-verification, errors, no phone number in analytics, process-death restore |
| `SignupFlowTest` (UI) | Too-short number, masked number on code screen, wrong code error, correct code → Home, sign out → sign-up |
| `CoreJourneyTest` (UI) | Onboarding → sign up with OTP → create goal → complete → undo → complete → dashboard "All done"; Settings shows the account |
| `SafetyConfirmationTest` (UI) | Marathon milestone shows the supportive message and safety notice |
| `DatabaseMigrationTest` (UI) | Exported schema matches entities; data survives reopen |

---

## Release

1. Create a keystore (never commit it), then add an untracked `keystore.properties` at the project root:
   ```properties
   storeFile=../keys/winterarc-release.jks
   storePassword=…
   keyAlias=winterarc
   keyPassword=…
   ```
2. Build:
   ```bash
   ./gradlew bundleRelease
   ```
   Without `keystore.properties`, the output is unsigned.
3. Release builds use R8 (`optimization { enable true }`) with rules in `app/src/main/keepRules/rules.keep`. Debug and verbose logs are stripped.
4. Bump `versionCode` and `versionName` in `app/build.gradle`.

Analytics (`Analytics`) and crash reporting (`CrashReporter`) are interfaces. Release binds no-ops in `src/release/.../BuildTypeModule.java`. To connect a provider, implement the interface there, and update the privacy policy first.

---

## What remains before a public Play Store release

- **Legal and policy:** have the privacy policy and terms reviewed, add a hosted privacy-policy URL, and complete the Play Data safety form. The phone number is collected for account management and shared with Firebase Authentication.
- **Accounts:** Play requires in-app **account deletion** for apps with account creation (delete the Firebase user and its data). Add Firebase **App Check** and an SMS region policy before launch. Cloud sync of goals to the signed-in account is not built yet.
- **Content:** a proper human review of every fact and quote, plus localisation of strings and content (only `en` ships).
- **Accessibility audit:** a full TalkBack pass on a physical device, Accessibility Scanner on every screen, and checks at 200% font scale on small phones.
- **Store assets:** final launcher icon (the current one is an original placeholder grid mark), screenshots, feature graphic.
- **Reminders on Android 12+:** WorkManager timing is inexact by a few minutes. Consider `AlarmManager.setExactAndAllowWhileIdle` with the `SCHEDULE_EXACT_ALARM` flow if exact times matter.
- **Backup/restore:** goal data is included in Android backup. Add an explicit export/import (CSV/JSON) for user trust.
- **Observability:** choose crash and analytics providers (with consent UX) behind the existing interfaces.
- **Performance:** a baseline profile and a macrobenchmark for cold start and scrolling a year-long grid.
- **Social backend:** connect Community and Chats to authenticated server APIs, then add moderation,
  reporting/blocking, push messaging, abuse controls and updated privacy disclosures.

---

## Verification report (this build)

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | ✅ |
| `./gradlew testDebugUnitTest` | ✅ 94 tests, 0 failures |
| `./gradlew connectedDebugAndroidTest` (Pixel 10 Pro AVD, API 37) | ✅ 5 tests, 0 failures |
| `./gradlew assembleRelease` (R8 enabled) | ✅. Demo classes, demo strings and the fake OTP provider are absent from release dex |
| `./gradlew lintDebug` | ✅ 0 errors (fixed an API-31-only call in `Amount.parse` that would have crashed on Android 8–11) |
| Manual on emulator | Onboarding, empty state, demo load, home, detail, milestone celebration → continue, wizard (consistency and progression with milestone carousel), settings, dark mode, release build create → complete → restart persistence |
