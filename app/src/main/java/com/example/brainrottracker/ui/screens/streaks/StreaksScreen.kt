package com.example.brainrottracker.ui.screens.streaks

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.brainrottracker.R
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
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

// ── Milestone model ─────────────────────────────────────────────────────────
// Each tier of the "journey". Thresholds drive both the hero card and the
// horizontal journey rail; everything below is derived from the live
// currentStreak / longestStreak supplied by the ViewModel.
private data class Milestone(
    val days: Int,
    val journeyTitle: String,   // "Day 7"
    val journeySub: String,     // "Focus Warrior"
    val badgeName: String,      // "7-Day Warrior" (hero badge + progress label)
    val journeyImg: Int,
)

private val MILESTONES = listOf(
    Milestone(1, "Day 1", "Started", "First Step", R.drawable.journey_day1),
    Milestone(7, "Day 7", "Focus Warrior", "7-Day Warrior", R.drawable.journey_day7),
    Milestone(30, "Day 30", "Legend", "30-Day Legend", R.drawable.journey_day30),
    Milestone(100, "Day 100", "Champion", "100-Day Champion", R.drawable.journey_day100),
    Milestone(365, "Day 365", "Master", "365-Day Master", R.drawable.journey_day365),
)

private data class AchievementBadge(
    val name: String,
    val requirement: Int,
    val img: Int,
)

private val BADGES = listOf(
    AchievementBadge("First Step", 1, R.drawable.ach_first_step),
    AchievementBadge("Warrior", 7, R.drawable.ach_warrior),
    AchievementBadge("Legend", 30, R.drawable.ach_legend),
    AchievementBadge("Zen Master", 60, R.drawable.ach_zen_master),
    AchievementBadge("Champion", 100, R.drawable.ach_champion),
)

private val FOCUS_TIPS = listOf(
    "Break your study into 25–30 minute sessions with short breaks.",
    "Consistency beats intensity.",
    "Focus on progress, not perfection.",
    "Protect your attention like a superpower.",
    "Small daily wins create extraordinary results.",
    "One focused session today is better than a perfect plan tomorrow.",
    "Train your brain daily, even for a few minutes.",
    "Every streak starts with Day One.",
)

// Hero scene that matches the band the current streak falls into.
private fun heroImageFor(streak: Int): Int = when {
    streak >= 365 -> R.drawable.streak_hero_365
    streak >= 100 -> R.drawable.streak_hero_100
    streak >= 30 -> R.drawable.streak_hero_30
    streak >= 7 -> R.drawable.streak_hero_7
    else -> R.drawable.streak_hero_1
}

// Fixed warm-on-bright colors used over the hero scene (legible in both themes).
private val HeroPurple = Color(0xFF6D28D9)
private val HeroInk = Color(0xFF2E2A26)
private val HeroInkSoft = Color(0xFF54504A)

