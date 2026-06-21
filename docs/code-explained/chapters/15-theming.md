A consistent look is what makes an app feel like *one* app instead of a pile of screens stitched together. This chapter explains how LoopOut keeps every screen visually in sync: a small "design system" built on top of Jetpack Compose, a warm color palette that flips between light and dark, a single switch that controls which mode is active, and shared rules for fonts, spacing, and animation.

## Why a design system at all?

Imagine you're painting every room in a house by hand, mixing each can of paint fresh. Room three comes out a slightly different shade of beige than room one, and when you decide to repaint, you have to redo every wall individually. That's what UI code looks like without a design system: each screen hard-codes its own colors and sizes, they drift apart over time, and a redesign means editing hundreds of files.

A **design system** is the opposite: one place that defines the colors, fonts, spacing, and motion, and every screen *reads* from that one place. Change it once, and the whole app updates.

LoopOut's design system lives in the `theme/` package. The single front door is an object called `AppTheme`.

> 💡 **Concept —** **Jetpack Compose** is Android's modern UI toolkit. Instead of writing screen layouts in XML files, you describe the UI with Kotlin functions marked `@Composable`. A composable function *emits* UI, and Compose re-runs it ("recomposes") when the data it reads changes.

## The problem CompositionLocal solves

Here's the awkward part. In Compose, a function only knows what you pass to it as parameters. If a deeply nested button needs to know "are we in dark mode, and what's the accent color?", the naive solution is to pass those values down through every function in between — even functions that don't care about color themselves. This is called *prop drilling*, and it clutters every signature.

`CompositionLocal` is Compose's fix. Think of it as an **ambient value** — like the temperature of a room. You set it once at the top of the house, and anything inside can read it without being handed a thermometer at every doorway.

> 💡 **Concept —** **CompositionLocal** is a value that flows implicitly down the composable tree. A parent *provides* it; any descendant can *read* it without it being a function parameter. The project's `CLAUDE.md` calls out exactly this: the UI now "reads AppTheme.colors via CompositionLocal; no more rememberIsDark/color-threading."

## `AppTheme`: the single front door

`AppTheme.kt` is tiny on purpose. It just exposes read-only accessors:

```kotlin
object AppTheme {
    val colors: AppColors
        @Composable @ReadOnlyComposable get() = LocalAppColors.current

    val spacing: Spacing
        @Composable @ReadOnlyComposable get() = LocalSpacing.current

    val radii: Radii
        @Composable @ReadOnlyComposable get() = LocalRadii.current

    val typography: Typography
        @Composable @ReadOnlyComposable get() = MaterialTheme.typography

    val shapes: Shapes
        @Composable @ReadOnlyComposable get() = MaterialTheme.shapes
}
```

Line by line:

- `object AppTheme` — a Kotlin singleton; there's exactly one of it, app-wide.
- `val colors ... get() = LocalAppColors.current` — when a screen reads `AppTheme.colors`, it reaches into the CompositionLocal called `LocalAppColors` and returns whatever was *provided* higher up the tree. `.current` is the "read the ambient value" call.
- `@Composable @ReadOnlyComposable` — these annotations say the getter can only be called from composable code and that it only *reads* (it never emits UI or has side effects), which lets Compose optimize it.
- `typography` and `shapes` deliberately just forward to `MaterialTheme` — the design system reuses Material's machinery for those two instead of reinventing them.

So in a screen you write `AppTheme.colors.accent` and never think about where it came from.

## The warm palette and semantic color roles

`Color.kt` holds the raw paint cans — concrete color values. The dark set is prefixed `Warm*`, the light set `WarmLight*`:

```kotlin
// Warm / dark palette (used across all screens in dark mode)
val WarmBackground = Color(0xFF181715)
val WarmSurface = Color(0xFF252320)
val WarmAccent = Color(0xFFCC785C)       // terracotta CTA
// Warm / light palette (used across all screens in light mode)
val WarmLightBackground = Color(0xFFFAF9F5)
val WarmLightSurface = Color(0xFFEFE9DE)
```

`Color(0xFF181715)` is an ARGB hex value: `FF` is fully opaque, then red/green/blue. These warm earthy tones are the app's real palette.

