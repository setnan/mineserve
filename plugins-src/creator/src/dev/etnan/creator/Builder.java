package dev.etnan.creator;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

/** Buildings by hand: a house from the built-in plan, or anything else the AI draws. It clears the plot, gathers
 *  wood, stone and sand (further away if it has to), crafts what it needs and sets the blocks one by one. A building
 *  in progress is saved when the server stops and taken up again after the start. Also "undo build" and taking
 *  other things down. */
final class Builder {
    final Helper core;

    Builder(Helper core) { this.core = core; }

    // --- Building ----------------------------------------------------------------------------------
    static final Pattern BUILD_WORDS = Pattern.compile("(?:build|bygg)(?: \\S+){0,6}? (house|cottage|hut|cabin|hus|hytte)\\b(.*)");

    static final Pattern XYZ = Pattern.compile("(-?\\d+)[ ,]+(-?\\d+)[ ,]+(-?\\d+)(?:\\s+(\\w+))?");

    static final String[] FACES = {"south", "west", "north", "east"};

    static final Pattern DOWN_WORDS = Pattern.compile("(?:undo|remove|take down|tear down|pull down|demolish|angre|fjern|riv|ta ned)(?: the| my| your| last| det| siste| mitt| ditt| this| that)* ?(?:build|building|house|bygg|bygget|bygningen|huset|hus)\\b(.*)");

    static final String[] WOODS = {"OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "DARK_OAK", "MANGROVE", "CHERRY", "PALE_OAK"};

    /** A building job: a house going up (clear the plot, gather, place) or something coming down. One thing every 0.4 s. */
    final class Site {
        UUID id; World w; int x, y, z, fx, fz; String where, wood; boolean down;
        final Deque<Block> clear = new ArrayDeque<>(), trees = new ArrayDeque<>(), stone = new ArrayDeque<>(), sand = new ArrayDeque<>();
        final List<Object[]> place = new ArrayList<>();
        int at, wait, far, phase, needLogs, needCobble, needSand, sheep, broke, put;
        boolean door, paused; String bed;
        final List<long[]> held = new ArrayList<>();
        final List<String> placed = new ArrayList<>();
        final Set<String> lacked = new java.util.TreeSet<>();
        final Set<String> had = new HashSet<>();                           // set in an earlier round of the same drawing
        List<Inventory> from; long fromAt;
        Helper.Stance stance; Location anchor;
        org.bukkit.scheduler.BukkitTask task;
        int out = 5; String name = "little house"; boolean plan;           // how far in front of the middle it stands; what is built; from a drawing
        final Set<Material> keep = new HashSet<>();                       // what a drawing needs: kept when the plot is cleared
        final Inventory pile = Bukkit.createInventory(null, 54);          // the materials for this job, kept apart from the bag (which may be full)
    }

    final Map<UUID, Site> sites = new HashMap<>();

    final Map<UUID, Object[]> razing = new HashMap<>();              // asked to take something down: the blocks, and when

    static boolean builtBlock(Material m) {
        return (!Helper.natural(m) || Tag.LOGS.isTagged(m)) && !m.name().endsWith("_ORE") && m != Material.BEDROCK;
    }

    void note(Site s, String text) {
        Player p = Bukkit.getPlayer(s.id);
        if (p != null) core.say(p, text); else core.awayLog(s.id, text);
    }

    List<Inventory> stock(Site s) {
        long now = System.currentTimeMillis();
        if (s.from == null || now - s.fromAt > 10_000) {
            s.from = new ArrayList<>(List.of(s.pile, core.bag(s.id)));
            s.from.addAll(Helper.stores(new Location(s.w, s.x, s.y, s.z), 12));
            s.fromAt = now;
        }
        return s.from;
    }

    void stow(Site s, ItemStack it) {
        if (Helper.empty(it)) return;
        Map<Integer, ItemStack> rest = s.pile.addItem(it.clone());
        if (!rest.isEmpty()) { rest = core.bag(s.id).addItem(rest.values().iterator().next()); core.dirty.add(s.id); }
        for (ItemStack r : rest.values()) s.w.dropItemNaturally(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out), r);
    }

    boolean ensure(Site s, Material m, int n, int depth) {
        if (m == null) return false;
        while (Helper.count(stock(s), k -> k == m) < n) if (depth > 6 || !make(s, m, depth + 1)) return false;
        return true;
    }

    /** How a thing is made: {what goes in (thing, how many, ...), how many come out, the wait}. Null: it cannot be made. */
    Object[] recipe(Site s, Material m) {
        Material log = Helper.mat(s.wood + "_LOG"), planks = Helper.mat(s.wood + "_PLANKS");
        String n = m.name();
        Object[] in;
        int out = 1, wait = 0, smelt = s.plan ? 2 : 25;
        if (m == planks) { in = new Object[] {log, 1}; out = 4; }
        else if (m == Helper.mat(s.wood + "_STAIRS")) { in = new Object[] {planks, 6}; out = 4; }
        else if (m == Helper.mat(s.wood + "_SLAB")) { in = new Object[] {planks, 3}; out = 6; }
        else if (m == Helper.mat(s.wood + "_DOOR")) { in = new Object[] {planks, 6}; out = 3; }
        else if (m == Helper.mat(s.wood + "_FENCE")) { in = new Object[] {planks, 4, Material.STICK, 2}; out = 3; }
        else if (m == Helper.mat(s.wood + "_FENCE_GATE")) in = new Object[] {Material.STICK, 4, planks, 2};
        else if (m == Helper.mat(s.wood + "_TRAPDOOR")) { in = new Object[] {planks, 6}; out = 2; }
        else if (m == Helper.mat(s.wood + "_PRESSURE_PLATE")) in = new Object[] {planks, 2};
        else if (m == Helper.mat(s.wood + "_BUTTON")) in = new Object[] {planks, 1};
        else if (m == Material.CHEST) in = new Object[] {planks, 8};
        else if (m == Material.BARREL) in = new Object[] {planks, 7};
        else if (m == Material.CRAFTING_TABLE) in = new Object[] {planks, 4};
        else if (m == Material.LADDER) { in = new Object[] {Material.STICK, 7}; out = 3; }
        else if (m == Material.FURNACE) in = new Object[] {Material.COBBLESTONE, 8};
        else if (m == Material.STICK) { in = new Object[] {planks, 2}; out = 4; }
        else if (m == Material.CHARCOAL) { in = new Object[] {log, 1}; wait = smelt; }
        else if (m == Material.CAMPFIRE) in = new Object[] {Material.STICK, 3, Material.CHARCOAL, 1, log, 3};
        else if (m == Material.TORCH || m == Material.WALL_TORCH) { in = m == Material.WALL_TORCH ? new Object[] {Material.TORCH, 1} : new Object[] {Material.STICK, 1, Material.CHARCOAL, 1}; out = m == Material.WALL_TORCH ? 1 : 4; }
        else if (m == Material.IRON_NUGGET) { in = new Object[] {Material.IRON_INGOT, 1}; out = 9; }
        else if (m == Material.LANTERN) in = new Object[] {Material.IRON_NUGGET, 8, Material.TORCH, 1};
        else if (m == Material.GLASS) { in = new Object[] {Material.SAND, 1}; wait = smelt; }
        else if (m == Material.GLASS_PANE) { in = new Object[] {Material.GLASS, 6}; out = 16; }
        else if (m == Material.STONE) { in = new Object[] {Material.COBBLESTONE, 1}; wait = smelt; }
        else if (m == Material.SMOOTH_STONE) { in = new Object[] {Material.STONE, 1}; wait = smelt; }
        else if (m == Material.STONE_BRICKS) { in = new Object[] {Material.STONE, 4}; out = 4; }
        else if (m == Material.SANDSTONE) in = new Object[] {Material.SAND, 4};
        else if (n.endsWith("_STAIRS") || n.endsWith("_SLAB") || n.endsWith("_WALL")) {                // of stone: from the block of the same name
            String stem = n.substring(0, n.lastIndexOf('_'));
            Material from = Helper.mat(stem) != null && Helper.mat(stem).isBlock() ? Helper.mat(stem) : Helper.mat(stem + "S");
            if (from == null || !from.isBlock() || from == m || Tag.PLANKS.isTagged(from)) return null;
            in = new Object[] {from, n.endsWith("_SLAB") ? 3 : 6};
            out = n.endsWith("_STAIRS") ? 4 : 6;
        }
        else return null;
        return new Object[] {in, out, wait};
    }

    /** Makes one batch of something from what is in the bag and the chests by the plot, the way a crafting table or a furnace would. */
    boolean make(Site s, Material m, int depth) {
        Object[] r = recipe(s, m);
        if (r == null) return false;
        Object[] in = (Object[]) r[0];
        for (int i = 0; i < in.length; i += 2) if (!ensure(s, (Material) in[i], (Integer) in[i + 1], depth)) return false;
        for (int i = 0; i < in.length; i += 2) { Material k = (Material) in[i]; Helper.take(stock(s), q -> q == k, (Integer) in[i + 1]); }
        stow(s, new ItemStack(m, (Integer) r[1]));
        s.wait += (Integer) r[2];                                          // a furnace takes its time
        return true;
    }

    final Map<UUID, Object[]> designs = new HashMap<>();             // waiting for a drawing: world, x, y, z, facing, when

    static final Pattern PLAIN_HOUSE = Pattern.compile("(?:build|bygg|a|an|the|me|us|et|en|ei|small|little|liten|lite|house|cottage|hut|cabin|hus|hytte|here|her|for|yourself|myself|deg|selv|meg|oss|please|-?\\d+|[a-z_]*world[a-z_]*|[\\s,.!])*");

    static final Pattern ANY_BUILD = Pattern.compile("(?:build|bygg|construct|make|lag)\\s+(?:me |us |meg |oss )?(.{3,300})");

