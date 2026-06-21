#!/usr/bin/env python3
"""Convert design SVGs + art PNGs into clean figure PNGs for the code-explained PDF."""
import os, subprocess, sys
from PIL import Image

ROOT = "/Users/aasarmehdi/claude/brainrot-tracker"
FIG = os.path.join(ROOT, "docs/code-explained/figures")
os.makedirs(FIG, exist_ok=True)


def svg_to_png(src, out, width=900):
    src = os.path.join(ROOT, src)
    out = os.path.join(FIG, out)
    try:
        subprocess.run(["rsvg-convert", "-u", "-w", str(width), src, "-o", out],
                       check=True, capture_output=True)
        # re-save optimized
        im = Image.open(out).convert("RGBA")
        bg = Image.new("RGBA", im.size, (255, 255, 255, 255))
        bg.alpha_composite(im)
        bg.convert("RGB").save(out, "PNG", optimize=True)
        print("OK svg ", out, im.size)
        return True
    except Exception as e:
        print("FAIL svg", src, "->", e)
        return False


def png_resize(src, out, max_w=1000):
    src = os.path.join(ROOT, src)
    out = os.path.join(FIG, out)
    try:
        im = Image.open(src).convert("RGB")
        if im.width > max_w:
            h = int(im.height * max_w / im.width)
            im = im.resize((max_w, h), Image.LANCZOS)
        im.save(out, "PNG", optimize=True)
        print("OK png ", out, im.size)
        return True
    except Exception as e:
        print("FAIL png", src, "->", e)
        return False


def montage(srcs, out, target_h=260, pad=24, bg=(255, 255, 255)):
    """Horizontal montage of images (svg or png paths relative to ROOT)."""
    imgs = []
    for sp in srcs:
        full = os.path.join(ROOT, sp)
        try:
            if sp.lower().endswith(".svg"):
                tmp = os.path.join(FIG, "_tmp_montage.png")
                subprocess.run(["rsvg-convert", "-u", "-h", str(target_h * 2), full, "-o", tmp],
                               check=True, capture_output=True)
                im = Image.open(tmp).convert("RGBA")
            else:
                im = Image.open(full).convert("RGBA")
            r = target_h / im.height
            im = im.resize((max(1, int(im.width * r)), target_h), Image.LANCZOS)
            imgs.append(im)
        except Exception as e:
            print("  montage skip", sp, e)
    if not imgs:
        print("FAIL montage", out, "no images")
        return False
    W = sum(i.width for i in imgs) + pad * (len(imgs) + 1)
    H = target_h + pad * 2
    canvas = Image.new("RGBA", (W, H), bg + (255,))
    x = pad
    for im in imgs:
        canvas.alpha_composite(im, (x, pad))
        x += im.width + pad
    o = os.path.join(FIG, out)
    canvas.convert("RGB").save(o, "PNG", optimize=True)
    print("OK montage", o, canvas.size)
    tmp = os.path.join(FIG, "_tmp_montage.png")
    if os.path.exists(tmp):
        os.remove(tmp)
    return True


# --- Individual UI screen figures (from design SVGs) ---
svg_to_png("dashboard/dashboard variation 1/main top variant.svg", "dash_hero.png", 820)
svg_to_png("dashboard/dashboard variation 1/insight variant.svg", "dash_insight.png", 820)
svg_to_png("dashboard/dashboard variation 3/NEAR LIMIT You're reaching your limit. It's okay to slow down and take care of yourself..svg", "dash_nearlimit.png", 820)
svg_to_png("dashboard/dashboard variation 2/reminder variant.svg", "dash_reminder.png", 820)
svg_to_png("setting/app blocking.svg", "set_blocking.png", 820)
svg_to_png("setting/appearence.svg", "set_appearance.png", 820)
svg_to_png("setting/sign in with google.svg", "set_signin.png", 820)
svg_to_png("setting/balance today , better tomaraow.svg", "set_balance.png", 820)
svg_to_png("streak & progress/2.svg", "streak_overview.png", 820)
svg_to_png("streak & progress/focus tip.svg", "streak_focustip.png", 820)
svg_to_png("streak & progress/top main bar/day 7-30.svg", "streak_topbar.png", 820)

# --- Montages (infographic-style strips) ---
montage([
    "streak & progress/your journey/day 1.svg",
    "streak & progress/your journey/day 7.svg",
    "streak & progress/your journey/day 30.svg",
    "streak & progress/your journey/day 100.svg",
    "streak & progress/your journey/day 365.svg",
], "streak_journey_strip.png", target_h=420)

montage([
    "streak & progress/achievements/first step.svg",
    "streak & progress/achievements/warrior.svg",
    "streak & progress/achievements/champion.svg",
    "streak & progress/achievements/zen master.svg",
    "streak & progress/achievements/legend.svg",
], "achievements_strip.png", target_h=300)

# --- Art (mascot / brain) — pick representative PNGs, resized ---
png_resize("docs/play-assets/loopout-dashboard-ui-generated.png", "dashboard_render.png", 760)
png_resize("icon.png", "app_icon.png", 360)

print("\nfigures dir listing:")
for f in sorted(os.listdir(FIG)):
    if f.endswith(".png"):
        p = os.path.join(FIG, f)
        print(f"  {f:32s} {os.path.getsize(p)//1024} KB")
