package com.example.brainrottracker.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.ui.permissions.AppPermission
import com.example.brainrottracker.ui.permissions.PermissionCard
import com.example.brainrottracker.ui.permissions.rememberMissingPermissions
import com.example.brainrottracker.ui.permissions.rememberPermissionRequester

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AppTheme.colors
    val bg = colors.background
    val stepDim = colors.surfaceAlt
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary

    // Permission state + request flow live in the shared permissions module so onboarding and
    // the post-onboarding banner/fix screen can never drift.
    val missing by rememberMissingPermissions()
    val requestPermission = rememberPermissionRequester()

    val total = AppPermission.entries.size
    val grantedCount = total - missing.size
    // Gate the primary CTA on required permissions only — notifications is optional, so a user
    // who skips it still gets a clear "Continue" rather than "Continue Anyway".
    val allGranted = missing.none { it.required }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(bg)
            .padding(horizontal = 32.dp)
            .padding(top = 96.dp, bottom = 24.dp)
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // ── Logo ──────────────────────────────────────────────────────
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "✳",
                fontSize = 22.sp,
                color = textPrimary
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "LoopOut",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                color = textPrimary
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Permission progress ───────────────────────────────────────
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(240.dp)
            ) {
                repeat(total) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < grantedCount) colors.success else stepDim)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$grantedCount OF $total GRANTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = textSecondary
            )
        }

        Spacer(Modifier.height(32.dp))

        // ── Page title ────────────────────────────────────────────────
        Text(
            text = "Grant Permissions",
            fontFamily = FontFamily.Serif,
            fontWeight = FontWeight.Normal,
            fontSize = 36.sp,
            letterSpacing = (-0.5).sp,
            lineHeight = 41.sp,
            color = textPrimary,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(24.dp))

        // ── Permission cards ──────────────────────────────────────────
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            AppPermission.entries.forEach { perm ->
                PermissionCard(
                    title = perm.title,
                    description = perm.description,
                    isGranted = perm !in missing,
                    onOpenSettings = { requestPermission(perm) }
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // ── Footer ────────────────────────────────────────────────────
        // Primary CTA reflects state: a clear "Continue" once everything is
        // granted, otherwise a muted "Continue Anyway" with Skip still offered.
        Button(
            onClick = onComplete,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (allGranted) colors.accent else stepDim,
                contentColor = if (allGranted) Color.White else textPrimary
            )
        ) {
            Text(
                text = if (allGranted) "Continue" else "Continue Anyway",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }

        if (!allGranted) {
            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onComplete) {
                Text(
                    text = "Skip for now",
                    color = textSecondary,
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp
                )
            }
        }
    }
}
