package com.example.brainrottracker.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrottracker.theme.AppTheme

/** A selectable icon+label pill (theme picker, blocking-mode chooser). Text colors come from the
 *  theme; the caller supplies the accent and the selected/unselected backgrounds. */
@Composable
fun PillToggleButton(
    icon: ImageVector,
    label: String,
    selected: Boolean,
    accent: Color,
    selectedBg: Color,
    unselectedBg: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colors = AppTheme.colors
    val shape = RoundedCornerShape(10.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) selectedBg else unselectedBg)
            .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 6.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            tint = if (selected) accent else colors.textSecondary,
            modifier = Modifier.size(17.dp)
        )
        Spacer(Modifier.width(5.dp))
        Text(
            label,
            color = if (selected) colors.textPrimary else colors.textSecondary,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1
        )
    }
}
