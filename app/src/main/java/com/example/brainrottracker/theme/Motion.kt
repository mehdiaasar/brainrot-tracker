package com.example.brainrottracker.theme

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Shared motion tokens so animations across the app feel like one product:
 * a calm spring for layout/value changes, a slightly springy one for press feedback,
 * and an emphasized tween for color/cross-fade transitions.
 */
object Motion {
    fun <T> spec(): AnimationSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = Spring.StiffnessMediumLow,
    )

    fun pressSpec(): AnimationSpec<Float> = spring(
        dampingRatio = 0.55f,
        stiffness = Spring.StiffnessMedium,
    )

    val colorSpec: AnimationSpec<Color> = tween(durationMillis = 320, easing = FastOutSlowInEasing)
    val floatSpec: AnimationSpec<Float> = tween(durationMillis = 450, easing = FastOutSlowInEasing)
    val dpSpec: AnimationSpec<Dp> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}
