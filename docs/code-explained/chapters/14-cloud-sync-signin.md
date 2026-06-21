LoopOut works perfectly without an account, without the internet, and without anything in the cloud. Everything you've seen so far — counting reels, drawing the overlay, tracking streaks — runs entirely on the phone. This chapter is about the *optional* extra: signing in with Google so the app can back up your daily totals to the cloud. The most important idea here is that this feature is bolted on so carefully that the app stays whole even when none of it is configured.

## Three ideas to define first

Before we read any code, let's pin down three pieces of jargon, because the whole chapter leans on them.

> 💡 **Concept — Authentication ("auth").** Authentication is just *proving who you are* to a system. When you "sign in with Google," Google vouches for you and hands the app a sort of signed permission slip (a *token*) that says "this really is the person who owns this Google account." The app never sees your password — Google handles that. Auth answers the question "who is this user?" so the app can keep one person's data separate from everyone else's.

> 💡 **Concept — A NoSQL document store.** A traditional database is a grid of tables, rows, and columns (LoopOut's on-device Room database is exactly that). A *document store* like Firebase **Firestore** is different: instead of rigid tables, you store **documents** — bundles of named fields, a bit like a JSON object or a labelled folder of values. Documents live inside **collections** (folders). Firestore is called "NoSQL" because it doesn't use the SQL table-and-row model. LoopOut writes one document per day, where each document holds that day's reel counts, limits, and score.

> 💡 **Concept — Consent-gating.** "Gating" something means putting a locked gate in front of it that only opens under certain conditions. *Consent-gating* means the gate only opens after the user has explicitly said "yes, I agree." LoopOut never uploads anything until the user has both signed in **and** ticked a separate "back up my stats" agreement. Two locks, not one.

## The big design decision: stay alive with nothing configured

Here is the headline. LoopOut ships its cloud code, but a fresh checkout of the project has **no Firebase project attached**. The way Firebase normally connects an app to its cloud backend is a file called `google-services.json` that you download from the Firebase console and drop into the `app/` folder. LoopOut's build is written so that if that file is missing, the whole cloud layer quietly switches off and the app runs in a *fully anonymous, local-only* mode.

This starts in the build script, `build.gradle.kts`:

```kotlin
// google-services hard-fails without google-services.json, so only apply it once the
// Firebase project is set up (download the json from the Firebase console into app/).
if (file("google-services.json").exists()) {
    apply(plugin = libs.plugins.google.services.get().pluginId)
}
```

A *plugin* is an extra tool that hooks into the build process. The `google-services` plugin reads `google-services.json` and wires Firebase into the app. The problem: that plugin **crashes the build** if the JSON file isn't there. So the script wraps it in an `if (file(...).exists())` check — apply the plugin only when the config file is present. No file, no plugin, no crash. The app still compiles and installs.

The Firebase *libraries* are still listed as dependencies, so the code compiles either way:

```kotlin
// Firebase (Auth + Firestore for optional cloud backup of daily aggregates)
implementation(platform(libs.firebase.bom))
implementation(libs.firebase.auth)
implementation(libs.firebase.firestore)
```

The libraries are *present*; they're just never *initialised* without the JSON. That distinction is what the runtime guard checks for next.

> 💡 **Concept — The runtime guard.** `FirebaseApp.getApps(context)` returns the list of initialised Firebase apps. With no `google-services.json`, that list is **empty**. So everywhere LoopOut touches Firebase, it first asks "is Firebase even set up?" and bails out early if not. This is the single pattern that keeps the anonymous build safe.

## Signing in: Credential Manager → Firebase Auth

The sign-in flow lives in `GoogleSignInViewModel.kt`. The screen (`GoogleSignInScreen.kt`) shows a "Continue with Google" button; tapping it calls `viewModel.signIn(context, webClientId)`. Let's walk the ViewModel.

First, it models the sign-in process as a small set of named states:

```kotlin
sealed class SignInState {
    object Idle : SignInState()
    object Loading : SignInState()
    data class Success(val name: String, val email: String) : SignInState()
    data class Error(val message: String) : SignInState()
    object Cancelled : SignInState()
}
```

A `sealed class` is a fixed menu of possibilities — sign-in can only ever be `Idle`, `Loading`, `Success`, `Error`, or `Cancelled`, nothing else. The screen reacts to whichever one is current. `Success` and `Error` carry extra data (your name/email, or a message); the others are bare objects because they need no payload.

Now the actual sign-in. It uses **Credential Manager**, Android's modern, unified way to ask for credentials:

```kotlin
val credentialManager = CredentialManager.create(activityContext)

val googleIdOption = GetGoogleIdOption.Builder()
    .setFilterByAuthorizedAccounts(false)
    .setServerClientId(webClientId)
    .setAutoSelectEnabled(false)
    .build()

val request = GetCredentialRequest.Builder()
    .addCredentialOption(googleIdOption)
    .build()

val result = credentialManager.getCredential(
    context = activityContext,
    request = request
)
```

Line by line:
- `CredentialManager.create(...)` gets the system credential service.
- `GetGoogleIdOption` describes *what kind* of credential we want — a Google ID. `setFilterByAuthorizedAccounts(false)` means "show all Google accounts on the phone, not just ones that have used this app before." `setServerClientId(webClientId)` ties the request to *our* project's Google identity (the `webClientId` comes from a string resource). `setAutoSelectEnabled(false)` forces the account picker to actually appear instead of silently auto-choosing.
- `getCredential(...)` is the call that pops up Google's account picker. It's a `suspend` function — it pauses the coroutine while the user picks an account, without freezing the screen.

> 💡 **Concept — `suspend` and `await()`.** A `suspend` function can pause and resume without blocking the app. The sign-in code lives inside `viewModelScope.launch { ... }`, a coroutine, so it can wait for slow things (the user, the network) without locking the UI. You'll see `.await()` later for the same reason.

When the user picks an account, we extract the Google ID token and exchange it for a Firebase user:

```kotlin
val googleCred = GoogleIdTokenCredential.createFrom(credential.data)
// Exchange the Google ID token for a Firebase user so Firestore rules
// can scope data to the account. Skipped when Firebase isn't configured
// (no google-services.json) — sign-in then remains local-only.
if (FirebaseApp.getApps(activityContext).isNotEmpty()) {
    val firebaseCred = GoogleAuthProvider.getCredential(googleCred.idToken, null)
    FirebaseAuth.getInstance().signInWithCredential(firebaseCred).await()
}
```

This is the heart of the graceful-degradation design. The `idToken` is Google's "permission slip." `GoogleAuthProvider.getCredential(...)` repackages it into a Firebase credential, and `signInWithCredential(...).await()` hands it to Firebase, which creates (or finds) a Firebase user with a stable id (`uid`). That `uid` is later used to keep your cloud data separate from everyone else's.

But look at the `if`: the entire Firebase exchange runs **only** when `FirebaseApp.getApps(...)` is non-empty. With no `google-services.json`, this whole block is skipped — the user still picked a Google account, the app still records their name and email, but nothing touches the cloud. Sign-in becomes a purely cosmetic "this is who you are" with no backend.

Either way, the app remembers the user locally:

```kotlin
AppPreferences.setSignedIn(
    context = activityContext,
    email = googleCred.id,
    name = googleCred.displayName ?: "",
    photoUrl = googleCred.profilePictureUri?.toString()
)
_state.value = SignInState.Success(...)
```

`AppPreferences` (in `AppPreferences.kt`) is a **DataStore** — a small, asynchronous key-value store for app settings. `setSignedIn(...)` writes the flag `is_signed_in = true` plus the name, email, and photo URL. Other screens observe `isSignedInFlow(...)` to show an account card. Note the empty-string fallbacks (`?: ""`) — DataStore here stores plain strings, so "no value" is represented as `""` rather than null.

Finally, the error handling distinguishes a *cancellation* from a *real failure*:

```kotlin
} catch (e: GetCredentialCancellationException) {
    _state.value = SignInState.Cancelled
} catch (e: GetCredentialException) {
    _state.value = SignInState.Error(e.message ?: "Sign-in failed. Try again.")
}
```

If the user just backs out of the account picker, that's `Cancelled` (no scary error shown). A genuine problem becomes `Error` with a message. The screen treats these very differently.

| `SignInState` | What happened | What the screen does |
| --- | --- | --- |
| `Idle` | Nothing yet | Shows the "Continue with Google" button |
| `Loading` | Picker / Firebase in flight | Shows a spinner |
| `Success` | Signed in | Triggers the consent dialog |
| `Cancelled` | User backed out | Silently resets to `Idle` |
| `Error` | Something failed | Shows a red error box |

## The second lock: the consent dialog

Signing in proves *who* you are. It does **not** grant permission to upload your data. That's a separate, deliberate step, and it lives in `GoogleSignInScreen.kt`. The screen watches the sign-in state and, on success, raises a consent dialog:

```kotlin
LaunchedEffect(state) {
    when (state) {
        // Cloud upload of usage data needs explicit consent (Play accessibility policy)
        is SignInState.Success -> showSyncConsent = true
        is SignInState.Cancelled -> viewModel.resetError()
        else -> Unit
    }
}
```

The dialog spells out exactly what gets uploaded and lets the user choose:

```kotlin
confirmButton = {
    TextButton(onClick = {
        prefs.edit().putBoolean(Prefs.CLOUD_SYNC_ENABLED, true).apply()
        showSyncConsent = false
        onSignInSuccess()
    }) { Text("Enable backup") }
},
dismissButton = {
    TextButton(onClick = {
        prefs.edit().putBoolean(Prefs.CLOUD_SYNC_ENABLED, false).apply()
        showSyncConsent = false
        onSignInSuccess()
    }) { Text("Not now") }
}
```

Both buttons advance the user into the app (`onSignInSuccess()`) — the only difference is the boolean they write to the `cloud_sync_enabled` preference. "Enable backup" sets it `true`; "Not now" sets it `false`. That single flag is the gate the uploader checks. This is why being signed in is *not enough* to start syncing: you must also have flipped this switch on.

> ⚠️ **Gotcha — Why two locks instead of one?** Google Play's accessibility-service policy is strict because the reel counts ultimately come from the Accessibility API. The app must not silently ship that data anywhere. Separating "I'm signed in" from "I agree to back up my stats" makes consent explicit and revocable, which keeps the app policy-compliant. The dialog text promises "Only daily totals are uploaded — never screen content."

## The uploader: one document per finished day

With both locks open, `UsageSyncManager.kt` does the actual uploading. Its single public method, `syncIfEnabled()`, opens with three guards in a row:

```kotlin
suspend fun syncIfEnabled() {
    if (FirebaseApp.getApps(context).isEmpty()) return
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (!prefs.getBoolean(KEY_ENABLED, false)) return
    val user = FirebaseAuth.getInstance().currentUser ?: return
```

Three early exits, in order: (1) Firebase isn't configured → stop. (2) The user never enabled `cloud_sync_enabled` → stop. (3) Nobody is signed in to Firebase → stop. Only if all three pass does anything upload. This is the consent gate and the runtime guard, working together.

Next it figures out *which* days still need uploading:

```kotlin
val yesterday = LocalDate.now().minusDays(1)
val lastSynced = prefs.getString(KEY_LAST_SYNCED, null)?.let(LocalDate::parse)
var date = lastSynced?.plusDays(1) ?: yesterday
if (date.isAfter(yesterday)) return
```

It only ever uploads **finished** days — never today, because today's counts are still changing. It remembers the `last_synced_date` and resumes from the day after, looping forward to yesterday. If everything's already synced, `date` is after yesterday and it returns immediately.

> 💡 **Concept — Idempotency via `last_synced_date`.** By tracking the last successfully synced day, the manager can be called over and over (on app launch, on day-rollover) and only ever does new work. Re-running it after a full sync does nothing — safe to call freely.

For each pending day it builds a single document and writes it:

```kotlin
val doc = buildMap<String, Any> {
    put("date", dateStr)
    Platform.entries.forEach { p ->
        put("reels_${p.name.lowercase()}", log?.getReelsForPlatform(p) ?: 0)
        put("limit_${p.name.lowercase()}", limits[p.name]?.dailyReelLimit ?: 30)
        if (date == yesterday) {
            put("minutes_${p.name.lowercase()}", yesterdayMinutes[p] ?: 0)
        }
    }
    put("brainHealthScore", log?.brainHealthScore ?: 100)
    streak?.let {
        put("underLimit", it.underLimit)
        put("streakDay", it.streakDay)
    }
}
firestore.collection("users").document(user.uid)
    .collection("dailyLogs").document(dateStr)
    .set(doc)
prefs.edit().putString(KEY_LAST_SYNCED, dateStr).apply()
```

The document is a flat bag of fields: per-platform reel counts and limits, plus the brain-health score and streak info. Screen-time `minutes_*` are only included for *yesterday*, because `UsageStats` only retains recent activity — older minutes aren't reliable. The path `users/{uid}/dailyLogs/{date}` is the NoSQL structure in action: a `users` collection, your `uid` document, a `dailyLogs` sub-collection, one document per date. Because the path is keyed by the **same date string**, re-uploading a day overwrites rather than duplicates.

> 💡 **Concept — Fire-and-forget writes.** Notice `.set(doc)` isn't awaited. Firestore caches writes locally and replays them to the server when the device is back online, so LoopOut needs no retry logic of its own. The class comment calls this out directly.

`syncIfEnabled()` is invoked from `MainActivity.onCreate` and from `ReelCounterService` at day-rollover, so finished days get backed up both when the app opens and as midnight passes. And because every one of those calls hits the three guards first, an anonymous, unconfigured build runs all of this code and uploads exactly nothing.
