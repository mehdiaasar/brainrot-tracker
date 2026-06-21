Almost every app needs to remember small bits of information between launches: "is the user signed in?", "did they pick dark mode?", "is blocking turned on?" LoopOut keeps these scattered settings in two different key-value stores, and this chapter explains what they are, why there are two, and walks through every key the app defines.

## What "key-value persistence" means

Imagine a tiny notebook that survives even when the app is closed and the phone is rebooted. On each line you write a **label** and a **value** next to it — like `theme_mode = "DARK"` or `is_signed_in = true`. Later, you look up a label and read its value back.

That is exactly what a **key-value store** is: a persistent dictionary. The label is the **key** (always a string), and the value is a small primitive — a `Boolean`, `String`, `Int`, `Float`, and so on.

> 💡 **Concept —** Key-value stores are for *small, simple* settings. They are not for large or structured data (lists of reels, history per day). That bigger, relational data lives in the **Room database** (covered in its own chapter). A good rule of thumb: if it is a single toggle, a timestamp, or one short string, a preference store is the right home.

LoopOut uses two such stores:

| Store | Technology | Used for | Access style |
|---|---|---|---|
| `AppPreferences` | Jetpack **DataStore** | Google sign-in state | Asynchronous (coroutines + `Flow`) |
| `Prefs` / `brainrot_prefs` | Classic **SharedPreferences** | Theme, blocking, snooze, sync consent, HUD scale | Synchronous (blocking reads/writes) |

Why two? Because they were built at different times by Android, and each is better suited to its job. Let's meet them one at a time.

## SharedPreferences: the classic, synchronous store

**SharedPreferences** is the original Android key-value store, available since the very first versions of the platform. It stores everything in a single XML file on disk and lets you read and write values *synchronously* — meaning the call returns the answer immediately, on whatever thread you call it from.

In LoopOut, the *file name and all its keys* are centralized in one tiny file, `Prefs.kt`:

```kotlin
object Prefs {
    const val FILE = "brainrot_prefs"

    const val BLOCKING_ENABLED = "blocking_enabled"
    const val BLOCKING_MODE = "blocking_mode"
    const val HUD_SCALE = "hud_scale"
    const val THEME_MODE = "theme_mode"
    const val CLOUD_SYNC_ENABLED = "cloud_sync_enabled"
    const val LAST_SYNCED_DATE = "last_synced_date"
    const val SNOOZE_UNTIL_ALL = "snooze_until_all"
    const val ACCESSIBILITY_DISCLOSURE_ACCEPTED = "accessibility_disclosure_accepted"
}
```

An `object` in Kotlin is a **singleton** — a single shared instance that exists for the whole app. Each `const val` is a compile-time string constant. So `Prefs.THEME_MODE` is just a fancy, typo-proof way of writing `"theme_mode"`.

> 💡 **Concept —** Why bother with constants instead of typing the raw strings? The doc comment at the top of `Prefs.kt` explains it perfectly: *"a typo'd key silently reads the default and loses state."* If a service writes to `"blocking_enabled"` but a screen accidentally reads `"blocking_enbaled"`, there is no compiler error and no crash — the read just returns the default and the setting appears to "not save." Funneling every key through `Prefs` makes that class of bug impossible, because a misspelled `Prefs.BLOCKNG_ENABLED` would fail to compile.

`Prefs` holds *no logic* — it only defines names. The actual reading and writing happens at each call site. Here is a real read, from `LimitsScreen.kt`:

```kotlin
val sharedPrefs = remember { context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE) }
...
var blockingEnabled by remember { mutableStateOf(sharedPrefs.getBoolean(Prefs.BLOCKING_ENABLED, false)) }
```

Line by line:

- `context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)` opens (or creates) the `brainrot_prefs` file. `MODE_PRIVATE` means only this app can read it.
- `sharedPrefs.getBoolean(Prefs.BLOCKING_ENABLED, false)` reads the `"blocking_enabled"` key. The second argument, `false`, is the **default** returned when the key has never been written. This is why a correct spelling matters so much — a wrong key falls back to this default forever.

