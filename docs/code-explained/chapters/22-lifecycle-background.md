One of the first things that trips up newcomers to Android is that *you* almost never decide when your code runs — the operating system does. Your app is a guest on a phone that the OS is constantly trying to keep fast, cool, and battery-friendly. This chapter explains the rules the OS plays by: the **lifecycle** of an Activity, the different flavours of Service, why long-running work needs a *foreground service*, why an AccessibilityService gets special treatment, and how LoopOut survives its own process being killed — all grounded in the real services you've already met.

## What "lifecycle" means

In a desktop program, `main()` runs top to bottom and the program lives until it returns. Android is different. Your code lives inside **components** — Activities (screens), Services (background workers), and a few others — and the OS calls specific methods on those components at specific moments. Those methods are the component's **lifecycle callbacks**.

> 💡 **Concept —** *lifecycle callback.* A method the system calls *for* you when a component's situation changes (it becomes visible, goes to the background, is destroyed…). You don't call these yourself; you *override* them to react. Think of them as doorbell buttons the OS presses, not buttons you press.

The golden rule: **the system can create and destroy your components at almost any time** to reclaim memory or save battery. Your job is to put the right work in the right callback.

## The Activity lifecycle (MainActivity)

An **Activity** is one screen of an app. `MainActivity` is the only Activity in LoopOut — it hosts the entire Compose UI. Here is how it starts up, condensed from `MainActivity.kt`:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getInstance(applicationContext)
        // ...lifecycleScope work, ThemeController.init, notification channels...
        enableEdgeToEdge()
        setContent {
            BrainRotTrackerTheme {
                Surface(...) { MainNavigation() }
            }
        }
    }
}
```

`onCreate` is the **first** callback in an Activity's life. The system calls it once, when the screen is being built. That is exactly why setup work lives here: getting the database singleton, initializing the theme (`ThemeController.init`), creating notification channels, and finally `setContent { ... }`, which hands Compose the UI tree to draw.

The four lifecycle callbacks a beginner should know, in the order they typically fire:

| Callback | When the system calls it | What you'd do here |
| --- | --- | --- |
| `onCreate` | Screen is first created | One-time setup: read DB, build UI |
| `onResume` | Screen is in front and interactive | Start things the user must see live |
| `onPause` | Something is partially covering the screen | Pause/save quickly — must be fast |
| `onDestroy` | Screen is being torn down | Release resources |

`MainActivity` only overrides `onCreate` — it doesn't need the others, because Compose and the ViewModels handle live data on their own. But notice the *philosophy*: anything that should happen "every time the app is opened" goes in `onCreate`.

> 💡 **Concept —** *why catch-up work lives in `onCreate`.* LoopOut has no background scheduler for streaks. Instead, opening the app *is* the trigger. `onCreate` calls `repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))` so any days that ended while the app was closed get evaluated the moment you open it. We'll come back to this "lazy back-fill" idea at the end.

> ⚠️ **Gotcha —** *`onCreate` runs again on rotation.* By default, rotating the phone *destroys and recreates* the Activity, so `onCreate` runs a second time. This is why you should never store important state in plain Activity fields — put it in a ViewModel or the database. (LoopOut's `onCreate` work is idempotent: running it twice does no harm.)

### lifecycleScope: coroutines that die with the Activity

Look again at the startup work:

```kotlin
lifecycleScope.launch(Dispatchers.IO) {
    val repository = UsageRepository(db)
    repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
    try {
        UsageSyncManager(applicationContext, repository).syncIfEnabled()
    } catch (_: Exception) {
    }
}
```

`lifecycleScope` is a **coroutine scope** tied to the Activity's lifecycle.

> 💡 **Concept —** *coroutine & scope.* A *coroutine* is a lightweight background task that can pause ("suspend") and resume without blocking a thread. A *scope* is the leash it runs on: when the scope is cancelled, every coroutine launched in it is cancelled too. `lifecycleScope` is cancelled automatically when the Activity is destroyed.

`Dispatchers.IO` means "run this on a background thread pool built for disk/network work," so the database read never freezes the UI. Because it's `lifecycleScope`, if the user closes the app mid-sync, the coroutine is cleaned up for you — no leaked work pointing at a dead screen.

## Services: code with no screen

A **Service** is an Android component that does work without a UI. There are different ways a service can relate to the system, and LoopOut quietly uses several patterns.

| Kind | Who keeps it alive | Example in LoopOut |
| --- | --- | --- |
| **Started** service | Lives until it stops itself or is killed | `FloatingCounterService` (launched with an Intent) |
| **Bound** service | Lives while another component is "connected" to it | The *concept* behind `AccessibilityService` (the system binds to it) |
| **Foreground** service | A started service the user can see (notification) | `FloatingCounterService` again |

### Started services and onStartCommand

`FloatingCounterService` is a classic **started service**. The accessibility service launches it with an `Intent` (`startForegroundService(intent)` inside `startOverlayService()` in `ReelCounterService.kt`). The system responds by calling, in order, `onCreate` then `onStartCommand`:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    notifHelper.createChannels()
    startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notifHelper.getServiceNotification())
    return START_STICKY
}
```

