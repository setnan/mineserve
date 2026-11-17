"""
Draws the Haven angels: the angelman uniform (from skins/angelman.png) with an open helmet and a face, and the angel
woman in the same uniform with slim arms, a circlet, long hair and a skirt. One skin per hair colour, plus the white,
gold-edged angel wings (an elytra texture). Writes into resourcepack-extra; run heads.py afterwards to rebuild the pack.

    python3 plugins-src/haven/angels.py [preview-folder]
"""
import sys
from PIL import Image

import os
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SRC = os.path.join(ROOT, "skins", "angelman.png")
SKINS = os.path.join(ROOT, "resourcepack-extra", "assets", "supermann", "textures", "entity", "skins")
WINGS = os.path.join(ROOT, "resourcepack-extra", "assets", "haven", "textures", "entity", "equipment", "wings")
EQUIP = os.path.join(ROOT, "resourcepack-extra", "assets", "haven", "equipment")
OUT = sys.argv[1] if len(sys.argv) > 1 else None

SKIN, SKIN_SH, SKIN_HI = (236, 196, 164, 255), (214, 168, 136, 255), (246, 214, 186, 255)
EYE_W, IRIS_M, IRIS_F = (250, 250, 250, 255), (60, 120, 200, 255), (40, 150, 170, 255)
BROW_M, BROW_F = (150, 110, 60, 255), (190, 150, 80, 255)
LIPS_M, LIPS_F = (190, 120, 110, 255), (214, 100, 120, 255)
GOLD, GOLD_D = (232, 180, 40, 255), (176, 128, 20, 255)
HAIRS = {   # main, dark, highlight, brows
    "blond": ((246, 220, 140, 255), (214, 180, 96, 255), (255, 240, 190, 255), (190, 150, 80, 255)),
    "brown": ((120, 80, 48, 255), (92, 60, 36, 255), (150, 104, 64, 255), (84, 54, 32, 255)),
    "black": ((44, 40, 46, 255), (26, 24, 28, 255), (74, 68, 78, 255), (30, 28, 32, 255)),
    "red":   ((184, 84, 40, 255), (140, 60, 28, 255), (214, 120, 70, 255), (140, 62, 30, 255)),
}
HAIR, HAIR_D, HAIR_H, BROW = HAIRS["blond"]
SILVER, SILVER_D, WHITE = (220, 222, 228, 255), (160, 164, 172, 255), (246, 246, 250, 255)
CLEAR = (0, 0, 0, 0)

def rect(im, x, y, w, h, c):
    for i in range(x, x + w):
        for j in range(y, y + h):
            im.putpixel((i, j), c)

def face(im, ox, oy, eye_iris, brow, lips, woman):
    """An 8x8 face at (ox, oy): head front."""
    rect(im, ox, oy, 8, 8, SKIN)
    for i in range(8):
        im.putpixel((ox + i, oy + 7), SKIN_SH)               # chin shadow
    im.putpixel((ox, oy + 4), SKIN_SH); im.putpixel((ox + 7, oy + 4), SKIN_SH)
    # brows (row 3), eyes (row 4), nose (row 5), mouth (row 6)
    for x in (1, 2): im.putpixel((ox + x, oy + 3), brow)
    for x in (5, 6): im.putpixel((ox + x, oy + 3), brow)
    im.putpixel((ox + 1, oy + 4), EYE_W); im.putpixel((ox + 2, oy + 4), eye_iris)
    im.putpixel((ox + 5, oy + 4), eye_iris); im.putpixel((ox + 6, oy + 4), EYE_W)
    im.putpixel((ox + 3, oy + 5), SKIN_SH); im.putpixel((ox + 4, oy + 5), SKIN_HI)
    for x in (3, 4): im.putpixel((ox + x, oy + 6), lips)
    if woman:
        im.putpixel((ox + 2, oy + 6), SKIN_HI); im.putpixel((ox + 5, oy + 6), SKIN_HI)
        im.putpixel((ox + 1, oy + 5), (240, 180, 170, 255)); im.putpixel((ox + 6, oy + 5), (240, 180, 170, 255))  # blush

