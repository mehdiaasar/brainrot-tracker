LoopOut puts a tiny live dashboard right on your home screen: a mood character, today's reel count, and your brain-health score — all without opening the app. That little panel is an **app widget**, and in this chapter we'll see how three small files bring it to life using **Jetpack Glance**.

## What is an app widget?

An **app widget** is a small piece of your app's UI that lives *outside* your app — on the home screen, the lock screen, or in the "widgets" tray. The clock, the weather panel, and the music-player controls you've probably seen are all app widgets.

Here's the catch that shapes everything in this chapter: a widget does **not** run inside your app's process. It is hosted by a completely separate program called the **launcher** (the home-screen app). Your app draws the widget, hands the finished result to the launcher, and the launcher displays it. Think of it like mailing someone a poster: you design and print it, but they hang it on *their* wall. You can't reach across and tweak it in real time — you can only mail an updated poster.

Because of this hand-off, Android historically forced widgets to be built from a restricted toolkit called **RemoteViews** — a serializable description of a layout that can be safely shipped to another process. RemoteViews supports only a handful of view types (text, image, a few layouts) and almost no interactivity. Writing them by hand is tedious and error-prone.

> 💡 **Concept — "Process":** A process is an isolated sandbox the operating system gives a running program. Two processes can't directly touch each other's memory; they communicate only through controlled channels. Your app and the launcher are separate processes, which is exactly why widgets need a "shippable" UI format.

## Where Jetpack Glance fits in

**Jetpack Glance** is Google's modern answer to the RemoteViews pain. It lets you *write* a widget using composable functions — the same Compose-style declarative code you use inside the app — and then Glance **compiles that down to RemoteViews** behind the scenes. You get the pleasant `Column { Text(...) }` syntax; Glance handles the ugly translation.

It is critical to understand that **Glance is not regular Compose.** It only looks similar. The two share concepts (composables, modifiers) but live in different packages and obey different rules.

| Regular Compose (in-app) | Jetpack Glance (widget) |
| --- | --- |
| `androidx.compose.*` imports | `androidx.glance.*` imports |
| `androidx.compose.foundation.layout.Column` | `androidx.glance.layout.Column` |
| `Modifier` | `GlanceModifier` |
| Renders directly to the screen | Compiled to RemoteViews, shipped to the launcher |
| Re-composes continuously, ~60fps | Recomposed only when you trigger an update |
| Full set of UI widgets | Restricted set (Text, Image, Row, Column, Box, Spacer…) |

> ⚠️ **Gotcha — Double-check your imports.** Because the type names overlap (`Column`, `Text`, `Image`, `Spacer`), it's easy to auto-import the wrong `Column` from regular Compose into a Glance file. The code won't compile, and the error can be confusing. Notice in `BrainRotWidget.kt` that every layout import begins with `androidx.glance` — that's deliberate.

## The three files that make a widget

A Glance widget in LoopOut is exactly three files working together:

| File | Role |
| --- | --- |
| `BrainRotWidget.kt` | The Glance composable — *what* the widget looks like and *what data* it shows |
| `BrainRotWidgetReceiver.kt` | The bridge that tells Android "this widget belongs to me" |
| `brainrot_widget_info.xml` | Metadata — size, how often to refresh, what category |

Let's walk each one.

## BrainRotWidget: the Glance composable

The widget class extends `GlanceAppWidget` and overrides one method, `provideGlance` (`BrainRotWidget.kt`):

```kotlin
class BrainRotWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val db = AppDatabase.getInstance(context)
        val todayLog = db.dailyLogDao().getByDateOnce(LocalDate.now().toString())
        val limits = db.userLimitsDao().getAllOnce()
        val reelLimit = limits.firstOrNull()?.dailyReelLimit ?: 50
        val totalReels = todayLog?.getTotalReels() ?: 0
```

`provideGlance` is the entry point Glance calls whenever the widget needs to be (re)built. A few things to notice:

- It's a **`suspend` function** — meaning it can pause to do slow work (like reading a database) without freezing anything. That's perfect, because the first thing it does is hit the Room database.
- `getByDateOnce(...)` and `getAllOnce()` are "once" reads — they fetch a single snapshot rather than a live stream. A widget only needs the current numbers at refresh time, not a continuously-updating flow.
- `LocalDate.now().toString()` produces today's date (e.g. `"2026-06-19"`), which is the key each `DailyLog` row is stored under.
- The `?: 50` and `?: 0` are **Elvis operators**: "if the left side is `null`, use this fallback instead." If the user has never set a limit, assume 50; if there's no log for today yet, assume 0 reels. This keeps a brand-new install from crashing on missing data.

Next it derives the *mood* and *health score* (`BrainRotWidget.kt`):

