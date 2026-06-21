#!/usr/bin/env python3
"""Assemble the chapter markdown + figures + diagrams into the final styled PDF."""
import os, re, glob, sys
import markdown
from pygments.formatters import HtmlFormatter
from weasyprint import HTML

BASE = "/Users/aasarmehdi/claude/brainrot-tracker/docs/code-explained"
CHAPTERS = os.path.join(BASE, "chapters")
BUILD = os.path.join(BASE, "build")
OUT_PDF = "/Users/aasarmehdi/claude/brainrot-tracker/LoopOut_Code_Explained.pdf"

# ---- Display titles (keyed by file id) ----
TITLES = {
    "product-overview": "What LoopOut Is and What Problem It Solves",
    "android-anatomy": "Android App Anatomy: The Building Blocks",
    "compose-mvvm": "Jetpack Compose and MVVM: How the UI Works",
    "build-structure": "Project Structure and the Build System",
    "entry-navigation": "App Entry Point and Navigation",
    "room-database": "The Local Database: Room, Entities, and DAOs",
    "repository": "The Repository: The Single Source of Truth",
    "preferences": "Preferences: DataStore and SharedPreferences",
    "screen-time": "Measuring Screen Time with UsageStatsManager",
    "reel-detection": "The Heart of the App: Detecting Reels",
    "blocking-overlay": "App Blocking and the Floating Overlay",
    "notifications": "Notifications",
    "widget": "The Home-Screen Widget (Jetpack Glance)",
    "cloud-sync-signin": "Optional Cloud Sync and Google Sign-In",
    "theming": "Theming and the Design System",
    "screen-dashboard": "Screen Walkthrough: The Dashboard",
    "screens-stats-streaks-limits": "Screen Walkthroughs: Stats, Streaks, and Limits",
    "onboarding-permissions": "Onboarding and the Permission Flow",
    "ui-components": "The Reusable UI Component Library",
    "accessibility-capabilities": "Deep Dive: What Android Accessibility Can Do",
    "permissions-privacy": "Permissions, Privacy, and Play Store Policy",
    "lifecycle-background": "Lifecycle and the Background Execution Model",
    "end-to-end-glossary": "How It All Fits Together, and a Glossary",
}

# ---- Parts: (kicker, title, description, first_order, last_order) ----
PARTS = [
    ("Part I", "Orientation", "What the app does, and the Android & Jetpack Compose ideas you need before reading a single line of code.", 1, 3),
    ("Part II", "Inside the Code", "A guided tour of every layer — from the build system and the database to the accessibility engine that powers reel detection.", 4, 15),
    ("Part III", "The Screens", "Walking through each screen the user touches, and the reusable building blocks they are made from.", 16, 19),
    ("Part IV", "Android Deep Dives", "The bigger Android topics this app leans on: accessibility, permissions and privacy, and how code is allowed to run in the background.", 20, 22),
    ("Part V", "Putting It All Together", "One reel's journey through the whole system end to end, plus a plain-language glossary of every term.", 23, 23),
]

