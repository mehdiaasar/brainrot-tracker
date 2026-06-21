Every Android app needs a starting point — a single piece of code the operating system runs when the user taps the app icon. In LoopOut that starting point is `MainActivity`, and almost everything the user ever sees is reached from there. This chapter follows the path from "app launched" to "a screen is on the glass," and explains how the user moves between screens afterward.

## The entry point: `MainActivity`

In Android, an **Activity** is a single screen-sized container that the OS knows how to launch. Think of it as the front door to your app: the system hands you an Activity, and you fill it with content. LoopOut has exactly one Activity for its whole user interface — this is called a **single-Activity architecture**. Instead of a separate Activity per screen (the old way), LoopOut keeps one Activity and swaps the *content inside it*. We'll see how that swap works once we get to navigation.

`MainActivity` is small. Its entire job lives in one method:

```kotlin
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
```

`ComponentActivity` is the modern base class that knows how to host Jetpack Compose UI. `onCreate` is a **lifecycle callback** — a method the Android system calls automatically at a specific moment, here "the Activity is being created." You never call `onCreate` yourself; the OS does. The `savedInstanceState` argument carries any state Android saved if it previously destroyed the Activity (for example, to reclaim memory). Calling `super.onCreate(...)` first lets the parent class do its own setup — always do this.

> 💡 **Concept — Compose vs. Views.** Older Android apps built screens out of XML layout files and `View` objects. LoopOut uses **Jetpack Compose**, a newer toolkit where the UI is described in Kotlin functions. You declare *what* the screen should look like for a given state, and Compose figures out *how* to draw and update it.

### Step 1: warm up the data and background work

Before any UI appears, `onCreate` does a few setup chores:

```kotlin
// Initialize database singleton
val db = AppDatabase.getInstance(applicationContext)

// Catch up streak evaluation in case the tracking service wasn't running at midnight,
// then push any unsynced days to the cloud (no-op unless signed in with backup on)
lifecycleScope.launch(Dispatchers.IO) {
    val repository = UsageRepository(db)
    repository.evaluateStreaksUpTo(LocalDate.now().minusDays(1))
    try {
        UsageSyncManager(applicationContext, repository).syncIfEnabled()
    } catch (_: Exception) {
    }
}
```

`AppDatabase.getInstance(...)` grabs the app's one shared database (a **singleton** — a single instance reused everywhere). `applicationContext` is a long-lived handle to the app itself, safer to hold onto than the Activity.

The block inside `lifecycleScope.launch(Dispatchers.IO) { ... }` runs on a **coroutine** — Kotlin's lightweight way to do work without blocking the screen. `Dispatchers.IO` says "run this on a background thread meant for disk and network." Why background? Because `evaluateStreaksUpTo(...)` reads and writes the database, and database work must never run on the **main thread** (the thread that draws the UI) or the app would freeze.

`evaluateStreaksUpTo(LocalDate.now().minusDays(1))` catches up the user's streak records up to *yesterday*. As the project notes explain, streaks are normally written when the tracking service detects a day rollover at midnight — but if the service wasn't running then, this line fills in the gap on the next launch. `syncIfEnabled()` then pushes any unsynced days to the cloud; it does nothing unless the user is signed in with backup turned on, and the `try/catch` swallows any failure so a sync hiccup can never crash startup.

> ⚠️ **Gotcha — empty `catch` blocks are deliberate here, not lazy.** `catch (_: Exception) {}` looks like swallowing errors blindly. In this one spot it's intentional: cloud sync is optional, and a network error must never block the user from opening the app. Don't copy this pattern for logic that actually matters.

### Step 2: theme and notification setup

```kotlin
// Load saved theme preference
ThemeController.init(applicationContext)

// Create notification channels
NotificationHelper(applicationContext).createChannels()
```

`ThemeController.init(...)` loads the user's saved Light/Dark/System choice from preferences so the UI can pick the right colors immediately. `NotificationHelper(...).createChannels()` registers the app's notification **channels** — on modern Android, every notification must belong to a channel (a category the user can mute or tune in system settings), and channels must be created before any notification is posted.

### Step 3: draw the UI

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

`enableEdgeToEdge()` lets the app draw behind the system bars (the status bar at the top and the navigation bar at the bottom) for a modern full-bleed look. `setContent { ... }` is the bridge from the old Activity world into Compose: everything inside this block *is* the app's UI.

Reading inward: `BrainRotTrackerTheme` wraps the app in its color palette, typography, and shapes. `Surface` paints a background filling the whole screen (`Modifier.fillMaxSize()`) using the theme's background color. And finally `MainNavigation()` — that one call is the entire rest of the app's screens. Everything the user navigates lives below this single function.

> 💡 **Concept — order matters.** Notice that the database, streak catch-up, theme, and notification channels are all kicked off *before* `setContent`. By the time the first screen draws, the things it depends on already exist. The streak/sync work is the one exception — it runs on a background coroutine, so the UI doesn't wait for it to finish.

