# Play Store Listing — LoopOut

Copy-paste source for the Google Play Console store listing. Keep in sync with app
behavior.

---

## App name (max 30 chars)

```
LoopOut: Reel & Shorts Limit
```
(28 chars. Alt: just `LoopOut` if you prefer a clean name.)

---

## Short description (max 80 chars)

```
Count and cut down the reels, Shorts & TikToks you watch. Reclaim your time.
```
(75 chars.)

---

## Full description (max 4000 chars)

```
Doomscrolling short videos and losing hours without noticing? LoopOut helps you
see exactly how much you scroll — and stop when you've had enough.

LoopOut counts every reel, Short, Spotlight, and TikTok you watch, shows your daily
totals and trends, and gently (or firmly) blocks the feed once you hit the limit you
set. No accounts, no ads, no tracking — everything stays on your phone.

WHAT IT DOES
• Counts short-form videos across Instagram, YouTube, TikTok, and Snapchat
• Live screen-time for each app, right next to your video counts
• Set a daily limit per app — and choose how strict the block is
• Three blocking styles: Hard (locked until midnight), Snooze (5-minute grace), or
  a one-time Reminder per session
• Streaks reward every day you stay under your limit
• A simple "brain health" score that reflects your short-video habits
• Home-screen widget and a floating counter so your progress is always visible
• Warm, calm design with full Light / Dark / System theme support

PRIVATE BY DESIGN
• Everything stays on your device — LoopOut works fully offline and collects no data
• No accounts, no ads, no third-party analytics. We never sell your data.
• The content of the videos you watch is NEVER recorded, stored, or sent anywhere —
  LoopOut only keeps a count

HOW THE COUNTING WORKS (Accessibility)
To recognize when you're scrolling a short-video feed, LoopOut uses Android's
Accessibility service — the only way Android lets an app tell that a reel just played
in another app. It watches ONLY Instagram, YouTube, TikTok, and Snapchat, ONLY to
count short videos and enforce your limits. It reads no messages, captions, or other
content, and nothing about what you watch ever leaves your device. You enable it
yourself after a clear explanation, and can turn it off any time in
Settings → Accessibility.

Take back control of your scroll. Set a limit, build a streak, and LoopOut.
```

---

## Categorization

- **App category:** Health & Fitness (or Productivity)
- **Tags:** digital wellbeing, screen time, focus, habit

---

## Graphics checklist (Play requirements)

- [ ] **App icon** — 512×512 PNG, 32-bit, < 1 MB. ⚠️ Current launcher icon is still the
      Android Studio default — must be replaced before upload.
- [x] **Feature graphic** — 1024×500 PNG/JPEG. See `docs/play-assets/feature-graphic.png`.
- [ ] **Phone screenshots** — 2–8, 16:9 or 9:16, min 320px, max 3840px. You must
      capture these from the running app (Dashboard, Stats, Limits, Streaks, blocking
      scrim are good choices).

---

## Data safety form — quick answers

**v1 ships with cloud sync / Google Sign-In hidden (`CLOUD_SYNC_ENABLED = false`), so
the app collects and transmits NO data — everything stays on-device.**

- Does the app collect or share any user data? **No**
- Data shared with third parties? **No**
- Data collected? **No** (reel counts, limits, streaks are stored only in the local
  Room DB; screen-time is read live from the system and never stored or sent)

(When cloud sync is re-enabled in a future version with Firebase, revisit this: it
would then collect app activity (numeric daily aggregates) + account info (name/email)
for the optional, off-by-default backup, encrypted in transit, user-deletable.)

---

## Content rating

Complete the IARC questionnaire — LoopOut has no objectionable content; expected
rating: Everyone / PEGI 3.

---

## Privacy policy URL

Privacy policy is hosted at: **https://mehdiaasar.github.io/loopOut/**
(source: `docs/privacy.html`, served from the `mehdiaasar/loopOut` GitHub repo via Pages).
Paste this URL into the Play Console store listing and the accessibility declaration form.
