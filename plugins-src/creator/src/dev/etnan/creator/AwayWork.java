package dev.etnan.creator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

/** The work a helper does at home (or at a saved place) while its owner is logged out: the ten-second round in
 *  away(), and the jobs it runs: fields, fishing, smelting, animals, mob farms, the farm sorting, the routines, and
 *  the summary you get when you come back. */
final class AwayWork {
    final Helper core;

    AwayWork(Helper core) { this.core = core; }

    final Map<UUID, List<long[]>> awayHeld = new HashMap<>();      // home chunks kept loaded for work while the owner is away

    final Map<UUID, World> awayWorld = new HashMap<>();

    final Map<UUID, Long> awayTime = new HashMap<>();              // routines while away: the world time at the last look

    final Map<String, Long> awayRan = new HashMap<>();

    final Map<UUID, Map<String, Integer>> awaySeen = new HashMap<>();  // the counters at the last ten-minute line

    final Map<UUID, Integer> awayMinute = new HashMap<>();         // what it got since the last line in the log

    final Map<UUID, Integer> awayIdle = new HashMap<>();         // ten-second rounds in a row with nothing to do in the field

    /** Other players near the helper (or near home, when it works there without its figure): in the log at once,
     *  and then not again for ten minutes. */
    void awayMeet(UUID id, Location around) {
        long now = System.currentTimeMillis();
        for (Player other : around.getNearbyPlayers(16)) {
            if (other.getUniqueId().equals(id) || other.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Long last = core.awayTold.get(id + ":met:" + other.getUniqueId());
            if (last != null && now - last < 600_000) continue;
            core.awayTold.put(id + ":met:" + other.getUniqueId(), now);
            Location l = other.getLocation();
            core.awayLog(id, "met " + other.getName() + " at " + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
        }
    }

    void awayLet(UUID id) {
        List<long[]> held = awayHeld.remove(id);
        World w = awayWorld.remove(id);
        if (held != null && w != null) for (long[] c : held) w.removePluginChunkTicket((int) c[0], (int) c[1], core.plugin);
    }

    /** Every ten seconds, for everyone who is logged out with a job for their helper. */
    void away() {
        if (core.paused()) { for (UUID id : new ArrayList<>(awayHeld.keySet())) { core.awayLog(id, "paused"); awayLet(id); } return; }
        double hrs = core.conf.getDouble("settings.away-hours", 0);                 // 0 or less: no limit
        long max = hrs <= 0 ? Long.MAX_VALUE : (long) (hrs * 3_600_000L);
        boolean changed = false;
        for (String k : core.conf.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
            String job = core.conf.getString(core.path(id, "away.job"), "");
            if (job.isEmpty() && !core.conf.getBoolean(core.path(id, "away.rest"), false) && core.conf.getBoolean(core.path(id, "active")) && core.home(id) != null) {
                job = "home";                                              // nothing to do: the chores at home
                core.conf.set(core.path(id, "away.job"), job);
                core.conf.set(core.path(id, "watch"), true);
                changed = true;
            }
            if (core.conf.getBoolean(core.path(id, "away.full"), false) && !core.conf.contains(core.path(id, "away.why")) && core.home(id) != null && core.myBarrel(id, core.home(id).getWorld()) != null) {
                core.conf.set(core.path(id, "away.full"), null);                     // stopped because the chests were full, but there is a magic barrel now
                changed = true;
            }
            long ms = core.conf.getLong(core.path(id, "away.ms"), 0);
            if (Bukkit.getPlayer(id) == null && core.conf.getBoolean(core.path(id, "active")) && core.conf.contains(core.path(id, "routines"))) {
                Location rh = core.home(id);
                if (rh != null) awayRoutines(id, rh);
            }
            Location hm0 = job.isEmpty() || Bukkit.getPlayer(id) != null || !core.flag(id, "watch", false) || !core.conf.getBoolean(core.path(id, "active"))
                    || ms >= max || core.conf.getBoolean(core.path(id, "away.full"), false) ? null : core.home(id);
            Location hm = hm0 != null && job.equals("place") ? core.placeNamed(id, core.conf.getString(core.path(id, "away.place"), "")) : hm0;   // that job is done at the saved place, not at home
            if (hm != null && core.builder.sites.containsKey(id)) {                     // still building: that is finished first, then the job
                core.awayWarn(id, "building", "finishes the " + core.builder.sites.get(id).name + " at " + core.builder.sites.get(id).where + " first, then starts the job");
                continue;
            }
            if (hm0 != null && hm == null) core.awayWarn(id, "nofarm", "no place called " + core.conf.getString(core.path(id, "away.place"), "?") + " yet (remember <name> <x> <y> <z> [world])");
            if (hm == null) {
                if (awayHeld.containsKey(id)) {
                    String did = awayDid(id);
                    core.awayLog(id, "stopped (" + (Bukkit.getPlayer(id) != null ? "the owner is back" : ms >= max ? "the time is up" : core.conf.getBoolean(core.path(id, "away.full"), false) ? core.conf.getString(core.path(id, "away.why"), "bag and chests are full, no wood for a new chest") : "called off")
                            + ") after " + ms / 60_000 + " min" + (did.isEmpty() ? ", nothing done" : ": " + did));
                }
                awayLet(id);
                continue;
            }
            if (!awayHeld.containsKey(id)) {                              // keep home loaded (crops, furnaces)
                Map<String, Integer> was0 = new HashMap<>();
                for (String c0 : List.of("fish", "crops", "sown", "meal", "wool", "fed", "meat", "smelted", "xp", "mined", "explored", "maps", "chicks", "goldchicks", "mobs")) was0.put(c0, core.conf.getInt(core.path(id, "away." + c0), 0));
                awaySeen.put(id, was0);
                core.awayTold.keySet().removeIf(k0 -> k0.startsWith(id + ":warn:"));
                core.awayLog(id, "starts to " + (job.equals("place") ? "work at " + core.conf.getString(core.path(id, "away.place"), "the place") + " (" : (job.equals("home") ? "work" : job) + " at home (") + hm.getWorld().getName() + " " + hm.getBlockX() + " " + hm.getBlockY() + " " + hm.getBlockZ() + "), " + ms / 60_000 + " min worked so far");
                List<long[]> held = new ArrayList<>();
                for (int cx = (hm.getBlockX() - Helper.HOME) >> 4; cx <= (hm.getBlockX() + Helper.HOME) >> 4; cx++) for (int cz = (hm.getBlockZ() - Helper.HOME) >> 4; cz <= (hm.getBlockZ() + Helper.HOME) >> 4; cz++) {
                    long[] c = {cx, cz};
                    hm.getWorld().addPluginChunkTicket((int) c[0], (int) c[1], core.plugin);
                    held.add(c);
                }
                awayHeld.put(id, held);
                awayWorld.put(id, hm.getWorld());
            }
            Block c = hm.getBlock();
            int got = 0;
            switch (job) {
                case "fish" -> got = awayFish(id, hm);
                case "farm" -> got = awayFarm(id, hm);
                case "home" -> {                                          // a bit of everything, and the gates stay shut
                    int field = awayFarm(id, hm);
                    got = awayFish(id, hm) + field;
                    int idle = field > 0 ? 0 : awayIdle.merge(id, 1, Integer::sum);
                    if (field > 0) awayIdle.put(id, 0);
                    if (idle >= 3) core.bump(id, "explored", core.explorer.awayExplore(id, hm, true));   // the field is done: out exploring
                    if (ms % 60_000 == 0) {
                        got += awayAnimals(id, hm);
                        int out = awaySmelt(id, hm);
                        core.bump(id, "smelted", out);
                        got += out;
                    }
                    shutYard(yard(id, hm));
                }
                case "place" -> got = awayPlace(id, hm);
                case "smelt" -> core.bump(id, "smelted", got = awaySmelt(id, hm));
                case "mine" -> core.bump(id, "mined", got = core.explorer.awayMine(id, hm));
                case "explore" -> core.bump(id, "explored", got = core.explorer.awayExplore(id, hm, false));
                default -> { }
            }
            Inventory bag = core.bag(id);
            int free = 0;
            for (ItemStack it : bag.getStorageContents()) if (Helper.empty(it)) free++;
            if (free < 6 && !job.equals("smelt") && core.myBarrel(id, hm.getWorld()) != null) {      // nearly full, and you have a magic barrel: all of it in your own store
                int moved = core.storage.putMine(id, bag);
                core.dirty.add(id);
                if (moved > 0) core.awayLog(id, "put " + moved + " things in your own store in the magic barrel");
            } else if (free < 6 && !job.equals("smelt")) {                // nearly full: into the chests at home
                int moved = 0;
                List<Inventory> to = Helper.stores(hm, Helper.HOME);
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (Helper.empty(it)) continue;
                    int before = it.getAmount();
                    for (int pass = 0; pass < 2 && it.getAmount() > 0; pass++)
                        for (Inventory inv : to) {
                            if (pass == 0 && !inv.contains(it.getType())) continue;
                            Map<Integer, ItemStack> rest = inv.addItem(it.clone());
                            it.setAmount(rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
                            if (it.getAmount() == 0) break;
                        }
                    moved += before - it.getAmount();
                    bag.setItem(i, it.getAmount() > 0 ? it : null);
                }
                core.dirty.add(id);
                if (moved == 0 && free == 0 && !moreRoom(id, hm)) core.conf.set(core.path(id, "away.full"), true);       // bag and chests full: it stops
            }
            awayMeet(id, hm);
            if ((ms + 10_000) % 600_000 == 0) {                           // a line every ten minutes: what was done since the last one
                Map<String, Integer> was = awaySeen.computeIfAbsent(id, k2 -> new HashMap<>());
                List<String> did = new ArrayList<>();
                for (String[] c2 : new String[][] {{"fish", "fish"}, {"crops", "crops"}, {"sown", "sown"}, {"meal", "bone meal used"}, {"wool", "sheep sheared"}, {"fed", "animals fed"}, {"chicks", "chicks hatched"}, {"mobs", "monsters killed"}, {"goldchicks", "golden chicks"},
                        {"meat", "slaughtered"}, {"smelted", "smelted"}, {"xp", "experience for you"}, {"mined", "ore blocks"}, {"explored", "new chunks"}, {"maps", "treasure maps"}}) {
                    int now2 = core.conf.getInt(core.path(id, "away." + c2[0]), 0), d = now2 - was.getOrDefault(c2[0], 0);
                    was.put(c2[0], now2);
                    if (d > 0) did.add(c2[1] + " " + d);
                }
                core.awayLog(id, "last 10 min (" + (job.equals("home") ? "work" : job) + "): " + (did.isEmpty() ? "nothing done" : String.join(", ", did)) + ". " + (ms + 10_000) / 60_000 + " min in all");
            }
            if (ms % 300_000 == 0 && ms > 0) core.chores.tidy(id);
            core.conf.set(core.path(id, "away.ms"), ms + 10_000);
            changed = true;
        }
        if (changed && core.tick % 1200 == 0) { core.flushBags(); core.save(); }         // once a minute; shutdown() and pause save too
    }

    /** Done things out of the furnaces at home into the chests there; ores and raw food from the bag and the chests
     *  into empty furnaces (raw iron, gold and copper also from your magic barrel), and fuel the same way. Returns how many things came out. */
    int awaySmelt(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        List<Inventory> chests = Helper.stores(hm, Helper.HOME);
        from.addAll(chests);
        int took = 0;
        for (FurnaceInventory f : Helper.furnaces(hm, Helper.HOME)) {
            ItemStack done = f.getResult();
            if (!Helper.empty(done)) {
                int before = done.getAmount(), left = before;
                for (Inventory inv : chests) {
                    ItemStack rest = done.clone();
                    rest.setAmount(left);
                    Map<Integer, ItemStack> over = inv.addItem(rest);
                    left = over.isEmpty() ? 0 : over.values().iterator().next().getAmount();
                    if (left == 0) break;
                }
                done.setAmount(left);
                f.setResult(left > 0 ? done : null);
                took += before - left;
            }
            if (Helper.empty(f.getSmelting()))
                load:
                for (Inventory inv : from) for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (Helper.empty(it) || !f.canSmelt(it)) continue;
                    String n = it.getType().name();
                    if (!(n.startsWith("RAW_") || n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || it.getType().isEdible())) continue;
                    f.setSmelting(it.clone());
                    inv.setItem(i, null);
                    break load;
                }
            if (Helper.empty(f.getSmelting()) && Helper.empty(f.getResult()) && core.storage != null) {   // nothing in the bag or the chests: raw ore from your own store in the magic barrel
                ItemStack ore = core.storage.takeMine(id, Helper.RAW_ORES::contains, 64);
                if (ore != null) f.setSmelting(ore);
            }
            if (!Helper.empty(f.getSmelting()) && Helper.empty(f.getFuel()))
                fuel:
                for (Inventory inv : from) for (int i = 0; i < inv.getSize(); i++) {
                    ItemStack it = inv.getItem(i);
                    if (Helper.empty(it) || !Helper.FUELS.contains(it.getType())) continue;
                    ItemStack one = it.clone();
                    if (it.getType() != Material.LAVA_BUCKET) { one.setAmount(Math.min(it.getAmount(), 8)); it.setAmount(it.getAmount() - one.getAmount()); inv.setItem(i, it.getAmount() > 0 ? it : null); }
                    else inv.setItem(i, null);
                    f.setFuel(one);
                    break fuel;
                }
            if (!Helper.empty(f.getSmelting()) && Helper.empty(f.getFuel()) && core.storage != null) {      // none in the bag or the chests: from your own store in the magic barrel
                ItemStack one = core.storage.takeMine(id, m -> Helper.FUELS.contains(m) && m != Material.LAVA_BUCKET, 8);
                if (one != null) f.setFuel(one);
            }
            if (!Helper.empty(f.getSmelting()) && Helper.empty(f.getFuel())) core.awayWarn(id, "fuel", "a furnace at home has no fuel (coal, charcoal, blaze rods, a lava bucket or dried kelp blocks in a chest or your magic barrel)");
        }
        core.dirty.add(id);
        core.xp(id, (int) Math.round(took * 0.35));                            // about what a furnace gives per thing
        return took;
    }

    /** What there is to tend within 100 blocks of home: looked up once every five minutes (a look through every
     *  block there is too much to do every ten seconds). */
    static final class Yard {
        final List<Block> soil = new ArrayList<>(), doors = new ArrayList<>();
        Block water;
        long at;
    }

    final Map<UUID, Yard> yards = new HashMap<>();

    Yard yard(UUID id, Location hm) {
        Yard y = yards.get(id);
        long now = System.currentTimeMillis();
        if (y != null && now - y.at < 300_000) return y;
        y = new Yard();
        y.at = now;
        World w = hm.getWorld();
        int hx = hm.getBlockX(), hy = hm.getBlockY(), hz = hm.getBlockZ();
        int lo = Math.max(w.getMinHeight(), hy - 16), hi = Math.min(w.getMaxHeight() - 2, hy + 16);
        double best = Double.MAX_VALUE;
        for (int cx = (hx - Helper.HOME) >> 4; cx <= (hx + Helper.HOME) >> 4; cx++) for (int cz = (hz - Helper.HOME) >> 4; cz <= (hz + Helper.HOME) >> 4; cz++) {
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                int bx = cx * 16 + x, bz = cz * 16 + z;
                if (Math.abs(bx - hx) > Helper.HOME || Math.abs(bz - hz) > Helper.HOME) continue;
                for (int yy = lo; yy <= hi; yy++) {
                    Material t = snap.getBlockType(x, yy, z);
                    if (t.isAir() || t == Material.STONE || t == Material.DIRT || t == Material.GRASS_BLOCK || t == Material.DEEPSLATE) continue;
                    if (t == Material.FARMLAND || t == Material.SOUL_SAND && snap.getBlockType(x, yy + 1, z) == Material.NETHER_WART) y.soil.add(w.getBlockAt(bx, yy, bz));
                    else if (Tag.FENCE_GATES.isTagged(t) || Tag.DOORS.isTagged(t) && t != Material.IRON_DOOR) y.doors.add(w.getBlockAt(bx, yy, bz));
                    else if (t == Material.WATER && snap.getBlockType(x, yy + 1, z).isAir()) {
                        double d = (bx - hx) * (double) (bx - hx) + (bz - hz) * (double) (bz - hz);
                        if (d < best) { best = d; y.water = w.getBlockAt(bx, yy, bz); }
                    }
                }
            }
        }
        yards.put(id, y);
        return y;
    }

