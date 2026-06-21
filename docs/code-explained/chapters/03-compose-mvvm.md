Almost every screen you tap through in LoopOut is built the same way: a **ViewModel** prepares the data, and a **Composable** function paints it on screen. This chapter teaches that pattern from scratch, using the real Dashboard code. If you have never written an Android app before, start here — once these ideas click, the rest of the UI code reads like plain English.

## The old way vs. the new way

For most of Android's history, you described a screen in an XML layout file and then wrote Java/Kotlin code that *reached into* that layout to change it: "find the text view with id `counter`, set its text to 42." Every time the data changed, you had to remember to go re-poke the right widget. Miss one, and the screen showed stale data.

Jetpack Compose flips this around. **Compose is a declarative UI toolkit**: instead of issuing step-by-step commands to mutate widgets, you write a function that *describes* what the screen should look like for a given set of data. When the data changes, Compose re-runs your function and works out what actually changed on screen for you.

> 💡 **Concept — Declarative UI.** Think of a thermostat display. You don't tell it "erase the old number, draw a new one." You just say "show whatever the current temperature is," and when the temperature changes the display updates itself. Compose works the same way: you describe the screen as a function of the data, and re-describing is the only update mechanism.

## @Composable functions: the building blocks

The basic unit of Compose UI is a function annotated with `@Composable`. Here is the top of LoopOut's main screen, in `DashboardScreen.kt`:

```kotlin
@Composable
fun DashboardScreen(
    modifier: Modifier = Modifier,
    onOpenSettings: () -> Unit = {},
    onViewStreaks: () -> Unit = {},
    onEditPlan: () -> Unit = {},
    viewModel: DashboardViewModel = viewModel()
) {
```

Line by line:

- `@Composable` marks this as a function that emits UI. You can only call composable functions from inside other composable functions — they form a tree, just like nested HTML tags.
- `DashboardScreen` returns nothing (`Unit`); instead of returning a value, it *describes* UI by calling other composables.
- `modifier: Modifier = Modifier` — a **Modifier** is Compose's universal "decorator" for size, padding, background, click handling, and more. Passing one in lets the caller position this screen.
- The three `() -> Unit` parameters are **callbacks** — functions the screen calls when something happens (e.g. the user taps "Edit Plan"). More on these under "events up."
- `viewModel: DashboardViewModel = viewModel()` hands the screen its ViewModel; the `viewModel()` default fetches (or creates) the right one automatically.

Composables call other composables to build the tree. Inside `DashboardScreen` you'll find `Column { ... }`, `Row { ... }`, `Box { ... }`, and `Text(...)` — Compose's layout and content primitives:

```kotlin
Text(
    "LoopOut",
    fontWeight = FontWeight.Medium,
    color = textPrimary,
    fontSize = 16.sp,
    letterSpacing = (-0.3).sp
)
```

`Text` is a composable that draws a string. `Column` stacks its children vertically, `Row` arranges them horizontally, `Box` overlaps them. There is no XML anywhere — the layout *is* the Kotlin code.

