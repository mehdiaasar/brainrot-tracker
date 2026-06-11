package com.example.brainrottracker.ui.screens.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.service.ReelCounterService
import com.example.brainrottracker.ui.components.BrainMascot
import com.example.brainrottracker.ui.components.PlatformLogo
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.rememberIsDark
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmGrantedGreen
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

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
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
    val brainHealth by viewModel.brainHealth.collectAsState()
    val insightsList by viewModel.insights.collectAsState()
    val screenTimeToday by viewModel.screenTimeToday.collectAsState()

    val lifecycleOwner = LocalLifecycleOwner.current
    var isTracking by remember { mutableStateOf(ReelCounterService.isRunning) }
    var hasUsagePermission by remember { mutableStateOf(com.example.brainrottracker.data.util.ScreenTimeHelper.hasPermission(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isTracking = ReelCounterService.isRunning
                hasUsagePermission = com.example.brainrottracker.data.util.ScreenTimeHelper.hasPermission(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val totalMinutes = screenTimeToday
    val hours = totalMinutes / 60
    val mins = totalMinutes % 60
    val timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
    val totalReels = todayLog?.getTotalReels() ?: 0

    val healthLabel = when {
        brainHealth >= 90 -> "Elite"
        brainHealth >= 75 -> "Healthy"
        brainHealth >= 50 -> "Tired"
        brainHealth >= 25 -> "Overstimulated"
        else -> "Brainrot"
    }

    val currentInsightIndex = remember { mutableStateOf(0) }
    LaunchedEffect(insightsList) { currentInsightIndex.value = 0 }
    val insightText = insightsList.getOrNull(currentInsightIndex.value)
        ?: "You've been mindful today — keep the momentum going."

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        // Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
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
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(badgeBg)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(badgeContent)
                        )
                        Text(
                            if (isTracking) "TRACKING ON" else "TRACKING OFF",
                            color = badgeContent,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp
                        )
                    }
                }
            }
        }

        // Usage Access banner — shown only when permission is missing
        if (!hasUsagePermission) {
            item {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(trackBg)
                        .clickable {
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "Grant Usage Access",
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                            fontSize = 14.sp
                        )
                        Text(
                            "Required to show screen time",
                            color = textSecondary,
                            fontSize = 12.sp
                        )
                    }
                    Text("→", color = WarmAccent, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        // Mascot hero — the brain degrades visually as today's usage climbs
        item {
            MascotHero(
                health = brainHealth,
                totalReels = totalReels,
                todayLog = todayLog,
                surface = surface,
                trackBg = trackBg,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                dark = dark
            )
        }

        // Score circle with a progress ring colored by brain-health tier
        item {
            val healthColor = when {
                brainHealth >= 75 -> WarmGrantedGreen
                brainHealth >= 40 -> WarmAccent
                else -> WarmError
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(180.dp)
                        .clip(CircleShape)
                        .background(surface)
                        .then(
                            if (!dark) Modifier.border(1.dp, cardBorder, CircleShape)
                            else Modifier
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.size(180.dp)) {
                        val stroke = 10.dp.toPx()
                        val inset = stroke / 2f + 6.dp.toPx()
                        val topLeft = Offset(inset, inset)
                        val arcSize = Size(size.width - inset * 2f, size.height - inset * 2f)
                        // Track
                        drawArc(
                            color = trackBg,
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = stroke),
                            topLeft = topLeft,
                            size = arcSize
                        )
                        // Progress
                        if (brainHealth > 0) {
                            drawArc(
                                color = healthColor,
                                startAngle = -90f,
                                sweepAngle = 360f * brainHealth / 100f,
                                useCenter = false,
                                style = Stroke(width = stroke, cap = StrokeCap.Round),
                                topLeft = topLeft,
                                size = arcSize
                            )
                        }
                    }
                    Box(
                        modifier = Modifier
                            .size(140.dp)
                            .clip(CircleShape)
                            .background(if (dark) WarmBackground else WarmLightBorder),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "$brainHealth",
                                fontWeight = FontWeight.Medium,
                                color = healthColor,
                                fontSize = 36.sp,
                                lineHeight = 40.sp,
                                letterSpacing = (-0.5).sp
                            )
                            Text(
                                healthLabel.uppercase(),
                                fontWeight = FontWeight.Medium,
                                color = textSecondary,
                                fontSize = 10.sp,
                                letterSpacing = 1.5.sp
                            )
                        }
                    }
                }
            }
        }

        // Insight card — tap to cycle through all of today's insights
        item {
            val hasMultiple = insightsList.size > 1
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .then(
                        if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        else Modifier
                    )
                    .clickable(enabled = hasMultiple) {
                        currentInsightIndex.value =
                            (currentInsightIndex.value + 1) % insightsList.size
                    }
                    .padding(24.dp)
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .padding(top = 6.dp)
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(WarmAccent)
                        )
                        Text(
                            insightText,
                            fontStyle = FontStyle.Italic,
                            color = textSecondary,
                            fontSize = 15.sp,
                            lineHeight = 24.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (hasMultiple) {
                        Spacer(Modifier.height(16.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            insightsList.indices.forEach { i ->
                                Box(
                                    modifier = Modifier
                                        .size(6.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (i == currentInsightIndex.value) WarmAccent else trackBg
                                        )
                                )
                            }
                        }
                    }
                }
            }
        }

        // Time + Reels stats
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                DashStatCard(
                    label = "TIME TODAY",
                    value = timeLabel,
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
                DashStatCard(
                    label = "REELS WATCHED",
                    value = "$totalReels videos",
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
            }
        }

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun DashStatCard(
    label: String,
    value: String,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(surface)
            .then(
                if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(8.dp))
                else Modifier
            )
            .padding(16.dp)
    ) {
        Column {
            Text(
                value,
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                fontSize = 22.sp,
                letterSpacing = (-0.3).sp,
                lineHeight = 26.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                label,
                fontWeight = FontWeight.Medium,
                color = textSecondary,
                fontSize = 10.sp,
                letterSpacing = 1.5.sp
            )
        }
    }
}

