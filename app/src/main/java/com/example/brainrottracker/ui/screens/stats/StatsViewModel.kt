package com.example.brainrottracker.ui.screens.stats

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.repository.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class WeeklyStats(
    val dailyLogs: List<DailyLog> = emptyList(),
    val totalReels: Int = 0,
    val totalMinutes: Int = 0,
    val productivityScore: Int = 100,
    val bestDay: DailyLog? = null,
    val worstDay: DailyLog? = null,
)

class StatsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val weeklyStats: StateFlow<WeeklyStats> = repository.getWeeklyLogs()
        .map { logs ->
            val totalReels = logs.sumOf { it.getTotalReels() }
            val totalMinutes = logs.sumOf { it.getTotalMinutes() }
            val productivityScore = if (logs.isEmpty()) 100
            else (100 - (totalReels.toFloat() / (logs.size * 30) * 100).toInt()).coerceIn(0, 100)
            WeeklyStats(
                dailyLogs = logs,
                totalReels = totalReels,
                totalMinutes = totalMinutes,
                productivityScore = productivityScore,
                bestDay = logs.minByOrNull { it.getTotalReels() },
                worstDay = logs.maxByOrNull { it.getTotalReels() }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), WeeklyStats())
}
