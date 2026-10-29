# WinterArc

**Turn personal goals into visible daily progress.** WinterArc tracks each goal in one of two modes and shows every day as a square in a contribution grid:

- **Consistency**: repeat a fixed target ("walk 5 km every day").
- **Progression**: start comfortably and move toward a milestone ("1 km → 2 km → … → 10 km"). The target only moves up after a *successful completion*. Missed days never advance it, and it never exceeds the final milestone.

Offline-first: goals and history stay on the device. Accounts use **Continue with Google** or **email + password** (Supabase Authentication). A Twitter/X-style **Feed** lets members post, reply in threads, and join goal communities.

> Product story: *choose a meaningful goal → pick consistency or progression → check in daily → watch effort become an undeniable visual record.*

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
Splash ─┬─(first launch)→ Onboarding (3 pages, skippable) ─→ Welcome ─→ Home
        ├─(signed out)──────────────────────────────────→ Welcome ─→ Home
        └─(signed in)──────────────────────────────────────────────→ Home
Welcome ──→ Continue with Google (Credential Manager)
        ──→ Create account (name, email, password, confirm password, show/hide toggles)
        ──→ Log in (email, password, Forgot password → reset email)
Primary navigation: Home · Feed · New goal · Chats · Profile

Home ──→ Goal editor wizard (Activity → Mode → Target → Details → Schedule → Review)
     ──→ Goal detail ──→ Log progress / Skip sheets, Note dialog, Day detail sheet
     │               ──→ Milestone celebration (Maintain | Continue | Complete)
     │               ──→ Progress & stats (12-month grid, monthly breakdown, milestones)
     │               ──→ Edit goal (wizard, opens on Review)
     ├─→ Archive (restore / delete permanently)
     └─→ Settings ──→ Account (signed-in email, Sign out), Privacy policy, Terms & disclaimer
Feed ─┬─ Posts: For you | Following timeline ──→ Compose post (280 chars)
      │        └─→ Post ──→ threaded replies, reply composer, edit/delete own posts & comments
      └─ Communities: Discover / My communities ──→ Community chat, settings
