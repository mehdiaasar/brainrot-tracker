package com.example.brainrottracker.ui.screens.dashboard

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.lerp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.service.ReelCounterService
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmGoalBlue
import com.example.brainrottracker.theme.WarmGrantedGreen
import com.example.brainrottracker.theme.WarmInsightAccent
import com.example.brainrottracker.theme.WarmInsightAccentDark
import com.example.brainrottracker.theme.WarmInsightBg
import com.example.brainrottracker.theme.WarmInsightBgDark
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightInner
import com.example.brainrottracker.theme.WarmLightSurface
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmStepDim
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary
import com.example.brainrottracker.theme.rememberIsDark
import com.example.brainrottracker.ui.components.MoodCharacter
import com.example.brainrottracker.ui.components.PlatformLogo
import kotlin.math.abs
import androidx.compose.ui.util.lerp as lerpFloat

/** Warm amber used for the "nearing limit" tier across the dashboard. */
private val DashAmber = Color(0xFFE8A55A)

private val HeaderMax = 530.dp
private val HeaderMin = 224.dp

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onViewStreaks: () -> Unit = {},
    onEditPlan: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val trackBg = if (dark) WarmStepDim else WarmLightInner
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary

    val context = LocalContext.current
    val todayLog by viewModel.todayLog.collectAsState()
    val yesterdayLog by viewModel.yesterdayLog.collectAsState()
    val brainHealth by viewModel.brainHealth.collectAsState()
    val screenTimeToday by viewModel.screenTimeToday.collectAsState()
    val mood by viewModel.mood.collectAsState()
    val focusShield by viewModel.focusShield.collectAsState()
    val dailyGoalOnTrack by viewModel.dailyGoalOnTrack.collectAsState()
    val currentStreak by viewModel.currentStreak.collectAsState()
    val reelLimit by viewModel.reelLimit.collectAsState()
    val minuteLimit by viewModel.minuteLimit.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isTracking by remember { mutableStateOf(ReelCounterService.isRunning) }
    var hasUsagePermission by remember { mutableStateOf(ScreenTimeHelper.hasPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTracking = ReelCounterService.isRunning
                hasUsagePermission = ScreenTimeHelper.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val totalReels = todayLog?.getTotalReels() ?: 0
    val hours = screenTimeToday / 60
    val mins = screenTimeToday % 60
    val timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

    // --- Scroll-driven collapse, then slide away --------------------------------
    // Two phases, both fed by scroll before the list itself moves:
    //   1. `headerPx`  shrinks HeaderMax → HeaderMin — the Brain collapses center → right.
    //   2. `headerOffsetPx` then slides the whole (collapsed) hero up off-screen so the body
    //      rises to fill the screen. Scrolling back down reverses both phases.
    val density = LocalDensity.current
    val maxPx = with(density) { HeaderMax.toPx() }
    val minPx = with(density) { HeaderMin.toPx() }
    var headerPx by remember { mutableFloatStateOf(maxPx) }
    var headerOffsetPx by remember { mutableFloatStateOf(0f) }   // -minPx..0 (slide up)
    val fraction = ((maxPx - headerPx) / (maxPx - minPx)).coerceIn(0f, 1f)
    val headerHeight = with(density) { headerPx.toDp() }
    // Body starts at the header's visible bottom edge; shrinks to 0 once it has slid away.
    val bodyTopPad = with(density) { (headerPx + headerOffsetPx).coerceAtLeast(0f).toDp() }

    val collapseConnection = remember(maxPx, minPx) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                var remaining = available.y
                if (remaining < 0f) {
                    // 1. Collapse the header.
                    if (headerPx > minPx) {
                        val newPx = (headerPx + remaining).coerceAtLeast(minPx)
                        remaining -= (newPx - headerPx)
                        headerPx = newPx
                    }
                    // 2. Then slide it up off-screen.
                    if (remaining < 0f && headerOffsetPx > -minPx) {
                        val newOff = (headerOffsetPx + remaining).coerceAtLeast(-minPx)
                        remaining -= (newOff - headerOffsetPx)
                        headerOffsetPx = newOff
                    }
                    return Offset(0f, available.y - remaining)
                }
                return Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource
            ): Offset {
                var remaining = available.y
                if (remaining > 0f) {
                    // 1. Slide the header back down into view.
                    if (headerOffsetPx < 0f) {
                        val newOff = (headerOffsetPx + remaining).coerceAtMost(0f)
                        remaining -= (newOff - headerOffsetPx)
                        headerOffsetPx = newOff
                    }
                    // 2. Then expand it.
                    if (remaining > 0f && headerPx < maxPx) {
                        val newPx = (headerPx + remaining).coerceAtMost(maxPx)
                        remaining -= (newPx - headerPx)
                        headerPx = newPx
                    }
                    return Offset(0f, available.y - remaining)
                }
                return Offset.Zero
            }
        }
    }

    val listState = rememberLazyListState()

    Column(modifier = modifier.fillMaxSize().background(bg)) {
        DashboardTopBar(
            isTracking = isTracking,
            trackBg = trackBg,
            textPrimary = textPrimary,
            textSecondary = textSecondary,
        )

        if (!hasUsagePermission) {
            UsageAccessBanner(context, trackBg, textPrimary, textSecondary)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .nestedScroll(collapseConnection)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = bodyTopPad)
            ) {
                // Stats cluster — Reels + Score, with the streak morphing from the
                // full-width bar (below) into a third column (inline) as we collapse.
                item {
                    StatsCluster(
                        fraction = fraction,
                        totalReels = totalReels,
                        reelLimit = reelLimit,
                        todayLog = todayLog,
                        score = brainHealth,
                        streak = currentStreak,
                        onViewStreaks = onViewStreaks,
                        surface = surface,
                        cardBorder = cardBorder,
                        trackBg = trackBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        dark = dark
                    )
                }

                item {
                    TrackingTabs(
                        timeLabel = timeLabel,
                        minuteLimit = minuteLimit,
                        mood = mood,
                        dailyGoalOnTrack = dailyGoalOnTrack,
                        focusShield = focusShield,
                        surface = surface,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        dark = dark
                    )
                }

                item {
                    InsightBar(
                        mood = mood,
                        todayReels = totalReels,
                        yesterdayReels = yesterdayLog?.getTotalReels() ?: 0,
                        dark = dark
                    )
                }

                item {
                    FocusPlan(
                        totalReels = totalReels,
                        reelLimit = reelLimit,
                        timeLabel = timeLabel,
                        minuteLimit = minuteLimit,
                        screenTimeToday = screenTimeToday,
                        onEditPlan = onEditPlan,
                        surface = surface,
                        cardBorder = cardBorder,
                        trackBg = trackBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        dark = dark
                    )
                }

                item {
                    ReminderCard(
                        mood = mood,
                        surface = surface,
                        cardBorder = cardBorder,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary,
                        dark = dark
                    )
                }

                item { Spacer(Modifier.height(16.dp)) }
            }

            // Collapsing hero — opaque; after collapsing it slides up via [headerOffsetPx].
            HeroHeader(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeight)
                    .graphicsLayer { translationY = headerOffsetPx }
                    .background(bg),
                fraction = fraction,
                mood = mood,
                surface = surface,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dark = dark
            )
        }
    }
}

