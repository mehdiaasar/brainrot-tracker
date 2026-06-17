package com.example.brainrottracker.ui.screens.dashboard

import androidx.compose.ui.graphics.Color
import com.example.brainrottracker.theme.WarmError
import com.example.brainrottracker.theme.WarmGrantedGreen

/**
 * Focus Shield — a game-like "protection level" that reflects how well the app is
 * shielding the user from doomscrolling today. It is derived from the same usage-vs-limit
 * ratios that drive the dashboard mood, so the shield, the mascot, and the score all move
 * together as the day's scrolling climbs.
 */
enum class ShieldStatus(
    val label: String,
    val message: String,
    /** Short caption shown under the label in the dashboard tracking tab. */
    val tabSubtitle: String,
    val color: Color,
) {
    ACTIVE(
        label = "Active",
        message = "Focus Shield is protecting your brain.",
        tabSubtitle = "Blocking distractions",
        color = WarmGrantedGreen,
    ),
    WEAK(
        label = "Weak",
        message = "Your shield is weakening.",
        tabSubtitle = "Blocking distractions",
        color = Color(0xFFE8A55A),
    ),
    DAMAGED(
        label = "Damaged",
        message = "Too many distractions are getting through.",
        tabSubtitle = "Too many distractions",
        color = Color(0xFFE07A3E),
    ),
    BROKEN(
        label = "Inactive",
        message = "Your brain is exposed to distractions.",
        tabSubtitle = "Too many distractions",
        color = WarmError,
    );
}

data class FocusShield(val status: ShieldStatus, val percent: Int) {
    companion object {
        /**
         * @param reelRatio today's reels / daily reel limit
         */
        fun fromUsage(reelRatio: Float): FocusShield {
            // 0 usage → 100% protection; at the limit → ~40%; well over → floors out near 5%.
            val percent = (100f - reelRatio * 60f).coerceIn(5f, 100f).toInt()
            val status = when {
                reelRatio < 0.5f -> ShieldStatus.ACTIVE
                reelRatio < 0.85f -> ShieldStatus.WEAK
                reelRatio < 1.25f -> ShieldStatus.DAMAGED
                else -> ShieldStatus.BROKEN
            }
            return FocusShield(status, percent)
        }
    }
}
