#!/usr/bin/env python3
"""GrabCraft blueprints as in-game commands.

Reads the links in the Obsidian note "Minecraft Blueprints", fetches the block data (3D model) from
GrabCraft, translates the old block names to today's ids, and writes one build function per
blueprint into the supermann datapack (bp/<code>.mcfunction, local coordinates so the build appears in
front of the player, in the direction they look) plus aliases in data/commands.yml (/build_<code>).
Run: ./mc.sh blueprints, then /reload in game; new aliases need ./mc.sh restart.
"""
import json, os, re, sys, urllib.request

MC = os.path.dirname(os.path.abspath(__file__))
def env_setting(key, default):
    """A setting from the environment or .env (mc.sh settings live there), ~ expanded, relative to this folder."""
    v = os.environ.get(key)
    if v is None:
        try:
            for line in open(os.path.join(os.path.dirname(os.path.abspath(__file__)), ".env")):
                if line.startswith(key + "="):
                    v = line.split("=", 1)[1].strip()
        except FileNotFoundError:
            pass
    return os.path.join(os.path.dirname(os.path.abspath(__file__)), os.path.expanduser(v or default))


NOTE = env_setting("MC_BLUEPRINTS_NOTE", "notes/blueprints.md")   # set MC_BLUEPRINTS_NOTE in .env
CACHE = os.path.join(MC, "data", "blueprints")
FUNC = os.path.join(MC, "data", "world", "datapacks", "supermann", "data", "supermann", "function")
ALIASES = os.path.join(MC, "data", "commands.yml")
UA = {"User-Agent": "Mozilla/5.0"}
MARK_START = "  # --- auto: blueprint aliases (written by ./mc.sh blueprints, do not edit) ---"
MARK_END = "  # --- end blueprint aliases ---"
MAX_BLOCKS = 60000
# Short codes for the titles in the note (otherwise the title without spaces is used)
KEYS = {"iron golem farm": "ironfarm", "storage sorter": "storage", "feudal japanese castel": "japancastle",
        "chinese castle": "chinacastle", "victorian castle": "victoriancastle", "working castle gate": "castlegate",
        "super castle": "supercastle", "nordic log house": "nordichouse", "fantasy town house": "fantasyhouse",
        "mansion": "mansion", "skyscraper": "skyscraper", "botanical garden": "garden",
        "underwater survival house": "underwaterhouse", "small rustic starter house": "starterhouse"}

COLORS = ["white", "orange", "magenta", "light blue", "yellow", "lime", "pink", "gray", "light gray", "cyan",
          "purple", "blue", "brown", "green", "red", "black"]
WOODS = ["oak", "spruce", "birch", "jungle", "acacia", "dark oak"]
DIRS = {"north", "south", "east", "west", "up", "down"}
# Directions are written as macro keys ($(N) etc.) so the build can be rotated to the player's view direction
MACRO = {"north": "$(N)", "south": "$(S)", "east": "$(E)", "west": "$(W)"}
# For stairs GrabCraft gives the direction the steps face (low side); Minecraft "facing" is the high side
OPP = {"north": "south", "south": "north", "east": "west", "west": "east"}
# Blueprints that must not clear their box to air first (underwater builds): only the inside becomes air
NO_CLEAR = {"underwaterhouse"}
SX, SZ = 2000000, 2000000      # store for the undo copy (overworld), force-loaded only while building/undoing

unknown = {}


def wood(s):
    for w in sorted(WOODS, key=len, reverse=True):
        if s.startswith(w):
            return w.replace(" ", "_"), s[len(w):].strip()
    return None, s


def color(s):
    for c in sorted(COLORS, key=len, reverse=True):
        if s.startswith(c):
            return c.replace(" ", "_"), s[len(c):].strip()
    return None, s


def facing_of(parts):
    for p in parts:
        m = re.match(r"(?:facing |output facing |on block side facing )?(north|south|east|west|up|down)\b", p)
        if m: return m.group(1)
    return None