/** Static top bar: brand and tracking badge. */
@Composable
private fun DashboardTopBar(
    isTracking: Boolean,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("✳", color = textPrimary, fontSize = 18.sp)
            Text(
                "FocusCenter",
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                fontSize = 16.sp,
                letterSpacing = (-0.3).sp
            )
        }

        val badgeBg = if (isTracking) WarmAccent else trackBg
        val badgeContent = if (isTracking) Color.White else textSecondary
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(badgeBg)
                .padding(horizontal = 12.dp, vertical = 5.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(badgeContent))
            Text(
                if (isTracking) "TRACKING ON" else "TRACKING OFF",
                color = badgeContent,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.2.sp
            )
        }
    }
}

/**
 * The morphing hero. At [fraction] 0 the Brain is large and centred with the speech bubble
 * below it (Image #8); at 1 it has shrunk to the right of the headline with the bubble tucked
 * under the headline (Image #9). Everything is positioned by interpolation so the collapse is
 * one continuous motion, identical across all five Brain variations.
 */
@Composable
private fun HeroHeader(
    modifier: Modifier,
    fraction: Float,
    mood: DashboardMood,
    surface: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    BoxWithConstraints(modifier = modifier) {
        val side = 20.dp
        val contentW = maxWidth - side * 2

        // Greeting + headline + speech bubble share one column, so the bubble is always laid
        // out directly below the headline and can never overlap it. The column narrows as we
        // collapse to make room for the Brain on the right.
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(start = side, top = 4.dp, end = side)
                .width(lerp(contentW, contentW * 0.45f, fraction))
        ) {
            Text(
                "Good morning! ${mood.greetingEmoji}",
                color = textSecondary,
                fontSize = 15.sp
            )
            Spacer(Modifier.height(4.dp))
            Text(
                mood.headline,
                fontWeight = FontWeight.Bold,
                color = textPrimary,
                fontSize = 27.sp,
                lineHeight = 31.sp,
                letterSpacing = (-0.5).sp
            )
            Spacer(Modifier.height(10.dp))
            SpeechBubble(text = mood.bubble, surface = surface, textPrimary = textPrimary, dark = dark)
        }

        // Brain: full-bleed layer that slides centre-low → right and shrinks (1.5× sizes).
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = side),
            contentAlignment = BiasAlignment(
                horizontalBias = lerpFloat(0f, 1f, fraction),
                verticalBias = lerpFloat(0.34f, 0f, fraction)
            )
        ) {
            MoodCharacter(
                drawableRes = mood.mainRes,
                bobAmount = lerpFloat(0.03f, 0.015f, fraction),
                modifier = Modifier.size(lerp(339.dp, 210.dp, fraction))
            )
        }
    }
}

