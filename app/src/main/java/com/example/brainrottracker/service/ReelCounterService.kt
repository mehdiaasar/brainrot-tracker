package com.example.brainrottracker.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.notification.NotificationHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
    private val hideRunnable = Runnable { FloatingCounterService.instance?.hide() }
    private val AUTO_HIDE_DELAY_MS = 4000L

    // Per-package: the last known item index in the reel pager
    private val lastPagerIndex = mutableMapOf<String, Int>()

    // Per-package: whether we've confirmed a reel pager exists on the current screen
    private val hasReelPager = mutableMapOf<String, Boolean>()

    // Per-package: the last known signature for signature-based tracking (YouTube)
    private val lastSeenSignature = mutableMapOf<String, String>()

    // List of recently counted signatures to prevent double counting on YouTube Shorts
    private val recentYouTubeSignatures = mutableListOf<String>()
    private val MAX_SIGNATURES_HISTORY = 15

    // Rate limit: don't inspect the view tree more than once per second
    private val lastInspectionMs = mutableMapOf<String, Long>()
    private val INSPECTION_COOLDOWN_MS = 800L

    // Debounce: minimum time between two counted reels on the same platform
    private val DEBOUNCE_MS = 1200L
    private val lastCountedMs = mutableMapOf<String, Long>()

    // In-memory reel counts for today
    private val reelCounts = mutableMapOf<String, Int>()

    // Cache of daily reel limits for each platform name
    private val limitsCache = mutableMapOf<String, Int>()

    companion object {
        private const val TAG = "ReelCounter"
        private const val MAX_TREE_DEPTH = 12

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

        serviceScope.launch {
            val todayLog = repository.getTodayLogSnapshot()
            todayLog?.let { log ->
                Platform.entries.forEach { p ->
                    reelCounts[p.packageName] = log.getReelsForPlatform(p)
                }
            }
        }

        serviceScope.launch {
            repository.getLimits().collect { limitsList ->
                limitsList.forEach { limit ->
                    limitsCache[limit.platform] = limit.dailyReelLimit
                }
            }
        }

        startService(Intent(this, FloatingCounterService::class.java))
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        isRunning = false
        instance = null
        mainHandler.removeCallbacks(hideRunnable)
        serviceScope.cancel()
        stopService(Intent(this, FloatingCounterService::class.java))
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Event dispatcher
    // ─────────────────────────────────────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString() ?: return
        val platform = Platform.fromPackageName(pkg) ?: return

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> {
                Log.d(TAG, "WINDOW_STATE: pkg=$pkg class=${event.className}")
                lastInspectionMs.remove(pkg) // Clear cooldown so the new screen is inspected immediately!
                // Do NOT clear state variables here. This prevents resetting signature detection
                // on minor state transitions (like opening comments or showing overlays).
                // The layout inspection triggered by subsequent content changes will handle visibility updates.
            }

            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {
                handleViewScrolled(event, platform, pkg)
                if (hasReelPager[pkg] == true || platform == Platform.TIKTOK) {
                    showBubbleAndResetTimer(platform)
                }
            }

            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> {
                val isSubtree = (event.contentChangeTypes and
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_SUBTREE) != 0
                if (isSubtree) {
                    handleContentChanged(event, platform, pkg)
                }
            }

            else -> {
                if (hasReelPager[pkg] == true || platform == Platform.TIKTOK) {
                    showBubbleAndResetTimer(platform)
                }
            }
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // TYPE_VIEW_SCROLLED — works for some apps (Instagram feed, TikTok)
    // ─────────────────────────────────────────────────────────────────────────

    private fun handleViewScrolled(event: AccessibilityEvent, platform: Platform, pkg: String) {
        // TikTok: entire app is short videos, always count forward scrolls
        if (platform == Platform.TIKTOK) {
            if (isForwardScroll(event)) {
                tryCountReel(platform, pkg, "VIEW_SCROLLED/TikTok")
            }
            return
        }

        // Only process scroll events if we are actively viewing a Reels/Shorts pager
        if (hasReelPager[pkg] != true) return

        val fromIdx = event.fromIndex
        val toIdx = event.toIndex
        val itemCount = event.itemCount

        if (fromIdx >= 0 && toIdx >= 0) {
            val visibleItems = toIdx - fromIdx

            if (visibleItems <= 1 && itemCount > 2) {
                // Single-item pager — this is a reel view
                if (isForwardScroll(event)) {
                    tryCountReel(platform, pkg, "VIEW_SCROLLED/pager")
                }
            }
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
            if (pkg == "com.google.android.youtube") {
                handleYouTubeShorts(root, platform, pkg)
            } else {
                handleGenericReels(root, platform, pkg)
            }
        } finally {
            root.recycle()
        }
    }

    private fun handleYouTubeShorts(root: AccessibilityNodeInfo, platform: Platform, pkg: String) {
        val info = inspectYouTubeShorts(root)
        if (info.isActive) {
            hasReelPager[pkg] = true
            showBubbleAndResetTimer(platform)

            val signature = info.signature
            if (signature != null) {
                val lastKnownSig = lastSeenSignature[pkg]

                if (lastKnownSig == null) {
                    // First detection — record baseline
                    lastSeenSignature[pkg] = signature
                    recentYouTubeSignatures.add(signature)
                    Log.d(TAG, "YT SHORTS BASELINE: signature=$signature")
                } else if (signature != lastKnownSig) {
                    lastSeenSignature[pkg] = signature
                    Log.d(TAG, "YT SHORTS CHANGED: $lastKnownSig → $signature")

                    if (!recentYouTubeSignatures.contains(signature)) {
                        // New video, count it!
                        recentYouTubeSignatures.add(signature)
                        if (recentYouTubeSignatures.size > MAX_SIGNATURES_HISTORY) {
                            recentYouTubeSignatures.removeAt(0)
                        }
                        tryCountReel(platform, pkg, "YT_SHORTS_SIG_CHANGE")
                    } else {
                        Log.d(TAG, "YT SHORTS RE-WATCH: signature already in history, not counting")
                    }
                }
            }
        } else {
            if (hasReelPager[pkg] == true) {
                Log.d(TAG, "YT SHORTS LOST: navigated away")
            }
            hasReelPager[pkg] = false
            hideBubble()
        }
    }

    private fun handleGenericReels(root: AccessibilityNodeInfo, platform: Platform, pkg: String) {
        val pagerInfo = findReelPager(root)

        if (pagerInfo != null) {
            val (currentIndex, totalItems) = pagerInfo
            hasReelPager[pkg] = true
            showBubbleAndResetTimer(platform)

            val lastIndex = lastPagerIndex[pkg]
            if (lastIndex == null) {
                // First detection — record baseline, don't count
                lastPagerIndex[pkg] = currentIndex
                Log.d(TAG, "PAGER FOUND: pkg=$pkg index=$currentIndex total=$totalItems (baseline)")
            } else if (currentIndex != lastIndex) {
                // Index changed! User swiped to a different reel.
                val isForward = currentIndex > lastIndex
                lastPagerIndex[pkg] = currentIndex
                Log.d(TAG, "PAGER CHANGED: pkg=$pkg $lastIndex→$currentIndex (forward=$isForward)")

                if (isForward) {
                    tryCountReel(platform, pkg, "PAGER_INDEX_CHANGE")
                }
            }
        } else {
            if (hasReelPager[pkg] == true) {
                Log.d(TAG, "PAGER LOST: pkg=$pkg (navigated away from reels?)")
            }
            hasReelPager[pkg] = false
            hideBubble()
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

        // Fallback: if no view ID matched, we identify Shorts by presence of a recycler,
        // creator handle (@), and either a like button description ("like this...") or comment button.
        // This avoids matches on the Home feed where like buttons and comment lists are not directly visible on cards.
        if (!isActive && hasRecycler && hasHandle && (likeDesc != null || commentDesc != null)) {
            isActive = true
        }

        val signature = if (handle != null || commentDesc != null || titleText != null) {
            "$handle|$commentDesc|$likeDesc|$titleText"
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
    private fun findReelPager(root: AccessibilityNodeInfo): Pair<Int, Int>? {
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

                if (node.isScrollable && isPagerClass) {
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

                        // A reel pager has at most 2 visible children
                        // (the current reel + maybe the next one being pre-loaded)
                        if (visibleChildCount in 1..2 && currentItemIndex != null) {
                            Log.d(TAG, "  Found pager: class=$cls visible=$visibleChildCount index=$currentItemIndex total=$totalItems")
                            result = Pair(currentItemIndex, if (totalItems > 0) totalItems else -1)
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
            withContext(Dispatchers.Main) {
                showBubbleAndResetTimer(platform)
            }
            repository.getTodayLogSnapshot()?.let { log ->
                notificationHelper.checkMilestone(log.getTotalReels())
            }
        }
    }

    private fun showBubbleAndResetTimer(platform: Platform) {
        mainHandler.removeCallbacks(hideRunnable)
        val count = reelCounts[platform.packageName] ?: 0
        val limit = limitsCache[platform.name] ?: 30
        FloatingCounterService.instance?.updateStats(platform.displayName, platform.emoji, count, limit)
        FloatingCounterService.instance?.show()
        mainHandler.postDelayed(hideRunnable, AUTO_HIDE_DELAY_MS)
    }

    private fun hideBubble() {
        mainHandler.removeCallbacks(hideRunnable)
        FloatingCounterService.instance?.hide()
    }
}
