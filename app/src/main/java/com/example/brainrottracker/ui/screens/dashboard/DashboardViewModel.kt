package com.example.brainrottracker.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.service.ReelCounterService
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.abs

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val todayLog: StateFlow<DailyLog?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val limits: StateFlow<List<UserLimits>> = repository.getLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val brainHealth: StateFlow<Int> = repository.getTodayLog()
        .map { log ->
            if (log != null) {
                val currentLimits = limits.value
                repository.calculateBrainHealth(log, currentLimits)
            } else 100
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val isTrackingActive: Boolean
        get() = ReelCounterService.isRunning

    // Yesterday's Log
    val yesterdayLog: StateFlow<DailyLog?> = repository.getLogForDate(LocalDate.now().minusDays(1).toString())
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Current Streak
    val currentStreak: StateFlow<Int> = repository.getStreakData()
        .map { records -> repository.calculateCurrentStreak(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    // Progress Psychology Insights
    val insights: StateFlow<List<String>> = combine(
        todayLog,
        yesterdayLog,
        limits,
        currentStreak
    ) { today, yesterday, currentLimits, streak ->
        val list = mutableListOf<String>()

        // 1. Streak Insight
        if (streak > 0) {
            list.add("🔥 You are on a $streak-day streak! Keep protecting your focus.")
        } else {
            list.add("✨ Start a new focus streak today by staying under your limits.")
        }

        // 2. Avoided Reels Calculation
        val totalLimit = if (currentLimits.isNotEmpty()) {
            currentLimits.sumOf { it.dailyReelLimit }
        } else {
            120 // Default limits sum (30 reels * 4 platforms)
        }
        val todayReels = today?.getTotalReels() ?: 0
        if (todayReels < totalLimit) {
            val avoided = totalLimit - todayReels
            list.add("🛡️ You avoided $avoided reels/videos today.")
        } else {
            list.add("⚠️ Daily limits reached. Time to close the feeds and rest your eyes.")
        }

        // 3. Screen Time Comparison with Yesterday
        val todayMins = today?.getTotalMinutes() ?: 0
        val yesterdayMins = yesterday?.getTotalMinutes() ?: 0
        if (yesterdayMins > 0) {
            val diff = yesterdayMins - todayMins
            if (diff > 0) {
                val percent = (diff * 100) / yesterdayMins
                list.add("📈 Focus improved: $percent% less screen time than yesterday!")
            } else if (diff < 0) {
                val percent = (abs(diff) * 100) / yesterdayMins
                list.add("📱 Screen time is $percent% higher than yesterday. Step away when ready.")
            } else {
                list.add("⚖️ Screen time matches yesterday. Try reducing it by 5 minutes!")
            }
        } else if (todayMins > 0) {
            list.add("🌱 Building healthy habits today. You've logged $todayMins minutes.")
        } else {
            list.add("💎 Pure focus today. Zero minutes of short-form content watched.")
        }

        // 4. Score-based generic insight
        val health = if (today != null) repository.calculateBrainHealth(today, currentLimits) else 100
        if (health >= 90) {
            list.add("🚀 Your brain health is elite! Your concentration is razor sharp.")
        } else if (health >= 75) {
            list.add("🧠 You're keeping your mind clean and mindful today.")
        }

        list
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf("✨ Start your wellness journey today."))

    fun getMotivationalMessage(score: Int): String = when {
        score >= 90 -> "🌟 Elite Focus. Your brain is thriving!"
        score >= 75 -> "💪 Healthy Mind. Stay mindful."
        score >= 50 -> "😐 Moderate Usage. Take a brief break."
        score >= 25 -> "⚠️ High Brainrot. Your mind needs rest."
        else -> "🚨 Critical Damage. Close those apps now!"
    }
}