/**
 * Reels + Score sit side by side. The streak starts as a full-width bar beneath the row and,
 * as [fraction] climbs, fades/shrinks away while an inline streak column grows into the row —
 * so it "rises onto the same axis" as Reels and Score.
 */
@Composable
private fun StatsCluster(
    fraction: Float,
    totalReels: Int,
    reelLimit: Int,
    todayLog: com.example.brainrottracker.data.local.db.entity.DailyLog?,
    score: Int,
    streak: Int,
    onViewStreaks: () -> Unit,
    surface: Color,
    cardBorder: Color,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 4.dp, bottom = 12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(IntrinsicSize.Min),
            horizontalArrangement = Arrangement.spacedBy(lerp(12.dp, 8.dp, fraction))
        ) {
            ReelsCard(
                totalReels = totalReels,
                reelLimit = reelLimit,
                todayLog = todayLog,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                surface = surface,
                cardBorder = cardBorder,
                trackBg = trackBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dark = dark
            )
            ScoreRing(
                score = score,
                modifier = Modifier.weight(1f).fillMaxHeight(),
                surface = surface,
                cardBorder = cardBorder,
                trackBg = trackBg,
                textSecondary = textSecondary,
                dark = dark
            )
            // Inline streak column — width grows with the collapse.
            if (fraction > 0.02f) {
                InlineStreak(
                    streak = streak,
                    onClick = onViewStreaks,
                    modifier = Modifier
                        .weight(fraction)
                        .fillMaxHeight()
                        .graphicsLayer { alpha = fraction }
                        .clipToBounds(),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
            }
        }

        // Full-width streak bar — collapses (height + fade) as the inline one appears.
        val barAlpha = (1f - fraction * 1.5f).coerceIn(0f, 1f)
        if (barAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lerp(12.dp, 0.dp, fraction))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(lerp(74.dp, 0.dp, (fraction * 1.5f).coerceAtMost(1f)))
                    .clipToBounds()
                    .graphicsLayer { alpha = barAlpha }
            ) {
                StreakBar(
                    streak = streak,
                    onClick = onViewStreaks,
                    modifier = Modifier.fillMaxWidth().height(74.dp),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
            }
        }
    }
}

