package com.example.brainrottracker.data.repository

import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.model.Platform
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

class UsageRepository(private val database: AppDatabase) {

    private val dailyLogDao = database.dailyLogDao()
    private val userLimitsDao = database.userLimitsDao()
    private val streakDao = database.streakDao()

    private fun today(): String = LocalDate.now().toString()

    // ── Daily Log ────────────────────────────────────────────────────────

    fun getTodayLog(): Flow<DailyLog?> = dailyLogDao.getByDate(today())

    fun getLogForDate(date: String): Flow<DailyLog?> = dailyLogDao.getByDate(date)

    suspend fun getTodayLogSnapshot(): DailyLog? = dailyLogDao.getByDateOnce(today())

    suspend fun getLogSnapshot(date: String): DailyLog? = dailyLogDao.getByDateOnce(date)

    suspend fun ensureTodayLogExists() {
        val existing = dailyLogDao.getByDateOnce(today())
        if (existing == null) {
            dailyLogDao.insertOrUpdate(DailyLog(date = today()))
        }
    }

    suspend fun incrementReelCount(platform: Platform) {
        ensureTodayLogExists()
        val date = today()
        when (platform) {
            Platform.INSTAGRAM -> dailyLogDao.incrementInstagramReels(date)
            Platform.YOUTUBE -> dailyLogDao.incrementYoutubeShorts(date)
            Platform.TIKTOK -> dailyLogDao.incrementTiktokVideos(date)
            Platform.SNAPCHAT -> dailyLogDao.incrementSnapchatSpotlights(date)
        }
    }

    fun getWeeklyLogs(): Flow<List<DailyLog>> {
        val end = LocalDate.now()
        val start = end.minusDays(6)
        return dailyLogDao.getDateRange(start.toString(), end.toString())
    }

    fun getMonthlyLogs(): Flow<List<DailyLog>> {
        val end = LocalDate.now()
        val start = end.minusDays(29)
        return dailyLogDao.getDateRange(start.toString(), end.toString())
    }

    // ── User Limits ─────────────────────────────────────────────────────

    fun getLimits(): Flow<List<UserLimits>> = userLimitsDao.getAll()

    suspend fun getLimitsSnapshot(): List<UserLimits> = userLimitsDao.getAllOnce()

    fun getLimitForPlatform(platform: Platform): Flow<UserLimits?> =
        userLimitsDao.getForPlatform(platform.name)

    suspend fun updateLimits(limits: UserLimits) {
        userLimitsDao.upsert(limits)
    }

    // ── Brain Health ────────────────────────────────────────────────────

    suspend fun updateBrainHealth(score: Int) {
        ensureTodayLogExists()
        dailyLogDao.updateBrainHealthScore(today(), score.coerceIn(0, 100))
    }

    /**
     * Calculates a brain health score from 0–100 based on how much usage
     * is under or over the configured limits.
     *
     * 100 = zero usage across all platforms.
     *   0 = usage is at or beyond 2× the limit on every platform.
     *
     * Each platform contributes equally (25 points max).
     * For each platform the score component is:
     *   25 × (1 − avgRatio)  where avgRatio = avg(reelRatio, minuteRatio) clamped to [0, 2] / 2
     * If no limit is configured for a platform, default limits (30 reels, 60 min) are assumed.
     */
    fun calculateBrainHealth(
        log: DailyLog,
        limits: List<UserLimits>,
        minuteOverrides: Map<Platform, Int> = emptyMap()
    ): Int {
        val limitMap = limits.associateBy { it.platform }
        var totalScore = 0.0

        for (platform in Platform.entries) {
            val platformLimits = limitMap[platform.name]
            val reelLimit = platformLimits?.dailyReelLimit ?: 30
            val minuteLimit = platformLimits?.dailyMinuteLimit ?: 60

            val reels = log.getReelsForPlatform(platform).toDouble()
            // Minutes come live from UsageStatsManager (ScreenTimeHelper); not stored in the DB
            val minutes = (minuteOverrides[platform] ?: 0).toDouble()

            // Ratio of usage to limit, clamped to 0..2 so going 2× over = 0 points
            val reelRatio = if (reelLimit > 0) (reels / reelLimit).coerceIn(0.0, 2.0) else if (reels > 0) 2.0 else 0.0
            val minuteRatio = if (minuteLimit > 0) (minutes / minuteLimit).coerceIn(0.0, 2.0) else if (minutes > 0) 2.0 else 0.0

            val avgRatio = (reelRatio + minuteRatio) / 2.0
            // Each platform contributes up to 25 points
            val platformScore = 25.0 * (1.0 - avgRatio / 2.0)
            totalScore += platformScore
        }

        return totalScore.toInt().coerceIn(0, 100)
    }

    // ── Streaks ─────────────────────────────────────────────────────────

    fun getStreakData(): Flow<List<StreakRecord>> = streakDao.getAll()

    suspend fun getStreakSnapshot(date: String): StreakRecord? = streakDao.getByDateOnce(date)

    /**
     * Evaluates streak records for every day up to and including [yesterday]
     * that doesn't have one yet. Called lazily (service day-rollover, app open)
     * so missed midnights are caught up on the next opportunity.
     *
     * A day is under-limit when no log exists (the phone wasn't doomscrolled)
     * or every platform's reel count is within its limit. Historical days are
     * judged against the *current* limits — limits aren't versioned.
     */
    suspend fun evaluateStreaksUpTo(yesterday: LocalDate) {
        val firstUnevaluated = streakDao.getLatestDate()?.let { LocalDate.parse(it).plusDays(1) }
            ?: dailyLogDao.getEarliestDate()?.let { LocalDate.parse(it) }
            ?: yesterday
        if (firstUnevaluated.isAfter(yesterday)) return

        val limits = getLimitsSnapshot().associateBy { it.platform }
        fun limitFor(platform: Platform): Int =
            limits[platform.name]?.dailyReelLimit ?: 30

        var date = firstUnevaluated
        var previous = streakDao.getByDateOnce(date.minusDays(1).toString())
        while (!date.isAfter(yesterday)) {
            val log = dailyLogDao.getByDateOnce(date.toString())
            val underLimit = log == null ||
                Platform.entries.all { log.getReelsForPlatform(it) <= limitFor(it) }
            val streakDay = if (underLimit) {
                (previous?.takeIf { it.underLimit }?.streakDay ?: 0) + 1
            } else {
                0
            }
            val record = StreakRecord(date.toString(), underLimit, streakDay)
            streakDao.insert(record)
            previous = record
            date = date.plusDays(1)
        }
    }

    /**
     * Calculates the current consecutive streak ending with the latest record.
     */
    fun calculateCurrentStreak(records: List<StreakRecord>): Int {
        if (records.isEmpty()) return 0

        val sorted = records.sortedByDescending { it.date }
        var streak = 0

        for (record in sorted) {
            if (record.underLimit) {
                streak++
            } else {
                break
            }
        }

        return streak
    }

    /**
     * Finds the longest consecutive streak across all recorded history.
     */
    fun calculateLongestStreak(records: List<StreakRecord>): Int {
        if (records.isEmpty()) return 0

        val sorted = records.sortedBy { it.date }
        var longest = 0
        var current = 0

        for (record in sorted) {
            if (record.underLimit) {
                current++
                if (current > longest) {
                    longest = current
                }
            } else {
                current = 0
            }
        }

        return longest
    }
}
