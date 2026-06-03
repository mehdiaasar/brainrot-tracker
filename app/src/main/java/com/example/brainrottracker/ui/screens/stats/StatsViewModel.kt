package com.example.brainrottracker.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class WeeklyStats(
    val dailyLogs: List<DailyLog> = emptyList(),
    val totalReels: Int = 0,
    val totalMinutes: Int = 0,
    val productivityScore: Int = 100,
    val bestDay: DailyLog? = null,
    val worstDay: DailyLog? = null,
    /** Brain-health (0..100) for each logged day, keyed by date string. */
    val healthByDate: Map<String, Int> = emptyMap(),
    /** Platforms used this week with their total reel counts, highest first. */
    val topApps: List<Pair<Platform, Int>> = emptyList(),
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val weeklyStats: StateFlow<WeeklyStats> =
        combine(repository.getWeeklyLogs(), repository.getLimits()) { logs, limits ->
            val totalReels = logs.sumOf { it.getTotalReels() }
            val totalMinutes = logs.sumOf { it.getTotalMinutes() }
            val productivityScore = if (logs.isEmpty()) 100
            else (100 - (totalReels.toFloat() / (logs.size * 30) * 100).toInt()).coerceIn(0, 100)

            val healthByDate = logs.associate { it.date to repository.calculateBrainHealth(it, limits) }

            val topApps = Platform.entries
                .map { p -> p to logs.sumOf { it.getReelsForPlatform(p) } }
                .filter { it.second > 0 }
                .sortedByDescending { it.second }

            WeeklyStats(
                dailyLogs = logs,
                totalReels = totalReels,
                totalMinutes = totalMinutes,
                productivityScore = productivityScore,
                bestDay = logs.minByOrNull { it.getTotalReels() },
                worstDay = logs.maxByOrNull { it.getTotalReels() },
                healthByDate = healthByDate,
                topApps = topApps
            )
        }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyStats())
}
