package com.example.brainrottracker.ui.screens.streaks

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
import com.example.brainrottracker.theme.DangerRed
import com.example.brainrottracker.theme.PrimaryCyan
import com.example.brainrottracker.theme.SuccessGreen
import com.example.brainrottracker.theme.TextSecondary
import com.example.brainrottracker.theme.TextTertiary
import com.example.brainrottracker.ui.components.AnimatedCounter
import com.example.brainrottracker.ui.components.GlassCard
import com.example.brainrottracker.ui.components.StreakCalendar

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
    val state by viewModel.streakState.collectAsState()

    val achievements = listOf(
        Achievement("🌱", "First Step", "1 day under limit", 1),
        Achievement("⚔️", "7-Day Warrior", "7 consecutive days", 7),
        Achievement("👑", "30-Day Legend", "30 consecutive days", 30),
        Achievement("🧘", "Zero Reel Day", "Watch 0 reels in a day", 0),
        Achievement("⚡", "Half Hour Hero", "Under 30 min total", 0),
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Screen Title
        item {
            Text(
                text = "Streaks & Progress",
                style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Black),
                color = Color.White
            )
        }

        // Fire streak header
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(text = "🔥", fontSize = 64.sp)
                Spacer(Modifier.height(8.dp))

                Row(verticalAlignment = Alignment.Bottom) {
                    AnimatedCounter(
                        count = state.currentStreak,
                        style = MaterialTheme.typography.displayLarge.copy(
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan
                        )
                    )
                    Text(
                        text = " days",
                        style = MaterialTheme.typography.headlineSmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                }

                Text(
                    text = "Current Streak",
                    style = MaterialTheme.typography.titleMedium,
                    color = TextSecondary
                )
            }
        }

        // Streak records
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🔥", fontSize = 24.sp)
                        Text(
                            text = "${state.currentStreak}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = PrimaryCyan
                        )
                        Text("Current", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
                GlassCard(modifier = Modifier.weight(1f)) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                        Text("🏆", fontSize = 24.sp)
                        Text(
                            text = "${state.longestStreak}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                        Text("Best", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                    }
                }
            }
        }

        // Streak calendar
        item {
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Column {
                    Text(
                        text = "Activity Calendar",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                    StreakCalendar(
                        streakData = state.streakMap,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // Achievements
        item {
            Text(
                text = "Achievements",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        }

        items(achievements) { achievement ->
            val isUnlocked = when (achievement.title) {
                "First Step" -> state.longestStreak >= 1
                "7-Day Warrior" -> state.longestStreak >= 7
                "30-Day Legend" -> state.longestStreak >= 30
                else -> false // Other achievements require specific logic
            }

            GlassCard(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(
                                if (isUnlocked) SuccessGreen.copy(alpha = 0.2f)
                                else Color.White.copy(alpha = 0.05f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = achievement.emoji,
                            fontSize = 24.sp,
                            modifier = if (!isUnlocked) Modifier else Modifier
                        )
                    }

                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 12.dp)
                    ) {
                        Text(
                            text = achievement.title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isUnlocked) Color.White else TextTertiary
                        )
                        Text(
                            text = achievement.description,
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    Text(
                        text = if (isUnlocked) "✅" else "🔒",
                        fontSize = 20.sp
                    )
                }
            }
        }

        item { Spacer(Modifier.height(16.dp)) }
    }
}
