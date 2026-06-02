package com.example.brainrottracker.service

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PixelFormat
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
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.example.brainrottracker.notification.NotificationHelper

class FloatingCounterService : Service() {

    private var windowManager: WindowManager? = null
    private var rootView: FrameLayout? = null
    private var containerView: LinearLayout? = null
    private var emojiContainer: LinearLayout? = null
    private var emojiView: TextView? = null
    private var countTextView: TextView? = null
    private var progressBar: CustomProgressBar? = null
    private var limitTextView: TextView? = null
    private var badgeView: TextView? = null

    private var layoutParams: WindowManager.LayoutParams? = null
    private var isViewAdded = false
    private var currentCount = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private var criticalAnimator: ObjectAnimator? = null

    companion object {
        private const val TAG = "FloatingCounter"
        var instance: FloatingCounterService? = null
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createFloatingView()
        Log.d(TAG, "FloatingCounterService created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notificationHelper = NotificationHelper(applicationContext)
        notificationHelper.createChannels()
        val notification = notificationHelper.getServiceNotification()
        startForeground(NotificationHelper.SERVICE_NOTIFICATION_ID, notification)
        Log.d(TAG, "FloatingCounterService started as foreground")

        if (intent != null && intent.action == "com.example.brainrottracker.UPDATE_HUD") {
            val platform = intent.getStringExtra("platform") ?: "Instagram"
            val emoji = intent.getStringExtra("emoji") ?: "📸"
            val count = intent.getIntExtra("count", 0)
            val limit = intent.getIntExtra("limit", 30)
            updateStats(platform, emoji, count, limit)
            show()
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createFloatingView() {
        val context = this
        val dp = { value: Float ->
            TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
        }

        rootView = FrameLayout(context)

        containerView = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            
            // Set padding for container view
            setPadding(dp(4f), dp(4f), dp(4f), dp(4f))

            background = GradientDrawable().apply {
                cornerRadius = dp(100f).toFloat() // Make it a perfect capsule pill shape matching Figma rounded-full
                setColor(0xE60A0F2C.toInt()) // dark navy glass background matching Figma card backgrounds
                setStroke(dp(1.5f), 0xFF2B7FFF.toInt())
            }
            alpha = 0.95f
            elevation = dp(8f).toFloat()
        }

        emojiView = TextView(context).apply {
            text = "▶" // Use Play icon triangle to represent PlayCircle from Figma Screen 6
            textSize = 10f
            gravity = Gravity.CENTER
            setTextColor(0xFF2B7FFF.toInt())
        }

        emojiContainer = LinearLayout(context).apply {
            gravity = Gravity.CENTER
            val size = dp(22f)
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                bottomMargin = dp(2f)
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x332B7FFF.toInt()) // 20% opacity blue initially
            }
            addView(emojiView)
        }

        countTextView = TextView(context).apply {
            text = "0"
            textSize = 13f
            setTextColor(0xFFFFFFFF.toInt())
            setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        progressBar = CustomProgressBar(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(3f)
            ).apply {
                topMargin = dp(2f)
                bottomMargin = dp(2f)
                leftMargin = dp(2f)
                rightMargin = dp(2f)
            }
        }

        limitTextView = TextView(context).apply {
            text = "/ 30"
            textSize = 8f
            setTextColor(0x80FFFFFF.toInt())
            gravity = Gravity.CENTER
        }

        containerView?.apply {
            addView(emojiContainer)
            addView(countTextView)
            addView(progressBar)
            addView(limitTextView)
        }

        // Top-right Alert Badge matching Screen 6
        badgeView = TextView(context).apply {
            text = "!"
            textSize = 9f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setTypeface(typeface, Typeface.BOLD)
            
            val badgeSize = dp(14f)
            layoutParams = FrameLayout.LayoutParams(badgeSize, badgeSize).apply {
                gravity = Gravity.TOP or Gravity.END
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0xFFE7000B.toInt())
            }
            visibility = View.GONE
        }

        // Add views to FrameLayout root with 6dp offset for the badge overlap
        containerView?.layoutParams = FrameLayout.LayoutParams(
            dp(38f),
            dp(56f)
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.START
        }

