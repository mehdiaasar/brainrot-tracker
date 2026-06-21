package com.example.brainrottracker.data.local.prefs

/**
 * Single source of truth for the app's `SharedPreferences` file name and keys. Previously these
 * magic strings were duplicated across services, screens, the widget, and the theme controller;
 * centralizing them here prevents drift (a typo'd key silently reads the default and loses state).
 */
object Prefs {
    const val FILE = "brainrot_prefs"

    const val BLOCKING_ENABLED = "blocking_enabled"
    const val BLOCKING_MODE = "blocking_mode"
    const val HUD_SCALE = "hud_scale"
    const val THEME_MODE = "theme_mode"
    const val CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    const val LAST_SYNCED_DATE = "last_synced_date"
    const val SNOOZE_UNTIL_ALL = "snooze_until_all"
    const val ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
}
