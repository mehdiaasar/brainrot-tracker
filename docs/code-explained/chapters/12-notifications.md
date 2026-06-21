Notifications are the little messages that slide into the top of your phone screen — the ones from your messaging apps, your calendar, your bank. In LoopOut they do three jobs: they cheer or warn you as your reel count climbs, they hand you a tidy recap at the end of each day, and they keep a quiet "I'm watching your screen time" badge alive while the tracking service runs. Almost all of this logic lives in one small file, `NotificationHelper.kt`, and this chapter walks through it line by line.

## What a notification actually is

A **notification** is a request your app makes to Android: "please show this message to the user." Your app does not draw the popup itself. Instead it builds a small description — an icon, a title, some text — and hands it to the operating system. Android decides where and how to show it (status bar, lock screen, a banner that drops down) based on rules you'll meet shortly.

> 💡 **Concept —** Think of a notification like mailing a letter. You write the contents and drop it in the mailbox; the postal service decides the route, the delivery time, and whether it goes to the front door or the back. Your app writes the letter (the notification); Android is the postal service.

## Notification channels: why they exist

Before Android 8.0 (codename "Oreo", API level 26), an app's notifications were one undifferentiated pile. If an app spammed you, your only choice was to turn off *all* of its notifications. Android 8 fixed this by introducing **notification channels**: named buckets that group similar notifications together. The user can then tune each channel separately — mute the noisy one, keep the important one.

> 💡 **Concept —** "API level" is just a version number for Android's developer interface. API 26 = Android 8.0. The code checks `Build.VERSION.SDK_INT >= Build.VERSION_CODES.O` to ask "is this phone running Android 8 or newer?" — `O` stands for Oreo. Channels simply don't exist on older phones, so the code skips creating them there.

LoopOut declares three channels. Their IDs are constants at the top of the class so the rest of the code can reference them without typos:

```kotlin
const val CHANNEL_MILESTONES = "reel_milestones"
const val CHANNEL_DAILY_SUMMARY = "daily_summary"
const val CHANNEL_SERVICE = "service_running"
```

These string IDs are internal — the user never sees them. The user sees the human-readable names ("Reel Milestones", etc.) that are set when each channel is created.

### Importance: how loud each channel is allowed to be

When you create a channel you give it an **importance level**, which decides how intrusive its notifications may be — silent, quiet, or a buzzing banner that pops over whatever you're doing. Here's `createChannels()`:

```kotlin
fun createChannels() {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val manager = context.getSystemService(NotificationManager::class.java)

        val milestoneChannel = NotificationChannel(
            CHANNEL_MILESTONES,
            "Reel Milestones",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Alerts when you hit reel watching milestones"
            enableVibration(true)
        }
```

Reading this top to bottom: `getSystemService(NotificationManager::class.java)` fetches the system's `NotificationManager` — the object that actually creates channels and posts notifications. A `NotificationChannel` is built from three things: the internal ID, the display name, and the importance. `IMPORTANCE_HIGH` means "this may pop up as a banner and make a sound." The `.apply { ... }` block then sets extra properties on that channel: a description (shown in system settings) and `enableVibration(true)` so the phone buzzes when a milestone hits.

The other two channels are deliberately calmer:

```kotlin
        val summaryChannel = NotificationChannel(
            CHANNEL_DAILY_SUMMARY,
            "Daily Summary",
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "End-of-day usage summary"
        }

        val serviceChannel = NotificationChannel(
            CHANNEL_SERVICE,
            "Tracking Service",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Shows when LoopOut is actively monitoring"
            setShowBadge(false)
        }
```

`IMPORTANCE_DEFAULT` makes a sound but does not aggressively pop over your screen — fine for a once-a-day recap. `IMPORTANCE_LOW` is silent and unobtrusive — perfect for the always-present "service running" badge that you don't want nagging you. `setShowBadge(false)` stops that channel from putting a dot on the app's home-screen icon, which would be annoying for a notification that's *always* there.

Finally the three are registered:

```kotlin
        manager.createNotificationChannel(milestoneChannel)
        manager.createNotificationChannel(summaryChannel)
        manager.createNotificationChannel(serviceChannel)
```

| Channel | Internal ID | Importance | Behavior |
|---|---|---|---|
| Reel Milestones | `reel_milestones` | HIGH | Banner + vibration |
| Daily Summary | `daily_summary` | DEFAULT | Sound, no banner |
| Tracking Service | `service_running` | LOW | Silent, no badge |

> ⚠️ **Gotcha —** Importance is locked in at *creation* time. If LoopOut later changed `IMPORTANCE_HIGH` to `LOW` in code, existing installs would not notice — Android remembers the channel's first settings (and the user's own tweaks) forever. The only ways to change it are to delete and recreate the channel under a new ID, or have the user adjust it in system settings. Creating a channel that already exists is harmless: Android quietly ignores the duplicate.

