#!/usr/bin/env python3
"""Player heads on the locator bar, and the server resource pack.

Builds the resource pack data/resourcepack/supermann-heads.zip with every player head (from the Mojang skin)
as a 9x9 icon in four distance steps, one waypoint style per player, plus everything in resourcepack-extra/
(item models and textures from the *_pack.py scripts), and writes the datapack function
supermann:heads_style that sets the style on each player. Updates RESOURCE_PACK_SHA1 in .env.
Run: ./mc.sh heads   (then ./mc.sh restart so the server hands out the new pack)
Standard library only, no pip.
"""
import base64, hashlib, json, os, re, struct, sys, urllib.request, zipfile, zlib

MC = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(MC, "data", "resourcepack")
ZIP = os.path.join(OUT, "supermann-heads.zip")
FUNC = os.path.join(MC, "data", "world", "datapacks", "supermann", "data", "supermann", "function", "heads_style.mcfunction")
TIERS = (1.0, 0.85, 0.7, 0.55)          # brightness near -> far

def client_jar(version):
    """The Minecraft client jar the pack scripts read vanilla textures from: MC_CLIENT_JAR, otherwise the
    launcher's folder on macOS, Linux or Windows. The game must have been started once with that version."""
    if os.environ.get("MC_CLIENT_JAR"):
        return os.path.expanduser(os.environ["MC_CLIENT_JAR"])
    for base in ("~/Library/Application Support/minecraft", "~/.minecraft", os.path.join(os.environ.get("APPDATA", ""), ".minecraft")):
        p = os.path.join(os.path.expanduser(base), "versions", version, f"{version}.jar")
        if os.path.exists(p):
            return p
    raise SystemExit(f"Minecraft {version} client jar not found: start that version once in the launcher, or set MC_CLIENT_JAR")



