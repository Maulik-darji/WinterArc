# WinterArc — Feed, Posts & Threaded Comments, Email/Google Accounts

Refined product spec for the October 2026 update. Supersedes the phone-number OTP flow.

## 1. Navigation

Bottom bar, in order: **Home · Feed · New · Chats · Profile**.

- Modern line icons (Phosphor, MIT licence) with a matching **filled** variant for the selected tab.
- "New" stays a primary action button in the bar (creates a goal).
- "Community" is renamed **Feed**. The "Community / Grow alongside people…" header is removed; the
  screen opens straight into content.

## 2. Feed (posts) — separate from Communities

Feed has two top tabs: **Posts** and **Communities**. Posts are not tied to a community.

### Timeline (X/Twitter-style)
- Edge-to-edge rows separated by hairline dividers (no cards).
- Row: avatar · **Display name** · @handle · relative time ("2m", "3h", "Oct 4") · "Edited" marker.
- Body text (max 280 characters).
- Action row: reply count, like (toggle, count), share (system share sheet).
- Author's own posts: overflow → Edit, Delete (confirm).
- Floating compose button → **Compose post** screen with a live 280-character counter.
- Sub-tabs **For you** (all posts, newest first) and **Following** (people you follow).
- Empty states for both.

### Post detail
- The post at full size, with exact timestamp and counts.
- Comments below, then a fixed composer at the bottom: "Post your reply".

### Comments & threads
- Anyone signed in can comment on a post and **reply to any comment**.
- Replies form **threads**: children are indented under their parent and joined by a visible
  thread line drawn from the parent's avatar; depth indentation is capped at 3 levels (deeper
  replies stay at level 3 and show "Replying to @handle").
- Replying shows a "Replying to @handle" chip above the composer, with a cancel action.
- **Comment authors can edit and delete their own comments.** Edited comments show "Edited".
  Deleting a comment that has replies keeps the thread intact with a "Comment deleted" placeholder;
  deleting a leaf comment removes it.
- Comments: max 500 characters, trimmed, empty input rejected.
- Like counts and reply counts update live.

## 3. Accounts — Google or email/password (phone OTP removed)

### Welcome
- **Continue with Google** (Credential Manager → Google ID token → Supabase Auth).
- "or" divider → **Create account** / **Log in** with email.

### Create account
- Name, email, password, **confirm password**.
- Both password fields have an **eye toggle** to show/hide the password.
- Rules: valid email; password ≥ 8 characters with at least one letter and one number; the two
  passwords must match. Errors shown inline, per field.
- On success a verification email is sent; the user can continue into the app.

### Log in
- Email + password (eye toggle) and **Forgot password?** (sends a reset email).

### Errors (calm, specific)
Email already in use · wrong email or password · weak password · invalid email · account disabled ·
too many attempts · no connection · Google sign-in cancelled · not configured.

### Configuration
- Supabase Auth is the identity provider for the Android app and the website (no other account system).
- Requires the **Email** and **Google** providers enabled in Supabase, a Google Cloud Web client (id + secret in
  Supabase) and Android client, and `GOOGLE_WEB_CLIENT_ID` in the app's build settings.
- Debug builds without Supabase settings use a local test provider (any email/password; "Test Google
  account") so the flow works on emulators. Release builds never fake a login.

## 4. Data & production readiness

- Posts, likes and comments are stored behind `FeedRepository` (Room, explicit migration v1→v2),
  ready for Supabase sync; `supabase/migrations` includes matching tables with row-level security
  keyed on the signed-in user's id.
- No sample data. Every authorship check (edit/delete) is enforced in the repository, not just
  hidden in the UI. Unit tests cover threading, permissions, validation and password rules.
- Accessibility: content descriptions on every action, 48dp targets, TalkBack-readable threads.
- Haptics: like toggle, post/comment sent, delete, errors.