> ⚠️ **Gotcha —** legacy colors. Older screens once carried navy/cyan colors of their own. The current `Color.kt` is all-warm, but `CLAUDE.md` still flags navy/cyan as *legacy* values referenced by a few leftover components. Don't reach for them in new screens — use the `Warm*`/`WarmLight*` tokens (or, better, the semantic roles below).

Raw colors alone aren't enough. Is `WarmSurface` "the card color" or "the background"? To answer that, `AppColors.kt` defines **semantic roles** — names that describe *purpose*, not appearance:

```kotlin
@Immutable
data class AppColors(
    val isDark: Boolean,
    // Core surfaces
    val background: Color,
    val surface: Color,       // card fill
    val surfaceAlt: Color,    // inner / progress-track fill
    val border: Color,        // card stroke, dividers, chart gridlines
    val trackInactive: Color, // slider inactive track
    // Text
    val textPrimary: Color,
    val textSecondary: Color,
    // Brand / CTA
    val accent: Color,        // terracotta WarmAccent
    val accentOn: Color,      // content drawn on top of [accent]
    // Status
    val success: Color,
    val warning: Color,
    val error: Color,
    // … goal, insight, settings accents, hero …
)
```

`@Immutable` is a promise to Compose that, once built, this object never changes its fields — which lets Compose skip needless recomposition. Naming colors by *role* means a screen asks for `colors.textSecondary`, and the design system decides which actual paint that maps to in the current mode.

Two factory functions do that mapping:

```kotlin
fun darkColors() = AppColors(
    isDark = true,
    background = WarmBackground,
    surface = WarmSurface,
    textPrimary = WarmText,
    accent = WarmAccent,
    accentOn = Color.White,
    // …
)

fun lightColors() = AppColors(
    isDark = false,
    background = WarmLightBackground,
    surface = WarmLightSurface,
    textPrimary = WarmLightText,
    accent = WarmAccent,   // accent is the same terracotta in both modes
    accentOn = Color.White,
    // …
)
```

Notice `accent` is the same terracotta in both modes, while `background` and `surface` swap. That's the whole point of semantic roles: the *meaning* stays constant, the *value* adapts.

| Semantic role | Used for | Dark value | Light value |
|---|---|---|---|
| `background` | screen backdrop | `WarmBackground` | `WarmLightBackground` |
| `surface` | card fill | `WarmSurface` | `WarmLightSurface` |
| `border` | card stroke, dividers | `WarmBorder` | `WarmLightBorder` |
| `accent` | primary call-to-action | `WarmAccent` | `WarmAccent` |
| `textPrimary` | main text | `WarmText` | `WarmLightText` |

Finally the CompositionLocal itself:

```kotlin
val LocalAppColors = staticCompositionLocalOf<AppColors> {
    error("AppColors not provided — wrap content in BrainRotTrackerTheme")
}
```

`staticCompositionLocalOf` creates a CompositionLocal whose default is to *crash* with a helpful message. There is no sensible default color set, so if you ever read colors outside the theme, you get a clear error instead of mysterious wrong colors.

> 💡 **Concept —** `staticCompositionLocalOf` vs `compositionLocalOf`. The "static" version assumes the value rarely changes; when it does, Compose recomposes the whole subtree that reads it rather than tracking each individual reader. That's perfect here — the theme flips only when the user changes mode, which is exactly when we *want* the whole screen to redraw.

## `ThemeController`: one switch for the whole app

How does the app know whether to provide dark or light colors? That's `ThemeController.kt`, a singleton that remembers the user's choice:

```kotlin
object ThemeController {

    enum class Mode { SYSTEM, LIGHT, DARK }

    var mode by mutableStateOf(Mode.SYSTEM)
        private set

    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        val p = context.getSharedPreferences(Prefs.FILE, Context.MODE_PRIVATE)
        prefs = p
        mode = runCatching { Mode.valueOf(p.getString(Prefs.THEME_MODE, Mode.SYSTEM.name)!!) }
            .getOrDefault(Mode.SYSTEM)
    }

    fun selectMode(newMode: Mode) {
        mode = newMode
        prefs?.edit()?.putString(Prefs.THEME_MODE, newMode.name)?.apply()
    }
}
```

What's happening:

