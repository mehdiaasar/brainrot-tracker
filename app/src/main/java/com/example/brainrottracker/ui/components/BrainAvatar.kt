package com.example.brainrottracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseInOutSine
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.InfiniteTransition
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrottracker.theme.BrainCritical
import com.example.brainrottracker.theme.BrainHealthy
import com.example.brainrottracker.theme.BrainOverstimulated
import com.example.brainrottracker.theme.BrainRot
import com.example.brainrottracker.theme.BrainTired
import kotlinx.coroutines.delay
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun BrainAvatar(
    healthScore: Int,
    modifier: Modifier = Modifier,
    size: Dp = 200.dp
) {
    val state = remember(healthScore) {
        when {
            healthScore >= 75 -> "HEALTHY"
            healthScore >= 50 -> "TIRED"
            healthScore >= 25 -> "OVERSTIMULATED"
            healthScore >= 10 -> "CRITICAL"
            else -> "ROT"
        }
    }

    // Dynamic state-specific color mapping
    val baseColor by animateColorAsState(
        targetValue = when (state) {
            "HEALTHY" -> BrainHealthy
            "TIRED" -> BrainTired
            "OVERSTIMULATED" -> BrainOverstimulated
            "CRITICAL" -> BrainCritical
            else -> BrainRot
        },
        animationSpec = tween(800),
        label = "brainColor"
    )

    val secondColor by animateColorAsState(
        targetValue = when (state) {
            "HEALTHY" -> Color(0xFF00B4D8)      // Teal/Cyan helper
            "TIRED" -> Color(0xFFD97706)        // Saturated Amber
            "OVERSTIMULATED" -> Color(0xFFEA580C)  // Saturated Orange
            "CRITICAL" -> Color(0xFFDC2626)      // Saturated Red
            else -> Color(0xFF4C0519)            // Deep Rose Black
        },
        animationSpec = tween(800),
        label = "brainColor2"
    )

    // Dynamic pulse duration based on stimulation state
    val pulseDuration = when (state) {
        "HEALTHY" -> 3000
        "TIRED" -> 2000
        "OVERSTIMULATED" -> 1200
        "CRITICAL" -> 700
        else -> 400 // Fast erratic vibration pulse
    }

    val infiniteTransition: InfiniteTransition = rememberInfiniteTransition(label = "brainAnim")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = if (state == "ROT") 0.93f else 0.88f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val glowAlpha by infiniteTransition.animateFloat(
        initialValue = if (state == "ROT") 0.05f else 0.1f,
        targetValue = if (state == "ROT") 0.15f else 0.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(pulseDuration, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "glowAlpha"
    )

    // Rotating ring
    val ringRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (state == "ROT") 4000 else 8000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ringRotation"
    )

    // Jitter/Glitch Offset for high stimulation states
    var shakeX by remember { mutableStateOf(0f) }
    var shakeY by remember { mutableStateOf(0f) }

    LaunchedEffect(state) {
        if (state == "ROT") {
            while (true) {
                shakeX = ((-6..6).random()).toFloat()
                shakeY = ((-6..6).random()).toFloat()
                delay(40)
            }
        } else if (state == "CRITICAL") {
            while (true) {
                shakeX = ((-2..2).random()).toFloat()
                shakeY = ((-2..2).random()).toFloat()
                delay(60)
            }
        } else {
            shakeX = 0f
            shakeY = 0f
        }
    }

    // Arc fill for score
    val arcSweep = remember { Animatable(0f) }
    LaunchedEffect(healthScore) {
        arcSweep.animateTo(
            targetValue = 360f * (healthScore / 100f),
            animationSpec = tween(1200, easing = FastOutSlowInEasing)
        )
    }

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(size)
            .graphicsLayer {
                translationX = shakeX
                translationY = shakeY
            }
    ) {
        Canvas(modifier = Modifier.size(size)) {
            val center = Offset(this.size.width / 2, this.size.height / 2)
            val maxRadius = this.size.minDimension / 2

            // ── Outermost subtle ambient glow (toned down by 60%) ─────────────
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        baseColor.copy(alpha = glowAlpha * 0.25f),
                        Color.Transparent
                    ),
                    center = center,
                    radius = maxRadius * 1.35f * pulseScale
                ),
                radius = maxRadius * 1.35f * pulseScale,
                center = center
            )

            // ── Rotating orbit rings (only show when healthy/tired to prevent visual noise) ──
            if (state == "HEALTHY" || state == "TIRED") {
                rotate(ringRotation, pivot = center) {
                    val dashCount = 8
                    val orbitRadius = maxRadius * 0.88f
                    for (d in 0 until dashCount) {
                        val angle = Math.toRadians((d * 360.0 / dashCount))
                        val x = center.x + orbitRadius * cos(angle).toFloat()
                        val y = center.y + orbitRadius * sin(angle).toFloat()
                        drawCircle(
                            color = baseColor.copy(alpha = 0.25f),
                            radius = 3f,
                            center = Offset(x, y)
                        )
                    }
                }
            }

            // ── Main brain lobes silhouette ─────────────────────────
            val r = maxRadius * 0.45f * pulseScale
            val lobeRadius = r * 0.52f

            // Left hemisphere lobes centers
            val lFrontal = Offset(center.x - r * 0.35f, center.y - r * 0.3f)
            val lParietal = Offset(center.x - r * 0.45f, center.y + r * 0.05f)
            val lOccipital = Offset(center.x - r * 0.3f, center.y + r * 0.35f)
            val lTemporal = Offset(center.x - r * 0.15f, center.y + r * 0.05f)

            // Right hemisphere lobes centers
            val rFrontal = Offset(center.x + r * 0.35f, center.y - r * 0.3f)
            val rParietal = Offset(center.x + r * 0.45f, center.y + r * 0.05f)
            val rOccipital = Offset(center.x + r * 0.3f, center.y + r * 0.35f)
            val rTemporal = Offset(center.x + r * 0.15f, center.y + r * 0.05f)

            // Dynamic opacity for synapses
            val synapseAlpha = when (state) {
                "ROT" -> 0.1f
                "CRITICAL" -> 0.25f
                else -> 0.4f
            }
            val synapseColor = secondColor.copy(alpha = synapseAlpha)

            fun drawSynapse(from: Offset, to: Offset) {
                drawLine(
                    color = synapseColor,
                    start = from,
                    end = to,
                    strokeWidth = 2f
                )
            }

            // Draw synapses (neural network paths)
            drawSynapse(lFrontal, lParietal)
            drawSynapse(lParietal, lOccipital)
            drawSynapse(lOccipital, lTemporal)
            drawSynapse(lTemporal, lFrontal)
            drawSynapse(rFrontal, rParietal)
            drawSynapse(rParietal, rOccipital)
            drawSynapse(rOccipital, rTemporal)
            drawSynapse(rTemporal, rFrontal)
            drawSynapse(lFrontal, rFrontal)
            drawSynapse(lOccipital, rOccipital)

            // Draw the lobes (glowing overlapping spheres representing brain halves)
            val lobesList = listOf(
                lFrontal, lParietal, lOccipital, lTemporal,
                rFrontal, rParietal, rOccipital, rTemporal
            )

            lobesList.forEach { lobeCenter ->
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            baseColor.copy(alpha = if (state == "ROT") 0.4f else 0.55f),
                            baseColor.copy(alpha = 0.05f)
                        ),
                        center = lobeCenter,
                        radius = lobeRadius * 1.3f
                    ),
                    radius = lobeRadius,
                    center = lobeCenter
                )
                // Specular highlights
                drawCircle(
                    color = Color.White.copy(alpha = if (state == "ROT") 0.05f else 0.15f),
                    radius = lobeRadius * 0.25f,
                    center = Offset(lobeCenter.x - lobeRadius * 0.2f, lobeCenter.y - lobeRadius * 0.2f)
                )
            }

            // ── Inner core of focus energy ───────────────────────────
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = if (state == "ROT") 0.1f else 0.35f),
                        baseColor.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = center
                ),
                radius = r * 0.45f,
                center = center
            )

            // ── Progress arc (Health score meter) ───────────────────────────
            // Outer track
            drawArc(
                color = Color.White.copy(alpha = 0.05f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - maxRadius * 0.85f, center.y - maxRadius * 0.85f),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.7f, maxRadius * 1.7f)
            )
            // Glowing sweep fill
            drawArc(
                brush = Brush.sweepGradient(
                    colors = listOf(
                        secondColor.copy(alpha = 0.8f),
                        baseColor,
                        secondColor.copy(alpha = 0.8f)
                    ),
                    center = center
                ),
                startAngle = -90f,
                sweepAngle = arcSweep.value,
                useCenter = false,
                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round),
                topLeft = Offset(center.x - maxRadius * 0.85f, center.y - maxRadius * 0.85f),
                size = androidx.compose.ui.geometry.Size(maxRadius * 1.7f, maxRadius * 1.7f)
            )
        }

        // Central health score typography display
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = "$healthScore",
                style = MaterialTheme.typography.displaySmall.copy(
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = (size.value * 0.22f).sp,
                    color = Color.White
                )
            )
        }
    }
}