```kotlin
val reelRatio = if (reelLimit > 0) totalReels.toFloat() / reelLimit else 0f
val mood = DashboardMood.fromUsage(reelRatio)
val health = if (todayLog != null) {
    UsageRepository(db).calculateBrainHealth(todayLog, limits)
} else 100
val dark = resolveIsDark(context)

provideContent {
    WidgetContent(todayLog = todayLog, mood = mood, health = health, dark = dark)
}
```

- `reelRatio` is how far into the daily limit you are (e.g. 25 reels out of a 50 limit = `0.5`). `DashboardMood.fromUsage(reelRatio)` maps that ratio to one of five moods — `GREAT`, `ZONE`, `NEAR`, `LIMIT`, `OVER` — the same logic the in-app dashboard uses, so the widget and the app always agree.
- `health` is recomputed live via `UsageRepository(db).calculateBrainHealth(...)`. The code comment explains *why*: the score stored in the database "is never refreshed, which is why the widget always showed 100%." Computing it fresh fixes that stale-data bug.
- `provideContent { ... }` is the moment the data-gathering ends and the *UI description* begins. Everything inside that block is Glance composable code that Glance will turn into RemoteViews.

> 💡 **Concept — Why recompute instead of read the stored value?** The database has a `brainHealthScore` column, but nothing updates it between app sessions. A widget refreshing on its own schedule would read whatever was last written — often `100`. Recomputing from the raw `todayLog` guarantees the widget shows the truth.

### Resolving light vs. dark theme

A widget can't ask Compose's theme system for colors the way the app does, so it figures out dark mode manually (`BrainRotWidget.kt`):

```kotlin
private fun resolveIsDark(context: Context): Boolean {
    val prefs = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
    return when (prefs.getString(Prefs.THEME_MODE, "SYSTEM")) {
        "LIGHT" -> false
        "DARK" -> true
        else -> {
            val night = context.resources.configuration.uiMode and
                Configuration.UI_MODE_NIGHT_MASK
            night == Configuration.UI_MODE_NIGHT_YES
        }
    }
}
```

It reads the user's saved theme preference from `SharedPreferences` (a simple key-value file). If they chose `LIGHT` or `DARK`, it honors that. If they left it on `SYSTEM`, it inspects `configuration.uiMode` — a bitfield holding the device's current settings — and masks out the "night" bits to decide. This mirrors how the floating HUD picks its theme, per the comment in the source.

### Drawing the layout

`WidgetContent` is the composable that actually lays out the widget (`BrainRotWidget.kt`). First it picks colors as `ColorProvider`s:

```kotlin
val bgColor = ColorProvider(if (dark) Color(0xFF252320) else Color(0xFFEFE9DE))
val textColor = ColorProvider(if (dark) Color(0xFFFAF9F5) else Color(0xFF141413))
val subtextColor = ColorProvider(if (dark) Color(0xFFA09D96) else Color(0xFF6C6A64))
val healthColor = ColorProvider(mood.accent)
```

A `ColorProvider` wraps a color in a form Glance can ship to the launcher. Each color is chosen by the `dark` flag, except `healthColor`, which follows the mood's `accent` — so a "great" day glows green and an "over the limit" day turns red, matching the dashboard's score ring.

The body is a `Row` split into two halves (`BrainRotWidget.kt`):

```kotlin
Row(
    modifier = GlanceModifier
        .fillMaxSize()
        .background(bgColor)
        .cornerRadius(20.dp)
        .padding(horizontal = 10.dp, vertical = 4.dp)
        .clickable(actionStartActivity<MainActivity>()),
    verticalAlignment = Alignment.CenterVertically
) {
    Image(
        provider = ImageProvider(mood.mainRes),
        contentDescription = mood.headline,
        modifier = GlanceModifier.fillMaxHeight().defaultWeight()
    )
```

Reading the modifier chain top to bottom:

- `fillMaxSize()` — take the whole widget cell.
- `background(bgColor)` and `cornerRadius(20.dp)` — paint a rounded card.
- `padding(...)` — breathing room inside the edges.
- `clickable(actionStartActivity<MainActivity>())` — tapping the widget opens LoopOut's `MainActivity`. **This is the key interactivity rule for widgets:** you can't run arbitrary click handlers like in the app; you can only fire predefined *actions*. `actionStartActivity` launches an activity — one of the few things RemoteViews can do.
- The `Image` shows the mood character (`mood.mainRes` is a drawable resource). `fillMaxHeight().defaultWeight()` makes the brain as tall as the cell and gives it one share of the width.

> 💡 **Concept — `defaultWeight()`:** Inside a Glance `Row`, calling `defaultWeight()` on a child means "split the leftover space evenly among all children that asked for weight." The image takes one half and the stats `Column` takes the other half, so the brain stays large while the numbers are never pushed off-screen — even on a small home-screen cell.

