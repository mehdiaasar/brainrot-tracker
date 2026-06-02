package com.example.brainrottracker.data.local.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.example.brainrottracker.data.model.Platform

@Entity(tableName = "daily_logs")
data class DailyLog(
    @PrimaryKey
    val date: String,
    val instagramReels: Int = 0,
    val instagramMinutes: Int = 0,
    val youtubeShorts: Int = 0,
    val youtubeMinutes: Int = 0,
    val tiktokVideos: Int = 0,
    val tiktokMinutes: Int = 0,
    val snapchatSpotlights: Int = 0,
    val snapchatMinutes: Int = 0,
    val brainHealthScore: Int = 100
) {
    fun getTotalReels(): Int =
        instagramReels + youtubeShorts + tiktokVideos + snapchatSpotlights

    fun getTotalMinutes(): Int =
        instagramMinutes + youtubeMinutes + tiktokMinutes + snapchatMinutes

    fun getReelsForPlatform(platform: Platform): Int = when (platform) {
        Platform.INSTAGRAM -> instagramReels
        Platform.YOUTUBE -> youtubeShorts
        Platform.TIKTOK -> tiktokVideos
        Platform.SNAPCHAT -> snapchatSpotlights
    }

    fun getMinutesForPlatform(platform: Platform): Int = when (platform) {
        Platform.INSTAGRAM -> instagramMinutes
        Platform.YOUTUBE -> youtubeMinutes
        Platform.TIKTOK -> tiktokMinutes
        Platform.SNAPCHAT -> snapchatMinutes
    }
}
