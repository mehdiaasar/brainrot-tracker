package com.example.brainrottracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp

/**
 * An original, fully Compose-drawn cartoon brain mascot whose appearance degrades as
 * brain-health drops: a bright bouncy pink brain at 100, a melted grey one near 0.
 *
 * Nothing here is loaded from an image asset — the whole character is vector geometry,
 * so it scales cleanly and animates between states.
 *
 * @param health 0..100, where 100 is healthiest.
 */
@Composable
fun BrainMascot(
    health: Int,
    modifier: Modifier = Modifier,
) {
    val t = (health.coerceIn(0, 100)) / 100f

    // Healthy → rotten color morph.
    val healthyPink = Color(0xFFE78BA6)
    val rotGrey = Color(0xFFB2A9A6)
    val brainColor by animateColorAsState(
        targetValue = lerp(rotGrey, healthyPink, t),
        animationSpec = tween(600),
        label = "brainColor"
    )
    val foldColor = lerp(brainColor, Color.Black, 0.18f)

    // Eyes open wide when healthy, droop shut when rotten.
    val eyeOpen by animateFloatAsState(
        targetValue = 0.16f + 0.84f * t,
        animationSpec = tween(600),
        label = "eyeOpen"
    )
    // Mouth: +1 = big smile, -1 = frown.
    val mouth by animateFloatAsState(
        targetValue = (t - 0.45f) * 2f,
        animationSpec = tween(600),
        label = "mouth"
    )

    // Idle bob — lively when healthy, almost still when rotten.
    val infinite = rememberInfiniteTransition(label = "bob")
    val bobPhase by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bobPhase"
    )
    val bob = (bobPhase - 0.5f) * 14f * t // px of vertical travel

    Canvas(modifier = modifier) {
        translate(top = bob) {
            drawBrain(brainColor, foldColor, eyeOpen, mouth, t)
        }
    }
}

