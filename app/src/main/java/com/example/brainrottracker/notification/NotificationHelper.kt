package com.example.brainrottracker.notification

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Build
import androidx.core.app.NotificationCompat
import com.example.brainrottracker.MainActivity
import com.example.brainrottracker.R
import com.example.brainrottracker.ui.screens.dashboard.DashboardMood

class NotificationHelper(private val context: Context) {

    companion object {
        const val CHANNEL_MILESTONES = "reel_milestones"
        const val CHANNEL_DAILY_SUMMARY = "daily_summary"
        const val CHANNEL_SERVICE = "service_running"
        const val SERVICE_NOTIFICATION_ID = 1001
        const val MILESTONE_NOTIFICATION_ID = 2000
        const val DAILY_SUMMARY_ID = 3001

        private val MILESTONES = listOf(25, 50, 75, 100, 150, 200)
    }

    private val shownMilestones = mutableSetOf<Int>()

    fun createChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(NotificationManager::class.java)

            val milestoneChannel = NotificationChannel(
                CHANNEL_MILESTONES,
                "Reel Milestones",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Alerts when you hit reel watching milestones"
                enableVibration(true)
            }

            val summaryChannel = NotificationChannel(
                CHANNEL_DAILY_SUMMARY,
                "Daily Summary",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = "End-of-day usage summary"
            }

            val serviceChannel = NotificationChannel(
                CHANNEL_SERVICE,
                "Tracking Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when LoopOut is actively monitoring"
                setShowBadge(false)
            }

            manager.createNotificationChannel(milestoneChannel)
            manager.createNotificationChannel(summaryChannel)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    fun checkMilestone(totalReels: Int) {
        val milestone = MILESTONES.firstOrNull { it == totalReels && it !in shownMilestones }
            ?: return

        shownMilestones.add(milestone)

        val (emoji, message) = when (milestone) {
            25 -> "🧠" to "You've watched 25 reels — doing okay!"
            50 -> "⚠️" to "50 reels! Time for a break?"
            75 -> "🟠" to "75 reels — your brain needs rest!"
            100 -> "🔴" to "100 reels! Consider closing the app"
            150 -> "💀" to "150 reels — serious doomscrolling alert!"
            200 -> "🚨" to "200 reels! Your brain is melting!"
            else -> "📱" to "You've watched $milestone reels today"
        }

        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
            .setSmallIcon(R.drawable.ic_brain_notification)
            .setContentTitle("$emoji LoopOut Alert")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(MILESTONE_NOTIFICATION_ID + milestone, notification)
    }

    fun showDailySummary(totalReels: Int, totalMinutes: Int, brainHealth: Int) {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val healthEmoji = when {
            brainHealth >= 75 -> "🟢"
            brainHealth >= 50 -> "🟡"
            brainHealth >= 25 -> "🟠"
            else -> "🔴"
        }

        val hours = totalMinutes / 60
        val mins = totalMinutes % 60
        val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"

        val notification = NotificationCompat.Builder(context, CHANNEL_DAILY_SUMMARY)
            .setSmallIcon(R.drawable.ic_brain_notification)
            .setContentTitle("📊 Today's Recap")
            .setContentText("$totalReels reels • $timeStr screen time • $healthEmoji $brainHealth% brain health")
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText("$totalReels reels watched\n$timeStr screen time\n$healthEmoji Brain Health: $brainHealth%\n\nKeep working on your digital habits!")
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        val manager = context.getSystemService(NotificationManager::class.java)
        manager.notify(DAILY_SUMMARY_ID, notification)
    }

    /**
     * Ongoing-service notification builder. Currently unused: the overlay was moved off a foreground
     * service into [com.example.brainrottracker.service.OverlayController] (drawn directly from the
     * accessibility service) to avoid MIUI/HyperOS killing the service. Kept for now in case a
     * persistent status notification is reintroduced.
     */
    fun getServiceNotification(
        mood: DashboardMood = DashboardMood.GREAT,
        total: Int = 0,
        health: Int = 100,
    ): Notification {
        val intent = Intent(context, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val text = if (total > 0) "$total reels today • $health% brain health"
        else "Monitoring your screen time"

        val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_brain_notification)
            .setContentTitle("LoopOut Active")
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .setOngoing(true)

        BitmapFactory.decodeResource(context.resources, mood.mainRes)?.let { builder.setLargeIcon(it) }
        return builder.build()
    }

    fun resetDailyMilestones() {
        shownMilestones.clear()
    }
}