Writing is just as direct, also from `LimitsScreen.kt`:

```kotlin
sharedPrefs.edit().putBoolean(Prefs.BLOCKING_ENABLED, it).apply()
```

- `.edit()` opens an editor (a staging area for changes).
- `.putBoolean(...)` queues a new value.
- `.apply()` commits it. `apply()` writes to memory immediately and saves to disk on a background thread, so it does not block the UI. (Its sibling `commit()` writes synchronously and returns a success flag — LoopOut consistently uses `apply()`.)

### Walking the `brainrot_prefs` keys

Each key in `Prefs` is read or written somewhere specific in the app. Here is the full map:

| Key constant | String | Type | What it stores | Touched by |
|---|---|---|---|---|
| `BLOCKING_ENABLED` | `blocking_enabled` | Boolean | Whether app-blocking is on at all | `LimitsScreen.kt`, `ReelCounterService.kt` |
| `BLOCKING_MODE` | `blocking_mode` | String | Which blocking style (`HARD`/`SNOOZE`/`REMIND`) | `BlockingMode.kt`, `LimitsScreen.kt` |
| `HUD_SCALE` | `hud_scale` | Float | Size of the floating counter bubble | `LimitsScreen.kt`, `FloatingCounterService.kt` |
| `THEME_MODE` | `theme_mode` | String | `SYSTEM` / `LIGHT` / `DARK` | `ThemeController.kt`, `BrainRotWidget.kt`, `FloatingCounterService.kt` |
| `CLOUD_SYNC_ENABLED` | `cloud_sync_enabled` | Boolean | Consent to upload daily aggregates | `GoogleSignInScreen.kt`, `UsageSyncManager.kt` |
| `LAST_SYNCED_DATE` | `last_synced_date` | String | Last date pushed to Firestore | `UsageSyncManager.kt` |
| `SNOOZE_UNTIL_ALL` | `snooze_until_all` | Long | Epoch-millis until which a snooze is active | `FloatingCounterService.kt` |
| `ACCESSIBILITY_DISCLOSURE_ACCEPTED` | `accessibility_disclosure_accepted` | Boolean | User saw the prominent-disclosure dialog | `OnboardingScreen.kt` |

Notice the spread of callers: a Compose screen, an accessibility service, a foreground service, the theme singleton, the widget, the sync manager. That is exactly *why* `brainrot_prefs` uses SharedPreferences. These callers run on many different threads, and several of them — like the always-running `ReelCounterService` — need to read a setting *right now*, synchronously, without launching a coroutine. From `ReelCounterService.kt`:

```kotlin
val prefs = getSharedPreferences(FloatingCounterService.PREFS, Context.MODE_PRIVATE)
if (!prefs.getBoolean("blocking_enabled", false)) return
```

The service opens the prefs file (`FloatingCounterService.PREFS` is itself just an alias for `Prefs.FILE`, so it is the same `brainrot_prefs` file), checks "is blocking even on?", and bails immediately if not. A synchronous one-liner is perfect here.

> ⚠️ **Gotcha —** That read passes the *raw string* `"blocking_enabled"` instead of `Prefs.BLOCKING_ENABLED`. It happens to be spelled correctly, so it works — but it is exactly the kind of magic string the `Prefs` object exists to eliminate. If you ever rename the key, the compiler will update every `Prefs.BLOCKING_ENABLED` reference for you, yet this hand-typed literal would silently rot. Prefer the constant.

> ⚠️ **Gotcha —** Synchronous reads are convenient, but the *first* call to `getSharedPreferences` has to load and parse the XML file from disk. On older devices, doing that on the main UI thread can cause a tiny stutter. LoopOut sidesteps this by wrapping screen reads in `remember { ... }` (so it happens once) and by reading in services that are off the UI thread anyway.

