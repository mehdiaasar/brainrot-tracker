package com.example.brainrottracker.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Spacing scale. Dominant values observed in the UI: xl=20 (screen gutter), md=12 (card gap), lg=16 (inner padding). */
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,
    val sm: Dp = 8.dp,
    val md: Dp = 12.dp,
    val lg: Dp = 16.dp,
    val xl: Dp = 20.dp,
    val xxl: Dp = 24.dp,
    val xxxl: Dp = 32.dp,
)

/** Corner-radius tokens for the app's cards and chips. Cards may still pass an explicit radius. */
@Immutable
data class Radii(
    val chip: Dp = 10.dp,
    val card: Dp = 12.dp,
    val cardLg: Dp = 16.dp,
)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadii = staticCompositionLocalOf { Radii() }
