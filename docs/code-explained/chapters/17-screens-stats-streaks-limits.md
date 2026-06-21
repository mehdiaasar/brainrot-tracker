In the last chapter you toured the Dashboard. Now we'll walk the other three full screens — Stats, Streaks, and Limits. Each one follows the same recipe you've already seen: a `ViewModel` that reads the database and hands the UI a single immutable snapshot, and a Compose function that turns that snapshot into pixels. Once you spot the pattern once, the three screens become variations on a theme.

## The pattern that repeats on every screen

Before we dive in, let's name the parts that show up in all three files, because they are nearly identical every time.

A **ViewModel** is a class that holds and prepares the data a screen needs. It survives screen rotations and lives slightly longer than the UI, so the screen can be torn down and rebuilt without losing its data. The ViewModels here all extend `AndroidViewModel`, the variant that gets a handle to the app's `Application` object (needed to open the database).

```kotlin
class LimitsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: UsageRepository
    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }
```

Every screen's ViewModel opens the same database, wraps it in a `UsageRepository`, and asks the repository for **flows**. A flow is a stream of values over time — think of a live news ticker: whenever the underlying data changes, the flow emits a fresh value. The screen subscribes to that ticker and redraws.

> 💡 **Concept —** `StateFlow` is a flow that always has a "current value." The ViewModels convert their flows into `StateFlow` with `.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), <initialValue>)`. The `WhileSubscribed(5000)` part means: keep the upstream database query alive while the screen is watching, and for 5 seconds after it stops watching (so a quick rotation doesn't re-run the query). The third argument is the value to show before real data arrives.

On the UI side, each screen reads the flow with one line:

```kotlin
val limits by viewModel.limits.collectAsState()
```

`collectAsState()` subscribes to the `StateFlow` and returns a Compose `State`. The `by` keyword means `limits` automatically reflects the latest value; whenever it changes, Compose re-runs the screen and the UI updates. This is the whole MVVM loop: ViewModel exposes state, screen observes it.

---

## Stats: turning a week of logs into a report

### What `StatsViewModel` computes

`StatsViewModel.kt` exposes one thing, `weeklyStats`, a `StateFlow<WeeklyStats>`. `WeeklyStats` is a plain data class — a bag of pre-computed numbers the screen can render without doing any math itself. (Its `productivityScore` defaults to `100`, so the very first frame, before any data loads, shows a perfect score rather than a zero.)

The interesting part is how four separate data sources are merged with `combine`:

```kotlin
val weeklyStats: StateFlow<WeeklyStats> =
    combine(
        repository.getWeeklyLogs(),
        repository.getPreviousWeekLogs(),
        repository.getLimits(),
        dailyMinutes,
    ) { logs, prevLogs, limits, daily ->
        ...
    }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyStats())
```

`combine` takes several flows and produces a new flow that fires whenever **any** of them changes, handing you the latest value from each. Here it joins this week's reel logs, last week's logs (for the "vs last week" comparison), the user's limits, and screen-time minutes.

That fourth source is special. Screen time is **not** stored in the database — it's queried live from Android's `UsageStatsManager`:

```kotlin
private val dailyMinutes = flow {
    emit(ScreenTimeHelper.getDailyMinutesByPlatform(app, 14))
}.flowOn(Dispatchers.IO)
```

`flow { emit(...) }` builds a one-shot flow that produces a single value. `flowOn(Dispatchers.IO)` runs that work on a background thread pool meant for input/output, because reading usage stats is slow and must never block the UI thread. It fetches 14 days at once; the `combine` block then slices that into "this week" (days 0–6) and "last week" (days 7–13) using two sets of date strings.

> 💡 **Concept —** The block inside `combine` runs the actual calculations. It computes `totalReels`, `totalMinutes`, a `dailyAverageMinutes` (the week's total minutes divided by 7), and the `productivityScore`. Notice the score isn't averaged over *logged* days only:
>
> ```kotlin
> val logsByDate = logs.associateBy { it.date }
> val healthByDate = thisWeekDates.associateWith { date ->
>     logsByDate[date]?.let { repository.calculateBrainHealth(it, limits) } ?: 100
> }
> val productivityScore = healthByDate.values.average().roundToInt().coerceIn(0, 100)
> ```
>
> A day with no log row scores `100` (a perfect day — nothing was watched). Without that, one bad logged day would define the whole week. `coerceIn(0, 100)` clamps the result into the valid range.

It also picks the best and worst days — but "best" means *least* scrolled, and only days with usage are considered:

```kotlin
val active = logs.filter { it.getTotalReels() > 0 }
val bestDay = active.minByOrNull { it.getTotalReels() }
val worstDay = active.maxByOrNull { it.getTotalReels() }
```

And week-over-week deltas come from a small helper that returns `null` when there's nothing to compare against (so the UI can show a dash instead of a fake "0%"):

```kotlin
private fun percentChange(current: Int, previous: Int): Int? {
    if (previous <= 0) return null
    return ((current - previous) * 100f / previous).roundToInt()
}
```

### What `StatsScreen` renders

The screen is a `LazyColumn` — a vertically scrolling list that only builds the rows currently on screen (cheaper than a plain `Column` for long content). Each `item { }` is one card:

```kotlin
val stats by viewModel.weeklyStats.collectAsState()
val mood = StatsMood.fromScore(stats.productivityScore)

LazyColumn(...) {
    item { Header(onBackClick = onBackClick) }
    item { HeroCard(score = stats.productivityScore, mood = mood) }
    item { ScreenTimeCard(minutesByDate = stats.minutesByDate, mood = mood) }
    item { SummaryRow(stats) }
    item { BestWorstRow(stats) }
    item { FooterCard(mood) }
    item { Spacer(Modifier.height(12.dp)) }
}
```

`SummaryRow` shows three stat cards — **Total Videos**, **Total Time**, and **Daily Average** — each with its week-over-week delta. `BestWorstRow` shows the "BEST DAY" and "WORST DAY" cards described above.

#### StatsMood: one number drives five looks

`StatsMood.fromScore(stats.productivityScore)` is a clever bit of design. `StatsMood` (in `StatsMood.kt`) is an **enum** — a fixed set of named values. Here it has five tiers, each bundling the art, accent colors, and copy for one mood:

```kotlin
companion object {
    /** @param score the weekly productivity score, 0..100. */
    fun fromScore(score: Int): StatsMood = when {
        score >= 80 -> EXCELLENT
        score >= 65 -> GOOD
        score >= 45 -> FAIR
        score >= 30 -> LOW
        else -> CRITICAL
    }
}
```

| Score | Mood | Headline |
|------|------|----------|
| 80–100 | `EXCELLENT` | "Excellent focus today! 🎉" |
| 65–79 | `GOOD` | "Focus slipped a little. 😟" |
| 45–64 | `FAIR` | "Focus needs attention. 😟" |
| 30–44 | `LOW` | "Focus needs attention. 😟" |
| 0–29 | `CRITICAL` | "Focus is critically low. 😟" |

Because the mood carries everything (`mood.accent`, `mood.headline`, `mood.heroRes`, `mood.footerBody`…), the cards never branch on the score themselves — they just read fields off `mood`. The hero gauge, for example, draws an arc whose color and sweep both come from the mood and score:

```kotlin
if (score > 0) {
    drawArc(
        color = mood.accent, startAngle = 180f, sweepAngle = 180f * score / 100f, useCenter = false,
        style = Stroke(width = stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize
    )
}
```

That's `Canvas` drawing — a grey track arc (drawn first) is a full 180° half-ring, and the colored portion sweeps `180° × score/100`, so a score of 50 fills half the semicircle. The colored arc is skipped entirely when the score is 0, so a zero-score gauge shows only the empty track.

> 💡 **Concept —** A `when {}` block with boolean conditions is Kotlin's tidy `if/else if` ladder; the first matching branch wins. Pairing it with an enum is a common way to map a continuous value (a 0–100 score) onto a handful of discrete UI states.

#### The stacked bar chart

`ScreenTimeCard` draws a 7-day chart by hand on a `Canvas`. The data is `minutesByDate: Map<String, Map<Platform, Int>>` — for each ISO date string, a map of platform to minutes. The chart picks the last seven days and finds a "nice" axis maximum rounded up to the next 20:

```kotlin
val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
val totals = days.map { d -> minutesByDate[d.toString()]?.values?.sum() ?: 0 }
val rawMax = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
val axisMax = (ceil(rawMax / 20f).toInt() * 20).coerceAtLeast(20)
```

Each day's bar is built by stacking the four platforms bottom-to-top (TikTok, YouTube, Instagram, Snapchat), with only the topmost non-empty segment getting rounded corners — a small touch that makes the bar look like a single capped pillar. The `?:` operator (the "Elvis operator") supplies a fallback whenever a map lookup returns `null`, so missing days quietly become zero.

> ⚠️ **Gotcha —** "Reels" and "minutes" are two different measurements. The summary cards and best/worst picks come from **reel counts** in the Room database, but the bar chart and the time totals come from **live `UsageStatsManager` minutes**. A day can have a low reel count but high minutes (long videos) or vice-versa, so don't assume the two always move together.

---

## Streaks: visualizing a habit

### What `StreaksViewModel` exposes

`StreaksViewModel.kt` exposes `streakState: StateFlow<StreakUiState>`, again built with `combine`:

```kotlin
val streakState: StateFlow<StreakUiState> = combine(
    repository.getStreakData(),
    repository.getMonthlyLogs(),
    yesterdayMinutes
) { records, logs, minutes ->
    val hasZeroReelDay = logs.any { it.getTotalReels() == 0 }
    val hasHalfHourDay = minutes in 1..30
    StreakUiState(
        records = records,
        currentStreak = repository.calculateCurrentStreak(records),
        longestStreak = repository.calculateLongestStreak(records),
        streakMap = records.associate { it.date to it.underLimit },
        hasZeroReelDay = hasZeroReelDay,
        hasHalfHourDay = hasHalfHourDay
    )
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakUiState())
```

The two numbers the screen leans on are `currentStreak` (consecutive recent under-limit days) and `longestStreak` (the all-time best). Both are computed by the repository from the `StreakRecord` rows; the ViewModel's job is just to package them. The `minutes` value here is *yesterday's* total screen-time across all platforms, fetched live:

```kotlin
private val yesterdayMinutes = flow {
    emit(ScreenTimeHelper.getYesterdayMinutesByPlatform(getApplication()).values.sum())
}.flowOn(Dispatchers.IO)
```

> ⚠️ **Gotcha —** `hasZeroReelDay` is checked against `getMonthlyLogs()`, not against every calendar day. The comment in the file spells out why: a zero-reel achievement should only unlock from a day you *actually tracked* (a log row exists), so untouched empty days don't unlock it for free. `hasHalfHourDay` uses `minutes in 1..30` — note `1..30`, not `0..30`, because zero minutes means no usage data, not a virtuous half-hour day.

### What `StreaksScreen` renders

The screen defines its milestones and badges as static lists at the top of the file — pure data. The visible state of each (reached / in-progress / locked) is then derived from the live `currentStreak`/`longestStreak`:

```kotlin
private val MILESTONES = listOf(
    Milestone(1, "Day 1", "Started", "First Step", R.drawable.journey_day1),
    Milestone(7, "Day 7", "Focus Warrior", "7-Day Warrior", R.drawable.journey_day7),
    Milestone(30, "Day 30", "Legend", "30-Day Legend", R.drawable.journey_day30),
    Milestone(100, "Day 100", "Champion", "100-Day Champion", R.drawable.journey_day100),
    Milestone(365, "Day 365", "Master", "365-Day Master", R.drawable.journey_day365),
)
```

The hero card finds the next milestone you haven't reached and turns the gap into a percentage:

```kotlin
val nextMilestone = MILESTONES.firstOrNull { it.days > streak } ?: MILESTONES.last()
val target = nextMilestone.days
val progress = (streak.toFloat() / target).coerceIn(0f, 1f)
val percent = (progress * 100).toInt()
```

`firstOrNull { it.days > streak }` walks the list and grabs the first milestone whose threshold is still ahead of you; `?: MILESTONES.last()` falls back to the 365-day master once you've cleared everything. `coerceIn(0f, 1f)` keeps the fill between empty and full.

The journey rail is a `LazyRow` — a horizontally scrolling list. The caller computes each card's state from the streak, then hands those flags to the `JourneyCard`:

```kotlin
items(MILESTONES.size) { i ->
    val m = MILESTONES[i]
    val reached = streak >= m.days
    val inProgress = !reached && m.days == target
    JourneyCard(m = m, reached = reached, inProgress = inProgress)
}
```

A reached card shows a green check, the in-progress card glows and says "In Progress," and locked cards dim their art to 35% opacity and stamp a lock icon:

```kotlin
.then(if (reached || inProgress) Modifier else Modifier.alpha(0.35f))
```

> 💡 **Concept —** `Modifier.then(...)` conditionally chains a modifier. The idiom `if (condition) someModifier else Modifier` means "apply this styling only when the condition holds." A bare `Modifier` is the no-op identity, so the `else` branch changes nothing.

Achievements work the same way but gate on `longestStreak` (you keep a badge even if your current streak breaks), and the screen highlights the first locked badge with a live progress bar:

```kotlin
val firstLocked = BADGES.indexOfFirst { state.longestStreak < it.requirement }
...
AchievementCard(
    badge = b,
    unlocked = state.longestStreak >= b.requirement,
    showProgress = i == firstLocked,
    progressNow = streak.coerceAtMost(b.requirement)
)
```

Note the subtle mix here: a badge **unlocks** based on `longestStreak`, but the little progress bar on the first locked badge fills based on your *current* streak (`progressNow`). So the bar can shrink if you break a streak even though already-earned badges stay lit.

Finally, the Focus Tip rotates per visit. `remember { FOCUS_TIPS.random() }` picks one random tip and *remembers* it across recompositions, so the tip stays fixed while you scroll but changes the next time you open the screen.

The header's account icon is just a `clickable` circle wired to a callback the navigation layer supplies: `Box(...).clickable(onClick = onOpenSettings)`. The Streaks screen doesn't know what "settings" is — it only knows to call `onOpenSettings`. That's how screens stay decoupled from navigation.

---

## Limits: editing settings that flow back into the database

`LimitsScreen.kt` is the app's settings page (its on-screen title is even "Settings"). It's the most interactive screen because it *writes* data, not just reads it.

### Sliders that save on release

The video and time limits are sliders. The screen seeds them from the database (falling back to 50 videos / 60 minutes when no row exists yet), then keeps a local editable copy:

```kotlin
val initialReelLimit = limits.firstOrNull()?.dailyReelLimit?.toFloat() ?: 50f
var reelLimit by remember(initialReelLimit) { mutableFloatStateOf(initialReelLimit) }
```

`mutableFloatStateOf` creates a piece of mutable Compose state for the slider's position. `remember(initialReelLimit)` keeps that value across recompositions but *resets* it if `initialReelLimit` changes (i.e. when the database value arrives). Dragging updates the local state instantly; releasing writes to the DB:

```kotlin
LimitSliderRow(
    label = "Videos per day",
    value = reelLimit,
    valueUnit = "videos",
    recommended = "Recommended: 30",
    onValueChange = { reelLimit = it },
    onValueChangeFinished = {
        viewModel.updateGlobalLimits(reelLimit.toInt(), minuteLimit.toInt())
    },
    valueRange = 0f..200f,
    sliderColors = sliderColors
)
```

`onValueChange` fires continuously while dragging (cheap, just updates local state). `onValueChangeFinished` fires once when you let go — *that's* when we persist, so we don't hammer the database on every pixel of movement. Note that both sliders save *both* values together, so changing the time slider also re-writes the current video limit and vice-versa.

The ViewModel writes the same limit to every platform:

```kotlin
fun updateGlobalLimits(reelLimit: Int, minuteLimit: Int) {
    viewModelScope.launch {
        Platform.entries.forEach { platform ->
            repository.updateLimits(UserLimits(platform.name, reelLimit, minuteLimit))
        }
    }
}
```

`viewModelScope.launch { }` runs the database write as a coroutine — a background task that won't freeze the UI. It loops over `Platform.entries` (every enum value) and upserts a `UserLimits` row for each, because the UI exposes one global limit even though the database stores per-platform rows.

### The blocking-mode selector

Some settings don't belong in Room — they're lightweight device preferences stored in `SharedPreferences`. The blocking toggle and mode are read once into local state:

```kotlin
val sharedPrefs = remember { context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) }
var blockingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(Prefs.BLOCKING_ENABLED, false)) }
var blockingMode by remember {
    mutableStateOf(BlockingMode.fromPref(sharedPrefs.getString(BlockingMode.PREF_KEY, null)))
}
```

`BlockingMode.fromPref(...)` parses the stored string back into an enum value, defaulting to `REMIND` (the gentlest mode) when the preference is missing or unrecognized. When blocking is on, three `PillToggleButton`s let you pick a mode, each writing its choice back to prefs immediately:

```kotlin
PillToggleButton(
    icon = Icons.Outlined.Lock,
    label = "Lock Hard",
    selected = blockingMode == BlockingMode.HARD,
    accent = SetPurple,
    selectedBg = purpleSoftBg,
    unselectedBg = pillUnselectedBg,
    onClick = {
        blockingMode = BlockingMode.HARD
        sharedPrefs.edit().putString(BlockingMode.PREF_KEY, BlockingMode.HARD.name).apply()
    }
)
```

| Mode | What the user is told |
|------|----------------------|
| `HARD` | "Blocks the app until midnight — the only way out is closing it." |
| `SNOOZE` | "Dismissing gives you 5 more minutes, then the block returns." |
| `REMIND` | "You'll be reminded each time you open the app." |

`BlockingMode.PREF_KEY` is just `Prefs.BLOCKING_MODE`, the string `"blocking_mode"`. The `ReelCounterService` (the always-running background piece) reads that same key from the same `SharedPreferences` file, so the moment you tap a pill, the enforcement behavior changes — no ViewModel round-trip needed. The Appearance card works identically, calling `ThemeController.selectMode(...)` to flip Light/Dark/System. The Floating Counter card uses a plain `Slider` that writes `Prefs.HUD_SCALE` on release the same way.

> ⚠️ **Gotcha —** `.edit().putString(...).apply()` saves the preference asynchronously (`apply()` writes in the background, vs `commit()` which blocks). For UI toggles that's exactly what you want, but it means a different component reading the value a microsecond later isn't strictly guaranteed to see it. In practice the gap is invisible.

### The Account card

The top of the screen optionally offers Google sign-in — but the entire block is gated:

```kotlin
if (CLOUD_SYNC_ENABLED) item {
    val user = signedInUser
    if (user != null) {
        // centered avatar + name + email
    } else {
        // "Sign in with Google" card
    }
}
```

`CLOUD_SYNC_ENABLED` is a build-wide flag; while it's off (v1, before Firebase is configured), the card never appears at all. When enabled, the card has two faces: signed-in users see a profile header built from `AppPreferences.userFlow(context)`, and signed-out users see a tappable card that calls `onNavigateToSignIn()` — another navigation callback the screen doesn't implement itself.

The signed-in avatar is just the first letter of the name (or email) in an orange circle:

```kotlin
val initial = user.name.ifEmpty { user.email }
    .firstOrNull()?.uppercaseChar()?.toString() ?: "?"
```

`ifEmpty { ... }` falls back to the email when there's no name, `firstOrNull()` safely grabs the first character even of an empty string, and the trailing `?: "?"` covers the case where everything is blank.

---

## What ties them together

All three screens are the same machine wearing different clothes: a ViewModel `combine`s a few repository flows into one immutable state object, exposes it as a `WhileSubscribed` `StateFlow`, and the screen `collectAsState`s it into a `LazyColumn` of cards. Reads come from Room (logs, limits, streak records) and live `UsageStatsManager` queries (screen-time minutes); writes go to Room (limits) or `SharedPreferences` (blocking, theme, HUD scale). Spot the recipe once, and every future screen in the app reads like a remix of it.
