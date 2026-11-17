#!/usr/bin/env python3
"""Builds the Haven datapack (data/world/datapacks/haven) from the vanilla worldgen files in the server jar: the
biomes, trees and ores of Haven. The worlds themselves are made by the Creator plugin (HavenGenerator.kt).

Haven is a world of floating islands over an azure sea, with no night (a midnight sun that dips into a
sunset and comes back up), no monsters, many waterfalls and its own biomes. Its colours are azure, green, pearl and gold
(as red, black, grey and orange are the Nether's). The trees are vanilla trees with
other blocks and colours. Run it again after a Minecraft update: ./plugins-src/haven/gen.py
"""
import copy, glob, json, os, re, shutil, sys, zipfile

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUT = os.path.join(ROOT, "data", "world", "datapacks", "haven")
JAR = sorted(glob.glob(os.path.join(ROOT, "data", "versions", "*", "paper-*.jar")),
             key=lambda p: [int(x) for x in re.findall(r"\d+", os.path.basename(os.path.dirname(p)))])[-1]
NS = "haven"
jar = zipfile.ZipFile(JAR)
shutil.rmtree(os.path.join(OUT, "data"), ignore_errors=True)
written = {}


def vanilla(kind, name):
    return json.loads(jar.read(f"data/minecraft/{kind}/{name.removeprefix('minecraft:')}.json"))


def put(kind, name, data):
    path = os.path.join(OUT, "data", NS, kind, name + ".json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w") as f:
        json.dump(data, f, indent=2)
    written[f"{kind}/{name}"] = True
    return f"{NS}:{name}"


# --- Trees: vanilla features copied with other blocks ----------------------------------------------------

def swap(data, blocks):
    text = json.dumps(data)
    for a, b in blocks.items():
        text = text.replace(f'"{a}"', f'"{b}"')
    return json.loads(text)


def clone_feature(fid, blocks, tag):
    name = fid.removeprefix("minecraft:") + "_" + tag
    if f"worldgen/feature/{name}" in written:
        return f"{NS}:{name}"
    d = vanilla("worldgen/feature", fid)
    if d.get("type") in ("minecraft:random_selector", "minecraft:simple_random_selector"):
        for f in d.get("features", []):
            if isinstance(f, dict) and "feature" in f and "chance" in f:
                f["feature"] = clone_placed_ref(f["feature"], blocks, tag)
            else:
                d["features"] = [clone_placed_ref(x, blocks, tag) for x in d["features"]]
                break
        if "default" in d:
            d["default"] = clone_placed_ref(d["default"], blocks, tag)
    else:
        d = swap(d, blocks)
    return put("worldgen/feature", name, d)


def clone_placed_ref(ref, blocks, tag):
    if isinstance(ref, str):
        return clone_placed(ref, blocks, tag)
    ref = copy.deepcopy(ref)
    ref["feature"] = ref["feature"] if isinstance(ref["feature"], dict) else clone_feature(ref["feature"], blocks, tag)
    return swap(ref, blocks)


def clone_placed(pid, blocks, tag, count=None):
    name = pid.removeprefix("minecraft:") + "_" + tag
    if f"worldgen/placed_feature/{name}" in written:
        return f"{NS}:{name}"
    d = vanilla("worldgen/placed_feature", pid)
    if isinstance(d["feature"], str):
        d["feature"] = clone_feature(d["feature"], blocks, tag)
    d = swap(d, blocks)
    if count is not None:
        for p in d["placement"]:
            if p["type"] == "minecraft:count":
                p["count"] = count
    return put("worldgen/placed_feature", name, d)


# Pearl-white trunks, silver leaves, coral blossoms: vanilla blocks for now, a resource pack can change their look.
DAWN = {"minecraft:cherry_log": "minecraft:stripped_pale_oak_log", "minecraft:cherry_wood": "minecraft:stripped_pale_oak_wood"}
SILVER = {"minecraft:birch_leaves": "minecraft:pale_oak_leaves"}
OASIS = {"minecraft:mangrove_leaves": "minecraft:flowering_azalea_leaves"}

# The golden forest: giant fungi with golden stems and caps, lit by shroomlight (what the portal is made of).
fungus = vanilla("worldgen/feature", "crimson_fungus")
fungus.update(stem_state="minecraft:stripped_bamboo_block", hat_state="minecraft:honeycomb_block",
              decor_state="minecraft:shroomlight", valid_base_block="minecraft:grass_block")
put("worldgen/feature", "golden_fungus", fungus)
put("worldgen/placed_feature", "golden_fungi", {"feature": f"{NS}:golden_fungus", "placement": [
    {"type": "minecraft:count", "count": 3}, {"type": "minecraft:in_square"},
    {"type": "minecraft:heightmap", "heightmap": "WORLD_SURFACE_WG"}, {"type": "minecraft:biome"}]})

# Waterfalls: water springs in the island rock, many more than in the overworld.
put("worldgen/placed_feature", "springs", {"feature": "minecraft:spring_water", "placement": [
    {"type": "minecraft:count", "count": 90}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 40},
                                                  "max_inclusive": {"absolute": 250}}},
    {"type": "minecraft:biome"}]})

