#!/usr/bin/env python3
"""Skins from the skins/ folder for yourself and your helper. The folder is yours and never goes into git.

Save a 64x64 skin PNG as skins/<name>.png (for example downloaded in the browser from a skin site; the name of the
file is the code word). Thin "Alex" arms are found by themselves; if not, name the file <name>.slim.png. Then in the chat (through chatbro.py):
    ai skin <name>          your own skin. Minecraft only shows skins signed by Mojang, so the PNG is signed
                            through mineskin.org: needs a free key in .chatbro.env (MINESKIN_API_KEY=...)
    ai skin reset           your normal skin again
    ai helper skin <name>   your helper (/helper): goes into the resource pack, no account or key needed
    ai skins                lists the names
In game, once a skin is imported: /skin <name>, /skin reset, /helper skin <name>.

From the terminal:  python3 skins.py list | sync | helper <name> | sign <name>
chatbro.py runs sync every 20 s, so a new file in skins/ is usable with /helper skin <name> shortly after.
"""
import hashlib
import json
import os
import re
import subprocess
import sys
import time
import urllib.error
import urllib.request
import uuid

import heads

MC = os.path.dirname(os.path.abspath(__file__))
SKINS = os.path.join(MC, "skins")
META = os.path.join(MC, "data", "plugins", "Creator", "skins")            # read by the Creator plugin (/skin, /helper)
TEX = os.path.join(MC, "resourcepack-extra", "assets", "supermann", "textures", "entity", "skins")
API = "https://api.mineskin.org/v2"
AGENT = "mineserve-ai/1.0"


def slug(s):
    s = s.lower().replace("æ", "ae").replace("ø", "o").replace("å", "a")
    s = re.sub(r"(\.(slim|classic))?\.png$", "", s.strip())   # name.slim.png -> name
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9_-]", "_", s)).strip("_-")[:40]


def names():
    if not os.path.isdir(SKINS):
        return []
    return sorted(slug(f) for f in os.listdir(SKINS) if f.lower().endswith(".png") and slug(f))


def find(name):
    """(slug, path) for the skin the player means: exact name first, then one that starts with or contains it."""
    want = slug(name)
    if not want or not os.path.isdir(SKINS):
        return None, None
    files = {slug(f): os.path.join(SKINS, f) for f in os.listdir(SKINS) if f.lower().endswith(".png") and slug(f)}
    if want in files:
        return want, files[want]
    for test in (lambda k: k.startswith(want), lambda k: want in k):
        hits = [k for k in files if test(k)]
        if len(hits) == 1:
            return hits[0], files[hits[0]]
    return None, None


def check(path):
    """Reads and checks the PNG. Returns (bytes, "slim" | "classic") or raises ValueError with a short reason."""
    data = open(path, "rb").read()
    try:
        px = heads.png_read(data)
    except Exception as e:  # noqa: BLE001
        raise ValueError(f"not a PNG I can read ({e})") from None
    h, w = len(px), len(px[0]) if px else 0
    if (w, h) == (64, 32):
        raise ValueError("it is an old 64x32 skin, pick a 64x64 one")
    if (w, h) != (64, 64):
        raise ValueError(f"a skin must be 64x64 pixels, this one is {w}x{h}")
    forced = re.search(r"\.(slim|classic)\.png$", path.lower())
    if forced:                                 # name.slim.png / name.classic.png: the file name decides
        return data, forced.group(1)
    # slim (Alex) arms are 3 pixels wide: the last two columns of the right arm are usually empty (not always:
    # some artists paint there too, then name the file name.slim.png)
    slim = all(px[y][x][3] == 0 for y in range(20, 32) for x in (54, 55))
    return data, "slim" if slim else "classic"


def meta(s):
    try:
        return json.load(open(os.path.join(META, s + ".json")))
    except (OSError, ValueError):
        return {}


def save_meta(s, d):
    os.makedirs(META, exist_ok=True)
    tmp = os.path.join(META, s + ".json.tmp")
    json.dump(d, open(tmp, "w"), indent=1)
    os.replace(tmp, os.path.join(META, s + ".json"))


def for_helper(s, path):
    """Puts the skin into the resource pack. Returns True if the pack changed (then it must be sent out again)."""
    data, model = check(path)
    os.makedirs(TEX, exist_ok=True)
    tex = os.path.join(TEX, s + ".png")
    changed = not os.path.exists(tex) or open(tex, "rb").read() != data
    if changed:
        open(tex, "wb").write(data)
        r = subprocess.run([sys.executable, os.path.join(MC, "heads.py")], cwd=MC, capture_output=True, text=True, timeout=300)
        if r.returncode != 0:
            raise RuntimeError("building the resource pack failed: " + (r.stderr or r.stdout).strip()[-300:])
    save_meta(s, {**meta(s), "model": model, "pack": True})
    return changed


