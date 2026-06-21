Before we trace a single line of LoopOut's logic, it helps to know what an Android app *is* made of. This chapter is a gentle primer: we'll learn the universal building blocks every Android app shares, and we'll ground each idea in LoopOut's own files so the concepts feel concrete instead of abstract.

## What is an Android app, really?

When you "install an app," your phone is unpacking a single file called an **APK** (Android Package Kit). Think of an APK as a sealed shipping box. Inside that box is everything the app needs: the compiled Kotlin/Java code, the images and icons, the text strings, and one special document that lists what's inside and what the app is allowed to do. That document is the `AndroidManifest.xml`.

> 💡 **Concept —** An APK is essentially a ZIP archive with a specific layout. Modern apps are usually shipped to the Play Store as an **AAB** (Android App Bundle), and Google generates per-device APKs from it. Either way, the manifest rides along inside.

### Package id vs. namespace

Every app on the Play Store needs a globally unique name so Android (and Google Play) can tell apps apart. LoopOut has two related names, and beginners constantly confuse them — so let's separate them clearly using LoopOut's real `build.gradle.kts`:

```kotlin
android {
    namespace = "com.example.brainrottracker"
    compileSdk = 36
    defaultConfig {
        // Immutable once published to Play. Confirmed as the LoopOut package name.
        applicationId = "io.github.aasarmehdi.brainrot"
```

- **`namespace`** (`com.example.brainrottracker`) is a *code* concept. It's the package prefix for the generated `R` class (the resource lookup table) and is woven into the project's folder structure. You'll see it at the top of every Kotlin file as `package com.example.brainrottracker`.
- **`applicationId`** (`io.github.aasarmehdi.brainrot`) is the app's *identity on the device and on the Play Store*. This is what makes the app unique among the millions on Google Play.

> ⚠️ **Gotcha —** These two can differ, and here they do. Renaming the user-facing app or its store identity (the `applicationId`) does **not** require renaming every code file (the `namespace`) — which is exactly why the namespace can stay `com.example.*` even though the published identity is `io.github.aasarmehdi.brainrot`. The comment in the file warns that `applicationId` is *immutable once published* — pick it carefully, because Play will never let you change it.

## The AndroidManifest.xml: the app's table of contents

If the APK is a shipping box, the manifest is the packing slip taped to the outside. The operating system reads it *before* running any of your code, so it can answer questions like: "What's the app's icon and name?", "Which screen do I open when the user taps the icon?", "What background features exist?", and "What sensitive permissions does this app want?"

Here's the outer shell of LoopOut's `AndroidManifest.xml`:

```xml
<manifest xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:tools="http://schemas.android.com/tools">

    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    ...
    <application
        android:allowBackup="true"
        android:icon="@mipmap/ic_launcher"
        android:label="@string/app_name"
        android:supportsRtl="true"
        android:theme="@style/Theme.BrainRotTracker">
```

- The `<manifest>` element wraps everything. The `xmlns:` lines are *namespaces* (an XML notion, unrelated to the Gradle `namespace` above) that let us write `android:...` attributes.
- The `<uses-permission>` lines (more on these soon) sit *outside* `<application>` because they describe the app as a whole.
- The `<application>` element holds app-wide settings: `@mipmap/ic_launcher` is the launcher icon, `@string/app_name` is the display name pulled from a strings resource, and `@style/Theme.BrainRotTracker` is the base visual theme. The `@` prefix means "look this value up in the resources box," rather than hard-coding it.

## The four component types

Android defines exactly **four** kinds of "components" — the entry points the system can start. Every Android app is assembled from these four LEGO bricks. Here's the full set, and which ones LoopOut uses:

| Component | What it is (analogy) | Used by LoopOut? |
|---|---|---|
| **Activity** | A single screen the user sees and touches — one "page" of the app | Yes — `MainActivity` |
| **Service** | A worker that runs without its own UI, often in the background | Yes — `ReelCounterService`, `FloatingCounterService` |
| **BroadcastReceiver** | A mailbox that wakes up when a system or app "broadcast" arrives | Yes — `BrainRotWidgetReceiver` |
| **ContentProvider** | A shared database that lets *other* apps query your data | No |

Let's meet each one as it appears in the real manifest.

### Activity — the screen

```xml
<activity
    android:name=".MainActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.MAIN" />
        <category android:name="android.intent.category.LAUNCHER" />
    </intent-filter>
</activity>
```

An **Activity** is one screen's worth of app. The leading dot in `.MainActivity` is shorthand for "the `MainActivity` class inside our `namespace` package" — so it expands to `com.example.brainrottracker.MainActivity`.

