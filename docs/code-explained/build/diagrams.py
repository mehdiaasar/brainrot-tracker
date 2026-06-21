#!/usr/bin/env python3
"""Render Graphviz architecture/flow diagrams for the code-explained PDF, themed to the app palette."""
import os, subprocess

ROOT = "/Users/aasarmehdi/claude/brainrot-tracker"
OUT = os.path.join(ROOT, "docs/code-explained/diagrams")
os.makedirs(OUT, exist_ok=True)

# Warm palette matching LoopOut
BG = "#FFF8F2"
CARD = "#FFFFFF"
BORDER = "#E7D3C1"
TEXT = "#3D2A1F"
ORANGE = "#EA580C"
ORANGE_L = "#FFE9D8"
GREEN = "#15803D"
GREEN_L = "#DCFCE7"
BLUE = "#2563EB"
BLUE_L = "#DBEAFE"
PURPLE = "#7C3AED"
PURPLE_L = "#EDE4FF"
RED = "#DC2626"
RED_L = "#FEE2E2"
FONT = "Helvetica"

HEAD = f'''digraph G {{
  bgcolor="{BG}";
  rankdir=%s;
  fontname="{FONT}";
  node [shape=box style="rounded,filled" fontname="{FONT}" fontsize=12 color="{BORDER}" fillcolor="{CARD}" fontcolor="{TEXT}" margin="0.18,0.10" penwidth=1.4];
  edge [color="#B08968" fontname="{FONT}" fontsize=10 fontcolor="#7A5A44" penwidth=1.3 arrowsize=0.8];
'''


def box(name, label, fill=CARD, fc=TEXT, shape="box"):
    return f'  "{name}" [label="{label}" fillcolor="{fill}" fontcolor="{fc}" shape={shape}];\n'


def render(name, dot, rankdir="TB"):
    src = (HEAD % rankdir) + dot + "}\n"
    dotfile = os.path.join(OUT, name + ".dot")
    png = os.path.join(OUT, name + ".png")
    with open(dotfile, "w") as f:
        f.write(src)
    subprocess.run(["dot", "-Tpng", "-Gdpi=150", dotfile, "-o", png], check=True,
                   capture_output=True)
    print("OK", png)


# 1) Architecture layers
arch = ""
arch += '  subgraph cluster_ui { label="UI layer  (Jetpack Compose)"; style="rounded,filled"; fillcolor="%s"; fontcolor="%s"; color="%s"; fontname="%s";\n' % (ORANGE_L, ORANGE, BORDER, FONT)
arch += box("screens", "Screens\\nDashboard · Stats · Limits\\nStreaks · Onboarding · SignIn")
arch += box("comps", "Reusable components\\nAppCard · AnimatedCounter\\nMoodCharacter · PlatformLogo")
arch += "  }\n"
arch += '  subgraph cluster_vm { label="ViewModel layer  (MVVM state holders)"; style="rounded,filled"; fillcolor="%s"; fontcolor="%s"; color="%s"; fontname="%s";\n' % (BLUE_L, BLUE, BORDER, FONT)
arch += box("vms", "Dashboard / Stats / Streaks /\\nLimits / GoogleSignIn ViewModels\\n(expose StateFlow)")
arch += "  }\n"
arch += box("repo", "UsageRepository\\n(single source of truth)", PURPLE_L, PURPLE)
arch += '  subgraph cluster_data { label="Data layer"; style="rounded,filled"; fillcolor="%s"; fontcolor="%s"; color="%s"; fontname="%s";\n' % (GREEN_L, GREEN, BORDER, FONT)
arch += box("room", "Room database\\nDailyLog · UserLimits · StreakRecord", shape="cylinder")
arch += box("prefs", "Prefs (SharedPreferences)\\n+ AppPreferences (DataStore)")
arch += box("sync", "UsageSyncManager\\n→ Firebase Firestore (optional)")
arch += "  }\n"
arch += '  subgraph cluster_sys { label="System-driven services & helpers"; style="rounded,filled"; fillcolor="#F3ECE4"; fontcolor="#6B4A33"; color="%s"; fontname="%s";\n' % (BORDER, FONT)
arch += box("reel", "ReelCounterService\\n(AccessibilityService)", "#FFF1E6", ORANGE)
arch += box("float", "FloatingCounterService\\n(overlay + blocking scrim)", "#FFF1E6", ORANGE)
arch += box("screentime", "ScreenTimeHelper\\n(UsageStatsManager)")
arch += box("widget", "BrainRotWidget\\n(Glance)")
arch += "  }\n"
arch += '  screens -> vms [dir=both label="state / events"];\n'
arch += "  comps -> screens [style=dotted arrowhead=none];\n"
arch += "  vms -> repo;\n  repo -> room;\n  repo -> sync;\n  vms -> prefs;\n"
arch += "  reel -> repo [label=\"increment reels\"];\n  reel -> float [label=\"start / block\"];\n"
arch += "  screentime -> vms [label=\"live screen time\"];\n  repo -> widget [style=dashed];\n"
render("arch_layers", arch, "TB")

