Before any of LoopOut's screens or services can run, something has to *assemble* the app: gather the code, download every library it depends on, compile it, and package it into an installable file. That job belongs to the build system. This chapter walks through how LoopOut is put together — the folders, the build scripts, and the catalog of libraries — so that when later chapters mention "Room" or "Compose," you already know where they come from and why they're there.

## The big picture: Gradle and modules

LoopOut is built by **Gradle**, the standard build tool for Android. Think of Gradle as a very precise recipe-follower: you write down what your app needs and how it should be packaged, and Gradle does the fetching, compiling, and bundling.

> 💡 **Concept — what is a "module"?** A module is a self-contained chunk of the project that Gradle builds as one unit. Big apps split into many modules; LoopOut is a **single-module** app. The one module is called `:app`.

That single module is declared in `settings.gradle.kts`:

```kotlin
rootProject.name = "BrainRot Tracker"
include(":app")
```

`include(":app")` tells Gradle "there is exactly one module, named `app`." The display name `"BrainRot Tracker"` is the old internal name (the user-facing name is now *LoopOut*; the rename hasn't touched this line, which is harmless).

The rest of `settings.gradle.kts` lists **repositories** — the online servers Gradle downloads libraries from. `google()` hosts Android and Google libraries, `mavenCentral()` hosts most everything else. The `includeGroupByRegex(...)` lines are a small optimization telling Gradle "only look on Google's server for libraries whose names start with `androidx`, `com.android`, or `com.google`," so it doesn't waste time searching there for unrelated packages.

> ⚠️ **Gotcha — two build files, different jobs.** There's a `build.gradle.kts` at the project root *and* one inside `app/`. The root one configures things shared across all modules; the `app/` one configures the app itself. They are not interchangeable.

## The root build file

The top-level `build.gradle.kts` is short:

```kotlin
plugins {
  alias(libs.plugins.android.application) apply false
  alias(libs.plugins.compose.compiler) apply false
  alias(libs.plugins.kotlin.serialization) apply false
  alias(libs.plugins.ksp) apply false
  alias(libs.plugins.google.services) apply false
}
```

A **plugin** is an add-on that teaches Gradle a new skill — how to build an Android app, how to compile Compose UI, and so on. Here every plugin has `apply false`, which means "make this plugin *available* to the whole project, but don't switch it on here." The actual switching-on happens in `app/build.gradle.kts`. This split keeps plugin *versions* defined in one place while letting each module pick which ones it uses.

## The version catalog: one place for every version number

Notice the plugins are referenced as `libs.plugins.android.application`, not by a raw name. That `libs.` prefix points to the **version catalog** in `gradle/libs.versions.toml` — a single file that names every library and version the project uses.

> 💡 **Concept — why a catalog?** Without it, version numbers get scattered across build files and drift out of sync. The catalog is the single source of truth: bump a number once and every module that references it updates together.

The file has three sections. `[versions]` holds the numbers:

```toml
kotlin = "2.3.20"
androidxComposeBom = "2026.03.01"
room = "2.7.1"
ksp = "2.3.9"
```

`[libraries]` gives each dependency a friendly name and points it at a version:

```toml
androidx-room-runtime = { module = "androidx.room:room-runtime", version.ref = "room" }
```

This says: the catalog entry `androidx-room-runtime` means the library `androidx.room:room-runtime`, at whatever version the `room` key holds. In Kotlin you reach it as `libs.androidx.room.runtime` (dashes become dots).

`[plugins]` does the same for plugins:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

> 💡 **Concept — what is a "dependency"?** A dependency is code written by someone else that your app reuses instead of rewriting. When you list `androidx.room:room-runtime`, Gradle downloads that library and links it in. The three-part name is `group:artifact:version`.

## The app build file, top to bottom

Now the heart of the chapter: `app/build.gradle.kts`. It opens by turning on the plugins it needs:

```kotlin
plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.ksp)
}
```

Each `alias(...)` reads from the catalog. `android.application` makes this an Android app; `compose.compiler` compiles Jetpack Compose UI; `kotlin.serialization` enables turning objects into a saveable form (used by the navigation keys); `ksp` is explained below.

### Conditionally applying google-services

Right after the plugins comes a clever piece of logic:

```kotlin
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}
```

The Firebase plugin (`google-services`) refuses to build unless a `google-services.json` config file is present — and that file only exists once you've set up a Firebase project. So instead of applying the plugin unconditionally, the build *checks whether the file exists* and only applies it if so. This is why LoopOut compiles and runs perfectly fine without any Firebase setup — the cloud features simply stay dormant.

### Reading signing credentials from a file

Next, the build loads release-signing secrets:

```kotlin
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}
```

> 💡 **Concept — what is "signing"?** Every Android app shipped to the Play Store must be cryptographically *signed* with a private key, proving it came from you. The key lives in a *keystore* file, protected by passwords. You never commit those secrets to git.

This code reads a `keystore.properties` file *if it exists*. The file is gitignored (there's a `keystore.properties.example` template). If it's missing, `keystoreProps` is simply empty, and — as we'll see — the release build comes out unsigned, which is fine for local testing.

### The android block: who the app is and what it targets

```kotlin
android {
    namespace = "com.example.brainrottracker"
    compileSdk = 36
    defaultConfig {
        applicationId = "io.github.aasarmehdi.loopout"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }
```

There are two different "names" here, and beginners constantly confuse them:

| Setting | Value | What it means |
|---|---|---|
| `namespace` | `com.example.brainrottracker` | The internal Kotlin package prefix — where the `.kt` files live. Used only at compile time. |
| `applicationId` | `io.github.aasarmehdi.loopout` | The app's unique identity on a device and on the Play Store. **Immutable once published.** |

The `namespace` stays as the original `com.example.brainrottracker` (renaming every package would be churn with no user benefit), while the `applicationId` is the real published identity.

The SDK numbers describe Android API levels:

| Setting | Value | Meaning |
|---|---|---|
| `minSdk` | 24 | Oldest Android version that can install the app (Android 7.0). |
| `targetSdk` | 36 | The version the app is tested and tuned against. |
| `compileSdk` | 36 | The version of the Android API used to *compile* the code. |

`versionCode = 1` is an internal counter Play uses to tell builds apart; `versionName = "1.0"` is the human-readable version users see.

### Signing config and the release build type

```kotlin
    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }
```

Only if the properties file exists does the build create a `release` signing config, pulling each value out of the loaded properties. No file, no signing config — matching the conditional logic from earlier.

```kotlin
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (keystorePropsFile.exists()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }
```

A **build type** is a flavor of the build. `release` is the one you ship; `debug` (provided automatically) is for development.

- `isMinifyEnabled = true` turns on **R8**, which renames and removes unused code to shrink and obfuscate the app.
- `isShrinkResources = true` strips unused images and layouts.
- `proguardFiles(...)` points R8 at the rules controlling what it's allowed to remove. The custom `proguard-rules.pro` protects things R8 can't see are needed — notably the serialized navigation keys.
- The `signingConfig` line attaches the release key *only* if it exists.

> ⚠️ **Gotcha — R8 can break reflection-based code.** Minification renames classes, which can break libraries that find classes by name at runtime (like serialization). That's exactly why `proguard-rules.pro` exists and why the project's notes say to smoke-test minified builds.

### compileOptions and desugaring

```kotlin
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
```

The first two lines say the code uses Java 17 language features. The third is subtle and important:

> 💡 **Concept — core library desugaring.** LoopOut uses the modern `java.time` date/time classes everywhere. But `java.time` only exists natively on Android API 26+, and `minSdk` is 24. **Desugaring** backports those classes so they work even on API 24–25. The matching dependency (`desugar.jdk.libs`) at the top of the `dependencies` block supplies the backported code.

### buildFeatures

```kotlin
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }
```

`compose = true` is the switch that lets you write UI with Jetpack Compose. The other three turn *off* features LoopOut doesn't use (`aidl` for cross-process interfaces, `buildConfig` for a generated config class, `shaders` for graphics shaders) — disabling unused features speeds up the build.

> 💡 **Concept — the JVM toolchain.** Just after the `android { }` block, a one-liner `kotlin { jvmToolchain(17) }` tells Gradle to compile against a Java 17 toolchain. It's the Kotlin-side companion to the Java 17 `compileOptions` above — together they pin the whole module to Java 17.

## The dependencies block

Finally, the long list of libraries. The first is the desugaring backport:

```kotlin
coreLibraryDesugaring(libs.desugar.jdk.libs)
```

Then the Compose **BOM**:

```kotlin
val composeBom = platform(libs.androidx.compose.bom)
implementation(composeBom)
```

> 💡 **Concept — what is a BOM?** A BOM (Bill of Materials) is a coordinated version list. Compose ships as many separate libraries that must all agree on versions. Instead of pinning each one, you declare the BOM, and it decides matching versions for all of them. Notice how the Compose entries in the catalog (like `androidx-compose-material3`) have **no** `version.ref` — the BOM fills it in.

> 💡 **Concept — `implementation` vs `ksp`.** `implementation(...)` adds a library your code calls at runtime. `ksp(...)` adds a **compile-time code generator**. KSP (Kotlin Symbol Processing) reads your annotations during compilation and writes extra Kotlin for you. Room uses it to turn your `@Dao` interfaces into real database code:
>
> ```kotlin
> implementation(libs.androidx.room.runtime)
> implementation(libs.androidx.room.ktx)
> ksp(libs.androidx.room.compiler)
> ```
>
> The compiler is `ksp`, not `implementation`, because it runs at build time and isn't shipped in the app.

Here's what the major dependency groups are *for*:

| Group | Catalog entries | Purpose |
|---|---|---|
| Compose UI | `compose.ui`, `compose.ui.tooling.preview`, `compose.material3` | The declarative UI toolkit and Material 3 widgets. |
| Material icons | `compose.material.icons.extended` | The extended Material icon set (versioned on its own, *not* by the Compose BOM). |
| Core + Lifecycle + Activity | `core.ktx`, `activity.compose`, `lifecycle.*` | Hosting Compose in an Activity and powering ViewModels. |
| Navigation 3 | `navigation3.ui`, `navigation3.runtime`, `lifecycle.viewmodel.navigation3` | Moving between screens (the alpha Navigation 3 library). |
| Room | `room.runtime`, `room.ktx`, `room.compiler` | The on-device SQLite database for daily logs, limits, streaks. |
| Glance | `glance.appwidget`, `glance.material3` | Building the home-screen widget with Compose-style code. |
| DataStore | `datastore.preferences` | Modern key-value storage (sign-in state). |
| Credential Manager + googleid | `credentials`, `credentials.play.services`, `googleid` | The Google sign-in flow. |
| Firebase | `firebase.bom`, `firebase.auth`, `firebase.firestore`, `coroutines.play.services` | Optional cloud auth and the Firestore backup of daily aggregates. |
| Coil | `coil.compose` | Loading the user's Google profile picture from a URL. |
| Testing | `junit`, `coroutines.test`, `androidx.test.*`, `espresso`, `compose.ui.test.*` | Unit and instrumented tests. |

Note that Firebase, like Compose, uses a BOM (`platform(libs.firebase.bom)`), so `firebase.auth` and `firebase.firestore` carry no version in the catalog.

> 💡 **Concept — the `implementation` / `testImplementation` / `androidTestImplementation` / `debugImplementation` split.** These are *configurations* that decide *when* a dependency is on the classpath. `implementation` ships in the app; `testImplementation` is only for local JVM unit tests; `androidTestImplementation` is only for on-device instrumented tests; `debugImplementation` is only in debug builds (LoopOut uses it for the Compose tooling/preview helpers you don't want in a release).

## The FeatureFlags switch

There's one more piece of "structure" worth knowing — not in the build files, but a tiny Kotlin file that decides whether a whole feature is visible: `FeatureFlags.kt`.

```kotlin
const val CLOUD_SYNC_ENABLED = false
```

> 💡 **Concept — a feature flag.** A feature flag is a single on/off constant the code checks before showing a feature. Here, because no Firebase project is wired up for the v1 release, `CLOUD_SYNC_ENABLED = false` hides the Google sign-in flow and the Account card. The sign-in and sync code stays in the project untouched — flipping this one constant to `true` (once Firebase is set up) brings the whole feature back. It's a clean way to ship a not-yet-ready feature *disabled* rather than deleting it.

This pairs neatly with the conditional `google-services` apply: the build *can* compile without Firebase, and the flag ensures the UI doesn't dangle a sign-in button that wouldn't work.

## Putting it together

When you run a build, Gradle reads `settings.gradle.kts` to find the `:app` module and its repositories, reads the root `build.gradle.kts` for the available plugins, then runs `app/build.gradle.kts`: it applies plugins, optionally wires Firebase and signing, resolves every dependency through the version catalog and BOMs, compiles your Kotlin (running KSP to generate Room code along the way), and — for release — minifies, shrinks, and signs the result. Every library name you'll meet in later chapters traces back to a single line in `libs.versions.toml`.
