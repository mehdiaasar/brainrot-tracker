package com.example.brainrottracker.data.repository

import com.example.brainrottracker.data.local.db.AppDatabase
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import com.example.brainrottracker.data.local.db.entity.UserLimits
import com.example.brainrottracker.data.model.Platform
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
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

    suspend fun addMinutes(platform: Platform, minutes: Int) {
        ensureTodayLogExists()
        val date = today()
        when (platform) {
            Platform.INSTAGRAM -> dailyLogDao.addInstagramMinutes(date, minutes)
            Platform.YOUTUBE -> dailyLogDao.addYoutubeMinutes(date, minutes)
            Platform.TIKTOK -> dailyLogDao.addTiktokMinutes(date, minutes)
            Platform.SNAPCHAT -> dailyLogDao.addSnapchatMinutes(date, minutes)
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
    fun calculateBrainHealth(log: DailyLog, limits: List<UserLimits>): Int {
        val limitMap = limits.associateBy { it.platform }
        var totalScore = 0.0

        for (platform in Platform.entries) {
            val platformLimits = limitMap[platform.name]
            val reelLimit = platformLimits?.dailyReelLimit ?: 30
            val minuteLimit = platformLimits?.dailyMinuteLimit ?: 60

            val reels = log.getReelsForPlatform(platform).toDouble()
            val minutes = log.getMinutesForPlatform(platform).toDouble()

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

    suspend fun updateStreak(date: String, underLimit: Boolean) {
        // Fetch yesterday's record to determine streak continuation
        val yesterday = LocalDate.parse(date).minusDays(1).toString()
        val yesterdayRecords = dailyLogDao.getByDateOnce(yesterday)

        // Check if there's an existing streak record for yesterday
        // We use a simple heuristic: if yesterday had a streak record, continue it
        val existingStreaks = database.streakDao()
        val previousStreakDay = getPreviousStreakDay(yesterday)

        val streakDay = if (underLimit) {
            previousStreakDay + 1
        } else {
            0
        }

        streakDao.insert(
            StreakRecord(
                date = date,
                underLimit = underLimit,
                streakDay = streakDay,
                freezeUsed = false
            )
        )
    }

    private suspend fun getPreviousStreakDay(date: String): Int {
        // We need a one-shot query; use the DAO's getByDateOnce-equivalent for streaks
        // Since streakDao.getByDate returns Flow, we query via a workaround:
        // Insert a helper that checks the streak_records table directly
        return try {
            val record = getStreakRecordOnce(date)
            if (record != null && (record.underLimit || record.freezeUsed)) {
                record.streakDay
            } else {
                0
            }
        } catch (_: Exception) {
            0
        }
    }

    private suspend fun getStreakRecordOnce(date: String): StreakRecord? {
        return streakDao.getByDate(date).firstOrNull()
    }

    /**
     * Calculates the current consecutive streak ending today or yesterday.
     * A streak day counts if underLimit is true or a freeze was used.
     */
    fun calculateCurrentStreak(records: List<StreakRecord>): Int {
        if (records.isEmpty()) return 0

        val sorted = records.sortedByDescending { it.date }
        var streak = 0

        for (record in sorted) {
            if (record.underLimit || record.freezeUsed) {
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
            if (record.underLimit || record.freezeUsed) {
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