# ---- Figure map: id -> list of {file, caption, kind, pos} ----
F = lambda p: os.path.join(BASE, p)
FIGMAP = {
    "product-overview": [
        {"file": "figures/dashboard_render.png", "caption": "The LoopOut dashboard — the home screen users see every day.", "kind": "screen", "pos": "top"},
        {"file": "figures/dash_hero.png", "caption": "The pink-brain mascot reflects your daily “brain health”.", "kind": "art", "pos": "end"},
    ],
    "android-anatomy": [
        {"file": "diagrams/arch_layers.png", "caption": "How LoopOut's parts fit together, layer by layer.", "kind": "wide", "pos": "top"},
    ],
    "compose-mvvm": [
        {"file": "figures/dash_insight.png", "caption": "Every screen is built from composable functions driven by ViewModel state.", "kind": "art", "pos": "top"},
    ],
    "entry-navigation": [
        {"file": "diagrams/nav_graph.png", "caption": "How the user moves between screens.", "kind": "wide", "pos": "top"},
    ],
    "repository": [
        {"file": "diagrams/data_flow_e2e.png", "caption": "The repository sits between the ViewModels and storage.", "kind": "wide", "pos": "top"},
    ],
    "reel-detection": [
        {"file": "diagrams/reel_pipeline.png", "caption": "From a swipe to a counted reel: the detection pipeline.", "kind": "wide", "pos": "top"},
        {"file": "diagrams/day_rollover.png", "caption": "What happens at midnight — the day-rollover routine.", "kind": "wide", "pos": "end"},
    ],
    "blocking-overlay": [
        {"file": "diagrams/blocking_states.png", "caption": "How the three blocking modes behave once you hit a limit.", "kind": "wide", "pos": "top"},
        {"file": "figures/set_blocking.png", "caption": "The mascot “shields” you when a tracked app is blocked.", "kind": "art", "pos": "end"},
    ],
    "cloud-sync-signin": [
        {"file": "figures/set_signin.png", "caption": "Signing in with Google is optional — the app works fully offline.", "kind": "art", "pos": "top"},
    ],
    "theming": [
        {"file": "figures/set_appearance.png", "caption": "Light, dark, and system themes through one central design system.", "kind": "art", "pos": "top"},
    ],
    "screen-dashboard": [
        {"file": "figures/dashboard_render.png", "caption": "The Dashboard — each piece is produced by the DashboardViewModel state.", "kind": "screen", "pos": "top"},
        {"file": "figures/dash_hero.png", "caption": "Happy mood: comfortably under your limits.", "kind": "art", "pos": "end"},
        {"file": "figures/dash_nearlimit.png", "caption": "Worried mood: approaching your daily limit.", "kind": "art", "pos": "end"},
    ],
    "screens-stats-streaks-limits": [
        {"file": "figures/streak_journey_strip.png", "caption": "The streak “journey” — the mascot grows as your streak grows.", "kind": "strip", "pos": "top"},
        {"file": "figures/achievements_strip.png", "caption": "Achievement badges unlocked along the way.", "kind": "strip", "pos": "end"},
        {"file": "figures/streak_topbar.png", "caption": "Streak hero art at the 7–30-day tier.", "kind": "art", "pos": "end"},
    ],
    "onboarding-permissions": [
        {"file": "figures/set_balance.png", "caption": "Onboarding sets the tone: balance today, better tomorrow.", "kind": "art", "pos": "top"},
    ],
    "ui-components": [
        {"file": "figures/dash_reminder.png", "caption": "MoodCharacter is one of several small reusable composables.", "kind": "art", "pos": "top"},
    ],
    "accessibility-capabilities": [
        {"file": "diagrams/reel_pipeline.png", "caption": "LoopOut uses only a small, read-only slice of the Accessibility API.", "kind": "wide", "pos": "end"},
    ],
    "end-to-end-glossary": [
        {"file": "diagrams/data_flow_e2e.png", "caption": "One swipe, traced end to end.", "kind": "wide", "pos": "top"},
    ],
}

md = markdown.Markdown(extensions=["extra", "codehilite", "sane_lists", "admonition", "attr_list"],
                       extension_configs={"codehilite": {"guess_lang": False, "noclasses": False}})


def figure_html(entry):
    path = F(entry["file"])
    if not os.path.exists(path):
        print("  WARN missing figure:", entry["file"])
        return ""
    kind = entry.get("kind", "wide")
    cap = entry.get("caption", "")
    return f'<figure class="fig-{kind}"><img src="file://{path}"><figcaption>{cap}</figcaption></figure>\n'


def convert_chapter(order, cid):
    fp = os.path.join(CHAPTERS, f"{order:02d}-{cid}.md")
    if not os.path.exists(fp):
        print("  WARN missing chapter file:", fp)
        return None
    text = open(fp, encoding="utf-8").read()
    # demote any stray H1 the writer may have added (we supply the chapter title)
    text = re.sub(r"^# (.+)$", r"## \1", text, flags=re.MULTILINE)
    md.reset()
    body = md.convert(text)
    # style callouts
    body = re.sub(r"<blockquote>\s*<p>\s*(?:\U0001F4A1|&#128161;)\s*",
                  '<blockquote class="callout concept"><p>', body)
    body = re.sub(r"<blockquote>\s*<p>\s*(?:⚠️|⚠|&#9888;)\s*",
                  '<blockquote class="callout gotcha"><p>', body)

    figs = FIGMAP.get(cid, [])
    top = "".join(figure_html(e) for e in figs if e.get("pos", "top") == "top")
    end = "".join(figure_html(e) for e in figs if e.get("pos", "top") == "end")
    title = TITLES.get(cid, cid)
    return (f'<section class="chapter" id="chap-{order}">'
            f'<p class="chapnum">Chapter {order}</p><h1>{title}</h1>\n'
            f'{top}{body}{end}</section>\n')