def convert(name, below=None):
    """GrabCraft name -> (block_id, {state: value}) or None if unknown."""
    raw = name.strip()
    m = re.match(r"^(.*?)\s*\((.*?)\)?\s*$", raw)       # some names lack the closing parenthesis
    base = (m.group(1) if m else raw).strip().lower().replace("  ", " ")
    parts = [p.strip().lower() for p in re.split(r"[,()]", (m.group(2) if m else "")) if p.strip()]
    extra = [q.strip().lower() for p in re.findall(r"\(([^()]*)\)", raw) for q in p.split(",") if q.strip()]  # "(off) (Facing East, ...)"
    parts = list(dict.fromkeys(extra + parts))
    st = {}
    fac = facing_of(parts)
    half = "top" if "upside-down" in parts else "bottom"

    # Name without a base: " (Jungle Wood, Bottom)" = plank slab
    if not base and parts:
        w, _ = wood(parts[0]);
        if w: return f"{w}_slab", {"type": "top" if "upper" in parts else "bottom"}

    # Water / lava
    if base in ("still water", "water"):
        if base == "still water": return "water", {}
        lvl = 0
        for p in parts:
            mm = re.search(r"water level (max|min)( ([+-]) (\d))?", p)
            if mm:
                lvl = 1 if mm.group(1) == "max" else 7
                if mm.group(2): lvl += int(mm.group(4)) * (1 if (mm.group(1) == "max") == (mm.group(3) == "-") else -1)
        if "falling" in parts: lvl += 8
        return "water", {"level": str(max(0, min(15, lvl)))}
    if base.startswith("lava"): return "lava", {}

    # Wood: planks, logs, slabs, stairs, fences, doors, trapdoors
    w, rest = wood(base)
    if w:
        if rest in ("wood plank", "wood planks", "planks"): return f"{w}_planks", {}
        if rest in ("wood", "wood (facing east/west)", "wood (facing north/south)", "wood, facing north/south", "wood, facing east/west", "log"):
            ax = "y"
            txt = base + " " + " ".join(parts)
            if "east/west" in txt: ax = "x"
            if "north/south" in txt: ax = "z"
            return f"{w}_log", {"axis": ax}
        if rest == "wood slab": return f"{w}_slab", {"type": "top" if "upper" in parts else "bottom"}
        if rest == "wood stairs": return f"{w}_stairs", {"facing": OPP.get(fac, "north"), "half": half}
        if rest == "fence": return f"{w}_fence", {}
        if rest == "fence gate": return f"{w}_fence_gate", {"facing": fac or "north"}
        if rest == "leaves": return f"{w}_leaves", {"persistent": "true"}
        if rest == "door":
            upper = "upper" in parts
            s = {"half": "upper" if upper else "lower"}
            if upper:
                s["hinge"] = "right" if "hinge right" in parts else "left"
                if below and below[0] == f"{w}_door": s["facing"] = below[1].get("facing", "north")
            else:
                s["facing"] = fac or "north"; s["open"] = "true" if "opened" in parts else "false"
            return f"{w}_door", s
        if rest == "sapling flower pot": return f"potted_{w}_sapling", {}
    if base.startswith("double ") and base.endswith(" slab"):
        core = base[7:-5]
        w2, _ = wood(core)
        if w2: return f"{w2}_planks", {}
        return {"stone": "smooth_stone", "cobblestone": "cobblestone", "stone brick": "stone_bricks", "quartz": "quartz_block"}.get(core, "smooth_stone"), {}
    if base == "smooth double stone slab": return "smooth_stone", {}
    if base == "stone slab": return "smooth_stone_slab", {"type": "top" if "upper" in parts else "bottom"}
    if base in ("cobblestone slab", "stone brick slab", "quartz slab"):
        return base.replace("stone brick", "stone_brick").replace(" slab", "_slab"), {"type": "top" if "upper" in parts else "bottom"}
    if base == "cobblestone" and "upper" in parts: return "cobblestone_slab", {"type": "top"}
    if base.endswith(" stairs"):
        core = base[:-7]
        ident = {"stone brick": "stone_brick", "cobblestone": "cobblestone", "quartz": "quartz", "brick": "brick",
                 "sandstone": "sandstone", "nether brick": "nether_brick"}.get(core)
        if ident: return f"{ident}_stairs", {"facing": OPP.get(fac, "north"), "half": half}

    # Colours
    c, rest = color(base)
    if c:
        if rest == "wool": return f"{c}_wool", {}
        if rest == "stained clay": return f"{c}_terracotta", {}
        if rest == "carpet": return f"{c}_carpet", {}
        if rest == "stained glass pane": return f"{c}_stained_glass_pane", {}
        if rest == "stained glass": return f"{c}_stained_glass", {}

    simple = {
        "dirt": "dirt", "grass": "grass_block", "stone": "stone", "cobblestone": "cobblestone", "glass": "glass",
        "glass pane": "glass_pane", "clay": "clay", "hardened clay": "terracotta", "stone bricks": "stone_bricks",
        "chiseled stone bricks": "chiseled_stone_bricks", "mossy stone bricks": "mossy_stone_bricks", "bricks": "bricks",
        "iron block": "iron_block", "gold block": "gold_block", "lapis lazuli block": "lapis_block", "quartz block": "quartz_block",
        "chiseled quartz block": "chiseled_quartz_block", "glowstone": "glowstone", "iron bars": "iron_bars", "iron bars 1": "iron_bars",
        "cobblestone wall": "cobblestone_wall", "nether brick fence": "nether_brick_fence", "snow block": "snow_block",
        "bookshelf": "bookshelf", "crafting table": "crafting_table", "note block": "note_block", "beacon": "beacon",
        "dragon egg": "dragon_egg", "cobweb": "cobweb", "gravel": "gravel", "coal ore": "coal_ore", "podzol": "podzol",
        "farmland": "farmland", "lily pad": "lily_pad", "dandelion": "dandelion", "poppy": "poppy", "blue orchid": "blue_orchid",
        "azure bluet": "azure_bluet", "oxeye daisy": "oxeye_daisy", "fern": "fern", "brown mushroom": "brown_mushroom",
        "red mushroom": "red_mushroom", "sugar canes": "sugar_cane", "flower pot": "flower_pot", "poppy flower pot": "potted_poppy",
        "dandelion flower pot": "potted_dandelion", "redstone wire": "redstone_wire", "tripwire": "tripwire",
        "wooden pressure plate": "oak_pressure_plate", "stone pressure plate": "stone_pressure_plate",
        "weighted pressure plate": "heavy_weighted_pressure_plate", "pillar quartz block": "quartz_pillar",
        "spruce leaves": "spruce_leaves", "birch leaves": "birch_leaves", "jungle leaves": "jungle_leaves",
    }
    if base in simple:
        b = simple[base]; s = {}
        if b.endswith("_leaves"): s["persistent"] = "true"
        if b == "quartz_pillar":
            txt = " ".join(parts); s["axis"] = "z" if "north-south" in txt else "x" if "east-west" in txt else "y"
        return b, s
    if base == "snow":
        mm = re.search(r"(one|two|three|four|five|six|seven|eight) layers", " ".join(parts))
        n = ["one", "two", "three", "four", "five", "six", "seven", "eight"].index(mm.group(1)) + 1 if mm else 1
        return "snow", {"layers": str(n)}
    if base == "flower":
        kind = {"sunflower": "sunflower", "double tallgrass": "tall_grass", "large fern": "large_fern", "lilac": "lilac",
                "rose bush": "rose_bush", "peony": "peony"}.get(parts[0] if parts else "")
        if kind: return kind, {"half": "upper" if "upper" in parts else "lower"}
    if base == "wheat":
        mm = re.search(r"age (\d)", " ".join(parts)); return "wheat", {"age": mm.group(1) if mm else "0"}
    if base == "melon stem":
        stages = ["first", "second", "third", "fourth", "fifth", "sixth", "seventh", "eighth"]
        mm = re.search(r"(\w+) stage", " ".join(parts)); return "melon_stem", {"age": str(stages.index(mm.group(1)) if mm and mm.group(1) in stages else 0)}
    if base == "hay bale":
        mm = re.search(r"axis (x|y|z)", " ".join(parts)); return "hay_block", {"axis": mm.group(1) if mm else "y"}
    if base == "pumpkin": return "carved_pumpkin", {"facing": fac or "south"}
    if base == "torch": return ("torch", {}) if fac in (None, "up") else ("wall_torch", {"facing": fac})
    if base == "redstone torch":
        lit = "false" if "off" in parts else "true"
        return ("redstone_torch", {"lit": lit}) if fac in (None, "up") else ("redstone_wall_torch", {"facing": fac, "lit": lit})
    if base == "redstone repeater block":
        mm = re.search(r"delay of (\d)", " ".join(parts))
        return "repeater", {"facing": fac or "north", "delay": mm.group(1) if mm else "1", "powered": "true" if "on" in parts else "false"}
    if base == "redstone comparator": return "comparator", {"facing": fac or "north"}
    if base in ("stone button", "wooden button"):
        return ("stone_button" if base.startswith("stone") else "oak_button"), {"facing": fac or "north", "face": "wall"}
    if base == "lever": return "lever", {"facing": fac or "north", "face": "wall", "powered": "true" if "active" in parts and "not active" not in parts else "false"}
    if base == "ladder": return "ladder", {"facing": fac or "north"}
    if base == "vines": return "vine", {(fac or "north"): "true"}
    if base == "wooden trapdoor":
        return "oak_trapdoor", {"facing": fac or "north", "open": "true" if "open" in parts else "false",
                                "half": "top" if "top half" in parts else "bottom"}
    if base == "iron door":
        upper = "upper" in parts; s = {"half": "upper" if upper else "lower"}
        if upper:
            s["hinge"] = "right" if "hinge right" in parts else "left"
            if below and below[0] == "iron_door": s["facing"] = below[1].get("facing", "north")
        else: s["facing"] = fac or "north"
        return "iron_door", s
    if base in ("chest", "trapped chest"): return base.replace(" ", "_"), {"facing": fac or "north"}
    if base in ("furnace", "burning furnace"): return "furnace", {"facing": fac or "north", "lit": "true" if base.startswith("burning") else "false"}
    if base == "bed": return "red_bed", {"facing": fac or "north", "part": "head" if "head of the bed" in parts else "foot"}
    if base == "hopper": return "hopper", {"facing": fac or "down", "enabled": "false" if "disabled" in parts else "true"}
    if base in ("piston", "sticky piston"): return base.replace(" ", "_"), {"facing": fac or "up", "extended": "true" if "extended" in parts and "not extended" not in parts else "false"}
    if base == "piston head": return "piston_head", {"facing": fac or "up"}
    if base == "anvil":
        txt = " ".join(parts); return "anvil", {"facing": "north" if txt.startswith("north") or txt.startswith("south") else "east"}
    if base.startswith("wall sign"):
        mm = re.search(r"(north|south|east|west)", raw.lower()); return "oak_wall_sign", {"facing": mm.group(1) if mm else "north"}
    if base.startswith("wall-mounted sign"):
        rot = {"south": 0, "south-southwest": 1, "southwest": 2, "west-southwest": 3, "west": 4, "west-northwest": 5, "northwest": 6,
               "north-northwest": 7, "north": 8, "north-northeast": 9, "northeast": 10, "east-northeast": 11, "east": 12,
               "east-southeast": 13, "southeast": 14, "south-southeast": 15}
        mm = re.search(r"block ([a-z-]+)", raw.lower()); return "oak_sign", {"rotation": str(rot.get(mm.group(1) if mm else "", 0))}
    if base == "tripwire hook": return "tripwire_hook", {"facing": fac or "north"}
    if base == "cauldron":
        mm = re.search(r"(\d)/3", " ".join(parts)); return "water_cauldron", {"level": mm.group(1) if mm else "3"}
    if base in ("weighted pressure plate",): return "heavy_weighted_pressure_plate", {}
    unknown[name] = unknown.get(name, 0) + 1
    return None


