Every Android app has to ask permission before it can touch anything sensitive — the camera, your files, what other apps are doing on screen. LoopOut leans on some of the most powerful permissions Android offers, so this chapter walks through each one, why the app needs it, and the privacy and Play Store promises that come bundled with that power.

## What a permission actually is

A *permission* is a labelled capability that an app declares it wants. Think of it like a building's keycard system: the app's `AndroidManifest.xml` is the list of doors the app is asking for keys to, and the user (and Google) decides which keys to hand over.

> 💡 **Concept — The manifest is the app's "front door contract."** `AndroidManifest.xml` is a single XML file that tells Android everything structural about the app: its launcher icon, its name, which screens (activities) and background workers (services) exist, and — at the very top — every permission it wants. Android reads this file *before* a single line of your Kotlin runs.

Android sorts permissions into tiers:

| Tier | Granted how? | Example in LoopOut |
| --- | --- | --- |
| **Normal** | Automatically at install — user never sees a prompt | `INTERNET`, `VIBRATE`, `FOREGROUND_SERVICE` |
| **Runtime (dangerous)** | A pop-up dialog the first time the app needs it | `POST_NOTIFICATIONS` |
| **Special / sensitive** | A manual toggle the user must flip in **system Settings** | `SYSTEM_ALERT_WINDOW`, `PACKAGE_USAGE_STATS`, `BIND_ACCESSIBILITY_SERVICE` |

The special-access permissions are the interesting ones. Android deliberately makes them *hard* to grant — there is no quick "Allow" button, the user has to dig into Settings and flip a switch — because they let an app see or affect things far beyond its own window.

## Every permission in the manifest

Here is the complete permission block, copied verbatim from `AndroidManifest.xml`:

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

Each `<uses-permission>` line is a request. Let's go through them one at a time.

### SYSTEM_ALERT_WINDOW — the "draw over other apps" key

This is the permission that lets LoopOut paint things *on top of* other apps — the floating counter bubble and the full-screen blocking scrim drawn by `FloatingCounterService`. Without it, an app can only draw inside its own window.

It is a special-access permission: the user grants it manually in Settings under "Display over other apps." Android guards it tightly because an overlay can cover the whole screen, which is exactly how some malware tries to trick people into tapping fake buttons. LoopOut uses it for the opposite reason — to *interrupt* you when you blow past a limit.

### PACKAGE_USAGE_STATS — reading screen-time numbers

This grants access to `UsageStatsManager`, the system service that reports how long you've spent in each app. LoopOut reads it through `ScreenTimeHelper` to show your daily minutes per platform.

```xml
<uses-permission android:name="android.permission.PACKAGE_USAGE_STATS"
    tools:ignore="ProtectedPermissions" />
```

> ⚠️ **Gotcha — That `tools:ignore="ProtectedPermissions"` is not optional.** `PACKAGE_USAGE_STATS` is a "protected" permission normally reserved for system apps, so Android Studio's linter flags declaring it as an error. The `tools:ignore` attribute tells the linter "yes, I know, this is intentional." The app still can't just *take* the permission — the user must enable "Usage access" for LoopOut in Settings. The manifest line only makes the toggle *eligible* to appear.

### POST_NOTIFICATIONS — the one runtime prompt

On Android 13 (API 33) and newer, an app must ask before it can show notifications. This is the only permission LoopOut requests with a system pop-up dialog at runtime (during onboarding), rather than a Settings toggle. The app uses notifications for milestone alerts, the end-of-day summary, and the persistent foreground-service notice.

### FOREGROUND_SERVICE and FOREGROUND_SERVICE_SPECIAL_USE — staying alive in the background

A *foreground service* is a long-running task that Android promises not to casually kill, in exchange for showing a persistent notification so the user knows it's running. LoopOut's overlay (`FloatingCounterService`) is one, so it needs `FOREGROUND_SERVICE`.

Since Android 14 (API 34), every foreground service must also declare *what type* of work it does (media playback, location, data sync, …). LoopOut's overlay doesn't fit any standard category, so it uses the catch-all `FOREGROUND_SERVICE_SPECIAL_USE` — and Android then requires you to *justify* that choice in writing inside the manifest:

```xml
<property
    android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
    android:value="Tracks screen time on social media apps to help users manage their digital wellness" />
```

That `<property>` string is surfaced to Google's reviewers. The service itself declares its type in its `<service>` tag:

```xml
<service
    android:name=".service.FloatingCounterService"
    android:exported="false"
    android:foregroundServiceType="specialUse" />
```

> 💡 **Concept — `android:exported="false"` means "private to this app."** An *exported* component can be launched by other apps; a non-exported one cannot. The overlay service is `exported="false"` because nothing outside LoopOut should ever be able to pop up its blocking scrim. It is only ever started from inside the app, by `ReelCounterService`.

### VIBRATE and INTERNET — the quiet normals

These two are **normal** permissions, granted automatically with no prompt. `VIBRATE` lets the app buzz the phone (e.g. when you hit a limit). `INTERNET` is declared so the *optional* cloud-backup feature can reach Google's servers — without the permission, Firebase wouldn't be able to talk to Firestore at all. In the current version (v1) that feature is not yet switched on, so in normal day-to-day use the app does not send anything over the network.

