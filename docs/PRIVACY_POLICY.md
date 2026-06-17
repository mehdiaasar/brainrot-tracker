# Privacy Policy — BrainRot Tracker

**Last updated:** 17 June 2026

BrainRot Tracker ("the app", "we", "us") is a digital-wellness app that helps you
track and limit how many short-form videos ("reels", "Shorts", "Spotlight",
TikToks) you watch on social media. This policy explains exactly what data the app
accesses, how it is used, and the choices you have.

We do **not** sell your data, show ads, or use third-party advertising or analytics
SDKs.

---

## Summary

- All of your usage data is stored **locally on your device** by default.
- The app reads on-screen content **only** from Instagram, YouTube, TikTok, and
  Snapchat, and **only** to count short-form videos and enforce the limits you set.
- The content of the videos you watch is **never** recorded, stored, or transmitted.
- Cloud backup is **optional, off by default**, and uploads only daily *numerical
  summaries* — never screen content.

---

## What data the app accesses

### 1. On-screen content (Accessibility Service)

To count reels and enforce your limits, the app uses Android's Accessibility
Service. It inspects the on-screen view hierarchy of a **restricted list of apps
only**:

- Instagram (`com.instagram.android`)
- YouTube (`com.google.android.youtube`)
- TikTok (`com.zhiliaoapp.musically`)
- Snapchat (`com.snapchat.android`)

The service detects when a full-screen short-form video feed is being scrolled and
increments a counter. **It does not read, log, store, or transmit the text,
images, messages, captions, or any other content of these apps.** Detection happens
on-device in real time and only the resulting *count* is kept.

The service is enabled only after an explicit consent screen, and you can disable it
at any time in **Android Settings → Accessibility**.

### 2. Screen-time data (Usage Access)

With your permission, the app reads Android's app-usage statistics to show how many
minutes you spent in the tracked apps. This is queried live from the system and is
**not stored** in the app's database.

### 3. Account information (optional)

If you choose to sign in with Google to enable cloud backup, the app receives your
Google account identifier and basic profile information (name, email, avatar) via
Google Sign-In / Firebase Authentication. Sign-in is entirely optional — the app is
fully functional without an account.

---

## Data stored on your device

The following is stored locally on your device and is required for the app to
function:

- Daily counts of short-form videos watched, per platform
- The daily limits you set
- Streak history and a derived "brain health" score

This data never leaves your device unless you explicitly enable cloud backup
(below).

---

## Optional cloud backup

If — and only if — you (a) sign in with Google **and** (b) turn on the "cloud
backup" setting, the app uploads one summary document per completed day to a private
Google Firebase Firestore database scoped to your account
(`users/{your-account-id}/dailyLogs/{date}`). Each document contains only **numeric
daily aggregates**:

- Per-platform video counts for the day
- Per-platform daily limits
- Per-platform minutes (yesterday only)
- The day's "brain health" score
- Streak status and streak day number

No screen content, no message text, no browsing history, and no real-time activity
is ever uploaded. Backup is off by default and can be turned off at any time in the
app's settings. Your data is readable only by your authenticated account.

---

## How we use the data

The data is used solely to provide the app's features: counting short-form videos,
enforcing the limits you set, showing your stats and streaks, and (optionally)
backing up your daily summaries to your own account. We do not use it for
advertising, profiling, or any purpose unrelated to the app's stated function, and
we do not share it with third parties.

---

## Third-party services

- **Google Firebase (Authentication & Firestore)** — used only if you opt into
  Google Sign-In and cloud backup. Governed by Google's privacy policy:
  https://policies.google.com/privacy

No other third-party services receive your data.

---

## Data retention and deletion

- **Local data:** removed when you uninstall the app or clear its storage from
  Android Settings.
- **Cloud backup data:** you can disable backup at any time. To delete data already
  uploaded, sign in, turn off backup, and contact us at the address below to request
  deletion of your stored documents; we will remove them.

---

## Children

The app is not directed at children under 13 and does not knowingly collect data
from them.

---

## Changes to this policy

We may update this policy as the app evolves. Material changes will be reflected by
updating the "Last updated" date above.

---

## Contact

For privacy questions or data-deletion requests, contact:

**Aasar Mehdi** — mehdiaasar0@gmail.com