    /** Shuts the open doors and gates of the yard that no player stands by. */
    int shutYard(Yard y) {
        int n = 0;
        for (Block b : y.doors) {
            if (!(b.getBlockData() instanceof org.bukkit.block.data.Openable o) || !o.isOpen()) continue;
            Location at = b.getLocation().add(0.5, 0.5, 0.5);
            if (!at.getNearbyPlayers(5).isEmpty()) continue;
            o.setOpen(false);
            b.setBlockData(o);
            boolean door = Tag.DOORS.isTagged(b.getType());
            b.getWorld().playSound(at, door ? Sound.BLOCK_WOODEN_DOOR_CLOSE : Sound.BLOCK_FENCE_GATE_CLOSE, 0.7f, 1f);
            if (!door || !(o instanceof org.bukkit.block.data.Bisected half) || half.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM) n++;
        }
        return n;
    }

    int awayFish(UUID id, Location hm) {
        Block b = yard(id, hm).water;
        if (b == null || b.getType() != Material.WATER) { core.awayWarn(id, "water", "no open water within 100 blocks of home, so no fishing"); return 0; }
        Collection<ItemStack> loot = core.chores.fishLoot(b.getLocation());
        core.awayNote(id, loot);
        int n = core.addToBag(id, loot);
        core.bump(id, "fish", n);
        core.xp(id, 1 + ThreadLocalRandom.current().nextInt(6));
        return n;
    }