    /** "build a stone tower with a spiral stair here": asks for a drawing. False: not a wish for a building. */
    boolean designOrder(UUID id, Player p, String t, Consumer<String> tell) {
        Matcher m = ANY_BUILD.matcher(t);
        if (!m.matches() || m.group(1).matches("(?:a |an |the |en |ei |et )?(?:bridge|stairs|bro|trapp|super ?tool.*)")) return false;
        String thing = Helper.ARTICLE.matcher(m.group(1).trim().replaceAll("\\s+(?:here|for me|please|her|til meg|takk)$", "")).replaceFirst("").trim();
        String key = thing.toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        Material one = key.isEmpty() ? null : Material.matchMaterial(key);
        if (key.equals("BED") || key.equals("SENG") || one != null && one.isItem() && !Bukkit.getRecipesFor(new ItemStack(one)).isEmpty()) {      // one thing, not a building: it is crafted and handed over
            core.chores.craft(id, p, p != null ? p.getLocation() : null, thing, false, tell);
            return true;
        }
        if (sites.containsKey(id)) { tell.accept("I am already at work on a building. Say stop building first."); return true; }
        String wish = m.group(1), who = Bukkit.getOfflinePlayer(id).getName();
        Matcher c = XYZ.matcher(wish);
        if (c.find()) {                                                   // "... 100 64 100 [world]": there, the front to the south
            World w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : core.home(id) != null ? core.home(id).getWorld() : Bukkit.getWorlds().get(0);
            designs.put(id, new Object[] {w, Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3)), "north", System.currentTimeMillis()});
            wish = (wish.substring(0, c.start()) + wish.substring(c.end())).replaceAll("\\b(?:at|ved|på)\\s*$", "").trim();
        } else if (p != null) {
            Location l = p.getLocation();
            designs.put(id, new Object[] {l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), p.getFacing().name().toLowerCase(Locale.ROOT), System.currentTimeMillis()});
        } else { tell.accept("Where? Say build <what> <x> <y> <z> [world] (y is the level you walk on)."); return true; }
        if (who == null || wish.length() < 3) { designs.remove(id); return false; }
        core.plugin.getLogger().info("HELPER_BUILD " + who + "|" + core.name(id).replace('|', ' ') + "|" + wish.replace('|', ' ').replaceAll("\\s+", " "));
        tell.accept("Let me draw it first. Give me a minute or two, then I start" + (c.reset(m.group(1)).find() ? " there." : " in front of where you stand now."));
        return true;
    }

    /** The drawing has come (plugins/Creator/blueprints/<player>.json): build it. */
    void buildPlan(UUID id, Consumer<String> tell) {
        Object[] d = designs.remove(id);
        if (d == null || System.currentTimeMillis() - (Long) d[5] > 20 * 60_000L) { tell.accept("I have no drawing waiting. Tell me what to build."); return; }
        File f = new File(new File(core.plugin.getDataFolder(), "blueprints"), Bukkit.getOfflinePlayer(id).getName() + ".json");
        core.conf.set(core.path(id, "plan"), ((World) d[0]).getName() + " " + d[1] + " " + d[2] + " " + d[3] + " " + d[4]);      // for "continue building"
        core.save();
        try { java.nio.file.Files.copy(f.toPath(), new File(f.getParentFile(), f.getName().replace(".json", ".built.json")).toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING); } catch (IOException ignored) { }   // a later drawing does not take this one away
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(f.toPath())) {
            tell.accept(blueprint(id, com.google.gson.JsonParser.parseReader(r).getAsJsonObject(), (World) d[0], (Integer) d[1], (Integer) d[2], (Integer) d[3], (String) d[4]));
        } catch (IOException | RuntimeException e) {
            core.plugin.getLogger().warning("Blueprint " + f + ": " + e);
            tell.accept("I could not read my own drawing. Ask me again?");
        }
    }

    final Set<UUID> buildingOn = new HashSet<>();

    static final Pattern BUILD_ON = Pattern.compile("(?:(?:continue|keep|go on|carry on|resume|finish)(?: on| with)?(?: the)? build(?:ing)?|keep building|build on|finish (?:the |my )?(?:house|building)|bygg videre|fortsett (?:å bygge|byggingen|bygget)|bygg ferdig)(?: .*)?");

    /** "continue building": the last drawing again on the same spot; what already stands is kept and the rest is set. */
    void buildOn(UUID id, String at, Consumer<String> tell) {
        String[] a = (at.isBlank() ? core.conf.getString(core.path(id, "plan"), "") : at).trim().split("\\s+");
        if (a.length == 4 && core.conf.getString(core.path(id, "built.world")) != null) a = new String[] {core.conf.getString(core.path(id, "built.world")), a[0], a[1], a[2], a[3]};
        World w = a.length == 5 ? Bukkit.getWorld(a[0]) : null;
        File f = new File(new File(core.plugin.getDataFolder(), "blueprints"), Bukkit.getOfflinePlayer(id).getName() + ".built.json");
        if (!f.isFile()) f = new File(f.getParentFile(), Bukkit.getOfflinePlayer(id).getName() + ".json");      // a build from before the copies were kept
        if (w == null || !f.isFile() || !Arrays.asList(FACES).contains(a[4])) { tell.accept("I have no half-built drawing to go on with. Tell me what to build."); return; }
        buildingOn.add(id);
        try (java.io.Reader r = java.nio.file.Files.newBufferedReader(f.toPath())) {
            tell.accept(blueprint(id, com.google.gson.JsonParser.parseReader(r).getAsJsonObject(), w, Integer.parseInt(a[1]), Integer.parseInt(a[2]), Integer.parseInt(a[3]), a[4]));
        } catch (IOException | RuntimeException e) {
            core.plugin.getLogger().warning("Blueprint " + f + ": " + e);
            tell.accept("I could not read my own drawing. Ask me again?");
        } finally { buildingOn.remove(id); }
    }

    static final Set<String> NO_BUILD = Set.of("WATER", "LAVA", "FIRE", "SOUL_FIRE", "TNT", "BEDROCK", "BARRIER", "SPAWNER", "TRIAL_SPAWNER", "JIGSAW", "LIGHT", "END_PORTAL_FRAME");

    static String turn(String props, int turns) {                    // the drawing has its front to the south
        if (props.isEmpty()) return props;
        StringBuilder out = new StringBuilder("[");
        for (String kv : props.substring(1, props.length() - 1).split(",")) {
            String[] a = kv.trim().split("=");
            if (a.length != 2 || !a[0].matches("[a-z_]+") || !a[1].matches("[a-z0-9_]+")) continue;
            int i = Arrays.asList(FACES).indexOf(a[1]);
            if (a[0].equals("facing") && i >= 0) a[1] = FACES[(i + turns) % 4];
            else if (a[0].equals("axis") && turns % 2 == 1) a[1] = a[1].equals("x") ? "z" : a[1].equals("z") ? "x" : a[1];
            else if (Arrays.asList(FACES).contains(a[0])) continue;         // fence and pane arms: worked out when it stands
            if (out.length() > 1) out.append(',');
            out.append(a[0]).append('=').append(a[1]);
        }
        return out.length() == 1 ? "" : out.append(']').toString();
    }

    /** The thing that is set for a word of a plan: LOG, PLANKS, WOOD:FENCE (of the wood of this job), or a block's own name. */
    /** The thing in the hand for a block: a torch for a wall torch, a sign for a wall sign. */
    static Material asItem(Material m) {
        if (m == null || m.isItem()) return m;
        try { Material i = m.createBlockData().getPlacementMaterial(); return i == null || i.isAir() ? m : i; } catch (RuntimeException e) { return m; }
    }

    Material planItem(Site s, String base) {
        return switch (base) {
            case "LOG" -> Helper.mat(s.wood + "_LOG");
            case "PLANKS" -> Helper.mat(s.wood + "_PLANKS");
            case "STAIRS" -> Helper.mat(s.wood + "_STAIRS");
            case "SLAB" -> Helper.mat(s.wood + "_SLAB");
            case "DOOR" -> Helper.mat(s.wood + "_DOOR");
            case "BED" -> null;
            default -> base.startsWith("WOOD:") ? Helper.mat(s.wood + "_" + base.substring(5)) : Helper.mat(base);
        };
    }

    /** How much wood (0), stone (1) and sand (2) it takes to make n of this from nothing. False: it cannot be made. */
    boolean raw(Site s, Material m, double n, double[] acc, int depth) {
        if (m == null || depth > 8) return false;
        if (m == Helper.mat(s.wood + "_LOG")) { acc[0] += n; return true; }
        if (m == Material.COBBLESTONE) { acc[1] += n; return true; }
        if (m == Material.SAND) { acc[2] += n; return true; }
        Object[] r = recipe(s, m);
        if (r == null) return false;
        Object[] in = (Object[]) r[0];
        double[] sub = new double[3];
        for (int i = 0; i < in.length; i += 2) if (!raw(s, (Material) in[i], n / (Integer) r[1] * (Integer) in[i + 1], sub, depth + 1)) return false;
        for (int i = 0; i < 3; i++) acc[i] += sub[i];
        return true;
    }

    String blueprint(UUID id, com.google.gson.JsonObject j, World w, int px, int py, int pz, String facing) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        String title = j.has("name") ? j.get("name").getAsString().replaceAll("[^\\p{L}\\p{N} '-]", "").trim() : "";
        if (title.isEmpty()) title = "building";
        if (title.length() > 40) title = title.substring(0, 40);
        Map<Character, String> legend = new HashMap<>();
        for (com.google.gson.JsonElement e : j.getAsJsonArray("legend")) {
            String k = e.getAsJsonObject().get("key").getAsString(), b = e.getAsJsonObject().get("block").getAsString().trim().toLowerCase(Locale.ROOT).replace("minecraft:", "");
            if (k.length() == 1 && !b.isEmpty()) legend.put(k.charAt(0), b);
        }
        Map<Integer, List<String>> layers = new java.util.TreeMap<>();
        int cols = 0, rows = 0;
        for (com.google.gson.JsonElement e : j.getAsJsonArray("layers")) {
            List<String> rs = new ArrayList<>();
            for (com.google.gson.JsonElement r : e.getAsJsonObject().getAsJsonArray("rows")) { rs.add(r.getAsString()); cols = Math.max(cols, r.getAsString().length()); }
            rows = Math.max(rows, rs.size());
            layers.put(e.getAsJsonObject().get("y").getAsInt(), rs);
        }
        if (layers.isEmpty() || cols == 0) return "My drawing came out empty. Ask me again, maybe with a few more words about what you want.";
        int ymin = ((java.util.TreeMap<Integer, List<String>>) layers).firstKey(), ymax = ((java.util.TreeMap<Integer, List<String>>) layers).lastKey();
        if (cols > 25 || rows > 25 || ymin < -10 || ymax > 30) return "What I drew is too big for me to build by hand (" + cols + " x " + rows + ", from " + ymin + " to " + ymax + "). Ask for something smaller: at most 25 x 25 and 30 high.";
        if (py + ymin < w.getMinHeight() + 6 || py + ymax > w.getMaxHeight() - 4) return "That is too high or too low to build.";
        int cu = cols / 2, cv = rows / 2, fi = Math.max(0, Arrays.asList(FACES).indexOf(facing)), turns = (fi + 2) % 4, gap = rows - 1 - cv + 3;
        int x = px + (facing.equals("east") ? gap : facing.equals("west") ? -gap : 0), y = py, z = pz + (facing.equals("south") ? gap : facing.equals("north") ? -gap : 0);
        int[][] rot = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};
        int[] r = rot[turns];
        Site s = new Site();
        s.id = id; s.w = w; s.x = x; s.y = y; s.z = z; s.fx = r[1]; s.fz = r[3]; s.out = gap + 1; s.name = title.toLowerCase(Locale.ROOT); s.plan = true; s.wood = "OAK";
        s.where = w.getName() + " " + x + " " + y + " " + z;
        // the drawing, cell by cell
        Map<String, String> plan = new java.util.LinkedHashMap<>();          // "u y v" -> what
        Set<String> unknown = new java.util.TreeSet<>(), beds = new HashSet<>();
        for (Map.Entry<Integer, List<String>> ly : layers.entrySet()) for (int row = 0; row < ly.getValue().size(); row++) {
            String line = ly.getValue().get(row);
            for (int col = 0; col < line.length(); col++) {
                String spec = legend.get(line.charAt(col));
                if (spec == null || spec.equals("air") || spec.equals("cave_air")) continue;
                String base = spec.contains("[") ? spec.substring(0, spec.indexOf('[')) : spec, props = spec.contains("[") && spec.endsWith("]") ? spec.substring(spec.indexOf('[')) : "";
                Material m = Helper.mat(base);
                String up = m == null ? "" : m.name();
                if (m == null || !m.isBlock() || m.isAir() || NO_BUILD.contains(up) || up.contains("COMMAND") || up.contains("STRUCTURE") || up.contains("PORTAL") || up.contains("INFESTED")) { unknown.add(base.replace('_', ' ')); continue; }
                String key = (col - cu) + " " + ly.getKey() + " " + (row - cv), word = base;
                props = turn(props, turns);
                if (up.endsWith("_BED")) { beds.add(key); continue; }
                String bare = up.startsWith("STRIPPED_") ? up.substring(9) : up;
                for (String wd : new String[] {"DARK_OAK", "PALE_OAK", "OAK", "SPRUCE", "BIRCH", "JUNGLE", "ACACIA", "MANGROVE", "CHERRY", "BAMBOO", "CRIMSON", "WARPED"}) if (bare.startsWith(wd + "_")) {
                    String part = bare.substring(wd.length() + 1);
                    if (part.equals("STEM") || part.equals("HYPHAE") || part.equals("BLOCK") || part.equals("WOOD")) part = "LOG";
                    if (part.equals("LEAVES") || part.equals("SAPLING") || part.equals("PROPAGULE") || part.equals("ROOTS") || part.equals("MOSAIC")) break;
                    if (part.equals("DOOR")) {
                        boolean upper = plan.getOrDefault((col - cu) + " " + (ly.getKey() - 1) + " " + (row - cv), "").startsWith("DOOR[half=lower");
                        String face = "north";
                        Matcher fm = Pattern.compile("facing=([a-z]+)").matcher(spec);
                        if (fm.find() && Arrays.asList(FACES).contains(fm.group(1))) face = fm.group(1);
                        if (upper) { String low = plan.get((col - cu) + " " + (ly.getKey() - 1) + " " + (row - cv)); props = low.substring(low.indexOf('[')).replace("half=lower", "half=upper"); }
                        else props = "[half=lower,facing=" + FACES[(Arrays.asList(FACES).indexOf(face) + turns) % 4] + "]";
                    }
                    word = Set.of("LOG", "PLANKS", "STAIRS", "SLAB", "DOOR").contains(part) ? part : "WOOD:" + part;
                    break;
                }
                plan.put(key, word + props);
            }
        }
        for (String head : new ArrayList<>(beds)) {                         // a bed is two blocks: the other half next to it
            if (!beds.contains(head)) continue;
            String[] a = head.split(" ");
            int u = Integer.parseInt(a[0]), hy = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            int[][] side = {{0, 1}, {-1, 0}, {0, -1}, {1, 0}};               // south, west, north, east
            int pick = -1;
            for (int k = 0; k < 4 && pick < 0; k++) if (beds.contains((u + side[k][0]) + " " + hy + " " + (v + side[k][1]))) pick = k;
            for (int k = 0; k < 4 && pick < 0; k++) if (!plan.containsKey((u + side[k][0]) + " " + hy + " " + (v + side[k][1])) && Math.abs(u + side[k][0]) <= cu && Math.abs(v + side[k][1]) <= cv) pick = k;
            beds.remove(head);
            if (pick < 0) continue;
            String foot = (u + side[pick][0]) + " " + hy + " " + (v + side[pick][1]), face = FACES[((pick + 2) % 4 + turns) % 4];   // it faces from the foot to the head
            beds.remove(foot);
            plan.put(head, "BED[part=head,facing=" + face + "]");
            plan.put(foot, "BED[part=foot,facing=" + face + "]");
        }
        if (plan.isEmpty()) return "Nothing in my drawing is something I can build" + (unknown.isEmpty() ? "." : " (" + String.join(", ", unknown) + ").") + " Ask me again?";
        if (plan.size() > 4000) return "What I drew takes " + plan.size() + " blocks. That is too much to build by hand; ask for something smaller.";
        // the plot: everything from the top down, and the hole for what goes under the ground
        if (buildingOn.contains(id) && w.getName().equals(core.conf.getString(core.path(id, "built.world")))) { s.had.addAll(core.conf.getStringList(core.path(id, "built.blocks"))); s.placed.addAll(s.had); }
        for (int h = ymax + 1; h >= ymin; h--) for (int u = -cu - 1; u <= cols - cu; u++) for (int v = -cv - 1; v <= rows - cv; v++) {
            boolean rim = u < -cu || u > cols - 1 - cu || v < -cv || v > rows - 1 - cv;
            if (h < 0 && rim) continue;
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (s.had.contains(b.getX() + " " + b.getY() + " " + b.getZ())) continue;      // what it set itself last time stays
            if (buildingOn.contains(id) && !Helper.natural(b.getType())) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }   // what its owner has set in the meantime stays too, and is not hers to take down
            if (!Helper.natural(b.getType()) && h <= 0) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }      // a foundation at your feet or lower: it stays, she builds on it
            if (!Helper.natural(b.getType())) return "I cannot build there: there is " + Helper.word(b.getType()) + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + ", above where you stand. I build on untouched ground or on a foundation at your feet or lower. Pick another spot.";
            if (!b.getType().isAir()) s.clear.add(b);
        }
        List<String> keys = new ArrayList<>(plan.keySet());
        keys.sort(Comparator.<String>comparingInt(k -> Integer.parseInt(k.split(" ")[1])).thenComparingInt(k -> plan.get(k).contains("part=foot") || plan.get(k).contains("half=upper") ? 1 : 0));
        for (String k : keys) {
            String[] a = k.split(" ");
            int u = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            s.place.add(new Object[] {w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v), plan.get(k)});
        }
        // what there is around: trees and sand within 40 blocks, the chests at home
        Map<String, List<Block>> trees = new HashMap<>();
        List<Block> sands = new ArrayList<>();
        survey(w, x, y, z, trees, sands);
        if (farSand.containsKey(id)) { Set<Block> known = new HashSet<>(sands); for (Block b : farSand.get(id)) if (known.add(b)) sands.add(b); }
        if (farWood.containsKey(id)) for (Map.Entry<String, List<Block>> e : farWood.get(id).entrySet()) {
            List<Block> logs = trees.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            Set<Block> known = new HashSet<>(logs);
            for (Block b : e.getValue()) if (known.add(b)) logs.add(b);
        }
        Location hm = core.homeIn(id, w);
        List<Inventory> homeChests = hm == null ? new ArrayList<>() : Helper.stores(hm, Helper.HOME);
        List<Inventory> all = new ArrayList<>(stock(s));
        all.addAll(homeChests);
        String best = "OAK";
        int bestLogs = -1;
        for (String wd : WOODS) {
            Material lg = Helper.mat(wd + "_LOG"), pl = Helper.mat(wd + "_PLANKS");
            if (lg == null || pl == null || Helper.mat(wd + "_STAIRS") == null) continue;
            int have = Helper.count(all, k -> k == lg) + Helper.count(all, k -> k == pl) / 4 + trees.getOrDefault(wd, List.of()).size();
            if (have > bestLogs) { bestLogs = have; best = wd; }
        }
        s.wood = best;
        Material lg = Helper.mat(best + "_LOG"), pl = Helper.mat(best + "_PLANKS");
        Map<Material, Integer> want = new java.util.LinkedHashMap<>();
        for (String what : plan.values()) {
            String base = what.contains("[") ? what.substring(0, what.indexOf('[')) : what;
            if (what.contains("half=upper") || what.contains("part=foot")) continue;
            Material m = base.equals("BED") ? pl : asItem(planItem(s, base));
            if (m == null) { unknown.add(base.toLowerCase(Locale.ROOT).replace("wood:", "").replace('_', ' ')); continue; }
            want.merge(m, base.equals("BED") ? 3 : 1, Integer::sum);
        }
        double[] acc = new double[3];
        Set<String> cannot = new java.util.TreeSet<>();
        int before0 = Helper.count(stock(s), k -> k == lg) + Helper.count(stock(s), k -> k == pl) / 4, before1 = Helper.count(stock(s), k -> k == Material.COBBLESTONE),
                before2 = Helper.count(stock(s), k -> k == Material.GLASS) + Helper.count(stock(s), k -> k == Material.SAND);
        for (Map.Entry<Material, Integer> e : want.entrySet()) {
            Material k = e.getKey();
            s.keep.add(k);
            int lack = e.getValue() - Helper.count(stock(s), q -> q == k), there = Math.min(lack, Helper.count(homeChests, q -> q == k));
            if (there > 0) { Helper.take(homeChests, q -> q == k, there); stow(s, new ItemStack(k, there)); lack -= there; }
            if (lack > 0 && !raw(s, k, lack, acc, 0)) cannot.add(lack + " " + Helper.word(k));
        }
        Object[][] bring = {{lg, (int) Math.ceil(acc[0]) + 2}, {Material.COBBLESTONE, (int) Math.ceil(acc[1])}, {Material.SAND, (int) Math.ceil(acc[2])}};
        for (Object[] b : bring) {
            Material k = (Material) b[0];
            int there = Math.min((Integer) b[1], Helper.count(homeChests, q -> q == k));
            if (there > 0) { Helper.take(homeChests, q -> q == k, there); stow(s, new ItemStack(k, there)); }
        }
        for (Inventory inv : homeChests) for (ItemStack it : inv.getContents()) if (!Helper.empty(it) && Tag.WOOL.isTagged(it.getType()) && it.getAmount() >= 3 && plan.containsValue("BED[part=head,facing=south]") | plan.values().stream().anyMatch(q -> q.startsWith("BED")) && Helper.count(stock(s), q -> Tag.WOOL.isTagged(q)) < 3) {
            it.setAmount(it.getAmount() - 3);
            stow(s, new ItemStack(it.getType(), 3));
        }
        if (plan.values().stream().noneMatch(q -> q.startsWith("BED"))) s.sheep = 99;
        int logs = acc[0] > 0 ? (int) Math.ceil(acc[0]) + 2 : 0, cobble = (int) Math.ceil(acc[1]), sand = (int) Math.ceil(acc[2]);
        s.needLogs = before0 + logs;
        int woodHere = Helper.count(stock(s), k -> k == lg) + Helper.count(stock(s), k -> k == pl) / 4 + trees.getOrDefault(best, List.of()).size();
        int sandHere = Helper.count(stock(s), k -> k == Material.GLASS) + Helper.count(stock(s), k -> k == Material.SAND) + sands.size(), sandAll = before2 + sand;
        if ((woodHere < s.needLogs || sandHere < sandAll) && !farWood.containsKey(id) && !seeking.contains(id) && w.getEnvironment() == World.Environment.NORMAL) {
            int need = s.needLogs;
            boolean on = buildingOn.contains(id), lackWood = woodHere < need, lackSand = sandHere < sandAll;
            endSite(s);                                                    // what was taken out of the chests goes back into the bag
            seekFar(id, w, x, y, z, lackWood ? need : 0, lackSand ? sandAll - sandHere + sands.size() : 0, (fw, fs) -> {
                farWood.put(id, fw);
                farSand.put(id, fs);
                if (on) buildingOn.add(id);
                try { return blueprint(id, j, w, px, py, pz, facing); } finally { farWood.remove(id); farSand.remove(id); if (on) buildingOn.remove(id); }
            });
            return "I have drawn a " + title.toLowerCase(Locale.ROOT) + ". I find too little " + (lackWood ? lackSand ? "wood and sand" : "wood" : "sand") + " around here, so I go looking for it first, however far that is, and then I build.";
        }
        s.needCobble = before1 + cobble;
        s.needSand = before2 + sand;
        s.trees.addAll(trees.getOrDefault(best, List.of()));
        Location centre = new Location(w, x, y, z);
        sands.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(centre)));
        s.sand.addAll(sands.subList(0, Math.min(sands.size(), sand + 4)));
        holdFar(s);
        for (int dy = 4 - ymin; dy <= 40 - ymin && s.stone.size() < cobble + 12 && y - dy > w.getMinHeight() + 6; dy++) for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) {   // a quarry right under it
            Block b = w.getBlockAt(x + u, y - dy, z + v);
            if (b.getType() == Material.STONE || b.getType() == Material.COBBLESTONE) s.stone.add(b);
        }
        startSite(s);
        core.awayLog(id, "starts to build a " + s.name + " at " + s.where + " (" + s.clear.size() + " blocks to clear, " + s.place.size() + " to set, wood: " + best.toLowerCase(Locale.ROOT) + ")");
        int mins = Math.max(3, (s.clear.size() + logs + cobble + sand + s.place.size()) * 8 / 20 / 60 + 2);
        String short1 = logs > bestLogs ? " I find only about " + Math.max(0, bestLogs) + " of the " + logs + " logs it takes, so some wood may be missing." : "";
        String miss = cannot.isEmpty() ? "" : " I have no way to make " + String.join(", ", cannot) + ": put it in a chest at home or by the plot and I use it, or I leave it out.";
        String odd = unknown.isEmpty() ? "" : " I left out what I cannot set: " + String.join(", ", unknown) + ".";
        return "I have drawn a " + s.name + ": " + cols + " x " + rows + " and " + (ymax - ymin + 1) + " high, " + s.place.size() + " blocks, at " + x + " " + y + " " + z + ". I clear the plot, fetch what I need and build it by hand, so give me about "
                + mins + " minutes." + short1 + miss + odd + " Say stop building if you change your mind.";
    }

    /** Fences, panes, bars and walls reach out to what stands next to them (they are set without the game's own tidying). */
    static void connect(Block b) {
        BlockData d = b.getBlockData();
        BlockFace[] sides = {BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST};
        if (d instanceof org.bukkit.block.data.type.Wall wall) {
            for (BlockFace f : sides) { Material n = b.getRelative(f).getType(); wall.setHeight(f, n.isOccluding() || Tag.WALLS.isTagged(n) ? org.bukkit.block.data.type.Wall.Height.LOW : org.bukkit.block.data.type.Wall.Height.NONE); }
            b.setBlockData(wall, false);
        } else if (d instanceof org.bukkit.block.data.MultipleFacing mf && (Tag.FENCES.isTagged(b.getType()) || b.getType().name().endsWith("_PANE") || b.getType() == Material.IRON_BARS)) {
            for (BlockFace f : sides) if (mf.getAllowedFaces().contains(f)) {
                Material n = b.getRelative(f).getType();
                mf.setFace(f, n.isOccluding() || n == b.getType() || Tag.FENCES.isTagged(b.getType()) && (Tag.FENCES.isTagged(n) || Tag.FENCE_GATES.isTagged(n)) || n.name().endsWith("_PANE") && b.getType().name().endsWith("_PANE") || n.name().endsWith("GLASS") && b.getType().name().endsWith("_PANE"));
            }
            b.setBlockData(mf, false);
        }
    }

    /** "build a house here", "build a house 100 64 100 [world]", "stop building", "undo build" (its own last house),
     *  "tear down the house here / 100 64 100" (anything built, asked by an op or from the console, after a yes). */
    boolean buildOrder(UUID id, Player p, String t, Consumer<String> tell) {
        if (t.matches("(?:stop|cancel|avbryt|stopp)(?: the)? ?(?:build|building|bygg|bygging|byggingen|bygginga|riving|rivingen)")) {
            Site s = sites.get(id);
            if (s == null) { tell.accept("I am not building anything."); return true; }
            endSite(s);
            tell.accept(s.down ? "I stopped. What is left stands as it is." : "I stopped. What I have built stands as it is; say undo build and I take it down again.");
            return true;
        }
        if (razing.containsKey(id) && t.matches("(?:(?:tear|take|pull) (?:it )?down |riv |ta ned )?(?:yes|ja)(?: (?:tear|take|pull) it down| riv)?")) {
            Object[] r = razing.remove(id);
            if (System.currentTimeMillis() - (Long) r[1] > 60_000) { tell.accept("That was more than a minute ago. Ask me again."); return true; }
            @SuppressWarnings("unchecked") List<Block> blocks = (List<Block>) r[0];
            tell.accept(raze(id, blocks, false));
            return true;
        }
        Matcher d = DOWN_WORDS.matcher(t);
        if (d.matches()) {
            Matcher c = XYZ.matcher(d.group(1));
            boolean at = c.find(), here = d.group(1).matches(".*\\b(here|her|this|that|den|det der)\\b.*");
            if (!at && !here) { tell.accept(unbuild(id)); return true; }
            if (p != null && !p.isOp()) { tell.accept("Only an op can ask me to take down something I did not build. My own last house I take down when you say undo build."); return true; }
            Block start;
            if (at) {
                World w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : core.home(id) != null ? core.home(id).getWorld() : Bukkit.getWorlds().get(0);
                start = w.getBlockAt(Integer.parseInt(c.group(1)), Integer.parseInt(c.group(2)), Integer.parseInt(c.group(3)));
            } else if (p != null) {
                start = p.getTargetBlockExact(10);
                if (start == null) start = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
            } else { tell.accept("Where? Say tear down the house <x> <y> <z> [world]."); return true; }
            if (!builtBlock(start.getType())) {
                Block found = null;
                for (int dx = -2; dx <= 2 && found == null; dx++) for (int dy = -2; dy <= 2 && found == null; dy++) for (int dz = -2; dz <= 2 && found == null; dz++)
                    if (builtBlock(start.getRelative(dx, dy, dz).getType())) found = start.getRelative(dx, dy, dz);
                if (found == null) { tell.accept("I see nothing built at " + start.getX() + " " + start.getY() + " " + start.getZ() + "."); return true; }
                start = found;
            }
            List<Block> all = new ArrayList<>();
            Set<Block> seen = new HashSet<>(List.of(start));
            Deque<Block> open = new ArrayDeque<>(List.of(start));
            int[] lo = {start.getX(), start.getY(), start.getZ()}, hi = lo.clone();
            while (!open.isEmpty() && all.size() <= 4000) {
                Block b = open.poll();
                all.add(b);
                lo[0] = Math.min(lo[0], b.getX()); lo[1] = Math.min(lo[1], b.getY()); lo[2] = Math.min(lo[2], b.getZ());
                hi[0] = Math.max(hi[0], b.getX()); hi[1] = Math.max(hi[1], b.getY()); hi[2] = Math.max(hi[2], b.getZ());
                for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                    Block nb = b.getRelative(dx, dy, dz);
                    if (builtBlock(nb.getType()) && seen.add(nb)) open.add(nb);
                }
            }
            if (all.size() > 4000) { tell.accept("That is more than 4000 blocks hanging together. Too big for me to be sure what you mean."); return true; }
            razing.put(id, new Object[] {all, System.currentTimeMillis()});
            tell.accept("That is " + all.size() + " blocks, from " + lo[0] + " " + lo[1] + " " + lo[2] + " to " + hi[0] + " " + hi[1] + " " + hi[2] + ". I take it apart block by block and keep the materials. Say tear down yes within a minute.");
            return true;
        }
        if (t.equals("mergeskin")) {                                       // the skin is signed now: the figure shows it to all, and so does a merged owner
            Player on = Bukkit.getPlayer(id);
            Helper.H hh = core.helpers.get(id);
            if (on != null && hh != null && hh.m != null) hh.m.setProfile(core.profile(on));
            if (on != null && hh != null && hh.merged) core.wearSkin(on, false);
            return true;
        }
        if (t.equals("buildplan")) { Player on = Bukkit.getPlayer(id); buildPlan(id, on != null ? said -> core.say(on, said) : tell); return true; }
        if (t.startsWith("buildon") || BUILD_ON.matcher(t).matches()) { Player on = Bukkit.getPlayer(id); buildOn(id, t.startsWith("buildon") ? t.substring(7) : "", on != null ? said -> core.say(on, said) : tell); return true; }
        if (!PLAIN_HOUSE.matcher(t).matches() && designOrder(id, p, t, tell)) return true;      // more than "a little house": it is drawn first
        Matcher m = BUILD_WORDS.matcher(t);
        if (!m.matches()) return false;
        Matcher c = XYZ.matcher(m.group(2));
        World w;
        int x, y, z, turns = 0;
        if (c.find()) {
            w = c.group(4) != null && Bukkit.getWorld(c.group(4)) != null ? Bukkit.getWorld(c.group(4)) : p != null ? p.getWorld() : core.home(id) != null ? core.home(id).getWorld() : Bukkit.getWorlds().get(0);
            x = Integer.parseInt(c.group(1)); y = Integer.parseInt(c.group(2)); z = Integer.parseInt(c.group(3));
        } else if (p != null) {                                           // six blocks in front of the player, the door towards them
            String f = p.getFacing().name().toLowerCase(Locale.ROOT);
            int fi = Math.max(0, Arrays.asList(FACES).indexOf(f));
            turns = (fi + 2) % 4;
            Location l = p.getLocation();
            w = l.getWorld();
            x = l.getBlockX() + (f.equals("east") ? 6 : f.equals("west") ? -6 : 0); y = l.getBlockY(); z = l.getBlockZ() + (f.equals("south") ? 6 : f.equals("north") ? -6 : 0);
        } else { tell.accept("Where? Say build a house <x> <y> <z> [world] (y is the floor you walk on)."); return true; }
        tell.accept(house(id, w, x, y, z, turns, null));
        return true;
    }

    /** The trees (by kind of wood) and the open sand within 40 blocks of a plot. */
    void survey(World w, int x, int y, int z, Map<String, List<Block>> trees, List<Block> sands) {
        int lo = Math.max(w.getMinHeight() + 1, y - 12), hi = Math.min(w.getMaxHeight() - 2, y + 30);
        for (int cx = (x - 40) >> 4; cx <= (x + 40) >> 4; cx++) for (int cz = (z - 40) >> 4; cz <= (z + 40) >> 4; cz++) {
            if (!w.isChunkGenerated(cx, cz)) continue;
            org.bukkit.ChunkSnapshot snap = w.getChunkAt(cx, cz).getChunkSnapshot();
            for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) for (int yy = lo; yy <= hi; yy++) {
                Material t = snap.getBlockType(bx, yy, bz);
                if (t == Material.SAND) { if (snap.getBlockType(bx, yy + 1, bz).isAir() && sands.size() < 400) sands.add(w.getBlockAt(cx * 16 + bx, yy, cz * 16 + bz)); continue; }
                if (!Tag.LOGS.isTagged(t) || !t.name().endsWith("_LOG") || t.name().startsWith("STRIPPED") || !Tag.DIRT.isTagged(snap.getBlockType(bx, yy - 1, bz))) continue;
                Block base = w.getBlockAt(cx * 16 + bx, yy, cz * 16 + bz), top = base;
                while (top.getRelative(BlockFace.UP).getType() == t && top.getY() < hi + 30) top = top.getRelative(BlockFace.UP);
                boolean tree = false;
                for (int dx = -1; dx <= 1 && !tree; dx++) for (int dy = -1; dy <= 1 && !tree; dy++) for (int dz = -1; dz <= 1 && !tree; dz++)
                    if (top.getRelative(dx, dy, dz).getBlockData() instanceof org.bukkit.block.data.type.Leaves lv && !lv.isPersistent()) tree = true;
                if (!tree) continue;                                       // a log somebody set down, not a tree
                List<Block> logs = trees.computeIfAbsent(t.name().replace("_LOG", ""), k -> new ArrayList<>());
                Set<Block> seen = new HashSet<>(List.of(base));
                Deque<Block> open = new ArrayDeque<>(List.of(base));
                int n = 0;
                while (!open.isEmpty() && n++ < 120) {
                    Block b = open.poll();
                    if (!logs.contains(b)) logs.add(b);
                    for (int dx = -1; dx <= 1; dx++) for (int dy = 0; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                        Block nb = b.getRelative(dx, dy, dz);
                        if (nb.getType() == t && seen.add(nb)) open.add(nb);
                    }
                }
            }
        }
    }

    final Set<UUID> seeking = new HashSet<>();

    static final String[] TREE_BIOMES = {"forest", "birch_forest", "dark_forest", "flower_forest", "old_growth_birch_forest", "taiga", "old_growth_pine_taiga", "old_growth_spruce_taiga",
            "snowy_taiga", "jungle", "sparse_jungle", "savanna", "cherry_grove", "windswept_forest", "grove", "swamp", "mangrove_swamp", "pale_garden", "wooded_badlands"};

    static String xyz(Block b) { return b.getX() + " " + b.getY() + " " + b.getZ(); }

    static List<String> xyzs(Collection<Block> bs) { List<String> l = new ArrayList<>(); for (Block b : bs) l.add(xyz(b)); return l; }

    static void blocks(World w, List<String> from, Collection<Block> to) {
        for (String k : from) {
            String[] a = k.split(" ");
            try { to.add(w.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]))); } catch (RuntimeException ignored) { }
        }
    }

    void saveSites() {
        for (Site s : new ArrayList<>(sites.values())) {
            if (s.task != null) s.task.cancel();
            String k = core.path(s.id, "site");
            core.conf.set(k, null);
            core.conf.set(k + ".world", s.w.getName());
            core.conf.set(k + ".at", List.of(s.x, s.y, s.z, s.fx, s.fz, s.out, s.phase, s.at, s.needLogs, s.needCobble, s.needSand, s.sheep, s.broke, s.put));
            core.conf.set(k + ".where", s.where);
            core.conf.set(k + ".wood", s.wood);
            core.conf.set(k + ".name", s.name);
            core.conf.set(k + ".bed", s.bed);
            core.conf.set(k + ".flags", (s.down ? "d" : "") + (s.door ? "o" : "") + (s.plan ? "p" : ""));
            core.conf.set(k + ".clear", xyzs(s.clear));
            core.conf.set(k + ".trees", xyzs(s.trees));
            core.conf.set(k + ".stone", xyzs(s.stone));
            core.conf.set(k + ".sand", xyzs(s.sand));
            List<String> place = new ArrayList<>();
            for (Object[] o : s.place) place.add(xyz((Block) o[0]) + "|" + o[1]);
            core.conf.set(k + ".place", place);
            core.conf.set(k + ".placed", s.placed);
            core.conf.set(k + ".lacked", new ArrayList<>(s.lacked));
            core.conf.set(k + ".had", new ArrayList<>(s.had));
            List<String> keep = new ArrayList<>();
            for (Material m : s.keep) keep.add(m.name());
            core.conf.set(k + ".keep", keep);
            core.conf.set(k + ".pile", Base64.getEncoder().encodeToString(ItemStack.serializeItemsAsBytes(s.pile.getContents())));
            core.awayLog(s.id, "saves the " + s.name + " at " + s.where + " for after the restart");
        }
        sites.clear();
    }

    void loadSites() {
        for (String key : core.conf.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(key); } catch (IllegalArgumentException e) { continue; }
            String k = core.path(id, "site");
            if (!core.conf.contains(k)) continue;
            World w = Bukkit.getWorld(core.conf.getString(k + ".world", ""));
            try {
                if (w == null || sites.containsKey(id)) continue;
                List<Integer> n = core.conf.getIntegerList(k + ".at");
                Site s = new Site();
                s.id = id; s.w = w;
                s.x = n.get(0); s.y = n.get(1); s.z = n.get(2); s.fx = n.get(3); s.fz = n.get(4); s.out = n.get(5); s.phase = n.get(6); s.at = n.get(7);
                s.needLogs = n.get(8); s.needCobble = n.get(9); s.needSand = n.get(10); s.sheep = n.get(11); s.broke = n.get(12); s.put = n.get(13);
                s.where = core.conf.getString(k + ".where");
                s.wood = core.conf.getString(k + ".wood", "OAK");
                s.name = core.conf.getString(k + ".name", "little house");
                s.bed = core.conf.getString(k + ".bed");
                String f = core.conf.getString(k + ".flags", "");
                s.down = f.contains("d"); s.door = f.contains("o"); s.plan = f.contains("p");
                blocks(w, core.conf.getStringList(k + ".clear"), s.clear);
                blocks(w, core.conf.getStringList(k + ".trees"), s.trees);
                blocks(w, core.conf.getStringList(k + ".stone"), s.stone);
                blocks(w, core.conf.getStringList(k + ".sand"), s.sand);
                for (String pl : core.conf.getStringList(k + ".place")) {
                    int bar = pl.indexOf('|');
                    List<Block> one = new ArrayList<>();
                    blocks(w, List.of(pl.substring(0, bar)), one);
                    if (!one.isEmpty()) s.place.add(new Object[] {one.get(0), pl.substring(bar + 1)});
                }
                s.placed.addAll(core.conf.getStringList(k + ".placed"));
                s.lacked.addAll(core.conf.getStringList(k + ".lacked"));
                s.had.addAll(core.conf.getStringList(k + ".had"));
                for (String m : core.conf.getStringList(k + ".keep")) { Material mm = Helper.mat(m); if (mm != null) s.keep.add(mm); }
                String pile = core.conf.getString(k + ".pile");
                if (pile != null) s.pile.setContents(ItemStack.deserializeItemsFromBytes(Base64.getDecoder().decode(pile)));
                startSite(s);
                holdFar(s);
                core.awayLog(id, "goes on with the " + s.name + " at " + s.where + " after the restart");
                Player p = Bukkit.getPlayer(id);
                if (p != null) core.say(p, "I go on with the " + s.name + " at " + s.where + " where I stopped.");
            } catch (RuntimeException e) {
                core.plugin.getLogger().warning("Could not take up the building of " + id + " again: " + e);
            }
            core.conf.set(k, null);
        }
        core.save();
    }

    /** The forest further away stays loaded while she fells there. */
    void holdFar(Site s) {
        Set<Long> faraway = new HashSet<>();
        List<Block> out = new ArrayList<>(s.trees);
        out.addAll(s.sand);
        for (Block b : out) if ((Math.abs(b.getX() - s.x) > 40 || Math.abs(b.getZ() - s.z) > 40) && faraway.add(((long) (b.getX() >> 4) << 32) ^ ((b.getZ() >> 4) & 0xffffffffL))) {
            s.w.addPluginChunkTicket(b.getX() >> 4, b.getZ() >> 4, core.plugin);
            s.held.add(new long[] {b.getX() >> 4, b.getZ() >> 4});
        }
    }

    final Map<UUID, Map<String, List<Block>>> farWood = new HashMap<>();

    static int most(Map<String, List<Block>> trees) {
        int n = 0;
        for (Map.Entry<String, List<Block>> e : trees.entrySet()) if (Helper.mat(e.getKey() + "_STAIRS") != null) n = Math.max(n, e.getValue().size());
        return n;
    }

    final Map<UUID, List<Block>> farSand = new HashMap<>();

    static final String[] SAND_BIOMES = {"desert", "beach"};

    /** Too little wood or sand by the plot: first the land already seen within 120 blocks, then the nearest forest (and
     *  the nearest desert or beach) however far that is, a little at a time so the server keeps its pace. When it is
     *  found the building is ordered again with the trees and the sand. */
    void seekFar(UUID id, World w, int x, int y, int z, int needLogs, int needSand, java.util.function.BiFunction<Map<String, List<Block>>, List<Block>, String> again) {
        seeking.add(id);
        Map<String, List<Block>> found = new HashMap<>();
        List<Block> sand = new ArrayList<>();
        List<int[]> spots = new ArrayList<>();
        for (int dx = -80; dx <= 80; dx += 80) for (int dz = -80; dz <= 80; dz += 80) if (dx != 0 || dz != 0) spots.add(new int[] {x + dx, z + dz});
        int[] k = {0, 0, 0, 0};                                            // spot, stage, target x, target z
        int[] wood = {0, x, z, 0}, snd = {0, x, z, 0};                    // tries, last x, last z, found there (1)
        Deque<int[]> gen = new ArrayDeque<>();
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        Runnable done = () -> {
            task[0].cancel();
            seeking.remove(id);
            Player p = Bukkit.getPlayer(id);
            if (p != null && wood[0] > 0 && most(found) >= needLogs) core.say(p, "I found a forest " + (int) Math.hypot(wood[1] - x, wood[2] - z) + " blocks away, at x " + wood[1] + ", z " + wood[2] + ".");
            if (p != null && snd[0] > 0 && sand.size() >= needSand) core.say(p, "I found sand " + (int) Math.hypot(snd[1] - x, snd[2] - z) + " blocks away, at x " + snd[1] + ", z " + snd[2] + ".");
            String res = again.apply(found, sand);
            if (p != null) core.say(p, res); else core.awayLog(id, res);
        };
        task[0] = Bukkit.getScheduler().runTaskTimer(core.plugin, () -> {
            try {
                boolean lackWood = most(found) < needLogs, lackSand = sand.size() < needSand;
                if (k[1] == 0) {
                    if (k[0] < spots.size() && (lackWood || lackSand)) {
                        int[] sp = spots.get(k[0]++);
                        if (w.isChunkGenerated(sp[0] >> 4, sp[1] >> 4)) survey(w, sp[0], w.getHighestBlockYAt(sp[0], sp[1], org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES), sp[1], found, sand);
                        return;
                    }
                    k[1] = 1;
                }
                if (k[1] == 1) {                                           // where is the nearest forest, or the nearest sand?
                    int[] t = lackWood && wood[0] < 8 ? wood : lackSand && snd[0] < 4 ? snd : null;
                    if (t == null) { done.run(); return; }
                    List<org.bukkit.block.Biome> biomes = new ArrayList<>();
                    Registry<org.bukkit.block.Biome> reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
                    for (String b : t == wood ? TREE_BIOMES : SAND_BIOMES) { org.bukkit.block.Biome bm = reg.get(NamespacedKey.minecraft(b)); if (bm != null) biomes.add(bm); }
                    Location from = new Location(w, t[0] == 0 ? x : t[1] + (t[1] - x >= 0 ? 96 : -96), y, t[0] == 0 ? z : t[2] + (t[2] - z >= 0 ? 96 : -96));
                    t[0]++;
                    org.bukkit.util.BiomeSearchResult r = w.locateNearestBiome(from, 12800, 64, 64, biomes.toArray(new org.bukkit.block.Biome[0]));
                    if (r == null) { t[0] = 99; return; }                  // none in this world: on to the other thing, or home
                    k[2] = t[1] = r.getLocation().getBlockX(); k[3] = t[2] = r.getLocation().getBlockZ();
                    for (int cx = (k[2] - 40) >> 4; cx <= (k[2] + 40) >> 4; cx++) for (int cz = (k[3] - 40) >> 4; cz <= (k[3] + 40) >> 4; cz++) gen.add(new int[] {cx, cz});
                    k[1] = 2;
                    return;
                }
                for (int i = 0; i < 2 && !gen.isEmpty(); i++) { int[] c = gen.poll(); w.getChunkAt(c[0], c[1]); }      // the land there is made, two chunks at a time
                if (!gen.isEmpty()) return;
                survey(w, k[2], w.getHighestBlockYAt(k[2], k[3], org.bukkit.HeightMap.MOTION_BLOCKING_NO_LEAVES), k[3], found, sand);
                k[1] = 1;
            } catch (RuntimeException e) {
                core.plugin.getLogger().warning("Search for wood and sand stopped: " + e);
                task[0].cancel();
                seeking.remove(id);
            }
        }, 4L, 4L);
    }

    /** A little cottage, 7 x 7 with a gabled roof, built by hand: the plot is cleared, the wood is felled from the trees
     *  around (or taken from the bag and the chests at home and by the plot), stone is dug under the plot, sand is dug
     *  and smelted to glass, and every part is crafted and set one block at a time. Only on untouched ground. */
    String house(UUID id, World w, int x, int y, int z, int turns, Map<String, List<Block>> far) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        if (seeking.contains(id)) return "I am out looking for wood for the house. I will tell you when I have found it.";
        if (y < w.getMinHeight() + 24 || y > w.getMaxHeight() - 12) return "That is too high or too low to build.";
        String front = FACES[turns], right = FACES[(turns + 3) % 4], back = FACES[(turns + 2) % 4], left = FACES[(turns + 1) % 4];
        Map<String, String> plan = new java.util.LinkedHashMap<>();        // "u h v" -> what goes there
        for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) {
            boolean edge = Math.abs(u) == 3 || Math.abs(v) == 3, corner = Math.abs(u) == 3 && Math.abs(v) == 3;
            plan.put(u + " -1 " + v, edge ? "cobblestone" : "PLANKS");
            if (edge) for (int h = 0; h <= 2; h++) plan.put(u + " " + h + " " + v, corner ? "LOG" : "PLANKS");
            if (Math.abs(u) == 3) plan.put(u + " 3 " + v, "PLANKS");
        }
        for (int h = 3; h <= 6; h++) for (int u = -(6 - h); u <= 6 - h; u++) { plan.put(u + " " + h + " 3", "PLANKS"); plan.put(u + " " + h + " -3", "PLANKS"); }
        for (String win : new String[] {"-3 1 0", "3 1 0", "0 1 -3", "-2 1 3", "2 1 3", "0 4 3", "0 4 -3"}) plan.put(win, "glass");
        plan.put("0 0 3", "DOOR[half=lower,facing=" + back + "]");
        plan.put("0 1 3", "DOOR[half=upper,facing=" + back + "]");
        for (int k = 0; k <= 3; k++) for (int v = -4; v <= 4; v++) {
            plan.put(-(4 - k) + " " + (3 + k) + " " + v, "STAIRS[facing=" + right + "]");
            plan.put((4 - k) + " " + (3 + k) + " " + v, "STAIRS[facing=" + left + "]");
        }
        for (int v = -4; v <= 4; v++) plan.put("0 7 " + v, "SLAB");
        plan.put("2 0 -2", "furnace[facing=" + front + "]");
        for (int h = 1; h <= 6; h++) plan.put("2 " + h + " -2", "cobblestone");
        plan.put("2 7 -2", "campfire[lit=true]");
        plan.put("-2 0 -2", "BED[part=head,facing=" + back + "]");
        plan.put("-2 0 -1", "BED[part=foot,facing=" + back + "]");
        plan.put("-2 0 1", "chest[facing=" + right + "]");
        plan.put("2 0 0", "crafting_table");
        plan.put("2 1 0", "lantern");
        int[][] rot = {{1, 0, 0, 1}, {0, -1, 1, 0}, {-1, 0, 0, -1}, {0, 1, -1, 0}};      // dx = a*u + b*v, dz = c*u + d*v
        int[] r = rot[turns];
        Site s = new Site();
        s.id = id; s.w = w; s.x = x; s.y = y; s.z = z; s.fx = r[1]; s.fz = r[3];
        s.where = w.getName() + " " + x + " " + y + " " + z;
        for (int h = 8; h >= -1; h--) for (int u = -4; u <= 4; u++) for (int v = -4; v <= 4; v++) {
            if (h == -1 && (Math.abs(u) > 3 || Math.abs(v) > 3)) continue;
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (!Helper.natural(b.getType()) && h <= 0) { s.had.add(b.getX() + " " + b.getY() + " " + b.getZ()); continue; }      // a foundation at your feet or lower: it stays, she builds on it
            if (!Helper.natural(b.getType())) return "I cannot build there: there is " + Helper.word(b.getType()) + " at " + b.getX() + " " + b.getY() + " " + b.getZ() + ", above where you stand. I build on untouched ground or on a foundation at your feet or lower. Pick another spot.";
            if (!b.getType().isAir()) s.clear.add(b);
        }
        int legs = 0;
        List<Object[]> under = new ArrayList<>();
        for (int u = -3; u <= 3; u++) for (int v = -3; v <= 3; v++) if (Math.abs(u) == 3 || Math.abs(v) == 3) for (int h = -2; h >= -5; h--) {   // legs down to the ground
            Block b = w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v);
            if (b.getType().isOccluding() || !Helper.natural(b.getType())) break;
            under.add(new Object[] {b, "cobblestone"});
            legs++;
        }
        s.place.addAll(under);
        List<String> keys = new ArrayList<>(plan.keySet());
        keys.sort(java.util.Comparator.comparingInt(k -> Integer.parseInt(k.split(" ")[1])));
        int nPlanks = 0, nLogs = 0, nStairs = 0, nSlab = 0, nCobble = legs, nGlass = 0;
        for (String k : keys) {
            String[] a = k.split(" ");
            int u = Integer.parseInt(a[0]), h = Integer.parseInt(a[1]), v = Integer.parseInt(a[2]);
            String what = plan.get(k);
            s.place.add(new Object[] {w.getBlockAt(x + r[0] * u + r[1] * v, y + h, z + r[2] * u + r[3] * v), what});
            if (what.equals("PLANKS")) nPlanks++; else if (what.equals("LOG")) nLogs++; else if (what.startsWith("STAIRS")) nStairs++;
            else if (what.equals("SLAB")) nSlab++; else if (what.equals("cobblestone")) nCobble++; else if (what.equals("glass")) nGlass++;
        }
        int planks = nPlanks + (nStairs + 3) / 4 * 6 + (nSlab + 5) / 6 * 3 + 6 + 8 + 4 + 3 + 2;      // door, chest, table, bed, sticks
        int logsAll = nLogs + 3 + 2 + (planks + 3) / 4;                                              // campfire, charcoal
        // what there is: the bag, the chests by the plot and at home, and the trees and the sand within 40 blocks
        Map<String, List<Block>> trees = new HashMap<>();
        List<Block> sands = new ArrayList<>();
        survey(w, x, y, z, trees, sands);
        if (farSand.containsKey(id)) { Set<Block> known = new HashSet<>(sands); for (Block b : farSand.get(id)) if (known.add(b)) sands.add(b); }
        if (far != null) for (Map.Entry<String, List<Block>> e : far.entrySet()) {       // the trees found further away, after the near ones
            List<Block> logs = trees.computeIfAbsent(e.getKey(), k -> new ArrayList<>());
            Set<Block> known = new HashSet<>(logs);
            for (Block b : e.getValue()) if (known.add(b)) logs.add(b);
        }
        Location hm = core.homeIn(id, w);
        List<Inventory> homeChests = hm == null ? new ArrayList<>() : Helper.stores(hm, Helper.HOME);
        List<Inventory> all = new ArrayList<>(stock(s));
        all.addAll(homeChests);
        String best = null;
        int bestLogs = -1;
        for (String wd : WOODS) {
            Material lg = Helper.mat(wd + "_LOG"), pl = Helper.mat(wd + "_PLANKS");
            if (lg == null || pl == null || Helper.mat(wd + "_STAIRS") == null) continue;
            int have = Helper.count(all, k -> k == lg) + Helper.count(all, k -> k == pl) / 4 + trees.getOrDefault(wd, List.of()).size();
            if (have > bestLogs) { bestLogs = have; best = wd; }
        }
        int sandHave = sands.size() + Helper.count(all, k -> k == Material.GLASS) + Helper.count(all, k -> k == Material.SAND);
        boolean lackWood = best == null || bestLogs < logsAll, lackSand = sandHave < nGlass;
        if ((lackWood || lackSand) && far == null && w.getEnvironment() == World.Environment.NORMAL) {
            seekFar(id, w, x, y, z, lackWood ? logsAll : 0, lackSand ? nGlass - sandHave + sands.size() : 0, (fw, fs) -> {
                farSand.put(id, fs);
                try { return house(id, w, x, y, z, turns, fw); } finally { farSand.remove(id); }
            });
            return "A house takes about " + logsAll + " logs of one kind of wood and sand for " + nGlass + " windows, and I find too little " + (lackWood ? lackSand ? "wood and sand" : "wood" : "sand")
                    + " around here. I go looking for it, however far that is, and come back to build.";
        }
        if (best == null || bestLogs < logsAll) return "A house takes about " + logsAll + " logs of one kind of wood, and I find only " + Math.max(0, bestLogs) + (best == null ? "" : " of " + best.toLowerCase(Locale.ROOT).replace('_', ' '))
                + (far == null ? " (trees within 40 blocks, my bag and the chests at home)" : ", even in the nearest forest I could find") + ". Pick a spot nearer a forest, or put logs in a chest at home.";
        s.wood = best;
        Material lg = Helper.mat(best + "_LOG"), pl = Helper.mat(best + "_PLANKS");
        // from the chests at home into the bag: what the house needs
        Object[][] bring = {{lg, logsAll}, {pl, planks}, {Material.COBBLESTONE, nCobble + 8}, {Material.GLASS, nGlass}, {Material.SAND, nGlass}, {Material.LANTERN, 1}, {Material.IRON_INGOT, 1},
                {Material.COAL, 2}, {Material.CHARCOAL, 2}};
        for (Object[] b : bring) {
            Material k = (Material) b[0];
            int want = (Integer) b[1] - Helper.count(stock(s), q -> q == k), there = Math.min(want, Helper.count(homeChests, q -> q == k));
            if (there > 0) { Helper.take(homeChests, q -> q == k, there); stow(s, new ItemStack(k, there)); }
        }
        for (Inventory inv : homeChests) for (ItemStack it : inv.getContents()) if (!Helper.empty(it) && Tag.WOOL.isTagged(it.getType()) && it.getAmount() >= 3 && Helper.count(stock(s), q -> Tag.WOOL.isTagged(q)) < 3) {
            it.setAmount(it.getAmount() - 3);
            stow(s, new ItemStack(it.getType(), 3));
        }
        s.needLogs = logsAll;
        s.needCobble = nCobble + 8;
        s.needSand = nGlass;
        s.trees.addAll(trees.getOrDefault(best, List.of()));
        Location centre = new Location(w, x, y, z);
        sands.sort(java.util.Comparator.comparingDouble(b -> b.getLocation().distanceSquared(centre)));
        s.sand.addAll(sands.subList(0, Math.min(sands.size(), nGlass + 4)));
        holdFar(s);
        for (int dy = 4; dy <= 22 && s.stone.size() < nCobble + 12; dy++) for (int u = -2; u <= 2; u++) for (int v = -2; v <= 2; v++) {      // a little quarry right under the house
            Block b = w.getBlockAt(x + u, y - dy, z + v);
            if (b.getType() == Material.STONE || b.getType() == Material.COBBLESTONE) s.stone.add(b);
        }
        startSite(s);
        core.awayLog(id, "starts to build a house at " + s.where + " (" + s.clear.size() + " blocks to clear, " + s.place.size() + " to set, wood: " + best.toLowerCase(Locale.ROOT) + ")");
        int mins = Math.max(3, (s.clear.size() + logsAll + nCobble + s.place.size()) * 8 / 20 / 60 + 2);
        return "I will build a little house of " + best.toLowerCase(Locale.ROOT).replace('_', ' ') + " at " + x + " " + y + " " + z + ", the door to the " + front + ". I clear the plot, fetch what I need and build it by hand, so give me about "
                + mins + " minutes. Say stop building if you change your mind.";
    }

    void startSite(Site s) {
        for (int cx = (s.x - 44) >> 4; cx <= (s.x + 44) >> 4; cx++) for (int cz = (s.z - 44) >> 4; cz <= (s.z + 44) >> 4; cz++) { s.w.addPluginChunkTicket(cx, cz, core.plugin); s.held.add(new long[] {cx, cz}); }
        Helper.H h = core.helpers.get(s.id);
        if (h != null) { s.stance = h.stance; s.anchor = h.anchor; }
        sites.put(s.id, s);
        s.task = Bukkit.getScheduler().runTaskTimer(core.plugin, () -> { try { step(s); } catch (RuntimeException e) { core.plugin.getLogger().warning("Building stopped: " + e); endSite(s); } }, 8L, 8L);
    }

    void endSite(Site s) {
        if (s.task != null) s.task.cancel();
        if (sites.get(s.id) == s) sites.remove(s.id);
        for (long[] c : s.held) s.w.removePluginChunkTicket((int) c[0], (int) c[1], core.plugin);
        Helper.H h = core.helpers.get(s.id);
        if (h != null && s.stance != null) { h.stance = s.stance; h.anchor = s.anchor; }
        if (h != null && h.watching && Bukkit.getPlayer(s.id) == null) {   // built while you were away: back to its post at home, where the away job takes over
            Location post = core.home(s.id) != null ? core.home(s.id) : h.w.getLocation();
            h.stance = Helper.Stance.GUARD;
            h.anchor = post;
            if (h.w.getWorld() != post.getWorld() || h.w.getLocation().distanceSquared(post) > 32 * 32) h.w.teleport(post);
        }
        for (ItemStack it : s.pile.getContents()) {                         // what is left over: into the bag, the chests at home, or on the ground by the plot
            if (Helper.empty(it)) continue;
            Map<Integer, ItemStack> rest = core.group(s.w).equals(core.grp(s.id)) ? core.bag(s.id).addItem(it) : new HashMap<>(Map.of(0, it));      // never into the bag of another world
            core.dirty.add(s.id);
            Location hm = core.homeIn(s.id, s.w);
            if (!rest.isEmpty() && hm != null) for (Inventory inv : Helper.stores(hm, Helper.HOME)) { if (rest.isEmpty()) break; rest = inv.addItem(rest.values().iterator().next()); }
            for (ItemStack r : rest.values()) s.w.dropItemNaturally(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out), r);
        }
        s.pile.clear();
        if (!s.down && !s.placed.isEmpty()) { core.conf.set(core.path(s.id, "built.world"), s.w.getName()); core.conf.set(core.path(s.id, "built.blocks"), s.placed); core.save(); }
        core.flushBags();
    }

    /** The figure goes to where the work is. False while it is still on its way (it is fetched after five seconds). */
    boolean reach(Site s, Block b) {
        Helper.H h = core.helpers.get(s.id);
        if (h == null || h.merged || h.w == null || h.w.isDead()) return true;
        Location l = b.getLocation().add(0.5, 0, 0.5);
        h.stance = Helper.Stance.GUARD;
        h.anchor = l;
        Location at = h.w.getLocation();
        boolean near = at.getWorld() == s.w && Math.abs(at.getX() - l.getX()) <= 9 && Math.abs(at.getZ() - l.getZ()) <= 9;
        if (!near) {
            if (++s.far > 12) { h.w.teleport(new Location(s.w, s.x + 0.5 + s.fx * s.out, s.y, s.z + 0.5 + s.fz * s.out)); s.far = 0; if (Math.abs(b.getX() - s.x) > 12 || Math.abs(b.getZ() - s.z) > 12) h.w.teleport(l.clone().add(1, 1, 0)); }
            return false;
        }
        s.far = 0;
        Material held = Helper.toolFor(b.getType());
        if (held != null) core.wield(h, held);
        try { h.m.swingMainHand(); } catch (RuntimeException ignored) { }
        return true;
    }

    void take1(Site s, Block b, boolean keepAll) {
        if (b.getState() instanceof org.bukkit.inventory.InventoryHolder holder) {
            for (ItemStack it : holder.getInventory().getContents()) if (!Helper.empty(it)) stow(s, it);
            holder.getInventory().clear();
        }
        Material m = b.getType();
        for (ItemStack it : Helper.drops(b)) {
            Material k = it.getType();
            if (keepAll || s.keep.contains(k) || Tag.LOGS.isTagged(k) || Tag.PLANKS.isTagged(k) || k == Material.COBBLESTONE || k == Material.SAND || Tag.WOOL.isTagged(k)) stow(s, it);
            else b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.5, 0.5), it);
        }
        b.getWorld().playSound(b.getLocation(), m.createBlockData().getSoundGroup().getBreakSound(), 0.8f, 1f);
        b.setType(Material.AIR, s.down);
        s.broke++;
        s.from = null;
    }

    /** One thing every 0.4 seconds: a block broken, a block set, or a wait for the furnace. */
    void step(Site s) {
        if (core.paused() || s.paused) return;
        if (s.wait > 0) { s.wait--; return; }
        UUID id = s.id;
        if (s.down) {
            Block b = s.clear.peek();
            while (b != null && b.getType().isAir()) { s.clear.poll(); b = s.clear.peek(); }
            if (b == null) {
                endSite(s);
                core.conf.set(core.path(id, "built"), null);
                core.save();
                note(s, "It is down: " + s.broke + " blocks taken apart at " + s.where + ". The materials are in my bag and the chests at home; what did not fit lies on the ground there.");
                return;
            }
            if (!reach(s, b)) return;
            s.clear.poll();
            take1(s, b, true);
            return;
        }
        if (s.phase == 0) {                                                // the plot
            Block b = s.clear.peek();
            while (b != null && b.getType().isAir()) { s.clear.poll(); b = s.clear.peek(); }
            if (b == null) { s.phase = 1; note(s, "The plot is clear. Now I fetch what I need: wood, stone and sand."); return; }
            if (!reach(s, b)) return;
            s.clear.poll();
            take1(s, b, false);
            return;
        }
        Material lg = Helper.mat(s.wood + "_LOG"), pl = Helper.mat(s.wood + "_PLANKS");
        if (s.phase == 1) {
            List<Inventory> from = stock(s);
            int logs = Helper.count(from, k -> k == lg) + Helper.count(from, k -> k == pl) / 4;
            Block b = null;
            if (logs < s.needLogs) { while ((b = s.trees.peek()) != null && b.getType() != lg) s.trees.poll(); if (b != null) s.trees.poll(); }
            if (b == null && Helper.count(from, k -> k == Material.COBBLESTONE) < s.needCobble) { while ((b = s.stone.peek()) != null && b.getType() != Material.STONE && b.getType() != Material.COBBLESTONE) s.stone.poll(); if (b != null) s.stone.poll(); }
            if (b == null && Helper.count(from, k -> k == Material.GLASS) + Helper.count(from, k -> k == Material.SAND) < s.needSand) { while ((b = s.sand.peek()) != null && b.getType() != Material.SAND) s.sand.poll(); if (b != null) s.sand.poll(); }
            if (b != null) {
                if (!reach(s, b)) { if (lg == b.getType()) ((ArrayDeque<Block>) s.trees).addFirst(b); else if (b.getType() == Material.SAND) ((ArrayDeque<Block>) s.sand).addFirst(b); else ((ArrayDeque<Block>) s.stone).addFirst(b); return; }
                take1(s, b, false);
                return;
            }
            if (Helper.count(from, k -> Tag.WOOL.isTagged(k)) < 3 && s.sheep < 4) {                  // wool for the bed
                s.sheep++;
                for (Entity e : s.w.getNearbyEntities(new Location(s.w, s.x, s.y, s.z), 40, 16, 40)) if (e instanceof Sheep sh && sh.isAdult() && !sh.isSheared()) {
                    sh.setSheared(true);
                    org.bukkit.DyeColor col = sh.getColor();
                    Material wool = Helper.mat((col == null ? "WHITE" : col.name()) + "_WOOL");
                    stow(s, new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
                    s.w.playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
                    s.from = null;
                    return;
                }
            }
            s.phase = 2;
            note(s, "I have what I could find. Now I build.");
            return;
        }
        if (s.at >= s.place.size()) {
            if (s.plan) for (Object[] pb : s.place) connect((Block) pb[0]);
            endSite(s);
            String lack = s.lacked.isEmpty() ? "" : " I had to leave out: " + String.join(", ", s.lacked) + ".";
            core.awayLog(id, "the " + s.name + " at " + s.where + " is finished (" + s.put + " blocks set, " + s.broke + " broken)" + lack);
            Player owner = Bukkit.getPlayer(id);
            if (owner != null) core.say(owner, "Your " + s.name + " at " + s.x + " " + s.y + " " + s.z + " is ready! I built it by hand." + lack + " Say undo build and I take it down again.");
            else {
                List<String> finds = core.conf.getStringList(core.path(id, "away.finds"));
                if (finds.size() < 30) { finds.add("a " + s.name + " I built for you at " + s.x + " " + s.y + " " + s.z); core.conf.set(core.path(id, "away.finds"), finds); }
            }
            return;
        }
        Block b = (Block) s.place.get(s.at)[0];
        String what = (String) s.place.get(s.at)[1], base = what.contains("[") ? what.substring(0, what.indexOf('[')) : what, props = what.contains("[") ? what.substring(what.indexOf('[')) : "";
        if (!b.getType().isAir() && s.had.contains(b.getX() + " " + b.getY() + " " + b.getZ())) { s.at++; return; }      // it stands already
        if (!reach(s, b)) return;
        s.at++;
        Material item;
        boolean free = false;
        switch (base) {
            case "LOG" -> item = lg;
            case "PLANKS" -> item = pl;
            case "stone", "stone_bricks", "cobblestone" -> { item = Helper.mat(base); if (s.plan && item != null && !ensure(s, item, 1, 0) && item != Material.COBBLESTONE && ensure(s, Material.COBBLESTONE, 1, 0)) item = Material.COBBLESTONE; }
            case "STAIRS" -> item = Helper.mat(s.wood + "_STAIRS");
            case "SLAB" -> item = Helper.mat(s.wood + "_SLAB");
            case "DOOR" -> { item = Helper.mat(s.wood + "_DOOR"); if (props.contains("upper")) { if (!s.door) return; free = true; } }
            case "BED" -> {
                if (props.contains("foot")) { if (s.bed == null) return; item = Helper.mat(s.bed + "_BED"); free = true; }
                else {
                    item = null;
                    List<Inventory> from = stock(s);
                    for (org.bukkit.DyeColor c : org.bukkit.DyeColor.values()) {
                        Material wool = Helper.mat(c.name() + "_WOOL");
                        if (wool == null || Helper.count(from, k -> k == wool) < 3 || !ensure(s, pl, 3, 0)) continue;
                        Helper.take(stock(s), k -> k == wool, 3);
                        Helper.take(stock(s), k -> k == pl, 3);
                        s.bed = c.name();
                        item = Helper.mat(s.bed + "_BED");
                        free = true;
                        break;
                    }
                    if (item == null) { s.lacked.add("the bed (no three pieces of wool of one colour)"); return; }
                }
            }
            default -> item = base.startsWith("WOOD:") ? Helper.mat(s.wood + "_" + base.substring(5)) : Helper.mat(base);
        }
        if (item == null) { if (s.plan) s.lacked.add(base.toLowerCase(Locale.ROOT).replace("wood:", "").replace('_', ' ')); return; }
        Material block = item;                                             // a wall torch is set, a torch is what it takes
        item = asItem(item);
        if (!free && !ensure(s, item, 1, 0)) {
            block = item;
            if (item == Material.COBBLESTONE && ensure(s, pl, 1, 0)) { item = pl; props = ""; }                    // no stone: wood will do
            else if (item == Material.LANTERN && ensure(s, Material.TORCH, 1, 0)) { item = Material.TORCH; props = ""; }
            else { s.lacked.add(item == Material.GLASS ? "the window glass (no sand to be found)" : Helper.word(item)); return; }
        }
        if (!free) { Material k = item; Helper.take(stock(s), q -> q == k, 1); core.dirty.add(id); }
        if (base.equals("DOOR")) s.door = true;
        BlockData data;
        if (block != item && (!block.isBlock() || !props.isEmpty() && item != asItem(block))) block = item;      // swapped for something else on the way
        if (!block.isBlock()) { s.lacked.add(Helper.word(block)); return; }
        try { data = Bukkit.createBlockData(block.getKey() + props); } catch (IllegalArgumentException e) { data = block.createBlockData(); }
        b.setBlockData(data, false);
        b.getWorld().playSound(b.getLocation(), data.getSoundGroup().getPlaceSound(), 0.8f, 1f);
        s.placed.add(b.getX() + " " + b.getY() + " " + b.getZ());
        s.put++;
        s.from = null;
    }

    /** Takes its own last house down again, block by block; the materials go into the bag. */
    String unbuild(UUID id) {
        Site running = sites.get(id);
        if (running != null) { if (running.down) return "I am taking it down already."; endSite(running); }
        List<String> was = core.conf.getStringList(core.path(id, "built.blocks"));
        World w = Bukkit.getWorld(core.conf.getString(core.path(id, "built.world"), ""));
        if (was.isEmpty() || w == null) return "I have no house of mine to take down.";
        List<Block> blocks = new ArrayList<>();
        for (String k : was) {
            String[] a = k.split(" ");
            try { blocks.add(w.getBlockAt(Integer.parseInt(a[0]), Integer.parseInt(a[1]), Integer.parseInt(a[2]))); } catch (RuntimeException ignored) { }
        }
        return raze(id, blocks, true);
    }

    String raze(UUID id, List<Block> blocks, boolean own) {
        if (sites.containsKey(id)) return "I am already at work on a building. Say stop building first.";
        if (blocks.isEmpty()) return "There is nothing to take down.";
        Site s = new Site();
        s.id = id; s.down = true; s.w = blocks.get(0).getWorld();
        blocks.sort(java.util.Comparator.comparingInt((Block b) -> -b.getY()));
        s.x = blocks.get(blocks.size() - 1).getX(); s.y = blocks.get(blocks.size() - 1).getY() + 1; s.z = blocks.get(blocks.size() - 1).getZ();
        s.where = s.w.getName() + " " + s.x + " " + s.y + " " + s.z;
        s.clear.addAll(blocks);
        startSite(s);
        core.awayLog(id, "starts to take down " + blocks.size() + " blocks at " + s.where);
        return "I take it down block by block, from the top: " + blocks.size() + " blocks, about " + Math.max(1, blocks.size() * 8 / 20 / 60 + 1) + " minutes. The materials go into my bag. Say stop building to stop me.";
    }
}
