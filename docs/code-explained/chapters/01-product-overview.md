Before we read a single line of Kotlin, let's understand *what* we are building and *why*. This chapter is the "what and why" tour. There is almost no code here — just enough to make the ideas concrete. Later chapters take each piece apart.

## The one-sentence pitch

LoopOut is an Android app that counts how many short videos you scroll through each day — Instagram Reels, YouTube Shorts, TikTok videos, and Snapchat Spotlight — shows you that number in a friendly way, and then helps you stop before the day disappears.

The project's own spec puts it plainly (`SPEC.md`):

> BrainRot Tracker is an Android app that watches how many short videos (Instagram Reels, YouTube Shorts, TikTok videos, Snapchat Spotlight) you scroll through each day, shows you the damage in a friendly way (a cartoon brain that "rots" as you scroll), and helps you stop — with daily limits, a blocking screen, streaks, and notifications.

> 💡 **Concept — Two names, one app.** The code calls the project *BrainRot Tracker*; the public Google Play name is *LoopOut* (`docs/PLAY_STORE_LISTING.md`). This is normal: the internal codename and the marketing name drift apart over a project's life. The internal code namespace is still `com.example.brainrottracker`, so you will see "brainrot" all through the source even though users see "LoopOut." Don't let it confuse you — they are the same app.

## The metaphor: "brain rot"

"Brain rot" is internet slang for the foggy, drained feeling after an hour of mindlessly swiping short videos. LoopOut takes that feeling literally and turns it into the app's mascot: **a cartoon brain that gets sadder and greyer as you scroll more.**

This is not a gimmick — it is the central design idea. Numbers like "you watched 137 reels" are easy to ignore. A little brain face that visibly droops from happy-pink to droopy-grey is harder to dismiss. The app reuses this character everywhere: on the main screen, on the floating bubble that hovers over Instagram, and even on the persistent notification.

> 💡 **Concept — Mascot, not photo.** The brain face shown over other apps is *drawn by math*, not stored as a single image file. In the floating overlay its color, eye openness, and mouth curve are all calculated from a "mood," so it can be re-drawn at any size and any expression without shipping dozens of pictures. (The in-app screens use a small set of pre-drawn character illustrations — one per mood — but the idea is the same: the brain is a *live readout* of how your day is going, not a fixed logo.) We'll meet the drawing code much later; for now just know the brain reacts to your behavior.

## Who is this for?

The target user is someone who already knows they scroll too much and wants help, not judgment. The Play Store full description names the pain directly (`docs/PLAY_STORE_LISTING.md`):

> Doomscrolling short videos and losing hours without noticing? LoopOut helps you see exactly how much you scroll — and stop when you've had enough.

A few things follow from *who* it is for:

- **It must be private.** Someone tracking an embarrassing habit will not trust an app that phones home. So LoopOut works fully offline, has no ads, and — in the shipping version — collects no data at all.
- **It must be gentle by default but able to be strict.** The user picks how hard the brakes are (more on that below).
- **It must require no account.** You can use every feature without signing up. Cloud backup exists but is entirely optional and off by default.

The listing's privacy promise is explicit:

> Everything stays on your device — LoopOut works fully offline and collects no data. No accounts, no ads, no third-party analytics. We never sell your data. The content of the videos you watch is NEVER recorded, stored, or sent anywhere — LoopOut only keeps a count.

## The four tracked platforms

LoopOut watches exactly four apps — the big four homes of short-form video:

| Platform | What it counts | Why it's special |
|---|---|---|
| **Instagram** | Reels | Has a clear full-screen "one video at a time" player the app can detect. |
| **YouTube** | Shorts | The player hides its position, so the app fingerprints each video instead. |
| **TikTok** | Every video | The whole app *is* short video, so every forward swipe counts. |
| **Snapchat** | Spotlight | Detected the same way as Instagram Reels. |

You don't need to understand the detection differences yet — the reel-detection chapter covers them. The takeaway now: the app does **not** watch your whole phone. It listens only to these four apps, and only for "did a short video just play." Everything else your phone does is invisible to it.

> ⚠️ **Gotcha — "Watching four apps" sounds creepier than it is.** LoopOut uses an Android feature called the *Accessibility Service* (the same plumbing screen readers for blind users rely on) to notice when a reel scrolls by. That feature *can* read screen content, which is exactly why Google Play scrutinizes it. LoopOut deliberately narrows it to four apps — declared in `accessibility_service_config.xml`, so Android delivers it nothing about any other app — and uses it only to count. It reads no messages or captions, and nothing about what you watch leaves the device. Because the capability is sensitive, the app shows a plain-English consent dialog *before* it ever asks you to turn the feature on.

## The core user journey

Here is the high-level path a new user walks, start to finish. Each step is a screen or behavior we'll dissect in later chapters.

### 1. Onboarding — granting permissions

On first launch, an onboarding screen walks you through four special permissions, each with a live "GRANTED / PENDING" badge. LoopOut can't do its job without some of these, but every one is requested with an explanation.

