Most of LoopOut's screens look consistent — cards have the same rounded corners, buttons spring the same way when you tap them, numbers roll the same way when they change. That consistency is not luck. It comes from a small folder of reusable building blocks in `ui/components/`. This chapter walks through each one and shows the Compose techniques they teach.

## Why extract reusable components at all?

Imagine you are building with LEGO. You *could* mold a brand-new brick every time you need one, but it would be slow, and your bricks would never quite match. Instead you reuse a handful of standard pieces. Reusable composables are those standard bricks.

> 💡 **Concept —** A **composable** is a function marked `@Composable` that describes a piece of UI. Compose (Android's modern UI toolkit) calls your function, looks at what it returns, and draws it. You build screens by calling small composables from bigger ones.

Pulling repeated UI into one place buys you three things:

| Benefit | What it means in practice |
| --- | --- |
| Consistency | Every card uses the same corner radius and shadow rule, so the app feels coherent. |
| One place to change | Tweak `AppCard` once and every screen updates. |
| Readable screens | A screen reads like a list of intentions (`AppCard { ... }`) instead of a wall of low-level styling. |

The seven components below live in `app/src/main/java/com/example/brainrottracker/ui/components/`.

## `AppCard` — the standard surface

A **card** is a raised rounded rectangle that groups related content. Almost every screen uses one. `AppCard.kt` defines the house style in two layers.

The first layer is a **modifier extension**. A `Modifier` is Compose's way of attaching behavior or styling to an element — size, padding, background, click handling — chained together like decorators.

```kotlin
fun Modifier.appCard(
    color: Color,
    shape: Shape = RoundedCornerShape(12.dp),
    border: Boolean = false,
    borderColor: Color = Color.Transparent,
    shadow: Dp = 0.dp,
): Modifier = this
    .then(if (shadow > 0.dp) Modifier.shadow(shadow, shape, clip = false) else Modifier)
    .clip(shape)
    .background(color)
    .then(if (border) Modifier.border(1.dp, borderColor, shape) else Modifier)
```

Reading it top to bottom:

- `fun Modifier.appCard(...)` adds a method onto `Modifier`, so you can write `Modifier.appCard(...)`. This is a Kotlin **extension function**.
- `.then(...)` chains another modifier conditionally. If `shadow` is greater than zero, it adds a drop shadow; otherwise it adds `Modifier` (an empty no-op). This is the idiomatic way to do "apply this modifier only sometimes."
- `.clip(shape)` rounds the corners, `.background(color)` fills the surface, and the final `.then(...)` draws a 1dp border only when `border` is true.

> 💡 **Concept —** `dp` means **density-independent pixels** — a unit that looks the same physical size on every screen, regardless of pixel density. Always size UI in `dp`, never raw pixels.

The order matters: shadow, then clip, then background, then border. The file's own comment calls this "the repeated `shadow → clip → background → border` chain." Clipping before background means the background respects the rounded corners.

The second layer is a ready-to-use composable for the common case:

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
) {
```

The defaults encode design rules: the surface color, radius, and padding all come from `AppTheme` (the app's theme object), and notice the light/dark rule — `border` is on only in light mode (`!AppTheme.colors.isDark`), and `shadow` is 3dp in light mode but 0dp in dark. The comment explains why: in light mode the soft shadow makes cards "read with depth instead of looking flat," while "dark mode stays borderless and flat."

The last parameter is the interesting one:

```kotlin
    content: @Composable ColumnScope.() -> Unit,
```

> 💡 **Concept —** This is a **slot API**. The `content` parameter is itself a composable lambda, so callers pass in *whatever they want inside the card*. `AppCard` supplies the surface; the caller fills the contents. Because the slot has receiver `ColumnScope`, the contents are laid out top-to-bottom and can use `Column`-only modifiers like `Modifier.weight()`.

So a screen writes `AppCard { Text("Hi"); Text("There") }` and gets a properly styled, theme-aware card for free. The two layers also serve different needs: complex cards with bespoke internal layout (image-bleed cards, Row-based insight cards) skip the `AppCard` composable and apply the lower-level `Modifier.appCard(...)` directly to their own `Column` or `Row`.

## `AnimatedCounter` — numbers that roll

When your reel count ticks from 9 to 10, `AnimatedCounter` slides the digits instead of snapping. It lives in `AnimatedCounter.kt`.

```kotlin
val countStr = count.toString()
Row(modifier = modifier) {
    countStr.forEach { digit ->
        AnimatedContent(
            targetState = digit,
            transitionSpec = {
                slideInVertically { -it } togetherWith slideOutVertically { it }
            },
            label = "digit_$digit"
        ) { targetDigit ->
            Text(text = targetDigit.toString(), style = style)
        }
    }
}
```

- The number is turned into a string and each character laid out in a horizontal `Row`, one `AnimatedContent` per digit. Animating digits separately means changing `19` to `20` only rolls the digits that actually changed.
- `AnimatedContent` is a Compose API that animates the transition whenever `targetState` changes. Here the state is a single `digit` character.
- `transitionSpec` defines the motion: `slideInVertically { -it }` slides the new digit in from above (`-it` is negative height = above), while `slideOutVertically { it }` slides the old digit downward and out. `togetherWith` runs both at once — that's the rolling-odometer effect.

> ⚠️ **Gotcha —** The `label` is `"digit_$digit"`, which is the digit's *value*, not its position. Labels are only used by Android Studio's animation inspector tooling, so this is harmless, but it is not a per-slot identifier. The visual behavior comes entirely from `targetState`.

## `MoodCharacter` — the bobbing mascot

`MoodCharacter.kt` renders the dashboard mascot. It does two animations at once: a **crossfade** when the mood image changes, and a continuous gentle **bob**.

```kotlin
val infinite = rememberInfiniteTransition(label = "moodIdle")
val phase by infinite.animateFloat(
    initialValue = 0f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(
        tween(periodMillis, easing = EaseInOutSine), RepeatMode.Reverse
    ),
    label = "moodPhase"
)
val bob = (phase - 0.5f) * 2f   // -1..1
```

- `rememberInfiniteTransition` creates an animation that never stops — perfect for an idle motion.
- `animateFloat` drives a number from `0f` to `1f` and, because of `RepeatMode.Reverse`, back to `0f` forever. `tween(periodMillis, easing = EaseInOutSine)` makes each pass take `periodMillis` (defaulting to 2200 ms for a full cycle) and ease smoothly in and out along a sine curve, so the bob accelerates and decelerates naturally instead of moving robotically.
- `val bob = (phase - 0.5f) * 2f` remaps the `0..1` phase into `-1..1`, so the character can move both up and down around its resting position.

The motion is applied with `graphicsLayer`, a modifier that transforms how an element is drawn without re-laying-out the screen:

```kotlin
Crossfade(targetState = drawableRes, animationSpec = tween(600), label = "moodCharacter") { res ->
    Image(
        painter = painterResource(res),
        contentDescription = null,
        contentScale = ContentScale.Fit,
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                translationY = bob * bobAmount * size.height
                transformOrigin = TransformOrigin(0.5f, 1f)
            }
    )
}
```

- `Crossfade` fades smoothly between images whenever `drawableRes` changes — a 600 ms fade, matching `tween(600)`. So when mood shifts from "great" to "near limit," the old art fades out as the new fades in.
- `translationY = bob * bobAmount * size.height` shifts the image vertically by a *fraction* of its own height (`bobAmount` defaults to `0.03f`, a 3% bob), so it scales correctly at any size.
- `transformOrigin = TransformOrigin(0.5f, 1f)` anchors transforms at the bottom-center, so the character feels grounded.
- `contentDescription = null` marks the image as decorative to screen readers — appropriate for a mascot that conveys no essential information.

## `PlatformLogo` — vector art drawn in code

`PlatformLogo.kt` draws each platform's icon (Instagram, YouTube, TikTok, Snapchat) using a `Canvas` — no image files needed.

```kotlin
@Composable
fun PlatformLogo(platform: Platform, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        when (platform) {
            Platform.INSTAGRAM -> drawInstagram()
            Platform.YOUTUBE -> drawYouTube()
            Platform.TIKTOK -> drawTikTok()
            Platform.SNAPCHAT -> drawSnapchat()
        }
    }
}
```

> 💡 **Concept —** A `Canvas` gives you a `DrawScope` — a blank drawing surface with primitives like `drawRoundRect`, `drawCircle`, and `drawPath`. The private helpers (`drawInstagram()`, etc.) are extension functions on `DrawScope`, so they draw directly onto the canvas.

A representative slice from the Instagram glyph:

```kotlin
val s = size.minDimension
val corner = s * 0.28f
drawRoundRect(
    brush = Brush.linearGradient(
        colors = listOf(Color(0xFFFEDA77), Color(0xFFF58529), Color(0xFFDD2A7B), Color(0xFF8134AF)),
        start = Offset(0f, s),
        end = Offset(s, 0f)
    ),
    topLeft = Offset(0f, 0f),
    size = Size(s, s),
    cornerRadius = CornerRadius(corner, corner)
)
```

- `size.minDimension` is the smaller of the canvas's width/height. Every dimension is then a fraction of `s` (`corner = s * 0.28f`), so the logo scales crisply at any size — a key advantage of drawing vectors over shipping fixed bitmaps.
- `Brush.linearGradient` paints Instagram's signature warm-to-purple gradient diagonally (from bottom-left `Offset(0f, s)` to top-right `Offset(s, 0f)`).

Other glyphs show off `Path` building: TikTok draws its musical note three times — a cyan copy and a red/pink copy offset behind a final white one — for the signature "glitch" look, and Snapchat builds a ghost with `quadraticBezierTo` curves for the scalloped bottom. The file comment frames this as "nominative use" — drawing logos purely to identify which app a count belongs to.

## `PillToggleButton` — a selectable chip

Used by the theme picker and blocking-mode chooser, `PillToggleButton.kt` is an icon-plus-label pill that looks "on" or "off."

```kotlin
Row(
    modifier = modifier
        .clip(shape)
        .background(if (selected) selectedBg else unselectedBg)
        .then(if (selected) Modifier.border(1.5.dp, accent, shape) else Modifier)
        .clickable { onClick() }
        .padding(vertical = 12.dp, horizontal = 6.dp),
    ...
)
```

The selected state changes four things at once: the background swaps (`selectedBg` vs `unselectedBg`), a 1.5dp accent border appears (via the same conditional `.then` trick from `AppCard`), the icon `tint` becomes the `accent`, and the label color brightens from `textSecondary` to `textPrimary`. Note the caller supplies `accent`, `selectedBg`, and `unselectedBg`, while the *text and icon* colors come from `AppTheme.colors` — a deliberate split so callers control the brand accent but typography stays consistent.

## `PressScale` — tactile feedback in one modifier

`PressScale.kt` makes any element shrink slightly while pressed, the same way a real button gives under your finger.

```kotlin
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    pressedScale: Float = 0.97f,
): Modifier = composed {
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) pressedScale else 1f,
        animationSpec = Motion.pressSpec(),
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}
```

- `composed { ... }` lets a `Modifier` use composable state (like `remember` and animations) inside its definition. A plain `Modifier` extension cannot; `composed` bridges that gap.
- An `InteractionSource` is a stream of interaction events. `collectIsPressedAsState()` turns "is this currently pressed?" into a boolean state that recomposes when it changes.
- `animateFloatAsState` smoothly animates `scale` toward `0.97f` when pressed and back to `1f` when released, using a spring (`Motion.pressSpec()`) for a bouncy feel.

> ⚠️ **Gotcha —** The doc comment gives two rules: share the *same* `interactionSource` you pass to the element's `clickable`, or the scale will not track real presses; and put `pressScale` at the **start** of the modifier chain so the whole surface — background included — scales together.

## `WarmToggle` — a hand-built switch

`WarmToggle.kt` is an on/off switch built from two `Box`es instead of Material's `Switch`, to match the warm palette.

```kotlin
Box(
    modifier = Modifier
        .size(width = 48.dp, height = 28.dp)
        .clip(CircleShape)
        .background(if (checked) AppTheme.colors.settingsOrange else offColor)
        .clickable { onCheckedChange(!checked) }
        .padding(4.dp),
    contentAlignment = if (checked) Alignment.CenterEnd else Alignment.CenterStart
) {
    Box(modifier = Modifier.size(20.dp).clip(CircleShape).background(Color.White))
}
```

- The outer `Box` is the pill-shaped track; its background is the theme's orange (`settingsOrange`) when on, or `offColor` (defaulting to `AppTheme.colors.border`) when off.
- `contentAlignment` is the whole trick: when checked it aligns the inner white knob to `CenterEnd` (right), when unchecked to `CenterStart` (left). The knob "slides" simply by being aligned to a different edge.
- `onCheckedChange(!checked)` reports the *flipped* value upward. This is the **state-hoisting** pattern: `WarmToggle` does not store its own state; it receives `checked` and reports changes, leaving the parent in control.

> 💡 **Concept —** **State hoisting** means a component holds no internal state — it takes its current value as a parameter (`checked`) and emits an event when the user acts (`onCheckedChange`). The single source of truth lives in the parent. This keeps components predictable and easy to test, and is the recommended Compose pattern for any control whose value matters to the rest of the app.

## What these seven teach

| Component | Headline Compose technique |
| --- | --- |
| `AppCard` | Slot APIs (`content: @Composable ColumnScope.() -> Unit`) and conditional modifiers |
| `AnimatedCounter` | `AnimatedContent` with a custom `transitionSpec` |
| `MoodCharacter` | `rememberInfiniteTransition`, `Crossfade`, and `graphicsLayer` |
| `PlatformLogo` | `Canvas` / `DrawScope` vector drawing with `Path` and `Brush` |
| `PillToggleButton` | State-driven styling and the caller-vs-theme color split |
| `PressScale` | `composed {}` modifiers and `InteractionSource` |
| `WarmToggle` | State hoisting and alignment-based layout |

Together they show the core lesson of building with Compose: keep the pieces small, let the *theme* and the *caller* decide the details through parameters and slots, and reuse relentlessly. Each screen you read later in this book is mostly just these bricks, arranged.
