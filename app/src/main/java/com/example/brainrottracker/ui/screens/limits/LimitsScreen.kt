package com.example.brainrottracker.ui.screens.limits

import android.content.Context
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.theme.DangerRed
import com.example.brainrottracker.theme.InstagramPink
import com.example.brainrottracker.theme.PrimaryBlue
import com.example.brainrottracker.theme.PrimaryCyan
import com.example.brainrottracker.theme.SnapchatYellow
import com.example.brainrottracker.theme.TextSecondary
import com.example.brainrottracker.theme.TikTokCyan
import com.example.brainrottracker.theme.YouTubeRed

@Composable
fun LimitsScreen(
    modifier: Modifier = Modifier,
    viewModel: LimitsViewModel = viewModel()
) {
    val limits by viewModel.limits.collectAsState()
    val todayLog by viewModel.todayLog.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("brainrot_prefs", Context.MODE_PRIVATE) }
    var strictMode by remember { mutableStateOf(sharedPrefs.getBoolean("strict_mode", false)) }

    val platformColors = mapOf(
        Platform.INSTAGRAM.name to InstagramPink,
        Platform.YOUTUBE.name to YouTubeRed,
        Platform.TIKTOK.name to TikTokCyan,
        Platform.SNAPCHAT.name to SnapchatYellow,
    )

    // Check if any daily limit is breached
    val isAnyLimitBreached = remember(limits, todayLog) {
        if (todayLog == null) false
        else {
            Platform.entries.any { platform ->
                val limit = limits.find { it.platform == platform.name }
                val defaultReels = when (platform) {
                    Platform.INSTAGRAM -> 30
                    Platform.YOUTUBE -> 20
                    Platform.TIKTOK -> 50
                    Platform.SNAPCHAT -> 15
                }
                val cap = limit?.dailyReelLimit ?: defaultReels
                val current = when (platform) {
                    Platform.INSTAGRAM -> todayLog!!.instagramReels
                    Platform.YOUTUBE -> todayLog!!.youtubeShorts
                    Platform.TIKTOK -> todayLog!!.tiktokVideos
                    Platform.SNAPCHAT -> todayLog!!.snapchatSpotlights
                }
                current >= cap
            }
        }
    }

    val isEditingLocked = strictMode && isAnyLimitBreached

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Text(
                text = "Daily Limits & Blocks",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
            Text(
                text = "Set limits for each platform",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Lock Banner shown when strict mode is active and cap is breached
        if (isEditingLocked) {
            item {
                LimitsCard(
                    modifier = Modifier.fillMaxWidth(),
                    borderColor = DangerRed.copy(alpha = 0.5f)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("🔒", fontSize = 24.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Editing Locked",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = DangerRed
                            )
                            Text(
                                text = "Strict Mode is active and you have reached a daily limit today. You cannot edit settings until tomorrow.",
                                style = MaterialTheme.typography.bodySmall,
                                color = TextSecondary
                            )
                        }
                    }
                }
            }
        }

        // Strict mode toggle
        item {
            LimitsCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Strict Mode Icon
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(PrimaryBlue.copy(alpha = 0.20f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("🔒", fontSize = 16.sp)
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Strict Mode",
                            style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                        Text(
                            text = "Prevents editing limits once daily cap is breached",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                    Switch(
                        checked = strictMode,
                        onCheckedChange = { checked ->
                            if (!isEditingLocked) {
                                strictMode = checked
                                sharedPrefs.edit().putBoolean("strict_mode", checked).apply()
                            }
                        },
                        enabled = !isEditingLocked,
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PrimaryBlue,
                            uncheckedThumbColor = Color(0xFF9E9EA9),
                            uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                        )
                    )
                }
            }
        }

        // Per-platform limit cards
        items(Platform.entries.toList()) { platform ->
            val existingLimit = limits.find { it.platform == platform.name }
            val color = platformColors[platform.name] ?: PrimaryCyan
            val currentReels = when (platform) {
                Platform.INSTAGRAM -> todayLog?.instagramReels ?: 0
                Platform.YOUTUBE -> todayLog?.youtubeShorts ?: 0
                Platform.TIKTOK -> todayLog?.tiktokVideos ?: 0
                Platform.SNAPCHAT -> todayLog?.snapchatSpotlights ?: 0
            }

            PlatformLimitCard(
                platform = platform,
                currentLimit = existingLimit,
                currentReels = currentReels,
                platformColor = color,
                isEditingLocked = isEditingLocked,
                onUpdateLimit = { updatedLimits ->
                    viewModel.updateLimit(updatedLimits)
                }
            )
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}