@Composable
fun StreaksScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    viewModel: StreaksViewModel = viewModel()
) {
    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val inner = if (dark) WarmStepDim else WarmLightInner
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary
    val purple = if (dark) WarmInsightAccentDark else WarmInsightAccent
    val purpleBg = if (dark) WarmInsightBgDark else WarmInsightBg

    val state by viewModel.streakState.collectAsState()
    val streak = state.currentStreak

    // Next milestone the user is climbing toward, plus progress to it.
    val nextMilestone = MILESTONES.firstOrNull { it.days > streak } ?: MILESTONES.last()
    val target = nextMilestone.days
    val progress = (streak.toFloat() / target).coerceIn(0f, 1f)
    val percent = (progress * 100).toInt()

    val tip = remember { FOCUS_TIPS.random() }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .background(bg),
        contentPadding = PaddingValues(bottom = 32.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(top = 24.dp, bottom = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "Streaks & Progress",
                        color = textPrimary,
                        fontSize = 26.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = (-0.5).sp,
                        lineHeight = 30.sp
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Keep going, you're doing great! 💪",
                        color = WarmAccent,
                        fontSize = 14.sp,
                        lineHeight = 18.sp
                    )
                }
                // Settings entry — account icon, top-right corner.
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(surface)
                        .border(1.dp, cardBorder, CircleShape)
                        .clickable(onClick = onOpenSettings),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Person,
                        contentDescription = "Open settings",
                        tint = textSecondary,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // ── Journey-progress hero card ─────────────────────────────────────
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .height(280.dp)
            ) {
                Image(
                    painterResource(heroImageFor(streak)),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
                // Left-side scrim so the overlaid text stays readable.
                Box(
                    Modifier
                        .fillMaxSize()
                        .background(
                            Brush.horizontalGradient(
                                0f to Color.White.copy(alpha = 0.62f),
                                0.4f to Color.White.copy(alpha = 0.28f),
                                0.7f to Color.Transparent
                            )
                        )
                )

                // Badge — next milestone, top-right.
                Row(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(14.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.92f))
                        .padding(start = 6.dp, end = 14.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        painterResource(nextMilestone.journeyImg),
                        contentDescription = null,
                        modifier = Modifier.size(26.dp)
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        nextMilestone.badgeName,
                        color = HeroInk,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                // Overlaid copy + progress.
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp)
                ) {
                    Text(
                        "Journey Progress",
                        color = HeroPurple,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        "$percent%",
                        color = HeroPurple,
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-1.5).sp,
                        lineHeight = 56.sp
                    )
                    Text(
                        "to ${nextMilestone.badgeName} 👑",
                        color = HeroPurple,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "You're building an\namazing mind!",
                        color = HeroInkSoft,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )

                    Spacer(Modifier.weight(1f))

                    // "n / target days" pill.
                    Box(
                        Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.9f))
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            "$streak / $target days",
                            color = HeroInk,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    HeroProgressBar(progress)
                    Spacer(Modifier.height(4.dp))
                    Row(Modifier.fillMaxWidth(0.62f)) {
                        Text("0", color = HeroInkSoft, fontSize = 11.sp)
                        Spacer(Modifier.weight(1f))
                        Text("$target", color = HeroInkSoft, fontSize = 11.sp)
                    }
                }
            }
        }

        // ── Your Journey ───────────────────────────────────────────────────
        item { SectionHeader("Your Journey", textPrimary) }
        item {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
            ) {
                items(MILESTONES.size) { i ->
                    val m = MILESTONES[i]
                    val reached = streak >= m.days
                    val inProgress = !reached && m.days == target
                    JourneyCard(
                        m = m,
                        reached = reached,
                        inProgress = inProgress,
                        surface = surface,
                        border = cardBorder,
                        purple = purple,
                        purpleBg = purpleBg,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }
        }

        // ── Motivation card (uses the studious "Almost there!" brain) ──────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(purpleBg)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        if (streak == 0) "Start your streak" else "$streak-day streak",
                        color = purple,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "You're $percent% of the way to becoming a ${nextMilestone.badgeName}. Keep it up!",
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
                Image(
                    painterResource(R.drawable.streak_quest_brain),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(112.dp)
                )
            }
        }

        // ── Achievements ───────────────────────────────────────────────────
        item { SectionHeader("Achievements", textPrimary) }
        item {
            val firstLocked = BADGES.indexOfFirst { state.longestStreak < it.requirement }
            LazyRow(
                contentPadding = PaddingValues(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(top = 12.dp, bottom = 28.dp)
            ) {
                items(BADGES.size) { i ->
                    val b = BADGES[i]
                    AchievementCard(
                        badge = b,
                        unlocked = state.longestStreak >= b.requirement,
                        showProgress = i == firstLocked,
                        progressNow = streak.coerceAtMost(b.requirement),
                        surface = surface,
                        border = cardBorder,
                        inner = inner,
                        purple = purple,
                        textPrimary = textPrimary,
                        textSecondary = textSecondary
                    )
                }
            }
        }

        // ── Focus Tip (rotates each visit) ─────────────────────────────────
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(purpleBg)
                    .padding(start = 14.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("💡", fontSize = 16.sp)
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "Focus Tip",
                            color = purple,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        tip,
                        color = textSecondary,
                        fontSize = 14.sp,
                        lineHeight = 19.sp
                    )
                }
                Image(
                    painterResource(R.drawable.streak_focus_tip),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier.height(112.dp)
                )
            }
        }
    }
}

