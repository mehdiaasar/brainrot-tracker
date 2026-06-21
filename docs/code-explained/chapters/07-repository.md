Every screen in LoopOut — the dashboard, the stats page, the streaks calendar, the home-screen widget — needs the same numbers: how many reels you watched today, what your limits are, how long your streak is. If each screen reached into the database on its own, they would all need to know SQL, table names, and how to compute a "brain health score." That is a recipe for bugs and disagreement. Instead, LoopOut funnels every data request through one class: `UsageRepository`. This chapter explains what that class does and why it exists.

## Why have a "repository" at all?

A **repository** is a middle layer that sits between your screens (the UI) and your data storage (the database). The screens ask the repository for data in friendly terms — "give me today's log" — and the repository knows the messy details of how to fetch it.

> 💡 **Concept — the repository pattern.** Think of a librarian. You don't walk into the stacks and hunt through shelves yourself; you ask the librarian, who knows the catalog system. The repository is the librarian. The UI never touches the database directly; it always goes through `UsageRepository`.

This gives us the **single source of truth**: one place that defines what the data means. There is exactly one definition of "what counts as a good day" (the brain-health score), one definition of "increment a reel count," one definition of "evaluate a streak." Every screen that needs those answers gets the *same* answer, because they all call the same method.

> 💡 **Concept — single source of truth.** When the same fact is computed in only one place, it can't disagree with itself. If the dashboard and the widget both computed the brain-health score with their own copy-pasted math, a fix in one would silently leave the other wrong. Routing both through `repository.calculateBrainHealth(...)` makes that impossible.

## How the repository is built (no DI framework)

Many Android apps use a *dependency injection* (DI) framework — a library like Hilt or Dagger that automatically constructs objects and hands them to whoever needs them. LoopOut deliberately keeps things simple: it has **no DI framework**. The repository is just a normal class you create with a constructor call.

```kotlin
class UsageRepository(private val database: AppDatabase) {

    private val dailyLogDao = database.dailyLogDao()
    private val userLimitsDao = database.userLimitsDao()
    private val streakDao = database.streakDao()
```

The repository takes an `AppDatabase` (the Room database) in its constructor and immediately pulls out its three **DAOs** — Data Access Objects. A DAO is an interface where each method maps to a single SQL query; Room generates the actual code behind it. So `dailyLogDao` is the repository's gateway to the `daily_logs` table, and so on.

Each ViewModel that needs data builds its own repository in its `init` block, as in `DashboardViewModel.kt`:

```kotlin
init {
    val db = AppDatabase.getInstance(application)
    repository = UsageRepository(db)
}
```

> 💡 **Concept — AndroidViewModel.** A *ViewModel* holds the data and logic for one screen and survives configuration changes (like rotating the phone). `AndroidViewModel` is the variant that gets a handle to the `Application` object, which it needs here to open the database. Because `AppDatabase.getInstance(...)` returns a shared singleton, every repository ends up wrapping the *same* database, so this lightweight approach is safe even though each ViewModel makes its own repository.

## Exposing data as Flows

Most read methods return a `Flow`. A **`Flow`** is a stream of values over time: when the underlying database row changes, the Flow automatically emits the new value, and the screen redraws. It is "live" rather than a one-time fetch.

```kotlin
fun getTodayLog(): Flow<DailyLog?> = dailyLogDao.getByDate(today())

fun getWeeklyLogs(): Flow<List<DailyLog>> {
    val end = LocalDate.now()
    val start = end.minusDays(6)
    return dailyLogDao.getDateRange(start.toString(), end.toString())
}
```

`getTodayLog()` hands back the Flow for today's row. `getWeeklyLogs()` computes a 7-day window — `end` is today, `start` is six days earlier — and asks the DAO for everything in that date range. Dates are stored as plain `String`s (like `"2026-06-19"`) because `LocalDate.toString()` produces ISO format, which sorts and compares correctly as text. The same pattern powers `getPreviousWeekLogs()` (days −13..−7, for week-over-week deltas) and `getMonthlyLogs()` (the last 30 days).

