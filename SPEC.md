# BrainRot Tracker — Project Spec Sheet

A plain-language technical specification of every feature in the app: what it does, how it's
built, which libraries and Android capabilities it relies on, and where the code lives.
Written assuming no prior app-development knowledge.

---

## 1. What the app is

BrainRot Tracker is an Android app that watches how many short videos (Instagram Reels,
YouTube Shorts, TikTok videos, Snapchat Spotlight) you scroll through each day, shows you the
damage in a friendly way (a cartoon brain that "rots" as you scroll), and helps you stop —
with daily limits, a blocking screen, streaks, and notifications. Optionally, you can sign in
with Google to back up your daily totals to the cloud.

---

## 2. The technology stack (the tools the app is built with)

| Technology | What it is, in plain words |
|---|---|
| **Kotlin** | The programming language the app is written in. It's Google's recommended language for Android — think of it as the "English" the instructions are written in. |
| **Jetpack Compose** | The toolkit used to draw every screen. Instead of designing screens in a separate visual editor, you describe the UI in Kotlin code ("a column containing a title, then a slider…") and Compose draws it and automatically redraws it when data changes. |
| **Gradle** | The build system. It takes all the source code, images, and libraries, and assembles them into an installable app file (an APK). Commands like `./gradlew assembleDebug` run it. |
| **Android SDK (API 24–36)** | The set of capabilities Android itself provides (notifications, databases, sensors…). The app runs on Android 7.0 (API 24) and newer, and is built against Android 16 (API 36). |
| **Room** | A library that manages the app's local database (see §12). |
| **Firebase** | Google's cloud backend service, used for sign-in and the optional cloud backup (see §11). |

**Architecture in one paragraph:** the app follows **MVVM** (Model–View–ViewModel). Each
screen (the *View*, written in Compose) is dumb — it only displays data. A *ViewModel* class
feeds it that data and survives screen rotations. The ViewModels get their data from a single
*Repository* (`UsageRepository`), which is the only thing allowed to talk to the database.
This separation means UI code never touches storage directly, which keeps bugs contained.

---

## 3. Feature: Counting reels you scroll (the core trick)

**What it does:** Counts every short video you swipe past inside Instagram, YouTube, TikTok
and Snapchat — even though those are other companies' apps the tracker has no access to
normally.

**The Android capability that makes it possible — Accessibility Service:**
Android has a feature built for users with disabilities: an *AccessibilityService* can "see"
what's on screen in any app (the same mechanism screen readers for blind users rely on). An
app can register such a service, and once the user manually enables it in system settings,
Android streams it events like "the window changed" or "a list scrolled," along with a
description of the views on screen. BrainRot Tracker uses this — and only listens to the four
social apps, declared in `accessibility_service_config.xml`, so it literally receives nothing
about any other app.

**How counting actually works (`service/ReelCounterService.kt`):**
- When you scroll, Android sends the service a `TYPE_VIEW_SCROLLED` or
  `TYPE_WINDOW_CONTENT_CHANGED` event.
- The service then inspects the "view tree" — a machine-readable outline of everything on
  screen — looking for the signature of a reels player: a **full-screen, single-column,
  scrollable pager** (a list that shows exactly one item at a time). A normal feed shows many
  items at once, so feeds don't trigger counts.
- Each platform needs slightly different handling:
  - **Instagram / Snapchat:** find the pager and watch its *current item index*; when the
    index increases, you swiped to the next reel → count +1.
  - **YouTube Shorts:** YouTube's pager doesn't expose an index, so the service builds a
    "signature" of the current video (creator handle + title + button labels) and counts when
    the signature changes to one it hasn't seen recently.
  - **TikTok:** the whole app is short videos, so every forward scroll counts.
- Two safety valves prevent over-counting: a **debounce** (two counts on the same platform
  must be ≥1.2 seconds apart) and a **rate limit** (the view tree is inspected at most every
  0.8 seconds, because walking it is expensive).
- Every confirmed count is written to the local database via `UsageRepository.incrementReelCount`.

**Libraries used:** none beyond the Android SDK itself — this is raw platform capability.
Kotlin **coroutines** (a way to run work in the background without freezing the screen) are
used so database writes never block event handling.