# 2) Reel-detection pipeline
reel = ""
reel += box("evt", "Accessibility event\\n(CONTENT_CHANGED · VIEW_SCROLLED ·\\nWINDOW_STATE_CHANGED)", ORANGE_L, ORANGE)
reel += box("gate", "Rate-limit gate\\n(≥ 0.8s since last tree scan?)")
reel += box("bfs", "BFS scan of view hierarchy\\n(AccessibilityNodeInfo)")
reel += box("detect", "Per-platform detector\\nIG/Snap: full-screen pager\\nYT: signature · TikTok: scroll dir")
reel += box("debounce", "Debounce\\n(ignore repeats within 1.2s)", GREEN_L, GREEN)
reel += box("inc", "repository.incrementReelCount(platform)", PURPLE_L, PURPLE)
reel += box("db", "DailyLog row updated", CARD, TEXT, "cylinder")
reel += box("ui", "UI Flows update\\n(Dashboard · widget)", BLUE_L, BLUE)
reel += box("milestone", "Milestone check\\n(25/50/.../200 → notification)")
reel += box("block", "evaluateBlocking()", RED_L, RED)
reel += "  evt -> gate -> bfs -> detect -> debounce -> inc -> db;\n"
reel += "  db -> ui;\n  db -> milestone;\n  inc -> block [label=\"every counted reel\"];\n"
reel += '  detect -> debounce [label="reel confirmed"];\n'
render("reel_pipeline", reel, "TB")

# 3) Blocking modes decision
blk = ""
blk += box("trigger", "Trigger:\\ncounted reel · 1s heartbeat ·\\napp reopened (WINDOW_STATE_CHANGED)", ORANGE_L, ORANGE)
blk += box("eval", "evaluateBlocking()")
blk += box("over", "Total reels ≥ limit?\\n(fallback limit = 30)", shape="diamond")
blk += box("ok", "Do nothing", GREEN_L, GREEN)
blk += box("mode", "Which blocking_mode?", shape="diamond")
blk += box("hard", "HARD\\nFull scrim every time app is\\nforegrounded until midnight.\\nOnly 'Close app'.", RED_L, RED)
blk += box("snooze", "SNOOZE\\n5-min grace stored as\\nsnooze_until_<PLATFORM>.\\nRe-block when it expires.", "#FEF3C7", "#B45309")
blk += box("remind", "REMIND\\nScrim once per foreground\\nsession (sessionDismissed),\\ncleared by onTrackedAppLeft.", BLUE_L, BLUE)
blk += "  trigger -> eval -> over;\n"
blk += '  over -> ok [label="no"];\n  over -> mode [label="yes"];\n'
blk += "  mode -> hard;\n  mode -> snooze;\n  mode -> remind;\n"
render("blocking_states", blk, "TB")

# 4) Navigation graph
nav = ""
nav += box("onb", "Onboarding\\n(permissions + prominent\\ndisclosure dialog)", ORANGE_L, ORANGE)
nav += box("signin", "GoogleSignIn\\n(optional, skippable)", BLUE_L, BLUE)
nav += box("dash", "Dashboard", PURPLE_L, PURPLE)
nav += box("stats", "Stats")
nav += box("streaks", "Streaks")
nav += box("limits", "Limits\\n(+ Account card)")
nav += '  onb -> signin [label="finish setup"];\n'
nav += '  signin -> dash [label="sign in / skip"];\n'
nav += '  onb -> dash [label="already enabled" style=dashed];\n'
nav += '  dash -> stats [dir=both]; dash -> streaks [dir=both]; dash -> limits [dir=both];\n'
nav += '  stats -> streaks [dir=both style=dotted]; streaks -> limits [dir=both style=dotted];\n'
nav += '  limits -> signin [label="Account card" style=dashed constraint=false];\n'
render("nav_graph", nav, "LR")

# 5) Day rollover
roll = ""
roll += box("tick", "Accessibility event fires\\ncheckDayRollover()", ORANGE_L, ORANGE)
roll += box("new", "Date changed since\\nlast counted day?", shape="diamond")
roll += box("nochange", "Continue", GREEN_L, GREEN)
roll += box("reset", "Reset in-memory\\nper-platform counts")
roll += box("streak", "evaluateStreaksUpTo(yesterday)\\n→ back-fill StreakRecord rows", PURPLE_L, PURPLE)
roll += box("summary", "Show daily-summary notification\\n(only if gap is exactly 1 day)")
roll += box("rearm", "Re-arm milestone notifications")
roll += box("sync2", "Kick optional cloud sync", BLUE_L, BLUE)
roll += "  tick -> new;\n"
roll += '  new -> nochange [label="no"];\n  new -> reset [label="yes (midnight)"];\n'
roll += "  reset -> streak -> summary -> rearm -> sync2;\n"
render("day_rollover", roll, "TB")

# 6) End-to-end story
e2e = ""
e2e += box("swipe", "User swipes a Reel\\nin Instagram", ORANGE_L, ORANGE)
e2e += box("acc", "ReelCounterService\\nreceives the event")
e2e += box("conf", "Hierarchy scan + debounce\\nconfirm one reel")
e2e += box("repo2", "UsageRepository\\nincrements today's count", PURPLE_L, PURPLE)
e2e += box("write", "Room writes DailyLog", CARD, TEXT, "cylinder")
e2e += box("fan", "Flows emit new value", GREEN_L, GREEN)
e2e += box("see", "Dashboard counter,\\nwidget, mood brain update", BLUE_L, BLUE)
e2e += box("guard", "evaluateBlocking +\\nmilestone check", RED_L, RED)
e2e += box("mid", "At midnight: streaks +\\nsummary + cloud sync")
e2e += "  swipe -> acc -> conf -> repo2 -> write -> fan -> see;\n"
e2e += "  repo2 -> guard [constraint=false];\n  fan -> mid [style=dashed];\n"
render("data_flow_e2e", e2e, "LR")

print("\ndiagrams:")
for f in sorted(os.listdir(OUT)):
    if f.endswith(".png"):
        print("  ", f, os.path.getsize(os.path.join(OUT, f)) // 1024, "KB")
