package com.example.brainrottracker.ui.permissions

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.ui.components.appCard

/**
 * One permission row: title + GRANTED/PENDING badge, description, and an action button.
 * Shared by the onboarding checklist and the post-onboarding permission fix screen so both
 * render identically. [actionLabel] lets the fix screen say "Allow" while onboarding keeps
 * "⚙  Open Settings".
 */
@Composable
fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit,
    actionLabel: String = "⚙  Open Settings",
) {
    val colors = AppTheme.colors
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .appCard(
                color = colors.surface,
                shape = RoundedCornerShape(12.dp),
                border = true,
                borderColor = colors.border,
            )
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Title row with status badge
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                fontWeight = FontWeight.Medium,
                fontSize = 16.sp,
                lineHeight = 22.sp,
                color = colors.textPrimary,
                // Take the remaining width so the badge keeps its full size and never gets
                // squeezed into a two-line "GRANTE D" on narrower screens.
                modifier = Modifier.weight(1f, fill = false).padding(end = 12.dp)
            )
            StatusBadge(isGranted = isGranted)
        }

        // Description
        Text(
            text = description,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = colors.textSecondary
        )

        // Action button
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            border = BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.background,
                contentColor = colors.textPrimary
            )
        ) {
            Text(
                text = actionLabel,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StatusBadge(isGranted: Boolean) {
    val colors = AppTheme.colors
    if (isGranted) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(colors.success)
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "GRANTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = Color.White,
                maxLines = 1,
                softWrap = false
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .border(1.dp, colors.border, RoundedCornerShape(50.dp))
                .padding(horizontal = 12.dp, vertical = 5.dp)
        ) {
            Text(
                text = "PENDING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.sp,
                color = colors.textSecondary,
                maxLines = 1,
                softWrap = false
            )
        }
    }
}
