package com.example.brainrottracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * The app's card surface, centralizing the repeated `shadow → clip → background → border` chain
 * and the house rule "border in light, none in dark". Apply to an existing Column/Row when the
 * card has bespoke internal layout (image-bleed cards, Row-based insight cards):
 *
 *   Column(Modifier.appCard(colors.surface, border = !colors.isDark, borderColor = colors.border)
 *                  .padding(16.dp)) { … }
 */
fun Modifier.appCard(
    color: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    border: Boolean = false,
    borderColor: Color = Color.Transparent,
    shadow: Dp = 0.dp,
): Modifier = this
    .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
    .clip(shape)
    .background(color)
    .then(if (border) Modifier.border(1.dp, borderColor, shape) else Modifier)
