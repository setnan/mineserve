package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.ChunkSnapshot;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.structure.Structure;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.CompassMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.StructureSearchResult;

/**
 * Metal Detector: a craftable compass (diamond, obsidian, 4 iron, redstone). Hold it and /detector <thing>:
 * blocks within 48 blocks (ores, spawners, chests …), structures (stronghold, village …) or biomes. Guides with an
 * arrow and distance on the action bar, beeps faster when closer, particles near the target, and the needle points
 * at it (lodestone target). Right-click searches again, sneak + right-click stops.
 * Looks: custom_model_data "metal_detector" on the vanilla compass (detector_pack.py).
 */
final class Detector implements Listener {
    private static final int RANGE = 48;
    private static final String MODEL = "metal_detector";
    private final JavaPlugin plugin;
    private final NamespacedKey tag, recipeKey;
    /** Keys used before the plugin was renamed from Skaper; items carrying them are converted on join. */
    private static final NamespacedKey OLD_TAG = new NamespacedKey("skaper", "detektor");
    private static final NamespacedKey OLD_TAG2 = new NamespacedKey("skaper", "metalldetektor");

    private static final class Target {
        String query, label;
        Set<Material> blocks;        // null for structures and biomes
        Location loc;
        int beep;
        boolean refining;           // stronghold: switched to scanning for the portal frame
    }

    private final Map<UUID, Target> targets = new HashMap<>();
    private final Map<UUID, String> last = new HashMap<>();

    // friendly names -> blocks
    private static final Map<String, List<Material>> BLOCKS = new LinkedHashMap<>();
    // -> structures (minecraft:<key>)
    private static final Map<String, List<String>> STRUCTS = new LinkedHashMap<>();
    // -> biomes
    private static final Map<String, List<String>> BIOMES = new LinkedHashMap<>();

    private static final Map<String, String> STRUCT_OF = new HashMap<>();

