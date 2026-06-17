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

    /** The seven days *before* the current week (days −13..−7), for week-over-week deltas. */
    fun getPreviousWeekLogs(): Flow<List<DailyLog>> {
        val end = LocalDate.now().minusDays(7)
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
     * Calculates a brain health score from 0–100 from reel/video counts vs. limits.
     *
     * 100 = nothing was watched (or every platform in use was at zero usage).
     *   0 = usage is at or beyond 2× the reel limit on every platform in use.
     *
     * Reels-only by design: this is the single definition of "a good day" shared
     * with the weekly productivity score and the streak logic. Screen-time
     * minutes are still surfaced separately (dashboard, widget, mood face) but no
     * longer feed the score, so the three metrics can't disagree.
     *
     * Only platforms with actual usage count, weighted equally among themselves —
     * unused platforms don't hand out "free" points, so a heavy day on a single
     * app can still drag the score to 0. Each platform's reel ratio is clamped to
     * [0, 2] (2× over = full penalty) and its health fraction is `1 − ratio / 2`.
     * If no limit is configured for a platform, the default reel limit (30) is assumed.
     */
    fun calculateBrainHealth(
        log: DailyLog,
        limits: List<UserLimits>
    ): Int {
        val limitMap = limits.associateBy { it.platform }
        var fractionSum = 0.0
        var activePlatforms = 0

        for (platform in Platform.entries) {
            val reels = log.getReelsForPlatform(platform).toDouble()
            // Skip platforms the user didn't touch so they don't dilute the score.
            if (reels <= 0.0) continue
            activePlatforms++

            val reelLimit = limitMap[platform.name]?.dailyReelLimit ?: 30
            // Ratio of usage to limit, clamped to 0..2 so going 2× over = 0 points
            val reelRatio = if (reelLimit > 0) (reels / reelLimit).coerceIn(0.0, 2.0) else 2.0
            fractionSum += (1.0 - reelRatio / 2.0)
        }

        if (activePlatforms == 0) return 100
        return (100.0 * fractionSum / activePlatforms).toInt().coerceIn(0, 100)
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
