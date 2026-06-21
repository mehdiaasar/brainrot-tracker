package com.example.brainrottracker.service

import android.animation.ObjectAnimator
import android.animation.PropertyValuesHolder
import android.animation.ValueAnimator
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Outline
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.WindowManager
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.compose.ui.graphics.toArgb
import com.example.brainrottracker.R
import com.example.brainrottracker.data.local.prefs.Prefs
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.notification.NotificationHelper
import com.example.brainrottracker.ui.screens.dashboard.DashboardMood

/**
 * Floating HUD overlay: a rounded pill showing a brain mascot that degrades with brain health
 * plus today's total reel count. Tapping it opens an info popup with the per-platform breakdown.
 * Dragging moves the pill.
 *
 * Appearance follows the app's Warm palette and the user's light/dark theme; size follows the
 * `hud_scale` preference. Both are re-read from prefs every time the pill is shown.
 */
class FloatingCounterService : Service() {

    private var windowManager: WindowManager? = null
    // The WindowManager used to add overlays. When the accessibility service is alive we add
    // through ITS context so we can use TYPE_ACCESSIBILITY_OVERLAY, which sits in a strictly
    // higher window layer than any other app's TYPE_APPLICATION_OVERLAY (e.g. Brain Pal).
    private var overlayWindowManager: WindowManager? = null
    private var useAccessibilityOverlay = false
    private var rootView: FrameLayout? = null
    private var pill: LinearLayout? = null
    private var brainView: ImageView? = null
    private var countTextView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false
    private var visible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    // Slow zoom in/out "breathing" played only when brain health is critical. Scale reads as a
    // gentle pulse rather than the opacity blink it replaced, which flickered badly over video.
    private var criticalAnimator: ObjectAnimator? = null

    private lateinit var prefs: SharedPreferences
    private val notifHelper by lazy { NotificationHelper(applicationContext) }

    // Latest stats, kept so the popup can render the current snapshot.
    private var currentTotal = 0
    private var currentHealth = 100
    private var currentBreakdown: List<HudPlatform> = emptyList()
    // Same 5-variation brain concept as the dashboard, picked from today's usage ratio.
    private var currentMood: DashboardMood = DashboardMood.GREAT

    // Resolved appearance (recomputed from prefs on each show()).
    private var isDark = true
    private var scale = 1.2f

    // Popup overlay state.
    private var popupView: View? = null
    private var isPopupAdded = false

    // Blocking overlay state. Blocking is total-based (combined reels across all apps vs one global
    // limit), so suppression is global too: a REMIND-mode dismissal lasts until the user leaves ALL
    // reel apps (cleared by onAllTrackedAppsLeft), and SNOOZE expiry lives in one global pref so it
    // covers every app and survives service restarts; HARD is never suppressed.
    private var blockingView: View? = null
    private var isBlockingAdded = false
    private var remindDismissedThisSession = false

    /** One row of the tap-to-open breakdown. */
    data class HudPlatform(val platform: Platform, val count: Int, val limit: Int)

