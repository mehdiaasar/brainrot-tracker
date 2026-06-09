package com.example.brainrottracker.ui.screens.limits

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import com.example.brainrottracker.theme.ThemeController
import com.example.brainrottracker.theme.rememberIsDark
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightInner
import com.example.brainrottracker.theme.WarmLightSurface
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary

@Composable
fun LimitsScreen(
    modifier: Modifier = Modifier,
    viewModel: LimitsViewModel = viewModel()
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val trackBg = if (dark) com.example.brainrottracker.theme.WarmStepDim else WarmLightInner
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary

    val limits by viewModel.limits.collectAsState()
    val context = LocalContext.current
    val sharedPrefs = remember { context.getSharedPreferences("brainrot_prefs", Context.MODE_PRIVATE) }

    val initialReelLimit = limits.firstOrNull()?.dailyReelLimit?.toFloat() ?: 50f
    val initialMinuteLimit = limits.firstOrNull()?.dailyMinuteLimit?.toFloat() ?: 60f

    var reelLimit by remember(initialReelLimit) { mutableFloatStateOf(initialReelLimit) }
    var minuteLimit by remember(initialMinuteLimit) { mutableFloatStateOf(initialMinuteLimit) }
    var blockingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean("blocking_enabled", false)) }
    var hudScale by remember { mutableFloatStateOf(sharedPrefs.getFloat("hud_scale", 1.2f)) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
    ) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {

            // Header
            item {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(top = 32.dp, bottom = 12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("✳", color = textPrimary, fontSize = 18.sp)
                        Text(
                            "SETTINGS",
                            color = textSecondary,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            letterSpacing = 1.5.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "Daily Limits",
                        fontWeight = FontWeight.Normal,
                        color = textPrimary,
                        fontSize = 36.sp,
                        lineHeight = 41.sp,
                        letterSpacing = (-0.5).sp
                    )
                }
            }

            // Global daily limits card
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(20.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎯", fontSize = 18.sp)
                            Column {
                                Text(
                                    "Daily Video Limit",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Applies to all platforms",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }

                        // Video limit slider
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Videos per day", color = textSecondary, fontSize = 14.sp)
                                Text(
                                    "${reelLimit.toInt()} videos",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = reelLimit,
                                onValueChange = { reelLimit = it },
                                onValueChangeFinished = {
                                    viewModel.updateGlobalLimits(reelLimit.toInt(), minuteLimit.toInt())
                                },
                                valueRange = 0f..200f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = WarmAccent,
                                    activeTrackColor = WarmAccent,
                                    inactiveTrackColor = trackBg,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                        }

                        HorizontalDivider(color = cardBorder, thickness = 1.dp)

                        // Time limit slider
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Time per day", color = textSecondary, fontSize = 14.sp)
                                Text(
                                    "${minuteLimit.toInt()} min",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = minuteLimit,
                                onValueChange = { minuteLimit = it },
                                onValueChangeFinished = {
                                    viewModel.updateGlobalLimits(reelLimit.toInt(), minuteLimit.toInt())
                                },
                                valueRange = 0f..240f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = WarmAccent,
                                    activeTrackColor = WarmAccent,
                                    inactiveTrackColor = trackBg,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }

            // App blocking toggle
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("🚫", fontSize = 18.sp)
                            Column {
                                Text(
                                    "App Blocking",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Show overlay when daily limit is reached",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        WarmToggle(
                            checked = blockingEnabled,
                            onCheckedChange = {
                                blockingEnabled = it
                                sharedPrefs.edit().putBoolean("blocking_enabled", it).apply()
                            }
                        )
                    }
                }
            }

            // Appearance / theme selector
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("🎨", fontSize = 18.sp)
                            Column {
                                Text(
                                    "Appearance",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Choose your theme",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        ThemeModeSelector(
                            selected = ThemeController.mode,
                            onSelect = { ThemeController.selectMode(it) },
                            trackBg = trackBg,
                            textPrimary = textPrimary,
                            textSecondary = textSecondary
                        )
                    }
                }
            }

            // Floating counter (HUD) size
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 24.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(surface)
                        .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                        .padding(24.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text("📏", fontSize = 18.sp)
                            Column {
                                Text(
                                    "Floating Counter",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 16.sp,
                                    lineHeight = 22.sp
                                )
                                Text(
                                    "Adjust the on-screen counter size",
                                    color = textSecondary,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp
                                )
                            }
                        }
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Size", color = textSecondary, fontSize = 14.sp)
                                Text(
                                    "${(hudScale * 100).toInt()}%",
                                    fontWeight = FontWeight.Medium,
                                    color = textPrimary,
                                    fontSize = 13.sp
                                )
                            }
                            Slider(
                                value = hudScale,
                                onValueChange = { hudScale = it },
                                onValueChangeFinished = {
                                    sharedPrefs.edit().putFloat("hud_scale", hudScale).apply()
                                },
                                valueRange = 0.8f..1.8f,
                                modifier = Modifier.fillMaxWidth(),
                                colors = SliderDefaults.colors(
                                    thumbColor = WarmAccent,
                                    activeTrackColor = WarmAccent,
                                    inactiveTrackColor = trackBg,
                                    activeTickColor = Color.Transparent,
                                    inactiveTickColor = Color.Transparent
                                )
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeController.Mode,
    onSelect: (ThemeController.Mode) -> Unit,
    trackBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val options = listOf(
        ThemeController.Mode.LIGHT to "☀  Light",
        ThemeController.Mode.DARK to "🌙  Dark",
        ThemeController.Mode.SYSTEM to "⚙  System",
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(trackBg)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        options.forEach { (mode, label) ->
            val isSelected = mode == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (isSelected) WarmAccent else Color.Transparent)
                    .clickable { onSelect(mode) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    color = if (isSelected) Color.White else textSecondary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun WarmToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Box(
        modifier = Modifier
            .size(width = 48.dp, height = 28.dp)
            .clip(CircleShape)
            .background(if (checked) WarmAccent else WarmLightBorder)
            .clickable { onCheckedChange(!checked) }
            .padding(4.dp),
        contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(Color.White)
        )
    }
}
