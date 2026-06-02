package com.example.brainrottracker.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle

@Composable
fun AnimatedCounter(
    count: Int,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle.Default,
) {
    // Break into individual digits for per-digit rolling animation
    val countStr = count.toString()
    Row(modifier = modifier) {
        countStr.forEach { digit ->
            AnimatedContent(
                targetState = digit,
                transitionSpec = {
                    // Slide up when increasing
                    slideInVertically { -it } togetherWith slideOutVertically { it }
                },
                label = "digit_$digit"
            ) { targetDigit ->
                Text(
                    text = targetDigit.toString(),
                    style = style
                )
            }
        }
    }
}
