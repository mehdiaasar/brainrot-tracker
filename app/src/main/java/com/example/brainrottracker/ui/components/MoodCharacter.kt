package com.example.brainrottracker.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource

/**
 * A painted character that smoothly crossfades whenever [drawableRes] changes (e.g. the
 * dashboard mood shifts from "great" to "near limit") while gently bobbing in place so it
 * always feels alive. Used for the dashboard hero, insight, and reminder characters.
 *
 * @param drawableRes the artwork to show; changing it triggers a 600 ms crossfade
 * @param bobAmount   vertical bob as a fraction of height (0 disables the idle motion)
 * @param periodMillis one full bob cycle in milliseconds
 */
@Composable
fun MoodCharacter(
    drawableRes: Int,
    modifier: Modifier = Modifier,
    bobAmount: Float = 0.03f,
    periodMillis: Int = 2200,
) {
    val infinite = rememberInfiniteTransition(label = "moodIdle")
    val phase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(periodMillis, easing = EaseInOutSine), RepeatMode.Reverse
        ),
        label = "moodPhase"
    )
    val bob = (phase - 0.5f) * 2f   // -1..1

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = drawableRes,
            animationSpec = tween(600),
            label = "moodCharacter"
        ) { res ->
            Image(
                painter = painterResource(res),
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationY = bob * bobAmount * size.height
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
        }
    }
}