def state_str(st):
    if not st: return ""
    out = []
    for k, v in st.items():
        k2 = MACRO.get(k, k)                      # vine: west=true -> $(W)=true
        if k == "axis": v = {"x": "$(AX)", "z": "$(AZ)"}.get(v, v)
        elif k == "rotation": v = f"$(ROT{v})"
        elif v in MACRO: v = MACRO[v]
        out.append(f"{k2}={v}")
    return "[" + ",".join(out) + "]"


ATTACHED = {"torch", "wall_torch", "redstone_torch", "redstone_wall_torch", "ladder", "vine", "lever", "tripwire_hook", "tripwire",
            "redstone_wire", "repeater", "comparator", "carpet", "snow", "lily_pad", "sugar_cane", "wheat", "melon_stem",
            "flower_pot", "rail", "oak_wall_sign", "oak_sign", "piston_head"}


def attached(bid):
    return bid in ATTACHED or bid.endswith(("_button", "_pressure_plate", "_carpet", "_door", "_trapdoor", "_sapling")) \
        or bid.startswith("potted_") or bid in ("dandelion", "poppy", "blue_orchid", "azure_bluet", "oxeye_daisy", "fern",
                                                 "brown_mushroom", "red_mushroom", "tall_grass", "large_fern", "sunflower",
                                                 "lilac", "rose_bush", "peony")


