package com.example.brainrottracker.data.util

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process
import com.example.brainrottracker.data.model.Platform
import java.util.Calendar

object ScreenTimeHelper {

    /** True if the user has granted Usage Access via Settings → Apps → Special app access. */
    fun hasPermission(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    /** Total foreground minutes per platform since midnight today. */
    fun getTodayMinutesByPlatform(context: Context): Map<Platform, Int> {
        val midnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return queryMinutesFromEvents(context, midnight, System.currentTimeMillis())
    }

    /** Total foreground minutes per platform for yesterday (midnight-to-midnight). */
    fun getYesterdayMinutesByPlatform(context: Context): Map<Platform, Int> {
        val todayMidnight = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val yesterdayMidnight = todayMidnight - 24L * 60 * 60 * 1000
        return queryMinutesFromEvents(context, yesterdayMidnight, todayMidnight)
    }

    /** Total foreground minutes per platform over the last 7 days. */
    fun getWeekMinutesByPlatform(context: Context): Map<Platform, Int> {
        val startMs = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return queryMinutesFromEvents(context, startMs, System.currentTimeMillis())
    }

    /**
     * Sums foreground time precisely by processing raw activity events.
     * Unlike queryAndAggregateUsageStats, this respects exact window boundaries
     * and won't bleed sessions from outside the window.
     */
    private fun queryMinutesFromEvents(
        context: Context,
        startMs: Long,
        endMs: Long
    ): Map<Platform, Int> {
        if (!hasPermission(context)) return Platform.entries.associateWith { 0 }
        val platformPackages = Platform.entries.map { it.packageName }.toSet()
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val events = usm.queryEvents(startMs, endMs)
            val event = UsageEvents.Event()
            val foregroundStart = mutableMapOf<String, Long>()
            val totalMs = mutableMapOf<String, Long>()

            while (events.hasNextEvent()) {
                events.getNextEvent(event)
                val pkg = event.packageName
                if (pkg !in platformPackages) continue
                when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> {
                        if (pkg !in foregroundStart) foregroundStart[pkg] = event.timeStamp
                    }
                    UsageEvents.Event.ACTIVITY_PAUSED,
                    UsageEvents.Event.ACTIVITY_STOPPED -> {
                        foregroundStart.remove(pkg)?.let { start ->
                            totalMs[pkg] = (totalMs[pkg] ?: 0L) + (event.timeStamp - start)
                        }
                    }
                }
            }

            // Any app still in foreground at the end of the window
            for ((pkg, start) in foregroundStart) {
                totalMs[pkg] = (totalMs[pkg] ?: 0L) + (endMs - start)
            }

            Platform.entries.associateWith { platform ->
                ((totalMs[platform.packageName] ?: 0L) / 60_000L).toInt()
            }
        } catch (_: Exception) {
            Platform.entries.associateWith { 0 }
        }
    }
}
