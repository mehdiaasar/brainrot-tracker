# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
# Build debug APK
./gradlew assembleDebug

# Install on connected device/emulator
./gradlew installDebug

# Run unit tests
./gradlew test

# Run instrumented tests (requires connected device)
./gradlew connectedAndroidTest

# Run a single test class
./gradlew test --tests "com.example.brainrottracker.YourTestClass"

# Lint
./gradlew lint
```

`adb` is not on PATH on this machine — use `~/Library/Android/sdk/platform-tools/adb`.

## Architecture

This is a single-module Android app (Kotlin + Jetpack Compose) targeting API 24+ (compileSdk/targetSdk 36).

**Data layer** — Room database (`brainrot_tracker_db`, v2, destructive fallback pre-launch) with three tables:
- `DailyLog` — per-day reel counts as one column per platform, plus `brainHealthScore`
- `UserLimits` — per-platform daily reel/minute limits
- `StreakRecord` — `date`, `underLimit`, `streakDay`

All DB access goes through `UsageRepository`, which is instantiated directly in `AndroidViewModel` subclasses (no DI framework). Flows are exposed as `StateFlow` via `stateIn(WhileSubscribed(5000))`.

Screen time is **not** stored in the DB: `ScreenTimeHelper` queries `UsageStatsManager` raw activity events (RESUMED/PAUSED) live, and Dashboard/Stats ViewModels emit those results directly. The event-based approach is deliberate — `queryAndAggregateUsageStats` bleeds sessions across window boundaries.

Two preference stores exist: `AppPreferences` (DataStore, Google sign-in state) and a `brainrot_prefs` SharedPreferences file (theme mode, `blocking_enabled`/`blocking_mode`/snooze timestamps, cloud-sync consent, HUD scale).

**Streaks** — `UsageRepository.evaluateStreaksUpTo(yesterday)` lazily writes `StreakRecord` rows for all unevaluated past days (no WorkManager). It is triggered from `ReelCounterService` day-rollover detection, service connect, and `MainActivity.onCreate`. Days with no `DailyLog` row count as under-limit; historical days are judged against current limits.

**Day rollover** — `ReelCounterService.checkDayRollover()` (called on every accessibility event) resets in-memory counts at midnight, evaluates streaks, shows the daily-summary notification (only when the gap is exactly one day), re-arms milestone notifications, and kicks a cloud sync.

**Cloud sync (optional)** — `UsageSyncManager` uploads one aggregate doc per finished day to Firestore `users/{uid}/dailyLogs/{date}`, gated on sign-in AND the `cloud_sync_enabled` consent pref. Google sign-in (`GoogleSignInScreen`, Credential Manager → Firebase Auth) is offered once after onboarding and from the Account card on the Limits screen — the app is fully functional anonymous. The `google-services` plugin is applied **only if `app/google-services.json` exists**, and all Firebase calls are guarded by `FirebaseApp.getApps()`, so the project builds and runs without a Firebase project configured.

**UI layer** — MVVM with Jetpack Compose. Navigation uses `androidx.navigation3` (Navigation 3 alpha) with serializable `NavKey` data objects (`NavigationKeys.kt`). Navigation state lives in `MainNavigation()` inside `Navigation.kt` — a single `rememberNavBackStack` drives the whole app; bottom nav hides on the onboarding screen.

Screens: `Onboarding → (optional GoogleSignIn) → Dashboard / Stats / Limits / Streaks`. Each screen has a matching `*ViewModel`. The bottom bar hides while `Onboarding` or `GoogleSignIn` is on top of the back stack.

**Reel detection** — `ReelCounterService` is an `AccessibilityService` that listens for `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_SCROLLED` events. It inspects the view hierarchy via BFS to detect full-screen single-column pagers (Instagram Reels) or uses signature-based tracking (YouTube Shorts). TikTok is handled by scroll direction alone. It debounces counts (1.2 s) and rate-limits tree inspections (0.8 s).

Snapchat Spotlight is a special case: the feed is a `SurfaceView` that exposes no scrollable list, no readable index, no per-video identity, and fires no scroll events — its accessibility tree is identical from one video to the next. So detection works differently: presence on the feed is gated on a visible `spotlight_container` (`handleSnapchatSpotlight` / `isInSnapchatSpotlight`), and each new video is counted off the one per-swipe signal Snapchat emits — a `CONTENT_CHANGE_TYPE_ENABLED` (4096) content-change when the next video activates — dwell-gated via `armSnapchatDwell` so skipped videos don't count. The Spotlight container is a *not-important* view, invisible unless `FLAG_INCLUDE_NOT_IMPORTANT_VIEWS` is set; that flag changes the window `rootInActiveWindow` returns and **breaks YouTube Shorts detection**, so `setIncludeNotImportantViews` toggles it at runtime — on only while Snapchat is foreground (driven from `TYPE_WINDOW_STATE_CHANGED`), off for every other app. Known limitation: the landing video (no swipe-in event) isn't counted, so Spotlight undercounts by ~1 per session; the ENABLED bit requires Android 14+ (API 34).

**App blocking** — `evaluateBlocking()` in `ReelCounterService` fires on every counted reel, on `TYPE_WINDOW_STATE_CHANGED` (app reopened), and on each 1 s heartbeat tick; it falls back to the default limit (30) when no `UserLimits` row exists. Three user-selectable modes (`BlockingMode`, `blocking_mode` pref, selector on Limits screen): HARD (re-blocks on every foreground entry until midnight, "Close app" only), SNOOZE (5-min grace stored as `snooze_until_<PLATFORM>` epoch millis), REMIND (once per foreground session, tracked via `remindDismissedThisSession` in `OverlayController` and cleared by `onAllTrackedAppsLeft`, which the heartbeat calls when the user leaves the tracked app). Known limitation: REMIND session-end detection is best-effort when no reel feed is open (the heartbeat stops once the user leaves all tracked apps).

**Floating overlay** — `OverlayController` is **not** a service; it is a plain controller created and owned by `ReelCounterService` (the always-alive accessibility service) and draws `SYSTEM_ALERT_WINDOW` views: a counter bubble (with expandable stats popup, a normal `TYPE_APPLICATION_OVERLAY` added via the application context so the OS keeps it off the lock screen) and a full-screen mode-aware blocking scrim (`TYPE_ACCESSIBILITY_OVERLAY`, top-most, added via the accessibility service's WindowManager). There is **no foreground service** — drawing from the accessibility service avoids background foreground-service starts that MIUI/HyperOS (Xiaomi/POCO) blocks and penalizes (which previously surfaced as "This service is malfunctioning" and killed reel detection). Visibility is driven by the heartbeat + reel detection in `ReelCounterService`.

**Notifications** — `NotificationHelper` manages two user-facing channels: milestone alerts (25/50/…/200 reels per day) and an end-of-day summary. (The `service_running` channel and `getServiceNotification()` builder were removed along with the foreground service.)

**Widget** — `BrainRotWidget` (Glance) + `BrainRotWidgetReceiver` for a home screen widget showing today's stats.

**Theme** — The app supports System/Light/Dark via `ThemeController`, a singleton holding the mode as Compose state backed by SharedPreferences; resolve the active state with `rememberIsDark()`. All screens use the warm palette in `theme/Color.kt`: `Warm*` colors for dark mode, `WarmLight*` for light mode. The navy/cyan colors at the top of `Color.kt` are legacy and still referenced by some components.

**Screen prototypes** — `ui:ux/` contains standalone Vite/React/Tailwind projects (shadcn/ui) used for UI prototyping (`Screen N` / `Screen N dark`). They are not part of the Android build and not tracked in git.

## Key Permissions

The app requires these special permissions that must be granted manually by the user:
- `BIND_ACCESSIBILITY_SERVICE` — for reel detection. Onboarding shows a Play-policy prominent-disclosure dialog (consent persisted as `accessibility_disclosure_accepted`) **before** opening accessibility settings.
- `SYSTEM_ALERT_WINDOW` — for the floating counter overlay and blocking scrim
- `PACKAGE_USAGE_STATS` — for screen-time data (`ScreenTimeHelper.hasPermission` checks via AppOpsManager)
- `POST_NOTIFICATIONS` — requested at runtime in onboarding on API 33+ (first tap = system dialog, later taps = settings deep link)

Onboarding checks whether the accessibility service is already enabled and skips to `Dashboard` if it is.

## Release / Play Store

- `applicationId` is `io.github.aasarmehdi.brainrot` (placeholder — confirm before first upload; immutable on Play). `namespace` intentionally stays `com.example.brainrottracker`.
- Release builds minify + shrink; `app/proguard-rules.pro` strips `Log.v/d/i` via R8 and keeps kotlinx-serialization serializers for the NavKeys. Smoke-test minified builds: Navigation 3 serialized NavKeys are the likeliest R8 breakage.
- Release signing reads `keystore.properties` (gitignored; see `keystore.properties.example`); without it the release build is unsigned.
- Firebase setup is a manual step: create the project, add SHA fingerprints, drop `google-services.json` into `app/`, and put the Web client ID into `strings.xml` (`google_web_client_id`). Firestore rules must restrict `users/{uid}/**` to `request.auth.uid == uid`.
- A hosted privacy-policy URL is required for the Play listing and the accessibility declaration form; it must disclose the optional Firestore upload of daily aggregates.

## Adding a New Platform

1. Add an entry to `Platform` enum (`data/model/Platform.kt`) with its package name, display name, and emoji.
2. Add a reel-count column to `DailyLog` and increment `AppDatabase` version.
3. Add the `increment*` DAO query in `DailyLogDao` and wire the new case in `UsageRepository.incrementReelCount`.
4. Add the package to `accessibility_service_config.xml`'s `packageNames`.
5. If the platform's reel UI needs special handling, add a branch in `ReelCounterService`.
