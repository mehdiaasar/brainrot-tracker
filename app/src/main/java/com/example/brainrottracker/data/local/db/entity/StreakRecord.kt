package com.example.brainrottracker.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "streak_records")
data class StreakRecord(
    @PrimaryKey
    val date: String,
    val underLimit: Boolean,
    val streakDay: Int,
    val freezeUsed: Boolean = false
)
