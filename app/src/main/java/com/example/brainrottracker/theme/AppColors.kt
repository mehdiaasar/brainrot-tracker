package com.example.brainrottracker.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Semantic color roles for the app, resolved once per theme (light/dark) and provided via
 * [LocalAppColors]. Screens read these through [AppTheme.colors] instead of hand-picking
 * `if (dark) Warm… else WarmLight…` and threading the colors through every composable.
 *
 * Values are sourced verbatim from the existing Warm* tokens in [Color.kt] so the migration
 * is a pure structural change — the rendered colors are byte-identical.
 */
@Immutable
data class AppColors(
    val isDark: Boolean,
    // Core surfaces
    val background: Color,
    val surface: Color,       // card fill
    val surfaceAlt: Color,    // inner / progress-track fill (WarmStepDim / WarmLightInner)
    val border: Color,        // card stroke, dividers, chart gridlines
    val trackInactive: Color, // slider inactive track
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    // Brand / CTA
    val accent: Color,        // terracotta WarmAccent
    val accentOn: Color,      // content drawn on top of [accent]
    // Status
    val success: Color,       // WarmGrantedGreen
    val warning: Color,       // WarmAmber — "nearing limit" tier
    val error: Color,         // WarmError
    val goal: Color,          // WarmGoalBlue
    // Semantic accent: dashboard/streaks insight (mode-specific pair)
    val insightBg: Color,
    val insightAccent: Color,
    // Settings/Limits + Streaks-hero accents (mostly mode-independent)
    val settingsOrange: Color,
    val settingsPurple: Color,
    val settingsBlue: Color,
    val settingsGreen: Color,
    val heroPurple: Color,
)

fun darkColors() = AppColors(
    isDark = true,
    background = WarmBackground,
    surface = WarmSurface,
    surfaceAlt = WarmStepDim,
    border = WarmBorder,
    trackInactive = WarmStepDim,
    textPrimary = WarmText,
    textSecondary = WarmTextSecondary,
    accent = WarmAccent,
    accentOn = Color.White,
    success = WarmGrantedGreen,
    warning = WarmAmber,
    error = WarmError,
    goal = WarmGoalBlue,
    insightBg = WarmInsightBgDark,
    insightAccent = WarmInsightAccentDark,
    settingsOrange = SetOrange,
    settingsPurple = SetPurple,
    settingsBlue = SetBlue,
    settingsGreen = SetGreen,
    heroPurple = HeroPurple,
)

fun lightColors() = AppColors(
    isDark = false,
    background = WarmLightBackground,
    surface = WarmLightSurface,
    surfaceAlt = WarmLightInner,
    border = WarmLightBorder,
    trackInactive = Color(0xFFE7E2DA),
    textPrimary = WarmLightText,
    textSecondary = WarmLightTextSecondary,
    accent = WarmAccent,
    accentOn = Color.White,
    success = WarmGrantedGreen,
    warning = WarmAmber,
    error = WarmError,
    goal = WarmGoalBlue,
    insightBg = WarmInsightBg,
    insightAccent = WarmInsightAccent,
    settingsOrange = SetOrange,
    settingsPurple = SetPurple,
    settingsBlue = SetBlue,
    settingsGreen = SetGreen,
    heroPurple = HeroPurple,
)

/** Provided by [BrainRotTrackerTheme]; errors if a subtree is read outside the theme. */
val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors not provided — wrap content in BrainRotTrackerTheme")
}