**Privacy note:** because reading screen content is sensitive, Google Play requires a
"prominent disclosure" — the app shows a consent dialog explaining exactly what it reads
*before* sending you to enable the service (see §13).

---

## 4. Feature: The floating counter bubble (HUD)

**What it does:** While you're scrolling reels, a small pill floats on top of Instagram/
TikTok/etc. showing a brain face and today's count. Tap it for a per-platform breakdown
popup; drag it to move it.

**The Android capability — overlay windows (`SYSTEM_ALERT_WINDOW`):**
Normally an app can only draw inside its own window. Android has a special permission,
"Display over other apps," that lets an app add views on top of *everything* (this is how
Facebook Messenger's chat heads worked). The user grants it once in onboarding.

**How it's built (`service/FloatingCounterService.kt`):**
- It's a **foreground service** — a piece of the app that keeps running with no screen open.
  Android requires foreground services to show a permanent notification ("BrainRot Tracker
  Active") so the user always knows it's alive; type `specialUse` is declared in the manifest.
- The bubble itself is built with classic Android views (not Compose — Compose can't easily
  render into overlay windows) and added via the `WindowManager` system service.
- The brain face is **drawn by hand on a Canvas** (`BrainFaceView`): circles for the brain
  lobes, arcs for the mouth. Its color and expression interpolate from pink/happy to
  grey/droopy as brain health drops. The platform logos in the popup are also hand-drawn
  Canvas paths, so no trademarked image files ship with the app.
- A 1-second "heartbeat" in `ReelCounterService` polls which app is in the foreground:
  it shows the pill while a reel feed is open, hides it when you leave, and (see §5)
  re-checks blocking. Polling is needed because the accessibility service receives **no
  events at all** from apps outside its declared package list — it can't "hear" you leave.
- Appearance (light/dark, size) is re-read from saved preferences each time it shows, so the
  Settings sliders take effect immediately.

---

## 5. Feature: App blocking with three modes

**What it does:** When you hit your daily limit on a platform, a full-screen dark overlay
("Limit Reached") covers the app. How strict it is depends on a mode you pick in Settings:

| Mode | Behavior |
|---|---|
| **Hard** | The overlay reappears *every time* you open the app until midnight. The only button is **"Close app"**, which sends you to the home screen. |
| **Snooze** | Dismissing buys you 5 more minutes, then the overlay returns. The 5-minute deadline is saved to disk, so restarting the phone doesn't reset it. |
| **Remind** | The overlay shows once each time you open the app; "Got it" dismisses it for that visit. |

**How it works:**
- `evaluateBlocking()` in `ReelCounterService` is the single gatekeeper. It runs at three
  moments: every counted reel, every time a tracked app comes to the foreground (Android
  sends a `TYPE_WINDOW_STATE_CHANGED` accessibility event for that), and on every heartbeat
  tick. If `count ≥ limit` it tells `FloatingCounterService` to show the blocking scrim,
  passing the mode.
- The scrim is another `SYSTEM_ALERT_WINDOW` overlay — a 87%-black full-screen layer with a
  card on top, same mechanism as the bubble (§4).
- Per-mode suppression lives in `FloatingCounterService.showBlockingOverlay`: Hard is never
  suppressed; Snooze checks a `snooze_until_<PLATFORM>` timestamp in saved preferences;
  Remind keeps an in-memory "dismissed this session" set that the heartbeat clears when you
  leave the app.
- **"Close app"** uses a superpower only accessibility services have:
  `performGlobalAction(GLOBAL_ACTION_HOME)` — programmatically pressing the Home button.
- At midnight the day-rollover logic (§8) zeroes the counts, which automatically lifts all
  blocks for the new day.

**The mode selector UI** is on the Settings/Goals screen (`ui/screens/limits/LimitsScreen.kt`),
a three-button segmented control written in Compose; the choice is stored as a string in
SharedPreferences (§12).

---

## 6. Feature: Screen time per app

**What it does:** Shows how many minutes you actually spent inside each social app today,
yesterday, and over the week — the same numbers Android's own Digital Wellbeing shows.

**The Android capability — UsageStatsManager:**
Android keeps a system-wide log of app usage. Apps can query it after the user grants the
special "Usage access" permission (granted via system settings; checked through
`AppOpsManager` since there's no normal permission dialog for it).

**How it's implemented (`data/util/ScreenTimeHelper.kt`):**
- Instead of asking Android for pre-aggregated totals (its `queryAndAggregateUsageStats` API
  is known to leak time across day boundaries — a session that started yesterday gets counted
  into today), the helper requests the **raw event log**: every `ACTIVITY_RESUMED` (app came
  to front) and `ACTIVITY_PAUSED/STOPPED` (app left front) event with timestamps.
- It then replays those events like a stopwatch: resume = start timing, pause = stop and add
  the elapsed milliseconds. Sessions still running at the window edge are clipped to the
  edge. This gives exact midnight-to-midnight numbers.
- These minutes are **never stored in the app's database** — they're recomputed live from the
  system log whenever a screen needs them (Dashboard refreshes every 30 seconds). The system
  is the source of truth.

---

## 7. Feature: Brain health score & the brain mascot

**What it does:** A 0–100 score summarizing how "fried" your brain is today, shown as a big
ring on the Dashboard and embodied by a cartoon brain that gets sadder and greyer as the
score drops (Elite → Healthy → Tired → Overstimulated → Brainrot).

**How the score is computed (`UsageRepository.calculateBrainHealth`):**
Each of the four platforms contributes up to 25 points. For each platform the app compares
your reel count against your reel limit and your live screen-time minutes (from §6) against
your minute limit; going 2× over a limit zeroes that platform's points. The result is written
back to the database so history is kept per day.

**How the mascot is drawn:** two implementations of the same character — a Compose version
(`ui/components/BrainMascot.kt`) for in-app screens and a Canvas version inside
`FloatingCounterService` for the overlay (§4). Both interpolate colors, eye openness, and
mouth curve from the health value. No image assets; it's all geometry, so it scales sharply
at any size.

---

## 8. Feature: Streaks & day rollover

**What it does:** Every day you stay under your limits on all platforms earns a streak day,
shown as a calendar and current/longest streak counters on the Streaks screen. Days you
didn't scroll at all count as wins.

**How it's implemented — "lazy catch-up" instead of an alarm:**
Phones kill background work aggressively, so instead of scheduling a midnight job (which
might never fire), the app evaluates streaks *opportunistically*:
- `UsageRepository.evaluateStreaksUpTo(yesterday)` walks every day that hasn't been judged
  yet — from the last evaluated date up through yesterday — reads that day's log from the
  database, compares each platform's count to the limits, and writes a `StreakRecord` row
  (`date`, `underLimit`, `streakDay`). A day with no log row at all counts as under-limit.
- This runs from three triggers, so it can't be missed for long: when the accessibility
  service starts, when the app is opened (`MainActivity`), and at **day rollover**.
- **Day rollover** (`ReelCounterService.checkDayRollover`): on every accessibility event the
  service cheaply compares "what day is it now" with "what day do my in-memory counters
  belong to." On mismatch (first scroll after midnight) it: resets in-memory counts (which
  also lifts blocking), evaluates the finished day's streak, shows the daily-summary
  notification (§9), re-arms milestone notifications, and uploads to the cloud if enabled
  (§11).

The Streaks screen itself is Compose: a month calendar (`ui/components/StreakCalendar.kt`)
colored green/red per day from the records, plus achievement cards.

---

## 9. Feature: Notifications

**What it does:** Three kinds of notifications, each in its own channel (Android's category
system that lets users mute one kind without muting all):

1. **Milestone alerts** — "50 reels! Time for a break?" at 25/50/75/100/150/200 reels per day
   (fired from the counting path; reset at midnight).
2. **Daily summary** — a recap of yesterday (reels, screen time, brain health) shown at day
   rollover, only when the app actually saw the previous day end (gap of exactly one day).
3. **Service notification** — the permanent low-priority "tracking active" notice Android
   requires for the foreground overlay service (§4).

**How:** `notification/NotificationHelper.kt` uses Android's `NotificationManager` and the
`NotificationCompat` support library (handles old-Android differences). On Android 13+ the
system requires apps to *ask* before posting notifications — onboarding fires the real
permission dialog via the Activity Result API (`ActivityResultContracts.RequestPermission`),
falling back to a settings deep-link if the user keeps declining.

---

## 10. Feature: Home screen widget

**What it does:** A small widget you can place on your home screen showing today's reel count
and brain health without opening the app.

**How:** Built with **Glance** (`widget/BrainRotWidget.kt`), Google's library that lets you
write widgets in Compose-style syntax even though widgets actually use a restricted remote
rendering system (`RemoteViews`) under the hood. `BrainRotWidgetReceiver` is the
`BroadcastReceiver` — a component Android wakes up when it's time to (re)draw the widget —
declared in the manifest with `APPWIDGET_UPDATE`. The widget reads today's row straight from
the Room database. It's a snapshot, not live: it refreshes when Android asks it to, not on
every scroll.

---

## 11. Feature: Google sign-in & cloud backup (optional)

**What it does:** Entirely optional — the app is fully functional anonymously. Signing in
with Google lets the app upload **one small record per finished day** (per-platform counts,
minutes, brain health, limits, streak status) to your private cloud space, for backup and for
the developer's aggregate analysis. A consent dialog gates it, and a Settings toggle turns it
off anytime.

**The pieces:**
- **Credential Manager + Google ID** (`androidx.credentials`, `googleid` libraries): Android's
  modern sign-in sheet. It returns a cryptographic *ID token* proving which Google account
  you picked. UI: `ui/screens/signin/GoogleSignInScreen.kt`, offered once after onboarding
  (skippable) and from the Account card in Settings.
- **Firebase Authentication** (`firebase-auth`): the token is exchanged for a Firebase user
  account (`GoogleSignInViewModel`), giving the app a stable user id (`uid`).
- **Cloud Firestore** (`firebase-firestore`): a cloud document database. `data/sync/
  UsageSyncManager.kt` writes each day to `users/{uid}/dailyLogs/{date}`. Writes are
  "fire-and-forget" because Firestore queues them on the device and auto-retries when
  online. A `last_synced_date` marker prevents re-uploading. Security rules (set in the
  Firebase console) must restrict `users/{uid}/**` so each user can only touch their own data.
- **Graceful degradation:** the Firebase build plugin is applied only if the config file
  `app/google-services.json` exists, and every Firebase call first checks
  `FirebaseApp.getApps()` — so the project builds, runs, and signs in locally even before the
  Firebase project is created.
- Sign-in state (email/name) is remembered locally in a small **DataStore** preferences file
  (`data/local/prefs/AppPreferences.kt`).

**Privacy/Play-policy posture:** data derived from the Accessibility API leaving the device
is exactly what Google Play scrutinizes hardest. That's why upload requires sign-in **plus**
an explicit consent dialog, only daily totals are sent (never screen content), and the privacy
policy must disclose it.

---

## 12. How data is stored on the phone

Three storage mechanisms, each suited to a different job:

1. **Room database** (`data/local/db/`) — the main store. Room is a library that wraps
   SQLite, the tiny relational database engine built into every Android phone. You define
   Kotlin classes as tables (*entities*) and interfaces with annotated SQL (*DAOs*), and Room
   generates the plumbing. Three tables:
   - `DailyLog` — one row per day: reel count per platform + brain health score.
   - `UserLimits` — one row per platform: daily reel/minute limits.
   - `StreakRecord` — one row per evaluated day: under-limit flag + streak day number.
   Reads are exposed as **Flows** — live pipes that re-emit whenever the data changes, which
   is how screens update instantly when a reel is counted. Schema is at version 2; while
   unpublished, schema changes just wipe and recreate the database (destructive migration).
2. **SharedPreferences** (`brainrot_prefs` file) — simple key-value storage for settings that
   services must read synchronously: theme mode, blocking on/off, blocking mode, snooze
   deadlines, HUD size, cloud-sync consent, disclosure-accepted flag.
3. **DataStore** (`AppPreferences`) — the modern, coroutine-friendly key-value store; holds
   Google sign-in state (signed-in flag, email, name, photo URL).

---

## 13. Onboarding & permissions

**What it does:** First-launch screen that walks the user through granting the four special
permissions, with live GRANTED/PENDING badges and a progress bar
(`ui/screens/onboarding/OnboardingScreen.kt`).

| Permission | Why the app needs it | How it's granted |
|---|---|---|
| Accessibility Access | Reel counting (§3) | System settings page. A **prominent-disclosure consent dialog** (Play policy requirement) appears first, explaining what is read and that it stays on-device unless backup is enabled. |
| Display over other apps | Floating bubble + blocking scrim (§4–5) | System settings page |
| Notifications | Milestones & daily summary (§9) | Real runtime dialog on Android 13+, settings page otherwise |
| Usage access | Screen-time minutes (§6) | System settings page |

The screen re-checks all four every time you return to it (lifecycle `ON_RESUME` observer),
so badges flip to GRANTED the moment you come back from settings. Everything is skippable;
onboarding is bypassed entirely on later launches if the accessibility service is already on.

---

## 14. Screens & navigation

Five screens plus sign-in, with a bottom tab bar (Dashboard / Stats / Goals / Streaks):

- **Dashboard** — mascot, today's count, health ring, screen time, insight cards.
- **Stats** — weekly bar chart, totals, best/worst day, per-platform ranking.
- **Goals (Settings)** — limit sliders, blocking toggle + mode, theme, account, HUD size.
- **Streaks** — calendar + streak counters + achievements.
- **Onboarding / Google Sign-In** — full-screen flows that hide the tab bar.

**Navigation** uses **Navigation 3** (`androidx.navigation3`), Google's Compose-native
navigation library: each destination is a tiny serializable key object (`NavigationKeys.kt`),
and a single back-stack list in `Navigation.kt` drives which screen is showing. Each screen
has a matching ViewModel created with the lifecycle-viewmodel-compose library.

**Theming:** System/Light/Dark, chosen in Settings. `theme/ThemeController.kt` holds the
choice as Compose state (so every screen recomposes instantly when changed) backed by
SharedPreferences (so it survives restarts). All colors come from a warm cream/terracotta
palette in `theme/Color.kt` with parallel dark/light variants; the overlay service reads the
same preference so the bubble matches the app theme.

---

## 15. Production / release engineering

Behind-the-scenes work that makes the app shippable to Google Play:

- **R8 / ProGuard** (`app/proguard-rules.pro`): release builds are *minified* — unused code
  stripped, names shortened — and resources shrunk, which cuts APK size and obfuscates code.
  Custom rules strip debug logging (`Log.v/d/i`) from release builds and protect the
  serialization code Navigation 3 needs.
- **Core library desugaring** (`desugar_jdk_libs`): the app uses modern Java time APIs
  (`java.time.LocalDate`) that only exist on Android 8+; desugaring rewrites them at build
  time so they also work on Android 7, the app's minimum.
- **Release signing**: Android apps must be cryptographically signed. Credentials live in an
  untracked `keystore.properties` file (template: `keystore.properties.example`); without it
  the build still works, just unsigned.
- **Application ID**: `io.github.aasarmehdi.brainrot` (placeholder — permanent once on Play,
  so confirm before first upload).
- **Hardening**: the overlay service is not exported (other apps can't start or spoof it);
  unused permissions were removed; the accessibility service description fully discloses data
  use.
- **Still required manually before publishing**: create the Firebase project (+
  `google-services.json` + real web client ID), Firestore security rules, a release keystore,
  and a hosted privacy-policy URL disclosing the optional cloud upload.

---

## 16. File map (where to find things)

```
app/src/main/java/com/example/brainrottracker/
├── MainActivity.kt            App entry point; kicks streak catch-up + cloud sync
├── Navigation.kt              Back stack, bottom bar, screen wiring
├── NavigationKeys.kt          One key object per screen
├── data/
│   ├── local/db/              Room database, entities, DAOs
│   ├── local/prefs/           DataStore (sign-in state)
│   ├── model/Platform.kt      The 4 tracked platforms enum
│   ├── repository/            UsageRepository — all data logic (health, streaks)
│   ├── sync/                  UsageSyncManager — Firestore upload
│   └── util/ScreenTimeHelper.kt  UsageStats event-replay stopwatch
├── notification/              NotificationHelper — channels, milestones, summary
├── service/
│   ├── ReelCounterService.kt  Accessibility service: counting, blocking, rollover
│   ├── FloatingCounterService.kt  Overlay: bubble, popup, blocking scrim
│   └── BlockingMode.kt        HARD / SNOOZE / REMIND
├── theme/                     Colors, typography, ThemeController
├── ui/components/             Mascot, calendar, cards, logos
├── ui/screens/                dashboard / stats / limits / streaks / onboarding / signin
└── widget/                    Glance home-screen widget
```
