#!/usr/bin/env python3
"""Builds the look of the super tools from the Big Bench (Creator plugin) in resourcepack-extra/.

Reads data/plugins/Creator/supers/*.json. If a tool has a "texture" (a 16x16 pixel image as text, drawn by Codex
through chatbro or by hand), it is used, animated if it has several frames. Otherwise the netherite tool is recoloured
in "color". Tools with "materials" (wood, stone … netherite) get one version per material that looks like the
normal tool of that material, only bigger. "size" makes the tool bigger in the hand. The plugin sets
custom_model_data "super_<id>" on normal tools, so without the pack they look like the normal tool. Run (chatbro does
it by itself for new tools):
    python3 super_pack.py && python3 heads.py && ./mc.sh cmd superreload
"""
import glob
import json
import os
import re
import zipfile

import heads

MC = os.path.dirname(os.path.abspath(__file__))
VERSION = "26.3"
EXTRA = os.path.join(MC, "resourcepack-extra", "assets", "supermann")
SUPERS = os.path.join(MC, "data", "plugins", "Creator", "supers")
KIND = {"sword": "sword", "axe": "axe", "pickaxe": "pickaxe", "shovel": "shovel", "hoe": "hoe"}
TIERS = {"wood": "wooden", "stone": "stone", "copper": "copper", "iron": "iron", "gold": "golden", "diamond": "diamond",
         "netherite": "netherite"}
HEX = re.compile(r"^#[0-9a-fA-F]{6}$")


def check_texture(t):
    """Returns (frames as pixel lists, frametime) or raises ValueError with a short reason."""
    if not isinstance(t, dict):
        raise ValueError("texture missing")
    pal = {}
    for p in t.get("palette") or []:
        ch, col = p.get("char", ""), p.get("color", "")
        if len(ch) != 1 or ch == "." or not HEX.match(col):
            raise ValueError(f"invalid palette colour {p}")
        pal[ch] = tuple(int(col[i:i + 2], 16) for i in (1, 3, 5)) + (255,)
    if not 2 <= len(pal) <= 24:
        raise ValueError("the palette needs 2–24 colours")
    frames = []
    for img in (t.get("frames") or [])[:8]:
        if len(img) != 16 or any(len(r) != 16 for r in img):
            raise ValueError("each frame must be 16 rows of 16 characters")
        px = []
        for r in img:
            row = []
            for ch in r:
                if ch == ".":
                    row.append((0, 0, 0, 0))
                elif ch in pal:
                    row.append(pal[ch])
                else:
                    raise ValueError(f"the character \"{ch}\" is not in the palette")
            px.append(row)
        if sum(1 for row in px for p in row if p[3]) < 20:
            raise ValueError("the frame is almost empty")
        frames.append(px)
    if not frames:
        raise ValueError("no frames")
    return frames, max(1, min(20, int(t.get("speed") or 3)))


# --- Recolouring when there is no drawing -----------------------------------------------------------

def is_handle(tool, x, y):
    if tool.endswith("sword"):
        return (x <= 4 and y >= 6) or (y >= 11 and x <= 9)
    return y >= 7 and 14 <= x + y <= (17 if y >= 8 else 16)


def lum(p):
    return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def mix(a, b, t):
    return tuple(max(0, min(255, int(a[i] + (b[i] - a[i]) * t))) for i in range(3))


def recolor(px, tool, colour):
    mid = tuple(int(colour[i:i + 2], 16) for i in (1, 3, 5)) if HEX.match(colour or "") else (200, 200, 200)
    dark, light = mix((0, 0, 0), mid, 0.4), mix(mid, (255, 255, 255), 0.7)
    head = [lum(p) for y, row in enumerate(px) for x, p in enumerate(row) if p[3] and not is_handle(tool, x, y)]
    lo, hi = min(head), max(head)
    out = []
    for y, row in enumerate(px):
        r2 = []
        for x, p in enumerate(row):
            if not p[3] or is_handle(tool, x, y):
                r2.append(tuple(p))
                continue
            t = (lum(p) - lo) / max(1, hi - lo)
            r2.append((mix(dark, mid, t * 2) if t < 0.5 else mix(mid, light, (t - 0.5) * 2)) + (p[3],))
        out.append(r2)
    return out