Chats ──→ Search connections ──→ Conversation (send local messages)
Profile ──→ Personal goal summary, editable display name, Archive and Settings
Reminder notification ──→ Goal detail (deep link)
```

The app ships with **no sample data**. Feed and community content is stored locally in Room
(`SocialDatabase`) behind repository interfaces, with matching Supabase tables and row-level
security in `supabase/migrations`. Server sync, moderation, reporting, blocking and push
notifications must be connected before the Feed is opened up as a live public network.

---

## Feed: posts and threaded comments

Posts are separate from communities (spec: `docs/SPEC_feed_posts_auth.md`).

- **Timeline** (`ui/feed/PostsFragment`): edge-to-edge rows with hairline dividers. Each row shows avatar, name, @handle, relative time ("now", "5m", "3h", "4d", "Oct 4") and *Edited*, plus reply, like and share actions. *For you* shows everyone's posts; *Following* shows people you follow. The compose button opens `ComposePostFragment`, which has a live 280-character counter and asks before discarding a draft. Authors can edit or delete their own posts from the ⋯ menu.
- **Post screen** (`PostDetailFragment`): the full post with exact time and counts, then the comments, with a composer pinned above the keyboard labelled "Post your reply".
- **Threads**: anyone can reply to the post or to any comment. `domain/feed/CommentThreads` turns the flat comment list into display order. `ThreadLinesView` draws the connector lines. Indentation stops at 3 levels; deeper replies stay at level 3 and show "Replying to @handle". Replying shows a "Replying to @handle" chip that you can cancel.
- **Edit/delete**: comment authors can edit (marked *Edited*) or delete their comments. Deleting a comment that has replies leaves a "This comment was deleted." placeholder so the thread still reads correctly. Deleting a comment with no replies removes it, along with any placeholder that is left with no replies. `RoomFeedRepository` enforces authorship on every write, so these rules don't depend on what the UI shows.
- **Limits**: 280 characters per post, 500 per comment. Text is trimmed and empty input is rejected, in both the UI and the repository.
- **Haptics**: likes, sending, deleting and errors each give haptic feedback. Every action has a content description, and comment rows tell TalkBack their thread level.

---

## Data model (Room)

### `WinterArcDatabase` (schema v1): goals

| Table | Key columns |
|---|---|
| `goals` | id, uuid, title, description, activity_type, tracking_mode, unit, custom_unit_label, current_target_milli, start/final/hop_milli (progression), schedule_mask (7-bit), start_date, end_date, reminder_enabled, reminder_minute_of_day, color, icon, status (active/paused/completed/archived), created_at, updated_at |
| `progress_entries` | id, uuid, goal_id (FK cascade), date (unique per goal), scheduled_target_milli, actual_milli, status (completed/partial/skipped/note_only), skip_reason, note, progression_level, advanced_progression, created_at, updated_at |
| `milestones` | id, goal_id, target_milli, achieved_date, celebration_state (pending/maintained/continued/completed), created_at |
| `pause_periods` | id, goal_id, start_date, end_date (null while paused) |
| `motivational_content` | id, content_type, activity_type, min/max_target_milli, message, attribution, source_name, source_url, locale, content_version |

### `SocialDatabase` (schema v2): profiles, communities, feed

v1 tables: `social_users`, `communities`, `community_members`, `community_posts`, `post_likes`, `follows`, `community_messages`.
v2 adds `feed_posts` (id, author_user_id, body, created_at, edited_at), `feed_post_likes` (post_id + user_id, FK cascade) and `post_comments` (id, post_id FK cascade, parent_comment_id, author_user_id, body, created_at, edited_at, deleted). The explicit `SocialDatabase.MIGRATION_1_2` runs at startup, and `SocialDatabaseMigrationTest` checks it.

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

## Accounts: Google or email + password

Flow: onboarding → **Welcome** → *Continue with Google*, or *Create account* (name, email, password, **confirm password**, an **eye toggle** on both password fields), or *Log in* (with **Forgot password?**). Then Home. Settings shows the signed-in email and **Sign out** (local goals stay on the device).

Rules (`domain/auth/Credentials`, unit-tested): a valid email; a password of 8–128 characters with at least one letter and one number; both passwords must match; a name of at most 50 characters. Problems appear inline on the field they belong to. Provider errors (email already in use, wrong email or password, weak password, account disabled, too many attempts, no connection, Google cancelled, not configured) show as plain-language messages.

| Layer | Class |
|---|---|
| Contract | `domain/auth/AuthRepository` (sign up, log in, Google ID token, password reset, sign out), `AuthUser`, `AuthError`, `Credentials` |
| Real provider | `data/auth/SupabaseAuthRepository`: email/password with display name; Google via an ID-token exchange (`grant_type=id_token`); reset email; token refresh; error mapping. The session is kept encrypted with the Android Keystore (`KeystoreSessionStorage`) |
| Google sign-in | `ui/auth/GoogleSignIn`: Credential Manager `GetSignInWithGoogleOption` → Google ID token → Supabase Auth |
| Debug fallback | `src/debug/.../LocalTestAuthRepository`: accounts stored on the device with SHA-256 password hashes, plus a "Test Google account" option. Shows a *Test mode* banner. Not included in release builds |
| Release fallback | `UnavailableAuthRepository`: release builds without Supabase settings show "Sign-in isn't available" and never fake a login |
| Selection | `di/AuthModule`: Supabase Auth when `SUPABASE_URL` and `SUPABASE_PUBLISHABLE_KEY` are set, otherwise the build-type fallback. Instrumented-test builds always use the local test provider, so tests never create real accounts |
| State | `ui/auth/AuthViewModel` (activity-scoped): busy state, per-field problems and one-shot events |

Analytics: `signup_started`, `auth_failed{reason}`, `signup_completed`. Email addresses and passwords are never sent to analytics.

### Enabling real accounts (Supabase Auth)

Everything is configured in the Supabase dashboard and Google Cloud; there is no other account system.

1. **Email sign-up:** *Authentication → Sign In / Providers → Email* is on by default. Decide on **Confirm email**: when it is on, new users must open the emailed link before they can log in (the app and the website both handle this and say so). Set a sensible **minimum password length** (8) to match the apps.
2. **URL settings:** under *Authentication → URL Configuration* set **Site URL** to your website's address, and add `http://localhost:5173/**` and your production domain with `/**` to **Redirect URLs**. Confirmation and reset emails open the website.
3. **Google:** in Google Cloud Console → *APIs & Services → Credentials* create
   - a **Web application** OAuth client. Add the Supabase *Callback URL* (shown in the Google provider panel, `https://<project>.supabase.co/auth/v1/callback`) as an **Authorized redirect URI**, and your website's address as an **Authorized JavaScript origin**;
   - an **Android** OAuth client with package `com.app.winterarc` (and `com.app.winterarc.debug`) and your signing **SHA-1** (`./gradlew signingReport`).

   Then in Supabase → *Providers → Google* turn it on and paste the Web client's **Client ID** and **Client Secret**. In **Client IDs** put the Web client ID first, then the Android client ID, comma-separated. Leave *Skip nonce checks* off.
4. **App setting:** put the Web client ID in `local.properties` as `GOOGLE_WEB_CLIENT_ID=...` (see `local.properties.example`). It is public, not a secret. Without it the Google button is hidden.
5. Run the SQL files in `supabase/migrations` in order (SQL Editor).

## Supabase cloud connection

The app includes an authenticated Supabase REST foundation under `data/remote`. Room remains the
offline source of truth; the remote layer currently supports pulling changed goals and UUID-based
goal upserts. A later sync worker can reconcile those results into Room without changing screens.

1. Create a Supabase project and copy its **Project URL** and **publishable key** from **Connect**.
   Never put a secret or `service_role` key in the Android app.
2. Copy the two `SUPABASE_*` entries from `local.properties.example` into the untracked
   `local.properties`. CI can instead provide environment variables with the same names.
3. Set up sign-in as described under *Accounts* above. A signed-in user's Supabase access token
   already carries the `authenticated` role, so no extra token claims are needed.