def angelman(src):
    """The uniform from the source skin, with a face, short hair and a gold crown instead of the helmet."""
    im = src.copy()
    rect(im, 32, 0, 32, 16, CLEAR)                               # no helmet: the whole hat layer cleared
    rect(im, 8, 0, 8, 8, HAIR)                                   # base head: hair on top, back and sides
    rect(im, 24, 8, 8, 8, HAIR)
    rect(im, 0, 8, 8, 8, SKIN); rect(im, 16, 8, 8, 8, SKIN)      # sides: ears and cheeks below the hair
    rect(im, 0, 8, 8, 4, HAIR); rect(im, 16, 8, 8, 4, HAIR)
    for x in range(3, 8): im.putpixel((x, 12), HAIR)             # sideburn toward the face (right side)
    for x in range(16, 21): im.putpixel((x, 12), HAIR)           # and the left
    for x in range(8, 16):
        if (x * 3) % 5 == 0: im.putpixel((x, 3), HAIR_H)         # a little shine on top
    for x in range(24, 32):
        im.putpixel((x, 15), HAIR_D)                             # neckline at the back
    face(im, 8, 8, IRIS_M, BROW, LIPS_M, False)
    for x in range(8, 16): im.putpixel((x, 8), HAIR); im.putpixel((x, 9), HAIR if x in (8, 9, 14, 15) else SKIN)
    im.putpixel((11, 9), HAIR_D); im.putpixel((12, 9), HAIR)     # a short fringe
    for (x, y) in ((8, 10), (15, 10)): im.putpixel((x, y), HAIR)
    # the crown: a gold band all the way round on the hat layer, with points and a jewel in front
    for x in range(32, 64): im.putpixel((x, 9), GOLD)
    for x in (33, 36, 39, 41, 46, 49, 52, 55, 57, 60, 63): im.putpixel((x, 8), GOLD)
    im.putpixel((43, 8), GOLD); im.putpixel((44, 8), GOLD)
    im.putpixel((43, 9), (90, 170, 230, 255)); im.putpixel((44, 9), GOLD_D)   # a blue jewel
    for x in range(40, 48):
        if x % 2: im.putpixel((x, 7) if False else (x, 9), im.getpixel((x, 9)))
    return im

def to_slim(im):
    """Classic 4-wide arms to slim 3-wide arms (drops a middle column of the front, back, top and bottom)."""
    out = im.copy()
    def arm(x0, y0):      # x0, y0: the arm's top-left in the texture (the 'right' face column starts at x0)
        top, bottom = (x0 + 4, y0), (x0 + 8, y0)
        rfront = (x0 + 4, y0 + 4); left = (x0 + 8, y0 + 4); back = (x0 + 12, y0 + 4)
        src = im.copy()
        rect(out, x0, y0, 16, 16, CLEAR)
        def take(sx, sy, w, h, dx, dy, drop=None):
            col = 0
            for i in range(w):
                if drop is not None and i == drop: continue
                for j in range(h): out.putpixel((dx + col, dy + j), src.getpixel((sx + i, sy + j)))
                col += 1
        take(top[0], top[1], 4, 4, x0 + 4, y0, drop=2)            # top 3x4
        take(bottom[0], bottom[1], 4, 4, x0 + 7, y0, drop=2)      # bottom 3x4
        take(x0, y0 + 4, 4, 12, x0, y0 + 4)                       # outer side 4x12
        take(rfront[0], rfront[1], 4, 12, x0 + 4, y0 + 4, drop=2) # front 3x12
        take(left[0], left[1], 4, 12, x0 + 7, y0 + 4)             # inner side 4x12
        take(back[0], back[1], 4, 12, x0 + 11, y0 + 4, drop=1)    # back 3x12
    for (x0, y0) in ((40, 16), (40, 32), (32, 48), (48, 48)):
        arm(x0, y0)
    return out