# ---- Build body ----
parts_by_first = {p[3]: p for p in PARTS}
pieces = []
for order in range(1, len(TITLES) + 1):
    cid = list(TITLES.keys())[order - 1]
    if order in parts_by_first:
        kicker, ptitle, pdesc, _, _ = parts_by_first[order]
        pieces.append(
            f'<section class="part"><div class="pkicker">{kicker}</div>'
            f'<h1>{ptitle}</h1><div class="pdesc">{pdesc}</div><div class="pline"></div></section>\n')
    ch = convert_chapter(order, cid)
    if ch:
        pieces.append(ch)

# ---- Table of contents ----
toc_rows = []
for kicker, ptitle, pdesc, lo, hi in PARTS:
    toc_rows.append(f'<li class="toc-part">{kicker} &middot; {ptitle}</li>')
    for order in range(lo, hi + 1):
        cid = list(TITLES.keys())[order - 1]
        toc_rows.append(
            f'<li class="toc-entry"><span class="toc-num">{order}</span>'
            f'<a href="#chap-{order}">{TITLES[cid]}</a></li>')
toc_html = '<section class="toc"><h1>Table of Contents</h1><ol>' + "".join(toc_rows) + "</ol></section>"

# ---- Cover & about ----
icon = F("figures/app_icon.png")
brain = F("figures/dash_hero.png")
cover = f'''<div class="cover"><div class="inner">
  <img class="icon" src="file://{icon}">
  <h1>LoopOut</h1>
  <div class="tag">The Code, Explained</div>
  <div class="sub">A beginner's complete walkthrough of the codebase —<br>every layer, every screen, and the Android features that make it work.</div>
</div>
  <div class="brainfig"><img src="file://{brain}"></div>
  <div class="meta">Generated walkthrough of the brainrot-tracker / LoopOut Android app &middot; Kotlin + Jetpack Compose</div>
</div>'''

about = f'''<section class="about"><h1>About This Book</h1>
<p class="lead">This book explains the entire LoopOut Android app — a digital-wellbeing app that counts the short-form “reels” you scroll and gently helps you cut back — to someone who is brand new to app development.</p>
<p>You do not need any prior Android knowledge. Every technical term is defined the first time it appears, ideas are introduced with analogies, and real snippets of the project's own code are shown and explained line by line. By the end you will understand not just <em>what</em> each file does, but <em>why</em> it is built the way it is.</p>
<div class="stats">
  <div class="stat"><span class="n">50+</span><span class="l">Kotlin source files</span></div>
  <div class="stat"><span class="n">~8.8k</span><span class="l">lines of code</span></div>
  <div class="stat"><span class="n">23</span><span class="l">chapters</span></div>
  <div class="stat"><span class="n">4</span><span class="l">platforms tracked</span></div>
</div>
<h3>How to read it</h3>
<p>The chapters build on each other, so reading front-to-back is ideal — but every chapter also stands on its own, and the table of contents and PDF bookmarks let you jump straight to any topic. If you are completely new, do not skip <strong>Part I</strong>: it teaches the Android and Jetpack Compose vocabulary that the rest of the book relies on.</p>
<h3>Two kinds of asides</h3>
<blockquote class="callout concept"><p><strong>Concept —</strong> a short explanation of a general programming or Android idea, so you never have to leave the page to look something up.</p></blockquote>
<blockquote class="callout gotcha"><p><strong>Gotcha —</strong> a subtle pitfall, edge case, or “why it's done this odd way” note that trips up newcomers.</p></blockquote>
<h3>About the images</h3>
<p>Screenshots show the app's real dashboard design. The expressive pink-brain artwork is the app's actual mascot, whose mood changes with your usage. The flow and architecture diagrams were drawn specifically for this book to match the code described in each chapter.</p>
</section>'''

# ---- Assemble ----
pyg_css = HtmlFormatter(style="friendly").get_style_defs(".codehilite")
book_css = open(os.path.join(BUILD, "book.css"), encoding="utf-8").read()

doc = f'''<!doctype html><html><head><meta charset="utf-8">
<style>{book_css}</style>
<style>{pyg_css}</style>
</head><body>
{cover}
{about}
{toc_html}
{''.join(pieces)}
</body></html>'''

html_path = os.path.join(BUILD, "book.html")
open(html_path, "w", encoding="utf-8").write(doc)
print("HTML written:", html_path, len(doc) // 1024, "KB")

HTML(string=doc, base_url=BASE).write_pdf(OUT_PDF)
print("PDF written:", OUT_PDF, os.path.getsize(OUT_PDF) // 1024, "KB")