- `enum class Mode { SYSTEM, LIGHT, DARK }` — three choices. `SYSTEM` means "follow the phone's setting."
- `var mode by mutableStateOf(Mode.SYSTEM)` — this is **Compose state**. When `mode` changes, every composable that read it recomposes automatically. `private set` means only `ThemeController` can change it (callers must go through `selectMode`).
- `init(context)` runs once at startup. It reads the saved mode out of `SharedPreferences` (a small key-value file on disk), keyed by `Prefs.THEME_MODE`. `runCatching { … }.getOrDefault(Mode.SYSTEM)` safely falls back to `SYSTEM` if the stored value is missing or corrupt.
- `selectMode(newMode)` updates the in-memory state *and* persists it to disk with `.apply()` (an asynchronous save).

> 💡 **Concept —** **SharedPreferences** is Android's simplest persistence: a named file of key/value pairs. It survives app restarts, so the user's theme choice sticks. Here the file name comes from `Prefs.FILE`, which equals `"brainrot_prefs"` — the same shared file `CLAUDE.md` describes for theme mode and the blocking settings. Centralizing that name in the `Prefs` object stops a typo'd key from silently reading the default and losing state.

The mode is an *intention*; the app still needs a yes/no answer for "is it dark right now?" That's `rememberIsDark()`:

```kotlin
@Composable
fun rememberIsDark(): Boolean = when (ThemeController.mode) {
    ThemeController.Mode.SYSTEM -> isSystemInDarkTheme()
    ThemeController.Mode.LIGHT -> false
    ThemeController.Mode.DARK -> true
}
```

For `SYSTEM` it asks Compose's built-in `isSystemInDarkTheme()`; otherwise it's a fixed `true`/`false`. Because it reads `ThemeController.mode` (Compose state), it re-evaluates the instant the user flips the switch.

## Wiring it together in `BrainRotTrackerTheme`

`Theme.kt` is where the providing happens — the top of the house where the thermostat is set:

```kotlin
@Composable
fun BrainRotTrackerTheme(
    content: @Composable () -> Unit,
) {
    val dark = rememberIsDark()
    val appColors = if (dark) darkColors() else lightColors()
    CompositionLocalProvider(
        LocalAppColors provides appColors,
        LocalSpacing provides Spacing(),
        LocalRadii provides Radii(),
    ) {
        MaterialTheme(
            colorScheme = materialSchemeFrom(appColors),
            typography = Typography,
            shapes = WarmShapes,
            content = content,
        )
    }
}
```

Reading it:

- `val dark = rememberIsDark()` then pick the matching `AppColors` factory.
- `CompositionLocalProvider(... provides ...)` installs the ambient values for everything inside `content`. From here down, `AppTheme.colors` works.
- It then nests a `MaterialTheme`, which is Compose's built-in theming for stock widgets (Slider, ripple, text selection). `materialSchemeFrom(appColors)` translates our semantic roles into Material's vocabulary so those built-in components inherit the warm palette too:

```kotlin
private fun materialSchemeFrom(c: AppColors) = if (c.isDark) {
    darkColorScheme(
        primary = c.accent, onPrimary = c.accentOn,
        surface = c.surface, onSurface = c.textPrimary,
        // …
    )
} else {
    lightColorScheme( /* … */ )
}
```

Without this bridge, a stock `Slider` would render in Material's stale default colors and clash with the app.

## Typography: `Type.kt`

`Type.kt` defines two things. `AppType` is a custom set of text styles screens use directly (`style = AppType.body`), each fixed at a specific size, weight, and line-height:

```kotlin
object AppType {
    val screenTitleLg = TextStyle(fontWeight = FontWeight.Bold, fontSize = 36.sp, lineHeight = 41.sp, letterSpacing = (-0.5).sp)
    val displayNumber = TextStyle(fontWeight = FontWeight.Bold, fontSize = 52.sp, lineHeight = 56.sp, letterSpacing = (-1.5).sp)
    val body = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp)
    val caption = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp)
    // …
}
```

> 💡 **Concept —** `sp` ("scale-independent pixels") is the unit for text. Unlike `dp`, `sp` respects the user's system font-size accessibility setting, so text grows when someone enlarges their phone's fonts.

The file's comment explains the intent: one style per recurring role at its *exact current size*, so adopting it changes nothing visually — it just centralizes the values. If one call needs a different weight, override it at the call site (`Text(x, style = AppType.body, fontWeight = FontWeight.Bold)`) while the size stays consistent. The second value, `Typography`, fills in Material's standard slots (`displayLarge`, `bodyMedium`, …) so stock components are styled too, all using `FontFamily.SansSerif` (the platform default).

