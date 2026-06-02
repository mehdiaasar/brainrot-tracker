package com.example.brainrottracker.data.local.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.brainrottracker.data.local.db.dao.DailyLogDao
import com.example.brainrottracker.data.local.db.dao.StreakDao
import com.example.brainrottracker.data.local.db.dao.UserLimitsDao
import com.example.brainrottracker.data.local.db.entity.DailyLog
import com.example.brainrottracker.data.local.db.entity.StreakRecord
import com.example.brainrottracker.data.local.db.entity.UserLimits

@Database(
    entities = [DailyLog::class, UserLimits::class, StreakRecord::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun dailyLogDao(): DailyLogDao
    abstract fun userLimitsDao(): UserLimitsDao
    abstract fun streakDao(): StreakDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "brainrot_tracker_db"
                )
                    .fallbackToDestructiveMigration(false)
                    .build()
                    .also { INSTANCE = it }
            }
        }
    }
}