    static {
        b("diamond", Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        b("iron", Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        b("gold", Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
        b("coal", Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        b("copper", Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        b("redstone", Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        b("lapis", Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        b("emerald", Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        b("netherite", Material.ANCIENT_DEBRIS);
        b("quartz", Material.NETHER_QUARTZ_ORE);
        b("spawner", Material.SPAWNER, Material.TRIAL_SPAWNER);
        b("chest", Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);
        b("amethyst", Material.BUDDING_AMETHYST, Material.AMETHYST_CLUSTER);
        b("obsidian", Material.OBSIDIAN, Material.CRYING_OBSIDIAN);
        b("lava", Material.LAVA);
        s("stronghold", "stronghold"); s("endportal", "stronghold"); s("end_portal", "stronghold"); s("end_portal_frame", "stronghold");
        s("village", "village_plains", "village_desert", "village_savanna", "village_snowy", "village_taiga");
        s("desert_temple", "desert_pyramid"); s("jungle_temple", "jungle_pyramid"); s("witch_hut", "swamp_hut");
        s("mansion", "mansion"); s("monument", "monument"); s("shipwreck", "shipwreck", "shipwreck_beached");
        s("mineshaft", "mineshaft", "mineshaft_mesa"); s("fortress", "fortress"); s("bastion", "bastion_remnant");
        s("end_city", "end_city"); s("outpost", "pillager_outpost"); s("igloo", "igloo"); s("ancient_city", "ancient_city");
        s("trial_chambers", "trial_chambers"); s("treasure", "buried_treasure"); s("ocean_ruin", "ocean_ruin_cold", "ocean_ruin_warm");
        s("ruined_portal", "ruined_portal", "ruined_portal_desert", "ruined_portal_jungle", "ruined_portal_swamp",
                "ruined_portal_mountain", "ruined_portal_ocean", "ruined_portal_nether");
        s("trail_ruins", "trail_ruins");
        bi("mushroom", "mushroom_fields"); bi("jungle", "jungle", "bamboo_jungle", "sparse_jungle"); bi("cherry", "cherry_grove");
        bi("desert", "desert"); bi("snow", "snowy_plains", "snowy_taiga"); bi("ice_spikes", "ice_spikes"); bi("savanna", "savanna");
        bi("swamp", "swamp", "mangrove_swamp"); bi("badlands", "badlands", "eroded_badlands", "wooded_badlands");
        bi("forest", "forest"); bi("birch_forest", "birch_forest"); bi("dark_forest", "dark_forest"); bi("pale_garden", "pale_garden");
        bi("ocean", "ocean", "deep_ocean"); bi("coral_reef", "warm_ocean"); bi("mountains", "jagged_peaks", "frozen_peaks", "stony_peaks");
        bi("lush_caves", "lush_caves"); bi("deep_dark", "deep_dark"); bi("dripstone", "dripstone_caves");
        bi("beach", "beach"); bi("plains", "plains", "sunflower_plains"); bi("flowers", "flower_forest", "meadow");
    }

    private static void b(String k, Material... m) { BLOCKS.put(k, List.of(m)); }
    private static void s(String k, String... v) { STRUCTS.put(k, List.of(v)); STRUCT_OF.put(k, v[0]); }
    private static void bi(String k, String... v) { BIOMES.put(k, List.of(v)); }

    Detector(JavaPlugin plugin) {
        this.plugin = plugin;
        this.tag = new NamespacedKey(plugin, "detector");
        this.recipeKey = new NamespacedKey(plugin, "metal_detector");
        ShapedRecipe r = new ShapedRecipe(recipeKey, item());
        r.shape("DIO", "IRI", " I ");
        r.setIngredient('D', Material.DIAMOND);
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('O', Material.OBSIDIAN);
        r.setIngredient('R', Material.REDSTONE);
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
        Bukkit.getScheduler().runTaskTimer(plugin, this::guide, 20L, 5L);
    }

    ItemStack item() {
        ItemStack it = new ItemStack(Material.COMPASS);
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        style(it);
        return it;
    }

    /** Name, lore, glint and model of the detector (also used to update old items). */
    private void style(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Metal Detector", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("/detector diamond, stronghold, village, jungle …", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: search again · Sneak + right-click: stop", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.setItemModel(null);
        var cmd = m.getCustomModelDataComponent();
        cmd.setStrings(List.of(MODEL));
        m.setCustomModelDataComponent(cmd);
        it.setItemMeta(m);
    }

    private boolean isDetector(ItemStack it) {
        if (it == null || it.getType() != Material.COMPASS || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(tag, PersistentDataType.BYTE) || pdc.has(OLD_TAG, PersistentDataType.BYTE) || pdc.has(OLD_TAG2, PersistentDataType.BYTE);
    }

    private ItemStack held(Player p) {
        if (isDetector(p.getInventory().getItemInMainHand())) return p.getInventory().getItemInMainHand();
        if (isDetector(p.getInventory().getItemInOffHand())) return p.getInventory().getItemInOffHand();
        return null;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(recipeKey);
        for (ItemStack it : e.getPlayer().getInventory().getContents()) migrate(it);
        for (ItemStack it : e.getPlayer().getEnderChest().getContents()) migrate(it);
    }

    /** Detectors from the old Skaper plugin (Norwegian name, key skaper:detektor, item_model) become new ones. */
    private void migrate(ItemStack it) {
        if (!isDetector(it)) return;
        ItemMeta m = it.getItemMeta();
        var pdc = m.getPersistentDataContainer();
        if (pdc.has(tag, PersistentDataType.BYTE) && !m.hasItemModel()) return;
        pdc.remove(OLD_TAG);
        pdc.remove(OLD_TAG2);
        pdc.set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        style(it);
    }

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (!isDetector(e.getItem())) return;
        e.setCancelled(true);                      // no lodestone binding with the detector
        if (e.getHand() != org.bukkit.inventory.EquipmentSlot.HAND) return;
        Player p = e.getPlayer();
        if (p.isSneaking()) { stop(p, "The detector is off."); return; }
        String q = last.get(p.getUniqueId());
        if (q == null) { p.sendMessage("Type /detector <thing>, e.g. /detector diamond, /detector stronghold or /detector jungle."); return; }
        search(p, q);
    }

    // --- Commands -----------------------------------------------------------------------------------

    void register(Commands r) {
        r.register("detector", "Metal detector: /detector <thing> (block, structure or biome)", List.of("d"), new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (args.length >= 1 && args[0].equalsIgnoreCase("give")) { give(src, args.length > 1 ? args[1] : null); return; }
                if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
                if (args.length == 0) {
                    p.sendMessage("Usage: /detector <thing>. Blocks: " + String.join(", ", BLOCKS.keySet())
                            + ". Structures: " + String.join(", ", STRUCTS.keySet()) + ". Biomes: " + String.join(", ", BIOMES.keySet())
                            + ". Minecraft ids work too.");
                    return;
                }
                search(p, String.join("_", args).toLowerCase(Locale.ROOT));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                String q = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>();
                for (Map<String, ?> m : List.of(BLOCKS, STRUCTS, BIOMES))
                    for (String k : m.keySet()) if (k.startsWith(q)) out.add(k);
                return out;
            }
        });
        r.register("givedetector", "Gives you (or a player) a Metal Detector (op only)", (BasicCommand) (src, args) -> give(src, args.length > 0 ? args[0] : null));
        r.register("detectoroff", "Turns the metal detector off", (BasicCommand) (src, args) -> {
            if (src.getExecutor() instanceof Player p) stop(p, "The detector is off.");
        });
    }

    /** /givedetector [player] or /d give [player]: op or console only. Without a name: yourself. */
    private void give(CommandSourceStack src, String name) {
        if (src.getSender() instanceof Player sp && !sp.isOp()) {
            sp.sendMessage("Only ops can give detectors. Craft one: diamond, iron, obsidian / iron, redstone, iron / _, iron, _.");
            return;
        }
        Player t = name != null ? Bukkit.getPlayerExact(name) : src.getExecutor() instanceof Player p ? p : null;
        if (t == null) { src.getSender().sendMessage(name == null ? "Usage: givedetector <player>" : "Could not find " + name); return; }
        t.getInventory().addItem(item());
        t.sendMessage("You got a Metal Detector. Hold it and type /d diamond (or stronghold, village, jungle …).");
        if (!t.equals(src.getSender())) src.getSender().sendMessage("Gave " + t.getName() + " a Metal Detector.");
    }

    // --- Search -------------------------------------------------------------------------------------

    private static String norm(String q) {
        return q.replace("minecraft:", "").replace(" ", "_");
    }

    private void search(Player p, String query) {
        if (held(p) == null) {
            p.sendMessage("You must hold a Metal Detector. Recipe: diamond, iron, obsidian / iron, redstone, iron / _, iron, _.");
            return;
        }
        String q = norm(query);
        last.put(p.getUniqueId(), q);
        World w = p.getWorld();
        Location from = p.getLocation();
        // 1) blocks
        Set<Material> mats = EnumSet.noneOf(Material.class);
        if (BLOCKS.containsKey(q)) mats.addAll(BLOCKS.get(q));
        else {
            Material m = Material.matchMaterial(q);
            if (m != null && m.isBlock()) mats.add(m);
            for (String v : new String[]{q + "_ore", "deepslate_" + q + "_ore", "deepslate_" + q}) {
                Material o = Material.matchMaterial(v);
                if (o != null && o.isBlock() && (m == null || !m.isBlock() || v.startsWith("deepslate_" + q))) mats.add(o);
            }
        }
        if (!mats.isEmpty()) { scan(p, q, mats); return; }
        // 2) structures
        var structReg = RegistryAccess.registryAccess().getRegistry(RegistryKey.STRUCTURE);
        List<String> sk = STRUCTS.getOrDefault(q, structReg.get(NamespacedKey.minecraft(q)) != null ? List.of(q) : List.of());
        if (!sk.isEmpty()) {
            Location best = null;
            for (String k : sk) {
                Structure st = structReg.get(NamespacedKey.minecraft(k));
                if (st == null) continue;
                StructureSearchResult res = w.locateNearestStructure(from, st, 100, false);
                if (res != null && (best == null || res.getLocation().distanceSquared(from) < best.distanceSquared(from))) best = res.getLocation();
            }
            if (best == null) { p.sendMessage("No " + q + " nearby (in this world)."); return; }
            best.setY(from.getY());
            point(p, q, best);
            return;
        }
        // 3) biomes
        var biomeReg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME);
        List<String> bk = BIOMES.getOrDefault(q, biomeReg.get(NamespacedKey.minecraft(q)) != null ? List.of(q) : List.of());
        if (!bk.isEmpty()) {
            List<Biome> biomes = new ArrayList<>();
            for (String k : bk) { Biome b = biomeReg.get(NamespacedKey.minecraft(k)); if (b != null) biomes.add(b); }
            var res = biomes.isEmpty() ? null : w.locateNearestBiome(from, 6400, biomes.toArray(new Biome[0]));
            if (res == null) { p.sendMessage("No " + q + " biome within 6400 blocks."); return; }
            Location l = res.getLocation();
            l.setY(from.getY());
            point(p, q, l);
            return;
        }
        p.sendMessage("Unknown target \"" + query + "\". Press Tab after /detector for suggestions.");
    }

    private void point(Player p, String label, Location l) {
        Target t = new Target();
        t.query = label; t.label = label; t.loc = l;
        targets.put(p.getUniqueId(), t);
        needle(p, l);
        p.sendMessage("The detector points to " + label + ", " + (int) Math.sqrt(horiz(p.getLocation(), l)) + " blocks away.");
    }

    /** Scans chunk snapshots within RANGE asynchronously and guides to the nearest block. */
    private void scan(Player p, String label, Set<Material> mats) { scan(p, label, mats, false); }

    /** keep = true: stay on the current target if nothing is found (used when refining a stronghold to its portal). */
    private void scan(Player p, String label, Set<Material> mats, boolean keep) {
        World w = p.getWorld();
        Location from = p.getLocation();
        int cx = from.getBlockX() >> 4, cz = from.getBlockZ() >> 4, cr = (RANGE >> 4) + 1;
        List<ChunkSnapshot> snaps = new ArrayList<>();
        for (int x = cx - cr; x <= cx + cr; x++)
            for (int z = cz - cr; z <= cz + cr; z++)
                if (w.isChunkLoaded(x, z)) snaps.add(w.getChunkAt(x, z).getChunkSnapshot(false, false, false));
        int minY = w.getMinHeight(), maxY = w.getMaxHeight();
        double fx = from.getX(), fy = from.getY(), fz = from.getZ();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            double best = Double.MAX_VALUE;
            int bx = 0, by = 0, bz = 0;
            for (ChunkSnapshot s : snaps)
                for (int x = 0; x < 16; x++)
                    for (int z = 0; z < 16; z++) {
                        int ax = (s.getX() << 4) + x, az = (s.getZ() << 4) + z;
                        double dx = ax + 0.5 - fx, dz = az + 0.5 - fz;
                        if (dx * dx + dz * dz > RANGE * RANGE) continue;
                        for (int y = minY; y < maxY; y++) {
                            if (!mats.contains(s.getBlockType(x, y, z))) continue;
                            double dy = y + 0.5 - fy, d = dx * dx + dy * dy + dz * dz;
                            if (d < best) { best = d; bx = ax; by = y; bz = az; }
                        }
                    }
            final boolean found = best < Double.MAX_VALUE;
            final Location hit = new Location(w, bx + 0.5, by + 0.5, bz + 0.5);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (!p.isOnline()) return;
                if (!found && keep) return;
                if (!found) { stop(p, "No " + label + " within " + RANGE + " blocks. Move and right-click with the detector to search again."); return; }
                Target t = new Target();
                t.query = label; t.label = label; t.blocks = mats; t.loc = hit;
                targets.put(p.getUniqueId(), t);
                needle(p, hit);
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
            });
        });
    }

    // --- Guiding ------------------------------------------------------------------------------------

    private static double horiz(Location a, Location b) {
        double dx = b.getX() - a.getX(), dz = b.getZ() - a.getZ();
        return dx * dx + dz * dz;
    }

    private void guide() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Target t = targets.get(p.getUniqueId());
            if (t == null || held(p) == null) continue;
            if (!t.loc.getWorld().equals(p.getWorld())) { stop(p, "The detector turned off because you changed worlds."); continue; }
            if (t.blocks != null && !t.blocks.contains(t.loc.getBlock().getType())) {   // mined: find the next one
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.8f);
                p.sendActionBar(Component.text("Found! Looking for the next " + t.label + " …", NamedTextColor.GREEN));
                targets.remove(p.getUniqueId());
                scan(p, t.label, t.blocks);
                continue;
            }
            if (t.blocks == null && !t.refining && "stronghold".equals(STRUCT_OF.get(t.query)) && horiz(p.getLocation(), t.loc) < 40 * 40) {
                t.refining = true;   // close to the stronghold: look for the end portal frame itself
                scan(p, "endportal", EnumSet.of(Material.END_PORTAL_FRAME), true);
            }
            Location eye = p.getEyeLocation();
            double dx = t.loc.getX() - eye.getX(), dy = t.loc.getY() - eye.getY(), dz = t.loc.getZ() - eye.getZ();
            double hd = Math.sqrt(dx * dx + dz * dz), dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
            double ang = Math.toDegrees(Math.atan2(-dx, dz)) - p.getLocation().getYaw();
            ang = ((ang % 360) + 540) % 360 - 180;    // -180..180, 0 = straight ahead
            String[] arrows = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};
            String arrow = arrows[(int) Math.round(((ang + 360) % 360) / 45.0) % 8];
            String vert = t.blocks == null ? "" : Math.abs(dy) < 1.5 ? " · at your level" : " · " + (int) Math.abs(dy) + (dy < 0 ? " below you" : " above you");
            p.sendActionBar(Component.text(arrow + " " + t.label + " · " + (int) (t.blocks == null ? hd : dist) + " blocks" + vert, NamedTextColor.AQUA));
            int every = (int) Math.max(1, Math.min(8, (t.blocks == null ? hd / 64 : dist / 6)));   // in 5-tick steps
            if (++t.beep >= every) {
                t.beep = 0;
                float pitch = (float) Math.max(0.6, Math.min(2.0, 2.0 - (t.blocks == null ? hd / 400 : dist / 40)));
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, pitch);
            }
            if (t.blocks != null && dist < 12) {
                for (int i = 1; i <= 3; i++) {
                    double k = i / 4.0;
                    p.spawnParticle(Particle.END_ROD, eye.getX() + dx * k, eye.getY() + dy * k, eye.getZ() + dz * k, 1, 0, 0, 0, 0);
                }
                p.spawnParticle(Particle.GLOW, t.loc, 4, 0.3, 0.3, 0.3, 0);
            }
        }
    }

    private void needle(Player p, Location l) {
        ItemStack it = held(p);
        if (it == null) return;
        CompassMeta m = (CompassMeta) it.getItemMeta();
        m.setLodestone(l);
        m.setLodestoneTracked(false);
        it.setItemMeta(m);
    }

    private void stop(Player p, String msg) {
        targets.remove(p.getUniqueId());
        ItemStack it = held(p);
        if (it != null) {
            CompassMeta m = (CompassMeta) it.getItemMeta();
            m.clearLodestone();
            it.setItemMeta(m);
        }
        p.sendMessage(msg);
    }
}