def png_read(data):
    assert data[:8] == b"\x89PNG\r\n\x1a\n", "not a PNG"
    pos, idat, palette, trns = 8, b"", None, None
    while pos < len(data):
        ln, typ = struct.unpack(">I4s", data[pos:pos + 8]); body = data[pos + 8:pos + 8 + ln]; pos += 12 + ln
        if typ == b"IHDR": w, h, depth, ctype = struct.unpack(">IIBB", body[:10])
        elif typ == b"PLTE": palette = [tuple(body[i:i + 3]) for i in range(0, len(body), 3)]
        elif typ == b"tRNS": trns = body
        elif typ == b"IDAT": idat += body
    assert depth == 8 or (depth < 8 and ctype in (0, 3)), f"bit depth {depth} not supported"
    ch = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    raw = zlib.decompress(idat); stride = w * ch if depth == 8 else (w * depth + 7) // 8; px = []; prev = bytearray(stride); p = 0
    for _ in range(h):
        f = raw[p]; line = bytearray(raw[p + 1:p + 1 + stride]); p += 1 + stride
        for i in range(stride):
            a = line[i - ch] if i >= ch else 0; b = prev[i]; c = prev[i - ch] if i >= ch else 0
            if f == 1: line[i] = (line[i] + a) & 255
            elif f == 2: line[i] = (line[i] + b) & 255
            elif f == 3: line[i] = (line[i] + (a + b) // 2) & 255
            elif f == 4:
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                line[i] = (line[i] + (a if pa <= pb and pa <= pc else b if pb <= pc else c)) & 255
        row = []
        if depth < 8:   # palette/grey with 1, 2 or 4 bits per pixel: unpack to one byte each
            per = 8 // depth
            idx = [(line[x // per] >> (8 - depth * (x % per + 1))) & ((1 << depth) - 1) for x in range(w)]
            if ctype == 0: idx = [i * 255 // ((1 << depth) - 1) for i in idx]
        for x in range(w):
            v = line[x * ch:(x + 1) * ch] if depth == 8 else bytes([idx[x]])
            if ctype == 6: row.append(tuple(v))
            elif ctype == 2: row.append((v[0], v[1], v[2], 255))
            elif ctype == 0: row.append((v[0], v[0], v[0], 255))
            elif ctype == 4: row.append((v[0], v[0], v[0], v[1]))
            else:
                r, g, b_ = palette[v[0]]; row.append((r, g, b_, trns[v[0]] if trns and v[0] < len(trns) else 255))
        px.append(row); prev = line
    return px


def png_bytes(px):
    h, w = len(px), len(px[0]); raw = b"".join(b"\x00" + bytes(c for p in row for c in p) for row in px)
    def chunk(t, b): return struct.pack(">I", len(b)) + t + b + struct.pack(">I", zlib.crc32(t + b) & 0xffffffff)
    return (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 6, 0, 0, 0))
            + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))


def blend(base, over):
    r, g, b, a = over
    if a == 0: return base
    if a == 255: return over
    br, bg, bb, ba = base; f = a / 255
    return (int(r * f + br * (1 - f)), int(g * f + bg * (1 - f)), int(b * f + bb * (1 - f)), max(a, ba))


def face_icon(skin):
    px = png_read(skin)
    out = [[(0, 0, 0, 0)] * 9 for _ in range(9)]
    for y in range(8):
        for x in range(8):
            out[y][x] = blend(px[8 + y][8 + x], px[8 + y][40 + x])    # face + hat layer
    for i in range(9):                                                  # 1 px shadow at the bottom and right
        if out[i][8][3] == 0: out[i][8] = (0, 0, 0, 110)
        if out[8][i][3] == 0: out[8][i] = (0, 0, 0, 110)
    return out


def dim(img, k):
    return [[(int(r * k), int(g * k), int(b * k), a) for (r, g, b, a) in row] for row in img]


def fetch(url):
    with urllib.request.urlopen(url, timeout=20) as r:
        return r.read()


def skin_url(uuid):
    prof = json.loads(fetch("https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", "")))
    tex = [p for p in prof.get("properties", []) if p["name"] == "textures"]
    if not tex: return None
    return json.loads(base64.b64decode(tex[0]["value"])).get("textures", {}).get("SKIN", {}).get("url")


def players():
    seen = {}
    for f in ("whitelist.json", "usercache.json"):
        p = os.path.join(MC, "data", f)
        if os.path.exists(p):
            for u in json.load(open(p)):
                seen.setdefault(u["name"], u["uuid"])
    return seen


def set_env(key, val):
    p = os.path.join(MC, ".env"); s = open(p).read()
    if re.search(rf"^{key}=", s, re.M): s = re.sub(rf"^{key}=.*$", f"{key}={val}", s, flags=re.M)
    else: s = s.rstrip("\n") + f"\n{key}={val}\n"
    open(p, "w").write(s)


def main():
    os.makedirs(OUT, exist_ok=True)
    lines = ["# Generated by heads.py: sets each player head as the icon on the locator bar (needs the resource pack).",
             "# Run from loop.mcfunction. The colour is set to white so the face is not tinted."]
    with zipfile.ZipFile(ZIP + ".tmp", "w", zipfile.ZIP_DEFLATED) as z:   # swapped in at the end, so the web server never serves half a pack
        z.writestr("pack.mcmeta", json.dumps({"pack": {"description": "Supermann: player heads, tools and items",
                                                       "min_format": 97, "max_format": 9999}}, ensure_ascii=False))
        for name, uuid in sorted(players().items()):
            key = re.sub(r"[^a-z0-9_]", "_", name.lower())
            try:
                url = skin_url(uuid)
                icon = face_icon(fetch(url)) if url else None
            except Exception as e:   # noqa: BLE001
                print(f"  {name}: skipping ({e})"); continue
            if icon is None:
                print(f"  {name}: no skin"); continue
            for i, k in enumerate(TIERS):
                z.writestr(f"assets/supermann/textures/gui/sprites/hud/locator_bar_dot/{key}_{i}.png", png_bytes(dim(icon, k)))
            z.writestr(f"assets/supermann/waypoint_style/{key}.json",
                       json.dumps({"sprites": [f"supermann:{key}_{i}" for i in range(4)]}))
            lines.append(f"execute as @a[name={name}] run waypoint modify @s style set supermann:{key}")
            lines.append(f"execute as @a[name={name}] run waypoint modify @s color white")
            print(f"  {name}: ok ({uuid})")
        extra = os.path.join(os.path.dirname(os.path.abspath(__file__)), "resourcepack-extra")   # e.g. detector_pack.py
        for root, _, files in os.walk(extra):
            for fn in files:
                path = os.path.join(root, fn)
                z.write(path, os.path.relpath(path, extra))
    os.replace(ZIP + ".tmp", ZIP)
    sha1 = hashlib.sha1(open(ZIP, "rb").read()).hexdigest()
    open(FUNC, "w").write("\n".join(lines) + "\n")
    set_env("RESOURCE_PACK_SHA1", sha1)
    print(f"Pack: {ZIP} ({os.path.getsize(ZIP)} bytes, sha1 {sha1})")
    print(f"Function: {FUNC}")
    print("Remember: ./mc.sh restart so the server hands out the new pack (new sha1).")


if __name__ == "__main__":
    main()
