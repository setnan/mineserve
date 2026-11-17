"""
Renders an entity model with a texture, a quick 3D look from a few angles (no game needed):
    python3 render3d.py <texture.png> <out.png> [wolf|cat]
The boxes are the vanilla adult models, from memory: close, not exact.
"""
import math, sys
from PIL import Image, ImageDraw

TEX = Image.open(sys.argv[1]).convert("RGBA")
OUT = sys.argv[2]

# (texOffs u, v), box origin (x, y, z), size (w, h, d), pivot, xRot  -- model space: y down, head toward -z
CAT = [
    ((0, 0), (-2.5, -2, -3), (5, 4, 5), (0, 15, -9), 0.0),      # head
    ((0, 24), (-1.5, -0.02, -4), (3, 2, 2), (0, 15, -9), 0.0),  # nose
    ((0, 10), (-2, -3, 0), (1, 1, 2), (0, 15, -9), 0.0),        # ears
    ((6, 10), (1, -3, 0), (1, 1, 2), (0, 15, -9), 0.0),
    ((20, 0), (-2, 3, -8), (4, 16, 6), (0, 12, -10), math.pi / 2),   # body
    ((0, 15), (-0.5, 0, 0), (1, 8, 1), (0, 15, 8), 0.9),        # tail
    ((4, 15), (-0.5, 0, 0), (1, 8, 1), (0, 20, 14), 1.73),
    ((8, 13), (-1, 0, 1), (2, 6, 2), (1.1, 18, 5), 0.0),        # hind legs
    ((8, 13), (-1, 0, 1), (2, 6, 2), (-1.1, 18, 5), 0.0),
    ((40, 0), (-1, -0.2, 0), (2, 10, 2), (1.2, 14.1, -5), 0.0),  # front legs
    ((40, 0), (-1, -0.2, 0), (2, 10, 2), (-1.2, 14.1, -5), 0.0),
]
WOLF = [
    ((0, 0), (-2, -3, -2), (6, 6, 4), (-1, 13.5, -7), 0.0),     # head
    ((0, 10), (-0.5, -0.001, -5), (3, 3, 4), (-1, 13.5, -7), 0.0),   # muzzle
    ((16, 14), (-2, -5, 0), (2, 2, 1), (-1, 13.5, -7), 0.0),    # ears
    ((16, 14), (2, -5, 0), (2, 2, 1), (-1, 13.5, -7), 0.0),
    ((18, 14), (-3, -2, -3), (6, 9, 6), (0, 14, 2), math.pi / 2),       # body
    ((21, 0), (-3, -3, -3), (8, 6, 7), (-1, 14, -3), math.pi / 2),      # upper body (the mane)
    ((0, 18), (0, 0, -1), (2, 8, 2), (-2.5, 16, 7), 0.0),       # legs
    ((0, 18), (0, 0, -1), (2, 8, 2), (0.5, 16, 7), 0.0),
    ((0, 18), (0, 0, -1), (2, 8, 2), (-2.5, 16, -4), 0.0),
    ((0, 18), (0, 0, -1), (2, 8, 2), (0.5, 16, -4), 0.0),
    ((9, 18), (0, 0, -1), (2, 8, 2), (-1, 12, 8), math.pi / 5), # tail
]
PARTS = WOLF if (len(sys.argv) < 4 or sys.argv[3] == "wolf") else CAT

def faces(uv, o, s):
    u, v = uv; x0, y0, z0 = o; w, h, d = s; x1, y1, z1 = x0 + w, y0 + h, z0 + d
    # (texture rect u, v, tw, th), origin corner, u-direction (per texel), v-direction (per texel)
    return [
        ((u + d, v, w, d), (x0, y0, z0), (1, 0, 0), (0, 0, 1)),                 # top (visually up: min y)
        ((u + d + w, v, w, d), (x0, y1, z0), (1, 0, 0), (0, 0, 1)),             # bottom
        ((u, v + d, d, h), (x1, y0, z0), (0, 0, 1), (0, 1, 0)),                 # side
        ((u + d, v + d, w, h), (x1, y0, z0), (-1, 0, 0), (0, 1, 0)),            # front (-z)
        ((u + d + w, v + d, d, h), (x0, y0, z1), (0, 0, -1), (0, 1, 0)),        # other side
        ((u + d + w + d, v + d, w, h), (x0, y0, z1), (1, 0, 0), (0, 1, 0)),     # back (+z)
    ]

def world(p, pivot, rot):
    x, y, z = p
    c, s = math.cos(rot), math.sin(rot)
    y, z = y * c - z * s, y * s + z * c
    return (x + pivot[0], y + pivot[1], z + pivot[2])

def view(p, yaw, pitch):
    x, y, z = p[0], -p[1], p[2]                     # y up
    c, s = math.cos(yaw), math.sin(yaw)
    x, z = x * c + z * s, -x * s + z * c
    c, s = math.cos(pitch), math.sin(pitch)
    y, z = y * c - z * s, y * s + z * c
    return x, y, z

def render(yaw, pitch, size=520, scale=15):
    quads = []
    for (uv, o, s, pivot, rot) in PARTS:
        for (rect, org, du, dv) in faces(uv, o, s):
            tu, tv, tw, th = rect
            for i in range(int(round(tw))):
                for j in range(int(round(th))):
                    px = TEX.getpixel((min(63, tu + i), min(31, tv + j)))
                    if px[3] == 0: continue
                    pts = []
                    for (a, b) in ((i, j), (i + 1, j), (i + 1, j + 1), (i, j + 1)):
                        p = tuple(org[k] + du[k] * a + dv[k] * b for k in range(3))
                        pts.append(view(world(p, pivot, rot), yaw, pitch))
                    depth = sum(q[2] for q in pts) / 4
                    quads.append((depth, [(size / 2 + q[0] * scale, size * 0.55 - (q[1] + 15) * scale * 0.9 - 0) for q in pts], px))
    quads.sort(key=lambda q: -q[0])
    img = Image.new("RGBA", (size, size), (34, 40, 56, 255))
    dr = ImageDraw.Draw(img)
    for _, poly, col in quads: dr.polygon(poly, fill=col)
    return img

views = [render(math.radians(a), math.radians(p)) for (a, p) in ((-35, 20), (-90, 8), (25, 25))]
sheet = Image.new("RGBA", (520 * 3, 520), (34, 40, 56, 255))
for i, v in enumerate(views): sheet.paste(v, (i * 520, 0))
sheet.save(OUT)
print("ok")
