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

    // Per-platform reel/video increment queries
    @Query("UPDATE daily_logs SET instagramReels = instagramReels + 1 WHERE date = :date")
    suspend fun incrementInstagramReels(date: String)

    @Query("UPDATE daily_logs SET youtubeShorts = youtubeShorts + 1 WHERE date = :date")
    suspend fun incrementYoutubeShorts(date: String)

    @Query("UPDATE daily_logs SET tiktokVideos = tiktokVideos + 1 WHERE date = :date")
    suspend fun incrementTiktokVideos(date: String)

    @Query("UPDATE daily_logs SET snapchatSpotlights = snapchatSpotlights + 1 WHERE date = :date")
    suspend fun incrementSnapchatSpotlights(date: String)

    @Query("UPDATE daily_logs SET facebookReels = facebookReels + 1 WHERE date = :date")
    suspend fun incrementFacebookReels(date: String)

    @Query("SELECT MIN(date) FROM daily_logs")
    suspend fun getEarliestDate(): String?

    @Query("UPDATE daily_logs SET brainHealthScore = :score WHERE date = :date")
    suspend fun updateBrainHealthScore(date: String, score: Int)
}
