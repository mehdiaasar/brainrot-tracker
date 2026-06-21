This is the last chapter. We've explored every room of the house separately — the database, the services, the screens, the notifications. Now let's watch one person walk through the whole house in a single trip: from a thumb-flick on a reel all the way to a streak being recorded at midnight. Then we'll close with a plain-English glossary you can flip back to whenever a term trips you up.

## One reel, end to end

Imagine you open Instagram and swipe up to the next reel. Here is everything LoopOut does, in order, told as one continuous story. Each step names the real file and method so you can go read it yourself.

### Step 1 — Android wakes up our service

LoopOut's `ReelCounterService` is an **AccessibilityService** — a background service that the Android system feeds a stream of UI events whenever something changes on screen. The moment Instagram redraws after your swipe, Android calls our `onAccessibilityEvent`:

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    event ?: return
    checkDayRollover()
    val pkg = event.packageName?.toString() ?: return
    val platform = Platform.fromPackageName(pkg) ?: return

    // We're inside a tracked app: make sure the overlay service is up. Cheap no-op once running.
    startOverlayService()
```

Line by line: if the event is null we bail. We check whether the day has rolled over (more on that at the end). We read which app sent the event (`event.packageName`) and map it to one of our four tracked `Platform`s (Instagram, YouTube, TikTok, Snapchat). If the app isn't one we track, `Platform.fromPackageName` returns null and we return immediately. Otherwise we make sure the floating overlay service is running. (`ReelCounterService.kt`)

> 💡 **Concept —** The service does *not* run continuously polling Instagram. It is event-driven: Android pushes events to it. That's far cheaper on battery than constantly asking "what's on screen now?"

### Step 2 — Deciding the event is worth inspecting

A single swipe can fire dozens of events. We only act on a handful of event types, and for content changes we only act on a "subtree" change:

```kotlin
AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
    val isSubtree = (event.contentChangeTypes and
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
    if (isSubtree) {
        handleContentChanged(event, platform, pkg)
    }
}
```

`handleContentChanged` then **rate-limits** itself — it refuses to inspect the screen more than once every 800 ms (`INSPECTION_COOLDOWN_MS`). Reading the whole view tree is expensive, so we throttle it. (`ReelCounterService.kt`)

### Step 3 — Walking the view tree (BFS)

To know *which* reel is showing, we read Instagram's on-screen view hierarchy. We do a **breadth-first search (BFS)** — visit the root, then all its children, then their children — looking for Instagram's reels pager and the active reel's author and caption:

```kotlin
"com.instagram.android:id/clips_viewer_view_pager" -> {
    inViewer = true
    ...
}
"com.instagram.android:id/clips_author_username" ->
    if (username == null) username = node.text?.toString()
"com.instagram.android:id/clips_caption_component" ->
    if (caption == null) caption = node.text?.toString()
