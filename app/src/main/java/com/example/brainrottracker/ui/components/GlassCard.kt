package com.example.brainrottracker.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.example.brainrottracker.theme.CardBackground
import com.example.brainrottracker.theme.GlassBorder
import com.example.brainrottracker.theme.PrimaryCyan

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = PrimaryCyan,
    enableTilt: Boolean = true,
    backgroundColor: Color = CardBackground.copy(alpha = 0.7f),
    content: @Composable () -> Unit
) {
    var isPressed by remember { mutableStateOf(false) }
    var tiltX by remember { mutableStateOf(0f) }
    var tiltY by remember { mutableStateOf(0f) }

    val animatedTiltX by animateFloatAsState(
        targetValue = if (isPressed) tiltX else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tiltX"
    )
    val animatedTiltY by animateFloatAsState(
        targetValue = if (isPressed) tiltY else 0f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 300f),
        label = "tiltY"
    )
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.97f else 1f,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "scale"
    )

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                if (enableTilt) {
                    rotationX = animatedTiltY * 8f
                    rotationY = animatedTiltX * 8f
                    cameraDistance = 12f * density
                }
                shadowElevation = if (isPressed) 2.dp.toPx() else 12.dp.toPx()
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = { offset ->
                        isPressed = true
                        if (enableTilt) {
                            val centerX = size.width / 2f
                            val centerY = size.height / 2f
                            tiltX = ((offset.x - centerX) / centerX).coerceIn(-1f, 1f)
                            tiltY = -((offset.y - centerY) / centerY).coerceIn(-1f, 1f)
                        }
                        tryAwaitRelease()
                        isPressed = false
                        tiltX = 0f
                        tiltY = 0f
                    }
                )
            }
            .drawBehind {
                // Subtle premium depth shadow glow
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.02f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.maxDimension * 0.7f
                    ),
                    radius = size.maxDimension * 0.7f,
                    center = Offset(size.width / 2f, size.height * 0.9f)
                )
            },
        shape = RoundedCornerShape(24.dp), // More premium rounded corners (24.dp)
        color = backgroundColor,
        border = BorderStroke(
            width = 1.dp,
            brush = Brush.linearGradient(
                colors = listOf(
                    accentColor.copy(alpha = 0.15f),
                    GlassBorder.copy(alpha = 0.3f),
                    accentColor.copy(alpha = 0.05f)
                )
            )
        ),
        tonalElevation = 0.dp,
        shadowElevation = 8.dp,
    ) {
        Box(
            modifier = Modifier
                .drawBehind {
                    // Inner glass shimmer highlight (top-left to bottom-right)
                    drawRect(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color.White.copy(alpha = 0.06f),
                                Color.Transparent
                            ),
                            start = Offset(0f, 0f),
                            end = Offset(size.width * 0.6f, size.height * 0.6f)
                        )
                    )
                }
                .padding(16.dp)
        ) {
            content()
        }
    }
}
