package com.example.brainrottracker.ui.screens.dashboard

import androidx.compose.ui.graphics.Color
import com.example.brainrottracker.R
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmGrantedGreen

/**
 * The five dashboard "variations": every mood swaps the hero, insight, and reminder
 * characters plus all the dynamic copy, mirroring the FocusCenter mock screens.
 *
 * Picked from how far today's usage is into the daily limits — see [fromUsage].
 */
enum class DashboardMood(
    val mainRes: Int,
    val insightRes: Int,
    val reminderRes: Int,
    val headline: String,
    val bubble: String,
    val scoreCaption: String,
    val reminderTitle: String,
    val reminderText: String,
    val reminderEmoji: String,
    /** Closing encouragement line shown in the Insight card, tinted with the insight accent. */
    val insightTail: String,
    /** Daily-goal pill text + subtitle shown in the tracking-tabs row. */
    val goalLabel: String,
    val goalSub: String,
    /** Greeting emoji next to "Good morning!". */
    val greetingEmoji: String,
    val accent: Color,
) {
    GREAT(
        mainRes = R.drawable.char_great_main,
        insightRes = R.drawable.char_great_insight,
        reminderRes = R.drawable.char_great_reminder,
        headline = "You're doing amazing!",
        bubble = "Keep up the great work! Your brain is happy. 💚",
        scoreCaption = "Great job! 🎉",
        reminderTitle = "Remember",
        reminderText = "Every moment of focus is a step towards a better you. 🌱",
        reminderEmoji = "💚",
        insightTail = "That's awesome! Keep it up! 💪",
        goalLabel = "On Track",
        goalSub = "Great progress!",
        greetingEmoji = "☀️",
        accent = WarmGrantedGreen,
    ),
    ZONE(
        mainRes = R.drawable.char_zone_main,
        insightRes = R.drawable.char_zone_insight,
        reminderRes = R.drawable.char_zone_reminder,
        headline = "You're in the zone!",
        bubble = "Stay focused, you're doing great! 💪",
        scoreCaption = "Keep it up! 🎉",
        reminderTitle = "Remember",
        reminderText = "Small steps every day create big changes. 🌱",
        reminderEmoji = "💚",
        insightTail = "That's awesome! 🔥",
        goalLabel = "On Track",
        goalSub = "Great progress!",
        greetingEmoji = "☀️",
        accent = WarmGrantedGreen,
    ),
    NEAR(
        mainRes = R.drawable.char_near_main,
        insightRes = R.drawable.char_near_insight,
        reminderRes = R.drawable.char_near_reminder,
        headline = "Careful! You're nearing your limit.",
        bubble = "Let's take small breaks and stay in control. 🙏",
        scoreCaption = "You can do better! 💪",
        reminderTitle = "Reminder",
        reminderText = "You're close to your limit. Take a break and relax! ☕",
        reminderEmoji = "🔔",
        insightTail = "Try shorter sessions to recharge. 🌱",
        goalLabel = "On Track",
        goalSub = "Great progress!",
        greetingEmoji = "☀️",
        accent = Color(0xFFE8A55A),
    ),
    LIMIT(
        mainRes = R.drawable.char_limit_main,
        insightRes = R.drawable.char_limit_insight,
        reminderRes = R.drawable.char_limit_reminder,
        headline = "You've reached today's limit.",
        bubble = "Take a break now. Your brain needs rest. 🔴",
        scoreCaption = "Time to rest. 😴",
        reminderTitle = "Reminder",
        reminderText = "Overuse can drain your focus and energy. Take a long break. 😣",
        reminderEmoji = "🔔",
        insightTail = "Let's aim for balance tomorrow! 🌱",
        goalLabel = "Over",
        goalSub = "Try again tomorrow.",
        greetingEmoji = "🌧️",
        accent = WarmError,
    ),
    OVER(
        mainRes = R.drawable.char_over_main,
        insightRes = R.drawable.char_over_insight,
        reminderRes = R.drawable.char_over_reminder,
        headline = "Your brain needs care.",
        bubble = "You've pushed too hard. Rest, reset, and be kind to yourself. 🧡",
        scoreCaption = "Please take care. 💔",
        reminderTitle = "Reminder",
        reminderText = "Your brain is overworked. Rest, hydrate, and do something offline. 🙏",
        reminderEmoji = "🔔",
        insightTail = "It's okay to rest. Tomorrow is a new day. 🌱",
        goalLabel = "Missed",
        goalSub = "Try again tomorrow.",
        greetingEmoji = "🌧️",
        accent = WarmError,
    );

    companion object {
        /**
         * @param reelRatio today's reels / daily reel limit
         */
        fun fromUsage(reelRatio: Float): DashboardMood {
            return when {
                reelRatio < 0.4f -> GREAT
                reelRatio < 0.75f -> ZONE
                reelRatio < 1f -> NEAR
                reelRatio < 1.5f -> LIMIT
                else -> OVER
            }
        }
    }
}
