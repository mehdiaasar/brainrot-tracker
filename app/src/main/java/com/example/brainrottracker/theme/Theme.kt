package com.example.brainrottracker.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.dp

private val WarmShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(20.dp),
)

/** Maps the app's semantic [AppColors] onto a Material color scheme so built-in components
 *  (Slider, ripple, text selection…) inherit the warm palette instead of a stale default. */
private fun materialSchemeFrom(c: AppColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.accentOn,
        secondary = c.success, onSecondary = c.accentOn,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary,
        error = c.error, onError = c.accentOn,
        outline = c.border, outlineVariant = c.border,
    )
} else {
    lightColorScheme(
        primary = c.accent, onPrimary = c.accentOn,
        secondary = c.success, onSecondary = c.accentOn,
        background = c.background, onBackground = c.textPrimary,
        surface = c.surface, onSurface = c.textPrimary,
        surfaceVariant = c.surfaceAlt, onSurfaceVariant = c.textSecondary,
        error = c.error, onError = c.accentOn,
        outline = c.border, outlineVariant = c.border,
    )
}

@Composable
fun BrainRotTrackerTheme(
    content: @Composable () -> Unit,
) {
    val dark = rememberIsDark()
    val appColors = if (dark) darkColors() else lightColors()
    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalSpacing provides Spacing(),
        LocalRadii provides Radii(),
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(appColors),
            typography = Typography,
            shapes = WarmShapes,
            content = content,
        )
    }
}