/** "Reels Scrolled Today" card: count, progress toward the limit, and used platforms. */
@Composable
private fun ReelsCard(
    totalReels: Int,
    reelLimit: Int,
    todayLog: com.example.brainrottracker.data.local.db.entity.DailyLog?,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    val ratio = if (reelLimit > 0) totalReels / reelLimit.toFloat() else 0f
    val fillColor = when {
        ratio < 0.75f -> WarmGrantedGreen
        ratio < 1f -> DashAmber
        else -> WarmError
    }
    val usedPlatforms = Platform.entries.mapNotNull { p ->
        val count = todayLog?.getReelsForPlatform(p) ?: 0
        if (count > 0) p to count else null
    }
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .padding(14.dp)
    ) {
        Text("Reels Scrolled Today", color = textSecondary, fontSize = 12.sp, lineHeight = 15.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            "$totalReels",
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            fontSize = 36.sp,
            lineHeight = 38.sp,
            letterSpacing = (-1).sp
        )
        Text("/ $reelLimit videos", color = textSecondary, fontSize = 11.sp)
        Spacer(Modifier.height(8.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(CircleShape)
                .background(trackBg)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(ratio.coerceIn(0f, 1f))
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(fillColor)
            )
        }
        Spacer(Modifier.height(10.dp))
        if (usedPlatforms.isEmpty()) {
            Text("No reels yet 🧘", color = textSecondary, fontSize = 12.sp)
        } else {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                usedPlatforms.take(2).forEach { (platform, count) ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformLogo(platform = platform, modifier = Modifier.size(15.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("$count", fontWeight = FontWeight.SemiBold, color = textPrimary, fontSize = 13.sp)
                    }
                }
            }
        }
    }
}

/** Brain-health score ring, colored by tier (HEALTHY / MODERATE / POOR / VERY POOR). */
@Composable
private fun ScoreRing(
    score: Int,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    trackBg: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    val (label, ringColor) = when {
        score >= 75 -> "HEALTHY" to WarmGrantedGreen
        score >= 50 -> "MODERATE" to DashAmber
        score >= 25 -> "POOR" to WarmError
        else -> "VERY POOR" to WarmError
    }
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .padding(14.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(modifier = Modifier.size(104.dp), contentAlignment = Alignment.Center) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 9.dp.toPx()
                val inset = stroke / 2f + 2.dp.toPx()
                val topLeft = Offset(inset, inset)
                val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                drawArc(
                    color = trackBg,
                    startAngle = 0f, sweepAngle = 360f, useCenter = false,
                    style = Stroke(width = stroke), topLeft = topLeft, size = arcSize
                )
                if (score > 0) {
                    drawArc(
                        color = ringColor,
                        startAngle = -90f, sweepAngle = 360f * score / 100f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("$score", fontWeight = FontWeight.Bold, color = ringColor, fontSize = 30.sp, lineHeight = 32.sp)
                Text(label, fontWeight = FontWeight.Medium, color = textSecondary, fontSize = 9.sp, letterSpacing = 1.sp)
            }
        }
    }
}

/** Compact streak column shown inside the stats row once collapsed (Image #9). */
@Composable
private fun InlineStreak(
    streak: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    val title = if (streak > 0) "$streak day streak!" else "No streak"
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("🔥", fontSize = 15.sp)
            Text(title, fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 13.sp, lineHeight = 16.sp, maxLines = 2)
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text("View Streaks", color = WarmAccent, fontSize = 12.sp, fontWeight = FontWeight.Medium, maxLines = 1)
            Text("›", color = WarmAccent, fontSize = 14.sp)
        }
    }
}

/** Full-width streak status bar shown below the row when expanded (Image #8). */
@Composable
private fun StreakBar(
    streak: Int,
    onClick: () -> Unit,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    val (title, sub) = when {
        streak <= 0 -> "No streak yet" to "Stay under your limits today to start one."
        streak == 1 -> "You are on a 1-day streak!" to "Keep protecting your focus."
        else -> "You are on a $streak-day streak!" to "You're building an amazing habit."
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Text("🔥", fontSize = 24.sp)
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.SemiBold, color = textPrimary, fontSize = 15.sp)
            Text(sub, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }
        Text("›", color = textSecondary, fontSize = 22.sp)
    }
}