The `BlockingMode` and `ThemeController` cases show a small but important pattern: when a string preference holds the *name* of an enum value, you must convert it back carefully. `ThemeController.kt` does:

```kotlin
mode = runCatching { Mode.valueOf(p.getString(Prefs.THEME_MODE, Mode.SYSTEM.name)!!) }
    .getOrDefault(Mode.SYSTEM)
```

`Mode.valueOf("DARK")` turns the stored string back into the `Mode.DARK` enum constant. `runCatching { ... }.getOrDefault(Mode.SYSTEM)` guards against a corrupted or unknown value crashing the app — if the conversion throws, it quietly falls back to `Mode.SYSTEM`. This is a manual version of the "type safety" that DataStore gives you more directly — which brings us to the second store.

## DataStore: the modern, asynchronous store

**Jetpack DataStore** is Google's newer replacement for SharedPreferences. It stores the same kind of key-value data, but it exposes everything through Kotlin **coroutines** and **`Flow`** instead of blocking calls. (A `Flow` is a stream of values over time — think of it as a pipe that emits the current value, then emits again whenever the value changes.)

LoopOut uses DataStore for exactly one thing: the **Google sign-in state**. The whole store is defined in `AppPreferences.kt`.

```kotlin
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "brainrot_app_prefs")
```

This single line creates a DataStore named `brainrot_app_prefs` (a *different* file from `brainrot_prefs`). The `by preferencesDataStore(...)` syntax is a **property delegate** — it lazily builds one shared DataStore instance for the whole app the first time `context.dataStore` is accessed. You never construct it yourself.

The keys are declared with typed helpers, not raw strings:

```kotlin
private val IS_SIGNED_IN   = booleanPreferencesKey("is_signed_in")
private val USER_EMAIL     = stringPreferencesKey("user_email")
private val USER_NAME      = stringPreferencesKey("user_name")
private val USER_PHOTO_URL = stringPreferencesKey("user_photo_url")
```

`booleanPreferencesKey("is_signed_in")` says "this key holds a Boolean." That is DataStore's **type safety**: the key itself remembers its type, so you cannot accidentally read a Boolean key as a String. (With SharedPreferences the type lives only in *which getter you call* — `getBoolean` vs `getString` — and nothing stops you from mismatching.)

### Reading as a Flow

Sign-in status is exposed as a stream the rest of the app can observe:

```kotlin
fun isSignedInFlow(context: Context): Flow<Boolean> =
    context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { it[IS_SIGNED_IN] ?: false }
```

- `context.dataStore.data` is a `Flow<Preferences>` — it emits the whole preferences snapshot now, and again any time it changes.
- `.catch { emit(emptyPreferences()) }` is error handling: if reading the file fails (for example, disk corruption), instead of crashing, it emits an empty set of preferences so downstream code sees "nothing stored yet."
- `.map { it[IS_SIGNED_IN] ?: false }` transforms each emitted snapshot into a plain `Boolean`. The `?: false` supplies the default when the key is absent — the DataStore equivalent of the second argument to `getBoolean`.

The richer `userFlow` builds a whole object only when signed in:

```kotlin
fun userFlow(context: Context): Flow<SignedInUser?> =
    context.dataStore.data
        .catch { emit(emptyPreferences()) }
        .map { prefs ->
            if (prefs[IS_SIGNED_IN] == true) {
                SignedInUser(
                    prefs[USER_EMAIL] ?: "",
                    prefs[USER_NAME] ?: "",
                    prefs[USER_PHOTO_URL]?.ifEmpty { null }
                )
            } else null
        }
```

It maps the snapshot to a `SignedInUser?` — a small `data class` holding email, name, and an optional photo URL — or `null` when nobody is signed in. Note `prefs[USER_PHOTO_URL]?.ifEmpty { null }`: because the photo URL was stored as `""` to represent "none," this converts an empty string back to `null` on the way out.

### Writing with a suspend function

