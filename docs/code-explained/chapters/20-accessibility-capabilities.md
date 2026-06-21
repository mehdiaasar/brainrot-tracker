When you turn on LoopOut's reel counter, Android shows a scary warning: this app will be able to "observe your actions" and "retrieve window content." That warning exists because the feature LoopOut uses — the **Accessibility Service** — is one of the most powerful capabilities Android grants to any app. This chapter explains what that power *is*, where it came from, the full range of what it can do, and the small, careful slice of it that LoopOut actually uses.

> 💡 **Concept —** A *service* in Android is code that runs without a screen of its own. An **AccessibilityService** is a special kind of service that the operating system feeds a live stream of information about *other* apps — what's on their screens, what the user taps, when windows open. Normal apps are sandboxed and can't see each other; an accessibility service is a deliberate exception to that rule.

## Where it came from: assistive technology

The Accessibility API was not built to count reels. It was built so that people who can't use a phone the "normal" way can use it at all.

Consider a blind user. They can't read the screen, so Android ships **TalkBack**, a screen reader that speaks every button and heading aloud and lets the user navigate by swiping. Or a user with limited motor control who can't perform precise taps: **Switch Access** lets them drive the whole phone with one or two large physical buttons, scanning through on-screen elements one at a time. Or someone who prefers to talk: **Voice Access** lets them say "tap Send" and have it happen.

All three are accessibility services. To do their jobs they must (a) know what's on the screen and (b) act on the user's behalf — read out a button, then *press* it for the user. That dual need — **observe** and **act** — defines the entire API surface. Everything an accessibility service can do is some version of "see the screen" or "do something to the screen."

## The full capability surface

Here's the catalogue of what an `AccessibilityService` is technically capable of. LoopOut uses only the first two rows; the rest are listed so you understand the trust the user is extending.

| Capability | What it does | API entry point |
|---|---|---|
| Receive events | A callback for every UI event in watched apps | `onAccessibilityEvent(event)` |
| Read the screen | Walk the on-screen view tree: text, IDs, bounds | `rootInActiveWindow`, `AccessibilityNodeInfo` |
| Global actions | Press Back, Home, Recents, open notifications/quick settings, lock screen, take screenshot | `performGlobalAction(...)` |
| Node actions | Click, scroll, set text, or focus a *specific* on-screen element | `node.performAction(ACTION_CLICK, ...)` |
| Gesture dispatch | Synthesize taps and swipes at arbitrary coordinates (API 24+) | `dispatchGesture(...)` |
| Screenshots | Capture the current screen as a bitmap (API 30+) | `takeScreenshot(...)` |
| Magnification | Zoom and pan the display | `MagnificationController` |
| Soft keyboard | Show/hide or override the on-screen keyboard | `softKeyboardController` |
| Fingerprint gestures | React to swipes on the fingerprint sensor | `FingerprintGestureController` |

### Receiving events

The heart of the API is one callback. Android calls it every time something happens in a watched app:

```kotlin
override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    event ?: return
    checkDayRollover()
    val pkg = event.packageName?.toString() ?: return
    val platform = Platform.fromPackageName(pkg) ?: return
```

From `ReelCounterService.kt`. `event ?: return` bails if the event is null (a Kotlin idiom for "if null, stop"). Then it reads which app the event came from (`event.packageName`) and only continues if that app is one LoopOut tracks — `Platform.fromPackageName` returns `null` (and the method returns) for anything that isn't Instagram, YouTube, TikTok, or Snapchat.

Each event has a *type*. There are dozens. The branches LoopOut handles:

```kotlin
when (event.eventType) {
    AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED -> { ... }
    AccessibilityEvent.TYPE_VIEW_SCROLLED -> { ... }
    AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED -> { ... }
    else -> { ... }
}
```

`TYPE_WINDOW_STATE_CHANGED` fires when a new screen or app comes to the front. `TYPE_VIEW_SCROLLED` fires when a list scrolls. `TYPE_WINDOW_CONTENT_CHANGED` fires when something on screen updates (LoopOut acts on these only when the change touches a whole subtree — `CONTENT_CHANGE_TYPE_SUBTREE` — which is the signal that the reel content swapped). Other notable types a service *could* receive include `TYPE_VIEW_CLICKED` (the user tapped a button), `TYPE_VIEW_FOCUSED`, `TYPE_VIEW_TEXT_CHANGED` (the user typed), and `TYPE_NOTIFICATION_STATE_CHANGED` (a notification arrived — including its text).

