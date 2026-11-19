"""
Draws the Haven builders: little craftsmen in a white work uniform (a jacket with a coloured collar, gold buttons and a
belt with a gold buckle, light trousers with a stripe, brown leather boots), a leather pouch on the left hip, a gold
hammer on the back and short hair, no cap. Accent azure or green; hair brown, blond or grey. The sea builders wear the
same cut in bright azure or green (builder_seaazure_*, builder_seagreen_*).
Writes builder_<accent>_<hair>.png into resourcepack-extra; run heads.py afterwards to rebuild the pack.

    python3 plugins-src/haven/builders.py [preview-folder] [--styles]
"""
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
SKINS = os.path.join(ROOT, "resourcepack-extra", "assets", "supermann", "textures", "entity", "skins")
OUT = sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("--") else None

CLEAR = (0, 0, 0, 0)
GOLD, GOLD_D, GOLD_H = (232, 180, 40, 255), (176, 128, 20, 255), (255, 222, 110, 255)
SKIN, SKIN_SH, SKIN_HI = (236, 196, 164, 255), (214, 168, 136, 255), (246, 214, 186, 255)
EYE_W, IRIS, BROW, LIPS = (250, 250, 250, 255), (70, 130, 190, 255), (130, 96, 60, 255), (190, 120, 110, 255)
CLOTH, CLOTH_D, CLOTH_DD = (246, 246, 250, 255), (222, 224, 232, 255), (196, 200, 212, 255)
TROUSER, TROUSER_D = (232, 234, 240, 255), (206, 210, 220, 255)
LEATHER, LEATHER_D, LEATHER_H = (128, 84, 48, 255), (96, 60, 34, 255), (160, 110, 66, 255)
ACCENTS = {"azure": ((54, 160, 222, 255), (34, 124, 184, 255)), "green": ((64, 172, 104, 255), (42, 134, 78, 255))}
HAIR = {"brown": ((120, 80, 48, 255), (92, 60, 36, 255)), "blond": ((230, 196, 120, 255), (200, 160, 90, 255)),
        "grey": ((196, 196, 200, 255), (160, 160, 168, 255))}

def r(im, x, y, w, h, c):
    for i in range(x, x + w):
        for j in range(y, y + h): im.putpixel((i, j), c)

def head(im, style, accent, hair):
    """Head: skin, short hair, a plain friendly face, and a cap, a bandana, a beret or nothing (style)."""
    H, HD = HAIR[hair]
    A, AD = accent
    r(im, 8, 0, 8, 8, H); r(im, 16, 0, 8, 8, SKIN_SH)       # top: hair; underside: chin
    r(im, 24, 8, 8, 8, H)                                    # back of the head
    r(im, 0, 8, 8, 8, SKIN); r(im, 16, 8, 8, 8, SKIN)        # sides: skin, hair over the top half
    r(im, 0, 8, 8, 3, H); r(im, 16, 8, 8, 3, H)
    im.putpixel((2, 11), H); im.putpixel((21, 11), H)
    for x in range(24, 32): im.putpixel((x, 15), HD)
    r(im, 8, 8, 8, 8, SKIN)                                   # face
    for x in range(8, 16): im.putpixel((x, 8), H); im.putpixel((x, 15), SKIN_SH)
    for x in (8, 9, 14, 15): im.putpixel((x, 9), H)
    im.putpixel((11, 9), HD); im.putpixel((12, 9), H)
    for x in (9, 10, 13, 14): im.putpixel((x, 11), BROW)
    im.putpixel((9, 12), EYE_W); im.putpixel((10, 12), IRIS); im.putpixel((13, 12), IRIS); im.putpixel((14, 12), EYE_W)
    im.putpixel((11, 13), SKIN_SH); im.putpixel((12, 13), SKIN_HI)
    for x in (11, 12): im.putpixel((x, 14), LIPS)
    r(im, 32, 0, 32, 16, CLEAR)
    if style == "cap":                                    # a soft white work cap with a band of colour
        r(im, 40, 0, 8, 8, CLOTH)
        for x in range(32, 64): im.putpixel((x, 8), CLOTH); im.putpixel((x, 9), A)
        for x in range(40, 48): im.putpixel((x, 10), CLOTH_D)      # the peak over the eyes
        im.putpixel((43, 3), A); im.putpixel((44, 3), A)            # a button on top
    elif style == "bandana":
        for x in range(32, 64): im.putpixel((x, 9), A)
        for (x, y) in ((59, 10), (60, 10), (59, 11), (60, 12)): im.putpixel((x, y), A)
    elif style == "beret":
        r(im, 40, 0, 8, 8, A)
        for x in range(32, 64): im.putpixel((x, 8), A)
        for x in range(48, 56): im.putpixel((x, 9), A)
        im.putpixel((44, 3), GOLD)

