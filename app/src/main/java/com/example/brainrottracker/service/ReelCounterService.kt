package com.example.brainrottracker.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.sync.UsageSyncManager
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Reel/Shorts counter using VIEW HIERARCHY INSPECTION.
 *
 * Instead of guessing from event types (which are unreliable and noisy),
 * we inspect the actual view tree to:
 *   1. Find a full-screen vertical pager (the Reels/Shorts viewer)
 *   2. Read the currently visible item INDEX from the pager
 *   3. Only count when the index CHANGES (= user swiped to next reel)
 *
 * This approach:
 *   ✅ Never counts without an actual swipe (no false positives from content updates)
 *   ✅ Never counts on feed pages (feed RecyclerViews have many visible items, not 1)
 *   ✅ Handles YouTube Shorts, Instagram Reels, TikTok, Snapchat Spotlight
 *   ✅ Unified counter across all platforms
 *
 * The view hierarchy is only inspected when a CONTENT_CHANGE_TYPE_SUBTREE event
 * fires AND at least 1 second has passed since the last inspection (rate limiting).
 */
class ReelCounterService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private lateinit var repository: UsageRepository
    private lateinit var notificationHelper: NotificationHelper

    private val mainHandler = Handler(Looper.getMainLooper())

    // The floating HUD/blocking overlay, drawn directly from this always-alive accessibility service
    // (no foreground service — see OverlayController). Created in onServiceConnected, torn down in
    // onDestroy.
    private var overlay: OverlayController? = null

    // Heartbeat that runs while a tracked app is in the foreground. It keeps the HUD visible while a
    // reel feed is open, re-evaluates blocking (so an expired snooze re-blocks within ~1 s), and —
    // critically — tracks when the user leaves the reel page. The pill is tied to the reel page, not
    // to the app process: any poll where the foreground isn't one of our tracked apps means we're not
    // on a reel page, so the pill is hidden right away.
    //
    // The service is filtered to the tracked packages, so on Home / another app / the lock screen
    // `rootInActiveWindow` returns null (the window can't be introspected). We treat null the same as
    // an untracked app — "away" — and hide immediately. To avoid ending the session on a transient
    // null while still scrolling, the full teardown waits for a couple of consecutive away polls; a
    // real event in the meantime re-shows the pill via keepBubbleVisible().
    private val HEARTBEAT_MS = 1000L
    private val AWAY_POLLS_BEFORE_TEARDOWN = 2
    private var heartbeatActive = false
    private var lastForegroundTracked: String? = null
    private var consecutiveAwayPolls = 0
    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            val fg = rootInActiveWindow?.packageName?.toString()
            val fgPlatform = fg?.let { Platform.fromPackageName(it) }

            if (fg == null || fgPlatform == null) {
                // Not on a tracked reel app (untracked app, Home, or a null/locked window). The pill
                // belongs to the reel page only, so hide it immediately.
                overlay?.hide()
                consecutiveAwayPolls++
                if (consecutiveAwayPolls >= AWAY_POLLS_BEFORE_TEARDOWN) {
                    // Confirmed away: end the foreground session, re-arm the REMIND prompt, and stop
                    // polling. The pill stays hidden; it re-shows the next time a tracked reel page is
                    // foregrounded (an accessibility event restarts the heartbeat).
                    lastForegroundTracked?.let { endTrackedSession(it) }
                    overlay?.onAllTrackedAppsLeft()
                    lastForegroundTracked = null
                    heartbeatActive = false
                    return
                }
                mainHandler.postDelayed(this, HEARTBEAT_MS)
                return
            }

            // On a tracked app — reset the away streak.
            consecutiveAwayPolls = 0
            if (fg != lastForegroundTracked) {
                lastForegroundTracked?.let { endTrackedSession(it) }
                lastForegroundTracked = fg
            }
            if (hasReelPager[fg] == true || fgPlatform == Platform.TIKTOK) {
                overlay?.show()
            } else {
                overlay?.hide()
            }
            evaluateBlocking(fgPlatform, fg)
            mainHandler.postDelayed(this, HEARTBEAT_MS)
        }
    }

    // Per-package: the last known item index in the reel pager
    private val lastPagerIndex = mutableMapOf<String, Int>()

    // Per-package: the furthest pager index reached this session (high-water mark). We only count
    // a reel when the user advances PAST this — so scrolling back up to an already-watched reel and
    // forward again doesn't recount it. Reset when the reel viewer is left (a fresh feed = new reels).
    private val maxPagerIndex = mutableMapOf<String, Int>()

    // Per-package: whether we've confirmed a reel pager exists on the current screen
    private val hasReelPager = mutableMapOf<String, Boolean>()

    // Whether FLAG_INCLUDE_NOT_IMPORTANT_VIEWS is currently enabled (toggled per foreground app;
    // needed for Snapchat Spotlight, harmful to YouTube — see setIncludeNotImportantViews).
    private var includeNotImportantViewsOn = false

    // ── Identity-based counting (YouTube + Instagram) ──────────────────────────
    // Each reel has a stable identity. We count it once, only after it has been the active reel
    // for DWELL_MS (so quick skips don't count), and never again while its identity stays in the
    // session seen-set (so scrolling back to an already-watched reel doesn't recount it).
    private val DWELL_MS = 1500L
    private val MAX_SEEN_PER_APP = 500

    // Per-package: identities already counted this session (insertion-ordered, capped).
    private val seenIdentities = mutableMapOf<String, LinkedHashSet<String>>()
    // Per-package: the identity of the reel currently on screen.
    private val currentIdentity = mutableMapOf<String, String?>()
    // Per-package: the pending "count this reel after the dwell" callback.
    private val dwellRunnables = mutableMapOf<String, Runnable>()

    // Rate limit: don't inspect the view tree more than once per second
    private val lastInspectionMs = mutableMapOf<String, Long>()
    private val INSPECTION_COOLDOWN_MS = 800L

    // Debounce: minimum time between two counted reels on the same platform
    private val DEBOUNCE_MS = 1200L
    private val lastCountedMs = mutableMapOf<String, Long>()

    // In-memory reel counts for today
    private val reelCounts = mutableMapOf<String, Int>()

    // The date the in-memory state belongs to; reset at midnight rollover
    private var currentDay: String = LocalDate.now().toString()

    // Cache of daily reel limits for each platform name
    private val limitsCache = mutableMapOf<String, Int>()

    companion object {
        private const val TAG = "ReelCounter"
        private const val MAX_TREE_DEPTH = 12

        // AccessibilityEvent.CONTENT_CHANGE_TYPE_ENABLED (API 34+), inlined so the bit check
        // compiles/works on all API levels. Snapchat sets this when a new Spotlight video activates.
        private const val CONTENT_CHANGE_TYPE_ENABLED = 0x00001000 // 4096

        var isRunning = false
            private set
        var instance: ReelCounterService? = null
            private set
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        isRunning = true
        instance = this
        Log.d(TAG, "✅ onServiceConnected — service is LIVE")

        val db = AppDatabase.getInstance(applicationContext)
        repository = UsageRepository(db)
        notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.createChannels()

        // Draw the HUD/blocking overlay directly from this (always-alive) accessibility service —
        // no foreground service, so there are no background FGS starts for aggressive OEMs (MIUI/
        // HyperOS) to block or penalize. Visibility is driven by the heartbeat and reel detection.
        overlay = OverlayController(this)

        serviceScope.launch {
            val todayLog = repository.getTodayLogSnapshot()
            todayLog?.let { log ->
                Platform.entries.forEach { p ->
                    reelCounts[p.packageName] = log.getReelsForPlatform(p)
                }
            }
            // Catch up on any days that ended while the service was dead
            repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
        }

        serviceScope.launch {
            repository.getLimits().collect { limitsList ->
                limitsList.forEach { limit ->
                    limitsCache[limit.platform] = limit.dailyReelLimit
                }
            }
        }

    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        heartbeatActive = false
        mainHandler.removeCallbacks(heartbeatRunnable)
        serviceScope.cancel()
        overlay?.destroy()
        overlay = null
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event dispatcher
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        checkDayRollover()
        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackageName(pkg) ?: return

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "WINDOW_STATE: pkg=$pkg class=${event.className}")
                // Snapchat's Spotlight feed only exposes its container/feed nodes when
                // flagIncludeNotImportantViews is on, but that flag breaks YouTube Shorts detection
                // (it changes the window rootInActiveWindow returns). So enable it ONLY while
                // Snapchat is foreground and strip it for every other tracked app.
                setIncludeNotImportantViews(pkg == "com.snapchat.android")
                lastInspectionMs.remove(pkg) // Clear cooldown so the new screen is inspected immediately!
                // Do NOT clear state variables here. This prevents resetting signature detection
                // on minor state transitions (like opening comments or showing overlays).
                // The layout inspection triggered by subsequent content changes will handle visibility updates.

                // A tracked app reached (or switched within) the foreground: end the previous
                // app's session, re-check blocking, and start polling for when the user leaves.
                if (pkg != lastForegroundTracked) {
                    lastForegroundTracked?.let { endTrackedSession(it) }
                    lastForegroundTracked = pkg
                }
                // Don't block on foreground-entry alone: `hasReelPager[pkg]` can be stale-true here
                // (e.g. backgrounded straight from Shorts, reopened to the home feed), which would
                // flash the scrim on a non-feed screen. The heartbeat below re-evaluates within ~1s
                // with an up-to-date flag, and content inspection re-blocks as soon as a real reel
                // feed is detected.
                startHeartbeat()
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleViewScrolled(event, platform, pkg)
                if (hasReelPager[pkg] == true || platform == Platform.TIKTOK) {
                    keepBubbleVisible()
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                // Snapchat Spotlight has no scroll events, no readable pager index, and an identical
                // view tree per video (the feed is a SurfaceView). The one per-swipe signal it emits
                // is a CONTENT_CHANGE_TYPE_ENABLED change when the next video's content activates.
                // Arm a dwell on that bit while we're confirmed inside Spotlight: a video skipped
                // before DWELL_MS is never counted, matching YouTube/Instagram.
                if (platform == Platform.SNAPCHAT &&
                    (event.contentChangeTypes and CONTENT_CHANGE_TYPE_ENABLED) != 0 &&
                    hasReelPager[pkg] == true
                ) {
                    armSnapchatDwell(platform, pkg)
                }

                val isSubtree = (event.contentChangeTypes and
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
                if (isSubtree) {
                    handleContentChanged(event, platform, pkg)
                }
            }

            else -> {
                if (hasReelPager[pkg] == true || platform == Platform.TIKTOK) {
                    keepBubbleVisible()
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TYPE_VIEW_SCROLLED — works for some apps (Instagram feed, TikTok)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleViewScrolled(event: AccessibilityEvent, platform: Platform, pkg: String) {
        // TikTok: entire app is short videos. Its scroll events don't expose a reliable feed
        // position, so we keep the simple forward-scroll count here (no high-water dedup).
        if (platform == Platform.TIKTOK) {
            if (isForwardScroll(event)) {
                tryCountReel(platform, pkg, "VIEW_SCROLLED/TikTok")
            }
            return
        }

        // YouTube and Instagram are counted by the identity/dwell engine (handleContentChanged),
        // so scroll events must NOT also count for them — that would double-count.
        if (pkg == "com.google.android.youtube" || pkg == "com.instagram.android") return

        // Only process scroll events if we are actively viewing a Reels/Shorts pager (Snapchat)
        if (hasReelPager[pkg] != true) return

        val fromIdx = event.fromIndex
        val toIdx = event.toIndex
        val itemCount = event.itemCount

        if (fromIdx >= 0 && toIdx >= 0) {
            val visibleItems = toIdx - fromIdx

            if (visibleItems <= 1 && itemCount > 2) {
                // Single-item pager — this is a reel view
                countScrollReel(event, platform, pkg, "VIEW_SCROLLED/pager")
            }
        }
    }

    /**
     * Count from a scroll event with the same no-recount guarantee as the index path: prefer the
     * absolute feed position ([AccessibilityEvent.getFromIndex]) and a high-water mark; fall back to
     * plain forward-scroll detection only when no position index is exposed.
     */
    private fun countScrollReel(event: AccessibilityEvent, platform: Platform, pkg: String, source: String) {
        val fromIdx = event.fromIndex
        if (fromIdx >= 0) {
            countByHighWater(fromIdx, platform, pkg, source)
        } else if (isForwardScroll(event)) {
            tryCountReel(platform, pkg, source)
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TYPE_WINDOW_CONTENT_CHANGED — inspect view tree for pager item change
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleContentChanged(event: AccessibilityEvent, platform: Platform, pkg: String) {
        // TikTok: skip tree inspection, use simpler approach
        if (platform == Platform.TIKTOK) return

        // Rate limit tree inspections
        val now = System.currentTimeMillis()
        val lastInspection = lastInspectionMs[pkg] ?: 0L
        val cooldownPassed = (now - lastInspection >= INSPECTION_COOLDOWN_MS)
        
        Log.d(TAG, "handleContentChanged: pkg=$pkg eventType=${event.eventType} cooldownPassed=$cooldownPassed")
        if (!cooldownPassed) return

        // Inspect the view hierarchy
        var root = try { rootInActiveWindow } catch (_: Exception) { null }
        if (root != null && root.packageName?.toString() != pkg) {
            root.recycle()
            root = null
        }

        if (root == null) {
            val source = try { event.source } catch (_: Exception) { null }
            if (source != null) {
                var current: AccessibilityNodeInfo = source
                while (true) {
                    val p = current.parent
                    if (p != null) {
                        current = p
                    } else {
                        break
                    }
                }
                root = current
                Log.d(TAG, "  Found root via event.source parent traversal")
            }
        }

        Log.d(TAG, "  root retrieved? ${root != null} package=${root?.packageName} class=${root?.className} childCount=${root?.childCount}")
        if (root == null) {
            return
        }
        
        // Update inspection cooldown timestamp now that we have a valid root
        lastInspectionMs[pkg] = now

        try {
            when (pkg) {
                "com.google.android.youtube" -> evaluateReelSession(pkg, platform, probeYouTube(root))
                "com.instagram.android" -> evaluateReelSession(pkg, platform, probeInstagram(root))
                "com.snapchat.android" -> handleSnapchatSpotlight(root, pkg)
                "com.facebook.katana" -> handleFacebookReels(root, pkg)
                else -> handleGenericReels(root, platform, pkg)
            }
        } finally {
            root.recycle()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Identity-based counting engine (YouTube + Instagram)
    //
    //   probe(root)  → ReelProbe(inViewer, identity)   [platform-specific, below]
    //        │
    //        ▼
    //   evaluateReelSession → tracks the active reel and shows/hides the HUD
    //        │ (active identity changed)
    //        ▼
    //   onActiveReel → starts a DWELL_MS timer; if the same reel is still active when it
    //                  fires and it isn't already in the seen-set, count it once.
    // ─────────────────────────────────────────────────────────────────────────

    /** Result of probing the view tree for the active short-form reel. */
    data class ReelProbe(val inViewer: Boolean, val identity: String?)

    private fun evaluateReelSession(pkg: String, platform: Platform, probe: ReelProbe) {
        if (probe.inViewer) {
            hasReelPager[pkg] = true
            keepBubbleVisible()
            if (probe.identity != null) onActiveReel(pkg, platform, probe.identity)
        } else {
            if (hasReelPager[pkg] == true) Log.d(TAG, "VIEWER LOST: pkg=$pkg")
            hasReelPager[pkg] = false
            // Left the viewer (maybe briefly — opened comments, a profile…). Stop the pending
            // dwell and forget the active reel, but KEEP the seen-set so returning to the same
            // feed doesn't recount; the seen-set is only cleared on app-leave / midnight.
            cancelDwell(pkg)
            currentIdentity[pkg] = null
            hideBubble()
        }
    }

    /** A reel is on screen. If it's newly active, (re)arm the dwell timer that will count it. */
    private fun onActiveReel(pkg: String, platform: Platform, identity: String) {
        if (currentIdentity[pkg] == identity) return // same reel still showing — let the timer run
        currentIdentity[pkg] = identity
        cancelDwell(pkg)

        if (seenIdentities[pkg]?.contains(identity) == true) {
            Log.d(TAG, "REVISIT: pkg=$pkg id=$identity — already counted")
            return
        }

        val runnable = Runnable {
            dwellRunnables.remove(pkg)
            // Only count if this reel is *still* the active one after the dwell (not skipped past).
            if (currentIdentity[pkg] != identity) return@Runnable
            val seen = seenIdentities.getOrPut(pkg) { LinkedHashSet() }
            if (seen.add(identity)) {
                if (seen.size > MAX_SEEN_PER_APP) seen.iterator().let { it.next(); it.remove() }
                tryCountReel(platform, pkg, "DWELL")
            }
        }
        dwellRunnables[pkg] = runnable
        mainHandler.postDelayed(runnable, DWELL_MS)
    }

    private fun cancelDwell(pkg: String) {
        dwellRunnables.remove(pkg)?.let { mainHandler.removeCallbacks(it) }
    }

    // ── YouTube probe ──────────────────────────────────────────────────────────

    /** YouTube Shorts: gate via inspectYouTubeShorts; identity is the creator + title signature. */
    private fun probeYouTube(root: AccessibilityNodeInfo): ReelProbe {
        val info = inspectYouTubeShorts(root)
        return ReelProbe(info.isActive, if (info.isActive) info.signature else null)
    }

    // ── Instagram probe ─────────────────────────────────────────────────────────

    /**
     * Instagram Reels: gate on a *visible* clips_viewer_view_pager (absent on the home feed and on
     * the stories viewer, which uses reel_viewer_* ids). Identity is the active reel's author handle
     * plus caption text; falls back to the pager child's row index when no text is readable.
     */
    private fun probeInstagram(root: AccessibilityNodeInfo): ReelProbe {
        var inViewer = false
        var username: String? = null
        var caption: String? = null
        var rowIndex: Int? = null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser) {
                    when (node.viewIdResourceName) {
                        "com.instagram.android:id/clips_viewer_view_pager" -> {
                            inViewer = true
                            // The active page is the visible child; read its row index if exposed.
                            for (i in 0 until node.childCount) {
                                val child = node.getChild(i) ?: continue
                                toRecycle.add(child)
                                if (child.isVisibleToUser && rowIndex == null) {
                                    rowIndex = child.collectionItemInfo?.rowIndex
                                }
                            }
                        }
                        "com.instagram.android:id/clips_author_username" ->
                            if (username == null) username = node.text?.toString()
                        "com.instagram.android:id/clips_caption_component" ->
                            if (caption == null) caption = node.text?.toString()
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        toRecycle.add(child)
                        queue.add(child)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "probeInstagram error: ${e.message}")
        } finally {
            toRecycle.forEach { try { it.recycle() } catch (_: Exception) {} }
        }

        if (!inViewer) return ReelProbe(false, null)
        val identity = when {
            username != null || caption != null -> "ig:${username ?: ""}|${caption ?: ""}"
            rowIndex != null -> "ig:idx:$rowIndex"
            else -> null
        }
        return ReelProbe(true, identity)
    }

    private fun handleGenericReels(
        root: AccessibilityNodeInfo,
        platform: Platform,
        pkg: String,
        requiredPagerId: String? = null
    ) {
        val pagerInfo = findReelPager(root, requiredPagerId)

        if (pagerInfo != null) {
            val (currentIndex, totalItems) = pagerInfo
            hasReelPager[pkg] = true
            keepBubbleVisible()

            // Some pagers (Instagram ViewPager2) don't expose a readable index; counting then
            // relies on scroll events instead, so only run index-based counting for real indices.
            if (currentIndex >= 0) countByHighWater(currentIndex, platform, pkg, "PAGER_INDEX_CHANGE")
        } else {
            if (hasReelPager[pkg] == true) {
                Log.d(TAG, "PAGER LOST: pkg=$pkg (navigated away from reels?)")
                // Left the viewer (not just an app switch) — reset dedup so the next session counts.
                maxPagerIndex.remove(pkg)
                lastPagerIndex.remove(pkg)
            }
            hasReelPager[pkg] = false
            hideBubble()
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Snapchat Spotlight detection
    //
    // Snapchat's Spotlight feed is a SurfaceView: it exposes no scrollable list,
    // no per-video text/handle/index — the view tree is identical between videos.
    // The only stable signal is the visible `spotlight_container` view, which is
    // present only on the Spotlight tab (Stories/Discover use opera_viewer without
    // it). We use it to gate "in viewer" (bubble + blocking); counting is driven
    // by forward scroll events, the only per-swipe signal Snapchat surfaces.
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Snapchat Spotlight gate. The feed exposes no scroll/index/identity, but the Spotlight tab is
     * uniquely marked by a visible `spotlight_container` (Stories/Discover reuse `opera_viewer`
     * without it). When present we treat the user as "on a reel feed" — this drives the bubble and
     * gates blocking, and arms the CONTENT_CHANGE_TYPE_ENABLED counter in the event dispatcher.
     */
    private fun handleSnapchatSpotlight(root: AccessibilityNodeInfo, pkg: String) {
        val inSpotlight = isInSnapchatSpotlight(root)
        if (inSpotlight) {
            hasReelPager[pkg] = true
            keepBubbleVisible()
        } else {
            if (hasReelPager[pkg] == true) Log.d(TAG, "SPOTLIGHT LOST: pkg=$pkg")
            hasReelPager[pkg] = false
            cancelDwell(pkg) // drop a pending count if the user left before the dwell fired
            hideBubble()
        }
    }

    /**
     * Snapchat exposes no per-video identity, so we dwell-gate by time alone: each new ENABLED
     * change arms a delayed count and cancels the previous one. A video swiped past before DWELL_MS
     * never counts, so only videos actually watched are counted — matching YouTube/Instagram.
     */
    private fun armSnapchatDwell(platform: Platform, pkg: String) {
        cancelDwell(pkg)
        val runnable = Runnable {
            dwellRunnables.remove(pkg)
            // Only count if still on the Spotlight feed (user didn't leave during the dwell).
            if (hasReelPager[pkg] == true) tryCountReel(platform, pkg, "SNAP_DWELL")
        }
        dwellRunnables[pkg] = runnable
        mainHandler.postDelayed(runnable, DWELL_MS)
    }

    /** True when the visible Spotlight feed container is in the tree. */
    private fun isInSnapchatSpotlight(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>() // node, depth
        queue.add(root to 0)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        var found = false
        try {
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()
                if (depth > MAX_TREE_DEPTH) continue
                if (node.viewIdResourceName == "com.snapchat.android:id/spotlight_container" &&
                    node.isVisibleToUser
                ) {
                    found = true
                    break
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        toRecycle.add(child)
                        queue.add(child to depth + 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "isInSnapchatSpotlight error: ${e.message}")
        } finally {
            toRecycle.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
        return found
    }

    /**
     * Toggle FLAG_INCLUDE_NOT_IMPORTANT_VIEWS at runtime. Snapchat's Spotlight container is a
     * not-important view (invisible to the service without this flag), but leaving the flag on
     * permanently changes the window `rootInActiveWindow` returns for YouTube and breaks Shorts
     * detection — so we scope it to Snapchat's foreground session only.
     */
    private fun setIncludeNotImportantViews(enable: Boolean) {
        if (enable == includeNotImportantViewsOn) return
        val info = serviceInfo ?: return
        info.flags = if (enable) {
            info.flags or AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS
        } else {
            info.flags and AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS.inv()
        }
        try {
            serviceInfo = info
            includeNotImportantViewsOn = enable
            Log.d(TAG, "FLAG includeNotImportantViews=$enable")
        } catch (e: Exception) {
            Log.w(TAG, "setServiceInfo failed: ${e.message}")
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // YouTube Shorts Inspection Helper
    // ─────────────────────────────────────────────────────────────────────────

    data class YouTubeShortsInfo(
        val isActive: Boolean,
        val signature: String?
    )

    private fun inspectYouTubeShorts(root: AccessibilityNodeInfo): YouTubeShortsInfo {
        Log.d(TAG, "inspectYouTubeShorts: starting BFS on root class=${root.className} childCount=${root.childCount}")
        var isActive = false
        var isRegularWatch = false
        var hasRecycler = false
        var hasHandle = false
        var handle: String? = null
        var commentDesc: String? = null
        var likeDesc: String? = null
        var titleText: String? = null

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()

        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                val viewId = node.viewIdResourceName ?: ""

                // Enforce visibility check to avoid matching hidden tab hierarchies
                val isVisible = node.isVisibleToUser

                if (isVisible) {
                    if (viewId == "com.google.android.youtube:id/reel_recycler" ||
                        viewId == "com.google.android.youtube:id/reel_watch_fragment_root" ||
                        viewId == "com.google.android.youtube:id/reel_watch_refresher" ||
                        viewId == "com.google.android.youtube:id/reel_player_page_container" ||
                        viewId == "com.google.android.youtube:id/reel_prev_reel_button" ||
                        viewId == "com.google.android.youtube:id/reel_play_pause_button" ||
                        viewId == "com.google.android.youtube:id/reel_next_reel_button"
                    ) {
                        isActive = true
                    }

                    // Regular (long-form) watch page indicators. A normal video page also has a
                    // RecyclerView, a creator @handle, a "like this video" button and comments, so
                    // the heuristic fallback below would otherwise misfire on every regular video
                    // (verified on-device: no reel_* activation IDs are present, only these).
                    if (viewId == "com.google.android.youtube:id/watch_player" ||
                        viewId == "com.google.android.youtube:id/watch_panel" ||
                        viewId == "com.google.android.youtube:id/watch_list" ||
                        viewId == "com.google.android.youtube:id/next_gen_watch_container_layout" ||
                        viewId == "com.google.android.youtube:id/player_collapse_button" ||
                        viewId == "com.google.android.youtube:id/player_control_play_pause_replay_button"
                    ) {
                        isRegularWatch = true
                    }

                    val clsName = node.className?.toString() ?: ""
                    if (clsName.contains("RecyclerView")) {
                        hasRecycler = true
                    }

                    val text = node.text?.toString() ?: ""
                    val desc = node.contentDescription?.toString() ?: ""

                    if (handle == null) {
                        if (text.startsWith("@") && text.length in 3..40) {
                            handle = text
                            hasHandle = true
                        } else if (desc.startsWith("@") && desc.length in 3..40) {
                            handle = desc
                            hasHandle = true
                        }
                    }

                    if (commentDesc == null) {
                        if (desc.contains("comment", ignoreCase = true)) {
                            commentDesc = desc
                        } else if (text.contains("comment", ignoreCase = true)) {
                            commentDesc = text
                        }
                    }

                    if (likeDesc == null) {
                        if (desc.contains("like this video", ignoreCase = true) || desc.contains("like this short", ignoreCase = true)) {
                            likeDesc = desc
                        }
                    }

                    if (titleText == null && text.isNotEmpty() && !text.startsWith("@") &&
                        !text.contains("comment", ignoreCase = true) && !text.contains("like", ignoreCase = true) &&
                        text.length > 5 && text.length < 150
                    ) {
                        val lowerText = text.lowercase()
                        if (lowerText != "home" && lowerText != "shorts" && lowerText != "subscriptions" && lowerText != "you" && lowerText != "library") {
                            val rect = android.graphics.Rect()
                            node.getBoundsInScreen(rect)
                            if (rect.top > 1200) {
                                titleText = text
                            }
                        }
                    }
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        toRecycle.add(child)
                        queue.add(child)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting YouTube Shorts: ${e.message}")
        } finally {
            toRecycle.forEach {
                try { it.recycle() } catch (_: Exception) {}
            }
        }

        // A regular watch page is never a Short — bail out even if a reel_* ID happened to be
        // visible (e.g. a background mini-player) so the counter never shows on long-form videos.
        if (isRegularWatch) {
            return YouTubeShortsInfo(isActive = false, signature = null)
        }

        // Fallback: if no view ID matched, we identify Shorts by presence of a recycler,
        // creator handle (@), and either a like button description ("like this...") or comment button.
        // This avoids matches on the Home feed where like buttons and comment lists are not directly visible on cards.
        if (!isActive && hasRecycler && hasHandle && (likeDesc != null || commentDesc != null)) {
            isActive = true
        }

        // Identity must be STABLE while watching one short, so it's built only from the creator
        // handle and the title/caption — never from like/comment counts, which tick up live and
        // would make the same short look like a new one. (commentDesc/likeDesc are used above only
        // to gate detection, not to identify the short.)
        val signature = if (handle != null || titleText != null) {
            "yt:$handle|$titleText"
        } else {
            null
        }

        return YouTubeShortsInfo(isActive, signature)
    }

    // ─────────────────────────────────────────────────────────────────────────
    // View hierarchy inspection — find the reel pager and its current item
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Searches the view tree for a full-screen vertical pager (Reels/Shorts).
     *
     * Returns: Pair(currentItemIndex, totalItems) or null if no pager found.
     *
     * A "reel pager" is identified as a SCROLLABLE view that:
     *   - Has className containing "RecyclerView" or "ViewPager"
     *   - Has CollectionInfo with columnCount == 1 (single column = vertical scroll)
     *   - Has at most 2 visible children (full-screen items, not a multi-item feed)
     *
     * The current item index comes from CollectionItemInfo.rowIndex of the
     * first visible child, or from the fromIndex of the scroll event.
     */
    /**
     * Facebook Reels: the immersive reel viewer is an old `androidx.viewpager.widget.ViewPager`
     * that exposes no collectionInfo, and Facebook strips its resource-id names, so neither the
     * generic pager detector nor an Instagram-style id probe works. The viewer is, however,
     * structurally identical to the home feed (both vertical RecyclerViews inside the tab pager) —
     * the only reliable discriminator is reel-viewer chrome, exposed via content-descriptions that
     * never appear on the home feed (e.g. "Create reel", "Reels tab details").
     *
     * So we gate purely on those signals: presence → we're on the reel feed, so flag the pager and
     * show the HUD. Counting itself happens off the pager's TYPE_VIEW_SCROLLED events through the
     * shared scroll path ([handleViewScrolled] → [countByHighWater]), exactly like Snapchat — each
     * forward swap increments `fromIndex`, which the high-water mark counts once.
     */
    private fun handleFacebookReels(root: AccessibilityNodeInfo, pkg: String) {
        val inReelViewer = isInFacebookReels(root)
        if (inReelViewer) {
            hasReelPager[pkg] = true
            keepBubbleVisible()
        } else {
            if (hasReelPager[pkg] == true) {
                Log.d(TAG, "FB reel viewer LOST: pkg=$pkg")
                // Left the viewer — reset dedup so the next reel session counts from scratch.
                maxPagerIndex.remove(pkg)
                lastPagerIndex.remove(pkg)
            }
            hasReelPager[pkg] = false
            hideBubble()
        }
    }

    /**
     * True when the Facebook immersive reel viewer is on screen. Detected by content-descriptions
     * unique to the reel viewer's chrome — these are absent on the home feed, whose only reel-ish
     * descriptions are the always-present "Reels, tab 2 of 6" bottom-nav button and a "Reel" tray
     * label (both deliberately excluded here).
     */
    private fun isInFacebookReels(root: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()
        var found = false
        try {
            while (queue.isNotEmpty()) {
                val node = queue.removeFirst()
                if (node.isVisibleToUser && isFacebookReelChrome(node.contentDescription?.toString())) {
                    found = true
                    break
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) { toRecycle.add(child); queue.add(child) }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "isInFacebookReels error: ${e.message}")
        } finally {
            toRecycle.forEach { try { it.recycle() } catch (_: Exception) {} }
        }
        return found
    }

    private fun isFacebookReelChrome(desc: String?): Boolean {
        desc ?: return false
        return desc == "Create reel" ||
                desc == "Reels tab details" ||
                desc == "Navigate to your Reels profile" ||
                desc == "Tap to show video controls"
    }

    private fun findReelPager(root: AccessibilityNodeInfo, requiredPagerId: String? = null): Pair<Int, Int>? {
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>() // node, depth
        queue.add(root to 0)

        var result: Pair<Int, Int>? = null
        val toRecycle = mutableListOf<AccessibilityNodeInfo>()

        try {
            while (queue.isNotEmpty()) {
                val (node, depth) = queue.removeFirst()
                if (depth > MAX_TREE_DEPTH) continue

                val cls = node.className?.toString() ?: ""
                val clsLower = cls.lowercase()

                // Check if this node looks like a pager/recycler
                val isPagerClass = clsLower.contains("recyclerview") ||
                        clsLower.contains("viewpager") ||
                        clsLower.contains("pager")

                // When a specific pager id is required (Instagram), only that exact view qualifies —
                // its absence means we're not in the Reels viewer (e.g. the home feed).
                val idQualifies = requiredPagerId == null || node.viewIdResourceName == requiredPagerId

                if (idQualifies && node.isScrollable && isPagerClass) {
                    val collection = node.collectionInfo

                    if (collection != null && collection.columnCount <= 1) {
                        // Single-column scrollable list — candidate for reel pager
                        // Count visible children and get current item index
                        var visibleChildCount = 0
                        var currentItemIndex: Int? = null
                        var totalItems = collection.rowCount

                        for (i in 0 until node.childCount) {
                            val child = node.getChild(i) ?: continue
                            toRecycle.add(child)

                            if (child.isVisibleToUser) {
                                visibleChildCount++
                                if (currentItemIndex == null) {
                                    // Get the row index from CollectionItemInfo
                                    val itemInfo = child.collectionItemInfo
                                    if (itemInfo != null) {
                                        currentItemIndex = itemInfo.rowIndex
                                    }
                                }
                            }
                        }

                        // With a required id we know this is the Reels pager once it's actually on
                        // screen (Instagram keeps it attached but hidden behind the feed tab, so the
                        // visibility check is essential). The index may be unreadable on ViewPager2,
                        // in which case scroll events drive counting. Otherwise a reel pager has at
                        // most 2 visible children (current + preload).
                        val accept = if (requiredPagerId != null) node.isVisibleToUser && visibleChildCount >= 1
                                     else visibleChildCount in 1..2 && currentItemIndex != null
                        if (accept) {
                            Log.d(TAG, "  Found pager: class=$cls visible=$visibleChildCount index=$currentItemIndex total=$totalItems")
                            result = Pair(currentItemIndex ?: -1, if (totalItems > 0) totalItems else -1)
                            break // Found it, stop searching
                        }
                    }
                }

                // Continue BFS to children
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        toRecycle.add(child)
                        queue.add(child to depth + 1)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error inspecting view tree: ${e.message}")
        } finally {
            // Recycle all non-root nodes
            toRecycle.forEach {
                try { it.recycle() } catch (_: Exception) {}
            }
        }

        return result
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Forward scroll detection (for TYPE_VIEW_SCROLLED events)
    // ─────────────────────────────────────────────────────────────────────────

    private val lastFromIndex = mutableMapOf<String, Int>()
    private val lastScrollY = mutableMapOf<String, Int>()

    private fun isForwardScroll(event: AccessibilityEvent): Boolean {
        val key = "${event.packageName}_${event.className}"
        val prevFrom = lastFromIndex[key]
        val prevY = lastScrollY[key]
        val curFrom = event.fromIndex
        val curY = event.scrollY

        if (curFrom >= 0) lastFromIndex[key] = curFrom
        if (curY >= 0) lastScrollY[key] = curY

        return when {
            prevFrom != null && curFrom >= 0 -> curFrom > prevFrom
            prevY != null && curY >= 0 -> curY > prevY
            else -> true // First event, allow
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Counting & bubble management
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Count a reel only when [index] advances past the furthest position reached this session.
     * The first index seen is the baseline (not counted, matching the original behaviour); revisiting
     * earlier reels never recounts, and a genuinely new reel (index beyond the high-water mark) does.
     */
    private fun countByHighWater(index: Int, platform: Platform, pkg: String, source: String) {
        val maxIndex = maxPagerIndex[pkg]
        if (maxIndex == null) {
            maxPagerIndex[pkg] = index
            Log.d(TAG, "$source: baseline index=$index pkg=$pkg")
        } else if (index > maxIndex) {
            maxPagerIndex[pkg] = index
            tryCountReel(platform, pkg, source)
        } else {
            Log.d(TAG, "$source: revisit index=$index (max=$maxIndex) — not counting")
        }
    }

    private fun tryCountReel(platform: Platform, pkg: String, source: String) {
        val now = System.currentTimeMillis()
        if (now - (lastCountedMs[pkg] ?: 0L) < DEBOUNCE_MS) {
            Log.d(TAG, "  ⏳ debounced ($source)")
            return
        }
        lastCountedMs[pkg] = now

        val newCount = (reelCounts[pkg] ?: 0) + 1
        reelCounts[pkg] = newCount
        val total = reelCounts.values.sum()
        Log.d(TAG, "  ✅ COUNTED via $source! ${platform.displayName}=$newCount total=$total")

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
    }

    /**
     * Shows the blocking scrim if blocking is on and the user's TOTAL reels across all tracked
     * apps (the number on the HUD) has reached the daily limit. The limit is a single global value
     * — the Limits screen writes the same slider value to every platform — so we treat the max of
     * the cached limits as that global cap. Called on every counted reel AND every time a tracked
     * app reaches the foreground, so the block re-triggers when any reel app is reopened.
     * Suppression (snooze, per-session dismissal) is mode-dependent and handled inside
     * [OverlayController].
     */
    private fun evaluateBlocking(platform: Platform, pkg: String) {
        val prefs = getSharedPreferences(OverlayController.PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean("blocking_enabled", false)) return

        // Block ONLY while the user is actually on a reel/shorts feed — never the rest of the app
        // (YouTube long-form/home/search, Instagram DMs, etc.). Same signal the HUD pill uses.
        // TikTok is entirely short-form, so it always qualifies. When not on a reel feed, dismiss
        // any scrim that's showing — this clears it when the user backs out of Shorts to the feed.
        val onReelFeed = hasReelPager[pkg] == true || platform == Platform.TIKTOK
        if (!onReelFeed) {
            overlay?.dismissBlockingOverlay()
            return
        }

        // Fall back to the entity default so blocking works before any limit was saved
        val limit = limitsCache.values.maxOrNull() ?: 30
        val total = reelCounts.values.sum()
        if (total >= limit) {
            val mode = BlockingMode.fromPref(prefs.getString(BlockingMode.PREF_KEY, null))
            overlay?.showBlockingOverlay(platform, limit, mode)
        }
    }

    /**
     * Detects midnight rollover. In-memory counts are reset, the finished day's streak is
     * evaluated, a recap notification is shown, and milestone alerts are re-armed. Runs
     * lazily on the next accessibility event, so a phone that was off at midnight catches
     * up on the next doomscroll.
     */
    private fun checkDayRollover() {
        val today = LocalDate.now().toString()
        if (today == currentDay) return
        val previousDay = currentDay
        currentDay = today
        reelCounts.clear()
        lastCountedMs.clear()
        // New day → forget what was watched so identical reels can count afresh.
        seenIdentities.clear()
        currentIdentity.clear()
        dwellRunnables.values.forEach { mainHandler.removeCallbacks(it) }
        dwellRunnables.clear()
        maxPagerIndex.clear()
        lastPagerIndex.clear()

        serviceScope.launch {
            repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
            // Recap only makes sense right after the day ended, not days later
            if (LocalDate.parse(previousDay).plusDays(1).toString() == today) {
                val log = repository.getLogSnapshot(previousDay)
                val minutes = ScreenTimeHelper.getYesterdayMinutesByPlatform(applicationContext)
                    .values.sum()
                notificationHelper.showDailySummary(
                    totalReels = log?.getTotalReels() ?: 0,
                    totalMinutes = minutes,
                    brainHealth = log?.brainHealthScore ?: 100
                )
            }
            notificationHelper.resetDailyMilestones()
            // Push the finished day to the cloud (no-op unless signed in with backup on)
            try {
                UsageSyncManager(applicationContext, repository).syncIfEnabled()
            } catch (_: Exception) {
            }
        }
    }

    private fun showBubbleAndResetTimer(log: DailyLog?, limits: List<UserLimits>, health: Int) {
        val total = log?.getTotalReels() ?: 0
        // Per-platform breakdown for the tap-to-open popup — only platforms used today.
        val breakdown = Platform.entries.mapNotNull { p ->
            val c = log?.getReelsForPlatform(p) ?: 0
            if (c > 0) {
                val limit = limits.find { it.platform == p.name }?.dailyReelLimit
                    ?: limitsCache[p.name] ?: 30
                OverlayController.HudPlatform(p, c, limit)
            } else null
        }
        // Same reel-ratio the dashboard uses to choose the brain variation.
        val reelLimit = limits.firstOrNull()?.dailyReelLimit ?: 50
        val reelRatio = if (reelLimit > 0) total.toFloat() / reelLimit else 0f
        overlay?.updateHud(total, health, breakdown, reelRatio)
        keepBubbleVisible()
    }

    /** Show the pill and keep it visible (via the heartbeat) for as long as a reel feed is open. */
    private fun keepBubbleVisible() {
        overlay?.show()
        startHeartbeat()
    }

    /**
     * The user left [pkg] (switched away or to another tracked app). End its FloatingCounter
     * session and drop all per-session dedup state so that returning to a *fresh* feed (different
     * reels) counts again, while scrolling back and forth within one session never recounts.
     */
    private fun endTrackedSession(pkg: String) {
        // Snapchat (high-water) state
        maxPagerIndex.remove(pkg)
        lastPagerIndex.remove(pkg)
        // YouTube / Instagram (identity engine) state
        cancelDwell(pkg)
        currentIdentity.remove(pkg)
        seenIdentities.remove(pkg)
    }

    /** Start the foreground-polling heartbeat without forcing the pill to show. */
    private fun startHeartbeat() {
        if (!heartbeatActive) {
            heartbeatActive = true
            consecutiveAwayPolls = 0
            mainHandler.postDelayed(heartbeatRunnable, HEARTBEAT_MS)
        }
    }

    private fun hideBubble() {
        // The heartbeat keeps running: it manages pill visibility and foreground-session
        // tracking itself, and stops when the user leaves all tracked apps.
        overlay?.hide()
    }
}
