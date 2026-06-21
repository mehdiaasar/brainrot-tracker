package com.example.brainrottracker.theme

import androidx.compose.ui.graphics.Color

// Warm / dark palette (used across all screens in dark mode)
val WarmBackground = Color(0xFF181715)
val WarmSurface = Color(0xFF252320)
val WarmBorder = Color(0xFF2F2C28)
val WarmStepDim = Color(0xFF1F1E1B)
val WarmText = Color(0xFFFAF9F5)
val WarmTextSecondary = Color(0xFFA09D96)
val WarmAccent = Color(0xFFCC785C)       // terracotta CTA
val WarmGrantedGreen = Color(0xFF5DB872) // granted badge

// Warm / light palette (used across all screens in light mode)
val WarmLightBackground = Color(0xFFFAF9F5)
val WarmLightSurface = Color(0xFFEFE9DE)
val WarmLightBorder = Color(0xFFE6DFD8)
val WarmLightInner = Color(0xFFEBE6DF)
val WarmLightText = Color(0xFF141413)
val WarmLightTextSecondary = Color(0xFF6C6A64)

// Insight card (dashboard) — soft lavender in light, muted indigo in dark
val WarmInsightBg = Color(0xFFEFE7FA)
val WarmInsightAccent = Color(0xFF8B5CF6)
val WarmInsightBgDark = Color(0xFF272233)
val WarmInsightAccentDark = Color(0xFFB89BF0)

// Daily-goal "on track" blue
val WarmGoalBlue = Color(0xFF3B82F6)

// ── Semantic accent tokens (promoted from per-screen private vals) ───────────
// Shared so the design-system factories and any screen can reference one source.
val WarmAmber = Color(0xFFE8A55A)   // "nearing limit" tier (was Dashboard's DashAmber)
val SetOrange = Color(0xFFF26B21)   // Settings/Limits primary accent + slider thumb
val SetPurple = Color(0xFF8B5CF6)   // App-blocking mode accent
val SetBlue = Color(0xFF57A6D4)     // Appearance accent
val SetGreen = Color(0xFF46A86B)    // Account / balance accent
val HeroPurple = Color(0xFF6D28D9)  // Streaks hero copy (fixed-on-bright, legible both themes)

val WarmError = Color(0xFFC64545)

// ── Stats & Reports — productivity-score tiers ──────────────────────────────
// Tier accents (score number, gauge arc, headline), tuned to the mock screens.
val StatsExcellent = Color(0xFF2EA84F)   // green
val StatsGood = Color(0xFFEFA42A)        // gold
val StatsFair = Color(0xFFF0792B)        // orange
val StatsLow = Color(0xFFF24E27)         // red-orange
val StatsCritical = Color(0xFFE5342B)    // red

// Hero-card backgrounds per tier — light mode (soft tint), dark mode (subtle wash).
val StatsBgExcellentLight = Color(0xFFEFF4EE)
val StatsBgGoodLight = Color(0xFFFBF1DC)
val StatsBgFairLight = Color(0xFFFCEBDF)
val StatsBgLowLight = Color(0xFFFBE9E2)
val StatsBgCriticalLight = Color(0xFFFCE4E3)
val StatsBgExcellentDark = Color(0xFF20251E)
val StatsBgGoodDark = Color(0xFF2A2520)
val StatsBgFairDark = Color(0xFF2C231D)
val StatsBgLowDark = Color(0xFF2C201C)
val StatsBgCriticalDark = Color(0xFF2C1D1D)

// Footer-card accent + background per tier (the LOW tier reads as a calm lavender).
val StatsFooterLowAccent = Color(0xFF8B5CF6)
val StatsBgLowFooterLight = Color(0xFFEEEAFB)
val StatsBgLowFooterDark = Color(0xFF26223A)

// Best/Worst day card tints.
val StatsBestBgLight = Color(0xFFEDF4EC)
val StatsBestBgDark = Color(0xFF20251E)
val StatsWorstBgLight = Color(0xFFFBEAEA)
val StatsWorstBgDark = Color(0xFF2C1D1D)

// Weekly bar-chart segment colors (mock palette). TikTok adapts so it stays visible on dark.
val ChartYouTube = Color(0xFFE0392F)
val ChartInstagram = Color(0xFFF2982A)
val ChartSnapchat = Color(0xFF2FA84F)
val ChartTikTokLight = Color(0xFF17150F)
val ChartTikTokDark = Color(0xFFC9C5BD)
