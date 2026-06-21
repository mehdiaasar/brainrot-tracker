package com.example.brainrottracker.ui.screens.onboarding

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.example.brainrottracker.data.local.prefs.Prefs
import com.example.brainrottracker.data.util.ScreenTimeHelper
import com.example.brainrottracker.theme.AppTheme
import com.example.brainrottracker.ui.components.appCard

@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val colors = AppTheme.colors
    val bg = colors.background
    val stepDim = colors.surfaceAlt
    val textPrimary = colors.textPrimary
    val textSecondary = colors.textSecondary

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

    // On API 33+ notifications need an actual runtime permission grant, not just a settings page.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { checkPermissions() }
    var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }

    // Play policy: prominent disclosure + consent BEFORE sending the user to enable
    // the accessibility service.
    val sharedPrefs = remember { context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) }
    var showAccessibilityDisclosure by remember { mutableStateOf(false) }

    if (showAccessibilityDisclosure) {
        AlertDialog(
            onDismissRequest = { showAccessibilityDisclosure = false },
            title = { Text("How Accessibility is used") },
            text = {
                Text(
                    "LoopOut uses Android's Accessibility API to read screen content " +
                        "from Instagram, YouTube, TikTok and Snapchat only, in order to count the " +
                        "short videos you watch.\n\n" +
                        "This data is processed and stored only on your device — it never leaves " +
                        "your phone and is never shared with third parties."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    sharedPrefs.edit().putBoolean(Prefs.ACCESSIBILITY_DISCLOSURE_ACCEPTED, true).apply()
                    showAccessibilityDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Agree & Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) { Text("Not now") }
            }
        )
    }

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
                            .background(if (i < grantedCount) colors.success else stepDim)
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
                description = "Allows LoopOut to detect active apps and screen usage.",
                isGranted = hasAccessibility,
                onOpenSettings = {
                    if (sharedPrefs.getBoolean(Prefs.ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)) {
                        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                    } else {
                        showAccessibilityDisclosure = true
                    }
                }
            )
            PermissionCard(
                title = "Overlay Permission",
                description = "Lets LoopOut show the floating counter over other apps.",
                isGranted = hasOverlay,
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
                onOpenSettings = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (!notificationRequestAttempted) {
                            // First tap: show the system permission dialog directly
                            notificationRequestAttempted = true
                            notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            // Repeat denials don't re-show the dialog — fall back to settings
                            val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                            context.startActivity(intent)
                        }
                    } else {
                        context.startActivity(Intent(Settings.ACTION_SETTINGS))
                    }
                }
            )
            PermissionCard(
                title = "Usage Access",
                description = "Fetches accurate screen time directly from the system — the same data shown in Digital Wellbeing.",
                isGranted = hasUsageStats,
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

@Composable
private fun PermissionCard(
    title: String,
    description: String,
    isGranted: Boolean,
    onOpenSettings: () -> Unit
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

        // Open Settings button
        OutlinedButton(
            onClick = onOpenSettings,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            shape = RoundedCornerShape(8.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, colors.border),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = colors.background,
                contentColor = colors.textPrimary
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