> ⚠️ **Gotcha —** `TYPE_NOTIFICATION_STATE_CHANGED` means an accessibility service can read the *content* of every notification: your messages, OTP codes, banking alerts. This single fact is why the OS treats the permission as so sensitive — and why abusive apps covet it.

### Reading the screen

When an event hints something interesting changed, a service can ask for the entire on-screen layout:

```kotlin
var root = try { rootInActiveWindow } catch (_: Exception) { null }
```

`rootInActiveWindow` returns an `AccessibilityNodeInfo` — the top of a tree that mirrors the foreground app's UI. Every button, text label, and image is a *node*. Each node exposes its text (`node.text`), its developer-assigned ID (`node.viewIdResourceName`), its on-screen rectangle (`node.getBoundsInScreen(...)`), and whether it's currently visible (`node.isVisibleToUser`). LoopOut walks this tree to recognize a reel viewer:

```kotlin
when (node.viewIdResourceName) {
    "com.instagram.android:id/clips_viewer_view_pager" -> {
        inViewer = true
```

This is LoopOut detecting Instagram's Reels pager purely by its view ID — no content stored, no taps performed. Just *reading* the structure.

> 💡 **Concept —** A **view tree** is how every Android screen is built: a root container holding child containers holding buttons and text, like nested boxes. "BFS" (breadth-first search) — which LoopOut uses with an `ArrayDeque` queue — means visiting the tree level by level to find a node of interest.

### Acting on the screen (the powers LoopOut declines)

This is where accessibility goes from "watcher" to "puppeteer," and where LoopOut deliberately stops.

- **Global actions:** `performGlobalAction(GLOBAL_ACTION_BACK)` presses Back system-wide; siblings include `GLOBAL_ACTION_HOME`, `GLOBAL_ACTION_RECENTS`, `GLOBAL_ACTION_NOTIFICATIONS`, `GLOBAL_ACTION_QUICK_SETTINGS`, `GLOBAL_ACTION_LOCK_SCREEN`, and `GLOBAL_ACTION_TAKE_SCREENSHOT`. No coordinates needed — the OS performs them.
- **Node actions:** a service can call `node.performAction(AccessibilityNodeInfo.ACTION_CLICK)` to press a button *in another app*, `ACTION_SCROLL_FORWARD` to scroll a list, or `ACTION_SET_TEXT` to type into a field it doesn't own.
- **Gesture dispatch:** `dispatchGesture(...)` (API 24+) lets a service draw an arbitrary tap or swipe by coordinates — effectively a robot finger.
- **Screenshots:** `takeScreenshot(...)` (API 30+) captures the whole display as an image.

Stack those together and an accessibility service can drive a phone as if a human held it: open an app, type, tap, swipe, screenshot, dismiss dialogs. That is genuinely useful for Voice Access and automation tools — and exactly the toolkit malware uses to drain a bank account while the screen looks idle.

## What LoopOut actually uses — and what it refuses

LoopOut's configuration is declared in `accessibility_service_config.xml`:

```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:description="@string/accessibility_service_description"
    android:accessibilityEventTypes="typeAllMask"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100"
    android:accessibilityFlags="flagReportViewIds"
    android:settingsActivity="com.example.brainrottracker.MainActivity"
    android:packageNames="com.instagram.android,com.google.android.youtube,com.zhiliaoapp.musically,com.snapchat.android" />
```

Line by line:

