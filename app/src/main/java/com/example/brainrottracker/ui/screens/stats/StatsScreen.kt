package com.example.brainrottracker.ui.screens.stats

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.outlined.AccessTime
import androidx.compose.material.icons.outlined.PlayCircle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.theme.InstagramPink
import com.example.brainrottracker.theme.PrimaryCyan
import com.example.brainrottracker.theme.SnapchatYellow
import com.example.brainrottracker.theme.SuccessGreen
import com.example.brainrottracker.theme.TextSecondary
import com.example.brainrottracker.theme.TextTertiary
import com.example.brainrottracker.theme.TikTokCyan
import com.example.brainrottracker.theme.YouTubeRed
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

@Composable
fun StatsScreen(
    modifier: Modifier = Modifier,
    viewModel: StatsViewModel = viewModel(),
    onBackClick: () -> Unit = {}
) {
    val stats by viewModel.weeklyStats.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF0A0F2C)) // React screen background #0A0F2C
    ) {
        // 1. Header Row matching React structure
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF070B22)) // Header background #070B22
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Circular Back button
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.1f))
                    .clickable { onBackClick() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Back",
                    tint = Color.White,
                    modifier = Modifier.size(20.dp)
                )
            }

            Text(
                text = "Stats & Reports",
                style = MaterialTheme.typography.titleMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 18.sp
                ),
                color = Color.White
            )

            // Balance placeholder
            Spacer(modifier = Modifier.size(36.dp))
        }

        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 2. Productivity score circle gauge
            item {
                Spacer(modifier = Modifier.height(8.dp))
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier.size(208.dp) // size-52 in Tailwind = 208dp
                    ) {
                        val score = stats.productivityScore
                        
                        Canvas(modifier = Modifier.size(208.dp)) {
                            // Background track circle
                            drawArc(
                                color = Color(0xFF1E244D), // oklch(0.3 0.05 264)
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Foreground progress arc with linear gradient
                            drawArc(
                                brush = Brush.linearGradient(
                                    colors = listOf(
                                        Color(0xFF2B7FFF), // oklch(0.623 0.214 259.815)
                                        Color(0xFF25F3ED)  // oklch(0.828 0.189 84.429)
                                    )
                                ),
                                startAngle = -90f,
                                sweepAngle = 360f * (score / 100f),
                                useCenter = false,
                                style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "$score",
                                style = MaterialTheme.typography.displayMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 56.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Productivity Score",
                                style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Medium),
                                color = Color(0xFF9EBEFE), // light blue
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }

            // 3. Grid of two summary cards (Total Videos & Time This Week)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Total Videos Card
                    ReactStatsCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.PlayCircle,
                            contentDescription = "Videos icon",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${stats.totalReels}",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Total Videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9EBEFE)
                        )
                    }

                    // Time This Week Card
                    ReactStatsCard(
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = androidx.compose.material.icons.Icons.Outlined.AccessTime,
                            contentDescription = "Time icon",
                            tint = Color(0xFF60A5FA),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        val h = stats.totalMinutes / 60
                        val m = stats.totalMinutes % 60
                        Text(
                            text = if (h > 0) "${h}h ${m}m" else "${m}m",
                            style = MaterialTheme.typography.headlineMedium.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 32.sp
                            ),
                            color = Color.White
                        )
                        Text(
                            text = "Time This Week",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF9EBEFE)
                        )
                    }
                }
            }

            // 4. Weekly Breakdown Card
            item {
                ReactStatsCard(modifier = Modifier.fillMaxWidth()) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Text(
                            text = "Weekly Breakdown",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )

                        // Platform Legends ordered TikTok, YouTube, Instagram, Snapchat
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            LegendItem(color = TikTokCyan, label = "TikTok")
                            LegendItem(color = YouTubeRed, label = "YouTube")
                            LegendItem(color = InstagramPink, label = "Instagram")
                            LegendItem(color = SnapchatYellow, label = "Snapchat")
                        }

                        // Contiguous Stacked Weekly Chart
                        WeeklyBarChartStacked(
                            dailyLogs = stats.dailyLogs,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(176.dp) // h-44 in Tailwind = 176dp
                        )
                    }
                }
            }

            // 5. Best/Worst Day Row
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Best Day Card - Dark emerald green
                    ReactStatsCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0xFF0F3827),
                        borderColor = Color(0x4D10B981) // border-emerald-500/30
                    ) {
                        Text("🏆", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Best Day",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7F3D0).copy(alpha = 0.8f)
                        )
                        val best = stats.bestDay
                        Text(
                            text = getDayName(best?.date),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (best != null) "${best.getTotalReels()} videos" else "0 videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFA7F3D0)
                        )
                    }

                    // Worst Day Card - Dark crimson red
                    ReactStatsCard(
                        modifier = Modifier.weight(1f),
                        backgroundColor = Color(0xFF3F1916),
                        borderColor = Color(0x4DEF4444) // border-red-500/30
                    ) {
                        Text("🔥", fontSize = 24.sp)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Worst Day",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFECACA).copy(alpha = 0.8f)
                        )
                        val worst = stats.worstDay
                        Text(
                            text = getDayName(worst?.date),
                            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = if (worst != null) "${worst.getTotalReels()} videos" else "0 videos",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFFECACA)
                        )
                    }
                }
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
fun ReactStatsCard(
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color(0xFF111740),
    borderColor: Color = Color.White.copy(alpha = 0.05f),
    content: @Composable ColumnScope.() -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(12.dp),
        color = backgroundColor,
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            content()
        }
    }
}

