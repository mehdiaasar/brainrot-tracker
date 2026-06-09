package com.example.brainrottracker.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
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
import android.widget.LinearLayout
import android.widget.TextView
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.notification.NotificationHelper
import java.time.LocalDate

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
    private var rootView: FrameLayout? = null
    private var pill: LinearLayout? = null
    private var brainView: BrainFaceView? = null
    private var countTextView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false
    private var visible = false
    private val mainHandler = Handler(Looper.getMainLooper())
    private var criticalAnimator: ObjectAnimator? = null

    private lateinit var prefs: SharedPreferences

    // Latest stats, kept so the popup can render the current snapshot.
    private var currentTotal = 0
    private var currentHealth = 100
    private var currentBreakdown: List<HudPlatform> = emptyList()

    // Resolved appearance (recomputed from prefs on each show()).
    private var isDark = true
    private var scale = 1.2f

    // Popup overlay state.
    private var popupView: View? = null
    private var isPopupAdded = false

    // Blocking overlay state.
    private var blockingView: View? = null
    private var isBlockingAdded = false
    private val dismissedToday = mutableSetOf<String>()
    private var dismissedDate: String = ""

    /** One row of the tap-to-open breakdown. */
    data class HudPlatform(val platform: Platform, val count: Int, val limit: Int)

    companion object {
        private const val TAG = "FloatingCounter"
        const val PREFS = "brainrot_prefs"
        const val KEY_SCALE = "hud_scale"
        const val KEY_THEME = "theme_mode"
        const val DEFAULT_SCALE = 1.2f

        var instance: FloatingCounterService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        resolveAppearance()
        createFloatingView()
        Log.d(TAG, "FloatingCounterService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.createChannels()
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notificationHelper.getServiceNotification())

        if (intent != null && intent.action == "com.example.brainrottracker.UPDATE_HUD") {
            val count = intent.getIntExtra("count", 0)
            val limit = intent.getIntExtra("limit", 30)
            val name = intent.getStringExtra("platform") ?: "Instagram"
            val ratio = if (limit > 0) count.toFloat() / limit else 0f
            val health = ((1f - ratio).coerceIn(0f, 1f) * 100).toInt()
            val p = Platform.entries.find { it.displayName == name }
            updateHud(count, health, if (p != null) listOf(HudPlatform(p, count, limit)) else emptyList())
            show()
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun dp(value: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()

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

        brainView = BrainFaceView(context).apply { health = currentHealth }
        countTextView = TextView(context).apply {
            text = "0"
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER_VERTICAL
        }

        pill = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            // No elevation: the shadow is what produced the translucent rectangular "film".
            // A slight rounded-rect outline keeps content corners crisp.
            val corner = dp(14f).toFloat()
            outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, corner)
                }
            }
            clipToOutline = true
            addView(brainView)
            addView(countTextView)
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
        }
        rootView?.addView(pill)
        applyPillAppearance()

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        layoutParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = dp(80f)
        }

        attachTouchHandler()
        Log.d(TAG, "Floating view created")
    }

    /** Push the resolved theme + scale onto the pill's views. */
    private fun applyPillAppearance() {
        brainView?.layoutParams = LinearLayout.LayoutParams(dp(30f * scale), dp(30f * scale))
        countTextView?.apply {
            textSize = 22f * scale
            setTextColor(accentColor)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { leftMargin = dp(10f * scale) }
        }
        pill?.apply {
            setPadding(dp(16f * scale), dp(10f * scale), dp(20f * scale), dp(10f * scale))
            background = GradientDrawable().apply {
                cornerRadius = dp(14f).toFloat()
                setColor(surfaceColor)
                setStroke(dp(1f), borderColor)
            }
            requestLayout()
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
                        if (isViewAdded) windowManager?.updateViewLayout(rootView, layoutParams)
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
                    windowManager?.addView(rootView, layoutParams)
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

    /** Legacy entry point retained for the manual UPDATE_HUD test path. */
    fun updateCount(totalCount: Int) {
        val health = ((1f - totalCount / 120f).coerceIn(0f, 1f) * 100).toInt()
        updateHud(totalCount, health, emptyList())
    }

    /**
     * Update the pill with today's [total] reel count, overall brain [health] (0..100), and the
     * per-platform [breakdown] used to populate the tap-to-open popup.
     */
    fun updateHud(total: Int, health: Int, breakdown: List<HudPlatform>) {
        mainHandler.post {
            currentTotal = total
            currentHealth = health.coerceIn(0, 100)
            currentBreakdown = breakdown

            countTextView?.text = "$total"
            brainView?.health = currentHealth

            // Gentle pulse when the brain is in a bad way.
            if (currentHealth < 25) {
                if (criticalAnimator == null) {
                    criticalAnimator = ObjectAnimator.ofFloat(pill, "alpha", 0.65f, 1f).apply {
                        duration = 800
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        start()
                    }
                }
            } else {
                criticalAnimator?.cancel()
                criticalAnimator = null
                pill?.alpha = 1f
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
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
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
            windowManager?.addView(scrim, params)
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

    private fun buildPopupCard(): View {
        val ctx = this
        val healthLabel = when {
            currentHealth >= 90 -> "Elite"
            currentHealth >= 75 -> "Healthy"
            currentHealth >= 50 -> "Tired"
            currentHealth >= 25 -> "Overstimulated"
            else -> "Brainrot"
        }

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

        // Header: brain face + health summary.
        val header = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        header.addView(BrainFaceView(ctx).apply {
            layoutParams = LinearLayout.LayoutParams(dp(56f), dp(56f))
            health = currentHealth
        })
        header.addView(LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(14f) }
            addView(TextView(ctx).apply {
                text = "Brain Health"
                setTextColor(textSecondaryColor)
                textSize = 12f
            })
            addView(TextView(ctx).apply {
                text = "$currentHealth% · $healthLabel"
                setTextColor(textColor)
                textSize = 20f
                setTypeface(Typeface.DEFAULT_BOLD)
            })
        })
        card.addView(header)

        card.addView(TextView(ctx).apply {
            text = "$currentTotal reels scrolled today"
            setTextColor(textSecondaryColor)
            textSize = 13f
            setPadding(0, dp(14f), 0, dp(10f))
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
        actions.addView(TextView(ctx).apply {
            text = "Open app"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat()
                setColor(accentColor)
            }
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener { openApp() }
        })
        actions.addView(TextView(ctx).apply {
            text = "Close"
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(8f) }
            setOnClickListener { removePopup() }
        })
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
                windowManager?.removeView(popupView)
            } catch (_: Exception) {}
        }
        isPopupAdded = false
        popupView = null
    }

    // ── Blocking overlay ──────────────────────────────────────────────────────

    fun showBlockingOverlay(platformName: String, limit: Int) {
        mainHandler.post {
            val today = LocalDate.now().toString()
            if (dismissedDate != today) {
                dismissedToday.clear()
                dismissedDate = today
            }
            if (dismissedToday.contains(platformName)) return@post
            if (isBlockingAdded) return@post

            resolveAppearance()
            val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            }
            val params = WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                type,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
            )

            val scrim = FrameLayout(this).apply {
                setBackgroundColor(0xDD000000.toInt())
            }
            scrim.addView(buildBlockingCard(platformName, limit) {
                dismissedToday.add(platformName)
                removeBlockingOverlay()
            }, centeredCardParams())

            blockingView = scrim
            try {
                windowManager?.addView(scrim, params)
                isBlockingAdded = true
            } catch (e: Exception) {
                Log.e(TAG, "showBlockingOverlay failed: ${e.message}", e)
            }
        }
    }

    private fun buildBlockingCard(platformName: String, limit: Int, onDismiss: () -> Unit): View {
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

        card.addView(TextView(this).apply {
            text = "🚫"
            textSize = 40f
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(12f) }
        })

        card.addView(TextView(this).apply {
            text = "Limit Reached"
            setTextColor(textColor)
            textSize = 22f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = dp(8f) }
        })

        card.addView(TextView(this).apply {
            text = "You've watched $limit $platformName videos today.\nTime to take a break! 🧘"
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
        actions.addView(TextView(this).apply {
            text = "Open App"
            setTextColor(Color.WHITE)
            textSize = 14f
            setTypeface(Typeface.DEFAULT_BOLD)
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat()
                setColor(accentColor)
            }
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            setOnClickListener {
                onDismiss()
                openApp()
            }
        })
        actions.addView(TextView(this).apply {
            text = "Got it"
            setTextColor(textSecondaryColor)
            textSize = 14f
            gravity = Gravity.CENTER
            setPadding(dp(16f), dp(12f), dp(16f), dp(12f))
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                .apply { leftMargin = dp(8f) }
            setOnClickListener { onDismiss() }
        })
        card.addView(actions)

        return card
    }

    private fun removeBlockingOverlay() {
        if (isBlockingAdded && blockingView != null) {
            try {
                windowManager?.removeView(blockingView)
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
                windowManager?.removeView(rootView)
            } catch (_: Exception) {}
            isViewAdded = false
        }
        Log.d(TAG, "FloatingCounterService destroyed")
    }

    // ── Custom views ──────────────────────────────────────────────────────────

    /**
     * An original, Canvas-drawn cartoon brain face that morphs with [health]: bright pink and
     * wide-eyed when healthy, grey and droopy when rotten. Mirrors the Compose `BrainMascot`.
     */
    class BrainFaceView(context: Context) : View(context) {
        var health: Int = 100
            set(value) {
                field = value.coerceIn(0, 100)
                invalidate()
            }

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val t = health / 100f
            val w = width.toFloat()
            val h = height.toFloat()
            val cx = w / 2f
            // headCy/headR tuned so the topmost bump (headCy - 1.25*headR) stays >= 0, i.e. the
            // brain is fully inside the view instead of being clipped at the top.
            val headR = w * 0.40f
            val headCy = h * 0.52f

            val brainColor = lerpColor(0xFFB2A9A6.toInt(), 0xFFE78BA6.toInt(), t)
            val foldColor = lerpColor(brainColor, Color.BLACK, 0.18f)

            paint.style = Paint.Style.FILL
            paint.color = brainColor
            val bumps = arrayOf(
                floatArrayOf(cx - headR * 0.6f, headCy - headR * 0.3f, headR * 0.55f),
                floatArrayOf(cx - headR * 0.18f, headCy - headR * 0.7f, headR * 0.55f),
                floatArrayOf(cx + headR * 0.28f, headCy - headR * 0.68f, headR * 0.55f),
                floatArrayOf(cx + headR * 0.62f, headCy - headR * 0.26f, headR * 0.52f),
                floatArrayOf(cx + headR * 0.56f, headCy + headR * 0.3f, headR * 0.5f),
                floatArrayOf(cx - headR * 0.56f, headCy + headR * 0.32f, headR * 0.5f),
                floatArrayOf(cx, headCy, headR * 0.95f)
            )
            bumps.forEach { canvas.drawCircle(it[0], it[1], it[2], paint) }

            paint.style = Paint.Style.STROKE
            paint.strokeCap = Paint.Cap.ROUND
            paint.strokeWidth = headR * 0.08f
            paint.color = foldColor
            canvas.drawLine(cx, headCy - headR * 0.8f, cx, headCy + headR * 0.5f, paint)
            val left = Path().apply {
                moveTo(cx - headR * 0.6f, headCy - headR * 0.1f)
                quadTo(cx - headR * 0.3f, headCy - headR * 0.3f, cx - headR * 0.34f, headCy + headR * 0.16f)
            }
            val right = Path().apply {
                moveTo(cx + headR * 0.6f, headCy - headR * 0.1f)
                quadTo(cx + headR * 0.3f, headCy - headR * 0.3f, cx + headR * 0.34f, headCy + headR * 0.16f)
            }
            canvas.drawPath(left, paint)
            canvas.drawPath(right, paint)

            val eyeOpen = 0.16f + 0.84f * t
            val eyeR = headR * 0.3f
            val eyeY = headCy + headR * 0.02f
            val eyeDx = headR * 0.42f
            drawEye(canvas, cx - eyeDx, eyeY, eyeR, eyeOpen, t)
            drawEye(canvas, cx + eyeDx, eyeY, eyeR, eyeOpen, t)

            val mouth = ((t - 0.45f) * 2f).coerceIn(-1f, 1f)
            val mouthY = headCy + headR * 0.58f
            val mouthW = headR * 0.7f
            paint.style = Paint.Style.STROKE
            paint.strokeWidth = headR * 0.1f
            paint.color = lerpColor(foldColor, Color.BLACK, 0.3f)
            val mPath = Path().apply {
                moveTo(cx - mouthW / 2f, mouthY)
                quadTo(cx, mouthY + mouth * headR * 0.34f, cx + mouthW / 2f, mouthY)
            }
            canvas.drawPath(mPath, paint)
        }

        private fun drawEye(canvas: Canvas, x: Float, y: Float, r: Float, open: Float, t: Float) {
            val ballH = r * 2f * open
            paint.style = Paint.Style.FILL
            paint.color = Color.WHITE
            canvas.drawOval(x - r, y - ballH / 2f, x + r, y + ballH / 2f, paint)
            val pupilR = (r * 0.62f).coerceAtMost(ballH / 2f)
            val py = y + (r - ballH / 2f) * 0.5f
            paint.color = 0xFF1A1A1A.toInt()
            canvas.drawCircle(x, py, pupilR, paint)
            if (t > 0.55f) {
                paint.color = Color.WHITE
                canvas.drawCircle(x - pupilR * 0.3f, py - pupilR * 0.35f, pupilR * 0.32f, paint)
            }
        }
    }

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

private fun lerpColor(a: Int, b: Int, t: Float): Int {
    val f = t.coerceIn(0f, 1f)
    return Color.argb(
        (Color.alpha(a) + (Color.alpha(b) - Color.alpha(a)) * f).toInt(),
        (Color.red(a) + (Color.red(b) - Color.red(a)) * f).toInt(),
        (Color.green(a) + (Color.green(b) - Color.green(a)) * f).toInt(),
        (Color.blue(a) + (Color.blue(b) - Color.blue(a)) * f).toInt()
    )
}
