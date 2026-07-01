package com.example.brainrottracker.ui.screens.dashboard

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.service.ReelCounterService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import kotlin.math.abs

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository
    private val app = application

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }

    val todayLog: StateFlow<DailyLog?> = repository.getTodayLog()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val limits: StateFlow<List<UserLimits>> = repository.getLimits()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Live UsageStats for today — refreshes every 30 seconds
    private val todayScreenTime: Flow<Map<Platform, Int>> = flow {
        while (true) {
            emit(ScreenTimeHelper.getTodayMinutesByPlatform(app))
            delay(30_000)
        }
    }.flowOn(Dispatchers.IO)

    // Live UsageStats for yesterday (one-shot per subscription)
    private val yesterdayScreenTime: Flow<Map<Platform, Int>> = flow {
        emit(ScreenTimeHelper.getYesterdayMinutesByPlatform(app))
    }.flowOn(Dispatchers.IO)

    /** Total foreground minutes across all platforms today (from system UsageStats). */
    val screenTimeToday: StateFlow<Int> = todayScreenTime
        .map { it.values.sum() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val brainHealth: StateFlow<Int> = combine(
        repository.getTodayLog(),
        limits
    ) { log, currentLimits ->
        if (log != null) repository.calculateBrainHealth(log, currentLimits)
        else 100
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)

    val isTrackingActive: Boolean
        get() = ReelCounterService.isRunning

    /** Daily video limit shown on the dashboard (matches the Limits screen default). */
    val reelLimit: StateFlow<Int> = limits
        .map { it.firstOrNull()?.dailyReelLimit ?: 50 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 50)

    /** Daily time limit in minutes. */
    val minuteLimit: StateFlow<Int> = limits
        .map { it.firstOrNull()?.dailyMinuteLimit ?: 60 }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)

    /** Today's reels as a fraction of the daily reel limit. Reels-only, matching the health score. */
    private val reelRatio: Flow<Float> = combine(todayLog, reelLimit) { log, reels ->
        if (reels > 0) (log?.getTotalReels() ?: 0) / reels.toFloat() else 0f
    }

    /** Which of the five dashboard variations to show, from reel usage vs. limit. */
    val mood: StateFlow<DashboardMood> = reelRatio
        .map { DashboardMood.fromUsage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), DashboardMood.GREAT)

    /** Focus Shield protection level + status, from the same reel ratio as [mood]. */
    val focusShield: StateFlow<FocusShield> = reelRatio
        .map { FocusShield.fromUsage(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), FocusShield(ShieldStatus.ACTIVE, 100))

    /** Daily-goal tracker: true while reels are under their limit. */
    val dailyGoalOnTrack: StateFlow<Boolean> = reelRatio
        .map { it < 1f }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    val yesterdayLog: StateFlow<DailyLog?> =
        repository.getLogForDate(LocalDate.now().minusDays(1).toString())
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val currentStreak: StateFlow<Int> = repository.getStreakData()
        .map { records -> repository.calculateCurrentStreak(records) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val insights: StateFlow<List<String>> = combine(
        todayLog,
        limits,
        currentStreak,
        todayScreenTime,
        yesterdayScreenTime
    ) { args ->
        @Suppress("UNCHECKED_CAST")
        val today = args[0] as DailyLog?
        @Suppress("UNCHECKED_CAST")
        val currentLimits = args[1] as List<UserLimits>
        val streak = args[2] as Int
        @Suppress("UNCHECKED_CAST")
        val todayMins = (args[3] as Map<Platform, Int>).values.sum()
        @Suppress("UNCHECKED_CAST")
        val yesterdayMins = (args[4] as Map<Platform, Int>).values.sum()

        val list = mutableListOf<String>()

        if (streak > 0) {
            list.add("🔥 You are on a $streak-day streak! Keep protecting your focus.")
        } else {
            list.add("✨ Start a new focus streak today by staying under your limits.")
        }

        val totalLimit = if (currentLimits.isNotEmpty()) currentLimits.sumOf { it.dailyReelLimit } else 120
        val todayReels = today?.getTotalReels() ?: 0
        if (todayReels < totalLimit) {
            list.add("🛡️ You avoided ${totalLimit - todayReels} reels/videos today.")
        } else {
            list.add("⚠️ Daily limits reached. Time to close the feeds and rest your eyes.")
        }

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

        val health = if (today != null) repository.calculateBrainHealth(today, currentLimits) else 100
        if (health >= 90) list.add("🚀 Your brain health is elite! Your concentration is razor sharp.")
        else if (health >= 75) list.add("🧠 You're keeping your mind clean and mindful today.")

        list
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5000),
        listOf("✨ Start your wellness journey today.")
    )
}