def uniform(im, accent):
    A, AD = accent
    # body (base 16,16): top, bottom, right side, front, left side, back; 12 rows on the sides
    r(im, 20, 16, 8, 4, CLOTH); r(im, 28, 16, 8, 4, CLOTH_D)
    for (x0, w) in ((16, 4), (20, 8), (28, 4), (32, 8)):
        r(im, x0, 20, w, 12, CLOTH)
        for y in range(20, 32): im.putpixel((x0, y), CLOTH_D); im.putpixel((x0 + w - 1, y), CLOTH_D)
        for x in range(x0, x0 + w):
            im.putpixel((x, 20), A)                               # collar
            im.putpixel((x, 28), AD); im.putpixel((x, 29), A)     # belt
            im.putpixel((x, 31), CLOTH_DD)                        # jacket hem
    for y in range(21, 28): im.putpixel((23, y), CLOTH_DD); im.putpixel((24, y), CLOTH_D)   # the front opening
    for y in (22, 24, 26): im.putpixel((24, y), GOLD)                                      # buttons
    im.putpixel((23, 28), GOLD_D); im.putpixel((24, 28), GOLD_D)
    im.putpixel((23, 29), GOLD); im.putpixel((24, 29), GOLD_H)                             # buckle
    im.putpixel((21, 22), A); im.putpixel((26, 22), A)                                     # collar points
    # arms: white sleeves, shoulder line, cuff, hands
    for (ax, ay) in ((40, 16), (32, 48)):
        r(im, ax + 4, ay, 4, 4, A); r(im, ax + 8, ay, 4, 4, SKIN_SH)          # shoulder top, hand underside
        for (fx, fw) in ((ax, 4), (ax + 4, 4), (ax + 8, 4), (ax + 12, 4)):
            r(im, fx, ay + 4, fw, 12, CLOTH)
            for x in range(fx, fx + fw):
                im.putpixel((x, ay + 4), A)                                  # shoulder
                im.putpixel((x, ay + 12), CLOTH_D)                           # a fold at the elbow
                im.putpixel((x, ay + 13), A); im.putpixel((x, ay + 14), AD)  # cuff
                im.putpixel((x, ay + 15), SKIN)                              # hand
    # legs: light trousers with a stripe down the outside, brown leather boots
    for (lx, ly, outer) in ((0, 16, 0), (16, 48, 8)):
        r(im, lx + 4, ly, 4, 4, TROUSER); r(im, lx + 8, ly, 4, 4, LEATHER_D)
        for fx in (lx, lx + 4, lx + 8, lx + 12):
            r(im, fx, ly + 4, 4, 12, TROUSER)
            for y in range(ly + 4, ly + 12): im.putpixel((fx + 3, y), TROUSER_D) if fx != lx + 4 else None
            for x in range(fx, fx + 4):
                im.putpixel((x, ly + 12), LEATHER_H)                          # boot tops
                for y in (ly + 13, ly + 14): im.putpixel((x, y), LEATHER)
                im.putpixel((x, ly + 15), LEATHER_D)                          # soles
        for y in range(ly + 4, ly + 12): im.putpixel((lx + outer, y), A)      # the stripe on the outer side
    # a leather pouch on the left hip: body left side and the top of the left leg's outer side, on the overlay layers
    for (x0, y0) in ((28, 44), (24, 52)):          # body overlay left side (28,36)+8 rows; left leg base outer side
        pass
    for x in range(28, 32):                            # body overlay, left side, rows 7-11
        for y in range(43, 48): im.putpixel((x, y), LEATHER if y > 44 else LEATHER_H)
    im.putpixel((29, 46), GOLD); im.putpixel((30, 46), GOLD_D)                 # its buckle
    for y in range(36, 43): im.putpixel((30, y), LEATHER_D)                    # the strap up to the belt
    for x in range(8, 12):                             # left leg overlay (0,48): outer side, the bottom of the pouch
        for y in range(52, 55): im.putpixel((x, y), LEATHER if y < 54 else LEATHER_D)
    im.putpixel((27, 43), LEATHER_D); im.putpixel((27, 44), LEATHER)           # a peek of it from the front
    im.putpixel((32, 43), LEATHER_D); im.putpixel((39, 43), CLEAR)
    # a gold hammer slung on the back (body overlay back 32,36), tilted 45 degrees: head up to the left, grip down
    # to the right. Drawn as rotated rectangles: u runs down the handle, v across it.
    import math
    ox, oy = 2.3, 3.0
    for x in range(8):
        for y in range(12):
            dx, dy = x + 0.5 - ox, y + 0.5 - oy
            u, v = (dx + dy) / math.sqrt(2), (dx - dy) / math.sqrt(2)
            c = None
            if -1.6 <= u <= 0.7 and abs(v) <= 2.4: c = GOLD_D if abs(v) > 1.7 else (GOLD_H if u < -0.8 and v < 0 else GOLD)
            elif 0.7 < u <= 7.4 and abs(v) <= 0.55: c = LEATHER if u > 5.6 else GOLD_D
            if c: im.putpixel((32 + x, 36 + y), c)

