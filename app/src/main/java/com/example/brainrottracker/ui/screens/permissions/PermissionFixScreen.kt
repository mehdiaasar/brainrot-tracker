package com.example.brainrottracker.ui.screens.permissions

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.ui.permissions.AppPermission
import com.example.brainrottracker.ui.permissions.PermissionCard

/**
 * Post-onboarding "Uh-oh! Some permissions are missing" screen (BrainPal image 2). Lists one
 * [PermissionCard] per currently-missing permission with an "Allow" action. The [missing] list is
 * lifecycle-aware upstream (see `rememberMissingPermissions`), so it shrinks as the user grants
 * each permission; when it empties, the screen auto-closes.
 */
@Composable
fun PermissionFixScreen(
    missing: List<AppPermission>,
    requestPermission: (AppPermission) -> Unit,
    onClose: () -> Unit,
) {
    val colors = AppTheme.colors

    LaunchedEffect(missing) {
        if (missing.isEmpty()) onClose()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.background)
            .statusBarsPadding()
            .padding(horizontal = 24.dp)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(Modifier.height(8.dp))

        // Close (X)
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .clickable(onClick = onClose),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Close,
                contentDescription = "Close",
                tint = colors.textPrimary
            )
        }

        Spacer(Modifier.height(24.dp))

        Text(
            text = "Uh-oh! Some permissions are missing",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 32.sp,
            letterSpacing = (-0.5).sp,
            lineHeight = 38.sp,
            color = colors.textPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Fix the permissions for LoopOut to detect reels.",
            fontSize = 15.sp,
            lineHeight = 22.sp,
            color = colors.textSecondary
        )

        Spacer(Modifier.height(24.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            missing.forEach { perm ->
                PermissionCard(
                    title = perm.title,
                    description = perm.description,
                    isGranted = false,
                    onOpenSettings = { requestPermission(perm) },
                    actionLabel = "Allow"
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