def angel_woman(src):
    im = to_slim(angelman(src))
    # face, feminine; a gold circlet and long golden hair instead of the helmet
    face(im, 8, 8, IRIS_F, BROW, LIPS_F, True)
    rect(im, 32, 0, 32, 16, CLEAR)                               # clear the whole hat layer
    rect(im, 8, 0, 8, 8, HAIR); rect(im, 24, 8, 8, 8, HAIR)      # base: hair on top and back
    rect(im, 0, 8, 8, 8, HAIR); rect(im, 16, 8, 8, 8, HAIR)      # and on the sides
    for x in range(8, 16): im.putpixel((x, 4), HAIR_H)
    # hat layer: fringe, circlet, hair framing the face, falling down the back
    for x in range(40, 48): im.putpixel((x, 8), HAIR)
    for x in (40, 41, 46, 47): im.putpixel((x, 9), HAIR)
    for x in range(40, 48): im.putpixel((x, 10), CLEAR)
    for x in range(41, 47): im.putpixel((x, 9), GOLD)            # circlet across the brow
    im.putpixel((43, 8), GOLD); im.putpixel((44, 8), GOLD_D)      # its little jewel
    for y in range(9, 16): im.putpixel((40, y), HAIR); im.putpixel((47, y), HAIR_D)
    rect(im, 32, 8, 8, 8, HAIR); rect(im, 48, 8, 8, 8, HAIR_D)   # sides
    rect(im, 56, 8, 8, 8, HAIR)                                  # back of the head
    rect(im, 40, 0, 8, 8, HAIR_H)                                # top
    for y in range(8, 16):
        for x in (32, 56):
            if (y + x) % 3 == 0: im.putpixel((x + 2, y), HAIR_D)
    # long hair down the back (body overlay back: 32,36 8x12), to the waist
    for x in range(32, 40):
        for y in range(36, 43):
            im.putpixel((x, y), HAIR if (x + y) % 4 else HAIR_D)
    for x in range(33, 39): im.putpixel((x, 43), HAIR_D)
    # waist: slimmer, with a gold sash; a little shaping at the chest
    body = (20, 20)
    for y in range(26, 29):
        im.putpixel((20, y), SILVER_D); im.putpixel((27, y), SILVER_D)
    for x in range(20, 28): im.putpixel((x, 28), GOLD)
    im.putpixel((23, 29), GOLD_D); im.putpixel((24, 29), GOLD_D)
    for x in (21, 22, 25, 26): im.putpixel((x, 23), WHITE)
    for x in (22, 25): im.putpixel((x, 24), SILVER_D)
    # a flowing white skirt over the upper legs (leg overlays), edged in gold
    for (lx, ly) in ((4, 36), (4, 52)):
        for x in range(lx, lx + 4):
            for y in range(ly, ly + 7):
                im.putpixel((x, y), GOLD if y == ly + 6 else (SILVER if (x - lx) in (1,) and y > ly + 1 else WHITE))
        for (sx, sy) in ((lx - 4, ly), (lx + 4, ly), (lx + 8, ly)):    # sides and back of the skirt
            for x in range(sx, sx + 4):
                for y in range(sy, sy + 7):
                    im.putpixel((x, y), WHITE if y < sy + 6 else GOLD)
    return im

