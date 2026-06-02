package com.example.brainrottracker.ui.screens.limits

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.repository.UsageRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LimitsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val limits: StateFlow<List<UserLimits>> = repository.getLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val todayLog: StateFlow<DailyLog?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    fun updateLimit(limits: UserLimits) {
        viewModelScope.launch {
            repository.updateLimits(limits)
        }
    }
}
