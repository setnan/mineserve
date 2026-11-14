#!/usr/bin/env python3
"""Builds the look of Glowing Coal (Creator plugin) in resourcepack-extra/.

Takes coal.png from the Minecraft client and turns the light specks into pulsing embers (8 frames).
The plugin sets custom_model_data "glowing_coal" on normal coal, so without the pack it looks like coal.
    python3 glowing_coal_pack.py && python3 heads.py && ./mc.sh restart
"""
import json
import math
import os
import zipfile

import heads

MC = os.path.dirname(os.path.abspath(__file__))
VERSION = "26.3"
EXTRA = os.path.join(MC, "resourcepack-extra", "assets", "supermann")
FRAMES = 8
EMBER = [(90, 10, 0), (200, 40, 0), (255, 110, 20), (255, 190, 60), (255, 240, 150)]


def lum(p):
    return 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]


def frame(px, f):
    ls = [lum(p) for row in px for p in row if p[3]]
    lo, hi = min(ls), max(ls)
    out = []
    for y, row in enumerate(px):
        r2 = []
        for x, p in enumerate(row):
            if not p[3]:
                r2.append(tuple(p))
                continue
            t = (lum(p) - lo) / max(1, hi - lo)
            if t < 0.35:                                      # dark coal stays, with a faint red tint
                r2.append((min(255, p[0] + 25), p[1], p[2], p[3]))
                continue
            wave = 0.5 + 0.5 * math.sin(2 * math.pi * f / FRAMES + (x * 0.9 + y * 0.6))
            k = min(len(EMBER) - 1, int((t - 0.35) / 0.65 * 3 + wave * 2))
            r2.append(EMBER[k] + (p[3],))
        out.append(r2)
    return out


def main():
    z = zipfile.ZipFile(heads.client_jar(VERSION))
    for d in ("textures/item", "models/item"):
        os.makedirs(os.path.join(EXTRA, d), exist_ok=True)
    px = heads.png_read(z.read("assets/minecraft/textures/item/coal.png"))
    tex = os.path.join(EXTRA, "textures", "item", "glowing_coal.png")
    open(tex, "wb").write(heads.png_bytes([row for f in range(FRAMES) for row in frame(px, f)]))
    json.dump({"animation": {"frametime": 3, "interpolate": True}}, open(tex + ".mcmeta", "w"))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "supermann:item/glowing_coal"}},
              open(os.path.join(EXTRA, "models", "item", "glowing_coal.json"), "w"))
    vanilla = json.loads(z.read("assets/minecraft/items/coal.json"))
    mc_items = os.path.join(MC, "resourcepack-extra", "assets", "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                         "cases": [{"when": "glowing_coal", "model": {"type": "minecraft:model", "model": "supermann:item/glowing_coal"}}],
                         "fallback": vanilla["model"]}}, open(os.path.join(mc_items, "coal.json"), "w"), indent=1)
    print("Wrote Glowing Coal to", EXTRA)


if __name__ == "__main__":
    main()