The `<intent-filter>` is the important bit. An **Intent** is Android's message for "please do something." This filter says: *"I respond to the MAIN action in the LAUNCHER category"* — which is the exact combination the home screen sends when a user taps an app icon. In other words, this block is what makes `MainActivity` the app's front door.

`android:exported="true"` means other apps (here, the launcher) are allowed to start it. That's required for a launcher activity.

> 💡 **Concept —** Surprisingly, LoopOut has only *one* Activity. Many modern Compose apps do. Instead of one Activity per screen, LoopOut keeps a single Activity and swaps the *content* inside it. We can see that in `MainActivity.kt`:

```kotlin
enableEdgeToEdge()
setContent {
    BrainRotTrackerTheme {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainNavigation()
        }
    }
}
```

`setContent { ... }` hands Jetpack Compose (the modern declarative UI toolkit) the entire screen. `MainNavigation()` then decides *which* of LoopOut's screens — Dashboard, Stats, Limits, Streaks — to draw. So the four "screens" you experience are Compose UI living inside this one Activity, not four separate Activities.

`MainActivity` extends `ComponentActivity`, the lightweight base class used by Compose-first apps. Its `onCreate` is the first code that runs when the app launches; that's why LoopOut uses it to initialize the database (`AppDatabase.getInstance(...)`), load the saved theme (`ThemeController.init(...)`), and create its notification channels (`NotificationHelper(...).createChannels()`) *before* calling `setContent` to show any UI.

### Service — the background worker

A **Service** has no screen of its own. It's a worker you start to keep doing a job. LoopOut declares two, and they show two *very different* flavors of service.

```xml
<!-- Accessibility Service for reel detection -->
<service
    android:name=".service.ReelCounterService"
    android:permission="android.permission.BIND_ACCESSIBILITY_SERVICE"
    android:exported="false">
    <intent-filter>
        <action android:name="android.accessibilityservice.AccessibilityService" />
    </intent-filter>
    <meta-data
        android:name="android.accessibilityservice"
        android:resource="@xml/accessibility_service_config" />
</service>
```

`ReelCounterService` is an **AccessibilityService** — a special service the *system* starts (only after the user manually enables it in Settings) so the app can observe on-screen events. That's how LoopOut "sees" when you're scrolling Reels or Shorts. The `android:permission="...BIND_ACCESSIBILITY_SERVICE"` line means Android will only bind to it if the binder holds that permission, which is a safety guarantee. The `<meta-data>` points to an XML config file (`accessibility_service_config.xml`) listing which apps it watches.

```xml
<!-- Floating counter overlay service (only started from inside the app) -->
<service
    android:name=".service.FloatingCounterService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

`FloatingCounterService` is a **foreground service** — one the user is always aware of via a persistent notification, because it does visible, ongoing work (drawing the floating counter bubble and the blocking screen). `android:foregroundServiceType="specialUse"` is a newer Android requirement: you must declare *why* you run in the foreground. The reason itself is spelled out at the bottom of the manifest:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Tracks screen time on social media apps to help users manage their digital wellness" />
```

> ⚠️ **Gotcha —** Both services set `android:exported="false"`. That means no *other* app can start them. `ReelCounterService` is bound by the system (once enabled in Settings); `FloatingCounterService` is started only from inside LoopOut. Marking internal components non-exported is an important security habit.

### BroadcastReceiver — the mailbox

```xml
<!-- Home screen widget -->
<receiver
    android:name=".widget.BrainRotWidgetReceiver"
    android:exported="true">
    <intent-filter>
        <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
    </intent-filter>
    <meta-data
        android:name="android.appwidget.provider"
        android:resource="@xml/brainrot_widget_info" />
</receiver>
```

A **BroadcastReceiver** is a mailbox that wakes up briefly when a matching broadcast (a system-wide announcement) arrives, does a quick job, and goes back to sleep. LoopOut's `BrainRotWidgetReceiver` powers its home-screen widget. The `APPWIDGET_UPDATE` action is the broadcast Android sends when the widget needs refreshing. It's `exported="true"` because the home screen (another process) must be able to deliver that update.

### ContentProvider — the one LoopOut skips

A **ContentProvider** exposes an app's data to *other* apps through a database-like interface (think of how the system Contacts app shares contacts). LoopOut keeps all its data private inside its own Room database, so it declares no ContentProvider. It's worth knowing the brick exists even though this app doesn't use it.

## Permissions: normal, dangerous, and special

A **permission** is the app asking the user (or the system) for the right to do something sensitive. LoopOut's manifest requests several:

```xml
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
<uses-permission android:name="android.permission.VIBRATE" />
<uses-permission android:name="android.permission.INTERNET" />
```

