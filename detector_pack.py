#!/usr/bin/env python3
"""Builds the look of the Metal Detector (Creator plugin) in resourcepack-extra/.

Takes the 32 compass images and items/compass.json from the Minecraft client (version below), colours the frame
purple like obsidian and the needle diamond blue, and writes the models as supermann:metal_detector, hooked into
minecraft:items/compass.json (custom_model_data "metal_detector", otherwise a normal compass).
heads.py includes everything in resourcepack-extra/ when it builds the resource pack, so run afterwards:
    python3 detector_pack.py && python3 heads.py && ./mc.sh restart
"""
import json
import os
import zipfile

import heads

MC = os.path.dirname(os.path.abspath(__file__))
VERSION = "26.3"
EXTRA = os.path.join(MC, "resourcepack-extra", "assets", "supermann")

# Vanilla compass palette -> obsidian frame, dark violet dial, diamond needle
COLORS = {
    (255, 255, 255): (214, 190, 255),   # highlight
    (216, 216, 216): (150, 112, 214),
    (168, 168, 168): (110, 74, 170),
    (130, 130, 130): (78, 50, 128),
    (100, 100, 100): (62, 38, 104),
    (94, 94, 94): (54, 34, 92),
    (53, 53, 53): (34, 22, 50),         # dial
    (47, 47, 47): (28, 18, 42),
    (24, 23, 23): (14, 9, 22),          # outline
    (79, 77, 77): (124, 100, 158),      # back half of the needle and a frame edge
    (255, 20, 20): (80, 236, 216),      # needle tip (diamond)
    (190, 21, 21): (38, 170, 158),
}


def recolor(px):
    out = []
    for row in px:
        r2 = []
        for p in row:
            rgb = tuple(p[:3])
            r2.append(tuple(COLORS.get(rgb, rgb)) + (p[3],) if p[3] else tuple(p))
        out.append(r2)
    return out


def main():
    z = zipfile.ZipFile(heads.client_jar(VERSION))
    os.makedirs(os.path.join(EXTRA, "textures", "item"), exist_ok=True)
    os.makedirs(os.path.join(EXTRA, "models", "item"), exist_ok=True)
    os.makedirs(os.path.join(EXTRA, "items"), exist_ok=True)
    for i in range(32):
        n = f"{i:02d}"
        px = heads.png_read(z.read(f"assets/minecraft/textures/item/compass_{n}.png"))
        open(os.path.join(EXTRA, "textures", "item", f"metal_detector_{n}.png"), "wb").write(heads.png_bytes(recolor(px)))
        json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": f"supermann:item/metal_detector_{n}"}},
                  open(os.path.join(EXTRA, "models", "item", f"metal_detector_{n}.json"), "w"))
    model = z.read("assets/minecraft/items/compass.json").decode().replace("minecraft:item/compass_", "supermann:item/metal_detector_")
    open(os.path.join(EXTRA, "items", "metal_detector.json"), "w").write(model)
    # The detector is a vanilla compass with custom_model_data "metal_detector": players without the pack see a compass.
    vanilla = json.loads(z.read("assets/minecraft/items/compass.json"))
    ours = json.loads(model)
    mc_items = os.path.join(MC, "resourcepack-extra", "assets", "minecraft", "items")
    os.makedirs(mc_items, exist_ok=True)
    json.dump({"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                         "cases": [{"when": "metal_detector", "model": ours["model"]}], "fallback": vanilla["model"]}},
              open(os.path.join(mc_items, "compass.json"), "w"))
    print(f"Wrote 32 images and models to {EXTRA}. Run python3 heads.py && ./mc.sh restart.")


if __name__ == "__main__":
    main()