    /** One bone meal from the bag or the chests; when there is none, a bone is ground into three. */
    boolean boneMeal(UUID id, List<Inventory> from) {
        if (Helper.count(from, m -> m == Material.BONE_MEAL) < 1) {
            if (Helper.count(from, m -> m == Material.BONE) < 1) return false;
            Helper.take(from, m -> m == Material.BONE, 1);
            core.chores.hand(id, null, new ItemStack(Material.BONE_MEAL, 3));
        }
        Helper.take(from, m -> m == Material.BONE_MEAL, 1);
        core.dirty.add(id);
        return true;
    }

    /** The fields within 100 blocks of home: ripe plants are harvested and set again, empty farmland is sown with what
     *  there is in the bag and the chests, and what is still growing gets bone meal (made from bones when needed). */
    int awayFarm(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        from.addAll(Helper.stores(hm, Helper.HOME));
        int n = 0, sown = 0, meal = 0;
        for (Block soil : yard(id, hm).soil) {
            if (n >= 24 && sown >= 16 && meal >= 8) break;
            Block b = soil.getRelative(BlockFace.UP);
            Material t = b.getType();
            if (Helper.SEEDS.containsKey(t) && b.getBlockData() instanceof Ageable a) {
                if (a.getAge() >= a.getMaximumAge()) {
                    if (n >= 24) continue;
                    Collection<ItemStack> crop = core.chores.harvest(b);
                    core.awayNote(id, crop);
                    core.addToBag(id, crop);
                    n++;
                } else if (meal < 8 && t != Material.NETHER_WART) {
                    if (!boneMeal(id, from)) { core.awayWarn(id, "meal", "out of bone meal and bones, the fields grow at their own speed now"); meal = 8; continue; }
                    b.applyBoneMeal(BlockFace.UP);
                    meal++;
                }
            } else if (soil.getType() == Material.FARMLAND && sown < 16 && t.isAir()) {
                for (Map.Entry<Material, Material> e : Helper.SEEDS.entrySet()) {
                    Material seed = e.getValue();
                    if (e.getKey() == Material.NETHER_WART || Helper.count(from, m -> m == seed) < 1) continue;
                    Helper.take(from, m -> m == seed, 1);
                    b.setType(e.getKey());
                    core.dirty.add(id);
                    sown++;
                    break;
                }
                if (b.getType().isAir()) core.awayWarn(id, "seed", "there is empty farmland and nothing to sow in the bag or the chests");
            }
        }
        core.bump(id, "crops", n);
        core.bump(id, "sown", sown);
        core.bump(id, "meal", meal);
        return n + sown;
    }