# Rainbow falls: three times the waterfalls (a feature may appear only once per biome, so it gets its own).
put("worldgen/placed_feature", "springs_rich", {"feature": "minecraft:spring_water", "placement": [
    {"type": "minecraft:count", "count": 180}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 40},
                                                  "max_inclusive": {"absolute": 250}}},
    {"type": "minecraft:biome"}]})

# Ores grow in Haven's pearl rock (calcite) and its diorite veins, not only in stone (see HavenGenerator.kt).
put("tags/block", "ore_replaceables", {"values": ["minecraft:calcite", "minecraft:diorite", "minecraft:stone",
                                                   "minecraft:granite", "minecraft:andesite"]})
ORE_SWAP = {"minecraft:stone_ore_replaceables": f"{NS}:ore_replaceables", "minecraft:height_specific_ore_replaceables": f"{NS}:ore_replaceables"}

# Gold in the mountains of the glory peaks.
put("worldgen/placed_feature", "gold_rich", {"feature": clone_feature("minecraft:ore_gold", ORE_SWAP, "haven"), "placement": [
    {"type": "minecraft:count", "count": 30}, {"type": "minecraft:in_square"},
    {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": 40},
                                                  "max_inclusive": {"absolute": 250}}},
    {"type": "minecraft:biome"}]})

# --- Ores: diamonds as common as gold in the Nether (they drop shards), rare havenite, pearl (nacre) in the peaks ---

def ore(name, state, size, count, lo, hi, discard=0.0):
    put("worldgen/feature", name, {"type": "minecraft:ore", "discard_chance_on_air_exposure": discard, "size": size,
        "targets": [{"state": state, "target": {"predicate_type": "minecraft:tag_match", "tag": f"{NS}:ore_replaceables"}}]})
    return put("worldgen/placed_feature", name, {"feature": f"{NS}:{name}", "placement": [
        {"type": "minecraft:count", "count": count}, {"type": "minecraft:in_square"},
        {"type": "minecraft:height_range", "height": {"type": "minecraft:uniform", "min_inclusive": {"absolute": lo}, "max_inclusive": {"absolute": hi}}},
        {"type": "minecraft:biome"}]})


DIAMONDS = ore("diamonds", "minecraft:diamond_ore", 8, 10, 34, 250)
HAVENITE = ore("havenite_debris", "minecraft:waxed_oxidized_chiseled_copper", 2, 1, 34, 120, discard=1.0)
NACRE = ore("nacre", "minecraft:pearlescent_froglight", 16, 10, 140, 250)
CORAL = ["minecraft:warm_ocean_vegetation", "minecraft:seagrass_warm", "minecraft:sea_pickle", "minecraft:kelp_warm"]

# --- Biomes ------------------------------------------------------------------------------------------

DROP = re.compile(r"lava|monster_room|ore_infested|freeze_top_layer|spring_water|fossil|sculk|dripstone")
SEA_LIFE = {
    "water_creature": [{"type": "minecraft:dolphin", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 2}, "weight": 2},
                       {"type": "minecraft:squid", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 4}, "weight": 1}],
    "water_ambient": [{"type": "minecraft:tropical_fish", "count": 8, "weight": 25},
                      {"type": "minecraft:pufferfish", "count": {"type": "minecraft:uniform", "min_inclusive": 1, "max_inclusive": 3}, "weight": 5}],
    "axolotls": [{"type": "minecraft:axolotl", "count": {"type": "minecraft:uniform", "min_inclusive": 2, "max_inclusive": 4}, "weight": 10}],
}


# Music: C418's creative music (floating, uplifting) where the land is open and golden, the cherry grove and meadow
# music in the gentle places, and shorter pauses than in ordinary worlds (3 to 8 minutes), so the music is part of Haven.
MUSIC = {
    "dawn_grove": "minecraft:music.overworld.cherry_grove", "rainbow_falls": "minecraft:music.overworld.cherry_grove",
    "flower_meadow": "minecraft:music.overworld.meadow", "silver_birch": "minecraft:music.overworld.meadow",
    "golden_forest": "minecraft:music.overworld.flower_forest", "cloud_jungle": "minecraft:music.overworld.jungle",
}


