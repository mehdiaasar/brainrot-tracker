package com.example.brainrottracker.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.R
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.ui.components.BrainMascot
import com.example.brainrottracker.ui.components.PlatformLogo
import com.example.brainrottracker.theme.StatsChartInstagram
import com.example.brainrottracker.theme.StatsChartSnapchat
import com.example.brainrottracker.theme.StatsChartTikTok
import com.example.brainrottracker.theme.StatsChartYouTube
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.rememberIsDark
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmGrantedGreen
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightSurface
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val gaugeBg = if (dark) WarmBackground else WarmLightBorder
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary

    val stats by viewModel.weeklyStats.collectAsState()

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        // Title
        item {
            Text(
                "Stats & Reports",
                fontWeight = FontWeight.Normal,
                color = textPrimary,
                fontSize = 36.sp,
                lineHeight = 41.sp,
                letterSpacing = (-0.5).sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 32.dp, bottom = 24.dp)
            )
        }

        // Productivity gauge card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .then(
                        if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                        else Modifier
                    )
                    .padding(horizontal = 24.dp, vertical = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 200.dp, height = 110.dp),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        val score = stats.productivityScore
                        Canvas(modifier = Modifier.fillMaxSize()) {
                            val strokeWidth = 28.dp.toPx()
                            val cx = size.width / 2f
                            val cy = size.height
                            val radius = cx - strokeWidth / 2f
                            val topLeft = Offset(cx - radius, cy - radius)
                            val arcSize = Size(radius * 2f, radius * 2f)

                            // Full semicircle track
                            drawArc(
                                color = gaugeBg,
                                startAngle = 180f,
                                sweepAngle = 180f,
                                useCenter = false,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                topLeft = topLeft,
                                size = arcSize
                            )
                            // Progress arc
                            if (score > 0) {
                                drawArc(
                                    color = WarmAccent,
                                    startAngle = 180f,
                                    sweepAngle = 180f * score / 100f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Butt),
                                    topLeft = topLeft,
                                    size = arcSize
                                )
                            }
                        }
                        Text(
                            "${stats.productivityScore}",
                            fontWeight = FontWeight.Normal,
                            color = textPrimary,
                            fontSize = 36.sp,
                            lineHeight = 40.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "PRODUCTIVITY SCORE",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )
                }
                Image(
                    painterResource(
                        if (stats.productivityScore >= 50) R.drawable.char_excited
                        else R.drawable.char_overwhelmed
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(96.dp)
                )
                }
            }
        }

        // Weekly bar chart card
        item {
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
                    .padding(24.dp)
            ) {
                Column {
                    Text(
                        "This Week",
                        fontWeight = FontWeight.Medium,
                        color = textPrimary,
                        fontSize = 16.sp,
                        lineHeight = 22.sp
                    )
                    // Legend
                    Row(
                        modifier = Modifier.padding(top = 12.dp, bottom = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        LegendDot(color = StatsChartTikTok, label = "TikTok", textColor = textSecondary)
                        LegendDot(color = StatsChartYouTube, label = "YouTube", textColor = textSecondary)
                        LegendDot(color = StatsChartInstagram, label = "Instagram", textColor = textSecondary)
                        LegendDot(color = StatsChartSnapchat, label = "Snapchat", textColor = textSecondary)
                    }
                    // Bar chart
                    WarmWeeklyBarChart(
                        dailyLogs = stats.dailyLogs,
                        healthByDate = stats.healthByDate,
                        textSecondary = textSecondary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(186.dp)
                    )
                }
            }
        }

        // Total videos + total time
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                StatSummaryCard(
                    label = "TOTAL VIDEOS",
                    value = "${stats.totalReels}",
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
                val h = stats.totalMinutes / 60
                val m = stats.totalMinutes % 60
                StatSummaryCard(
                    label = "TOTAL TIME",
                    value = if (h > 0) "${h}h ${m}m" else "${m}m",
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
            }
        }

        // Best + Worst day
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                BestWorstCard(
                    label = "Best",
                    labelColor = WarmGrantedGreen,
                    dayName = getDayName(stats.bestDay?.date),
                    count = "${stats.bestDay?.getTotalReels() ?: 0} videos",
                    charRes = R.drawable.char_proud,
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
                BestWorstCard(
                    label = "Worst",
                    labelColor = WarmError,
                    dayName = getDayName(stats.worstDay?.date),
                    count = "${stats.worstDay?.getTotalReels() ?: 0} videos",
                    charRes = R.drawable.char_overwhelmed,
                    modifier = Modifier.weight(1f),
                    surface = surface,
                    cardBorder = cardBorder,
                    textPrimary = textPrimary,
                    textSecondary = textSecondary,
                    dark = dark
                )
            }
        }

        // Top apps — only platforms actually used this week, ranked
        if (stats.topApps.isNotEmpty()) {
            item {
                Text(
                    "Top apps",
                    fontWeight = FontWeight.Medium,
                    color = textPrimary,
                    fontSize = 18.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 12.dp)
                )
            }
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .then(
                            if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                            else Modifier
                        )
                ) {
                    stats.topApps.forEachIndexed { index, (platform, count) ->
                        if (index > 0) {
                            HorizontalDividerLine(cardBorder)
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            PlatformLogo(platform = platform, modifier = Modifier.size(36.dp))
                            Spacer(Modifier.width(16.dp))
                            Text(
                                platform.displayName,
                                fontWeight = FontWeight.Medium,
                                color = textPrimary,
                                fontSize = 16.sp,
                                modifier = Modifier.weight(1f)
                            )
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

        item { Spacer(Modifier.height(8.dp)) }
    }
}

@Composable
private fun HorizontalDividerLine(color: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .height(1.dp)
            .background(color)
    )
}

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun WarmWeeklyBarChart(
    dailyLogs: List<DailyLog>,
    healthByDate: Map<String, Int>,
    textSecondary: Color,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val logMap = dailyLogs.associateBy { it.date }

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val barWidth = size.width / 7f * 0.55f
            val slotWidth = size.width / 7f
            val maxTotal = days.maxOfOrNull { day ->
                logMap[day.toString()]?.getTotalReels() ?: 0
            }?.coerceAtLeast(1) ?: 1

            days.forEachIndexed { index, day ->
                val log = logMap[day.toString()]
                val tiktok = log?.tiktokVideos ?: 0
                val youtube = log?.youtubeShorts ?: 0
                val instagram = log?.instagramReels ?: 0
                val snapchat = log?.snapchatSpotlights ?: 0
                val total = tiktok + youtube + instagram + snapchat
                if (total == 0) return@forEachIndexed

                val xCenter = index * slotWidth + slotWidth / 2f
                val xLeft = xCenter - barWidth / 2f
                val totalBarH = (total.toFloat() / maxTotal) * size.height
                var currentY = size.height

                // Draw segments bottom → top: Snapchat, Instagram, YouTube, TikTok
                if (snapchat > 0) {
                    val h = (snapchat.toFloat() / total) * totalBarH
                    currentY -= h
                    drawRect(StatsChartSnapchat, Offset(xLeft, currentY), Size(barWidth, h))
                }
                if (instagram > 0) {
                    val h = (instagram.toFloat() / total) * totalBarH
                    currentY -= h
                    drawRect(StatsChartInstagram, Offset(xLeft, currentY), Size(barWidth, h))
                }
                if (youtube > 0) {
                    val h = (youtube.toFloat() / total) * totalBarH
                    currentY -= h
                    drawRect(StatsChartYouTube, Offset(xLeft, currentY), Size(barWidth, h))
                }
                if (tiktok > 0) {
                    val h = (tiktok.toFloat() / total) * totalBarH
                    currentY -= h
                    drawRoundRect(
                        StatsChartTikTok,
                        Offset(xLeft, currentY),
                        Size(barWidth, h),
                        CornerRadius(4f, 4f)
                    )
                }
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                Text(
                    day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = textSecondary,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(6.dp))

        // Per-day brain mascot reflecting that day's brain-health.
        Row(modifier = Modifier.fillMaxWidth()) {
            days.forEach { day ->
                val log = logMap[day.toString()]
                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    if (log != null && log.getTotalReels() > 0) {
                        BrainMascot(
                            health = healthByDate[day.toString()] ?: 100,
                            modifier = Modifier.size(30.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatSummaryCard(
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
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(
                if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(20.dp)
    ) {
        Column {
            Text(
                label,
                color = textSecondary,
                fontSize = 10.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp
            )
            Spacer(Modifier.height(8.dp))
            Text(
                value,
                fontWeight = FontWeight.Normal,
                color = textPrimary,
                fontSize = 26.sp,
                lineHeight = 32.sp,
                letterSpacing = (-0.3).sp
            )
        }
    }
}

@Composable
private fun BestWorstCard(
    label: String,
    labelColor: Color,
    dayName: String,
    count: String,
    charRes: Int,
    modifier: Modifier,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    dark: Boolean
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .then(
                if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                else Modifier
            )
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.Bottom) {
            Column(modifier = Modifier.weight(1f)) {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(labelColor)
                        .padding(horizontal = 12.dp, vertical = 4.dp)
                ) {
                    Text(
                        label.uppercase(),
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )
                }
                Spacer(Modifier.height(12.dp))
                Text(dayName, fontWeight = FontWeight.Medium, color = textPrimary, fontSize = 16.sp, lineHeight = 22.sp)
                Text(count, color = textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
            }
            Image(
                painterResource(charRes),
                contentDescription = null,
                modifier = Modifier.size(56.dp)
            )
        }
    }
}

private fun getDayName(dateStr: String?): String {
    if (dateStr == null) return "N/A"
    return try {
        val date = LocalDate.parse(dateStr)
        date.dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    } catch (_: Exception) { "N/A" }
}
