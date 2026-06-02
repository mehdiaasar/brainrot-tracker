package com.example.brainrottracker.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "user_limits")
data class UserLimits(
    @PrimaryKey
    val platform: String,
    val dailyReelLimit: Int = 30,
    val dailyMinuteLimit: Int = 60,
    val blockingEnabled: Boolean = true
)
