#!/usr/bin/env python3
"""Catalog of GrabCraft builds as separate commands: /build_<type>_<name>, no AI model involved.

`python3 findbuild.py catalog` searches GrabCraft for each type in TYPES (castle, ship, tower …), takes the
first 10 hits under MAX_BLOCKS, saves them in data/build-catalog.json and writes aliases into
data/commands.yml (needs ./mc.sh restart). In game you type /build_ and scroll with the arrow keys.
When a player runs a code, chatbro.py sees it in the log and calls build_code: the 3D model (JSON) is
fetched, the build function is made with blueprints.py (reused next time) and the build goes up in front
of the player. Undo with /build_undo.

Test the search from the terminal:  python3 findbuild.py "a big castle"
"""
import html as htmlmod
import json
import os
import re
import subprocess
import sys
import urllib.parse
import urllib.request

import blueprints as bp

MC = os.path.dirname(os.path.abspath(__file__))
MAX_BLOCKS = bp.MAX_BLOCKS

# Norwegian -> English for common build words, so wishes in Norwegian also work. English words pass through as they are.
NO2EN = {
    "slott": "castle", "borg": "castle", "festning": "fortress", "hus": "house", "hytte": "cabin",
    "villa": "villa", "herregård": "mansion", "gård": "farm", "bondegård": "farmhouse", "låve": "barn",
    "stall": "stable", "tårn": "tower", "fyrtårn": "lighthouse", "bro": "bridge", "kirke": "church",
    "katedral": "cathedral", "skole": "school", "sykehus": "hospital", "butikk": "shop", "marked": "market",
    "restaurant": "restaurant", "kafé": "cafe", "kafe": "cafe", "hotell": "hotel", "skyskraper": "skyscraper",
    "blokk": "apartment", "leilighet": "apartment", "mølle": "windmill", "vindmølle": "windmill",
    "vannmølle": "watermill", "brønn": "well", "fontene": "fountain", "hage": "garden", "park": "park",
    "statue": "statue", "skip": "ship", "båt": "boat", "piratskip": "pirate ship", "ubåt": "submarine",
    "fly": "airplane", "helikopter": "helicopter", "rakett": "rocket", "romskip": "spaceship", "bil": "car",
    "tog": "train", "buss": "bus", "stasjon": "station", "flyplass": "airport", "havn": "harbor",
    "brygge": "dock", "trehus": "treehouse", "tre": "tree", "pyramide": "pyramid", "tempel": "temple",
    "palass": "palace", "port": "gate", "mur": "wall", "vegg": "wall", "gjerde": "fence", "portal": "portal",
    "farm": "farm", "dyr": "animal", "drage": "dragon", "hest": "horse", "hund": "dog", "katt": "cat",
    "robot": "robot", "ridder": "knight", "landsby": "village", "by": "town", "middelalder": "medieval",
    "middelaldersk": "medieval", "moderne": "modern", "japansk": "japanese", "kinesisk": "chinese",
    "nordisk": "nordic", "viking": "viking", "vikingskip": "viking ship", "under": "underwater",
    "undervanns": "underwater", "vann": "water", "strand": "beach", "fjell": "mountain", "is": "ice",
    "snø": "snow", "ørken": "desert", "jungel": "jungle", "skog": "forest", "lite": "small", "liten": "small",
    "lille": "small", "små": "small", "stort": "large", "stor": "large", "store": "large", "svært": "huge",
    "kjempe": "giant", "enkel": "simple", "koselig": "cozy", "fin": "beautiful", "fint": "beautiful",
    "middels": "medium", "rødt": "red", "rød": "red", "blå": "blue", "blått": "blue", "hvit": "white",
    "hvitt": "white", "svart": "black", "grønn": "green", "gull": "gold", "stein": "stone",
    "trehytte": "log cabin", "tømmerhus": "log house", "lager": "storage", "lagerrom": "storage",
    "jerngolem": "iron golem", "kafeteria": "cafe", "arena": "arena", "stadion": "stadium",
    "fengsel": "prison", "bunker": "bunker", "base": "base", "hule": "cave", "eventyr": "fantasy",
    "eventyrhus": "fantasy house", "heks": "witch", "trollmann": "wizard", "spøkelseshus": "haunted house",
}
STOP = {"en", "et", "ei", "meg", "oss", "deg", "til", "med", "og", "som", "her", "der", "foran", "på", "i",
        "a", "an", "the", "of", "kan", "du", "vær", "så", "snill", "please", "noe", "nytt", "ny"}