@Composable
fun LegendItem(color: Color, label: String) {
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
        Text(
            text = label,
            fontSize = 12.sp,
            color = Color(0xFFBFDBFE) // text-blue-200
        )
    }
}

@Composable
fun WeeklyBarChartStacked(
    dailyLogs: List<com.example.brainrottracker.data.local.db.entity.DailyLog>,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val days = (6 downTo 0).map { today.minusDays(it.toLong()) }
    val logMap = dailyLogs.associateBy { it.date }

    // Platform Colors in display order: TikTok, YouTube, Instagram, Snapchat
    val platformColors = listOf(TikTokCyan, YouTubeRed, InstagramPink, SnapchatYellow)

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            val barWidth = size.width / 7 * 0.45f
            val spacing = size.width / 7
            val maxReels = days.maxOfOrNull { day ->
                val log = logMap[day.toString()]
                log?.getTotalReels() ?: 0
            }?.coerceAtLeast(10) ?: 10

            days.forEachIndexed { index, day ->
                val log = logMap[day.toString()]
                // Display segments in order: TikTok, YouTube, Instagram, Snapchat
                val platformCounts = listOf(
                    log?.tiktokVideos ?: 0,
                    log?.youtubeShorts ?: 0,
                    log?.instagramReels ?: 0,
                    log?.snapchatSpotlights ?: 0
                )
                
                val totalForDay = platformCounts.sum()
                if (totalForDay > 0) {
                    val xPos = index * spacing + (spacing - barWidth) / 2
                    var currentY = size.height

                    // Draw segments stacked vertically
                    platformCounts.forEachIndexed { platformIndex, count ->
                        if (count > 0) {
                            val segmentHeight = (count.toFloat() / maxReels) * size.height * 0.85f
                            currentY -= segmentHeight
                            
                            // Draw each segment. We add a small rounding to make it look contiguous and clean
                            drawRoundRect(
                                color = platformColors[platformIndex],
                                topLeft = Offset(x = xPos, y = currentY),
                                size = Size(barWidth, segmentHeight),
                                cornerRadius = CornerRadius(4f, 4f)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Day labels row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            days.forEach { day ->
                Text(
                    text = day.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                    color = Color(0xFF9EBEFE), // text-blue-300
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

private fun getDayName(dateStr: String?): String {
    if (dateStr == null) return "N/A"
    return try {
        val date = LocalDate.parse(dateStr)
        date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, Locale.getDefault())
    } catch (e: Exception) {
        "N/A"
    }
}