/**
 * The hero block: a speech bubble, the degrading [BrainMascot], the big "reels scrolled
 * today" count, and a compact pill listing only the platforms actually used today.
 */
@Composable
private fun MascotHero(
    health: Int,
    totalReels: Int,
    todayLog: DailyLog?,
    surface: Color,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean
) {
    val message = when {
        health >= 90 -> "Feeling razor sharp! ✨"
        health >= 75 -> "All good up here."
        health >= 50 -> "Getting a little foggy…"
        health >= 25 -> "Ease off the scroll."
        else -> "The damage is real."
    }

    val usedPlatforms = Platform.entries.mapNotNull { p ->
        val count = todayLog?.getReelsForPlatform(p) ?: 0
        if (count > 0) p to count else null
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        SpeechBubble(text = message, surface = surface, textPrimary = textPrimary, dark = dark)

        Spacer(Modifier.height(4.dp))

        BrainMascot(
            health = health,
            modifier = Modifier.size(180.dp)
        )

        Spacer(Modifier.height(12.dp))

        Text(
            "$totalReels",
            fontWeight = FontWeight.Bold,
            color = textPrimary,
            fontSize = 56.sp,
            lineHeight = 60.sp,
            letterSpacing = (-1).sp
        )
        Text(
            "Reels Scrolled Today",
            color = textSecondary,
            fontSize = 15.sp,
            modifier = Modifier.padding(top = 2.dp)
        )

        Spacer(Modifier.height(16.dp))

        if (usedPlatforms.isEmpty()) {
            Text(
                "No reels yet today — nice. 🧘",
                color = textSecondary,
                fontSize = 13.sp
            )
        } else {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(trackBg)
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                usedPlatforms.forEachIndexed { index, (platform, count) ->
                    if (index > 0) {
                        Box(
                            modifier = Modifier
                                .padding(horizontal = 14.dp)
                                .size(width = 1.dp, height = 16.dp)
                                .background(textSecondary.copy(alpha = 0.3f))
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        PlatformLogo(platform = platform, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$count",
                            fontWeight = FontWeight.SemiBold,
                            color = textPrimary,
                            fontSize = 16.sp
                        )
                    }
                }
            }
        }
    }
}

/** A rounded message bubble with a downward tail, like a comic speech balloon. */
@Composable
private fun SpeechBubble(
    text: String,
    surface: Color,
    textPrimary: Color,
    dark: Boolean
) {
    val bubbleColor = if (dark) surface else Color.White
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .widthIn(max = 280.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(bubbleColor)
                .padding(horizontal = 20.dp, vertical = 12.dp)
        ) {
            Text(
                text,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium
            )
        }
        // Tail
        Canvas(modifier = Modifier.size(width = 18.dp, height = 9.dp)) {
            val path = androidx.compose.ui.graphics.Path().apply {
                moveTo(0f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width / 2f, size.height)
                close()
            }
            drawPath(path, bubbleColor)
        }
    }
}
