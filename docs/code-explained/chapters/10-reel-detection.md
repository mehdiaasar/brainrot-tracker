This is the chapter where LoopOut earns its name. Everything else in the app — the dashboard, the streaks, the blocking overlay — is downstream of one question: *did the user just swipe to another reel?* Answering that reliably, without the cooperation of Instagram, YouTube, TikTok, or Snapchat, is the hardest problem in the codebase, and it lives almost entirely in one file: `ReelCounterService.kt`.

## What is an AccessibilityService?

Android has a feature designed for people who can't see or use the screen normally: an **AccessibilityService**. It's a background program you grant special permission to that gets to *observe* what's happening on screen across all apps — what text appears, what buttons exist, when the user scrolls — and can even act on the user's behalf (tap, swipe, read aloud). Screen readers like TalkBack are built on it.

> 💡 **Concept —** Think of an AccessibilityService as a helpful assistant standing behind you, watching your screen. It can't reach *inside* other apps and read their private data, but it can describe what's visible the same way a screen reader would: "there's a scrollable list here, a button labelled '@someuser' there." LoopOut uses that same window to notice when a reel feed is open and when you've swiped past one.

We use it because there is no public API that says "the user watched a reel." Instagram doesn't broadcast that. So instead of asking the app, we *watch* the screen — exactly like an accessibility tool — and infer scrolling from what we see.

> ⚠️ **Gotcha —** This is a powerful, sensitive permission. Google Play scrutinizes it heavily, which is why onboarding shows a prominent-disclosure dialog *before* sending the user to settings. The app only ever reads on-screen structure to count reels; it never logs the content of what you watch.

## Declaring the service: `accessibility_service_config.xml`

Before any code runs, Android needs a configuration file describing what the service wants to listen to. Here it is in full:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagReportViewIds"
    android:settingsActivity="com.example.brainrottracker.MainActivity"
    android:packageNames="com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically,com.snapchat.android" />
