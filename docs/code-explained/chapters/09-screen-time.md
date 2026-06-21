LoopOut counts how many reels you scroll, but that is only half the story. The other half is *time*: how many minutes you actually spent staring at Instagram or TikTok today. This chapter explains how the app measures that real, wall-clock screen time — and, just as importantly, why it deliberately re-builds those numbers from scratch every time instead of saving them in the database.

Everything here lives in one small, focused file: `ScreenTimeHelper.kt`. It is a Kotlin `object` (a singleton — a class that has exactly one shared instance you call directly, like a toolbox bolted to the wall) with no state of its own. You hand it a `Context` and a time window, and it hands you back a map of minutes per platform.

## What "screen time" even means here

When you open Instagram and look at it, Android considers that app to be in the **foreground** — the app you can see and touch right now. The moment you switch to another app or lock the screen, Instagram moves to the **background**.

Screen time, in LoopOut's definition, is simply the total number of minutes a tracked app spent in the foreground. The four tracked apps come from the `Platform` enum (`Platform.kt`): Instagram, YouTube, TikTok, and Snapchat, each identified by its **package name** (the unique reverse-domain ID every Android app has, e.g. `com.instagram.android`).

> 💡 **Concept —** A *package name* is an app's permanent fingerprint. The display name "Instagram" can change, but `com.instagram.android` is fixed and is how the operating system refers to the app internally. LoopOut keys everything off package names.

## Why screen time is never stored in the database

LoopOut keeps reel *counts* in a Room database (covered in earlier chapters), but it never stores screen-time minutes there. Instead, every time a screen needs the numbers, `ScreenTimeHelper` asks Android fresh and computes them live.

This is a deliberate architectural choice. Android already keeps a detailed, system-wide log of when apps came to the foreground and left it. There is no point copying that log into our own database — it would just be a stale, second copy that we'd have to keep in sync. The operating system is the single source of truth, so we query it on demand.

> 💡 **Concept —** "Source of truth" means the one authoritative place a piece of data lives. Duplicating data invites bugs where the two copies disagree. By treating Android's usage log as the source of truth, LoopOut sidesteps a whole category of sync bugs.

## The sensitive permission: Usage Access

The system usage log is private. An app cannot read which other apps you have been using unless you explicitly grant it the **Usage Access** permission, technically called `PACKAGE_USAGE_STATS`.

This is not a normal runtime permission like camera or location. It is a **special app access** — the user has to dig into *Settings → Apps → Special app access → Usage access* and flip a toggle for LoopOut by hand. Android treats it this way precisely because knowing your full app-usage history is sensitive: it reveals habits, routines, even which banking or dating apps you open.

Because this permission is granted in a separate Settings screen rather than through a pop-up dialog, you cannot check it the usual way. LoopOut checks it through the `AppOpsManager`:

```kotlin
fun hasPermission(context: Context): Boolean {
    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
    val mode = appOps.checkOpNoThrow(
        AppOpsManager.OPSTR_GET_USAGE_STATS,
        Process.myUid(),
        context.packageName
    )
    return mode == AppOpsManager.MODE_ALLOWED
}
```

Reading it line by line:

- `getSystemService(Context.APP_OPS_SERVICE)` fetches Android's **App Ops** manager — a ledger that tracks whether each app is allowed to perform certain sensitive "operations." We cast it to `AppOpsManager` so Kotlin knows its type.
- `checkOpNoThrow(...)` asks: "for the operation `OPSTR_GET_USAGE_STATS` (reading usage stats), performed by this app, what is the current setting?" The `NoThrow` suffix means it returns a result code instead of throwing an exception if something is off.
- `Process.myUid()` is our own app's user ID (the numeric identity Android assigns each installed app), and `context.packageName` is our package name — together they say "check the setting *for us*."
- The result `mode` is compared to `MODE_ALLOWED`. If they match, the user has granted Usage Access and we return `true`.

> ⚠️ **Gotcha —** Every method that reads usage data calls `hasPermission` first. If access was never granted (or the user revoked it), the helper returns all-zero maps rather than crashing. You can see this in `queryMinutesFromEvents`: `if (!hasPermission(context)) return Platform.entries.associateWith { 0 }`.

