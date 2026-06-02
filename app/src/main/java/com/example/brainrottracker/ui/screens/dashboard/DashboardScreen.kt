package com.example.brainrottracker.ui.screens.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.Adjust
import androidx.compose.material.icons.outlined.FormatQuote
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.theme.BrainCritical
import com.example.brainrottracker.theme.BrainHealthy
import com.example.brainrottracker.theme.BrainOverstimulated
import com.example.brainrottracker.theme.BrainRot
import com.example.brainrottracker.theme.BrainTired
import com.example.brainrottracker.theme.InstagramPink
import com.example.brainrottracker.theme.PrimaryCyan
import com.example.brainrottracker.theme.SnapchatYellow
import com.example.brainrottracker.theme.TikTokCyan
import com.example.brainrottracker.theme.YouTubeRed
import com.example.brainrottracker.ui.components.GlassCard
import com.example.brainrottracker.ui.components.PlatformCard

@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    viewModel: DashboardViewModel = viewModel()
) {
    val todayLog by viewModel.todayLog.collectAsState()
    val brainHealth by viewModel.brainHealth.collectAsState()
    val limits by viewModel.limits.collectAsState()
    val insightsList by viewModel.insights.collectAsState()

    val log = todayLog
    val totalReels = log?.getTotalReels() ?: 0
    val totalMinutes = log?.getTotalMinutes() ?: 0

    // Soft ambient backdrop animation blobs
    val infiniteTransition = rememberInfiniteTransition(label = "ambientAnim")
    val blob1X by infiniteTransition.animateFloat(
        initialValue = 0.15f, targetValue = 0.35f,
        animationSpec = infiniteRepeatable(tween(8000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "blob1X"
    )
    val blob2X by infiniteTransition.animateFloat(
        initialValue = 0.8f, targetValue = 0.6f,
        animationSpec = infiniteRepeatable(tween(10000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "blob2X"
    )
    val blobAlpha by infiniteTransition.animateFloat(
        initialValue = 0.02f, targetValue = 0.06f,
        animationSpec = infiniteRepeatable(tween(6000, easing = EaseInOutSine), RepeatMode.Reverse),
        label = "blobAlpha"
    )

    // Dynamic theme accent matching brain states
    val accentColor = when {
        brainHealth >= 75 -> BrainHealthy
        brainHealth >= 50 -> BrainTired
        brainHealth >= 25 -> BrainOverstimulated
        brainHealth >= 10 -> BrainCritical
        else -> BrainRot
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF061733), Color(0xFF09090B))
                )
            )
            .drawBehind {
                // Background radial glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(accentColor.copy(alpha = blobAlpha), Color.Transparent),
                        center = Offset(size.width * blob1X, size.height * 0.2f),
                        radius = size.width * 0.55f
                    ),
                    radius = size.width * 0.55f,
                    center = Offset(size.width * blob1X, size.height * 0.2f)
                )
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(PrimaryCyan.copy(alpha = blobAlpha * 0.5f), Color.Transparent),
                        center = Offset(size.width * blob2X, size.height * 0.75f),
                        radius = size.width * 0.45f
                    ),
                    radius = size.width * 0.45f,
                    center = Offset(size.width * blob2X, size.height * 0.75f)
                )
            }
    ) {
        LazyColumn(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // 1. App Header
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left Target Icon Box
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF2B7FFF)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Adjust,
                            contentDescription = "Target Logo",
                            tint = Color(0xFFEFF6FF),
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Centered Title
                    Text(
                        text = "Focus Center",
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp,
                            color = Color.White
                        ),
                        textAlign = TextAlign.Center
                    )

                    // Right Settings Button
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Settings",
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            // 2. Tracking Status Pill Row
            item {
                val isActive = viewModel.isTrackingActive
                val trackingColor = if (isActive) Color(0xFF20C35F) else Color(0xFFFE302F)
                val trackingText = if (isActive) "Tracking On" else "Tracking Off"

                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(trackingColor)
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        // Pulsing indicator dot
                        val dotTransition = rememberInfiniteTransition(label = "pulseDot")
                        val dotAlpha by dotTransition.animateFloat(
                            initialValue = 0.4f,
                            targetValue = 1.0f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(1000, easing = EaseInOutSine),
                                repeatMode = RepeatMode.Reverse
                            ),
                            label = "dotAlpha"
                        )
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .graphicsLayer { alpha = dotAlpha }
                                .clip(CircleShape)
                                .background(Color.White)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = trackingText,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 14.sp
                            )
                        )
                    }
                }
            }

            // 3. Focus Level circular gauge (Size-60 / 240.dp)
            item {
                FocusLevelGauge(healthScore = brainHealth)
            }

            // 4. Suggestion Card (Italicized insight advice with Quote Icon & refresh button)
            item {
                val currentInsightIndex = remember { mutableStateOf(0) }
                val quoteText = insightsList.getOrNull(currentInsightIndex.value)
                    ?: "You've watched $totalReels reels today. Try a 10-minute walk instead."

                LaunchedEffect(insightsList) {
                    currentInsightIndex.value = 0
                }

                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 4.dp),
                    accentColor = accentColor,
                    enableTilt = false,
                    backgroundColor = Color.White.copy(alpha = 0.05f)
                ) {
                    Row(
                        verticalAlignment = Alignment.Top,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FormatQuote,
                            contentDescription = "Quote",
                            tint = Color(0xFF80AEF9),
                            modifier = Modifier
                                .size(20.dp)
                                .padding(top = 2.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = quoteText,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal
                            ),
                            color = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .clickable {
                                    if (insightsList.isNotEmpty()) {
                                        currentInsightIndex.value =
                                            (currentInsightIndex.value + 1) % insightsList.size
                                    }
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Refresh",
                                tint = Color.White.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }

            // 5. Time & Reels Card (Combined metrics double-card matching oklch(0.28_0.06_259.815))
            item {
                val hours = totalMinutes / 60
                val mins = totalMinutes % 60
                val timeLabel = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF162846),
                    accentColor = accentColor,
                    enableTilt = false
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = timeLabel,
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Time Today",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF80AEF9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }

                        // Vertical divider line
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(48.dp)
                                .background(Color.White.copy(alpha = 0.1f))
                        )

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Text(
                                text = "$totalReels",
                                style = MaterialTheme.typography.titleLarge.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 28.sp,
                                    color = Color.White
                                )
                            )
                            Text(
                                text = "Reels Watched",
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = Color(0xFF80AEF9),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            )
                        }
                    }
                }
            }

            // 6. Apps Breakdown Section Title
            item {
                Text(
                    text = "Apps Breakdown",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp)
                )
            }

            // 7. Platform Cards Grid Layout (2 columns)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(
                        Triple(Platform.INSTAGRAM, InstagramPink, "reels"),
                        Triple(Platform.YOUTUBE, YouTubeRed, "shorts"),
                    ).forEach { (platform, color, label) ->
                        val limitInfo = limits.find { it.platform == platform.name }
                        val reelLimit = limitInfo?.dailyReelLimit ?: 30
                        val minLimit = limitInfo?.dailyMinuteLimit ?: 60
                        PlatformCard(
                            platformName = platform.displayName,
                            reelCount = log?.getReelsForPlatform(platform) ?: 0,
                            minutes = log?.getMinutesForPlatform(platform) ?: 0,
                            reelLimit = reelLimit,
                            minuteLimit = minLimit,
                            platformColor = color,
                            reelLabel = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    listOf(
                        Triple(Platform.TIKTOK, TikTokCyan, "videos"),
                        Triple(Platform.SNAPCHAT, SnapchatYellow, "spotlights"),
                    ).forEach { (platform, color, label) ->
                        val limitInfo = limits.find { it.platform == platform.name }
                        val reelLimit = limitInfo?.dailyReelLimit ?: 30
                        val minLimit = limitInfo?.dailyMinuteLimit ?: 60
                        PlatformCard(
                            platformName = platform.displayName,
                            reelCount = log?.getReelsForPlatform(platform) ?: 0,
                            minutes = log?.getMinutesForPlatform(platform) ?: 0,
                            reelLimit = reelLimit,
                            minuteLimit = minLimit,
                            platformColor = color,
                            reelLabel = label,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Bottom Spacing
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
fun FocusLevelGauge(
    healthScore: Int,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        healthScore >= 75 -> Color(0xFF20C35F) // Healthy (oklch 150)
        healthScore >= 50 -> Color(0xFFFEB900) // Tired (oklch 84.429)
        else -> Color(0xFFFE302F) // Brainrot/Overstimulated (oklch 27.325)
    }
    val statusText = when {
        healthScore >= 90 -> "Elite"
        healthScore >= 75 -> "Healthy"
        healthScore >= 50 -> "Tired"
        healthScore >= 25 -> "Overstimulated"
        else -> "Brainrot"
    }
    val statusTextColor = when {
        healthScore >= 75 -> Color(0xFF20C35F)
        healthScore >= 50 -> Color(0xFFFEC100)
        else -> Color(0xFFFE302F)
    }

    val arcSweep = remember { Animatable(0f) }
    LaunchedEffect(healthScore) {
        arcSweep.animateTo(
            targetValue = 360f * (healthScore / 100f),
            animationSpec = tween(1200, easing = EaseInOutSine)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(240.dp)
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeWidthPx = 14.dp.toPx()
            val radius = (size.minDimension - strokeWidthPx) / 2
            val center = Offset(size.width / 2, size.height / 2)

            // Background circle with stroke
            drawCircle(
                color = Color.White.copy(alpha = 0.08f),
                radius = radius,
                center = center,
                style = Stroke(width = strokeWidthPx)
            )

            // Foreground sweep progress (LinearGradient brush matching yellow/green and blue/cyan)
            drawArc(
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2B7FFF),
                        Color(0xFF20C35F),
                        Color(0xFFFEB900)
                    )
                ),
                startAngle = -90f,
                sweepAngle = arcSweep.value,
                useCenter = false,
                style = Stroke(width = strokeWidthPx, cap = StrokeCap.Round),
                topLeft = Offset(center.x - radius, center.y - radius),
                size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2)
            )
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            // Large white score
            Text(
                text = "$healthScore",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 60.sp,
                    color = Color.White
                ),
                lineHeight = 60.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            // Label "Focus Level" in light blue
            Text(
                text = "Focus Level",
                style = MaterialTheme.typography.bodyMedium.copy(
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF80AEF9),
                    fontSize = 14.sp
                )
            )
            Spacer(modifier = Modifier.height(10.dp))
            // Status pill
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(12.dp))
                    .background(statusColor.copy(alpha = 0.15f))
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall.copy(
                        fontWeight = FontWeight.Bold,
                        color = statusTextColor,
                        fontSize = 12.sp
                    )
                )
            }
        }
    }
}

