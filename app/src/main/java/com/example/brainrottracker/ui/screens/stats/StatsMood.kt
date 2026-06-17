package com.example.brainrottracker.ui.screens.stats

import androidx.compose.ui.graphics.Color
import com.example.brainrottracker.R
import com.example.brainrottracker.theme.StatsBgCriticalDark
import com.example.brainrottracker.theme.StatsBgCriticalLight
import com.example.brainrottracker.theme.StatsBgExcellentDark
import com.example.brainrottracker.theme.StatsBgExcellentLight
import com.example.brainrottracker.theme.StatsBgFairDark
import com.example.brainrottracker.theme.StatsBgFairLight
import com.example.brainrottracker.theme.StatsBgGoodDark
import com.example.brainrottracker.theme.StatsBgGoodLight
import com.example.brainrottracker.theme.StatsBgLowDark
import com.example.brainrottracker.theme.StatsBgLowFooterDark
import com.example.brainrottracker.theme.StatsBgLowFooterLight
import com.example.brainrottracker.theme.StatsBgLowLight
import com.example.brainrottracker.theme.StatsCritical
import com.example.brainrottracker.theme.StatsExcellent
import com.example.brainrottracker.theme.StatsFair
import com.example.brainrottracker.theme.StatsFooterLowAccent
import com.example.brainrottracker.theme.StatsGood
import com.example.brainrottracker.theme.StatsLow

/**
 * The five Stats & Reports variations. Each tier of the weekly productivity score swaps the hero
 * brain + gauge accent, the chart's peeking brain, and the footer card (brain, accent, copy),
 * mirroring the five mock screens. Best/Worst-day brains are shared across tiers (per design).
 *
 * Brain art lives in res/drawable-nodpi (stats_t{1..5}_*), rendered from the `stats/stats {1..5}`
 * source folders. Tier is picked from the weekly productivity score — see [fromScore].
 */
enum class StatsMood(
    val accent: Color,
    val heroBgLight: Color,
    val heroBgDark: Color,
    val heroRes: Int,
    val headline: String,
    /** Two short support lines under the headline. */
    val subline1: String,
    val subline2: String,
    /** Small brain peeking beside the latest bar of the weekly chart. */
    val peekRes: Int,
    // ── Footer encouragement card ──
    val footerRes: Int,
    val footerAccent: Color,
    val footerBgLight: Color,
    val footerBgDark: Color,
    /** "star" → ⭐ in an accent circle (positive); "warn" → ⚠️ (caution). */
    val footerIconStar: Boolean,
    val footerTitle: String,
    val footerBody: String,
    /** Optional accent-colored closing line (null = none). */
    val footerTail: String?,
) {
    EXCELLENT(
        accent = StatsExcellent,
        heroBgLight = StatsBgExcellentLight,
        heroBgDark = StatsBgExcellentDark,
        heroRes = R.drawable.stats_t1_hero,
        headline = "Excellent focus today! 🎉",
        subline1 = "You're doing amazing.",
        subline2 = "Keep protecting your focus!",
        peekRes = R.drawable.stats_t1_peek,
        footerRes = R.drawable.stats_t1_footer,
        footerAccent = StatsExcellent,
        footerBgLight = StatsBgExcellentLight,
        footerBgDark = StatsBgExcellentDark,
        footerIconStar = true,
        footerTitle = "You're on the right track! 🚀",
        footerBody = "Keep building your focus. Small habits create big changes.",
        footerTail = null,
    ),
    GOOD(
        accent = StatsGood,
        heroBgLight = StatsBgGoodLight,
        heroBgDark = StatsBgGoodDark,
        heroRes = R.drawable.stats_t2_hero,
        headline = "Focus slipped a little. 😟",
        subline1 = "You can do better!",
        subline2 = "Let's stay mindful.",
        peekRes = R.drawable.stats_t2_peek,
        footerRes = R.drawable.stats_t2_footer,
        footerAccent = StatsGood,
        footerBgLight = StatsBgGoodLight,
        footerBgDark = StatsBgGoodDark,
        footerIconStar = true,
        footerTitle = "You're close to your best!",
        footerBody = "A little more focus today, a stronger tomorrow. 💪",
        footerTail = null,
    ),
    FAIR(
        accent = StatsFair,
        heroBgLight = StatsBgFairLight,
        heroBgDark = StatsBgFairDark,
        heroRes = R.drawable.stats_t3_hero,
        headline = "Focus needs attention. 😟",
        subline1 = "Screen time is taking over.",
        subline2 = "Let's get back on track!",
        peekRes = R.drawable.stats_t3_peek,
        footerRes = R.drawable.stats_t3_footer,
        footerAccent = StatsFair,
        footerBgLight = StatsBgFairLight,
        footerBgDark = StatsBgFairDark,
        footerIconStar = false,
        footerTitle = "Too much scrolling this week.",
        footerBody = "Your brain is tired. Take a break and reset.",
        footerTail = "Small pause > Big change.",
    ),
    LOW(
        accent = StatsLow,
        heroBgLight = StatsBgLowLight,
        heroBgDark = StatsBgLowDark,
        heroRes = R.drawable.stats_t4_hero,
        headline = "Focus needs attention. 😟",
        subline1 = "Let's get back on track",
        subline2 = "together!",
        peekRes = R.drawable.stats_t4_peek,
        footerRes = R.drawable.stats_t4_footer,
        footerAccent = StatsFooterLowAccent,
        footerBgLight = StatsBgLowFooterLight,
        footerBgDark = StatsBgLowFooterDark,
        footerIconStar = true,
        footerTitle = "It's okay to have off days.",
        footerBody = "You've got this. One better day can change your whole week. 💜",
        footerTail = null,
    ),
    CRITICAL(
        accent = StatsCritical,
        heroBgLight = StatsBgCriticalLight,
        heroBgDark = StatsBgCriticalDark,
        heroRes = R.drawable.stats_t5_hero,
        headline = "Focus is critically low. 😟",
        subline1 = "Your brain needs a reset.",
        subline2 = "Let's take back control!",
        peekRes = R.drawable.stats_t5_hero,
        footerRes = R.drawable.stats_t5_footer,
        footerAccent = StatsCritical,
        footerBgLight = StatsBgCriticalLight,
        footerBgDark = StatsBgCriticalDark,
        footerIconStar = false,
        footerTitle = "It's okay, everyone has off days.",
        footerBody = "But your brain needs rest and balance. Take a break, breathe, and reset.",
        footerTail = "Small steps today, big changes tomorrow. ❤️",
    );

    companion object {
        /** @param score the weekly productivity score, 0..100. */
        fun fromScore(score: Int): StatsMood = when {
            score >= 80 -> EXCELLENT
            score >= 65 -> GOOD
            score >= 45 -> FAIR
            score >= 30 -> LOW
            else -> CRITICAL
        }
    }
}
