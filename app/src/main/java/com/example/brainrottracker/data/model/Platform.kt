package com.example.brainrottracker.data.model

enum class Platform(
    val packageName: String,
    val displayName: String,
    val emoji: String
) {
    INSTAGRAM("com.instagram.android", "Instagram", "📸"),
    YOUTUBE("com.google.android.youtube", "YouTube", "▶️"),
    TIKTOK("com.zhiliaoapp.musically", "TikTok", "🎵"),
    SNAPCHAT("com.snapchat.android", "Snapchat", "👻");

    companion object {
        fun fromPackageName(packageName: String): Platform? {
            return entries.find { it.packageName == packageName }
        }
    }
}