```

Line by line, this is what each attribute buys us (`accessibility_service_config.xml`):

| Attribute | What it does |
|---|---|
| `accessibilityEventTypes="typeAllMask"` | Receive *every* type of accessibility event. We filter down to the three we care about in code. |
| `canRetrieveWindowContent="true"` | The crucial one — lets the service walk the view hierarchy (read the on-screen layout). Without this, we couldn't inspect anything. |
| `notificationTimeout="100"` | Wait at least 100 ms between events of the same type, so a burst of changes doesn't drown us. |
| `accessibilityFlags="flagReportViewIds"` | Include each view's resource id (like `clips_viewer_view_pager`). Our detection leans heavily on these ids. |
| `settingsActivity` | Which screen opens when the user taps "Settings" for the service. |
| `packageNames="…"` | Restrict the service to exactly four apps: Instagram, YouTube, TikTok (`com.zhiliaoapp.musically`), Snapchat. We get zero events from any other app. |

> 💡 **Concept —** A *view hierarchy* is the tree of UI elements on screen — a window contains layouts, which contain buttons, lists, text, and so on, nested like folders. `canRetrieveWindowContent` is permission to read that tree; `flagReportViewIds` adds each element's name tag to it.

The four package names match the `Platform` enum exactly (`Platform.kt`):

```kotlin
enum class Platform(val packageName: String, val displayName: String, val emoji: String) {
    INSTAGRAM("com.instagram.android", "Instagram", "📸"),
    YOUTUBE("com.google.android.youtube", "YouTube", "▶️"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok", "🎵"),
    SNAPCHAT("com.snapchat.android", "Snapchat", "👻");

    companion object {
        fun fromPackageName(packageName: String): Platform? =
            entries.find { it.packageName == packageName }
    }
}
```

`fromPackageName` is the bridge: given a package string from an event, it returns the matching `Platform` or `null`. The service calls it constantly to decide "is this an app I track?"

## The big idea: count by *index change*, not by events

The comment at the top of `ReelCounterService.kt` states the philosophy plainly:

```kotlin
 * Instead of guessing from event types (which are unreliable and noisy),
 * we inspect the actual view tree to:
 *   1. Find a full-screen vertical pager (the Reels/Shorts viewer)
 *   2. Read the currently visible item INDEX from the pager
 *   3. Only count when the index CHANGES (= user swiped to next reel)
```

A reel feed is a **full-screen single-column pager**: one video fills the screen, you swipe up for the next. That's very different from a normal feed (many small posts visible at once). Detecting "single column, one item visible at a time" is how we tell a reel viewer apart from everything else. We only count when the visible item *changes* — that means a swipe happened, not just a like animation or a comment popping in.

## Receiving events: `onAccessibilityEvent`

Every observed change arrives here. This is the front door:

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    event ?: return
    checkDayRollover()
    val pkg = event.packageName?.toString() ?: return
    val platform = Platform.fromPackageName(pkg) ?: return

    startOverlayService()

    when (event.eventType) {
        AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { … }
        AccessibilityEvent.TYPE_VIEW_SCROLLED -> { … }
        AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> { … }
        else -> { … }
    }
}
```

- `event ?: return` — if the event is null, bail. (Kotlin's elvis operator: "use this, or else do that.")
- `checkDayRollover()` runs on *every* event — that's how midnight reset happens lazily (more below).
- We resolve the package and `Platform`; if it's not one of our four apps, return.
- `startOverlayService()` makes sure the floating counter bubble service is up — it's a cheap no-op if it's already running.

The three event types we act on:

| Event type | Meaning | What we do |
|---|---|---|
| `TYPE_WINDOW_STATE_CHANGED` | The foreground window changed (app opened / screen switched). | Clear the inspection cooldown, re-check blocking, start the heartbeat. |
| `TYPE_VIEW_SCROLLED` | Something scrolled. | TikTok counting + keep the bubble visible. |
| `TYPE_WINDOW_CONTENT_CHANGED` | The on-screen content changed (a `SUBTREE` change). | Inspect the view tree to detect reels (the main path). |

```kotlin
AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
    val isSubtree = (event.contentChangeTypes and
            AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
    if (isSubtree) {
        handleContentChanged(event, platform, pkg)
    }
}
```

`contentChangeTypes` is a bitmask — a single integer where each bit flags a kind of change. The `and` extracts just the SUBTREE bit; `!= 0` means "that flag is set." We only inspect on subtree changes because those accompany the layout swaps a swipe produces.

## Rate limiting and debouncing

Accessibility events fire *constantly* — dozens per second on a busy screen. Inspecting the whole view tree each time would melt the battery, and counting on every event would massively over-count. Two guards prevent that.

**Inspection cooldown (0.8 s)** — don't walk the tree more than once every 800 ms per app (`ReelCounterService.kt`):

```kotlin
private val INSPECTION_COOLDOWN_MS = 800L
…
val cooldownPassed = (now - lastInspection >= INSPECTION_COOLDOWN_MS)
if (!cooldownPassed) return
```

**Count debounce (1.2 s)** — even once we've decided to count, ignore a second count on the same app within 1200 ms:

```kotlin
private val DEBOUNCE_MS = 1200L
…
if (now - (lastCountedMs[pkg] ?: 0L) < DEBOUNCE_MS) {
    Log.d(TAG, "  ⏳ debounced ($source)")
    return
}
```

> 💡 **Concept —** *Debouncing* means "wait until things settle before reacting." A light switch that ignores a second flick within a second is debounced. Here it stops one swipe from registering as two reels.

## Walking the tree: breadth-first search

To find the reel pager, the service does a **BFS (breadth-first search)** of the view tree. BFS explores a tree level by level: look at the root, then all its children, then all *their* children, using a queue. The simplest example is `findReelPager`, used for Snapchat (`ReelCounterService.kt`):

```kotlin
val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>() // node, depth
queue.add(root to 0)
…
while (queue.isNotEmpty()) {
    val (node, depth) = queue.removeFirst()
    if (depth > MAX_TREE_DEPTH) continue
    …
    for (i in 0 until node.childCount) {
        val child = node.getChild(i)
        if (child != null) { toRecycle.add(child); queue.add(child to depth + 1) }
    }
}
```

Each tree node is an `AccessibilityNodeInfo`. The pager test asks: is it scrollable, does its class name contain `recyclerview`/`viewpager`/`pager`, and does its `collectionInfo` report a single column?

```kotlin
if (idQualifies && node.isScrollable && isPagerClass) {
    val collection = node.collectionInfo
    if (collection != null && collection.columnCount <= 1) {
```

It then counts visible children. A reel pager shows the current item plus maybe one preloaded — "at most 2 visible children" — whereas a normal feed shows many:

```kotlin
val accept = if (requiredPagerId != null) node.isVisibleToUser && visibleChildCount >= 1
             else visibleChildCount in 1..2 && currentItemIndex != null
```

The current item's position comes from `CollectionItemInfo.rowIndex`. `MAX_TREE_DEPTH = 12` caps how deep we dig so we never get lost in a huge tree.

> ⚠️ **Gotcha —** Every `AccessibilityNodeInfo` you fetch must be *recycled* (returned to the system) or you leak memory. That's why every traversal collects children into a `toRecycle` list and recycles them in a `finally` block.

## A different strategy per app

The four apps expose their internals very differently, so each gets a tailored approach. This table is the heart of the chapter:

| Platform | Strategy | How a reel is identified | Where in code |
|---|---|---|---|
| **TikTok** | Scroll direction only | The whole app is short videos, and it exposes no reliable feed position — so a *forward* scroll = one reel. | `handleViewScrolled` → `isForwardScroll` |
| **Snapchat Spotlight** | Pager index (high-water) | Find the single-column pager; count when its `rowIndex` advances past the furthest seen. | `handleGenericReels` → `findReelPager` → `countByHighWater` |
| **Instagram Reels** | Signature identity + dwell | Requires a visible `clips_viewer_view_pager`; identity = author handle + caption (or row index). | `probeInstagram` → `evaluateReelSession` |
| **YouTube Shorts** | Signature identity + dwell | Detect Shorts via `reel_*` view ids (or heuristic); identity = `@handle` + title. | `probeYouTube` → `inspectYouTubeShorts` |

### TikTok — keep it simple

```kotlin
if (platform == Platform.TIKTOK) {
    if (isForwardScroll(event)) {
        tryCountReel(platform, pkg, "VIEW_SCROLLED/TikTok")
    }
    return
}
```

`isForwardScroll` compares the new scroll position (`fromIndex` or `scrollY`) to the previous one; bigger = moved forward = a new video. No tree inspection at all.

### Snapchat — high-water index

```kotlin
private fun countByHighWater(index: Int, platform: Platform, pkg: String, source: String) {
    val maxIndex = maxPagerIndex[pkg]
    if (maxIndex == null) {
        maxPagerIndex[pkg] = index          // first reel = baseline, not counted
    } else if (index > maxIndex) {
        maxPagerIndex[pkg] = index           // advanced to a new reel → count
        tryCountReel(platform, pkg, source)
    } // else: scrolled back to an already-seen reel → ignore
}
```

The "high-water mark" is the furthest index reached. Scrolling back up to a reel you already watched and forward again won't recount it — only genuinely *new* territory counts.

### Instagram and YouTube — identity + dwell

These two share an engine. First a `probe` reads the screen and returns a `ReelProbe(inViewer, identity)` — a stable fingerprint of the reel on screen. For Instagram, identity is `"ig:<username>|<caption>"`; for YouTube it's `"yt:<handle>|<title>"`. Then:

```kotlin
private fun onActiveReel(pkg: String, platform: Platform, identity: String) {
    if (currentIdentity[pkg] == identity) return       // same reel — let timer run
    currentIdentity[pkg] = identity
    cancelDwell(pkg)
    if (seenIdentities[pkg]?.contains(identity) == true) return  // already counted
    val runnable = Runnable {
        if (currentIdentity[pkg] != identity) return@Runnable     // skipped past it
        val seen = seenIdentities.getOrPut(pkg) { LinkedHashSet() }
        if (seen.add(identity)) tryCountReel(platform, pkg, "DWELL")
    }
    dwellRunnables[pkg] = runnable
    mainHandler.postDelayed(runnable, DWELL_MS)   // DWELL_MS = 1500
}
```

> 💡 **Concept —** *Dwell* means the reel has to *stay* on screen for 1.5 seconds before it counts. Rapid skips don't register. A `seenIdentities` set (capped at 500) remembers what's already counted so scrolling back doesn't double-count.

The probes carry clever guards. YouTube's `probeYouTube` keeps the identity stable when a press-and-hold (2x speed) briefly hides the title, so it isn't mistaken for a new Short. And `inspectYouTubeShorts` explicitly bails on a *regular* watch page (`isRegularWatch`) so long-form videos never trigger the counter.

## When a reel is confirmed: `tryCountReel`

Every path ends here. After the debounce check, it increments and fans out:

```kotlin
reelCounts[pkg] = (reelCounts[pkg] ?: 0) + 1
…
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

In plain English: bump the in-memory count, then on a background thread write to the database via the repository, recompute the brain-health score, update the floating bubble, re-check whether to block, and fire a milestone notification (25/50/… reels) if a threshold was crossed.

`evaluateBlocking` then decides whether to slam the blocking scrim down:

```kotlin
if (!prefs.getBoolean("blocking_enabled", false)) return
val limit = limitsCache.values.maxOrNull() ?: 30
val total = reelCounts.values.sum()
if (total >= limit) {
    val mode = BlockingMode.fromPref(prefs.getString(BlockingMode.PREF_KEY, null))
    FloatingCounterService.instance?.showBlockingOverlay(platform, limit, mode)
}
```

It compares the *total* reels across all apps to the daily limit (falling back to `30` if none is set) and asks the overlay service to block in the chosen mode.

## Midnight rollover: `checkDayRollover`

Recall this runs on every event. It's how the day resets without any background scheduler:

```kotlin
private fun checkDayRollover() {
    val today = LocalDate.now().toString()
    if (today == currentDay) return
    val previousDay = currentDay
    currentDay = today
    reelCounts.clear()
    …
    seenIdentities.clear(); currentIdentity.clear()
    maxPagerIndex.clear(); lastPagerIndex.clear()

    serviceScope.launch {
        repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
        if (LocalDate.parse(previousDay).plusDays(1).toString() == today) {
            val log = repository.getLogSnapshot(previousDay)
            val minutes = ScreenTimeHelper.getYesterdayMinutesByPlatform(applicationContext).values.sum()
            notificationHelper.showDailySummary(…)
        }
        notificationHelper.resetDailyMilestones()
        try { UsageSyncManager(applicationContext, repository).syncIfEnabled() } catch (_: Exception) {}
    }
}
```

When the date string changes it: clears all in-memory counts and dedup state (so identical reels can count afresh tomorrow), evaluates streaks up to yesterday, shows the daily-summary notification *only if the gap is exactly one day* (so a phone that was off for a week doesn't fire a stale recap), re-arms milestone alerts, and kicks an optional cloud sync.

> 💡 **Concept —** "Lazy" here means the work happens the next time you touch a tracked app, not at the stroke of midnight. If your phone slept through midnight, the reset simply catches up on your next doomscroll — no battery-draining scheduled job required.

## Putting it together

When you open Instagram and swipe through Reels, the chain is: `onAccessibilityEvent` → `TYPE_WINDOW_CONTENT_CHANGED` → cooldown check → `probeInstagram` reads the pager and builds an identity → `evaluateReelSession` → `onActiveReel` arms a 1.5 s dwell → the reel survives → `tryCountReel` debounces, increments, writes to the DB, updates the bubble, and checks blocking. Four very different apps, one consistent counter. That's the heart of LoopOut.
