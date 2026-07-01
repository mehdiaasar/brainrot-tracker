package com.example.brainrottracker.ui.permissions

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.brainrottracker.data.local.prefs.Prefs
import com.example.brainrottracker.data.util.ScreenTimeHelper

/**
 * Single source of truth for the special permissions LoopOut needs and how to check/request them.
 *
 * The checks and request intents used to live inline in `OnboardingScreen`; they were extracted
 * here so onboarding, the global [PermissionBanner], and the [com.example.brainrottracker.ui.screens.permissions.PermissionFixScreen]
 * all agree on "what's missing" and share one accessibility-disclosure dialog + notification launcher.
 *
 * Order matches the onboarding checklist (accessibility first — it's the one that fully breaks
 * reel detection).
 */
enum class AppPermission(
    val title: String,
    val description: String,
    /** Required permissions drive the nag banner / fix screen; optional ones don't. */
    val required: Boolean = true,
) {
    ACCESSIBILITY(
        "Accessibility Access",
        "Allows LoopOut to detect active apps and screen usage."
    ),
    OVERLAY(
        "Overlay Permission",
        "Lets LoopOut show the floating counter over other apps."
    ),
    NOTIFICATIONS(
        "Notification Access",
        "Optional — enables daily focus summaries and gentle nudges.",
        required = false
    ),
    USAGE_STATS(
        "Usage Access",
        "Fetches accurate screen time directly from the system — the same data shown in Digital Wellbeing."
    ),
}

/** True when the given permission is currently granted. */
fun AppPermission.isGranted(context: Context): Boolean = when (this) {
    AppPermission.ACCESSIBILITY -> try {
        val services = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        services.contains(context.packageName)
    } catch (_: Exception) { false }

    AppPermission.OVERLAY -> Settings.canDrawOverlays(context)
    AppPermission.NOTIFICATIONS -> NotificationManagerCompat.from(context).areNotificationsEnabled()
    AppPermission.USAGE_STATS -> ScreenTimeHelper.hasPermission(context)
}

/** The subset of permissions not yet granted, in checklist order. */
fun missingPermissions(context: Context): List<AppPermission> =
    AppPermission.entries.filter { !it.isGranted(context) }

/**
 * Lifecycle-aware list of currently-missing permissions. Re-checks on every `ON_RESUME` so the
 * value refreshes the moment the user returns from a system settings screen. Drives the banner's
 * visibility and the fix screen's card list.
 */
@Composable
fun rememberMissingPermissions(): State<List<AppPermission>> {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val state = remember { mutableStateOf(missingPermissions(context)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) state.value = missingPermissions(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    return state
}

/**
 * Returns a `launch(permission)` function that kicks off the correct grant flow for each
 * permission. Hosting composable emits (normally invisible) the Play-policy accessibility
 * disclosure dialog and owns the POST_NOTIFICATIONS runtime launcher, so any caller gets both
 * for free just by calling this.
 */
@Composable
fun rememberPermissionRequester(): (AppPermission) -> Unit {
    val context = LocalContext.current
    val sharedPrefs = remember {
        context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    }

    // On API 33+ notifications need an actual runtime permission grant, not just a settings page.
    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result reflected via rememberMissingPermissions on next resume */ }
    var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }

    // Play policy: prominent disclosure + consent BEFORE sending the user to the accessibility page.
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
                    sharedPrefs.edit()
                        .putBoolean(Prefs.ACCESSIBILITY_DISCLOSURE_ACCEPTED, true).apply()
                    showAccessibilityDisclosure = false
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                }) { Text("Agree & Continue") }
            },
            dismissButton = {
                TextButton(onClick = { showAccessibilityDisclosure = false }) { Text("Not now") }
            }
        )
    }

    return { permission ->
        when (permission) {
            AppPermission.ACCESSIBILITY -> {
                if (sharedPrefs.getBoolean(Prefs.ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)) {
                    context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
                } else {
                    showAccessibilityDisclosure = true
                }
            }
            AppPermission.OVERLAY -> {
                context.startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        android.net.Uri.parse("package:${context.packageName}")
                    )
                )
            }
            AppPermission.NOTIFICATIONS -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    if (!notificationRequestAttempted) {
                        // First tap: show the system permission dialog directly
                        notificationRequestAttempted = true
                        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    } else {
                        // Repeat denials don't re-show the dialog — fall back to settings
                        context.startActivity(
                            Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                                putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                            }
                        )
                    }
                } else {
                    context.startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
            AppPermission.USAGE_STATS -> {
                context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        }
    }
}