Because DataStore writes are asynchronous, the write functions are marked `suspend` — they can only be called from a coroutine, and they will not block the calling thread:

```kotlin
suspend fun setSignedIn(
    context: Context,
    email: String,
    name: String,
    photoUrl: String?
) {
    context.dataStore.edit { prefs ->
        prefs[IS_SIGNED_IN]   = true
        prefs[USER_EMAIL]     = email
        prefs[USER_NAME]      = name
        prefs[USER_PHOTO_URL] = photoUrl ?: ""
    }
}
```

`dataStore.edit { ... }` opens a transaction. Every assignment inside runs atomically — either all four keys are written or none are, so an observer never sees a half-updated state. `photoUrl ?: ""` is where the optional URL becomes an empty string (the inverse of the `.ifEmpty { null }` we saw on read).

Sign-out simply wipes the store:

```kotlin
suspend fun signOut(context: Context) {
    context.dataStore.edit { it.clear() }
}
```

`it.clear()` removes every key, so all the flows above immediately re-emit "signed out."

> 💡 **Concept —** Because the flows are *live*, UI that observes them updates automatically. In `LimitsScreen.kt`, the Account card does `remember { AppPreferences.userFlow(context) }.collectAsState(initial = null)` — the `remember` keeps the same flow across recompositions, and `collectAsState` turns its emissions into Compose state. The moment `setSignedIn` or `signOut` runs anywhere in the app, the card redraws itself. With SharedPreferences you would have to manually re-read after every change. This "set it once, the UI follows" behavior is the headline reason DataStore exists.

`Navigation.kt` shows the other way to consume a flow — grabbing just the first value at startup to decide where to route:

```kotlin
val signedIn = AppPreferences.isSignedInFlow(context).first()
```

`.first()` is also a `suspend` call: it waits for the very first emission, then stops listening. Perfect for a one-time "where should I navigate?" decision.

## So why two stores?

Both stores do the same fundamental job, so the split is pragmatic, not architectural purity:

| Question | SharedPreferences (`brainrot_prefs`) | DataStore (`AppPreferences`) |
|---|---|---|
| Read style | Synchronous — instant value | Asynchronous — `Flow` / `suspend` |
| Type safety | Only via which getter you call | Key remembers its type |
| Live updates | No — you re-read manually | Yes — flows re-emit on change |
| Best for | Settings read from services, the widget, and synchronously on screens | App-wide state the UI should react to live |
| Where in LoopOut | Theme, blocking, snooze, sync consent, HUD scale, disclosure flag | Google sign-in identity |

The blocking-related settings live in SharedPreferences because the accessibility and foreground services need to read them *synchronously, on their own threads,* very frequently — converting those hot paths to suspending flows would add needless friction. The sign-in identity lives in DataStore because it is genuinely app-wide reactive state: when you sign in or out, several screens should update instantly, and a `Flow` delivers that for free.

> ⚠️ **Gotcha —** These are two *separate files on disk* (`brainrot_prefs.xml` vs the `brainrot_app_prefs` DataStore). Clearing one does not touch the other. `signOut()` wiping DataStore leaves your theme and blocking settings intact — which is the desired behavior, but worth keeping in mind if you ever go hunting for "where did that value go?"

## Recap

- A **key-value store** is a persistent dictionary for small settings; structured data belongs in Room instead.
- **`Prefs.kt`** centralizes the SharedPreferences file name and every key as a `const val`, so a typo can never silently lose state — though a couple of call sites (notably `ReelCounterService`) still hand-type the raw string and would benefit from the constant.
- **SharedPreferences** (`brainrot_prefs`) is synchronous and ideal for the many services and the widget that read settings on demand.
- **DataStore** (`AppPreferences`, file `brainrot_app_prefs`) is coroutine- and `Flow`-based, type-safe, and reactive — LoopOut uses it for the live Google sign-in state, reading via `isSignedInFlow`/`userFlow` and writing via the `suspend` functions `setSignedIn`/`signOut`.