## What is a back stack?

Before reading `MainNavigation`, you need one idea: the **back stack**.

Imagine a stack of index cards. Each card is a screen. When you open a new screen, you drop a fresh card on *top* of the pile. When you press the system Back button, you remove the top card and the one beneath it becomes visible again. The screen you currently see is always whatever card is on top.

That pile is the back stack. "Push" means add a screen on top; "pop" means remove the top screen. LoopOut keeps this pile in a single list and lets the UI react to whatever sits on top.

> 💡 **Concept — old string routes vs. type-safe keys.** In older navigation libraries you named each screen with a string like `"dashboard"` and pushed routes by typing that string. A typo (`"dashbord"`) compiled fine and crashed at runtime. LoopOut instead uses **typed keys**: each screen is a real Kotlin object, so a wrong name simply won't compile. Safer, and your editor can autocomplete them.

## Navigation 3 and the `NavKey` data objects

LoopOut uses **Navigation 3** (`androidx.navigation3`, currently an alpha/preview library). Its screen keys are defined in `NavigationKeys.kt`, and the whole file is tiny:

```kotlin
@Serializable data object GoogleSignIn : NavKey
@Serializable data object Onboarding : NavKey
@Serializable data object Dashboard : NavKey
@Serializable data object Stats : NavKey
@Serializable data object Limits : NavKey
@Serializable data object Streaks : NavKey
```

Each line declares one destination. Let's unpack the keywords:

- `data object` is a Kotlin singleton — exactly one instance of `Dashboard` exists in the whole app, and you refer to it just by writing `Dashboard`. (`data` gives it a tidy `toString()` for debugging.) Because these screens carry no arguments, a single shared object per screen is all we need.
- `: NavKey` marks the object as something Navigation 3 recognizes as a destination — a "card" that can go on the back stack.
- `@Serializable` (from kotlinx.serialization) lets the object be **serialized**: converted to data that can be saved and later restored. Navigation 3 uses this so the back stack survives events like the screen rotating or Android killing the app to save memory — when the user returns, the same pile of screens is rebuilt.

| Key | Screen the user sees |
|-----|----------------------|
| `Onboarding` | First-run setup and permission disclosure |
| `GoogleSignIn` | Optional cloud-backup sign-in (gated off in v1) |
| `Dashboard` | Home screen with today's stats |
| `Stats` | Charts and history |
| `Streaks` | Streak / progress view |
| `Limits` | Per-platform limits, shown to the user as "Settings" |

> ⚠️ **Gotcha — R8 can break serialization.** Release builds run **R8**, which shrinks and renames code to make the app smaller. If R8 renamed or deleted the auto-generated serializers for these `NavKey` objects, restoring the back stack would crash. `app/proguard-rules.pro` prevents that with explicit *keep rules*:
>
> ```
> -keepclasseswithmembers class com.example.brainrottracker.** {
>     kotlinx.serialization.KSerializer serializer(...);
> }
> -keep,includedescriptorclasses class com.example.brainrottracker.**$$serializer { *; }
> ```
>
> These tell R8 "do not touch the serializers." This is exactly the kind of breakage the project notes flag as the most likely R8 problem — always smoke-test a minified release build of navigation.

## `MainNavigation()`: the app's traffic controller

This one composable function (in `Navigation.kt`) wires the back stack to the screens and the bottom navigation bar.

### Deciding where to start

```kotlin
val initState by produceState<InitState?>(initialValue = null) {
    val accessibilityEnabled = try {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: ""
        enabledServices.contains(context.packageName)
    } catch (_: Exception) { false }
    val signedIn = AppPreferences.isSignedInFlow(context).first()
    value = InitState(accessibilityEnabled, signedIn)
}
```

`produceState` runs a background block and feeds its result into a Compose state value. It starts as `null`, then publishes an `InitState` once two facts are known: whether the app's accessibility service (the reel detector) is already turned on in system settings, and whether the user is signed in. The sign-in check (`isSignedInFlow(...).first()`) reads the **first** value emitted by a DataStore-backed flow — `.first()` grabs that one value and stops collecting, since we only need the answer once at startup. While `initState` is still `null`, the function paints a plain colored box and returns early — a tiny custom splash so the user never sees a flicker:

```kotlin
if (initState == null) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(navBg)
    )
    return
}
```

```kotlin
val startDestination: NavKey = if (initState!!.accessibilityEnabled) Dashboard else Onboarding

val backStack = rememberNavBackStack(startDestination)
var currentDestination by remember { mutableStateOf(startDestination) }
```

