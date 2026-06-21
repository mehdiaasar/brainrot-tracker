package com.example.brainrottracker.ui.screens.stats

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material3.Icon
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.theme.ChartInstagram
import com.example.brainrottracker.theme.ChartSnapchat
import com.example.brainrottracker.theme.ChartTikTokDark
import com.example.brainrottracker.theme.ChartTikTokLight
import com.example.brainrottracker.theme.ChartYouTube
import com.example.brainrottracker.theme.StatsBestBgDark
import com.example.brainrottracker.theme.StatsBestBgLight
import com.example.brainrottracker.theme.StatsExcellent
import com.example.brainrottracker.theme.StatsCritical
import com.example.brainrottracker.theme.StatsWorstBgDark
import com.example.brainrottracker.theme.StatsWorstBgLight
import com.example.brainrottracker.ui.components.MoodCharacter
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val colors = AppTheme.colors

    val stats by viewModel.weeklyStats.collectAsState()
    val mood = StatsMood.fromScore(stats.productivityScore)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background),
    ) {
        item { Header(onBackClick = onBackClick) }

        item { HeroCard(score = stats.productivityScore, mood = mood) }

        item { ScreenTimeCard(minutesByDate = stats.minutesByDate, mood = mood) }

        item { SummaryRow(stats) }

        item { BestWorstRow(stats) }

        item { FooterCard(mood) }

        item { Spacer(Modifier.height(12.dp)) }
    }
}

/* ───────────────────────────── Header ───────────────────────────── */

@Composable
private fun Header(
    onBackClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    // Title row and subtitle are separate so the pill button aligns with just the title line.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(top = 20.dp, bottom = 18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "Stats & Reports",
                fontWeight = FontWeight.Bold, color = textPrimary,
                fontSize = 26.sp, lineHeight = 30.sp, letterSpacing = (-0.5).sp,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(12.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.DateRange,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = textSecondary,
                )
                Text("This Week", color = textSecondary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(5.dp))
        Text("See how you're growing 🌱", color = textSecondary, fontSize = 14.sp)
    }
}

/* ───────────────────────────── Hero ───────────────────────────── */

