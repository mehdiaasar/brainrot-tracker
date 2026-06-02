package com.example.brainrottracker.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.action.clickable
import androidx.glance.action.actionStartActivity
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
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
import java.time.LocalDate

class BrainRotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val todayLog = db.dailyLogDao().getByDateOnce(LocalDate.now().toString())

        provideContent {
            WidgetContent(todayLog = todayLog)
        }
    }

    @Composable
    fun WidgetContent(todayLog: DailyLog?) {
        val totalReels = todayLog?.getTotalReels() ?: 0
        val healthScore = todayLog?.brainHealthScore ?: 100

        val bgColor = ColorProvider(Color(0xFF1A1A2E))
        val textColor = ColorProvider(Color.White)
        val cyanColor = ColorProvider(Color(0xFF00D2FF))
        val subtextColor = ColorProvider(Color.White.copy(alpha = 0.7f))

        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(bgColor)
                .padding(12.dp)
                .clickable(actionStartActivity<MainActivity>()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🧠 BrainRot",
                style = TextStyle(
                    color = cyanColor,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )
            )

            Spacer(GlanceModifier.height(8.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$totalReels",
                        style = TextStyle(
                            color = textColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "reels",
                        style = TextStyle(
                            color = subtextColor,
                            fontSize = 11.sp,
                        )
                    )
                }

                Spacer(GlanceModifier.width(16.dp))

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    val healthColor = ColorProvider(
                        when {
                            healthScore >= 75 -> Color(0xFF38EF7D)
                            healthScore >= 50 -> Color(0xFFFFD93D)
                            else -> Color(0xFFFF6B6B)
                        }
                    )
                    Text(
                        text = "$healthScore%",
                        style = TextStyle(
                            color = healthColor,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    Text(
                        text = "health",
                        style = TextStyle(
                            color = subtextColor,
                            fontSize = 11.sp,
                        )
                    )
                }
            }
        }
    }
}
