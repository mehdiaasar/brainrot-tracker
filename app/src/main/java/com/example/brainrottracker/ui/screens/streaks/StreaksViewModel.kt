package com.example.brainrottracker.ui.screens.streaks

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import com.example.brainrottracker.data.repository.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class StreakUiState(
    val records: List<StreakRecord> = emptyList(),
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val streakMap: Map<String, Boolean> = emptyMap(),
)

class StreaksViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val streakState: StateFlow<StreakUiState> = repository.getStreakData()
        .map { records ->
            StreakUiState(
                records = records,
                currentStreak = repository.calculateCurrentStreak(records),
                longestStreak = repository.calculateLongestStreak(records),
                streakMap = records.associate { it.date to it.underLimit }
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), StreakUiState())
}