## Spacing and radii: `AppDimens.kt`

Consistent gaps matter as much as consistent color. `AppDimens.kt` defines a small scale:

```kotlin
@Immutable
data class Spacing(
    val xs: Dp = 4.dp,  val sm: Dp = 8.dp,  val md: Dp = 12.dp,
    val lg: Dp = 16.dp, val xl: Dp = 20.dp, val xxl: Dp = 24.dp, val xxxl: Dp = 32.dp,
)

@Immutable
data class Radii(val chip: Dp = 10.dp, val card: Dp = 12.dp, val cardLg: Dp = 16.dp)

val LocalSpacing = staticCompositionLocalOf { Spacing() }
val LocalRadii = staticCompositionLocalOf { Radii() }
```

The comment records the dominant real-world usage: `xl=20` is the screen gutter, `md=12` the gap between cards, `lg=16` inner padding. Screens write `AppTheme.spacing.md` instead of a magic `12.dp`, so the rhythm is uniform and tweakable in one place. `Dp` ("density-independent pixels") is the layout unit that looks the same physical size on every screen density.

> ⚠️ **Gotcha —** unlike `LocalAppColors`, the spacing and radii CompositionLocals are created with a working default (`Spacing()` / `Radii()`), not an `error(...)`. So reading `AppTheme.spacing` outside the theme won't crash — it just quietly returns the defaults. Convenient, but it means a missing theme wrapper shows up as wrong *colors* first.

## Motion: `Motion.kt`

Animations get the same treatment so transitions feel like one product:

```kotlin
object Motion {
    fun <T> spec(): AnimationSpec<T> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
    fun pressSpec(): AnimationSpec<Float> = spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium)
    val colorSpec: AnimationSpec<Color> = tween(durationMillis = 320, easing = FastOutSlowInEasing)
    val floatSpec: AnimationSpec<Float> = tween(durationMillis = 450, easing = FastOutSlowInEasing)
    val dpSpec: AnimationSpec<Dp> = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow)
}
```

A **spring** animates like a physical spring (dampingRatio controls bounce; lower = bouncier). A **tween** runs for a fixed duration with an easing curve. `spec()` is the calm default for layout/value changes; `pressSpec()` is bouncier for tap feedback; `colorSpec`/`floatSpec` are timed cross-fades.

| Token | Type | Use |
|---|---|---|
| `Motion.spec()` | spring (damping 0.85) | layout / value changes |
| `Motion.pressSpec()` | spring (damping 0.55) | press feedback |
| `Motion.colorSpec` | 320 ms tween | color cross-fades |
| `Motion.floatSpec` | 450 ms tween | float transitions |
| `Motion.dpSpec` | spring (damping 0.85) | size animations |

> 💡 **Concept —** `spec()` and `pressSpec()` are *functions* (with a generic `<T>` on `spec`) because an `AnimationSpec` is typed to what it animates; calling them fresh each time hands the animation API a correctly-typed spec. The `colorSpec`/`floatSpec`/`dpSpec` values are plain `val`s because their type is already fixed.

## It all comes together: `AppCard`

`AppCard.kt` shows the payoff. A card reads everything from `AppTheme` and bakes in the house rule "border in light mode, none in dark":

```kotlin
@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    color: Color = AppTheme.colors.surface,
    radius: Dp = AppTheme.radii.card,
    border: Boolean = !AppTheme.colors.isDark,
    shadow: Dp = if (AppTheme.colors.isDark) 0.dp else 3.dp,
    contentPadding: PaddingValues = PaddingValues(AppTheme.spacing.md),
    content: @Composable ColumnScope.() -> Unit,
)
```

Every default — surface color, corner radius, whether to draw a border, shadow depth, padding — comes from the design system. In light mode the card gets a soft 3 dp shadow so it reads with depth; dark mode stays flat and borderless. A screen just writes `AppCard { … }` and gets a card that's automatically correct in both light and dark.

Under the hood `AppCard` delegates to a `Modifier.appCard(...)` extension that centralizes the repeated `shadow → clip → background → border` chain, so cards with bespoke internal layouts (image-bleed cards, Row-based insight cards) can reuse the same look on any `Column` or `Row`. That single `AppCard { … }` line is the whole reason the design system exists: define once, reuse everywhere, and the app stays visually whole.