- `canRetrieveWindowContent="true"` — the one real power LoopOut takes: permission to *read* the view tree. It needs this to spot a reel pager.
- `flagReportViewIds` — asks Android to include developer view IDs (like `clips_viewer_view_pager`) in nodes. Without it, that ID-based detection wouldn't work.
- `packageNames="...instagram...youtube...musically...snapchat..."` — the most important line for privacy. The service is wired to receive events from **exactly four apps**. (`com.zhiliaoapp.musically` is TikTok's package.) The OS won't deliver events from your bank, your messages, or anything else. This matches `Platform.fromPackageName` in the service, which returns early for any other package.
- `notificationTimeout="100"` — the minimum interval, in milliseconds, that Android waits between delivering same-type events to the service. It thins out bursts of rapid events so the service isn't woken hundreds of times a second.

> 💡 **Concept —** `accessibilityEventTypes="typeAllMask"` asks for *all* event types, but combined with the `packageNames` filter that only means "all event types **from those four apps**." The mask widens what kinds of events arrive; the package list narrows which apps they come from. The code then handles only the three event types it cares about and ignores the rest.

Crucially, look at what LoopOut never calls. There is no `performGlobalAction`, no `performAction`, no `dispatchGesture`, no `takeScreenshot`, no node-clicking anywhere in `ReelCounterService.kt`. It is **read-only**. When it wants to interrupt you at your limit, it does *not* swipe or press Back inside Instagram; it raises its own overlay window:

```kotlin
FloatingCounterService.instance?.showBlockingOverlay(platform, limit, mode)
```

The blocking happens in LoopOut's *own* draw-over-other-apps window (a separate `SYSTEM_ALERT_WINDOW` permission), not by manipulating the social app. The accessibility service's only job is to *count* — to read enough of the tree to know a new reel appeared:

```kotlin
val newCount = (reelCounts[pkg] ?: 0) + 1
reelCounts[pkg] = newCount
```

The `ACCESSIBILITY_DECLARATION.md` says it plainly: "No content is read, stored, or transmitted." It does **not** collect, log, store, or transmit the content of any app.

## The responsibility, and why Google Play clamps down

Because the API can read notifications and impersonate the user, Google Play treats it as a near-nuclear permission. Note that the underlying permission, `BIND_ACCESSIBILITY_SERVICE`, is one the *system* holds, not the app: it appears in the manifest as the permission the service requires before the OS will bind to it. That's why no app can grant itself accessibility — only the user, in Settings, can.

- **Prominent disclosure.** Before LoopOut ever opens the Accessibility settings screen, it must show a plain-language dialog explaining what the service does — and the user must accept it (stored as `accessibility_disclosure_accepted`). The exact wording lives in the declaration doc and `OnboardingScreen.kt`.
- **Core-functionality justification.** Play requires you to argue, on a form, that accessibility is *essential* and *can't be done another way*. LoopOut's answer: `UsageStatsManager` reports time-in-app but "cannot distinguish short-form video viewing from other in-app activity," so there's no non-accessibility way to count reels.
- **The `isAccessibilityTool` debate.** Android added an `isAccessibilityTool="true"` flag (API 30+) to mark services genuinely meant for assistive use. Apps that aren't true assistive tools — like LoopOut, a digital-wellbeing tool that happens to use the API — should *not* set it, and must instead survive Play's stricter functionality review. LoopOut's config does not set the flag. Mislabeling has been a flashpoint between Google and app developers.

> ⚠️ **Gotcha —** Enabling an accessibility service can never be done silently. The final toggle lives deep in Android's own Settings, behind another OS warning. No app can flip it for you — which is precisely why LoopOut's onboarding can only *deep-link* you there, not enable it itself.

### Legitimate vs. abusive uses

| Legitimate | Abusive |
|---|---|
| Screen readers (TalkBack) | Reading 2FA codes from notifications |
| Switch / Voice Access | Auto-clicking "Allow" on permission dialogs |
| Password managers autofilling | Logging every keystroke (a keylogger) |
| Digital-wellbeing counters like LoopOut | Silently transferring money via dispatched gestures |

The line isn't the *capability* — it's intent, transparency, and restraint. LoopOut lands firmly on the left by taking one read power, scoping it to four named apps, performing no actions, and keeping everything on-device.

## Recap

The Accessibility API exists to let assistive software see and operate a phone for users who can't do so conventionally. That gives any accessibility service an enormous toolkit: reading every screen and notification, pressing buttons, typing, swiping, screenshotting. LoopOut uses a deliberately tiny corner of it — read-only window content, scoped to four social apps, with no actions performed — and surrounds even that with a consent dialog and an on-device-only promise. Understanding the *whole* surface is what makes LoopOut's restraint meaningful: it could do far more, and chooses not to.
