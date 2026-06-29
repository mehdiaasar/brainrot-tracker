package com.example.brainrottracker.service

import com.example.brainrottracker.data.local.prefs.Prefs

/**
 * How the blocking overlay behaves once a platform's daily limit is reached.
 *
 * HARD   — the scrim re-appears every time the user lands back on the reel feed until
 *          midnight; the only action is the "Go back" button (returns to the app's feed,
 *          and re-entering the reel viewer re-blocks).
 * SNOOZE — dismissing grants a 5-minute grace period, after which the scrim returns.
 * REMIND — the scrim shows once per foreground session; dismissing lets the user
 *          keep scrolling until they leave and reopen the app.
 */
enum class BlockingMode {
    HARD, SNOOZE, REMIND;

    companion object {
        const val PREF_KEY = Prefs.BLOCKING_MODE
        const val SNOOZE_MS = 5 * 60_000L

        fun fromPref(value: String?): BlockingMode =
            entries.find { it.name == value } ?: REMIND
    }
}