def preview(skin, slim, path, scale=12):
    """Front and back view side by side."""
    aw = 3 if slim else 4
    v = Image.new("RGBA", (2 * 16 + 4, 32), (40, 48, 64, 255))
    def blit(sx, sy, w, h, dx, dy):
        for i in range(w):
            for j in range(h):
                p = skin.getpixel((sx + i, sy + j))
                if p[3] > 0: v.putpixel((dx + i, dy + j), p)
    def figure(dx, back):
        # base then overlay for each part
        parts = []
        if not back:
            parts = [((8, 8), (40, 8), 8, 8, 4, 0), ((20, 20), (20, 36), 8, 12, 4, 8),
                     ((44, 20), (44, 36), aw, 12, 4 - aw, 8), ((36, 52), (52, 52), aw, 12, 12, 8),
                     ((4, 20), (4, 36), 4, 12, 4, 20), ((20, 52), (4, 52), 4, 12, 8, 20)]
        else:
            bR = 44 + aw + 4; bL = 36 + aw + 4
            parts = [((24, 8), (56, 8), 8, 8, 4, 0), ((32, 20), (32, 36), 8, 12, 4, 8),
                     ((bR, 20), (bR, 36), aw, 12, 12, 8), ((bL, 52), (bL + 16, 52), aw, 12, 4 - aw, 8),
                     ((12, 20), (12, 36), 4, 12, 8, 20), ((28, 52), (12, 52), 4, 12, 4, 20)]
        for (b, o, w, h, x, y) in parts:
            blit(b[0], b[1], w, h, dx + x, y)
            blit(o[0], o[1], w, h, dx + x, y)
    figure(0, False); figure(20, True)
    v.resize((v.width * scale, v.height * scale), Image.NEAREST).save(path)

def wings():
    """Elytra texture (64x32): the wing box at (22,0), 10x20x2; white feathers in rows, a gold upper edge."""
    im = Image.new("RGBA", (64, 32), CLEAR)
    FEATHER, SHADE = (250, 250, 252, 255), (214, 218, 228, 255)
    for x in range(22, 46):
        for y in range(0, 22):
            if y < 2 and not (24 <= x < 44): continue
            c = FEATHER
            if y >= 2 and (y - 2) % 4 == 3: c = SHADE                      # the edge of each feather row
            if y >= 2 and (x + y) % 7 == 0: c = SHADE                      # quills
            if y in (0, 1, 2): c = GOLD if (x % 3) else GOLD_D            # gilded leading edge
            im.putpixel((x, y), c)
    return im

src = Image.open(SRC).convert("RGBA")
os.makedirs(SKINS, exist_ok=True); os.makedirs(WINGS, exist_ok=True); os.makedirs(EQUIP, exist_ok=True)
sheet = []
for name, h in HAIRS.items():
    HAIR, HAIR_D, HAIR_H, BROW = h
    m = angelman(src); w = angel_woman(src)
    if name != "red": m.save(f"{SKINS}/angelman_{name}.png")
    w.save(f"{SKINS}/angel_woman_{name}.png")
    if OUT:
        if name != "red": preview(m, False, f"{OUT}/angelman_{name}_preview.png"); sheet.append(f"angelman_{name}_preview")
        preview(w, True, f"{OUT}/angel_woman_{name}_preview.png"); sheet.append(f"angel_woman_{name}_preview")
wings().save(f"{WINGS}/angel_wings.png")
open(f"{EQUIP}/angel_wings.json", "w").write('{"layers": {"wings": [{"texture": "haven:angel_wings"}]}}\n')
if OUT:
    big = Image.new("RGBA", (452 * 4 + 20, 440 * 2), (24, 28, 40, 255))
    order = ["angelman_blond_preview", "angelman_brown_preview", "angelman_black_preview", None,
             "angel_woman_blond_preview", "angel_woman_brown_preview", "angel_woman_black_preview", "angel_woman_red_preview"]
    for i, n in enumerate(order):
        if n: big.paste(Image.open(f"{OUT}/{n}.png"), (20 + (i % 4) * 452, 20 + (i // 4) * 440))
    big.paste(wings().crop((22, 0, 46, 22)).resize((24 * 12, 22 * 12), Image.NEAREST), (20 + 3 * 452 + 60, 80))
    big.save(f"{OUT}/angels_sheet.png")
print("ok")
