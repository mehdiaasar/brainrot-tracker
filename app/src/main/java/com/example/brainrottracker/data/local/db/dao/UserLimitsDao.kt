package com.example.brainrottracker.data.local.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.brainrottracker.data.local.db.entity.UserLimits
import kotlinx.coroutines.flow.Flow

@Dao
interface UserLimitsDao {

    @Query("SELECT * FROM user_limits")
    fun getAll(): Flow<List<UserLimits>>

    @Query("SELECT * FROM user_limits WHERE platform = :platform")
    fun getForPlatform(platform: String): Flow<UserLimits?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(limits: UserLimits)
}
