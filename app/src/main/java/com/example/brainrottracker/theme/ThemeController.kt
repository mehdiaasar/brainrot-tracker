package com.example.brainrottracker.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import com.example.brainrottracker.data.local.prefs.Prefs
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * App-wide theme controller. Holds the user's theme preference (System / Light / Dark)
 * as Compose state backed by SharedPreferences so every screen recomposes when it changes.
 */
object ThemeController {

    enum class Mode { SYSTEM, LIGHT, DARK }

    var mode by mutableStateOf(Mode.SYSTEM)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { Mode.valueOf(p.getString(Prefs.THEME_MODE, Mode.SYSTEM.name)!!) }
            .getOrDefault(Mode.SYSTEM)
    }

    fun selectMode(newMode: Mode) {
        mode = newMode
        prefs?.edit()?.putString(Prefs.THEME_MODE, newMode.name)?.apply()
    }
}

/** Resolves the active dark/light state based on the user's chosen mode. */
@Composable
fun rememberIsDark(): Boolean = when (ThemeController.mode) {
    ThemeController.Mode.SYSTEM -> isSystemInDarkTheme()
    ThemeController.Mode.LIGHT -> false
    ThemeController.Mode.DARK -> true
}