private fun DrawScope.drawBrain(
    brainColor: Color,
    foldColor: Color,
    eyeOpen: Float,
    mouth: Float,
    t: Float,
) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    // Sag the whole character downward a touch as health drops.
    val sag = (1f - t) * h * 0.04f

    val headR = w * 0.34f
    val headCy = h * 0.40f + sag

    // ── Body (stubby torso + arms + legs) ───────────────────────────────
    val bodyTop = headCy + headR * 0.55f
    val bodyColor = lerp(brainColor, Color.White, 0.04f)

    // Legs
    val legW = w * 0.09f
    val legH = h * 0.12f
    val legY = bodyTop + h * 0.12f
    listOf(cx - w * 0.10f, cx + w * 0.10f).forEach { lx ->
        drawRoundRectCompat(bodyColor, Offset(lx - legW / 2f, legY), Size(legW, legH), legW / 2f)
        drawOval(bodyColor, Offset(lx - legW * 0.7f, legY + legH - legW * 0.5f), Size(legW * 1.4f, legW * 0.9f))
    }
    // Torso
    val torsoW = w * 0.30f
    val torsoH = h * 0.16f
    drawRoundRectCompat(bodyColor, Offset(cx - torsoW / 2f, bodyTop), Size(torsoW, torsoH), torsoW * 0.4f)
    // Arms
    val armW = w * 0.07f
    val armH = h * 0.13f
    val armDroop = (1f - t) * h * 0.03f
    drawRoundRectCompat(bodyColor, Offset(cx - torsoW / 2f - armW * 0.6f, bodyTop + armDroop), Size(armW, armH), armW / 2f)
    drawRoundRectCompat(bodyColor, Offset(cx + torsoW / 2f - armW * 0.4f, bodyTop + armDroop), Size(armW, armH), armW / 2f)

    // ── Head (lumpy brain made of overlapping bumps) ────────────────────
    val bumps = listOf(
        Offset(cx - headR * 0.62f, headCy - headR * 0.30f) to headR * 0.55f,
        Offset(cx - headR * 0.20f, headCy - headR * 0.72f) to headR * 0.55f,
        Offset(cx + headR * 0.28f, headCy - headR * 0.70f) to headR * 0.55f,
        Offset(cx + headR * 0.66f, headCy - headR * 0.26f) to headR * 0.52f,
        Offset(cx + headR * 0.60f, headCy + headR * 0.30f) to headR * 0.50f,
        Offset(cx - headR * 0.58f, headCy + headR * 0.34f) to headR * 0.50f,
        Offset(cx, headCy) to headR * 0.95f,
    )
    bumps.forEach { (c, r) -> drawCircle(brainColor, r, c) }

    // Brain folds (squiggles) in a darker tint.
    val foldStroke = Stroke(width = headR * 0.07f, cap = StrokeCap.Round)
    // Center fissure
    drawLine(
        foldColor,
        Offset(cx, headCy - headR * 0.85f),
        Offset(cx, headCy + headR * 0.55f),
        strokeWidth = headR * 0.07f,
        cap = StrokeCap.Round
    )
    val leftFold = Path().apply {
        moveTo(cx - headR * 0.62f, headCy - headR * 0.10f)
        quadraticBezierTo(cx - headR * 0.30f, headCy - headR * 0.30f, cx - headR * 0.36f, headCy + headR * 0.18f)
    }
    val rightFold = Path().apply {
        moveTo(cx + headR * 0.62f, headCy - headR * 0.10f)
        quadraticBezierTo(cx + headR * 0.30f, headCy - headR * 0.30f, cx + headR * 0.36f, headCy + headR * 0.18f)
    }
    drawPath(leftFold, foldColor, style = foldStroke)
    drawPath(rightFold, foldColor, style = foldStroke)

    // ── Face ────────────────────────────────────────────────────────────
    val eyeR = headR * 0.30f
    val eyeY = headCy + headR * 0.02f
    val eyeDx = headR * 0.42f
    drawEye(Offset(cx - eyeDx, eyeY), eyeR, eyeOpen, t)
    drawEye(Offset(cx + eyeDx, eyeY), eyeR, eyeOpen, t)

    // Mouth
    val mouthY = headCy + headR * 0.58f
    val mouthW = headR * 0.7f
    val curve = mouth.coerceIn(-1f, 1f) * headR * 0.34f
    val mouthPath = Path().apply {
        moveTo(cx - mouthW / 2f, mouthY)
        quadraticBezierTo(cx, mouthY + curve, cx + mouthW / 2f, mouthY)
    }
    drawPath(
        mouthPath,
        lerp(foldColor, Color.Black, 0.3f),
        style = Stroke(width = headR * 0.09f, cap = StrokeCap.Round)
    )

    // Sweat drop cue when very unhealthy.
    if (t < 0.4f) {
        val a = (0.4f - t) / 0.4f
        drawCircle(
            Color(0xFF7FB7E8).copy(alpha = a),
            radius = headR * 0.10f,
            center = Offset(cx + headR * 0.78f, headCy - headR * 0.10f)
        )
    }
}

/**
 * A single eye drawn as a white ball with a dark pupil. As [open] shrinks toward 0 the
 * eye is vertically squished into a sleepy slit, which reads as a drooping/closed eyelid.
 */
private fun DrawScope.drawEye(center: Offset, r: Float, open: Float, t: Float) {
    val ballH = r * 2f * open
    drawOval(
        color = Color.White,
        topLeft = Offset(center.x - r, center.y - ballH / 2f),
        size = Size(r * 2f, ballH)
    )
    val pupilR = (r * 0.62f).coerceAtMost(ballH / 2f)
    val pupil = Offset(center.x, center.y + (r - ballH / 2f) * 0.5f)
    drawCircle(Color(0xFF1A1A1A), pupilR, pupil)
    // Sparkle when bright-eyed.
    if (t > 0.55f) {
        drawCircle(Color.White, pupilR * 0.32f, Offset(pupil.x - pupilR * 0.3f, pupil.y - pupilR * 0.35f))
    }
}

private fun DrawScope.drawRoundRectCompat(
    color: Color,
    topLeft: Offset,
    size: Size,
    corner: Float,
) {
    drawRoundRect(color = color, topLeft = topLeft, size = size, cornerRadius = CornerRadius(corner, corner))
}
