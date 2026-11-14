#!/usr/bin/env python3
"""Builds the look of the Golden Chicken and its items (Creator plugin) in resourcepack-extra/.

The chicken is the chicken variant supermann:golden (data/world/datapacks/supermann/data/supermann/chicken_variant/golden.json),
with the white feathers of chicken_temperate coloured gold. The golden egg, golden feather and golden food are normal
items with custom_model_data "golden_egg", "golden_feather", "golden_chicken_raw", "golden_chicken_cooked" and "golden_soup".
    python3 golden_chicken_pack.py && python3 heads.py && ./mc.sh restart
"""
import json
import os
import zipfile

import heads

MC = os.path.dirname(os.path.abspath(__file__))
VERSION = "26.3"
ASSETS = os.path.join(MC, "resourcepack-extra", "assets")
GOLD = [(120, 70, 0), (190, 120, 10), (235, 175, 30), (255, 215, 70), (255, 245, 160)]


def gold(px, only_light):
    """Light, unsaturated pixels (white feathers, egg shell) become gold shades; the rest stays (beak, comb, eyes)."""
    out = []
    for row in px:
        r2 = []
        for p in row:
            if not p[3]:
                r2.append(tuple(p))
                continue
            mx, mn = max(p[:3]), min(p[:3])
            grey = mx - mn < 40
            if not only_light:                       # the egg: every pixel by brightness
                l = 0.3 * p[0] + 0.59 * p[1] + 0.11 * p[2]
                r2.append(GOLD[min(len(GOLD) - 1, int(l / 256 * len(GOLD) * 1.15))] + (p[3],))
            elif grey and mx > 120:
                k = min(len(GOLD) - 1, max(0, int((mx - 90) / 166 * len(GOLD))))
                r2.append(GOLD[k] + (p[3],))
            else:
                r2.append(tuple(p))
        out.append(r2)
    return out


def main():
    z = zipfile.ZipFile(heads.client_jar(VERSION))
    ent = os.path.join(ASSETS, "supermann", "textures", "entity", "chicken")
    os.makedirs(ent, exist_ok=True)
    for src, dst in (("chicken_temperate", "golden"), ("chicken_temperate_baby", "golden_baby")):
        px = heads.png_read(z.read(f"assets/minecraft/textures/entity/chicken/{src}.png"))
        open(os.path.join(ent, f"{dst}.png"), "wb").write(heads.png_bytes(gold(px, True)))
    for d in ("textures/item", "models/item"):
        os.makedirs(os.path.join(ASSETS, "supermann", d), exist_ok=True)
    px = heads.png_read(z.read("assets/minecraft/textures/item/egg.png"))
    open(os.path.join(ASSETS, "supermann", "textures", "item", "golden_egg.png"), "wb").write(heads.png_bytes(gold(px, False)))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "supermann:item/golden_egg"}},
              open(os.path.join(ASSETS, "supermann", "models", "item", "golden_egg.json"), "w"))
    px = heads.png_read(z.read("assets/minecraft/textures/item/feather.png"))
    open(os.path.join(ASSETS, "supermann", "textures", "item", "golden_feather.png"), "wb").write(heads.png_bytes(gold(px, False)))
    json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "supermann:item/golden_feather"}},
              open(os.path.join(ASSETS, "supermann", "models", "item", "golden_feather.json"), "w"))
    # Food: raw and cooked golden chicken all gold; the soup in the stew bowl gold, the bowl as it is.
    for src, dst in (("chicken", "golden_chicken_raw"), ("cooked_chicken", "golden_chicken_cooked"), ("mushroom_stew", "golden_soup")):
        px = heads.png_read(z.read(f"assets/minecraft/textures/item/{src}.png"))
        if src == "mushroom_stew":                   # the soup is the inside of the bowl's opening (rows 6-8)
            out = []
            for y, row in enumerate(px):
                xs = [x for x, q in enumerate(row) if q[3]]
                inner = range(xs[0] + 1, xs[-1]) if xs and 6 <= y <= 8 else range(0)
                out.append([gold([[q]], False)[0][0] if x in inner else q for x, q in enumerate(row)])
            px = out
        elif src == "chicken":                       # raw: pale gold
            px = [[tuple(min(255, int(c * 0.6 + 255 * 0.4)) for c in q[:3]) + (q[3],) for q in row] for row in gold(px, False)]
        else:                                        # cooked: deep amber
            px = [[tuple(int(c * f) for c, f in zip(q[:3], (0.95, 0.72, 0.45))) + (q[3],) for q in row] for row in gold(px, False)]
        open(os.path.join(ASSETS, "supermann", "textures", "item", f"{dst}.png"), "wb").write(heads.png_bytes(px))
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"supermann:item/{dst}"}},
                  open(os.path.join(ASSETS, "supermann", "models", "item", f"{dst}.json"), "w"))
    mc_items = os.path.join(ASSETS, "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    for item, cmd in (("egg", "golden_egg"), ("feather", "golden_feather"), ("chicken", "golden_chicken_raw"), ("cooked_chicken", "golden_chicken_cooked"),
                      ("mushroom_stew", "golden_soup")):
        vanilla = json.loads(z.read(f"assets/minecraft/items/{item}.json"))
        json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                             "cases": [{"when": cmd, "model": {"type": "minecraft:model", "model": f"supermann:item/{cmd}"}}],
                             "fallback": vanilla["model"]}}, open(os.path.join(mc_items, f"{item}.json"), "w"), indent=1)
    print("Wrote the Golden Chicken and its items to", ASSETS)


if __name__ == "__main__":
    main()