`createChannels()` is called early and in several places — `MainActivity.onCreate` runs it (`NotificationHelper(applicationContext).createChannels()`), and both `ReelCounterService` and `FloatingCounterService` call it on startup. Calling it repeatedly is safe, and doing so guarantees the channels exist before any notification is posted.

## Channel 1 — Milestone alerts

The milestones are a fixed list, and a `MutableSet` remembers which ones already fired today so each one only buzzes once:

```kotlin
private val MILESTONES = listOf(25, 50, 75, 100, 150, 200)
```
```kotlin
private val shownMilestones = mutableSetOf<Int>()
```

`checkMilestone` is called from `ReelCounterService` every time a reel is counted, passing the day's running total:

```kotlin
fun checkMilestone(totalReels: Int) {
    val milestone = MILESTONES.firstOrNull { it == totalReels && it !in shownMilestones }
        ?: return

    shownMilestones.add(milestone)
```

`firstOrNull { ... }` scans the milestone list for one that *exactly equals* the current total *and* hasn't been shown yet. If none matches, `?: return` bails out immediately — the common case, since most reel counts (3, 26, 51…) aren't milestones. When one does match, it's added to `shownMilestones` so it can't fire a second time.

> ⚠️ **Gotcha —** The check is `it == totalReels`, an exact match, not "greater than or equal." If the count somehow jumps from 24 straight to 26 without ever being exactly 25, that milestone is silently skipped. In practice reels are counted one at a time so every milestone is hit, but it's worth knowing the trigger is precise rather than threshold-based.

Each milestone gets its own emoji and message via a `when` expression — the tone escalates from encouraging to alarming:

```kotlin
val (emoji, message) = when (milestone) {
    25 -> "🧠" to "You've watched 25 reels — doing okay!"
    50 -> "⚠️" to "50 reels! Time for a break?"
    ...
    200 -> "🚨" to "200 reels! Your brain is melting!"
    else -> "📱" to "You've watched $milestone reels today"
}
```

The `"🧠" to "..."` syntax builds a `Pair`, and `val (emoji, message) =` destructures it into two variables in one line.

Next, every notification needs to know what happens when the user taps it. That's a `PendingIntent`:

```kotlin
val intent = Intent(context, MainActivity::class.java)
val pendingIntent = PendingIntent.getActivity(
    context, 0, intent,
    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
)
```