# The sea builders: the same craftsmen in a bright suit for the water, azure with green lines or green with azure lines,
# and dark sea boots.
SEA = {"seaazure": dict(CLOTH=(40, 200, 255, 255), CLOTH_D=(26, 168, 232, 255), CLOTH_DD=(16, 136, 204, 255), TROUSER=(34, 186, 246, 255),
                        TROUSER_D=(22, 150, 216, 255), accent=((110, 255, 170, 255), (60, 220, 130, 255))),
       "seagreen": dict(CLOTH=(70, 240, 150, 255), CLOTH_D=(46, 208, 124, 255), CLOTH_DD=(30, 172, 100, 255), TROUSER=(60, 226, 140, 255),
                        TROUSER_D=(40, 190, 112, 255), accent=((90, 220, 255, 255), (40, 176, 232, 255)))}
SEA_BOOTS = dict(LEATHER=(24, 96, 120, 255), LEATHER_D=(14, 70, 92, 255), LEATHER_H=(40, 130, 156, 255))

def builder(accent, hair, style="hair", suit=None):
    im = Image.new("RGBA", (64, 64), CLEAR)
    head(im, style, accent, hair)
    old = {k: globals()[k] for k in ("CLOTH", "CLOTH_D", "CLOTH_DD", "TROUSER", "TROUSER_D", "LEATHER", "LEATHER_D", "LEATHER_H")}
    if suit: globals().update({k: v for k, v in suit.items() if k != "accent"}, **SEA_BOOTS)
    try: uniform(im, accent)
    finally: globals().update(old)
    return im

def preview_fn():
    ns = {"__file__": os.path.join(os.path.dirname(os.path.abspath(__file__)), "angels.py")}
    code = open(ns["__file__"]).read()
    exec(code.split("def face(")[0] + "def preview" + code.split("def preview")[1].split("src = Image")[0].split("def wings")[0], ns)
    return ns["preview"]

os.makedirs(SKINS, exist_ok=True)
for f in os.listdir(SKINS):
    if f.startswith("builder_"): os.remove(os.path.join(SKINS, f))
made = []
for a, ac in ACCENTS.items():
    for h in HAIR:
        builder(ac, h).save(f"{SKINS}/builder_{a}_{h}.png"); made.append((a, h))
for a, suit in SEA.items():
    for h in HAIR:
        builder(suit["accent"], h, suit=suit).save(f"{SKINS}/builder_{a}_{h}.png")
if OUT:
    preview = preview_fn()
    big = Image.new("RGBA", (452 * 3 + 20, 440 * 2), (24, 28, 40, 255))
    for i, (a, h) in enumerate(made):
        preview(builder(ACCENTS[a], h), False, f"{OUT}/b_{a}_{h}.png")
        big.paste(Image.open(f"{OUT}/b_{a}_{h}.png"), (20 + (i % 3) * 452, 20 + (i // 3) * 440))
    big.save(f"{OUT}/builders_sheet.png")
    if "--styles" in sys.argv:
        big = Image.new("RGBA", (452 * 4 + 20, 440), (24, 28, 40, 255))
        for i, (st, hr) in enumerate([("hair", "brown"), ("cap", "brown"), ("bandana", "blond"), ("beret", "grey")]):
            preview(builder(ACCENTS["azure"], hr, st), False, f"{OUT}/style_{st}.png")
            big.paste(Image.open(f"{OUT}/style_{st}.png"), (20 + i * 452, 20))
        big.save(f"{OUT}/builder_styles.png")
print("ok", len(made))
