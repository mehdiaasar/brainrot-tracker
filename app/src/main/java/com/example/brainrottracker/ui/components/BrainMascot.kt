package com.example.brainrottracker.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.Animatable
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.example.brainrottracker.R
import kotlinx.coroutines.delay
import kotlin.random.Random

/**
 * The BrainPal-style mascot, rendered from detailed painted artwork — one image per
 * brain-health state — with live animation layered on top:
 *
 *  - smooth crossfade when the state changes
 *  - idle bob + squash-and-stretch breathing (lively when healthy, sluggish when not)
 *  - a periodic quick "blink" squint
 *  - anxious jitter when overstimulated, trembling when critical, glitchy shake when rotten
 *
 * States (drawables in res/drawable-nodpi):
 *  75–100 HEALTHY   brain_healthy
 *  50–74  TIRED     brain_tired
 *  25–49  OVERSTIM  brain_overstimulated
 *  10–24  CRITICAL  brain_critical
 *  0–9    ROT       brain_rot
 *
 * @param health 0..100, where 100 is healthiest.
 */
@Composable
fun BrainMascot(
    health: Int,
    modifier: Modifier = Modifier,
) {
    val h = health.coerceIn(0, 100)
    val t = h / 100f

    val state = when {
        h >= 75 -> BrainMood.HEALTHY
        h >= 50 -> BrainMood.TIRED
        h >= 25 -> BrainMood.OVERSTIMULATED
        h >= 10 -> BrainMood.CRITICAL
        else -> BrainMood.ROT
    }

    val drawableRes = brainMoodDrawable(h)

    // ── Idle bob + breathing (squash & stretch) ──────────────────────────
    val infinite = rememberInfiniteTransition(label = "idle")
    val bobDuration = when (state) {
        BrainMood.HEALTHY -> 1300
        BrainMood.TIRED -> 2400
        BrainMood.OVERSTIMULATED -> 900
        BrainMood.CRITICAL -> 2000
        BrainMood.ROT -> 3000
    }
    val phase by infinite.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(bobDuration, easing = EaseInOutSine), RepeatMode.Reverse
        ),
        label = "phase"
    )
    val bobAmp = 0.035f * (0.3f + 0.7f * t)        // fraction of height
    val squashAmp = 0.030f * (0.3f + 0.7f * t)

    // ── Blink-style squint: quick vertical dip at random intervals ───────
    val blink = remember { Animatable(0f) }        // 0 = open, 1 = squint
    LaunchedEffect(state) {
        while (true) {
            delay(Random.nextLong(2400, 5200))
            blink.animateTo(1f, tween(70))
            blink.animateTo(0f, tween(110))
            if (Random.nextFloat() < 0.25f) {
                delay(150)
                blink.animateTo(1f, tween(60))
                blink.animateTo(0f, tween(100))
            }
        }
    }

    // ── Jitter / shake for stressed states ───────────────────────────────
    var shakeX by remember { mutableFloatStateOf(0f) }
    var shakeY by remember { mutableFloatStateOf(0f) }
    var shakeRot by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(state) {
        when (state) {
            BrainMood.ROT -> while (true) {
                shakeX = Random.nextInt(-5, 6).toFloat()
                shakeY = Random.nextInt(-3, 4).toFloat()
                shakeRot = Random.nextInt(-2, 3) * 0.4f
                delay(90)
            }
            BrainMood.CRITICAL -> while (true) {
                shakeX = Random.nextInt(-4, 5).toFloat()
                shakeY = Random.nextInt(-4, 5).toFloat()
                shakeRot = Random.nextInt(-2, 3) * 0.5f
                delay(55)
            }
            BrainMood.OVERSTIMULATED -> while (true) {
                shakeX = Random.nextInt(-2, 3).toFloat()
                shakeY = 0f
                shakeRot = 0f
                delay(70)
            }
            else -> { shakeX = 0f; shakeY = 0f; shakeRot = 0f }
        }
    }

    val bob = (phase - 0.5f) * 2f                  // -1..1
    val squashY = 1f - squashAmp * bob - 0.06f * blink.value
    val squashX = 1f + squashAmp * bob * 0.7f + 0.03f * blink.value

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Crossfade(
            targetState = drawableRes,
            animationSpec = tween(600),
            label = "brainState"
        ) { res ->
            Image(
                painter = painterResource(res),
                contentDescription = "Brain mascot",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = shakeX
                        translationY = shakeY + bob * bobAmp * size.height
                        rotationZ = shakeRot
                        scaleX = squashX
                        scaleY = squashY
                        transformOrigin = TransformOrigin(0.5f, 1f)
                    }
            )
        }
    }
}

private enum class BrainMood { HEALTHY, TIRED, OVERSTIMULATED, CRITICAL, ROT }

/**
 * The mascot artwork for a brain-health score — shared by the in-app mascot,
 * the floating HUD, and the home-screen widget so they always agree.
 */
fun brainMoodDrawable(health: Int): Int = when {
    health >= 75 -> R.drawable.brain_healthy
    health >= 50 -> R.drawable.brain_tired
    health >= 25 -> R.drawable.brain_overstimulated
    health >= 10 -> R.drawable.brain_critical
    else -> R.drawable.brain_rot
}
