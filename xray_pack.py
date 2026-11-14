#!/usr/bin/env python3
"""Builds the look of the X-Ray Goggles (Creator plugin) in resourcepack-extra/.

The icon (paper with custom_model_data "xray_goggles"), the goggles on the head (equipment asset supermann:xray_goggles) and
the screen filter while worn (camera_overlay supermann:misc/xray: blue-tinted edge, clear middle).
    python3 xray_pack.py && python3 heads.py && ./mc.sh restart
"""
import json
import math
import os
import zipfile

import heads

MC = os.path.dirname(os.path.abspath(__file__))
VERSION = "26.3"
ASSETS = os.path.join(MC, "resourcepack-extra", "assets")
T = (0, 0, 0, 0)
FRAME, FRAME_L, STRAP = (40, 40, 48, 255), (90, 90, 104, 255), (60, 44, 30, 255)
LENS, LENS_L, LENS_D = (60, 200, 255, 255), (190, 240, 255, 255), (20, 110, 180, 255)


def icon():
    px = [[T] * 16 for _ in range(16)]

    def put(x, y, c):
        px[y][x] = c
    for cx in (4, 11):                                   # two round lenses with a frame
        for y in range(4, 11):
            for x in range(cx - 3, cx + 4):
                d = math.hypot(x - cx, y - 7)
                if d <= 3.4:
                    put(x, y, FRAME if d > 2.3 else (LENS_L if (x - cx, y - 7) in ((-1, -1), (-1, 0), (0, -1)) else LENS if d < 1.6 else LENS_D))
    for x in (7, 8):                                      # bridge
        put(x, 6, FRAME_L)
    for x, y in ((0, 6), (0, 7), (15, 6), (15, 7)):        # straps
        put(x, y, STRAP)
    return px


def worn():
    """64x32 armour layout: the head is the 32x16 block top left, the face is x 8-15, y 8-15."""
    px = [[T] * 64 for _ in range(32)]
    for x in range(0, 32):                                # strap all the way round at eye height
        px[11][x] = STRAP
    for x in range(8, 16):
        px[10][x] = FRAME
        px[12][x] = FRAME
        px[11][x] = FRAME
    for x in (9, 10, 13, 14):                              # lenses on the face
        px[11][x] = LENS
    px[11][9] = LENS_L
    px[11][13] = LENS_L
    return px


def overlay(w=256, h=256):
    px = []
    for y in range(h):
        row = []
        for x in range(w):
            d = math.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2))
            a = 30 if d < 0.6 else int(30 + min(1, (d - 0.6) / 0.6) * 150)
            row.append((30, 140, 255, a))
        px.append(row)
    return px


def main():
    z = zipfile.ZipFile(heads.client_jar(VERSION))
    sm = os.path.join(ASSETS, "supermann")
    for d in ("textures/item", "models/item", "textures/entity/equipment/humanoid", "textures/misc", "equipment"):
        os.makedirs(os.path.join(sm, d), exist_ok=True)
    open(os.path.join(sm, "textures", "item", "xray_goggles.png"), "wb").write(heads.png_bytes(icon()))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "supermann:item/xray_goggles"}},
              open(os.path.join(sm, "models", "item", "xray_goggles.json"), "w"))
    open(os.path.join(sm, "textures", "entity", "equipment", "humanoid", "xray_goggles.png"), "wb").write(heads.png_bytes(worn()))
    json.dump({"layers": {"humanoid": [{"texture": "supermann:xray_goggles"}]}}, open(os.path.join(sm, "equipment", "xray_goggles.json"), "w"))
    open(os.path.join(sm, "textures", "misc", "xray.png"), "wb").write(heads.png_bytes(overlay()))
    vanilla = json.loads(z.read("assets/minecraft/items/paper.json"))
    mc_items = os.path.join(ASSETS, "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                         "cases": [{"when": "xray_goggles", "model": {"type": "minecraft:model", "model": "supermann:item/xray_goggles"}}],
                         "fallback": vanilla["model"]}}, open(os.path.join(mc_items, "paper.json"), "w"), indent=1)
    print("Wrote the X-Ray Goggles to", sm)


if __name__ == "__main__":
    main()