The right half is a `Column` of stacked stats:

```kotlin
Row(verticalAlignment = Alignment.CenterVertically) {
    Text(
        text = "$totalReels",
        style = TextStyle(color = textColor, fontSize = 24.sp, fontWeight = FontWeight.Bold)
    )
    Spacer(GlanceModifier.width(5.dp))
    Text(text = "reels", style = TextStyle(color = subtextColor, fontSize = 11.sp))
}
```

That's the big bold reel count next to a small grey "reels" label, with a `Spacer` (an empty gap) between them. The pattern repeats for `"$healthScore%"` + "health", then a `mood.scoreCaption` line like "Great job! 🎉". Notice these `Text`, `Spacer`, and `TextStyle` types are the **Glance** versions — the only text/spacing tools the launcher can render.

## BrainRotWidgetReceiver: the bridge to Android

The widget class describes the UI, but Android needs a registered component to talk to. That's the receiver — and it's astonishingly short (`BrainRotWidgetReceiver.kt`):

```kotlin
class BrainRotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = BrainRotWidget()
}
```

`GlanceAppWidgetReceiver` is a kind of **BroadcastReceiver** — a component the system can wake up to deliver events (here, "time to update the widget"). All this subclass does is point Android at *which* widget it manages: `BrainRotWidget()`. Glance's base class handles the rest of the RemoteViews plumbing.

For Android to find this receiver, it's declared in the manifest (`AndroidManifest.xml`):

```xml
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

- `exported="true"` — the launcher is a different app, so it must be allowed to reach this receiver.
- The `<intent-filter>` with `APPWIDGET_UPDATE` says "send me widget-update broadcasts."
- The `<meta-data>` line links the receiver to its sizing/refresh config file — the XML we'll look at next.

## brainrot_widget_info.xml: the widget's spec sheet

This file is metadata the launcher reads *before* it ever asks your code to draw anything — it decides how big the widget can be and how often to refresh it (`brainrot_widget_info.xml`):

```xml
<appwidget-provider xmlns:android="http://schemas.android.com/apk/res/android"
    android:minWidth="180dp"
    android:minHeight="64dp"
    android:targetCellWidth="3"
    android:targetCellHeight="1"
    android:maxResizeHeight="220dp"
    android:resizeMode="horizontal|vertical"
    android:updatePeriodMillis="1800000"
    android:widgetCategory="home_screen"
    android:description="@string/app_name" />
```

| Attribute | What it controls |
| --- | --- |
| `minWidth` / `minHeight` | Smallest size the widget can shrink to (180dp × 64dp) |
| `targetCellWidth` / `targetCellHeight` | Preferred grid size on the home screen: 3 cells wide, 1 tall |
| `maxResizeHeight` | How tall the user may stretch it (220dp) |
| `resizeMode` | The user can resize it horizontally *and* vertically |
| `updatePeriodMillis` | How often the system refreshes it: `1800000` ms = 30 minutes |
| `widgetCategory` | It belongs in the home-screen widget tray |
| `description` | Text shown in the widget picker (here, the app name) |

> ⚠️ **Gotcha — `updatePeriodMillis` has a floor.** Android refuses to refresh more often than every 30 minutes through this attribute, no matter how small a number you put. The 30-minute value here is the practical minimum. If you ever needed near-real-time updates, you'd trigger them from your own code (for example by calling `BrainRotWidget().update(...)` after data changes) rather than relying on this timer.

> 💡 **Concept — `dp` (density-independent pixels):** A `dp` is a screen-size unit that looks the same physical size on phones with very different pixel densities. Using `dp` instead of raw pixels keeps the widget proportioned correctly across devices.

## Putting it together

When you drop the LoopOut widget on your home screen, here's the full round trip:

1. The launcher reads `brainrot_widget_info.xml` to learn the widget's size and refresh rate.
2. Every 30 minutes (or when the system decides), it broadcasts an update; `BrainRotWidgetReceiver` catches it.
3. Glance calls `BrainRotWidget.provideGlance`, which reads today's reels and limits from Room, computes the mood and health score, and resolves the theme.
4. `provideContent { WidgetContent(...) }` describes the UI in Glance composables.
5. Glance compiles that into RemoteViews and ships them to the launcher, which paints the brain + stats card.
6. Tapping it fires `actionStartActivity<MainActivity>()`, opening the app.

Three small files, one clean separation of concerns: **what to show** (`BrainRotWidget`), **who owns it** (`BrainRotWidgetReceiver`), and **how it's sized and scheduled** (`brainrot_widget_info.xml`). That's the whole widget.