4. Run the files in `supabase/migrations` in order in the Supabase SQL Editor. They create the
   cloud schema, grant only what each role needs, enable RLS, and restrict writes to rows whose
   `user_id` matches the token's subject (the user's id).
5. Rebuild the app. `BuildConfig.SUPABASE_CONFIGURED` becomes true only when both client values are
   present. `SupabaseGoalRemoteDataSource` refuses requests when configuration or sign-in is absent.

The publishable key is expected to be extractable from an APK. Access control therefore lives in
the checked-in RLS migration, not in attempts to hide the client key.

## Setup

1. Android Studio (latest stable) with JDK 21 available (Gradle toolchain).
2. Open the project. Gradle downloads dependencies, and AGP installs SDK platform 37 if missing.
3. Run the **app** configuration (debug variant, application id `com.app.winterarc.debug`).

```bash
./gradlew assembleDebug
```

### Sample data

The app ships **no demo or sample data**. Goals, communities and chats start empty and fill only
with what the user creates. Builds before this change seeded sample people, communities, posts and
chat messages; `RoomSocialRepository.ensureProfile` removes those rows from existing installs.

## Testing

```bash
./gradlew testDebugUnitTest            # 111 JVM/Robolectric tests
./gradlew connectedDebugAndroidTest    # 7 instrumented tests (emulator/device; orchestrator clears data)
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
| `CredentialsTest` | Email normalisation and format, password length/letter/number rules, confirm mismatch, name limits |
| `CommentThreadsTest` | Pre-order thread order, depth cap with "Replying to", thread-line flags, deleted placeholders pruned when they have no replies, orphan handling |
| `RoomFeedRepositoryTest`, `RoomSocialRepositoryTest` | Post/comment limits, authorship checks on edit/delete, like toggling, deleting a comment with replies (placeholder) vs. without (removed, plus cleanup), cascade delete, profiles |
| `SignupFlowTest` (UI) | Password mismatch, create account → Home, sign out → Welcome, wrong password error, log back in |
| `FeedFlowTest` (UI) | Write a post → open it → comment → reply in a thread ("Replying to @…") → edit the reply → delete the parent comment (placeholder remains) |
| `CoreJourneyTest` (UI) | Onboarding → create an account → create goal → complete → undo → complete → dashboard "All done"; Settings shows the account |
| `SafetyConfirmationTest` (UI) | Marathon milestone shows the supportive message and safety notice |
| `DatabaseMigrationTest`, `SocialDatabaseMigrationTest` (UI) | Exported schemas match entities; goal data survives reopen; social v1 → v2 keeps users, adds feed tables, comments cascade with their post |

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

- **Legal and policy:** have the privacy policy and terms reviewed, add a hosted privacy-policy URL, and complete the Play Data safety form. The email address (and Google account, if used) is collected for account management and shared with Supabase (Authentication and database). Feed posts and comments are user-generated content, so they need Play's UGC requirements: reporting, blocking and moderation.
- **Accounts:** Play requires in-app **account deletion** for apps with account creation (delete the Supabase user and its data; this needs a small server-side function, since clients can't delete their own auth user). Add CAPTCHA/rate limits under *Authentication → Attack Protection* before launch. Cloud sync of goals to the signed-in account is not built yet.
- **Content:** a proper human review of every fact and quote, plus localisation of strings and content (only `en` ships).
- **Accessibility audit:** a full TalkBack pass on a physical device, Accessibility Scanner on every screen, and checks at 200% font scale on small phones.
- **Store assets:** final launcher icon (the current one is an original placeholder grid mark), screenshots, feature graphic.
- **Reminders on Android 12+:** WorkManager timing is inexact by a few minutes. Consider `AlarmManager.setExactAndAllowWhileIdle` with the `SCHEDULE_EXACT_ALARM` flow if exact times matter.
- **Backup/restore:** goal data is included in Android backup. Add an explicit export/import (CSV/JSON) for user trust.
- **Observability:** choose crash and analytics providers (with consent UX) behind the existing interfaces.
- **Performance:** a baseline profile and a macrobenchmark for cold start and scrolling a year-long grid.
- **Social backend:** sync the Feed, Communities and Chats through the Supabase tables in `supabase/migrations` (RLS is already keyed on the signed-in user's id), then add moderation, reporting/blocking, push notifications for replies, and rate limits.

---

## Verification report (this build)

| Check | Result |
|---|---|
| `./gradlew assembleDebug` | ✅ |
| `./gradlew testDebugUnitTest` | ✅ 111 tests, 0 failures |
| `./gradlew connectedDebugAndroidTest` (emulator, API 37) | ✅ 7 tests, 0 failures |
| `./gradlew assembleRelease` (R8 enabled) | ✅. The debug test login (`LocalTestAuthRepository`) is absent from release dex |
| `./gradlew lintDebug` | ✅ 0 errors |
| Manual on emulator | Welcome, sign-up (light/dark), Feed timeline, compose, post with a 4-level thread and reply chip (light/dark), onboarding, empty state, home, detail, milestone celebration → continue, wizard (consistency and progression with milestone carousel), settings, dark mode, release build create → complete → restart persistence |
