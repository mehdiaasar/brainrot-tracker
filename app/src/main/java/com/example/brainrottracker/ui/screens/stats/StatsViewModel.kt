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
import kotlin.math.roundToInt

data class WeeklyStats(
    val dailyLogs: List<DailyLog> = emptyList(),
    val totalReels: Int = 0,
    val totalMinutes: Int = 0,
    val productivityScore: Int = 100,
    val bestDay: DailyLog? = null,
    val worstDay: DailyLog? = null,
    val healthByDate: Map<String, Int> = emptyMap(),
    val topApps: List<Pair<Platform, Int>> = emptyList(),
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository
    private val app = application

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    // Fetch week screen time from UsageStats once per subscription (no need to poll here)
    private val weekScreenTime = flow {
        emit(ScreenTimeHelper.getWeekMinutesByPlatform(app))
    }.flowOn(Dispatchers.IO)

    val weeklyStats: StateFlow<WeeklyStats> =
        combine(repository.getWeeklyLogs(), repository.getLimits(), weekScreenTime) { logs, limits, weekMinutes ->
            val totalReels = logs.sumOf { it.getTotalReels() }
            val totalMinutes = weekMinutes.values.sum()

            val healthByDate = logs.associate { log ->
                log.date to repository.calculateBrainHealth(log, limits)
            }

            val productivityScore = if (healthByDate.isEmpty()) 100
            else healthByDate.values.average().roundToInt().coerceIn(0, 100)

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
