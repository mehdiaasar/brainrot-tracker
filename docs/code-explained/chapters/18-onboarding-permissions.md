The very first thing a new user sees in LoopOut is the onboarding screen. Its job is not to dazzle — it is to honestly explain, and then request, the four sensitive permissions LoopOut relies on to do its job. This chapter walks through `OnboardingScreen.kt`: how it checks which permissions are already granted, how it deep-links into Android's Settings, and why the accessibility permission is wrapped in a special consent dialog required by Google Play policy.

## Why permissions can't be granted silently

Android divides permissions into tiers. Ordinary ones (like vibrating the phone) are granted automatically when you install the app. But four of the capabilities LoopOut wants are *special* — they grant so much power that Android refuses to let an app simply ask for them with a pop-up. The user has to physically walk into the system Settings app and flip a switch.

> 💡 **Concept —** A *permission* is the operating system's way of saying "this app is allowed to do this potentially-invasive thing." The more dangerous the capability, the more friction Android puts between the app and the grant. Reading other apps' screen content (accessibility) is among the most powerful, so it demands the most ceremony.

Three of these are declared up front in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

Declaring a permission in the manifest is just the app *announcing its intent* to use it — like listing ingredients on a recipe. It does not grant anything. (Accessibility is the odd one out: it isn't a `<uses-permission>` at all. It's enabled when the user toggles LoopOut's `ReelCounterService` on in Settings; the manifest declares that service with `android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"` instead.)

| Permission | What it unlocks | How the user grants it |
|---|---|---|
| Accessibility | Reading reel/short-video screen content | Toggle the service in Accessibility settings |
| Overlay (`SYSTEM_ALERT_WINDOW`) | Drawing the floating counter over other apps | Per-app overlay settings page |
| Notifications (`POST_NOTIFICATIONS`) | Daily summaries and nudges | Runtime dialog (API 33+) or settings |
| Usage access (`PACKAGE_USAGE_STATS`) | Reading accurate system screen-time | Usage-access settings page |

## Tracking which permissions are already granted

The screen keeps four pieces of state — one boolean per permission:

```kotlin
var hasAccessibility by remember { mutableStateOf(false) }
var hasOverlay by remember { mutableStateOf(false) }
var hasNotifications by remember { mutableStateOf(false) }
var hasUsageStats by remember { mutableStateOf(false) }
```

> 💡 **Concept —** `remember { mutableStateOf(false) }` creates a value Jetpack Compose *watches*. When it changes, Compose automatically redraws any part of the screen that read it. That's how the progress bar and status badges update the instant a permission flips.

A single helper re-reads the live truth from Android each time it runs:

```kotlin
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
```

Each check asks a different corner of the system:

- **Accessibility** has no neat "is my service on?" API, so the code reads the secure setting `ENABLED_ACCESSIBILITY_SERVICES` — a colon-separated list of enabled services — and checks whether LoopOut's package name appears in it. (Reading this string can throw, so the whole thing is wrapped in a `try`/`catch` that defaults to `false`.)
- **Overlay** uses `Settings.canDrawOverlays(context)`, a direct yes/no.
- **Notifications** asks `NotificationManagerCompat` whether notifications are enabled.
- **Usage access** delegates to `ScreenTimeHelper.hasPermission`, which queries the `AppOpsManager` for `OPSTR_GET_USAGE_STATS` and returns true only when the mode is `MODE_ALLOWED`.

### Re-checking when the user comes back

Here's the trick that makes onboarding feel alive. When the user taps "Open Settings," LoopOut is pushed to the background while they flip a switch in another app. When they return, the screen must notice. That's what this does:

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) checkPermissions()
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

> 💡 **Concept —** The *lifecycle* is Android's running commentary on a screen's state: created, started, resumed, paused, stopped, destroyed. `ON_RESUME` fires every time the screen comes back to the foreground. By re-running `checkPermissions()` on resume, the badges flip from "PENDING" to "GRANTED" the moment the user navigates back from Settings — no refresh button needed.

`DisposableEffect` registers the observer when the screen appears and the `onDispose { ... }` block removes it when the screen goes away, so we never leak a dangling listener. A separate one-shot `remember { checkPermissions(); true }` runs the check once on first composition too, so the screen is already correct before the user touches anything.

## The accessibility prominent-disclosure dialog

The accessibility permission is special twice over. Technically it's the most powerful. But Google Play *policy* also requires that, before you send the user to enable an accessibility service, you show a **prominent disclosure**: a plain-language explanation of exactly what data you read and what you do with it. Skipping this can get an app removed from the store.

LoopOut implements that as an `AlertDialog`:

```kotlin
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
```

Read the `confirmButton` carefully — the *order* matters:

1. It persists consent by writing `ACCESSIBILITY_DISCLOSURE_ACCEPTED = true` into `SharedPreferences`.
2. It dismisses the dialog.
3. *Only then* does it open the Accessibility settings page via `Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)`.

> ⚠️ **Gotcha —** The disclosure must appear *before* the user reaches the toggle, not after. If the order were reversed, the user could enable the service without ever seeing the explanation — a policy violation. The consent is recorded persistently so it never has to be shown twice.

The accessibility card respects that saved consent. When the user taps "Open Settings" on the accessibility card:

```kotlin
onOpenSettings = {
    if (sharedPrefs.getBoolean(Prefs.ACCESSIBILITY_DISCLOSURE_ACCEPTED, false)) {
        context.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    } else {
        showAccessibilityDisclosure = true
    }
}
```

If consent was given on a previous run, jump straight to settings. Otherwise, show the dialog first. The `sharedPrefs` handle is opened once with `context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)`, where `Prefs` is a small object centralizing every SharedPreferences key and the file name (`"brainrot_prefs"`) — so a typo can't silently desync the consent flag from where it's read.

> 💡 **Concept —** `SharedPreferences` is Android's simple key-value store for small bits of state that must survive app restarts — perfect for a one-time "yes, I read the disclosure" flag.

## Deep-linking into Settings for overlay and usage access

Overlay and usage access have no runtime dialog at all. The only way in is to *deep-link* — fire an `Intent` that opens a specific Settings page.

```kotlin
val intent = Intent(
    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
    android.net.Uri.parse("package:${context.packageName}")
)
context.startActivity(intent)
```

> 💡 **Concept —** An `Intent` is a message saying "please do this action." `startActivity` hands it to Android, which finds the matching screen — here, the overlay-permission page. The `package:` URI tells Settings *which* app to show, so the user lands directly on LoopOut's entry instead of a long list.

Usage access is the same idea with `Settings.ACTION_USAGE_ACCESS_SETTINGS` (no package URI — that screen opens to the full app list). This deep-linking is the standard UX for special permissions: the app can't grant them, so the kindest thing it can do is drop the user as close to the switch as it can.

## Notifications: dialog first, settings later

Notifications are the most nuanced. On Android 13 (API 33, "Tiramisu") and newer, `POST_NOTIFICATIONS` is a genuine runtime permission with a one-time system dialog. But Android only shows that dialog *once* — after the first denial, re-launching it does nothing. So LoopOut tracks whether it has already tried:

```kotlin
val notificationLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
) { checkPermissions() }
var notificationRequestAttempted by rememberSaveable { mutableStateOf(false) }
```

`rememberLauncherForActivityResult` is the modern way to request a permission and get a callback — here it simply re-runs `checkPermissions()` once the user responds. `notificationRequestAttempted` uses `rememberSaveable` so it survives even a screen rotation.

The card's logic branches on all of this:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (!notificationRequestAttempted) {
        notificationRequestAttempted = true
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
} else {
    context.startActivity(Intent(Settings.ACTION_SETTINGS))
}
```

- **First tap (API 33+):** launch the real system permission dialog.
- **Any later tap:** the dialog won't reappear, so deep-link to the app's notification settings instead.
- **Older Android:** notifications are on by default, so just open the main Settings screen.

> ⚠️ **Gotcha —** Calling `notificationLauncher.launch(...)` a second time after the user has denied does nothing visible. Without the `notificationRequestAttempted` flag, the button would feel broken. Falling back to the settings page is what makes the second tap useful.

## Skip, continue, and never seeing this screen again

The four booleans combine into one value, `allGranted = hasAccessibility && hasOverlay && hasNotifications && hasUsageStats`, which drives the footer button. When everything is granted the button reads "Continue" in the accent color; otherwise it's a muted "Continue Anyway" with a "Skip for now" link below. Both buttons call the same `onComplete` — LoopOut never *forces* a grant. The app degrades gracefully and lets the user proceed, because each missing permission only disables the feature that needs it.

Tapping either button is also the *only* place this screen leads anywhere — onboarding itself doesn't decide what comes next. That decision lives one layer up. When `MainActivity` starts, its `onCreate` does background work (catching up streak evaluation and an optional cloud sync) and then hands off to `MainNavigation()`. The navigation layer reads the same `ENABLED_ACCESSIBILITY_SERVICES` secure setting and picks the start destination accordingly:

```kotlin
val startDestination: NavKey = if (initState!!.accessibilityEnabled) Dashboard else Onboarding
```

So a returning user who has already enabled the accessibility service is dropped straight onto the Dashboard and never sees onboarding again — while a fresh install, with the service still off, lands here to be walked through the four permissions one card at a time.