If the accessibility service is already enabled, the app skips setup and starts on `Dashboard`; otherwise it starts on `Onboarding`. `rememberNavBackStack(startDestination)` creates the back stack with that first card on it. `currentDestination` separately tracks which *bottom-nav tab* is highlighted (the back stack can hold pushed sub-screens like `Limits` that aren't a tab, so the highlighted tab is tracked on its own).

> 💡 **Concept — `initState!!`.** The double `!!` is Kotlin's "I promise this isn't null" operator. It's safe here only because the early `return` above already bailed out while `initState` was `null` — so any code past that point knows the value exists.

### Pushing, popping, and switching tabs

The function defines three small helpers that manipulate the pile. Each is just list operations on `backStack` — `+=` pushes a card, `removeLastOrNull()` pops the top one:

```kotlin
val selectTab: (NavKey) -> Unit = { key ->
    if (currentDestination != key) {
        currentDestination = key
        while (backStack.size > 1) backStack.removeLastOrNull()
        backStack.removeLastOrNull()
        backStack += key
    }
}
val openSettings: () -> Unit = { if (backStack.lastOrNull() != Limits) backStack += Limits }
val backFromSettings: () -> Unit = {
    currentDestination = Streaks
    while (backStack.size > 1) backStack.removeLastOrNull()
    backStack.removeLastOrNull()
    backStack += Streaks
}
```

`selectTab` switches top-level tabs by emptying the pile (the `while` loop pops down to one card, then one more pop clears it) and pushing the chosen tab, so tabs never stack up endlessly. `openSettings` pushes `Limits` on top (unless it's already there) — that's how the dashboard opens the screen the user sees as "Settings." `backFromSettings` is the reverse: it collapses whatever is on the pile and lands the user on the Streaks tab, which is where the Settings screen's own back button returns to. `backStack.lastOrNull()` answers "what's the top card?" — the screen currently shown.

### Showing the right screen: `NavDisplay` and the entry provider

```kotlin
NavDisplay(
    backStack = backStack,
    onBack = { backStack.removeLastOrNull() },
    entryProvider = entryProvider {
        entry<Onboarding> { OnboardingScreen( /* ... */ ) }
        entry<Dashboard> { DashboardScreen( /* ... */ ) }
        // ...
    }
)
```

`NavDisplay` is the Compose component that *renders the top of the back stack*. You hand it the `backStack` and an `entryProvider` — a lookup table that says, for each `NavKey`, which composable to show. `onBack = { backStack.removeLastOrNull() }` wires the system Back button to a simple pop. When the top card changes, `NavDisplay` automatically animates to the matching screen. The `Limits` entry even attaches a custom `transitionSpec` (and a matching `popTransitionSpec`) so Settings slides in from the right and slides back off to the right on return, while ordinary tab switches keep their default cross-fade.

Notice the screens receive *callbacks*, not navigation logic: `DashboardScreen(onOpenSettings = openSettings, onViewStreaks = { selectTab(Streaks) }, onEditPlan = openSettings)`. A screen says "the user wants settings" by calling `onOpenSettings()`; it doesn't know or care how the back stack works. (Some screens take no callbacks at all — `StatsScreen()` is rendered with none.) This keeps screens reusable and the navigation rules in one place.

> 💡 **Concept — sign-in is gated off in v1.** The `Onboarding` entry's `onComplete` can push `GoogleSignIn`, but only `if (CLOUD_SYNC_ENABLED && initState?.signedIn == false)`. `CLOUD_SYNC_ENABLED` is a compile-time `const val` in `FeatureFlags.kt`, set to `false` until Firebase is configured, so today onboarding finishes straight to the Dashboard and the sign-in screen never appears.

### Hiding the bottom bar on full-screen flows

```kotlin
val topKey = backStack.lastOrNull()
val showBottomBar = topKey != Onboarding && topKey != GoogleSignIn
```

The bottom navigation bar (Dashboard / Stats / Streaks) makes sense once the user is inside the app, but not during onboarding or sign-in, which should feel like full-screen flows. This single line checks the top card: if it's `Onboarding` or `GoogleSignIn`, `showBottomBar` is false. That boolean drives an `AnimatedVisibility` block that slides the bar in or out:

```kotlin
AnimatedVisibility(
    visible = showBottomBar,
    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
) { /* the row of nav items */ }
```

Each nav item is a `Column` with an icon and a label; its color animates to the accent color when its `key` matches `currentDestination`, and tapping it calls `selectTab(item.key)`. The whole thing sits in a `Scaffold`, a Material layout that reserves space for the bottom bar and hands the content area an `innerPadding` so screens don't draw underneath it.

## Putting it together

The full launch story is now one straight line: Android calls `MainActivity.onCreate` → it sets up the database, kicks off streak catch-up and sync on a background coroutine, loads the theme, and creates notification channels → `setContent` hands control to Compose → `MainNavigation()` decides the start screen, builds the back stack, and lets `NavDisplay` render whatever sits on top. From there, every screen change is just a push or pop on that single list of typed `NavKey` cards — and the bottom bar quietly appears or hides based on which card is showing.
