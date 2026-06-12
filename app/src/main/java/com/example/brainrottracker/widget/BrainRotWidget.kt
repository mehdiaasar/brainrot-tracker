package com.example.brainrottracker.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.example.brainrottracker.MainActivity
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.ui.screens.dashboard.DashboardMood
import java.time.LocalDate

class BrainRotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val todayLog = db.dailyLogDao().getByDateOnce(LocalDate.now().toString())
        val limits = db.userLimitsDao().getAllOnce()
        val reelLimit = limits.firstOrNull()?.dailyReelLimit ?: 50
        val minuteLimit = limits.firstOrNull()?.dailyMinuteLimit ?: 60
        val totalReels = todayLog?.getTotalReels() ?: 0
        // Live screen-time + health, the same way the dashboard computes them — the stored
        // brainHealthScore is never refreshed, which is why the widget always showed 100%.
        val minuteMap = ScreenTimeHelper.getTodayMinutesByPlatform(context)
        val minutes = minuteMap.values.sum()
        val reelRatio = if (reelLimit > 0) totalReels.toFloat() / reelLimit else 0f
        val minuteRatio = if (minuteLimit > 0) minutes.toFloat() / minuteLimit else 0f
        val mood = DashboardMood.fromUsage(reelRatio, minuteRatio)
        val health = if (todayLog != null) {
            UsageRepository(db).calculateBrainHealth(todayLog, limits, minuteMap)
        } else 100
        val dark = resolveIsDark(context)

        provideContent {
            WidgetContent(todayLog = todayLog, mood = mood, health = health, dark = dark)
        }
    }

    /** Same theme resolution as the floating HUD: user pref, falling back to system. */
    private fun resolveIsDark(context: Context): Boolean {
        val prefs = context.getSharedPreferences("brainrot_prefs", Context.MODE_PRIVATE)
        return when (prefs.getString("theme_mode", "SYSTEM")) {
            "LIGHT" -> false
            "DARK" -> true
            else -> {
                val night = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
                night == Configuration.UI_MODE_NIGHT_YES
            }
        }
    }

    @Composable
    fun WidgetContent(todayLog: DailyLog?, mood: DashboardMood, health: Int, dark: Boolean) {
        val totalReels = todayLog?.getTotalReels() ?: 0
        val healthScore = health

        // Warm palette, mirroring the app/HUD colors
        val bgColor = ColorProvider(if (dark) Color(0xFF252320) else Color(0xFFEFE9DE))
        val textColor = ColorProvider(if (dark) Color(0xFFFAF9F5) else Color(0xFF141413))
        val subtextColor = ColorProvider(if (dark) Color(0xFFA09D96) else Color(0xFF6C6A64))
        // Score color follows the mood accent, like the dashboard score ring.
        val healthColor = ColorProvider(mood.accent)

        // Brain and stats each take a weighted half of the width, so the brain stays big
        // (height-bound) on normal/wide widgets and grows with the cell, while the stats are
        // always visible — even on small cells — instead of being pushed off the right edge.
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .cornerRadius(20.dp)
                .padding(horizontal = 10.dp, vertical = 4.dp)
                .clickable(actionStartActivity<MainActivity>()),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Image(
                provider = ImageProvider(mood.mainRes),
                contentDescription = mood.headline,
                modifier = GlanceModifier.fillMaxHeight().defaultWeight()
            )

            Spacer(GlanceModifier.width(8.dp))

            Column(
                modifier = GlanceModifier.defaultWeight(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$totalReels",
                        style = TextStyle(color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(text = "reels", style = TextStyle(color = subtextColor, fontSize = 11.sp))
                }
                Spacer(GlanceModifier.height(4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "$healthScore%",
                        style = TextStyle(color = healthColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                    )
                    Spacer(GlanceModifier.width(5.dp))
                    Text(text = "health", style = TextStyle(color = subtextColor, fontSize = 11.sp))
                }
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = mood.scoreCaption,
                    style = TextStyle(color = subtextColor, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                )
            }
        }
    }
}