// ── Pieces ──────────────────────────────────────────────────────────────────

@Composable
private fun SectionHeader(title: String, textPrimary: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            title,
            fontWeight = FontWeight.SemiBold,
            color = textPrimary,
            fontSize = 19.sp,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun HeroProgressBar(progress: Float) {
    val barShape = RoundedCornerShape(4.dp)
    Box(
        Modifier
            .fillMaxWidth(0.62f)
            .height(10.dp)
            .clip(barShape)
            .background(Color.White.copy(alpha = 0.8f))
    ) {
        Box(
            Modifier
                .fillMaxWidth(progress.coerceAtLeast(0.02f))
                .fillMaxSize()
                .clip(barShape)
                .background(HeroPurple)
        )
        // Star marker riding the fill edge.
        Box(
            Modifier
                .fillMaxWidth(progress.coerceIn(0.04f, 0.96f))
                .fillMaxSize(),
            contentAlignment = Alignment.CenterEnd
        ) {
            Text("⭐", fontSize = 14.sp)
        }
    }
}

@Composable
private fun JourneyCard(
    m: Milestone,
    reached: Boolean,
    inProgress: Boolean,
    surface: Color,
    border: Color,
    purple: Color,
    purpleBg: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    val titleColor = when {
        reached -> WarmGrantedGreen
        inProgress -> purple
        else -> textSecondary
    }
    Column(
        modifier = Modifier
            .width(124.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(if (inProgress) purpleBg else surface)
            .border(
                1.dp,
                if (inProgress) purple.copy(alpha = 0.5f) else border,
                RoundedCornerShape(16.dp)
            )
            .padding(vertical = 10.dp, horizontal = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(m.journeyTitle, color = titleColor, fontSize = 15.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(96.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painterResource(m.journeyImg),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (reached || inProgress) Modifier else Modifier.alpha(0.35f))
            )
            if (!reached && !inProgress) {
                Icon(
                    Icons.Filled.Lock,
                    contentDescription = null,
                    tint = textSecondary,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(m.journeySub, color = textPrimary, fontSize = 13.sp, fontWeight = FontWeight.Medium)
        Spacer(Modifier.height(6.dp))
        when {
            reached -> StatusCheck()
            inProgress -> Text("In Progress", color = purple, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            else -> Text("Locked", color = textSecondary, fontSize = 12.sp)
        }
    }
}

@Composable
private fun AchievementCard(
    badge: AchievementBadge,
    unlocked: Boolean,
    showProgress: Boolean,
    progressNow: Int,
    surface: Color,
    border: Color,
    inner: Color,
    purple: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Column(
        modifier = Modifier
            .width(140.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(surface)
            .border(1.dp, border, RoundedCornerShape(16.dp))
            .padding(vertical = 16.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Image(
                painterResource(badge.img),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                alignment = Alignment.BottomCenter,
                modifier = Modifier
                    .fillMaxSize()
                    .then(if (unlocked) Modifier else Modifier.alpha(0.35f))
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(badge.name, color = textPrimary, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            "${badge.requirement} day streak",
            color = textSecondary,
            fontSize = 12.sp
        )
        Spacer(Modifier.height(10.dp))
        when {
            unlocked -> StatusCheck()
            showProgress -> {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape)
                        .background(inner)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(
                                (progressNow.toFloat() / badge.requirement).coerceIn(0.02f, 1f)
                            )
                            .fillMaxSize()
                            .clip(CircleShape)
                            .background(purple)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    "$progressNow / ${badge.requirement}",
                    color = purple,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            else -> Icon(
                Icons.Filled.Lock,
                contentDescription = "Locked",
                tint = textSecondary,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
private fun StatusCheck() {
    Box(
        modifier = Modifier
            .size(22.dp)
            .clip(CircleShape)
            .background(WarmGrantedGreen),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            Icons.Filled.Check,
            contentDescription = "Done",
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
    }
}