    /** Work at a saved place: it looks at what is there. Farmland within 32 blocks: the fields are tended as at home.
     *  Else a spawner or dark, roofed floors: the mob farm is run. Else open water within 32 blocks: it fishes. */
    int awayPlace(UUID id, Location at) {
        Yard y = yard(id, at);
        String name = core.conf.getString(core.path(id, "away.place"), "the place"), see = null;
        boolean fields = false;
        for (Block b : y.soil) if (Math.abs(b.getX() - at.getBlockX()) <= 32 && Math.abs(b.getZ() - at.getBlockZ()) <= 32) { fields = true; break; }
        int got = 0;
        if (core.farms(id).contains(Helper.slug(name))) { see = "my mob farm"; got = Math.max(0, awayMobs(id, at, true)); }
        else if (fields) { see = "fields"; got = awayFarm(id, at); }
        else if ((got = awayMobs(id, at, false)) >= 0) see = "a mob farm";
        else {
            got = 0;
            if (y.water != null && Math.abs(y.water.getX() - at.getBlockX()) <= 32 && Math.abs(y.water.getZ() - at.getBlockZ()) <= 32) { see = "water to fish in"; got = awayFish(id, at); }
        }
        if (core.awayTold.putIfAbsent(id + ":warn:see", System.currentTimeMillis()) == null)
            core.awayLog(id, see == null ? "NOTE: I see nothing to do at " + name + ": no fields, no mob farm and no water" : "at " + name + " I see " + see + ", so that is my job here");
        return got;
    }