@Composable
private fun HeroCard(
    score: Int, mood: StatsMood,
) {
    val colors = AppTheme.colors
    val track = colors.border
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val dark = colors.isDark
    val cardBg = if (dark) mood.heroBgDark else mood.heroBgLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .padding(start = 18.dp, end = 6.dp, top = 20.dp, bottom = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text("Productivity Score", color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                Box(
                    modifier = Modifier.size(17.dp).clip(CircleShape)
                        .border(1.2.dp, textSecondary, CircleShape),
                    contentAlignment = Alignment.Center
                ) { Text("i", color = textSecondary, fontSize = 10.sp) }
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    "$score", fontWeight = FontWeight.Bold, color = mood.accent,
                    fontSize = 56.sp, lineHeight = 58.sp, letterSpacing = (-2).sp
                )
                Text(
                    " /100", color = textSecondary, fontSize = 19.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(bottom = 9.dp)
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(mood.headline, fontWeight = FontWeight.Bold, color = mood.accent, fontSize = 17.sp, lineHeight = 22.sp)
            Spacer(Modifier.height(6.dp))
            Text(mood.subline1, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            Text(mood.subline2, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
        }

        // The arc is pinned near the top of a tall box; the (large) brain sits bottom-aligned
        // beneath it, so its head stays below the semi-ring instead of poking through.
        Box(
            modifier = Modifier.size(width = 158.dp, height = 178.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val cx = size.width / 2f
                val radius = cx - stroke / 2f
                val apexY = 8.dp.toPx()
                val topLeft = Offset(cx - radius, apexY)
                val arcSize = Size(radius * 2f, radius * 2f)
                drawArc(
                    color = track, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                    style = Stroke(width = stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize
                )
                if (score > 0) {
                    drawArc(
                        color = mood.accent, startAngle = 180f, sweepAngle = 180f * score / 100f, useCenter = false,
                        style = Stroke(width = stroke, cap = StrokeCap.Round), topLeft = topLeft, size = arcSize
                    )
                }
            }
            MoodCharacter(drawableRes = mood.heroRes, bobAmount = 0.025f, modifier = Modifier.size(150.dp))
        }
    }
}

/* ───────────────────────── Screen-time chart ───────────────────────── */

@Composable
private fun ScreenTimeCard(
    minutesByDate: Map<String, Map<Platform, Int>>, mood: StatsMood,
) {
    val colors = AppTheme.colors
    val surface = colors.surface
    val cardBorder = colors.border
    val track = colors.border
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val dark = colors.isDark
    val tiktok = if (dark) ChartTikTokDark else ChartTikTokLight
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(18.dp)) else Modifier)
            .padding(20.dp)
    ) {
        Text("Screen Time Breakdown", fontWeight = FontWeight.SemiBold, color = textPrimary, fontSize = 16.sp)
        Row(
            modifier = Modifier.padding(top = 14.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(13.dp)
        ) {
            LegendDot(tiktok, "TikTok", textSecondary)
            LegendDot(ChartYouTube, "YouTube", textSecondary)
            LegendDot(ChartInstagram, "Instagram", textSecondary)
            LegendDot(ChartSnapchat, "Snapchat", textSecondary)
        }
        BarChart(
            minutesByDate = minutesByDate, mood = mood, tiktok = tiktok, track = track,
            textSecondary = textSecondary,
            modifier = Modifier.fillMaxWidth().height(196.dp)
        )
    }
}

@Composable
private fun BarChart(
    minutesByDate: Map<String, Map<Platform, Int>>, mood: StatsMood,
    tiktok: Color, track: Color, textSecondary: Color,
    modifier: Modifier = Modifier,
) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val totals = days.map { d -> minutesByDate[d.toString()]?.values?.sum() ?: 0 }
    val rawMax = (totals.maxOrNull() ?: 0).coerceAtLeast(1)
    val axisMax = (ceil(rawMax / 20f).toInt() * 20).coerceAtLeast(20)
    val axisLabels = listOf(axisMax, axisMax * 3 / 4, axisMax / 2, axisMax / 4, 0)

    val axisWidth = 34.dp

    Column(modifier = modifier) {
        Row(modifier = Modifier.fillMaxWidth().weight(1f)) {
            Column(
                modifier = Modifier.fillMaxHeight().width(axisWidth).padding(end = 6.dp),
                verticalArrangement = Arrangement.SpaceBetween,
                horizontalAlignment = Alignment.End
            ) {
                axisLabels.forEach { v -> Text("${v}m", color = textSecondary, fontSize = 10.sp, lineHeight = 11.sp) }
            }
            // Brain sits to the right of the bars (in a ~68dp gutter), with only its hand
            // (~16dp) overlapping the last bar — mirroring the mock. The Canvas is padded so
            // bars end before the gutter; the brain is at BottomEnd of the full-width Box.
            val brainGutter = 68.dp
            Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                Canvas(modifier = Modifier.padding(end = brainGutter).fillMaxSize()) {
                    for (i in 0..4) {
                        val y = size.height * i / 4f
                        drawRect(track, Offset(0f, y), Size(size.width, 1.dp.toPx()))
                    }
                    val slot = size.width / 7f
                    val barW = slot * 0.5f
                    // Stacked bottom → top: TikTok, YouTube, Instagram, Snapchat.
                    val order = listOf(
                        Platform.TIKTOK to tiktok,
                        Platform.YOUTUBE to ChartYouTube,
                        Platform.INSTAGRAM to ChartInstagram,
                        Platform.SNAPCHAT to ChartSnapchat,
                    )
                    days.forEachIndexed { index, day ->
                        val m = minutesByDate[day.toString()] ?: emptyMap()
                        val total = m.values.sum()
                        if (total == 0) return@forEachIndexed
                        val xLeft = index * slot + (slot - barW) / 2f
                        val fullH = (total.toFloat() / axisMax) * size.height
                        val topPlatform = order.lastOrNull { (p, _) -> (m[p] ?: 0) > 0 }?.first
                        var y = size.height
                        order.forEach { (p, c) ->
                            val v = m[p] ?: 0
                            if (v <= 0) return@forEach
                            val h = (v.toFloat() / total) * fullH
                            y -= h
                            if (p == topPlatform) drawRoundRect(c, Offset(xLeft, y), Size(barW, h), CornerRadius(6f, 6f))
                            else drawRect(c, Offset(xLeft, y), Size(barW, h))
                        }
                    }
                }
                MoodCharacter(
                    drawableRes = mood.peekRes, bobAmount = 0.03f,
                    modifier = Modifier.align(Alignment.BottomEnd).size(84.dp)
                )
            }
        }

        Spacer(Modifier.height(8.dp))

        Row(modifier = Modifier.fillMaxWidth()) {
            Spacer(Modifier.width(axisWidth))
            // Pad by the same gutter so day labels align under their bars, not under the brain.
            Row(modifier = Modifier.weight(1f).padding(end = 68.dp)) {
                days.forEachIndexed { index, day ->
                    val isToday = index == days.lastIndex
                    Text(
                        day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
                        fontSize = 11.sp,
                        fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                        color = if (isToday) mood.accent else textSecondary,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

/* ───────────────────────── Summary cards ───────────────────────── */

@Composable
private fun SummaryRow(stats: WeeklyStats) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        SummaryCard(
            "▶", colors.accent, "Total Videos", "${stats.totalReels}", stats.reelsDeltaPct,
            Modifier.weight(1f)
        )
        SummaryCard(
            "🕐", colors.settingsPurple, "Total Time", fmtHm(stats.totalMinutes), stats.timeDeltaPct,
            Modifier.weight(1f)
        )
        SummaryCard(
            "◎", colors.success, "Daily Average", fmtHm(stats.dailyAverageMinutes), stats.avgDeltaPct,
            Modifier.weight(1f)
        )
    }
}

@Composable
private fun SummaryCard(
    icon: String, iconTint: Color, label: String, value: String, deltaPct: Int?,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val surface = colors.surface
    val cardBorder = colors.border
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val dark = colors.isDark
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(surface)
            .then(if (!dark) Modifier.border(1.dp, cardBorder, RoundedCornerShape(14.dp)) else Modifier)
            .padding(horizontal = 12.dp, vertical = 14.dp)
    ) {
        Box(
            modifier = Modifier.size(30.dp).clip(CircleShape).background(iconTint.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Text(icon, color = iconTint, fontSize = 14.sp) }
        Spacer(Modifier.height(10.dp))
        Text(label, color = textSecondary, fontSize = 11.sp, lineHeight = 13.sp, maxLines = 1)
        Spacer(Modifier.height(3.dp))
        Text(value, fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 17.sp, lineHeight = 20.sp, maxLines = 1)
        Spacer(Modifier.height(6.dp))
        if (deltaPct == null) {
            Text("vs last week —", color = textSecondary, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
        } else {
            val good = deltaPct < 0
            val arrow = if (deltaPct > 0) "↑" else if (deltaPct < 0) "↓" else "→"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("vs last wk ", color = textSecondary, fontSize = 9.sp, lineHeight = 11.sp, maxLines = 1)
                Text(
                    "$arrow${abs(deltaPct)}%",
                    color = if (good) StatsExcellent else StatsCritical,
                    fontSize = 9.sp, fontWeight = FontWeight.SemiBold, lineHeight = 11.sp, maxLines = 1
                )
            }
        }
    }
}

/* ───────────────────────── Best / Worst day ───────────────────────── */

@Composable
private fun BestWorstRow(stats: WeeklyStats) {
    val colors = AppTheme.colors
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        DayCard(
            badge = "BEST DAY", badgeColor = colors.success,
            bgLight = StatsBestBgLight, bgDark = StatsBestBgDark,
            dayName = dayName(stats.bestDay?.date),
            videos = stats.bestDay?.getTotalReels() ?: 0, minutes = stats.bestDayMinutes,
            charRes = com.example.brainrottracker.R.drawable.stats_best,
            modifier = Modifier.weight(1f)
        )
        DayCard(
            badge = "WORST DAY", badgeColor = colors.error,
            bgLight = StatsWorstBgLight, bgDark = StatsWorstBgDark,
            dayName = dayName(stats.worstDay?.date),
            videos = stats.worstDay?.getTotalReels() ?: 0, minutes = stats.worstDayMinutes,
            charRes = com.example.brainrottracker.R.drawable.stats_worst,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DayCard(
    badge: String, badgeColor: Color, bgLight: Color, bgDark: Color,
    dayName: String, videos: Int, minutes: Int, charRes: Int,
    modifier: Modifier,
) {
    val colors = AppTheme.colors
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary
    val dark = colors.isDark
    // Layered so the brain can be large in the bottom-right corner (bleeding past the text)
    // without colliding with the badge — the text is width-constrained to the left.
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (dark) bgDark else bgLight)
            .heightIn(min = 124.dp)
    ) {
        MoodCharacter(
            drawableRes = charRes, bobAmount = 0.025f,
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 4.dp, bottom = 4.dp).size(96.dp)
        )
        Column(modifier = Modifier.align(Alignment.TopStart).fillMaxWidth(0.62f).padding(14.dp)) {
            Box(
                modifier = Modifier.clip(CircleShape).background(badgeColor).padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    badge, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = 0.3.sp, maxLines = 1, softWrap = false
                )
            }
            Spacer(Modifier.height(10.dp))
            Text(dayName, fontWeight = FontWeight.Bold, color = textPrimary, fontSize = 16.sp, lineHeight = 20.sp)
            Text("$videos videos", color = textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
            Text(fmtHm(minutes), color = textSecondary, fontSize = 12.sp, lineHeight = 17.sp)
        }
    }
}

/* ───────────────────────── Footer card ───────────────────────── */

@Composable
private fun FooterCard(mood: StatsMood) {
    val colors = AppTheme.colors
    val textSecondary = colors.textSecondary
    val dark = colors.isDark
    val cardBg = if (dark) mood.footerBgDark else mood.footerBgLight
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
            .padding(bottom = 16.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(cardBg)
            .padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .padding(start = 16.dp)
                .size(46.dp)
                .clip(CircleShape)
                .background(mood.footerAccent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center
        ) { Text(if (mood.footerIconStar) "★" else "⚠", color = mood.footerAccent, fontSize = 20.sp) }
        Column(modifier = Modifier.weight(1f).padding(start = 14.dp, top = 16.dp, bottom = 16.dp, end = 4.dp)) {
            Text(mood.footerTitle, color = mood.footerAccent, fontSize = 15.sp, fontWeight = FontWeight.Bold, lineHeight = 20.sp)
            Spacer(Modifier.height(4.dp))
            Text(mood.footerBody, color = textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
            if (mood.footerTail != null) {
                Spacer(Modifier.height(4.dp))
                Text(mood.footerTail, color = mood.footerAccent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, lineHeight = 18.sp)
            }
        }
        MoodCharacter(drawableRes = mood.footerRes, bobAmount = 0.025f, modifier = Modifier.size(116.dp))
    }
}

/* ───────────────────────── helpers ───────────────────────── */

@Composable
private fun LegendDot(color: Color, label: String, textColor: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
        Box(modifier = Modifier.size(9.dp).clip(CircleShape).background(color))
        Text(label, color = textColor, fontSize = 12.sp, fontWeight = FontWeight.Medium)
    }
}

private fun fmtHm(minutes: Int): String {
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m}m"
}

private fun dayName(dateStr: String?): String {
    if (dateStr == null) return "—"
    return try {
        LocalDate.parse(dateStr).dayOfWeek.getDisplayName(TextStyle.FULL, Locale.getDefault())
    } catch (_: Exception) { "—" }
}
