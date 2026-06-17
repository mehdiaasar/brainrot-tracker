# Play Console — Accessibility (AccessibilityService) Declaration

This document holds the answers to paste into the Google Play Console **Permissions
declaration form** for the `BIND_ACCESSIBILITY_SERVICE` / `AccessibilityService`
usage, plus the in-app prominent-disclosure wording. Keep it in sync with the actual
app behavior.

---

## 1. Is your app's core functionality the use of the Accessibility API?

**Answer: Yes.** The app's primary purpose is to help users monitor and reduce their
consumption of short-form video ("reels"/"Shorts") on social media. Detecting when a
short-form video feed is being scrolled, and blocking it once the user's
self-imposed daily limit is reached, can only be done by inspecting the on-screen UI
of those apps in real time — which requires the Accessibility API. There is no
public Android API that reports "a reel was just watched in another app."

---

## 2. Which accessibility capabilities does the app use, and why?

- **Retrieve window content (`canRetrieveWindowContent`)** — to detect, on-device,
  that the foreground app is showing a full-screen, single-column video pager
  (Instagram Reels, Snapchat Spotlight, YouTube Shorts) or that a TikTok-style feed
  is being scrolled. This is used purely to count short-form videos. No content is
  read, stored, or transmitted.
- **Window state / scroll events** — to know when a tracked app is opened or a feed
  is scrolled, so counts and limit enforcement stay accurate.

The service is restricted to four package names only (Instagram, YouTube, TikTok,
Snapchat) via `accessibility_service_config.xml`; it does not observe any other app.

---

## 3. Could this functionality be achieved without the Accessibility API?

**No.** UsageStatsManager reports time spent in an app but cannot distinguish
short-form video viewing from other in-app activity, and provides no per-video
signal or any hook to interrupt the feed at a limit. Counting individual reels and
enforcing per-video limits requires reading the foreground UI, which only the
Accessibility API exposes.

---

## 4. How does the app obtain user consent?

Before the Accessibility settings screen is ever opened, the app shows a
**prominent disclosure** dialog explaining what the service does and why, in plain
language. The user must explicitly accept (persisted as
`accessibility_disclosure_accepted`) before being taken to enable the service.
Enabling the service is itself a manual action in Android Settings. The user can
disable it at any time.

---

## 5. Prominent disclosure wording (shown in-app before enabling)

> **BrainRot Tracker needs the Accessibility service**
>
> To count reels, Shorts, Spotlight, and TikToks — and to block them once you hit
> your daily limit — BrainRot Tracker uses Android's Accessibility service to detect
> when you're scrolling a short-video feed in Instagram, YouTube, TikTok, or
> Snapchat.
>
> It only watches these four apps, and only to count videos and enforce the limits
> you set. It never reads or stores your messages, captions, or any other content,
> and nothing about what you watch ever leaves your device.
>
> You can turn this off any time in Settings → Accessibility.
>
> [ Not now ]   [ Continue ]

---

## 6. Data handling statement (for the form's data section)

The Accessibility service processes on-screen information **on-device only**, in
real time, solely to count short-form videos and trigger limit enforcement. It does
**not** collect, log, store, or transmit the content of any app. The app contains no
ads and no third-party analytics. See the privacy policy for full details:
**[INSERT HOSTED PRIVACY POLICY URL]**

---

## 7. Links to provide on the form

- **Privacy policy URL:** [INSERT HOSTED URL — see docs/PRIVACY_POLICY.md]
- **Video walkthrough (recommended):** Google often asks for a short screen
  recording showing (a) the prominent-disclosure dialog, (b) enabling the service,
  and (c) the reel-counting / blocking feature in action. Record one before
  submitting to speed up review.
