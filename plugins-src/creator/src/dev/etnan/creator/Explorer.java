package dev.etnan.creator;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.generator.structure.GeneratedStructure;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Vector;

/** What a helper does out in the land while its owner is away: digging for one kind of ore near home, exploring
 *  further and further out, picking up the ore it is looking for on the way (the kind you have least of, or what
 *  you asked for with "find") and making treasure maps for chests nobody has opened. */
final class Explorer {
    final Helper core;

    Explorer(Helper core) { this.core = core; }

    /** The n-th chunk of a square spiral around 0,0. */
    static int[] spiral(int n) {
        int x = 0, z = 0, dx = 0, dz = -1;
        for (int i = 0; i < n; i++) {
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) { int t = dx; dx = -dz; dz = t; }
            x += dx;
            z += dz;
        }
        return new int[] {x, z};
    }

    static boolean oreFor(Material m, String target) {
        String n = m.name();
        return n.contains(target) && (n.endsWith("_ORE") || m == Material.ANCIENT_DEBRIS || m == Material.OBSIDIAN);
    }

    /** "diamonds", "kull", "iron ore" -> DIAMOND, COAL, IRON; null when it is not something it digs for. */
    static String oreTarget(String words) {
        String w = Helper.ARTICLE.matcher(words.trim().toLowerCase(Locale.ROOT)).replaceFirst("").replaceAll("\\s+(?:ore|malm)$", "").trim();
        String t = Helper.ORE_WORDS.get(w);
        if (t == null) t = w.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (t.endsWith("S") && t.length() > 4) { String one = t.substring(0, t.length() - 1); for (Material m : Material.values()) if (oreFor(m, one)) return one; }
        for (Material m : Material.values()) if (oreFor(m, t)) return t;
        return null;
    }

    /** Digs out one kind of ore within 128 blocks of home (all heights), where the land already exists: looks
     *  through up to four chunks every ten seconds, and takes three blocks at a time, with fortune III. */
    int awayMine(UUID id, Location hm) {
        String target = core.conf.getString(core.path(id, "away.target"), "");
        if (target.isEmpty()) return 0;
        Deque<Block> q = core.awayOre.computeIfAbsent(id, k -> new ArrayDeque<>());
        World w = hm.getWorld();
        for (int tries = 0; q.isEmpty() && tries < 4; tries++) {
            int i = core.awayScan.merge(id, 1, Integer::sum) - 1;
            int[] c = spiral(i);
            if (Math.max(Math.abs(c[0]), Math.abs(c[1])) > 8) {
                core.awayScan.remove(id);
                core.conf.set(core.path(id, "away.why"), "there is no more " + target.toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home");
                core.conf.set(core.path(id, "away.full"), true);
                return 0;
            }
            int cx = (hm.getBlockX() >> 4) + c[0], cz = (hm.getBlockZ() >> 4) + c[1];
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int y = w.getMinHeight(); y < w.getMaxHeight(); y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++)
                if (oreFor(snap.getBlockType(x, y, z), target)) q.add(w.getBlockAt(cx * 16 + x, y, cz * 16 + z));
        }
        int n = 0;
        while (n < 3 && !q.isEmpty()) {
            Block b = q.poll();
            if (!oreFor(b.getType(), target)) continue;
            Collection<ItemStack> d = Helper.drops(b);
            core.awayNote(id, d);
            core.addToBag(id, d);
            String ore = b.getType().name();
            core.xp(id, ore.contains("DIAMOND") || ore.contains("EMERALD") ? 3 + ThreadLocalRandom.current().nextInt(5) : ore.contains("LAPIS") || ore.contains("QUARTZ") || ore.contains("REDSTONE") ? 2 + ThreadLocalRandom.current().nextInt(4)
                    : ore.contains("COAL") ? ThreadLocalRandom.current().nextInt(3) : 0);
            b.setType(Material.AIR);
            n++;
        }
        return n;
    }

    /** Goes further and further out from home (to 3000 blocks), one new piece of land every ten seconds, also where
     *  nobody has been. Buildings it finds are remembered as places (goto <name>) and told about afterwards. On the
     *  way it digs out what it is looking for (see SEEK), and for every treasure chest nobody has opened it makes
     *  a treasure map with a red cross and puts it in a chest at home. From the home chores (side) it never stops
     *  the work when there is no more land to see. */
    static final int EXPLORE_STEPS = 94;                           // two chunks a step: about 3000 blocks out

    int awayExplore(UUID id, Location hm, boolean side) {
        int i = core.conf.getInt(core.path(id, "away.scan"), 0);
        int[] c = spiral(i);
        if (Math.max(Math.abs(c[0]), Math.abs(c[1])) > EXPLORE_STEPS) {
            if (side) return 0;
            core.conf.set(core.path(id, "away.why"), "I have seen everything within 3000 blocks of home");
            core.conf.set(core.path(id, "away.full"), true);
            return 0;
        }
        core.conf.set(core.path(id, "away.scan"), i + 1);
        World w = hm.getWorld();
        int cx = (hm.getBlockX() >> 4) + c[0] * 2, cz = (hm.getBlockZ() >> 4) + c[1] * 2;
        w.getChunkAtAsync(cx, cz, true).thenAccept(ch -> {
            passOres(id, ch);
            treasures(id, hm, ch);
            Registry<Structure> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
            for (GeneratedStructure gs : w.getStructures(cx, cz)) {
                NamespacedKey k = reg.getKey(gs.getStructure());
                if (k == null) continue;
                Vector mid = gs.getBoundingBox().getCenter();
                int x = mid.getBlockX(), z = mid.getBlockZ();
                Location at = new Location(w, x + 0.5, w.getEnvironment() == World.Environment.NORMAL ? w.getHighestBlockYAt(x, z) + 1 : mid.getBlockY(), z + 0.5);
                ConfigurationSection sec = core.conf.getConfigurationSection(core.path(id, "places"));
                boolean known = false;
                if (sec != null) for (String name : sec.getKeys(false)) {
                    Location l = Helper.place(sec.getString(name));
                    if (name.startsWith(k.getKey()) && l != null && l.getWorld() == w && l.distanceSquared(at) < 200 * 200) { known = true; break; }
                }
                if (known) continue;
                String name = k.getKey();
                for (int n = 2; sec != null && sec.contains(name) && n < 50; n++) name = k.getKey() + "_" + n;
                core.conf.set(core.path(id, "places." + name), Helper.spot(at));
                String told = k.getKey().replace('_', ' ') + " at " + x + " " + z + " (goto " + name.replace('_', ' ') + ")";
                core.awayLog(id, "found a " + told);
                List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
                if (finds.size() < 30) { finds.add(told); core.conf.set(core.path(id, "away.finds"), finds); }
            }
        });
        return 1;
    }

    /** What it digs for while exploring, in this order: the first kind you have less than the limit of (in your own
     *  store, its bag and the chests at home). "find <ore or wood>" puts one kind first, however much you have. */
    static final String[][] SEEK = {   // name, what it yields, limit
            {"diamond", "DIAMOND", "640"}, {"gold", "RAW_GOLD GOLD_INGOT", "640"}, {"iron", "RAW_IRON IRON_INGOT", "640"},
            {"coal", "COAL", "640"}, {"lapis", "LAPIS_LAZULI", "640"}, {"redstone", "REDSTONE", "640"}};

    final Map<UUID, Object[]> seekNow = new HashMap<>();             // the kind chosen from the stock, and when

    /** "redstone", "gold ore", "oak", "birch logs", "wood" -> ORE:REDSTONE, ORE:GOLD, LOG:OAK, LOG:BIRCH, LOG:; null if it is neither. */
    static String seekSpec(String words) {
        String w = Helper.ARTICLE.matcher(words.trim().toLowerCase(Locale.ROOT)).replaceFirst("").trim();
        if (w.matches("wood|woods|log|logs|tree|trees|tre|ved|stokker")) return "LOG:";
        String t = w.replaceAll("\\s+(?:logs?|wood|trees?|tre|stokker)$", "").toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (Helper.mat(t + "_LOG") != null) return "LOG:" + t;
        if (Helper.mat(t + "_STEM") != null && !t.contains("MUSHROOM")) return "LOG:" + t;
        String ore = oreTarget(w);
        return ore == null ? null : "ORE:" + ore;
    }

    static boolean wanted(Material m, String spec) {
        if (spec.startsWith("ORE:")) {
            String t = spec.substring(4);
            return oreFor(m, t) && (!t.equals("IRON") || m.name().endsWith("IRON_ORE")) && (!t.equals("GOLD") || m.name().endsWith("GOLD_ORE"));
        }
        String t = spec.substring(4), n = m.name();
        boolean log = Tag.LOGS.isTagged(m) && !n.startsWith("STRIPPED_") && (n.endsWith("_LOG") || n.endsWith("_STEM"));
        return log && (t.isEmpty() || n.equals(t + "_LOG") || n.equals(t + "_STEM"));
    }

    static String seekWord(String spec) {
        String t = spec.substring(4).toLowerCase(Locale.ROOT).replace('_', ' ');
        return spec.startsWith("LOG:") ? (t.isEmpty() ? "wood" : t + " wood") : t;
    }

    /** What it digs for now: your order, else the first kind below its limit, else nothing. Looked at once a minute. */
    String digFor(UUID id) {
        String asked = core.conf.getString(core.path(id, "seek"));
        if (asked != null) return asked;
        Object[] was = seekNow.get(id);
        if (was != null && System.currentTimeMillis() - (long) was[1] < 60_000) return (String) was[0];
        List<Inventory> in = new ArrayList<>(List.of(core.bag(id)));
        Location hm = core.home(id);
        if (hm != null && hm.getWorld() != null) in.addAll(Helper.stores(hm, Helper.HOME));
        String pick = null;
        for (String[] k : SEEK) {
            Set<Material> yields = new HashSet<>();
            for (String y : k[1].split(" ")) yields.add(Material.valueOf(y));
            long have = Helper.count(in, yields::contains) + (core.storage == null ? 0 : core.storage.countMine(id, yields));
            if (have < core.conf.getLong("settings.seek-limit." + k[0], Long.parseLong(k[2]))) { pick = "ORE:" + k[0].toUpperCase(Locale.ROOT); break; }
        }
        String before = was == null ? null : (String) was[0];
        if (!java.util.Objects.equals(before, pick)) core.awayLog(id, pick == null ? "has plenty of every ore now, so it digs for none while exploring"
                : "looks for " + seekWord(pick) + " while exploring" + (before == null ? "" : " (enough " + seekWord(before) + " now)"));
        seekNow.put(id, new Object[] {pick, System.currentTimeMillis()});
        return pick;
    }

    /** A log that belongs to a tree: leaves that grew there (not placed) within a few blocks above or beside it.
     *  So the walls of a log cabin are left alone. */
    static boolean wildLog(org.bukkit.ChunkSnapshot snap, int x, int y, int z, int top) {
        for (int dy = 0; dy <= 8 && y + dy < top; dy++) for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            int ax = x + dx, az = z + dz;
            if (ax < 0 || ax > 15 || az < 0 || az > 15) continue;
            if (snap.getBlockData(ax, y + dy, az) instanceof org.bukkit.block.data.type.Leaves l && !l.isPersistent()) return true;
        }
        return false;
    }

    /** What it is looking for in a piece of land it passes: up to 64 blocks of it.
     *  Ore turns to stone (no holes in the land), a felled log to air. */
    void passOres(UUID id, org.bukkit.Chunk ch) {
        String spec = digFor(id);
        if (spec == null) return;
        World w = ch.getWorld();
        org.bukkit.ChunkSnapshot snap = ch.getChunkSnapshot();
        int n = 0, top = w.getMaxHeight();
        Map<Material, Integer> got = new java.util.EnumMap<>(Material.class);
        outer:
        for (int y = w.getMinHeight(); y < top; y++) for (int x = 0; x < 16; x++) for (int z = 0; z < 16; z++) {
            Material m = snap.getBlockType(x, y, z);
            if (!wanted(m, spec)) continue;
            boolean log = spec.startsWith("LOG:");
            if (log && !wildLog(snap, x, y, z, top)) continue;
            Block b = ch.getBlock(x, y, z);
            for (ItemStack d : Helper.drops(b)) if (!Helper.empty(d)) got.merge(d.getType(), d.getAmount(), Integer::sum);
            String k = m.name();
            if (!log) core.xp(id, k.contains("DIAMOND") || k.contains("EMERALD") ? 3 + ThreadLocalRandom.current().nextInt(5) : k.contains("LAPIS") || k.contains("REDSTONE") ? 2 + ThreadLocalRandom.current().nextInt(4) : k.contains("COAL") ? ThreadLocalRandom.current().nextInt(3) : 0);
            b.setType(log ? Material.AIR : k.startsWith("DEEPSLATE") ? Material.DEEPSLATE : k.startsWith("NETHER") ? Material.NETHERRACK : Material.STONE);
            if (++n >= 64) break outer;
        }
        if (n == 0) return;
        core.bump(id, "mined", n);
        List<ItemStack> stacks = new ArrayList<>();                         // one line in the log per kind (per stack), not per block
        got.forEach((m, a) -> { for (int left = a; left > 0; left -= m.getMaxStackSize()) stacks.add(new ItemStack(m, Math.min(left, m.getMaxStackSize()))); });
        core.awayNote(id, stacks);
        core.addToBag(id, stacks);
    }

    /** Chests nobody has opened yet (buried treasure, shipwrecks, temples, dungeons …, not villages): a treasure map. */
    void treasures(UUID id, Location hm, org.bukkit.Chunk ch) {
        World w = ch.getWorld();
        for (BlockState st : ch.getTileEntities(b -> b.getType() == Material.CHEST || b.getType() == Material.BARREL, false)) {
            if (!(st instanceof org.bukkit.loot.Lootable lt) || !lt.hasLootTable() || lt.getLootTable() == null) continue;
            String table = lt.getLootTable().getKey().getKey();                         // chests/buried_treasure …
            if (table.contains("village")) continue;
            int x = st.getX(), y = st.getY(), z = st.getZ();
            ConfigurationSection sec = core.conf.getConfigurationSection(core.path(id, "places"));
            boolean known = false;
            if (sec != null) for (String name : sec.getKeys(false)) {
                Location l = Helper.place(sec.getString(name));
                if (name.startsWith("treasure") && l != null && l.getWorld() == w && l.distanceSquared(new Location(w, x, y, z)) < 48 * 48) { known = true; break; }
            }
            if (known) continue;
            String what = table.replaceFirst("^chests/", "").replace('/', ' ').replace('_', ' ');
            String name = "treasure";
            for (int n = 2; sec != null && sec.contains(name) && n < 200; n++) name = "treasure_" + n;
            core.conf.set(core.path(id, "places." + name), Helper.spot(new Location(w, x + 0.5, y + 1, z + 0.5)));
            ItemStack map = treasureMap(w, x, y, z, what);
            boolean inBarrel = core.myBarrel(id, hm.getWorld()) != null;
            if (inBarrel) core.storage.putMine(id, List.of(map));
            else {
                List<Inventory> to = Helper.stores(hm, Helper.HOME);
                Map<Integer, ItemStack> rest = Map.of(0, map);
                for (Inventory inv : to) { if (rest.isEmpty()) break; rest = inv.addItem(map.clone()); }
                if (!rest.isEmpty()) core.bag(id).addItem(map);
            }
            core.dirty.add(id);
            core.bump(id, "maps", 1);
            String told = "a treasure (" + what + ") at " + x + " " + y + " " + z + ": a map is in " + (inBarrel ? "your magic barrel" : "the chests at home") + " (goto " + name.replace('_', ' ') + ")";
            core.awayLog(id, "found " + told);
            List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
            if (finds.size() < 30) { finds.add(told); core.conf.set(core.path(id, "away.finds"), finds); }
        }
    }

    static ItemStack treasureMap(World w, int x, int y, int z, String what) {
        org.bukkit.map.MapView v = Bukkit.createMap(w);
        v.setCenterX(x);
        v.setCenterZ(z);
        v.setScale(org.bukkit.map.MapView.Scale.NORMAL);
        v.setTrackingPosition(true);
        v.setUnlimitedTracking(true);
        ItemStack it = new ItemStack(Material.FILLED_MAP);
        if (it.getItemMeta() instanceof org.bukkit.inventory.meta.MapMeta mm) {
            mm.setMapView(v);
            mm.customName(Component.text("Treasure map: " + what, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
            mm.lore(List.of(Component.text(x + " " + y + " " + z + " in " + w.getName(), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                    Component.text("Dig where the red cross is.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
            it.setItemMeta(mm);
        }
        it.setData(io.papermc.paper.datacomponent.DataComponentTypes.MAP_DECORATIONS, io.papermc.paper.datacomponent.item.MapDecorations.mapDecorations(
                Map.of("treasure", io.papermc.paper.datacomponent.item.MapDecorations.decorationEntry(org.bukkit.map.MapCursor.Type.RED_X, x, z, 180f))));
        return it;
    }
}