/** Tracking tabs — Time / Daily Goals / Focus Shield (Focus Score intentionally removed). */
@Composable
private fun TrackingTabs(
    timeLabel: String,
    minuteLimit: Int,
    mood: DashboardMood,
    dailyGoalOnTrack: Boolean,
    focusShield: FocusShield,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .padding(vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TrackingTab("⏱️", "Time Today", timeLabel, textPrimary, "/ ${minuteLimit}m", textPrimary, textSecondary)
        TabDivider(cardBorder)
        TrackingTab(
            "🎯", "Daily Goal", mood.goalLabel,
            if (dailyGoalOnTrack) WarmGoalBlue else WarmError, mood.goalSub, textPrimary, textSecondary
        )
        TabDivider(cardBorder)
        TrackingTab(
            "🛡️", "Focus Shield", focusShield.status.label,
            focusShield.status.color, focusShield.status.tabSubtitle, textPrimary, textSecondary
        )
    }
}

@Composable
private fun RowScope.TrackingTab(
    icon: String,
    title: String,
    value: String,
    valueColor: Color,
    sub: String,
    textPrimary: Color,
    textSecondary: Color,
) {
    Column(
        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(icon, fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(title, color = textSecondary, fontSize = 11.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        Text(value, color = valueColor, fontSize = 15.sp, fontWeight = FontWeight.Bold, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(sub, color = textSecondary, fontSize = 10.sp, lineHeight = 13.sp, maxLines = 2)
    }
}

@Composable
private fun TabDivider(color: Color) {
    Box(Modifier.height(48.dp).width(1.dp).background(color))
}

/** Insight bar: today-vs-yesterday comparison plus the mood's encouragement and trend art. */
@Composable
private fun InsightBar(
    mood: DashboardMood,
    todayReels: Int,
    yesterdayReels: Int,
    dark: Boolean,
) {
    val bg = if (dark) WarmInsightBgDark else WarmInsightBg
    val accent = if (dark) WarmInsightAccentDark else WarmInsightAccent
    val bodyColor = if (dark) WarmText else WarmLightText

    val comparison = if (yesterdayReels > 0) {
        val diff = todayReels - yesterdayReels
        val pct = abs(diff) * 100 / yesterdayReels
        when {
            diff < 0 -> "You watched $pct% fewer videos compared to yesterday."
            diff > 0 -> "You watched $pct% more videos compared to yesterday."
            else -> "You watched about the same as yesterday."
        }
    } else {
        "No comparison yet — today is building your baseline."
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(bg),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("💡", fontSize = 14.sp)
                Text("Insight", color = accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(comparison, color = bodyColor, fontSize = 14.sp, lineHeight = 19.sp)
            Spacer(Modifier.height(6.dp))
            Text(mood.insightTail, color = accent, fontSize = 13.sp, fontWeight = FontWeight.Medium, lineHeight = 18.sp)
        }
        // Edge-to-edge character: end padding insets it from the right so the whole brain
        // stays visible, while the scale still lets it bleed top/bottom (clipped by the card).
        MoodCharacter(
            drawableRes = mood.insightRes,
            bobAmount = 0.02f,
            modifier = Modifier
                .fillMaxHeight()
                .width(150.dp)
                .padding(end = 22.dp)
                .graphicsLayer { scaleX = 1.12f; scaleY = 1.12f }
        )
    }
}

/** "Today's Focus Plan" — the active video and time limits; rows open Settings. */
@Composable
private fun FocusPlan(
    totalReels: Int,
    reelLimit: Int,
    timeLabel: String,
    minuteLimit: Int,
    screenTimeToday: Int,
    onEditPlan: () -> Unit,
    surface: Color,
    cardBorder: Color,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    val reelOver = reelLimit in 1..totalReels
    val timeOver = minuteLimit in 1..screenTimeToday
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Today's Focus Plan", fontWeight = FontWeight.SemiBold, color = textPrimary, fontSize = 16.sp)
            Row(
                modifier = Modifier.clip(RoundedCornerShape(6.dp)).clickable(onClick = onEditPlan)
                    .padding(horizontal = 4.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text("Edit Plan", color = WarmAccent, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                Text("›", color = WarmAccent, fontSize = 16.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        FocusPlanRow(
            "🎯", "Limit: $reelLimit videos", "Daily video limit", "$totalReels", " / $reelLimit",
            reelOver, onEditPlan, trackBg, textPrimary, textSecondary
        )
        Spacer(Modifier.height(10.dp))
        FocusPlanRow(
            "⏳", "Limit: $minuteLimit min", "Daily time limit", timeLabel, " / ${minuteLimit}m",
            timeOver, onEditPlan, trackBg, textPrimary, textSecondary
        )
    }
}

@Composable
private fun FocusPlanRow(
    icon: String,
    title: String,
    subtitle: String,
    valueStart: String,
    valueEnd: String,
    over: Boolean,
    onClick: () -> Unit,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
) {
    Row(
        modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(RoundedCornerShape(10.dp)).background(trackBg),
            contentAlignment = Alignment.Center
        ) { Text(icon, fontSize = 18.sp) }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontWeight = FontWeight.Medium, color = textPrimary, fontSize = 14.sp)
            Text(subtitle, color = textSecondary, fontSize = 12.sp)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(valueStart, fontWeight = FontWeight.SemiBold, color = if (over) WarmError else WarmAccent, fontSize = 14.sp)
            Text(valueEnd, color = textSecondary, fontSize = 14.sp)
            Spacer(Modifier.width(6.dp))
            Text("›", color = textSecondary, fontSize = 16.sp)
        }
    }
}

/** Reminder card: the mood's reminder copy alongside its dynamic Brain character. */
@Composable
private fun ReminderCard(
    mood: DashboardMood,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 12.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp)) else Modifier),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f).padding(start = 16.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(mood.reminderEmoji, fontSize = 14.sp)
                Text(mood.reminderTitle, color = mood.accent, fontSize = 15.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(6.dp))
            Text(mood.reminderText, color = textSecondary, fontSize = 13.sp, lineHeight = 19.sp)
        }
        // Edge-to-edge character: the short reminder card means only a tiny scale, so the
        // whole brain stays visible while still reaching the card's top and bottom.
        MoodCharacter(
            drawableRes = mood.reminderRes,
            bobAmount = 0.02f,
            modifier = Modifier
                .fillMaxHeight()
                .width(132.dp)
                .graphicsLayer { scaleX = 1.04f; scaleY = 1.04f }
        )
    }
}

/** Usage-access prompt shown under the top bar when screen-time permission is missing. */
@Composable
private fun UsageAccessBanner(
    context: android.content.Context,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(trackBg)
            .clickable { context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("Grant Usage Access", fontWeight = FontWeight.Medium, color = textPrimary, fontSize = 14.sp)
            Text("Required to show screen time", color = textSecondary, fontSize = 12.sp)
        }
        Text("→", color = WarmAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

/** A rounded message bubble with an upward tail, like a comic speech balloon. */
@Composable
private fun SpeechBubble(
    text: String,
    surface: Color,
    textPrimary: Color,
    dark: Boolean
) {
    val bubbleColor = if (dark) surface else Color.White
    Column {
        Canvas(
            modifier = Modifier
                .padding(start = 22.dp)
                .size(width = 16.dp, height = 8.dp)
        ) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(size.width / 2f, 0f)
                lineTo(size.width, size.height)
                lineTo(0f, size.height)
                close()
            }
            drawPath(path, bubbleColor)
        }
        Box(
            modifier = Modifier
                .widthIn(max = 250.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(bubbleColor)
                .padding(horizontal = 14.dp, vertical = 10.dp)
        ) {
            Text(text, color = textPrimary, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium)
        }
    }
}