    companion object {
        private const val TAG = "FloatingCounter"
        const val PREFS = Prefs.FILE
        /** Global snooze expiry (epoch millis) — one key for all apps, since blocking is total-based. */
        const val SNOOZE_KEY = Prefs.SNOOZE_UNTIL_ALL
        const val KEY_SCALE = Prefs.HUD_SCALE
        const val KEY_THEME = Prefs.THEME_MODE
        const val DEFAULT_SCALE = 1.2f
        /** How far the oversized Brain bleeds beyond the capsule, in dp (pre-scale). */
        private const val BLEED_DP = 30f

        var instance: FloatingCounterService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Prefer the accessibility service's WindowManager so we can draw a TYPE_ACCESSIBILITY_OVERLAY
        // (top-most layer). It's started right after the service connects, so the instance is set here.
        val acc = ReelCounterService.instance
        overlayWindowManager = (acc?.getSystemService(WINDOW_SERVICE) as? WindowManager) ?: windowManager
        useAccessibilityOverlay = acc != null
        resolveAppearance()
        createFloatingView()
        Log.d(TAG, "FloatingCounterService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        notifHelper.createChannels()
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notifHelper.getServiceNotification())
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

    /** WindowManager to add/update/remove overlays with — the accessibility one when available. */
    private fun overlayWm(): WindowManager = overlayWindowManager ?: windowManager!!

    /** Window type for our overlays: accessibility layer (top-most) when we can, else app overlay. */
    private fun overlayType(): Int = when {
        useAccessibilityOverlay -> WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O -> WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else -> @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE
    }

    // ── Appearance (theme + size) ─────────────────────────────────────────────

    /** Resolve the active light/dark state and size scale from preferences. */
    private fun resolveAppearance() {
        isDark = when (prefs.getString(KEY_THEME, "SYSTEM")) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val night = resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
        scale = prefs.getFloat(KEY_SCALE, DEFAULT_SCALE).coerceIn(0.8f, 1.8f)
    }

    private val surfaceColor get() = if (isDark) 0xFF252320.toInt() else 0xFFEFE9DE.toInt()
    private val innerColor get() = if (isDark) 0xFF1F1E1B.toInt() else 0xFFEBE6DF.toInt()
    private val borderColor get() = if (isDark) 0xFF2F2C28.toInt() else 0xFFE6DFD8.toInt()
    private val textColor get() = if (isDark) 0xFFFAF9F5.toInt() else 0xFF141413.toInt()
    private val textSecondaryColor get() = if (isDark) 0xFFA09D96.toInt() else 0xFF6C6A64.toInt()
    private val accentColor get() = 0xFFCC785C.toInt() // terracotta — same in both themes

    private fun createFloatingView() {
        val context = this
        rootView = FrameLayout(context)

        brainView = ImageView(context).apply {
            setImageResource(currentMood.mainRes)
        }
        countTextView = TextView(context).apply {
            text = "0"
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

        pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // Children are NOT clipped, so the oversized Brain can bleed beyond the capsule
            // and "appear big" without enlarging the capsule itself.
            clipToOutline = false
            clipChildren = false
            clipToPadding = false
            addView(brainView)
            addView(countTextView)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).apply { gravity = Gravity.CENTER }
        }
        rootView?.clipChildren = false
        rootView?.clipToPadding = false
        rootView?.addView(pill)
        applyPillAppearance()

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                // Let the pill be dragged into the status-bar area and right up to every screen
                // edge; without these the system clamps the window to the safe area, which (with
                // the asymmetric bleed padding) blocked movement towards the top and left.
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // Default to the top-centre. The window carries BLEED_DP of top padding (room for the
            // oversized brain), so a small y still clears the status bar while reading as "top".
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(8f)
        }

