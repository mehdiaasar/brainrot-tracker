package com.example.brainrottracker.data.sync

import android.content.Context
import com.example.brainrottracker.data.model.Platform
import com.example.brainrottracker.data.repository.UsageRepository
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import java.time.LocalDate

/**
 * Uploads one document per finished day to `users/{uid}/dailyLogs/{date}` for the
 * signed-in user. Runs only when the user has signed in AND explicitly enabled cloud
 * backup ("cloud_sync_enabled" pref) — required because the underlying counts come
 * from the Accessibility API.
 *
 * Writes are fire-and-forget: Firestore persists them locally and retries when the
 * device is online, so no explicit retry logic is needed.
 */
class UsageSyncManager(
    private val context: Context,
    private val repository: UsageRepository
) {

    suspend fun syncIfEnabled() {
        if (FirebaseApp.getApps(context).isEmpty()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(KEY_ENABLED, false)) return
        val user = FirebaseAuth.getInstance().currentUser ?: return

        val yesterday = LocalDate.now().minusDays(1)
        val lastSynced = prefs.getString(KEY_LAST_SYNCED, null)?.let(LocalDate::parse)
        var date = lastSynced?.plusDays(1) ?: yesterday
        if (date.isAfter(yesterday)) return

        val firestore = FirebaseFirestore.getInstance()
        val limits = repository.getLimitsSnapshot().associateBy { it.platform }
        // UsageStats only retains recent activity, so minutes are only reliable for yesterday
        val yesterdayMinutes = ScreenTimeHelper.getYesterdayMinutesByPlatform(context)

        while (!date.isAfter(yesterday)) {
            val dateStr = date.toString()
            val log = repository.getLogSnapshot(dateStr)
            val streak = repository.getStreakSnapshot(dateStr)
            val doc = buildMap<String, Any> {
                put("date", dateStr)
                Platform.entries.forEach { p ->
                    put("reels_${p.name.lowercase()}", log?.getReelsForPlatform(p) ?: 0)
                    put("limit_${p.name.lowercase()}", limits[p.name]?.dailyReelLimit ?: 30)
                    if (date == yesterday) {
                        put("minutes_${p.name.lowercase()}", yesterdayMinutes[p] ?: 0)
                    }
                }
                put("brainHealthScore", log?.brainHealthScore ?: 100)
                streak?.let {
                    put("underLimit", it.underLimit)
                    put("streakDay", it.streakDay)
                }
            }
            firestore.collection("users").document(user.uid)
                .collection("dailyLogs").document(dateStr)
                .set(doc)
            prefs.edit().putString(KEY_LAST_SYNCED, dateStr).apply()
            date = date.plusDays(1)
        }
    }

    companion object {
        const val PREFS = "brainrot_prefs"
        const val KEY_ENABLED = "cloud_sync_enabled"
        const val KEY_LAST_SYNCED = "last_synced_date"
    }
}