Two important things here. First, `startForeground(...)` is what *promotes* this started service to a **foreground service** — more on that next. Second, the return value `START_STICKY` tells the system: "if you kill me to save memory, please recreate me when you can." That return value is the service's restart policy.

> 💡 **Concept —** *`onBind` returning null.* Notice `override fun onBind(intent: Intent?): IBinder? = null`. A service that returns `null` from `onBind` cannot be *bound* to — it's purely a started service. LoopOut doesn't bind to it; instead it reaches the running instance through a `companion object` field, `FloatingCounterService.instance`, which the service sets in `onCreate` and clears in `onDestroy`. That's a simpler (if blunter) way for the two services to talk than binding.

## Why long work needs a foreground service

Modern Android is ruthless about background apps. If your service is doing invisible work, the OS will happily kill it within seconds to save battery. A **foreground service** is the official "this work matters and the user knows about it" escape hatch: in exchange for keeping your service alive, you must show a **persistent notification** the user can't swipe away.

That's the whole reason `FloatingCounterService` calls `startForeground(...)` with `getServiceNotification()` — the floating counter bubble and blocking scrim need to stay drawn while you scroll, so the service must not be killed mid-scroll.

> ⚠️ **Gotcha —** *you have a few seconds to call `startForeground`.* When you start a service with `startForegroundService(...)`, you've promised the system that the service will call `startForeground(...)` almost immediately (within roughly 5 seconds). If it doesn't, the system kills the app with a `ForegroundServiceDidNotStartInTimeException` crash. That's why the call is the very first thing `onStartCommand` does after creating its channels.

### FOREGROUND_SERVICE_SPECIAL_USE

Since Android 14, every foreground service must declare *why* it runs as a `foregroundServiceType`. The system offers named categories — `location`, `mediaPlayback`, `camera`, and so on — each with its own rules. LoopOut's overlay doesn't fit any of them, so it uses the catch-all type, declared in `AndroidManifest.xml`:

```xml
<service
    android:name=".service.FloatingCounterService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

with the matching permission near the top of the manifest:

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
```

`specialUse` is the "none of the standard buckets fit, here's a justification" type. (The justification itself lives in a `<property>` element — `PROPERTY_SPECIAL_USE_FGS_SUBTYPE` — also in the manifest.) `android:exported="false"` means no *other* app can start this service — only LoopOut itself can, which is correct because the overlay is meaningless outside the app.

> 💡 **Concept —** *start it only when needed.* Rather than run the overlay 24/7, `ReelCounterService` starts `FloatingCounterService` on demand (when a tracked app comes to the foreground) and tears it down when you leave all tracked apps. In `ReelCounterService.kt`, the heartbeat calls `stopOverlayService()` once you leave, with the comment *"so it stops counting as background activity."* A foreground service you don't need is still a battery cost, so LoopOut keeps it alive only while it earns its keep.

## The AccessibilityService: a service the *system* owns

`ReelCounterService` is a different animal. It extends `AccessibilityService`, and crucially, **the app never starts it.** The *system* binds to it once the user flips it on in Settings, and keeps it bound for as long as it's enabled — across reboots, across your app being swiped away, essentially forever.

> 💡 **Concept —** *a system-bound service.* An AccessibilityService is a *bound* service whose lifetime is managed by the OS, not by your code. Because the OS is the one holding the binding, it has a very long life — far longer than a normal started service. That long life is exactly why LoopOut puts its core detection logic here.

Its "first callback" isn't `onCreate` or `onStartCommand` but `onServiceConnected`, which the system calls when it binds:

```kotlin
override fun onServiceConnected() {
    super.onServiceConnected()
    isRunning = true
    instance = this
    val db = AppDatabase.getInstance(applicationContext)
    repository = UsageRepository(db)
    // ...
    serviceScope.launch {
        val todayLog = repository.getTodayLogSnapshot()
        todayLog?.let { log ->
            Platform.entries.forEach { p ->
                reelCounts[p.packageName] = log.getReelsForPlatform(p)
            }
        }
        repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
    }
}
```