def display(k3):
    """Bigger in the hand, moved so the grip (lower left) stays in the hand. Starts from vanilla handheld."""
    k3 = max(1.0, min(2.0, float(k3 or 1)))
    k1 = round(min(1.3, 0.68 + (k3 - 0.85) * 0.6), 3)
    r3 = (0, -6.964, 1.228)     # the grip point (-5,-5) rotated by [0,-90,55]
    r1 = (0, -6.645, -2.418)    # ... and by [0,-90,25]
    t3 = [round(b + (0.85 - k3) * r, 3) for b, r in zip((0, 4, 0.5), r3)]
    t1 = [round(b + (0.68 - k1) * r, 3) for b, r in zip((1.13, 3.2, 1.13), r1)]
    return {
        "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": t3, "scale": [k3, k3, 0.85]},
        "firstperson_righthand": {"rotation": [0, -90, 25], "translation": t1, "scale": [k1, k1, 0.68]},
    }


def main():
    z = zipfile.ZipFile(heads.client_jar(VERSION))
    for d in ("textures/item", "models/item"):
        os.makedirs(os.path.join(EXTRA, d), exist_ok=True)
    for old in glob.glob(os.path.join(EXTRA, "textures", "item", "super_*")) + glob.glob(os.path.join(EXTRA, "models", "item", "super_*")):
        os.remove(old)
    cases = {f"{t}_{k}": [] for t in TIERS.values() for k in KIND.values()}
    n = drawn = 0
    for path in sorted(glob.glob(os.path.join(SUPERS, "*.json"))):
        try:
            s = json.load(open(path))
            fid, kind = s["id"], KIND[s["tool"]]
            tiers = [t for t in (s.get("materials") or []) if t in TIERS]
        except (ValueError, KeyError) as e:
            print(f"Skipping {os.path.basename(path)}: {e}")
            continue
        try:
            frames, speed = check_texture(s.get("texture"))
        except ValueError:
            frames = None
        variants = [(f"{fid}_{t}", f"{TIERS[t]}_{kind}") for t in tiers] if tiers else [(fid, f"netherite_{kind}")]
        for sid, tool in variants:
            tex = os.path.join(EXTRA, "textures", "item", f"super_{sid}.png")
            if frames:
                open(tex, "wb").write(heads.png_bytes([row for f in frames for row in f]))
                if len(frames) > 1:
                    json.dump({"animation": {"frametime": speed, "interpolate": False}}, open(tex + ".mcmeta", "w"))
                drawn += 1
            else:
                px = heads.png_read(z.read(f"assets/minecraft/textures/item/{tool}.png"))
                open(tex, "wb").write(heads.png_bytes(px if tiers else recolor(px, tool, s.get("color"))))
            json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"supermann:item/super_{sid}"},
                       "display": display(s.get("size"))},
                      open(os.path.join(EXTRA, "models", "item", f"super_{sid}.json"), "w"), indent=1)
            cases[tool].append({"when": f"super_{sid}", "model": {"type": "minecraft:model", "model": f"supermann:item/super_{sid}"}})
            n += 1
    mc_items = os.path.join(MC, "resourcepack-extra", "assets", "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    for tool, cs in cases.items():
        target = os.path.join(mc_items, f"{tool}.json")
        if not cs:
            if os.path.exists(target):
                os.remove(target)
            continue
        vanilla = json.loads(z.read(f"assets/minecraft/items/{tool}.json"))
        json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                             "cases": cs, "fallback": vanilla["model"]}}, open(target, "w"), indent=1)
    print(f"Wrote {n} super tools ({drawn} drawn, {n - drawn} recoloured) to {EXTRA}.")


if __name__ == "__main__":
    main()