BUILD_RE = re.compile(r"^(?:kan du\s+)?(?:bygg|bygge|lag|lage|sett opp|build|make)\b\s*(?:meg\s+|oss\s+)?(.*)$", re.I)


def to_query(text):
    words = re.findall(r"[a-zæøå0-9-]+", text.lower())
    out = [NO2EN.get(w, w) for w in words if w not in STOP]
    return " ".join(out).strip()


def search(query):
    url = "https://www.grabcraft.com/search/" + urllib.parse.quote(query)
    page = urllib.request.urlopen(urllib.request.Request(url, headers=bp.UA), timeout=30).read().decode("utf-8", "replace")
    results = []
    for box in page.split('class="product-box')[1:]:
        m = re.search(r'<h3 class="name"><a href="(/minecraft/[^"#]+)" title="([^"]+)"', box)
        if not m:
            continue
        b = re.search(r"Block count:&nbsp;([\d,]+)", box)
        d = re.search(r'class="product-description">([^<]*)', box)
        results.append({"url": "https://www.grabcraft.com" + m.group(1), "title": htmlmod.unescape(m.group(2)).strip(),
                        "desc": htmlmod.unescape(d.group(1)).strip() if d else "",
                        "blocks": int(b.group(1).replace(",", "")) if b else 0})
    return results


def rank(query, results):
    """Most query words in the title first (the description counts a little), then GrabCraft's order. Builds that are too big are dropped."""
    qw = set(query.split())
    def score(i_r):
        i, r = i_r
        t = set(re.findall(r"[a-z]+", r["title"].lower()))
        d = set(re.findall(r"[a-z]+", r["desc"].lower()))
        size = 0                       # "large" prefers many blocks, "small" few
        if qw & {"large", "huge", "giant"}: size = -r["blocks"]
        elif "small" in qw: size = r["blocks"]
        return (-(2 * len(qw & t) + len(qw & d)), size, i)
    ok = [r for r in results if 0 < r["blocks"] <= MAX_BLOCKS]
    return [r for _, r in sorted(enumerate(ok), key=score)]


def find(text, want=20):
    """Searches with the whole wish, and drops words one by one if that gives no hits."""
    q = to_query(text)
    w = q.split()
    # The whole wish first, then shorter variants: without leading words (adjectives, "large castle" -> "castle"),
    # then without trailing words ("lighthouse by the sea" -> "lighthouse"). Collects until there are enough hits.
    tries = [" ".join(w[i:]) for i in range(len(w))] + [" ".join(w[:j]) for j in range(len(w) - 1, 0, -1)]
    seen, found = set(), []
    for qq in dict.fromkeys(tries):
        for r in search(qq):
            if r["url"] not in seen:
                seen.add(r["url"]); found.append(r)
        if len(rank(q, found)) >= want:
            break
    return q, rank(q, found)


def key_for(url):
    slug = url.rstrip("/").split("/minecraft/")[-1].split("/")[0]
    return "chat_" + re.sub(r"[^a-z0-9]", "", slug.lower())[:24]


def rcon(cmd):
    r = subprocess.run(["./mc.sh", "cmd", cmd], cwd=MC, capture_output=True, text=True, timeout=60)
    return (r.stdout + r.stderr).strip()