def fetch_model(url, cache_id=None):
    os.makedirs(CACHE, exist_ok=True)
    html = urllib.request.urlopen(urllib.request.Request(url, headers=UA), timeout=30).read().decode("utf-8", "replace")
    pid = re.search(r"3dmodel/(\d+)\.json", html).group(1)
    path = os.path.join(CACHE, f"{pid}.json")
    if not os.path.exists(path):
        data = urllib.request.urlopen(urllib.request.Request(f"https://assets.grabcraft.com/files/products/3dmodel/{pid}.json", headers=UA), timeout=120).read()
        open(path, "wb").write(data)
    return pid, json.load(open(path))


def build(key, title, model):
    cubes = {}
    for y, xs in model.items():
        for x, zs in xs.items():
            for z, c in zs.items():
                cubes[(int(x), int(y), int(z))] = c["name"]
    xs = [k[0] for k in cubes]; ys = [k[1] for k in cubes]; zs = [k[2] for k in cubes]
    minx, maxx, miny, minz, maxz, maxy = min(xs), max(xs), min(ys), min(zs), max(zs), max(ys)
    W, D, H = maxx - minx + 1, maxz - minz + 1, maxy - miny + 1
    cx = (minx + maxx) / 2
    conv = {}
    for (x, y, z) in sorted(cubes, key=lambda k: k[1]):
        conv[(x, y, z)] = convert(cubes[(x, y, z)], conv.get((x, y - 1, z)))
    lines = [f"# {title} (GrabCraft). Local coordinates: ^left ^up ^forward, built from ^2 onwards. Generated by blueprints.py.",
             f"# Size {W}x{H}x{D}, {len(cubes)} blocks."]
    half = (W + 1) // 2
    if key in NO_CLEAR:
        # Find the inside: cells in the box that cannot be reached from the edge without passing the blueprint's blocks
        occ = set(cubes)
        box = lambda x, y, z: minx <= x <= maxx and miny <= y <= maxy and minz <= z <= maxz
        seen = set(); stack = []
        for x in range(minx, maxx + 1):
            for y in range(miny, maxy + 1):
                for z in range(minz, maxz + 1):
                    if (x in (minx, maxx) or y in (miny, maxy) or z in (minz, maxz)) and (x, y, z) not in occ:
                        stack.append((x, y, z))
        while stack:
            c = stack.pop()
            if c in seen or c in occ: continue
            seen.add(c)
            x, y, z = c
            for n in ((x+1,y,z),(x-1,y,z),(x,y+1,z),(x,y-1,z),(x,y,z+1),(x,y,z-1)):
                if box(*n) and n not in seen and n not in occ: stack.append(n)
        inner = [(x, y, z) for x in range(minx, maxx + 1) for y in range(miny, maxy + 1) for z in range(minz, maxz + 1)
                 if (x, y, z) not in occ and (x, y, z) not in seen]
        lines.append(f"# No clearing (underwater build). {len(inner)} inside cells are set to air.")
        for (x, y, z) in inner:
            lines.append(f"setblock ^{local_lat(cx, x)} ^{y - miny} ^{(maxz - z) + 2} minecraft:air")
    else:
        for layer in range(1, H + 1):      # clear the box to air, layer by layer
            lines.append(f"fill ^{half + 1} ^{layer} ^2 ^-{half + 1} ^{layer} ^{D + 2} minecraft:air")
    placed = 0
    for pass_attached in (False, True):
        for (x, y, z) in sorted(cubes, key=lambda k: (k[1], k[2], k[0])):
            r = conv[(x, y, z)]
            if r is None: continue
            bid, st = r
            if attached(bid) != pass_attached: continue
            cmd = f"setblock ^{local_lat(cx, x)} ^{y - miny} ^{(maxz - z) + 2} minecraft:{bid}{state_str(st)}"
            lines.append(("$" if "$(" in cmd else "") + cmd)   # only lines with a macro may start with $
            placed += 1
    os.makedirs(os.path.join(FUNC, "bp"), exist_ok=True)
    open(os.path.join(FUNC, "bp", f"{key}.mcfunction"), "w").write("\n".join(lines) + "\n")
    write_dispatcher(key, title, L=half + 1, H=H, F1=2, F2=D + 2, n=len(cubes))
    return W, H, D, len(cubes), placed
    return W, H, D, len(cubes), placed


