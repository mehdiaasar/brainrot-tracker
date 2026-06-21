The Dashboard is the home screen — the first thing the user sees after onboarding. It pulls together everything the app knows about today (reels scrolled, screen time, brain-health score, streak) and presents it through a friendly animated brain mascot whose expression changes as the day's scrolling climbs. This chapter walks through how that screen is built, starting from the data and ending at the pixels.

We will lean on the Compose + MVVM primer from Chapter 3. The short version: a **ViewModel** holds the screen's data as observable streams, and a **Composable** function reads those streams and describes what to draw. When the data changes, Compose redraws automatically. Let's see that pattern in action.

## The big picture: who talks to whom

The Dashboard is made of four source files that play distinct roles:

| File | Role |
| --- | --- |
| `DashboardViewModel.kt` | Holds all screen state as `StateFlow`s; combines database data with live screen-time numbers. |
| `DashboardScreen.kt` | The Composable UI — reads the ViewModel's state and draws the header, cards, and mascot. |
| `DashboardMood.kt` | An `enum` mapping today's usage to one of five "moods" (mascot art + copy). |
| `FocusShield.kt` | A `data class` mapping usage to a game-like "protection level". |

> 💡 **Concept — `StateFlow`.** A `StateFlow` is an observable value that always has a current value and notifies subscribers when it changes. Think of it as a live cell in a spreadsheet: anything that reads it recalculates when it updates. The UI subscribes to these cells and redraws.

## The ViewModel: turning data into observable state