        attachTouchHandler()
        Log.d(TAG, "Floating view created")
    }

    /** Push the resolved theme + scale onto the pill's views. */
    private fun applyPillAppearance() {
        applyBrainSize()
        countTextView?.apply {
            textSize = 22f * scale
            setTextColor(currentMood.accent.toArgb())
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(8f * scale) }
        }
        pill?.apply {
            setPadding(dp(16f * scale), dp(8f * scale), dp(18f * scale), dp(8f * scale))
            background = GradientDrawable().apply {
                cornerRadius = dp(16f).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1f), borderColor)
            }
            requestLayout()
        }
        // The overlay window wraps the root view, so pad it on every side to leave room for the
        // bleeding Brain and for the critical-health zoom (otherwise the window edge clips them).
        // Symmetric padding also centres the compact capsule exactly under the window.
        val bleed = dp(BLEED_DP * scale)
        rootView?.setPadding(bleed, bleed, bleed, bleed)
    }

    /**
     * Size the pill Brain. It bleeds out of the compact capsule via negative margins. The
     * first variation ("great") art reads ~20% larger than the others, so it is trimmed to match.
     */
    private fun applyBrainSize() {
        val base = if (currentMood == DashboardMood.GREAT) 68f else 85f
        val brainSize = dp(base * scale)
        val bleed = dp(BLEED_DP * scale)
        brainView?.layoutParams = LinearLayout.LayoutParams(brainSize, brainSize).apply {
            topMargin = -bleed
            bottomMargin = -bleed
            leftMargin = -bleed
        }
    }

    /** Distinguishes a tap (→ open popup) from a drag (→ move the pill). */
    private fun attachTouchHandler() {
        rootView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var downTime = 0L
            private val slop = dp(8f)

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams?.x ?: 0
                        initialY = layoutParams?.y ?: 0
                        touchX = event.rawX
                        touchY = event.rawY
                        downTime = System.currentTimeMillis()
                        pill?.animate()?.scaleX(0.94f)?.scaleY(0.94f)?.setDuration(80)?.start()
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams?.x = initialX + (event.rawX - touchX).toInt()
                        layoutParams?.y = initialY + (event.rawY - touchY).toInt()
                        clampToScreen()
                        if (isViewAdded) overlayWm().updateViewLayout(rootView, layoutParams)
                        return true
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        pill?.animate()?.scaleX(1f)?.scaleY(1f)?.setDuration(80)?.start()
                        val moved = kotlin.math.hypot(event.rawX - touchX, event.rawY - touchY)
                        val elapsed = System.currentTimeMillis() - downTime
                        if (event.action == MotionEvent.ACTION_UP && moved < slop && elapsed < 250) togglePopup()
                        return true
                    }
                }
                return false
            }
        })
    }

    /**
     * Keep the visible pill within the screen while still letting it touch every edge. The window
     * is wider/taller than the pill (it carries [BLEED_DP] padding on all four sides so the
     * oversized Brain can bleed out and the critical zoom has room), so the clamp accounts for that
     * padding rather than the raw window size. Uses CENTER_HORIZONTAL/TOP gravity, matching [layoutParams].
     */
    private fun clampToScreen() {
        val lp = layoutParams ?: return
        val rv = rootView ?: return
        val ww = rv.width
        val wh = rv.height
        if (ww == 0 || wh == 0) return
        val w = resources.displayMetrics.widthPixels
        val h = resources.displayMetrics.heightPixels
        val bleed = dp(BLEED_DP * scale)
        // x is an offset from the horizontal centre; padding is bleed on both left and right.
        val xMin = -((w - ww) / 2) - bleed
        val xMax = (w - ww) / 2 + bleed
        // y is an offset from the top; top and bottom padding are both bleed.
        val yMin = -bleed
        val yMax = h - wh + bleed
        if (xMin <= xMax) lp.x = lp.x.coerceIn(xMin, xMax)
        if (yMin <= yMax) lp.y = lp.y.coerceIn(yMin, yMax)
    }

    /**
     * Show the floating pill — safe to call from any thread. Re-applies theme/size and plays a
     * pop-in animation only on the hidden→visible transition, so the 1-second heartbeat that calls
     * this repeatedly doesn't re-animate or flicker the pill.
     */
    fun show() {
        mainHandler.post {
            try {
                if (visible && isViewAdded) return@post
                resolveAppearance()
                applyPillAppearance()
                if (!isViewAdded && rootView != null && layoutParams != null) {
                    layoutParams!!.type = overlayType()
                    try {
                        overlayWm().addView(rootView, layoutParams)
                    } catch (e: Exception) {
                        // Some OEMs reject accessibility overlays from this path — fall back to a
                        // normal app overlay so the counter still shows (just not guaranteed top-most).
                        Log.w(TAG, "accessibility overlay failed, falling back to app overlay: ${e.message}")
                        useAccessibilityOverlay = false
                        overlayWindowManager = windowManager
                        layoutParams!!.type = overlayType()
                        windowManager?.addView(rootView, layoutParams)
                    }
                    isViewAdded = true
                }
                rootView?.visibility = View.VISIBLE
                visible = true
                playPopIn()
            } catch (e: Exception) {
                Log.e(TAG, "show() failed: ${e.message}", e)
            }
        }
    }

    private fun playPopIn() {
        pill?.apply {
            scaleX = 0.7f
            scaleY = 0.7f
            alpha = 0f
            animate()
                .scaleX(1f).scaleY(1f).alpha(1f)
                .setDuration(240)
                .setInterpolator(OvershootInterpolator(1.6f))
                .start()
        }
    }

    /** Hide the floating pill (and any popup) — safe to call from any thread. */
    fun hide() {
        mainHandler.post {
            visible = false
            rootView?.visibility = View.GONE
            removePopup()
        }
    }

    /**
     * Update the pill with today's [total] reel count, overall brain [health] (0..100), the
     * per-platform [breakdown] used to populate the tap-to-open popup, and the usage [reelRatio]
     * (today's reels / daily limit) that picks the 5-variation mood — mirroring the dashboard.
     * When [reelRatio] is negative it is derived from [health] as a fallback.
     */
    fun updateHud(total: Int, health: Int, breakdown: List<HudPlatform>, reelRatio: Float = -1f) {
        mainHandler.post {
            currentTotal = total
            currentHealth = health.coerceIn(0, 100)
            currentBreakdown = breakdown
            val ratio = if (reelRatio >= 0f) reelRatio else (1f - currentHealth / 100f)
            currentMood = DashboardMood.fromUsage(ratio)

            // Keep the foreground notification's brain + stats in sync with the current mood/health.
            runCatching {
                getSystemService(NotificationManager::class.java)?.notify(
                    NotificationHelper.SERVICE_NOTIFICATION_ID,
                    notifHelper.getServiceNotification(currentMood, currentTotal, currentHealth)
                )
            }

            countTextView?.text = "$total"
            countTextView?.setTextColor(currentMood.accent.toArgb())
            brainView?.setImageResource(currentMood.mainRes)
            // Re-apply per-mood size (the "great" art is trimmed); show() may skip this when
            // the pill is already on screen and the mood changes mid-session.
            applyBrainSize()
            brainView?.requestLayout()

            // Slow zoom in/out when the brain is in a bad way — a gentle "breathing" attention cue.
            // The window carries bleed padding on every side, so the centre-pivot zoom grows into
            // that slack rather than off a window edge (which used to truncate the count).
            if (currentHealth < 25) {
                if (criticalAnimator == null) {
                    criticalAnimator = ObjectAnimator.ofPropertyValuesHolder(
                        pill,
                        PropertyValuesHolder.ofFloat(View.SCALE_X, 1f, 1.12f),
                        PropertyValuesHolder.ofFloat(View.SCALE_Y, 1f, 1.12f),
                    ).apply {
                        duration = 1100
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        start()
                    }
                }
            } else {
                criticalAnimator?.cancel()
                criticalAnimator = null
                pill?.scaleX = 1f
                pill?.scaleY = 1f
            }

            if (isPopupAdded) refreshPopupContent()
            Log.d(TAG, "HUD updated: total=$total health=$currentHealth")
        }
    }

    // ── Popup ───────────────────────────────────────────────────────────────

    private fun togglePopup() {
        if (isPopupAdded) removePopup() else showPopup()
    }

    private fun showPopup() {
        if (isPopupAdded) return
        resolveAppearance()
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            overlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        val scrim = FrameLayout(this).apply {
            setBackgroundColor(0xAA000000.toInt())
            setOnClickListener { removePopup() }
        }
        scrim.addView(buildPopupCard(), centeredCardParams())

        popupView = scrim
        try {
            overlayWm().addView(scrim, params)
            isPopupAdded = true
        } catch (e: Exception) {
            Log.e(TAG, "showPopup failed: ${e.message}", e)
        }
    }

    private fun centeredCardParams(): FrameLayout.LayoutParams {
        val maxW = resources.displayMetrics.widthPixels - dp(40f)
        val cardW = minOf(maxW, dp(340f))
        return FrameLayout.LayoutParams(cardW, FrameLayout.LayoutParams.WRAP_CONTENT)
            .apply { gravity = Gravity.CENTER }
    }

    private fun refreshPopupContent() {
        val scrim = popupView as? FrameLayout ?: return
        scrim.removeAllViews()
        scrim.addView(buildPopupCard(), centeredCardParams())
    }

    /** A pill action button shared by the popup and blocking overlays. [primary] = filled accent. */
    private fun actionButton(
        label: String,
        primary: Boolean,
        leftMargin: Int = 0,
        onClick: () -> Unit,
    ): TextView = TextView(this).apply {
        text = label
        textSize = 14f
        gravity = Gravity.CENTER
        setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            .apply { this.leftMargin = leftMargin }
        if (primary) {
            setTextColor(Color.WHITE)
            setTypeface(Typeface.DEFAULT_BOLD)
            background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat()
                setColor(accentColor)
            }
        } else {
            setTextColor(textSecondaryColor)
        }
        setOnClickListener { onClick() }
    }

    private fun buildPopupCard(): View {
        val ctx = this
        val healthLabel = when {
            currentHealth >= 75 -> "HEALTHY"
            currentHealth >= 50 -> "MODERATE"
            currentHealth >= 25 -> "POOR"
            else -> "VERY POOR"
        }
        val moodAccent = currentMood.accent.toArgb()

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20f), dp(20f), dp(20f), dp(16f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1f), borderColor)
            }
            setOnClickListener { } // consume taps so they don't reach the scrim
        }

        // Header: the dashboard's mood Brain + its headline/bubble copy.
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(ImageView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(72f), dp(72f))
            setImageResource(currentMood.mainRes)
        })
        header.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(14f) }
            addView(TextView(ctx).apply {
                text = currentMood.headline
                setTextColor(textColor)
                textSize = 18f
                setTypeface(Typeface.DEFAULT_BOLD)
            })
            addView(TextView(ctx).apply {
                text = currentMood.bubble
                setTextColor(textSecondaryColor)
                textSize = 13f
                setPadding(0, dp(4f), 0, 0)
            })
        })
        card.addView(header)

        // Score + reels summary row, styled like the dashboard's score ring + count.
        card.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(16f), 0, dp(10f))
            addView(TextView(ctx).apply {
                text = "$currentHealth"
                setTextColor(moodAccent)
                textSize = 30f
                setTypeface(Typeface.DEFAULT_BOLD)
            })
            addView(TextView(ctx).apply {
                text = "  $healthLabel"
                setTextColor(textSecondaryColor)
                textSize = 12f
                setTypeface(Typeface.DEFAULT_BOLD)
                layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            })
            addView(TextView(ctx).apply {
                text = "$currentTotal reels today"
                setTextColor(textSecondaryColor)
                textSize = 13f
            })
        })

        if (currentBreakdown.isEmpty()) {
            card.addView(TextView(ctx).apply {
                text = "No reels yet today — nice. 🧘"
                setTextColor(textSecondaryColor)
                textSize = 13f
                setPadding(0, dp(4f), 0, dp(8f))
            })
        } else {
            currentBreakdown.forEach { row ->
                card.addView(LinearLayout(ctx).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(12f), dp(10f), dp(12f), dp(10f))
                    background = GradientDrawable().apply {
                        cornerRadius = dp(10f).toFloat()
                        setColor(innerColor)
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT
                    ).apply { bottomMargin = dp(8f) }

                    addView(PlatformLogoView(ctx, row.platform).apply {
                        layoutParams = LinearLayout.LayoutParams(dp(28f), dp(28f))
                            .apply { rightMargin = dp(12f) }
                    })
                    addView(TextView(ctx).apply {
                        text = row.platform.displayName
                        setTextColor(textColor)
                        textSize = 14f
                        layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(TextView(ctx).apply {
                        text = "${row.count} / ${row.limit}"
                        setTextColor(if (row.count >= row.limit) accentColor else textSecondaryColor)
                        textSize = 14f
                        setTypeface(Typeface.DEFAULT_BOLD)
                    })
                })
            }
        }

        // Actions.
        val actions = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8f), 0, 0)
        }
        actions.addView(actionButton("Open app", primary = true) { openApp() })
        actions.addView(actionButton("Close", primary = false, leftMargin = dp(8f)) { removePopup() })
        card.addView(actions)

        return card
    }

    private fun openApp() {
        removePopup()
        val launch = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (launch != null) startActivity(launch)
    }

    private fun removePopup() {
        if (isPopupAdded && popupView != null) {
            try {
                overlayWm().removeView(popupView)
            } catch (_: Exception) {}
        }
        isPopupAdded = false
        popupView = null
    }

    // ── Blocking overlay ──────────────────────────────────────────────────────

    /** Called by [ReelCounterService] when the user leaves ALL tracked apps (the scroll session
     * ended). Re-arms the REMIND reminder so the next doomscroll session prompts again. */
    fun onAllTrackedAppsLeft() {
        remindDismissedThisSession = false
    }

    fun showBlockingOverlay(platform: Platform, limit: Int, mode: BlockingMode) {
        mainHandler.post {
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
            if (isBlockingAdded) return@post

            resolveAppearance()
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                overlayType(),
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            val scrim = FrameLayout(this).apply {
                setBackgroundColor(0xDD000000.toInt())
            }
            scrim.addView(buildBlockingCard(platform, limit, mode), centeredCardParams())

            blockingView = scrim
            try {
                overlayWm().addView(scrim, params)
                isBlockingAdded = true
            } catch (e: Exception) {
                Log.e(TAG, "showBlockingOverlay failed: ${e.message}", e)
            }
        }
    }

    private fun buildBlockingCard(platform: Platform, limit: Int, mode: BlockingMode): View {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24f), dp(28f), dp(24f), dp(20f))
            background = GradientDrawable().apply {
                cornerRadius = dp(20f).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1f), borderColor)
            }
            setOnClickListener { }
        }

        // Severe usage → the dashboard's "limit reached" Brain variation and its headline.
        val blockMood = DashboardMood.LIMIT
        card.addView(ImageView(this).apply {
            setImageResource(blockMood.mainRes)
            adjustViewBounds = true
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                dp(140f)
            ).apply { bottomMargin = dp(12f) }
        })

        card.addView(TextView(this).apply {
            text = blockMood.headline
            setTextColor(textColor)
            textSize = 22f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
        })

        val subtitle = when (mode) {
            BlockingMode.HARD -> "You've hit your daily limit of $limit videos.\nBlocked until midnight. 🧘"
            BlockingMode.SNOOZE -> "You've hit your daily limit of $limit videos.\nTime to take a break! 🧘"
            BlockingMode.REMIND -> "You've hit your daily limit of $limit videos.\nTime to take a break! 🧘"
        }
        card.addView(TextView(this).apply {
            text = subtitle
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(24f) }
        })

        val actions = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // Primary action in every mode: leave the blocked app.
        actions.addView(actionButton("Close app", primary = true) {
            removeBlockingOverlay()
            ReelCounterService.instance?.performGlobalAction(
                android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
            )
        })
        // Secondary action depends on the mode; HARD offers no way to keep scrolling.
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
        card.addView(actions)

        return card
    }

    private fun removeBlockingOverlay() {
        if (isBlockingAdded && blockingView != null) {
            try {
                overlayWm().removeView(blockingView)
            } catch (_: Exception) {}
        }
        isBlockingAdded = false
        blockingView = null
    }

    override fun onDestroy() {
        super.onDestroy()
        criticalAnimator?.cancel()
        criticalAnimator = null
        instance = null
        removePopup()
        removeBlockingOverlay()
        if (isViewAdded && rootView != null) {
            try {
                overlayWm().removeView(rootView)
            } catch (_: Exception) {}
            isViewAdded = false
        }
        Log.d(TAG, "FloatingCounterService destroyed")
    }

    // ── Custom views ──────────────────────────────────────────────────────────

    /**
     * Original Canvas-drawn renditions of each platform logo. Mirrors the Compose `PlatformLogo`
     * so the popup uses the real logos instead of emoji.
     */
    class PlatformLogoView(context: Context, private val platform: Platform) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        private val rect = RectF()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val s = minOf(width, height).toFloat()
            when (platform) {
                Platform.INSTAGRAM -> drawInstagram(canvas, s)
                Platform.YOUTUBE -> drawYouTube(canvas, s)
                Platform.TIKTOK -> drawTikTok(canvas, s)
                Platform.SNAPCHAT -> drawSnapchat(canvas, s)
            }
        }

        private fun drawInstagram(canvas: Canvas, s: Float) {
            val corner = s * 0.28f
            paint.style = Paint.Style.FILL
            paint.shader = LinearGradient(
                0f, s, s, 0f,
                intArrayOf(0xFFFEDA77.toInt(), 0xFFF58529.toInt(), 0xFFDD2A7B.toInt(), 0xFF8134AF.toInt()),
                null, Shader.TileMode.CLAMP
            )
            rect.set(0f, 0f, s, s)
            canvas.drawRoundRect(rect, corner, corner, paint)
            paint.shader = null
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = s * 0.085f
            paint.color = Color.WHITE
            val inset = s * 0.24f
            rect.set(inset, inset, s - inset, s - inset)
            canvas.drawRoundRect(rect, s * 0.14f, s * 0.14f, paint)
            canvas.drawCircle(s / 2f, s / 2f, s * 0.155f, paint)
            paint.style = Paint.Style.FILL
            canvas.drawCircle(s * 0.685f, s * 0.315f, s * 0.045f, paint)
        }

        private fun drawYouTube(canvas: Canvas, s: Float) {
            val rectH = s * 0.72f
            val top = (s - rectH) / 2f
            paint.style = Paint.Style.FILL
            paint.color = 0xFFE62C2C.toInt()
            rect.set(0f, top, s, top + rectH)
            canvas.drawRoundRect(rect, rectH * 0.32f, rectH * 0.32f, paint)
            paint.color = Color.WHITE
            val cx = s / 2f
            val cy = s / 2f
            val tri = rectH * 0.26f
            val play = Path().apply {
                moveTo(cx - tri * 0.7f, cy - tri)
                lineTo(cx - tri * 0.7f, cy + tri)
                lineTo(cx + tri * 0.95f, cy)
                close()
            }
            canvas.drawPath(play, paint)
        }

        private fun drawTikTok(canvas: Canvas, s: Float) {
            val corner = s * 0.26f
            paint.style = Paint.Style.FILL
            paint.color = 0xFF010101.toInt()
            rect.set(0f, 0f, s, s)
            canvas.drawRoundRect(rect, corner, corner, paint)

            fun note(color: Int, dx: Float, dy: Float) {
                paint.color = color
                paint.style = Paint.Style.STROKE
                paint.strokeCap = Paint.Cap.ROUND
                paint.strokeWidth = s * 0.085f
                val stem = Path().apply {
                    moveTo(s * 0.56f + dx, s * 0.26f + dy)
                    lineTo(s * 0.56f + dx, s * 0.62f + dy)
                }
                canvas.drawPath(stem, paint)
                val flag = Path().apply {
                    moveTo(s * 0.56f + dx, s * 0.26f + dy)
                    quadTo(s * 0.74f + dx, s * 0.28f + dy, s * 0.72f + dx, s * 0.40f + dy)
                }
                canvas.drawPath(flag, paint)
                paint.style = Paint.Style.FILL
                canvas.drawCircle(s * 0.45f + dx, s * 0.64f + dy, s * 0.085f, paint)
            }
            note(0xFF25F4EE.toInt(), -s * 0.025f, s * 0.02f)
            note(0xFFFE2C55.toInt(), s * 0.025f, -s * 0.02f)
            note(Color.WHITE, 0f, 0f)
        }

        private fun drawSnapchat(canvas: Canvas, s: Float) {
            val corner = s * 0.26f
            paint.style = Paint.Style.FILL
            paint.color = 0xFFFFFC00.toInt()
            rect.set(0f, 0f, s, s)
            canvas.drawRoundRect(rect, corner, corner, paint)

            val left = s * 0.28f
            val right = s * 0.72f
            val topY = s * 0.22f
            val bottomY = s * 0.74f
            val ghost = Path().apply {
                moveTo(left, bottomY)
                lineTo(left, s * 0.42f)
                quadTo(left, topY, s / 2f, topY)
                quadTo(right, topY, right, s * 0.42f)
                lineTo(right, bottomY)
                quadTo(s * 0.64f, bottomY + s * 0.07f, s * 0.575f, bottomY)
                quadTo(s * 0.5f, bottomY + s * 0.07f, s * 0.425f, bottomY)
                quadTo(s * 0.36f, bottomY + s * 0.07f, left, bottomY)
                close()
            }
            paint.color = Color.WHITE
            canvas.drawPath(ghost, paint)
            paint.color = 0xFFFFFC00.toInt()
            canvas.drawCircle(s * 0.43f, s * 0.42f, s * 0.03f, paint)
            canvas.drawCircle(s * 0.57f, s * 0.42f, s * 0.03f, paint)
        }
    }
}
