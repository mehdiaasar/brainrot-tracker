package com.example.brainrottracker.ui.screens.streaks

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
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
import java.time.LocalDate
import java.time.YearMonth

data class Achievement(
    val emoji: String,
    val title: String,
    val description: String,
    val requirement: Int,
)

@Composable
fun StreaksScreen(
    modifier: Modifier = Modifier,
    viewModel: StreaksViewModel = viewModel()
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val futureDay = if (dark) WarmStepDim else WarmLightInner
    val futureDayText = if (dark) WarmTextSecondary else WarmLightTextSecondary
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary

    val state by viewModel.streakState.collectAsState()

    val achievements = listOf(
        Achievement("🌱", "First Step", "1 day under limit", 1),
        Achievement("⚔️", "7-Day Warrior", "7 consecutive days", 7),
        Achievement("🏆", "30-Day Legend", "30 consecutive days", 30),
        Achievement("🚫", "Zero Reel Day", "0 videos watched", 0),
        Achievement("⚡", "Half Hour Hero", "Under 30 min total", 0),
    )

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
    ) {
        // Title
        item {
            Text(
                "Streaks & Progress",
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

        // Streak hero card
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(surface)
                    .border(1.dp, cardBorder, RoundedCornerShape(16.dp))
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🔥", fontSize = 32.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "${state.currentStreak}",
                        color = textPrimary,
                        fontSize = 64.sp,
                        fontWeight = FontWeight.Normal,
                        lineHeight = 67.sp,
                        letterSpacing = (-1.5).sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "DAY STREAK",
                        color = textSecondary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 1.5.sp
                    )
                    Spacer(Modifier.height(32.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Current", color = textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                            Text("${state.currentStreak} days", color = textPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(40.dp)
                                .background(if (dark) WarmBorder else WarmLightBorder)
                        )
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text("Record", color = textSecondary, fontSize = 14.sp, lineHeight = 22.sp)
                            Text("${state.longestStreak} days", color = textPrimary, fontSize = 14.sp, lineHeight = 22.sp)
                        }
                    }
                }
            }
        }

        // Calendar
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 32.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                    .padding(24.dp)
            ) {
                Column {
                    // Day headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp)
                    ) {
                        listOf("M", "T", "W", "T", "F", "S", "S").forEach { day ->
                            Text(
                                day,
                                color = textSecondary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                    // Calendar grid — full current month, aligned to the
                    // Monday-first day headers above.
                    val today = LocalDate.now()
                    val month = YearMonth.now()
                    // dayOfWeek.value: Monday = 1 … Sunday = 7 → leading blanks
                    val leadingBlanks = month.atDay(1).dayOfWeek.value - 1
                    val cells: List<LocalDate?> =
                        List(leadingBlanks) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }

                    cells.chunked(7).forEach { week ->
                        Row(modifier = Modifier.fillMaxWidth()) {
                            week.forEach { date ->
                                if (date == null) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .padding(2.dp)
                                    )
                                    return@forEach
                                }
                                val dateStr = date.toString()
                                val isFuture = date.isAfter(today)
                                val isToday = date == today
                                val underLimit = state.streakMap[dateStr]

                                val circleBg = when {
                                    isFuture -> futureDay
                                    underLimit == true -> WarmGrantedGreen
                                    underLimit == false -> WarmError
                                    else -> futureDay
                                }
                                val circleText = when {
                                    isFuture || underLimit == null -> futureDayText
                                    else -> Color.White
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(circleBg)
                                            .then(
                                                if (isToday) Modifier.border(2.dp, WarmAccent, CircleShape)
                                                else Modifier
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            "${date.dayOfMonth}",
                                            color = circleText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }
                            // Pad empty cells if week has less than 7 days
                            repeat(7 - week.size) {
                                Box(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }

        // Achievements header
        item {
            Text(
                "Achievements",
                fontWeight = FontWeight.Medium,
                color = textPrimary,
                fontSize = 18.sp,
                lineHeight = 25.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp)
            )
        }

        // Achievement items
        items(achievements.size) { index ->
            val achievement = achievements[index]
            val isUnlocked = when (achievement.title) {
                "First Step" -> state.longestStreak >= 1
                "7-Day Warrior" -> state.longestStreak >= 7
                "30-Day Legend" -> state.longestStreak >= 30
                "Zero Reel Day" -> state.hasZeroReelDay
                "Half Hour Hero" -> state.hasHalfHourDay
                else -> false
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 8.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(surface)
                    .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(if (dark) WarmStepDim else WarmLightInner),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(achievement.emoji, fontSize = 18.sp)
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            achievement.title,
                            fontWeight = FontWeight.Medium,
                            color = textPrimary,
                            fontSize = 16.sp,
                            lineHeight = 22.sp
                        )
                        Text(
                            achievement.description,
                            color = textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 22.sp
                        )
                    }
                    if (isUnlocked) {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(WarmGrantedGreen)
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "DONE",
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                letterSpacing = 1.5.sp
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(if (dark) WarmStepDim else WarmLightBorder)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Icon(
                                Icons.Filled.Lock,
                                contentDescription = "Locked",
                                tint = textSecondary,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }

        item { Spacer(Modifier.height(24.dp)) }
    }
}