def biome(name, base, *, trees=None, extra=(), notrees=False, grass, foliage, sky="#a6d6ff", fog="#c4e2ff", water="#2aa7ff"):
    d = vanilla("worldgen/biome", base)
    att = d.setdefault("attributes", {})
    spawns = att.get("minecraft:gameplay/natural_mob_spawns", {"argument": {"spawn_costs": {}, "spawns_by_category": {}}, "modifier": "overlay"})
    cats = spawns["argument"]["spawns_by_category"]
    for k in ("monster", "ambient", "underground_water_creature"):
        cats.pop(k, None)
    cats.update(copy.deepcopy(SEA_LIFE))
    att["minecraft:gameplay/natural_mob_spawns"] = spawns
    att["minecraft:audio/background_music"] = {"default": {"sound": MUSIC.get(name, "minecraft:music.creative"),
                                                           "min_delay": 3600, "max_delay": 9600}}
    att["minecraft:visual/sky_color"] = sky
    att["minecraft:visual/fog_color"] = fog
    att["minecraft:visual/water_fog_color"] = water
    d["effects"] = {**d.get("effects", {}), "water_color": water, "grass_color": grass, "foliage_color": foliage}
    d["carvers"] = []
    d["has_precipitation"] = False
    d["temperature"] = 0.8
    steps = [[f for f in step if not DROP.search(f)] for step in d["features"]]
    # no dirt, gravel or grey stone blobs in the pearl rock; the ores grow in calcite too
    steps[6] = [f if not f.startswith("minecraft:ore_") else clone_placed(f, ORE_SWAP, "haven") for f in steps[6]
                if not re.search(r"ore_(dirt|gravel|granite|diorite|andesite|tuff)|underwater_magma", f)]
    while len(steps) < 11:
        steps.append([])
    if notrees:
        steps[9] = [f for f in steps[9] if "tree" not in f]
    for old, new in (trees or {}).items():
        steps[9] = [new if f == old else f for f in steps[9]]
    steps[8].append(f"{NS}:springs")
    steps[6] += [DIAMONDS, HAVENITE]
    steps[9] += CORAL
    for step, fid in extra:
        steps[step].append(fid)
    d["features"] = steps
    return put("worldgen/biome", name, d)


B = {
    "dawn_grove": biome("dawn_grove", "cherry_grove", grass="#a8e07a", foliage="#ffd1e8",
                        trees={"minecraft:trees_cherry": clone_placed("trees_cherry", DAWN, "dawn")}),
    "silver_birch": biome("silver_birch", "birch_forest", grass="#9fe39a", foliage="#d9e6e0",
                          trees={"minecraft:trees_birch": clone_placed("trees_birch", SILVER, "silver")}),
    "cloud_jungle": biome("cloud_jungle", "jungle", grass="#6fe06a", foliage="#5fe08a"),
    "oasis": biome("oasis", "mangrove_swamp", grass="#8fe39a", foliage="#ff9a8a", water="#19d3e0",
                   trees={"minecraft:trees_mangrove": clone_placed("trees_mangrove", OASIS, "oasis")}),
    "glory_peaks": biome("glory_peaks", "meadow", grass="#b5e88a", foliage="#d8c060",
                         extra=[(9, clone_placed("trees_old_growth_pine_taiga", {}, "glory", count=3)), (6, f"{NS}:gold_rich"), (6, NACRE)]),
    "golden_savanna": biome("golden_savanna", "savanna", grass="#e3d070", foliage="#f2c230"),
    "golden_forest": biome("golden_forest", "flower_forest", grass="#d6e07a", foliage="#f0c040",
                           extra=[(9, f"{NS}:golden_fungi")]),
    # Open plains where the barley fields are (the plugin sows them), a meadow of flowers, the crystal valley
    # (the plugin sets the light crystals) and the rainbow falls (water everywhere).
    "golden_plains": biome("golden_plains", "plains", grass="#d9e27a", foliage="#e8c547", notrees=True),
    "flower_meadow": biome("flower_meadow", "flower_forest", grass="#8fe38a", foliage="#9fe090", notrees=True),
    "crystal_valley": biome("crystal_valley", "meadow", grass="#a6e8c8", foliage="#bfe8d8", notrees=True),
    "rainbow_falls": biome("rainbow_falls", "cherry_grove", grass="#9fe39a", foliage="#ffd1e8",
                           trees={"minecraft:trees_cherry": clone_placed("trees_cherry", DAWN, "dawn")}, extra=[(8, f"{NS}:springs_rich")]),
}

# --- The worlds -----------------------------------------------------------------------------------------
# Haven worlds are not datapack dimensions (those all share the main world's seed and cannot be added while the
# server runs). The Creator plugin makes one for every world, <world>_haven, with its own seed and its own island
# generator (HavenGenerator.kt), and these biomes and features. The plugin also drives the midnight sun.

# --- The Lion ---------------------------------------------------------------------------------------------
# Its own wolf variant (the wolf's body, with the coat and mane drawn by lion.py into the resource pack). No spawn
# conditions: no ordinary wolf ever gets it; the plugin gives it to the Lion.
L = f"{NS}:entity/wolf/lion"
put("wolf_variant", "lion", {"assets": {"wild": L, "tame": L, "angry": L},
                             "baby_assets": {"wild": L, "tame": L, "angry": L}, "spawn_conditions": []})

with open(os.path.join(OUT, "pack.mcmeta"), "w") as f:
    json.dump({"pack": {"description": "Haven: the biomes, trees and ores of the Haven worlds",
                        "pack_format": 121, "min_format": [121, 0], "max_format": [999, 0]}}, f, indent=2)
print(f"Haven datapack written to {os.path.relpath(OUT, ROOT)} ({len(written)} files) from {os.path.relpath(JAR, ROOT)}")
