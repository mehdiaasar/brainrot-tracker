package com.example.brainrottracker.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brainrottracker.data.local.db.entity.DailyLog
import kotlinx.coroutines.flow.Flow

@Dao
interface DailyLogDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(log: DailyLog)

    @Query("SELECT * FROM daily_logs WHERE date = :date")
    fun getByDate(date: String): Flow<DailyLog?>

    @Query("SELECT * FROM daily_logs WHERE date = :date LIMIT 1")
    suspend fun getByDateOnce(date: String): DailyLog?

    @Query("SELECT * FROM daily_logs WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getDateRange(startDate: String, endDate: String): Flow<List<DailyLog>>

    @Query("SELECT * FROM daily_logs ORDER BY date ASC")
    fun getAllLogs(): Flow<List<DailyLog>>

    // Per-platform reel/video increment queries
    @Query("UPDATE daily_logs SET instagramReels = instagramReels + 1 WHERE date = :date")
    suspend fun incrementInstagramReels(date: String)

    @Query("UPDATE daily_logs SET youtubeShorts = youtubeShorts + 1 WHERE date = :date")
    suspend fun incrementYoutubeShorts(date: String)

    @Query("UPDATE daily_logs SET tiktokVideos = tiktokVideos + 1 WHERE date = :date")
    suspend fun incrementTiktokVideos(date: String)

    @Query("UPDATE daily_logs SET snapchatSpotlights = snapchatSpotlights + 1 WHERE date = :date")
    suspend fun incrementSnapchatSpotlights(date: String)

    // Per-platform minute addition queries
    @Query("UPDATE daily_logs SET instagramMinutes = instagramMinutes + :minutes WHERE date = :date")
    suspend fun addInstagramMinutes(date: String, minutes: Int)

    @Query("UPDATE daily_logs SET youtubeMinutes = youtubeMinutes + :minutes WHERE date = :date")
    suspend fun addYoutubeMinutes(date: String, minutes: Int)

    @Query("UPDATE daily_logs SET tiktokMinutes = tiktokMinutes + :minutes WHERE date = :date")
    suspend fun addTiktokMinutes(date: String, minutes: Int)

    @Query("UPDATE daily_logs SET snapchatMinutes = snapchatMinutes + :minutes WHERE date = :date")
    suspend fun addSnapchatMinutes(date: String, minutes: Int)

    @Query("UPDATE daily_logs SET brainHealthScore = :score WHERE date = :date")
    suspend fun updateBrainHealthScore(date: String, score: Int)
}