    /** True when a hopper empties into this chest: it is where the farm delivers, not a place to keep things. */
    static boolean fed(Block chest) {
        for (BlockFace f : new BlockFace[] {BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block n = chest.getRelative(f);
            if (n.getBlockData() instanceof org.bukkit.block.data.type.Hopper hp && n.getRelative(hp.getFacing()).equals(chest)) return true;
        }
        return false;
    }

    /** Once a minute at the mob farm: what the hoppers have delivered (and the loot in the bag) goes into the other
     *  chests within 16 blocks, each kind of loot in chests of its own; when there is no room, a new chest is made
     *  (eight planks or two logs) and set down next to them. Returns how many things were moved. */
    int sortFarm(UUID id, Location at) {
        List<Inventory> inbox = new ArrayList<>(), keep = new ArrayList<>();
        for (Inventory inv : Helper.stores(at, 16)) {
            boolean in = false;
            if (inv.getHolder() instanceof org.bukkit.block.DoubleChest dc) {
                if (dc.getLeftSide() instanceof org.bukkit.block.Chest a && fed(a.getBlock())) in = true;
                if (dc.getRightSide() instanceof org.bukkit.block.Chest b && fed(b.getBlock())) in = true;
            } else if (inv.getLocation() != null) in = fed(inv.getLocation().getBlock());
            (in ? inbox : keep).add(inv);
        }
        List<Inventory> sources = new ArrayList<>(inbox);
        Inventory bag = core.bag(id);
        sources.add(bag);
        int moved = 0;
        for (Inventory src : sources) for (int i = 0; i < src.getSize(); i++) {
            ItemStack it = src.getItem(i);
            if (Helper.empty(it)) continue;
            String kind = Helper.lootKind(it);
            if (src == bag && kind.equals("other")) continue;                // its own things stay in the bag
            int before = it.getAmount();
            ItemStack rest = it.clone();
            for (int pass = 0; pass < 3 && rest != null; pass++) {
                if (pass == 2) { Inventory fresh = farmChest(id, at, keep, inbox); if (fresh == null) break; keep.add(fresh); }
                for (Inventory to : keep) {
                    boolean same = false, bare = true;
                    for (ItemStack c : to.getContents()) if (!Helper.empty(c)) { bare = false; if (Helper.lootKind(c).equals(kind)) { same = true; break; } }
                    if (pass == 0 ? !same : !bare) continue;
                    Map<Integer, ItemStack> left = to.addItem(rest);
                    rest = left.isEmpty() ? null : left.values().iterator().next();
                    if (rest == null) break;
                }
            }
            int gone = before - (rest == null ? 0 : rest.getAmount());
            if (gone > 0) { src.setItem(i, rest); moved += gone; if (src == bag) core.dirty.add(id); }
            if (rest != null) { core.awayWarn(id, "farmfull", "the chests at the mob farm are full and I have no wood for a new one (eight planks or two logs in a chest there)"); core.bump(id, "sorted", moved); return moved; }
        }
        core.bump(id, "sorted", moved);
        return moved;
    }

    /** A new chest by the storage chests of the mob farm. Null when there is no wood or no free spot. */
    Inventory farmChest(UUID id, Location at, List<Inventory> keep, List<Inventory> inbox) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        from.addAll(keep);
        from.addAll(inbox);
        boolean planks = Helper.count(from, Tag.PLANKS::isTagged) >= 8;
        if (!planks && Helper.count(from, Tag.LOGS::isTagged) < 2) return null;
        Block spot = null;
        List<Inventory> by = new ArrayList<>(keep);
        by.addAll(inbox);
        find:
        for (Inventory inv : by) {
            if (inv.getLocation() == null) continue;
            for (int up = 0; up <= 1; up++) for (BlockFace f : Helper.SIDES) {
                Block b = inv.getLocation().getBlock().getRelative(f).getRelative(0, up, 0);
                if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && (b.getRelative(BlockFace.DOWN).getType().isSolid() || up == 1 && b.getRelative(BlockFace.DOWN).getType() == Material.CHEST)
                        && !fed(b) && b.getLocation().getNearbyPlayers(1.5).isEmpty()) { spot = b; break find; }
            }
        }
        if (spot == null) return null;
        if (planks) Helper.take(from, Tag.PLANKS::isTagged, 8); else Helper.take(from, Tag.LOGS::isTagged, 2);
        core.dirty.add(id);
        spot.setType(Material.CHEST);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1f);
        core.awayLog(id, "made a new chest at the mob farm at " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
        if (finds.size() < 30) { finds.add("a new chest I made at the mob farm at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()); core.conf.set(core.path(id, "away.finds"), finds); }
        return spot.getState() instanceof org.bukkit.block.Chest c ? c.getInventory() : null;
    }

    static final class MobYard { final List<Block> floors = new ArrayList<>(); EntityType kind; long at; int rounds; }

    final Map<UUID, MobYard> mobYards = new HashMap<>();

    /** The mob farm at the saved place "mobfarm". No monsters come while nobody is logged in, so the helper calls
     *  them itself: three every ten seconds on the dark, roofed floors within 24 blocks (from the level of the place
     *  and 48 blocks up), the kind of a spawner when there is one within 24 blocks. The farm does the rest; what gets
     *  within 8 blocks of the place is killed, the drops go in the bag and the experience to the owner. */
    int awayMobs(UUID id, Location at, boolean sure) {
        World w = at.getWorld();
        MobYard y = mobYards.get(id);
        long now = System.currentTimeMillis();
        if (y == null || now - y.at > 300_000) {
            y = new MobYard();
            y.at = now;
            int hx = at.getBlockX(), hy = at.getBlockY(), hz = at.getBlockZ();
            int lo = Math.max(w.getMinHeight() + 1, hy - 1), hi = Math.min(w.getMaxHeight() - 3, hy + 48);
            for (int cx = (hx - 24) >> 4; cx <= (hx + 24) >> 4; cx++) for (int cz = (hz - 24) >> 4; cz <= (hz + 24) >> 4; cz++) {
                if (!w.isChunkGenerated(cx, cz)) continue;
                org.bukkit.Chunk ch = w.getChunkAt(cx, cz);
                for (org.bukkit.block.BlockState st : ch.getTileEntities()) if (st instanceof org.bukkit.block.CreatureSpawner sp && sp.getSpawnedType() != null
                        && Math.abs(st.getX() - hx) <= 24 && Math.abs(st.getZ() - hz) <= 24) y.kind = sp.getSpawnedType();
                org.bukkit.ChunkSnapshot snap = ch.getChunkSnapshot();
                for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
                    int bx = cx * 16 + x, bz = cz * 16 + z;
                    if (Math.abs(bx - hx) > 24 || Math.abs(bz - hz) > 24 || Math.abs(bx - hx) <= 8 && Math.abs(bz - hz) <= 8) continue;
                    for (int yy = lo; yy <= hi; yy++)
                        if (snap.getBlockType(x, yy, z).isAir() && snap.getBlockType(x, yy + 1, z).isAir() && snap.getBlockType(x, yy - 1, z).isOccluding()
                                && snap.getBlockEmittedLight(x, yy, z) == 0 && snap.getBlockSkyLight(x, yy, z) == 0) y.floors.add(w.getBlockAt(bx, yy, bz));
                }
            }
            mobYards.put(id, y);
        }
        if (!sure && y.kind == null && y.floors.size() < 64) return -1;              // no spawner and no dark floors to speak of: not a mob farm
        int got = 0, alive = 0, killed = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        List<org.bukkit.entity.Monster> prey = new ArrayList<>();
        for (Entity e : w.getNearbyEntities(at, 32, 56, 32)) {
            if (e instanceof Item item && item.getTicksLived() > 20 && Math.abs(e.getLocation().getX() - at.getX()) <= 8 && Math.abs(e.getLocation().getZ() - at.getZ()) <= 8 && Math.abs(e.getLocation().getY() - at.getY()) <= 6) {
                List<ItemStack> one = List.of(item.getItemStack());
                if (core.addToBag(id, one) == item.getItemStack().getAmount()) { core.awayNote(id, one); item.remove(); got++; }
                continue;
            }
            if (!(e instanceof org.bukkit.entity.Monster m) || m.customName() != null) continue;
            Location l = m.getLocation();
            if (Math.abs(l.getX() - at.getX()) <= 8 && Math.abs(l.getZ() - at.getZ()) <= 8 && Math.abs(l.getY() - at.getY()) <= 6) {
                if (prey.size() < 12) prey.add(m);
            } else if (m.getScoreboardTags().contains("helper_farm") && m.getTicksLived() > 6000) m.remove();   // never got to the killing room
            else alive++;
        }
        if (!prey.isEmpty()) {                                               // the sword: a blow every 0.6 s for the next nine seconds, until they are dead
            int[] blows = {0};
            Bukkit.getScheduler().runTaskTimer(core.plugin, task -> {
                prey.removeIf(m -> m.isDead() || !m.isValid());
                if (blows[0]++ >= 15 || prey.isEmpty() || Bukkit.getPlayer(id) != null || core.paused()) { task.cancel(); return; }
                org.bukkit.entity.Monster m = prey.get(0);
                m.addScoreboardTag("helper_farm");
                Helper.H hh = core.helpers.get(id);
                if (hh != null && !hh.merged && hh.w != null && hh.w.isValid() && hh.w.getWorld() == m.getWorld()) {
                    m.damage(10.0, hh.w);                                 // onKill counts it and gives the experience
                } else {
                    m.damage(10.0);
                    if (m.isDead()) { core.xp(id, 5); core.bump(id, "mobs", 1); }
                }
                m.getWorld().playSound(m.getLocation(), Sound.ENTITY_PLAYER_ATTACK_SWEEP, 0.7f, 1f);
            }, 4L, 12L);
        }
        if (y.rounds++ % 6 == 0) got += sortFarm(id, at);
        if (y.floors.isEmpty()) core.awayWarn(id, "nofloor", "no dark, roofed floors within 24 blocks of the mob farm (from its level and up), so I cannot call any monsters there");
        else if (alive < 40) for (int i = 0; i < 3; i++) {
            Block b = y.floors.get(r.nextInt(y.floors.size()));
            if (!b.getType().isAir() || !b.getRelative(BlockFace.DOWN).getType().isOccluding()) continue;
            Entity mob = w.spawnEntity(b.getLocation().add(0.5, 0, 0.5), y.kind != null ? y.kind : Helper.MOBS[r.nextInt(Helper.MOBS.length)]);
            mob.addScoreboardTag("helper_farm");
        }
        return got + prey.size();
    }

    /** Once a minute for the cows, pigs, sheep and chickens within 100 blocks of home: sheep are shorn, a herd under
     *  twelve gets fed two and two (so there are young), a herd over twelve grown ones loses up to two (meat, leather
     *  and feathers into the bag; never the young, the named or the leashed), and what lies on the ground is picked up. */
    int awayAnimals(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        from.addAll(Helper.stores(hm, Helper.HOME));
        Map<EntityType, List<Animals>> herds = new HashMap<>();
        int got = 0;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        GoldenChicken gc = GoldenChicken.instance;
        Collection<Entity> near = hm.getWorld().getNearbyEntities(hm, Helper.HOME, 16, Helper.HOME);
        List<org.bukkit.entity.Chicken> gold = new ArrayList<>();
        int hens = 0;
        for (Entity e : near) if (e instanceof org.bukkit.entity.Chicken c) {
            hens++;
            if (gc != null && gc.isGolden(c)) { gold.add(c); core.goldSeen(id, c, null); }
        }
        for (Entity e : near) {
            if (e instanceof Item egg && egg.getTicksLived() > 100 && hens < 300 && (egg.getItemStack().getType() == Material.EGG
                    || egg.getItemStack().getType() == Material.BLUE_EGG || egg.getItemStack().getType() == Material.BROWN_EGG)) {
                boolean golden = gc != null && gc.isEgg(egg.getItemStack());
                if (!golden || gold.size() < 12) {                           // cracked where it lies: one in eight hatches, now and then four
                    int chicks = 0;
                    for (int i = 0; i < egg.getItemStack().getAmount(); i++) if (r.nextInt(8) == 0) chicks += r.nextInt(32) == 0 ? 4 : 1;
                    Location at = egg.getLocation();
                    for (int i = 0; i < chicks; i++) {
                        org.bukkit.entity.Chicken c = at.getWorld().spawn(at, org.bukkit.entity.Chicken.class);
                        c.setBaby();
                        if (golden) { gc.makeGolden(c); gold.add(c); core.goldSeen(id, c, "hatched"); }
                    }
                    at.getWorld().playSound(at, Sound.ENTITY_EGG_THROW, 0.6f, 1f);
                    core.bump(id, "eggs", egg.getItemStack().getAmount());
                    core.bump(id, golden ? "goldchicks" : "chicks", chicks);
                    hens += chicks;
                    got++;
                    egg.remove();
                    continue;
                }
            }
            if (e instanceof Item item && item.getTicksLived() > 100) {      // what the animals dropped (golden eggs too, once there are twelve golden chickens)
                List<ItemStack> one = List.of(item.getItemStack());
                if (core.addToBag(id, one) == item.getItemStack().getAmount()) { core.awayNote(id, one); item.remove(); got++; }
                continue;
            }
            if (e instanceof Animals a && Helper.MEAT.containsKey(e.getType()) && a.customName() == null && !a.isLeashed()) herds.computeIfAbsent(e.getType(), k -> new ArrayList<>()).add(a);
        }
        if (gold.size() < 30) {                                              // golden chickens are never slaughtered, and two golden parents give a golden chick
            List<org.bukkit.entity.Chicken> ready = new ArrayList<>();
            for (org.bukkit.entity.Chicken c : gold) if (c.isAdult() && c.canBreed() && !c.isLoveMode() && ready.size() < 2) ready.add(c);
            if (ready.size() == 2) {
                Material food = null;
                for (Material f : Helper.FODDER) if (ready.get(0).isBreedItem(f) && Helper.count(from, m -> m == f) >= 2) { food = f; break; }
                if (food == null) core.awayWarn(id, "goldfood", "no seeds for the golden chickens (wheat, beetroot, melon or pumpkin seeds in a chest and I will breed them)");
                else {
                    Material fd = food;
                    Helper.take(from, m -> m == fd, 2);
                    core.dirty.add(id);
                    for (org.bukkit.entity.Chicken c : ready) c.setLoveModeTicks(600);
                    core.bump(id, "fed", 2);
                    got += 2;
                }
            }
        }
        for (Map.Entry<EntityType, List<Animals>> herd : herds.entrySet()) {
            List<Animals> grown = new ArrayList<>();
            for (Animals a : herd.getValue()) if (a.isAdult()) grown.add(a);
            for (Animals a : grown) if (a instanceof Sheep sh && !sh.isSheared()) {
                sh.setSheared(true);
                org.bukkit.DyeColor col = sh.getColor();
                Material wool = Material.matchMaterial((col == null ? "WHITE" : col.name()) + "_WOOL");
                List<ItemStack> w = List.of(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + r.nextInt(3)));
                core.awayNote(id, w);
                core.addToBag(id, w);
                core.bump(id, "wool", 1);
                got++;
            }
            if (grown.size() > 12) {
                for (int i = 0; i < Math.min(2, grown.size() - 12); i++) {
                    Animals a = grown.get(grown.size() - 1 - i);
                    List<ItemStack> meat = new ArrayList<>(List.of(new ItemStack(Helper.MEAT.get(herd.getKey()), 2 + r.nextInt(3))));
                    if (a instanceof Cow) meat.add(new ItemStack(Material.LEATHER, 1 + r.nextInt(2)));
                    if (herd.getKey() == EntityType.CHICKEN) meat.add(new ItemStack(Material.FEATHER, 1 + r.nextInt(2)));
                    core.awayNote(id, meat);
                    core.addToBag(id, meat);
                    a.remove();
                    core.xp(id, 1 + r.nextInt(3));
                    core.bump(id, "meat", 1);
                    got++;
                }
                int gone = Math.min(2, grown.size() - 12);
                core.awayLog(id, "slaughtered " + gone + " " + herd.getKey().getKey().getKey() + ", " + (grown.size() - gone) + " grown left");
            } else if (herd.getValue().size() < 12) {
                List<Animals> ready = new ArrayList<>();
                for (Animals a : grown) if (a.canBreed() && !a.isLoveMode() && ready.size() < 2) ready.add(a);
                if (ready.size() < 2) continue;
                Material food = null;
                for (Material f : Helper.FODDER) if (ready.get(0).isBreedItem(f) && Helper.count(from, m -> m == f) >= 2) { food = f; break; }
                if (food == null) continue;
                Material fd = food;
                Helper.take(from, m -> m == fd, 2);
                core.dirty.add(id);
                for (Animals a : ready) a.setLoveModeTicks(600);
                core.xp(id, 1 + r.nextInt(7));
                core.bump(id, "fed", 2);
                got += 2;
            }
        }
        return got;
    }

    /** Bag and chests full: a new chest from eight planks (or two logs) out of the chests, set down next to one of
     *  the chests at home. False when there is no wood or no free spot. */
    boolean moreRoom(UUID id, Location hm) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        List<Inventory> chests = Helper.stores(hm, Helper.HOME);
        from.addAll(chests);
        boolean planks = Helper.count(from, Tag.PLANKS::isTagged) >= 8;
        if (!planks && Helper.count(from, Tag.LOGS::isTagged) < 2) return false;
        Block spot = null;
        find:
        for (Inventory inv : chests) {
            Location l = inv.getLocation();
            if (l == null) continue;
            for (BlockFace f : Helper.SIDES) {
                Block b = l.getBlock().getRelative(f);
                if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid()) { spot = b; break find; }
            }
        }
        if (spot == null) for (int r = 1; r <= 4 && spot == null; r++) for (int x = -r; x <= r && spot == null; x++) for (int z = -r; z <= r && spot == null; z++) {
            Block b = hm.getBlock().getRelative(x, 0, z);
            if (b.getType().isAir() && b.getRelative(BlockFace.UP).getType().isAir() && b.getRelative(BlockFace.DOWN).getType().isSolid() && b.getLocation().getNearbyPlayers(1.5).isEmpty()) spot = b;
        }
        if (spot == null) return false;
        if (planks) Helper.take(from, Tag.PLANKS::isTagged, 8); else Helper.take(from, Tag.LOGS::isTagged, 2);
        core.dirty.add(id);
        spot.setType(Material.CHEST);
        spot.getWorld().playSound(spot.getLocation(), Sound.BLOCK_WOOD_PLACE, 0.8f, 1f);
        core.awayLog(id, "everything was full, so it made a new chest at " + spot.getX() + " " + spot.getY() + " " + spot.getZ());
        List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
        if (finds.size() < 30) { finds.add("a new chest I made at " + spot.getX() + " " + spot.getY() + " " + spot.getZ()); core.conf.set(core.path(id, "away.finds"), finds); }
        return true;
    }

    String awayDid(UUID id) {
        int fish = core.conf.getInt(core.path(id, "away.fish"), 0), crops = core.conf.getInt(core.path(id, "away.crops"), 0), smelted = core.conf.getInt(core.path(id, "away.smelted"), 0);
        List<String> did = new ArrayList<>();
        if (fish > 0) did.add("caught " + fish + (fish == 1 ? " thing" : " things") + " fishing");
        if (crops > 0) did.add("harvested " + crops + (crops == 1 ? " plant" : " plants"));
        if (smelted > 0) did.add("took " + smelted + (smelted == 1 ? " thing" : " things") + " out of the furnaces");
        int sown = core.conf.getInt(core.path(id, "away.sown"), 0), meal = core.conf.getInt(core.path(id, "away.meal"), 0), fed = core.conf.getInt(core.path(id, "away.fed"), 0),
                meat = core.conf.getInt(core.path(id, "away.meat"), 0), wool = core.conf.getInt(core.path(id, "away.wool"), 0);
        if (sown > 0) did.add("sowed " + sown + (sown == 1 ? " plant" : " plants"));
        if (meal > 0) did.add("used " + meal + " bone meal");
        if (fed > 0) did.add("fed " + fed + " animals");
        if (wool > 0) did.add("sheared " + wool + (wool == 1 ? " sheep" : " sheep"));
        if (meat > 0) did.add("slaughtered " + meat + (meat == 1 ? " animal" : " animals"));
        int mined = core.conf.getInt(core.path(id, "away.mined"), 0), explored = core.conf.getInt(core.path(id, "away.explored"), 0);
        if (mined > 0) did.add("dug out " + mined + " blocks of " + core.conf.getString(core.path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' '));
        int eggs = core.conf.getInt(core.path(id, "away.eggs"), 0), chicks = core.conf.getInt(core.path(id, "away.chicks"), 0), goldchicks = core.conf.getInt(core.path(id, "away.goldchicks"), 0);
        if (eggs > 0) did.add("cracked " + eggs + (eggs == 1 ? " egg" : " eggs") + " (" + chicks + (chicks == 1 ? " chick" : " chicks") + ")");
        if (goldchicks > 0) did.add("hatched " + goldchicks + " golden " + (goldchicks == 1 ? "chick" : "chicks"));
        int sorted = core.conf.getInt(core.path(id, "away.sorted"), 0);
        if (sorted > 0) did.add("sorted " + sorted + " things into the chests at the mob farm");
        int mobs = core.conf.getInt(core.path(id, "away.mobs"), 0);
        if (mobs > 0) did.add("killed " + mobs + (mobs == 1 ? " monster" : " monsters") + " at the mob farm");
        int earned = core.conf.getInt(core.path(id, "away.xp"), 0);
        if (earned > 0) did.add("earned you " + earned + " experience points");
        if (explored > 0) did.add("explored " + explored * 32 * 32 / 1000 + " thousand square blocks of land");
        return String.join(", ", did);
    }

    /** "sum": everything done since the job began (the counters start again each time the owner logs out). */
    String sum(UUID id) {
        String did = awayDid(id), job = core.conf.getString(core.path(id, "away.job"), "");
        long min = core.conf.getLong(core.path(id, "away.ms"), 0) / 60_000;
        List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
        if (did.isEmpty() && min == 0) {
            String last = core.conf.getString(core.path(id, "away.last"));
            return last == null ? "I have not done any work on my own yet." : "Last time you were away: " + last;
        }
        boolean full = core.conf.getBoolean(core.path(id, "away.full"), false);
        return "Since I began (" + min + " min of work" + (job.isEmpty() ? "" : ", job: " + (job.equals("home") ? "work at home" : job)) + "): " + (did.isEmpty() ? "nothing yet" : did) + "."
                + (finds.isEmpty() ? "" : " Special finds: " + String.join("; ", finds) + ".")
                + (!full ? "" : " I have stopped: " + core.conf.getString(core.path(id, "away.why"), "bag and chests are full") + ".");
    }

    /** "Did you find anything special?": the enchanted things, treasure, buildings and new chests of this time away,
     *  or of the last one when the owner is back. */
    String finds(UUID id) {
        List<String> now = core.conf.getStringList(core.path(id, "away.finds")), last = core.conf.getStringList(core.path(id, "away.lastfinds"));
        if (!now.isEmpty()) return "Yes! So far: " + String.join("; ", now) + ".";
        if (core.conf.getLong(core.path(id, "away.ms"), 0) > 0) return "Nothing special yet this time. I will tell you when I do.";
        return last.isEmpty() ? "No, nothing special last time." : "Yes! Last time you were away: " + String.join("; ", last) + ".";
    }

    void awayReport(Player p) {
        UUID id = p.getUniqueId();
        if (core.conf.getBoolean(core.path(id, "away.auto"))) {                      // it ran a mob farm on its own: the job from before counts again
            core.conf.set(core.path(id, "away.job"), core.conf.getString(core.path(id, "away.prevjob")));
            core.conf.set(core.path(id, "away.place"), core.conf.getString(core.path(id, "away.prevplace")));
            if (core.conf.getBoolean(core.path(id, "away.autowatch"))) core.conf.set(core.path(id, "watch"), null);
            for (String k : List.of("auto", "autowatch", "prevjob", "prevplace")) core.conf.set(core.path(id, "away." + k), null);
        }
        awayLet(id);
        String did = awayDid(id);
        int saved = core.conf.getInt(core.path(id, "xp"), 0);
        if (saved > 0) { p.giveExp(saved); core.conf.set(core.path(id, "xp"), null); core.save(); p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.2f); }
        long min = core.conf.getLong(core.path(id, "away.ms"), 0) / 60_000;
        boolean full = core.conf.getBoolean(core.path(id, "away.full"), false);
        if (!did.isEmpty()) core.conf.set(core.path(id, "away.lastfinds"), core.conf.getStringList(core.path(id, "away.finds")).isEmpty() ? null : core.conf.getStringList(core.path(id, "away.finds")));
        if (!did.isEmpty()) core.conf.set(core.path(id, "away.last"), did + " (" + min + " min of work)" + (core.conf.getStringList(core.path(id, "away.finds")).isEmpty() ? "" : ". Special finds: " + String.join("; ", core.conf.getStringList(core.path(id, "away.finds")))) + ".");
        String why = core.conf.getString(core.path(id, "away.why"));
        List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
        for (String k : List.of("ms", "fish", "crops", "sown", "meal", "fed", "meat", "wool", "smelted", "mined", "explored", "xp", "eggs", "chicks", "goldchicks", "mobs", "sorted", "full", "why", "finds")) core.conf.set(core.path(id, "away." + k), null);
        if (did.isEmpty() && !full) return;
        core.save();
        String stopped = !full ? "" : why != null ? " I stopped because " + why + "." : " My bag and the chests are full and I had no wood for a new chest, so I had to stop.";
        core.conf.set(core.path(id, "away.report"), (did.isEmpty() ? "" : "While you were away I " + did + " (" + min + " minutes of work). It is in the chests at home and in my bag."
                + (finds.isEmpty() ? "" : " The best of it: " + String.join("; ", finds) + ".")) + stopped);
        core.save();
        core.say(p, "Welcome back, " + core.nick(p) + "!" + (did.isEmpty() ? "" : " " + min + " min of work" + (saved > 0 ? ", " + saved + " experience for you" : "") + (finds.isEmpty() ? "" : ", " + finds.size() + (finds.size() == 1 ? " special find" : " special finds")) + ".")
                + (full ? " I had to stop early." : "") + " Say log for the details.");
    }

    /** The owner's routines also run while they are away, for the orders offline() knows. */
    void awayRoutines(UUID id, Location hm) {
        List<String> rs = core.conf.getStringList(core.path(id, "routines"));
        if (rs.isEmpty()) return;
        long t = hm.getWorld().getTime(), now = System.currentTimeMillis();
        Long lastSeen = awayTime.put(id, t);
        long last = lastSeen == null ? -1 : lastSeen;
        for (String r : rs) {
            String[] a = r.split("\\|", 2);
            if (a.length < 2) continue;
            boolean due;
            Integer at = Helper.TIMES.get(a[0]);
            if (at != null) due = last >= 0 && last != t && (last < t ? last < at && at <= t : at > last || at <= t);
            else {
                long every = Long.parseLong(a[0].replaceAll("\\D", "")) * 60_000L;
                Long ranAt = awayRan.putIfAbsent(id + r, now);
                due = ranAt != null && now - ranAt >= every;
                if (due) awayRan.put(id + r, now);
            }
            if (!due) continue;
            for (String order : a[1].split("\\s*(?:,|;|\\band\\b|\\bog\\b|\\bthen\\b|\\bså\\b)\\s*")) {
                if (order.isBlank()) continue;
                String o = order.trim();
                if (!core.orders.offline(id, o, said -> core.awayLog(id, "routine \"" + o + "\": " + said))) core.awayLog(id, "routine \"" + o + "\": not something I can do while you are away");
            }
        }
    }
}