def local_lat(cx, x):
    lat = cx - x
    return int(lat) if float(lat).is_integer() else int(lat + 0.5)   # even width: round towards the left


def write_dispatcher(key, title, L, H, F1, F2, n, pre=""):
    """bp_<key>.mcfunction: checks, computes the box (undo + forceload), waits for loading, builds."""
    open(os.path.join(FUNC, f"bp_{key}.mcfunction"), "w").write(f"""# /{key}: {title}. Builds in front of the player (front facing the player). Generated by blueprints.py.
# Box in local coordinates: ±{L} sideways, {H} up, {F1}..{F2} forward. Copied to the undo store before building.
execute if score #busy sm_tunnel matches 1 run return run tellraw @s {{"text":"[Supermann] A build job is already running, wait until it is done.","color":"red"}}
scoreboard players set #busy sm_tunnel 1
function supermann:dir
function supermann:bp_dirs
{pre}data modify storage supermann:job bp set value "{key}"
data modify storage supermann:job title set value "{title}"
data modify storage supermann:job dim set from entity @s Dimension
data modify storage supermann:undo dim set from entity @s Dimension
data modify storage supermann:undo title set value "{title}"
data merge storage supermann:undo {{L:{L},H:{H},F1:{F1},F2:{F2}}}
function supermann:bp_box with storage supermann:undo
function supermann:job_forceload with storage supermann:job
execute in minecraft:overworld run forceload add {SX} {SZ} {SX + 100} {SZ + 100}
tag @s add sm_job
tellraw @s {{"text":"[Supermann] Building {title} ({n} blocks) in front of you…","color":"gray"}}
scoreboard players set #tries sm_tunnel 0
function supermann:bp_wait
""")