## Two ways to ask Android for usage — and why we pick the hard one

Android offers two APIs through `UsageStatsManager` (the system service that exposes the usage log):

| API | What it gives you | Problem |
| --- | --- | --- |
| `queryAndAggregateUsageStats(start, end)` | Pre-totalled minutes per app | "Bleeds" sessions across window boundaries — it can attribute time from outside your window |
| `queryEvents(start, end)` | The raw stream of RESUMED / PAUSED / STOPPED events | More work, but exact |

The convenient one, `queryAndAggregateUsageStats`, does the summing for you. But it is imprecise about *where the boundaries fall*. If an app was open across midnight, the aggregate can lump that time onto the wrong day, or count time that began before your start point. The doc comment on the helper's private method states the reasoning plainly:

```kotlin
/**
 * Sums foreground time precisely by processing raw activity events.
 * Unlike queryAndAggregateUsageStats, this respects exact window boundaries
 * and won't bleed sessions from outside the window.
 */
private fun queryMinutesFromEvents(
    context: Context,
    startMs: Long,
    endMs: Long
): Map<Platform, Int> {
```

So LoopOut chooses the harder, exact path: pull the **raw events** and add up the sessions itself. A "session" is one continuous stretch in the foreground, bounded by a RESUMED event (app came forward) and a PAUSED or STOPPED event (app went away).

## Walking the event loop

The heart of the file is a loop that replays Android's event stream and reconstructs each session. Here is the setup, from `queryMinutesFromEvents`:

```kotlin
val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
val events = usm.queryEvents(startMs, endMs)
val event = UsageEvents.Event()
val foregroundStart = mutableMapOf<String, Long>()
val totalMs = mutableMapOf<String, Long>()
```

- `usm.queryEvents(startMs, endMs)` returns a `UsageEvents` object — think of it as a cursor or playback head positioned at the start of the event stream for that time window.
- `val event = UsageEvents.Event()` is a single reusable container. Instead of allocating a new object per event (wasteful), Android fills this same one each time we ask for the next event.
- `foregroundStart` remembers, per package, the timestamp when an app most recently came to the foreground. It is our "stopwatch start" bookkeeping.
- `totalMs` accumulates the total foreground milliseconds per package.

Now the loop itself:

```kotlin
while (events.hasNextEvent()) {
    events.getNextEvent(event)
    val pkg = event.packageName
    if (pkg !in platformPackages) continue
    when (event.eventType) {
        UsageEvents.Event.ACTIVITY_RESUMED -> {
            if (pkg !in foregroundStart) foregroundStart[pkg] = event.timeStamp
        }
        UsageEvents.Event.ACTIVITY_PAUSED,
        UsageEvents.Event.ACTIVITY_STOPPED -> {
            foregroundStart.remove(pkg)?.let { start ->
                totalMs[pkg] = (totalMs[pkg] ?: 0L) + (event.timeStamp - start)
            }
        }
    }
}
```

Step by step:

- `while (events.hasNextEvent())` keeps going as long as there are more events. `getNextEvent(event)` copies the next one into our reusable container.
- `if (pkg !in platformPackages) continue` skips any app we don't care about — we only track the four social platforms. (`platformPackages` is built one line earlier as `Platform.entries.map { it.packageName }.toSet()`.) `continue` jumps straight to the next loop iteration.
- On `ACTIVITY_RESUMED` (the app came to the foreground), we record the timestamp — *but only if we weren't already timing it* (`if (pkg !in foregroundStart)`). This guards against duplicate RESUMED events that would otherwise reset our stopwatch and lose time.
- On `ACTIVITY_PAUSED` or `ACTIVITY_STOPPED` (the app left the foreground), we `remove` the start timestamp and, if one existed, add the elapsed duration `event.timeStamp - start` to that app's running total. The `?.let { ... }` only runs when there actually was a matching start — a PAUSED with no prior RESUMED in our window is harmlessly ignored.

> 💡 **Concept —** This is the *stopwatch pattern*. RESUMED presses start; PAUSED/STOPPED presses stop and banks the lap time. The `foregroundStart` map lets us run one independent stopwatch per app simultaneously.

