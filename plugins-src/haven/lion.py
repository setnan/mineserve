"""
Draws the Lion's coat on the wolf model (64x32), the most lion-like body in the game: a broad, deep upper body (the
wolf's ruff) that becomes a majestic golden mane, framing the head; a tawny gold coat that glints faintly with gold;
dark golden brows over deep, dark blue eyes (kind and wise); a light muzzle with a dark nose; cream paws; a dark tuft
at the end of the tail. Writes resourcepack-extra/assets/haven/textures/entity/wolf/lion.png; gen.py adds the wolf
variant haven:lion. Run heads.py afterwards to rebuild the pack.

    python3 plugins-src/haven/lion.py [preview-file]
"""
import os, sys
from PIL import Image

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "resourcepack-extra", "assets", "haven", "textures", "entity", "wolf", "lion.png")

COAT = [(200, 144, 62), (210, 156, 72), (218, 166, 82), (228, 178, 96)]
GLINT = (255, 228, 140)
CREAM = [(226, 200, 150), (240, 220, 172)]
MANE = [(132, 78, 20), (172, 108, 28), (206, 142, 40), (238, 182, 66), (252, 210, 104)]
BROW, EYE, NOSE, MOUTH = (120, 72, 18), (26, 52, 138), (70, 44, 34), (150, 100, 60)

im = Image.new("RGBA", (64, 32), (0, 0, 0, 0))

def h(x, y, n): return ((x * 73856093) ^ (y * 19349663) ^ (n * 83492791)) & 0xffff

def coat(x, y):
    k = h(x, y, 1)
    im.putpixel((x, y), (GLINT if k % 41 == 0 else COAT[[1, 2, 2, 1, 2, 2, 0, 3][k % 8]]) + (255,))

def mane(x, y, depth=0):
    k = h(x, y, 2)
    shade = [2, 3, 1, 2, 3, 2, 0, 4, 3, 1][k % 10] - depth
    im.putpixel((x, y), MANE[max(0, min(4, shade))] + (255,))

def cream(x, y): im.putpixel((x, y), CREAM[h(x, y, 3) % 2] + (255,))

def box(u, v, w, hh, d, fill):
    """Every face of a model box at texOffs (u, v), size w x hh x d."""
    for x in range(u + d, u + d + 2 * w):
        for y in range(v, v + d): fill(x, y)
    for x in range(u, u + 2 * d + 2 * w):
        for y in range(v + d, v + d + hh): fill(x, y)

box(0, 18, 2, 8, 2, coat)                  # legs
box(9, 18, 2, 8, 2, coat)                  # tail
box(18, 14, 6, 9, 6, coat)                 # body (hind part)
box(21, 0, 8, 6, 7, mane)                  # upper body: the mane over chest and shoulders
box(0, 0, 6, 6, 4, coat)                   # head
box(0, 10, 3, 3, 4, coat)                  # muzzle
box(16, 14, 2, 2, 1, lambda x, y: mane(x, y, 1))   # ears, half hidden in the mane

# paws: cream on the last two rows and underneath; the tail's end: a dark tuft
for x in range(0, 8):
    for y in (26, 27): cream(x, y)
for x in range(4, 6):
    for y in range(18, 20): cream(x, y)
for x in range(9, 17):
    for y in (25, 26, 27): mane(x, y, 2)
# the belly under the hind body: cream (the body box's underside face and a stripe of the face beneath)
for x in range(30, 36):
    for y in range(14, 20): cream(x, y)

# the mane over the head: sides, back and the back half of the top; a frame round the face
for x in list(range(2, 4)) + list(range(10, 12)) + list(range(14, 20)):   # sides: the back half; and the back
    for y in range(4, 10): mane(x, y)
for x in range(4, 10):
    for y in range(0, 2): mane(x, y)
for y in range(4, 10): mane(4, y); mane(9, y)
for x in range(4, 10): mane(x, 4)
# the face (front 4,4 6x6; the frame took the outer ring): brows, eyes
for x in range(5, 9):
    for y in range(5, 10): coat(x, y)
for x in (5, 6): im.putpixel((x, 5), BROW + (255,))
for x in (7, 8): im.putpixel((x, 5), BROW + (255,))
im.putpixel((5, 6), EYE + (255,)); im.putpixel((8, 6), EYE + (255,))
im.putpixel((6, 6), COAT[1] + (255,)); im.putpixel((7, 6), COAT[1] + (255,))
for x in (5, 8): im.putpixel((x, 9), MANE[2] + (255,))   # the mane reaches round the cheeks
# muzzle: light, a dark nose on top of its front, the mouth below
for x in range(0, 18):
    for y in range(10, 17):
        if im.getpixel((x, y))[3]: cream(x, y)
for x in range(4, 7): im.putpixel((x, 14), NOSE + (255,)) if x == 5 else im.putpixel((x, 14), CREAM[0] + (255,))
for x in range(4, 7): im.putpixel((x, 16), MOUTH + (255,))
im.putpixel((5, 15), MOUTH + (255,))

os.makedirs(os.path.dirname(OUT), exist_ok=True)
im.save(OUT)
old = OUT.replace("/wolf/lion.png", "/cat/lion.png")
if os.path.exists(old): os.remove(old)
if len(sys.argv) > 1: im.resize((64 * 12, 32 * 12), Image.NEAREST).save(sys.argv[1])
print("ok", OUT)