| Permission | What it unlocks |
|---|---|
| Accessibility Access | Counting reels in the four apps |
| Display over other apps | The floating counter bubble and the blocking screen |
| Usage access | The per-app screen-time minutes |
| Notifications | Milestone alerts and the daily summary |

> 💡 **Concept — "Special" permissions.** Most app permissions (camera, location) pop up a simple Allow/Deny dialog. These four are different: for most of them Android makes the user flip a switch deep in system Settings, because they are powerful. (Notifications are the exception — on Android 13+ they use a normal pop-up dialog.) Onboarding's job is to hand-hold you through those Settings pages and then notice when you come back with the switch flipped.

### 2. The Dashboard — your daily readout

The home screen shows the brain mascot, today's reel count, a brain-health ring, your screen-time minutes, and a few insight cards. This is where the metaphor lives: the brain reacts to today's behavior in real time. The whole header is one big "hero" block that scrolls up out of the way as you read further down — a small detail, but it's the first thing you see.

### 3. Scrolling — the live floating counter

When you open Instagram or TikTok and start scrolling, a small pill floats on top of that app showing the brain face and today's count. Tap it for a per-platform breakdown; drag it to move it. This is the "ambient awareness" piece — you see the number climb without leaving the feed.

### 4. The brain-health score

LoopOut boils your day down to a single number from 0 to 100, shown as a ring on the Dashboard. The lower the number, the more "fried" your brain is. The score maps to a label — for example **Elite Focus → Healthy Mind → Moderate Usage → High Brainrot → Critical Damage** — and the mascot's expression drifts from happy to droopy along with it.

> 💡 **Concept — One number from your reels.** The score is a *summary* of how your reel counts compare to the limits you set. Importantly, it is **reels-only by design**: it looks at how many short videos you watched on each platform versus your reel limit, and does *not* fold in screen-time minutes. (Screen time is still shown to you — on the Dashboard, the widget, and the bubble — it just doesn't move the score, so the headline numbers can never disagree with each other.) Only platforms you actually used count toward the score, averaged among themselves; going twice over a limit zeroes that platform's contribution. The exact formula lives in `UsageRepository.calculateBrainHealth` and gets its own chapter.

> 💡 **Concept — The mascot's mood is its own thing.** The cartoon face you see (its five moods are named, in the code, GREAT, ZONE, NEAR, LIMIT, OVER) is chosen from how far *today's reels* are into *today's reel limit* — not directly from the 0–100 score. The score and the face usually move together because both come from the same reel counts, but they are computed separately. Don't be surprised when you meet two different calculations later.

### 5. Limits and blocking — the brakes

In the Settings screen (opened from the Dashboard — it was once called "Goals") you set a daily limit per platform. When you hit it, a full-screen dark "Limit Reached" overlay covers the app. *How strict* that block is depends on a mode you choose:

| Mode | Behavior |
|---|---|
| **Hard** | The block reappears *every time* you reopen the app, until midnight. The only button is "Close app." |
| **Snooze** | Dismissing buys you 5 more minutes, then the block returns. The deadline survives a restart. |
| **Remind** | The block shows once per visit; "Got it" dismisses it for that session. |

This is the heart of the "gentle or firm" promise: the user decides how much willpower to outsource to the app.

### 6. Streaks — the reward

Every day you stay under your limits on all four platforms earns a streak day, shown on a green/red calendar with current and longest-streak counters. Days you didn't scroll at all count as wins. Streaks are the positive flip side of blocking — punishment and reward in the same app.

### 7. (Optional) Sign in to back up

Finally, after onboarding you're offered — once, skippably — a Google sign-in that backs up one small numeric record per finished day to the cloud. This is purely optional; the app is fully functional without ever signing in, and the shipping version hides this feature entirely.

> ⚠️ **Gotcha — "Optional" really means optional here.** Many apps treat sign-in as the front door. LoopOut treats it as a side door you can ignore forever. The spec is emphatic that the app is "fully functional anonymously," and the Play listing's data-safety section currently answers "No" to *collects user data* because cloud sync ships disabled (`CLOUD_SYNC_ENABLED = false`, per `docs/PLAY_STORE_LISTING.md`). Keep this in mind: throughout the codebase, the cloud path is guarded so the app builds and runs even with no cloud project configured at all.

## How the pieces fit together (a first sketch)

You don't need the full architecture yet, but here is the mental model to carry into the next chapters:

- A background **counter** notices reels and records them.
- A **floating bubble** shows the running count over other apps.
- A **blocking screen** appears when you cross a limit, in your chosen strictness mode.
- A **local database** on the phone stores per-day counts, your limits, and your streaks.
- A handful of **screens** (Dashboard, Stats, Streaks, plus a Settings screen) read that database and draw the brain.
- An **optional cloud backup** can mirror daily totals if — and only if — you opt in.

Notice what is *not* in that list: no server you depend on, no login wall, no analytics pipeline. LoopOut is, at its core, a self-contained tracker that lives on your phone and tries to talk you out of one more swipe.

## What's next

Now that you know *what* LoopOut does and *who* it's for, the next chapters get concrete: how an Android project is laid out, how the app is built and run, and then — feature by feature — how each behavior in this chapter actually works in code.