def build_for(player, cand):
    """Makes the build function (reused if it exists), reloads the datapack and builds in front of the player."""
    key = key_for(cand["url"])
    if not os.path.exists(os.path.join(bp.FUNC, f"bp_{key}.mcfunction")):
        _, model = bp.fetch_model(cand["url"])
        bp.build(key, cand["title"].replace('"', "'"), model)
        rcon("reload")
    rcon(f"execute as {player} at @s run function supermann:bp_{key}")
    return key


# --- Catalog: /build_<type>_<name> as separate commands (scroll with the arrow keys in the command list) ---
CATALOG = os.path.join(MC, "data", "build-catalog.json")
ALIASES = bp.ALIASES
K_START = "  # --- auto: build catalog (written by findbuild.py catalog, do not edit) ---"
K_END = "  # --- end build catalog ---"
PER_TYPE = 10
# Each type is both the GrabCraft search word and the code prefix (/build_castle_...)
TYPES = ["castle", "house", "cabin", "mansion", "farmhouse", "tower", "lighthouse", "bridge", "church", "ship",
         "windmill", "temple", "pyramid", "statue", "dragon", "spaceship", "airplane", "car", "train", "treehouse",
         "skyscraper", "shop", "hotel", "fountain", "garden", "gate", "underwater", "japanese", "viking", "farm"]


def code_for(prefix, title, used):
    slug = re.sub(r"[^a-z0-9]+", "_", title.lower()).strip("_")[:26].strip("_")
    code, n = f"build_{prefix}_{slug}", 2
    while code in used:
        code, n = f"build_{prefix}_{slug}_{n}", n + 1
    return code


def make_catalog():
    """Searches GrabCraft for each type, saves the catalog and writes the aliases into commands.yml (needs ./mc.sh restart)."""
    cat, seen = {}, set()
    for prefix in TYPES:
        hits = [r for r in search(prefix) if 0 < r["blocks"] <= MAX_BLOCKS and r["url"] not in seen][:PER_TYPE]
        for r in hits:
            seen.add(r["url"])
            cat[code_for(prefix, r["title"], cat)] = r
        print(f"{prefix:12s} {len(hits)}")
    json.dump(cat, open(CATALOG, "w"), ensure_ascii=False, indent=1)
    write_catalog_aliases(cat)


def write_catalog_aliases(cat):
    """Writes the catalog block into commands.yml (between K_START and K_END, before the blueprint aliases)."""
    a = open(ALIASES).read()
    a = re.sub(re.escape(K_START) + r".*?" + re.escape(K_END) + r"\n", "", a, flags=re.S)
    block = K_START + "\n" + "".join(
        f"  {code}:\n  - 'tellraw @s {{\"text\":\"[AI] Fetching {r['title'][:40].replace(chr(34), '').replace(chr(39), '')} ({r['blocks']} blocks)…\",\"color\":\"gray\"}}'\n"
        for code, r in cat.items()) + K_END + "\n"
    marker = bp.MARK_START
    a = a.replace(marker, block + marker, 1) if marker in a else a.rstrip("\n") + "\n" + block
    open(ALIASES, "w").write(a)
    print(f"{len(cat)} codes written. Run ./mc.sh restart for them to show up in game.")


def in_catalog(code):
    try:
        return code in json.load(open(CATALOG))
    except (OSError, ValueError):
        return False


def build_code(player, code):
    """From chatbro when a player runs /build_<...>: builds the catalog entry in front of the player."""
    try:
        r = json.load(open(CATALOG))[code]
    except (OSError, ValueError, KeyError):
        return f"Could not find /{code} in the catalog."
    try:
        build_for(player, r)
    except Exception as e:   # noqa: BLE001
        return f"Could not fetch \"{r['title']}\" from GrabCraft ({e})."
    return None


if __name__ == "__main__":
    if len(sys.argv) < 2:
        sys.exit(__doc__)
    if sys.argv[1] == "catalog":
        make_catalog(); sys.exit(0)
    q, res = find(sys.argv[1])
    print(f"Search: {q}")
    for i, r in enumerate(res, 1):
        print(f"  {i:2d}. {r['blocks']:6d}  {r['title']}  {r['url']}")
