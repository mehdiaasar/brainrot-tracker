package com.example.brainrottracker.ui.screens.onboarding

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.theme.rememberIsDark
import com.example.brainrottracker.theme.WarmAccent
import com.example.brainrottracker.theme.WarmBackground
import com.example.brainrottracker.theme.WarmBorder
import com.example.brainrottracker.theme.WarmGrantedGreen
import com.example.brainrottracker.theme.WarmLightBackground
import com.example.brainrottracker.theme.WarmLightBorder
import com.example.brainrottracker.theme.WarmLightInner
import com.example.brainrottracker.theme.WarmLightSurface
import com.example.brainrottracker.theme.WarmLightText
import com.example.brainrottracker.theme.WarmLightTextSecondary
import com.example.brainrottracker.theme.WarmStepDim
import com.example.brainrottracker.theme.WarmSurface
import com.example.brainrottracker.theme.WarmText
import com.example.brainrottracker.theme.WarmTextSecondary

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val dark = rememberIsDark()
    val bg = if (dark) WarmBackground else WarmLightBackground
    val surface = if (dark) WarmSurface else WarmLightSurface
    val cardBorder = if (dark) WarmBorder else WarmLightBorder
    val stepDim = if (dark) WarmStepDim else WarmLightInner
    val textPrimary = if (dark) WarmText else WarmLightText
    val textSecondary = if (dark) WarmTextSecondary else WarmLightTextSecondary

    var hasAccessibility by remember { mutableStateOf(false) }
    var hasOverlay by remember { mutableStateOf(false) }
    var hasNotifications by remember { mutableStateOf(false) }
    var hasUsageStats by remember { mutableStateOf(false) }

    fun checkPermissions() {
        hasAccessibility = try {
            val services = Settings.Secure.getString(
                context.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ) ?: ""
            services.contains(context.packageName)
        } catch (_: Exception) { false }

        hasOverlay = Settings.canDrawOverlays(context)
        hasNotifications = NotificationManagerCompat.from(context).areNotificationsEnabled()
        hasUsageStats = ScreenTimeHelper.hasPermission(context)
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    remember { checkPermissions(); true }

    val allGranted = hasAccessibility && hasOverlay && hasNotifications && hasUsageStats

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
                text = "BrainRot Tracker",
                fontFamily = FontFamily.Serif,
                fontWeight = FontWeight.Normal,
                fontSize = 28.sp,
                letterSpacing = (-0.3).sp,
                color = textPrimary
            )
        }

        Spacer(Modifier.height(24.dp))

        // ── Permission progress ───────────────────────────────────────
        val grantedCount = listOf(hasAccessibility, hasOverlay, hasNotifications, hasUsageStats).count { it }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.width(240.dp)
            ) {
                repeat(4) { i ->
                    Box(
                        Modifier
                            .weight(1f)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (i < grantedCount) WarmGrantedGreen else stepDim)
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = "$grantedCount OF 4 GRANTED",
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
            PermissionCard(
                title = "Accessibility Access",
                description = "Allows BrainRot Tracker to detect active apps and screen usage.",
                isGranted = hasAccessibility,
                surface = surface,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                btnBg = bg,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }
            )
            PermissionCard(
                title = "Overlay Permission",
                description = "Lets BrainRot Tracker show the floating counter over other apps.",
                isGranted = hasOverlay,
                surface = surface,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                btnBg = bg,
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                    context.startActivity(intent)
                }
            )
            PermissionCard(
                title = "Notification Access",
                description = "Enables daily focus summaries and gentle nudges.",
                isGranted = hasNotifications,
                surface = surface,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                btnBg = bg,
                onOpenSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    } else {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            )
            PermissionCard(
                title = "Usage Access",
                description = "Fetches accurate screen time directly from the system — the same data shown in Digital Wellbeing.",
                isGranted = hasUsageStats,
                surface = surface,
                cardBorder = cardBorder,
                textPrimary = textPrimary,
                textSecondary = textSecondary,
                btnBg = bg,
                onOpenSettings = {
                    context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
            )
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
                containerColor = if (allGranted) WarmAccent else stepDim,
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

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    surface: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    btnBg: Color,
    onOpenSettings: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(surface)
            .border(1.dp, cardBorder, RoundedCornerShape(12.dp))
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
                color = textPrimary
            )
            StatusBadge(isGranted = isGranted, cardBorder = cardBorder, textSecondary = textSecondary)
        }

        // Description
        Text(
            text = description,
            fontSize = 14.sp,
            lineHeight = 22.sp,
            color = textSecondary
        )

        // Open Settings button
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, cardBorder),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = btnBg,
                contentColor = textPrimary
            )
        ) {
            Text(
                text = "⚙  Open Settings",
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp
            )
        }
    }
}

@Composable
private fun StatusBadge(isGranted: Boolean, cardBorder: Color, textSecondary: Color) {
    if (isGranted) {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .background(WarmGrantedGreen)
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "GRANTED",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = Color.White
            )
        }
    } else {
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50.dp))
                .border(1.dp, cardBorder, RoundedCornerShape(50.dp))
                .padding(horizontal = 12.dp, vertical = 4.dp)
        ) {
            Text(
                text = "PENDING",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = 1.5.sp,
                color = textSecondary
            )
        }
    }
}