@Composable
fun LimitsCard(
    modifier: Modifier = Modifier,
    borderColor: Color = Color.White.copy(alpha = 0.08f),
    content: @Composable () -> Unit
) {
    androidx.compose.material3.Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = Color(0xFF141A3D), // Figma limits card background #141A3D
        border = androidx.compose.foundation.BorderStroke(1.dp, borderColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            content()
        }
    }
}

@Composable
fun PlatformLimitCard(
    platform: Platform,
    currentLimit: UserLimits?,
    currentReels: Int,
    platformColor: Color,
    isEditingLocked: Boolean,
    onUpdateLimit: (UserLimits) -> Unit
) {
    val defaultReelLimit = when (platform) {
        Platform.INSTAGRAM -> 30
        Platform.YOUTUBE -> 20
        Platform.TIKTOK -> 50
        Platform.SNAPCHAT -> 15
    }
    val defaultMinuteLimit = when (platform) {
        Platform.INSTAGRAM -> 60
        Platform.YOUTUBE -> 90
        Platform.TIKTOK -> 45
        Platform.SNAPCHAT -> 30
    }

    var reelLimit by remember(currentLimit) { mutableFloatStateOf((currentLimit?.dailyReelLimit ?: defaultReelLimit).toFloat()) }
    var minuteLimit by remember(currentLimit) { mutableFloatStateOf((currentLimit?.dailyMinuteLimit ?: defaultMinuteLimit).toFloat()) }
    var blockingEnabled by remember(currentLimit) { mutableStateOf(currentLimit?.blockingEnabled ?: true) }

    LimitsCard(modifier = Modifier.fillMaxWidth()) {
        // Card Header Row: Icon + Name (Left), Lock (Right)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Platform Brand Icon Container
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(platformColor.copy(alpha = 0.20f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = platformEmoji(platform), fontSize = 14.sp)
                }

                Text(
                    text = "${platform.displayName}",
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                    color = Color.White
                )
            }
            if (isEditingLocked) {
                Text("🔒", fontSize = 16.sp)
            }
        }

        // Reel limit section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Videos / Day",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                    color = Color(0xFF9E9EA9)
                )
                // Badge displaying value
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${reelLimit.toInt()}",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryBlue
                    )
                }
            }

            Slider(
                value = reelLimit,
                onValueChange = { if (!isEditingLocked) reelLimit = it },
                onValueChangeFinished = {
                    onUpdateLimit(UserLimits(
                        platform = platform.name,
                        dailyReelLimit = reelLimit.toInt(),
                        dailyMinuteLimit = minuteLimit.toInt(),
                        blockingEnabled = blockingEnabled
                    ))
                },
                enabled = !isEditingLocked,
                valueRange = 0f..200f,
                steps = 19,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }

        // Time limit section
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Max Time / Day",
                    style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.Normal),
                    color = Color(0xFF9E9EA9)
                )
                // Badge displaying value
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(PrimaryBlue.copy(alpha = 0.20f))
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "${minuteLimit.toInt()}m",
                        style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                        color = PrimaryBlue
                    )
                }
            }

            Slider(
                value = minuteLimit,
                onValueChange = { if (!isEditingLocked) minuteLimit = it },
                onValueChangeFinished = {
                    onUpdateLimit(UserLimits(
                        platform = platform.name,
                        dailyReelLimit = reelLimit.toInt(),
                        dailyMinuteLimit = minuteLimit.toInt(),
                        blockingEnabled = blockingEnabled
                    ))
                },
                enabled = !isEditingLocked,
                valueRange = 0f..240f,
                steps = 23,
                colors = SliderDefaults.colors(
                    thumbColor = Color.White,
                    activeTrackColor = PrimaryBlue,
                    inactiveTrackColor = Color.White.copy(alpha = 0.10f),
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent
                )
            )
        }

        // Blocking toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Enable App Blocking",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
            Switch(
                checked = blockingEnabled,
                onCheckedChange = {
                    if (!isEditingLocked) {
                        blockingEnabled = it
                        onUpdateLimit(UserLimits(
                            platform = platform.name,
                            dailyReelLimit = reelLimit.toInt(),
                            dailyMinuteLimit = minuteLimit.toInt(),
                            blockingEnabled = it
                        ))
                    }
                },
                enabled = !isEditingLocked,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = PrimaryBlue,
                    uncheckedThumbColor = Color(0xFF9E9EA9),
                    uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                )
            )
        }
    }
}

private fun platformEmoji(platform: Platform): String = when (platform) {
    Platform.INSTAGRAM -> "📸"
    Platform.YOUTUBE -> "▶️"
    Platform.TIKTOK -> "🎵"
    Platform.SNAPCHAT -> "👻"
}
