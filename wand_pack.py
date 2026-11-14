#!/usr/bin/env python3
"""Builds the look of the Magic Wand (Creator plugin) in resourcepack-extra/.

A blaze shaft with an ender eye on top. The tip pulses (animated texture) and changes colour with the spell
(the custom_model_data string is set by the plugin). heads.py includes resourcepack-extra/ in the resource pack:
    python3 wand_pack.py && python3 heads.py && ./mc.sh restart
"""
import json
import math
import os

import heads

MC = os.path.dirname(os.path.abspath(__file__))
EXTRA = os.path.join(MC, "resourcepack-extra", "assets", "supermann")

# tip palettes: (mid, light, dark, spark)
PAL = {
    "green": ((40, 120, 60), (120, 230, 140), (20, 70, 30), (255, 170, 60)),
    "pink": ((230, 90, 170), (255, 190, 230), (150, 40, 110), (255, 220, 240)),
    "red": ((200, 30, 30), (255, 120, 100), (110, 10, 10), (255, 80, 40)),
    "ice": ((90, 190, 255), (220, 245, 255), (40, 110, 200), (230, 250, 255)),
    "yellow": ((240, 200, 40), (255, 250, 170), (170, 120, 10), (255, 255, 210)),
    "purple": ((150, 80, 220), (220, 180, 255), (90, 40, 150), (230, 200, 255)),
    "teal": ((40, 200, 180), (180, 255, 240), (20, 120, 110), (200, 255, 250)),
    "gold": ((230, 160, 30), (255, 230, 140), (150, 90, 10), (255, 240, 180)),
    "white": ((210, 210, 225), (255, 255, 255), (130, 130, 150), (255, 255, 255)),
    "blue": ((40, 80, 220), (130, 170, 255), (20, 40, 130), (160, 200, 255)),
    "fire": ((255, 110, 20), (255, 220, 90), (170, 40, 10), (255, 200, 60)),
}
MODE = {
    "floor": "green", "wall": "green", "sow": "green",
    "friendly": "pink", "hostile": "red", "outfit": "green", "variant": "pink", "gold": "gold", "zombify": "green", "tame": "pink", "ally": "gold", "pet": "pink", "ride": "teal", "freeze": "ice", "light": "yellow", "day": "yellow",
    "big": "purple", "small": "purple", "invisible": "purple",
    "blink": "teal", "lift": "teal", "fetch": "teal",
    "disarm": "gold", "shield": "gold", "guardian": "gold",
    "duplicate": "ice", "radar": "pink", "reveal": "pink", "open": "white", "repair": "white",
    "water": "blue", "fire": "fire",
}
T = (0, 0, 0, 0)
SHAFT_D, SHAFT_L, BAND = (200, 120, 20), (255, 210, 70), (70, 40, 20)
SPARKS = [(15, 0), (10, 1), (14, 5), (15, 3), (11, 0), (9, 3)]
FRAMES = 8


def scale(c, k):
    return tuple(max(0, min(255, int(v * k))) for v in c)


def frame(pal, f):
    mid, light, dark, spark = pal
    k = 0.85 + 0.25 * math.sin(f / FRAMES * 2 * math.pi)   # pulse
    px = [[T] * 16 for _ in range(16)]

    def put(x, y, c):
        if 0 <= x < 16 and 0 <= y < 16:
            px[y][x] = tuple(c) + (255,)
    for i in range(9):
        x, y = 2 + i, 13 - i
        put(x, y, SHAFT_D); put(x + 1, y, SHAFT_L)
    put(2, 14, SHAFT_D); put(1, 14, SHAFT_D); put(2, 13, SHAFT_D)
    for x, y in [(9, 6), (10, 6), (9, 5), (3, 12), (4, 12), (3, 11)]:
        put(x, y, BAND)
    for x, y in [(11, 4), (12, 3), (13, 2), (12, 4), (11, 3), (13, 3), (12, 2), (13, 1)]:
        put(x, y, scale(mid, k))
    for x, y in [(12, 2), (13, 2), (14, 1)]:
        put(x, y, scale(light, k))
    for x, y in [(11, 4), (12, 4)]:
        put(x, y, scale(dark, k))
    for i in range(3):                                       # three sparks that move around
        sx, sy = SPARKS[(f + i * 2) % len(SPARKS)]
        put(sx, sy, scale(spark, 0.7 + 0.3 * ((f + i) % 2)))
    return px


def main():
    for d in ("textures/item", "models/item", "items"):
        os.makedirs(os.path.join(EXTRA, d), exist_ok=True)
    for name, pal in PAL.items():
        a = name
        rows = []
        for f in range(FRAMES):
            rows += frame(pal, f)
        tex = os.path.join(EXTRA, "textures", "item", f"magic_wand_{a}.png")
        open(tex, "wb").write(heads.png_bytes(rows))
        json.dump({"animation": {"frametime": 3, "interpolate": True}}, open(tex + ".mcmeta", "w"))
        json.dump({"parent": "minecraft:item/handheld", "textures": {"layer0": f"supermann:item/magic_wand_{a}"}},
                  open(os.path.join(EXTRA, "models", "item", f"magic_wand_{a}.json"), "w"))

    def ref(pal):
        return {"type": "minecraft:model", "model": "supermann:item/magic_wand_" + pal}
    # The wand is a vanilla blaze rod with custom_model_data ["magic_wand", <spell>]: players without the pack see a blaze rod.
    wand = {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 1,
            "cases": [{"when": m, "model": ref(p)} for m, p in MODE.items()], "fallback": ref("green")}
    item = {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                      "cases": [{"when": "magic_wand", "model": wand}],
                      "fallback": {"type": "minecraft:model", "model": "minecraft:item/blaze_rod"}}}
    mc_items = os.path.join(MC, "resourcepack-extra", "assets", "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    json.dump(item, open(os.path.join(mc_items, "blaze_rod.json"), "w"), indent=1)
    old = os.path.join(EXTRA, "items", "magic_wand.json")
    if os.path.exists(old):
        os.remove(old)
    print(f"Wrote {len(PAL)} animated wands to {EXTRA}.")


if __name__ == "__main__":
    main()
