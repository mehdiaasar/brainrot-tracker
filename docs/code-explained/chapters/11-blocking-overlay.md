LoopOut does more than count reels — when you blow past your daily limit, it can draw a full-screen wall *on top of* Instagram or TikTok and gently shove you out. That trick relies on two pieces of Android machinery most apps never touch: a **foreground service** that keeps running while you scroll, and a **system overlay** that lets the app draw outside its own window. This chapter walks through both, then tours `FloatingCounterService` and the three blocking "moods" that decide how aggressive the wall is.

## Background workers: what a Service is

In Android, a **Service** is a component with no screen of its own. It is a way to run code that should keep going even when the user isn't looking at your app. Think of it as a back-office worker: no desk that faces customers, just a job to do.

There is a catch. Modern Android is ruthless about killing background work to save battery. A plain background service can be torn down at any moment. The escape hatch is a **foreground service**: you tell the system "this work is important and the user knows about it," and in exchange you *must* show a persistent notification so the user can see something is running. Music players, fitness trackers, and download managers all use foreground services.

> 💡 **Concept —** A *foreground service* is a long-running task that the system promises not to kill arbitrarily, in exchange for a visible, ongoing notification. The notification is the deal: the user is never surprised that the app is active.

Android 14+ requires you to declare *why* you run a foreground service. LoopOut's reason doesn't fit the standard categories (it isn't media playback or location), so it uses the catch-all `specialUse` type. You can see this in the manifest:

```xml
<service
    android:name=".service.FloatingCounterService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

- `android:name` — the class that implements the service.
- `android:exported="false"` — **no other app can start or talk to this service.** Only LoopOut's own code can. This is a security boundary; an exported overlay service would be a gift to malware.
- `android:foregroundServiceType="specialUse"` — the Android 14 declaration that this is a non-standard foreground service.

The matching code lives in `onStartCommand` in `FloatingCounterService.kt`:

```kotlin
override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
    notifHelper.createChannels()
    startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notifHelper.getServiceNotification())
    return START_STICKY
}
```

`startForeground(...)` is the line that promotes the service to foreground status, attaching the persistent notification. `START_STICKY` tells Android: if you kill me to reclaim memory, recreate me when you can.

## Drawing outside the lines: SYSTEM_ALERT_WINDOW

Normally an app can only draw inside its own window. The floating counter bubble and the blocking wall both need to appear *over other apps* — over Instagram itself. That requires a special permission, `SYSTEM_ALERT_WINDOW`, which the user must grant by hand in system settings (it is too powerful to be a routine runtime prompt).

> ⚠️ **Gotcha —** `SYSTEM_ALERT_WINDOW` is the "draw over other apps" permission. Because an overlay can cover any screen, Android treats it as high-risk: it can't be auto-granted, and Google Play scrutinizes apps that use it. LoopOut justifies it as the counter HUD and the blocking scrim.

Overlays are added through the **`WindowManager`**, the system service that owns every window on screen. You hand it a `View` plus a `LayoutParams`, and it paints that view on top of everything. The window *type* in those params decides which layer it lands in.

```kotlin
private fun overlayType(): Int = when {
    useAccessibilityOverlay -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    else -> @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
}
```

- `TYPE_ACCESSIBILITY_OVERLAY` — the highest layer, available only to an accessibility service. LoopOut prefers this so its blocking wall sits *above* every normal overlay (even another app's "draw over" bubble).
- `TYPE_APPLICATION_OVERLAY` — the standard "draw over other apps" layer on Android 8+ (`Build.VERSION_CODES.O`).
- `TYPE_PHONE` — the deprecated fallback for ancient devices.

To use the top-most accessibility layer, the service borrows the `WindowManager` of the *other* service — `ReelCounterService`, the accessibility service from the previous chapter. This happens in `onCreate`:

```kotlin
val acc = ReelCounterService.instance
overlayWindowManager = (acc?.getSystemService(WINDOW_SERVICE) as? WindowManager) ?: windowManager
useAccessibilityOverlay = acc != null
```

If the accessibility service is alive, its `WindowManager` is used and overlays go in the privileged layer. If not, it falls back to the service's own `WindowManager`. There's also a runtime safety net: some phone makers reject accessibility overlays, so `show()` catches the failure and retries as a plain app overlay so the counter still appears.

## Who starts and stops the service

`FloatingCounterService` does **not** run all day. `ReelCounterService` starts it on demand — only while you're actually inside a tracked app — and stops it the moment you leave. That keeps LoopOut's background footprint near zero.

```kotlin
private fun startOverlayService() {
    if (FloatingCounterService.instance != null) return // already running
    try {
        val intent = Intent(this, FloatingCounterService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    } ...
```

When you leave every tracked app, the accessibility service's heartbeat calls `stopOverlayService()`, which tears the overlay service down entirely (its `onDestroy` removes any lingering windows). The two services talk via a `companion object` `instance` reference — a simple static handle to the live object — rather than Android's binding machinery.

> 💡 **Concept —** A `companion object` is Kotlin's version of static members. Holding `var instance: FloatingCounterService?` there lets any class reach the single live service through `FloatingCounterService.instance` without the ceremony of `bindService`/`ServiceConnection`. The trade-off is that you must null it out in `onDestroy`, which both services do.

## The counter bubble and its popup

While a reel feed is open, the service draws a small rounded **pill** (a brain mascot plus today's total count) that you can drag around. Its touch handler distinguishes a *tap* from a *drag*:

```kotlin
MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
    pill?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start()
    val moved = kotlin.math.hypot(event.rawX - touchX, event.rawY - touchY)
    val elapsed = System.currentTimeMillis() - downTime
    if (event.action == MotionEvent.ACTION_UP && moved < slop && elapsed < 250) togglePopup()
    return true
}
```

A press that moved less than `slop` (8 dp) and lasted under 250 ms counts as a tap, which calls `togglePopup()` to open the stats card. Anything else is treated as a drag (handled in `ACTION_MOVE`, which updates the window position via `overlayWm().updateViewLayout(...)`).

The popup itself is a second overlay: a dark scrim (`0xAA000000`, semi-transparent black) filling the screen, with a centered card showing the mood brain, brain health, the per-platform breakdown, and "Open app" / "Close" buttons. Tapping the scrim dismisses it.

## evaluateBlocking: the decision to put up the wall

The wall is raised by `evaluateBlocking()` over in `ReelCounterService.kt`. Blocking is **total-based**: it sums reels across *all* tracked apps and compares that to one global limit.

```kotlin
private fun evaluateBlocking(platform: Platform, pkg: String) {
    val prefs = getSharedPreferences(FloatingCounterService.PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean("blocking_enabled", false)) return
    // Fall back to the entity default so blocking works before any limit was saved
    val limit = limitsCache.values.maxOrNull() ?: 30
    val total = reelCounts.values.sum()
    if (total >= limit) {
        val mode = BlockingMode.fromPref(prefs.getString(BlockingMode.PREF_KEY, null))
        FloatingCounterService.instance?.showBlockingOverlay(platform, limit, mode)
    }
}
```

Line by line:
- It first checks the `blocking_enabled` preference; if the user turned blocking off, it returns immediately.
- `limit` is the largest cached per-platform limit, or **`30`** if no `UserLimits` row exists yet. (The Limits screen writes the same slider value to every platform, so taking the max yields that one shared cap.) This **default-limit fallback** means blocking works even on a fresh install before any limit was saved.
- `total` is the sum of all in-memory reel counts.
- If you're at or over the limit, it reads the user's `BlockingMode` and tells the overlay service to show the wall.

This method is deliberately cheap and is called from **three** triggers, so the wall feels instant no matter how you hit the limit:

| Trigger | Where it fires | Why |
| --- | --- | --- |
| Per counted reel | inside `tryCountReel(...)` after a reel is recorded | catches the exact swipe that pushes you over |
| Window-state change | on `TYPE_WINDOW_STATE_CHANGED` (a tracked app reopened) | re-blocks when you reopen a blocked app |
| 1-second heartbeat | every `HEARTBEAT_MS` (1000 ms) tick while a tracked app is foregrounded | re-blocks ~1 s after a snooze expires |

## The three blocking modes

What the wall *does* — whether you can escape and for how long — is set by `BlockingMode`. The whole enum is tiny:

```kotlin
enum class BlockingMode {
    HARD, SNOOZE, REMIND;

    companion object {
        const val PREF_KEY = Prefs.BLOCKING_MODE
        const val SNOOZE_MS = 5 * 60_000L

        fun fromPref(value: String?): BlockingMode =
            entries.find { it.name == value } ?: REMIND
    }
}
```

`SNOOZE_MS` is five minutes in milliseconds. `fromPref` reads the saved mode string and defaults to `REMIND` (the gentlest mode) for any unknown or missing value. Here's how each mode behaves:

| Mode | Escape button | Suppression after dismiss | Returns when |
| --- | --- | --- | --- |
| **HARD** | "Close app" only | Never suppressed | every time you reopen a tracked app, until midnight |
| **SNOOZE** | "Snooze 5 min" | 5-minute grace, stored as epoch millis | grace expires (re-checked by the heartbeat) |
| **REMIND** | "Got it" | Once per foreground session | you leave all tracked apps and come back |

The mode logic lives at the top of `showBlockingOverlay`, which decides whether to even draw the wall:

```kotlin
when (mode) {
    BlockingMode.HARD -> Unit // never suppressed
    BlockingMode.SNOOZE -> {
        val snoozeUntil = prefs.getLong(SNOOZE_KEY, 0L)
        if (System.currentTimeMillis() < snoozeUntil) return@post
    }
    BlockingMode.REMIND -> {
        if (remindDismissedThisSession) return@post
    }
}
```

- **HARD** does nothing here — it is *never* suppressed, so the wall always shows.
- **SNOOZE** reads `SNOOZE_KEY` (a single global epoch-millis timestamp, `snooze_until_all`). If "now" is still before that time, the wall is skipped.
- **REMIND** checks the in-memory flag `remindDismissedThisSession`; if you already dismissed it this session, the wall stays down.

> 💡 **Concept —** All of this runs inside `mainHandler.post { ... }`, so `return@post` bails out of *that posted block*, not the whole method. Overlay views can only be touched from the main (UI) thread, and `showBlockingOverlay` may be called from a background coroutine — posting hops the work onto the right thread.

The buttons that *set* that suppression are built in `buildBlockingCard`. Every mode gets a primary "Close app" button that sends you home:

```kotlin
actions.addView(actionButton("Close app", primary = true) {
    removeBlockingOverlay()
    ReelCounterService.instance?.performGlobalAction(
        android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
    )
})
```

`performGlobalAction(GLOBAL_ACTION_HOME)` is an accessibility superpower: it presses the Home button for you, ejecting you from the doomscroll. The *secondary* button depends on the mode:

```kotlin
when (mode) {
    BlockingMode.HARD -> Unit
    BlockingMode.SNOOZE -> actions.addView(actionButton("Snooze 5 min", primary = false, leftMargin = dp(8f)) {
        prefs.edit()
            .putLong(SNOOZE_KEY, System.currentTimeMillis() + BlockingMode.SNOOZE_MS)
            .apply()
        removeBlockingOverlay()
    })
    BlockingMode.REMIND -> actions.addView(actionButton("Got it", primary = false, leftMargin = dp(8f)) {
        remindDismissedThisSession = true
        removeBlockingOverlay()
    })
}
```

- **HARD** adds *nothing* — there's no way to keep scrolling. "Close app" is your only exit.
- **SNOOZE** writes `now + SNOOZE_MS` into the global snooze pref, then removes the wall. The next time `evaluateBlocking` fires (within a second), it sees you're still over the limit but the snooze hasn't expired, so it stays away — until five minutes pass.
- **REMIND** flips `remindDismissedThisSession = true` and removes the wall, letting you scroll on for the rest of this session.

## How a REMIND session ends (and its limitation)

The REMIND flag is cleared by `onAllTrackedAppsLeft`, called from the accessibility service when you leave every tracked app:

```kotlin
fun onAllTrackedAppsLeft() {
    remindDismissedThisSession = false
}
```

The accessibility heartbeat polls the foreground app once a second; when it sees you've left all tracked apps, it calls this and tears down the overlay. Re-opening a tracked app starts a fresh session, so the REMIND wall can prompt you again.

> ⚠️ **Gotcha —** REMIND's session-end detection is **best-effort.** The heartbeat only runs while a reel feed is open; once you leave all tracked apps the overlay service stops, so detection relies on that last poll. If detection misses, a stale `remindDismissedThisSession` could let a new session slip through unblocked. HARD and SNOOZE don't have this fragility — HARD never suppresses, and SNOOZE's expiry is a timestamp in a preference that survives even a service restart.

## Putting it together

When you cross your limit inside Instagram, the flow is: `tryCountReel` records the swipe → calls `evaluateBlocking` → total ≥ limit, so it asks `FloatingCounterService.showBlockingOverlay` → the mode check decides whether to draw → `WindowManager.addView` paints a full-screen scrim in the accessibility layer → you tap "Close app" → `performGlobalAction(GLOBAL_ACTION_HOME)` drops you on the home screen. The wall, the counter pill, and the popup are all just `View`s handed to `WindowManager`, made possible by the foreground service and the `SYSTEM_ALERT_WINDOW` permission — the two pieces of Android plumbing this chapter set out to explain.
