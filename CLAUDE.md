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

## Architecture

This is a single-module Android app (Kotlin + Jetpack Compose) targeting API 24+.

**Data layer** — Room database (`brainrot_tracker_db`) with three tables:
- `DailyLog` — per-day reel counts and minutes per platform
- `UserLimits` — user-configured daily reel/minute limits per platform
- `StreakRecord` — streak history (underLimit flag + freeze support)

All DB access goes through `UsageRepository`, which is instantiated directly in `AndroidViewModel` subclasses (no DI framework). Flows are exposed as `StateFlow` via `stateIn(WhileSubscribed(5000))`.

**UI layer** — MVVM with Jetpack Compose. Navigation uses `androidx.navigation3` (Navigation 3 alpha) with serializable `NavKey` data objects (`NavigationKeys.kt`). Navigation state lives in `MainNavigation()` inside `Navigation.kt` — a single `rememberNavBackStack` drives the whole app. Bottom nav is a custom floating pill; the onboarding screen hides the bottom bar.

Screens: `Onboarding → Dashboard / Stats / Limits / Streaks`. Each screen has a matching `*ViewModel`.

**Reel detection** — `ReelCounterService` is an `AccessibilityService` that listens for `TYPE_WINDOW_CONTENT_CHANGED` and `TYPE_VIEW_SCROLLED` events. It inspects the view hierarchy via BFS to detect full-screen single-column pagers (Instagram Reels, Snapchat Spotlight) or uses signature-based tracking (YouTube Shorts). TikTok is handled by scroll direction alone. It debounces counts (1.2 s) and rate-limits tree inspections (0.8 s).

**Floating overlay** — `FloatingCounterService` is a foreground service (`FOREGROUND_SERVICE_SPECIAL_USE`) that draws a `SYSTEM_ALERT_WINDOW` overlay bubble showing the current reel count and limit. Started/stopped by `ReelCounterService`.

**Widget** — `BrainRotWidget` (Glance) + `BrainRotWidgetReceiver` for a home screen widget showing today's stats.

**Theme** — Dark navy/cyan design system defined in `theme/Color.kt`. A separate warm/terracotta palette (`Warm*` colors) is used only for the onboarding screen.

**Screen prototypes** — The `Screen N` and `Screen N dark` folders are standalone Vite/React/Tailwind projects (shadcn/ui) used for UI prototyping. They are not part of the Android build.

## Key Permissions

The app requires these special permissions that must be granted manually by the user:
- `BIND_ACCESSIBILITY_SERVICE` — for reel detection (guides the onboarding flow)
- `SYSTEM_ALERT_WINDOW` — for the floating counter overlay
- `PACKAGE_USAGE_STATS` — for screen-time data

Onboarding checks whether the accessibility service is already enabled and skips to `Dashboard` if it is.

## Adding a New Platform

1. Add an entry to `Platform` enum (`data/model/Platform.kt`) with its package name, display name, and emoji.
2. Add corresponding columns to `DailyLog` entity and increment `AppDatabase` version.
3. Add `increment*` and `addMinutes*` DAO queries in `DailyLogDao`.
4. Wire up the new cases in `UsageRepository.incrementReelCount` and `addMinutes`.
5. If the platform's reel UI needs special handling, add a branch in `ReelCounterService`.