> 💡 **Concept —** An `Intent` is a "what to do" message — here, "open `MainActivity`." A `PendingIntent` wraps it so *another* process (the system's notification shade) can fire it *later*, on your app's behalf, when the user taps. `FLAG_IMMUTABLE` means the system can't alter the intent's contents (a security requirement on modern Android), and `FLAG_UPDATE_CURRENT` reuses any existing pending intent but refreshes its data.

Now the notification itself is assembled with `NotificationCompat.Builder` — `Compat` meaning it works consistently across old and new Android versions:

```kotlin
val notification = NotificationCompat.Builder(context, CHANNEL_MILESTONES)
    .setSmallIcon(R.drawable.ic_brain_notification)
    .setContentTitle("$emoji LoopOut Alert")
    .setContentText(message)
    .setPriority(NotificationCompat.PRIORITY_HIGH)
    .setContentIntent(pendingIntent)
    .setAutoCancel(true)
    .build()

val manager = context.getSystemService(NotificationManager::class.java)
manager.notify(MILESTONE_NOTIFICATION_ID + milestone, notification)
```

The builder is attached to `CHANNEL_MILESTONES`, so it inherits that channel's HIGH importance and vibration. `setSmallIcon` is the status-bar symbol (the brain silhouette), `setContentIntent(pendingIntent)` wires up the tap action, and `setAutoCancel(true)` makes the notification disappear once tapped. `setPriority(PRIORITY_HIGH)` is the pre-Android-8 equivalent of channel importance — on old phones there are no channels, so priority is how the system ranks the notification.

The clever bit is the ID: `MILESTONE_NOTIFICATION_ID + milestone`. `MILESTONE_NOTIFICATION_ID` is `2000`, so the 25-reel alert gets ID `2025`, the 50-reel alert `2050`, and so on.

> 💡 **Concept —** A notification's numeric ID is its identity. `notify` with the *same* ID *replaces* the existing notification; a *new* ID adds another. By adding `milestone` to the base, each milestone gets a distinct ID, so all of them can sit in the shade at once instead of overwriting each other.

## Channel 2 — The end-of-day summary

`showDailySummary` fires exactly once per day, from `ReelCounterService.checkDayRollover()`, when the service notices the date has changed — and only when the gap is exactly one day, so a phone left off over a weekend doesn't get a stale recap. It receives the finished day's totals:

```kotlin
fun showDailySummary(totalReels: Int, totalMinutes: Int, brainHealth: Int) {
```

It picks a colored circle based on the brain-health score, then formats minutes into a friendly `"2h 15m"` string:

```kotlin
val hours = totalMinutes / 60
val mins = totalMinutes % 60
val timeStr = if (hours > 0) "${hours}h ${mins}m" else "${mins}m"
```

The notification uses `BigTextStyle` so it can expand into a multi-line recap:

```kotlin
.setStyle(
    NotificationCompat.BigTextStyle()
        .bigText("$totalReels reels watched\n$timeStr screen time\n$healthEmoji Brain Health: $brainHealth%\n\nKeep working on your digital habits!")
)
```

`setContentText` is the short collapsed line; `BigTextStyle().bigText(...)` is the longer version shown when the user expands the notification. It posts with the fixed ID `DAILY_SUMMARY_ID` (`3001`), so each day's recap replaces the previous one rather than piling up.

## Channel 3 — The persistent service notification

The third notification is different in kind. Android requires that any **foreground service** — a background process the system promises not to kill, here the floating-counter overlay — display an ongoing notification so the user always knows it's running. `getServiceNotification` builds it:

```kotlin
fun getServiceNotification(
    mood: DashboardMood = DashboardMood.GREAT,
    total: Int = 0,
    health: Int = 100,
): Notification {
```

The defaults let it be called bare (`getServiceNotification()`) at startup, before any reels are counted. The text adapts to whether there's data yet:

```kotlin
val text = if (total > 0) "$total reels today • $health% brain health"
else "Monitoring your screen time"
```

The key flag is `setOngoing(true)`, which makes the notification un-swipeable — the user can't dismiss it, which is exactly what a "this service is alive" badge should be:

```kotlin
val builder = NotificationCompat.Builder(context, CHANNEL_SERVICE)
    ...
    .setPriority(NotificationCompat.PRIORITY_LOW)
    .setContentIntent(pendingIntent)
    .setOngoing(true)

BitmapFactory.decodeResource(context.resources, mood.mainRes)?.let { builder.setLargeIcon(it) }
return builder.build()
```

`BitmapFactory.decodeResource` loads the current mood's brain artwork from a drawable resource into a `Bitmap`, and `setLargeIcon` shows it in the expanded view. Note this method *returns* the notification rather than posting it. `FloatingCounterService` does the posting: once via `startForeground(SERVICE_NOTIFICATION_ID, ...)` at launch, and again from `updateHud` — re-`notify`-ing the *same* `SERVICE_NOTIFICATION_ID` so the brain face and stats stay in sync as you scroll.

## The POST_NOTIFICATIONS runtime permission

There's one more rule. Since Android 13 (API 33, "Tiramisu"), apps must get the user's explicit permission to post notifications at all. The permission is declared in `AndroidManifest.xml`:

```xml
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

But declaring it isn't enough on Android 13+ — the app must *ask at runtime*. LoopOut does this from the onboarding screen's notification card:

```kotlin
if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
    if (!notificationRequestAttempted) {
        // First tap: show the system permission dialog directly
        notificationRequestAttempted = true
        notificationLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
    } else {
        // Repeat denials don't re-show the dialog — fall back to settings
        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
        }
        context.startActivity(intent)
    }
}
```

The first tap launches the standard system dialog. Android only ever shows that dialog *once*; if the user declines, future `launch` calls do nothing. So the code tracks `notificationRequestAttempted` and, on later taps, deep-links the user straight into the app's notification settings instead.

> ⚠️ **Gotcha —** On Android 12 and below the permission is granted automatically — there's no dialog. That's why the request is wrapped in the `>= TIRAMISU` check. If you post a notification on Android 13+ without this permission, Android silently swallows it: no error, no crash, just nothing on screen. When debugging "my notification isn't showing," the missing runtime grant is the first thing to check.

## Resetting at midnight

The last method clears the milestone memory so the same alerts can fire again tomorrow:

```kotlin
fun resetDailyMilestones() {
    shownMilestones.clear()
}
```

`ReelCounterService.checkDayRollover()` calls this right after showing the daily summary. Without it, `shownMilestones` would still contain yesterday's 25, 50, 75… and none of them would buzz again — your fresh day of scrolling would pass in silence.

## Putting it together

LoopOut's notification design maps cleanly onto Android's channel model: one HIGH-importance, buzzing channel for the moments that matter (milestones), one DEFAULT channel for the gentle daily recap, and one silent LOW channel for the unavoidable "service running" badge. Each fires from a clear trigger — milestones on every counted reel, the summary at the day rollover, the service notification for the life of the overlay — and the whole system respects the user's control: they can mute any single channel without losing the others.