Two lifecycle lessons here. First, like `MainActivity`, this is where setup and **state reload** happen. Second, notice it *reloads* `reelCounts` from the database — it doesn't assume the in-memory map already holds today's counts. Why? Process death.

### serviceScope: the service's own coroutine leash

The service builds its own scope rather than borrowing `lifecycleScope` (services don't have one):

```kotlin
private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
```

`Dispatchers.IO` runs the work off the main thread; `SupervisorJob()` means one failing child coroutine won't cancel its siblings. And it is cleaned up in the matching teardown callback:

```kotlin
override fun onDestroy() {
    super.onDestroy()
    isRunning = false
    instance = null
    heartbeatActive = false
    mainHandler.removeCallbacks(heartbeatRunnable)
    serviceScope.cancel()
    stopOverlayService()
}
```

`serviceScope.cancel()` stops every database/sync coroutine the service launched; `mainHandler.removeCallbacks(...)` stops the repeating 1-second heartbeat. This is the disciplined pairing the lifecycle expects: whatever you start in `onServiceConnected`, you stop in `onDestroy`.

> ⚠️ **Gotcha —** *`onInterrupt` is not `onDestroy`.* AccessibilityServices must override `onInterrupt()` (LoopOut's is empty: `override fun onInterrupt() {}`). It's called when feedback should stop, *not* when the service dies. Cleanup belongs in `onDestroy`.

## Process death and why counters are reset/reloaded

Here is the mental model that makes everything click: **your whole app runs inside a single Linux process, and Android can kill that process at any time.** When the user isn't looking and memory gets tight, the OS terminates the process — every object, every field, every in-memory map vanishes. No `onDestroy` is even guaranteed to run.

So `ReelCounterService`'s in-memory `reelCounts`, `seenIdentities`, and friends are *not* the source of truth. They're a fast cache. The truth lives in the Room database. When the system later rebinds the service, `onServiceConnected` runs again and rebuilds the cache from the DB (`reelCounts[p.packageName] = log.getReelsForPlatform(p)`). If a reel was counted before the kill, it was already written to the database in `tryCountReel` via `repository.incrementReelCount(platform)`, so nothing is lost.

The same logic governs the **midnight rollover**. `checkDayRollover()` doesn't trust a timer to fire at exactly 00:00 (the process might be dead then). Instead it checks the date on *every* accessibility event and resets the in-memory maps when the day has changed:

```kotlin
private fun checkDayRollover() {
    val today = LocalDate.now().toString()
    if (today == currentDay) return
    val previousDay = currentDay
    currentDay = today
    reelCounts.clear()
    // ...clear the other per-session maps...
    serviceScope.launch {
        repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
        // ...recap notification, milestone reset, cloud sync...
    }
}
```

The counters reset because it's a new day; they *reload* (in `onServiceConnected`) because it's a new process. Same cache, two different reasons to refill it.

## Avoiding WorkManager with lazy back-fill

Most apps that need "do something once a day" reach for **WorkManager** — Android's scheduler for deferrable background jobs that survive reboots. LoopOut deliberately doesn't. As the project notes put it, streaks are written *lazily* instead.

The trick: `repository.evaluateStreaksUpTo(yesterday)` writes streak rows for *every* unevaluated past day, not just the most recent one. So the app never needs a job to run "at the right time." It just needs *some* code path to call this method *eventually*, and the missing days are filled in. Those call sites are exactly the lifecycle moments we've seen:

| Trigger | Lifecycle moment |
| --- | --- |
| App opened | `MainActivity.onCreate` |
| Service (re)connected | `ReelCounterService.onServiceConnected` |
| Day changed mid-scroll | `checkDayRollover()` on an accessibility event |

> 💡 **Concept —** *lazy back-fill.* Instead of *pushing* work onto a schedule, the app *pulls* the work the next time it naturally wakes up. A phone that was switched off all night simply catches up the moment you next open the app or start doomscrolling. No alarms, no scheduler, no reboot receiver — just idempotent catch-up wired into callbacks that are guaranteed to fire sooner or later.

This is a recurring LoopOut design value: lean on lifecycle callbacks the system already promises to call, keep that work idempotent, and treat the database as the only durable state. Get those two habits right and the unpredictable timing of the Android background model stops being scary — it becomes just a set of doorbells you've learned to answer.