### BIND_ACCESSIBILITY_SERVICE — the heart of the app

This one isn't in the `<uses-permission>` list. It's attached to the service declaration itself:

```xml
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

`BIND_ACCESSIBILITY_SERVICE` is the permission *Android itself* holds in order to talk to your service — declaring it here guarantees that only the system can bind to `ReelCounterService`. The accessibility framework was originally built so screen readers can describe the UI to blind users; LoopOut reuses that same "read the screen" power to detect when you're scrolling reels. The linked `@xml/accessibility_service_config` resource restricts the service to four package names — Instagram, YouTube, TikTok and Snapchat — and grants `canRetrieveWindowContent`. Because this permission lets an app observe *other apps' screens*, it is the most scrutinised permission in the entire project.

## Why Google scrutinises these permissions

Overlays, usage stats, and especially accessibility are the favourite tools of spyware and scam apps. So Google requires real-world apps that use them to (a) show a *prominent disclosure* before asking, and (b) justify the usage in the Play Console. LoopOut does both.

The prominent disclosure is a plain-language dialog shown during onboarding **before** the accessibility Settings screen ever opens, with the user's acceptance saved as `accessibility_disclosure_accepted`. The exact wording lives in `OnboardingScreen.kt` and is mirrored in `ACCESSIBILITY_DECLARATION.md`:

> **How Accessibility is used**
>
> LoopOut uses Android's Accessibility API to read screen content from Instagram, YouTube, TikTok and Snapchat only, in order to count the short videos you watch.
>
> This data is processed and stored only on your device — it never leaves your phone and is never shared with third parties.

The user must tap **Agree & Continue** (which persists the consent flag and opens Android's Accessibility settings) or **Not now** to back out. The declaration document also answers Google's standard questions: yes, accessibility *is* the core function; no, it can't be done another way (`UsageStatsManager` reports total time but can't distinguish short-form-video viewing from other in-app activity, gives no per-video signal, and offers no hook to interrupt a feed at a limit); and the service is locked to four package names.

## The privacy posture: on-device by default

The single most important promise in `PRIVACY_POLICY.md` is that LoopOut works **fully offline and entirely on your device** by default. The accessibility service reads the screen *in real time*, counts a reel, throws the screen content away, and keeps only a number.

> ⚠️ **Gotcha — Cloud backup and Google Sign-In aren't live yet.** The privacy policy is explicit that in the current version (v1) the app "collects and transmits **no** data," and that Google Sign-In and cloud backup "are **not available in this version**" — they are documented for transparency and planned for a future update. The table and explanation below describe how the optional backup is designed to behave *once it ships*; until then nothing leaves the phone.

| Data | Where it lives | Leaves the device? |
| --- | --- | --- |
| Reel counts, limits, streaks, brain-health score | Local Room database | No |
| Screen-time minutes | Queried live, never stored | No |
| The actual content of reels/messages | Never recorded at all | No |
| Daily numeric summaries (opt-in, future feature) | Firebase Firestore, your account | Only if you enable backup |
| Google account name/email/avatar (opt-in, future feature) | Firebase Authentication | Only if you sign in |

> 💡 **Concept — "Aggregates only."** When cloud backup is on, the app is designed to upload *one summary document per completed day* to `users/{uid}/dailyLogs/{date}` — just per-platform counts, per-platform limits, per-platform minutes (yesterday only), the brain-health score, and streak status. No screen content, no message text, no browsing history. An *aggregate* is a rolled-up number, not raw activity.

Both gates must be true before any upload happens: the user must (a) sign in with Google **and** (b) flip the cloud-backup toggle on. It is off by default and reversible at any time.

## The release configuration

A few `build.gradle.kts` settings exist purely to satisfy Play Store rules and protect users.

```kotlin
applicationId = "io.github.aasarmehdi.loopout"
```

The `applicationId` is the app's globally unique identity on Play — and it is **immutable once published**. You can rename the app a hundred times, but this string is frozen forever after the first upload, which is why the comment in the file calls it out. (Note that the Kotlin `namespace` stays `com.example.brainrottracker`; the two are allowed to differ.)

```kotlin
release {
    isMinifyEnabled = true
    isShrinkResources = true
    proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
    if (keystorePropsFile.exists()) {
        signingConfig = signingConfigs.getByName("release")
    }
}
```

`isMinifyEnabled` runs R8 to strip and rename unused code (smaller download, harder to reverse-engineer); `isShrinkResources` deletes unused images and layouts. Release signing reads credentials from an untracked `keystore.properties` — secrets never live in git — and if that file is absent the release build is simply left unsigned rather than failing.

Two more pieces fall out of the same privacy-and-policy story:

- The Firebase Google-services plugin is applied **only if `app/google-services.json` exists**, so the project builds and runs cleanly without a Firebase project configured — which is why the optional cloud features can ship later without breaking today's build.
- The Play listing and the accessibility declaration both require a **hosted privacy-policy URL** (`https://mehdiaasar.github.io/loopOut/`). Google won't approve an accessibility app without a public policy that discloses exactly what the optional Firestore upload would contain — which is precisely what `PRIVACY_POLICY.md` spells out.