def write_box():
    # The box in absolute coordinates from the anchor (#x, #z, job.y), view direction and L/H/F1/F2.
    # x1<=x2, z1<=z2 (clone needs the lower corner). Also writes the forceload area and x2/z2 for job_check.
    box = ["# Computes the box around the build: absolute corners for undo (supermann:undo) and forceload (supermann:job). Generated.",
           "$scoreboard players set #L sm_tunnel $(L)", "$scoreboard players set #H sm_tunnel $(H)",
           "$scoreboard players set #F1 sm_tunnel $(F1)", "$scoreboard players set #F2 sm_tunnel $(F2)",
           "execute store result score #y1 sm_tunnel run data get storage supermann:job y",
           "scoreboard players remove #y1 sm_tunnel 1",
           "scoreboard players operation #y2 sm_tunnel = #y1 sm_tunnel",
           "scoreboard players operation #y2 sm_tunnel += #H sm_tunnel"]
    plans = {180: [("#x1", "-=", "#L"), ("#x2", "+=", "#L"), ("#z1", "-=", "#F2"), ("#z2", "-=", "#F1")],
             0:   [("#x1", "-=", "#L"), ("#x2", "+=", "#L"), ("#z1", "+=", "#F1"), ("#z2", "+=", "#F2")],
             90:  [("#x1", "-=", "#F2"), ("#x2", "-=", "#F1"), ("#z1", "-=", "#L"), ("#z2", "+=", "#L")],
             -90: [("#x1", "+=", "#F1"), ("#x2", "+=", "#F2"), ("#z1", "-=", "#L"), ("#z2", "+=", "#L")]}
    for yaw, ops in plans.items():
        for tgt, op, src in ops:
            base = "#x" if tgt.startswith("#x") else "#z"
            box.append(f"execute if score #yaw sm_tunnel matches {yaw} run scoreboard players operation {tgt} sm_tunnel = {base} sm_tunnel")
            box.append(f"execute if score #yaw sm_tunnel matches {yaw} run scoreboard players operation {tgt} sm_tunnel {op} {src} sm_tunnel")
    for k in ("x1", "y1", "z1", "x2", "y2", "z2"):
        box.append(f"execute store result storage supermann:undo {k} int 1 run scoreboard players get #{k} sm_tunnel")
    box += ["scoreboard players operation #sx2 sm_tunnel = #x2 sm_tunnel", "scoreboard players operation #sx2 sm_tunnel -= #x1 sm_tunnel",
            f"scoreboard players add #sx2 sm_tunnel {SX}",
            "scoreboard players operation #sz2 sm_tunnel = #z2 sm_tunnel", "scoreboard players operation #sz2 sm_tunnel -= #z1 sm_tunnel",
            f"scoreboard players add #sz2 sm_tunnel {SZ}",
            "execute store result storage supermann:undo sx2 int 1 run scoreboard players get #sx2 sm_tunnel",
            "execute store result storage supermann:undo sz2 int 1 run scoreboard players get #sz2 sm_tunnel",
            "# Forceload area for the job: the box + 2, and the opposite corner for job_check",
            "scoreboard players operation #fx sm_tunnel = #x1 sm_tunnel", "scoreboard players remove #fx sm_tunnel 2",
            "scoreboard players operation #fz sm_tunnel = #z1 sm_tunnel", "scoreboard players remove #fz sm_tunnel 2",
            "scoreboard players operation #fx2 sm_tunnel = #x2 sm_tunnel", "scoreboard players add #fx2 sm_tunnel 2",
            "scoreboard players operation #fz2 sm_tunnel = #z2 sm_tunnel", "scoreboard players add #fz2 sm_tunnel 2",
            "execute store result storage supermann:job fx int 1 run scoreboard players get #fx sm_tunnel",
            "execute store result storage supermann:job fz int 1 run scoreboard players get #fz sm_tunnel",
            "execute store result storage supermann:job fx2 int 1 run scoreboard players get #fx2 sm_tunnel",
            "execute store result storage supermann:job fz2 int 1 run scoreboard players get #fz2 sm_tunnel",
            "execute store result storage supermann:job x2 int 1 run scoreboard players get #fx2 sm_tunnel",
            "execute store result storage supermann:job z2 int 1 run scoreboard players get #fz2 sm_tunnel"]
    open(os.path.join(FUNC, "bp_box.mcfunction"), "w").write("\n".join(box) + "\n")