> 💡 **Concept — snapshot vs. Flow.** Some methods end in `...Snapshot` and return a plain value instead of a Flow (e.g. `getTodayLogSnapshot()`, which returns `DailyLog?`). A snapshot is a single read for "what is true *right now*," used by background logic that just needs one answer. A Flow is for the UI, which wants to keep watching. (Under the hood these call the DAO's `...Once` methods — `getByDateOnce` — which are the non-Flow, one-shot queries.)

## Coroutines and `suspend`

Database work must never run on the **main thread** (the thread that draws the UI), or the app freezes. Kotlin solves this with **coroutines** — lightweight tasks that can pause ("suspend") and resume without blocking a thread.

```kotlin
suspend fun ensureTodayLogExists() {
    val existing = dailyLogDao.getByDateOnce(today())
    if (existing == null) {
        dailyLogDao.insertOrUpdate(DailyLog(date = today()))
    }
}
```

The `suspend` keyword marks a function that *might pause* — here, while waiting on the database. You can only call a `suspend` function from a coroutine. `ensureTodayLogExists()` reads today's row; if it's missing, it inserts a fresh `DailyLog` so later updates have something to update.

> 💡 **Concept — Room and threads.** Room already runs its `suspend` DAO methods on a background thread pool, so the repository's `suspend` functions don't need to manually switch dispatchers. (A *dispatcher*, like `Dispatchers.IO`, is the thread pool a coroutine runs on.) Callers that build Flows in ViewModels sometimes add `.flowOn(Dispatchers.IO)` for their own heavier work — `DashboardViewModel` does this for its live screen-time polling — but the repository trusts Room here.

## Incrementing reel counts, per platform

When the accessibility service detects a reel, it tells the repository which app it came from. The repository routes that to the right column.

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

First it guarantees a row exists, then the `when` block picks the matching DAO method. Each DAO method is a one-line SQL `UPDATE`, e.g. `UPDATE daily_logs SET instagramReels = instagramReels + 1 WHERE date = :date`. Doing the `+ 1` inside SQL is safer than reading the value, adding one in Kotlin, and writing it back, because the database handles it as a single atomic step.

`Platform` is an enum that ties each app to its package name (`Platform.kt`). Each platform maps to one column on the `DailyLog` row:

| Platform | Package name | Reel column |
|---|---|---|
| INSTAGRAM | `com.instagram.android` | `instagramReels` |
| YOUTUBE | `com.google.android.youtube` | `youtubeShorts` |
| TIKTOK | `com.zhiliaoapp.musically` | `tiktokVideos` |
| SNAPCHAT | `com.snapchat.android` | `snapchatSpotlights` |

> 💡 **Concept — `getReelsForPlatform`.** The reverse lookup lives on the entity itself: `DailyLog.getReelsForPlatform(platform)` is a `when` that returns the right `Int` column for a given platform. The repository leans on it constantly so the scoring and streak code can loop over platforms generically instead of naming each column by hand.

## The brain-health score (reels-only, usage-weighted)

`calculateBrainHealth` turns a day's reel counts into a 0–100 score where 100 is a perfect (no-scrolling) day. It is **reels-only by design** — screen-time minutes are shown elsewhere (dashboard, widget, mood face) but don't feed this number, so the app's metrics can't contradict each other.

```kotlin
fun calculateBrainHealth(log: DailyLog, limits: List<UserLimits>): Int {
    val limitMap = limits.associateBy { it.platform }
    var fractionSum = 0.0
    var activePlatforms = 0

    for (platform in Platform.entries) {
        val reels = log.getReelsForPlatform(platform).toDouble()
        if (reels <= 0.0) continue          // skip untouched apps
        activePlatforms++

        val reelLimit = limitMap[platform.name]?.dailyReelLimit ?: 30
        val reelRatio = if (reelLimit > 0) (reels / reelLimit).coerceIn(0.0, 2.0) else 2.0
        fractionSum += (1.0 - reelRatio / 2.0)
    }

    if (activePlatforms == 0) return 100
    return (100.0 * fractionSum / activePlatforms).toInt().coerceIn(0, 100)
}
```

Step by step:

- `associateBy { it.platform }` builds a lookup map keyed by each limit's `platform` field. That field is a `String` — the enum's name, like `"INSTAGRAM"` — which is why the loop later looks limits up with `limitMap[platform.name]`.
- The loop walks every `Platform`. If you didn't touch an app (`reels <= 0.0`), `continue` skips it. This is the **usage-weighted** part: unused apps don't hand out "free" points, so a heavy day on a single app can still drag the score to zero.
- `reelLimit` comes from your settings, defaulting to **30** if you never set one (`?:` is Kotlin's "use this if null").
- `reelRatio` is usage ÷ limit, with `coerceIn(0.0, 2.0)` clamping it so that going 2× over your limit caps the penalty. Each platform's health fraction is `1 − ratio / 2`: exactly at your limit gives 0.5, double the limit gives 0.0, zero usage gives 1.0.
- Finally it averages the fractions across only the *active* platforms and scales to 0–100. No usage at all returns a clean 100.

> ⚠️ **Gotcha — the score isn't auto-saved.** `calculateBrainHealth` only computes; it doesn't write anything. A separate `updateBrainHealth(score)` method persists it: it calls `ensureTodayLogExists()` and then `dailyLogDao.updateBrainHealthScore(...)`, also `coerceIn(0, 100)`-clamped. Callers must explicitly save if they want it stored.

## Lazy streak back-fill: `evaluateStreaksUpTo`

A **streak** is a run of consecutive under-limit days. LoopOut has no background scheduler ticking at midnight, so streaks are filled in *lazily* — the next time the app opens (or the service detects a new day), it catches up on every day it missed.

```kotlin
suspend fun evaluateStreaksUpTo(yesterday: LocalDate) {
    val firstUnevaluated = streakDao.getLatestDate()?.let { LocalDate.parse(it).plusDays(1) }
        ?: dailyLogDao.getEarliestDate()?.let { LocalDate.parse(it) }
        ?: yesterday
    if (firstUnevaluated.isAfter(yesterday)) return
```

`firstUnevaluated` is "where do I resume?" It tries three options in order: the day after the most recent streak record (`streakDao.getLatestDate()`, which is `MAX(date)`); failing that, the earliest day with any log (`dailyLogDao.getEarliestDate()`, which is `MIN(date)`); failing that, just `yesterday`. If that start point is already past `yesterday`, everything is up to date and the function returns immediately.

```kotlin
    val limits = getLimitsSnapshot().associateBy { it.platform }
    fun limitFor(platform: Platform): Int =
        limits[platform.name]?.dailyReelLimit ?: 30

    var date = firstUnevaluated
    var previous = streakDao.getByDateOnce(date.minusDays(1).toString())
    while (!date.isAfter(yesterday)) {
        val log = dailyLogDao.getByDateOnce(date.toString())
        val underLimit = log == null ||
            Platform.entries.all { log.getReelsForPlatform(it) <= limitFor(it) }
        val streakDay = if (underLimit) {
            (previous?.takeIf { it.underLimit }?.streakDay ?: 0) + 1
        } else {
            0
        }
        val record = StreakRecord(date.toString(), underLimit, streakDay)
        streakDao.insert(record)
        previous = record
        date = date.plusDays(1)
    }
}
```

The loop walks one day at a time up to `yesterday`. For each:

- A day is **under-limit when there's no log at all** (`log == null` — the phone simply wasn't doomscrolled that day) *or* every platform's reels are within its limit (`Platform.entries.all { ... }`).
- `streakDay` continues the previous day's count when both days were under-limit, otherwise it resets to 0. Note that `previous` is seeded from the day *before* the resume point, so the count picks up correctly even when the back-fill starts mid-streak.
- It writes a `StreakRecord(date, underLimit, streakDay)` and carries it forward as `previous` for the next iteration. The DAO inserts with `OnConflictStrategy.REPLACE`, so re-running the back-fill safely overwrites rather than duplicates.

> ⚠️ **Gotcha — history is judged against *today's* limits.** Limits aren't versioned. `limitFor` reads your *current* settings, so if you tighten your Instagram limit, past days are re-judged under the new rule. This is a deliberate simplification, called out in the method's own doc comment.

## Reading streaks back out

Two helper methods turn the stored records into the numbers shown on the Streaks screen. `calculateCurrentStreak` sorts records newest-first (`sortedByDescending { it.date }`) and counts under-limit days until it hits a broken one, then stops. `calculateLongestStreak` sorts oldest-first and tracks the best run ever seen. Both are **pure functions** — they take a `List<StreakRecord>` and return an `Int`, touching no database — which makes them trivial to unit-test.

> 💡 **Concept — pure functions.** A pure function depends only on its inputs and has no side effects (no database, no clock, no network). Given the same list it always returns the same number, so you can test it with a hand-built list and no Android device at all.

## The big picture

| Capability | Repository method | Returns |
|---|---|---|
| Watch today live | `getTodayLog()` | `Flow<DailyLog?>` |
| One-time read | `getTodayLogSnapshot()` | `DailyLog?` |
| Count a reel | `incrementReelCount(platform)` | `suspend` |
| Score a day | `calculateBrainHealth(log, limits)` | `Int` |
| Save a score | `updateBrainHealth(score)` | `suspend` |
| Back-fill streaks | `evaluateStreaksUpTo(yesterday)` | `suspend` |
| Current / longest streak | `calculateCurrentStreak` / `calculateLongestStreak` | `Int` |

`UsageRepository` is small, but it carries the app's most important promise: there is exactly one place that decides what your numbers mean, and every screen trusts it. That is what "single source of truth" buys you.
