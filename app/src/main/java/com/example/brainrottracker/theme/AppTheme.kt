package com.example.brainrottracker.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable

/**
 * Single entry point for the design system. Reads the CompositionLocals provided by
 * [BrainRotTrackerTheme]. Use `AppTheme.colors`, `AppTheme.spacing`, `AppTheme.radii`
 * in screens; typography/shapes come straight from MaterialTheme.
 */
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val radii: Radii
        @Composable @ReadOnlyComposable get() = LocalRadii.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
}