def write_common():
    write_box()
    # Blueprints face south (+z). If the player looks north (yaw 180) it is built as is;
    # otherwise compass directions, axes and sign rotation are rotated to match.
    rot = {180: {"N": "north", "S": "south", "E": "east", "W": "west", "AX": "x", "AZ": "z", "add": 0},
           0: {"N": "south", "S": "north", "E": "west", "W": "east", "AX": "x", "AZ": "z", "add": 8},
           90: {"N": "west", "S": "east", "E": "north", "W": "south", "AX": "z", "AZ": "x", "add": 12},
           -90: {"N": "east", "S": "west", "E": "south", "W": "north", "AX": "z", "AZ": "x", "add": 4}}
    out = ["# Direction keys for blueprints (bp/*.mcfunction) from the player's view direction. Generated by blueprints.py."]
    for yaw, m in rot.items():
        d = {k: f'"{v}"' for k, v in m.items() if k != "add"}
        d.update({f"ROT{i}": str((i + m["add"]) % 16) for i in range(16)})
        out.append(f"execute if score #yaw sm_tunnel matches {yaw} run data merge storage supermann:job {{" + ",".join(f"{k}:{v}" for k, v in d.items()) + "}")
    open(os.path.join(FUNC, "bp_dirs.mcfunction"), "w").write("\n".join(out) + "\n")
    open(os.path.join(FUNC, "bp_wait.mcfunction"), "w").write("""# Waits until the area around the blueprint and the undo store are loaded (forceload takes a few ticks), then builds.
# NB: schedule runs in the overworld without an executor, so everything after this is pinned to a dimension via $(dim).
scoreboard players set #loaded sm_tunnel 0
function supermann:job_check with storage supermann:job
execute in minecraft:overworld unless loaded 2000000 64 2000000 run scoreboard players set #loaded sm_tunnel 0
execute in minecraft:overworld unless loaded 2000100 64 2000100 run scoreboard players set #loaded sm_tunnel 0
scoreboard players add #tries sm_tunnel 1
execute if score #loaded sm_tunnel matches 1 run return run function supermann:bp_go
execute if score #tries sm_tunnel matches 30.. run return run function supermann:bp_abort
schedule function supermann:bp_wait 20t
""")
    open(os.path.join(FUNC, "bp_go.mcfunction"), "w").write("""function supermann:bp_snap with storage supermann:undo
function supermann:bp_run with storage supermann:job
function supermann:bp_done with storage supermann:job
""")
    open(os.path.join(FUNC, "bp_snap.mcfunction"), "w").write("""# Copies the box to the undo store before building (one clone; max_block_modifications is raised in load).
$execute store success storage supermann:undo ok byte 1 run clone from $(dim) $(x1) $(y1) $(z1) $(x2) $(y2) $(z2) to minecraft:overworld 2000000 $(y1) 2000000
""")
    open(os.path.join(FUNC, "bp_run.mcfunction"), "w").write("""# Stands in the middle of the block the player stood on, facing the compass direction, and runs the blueprint with the direction keys.
$execute in $(dim) positioned $(x) $(y) $(z) positioned ~ ~-0.5 ~ rotated $(yaw) 0 run function supermann:bp/$(bp) with storage supermann:job
""")
    open(os.path.join(FUNC, "bp_done.mcfunction"), "w").write("""$execute in $(dim) run forceload remove $(fx) $(fz) $(fx2) $(fz2)
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
$tellraw @a[tag=sm_job] [{"text":"[Supermann] Done: $(title). ","color":"gold"},{"text":"Undo with /build_undo.","color":"gray"}]
execute unless data storage supermann:undo {ok:1b} run tellraw @a[tag=sm_job] {"text":"[Supermann] Could not take an undo copy of this build.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
""")
    open(os.path.join(FUNC, "bp_abort.mcfunction"), "w").write("""function supermann:job_forceload_remove with storage supermann:job
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
tellraw @a[tag=sm_job] {"text":"[Supermann] Could not load the area, try again.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
""")
    open(os.path.join(FUNC, "bp_undo.mcfunction"), "w").write("""# /build_undo: puts back what stood there before the last build (blueprint or /house). Last build only, once.
execute unless data storage supermann:undo {ok:1b} run return run tellraw @s {"text":"[Supermann] Nothing to undo.","color":"red"}
execute if score #busy sm_tunnel matches 1 run return run tellraw @s {"text":"[Supermann] A build job is running, wait until it is done.","color":"red"}
scoreboard players set #busy sm_tunnel 1
tag @s add sm_job
function supermann:bp_undo_load with storage supermann:undo
execute in minecraft:overworld run forceload add 2000000 2000000 2000100 2000100
scoreboard players set #tries sm_tunnel 0
function supermann:bp_undo_wait
""")
    open(os.path.join(FUNC, "bp_undo_load.mcfunction"), "w").write("""$execute in $(dim) run forceload add $(x1) $(z1) $(x2) $(z2)
""")
    open(os.path.join(FUNC, "bp_undo_check.mcfunction"), "w").write("""$execute in $(dim) if loaded $(x1) $(y1) $(z1) if loaded $(x2) $(y1) $(z2) run scoreboard players set #loaded sm_tunnel 1
""")
    open(os.path.join(FUNC, "bp_undo_wait.mcfunction"), "w").write("""scoreboard players set #loaded sm_tunnel 0
function supermann:bp_undo_check with storage supermann:undo
execute in minecraft:overworld unless loaded 2000000 64 2000000 run scoreboard players set #loaded sm_tunnel 0
execute in minecraft:overworld unless loaded 2000100 64 2000100 run scoreboard players set #loaded sm_tunnel 0
scoreboard players add #tries sm_tunnel 1
execute if score #loaded sm_tunnel matches 1 run return run function supermann:bp_undo_go with storage supermann:undo
execute if score #tries sm_tunnel matches 30.. run return run function supermann:bp_undo_abort with storage supermann:undo
schedule function supermann:bp_undo_wait 20t
""")
    open(os.path.join(FUNC, "bp_undo_go.mcfunction"), "w").write("""$execute store success score #ok sm_tunnel run clone from minecraft:overworld 2000000 $(y1) 2000000 $(sx2) $(y2) $(sz2) to $(dim) $(x1) $(y1) $(z1)
$execute in $(dim) run forceload remove $(x1) $(z1) $(x2) $(z2)
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
execute if score #ok sm_tunnel matches 1 run data modify storage supermann:undo ok set value 0b
$execute if score #ok sm_tunnel matches 1 run tellraw @a[tag=sm_job] {"text":"[Supermann] Undone: $(title) is removed and the old blocks are back.","color":"gold"}
execute unless score #ok sm_tunnel matches 1 run tellraw @a[tag=sm_job] {"text":"[Supermann] Undo failed (clone). Try again.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
""")
    open(os.path.join(FUNC, "bp_undo_abort.mcfunction"), "w").write("""$execute in $(dim) run forceload remove $(x1) $(z1) $(x2) $(z2)
execute in minecraft:overworld run forceload remove 2000000 2000000 2000100 2000100
tellraw @a[tag=sm_job] {"text":"[Supermann] Could not load the area, try /build_undo again.","color":"red"}
tag @a[tag=sm_job] remove sm_job
scoreboard players set #busy sm_tunnel 0
""")
    # /house goes the same way (forceload + undo): box ±6, 11 up, 1..11 forward; house_build needs the house_dirs keys
    write_dispatcher("house", "Cabin", L=6, H=11, F1=1, F2=11, n=0, pre="function supermann:house_dirs\n")
    open(os.path.join(FUNC, "bp", "house.mcfunction"), "w").write("function supermann:house_build with storage supermann:job\n")
    open(os.path.join(FUNC, "house.mcfunction"), "w").write("""# /house: furnished cabin with a fireplace right in front of you, door facing you. Goes via bp_house (forceload + undo); the build itself is house_build.
function supermann:bp_house
""")