> 💡 **Concept — `dp` and `sp`.** `16.dp` means 16 *density-independent pixels* (sizes that look the same on every screen), and `16.sp` means *scale-independent pixels* for text (which also respect the user's font-size setting). The `.dp`/`.sp` suffixes are Compose extensions on numbers.

## Recomposition and state

When the data behind a composable changes, Compose **recomposes** — it re-runs the affected functions to produce an updated description, then efficiently patches the screen. You never call "redraw" yourself.

But for recomposition to trigger, Compose has to *observe* the data; a plain Kotlin variable won't do. That is what **state** is for: data that, when it changes, causes recomposition. The Dashboard keeps a little local state to remember whether the tracking service is running:

```kotlin
var isTracking by remember { mutableStateOf(ReelCounterService.isRunning) }
```

Unpacking it:

- `mutableStateOf(...)` creates an observable container holding a value. Reading it inside a composable subscribes that composable to changes; writing it triggers recomposition.
- `remember { ... }` tells Compose to *keep this value across recompositions*. Without `remember`, the value would be re-created from scratch every time the function re-runs.
- `by` is Kotlin **property delegation** — it lets you read and write `isTracking` like a normal variable while it goes through the state object behind the scenes. Without `by` you'd write `isTracking.value` everywhere.

> ⚠️ **Gotcha — `remember` survives recomposition, not the whole lifecycle.** `remember` keeps a value while the composable stays on screen, but it's lost if the composable leaves the tree or the screen is recreated (e.g. a rotation). For data that must outlive the screen — your real app data — you use a ViewModel, the subject of the next section.

## MVVM: separating "what to show" from "how to show it"

LoopOut uses the **MVVM** pattern — Model, View, ViewModel — which splits a screen into three responsibilities:

| Layer | In LoopOut | Job |
|-------|-----------|-----|
| **Model** | Room database, `UsageRepository`, `ScreenTimeHelper` | Stores and computes the raw data |
| **ViewModel** | `DashboardViewModel` | Reads the model, shapes it into screen-ready state, holds it across config changes |
| **View** | `DashboardScreen` (the composables) | Renders the state; forwards user actions back up |

The big win is **separation of concerns**: the View never talks to the database directly and contains no business logic, so it stays simple and testable. The ViewModel knows nothing about colors or fonts, so the same logic could drive a different UI. Each piece changes independently.

### AndroidViewModel

`DashboardViewModel` extends `AndroidViewModel`:

```kotlin
class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: UsageRepository
    private val app = application

    init {
        val db = AppDatabase.getInstance(application)
        repository = UsageRepository(db)
    }
```

A plain `ViewModel` survives configuration changes (like rotation) so your data isn't thrown away and re-fetched. `AndroidViewModel` is the same thing but it's handed the `Application` object, which LoopOut needs to open the database and query screen-time stats. The `init` block runs once when the ViewModel is created, wiring up the repository (the Model); the class also stashes the `Application` as `app` for the screen-time poll below. LoopOut uses **no dependency-injection framework** — the ViewModel just constructs its own repository.

## Coroutines, Flow, and StateFlow

Reading from a database takes time, and the data keeps *changing* (the reel counter ticks up while you scroll). Android handles this with **coroutines** and **Flow**.

- A **coroutine** is a lightweight background task. It can pause and resume without blocking the screen, so slow work never freezes the UI.
- A **Flow** is a stream of values over time — like a pipe the database pushes new results into whenever the data changes.

`repository.getTodayLog()` returns a `Flow<DailyLog?>`: every time today's reel count changes in the database, a fresh `DailyLog` flows out. But a raw Flow is "cold" — it does nothing until someone collects it, and it has no current value. The View wants a value it can read *right now*. The fix is **StateFlow**:

```kotlin
val todayLog: StateFlow<DailyLog?> = repository.getTodayLog()
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
```

- `StateFlow` is a Flow that always has a *current* value, perfect for UI.
- `stateIn(...)` converts the cold database Flow into a hot StateFlow.
- `viewModelScope` is the coroutine scope tied to this ViewModel's life — when the ViewModel is cleared, the collection automatically stops. No leaks.
- `SharingStarted.WhileSubscribed(5000)` means: keep the underlying flow alive while the screen is watching, plus **5000 ms (5 seconds) after** the last watcher leaves. That grace window avoids tearing down and rebuilding the database query during a quick rotation.
- `null` is the **initial value** — what `todayLog` reports before the first real value arrives.

> 💡 **Concept — Why an initial value?** A StateFlow must always have *something* to return. Before the database answers, `todayLog` is `null`, `limits` is `emptyList()`, and `brainHealth` is `100`. The UI shows sensible defaults instantly instead of a blank screen.

The ViewModel also *derives* new state from existing flows. `brainHealth` combines two flows into one:

```kotlin
val brainHealth: StateFlow<Int> = combine(
    repository.getTodayLog(),
    limits
) { log, currentLimits ->
    if (log != null) repository.calculateBrainHealth(log, currentLimits)
    else 100
}.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 100)
```

`combine` merges the latest values of two flows; whenever *either* the day's log or the limits change, the block re-runs and produces a new score. The View just reads `brainHealth` and never sees this plumbing.

Some flows aren't database-backed at all. Screen time is polled live on a background thread:

```kotlin
private val todayScreenTime: Flow<Map<Platform, Int>> = flow {
    while (true) {
        emit(ScreenTimeHelper.getTodayMinutesByPlatform(app))
        delay(30_000)
    }
}.flowOn(Dispatchers.IO)
```

The `flow { ... }` builder emits a fresh reading, waits 30 seconds (`delay` is a coroutine pause, not a thread freeze), and repeats. `flowOn(Dispatchers.IO)` runs this work on the I/O thread pool so the main (UI) thread stays free.

## Connecting ViewModel to View: collectAsState

The View turns those StateFlows back into Compose state with one call each:

```kotlin
val todayLog by viewModel.todayLog.collectAsState()
val brainHealth by viewModel.brainHealth.collectAsState()
val mood by viewModel.mood.collectAsState()
```

`collectAsState()` subscribes the composable to a StateFlow and exposes its current value as Compose state. When the flow emits a new value, `collectAsState` triggers **recomposition** and the screen redraws with the new data — automatically. The `by` delegate again lets you read `brainHealth` as a plain `Int`.

> 💡 **Concept — `collectAsState` vs. `collectAsStateWithLifecycle`.** This file uses `collectAsState`, which collects while the composable is on screen. The lifecycle-aware variant, `collectAsStateWithLifecycle`, additionally *pauses* collection when the app is backgrounded (below the `STARTED` state), saving work for screens whose flows are expensive. They're interchangeable in usage; the lifecycle-aware one is the modern default for production screens.

Once collected, the values flow straight into composables:

```kotlin
val totalReels = todayLog?.getTotalReels() ?: 0
```

```kotlin
StatsCluster(
    fraction = fraction,
    totalReels = totalReels,
    reelLimit = reelLimit,
    todayLog = todayLog,
    score = brainHealth,
    streak = currentStreak,
    onViewStreaks = onViewStreaks
)
```

`StatsCluster` is a child composable that receives plain values. It doesn't know about ViewModels or flows — it just renders whatever it's given. That isolation is what makes Compose UI easy to read and reuse.

## Unidirectional data flow: state down, events up

Notice the shape of the data movement. **State flows down**: the ViewModel produces `totalReels`, `brainHealth`, `mood`; `DashboardScreen` passes them into `StatsCluster`, which passes them into `ScoreRing`, and so on down the tree. Children never reach *up* to grab data.

**Events flow up**: when the user taps something, the child calls a callback that was passed down to it. `StatsCluster` receives `onViewStreaks` and hands it to its streak child as `onClick`:

```kotlin
.clickable(interactionSource = interaction, indication = null, onClick = onClick)
```

That tap travels back up through the callbacks to `DashboardScreen`, and ultimately to navigation or the ViewModel — the thing that owns the relevant state. This one-way loop is called **unidirectional data flow (UDF)**.

> 💡 **Concept — Why one direction?** Because state has exactly one source of truth and only one path in and out, you never get two widgets fighting over the same value. To understand any piece of UI you trace data *down* and events *up* — never a tangle of mutual pokes. This is the discipline that makes a declarative UI predictable.

There's one more piece worth naming. The screen sometimes needs to react to the Android lifecycle — for example, re-checking a permission when the user returns from Settings. It uses a `DisposableEffect`:

```kotlin
DisposableEffect(lifecycleOwner) {
    val observer = LifecycleEventObserver { _, event ->
        if (event == Lifecycle.Event.ON_RESUME) {
            isTracking = ReelCounterService.isRunning
            hasUsagePermission = ScreenTimeHelper.hasPermission(context)
        }
    }
    lifecycleOwner.lifecycle.addObserver(observer)
    onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
}
```

`DisposableEffect` runs **side effects** — setup that isn't itself UI — and its `onDispose` block cleans up when the composable leaves the screen. Here it registers a lifecycle observer on entry and unregisters it on exit, so when the app resumes it refreshes `isTracking` and `hasUsagePermission`. Writing those state variables triggers recomposition, and the badge and banner update.

## Where the tree is rooted

All of this hangs off `MainActivity`, the single Android entry point:

```kotlin
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

`setContent { ... }` is the bridge from the classic Activity world into Compose: everything inside is composable. `BrainRotTrackerTheme` supplies colors and fonts to the whole tree, `Surface` paints the background, and `MainNavigation()` decides which screen composable (Dashboard, Stats, Limits, Streaks) to show. From here down, it's composables all the way.

## Recap

- **Compose is declarative**: you write `@Composable` functions that describe the screen for the current data; Compose **recomposes** when that data changes.
- **State** (`mutableStateOf` + `remember`) is observable data that drives recomposition.
- **MVVM** splits a screen into Model (data), ViewModel (screen-ready state), and View (composables) for clean separation of concerns.
- **`AndroidViewModel`** survives rotation and holds the app's state safely.
- **Coroutines + Flow** carry changing data off the main thread; **`stateIn(WhileSubscribed(5000))`** turns cold flows into always-available `StateFlow`s.
- **`collectAsState`** feeds those flows into the View, and **unidirectional data flow** (state down, events up) keeps the whole thing predictable.