`DashboardViewModel` is an `AndroidViewModel`, which is just a ViewModel that also gets a handle to the `Application` (it needs that to talk to the system's usage stats). In its `init` block it opens the database and builds the repository — there is no dependency-injection framework here, just direct construction:

```kotlin
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository
    private val app = application

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }
```

`UsageRepository` (Chapter 7) is the single gateway to the Room database. The ViewModel never touches SQL directly — it asks the repository for `Flow`s and reshapes them.

### Exposing today's data

The first piece of state is today's log row:

```kotlin
    val todayLog: StateFlow<DailyLog?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

`repository.getTodayLog()` returns a `Flow<DailyLog?>` — a cold stream straight from Room that re-emits every time today's row changes. `.stateIn(...)` converts that cold stream into a hot, always-on `StateFlow`. Its three arguments:

- `viewModelScope` — the coroutine scope tied to this ViewModel's lifetime; the subscription is cancelled when the ViewModel dies.
- `SharingStarted.WhileSubscribed(5000)` — keep the database query alive only while something is watching, plus a 5-second grace period so a quick screen rotation doesn't tear it down and rebuild it.
- `null` — the initial value before Room produces a real row.

This same `stateIn(viewModelScope, WhileSubscribed(5000), <initial>)` shape repeats for nearly every property in the file. Once you read one, you can read them all.

> 💡 **Concept — cold vs. hot streams.** A *cold* `Flow` does nothing until someone collects it, and runs fresh for each collector. A *hot* `StateFlow` runs once and shares its latest value with everyone. `stateIn` is the bridge: it collects the cold flow once and re-broadcasts it.

### Live screen time, polled every 30 seconds

Screen time is **not** stored in the database (see Chapter 9). It is read live from the system, so the ViewModel builds its own flow that re-polls on a timer:

```kotlin
    private val todayScreenTime: Flow<Map<Platform, Int>> = flow {
        while (true) {
            emit(ScreenTimeHelper.getTodayMinutesByPlatform(app))
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)
```

The `flow { ... }` builder emits a fresh per-platform minutes map, waits 30 seconds, and repeats forever. `.flowOn(Dispatchers.IO)` moves that work onto a background I/O thread so querying the system never blocks the UI. The screen exposes only the total:

```kotlin
    val screenTimeToday: StateFlow<Int> = todayScreenTime
        .map { it.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
```

`.map { it.values.sum() }` adds up the minutes across all platforms, turning the `Map<Platform, Int>` into a single `Int`.

### Combining streams: the brain-health score

Some state depends on more than one source. The brain-health score needs both today's log and the current limits, so it uses `combine`:

```kotlin
    val brainHealth: StateFlow<Int> = combine(
        repository.getTodayLog(),
        limits
    ) { log, currentLimits ->
        if (log != null) repository.calculateBrainHealth(log, currentLimits)
        else 100
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)
```

`combine` watches both flows; whenever **either** emits, it re-runs the block with the latest of each. If there's no log yet (a fresh day), the score defaults to a perfect `100`.

### The one ratio that drives the mascot

The mood, the Focus Shield, and the daily-goal flag are all driven by a single private number — today's reels divided by the reel limit:

```kotlin
    private val reelRatio: Flow<Float> = combine(todayLog, reelLimit) { log, reels ->
        if (reels > 0) (log?.getTotalReels() ?: 0) / reels.toFloat() else 0f
    }

    val mood: StateFlow<DashboardMood> = reelRatio
        .map { DashboardMood.fromUsage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMood.GREAT)

    val focusShield: StateFlow<FocusShield> = reelRatio
        .map { FocusShield.fromUsage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusShield(ShieldStatus.ACTIVE, 100))

    val dailyGoalOnTrack: StateFlow<Boolean> = reelRatio
        .map { it < 1f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
```

Because all three derive from the same `reelRatio`, the mascot's face, the shield's status, and the goal pill always move together. `reelLimit` and `minuteLimit` come from the first `UserLimits` row (`it.firstOrNull()?.dailyReelLimit`/`dailyMinuteLimit`), falling back to `50` reels and `60` minutes when no limits are saved.

> 💡 **Concept — single source of truth.** Three separate `if`-ladders for mood, shield, and goal could easily drift out of sync. Funneling all three through one `reelRatio` flow guarantees they always agree about how the day is going.

## DashboardMood: usage in, personality out

`DashboardMood` is an `enum class` — a fixed set of named values. Each value bundles three drawable mascot images (`mainRes`, `insightRes`, `reminderRes`) plus all the copy for that emotional state: a `headline`, a speech `bubble`, a `reminderTitle`/`reminderText`, a `greetingEmoji`, an `accent` color, and more. There are five moods, from happiest to most worried: `GREAT`, `ZONE`, `NEAR`, `LIMIT`, `OVER`.

The choice of mood is a simple threshold ladder in the companion object:

```kotlin
        fun fromUsage(reelRatio: Float): DashboardMood {
            return when {
                reelRatio < 0.4f -> GREAT
                reelRatio < 0.75f -> ZONE
                reelRatio < 1f -> NEAR
                reelRatio < 1.5f -> LIMIT
                else -> OVER
            }
        }
```

So under 40% of the limit, the brain is thrilled (`GREAT`, "You're doing amazing!"); at or past 150% it is `OVER` ("Your brain needs care."). Bundling art and copy into the enum keeps the UI dumb: a Composable just reads `mood.headline` or `mood.mainRes` and never branches on the ratio itself.

> 💡 **Concept — enum as a lookup table.** Instead of scattering `if (ratio < 0.4) "happy face"` checks all over the UI, the data lives once on the enum value. Add a field to the enum and every screen that reads it gets the new value for free.

## FocusShield: a parallel "protection level"

`FocusShield` is a `data class` holding a `ShieldStatus` and a `percent`. Its `fromUsage` turns the same ratio into a depleting shield:

```kotlin
        fun fromUsage(reelRatio: Float): FocusShield {
            // 0 usage → 100% protection; at the limit → ~40%; well over → floors out near 5%.
            val percent = (100f - reelRatio * 60f).coerceIn(5f, 100f).toInt()
            val status = when {
                reelRatio < 0.5f -> ShieldStatus.ACTIVE
                reelRatio < 0.85f -> ShieldStatus.WEAK
                reelRatio < 1.25f -> ShieldStatus.DAMAGED
                else -> ShieldStatus.BROKEN
            }
            return FocusShield(status, percent)
        }
```

`coerceIn(5f, 100f)` clamps the percentage so it never drops below 5% or rises above 100%, no matter how extreme the ratio. `ShieldStatus` is its own enum; each value carries a `label`, a `message`, a `tabSubtitle`, and a `color`. Note the four statuses are `ACTIVE`, `WEAK`, `DAMAGED`, and `BROKEN`, but `BROKEN`'s user-facing `label` is actually the word **"Inactive"** — the tracking tab reads `focusShield.status.label`, so it shows "Inactive", not "Broken".

> ⚠️ **Gotcha — `percent` is computed but not drawn here.** `fromUsage` faithfully fills in a `percent` field, yet the Dashboard's tracking tab only renders the status `label` and `color` — it never shows the number. The percentage exists for other surfaces (and future use); don't assume a field is on screen just because it's populated.

## The screen: from `collectAsState` to pixels

`DashboardScreen` is the Composable. Its first job is to subscribe to the ViewModel. `viewModel()` hands it the `DashboardViewModel` (created once and remembered across recompositions), and `collectAsState()` turns each `StateFlow` into a Compose `State` the function can read:

```kotlin
    val todayLog by viewModel.todayLog.collectAsState()
    val brainHealth by viewModel.brainHealth.collectAsState()
    val screenTimeToday by viewModel.screenTimeToday.collectAsState()
    val mood by viewModel.mood.collectAsState()
    val focusShield by viewModel.focusShield.collectAsState()
    val reelLimit by viewModel.reelLimit.collectAsState()
```

The `by` keyword lets us read `mood` as a plain value while Compose tracks the subscription under the hood. **This is the heart of the data flow:** when `viewModel.mood` emits a new value, `mood` here changes, and every composable that read `mood` recomposes — the mascot art and copy update with no manual wiring.

### Reacting to the user returning to the app

Two facts can't be observed as flows because they live outside the app's control — whether the accessibility service is running (`ReelCounterService.isRunning`), and whether usage-access permission is granted (`ScreenTimeHelper.hasPermission`). The screen re-checks them each time it resumes:

```kotlin
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTracking = ReelCounterService.isRunning
                hasUsagePermission = ScreenTimeHelper.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
```

`DisposableEffect` registers a lifecycle observer and, crucially, removes it again in `onDispose` when the screen leaves the composition — no leak. `isTracking` drives the "TRACKING ON/OFF" badge in the top bar; and if usage permission is missing, `UsageAccessBanner` appears under the top bar with a tap target that opens `Settings.ACTION_USAGE_ACCESS_SETTINGS`.

### The scroll-driven hero

The signature interaction is the collapsing header (the "hero"). The header has two key sizes, declared as file-level constants:

```kotlin
private val HeaderMax = 388.dp
private val HeaderMin = 224.dp
```

A single accumulator, `scrolled`, drives the whole effect — `0` means fully expanded, `maxPx` (the pixel value of `HeaderMax`) means scrolled completely off-screen:

```kotlin
    val maxPx = with(density) { HeaderMax.toPx() }
    val shrinkPx = with(density) { (HeaderMax - HeaderMin).toPx() }
    var scrolled by remember { mutableFloatStateOf(0f) }   // 0..maxPx
    val fraction = (scrolled / shrinkPx).coerceIn(0f, 1f)
    val headerOffsetPx = -scrolled
    val bodyTopPad = with(density) { (maxPx - scrolled).coerceAtLeast(0f).toDp() }
```

Watch the denominators here — this is the subtle part. `headerOffsetPx` slides the hero up by the *full* `scrolled` distance, and `bodyTopPad` shrinks the list's top padding in lock-step so the cards rise as the header leaves. But `fraction` divides `scrolled` by the *smaller* `shrinkPx` (`HeaderMax - HeaderMin`, i.e. `164.dp`). Because the shrink window is shorter than the total travel, `fraction` reaches `1` — and the mascot finishes shrinking and the streak finishes morphing — well before the header has fully scrolled away.

> 💡 **Concept — `maxPx` vs. `shrinkPx`.** Two different distances power one gesture. `maxPx` is *how far the header travels* before it's gone; `shrinkPx` is *how far you scroll before the shrink animation completes*. Dividing the same `scrolled` value by each gives one accumulator two synchronized but differently-paced effects.

The list and the header don't scroll independently — a `NestedScrollConnection` intercepts the gesture so the header collapses *first*, then the list takes over:

```kotlin
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                val dy = available.y
                if (dy < 0f && scrolled < maxPx) {
                    val newScrolled = (scrolled - dy).coerceAtMost(maxPx)
                    val used = newScrolled - scrolled
                    scrolled = newScrolled
                    return Offset(0f, -used)
                }
                return Offset.Zero
            }
```

`onPreScroll` runs *before* the list scrolls: while the user drags up (`dy < 0`) and the header isn't fully collapsed, it consumes that motion into `scrolled` and reports how much it used. `onPostScroll` does the reverse on the way back down. The header itself is drawn last, on top of the list, and slid by `graphicsLayer { translationY = headerOffsetPx }` — moving pixels cheaply without re-laying-out anything.

> ⚠️ **Gotcha — `graphicsLayer` vs. layout.** Animating `translationY`/`scale` inside `graphicsLayer` only repaints; changing a `Modifier.height` re-measures the whole tree every frame. The hero deliberately uses `graphicsLayer` for the slide to stay smooth.

Inside `HeroHeader`, `fraction` interpolates the mascot from large to small as you scroll:

```kotlin
            MoodCharacter(
                drawableRes = mood.mainRes,
                bobAmount = lerpFloat(0.03f, 0.015f, fraction),
                modifier = Modifier.size(lerp(339.dp, 210.dp, fraction))
            )
```

`lerp` (linear interpolation) blends between two values by `fraction`: at `0` the brain is `339.dp`, at `1` it's `210.dp`, and every value in between is proportional. The same `fraction` also gently slows the mascot's idle "bob" (from `0.03` to `0.015`) as it shrinks.

### The cards below

The body is a `LazyColumn` (a recycling vertical list) whose items are the screen's cards, each fed straight from the collected state:

- **`StatsCluster`** — the "Reels Scrolled Today" card (`ReelsCard`) and the brain-health `ScoreRing` sit side by side; the streak morphs from a full-width `StreakBar` into an inline third column (`InlineStreak`) as `fraction` grows. `ReelsCard` colors its progress bar by tier — `success` (green) under 75% of the limit, `warning` (amber) under 100%, `error` (red) at or over — and `ScoreRing` draws an arc with `drawArc` inside a `Canvas`, its sweep (`360f * score / 100f`) proportional to the score and labeled HEALTHY / MODERATE / POOR / VERY POOR by tier (≥75 / ≥50 / ≥25 / else).
- **`TrackingTabs`** — three tabs (Time / Daily Goal / Focus Shield) reading `timeLabel`, `mood.goalLabel` (+ `mood.goalSub`), and `focusShield.status.label` + `.color`.
- **`InsightBar`** — compares today's reels to yesterday's (`yesterdayReels = yesterdayLog?.getTotalReels() ?: 0`) into a "% fewer/more videos" sentence, then shows `mood.insightTail` next to the `mood.insightRes` mascot. With no yesterday data it falls back to "today is building your baseline."
- **`FocusPlan`** — the editable video and time limits; the video row turns red when `totalReels` reaches `reelLimit` and the time row when `screenTimeToday` reaches `minuteLimit`, and tapping either opens Settings via `onEditPlan`.
- **`ReminderCard`** — `mood.reminderTitle` / `mood.reminderText` beside the `mood.reminderRes` mascot.

Notice that **none** of these cards know about flows, ratios, or the database. They receive plain values (`totalReels`, `mood`, `focusShield`) and describe a layout. That is the MVVM contract in miniature: the ViewModel computes, the Composable displays, and `collectAsState` is the wire between them. Change the data, and the screen follows — automatically.
