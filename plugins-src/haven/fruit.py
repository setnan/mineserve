"""
Draws the fruits of Haven as they grow: the glow fruit of the caves (small, round, light green, with a bright cheek,
darker freckles and a short stalk with a leaf on top) and the sea fruit of the sea floor (the same in turquoise). They
are skins of a player head (HavenItems.glowFruit sets a head block, HavenItems.seaFruit shows a head item under water).
The items in the inventory get the same fruit as a 16 x 16 icon (supermann:glow_fruit, supermann:sea_fruit).
Writes the skins, the icons and their models into resourcepack-extra; run ./mc.sh pack afterwards.

    python3 plugins-src/haven/fruit.py [preview-folder]
"""
import os, sys, random, json
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
ASSETS = os.path.join(ROOT, "resourcepack-extra", "assets", "supermann")
SKINS = os.path.join(ASSETS, "textures", "entity", "skins")

GLOW = dict(BODY=(176, 250, 96, 255), SHADE=(132, 222, 70, 255), DEEP=(92, 184, 56, 255), LIGHT=(214, 255, 150, 255),
            SHINE=(246, 255, 214, 255), STALK=(120, 84, 44, 255), LEAF=(56, 150, 60, 255))
SEA = dict(BODY=(64, 214, 224, 255), SHADE=(38, 172, 206, 255), DEEP=(28, 120, 176, 255), LIGHT=(140, 240, 236, 255),
           SHINE=(226, 255, 252, 255), STALK=(30, 84, 150, 255), LEAF=(50, 110, 210, 255))
# the faces of the head in the skin: top, bottom, right, front, left, back
FACES = {"top": (8, 0), "bottom": (16, 0), "right": (0, 8), "front": (8, 8), "left": (16, 8), "back": (24, 8)}


def fruit(c):
    BODY, SHADE, DEEP, LIGHT, SHINE, STALK, LEAF = (c[k] for k in ("BODY", "SHADE", "DEEP", "LIGHT", "SHINE", "STALK", "LEAF"))
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    rnd = random.Random(7)
    for name, (ox, oy) in FACES.items():
        for x in range(8):
            for y in range(8):
                edge = x in (0, 7) or y in (0, 7)
                c = BODY
                if name == "bottom": c = DEEP if edge else SHADE
                elif name == "top": c = SHADE if edge else LIGHT
                else: c = DEEP if y == 7 else SHADE if (y >= 5 or x in (0, 7)) else LIGHT if y <= 1 else BODY
                if not edge and rnd.random() < 0.10: c = SHADE if c != SHADE else DEEP      # freckles
                im.putpixel((ox + x, oy + y), c)
    for name in ("front", "left"):                                                          # the bright cheek
        ox, oy = FACES[name]
        for x, y in ((2, 2), (3, 2), (2, 3)): im.putpixel((ox + x, oy + y), SHINE)
    ox, oy = FACES["top"]
    for x, y in ((3, 3), (4, 3), (3, 4), (4, 4)): im.putpixel((ox + x, oy + y), STALK)
    # the hat layer, just outside the fruit: a leaf lying on top
    for x, y in ((4, 2), (5, 2), (5, 1), (6, 1), (5, 3)): im.putpixel((40 + x, y), LEAF)
    return im


def icon(c):
    """The fruit as an item: a round fruit with a bright cheek, a stalk and a leaf, 16 x 16."""
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    rnd = random.Random(11)
    for x in range(16):
        for y in range(16):
            d = ((x - 7.5) ** 2 + (y - 9.0) ** 2) ** 0.5
            if d > 5.6: continue
            if d > 4.7: col = c["DEEP"]
            elif (x - 5.5) ** 2 + (y - 7.0) ** 2 < 5: col = c["LIGHT"]
            elif x + y > 20: col = c["SHADE"]
            else: col = c["BODY"]
            if col == c["BODY"] and rnd.random() < 0.08: col = c["SHADE"]
            im.putpixel((x, y), col)
    for x, y in ((5, 6), (6, 6), (5, 7)): im.putpixel((x, y), c["SHINE"])
    for x, y in ((8, 3), (8, 2), (7, 1)): im.putpixel((x, y), c["STALK"])
    for x, y in ((9, 2), (10, 1), (11, 1), (10, 2), (11, 2), (12, 2)): im.putpixel((x, y), c["LEAF"])
    return im


if __name__ == "__main__":
    os.makedirs(SKINS, exist_ok=True)
    for name, colours in (("glow_fruit", GLOW), ("sea_fruit", SEA)):
        im = fruit(colours)
        im.save(os.path.join(SKINS, name + ".png"))
        if len(sys.argv) > 1: im.resize((512, 512), Image.NEAREST).save(os.path.join(sys.argv[1], name + ".png"))
        im = icon(colours)                                 # the item: texture, model and item definition
        for sub in ("textures/item", "models/item", "items"): os.makedirs(os.path.join(ASSETS, sub), exist_ok=True)
        im.save(os.path.join(ASSETS, "textures", "item", name + ".png"))
        with open(os.path.join(ASSETS, "models", "item", name + ".json"), "w") as f:
            json.dump({"parent": "minecraft:item/generated", "textures": {"layer0": "supermann:item/" + name}}, f)
        with open(os.path.join(ASSETS, "items", name + ".json"), "w") as f:
            json.dump({"model": {"type": "minecraft:model", "model": "supermann:item/" + name}}, f)
        if len(sys.argv) > 1: im.resize((256, 256), Image.NEAREST).save(os.path.join(sys.argv[1], name + "_item.png"))
        print(name + ".png")
