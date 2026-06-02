package com.example.brainrottracker.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import kotlinx.coroutines.flow.Flow

@Dao
interface StreakDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: StreakRecord)

    @Query("SELECT * FROM streak_records WHERE date = :date")
    fun getByDate(date: String): Flow<StreakRecord?>

    @Query("SELECT * FROM streak_records ORDER BY date ASC")
    fun getAll(): Flow<List<StreakRecord>>

    @Query("SELECT * FROM streak_records WHERE date BETWEEN :startDate AND :endDate ORDER BY date ASC")
    fun getDateRange(startDate: String, endDate: String): Flow<List<StreakRecord>>
}