        rootView?.apply {
            addView(containerView)
            addView(badgeView)
        }

        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        // Start layout width/height with 6dp offset to accommodate badge bounds
        layoutParams = WindowManager.LayoutParams(
            dp(44f),
            dp(62f),
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = dp(16f)
            y = dp(200f)
        }

        // Make it draggable via rootView touch
        rootView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View, event: MotionEvent): Boolean {
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = layoutParams?.x ?: 0
                        initialY = layoutParams?.y ?: 0
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        layoutParams?.x = initialX - (event.rawX - initialTouchX).toInt()
                        layoutParams?.y = initialY + (event.rawY - initialTouchY).toInt()
                        if (isViewAdded && rootView != null) {
                            windowManager?.updateViewLayout(rootView, layoutParams)
                        }
                        return true
                    }
                }
                return false
            }
        })

        Log.d(TAG, "Floating view created")
    }

    /** Show the floating bubble — safe to call from any thread */
    fun show() {
        mainHandler.post {
            try {
                if (!isViewAdded && rootView != null && layoutParams != null) {
                    windowManager?.addView(rootView, layoutParams)
                    isViewAdded = true
                    Log.d(TAG, "Bubble ADDED to window")
                } else if (isViewAdded) {
                    rootView?.visibility = View.VISIBLE
                    Log.d(TAG, "Bubble set VISIBLE")
                }
            } catch (e: Exception) {
                Log.e(TAG, "show() failed: ${e.message}", e)
            }
        }
    }

    /** Hide the floating bubble — safe to call from any thread */
    fun hide() {
        mainHandler.post {
            rootView?.visibility = View.GONE
            Log.d(TAG, "Bubble HIDDEN")
        }
    }

    /** Legacy fallback for updateCount */
    fun updateCount(totalCount: Int) {
        updateStats("Overall", "🧠", totalCount, 30)
    }

    /** Update stats with platform name, emoji, count and limit */
    fun updateStats(platformName: String, emoji: String, count: Int, limit: Int) {
        currentCount = count
        mainHandler.post {
            val dp = { value: Float ->
                TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value, resources.displayMetrics).toInt()
            }

            // Calculate ratio and threshold colors based on Screen 6
            val ratio = if (limit > 0) count.toFloat() / limit else 0f
            val themeColor = when {
                ratio < 0.60f -> 0xFF2B7FFF.toInt() // Blue (Safe)
                ratio < 0.90f -> 0xFFFD9900.toInt() // Orange (Warning)
                else -> 0xFFE7000B.toInt()          // Red (Critical)
            }

            val countTextColor = if (ratio < 0.60f) 0xFFFFFFFF.toInt() else themeColor

            // Set texts
            emojiView?.text = "▶" // Always render the Play triangle to align with the PlayCircle specification
            emojiView?.setTextColor(themeColor) // Color the play triangle with the active state theme color
            countTextView?.text = "$count"
            countTextView?.setTextColor(countTextColor)
            limitTextView?.text = "/ $limit"

            // Update custom progress bar
            progressBar?.progress = ratio
            progressBar?.fillColor = themeColor
            progressBar?.invalidate()

            // Dynamic scaling configs based on threshold (0-60%, 60-90%, 90-100%+)
            val config = when {
                ratio < 0.60f -> RowConfig(38f, 56f, 3f, 10f, 13f, 8f, 3f, 22f)
                ratio < 0.90f -> RowConfig(51f, 64f, 5f, 12f, 15f, 9f, 4f, 28f)
                else -> RowConfig(58f, 70f, 6f, 14f, 17f, 10f, 5f, 32f)
            }

            // Apply size changes to window layout (incorporating 6dp badge offset space)
            val badgeOffset = 6f
            layoutParams?.width = dp(config.widthDp + badgeOffset)
            layoutParams?.height = dp(config.heightDp + badgeOffset)

            // Update container LinearLayout layout params inside FrameLayout
            val containerParams = containerView?.layoutParams as? FrameLayout.LayoutParams
            if (containerParams != null) {
                containerParams.width = dp(config.widthDp)
                containerParams.height = dp(config.heightDp)
                containerView?.layoutParams = containerParams
            }

            containerView?.setPadding(dp(config.paddingDp), dp(config.paddingDp), dp(config.paddingDp), dp(config.paddingDp))

            // Update text sizes
            emojiView?.textSize = config.emojiSize
            countTextView?.textSize = config.countSize
            limitTextView?.textSize = config.limitSize

            // Scale emoji circular background
            val emojiContainerParams = emojiContainer?.layoutParams as? LinearLayout.LayoutParams
            if (emojiContainerParams != null) {
                val cSize = dp(config.emojiContainerSize)
                emojiContainerParams.width = cSize
                emojiContainerParams.height = cSize
                emojiContainer?.layoutParams = emojiContainerParams
            }

            // Update emoji circular container color
            val emojiBg = emojiContainer?.background as? GradientDrawable
            if (emojiBg != null) {
                emojiBg.setColor(themeColor and 0x00FFFFFF or 0x33000000) // 20% opacity theme color
            }

            // Update progress bar size
            progressBar?.layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(config.barHeightDp)
            ).apply {
                topMargin = dp(2f)
                bottomMargin = dp(2f)
                leftMargin = dp(2f)
                rightMargin = dp(2f)
            }

            // Set dynamic elevation and colored outline spot/ambient shadow matching Figma glows
            containerView?.elevation = when {
                ratio < 0.60f -> dp(6f).toFloat()
                ratio < 0.90f -> dp(8f).toFloat()
                else -> dp(10f).toFloat()
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                containerView?.outlineSpotShadowColor = themeColor
                containerView?.outlineAmbientShadowColor = themeColor
            }

            // Update container background border color and shape to match active threshold state
            val bg = containerView?.background as? GradientDrawable
            if (bg != null) {
                bg.setColor(0xE60A0F2C.toInt()) // dark navy glass background
                bg.setStroke(dp(2.0f), themeColor)
                bg.cornerRadius = dp(100f).toFloat() // Ensure the container retains its perfect capsule pill shape
            }

            // Handle pulsing animation and badge for Critical State (>=90%)
            if (ratio >= 0.90f) {
                badgeView?.visibility = View.VISIBLE
                if (criticalAnimator == null) {
                    criticalAnimator = ObjectAnimator.ofFloat(containerView, "alpha", 0.6f, 1.0f).apply {
                        duration = 800
                        repeatMode = ValueAnimator.REVERSE
                        repeatCount = ValueAnimator.INFINITE
                        start()
                    }
                }
            } else {
                badgeView?.visibility = View.GONE
                criticalAnimator?.cancel()
                criticalAnimator = null
                containerView?.alpha = 0.95f
            }

            // Force update to WindowManager
            if (isViewAdded && rootView != null) {
                try {
                    windowManager?.updateViewLayout(rootView, layoutParams)
                } catch (e: Exception) {
                    Log.e(TAG, "updateViewLayout failed", e)
                }
            }

            Log.d(TAG, "Overlay updated: $platformName, count=$count/$limit, ratio=$ratio")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        criticalAnimator?.cancel()
        criticalAnimator = null
        instance = null
        if (isViewAdded && rootView != null) {
            try {
                windowManager?.removeView(rootView)
            } catch (_: Exception) {}
            isViewAdded = false
        }
        Log.d(TAG, "FloatingCounterService destroyed")
    }

    private data class RowConfig(
        val widthDp: Float,
        val heightDp: Float,
        val paddingDp: Float,
        val emojiSize: Float,
        val countSize: Float,
        val limitSize: Float,
        val barHeightDp: Float,
        val emojiContainerSize: Float
    )

    class CustomProgressBar(context: Context) : View(context) {
        var progress: Float = 0f
        var fillColor: Int = 0xFFFFFFFF.toInt()

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val radius = height / 2f
            
            // Draw background track
            val bgPaint = Paint().apply {
                color = 0x22FFFFFF
                isAntiAlias = true
            }
            canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, bgPaint)

            // Draw filled progress
            if (progress > 0f) {
                val fillPaint = Paint().apply {
                    color = fillColor
                    isAntiAlias = true
                }
                val fillWidth = width.toFloat() * progress.coerceIn(0f, 1f)
                canvas.drawRoundRect(0f, 0f, fillWidth, height.toFloat(), radius, radius, fillPaint)
            }
        }
    }
}
