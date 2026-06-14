package com.example.brainrottracker.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.util.ScreenTimeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.roundToInt

data class WeeklyStats(
    val dailyLogs: List<DailyLog> = emptyList(),
    val totalReels: Int = 0,
    val totalMinutes: Int = 0,
    val dailyAverageMinutes: Int = 0,
    val productivityScore: Int = 100,
    val bestDay: DailyLog? = null,
    val worstDay: DailyLog? = null,
    val bestDayMinutes: Int = 0,
    val worstDayMinutes: Int = 0,
    val healthByDate: Map<String, Int> = emptyMap(),
    /** Per-day, per-platform screen-time minutes for the current week (keyed by ISO date). */
    val minutesByDate: Map<String, Map<Platform, Int>> = emptyMap(),
    val topApps: List<Pair<Platform, Int>> = emptyList(),
    /** Week-over-week change, in percent (positive = up vs last week). null = no baseline. */
    val reelsDeltaPct: Int? = null,
    val timeDeltaPct: Int? = null,
    val avgDeltaPct: Int? = null,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository
    private val app = application

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    // Per-day, per-platform minutes across this week + the previous week (14 days), fetched
    // once per subscription. Sliced into "this week" / "last week" below.
    private val dailyMinutes = flow {
        emit(ScreenTimeHelper.getDailyMinutesByPlatform(app, 14))
    }.flowOn(Dispatchers.IO)

    val weeklyStats: StateFlow<WeeklyStats> =
        combine(
            repository.getWeeklyLogs(),
            repository.getPreviousWeekLogs(),
            repository.getLimits(),
            dailyMinutes,
        ) { logs, prevLogs, limits, daily ->
            val today = LocalDate.now()
            val thisWeekDates = (0..6).map { today.minusDays(it.toLong()).toString() }.toSet()
            val lastWeekDates = (7..13).map { today.minusDays(it.toLong()).toString() }.toSet()

            val minutesByDate = daily.filterKeys { it in thisWeekDates }
            val dayTotals = minutesByDate.mapValues { it.value.values.sum() }
            val totalMinutes = dayTotals.values.sum()
            val lastWeekMinutes = daily.filterKeys { it in lastWeekDates }
                .values.sumOf { it.values.sum() }

            val totalReels = logs.sumOf { it.getTotalReels() }
            val lastWeekReels = prevLogs.sumOf { it.getTotalReels() }

            // Daily average over the 7-day window.
            val dailyAverageMinutes = totalMinutes / 7
            val lastWeekAvg = lastWeekMinutes / 7

            val healthByDate = logs.associate { log ->
                log.date to repository.calculateBrainHealth(log, limits)
            }
            val productivityScore = if (healthByDate.isEmpty()) 100
            else healthByDate.values.average().roundToInt().coerceIn(0, 100)

            val topApps = Platform.entries
                .map { p -> p to logs.sumOf { it.getReelsForPlatform(p) } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            // Best day = least scrolled; worst = most scrolled. Only consider days with usage.
            val active = logs.filter { it.getTotalReels() > 0 }
            val bestDay = active.minByOrNull { it.getTotalReels() }
            val worstDay = active.maxByOrNull { it.getTotalReels() }

            WeeklyStats(
                dailyLogs = logs,
                totalReels = totalReels,
                totalMinutes = totalMinutes,
                dailyAverageMinutes = dailyAverageMinutes,
                productivityScore = productivityScore,
                bestDay = bestDay,
                worstDay = worstDay,
                bestDayMinutes = bestDay?.let { dayTotals[it.date] } ?: 0,
                worstDayMinutes = worstDay?.let { dayTotals[it.date] } ?: 0,
                healthByDate = healthByDate,
                minutesByDate = minutesByDate,
                topApps = topApps,
                reelsDeltaPct = percentChange(totalReels, lastWeekReels),
                timeDeltaPct = percentChange(totalMinutes, lastWeekMinutes),
                avgDeltaPct = percentChange(dailyAverageMinutes, lastWeekAvg),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyStats())

    /** Week-over-week change as a rounded percent, or null when there's no baseline to compare. */
    private fun percentChange(current: Int, previous: Int): Int? {
        if (previous <= 0) return null
        return ((current - previous) * 100f / previous).roundToInt()
    }
}