### The dangling-session fix

What if an app is *still open* when the window ends — say you're scrolling TikTok right now and we ask for "today's minutes"? There is no PAUSED event yet, so the stopwatch never stopped. The code handles this explicitly after the loop:

```kotlin
// Any app still in foreground at the end of the window
for ((pkg, start) in foregroundStart) {
    totalMs[pkg] = (totalMs[pkg] ?: 0L) + (endMs - start)
}
```

For every app left running in `foregroundStart`, we close its session at `endMs` (the end of the requested window) and bank the time. This is exactly the kind of boundary precision the aggregate API got wrong.

Finally, the milliseconds become whole minutes:

```kotlin
Platform.entries.associateWith { platform ->
    ((totalMs[platform.packageName] ?: 0L) / 60_000L).toInt()
}
```

Each platform's total is divided by `60_000` (the number of milliseconds in a minute — the `_` is just a digit separator for readability) and truncated to an `Int`. Apps with no recorded time fall back to `0L` via `?: 0L`. The whole body of the method is wrapped in `try { ... } catch (_: Exception) { Platform.entries.associateWith { 0 } }`, so any unexpected failure degrades gracefully to zeros instead of crashing.

> 💡 **Concept —** Integer division *truncates* (drops the fractional part), so 89 seconds of usage shows up as 1 minute, not 1.48. For a habit tracker that wants whole, glanceable numbers, that rounding-down is fine — and it never inflates the figure.

## The public methods: same engine, different windows

`queryMinutesFromEvents` is private. The screens call thin public wrappers that just pick a time window:

| Method | Window | Used by |
| --- | --- | --- |
| `getTodayMinutesByPlatform` | midnight today → now | Dashboard's live "today" figure |
| `getYesterdayMinutesByPlatform` | yesterday midnight → today midnight | Dashboard comparison, Streaks, the daily-summary notification, and the optional cloud sync |
| `getWeekMinutesByPlatform` | exactly 7×24 h ago → now | Rolling 7-day rollup (defined for reuse; no caller wires it up today) |
| `getDailyMinutesByPlatform(days)` | per-day buckets for *N* days | Stats screen chart & vs-last-week (called with `14`) |

`getTodayMinutesByPlatform` and `getYesterdayMinutesByPlatform` compute "midnight" with a `Calendar` whose hour, minute, second, and millisecond are all zeroed out, then call the shared engine. `getWeekMinutesByPlatform` is different: it does *not* snap to midnight — it just subtracts a flat `7 × 24 × 60 × 60 × 1000` milliseconds from "now," giving a rolling 7-day window.

> 💡 **Concept —** A method can exist before anything calls it. `getWeekMinutesByPlatform` is a ready-made building block — wiring it into a screen is a one-liner whenever a "last 7 days" view is needed. Including it costs almost nothing and keeps the helper's window-picking logic in one place.

`getDailyMinutesByPlatform` is a richer variant that does its own event-walking instead of delegating to `queryMinutesFromEvents`. It returns a map *keyed by ISO date* (e.g. `"2026-06-19"`), pre-seeds every date in the window with zeroes so callers always get a complete window, and attributes each finished session to the **local date of its start** via its own nested `attribute(pkg, start, end)` helper. That nested helper looks up the session's start date with `Instant.ofEpochMilli(start).atZone(zone).toLocalDate()` and, if that date falls outside the requested window, drops the session entirely. That pre-seeding is what lets the Stats screen draw a bar for every day even when some days had no usage.

> ⚠️ **Gotcha —** Because `getDailyMinutesByPlatform` attributes a session to the date it *started*, a session that crosses midnight counts entirely toward the earlier day. This keeps the per-day buckets simple and is a reasonable trade-off for a habit tracker.

## The big picture

LoopOut measures screen time by replaying Android's own raw activity events and timing each foreground session with a per-app stopwatch — never trusting the lossy aggregate API, and never duplicating the data into its own database. It gates everything behind the sensitive Usage Access permission, checks that permission through `AppOpsManager`, and fails safe to zeros whenever access is missing or something goes wrong. The result is a single small, stateless helper that any screen can call to get accurate, up-to-the-second minutes per platform.
