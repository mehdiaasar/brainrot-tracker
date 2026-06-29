package com.example.brainrottracker.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import com.example.brainrottracker.data.model.Platform

/**
 * Original Compose-drawn renditions of each platform's logo, used to identify which app a
 * count belongs to (nominative use). Drawn as vectors — no bitmap assets required.
 */
@Composable
fun PlatformLogo(
    platform: Platform,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        when (platform) {
            Platform.INSTAGRAM -> drawInstagram()
            Platform.YOUTUBE -> drawYouTube()
            Platform.TIKTOK -> drawTikTok()
            Platform.SNAPCHAT -> drawSnapchat()
            Platform.FACEBOOK -> drawFacebook()
        }
    }
}

private fun DrawScope.drawInstagram() {
    val s = size.minDimension
    val corner = s * 0.28f
    // Warm gradient squircle.
    drawRoundRect(
        brush = Brush.linearGradient(
            colors = listOf(Color(0xFFFEDA77), Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)),
            start = Offset(0f, s),
            end = Offset(s, 0f)
        ),
        topLeft = Offset(0f, 0f),
        size = Size(s, s),
        cornerRadius = CornerRadius(corner, corner)
    )
    val stroke = Stroke(width = s * 0.085f)
    // Rounded-square camera body.
    val inset = s * 0.24f
    drawRoundRect(
        color = Color.White,
        topLeft = Offset(inset, inset),
        size = Size(s - inset * 2, s - inset * 2),
        cornerRadius = CornerRadius(s * 0.14f, s * 0.14f),
        style = stroke
    )
    // Lens.
    drawCircle(Color.White, radius = s * 0.155f, center = Offset(s / 2f, s / 2f), style = stroke)
    // Flash dot.
    drawCircle(Color.White, radius = s * 0.045f, center = Offset(s * 0.685f, s * 0.315f))
}

private fun DrawScope.drawYouTube() {
    val w = size.width
    val h = size.height
    val rectH = h * 0.72f
    val top = (h - rectH) / 2f
    drawRoundRect(
        color = Color(0xFFE62C2C),
        topLeft = Offset(0f, top),
        size = Size(w, rectH),
        cornerRadius = CornerRadius(rectH * 0.32f, rectH * 0.32f)
    )
    // White play triangle.
    val cx = w / 2f
    val cy = h / 2f
    val tri = rectH * 0.26f
    val play = Path().apply {
        moveTo(cx - tri * 0.7f, cy - tri)
        lineTo(cx - tri * 0.7f, cy + tri)
        lineTo(cx + tri * 0.95f, cy)
        close()
    }
    drawPath(play, Color.White)
}

private fun DrawScope.drawTikTok() {
    val s = size.minDimension
    val corner = s * 0.26f
    // Black squircle backdrop so the offset-color note reads on any theme.
    drawRoundRect(
        color = Color(0xFF010101),
        topLeft = Offset(0f, 0f),
        size = Size(s, s),
        cornerRadius = CornerRadius(corner, corner)
    )
    // Musical note, drawn three times with a cyan/pink offset for the signature glitch look.
    fun note(color: Color, dx: Float, dy: Float) {
        val stroke = Stroke(width = s * 0.085f, cap = StrokeCap.Round)
        val stem = Path().apply {
            moveTo(s * 0.56f + dx, s * 0.26f + dy)
            lineTo(s * 0.56f + dx, s * 0.62f + dy)
        }
        drawPath(stem, color, style = stroke)
        // Flag at the top of the stem.
        val flag = Path().apply {
            moveTo(s * 0.56f + dx, s * 0.26f + dy)
            quadraticBezierTo(s * 0.74f + dx, s * 0.28f + dy, s * 0.72f + dx, s * 0.40f + dy)
        }
        drawPath(flag, color, style = stroke)
        // Note head.
        drawCircle(color, radius = s * 0.085f, center = Offset(s * 0.45f + dx, s * 0.64f + dy))
    }
    note(Color(0xFF25F4EE), -s * 0.025f, s * 0.02f) // cyan
    note(Color(0xFFFE2C55), s * 0.025f, -s * 0.02f) // red/pink
    note(Color.White, 0f, 0f)
}

private fun DrawScope.drawSnapchat() {
    val s = size.minDimension
    val corner = s * 0.26f
    drawRoundRect(
        color = Color(0xFFFFFC00),
        topLeft = Offset(0f, 0f),
        size = Size(s, s),
        cornerRadius = CornerRadius(corner, corner)
    )
    // Simple ghost silhouette.
    val ghost = Path().apply {
        val left = s * 0.28f
        val right = s * 0.72f
        val topY = s * 0.22f
        val bottomY = s * 0.74f
        moveTo(left, bottomY)
        // body sides up to a rounded dome
        lineTo(left, s * 0.42f)
        quadraticBezierTo(left, topY, s / 2f, topY)
        quadraticBezierTo(right, topY, right, s * 0.42f)
        lineTo(right, bottomY)
        // scalloped bottom (three little bumps)
        quadraticBezierTo(s * 0.64f, bottomY + s * 0.07f, s * 0.575f, bottomY)
        quadraticBezierTo(s * 0.5f, bottomY + s * 0.07f, s * 0.425f, bottomY)
        quadraticBezierTo(s * 0.36f, bottomY + s * 0.07f, left, bottomY)
        close()
    }
    drawPath(ghost, Color.White)
    // Eyes + cheeks dots in the brand yellow.
    drawCircle(Color(0xFFFFFC00), radius = s * 0.03f, center = Offset(s * 0.43f, s * 0.42f))
    drawCircle(Color(0xFFFFFC00), radius = s * 0.03f, center = Offset(s * 0.57f, s * 0.42f))
}

private fun DrawScope.drawFacebook() {
    val s = size.minDimension
    val corner = s * 0.26f
    // Brand-blue squircle.
    drawRoundRect(
        color = Color(0xFF1877F2),
        topLeft = Offset(0f, 0f),
        size = Size(s, s),
        cornerRadius = CornerRadius(corner, corner)
    )
    // White lowercase "f": a vertical stem with a hooked top and a crossbar.
    val stroke = Stroke(width = s * 0.11f, cap = StrokeCap.Round)
    val stem = Path().apply {
        // Hook at the top, then straight down the stem.
        moveTo(s * 0.62f, s * 0.24f)
        quadraticBezierTo(s * 0.44f, s * 0.24f, s * 0.44f, s * 0.42f)
        lineTo(s * 0.44f, s * 0.78f)
    }
    drawPath(stem, Color.White, style = stroke)
    // Crossbar.
    drawPath(
        Path().apply {
            moveTo(s * 0.34f, s * 0.48f)
            lineTo(s * 0.60f, s * 0.48f)
        },
        Color.White,
        style = stroke
    )
}
