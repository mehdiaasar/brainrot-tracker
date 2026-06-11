package com.example.brainrottracker.ui.screens.streaks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.util.ScreenTimeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class StreakUiState(
    val records: List<StreakRecord> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMap: Map<String, Boolean> = emptyMap(),
    val hasZeroReelDay: Boolean = false,
    val hasHalfHourDay: Boolean = false,
)

class StreaksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    // Yesterday's screen-time minutes from UsageStats (minutes aren't stored in the DB)
    private val yesterdayMinutes = flow {
        emit(ScreenTimeHelper.getYesterdayMinutesByPlatform(getApplication()).values.sum())
    }.flowOn(Dispatchers.IO)

    val streakState: StateFlow<StreakUiState> = combine(
        repository.getStreakData(),
        repository.getMonthlyLogs(),
        yesterdayMinutes
    ) { records, logs, minutes ->
        // A "Zero Reel Day" counts only for days that were actually tracked
        // (a log row exists), so empty/untouched days don't unlock it for free.
        val hasZeroReelDay = logs.any { it.getTotalReels() == 0 }
        val hasHalfHourDay = minutes in 1..30
        StreakUiState(
            records = records,
            currentStreak = repository.calculateCurrentStreak(records),
            longestStreak = repository.calculateLongestStreak(records),
            streakMap = records.associate { it.date to it.underLimit },
            hasZeroReelDay = hasZeroReelDay,
            hasHalfHourDay = hasHalfHourDay
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakUiState())
}