def write_aliases(keys):
    s = open(ALIASES).read()
    s = re.sub(re.escape(MARK_START) + r".*?" + re.escape(MARK_END) + r"\n", "", s, flags=re.S)
    # All builds under /build_<name>; /build lists them, /build_house is the cabin, /build_undo (= /buildundo) undoes
    block = MARK_START + "\n" + "".join(f"  build_{k}:\n  - function supermann:bp_{k}\n" for k, _ in keys) \
        + "  build:\n  - function supermann:bp_list\n  build_house:\n  - function supermann:house\n" \
        + "  build_undo:\n  - function supermann:bp_undo\n  buildundo:\n  - function supermann:bp_undo\n" + MARK_END + "\n"
    marker = "  # --- auto: world aliases"
    s = s.replace(marker, block + marker, 1) if marker in s else s.rstrip("\n") + "\n" + block
    open(ALIASES, "w").write(s)
    lst = ['tellraw @s {"text":"[Supermann] Builds (stand where it should go, face the front):","color":"gold"}',
           'tellraw @s [{"text":"  /build_house","color":"aqua"},{"text":"  Cabin with fireplace","color":"gray"}]']
    for k, t in keys:
        lst.append(f'tellraw @s [{{"text":"  /build_{k}","color":"aqua"}},{{"text":"  {t}","color":"gray"}}]')
    lst.append('tellraw @s {"text":"Undo the last build: /build_undo","color":"gray"}')
    open(os.path.join(FUNC, "bp_list.mcfunction"), "w").write("\n".join(lst) + "\n")


def main():
    note = open(NOTE).read()
    links = re.findall(r"\[([^\]]+)\]\((https://www\.grabcraft\.com/[^)#\s]+)", note)
    keys = []
    for title, url in links:
        key = KEYS.get(title.strip().lower()) or re.sub(r"[^a-z0-9]", "", title.lower())
        try:
            pid, model = fetch_model(url)
        except Exception as e:   # noqa: BLE001
            print(f"{title}: ERROR {e}"); continue
        W, H, D, n, placed = build(key, title.strip(), model)
        if n > MAX_BLOCKS:
            print(f"/build_{key:16s} {title}: {n} blocks is too many, skipping"); os.remove(os.path.join(FUNC, "bp", f"{key}.mcfunction")); continue
        keys.append((key, title.strip()))
        print(f"/build_{key:16s} {title:28s} {W}x{H}x{D}, {placed}/{n} blocks")
    write_common()
    write_aliases(keys)
    if unknown:
        print("Unknown block names (skipped):")
        for n, c in sorted(unknown.items(), key=lambda kv: -kv[1]): print(f"  {c:5d} {n}")
    print("Run /reload in game. New commands work after ./mc.sh restart.")


if __name__ == "__main__":
    main()
