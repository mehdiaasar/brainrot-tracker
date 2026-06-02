package com.example.brainrottracker.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

private val BrainRotColorScheme = darkColorScheme(
    primary = PrimaryCyan,
    onPrimary = DarkBackground,
    primaryContainer = PrimaryBlue,
    onPrimaryContainer = TextPrimary,
    secondary = SuccessTeal,
    onSecondary = DarkBackground,
    secondaryContainer = SuccessGreen,
    onSecondaryContainer = DarkBackground,
    tertiary = InstagramPink,
    onTertiary = TextPrimary,
    error = DangerRed,
    onError = DarkBackground,
    errorContainer = DangerOrange,
    onErrorContainer = TextPrimary,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    outline = GlassBorder,
    outlineVariant = DividerColor,
    surfaceContainerHighest = CardBackground,
)

private val BrainRotShapes = Shapes(
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp),
)

@Composable
fun BrainRotTrackerTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = BrainRotColorScheme,
        typography = Typography,
        shapes = BrainRotShapes,
        content = content,
    )
}
