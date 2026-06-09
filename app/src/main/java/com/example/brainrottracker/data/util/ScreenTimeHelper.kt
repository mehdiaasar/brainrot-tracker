package com.example.brainrottracker.data.util

import android.app.usage.UsageStatsManager
import android.content.Context
import com.example.brainrottracker.data.model.Platform
import java.util.Calendar

object ScreenTimeHelper {

    /** Returns true if the PACKAGE_USAGE_STATS permission has been granted by the user. */
    fun hasPermission(context: Context): Boolean {
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryAndAggregateUsageStats(now - 60_000L, now)
        return stats != null && stats.isNotEmpty()
    }

    /** Total foreground minutes per platform since midnight today. */
    fun getTodayMinutesByPlatform(context: Context): Map<Platform, Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        return queryMinutes(context, cal.timeInMillis)
    }

    /** Total foreground minutes per platform for yesterday (midnight-to-midnight). */
    fun getYesterdayMinutesByPlatform(context: Context): Map<Platform, Int> {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val endOfYesterday = cal.timeInMillis
        val startOfYesterday = endOfYesterday - 24L * 60 * 60 * 1000
        return queryMinutes(context, startOfYesterday, endOfYesterday)
    }

    /** Total foreground minutes per platform over the last 7 days. */
    fun getWeekMinutesByPlatform(context: Context): Map<Platform, Int> {
        val startMs = System.currentTimeMillis() - 7L * 24 * 60 * 60 * 1000
        return queryMinutes(context, startMs)
    }

    private fun queryMinutes(
        context: Context,
        startMs: Long,
        endMs: Long = System.currentTimeMillis()
    ): Map<Platform, Int> {
        return try {
            val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val stats = usm.queryAndAggregateUsageStats(startMs, endMs)
            Platform.entries.associateWith { platform ->
                (stats[platform.packageName]?.totalTimeInForeground ?: 0L)
                    .div(60_000L).toInt()
            }
        } catch (_: Exception) {
            Platform.entries.associateWith { 0 }
        }
    }
}