```

If a *visible* `clips_viewer_view_pager` is present, we know you're in the Reels viewer (not the home feed, and not the Stories viewer, which uses different IDs). The author handle plus caption become the reel's **identity** — `"ig:${username ?: ""}|${caption ?: ""}"` — and when no text is readable, the code falls back to the pager child's row index instead (`"ig:idx:$rowIndex"`). (`probeInstagram` in `ReelCounterService.kt`)

> 💡 **Concept —** An *identity* is a fingerprint that stays the same for one reel and changes when a different reel appears. Counting by identity means watching the same reel twice (scrolling back up) never double-counts.

### Step 4 — Dwell and debounce before counting

A new identity doesn't count instantly. `onActiveReel` starts a **dwell** timer (`DWELL_MS = 1500L`): the reel must stay on screen for 1.5 seconds before it counts, so flicking past five reels in a second doesn't rack up five.

If the dwell completes and the identity isn't already in the seen-set, we call `tryCountReel`, which applies a second guard — a **debounce** (`DEBOUNCE_MS = 1200L`) so two counts can't land within 1.2 seconds:

```kotlin
private fun tryCountReel(platform: Platform, pkg: String, source: String) {
    val now = System.currentTimeMillis()
    if (now - (lastCountedMs[pkg] ?: 0L) < DEBOUNCE_MS) {
        Log.d(TAG, "  ⏳ debounced ($source)")
        return
    }
    lastCountedMs[pkg] = now

    val newCount = (reelCounts[pkg] ?: 0) + 1
    reelCounts[pkg] = newCount
```

We bump the in-memory count for this platform. (`ReelCounterService.kt`)

### Step 5 — Writing to the database through the repository

Now the count must be persisted. The service launches a coroutine and calls the **repository**:

```kotlin
serviceScope.launch {
    repository.incrementReelCount(platform)
    val log = repository.getTodayLogSnapshot()
    val limits = repository.getLimitsSnapshot()
    val health = if (log != null) repository.calculateBrainHealth(log, limits) else 100
    withContext(Dispatchers.Main) {
        showBubbleAndResetTimer(log, limits, health)
        evaluateBlocking(platform, pkg)
    }
    log?.let { notificationHelper.checkMilestone(it.getTotalReels()) }
}
```

Inside `UsageRepository.incrementReelCount`, the repository makes sure today's row exists, then routes to the right column via the **DAO**:

```kotlin
suspend fun incrementReelCount(platform: Platform) {
    ensureTodayLogExists()
    val date = today()
    when (platform) {
        Platform.INSTAGRAM -> dailyLogDao.incrementInstagramReels(date)
        Platform.YOUTUBE -> dailyLogDao.incrementYoutubeShorts(date)
        Platform.TIKTOK -> dailyLogDao.incrementTiktokVideos(date)
        Platform.SNAPCHAT -> dailyLogDao.incrementSnapchatSpotlights(date)
    }
}
```

That `incrementInstagramReels(date)` runs a SQL `UPDATE` against the **Room** database, adding 1 to today's Instagram column. (`UsageRepository.kt`)

> 💡 **Concept —** `suspend` functions like `incrementReelCount` can pause without blocking the thread. `serviceScope` is built on `Dispatchers.IO`, so the database write runs on a background thread and never freezes the UI.

### Step 6 — Three things happen off that one write

From that single coroutine, three downstream effects ripple out:

| Effect | Code | What the user sees |
|---|---|---|
| Floating HUD updates | `showBubbleAndResetTimer` | The bubble's number ticks up |
| Blocking is re-checked | `evaluateBlocking` | A scrim may appear if you hit the limit |
| Milestone alert | `notificationHelper.checkMilestone` | A notification at 25/50/75/100/150/200 reels |

`calculateBrainHealth` recomputes the 0–100 score from reels-vs-limits; `evaluateBlocking` compares your *total* reels across all apps against the limit:

```kotlin
private fun evaluateBlocking(platform: Platform, pkg: String) {
    val prefs = getSharedPreferences(FloatingCounterService.PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(Prefs.BLOCKING_ENABLED, false)) return
    // Fall back to the entity default so blocking works before any limit was saved
    val limit = limitsCache.values.maxOrNull() ?: 30
    val total = reelCounts.values.sum()
    if (total >= limit) {
        val mode = BlockingMode.fromPref(prefs.getString(BlockingMode.PREF_KEY, null))
        FloatingCounterService.instance?.showBlockingOverlay(platform, limit, mode)
    }
}
```

Note the fallback: if no limit is saved yet, it uses 30. The limit treated as the cap is the *maximum* of the cached per-platform limits — the Limits screen writes the same slider value to every platform, so this is effectively one global cap. (`ReelCounterService.kt`)

### Step 7 — The Dashboard and widget update themselves

We never tell the Dashboard "a reel was counted." Instead, the repository exposes the data as a **Flow**:

```kotlin
fun getTodayLog(): Flow<DailyLog?> = dailyLogDao.getByDate(today())
```

Room emits a new value down this Flow every time today's row changes. The Dashboard's ViewModel collects it into a **StateFlow**, and Compose **recomposes** the screen automatically. The Glance **widget** reads the same data. The database is the single source of truth; everyone downstream just listens. (`UsageRepository.kt`)

> 💡 **Concept —** This is the heart of reactive UI. Write once to the database; every screen that's watching updates on its own. No manual "refresh the screen" calls anywhere.

### Step 8 — Midnight rollover and streaks

Every accessibility event calls `checkDayRollover()`. Most of the time today's date matches the stored `currentDay` and it returns instantly. But the first swipe after midnight triggers the rollover: in-memory counts and all the dedup state are cleared, then a coroutine evaluates streaks and (if exactly one day passed) shows the recap and kicks a cloud sync:

```kotlin
serviceScope.launch {
    repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
    // Recap only makes sense right after the day ended, not days later
    if (LocalDate.parse(previousDay).plusDays(1).toString() == today) {
        val log = repository.getLogSnapshot(previousDay)
        ...
        notificationHelper.showDailySummary(...)
    }
    notificationHelper.resetDailyMilestones()
    // Push the finished day to the cloud (no-op unless signed in with backup on)
    try { UsageSyncManager(applicationContext, repository).syncIfEnabled() } catch (_: Exception) {}
}
```

`evaluateStreaksUpTo` walks every unevaluated past day and writes a `StreakRecord`. A day counts as under-limit if there's no log at all (phone untouched) or every platform stayed within its limit:

```kotlin
val underLimit = log == null ||
    Platform.entries.all { log.getReelsForPlatform(it) <= limitFor(it) }
```

Historical days are judged against the *current* limits — limits aren't versioned. The cloud sync is optional and a no-op unless you signed in and turned backup on — hence the empty `catch`. (`UsageRepository.kt`, `ReelCounterService.kt`)

That's the whole journey: one thumb-flick becomes a tree walk, a debounced count, a database row, a reactive UI update, maybe a notification or a block, and eventually a streak record at midnight.

## Glossary

A plain-English definition of the recurring terms across this book, alphabetically.

| Term | Definition |
|---|---|
| **Accessibility­Service** | A background service Android feeds UI events to so it can assist users (or, here, detect reels). LoopOut's `ReelCounterService` is one; it requires the user to grant `BIND_ACCESSIBILITY_SERVICE`. |
| **Activity** | A single full-screen entry point of an app. `MainActivity` hosts the whole Compose UI. |
| **adb** | Android Debug Bridge — a command-line tool to talk to a device/emulator (install APKs, read logs). |
| **AppOpsManager** | A system service for checking app-level permissions like usage-stats access; `ScreenTimeHelper.hasPermission` uses it. |
| **BFS (breadth-first search)** | A way to walk a tree level by level (root, then children, then grandchildren). Used to scan the view hierarchy for the reel pager. |
| **BOM (Bill of Materials)** | A Gradle dependency that pins a whole family of libraries (e.g. Compose) to compatible versions so you don't list each version by hand. |
| **Composable** | A Kotlin function marked `@Composable` that describes a piece of UI. Compose calls it to draw, and re-calls it when its inputs change. |
| **CompositionLocal** | A way to pass data implicitly down the Compose tree without threading it through every function. The design system exposes `AppTheme.colors` this way. |
| **Coroutine** | A lightweight unit of concurrent work that can pause (`suspend`) and resume without blocking a thread. Started with `launch` inside a `CoroutineScope`. |
| **DAO (Data Access Object)** | A Room interface whose methods map to SQL queries (e.g. `incrementInstagramReels`). You write the signature; Room generates the code. |
| **DataStore** | A modern key-value preference store with coroutine/Flow APIs. `AppPreferences` uses it for Google sign-in state. |
| **Debounce** | Ignoring repeated triggers that arrive too close together. Reels can't be counted within 1.2 s of each other (`DEBOUNCE_MS`). |
| **DI (Dependency Injection)** | A pattern where objects receive their collaborators from outside. LoopOut deliberately uses *no* DI framework; `UsageRepository` is constructed directly. |
| **Dwell** | A required minimum time a reel must stay on screen before counting (1.5 s, `DWELL_MS`), so quick skips don't count. |
| **Entity** | A Kotlin class annotated `@Entity` that becomes a Room table row. `DailyLog`, `UserLimits`, and `StreakRecord` are entities. |
| **Firestore** | Google's cloud NoSQL database. The optional sync writes one aggregate doc per day to `users/{uid}/dailyLogs/{date}`. |
| **Flow** | A stream of values over time from coroutines. Room exposes query results as `Flow`, emitting a new value whenever the data changes. |
| **Foreground Service** | A long-running service with a persistent notification so the system won't kill it. `FloatingCounterService` is one (`FOREGROUND_SERVICE_SPECIAL_USE`). |
| **Glance** | Jetpack's Compose-style API for building home-screen widgets. `BrainRotWidget` is built with it. |
| **Heartbeat** | LoopOut's 1-second polling loop in `ReelCounterService` that keeps the HUD visible, re-checks blocking, and detects when you leave a tracked app (the service gets no events from untracked apps). |
| **High-water mark** | The furthest pager index reached this session. Counting only past it means scrolling back never recounts. Used for Snapchat and the Instagram pager-index fallback (TikTok counts by plain forward scroll instead). |
| **KSP (Kotlin Symbol Processing)** | A compiler plugin that generates code at build time. Room uses it to turn your DAO interfaces into real implementations. |
| **MVVM (Model-View-ViewModel)** | The architecture: the *View* (Composables) observes a *ViewModel*, which exposes state from the *Model* (repository/database). |
| **NavKey** | A serializable object identifying a screen in Navigation 3 (e.g. `Dashboard`, `Limits`). The back stack is a list of NavKeys. |
| **Recomposition** | Compose re-running a Composable because its inputs changed, to update what's drawn. Triggered automatically by new StateFlow values. |
| **Repository** | A class that hides data sources behind clean methods. `UsageRepository` is the single gateway to the database for the whole app. |
| **Room** | Jetpack's SQLite wrapper. You define entities and DAOs; Room generates the database (`brainrot_tracker_db`). |
| **Shared­Preferences** | A simple key-value file for small settings. The `brainrot_prefs` file holds theme mode, blocking flags, and snooze timestamps. |
| **StateFlow** | A Flow that always holds a current value, ideal for UI state. ViewModels expose it via `stateIn(WhileSubscribed(5000))`. |
| **suspend** | A keyword marking a function that can pause and resume inside a coroutine without blocking the thread (e.g. database writes). |
| **SYSTEM_ALERT_WINDOW** | The "draw over other apps" permission, needed for the floating counter bubble and the blocking scrim. |
| **UsageStatsManager** | A system service that reports per-app usage events. `ScreenTimeHelper` reads its RESUMED/PAUSED events live for screen-time. |
| **ViewModel** | A lifecycle-aware holder of UI state and logic, surviving configuration changes. Each screen has one (e.g. `DashboardViewModel`). |
| **WindowManager** | The system service used to add/remove overlay views on screen. `FloatingCounterService` uses it to draw the bubble and scrim. |
| **WorkManager** | Jetpack's scheduler for deferrable background jobs. LoopOut deliberately *avoids* it — streaks are evaluated lazily instead. |

That's the map and the dictionary. You now have both the bird's-eye flight path of a single reel and a reference for every term that flew past along the way. Welcome to Android development — go build something.