Permissions come in tiers. **Normal** permissions are low-risk and granted automatically at install time. **Dangerous** permissions touch private data and must be approved by the user at runtime via a popup. A third group — sometimes called **special** or **appop** permissions — are so powerful that the user must flip them on inside a dedicated Settings page; a popup isn't enough.

| Permission | Tier | What it lets LoopOut do |
|---|---|---|
| `INTERNET` | Normal | Talk to the network (optional cloud sync) |
| `VIBRATE` | Normal | Buzz the phone for alerts |
| `FOREGROUND_SERVICE` | Normal | Run a user-visible foreground service |
| `FOREGROUND_SERVICE_SPECIAL_USE` | Normal | Declare the "special use" foreground subtype |
| `POST_NOTIFICATIONS` | Dangerous (runtime) | Show notifications (API 33+ asks at runtime) |
| `SYSTEM_ALERT_WINDOW` | Special (Settings) | Draw the floating bubble *over* other apps |
| `PACKAGE_USAGE_STATS` | Special (Settings) | Read which apps you've used and for how long |

> 💡 **Concept —** The `tools:ignore="ProtectedPermissions"` on `PACKAGE_USAGE_STATS` simply silences a build-time warning. The build tools flag this permission as "protected" (not grantable the normal way); the `ignore` tells them *"yes, we know — this app deliberately guides the user to grant it in Settings."*

> ⚠️ **Gotcha —** Listing a special permission in the manifest does **not** grant it. `SYSTEM_ALERT_WINDOW` and `PACKAGE_USAGE_STATS` must be toggled on by the user in system Settings. This is exactly why LoopOut's onboarding flow walks you to those screens — the manifest only declares the *intent* to use them.

## Gradle: the build system that packs the box

You never assemble the APK by hand. **Gradle** is the build tool that compiles your Kotlin, processes resources, merges the manifest, and zips it all into the APK/AAB. LoopOut configures it in `build.gradle.kts` (the `.kts` means it's written in Kotlin script, rather than the older Groovy syntax).

The top of that file lists **plugins** — bundles of build behavior:

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}
```

`android.application` makes this an Android app project; `compose.compiler` enables Jetpack Compose; the others support kotlinx serialization (used for the navigation keys) and KSP, the Kotlin Symbol Processing tool that generates Room's database code at build time. The `dependencies { ... }` block lower down lists every library the app pulls in — Room for the database, Glance for the widget, Navigation 3, Firebase for optional sync, and more — and Gradle downloads and links them all for you.

> 💡 **Concept —** `libs.plugins.android.application` reads from a *version catalog* (a central `libs.versions.toml` file) so all version numbers live in one place. The `alias(...)` just references an entry from that catalog.

## SDK levels: minSdk, targetSdk, and compatibility

Android changes every year, and so does its API (the set of functions an app can call). Each yearly release has an **API level**, a simple integer. LoopOut pins three SDK numbers:

```kotlin
namespace = "com.example.brainrottracker"
compileSdk = 36
...
minSdk = 24
targetSdk = 36
```

- **`compileSdk = 36`** — the API version the code is *compiled against*. It's the newest set of functions the developer is allowed to call.
- **`minSdk = 24`** — the *oldest* Android version the app will install on (Android 7.0, Nougat). Phones older than that simply can't install LoopOut.
- **`targetSdk = 36`** — the version the app promises it has been *tested against*. Android uses this to decide whether to apply newer behavior changes or fall back to legacy behavior for compatibility.

> 💡 **Concept —** Think of it like a recipe. `minSdk` is the oldest oven you guarantee it works in; `targetSdk` is the modern oven you actually tuned it for; `compileSdk` is the cookbook edition you wrote it from. Supporting a wide range means the app reaches more phones but must be careful not to call new functions on old devices.

That wide-range support has a real consequence visible in the build file:

```kotlin
// java.time is used throughout but minSdk is 24 (java.time needs API 26)
isCoreLibraryDesugaringEnabled = true
```

LoopOut uses the modern `java.time` date classes — `MainActivity.kt`, for example, calls `LocalDate.now().minusDays(1)` to catch up on streaks — but those classes normally require API 26. **Desugaring** is a clever build-time trick that rewrites that code so it also works on API 24 and 25. It's a perfect example of how SDK levels shape day-to-day code decisions.

## Putting it together

You now have the vocabulary for the rest of the book. LoopOut is an APK identified by the `applicationId` `io.github.aasarmehdi.brainrot`, described by an `AndroidManifest.xml` that registers one Activity (`MainActivity`), two Services (`ReelCounterService`, `FloatingCounterService`), and one BroadcastReceiver (`BrainRotWidgetReceiver`), requests a careful mix of normal, dangerous, and special permissions, and is assembled by Gradle to run on everything from Android 7.0 up to the latest release. Every later chapter is just a deeper look at one of these pieces.