def sync_all():
    """Puts every PNG in skins/ into the resource pack (helpers can use them with /helper skin <name> right away).
    Rebuilds the pack once if anything changed. Returns the names that changed; bad files are skipped."""
    changed = []
    os.makedirs(TEX, exist_ok=True)
    for s in names():
        _, path = find(s)
        try:
            data, model = check(path)
        except ValueError:
            continue
        tex = os.path.join(TEX, s + ".png")
        if not os.path.exists(tex) or open(tex, "rb").read() != data:
            open(tex, "wb").write(data)
            changed.append(s)
        if meta(s).get("model") != model or not meta(s).get("pack"):
            save_meta(s, {**meta(s), "model": model, "pack": True})
    if changed:
        r = subprocess.run([sys.executable, os.path.join(MC, "heads.py")], cwd=MC, capture_output=True, text=True, timeout=300)
        if r.returncode != 0:
            raise RuntimeError("building the resource pack failed: " + (r.stderr or r.stdout).strip()[-300:])
    return changed


def _request(method, url, key, body=None, ctype=None):
    req = urllib.request.Request(url, data=body, method=method, headers={
        "Authorization": f"Bearer {key}", "User-Agent": AGENT, "Accept": "application/json",
        **({"Content-Type": ctype} if ctype else {})})
    try:
        with urllib.request.urlopen(req, timeout=60) as r:
            return r.status, json.load(r)
    except urllib.error.HTTPError as e:
        try:
            d = json.load(e)
        except ValueError:
            d = {}
        msgs = "; ".join(x.get("message", "") for x in d.get("errors", []) if isinstance(x, dict)) or e.reason
        raise RuntimeError(f"MineSkin said {e.code}: {msgs}") from None


def sign(s, path, key):
    """Gets a Mojang-signed texture for the PNG from mineskin.org (cached per file content) and saves it for /skin."""
    data, model = check(path)
    digest = hashlib.sha1(data).hexdigest()
    old = meta(s)
    if old.get("value") and old.get("signature") and old.get("png_sha1") == digest:
        return
    if not key:
        raise RuntimeError("MINESKIN_API_KEY is missing in .chatbro.env (free key: account.mineskin.org/keys)")
    boundary = uuid.uuid4().hex
    parts = []
    for name, val in (("variant", model), ("name", s[:48]), ("visibility", "unlisted")):
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"{name}\"\r\n\r\n{val}\r\n".encode())
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"file\"; filename=\"{s}.png\"\r\n"
                 f"Content-Type: image/png\r\n\r\n".encode() + data + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    status, d = _request("POST", API + "/queue", key, b"".join(parts), f"multipart/form-data; boundary={boundary}")
    deadline = time.time() + 120
    while not (d.get("skin") or {}).get("texture"):
        job = d.get("job") or {}
        if job.get("status") == "failed":
            msgs = "; ".join(x.get("message", "") for x in d.get("errors", []) if isinstance(x, dict))
            raise RuntimeError("MineSkin could not make the skin" + (f": {msgs}" if msgs else ""))
        if not job.get("id") or time.time() > deadline:
            raise RuntimeError("MineSkin took too long, try again in a minute")
        time.sleep(1.5)
        status, d = _request("GET", f"{API}/queue/{job['id']}", key)
    tex = d["skin"]["texture"]["data"]
    save_meta(s, {**old, "model": model, "value": tex["value"], "signature": tex["signature"], "png_sha1": digest})


def _key():
    for line in open(os.path.join(MC, ".chatbro.env")) if os.path.exists(os.path.join(MC, ".chatbro.env")) else []:
        if line.strip().startswith("MINESKIN_API_KEY="):          # a space in front of the name does not matter
            return line.split("=", 1)[1].strip().strip("\"'")
    return os.environ.get("MINESKIN_API_KEY", "")


if __name__ == "__main__":
    cmd = sys.argv[1] if len(sys.argv) > 1 else "list"
    if cmd == "list":
        print(", ".join(names()) or f"No skins yet: save 64x64 PNG files in {SKINS}")
    elif cmd == "sync":
        ch = sync_all()
        print(f"Imported {', '.join(ch)} (then: ./mc.sh cmd sendpack)" if ch else "All skins are already in the pack.")
    elif cmd in ("helper", "sign") and len(sys.argv) > 2:
        s, p = find(sys.argv[2])
        if not s:
            sys.exit(f"No skin called {sys.argv[2]!r}. Skins: {', '.join(names())}")
        if cmd == "helper":
            print(f"{s}: pack {'rebuilt' if for_helper(s, p) else 'already up to date'} (then: ./mc.sh cmd sendpack)")
        else:
            sign(s, p, _key())
            print(f"{s}: signed, use /skin {s} in game")
    else:
        sys.exit(__doc__)
