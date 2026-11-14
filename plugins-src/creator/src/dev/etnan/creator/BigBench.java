package dev.etnan.creator;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.resource.ResourcePackInfo;
import net.kyori.adventure.resource.ResourcePackRequest;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Egg;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Explosive;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.SmallFireball;
import org.bukkit.entity.Snowball;
import org.bukkit.entity.SpectralArrow;
import org.bukkit.entity.WindCharge;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.EquipmentSlotGroup;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.packs.ResourcePack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * BigBench: a 5x5 crafting bench (/bigbench, /bb, needs creator power) for super tools that can only be made there.
 * The tools are defined in plugins/Creator/supers/*.json (written by hand or by chatbro "claude make supertool …")
 * and are built from a fixed set of safe abilities (ABILITIES below), so a definition can never run code.
 * The five builtin tools ship inside the jar (supers/) and are copied out when the folder has no definitions.
 * /superreload reloads the files and sends the rebuilt resource pack to everyone online, no restart needed.
 * The items are netherite tools with custom_model_data ["super_<id>"]; super_pack.py draws them.
 */
final class BigBench implements Listener {
    private static final int[] GRID = new int[25];
    private static final int RESULT = 25;
    private static final int[] ICONS = {0, 9, 18, 27, 36, 8, 17, 26, 35, 44};
    private static final int PREV = 45, BOOK = 49, NEXT = 53;
    static {
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) GRID[r * 5 + c] = r * 9 + c + 1;
    }

    static final Map<String, String> TOOLS = Map.of("sword", "SWORD", "axe", "AXE", "pickaxe", "PICKAXE", "shovel", "SHOVEL", "hoe", "HOE");
    /** Materials a tool can come in: prefix of the vanilla tool and the material itself (for "@material"). */
    static final Map<String, String[]> TIERS = new LinkedHashMap<>();
    static {
        TIERS.put("wood", new String[]{"WOODEN", "OAK_PLANKS"});
        TIERS.put("stone", new String[]{"STONE", "COBBLESTONE"});
        TIERS.put("copper", new String[]{"COPPER", "COPPER_INGOT"});
        TIERS.put("iron", new String[]{"IRON", "IRON_INGOT"});
        TIERS.put("gold", new String[]{"GOLDEN", "GOLD_INGOT"});
        TIERS.put("diamond", new String[]{"DIAMOND", "DIAMOND"});
        TIERS.put("netherite", new String[]{"NETHERITE", "NETHERITE_INGOT"});
    }
    /** Ability types and which triggers they make sense for; documented for Codex in chatbro.py. */
    static final Set<String> ABILITIES = Set.of("area", "tree", "vein", "smelt", "harvest", "till", "damage", "sweep", "effect",
            "self_effect", "burn", "freeze", "lightning", "explosion", "push", "pull", "blink", "jump", "projectile", "heal",
            "freeze_water", "particles", "sound");
    static final Set<String> TRIGGERS = Set.of("mine", "hit", "use", "hold", "impact");

    record Ability(String trigger, String type, double strength, double seconds, String name, double cooldown) {}

    /** One craftable tool. A definition with "materials" becomes one Super per material (id <family>_<material>). */
    record Super(String id, String family, String tier, String name, TextColor color, Material base, String[] shape,
                 Map<Character, Material> keys, List<String> lore, double damage, double speed, double reach,
                 double blockReach, List<Ability> abilities, boolean approved, String madeBy) {
        boolean has(String type) {
            for (Ability e : abilities) if (e.type().equals(type)) return true;
            return false;
        }
    }

    /** Shipped in the jar under supers/; listed first in the bench. */
    private static final List<String> BUILTIN = List.of("longsword", "bigpickaxe", "bigshovel", "giantaxe", "greatscythe");

    private final Map<String, Super> supers = new LinkedHashMap<>();
    private static final Map<Material, Material> SEED = Map.of(Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART);

    private final JavaPlugin plugin;
    private final NamespacedKey tag, filler;
    private final File dir;
    private final Map<UUID, BlockFace> lastFace = new HashMap<>();
    private final Map<String, Long> cooldown = new HashMap<>();
    private final Map<UUID, Long> noFall = new HashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private final Map<Material, ItemStack> smeltCache = new HashMap<>();

    BigBench(JavaPlugin plugin) {
        this.plugin = plugin;
        tag = new NamespacedKey(plugin, "super");
        filler = new NamespacedKey(plugin, "bigbench_filler");
        dir = new File(plugin.getDataFolder(), "supers");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::holdTick, 40L, 10L);
    }

    // --- Definitions --------------------------------------------------------------------------------

    /** Reads supers/*.json. A broken file is skipped with a warning; the others still load. */
    String load() {
        supers.clear();
        dir.mkdirs();
        File[] have = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (have == null || have.length == 0) for (String id : BUILTIN) {
            try (InputStream in = plugin.getResource("supers/" + id + ".json")) {
                if (in != null) Files.copy(in, new File(dir, id + ".json").toPath());
            } catch (IOException ex) {
                plugin.getLogger().warning("Could not copy builtin super tool " + id + ": " + ex.getMessage());
            }
        }
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        List<String> bad = new ArrayList<>();
        if (files != null) {
            java.util.Arrays.sort(files);
            for (File f : files) {
                try {
                    for (Super s : parse(JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject()))
                        supers.put(s.id(), s);
                } catch (Exception ex) {
                    bad.add(f.getName());
                    plugin.getLogger().warning("Super tool " + f.getName() + " could not be read: " + ex.getMessage());
                }
            }
        }
        // the builtin five first, then the rest in file order
        Map<String, Super> sorted = new LinkedHashMap<>();
        for (String id : BUILTIN) for (Super s : supers.values()) if (s.family().equals(id)) sorted.put(s.id(), s);
        sorted.putAll(supers);
        supers.clear();
        supers.putAll(sorted);
        return families().size() + " super tools (" + supers.size() + " with materials) loaded" + (bad.isEmpty() ? "" : ", errors in " + String.join(", ", bad));
    }

    private static double num(JsonObject o, String k, double def) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsDouble() : def;
    }

    private static String str(JsonObject o, String k, String def) {
        return o != null && o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : def;
    }

    /** Same-kind materials count as one in recipes: any planks, any stone that makes stone tools. */
    private static Material norm(Material m) {
        if (m == null) return null;
        if (Tag.PLANKS.isTagged(m)) return Material.OAK_PLANKS;
        if (m == Material.BLACKSTONE || m == Material.COBBLED_DEEPSLATE) return Material.COBBLESTONE;
        return m;
    }

    private static List<Super> parse(JsonObject o) {
        String id = str(o, "id", "").toLowerCase(Locale.ROOT);
        if (!id.matches("[a-z0-9_]{2,32}")) throw new IllegalArgumentException("invalid id \"" + id + "\"");
        String kind = TOOLS.get(str(o, "tool", ""));
        if (kind == null) throw new IllegalArgumentException("tool must be one of " + TOOLS.keySet());
        TextColor color = TextColor.fromHexString(str(o, "color", "#FFFFFF"));
        if (color == null) color = NamedTextColor.WHITE;
        JsonArray rows = o.getAsJsonArray("recipe");
        if (rows == null || rows.size() != 5) throw new IllegalArgumentException("recipe must have 5 rows");
        String[] shape = new String[5];
        for (int i = 0; i < 5; i++) {
            String r = rows.get(i).getAsString();
            if (r.length() != 5) throw new IllegalArgumentException("row " + (i + 1) + " of the recipe must have 5 characters");
            shape[i] = r;
        }
        List<String> tiers = new ArrayList<>();
        if (o.has("materials")) for (JsonElement e : o.getAsJsonArray("materials")) {
            String t = Wand.ascii(e.getAsString().toLowerCase(Locale.ROOT));
            if (!TIERS.containsKey(t)) throw new IllegalArgumentException("unknown material \"" + t + "\", use " + TIERS.keySet());
            if (!tiers.contains(t)) tiers.add(t);
        }
        boolean tiered = !tiers.isEmpty();
        if (!tiered) tiers.add("netherite");
        List<String> lore = new ArrayList<>();
        if (o.has("description")) for (JsonElement e : o.getAsJsonArray("description")) lore.add(e.getAsString());
        JsonObject st = o.has("stats") ? o.getAsJsonObject("stats") : null;
        List<Ability> abilities = new ArrayList<>();
        if (o.has("abilities")) for (JsonElement e : o.getAsJsonArray("abilities")) {
            JsonObject a = e.getAsJsonObject();
            Ability ab = new Ability(str(a, "trigger", ""), str(a, "type", ""), num(a, "strength", 1), num(a, "seconds", 0),
                    str(a, "name", ""), num(a, "cooldown", 0));
            if (!TRIGGERS.contains(ab.trigger()) || !ABILITIES.contains(ab.type()))
                throw new IllegalArgumentException("unknown ability " + ab.trigger() + "/" + ab.type());
            abilities.add(ab);
        }
        String name = str(o, "name", id);
        boolean ok = o.has("approved") && o.get("approved").getAsBoolean();
        String by = str(o, "made_by", "?");
        List<Super> out = new ArrayList<>();
        for (String t : tiers) {
            Material base = Material.matchMaterial(TIERS.get(t)[0] + "_" + kind);
            if (base == null) continue;
            Map<Character, Material> keys = new LinkedHashMap<>();
            for (JsonElement e : o.getAsJsonArray("ingredients")) {
                JsonObject k = e.getAsJsonObject();
                String ch = str(k, "char", "");
                String mat = str(k, "material", "");
                Material m = mat.equalsIgnoreCase("@tool") ? base
                        : mat.equalsIgnoreCase("@material") ? Material.matchMaterial(TIERS.get(t)[1]) : Material.matchMaterial(mat);
                if (ch.length() != 1 || ch.equals(" ") || m == null || !m.isItem() || m.isAir())
                    throw new IllegalArgumentException("invalid ingredient " + k);
                keys.put(ch.charAt(0), norm(m));
            }
            int filled = 0;
            for (String r : shape) for (char ch : r.toCharArray()) {
                if (ch == ' ') continue;
                if (!keys.containsKey(ch)) throw new IllegalArgumentException("character \"" + ch + "\" in the recipe has no ingredient");
                filled++;
            }
            if (filled < 2) throw new IllegalArgumentException("the recipe needs at least 2 ingredients");
            // Tiered tools: damage/speed are added to what the vanilla tool of that material has.
            double damage = tiered ? clamp(num(st, "bonus_damage", 0), -5, 10) : clamp(num(st, "damage", 7), 1, 20);
            double speed = tiered ? clamp(num(st, "bonus_speed", 0), -2, 2) : clamp(num(st, "speed", 1.2), 0.5, 4);
            out.add(new Super(tiered ? id + "_" + t : id, id, tiered ? t : null, tiered ? name + " (" + t + ")" : name, color, base,
                    shape, keys, lore, damage, speed, clamp(num(st, "reach", 0), 0, 4), clamp(num(st, "block_reach", 0), 0, 3),
                    abilities, ok, by));
        }
        return out;
    }

    /** One Super per definition file (the strongest material), in order. */
    private List<Super> families() {
        Map<String, Super> f = new LinkedHashMap<>();
        for (Super s : supers.values()) f.put(s.family(), s);
        return new ArrayList<>(f.values());
    }

    private static double clamp(double v, double lo, double hi) {
        return Double.isNaN(v) ? lo : Math.max(lo, Math.min(hi, v));
    }

    private List<Super> approved() {
        return supers.values().stream().filter(Super::approved).toList();
    }

    private List<Super> approvedFamilies() {
        return families().stream().filter(Super::approved).toList();
    }

    // --- Items --------------------------------------------------------------------------------------

    ItemStack item(Super s) {
        ItemStack it = new ItemStack(s.base());
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(s.name(), s.color()).decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        for (String l : s.lore()) lore.add(Component.text(l, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        lore.add(Component.text(s.tier() == null ? "Super tool from the Big Bench · never breaks"
                : "Super tool from the Big Bench · twice as durable", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false));
        m.lore(lore);
        if (s.tier() == null) m.setUnbreakable(true);
        else if (m instanceof org.bukkit.inventory.meta.Damageable d) d.setMaxDamage(s.base().getMaxDurability() * 2);
        m.getPersistentDataContainer().set(tag, PersistentDataType.STRING, s.id());
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("super_" + s.id()));
        m.setCustomModelDataComponent(c);
        // Setting any attribute replaces the vanilla ones, so damage and speed are always given.
        if (s.tier() == null) {
            stat(m, Attribute.ATTACK_DAMAGE, s.damage() - 1, "base_attack_damage");
            stat(m, Attribute.ATTACK_SPEED, s.speed() - 4, "base_attack_speed");
        } else {
            double dmg = 0, spd = 0;
            for (var e : s.base().getDefaultAttributeModifiers(EquipmentSlot.HAND).entries()) {
                if (e.getKey() == Attribute.ATTACK_DAMAGE) dmg += e.getValue().getAmount();
                if (e.getKey() == Attribute.ATTACK_SPEED) spd += e.getValue().getAmount();
            }
            stat(m, Attribute.ATTACK_DAMAGE, Math.max(0, dmg + s.damage()), "base_attack_damage");
            stat(m, Attribute.ATTACK_SPEED, Math.max(-3.5, spd + s.speed()), "base_attack_speed");
        }
        if (s.reach() > 0) stat(m, Attribute.ENTITY_INTERACTION_RANGE, s.reach(), null);
        if (s.blockReach() > 0) stat(m, Attribute.BLOCK_INTERACTION_RANGE, s.blockReach(), null);
        it.setItemMeta(m);
        return it;
    }

    private void stat(ItemMeta m, Attribute a, double v, String vanillaKey) {
        NamespacedKey key = vanillaKey != null ? NamespacedKey.minecraft(vanillaKey) : new NamespacedKey(plugin, "super_" + a.getKey().getKey());
        m.addAttributeModifier(a, new AttributeModifier(key, v, AttributeModifier.Operation.ADD_NUMBER, EquipmentSlotGroup.MAINHAND));
    }

    private String superOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
    }

    private Super held(Player p) {
        String id = superOf(p.getInventory().getItemInMainHand());
        return id == null ? null : supers.get(id);
    }

    /** Brings tools in the inventory up to date with their definition (name, lore, stats), keeping enchantments. */
    private void refreshItems(Player p) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++) {
            String id = superOf(inv[i]);
            Super s = id == null ? null : supers.get(id);
            if (s == null) continue;
            ItemStack fresh = item(s);
            ItemMeta m = fresh.getItemMeta();
            for (var e : inv[i].getEnchantments().entrySet()) m.addEnchant(e.getKey(), e.getValue(), true);
            fresh.setItemMeta(m);
            if (!fresh.isSimilar(inv[i])) p.getInventory().setItem(i, fresh);
        }
    }

    // --- Resource pack without restart --------------------------------------------------------------

    private static String sha1(File f) {
        try (InputStream in = new FileInputStream(f)) {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            byte[] buf = new byte[65536];
            for (int n; (n = in.read(buf)) > 0; ) md.update(buf, 0, n);
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    /** Sends the pack on disk (same id and url as server.properties) when it differs from what the server started with. */
    private void pushPack(Collection<? extends Player> to, boolean always) {
        ResourcePack sp = Bukkit.getServerResourcePack();
        if (sp == null || to.isEmpty()) return;
        String hash = sha1(new File("resourcepack", "supermann-heads.zip"));
        if (hash == null || (!always && hash.equalsIgnoreCase(sp.getHash()))) return;
        ResourcePackRequest req = ResourcePackRequest.resourcePackRequest()
                .packs(ResourcePackInfo.resourcePackInfo(sp.getId(), URI.create(sp.getUrl()), hash))
                .replace(true).required(sp.isRequired()).build();
        for (Player p : to) p.sendResourcePacks(req);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        refreshItems(p);
        Bukkit.getScheduler().runTaskLater(plugin, () -> { if (p.isOnline()) pushPack(List.of(p), false); }, 60L);
    }

    // --- The bench ----------------------------------------------------------------------------------

    private static final class Holder implements InventoryHolder {
        Inventory inv;
        int page;

        @Override
        public Inventory getInventory() { return inv; }
    }

    void open(Player p) {
        Holder h = new Holder();
        h.inv = Bukkit.createInventory(h, 54, Component.text("Big Bench (5x5)"));
        draw(h);
        p.openInventory(h.inv);
    }

    private void draw(Holder h) {
        ItemStack pane = pane(Material.GRAY_STAINED_GLASS_PANE, " ", null, "");
        for (int i = 0; i < 54; i++) if (!isGrid(i) && i != RESULT) h.inv.setItem(i, pane);
        h.inv.setItem(24, pane(Material.LIME_STAINED_GLASS_PANE, "→", "What you can make shows up on the right", ""));
        List<Super> list = approvedFamilies();
        int pages = Math.max(1, (list.size() + ICONS.length - 1) / ICONS.length);
        h.page = Math.floorMod(h.page, pages);
        for (int i = 0; i < ICONS.length; i++) {
            int n = h.page * ICONS.length + i;
            if (n >= list.size()) break;
            Super s = list.get(n);
            ItemStack icon = item(s);
            ItemMeta m = icon.getItemMeta();
            List<Component> lore = new ArrayList<>(m.lore());
            lore.add(Component.text("Click: puts the recipe in the grid", NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            m.lore(lore);
            m.getPersistentDataContainer().remove(tag);
            m.getPersistentDataContainer().set(filler, PersistentDataType.STRING, s.id());
            if (s.tier() != null) m.displayName(Component.text(s.name().replaceAll(" \\(.*\\)$", ""), s.color())
                    .decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
            icon.setItemMeta(m);
            h.inv.setItem(ICONS[i], icon);
        }
        h.inv.setItem(BOOK, pane(Material.BOOK, "Recipes", "Click to see them in chat", ""));
        if (pages > 1) {
            h.inv.setItem(PREV, pane(Material.ARROW, "← Previous page", "Page " + (h.page + 1) + " of " + pages, ""));
            h.inv.setItem(NEXT, pane(Material.ARROW, "Next page →", "Page " + (h.page + 1) + " of " + pages, ""));
        }
    }

    private ItemStack pane(Material mat, String name, String lore, String id) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, NamedTextColor.WHITE).decoration(TextDecoration.ITALIC, false));
        if (lore != null) m.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(filler, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    private static boolean isGrid(int raw) {
        for (int s : GRID) if (s == raw) return true;
        return false;
    }

    /** The recipe the grid matches (anywhere in the 5x5, also mirrored), or null. */
    private Super match(Inventory inv) {
        Material[][] g = new Material[5][5];
        for (int i = 0; i < 25; i++) {
            ItemStack it = inv.getItem(GRID[i]);
            g[i / 5][i % 5] = it == null || it.getType().isAir() || superOf(it) != null ? null : norm(it.getType());
        }
        Material[][] have = trim(g);
        for (Super s : approved()) {
            Material[][] want = new Material[5][5];
            for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) want[r][c] = s.keys().get(s.shape()[r].charAt(c));
            Material[][] t = trim(want);
            if (same(have, t) || same(have, mirror(t))) return s;
        }
        return null;
    }

    private static Material[][] trim(Material[][] g) {
        int r0 = 5, r1 = -1, c0 = 5, c1 = -1;
        for (int r = 0; r < g.length; r++) for (int c = 0; c < g[r].length; c++)
            if (g[r][c] != null) { r0 = Math.min(r0, r); r1 = Math.max(r1, r); c0 = Math.min(c0, c); c1 = Math.max(c1, c); }
        if (r1 < 0) return new Material[0][0];
        Material[][] out = new Material[r1 - r0 + 1][c1 - c0 + 1];
        for (int r = r0; r <= r1; r++) for (int c = c0; c <= c1; c++) out[r - r0][c - c0] = g[r][c];
        return out;
    }

    private static Material[][] mirror(Material[][] g) {
        Material[][] out = new Material[g.length][];
        for (int r = 0; r < g.length; r++) {
            out[r] = new Material[g[r].length];
            for (int c = 0; c < g[r].length; c++) out[r][c] = g[r][g[r].length - 1 - c];
        }
        return out;
    }

    private static boolean same(Material[][] a, Material[][] b) {
        if (a.length == 0 || a.length != b.length || a[0].length != b[0].length) return false;
        for (int r = 0; r < a.length; r++) for (int c = 0; c < a[r].length; c++) if (a[r][c] != b[r][c]) return false;
        return true;
    }

    /** The finished tool, with the enchantments of any tool that went into it. */
    private ItemStack result(Inventory inv, Super s) {
        ItemStack out = item(s);
        ItemMeta m = out.getItemMeta();
        int left = 0, tools = 0;
        for (int slot : GRID) {
            ItemStack it = inv.getItem(slot);
            if (it == null) continue;
            for (Map.Entry<Enchantment, Integer> e : it.getEnchantments().entrySet())
                if (m.getEnchantLevel(e.getKey()) < e.getValue()) m.addEnchant(e.getKey(), e.getValue(), true);
            if (it.getType() == s.base() && it.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                left += it.getType().getMaxDurability() - d.getDamage();
                tools++;
            }
        }
        // Two worn tools give a worn long tool: durability is what was left in the tools that went in.
        if (s.tier() != null && tools > 0 && m instanceof org.bukkit.inventory.meta.Damageable d)
            d.setDamage(Math.max(0, d.getMaxDamage() - left * 2 / tools));
        out.setItemMeta(m);
        return out;
    }

    private void refresh(Inventory inv) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            Super s = match(inv);
            inv.setItem(RESULT, s == null ? null : result(inv, s));
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h) || !(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = h.inv;
        int raw = e.getRawSlot();
        if (e.getAction() == InventoryAction.COLLECT_TO_CURSOR || e.getClick() == ClickType.DOUBLE_CLICK) { e.setCancelled(true); return; }
        if (raw >= 54) {                                            // the player's own inventory
            if (e.isShiftClick()) {
                e.setCancelled(true);
                ItemStack it = e.getCurrentItem();
                if (it == null || it.getType().isAir()) return;
                for (int s : GRID) {
                    ItemStack g = top.getItem(s);
                    if (g == null || g.getType().isAir()) { top.setItem(s, it.clone()); e.setCurrentItem(null); break; }
                }
                refresh(top);
            }
            return;
        }
        if (isGrid(raw)) { refresh(top); return; }
        e.setCancelled(true);
        if (raw == RESULT) { take(p, top, e.isShiftClick()); return; }
        if (raw == BOOK) { for (Super s : approvedFamilies()) recipe(p, s); return; }
        if (raw == PREV || raw == NEXT) { h.page += raw == NEXT ? 1 : -1; draw(h); return; }
        ItemStack it = e.getCurrentItem();
        String id = it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(filler, PersistentDataType.STRING);
        if (id != null && supers.containsKey(id)) fill(p, top, best(p, supers.get(id).family()));
    }

    private void take(Player p, Inventory top, boolean shift) {
        Super s = match(top);
        if (s == null) return;
        ItemStack out = result(top, s);
        if (shift) {
            for (ItemStack left : p.getInventory().addItem(out).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        } else {
            ItemStack cur = p.getItemOnCursor();
            if (cur != null && !cur.getType().isAir()) return;
            p.setItemOnCursor(out);
        }
        for (int slot : GRID) {
            ItemStack g = top.getItem(slot);
            if (g == null || g.getType().isAir()) continue;
            g.setAmount(g.getAmount() - 1);
            top.setItem(slot, g.getAmount() > 0 ? g : null);
        }
        p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 1f, 1f);
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.6f, 1.4f);
        p.sendMessage(Component.text("You made " + s.name() + "!", s.color()));
        refresh(top);
    }

    private static String pretty(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    /** The strongest material of a family the player has everything for, otherwise the weakest (to show the recipe). */
    private Super best(Player p, String family) {
        Super fallback = null, found = null;
        for (Super s : supers.values()) {
            if (!s.family().equals(family) || !s.approved()) continue;
            if (fallback == null) fallback = s;
            if (missing(p, s).isEmpty()) found = s;
        }
        return found != null ? found : fallback;
    }

    private List<String> missing(Player p, Super s) {
        Map<Material, Integer> need = new LinkedHashMap<>();
        for (String row : s.shape()) for (char ch : row.toCharArray()) if (ch != ' ') need.merge(s.keys().get(ch), 1, Integer::sum);
        List<String> missing = new ArrayList<>();
        for (var n : need.entrySet()) {
            int have = 0;
            for (ItemStack it : p.getInventory().getStorageContents())
                if (it != null && norm(it.getType()) == n.getKey() && superOf(it) == null) have += it.getAmount();
            if (have < n.getValue()) missing.add((n.getValue() - have) + " " + pretty(n.getKey()));
        }
        return missing;
    }

    /** Recipe click: moves what is in the grid back, then takes the ingredients from the inventory. */
    private void fill(Player p, Inventory top, Super s) {
        giveBack(p, top);
        List<String> missing = missing(p, s);
        if (!missing.isEmpty()) {
            recipe(p, s);
            p.sendMessage(Component.text("You are missing: " + String.join(", ", missing), NamedTextColor.RED));
            refresh(top);
            return;
        }
        for (int r = 0; r < 5; r++) for (int c = 0; c < 5; c++) {
            char ch = s.shape()[r].charAt(c);
            if (ch == ' ') continue;
            Material mat = s.keys().get(ch);
            ItemStack[] inv = p.getInventory().getStorageContents();
            for (int i = 0; i < inv.length; i++) {
                ItemStack it = inv[i];
                if (it == null || norm(it.getType()) != mat || superOf(it) != null) continue;
                ItemStack one = it.clone();
                one.setAmount(1);
                top.setItem(GRID[r * 5 + c], one);
                it.setAmount(it.getAmount() - 1);
                p.getInventory().setItem(i, it.getAmount() > 0 ? it : null);
                break;
            }
        }
        refresh(top);
    }

    private void recipe(Player p, Super s) {
        StringBuilder sb = new StringBuilder();
        for (String row : s.shape()) {
            sb.append("\n   ");
            for (char ch : row.toCharArray()) sb.append(ch == ' ' ? "·" : String.valueOf(ch)).append(' ');
        }
        StringBuilder legend = new StringBuilder("\n   ");
        for (var k : s.keys().entrySet()) {
            String what = pretty(k.getValue());
            if (s.tier() != null && k.getValue() == s.base()) what = pretty(s.base()).replaceFirst("^\\w+ ", "") + " (same material)";
            else if (s.tier() != null && k.getValue() == norm(Material.matchMaterial(TIERS.get(s.tier())[1]))) what = "the material (planks, stone, ingots, diamond …)";
            legend.append(k.getKey()).append(" = ").append(what).append("  ");
        }
        if (s.tier() != null) legend.append("\n   Comes in ").append(String.join(", ", supers.values().stream()
                .filter(x -> x.family().equals(s.family())).map(Super::tier).toList()));
        p.sendMessage(Component.text(s.name().replaceAll(" \\(.*\\)$", "") + ":", s.color()).append(Component.text(sb.toString() + legend, NamedTextColor.GRAY)));
    }

    private void giveBack(Player p, Inventory top) {
        for (int slot : GRID) {
            ItemStack it = top.getItem(slot);
            if (it == null || it.getType().isAir()) continue;
            top.setItem(slot, null);
            for (ItemStack left : p.getInventory().addItem(it).values()) p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
        top.setItem(RESULT, null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        for (int raw : e.getRawSlots()) if (raw < 54 && !isGrid(raw)) { e.setCancelled(true); return; }
        refresh(h.inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Holder h && e.getPlayer() instanceof Player p) giveBack(p, h.inv);
    }

    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder) p.closeInventory();
    }

    // --- Abilities ----------------------------------------------------------------------------------

    /** What an ability works on: the player, an entity that was hit or looked at, and a place. */
    private record Ctx(Player p, Entity target, Location at, Block block) {}

    private boolean ready(Player p, Super s, int i, double secs) {
        String k = p.getUniqueId() + ":" + s.id() + ":" + i;
        long now = System.currentTimeMillis();
        if (cooldown.getOrDefault(k, 0L) > now) return false;
        if (secs > 0) cooldown.put(k, now + (long) (secs * 1000));
        return true;
    }

    /** Runs the abilities of a tool for one trigger. Returns true if any of them ran. */
    private boolean fire(Super s, String trigger, Ctx c) {
        boolean any = false;
        for (int i = 0; i < s.abilities().size(); i++) {
            Ability e = s.abilities().get(i);
            if (!e.trigger().equals(trigger)) continue;
            double min = trigger.equals("use") ? 0.4 : trigger.equals("hold") && e.type().equals("heal") ? 5 : 0;
            if (!ready(c.p(), s, i, Math.max(min, clamp(e.cooldown(), 0, 600)))) continue;
            try {
                if (apply(s, e, c)) any = true;
            } catch (RuntimeException ex) {
                plugin.getLogger().warning("Super tool " + s.id() + " ability " + e.type() + ": " + ex);
            }
        }
        return any;
    }

    private boolean apply(Super s, Ability e, Ctx c) {
        Player p = c.p();
        World w = p.getWorld();
        Location at = c.at() != null ? c.at() : p.getEyeLocation().add(p.getLocation().getDirection().multiply(2));
        LivingEntity t = c.target() instanceof LivingEntity le && le != p ? le : null;
        int secs = (int) clamp(e.seconds() <= 0 ? 5 : e.seconds(), 1, 60);
        switch (e.type()) {
            case "harvest" -> { return c.block() != null && SEED.containsKey(c.block().getType()) && harvest(p, c.block(), (int) clamp(e.strength(), 1, 4)); }
            case "till" -> { return c.block() != null && tillable(c.block()) && till(c.block(), (int) clamp(e.strength(), 1, 3)); }
            case "damage" -> { if (t == null) return false; hurt(p, t, clamp(e.strength(), 0, 20)); }
            case "sweep" -> {
                double r = clamp(e.strength(), 1, 5);
                Location mid = t != null ? t.getLocation() : at;
                for (Entity n : w.getNearbyEntities(mid, r, 2, r))
                    if (n instanceof Enemy && n instanceof LivingEntity le && n != t) hurt(p, le, 4);
                w.spawnParticle(Particle.SWEEP_ATTACK, mid.clone().add(0, 1, 0), 3, r / 2, 0.3, r / 2);
            }
            case "effect" -> {
                PotionEffectType pt = potion(e.name());
                if (pt == null) return false;
                List<LivingEntity> ts = t != null ? List.of(t) : enemies(at, 4, p);
                if (ts.isEmpty()) return false;
                for (LivingEntity le : ts) le.addPotionEffect(new PotionEffect(pt, secs * 20, (int) clamp(e.strength(), 1, 5) - 1));
            }
            case "self_effect" -> {
                PotionEffectType pt = potion(e.name());
                if (pt == null) return false;
                int ticks = e.trigger().equals("hold") ? (pt == PotionEffectType.NIGHT_VISION ? 300 : 40) : secs * 20;
                p.addPotionEffect(new PotionEffect(pt, ticks, (int) clamp(e.strength(), 1, 5) - 1, true, false, true));
            }
            case "burn" -> { if (t == null) return false; t.setFireTicks(secs * 20); }
            case "freeze" -> {
                List<LivingEntity> ts = t != null ? List.of(t) : enemies(at, 3, p);
                for (LivingEntity le : ts) {
                    le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, 3));
                    le.setFreezeTicks(Math.max(le.getMaxFreezeTicks(), secs * 20));
                }
                w.spawnParticle(Particle.SNOWFLAKE, at, 40, 1, 1, 1, 0.02);
            }
            case "lightning" -> {
                Location l = t != null ? t.getLocation() : at;
                w.strikeLightningEffect(l);
                for (LivingEntity le : enemies(l, 2.5, p)) hurt(p, le, clamp(e.strength() <= 1 ? 6 : e.strength(), 0, 20));
            }
            case "explosion" -> {
                Location l = t != null ? t.getLocation() : at;
                double r = clamp(e.strength(), 1, 4);
                w.spawnParticle(r > 2 ? Particle.EXPLOSION_EMITTER : Particle.EXPLOSION, l, 1);
                w.playSound(l, Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                for (Entity n : w.getNearbyEntities(l, r * 1.5, r * 1.5, r * 1.5)) {
                    if (n == p || !(n instanceof LivingEntity le) || n instanceof Player) continue;
                    hurt(p, le, r * 3);
                    le.setVelocity(le.getLocation().toVector().subtract(l.toVector()).normalize().multiply(0.6 + r * 0.2).setY(0.5));
                }
            }
            case "push" -> {
                double f = clamp(e.strength(), 0.5, 4);
                List<LivingEntity> ts = t != null ? List.of(t) : enemies(p.getLocation(), 5, p);
                for (LivingEntity le : ts)
                    le.setVelocity(le.getLocation().toVector().subtract(p.getLocation().toVector()).normalize().multiply(f).setY(0.3 + f * 0.1));
                if (ts.isEmpty()) return false;
            }
            case "pull" -> {
                double r = clamp(e.strength(), 2, 16);
                Location to = p.getLocation();
                for (Entity n : w.getNearbyEntities(to, r, r, r)) {
                    boolean want = e.trigger().equals("hold") ? n instanceof Item : (n instanceof Enemy || n instanceof Item);
                    if (!want || n == p) continue;
                    Vector v = to.toVector().subtract(n.getLocation().toVector());
                    if (v.lengthSquared() < 2) continue;
                    n.setVelocity(v.normalize().multiply(n instanceof Item ? 0.5 : 1.0).setY(0.2));
                }
            }
            case "blink" -> {
                RayTraceResult r = w.rayTraceBlocks(p.getEyeLocation(), p.getLocation().getDirection(), clamp(e.strength() <= 1 ? 24 : e.strength(), 4, 48));
                Location to = r != null && r.getHitBlock() != null
                        ? r.getHitBlock().getRelative(r.getHitBlockFace() == null ? BlockFace.UP : r.getHitBlockFace()).getLocation().add(0.5, 0, 0.5)
                        : p.getEyeLocation().add(p.getLocation().getDirection().multiply(clamp(e.strength(), 4, 48)));
                if (!to.getBlock().isPassable() || !to.clone().add(0, 1, 0).getBlock().isPassable()) to.add(0, 1, 0);
                if (!to.getBlock().isPassable()) return false;
                to.setYaw(p.getLocation().getYaw());
                to.setPitch(p.getLocation().getPitch());
                w.spawnParticle(Particle.PORTAL, p.getLocation(), 40, 0.4, 1, 0.4);
                p.teleport(to);
                p.setFallDistance(0);
                w.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
            }
            case "jump" -> {
                p.setVelocity(p.getVelocity().add(p.getLocation().getDirection().multiply(0.4)).setY(clamp(e.strength(), 0.5, 3)));
                noFall.put(p.getUniqueId(), System.currentTimeMillis() + 8000);
                w.spawnParticle(Particle.CLOUD, p.getLocation(), 15, 0.3, 0.1, 0.3, 0.05);
            }
            case "projectile" -> {
                Class<? extends Projectile> type = switch (e.name().toUpperCase(Locale.ROOT)) {
                    case "ARROW" -> Arrow.class;
                    case "SPECTRAL_ARROW" -> SpectralArrow.class;
                    case "WIND_CHARGE" -> WindCharge.class;
                    case "SMALL_FIREBALL", "FIREBALL" -> SmallFireball.class;
                    case "EGG" -> Egg.class;
                    default -> Snowball.class;
                };
                Projectile pr = p.launchProjectile(type, p.getLocation().getDirection().multiply(clamp(e.strength(), 0.5, 4)));
                if (pr instanceof AbstractArrow a) a.setPickupStatus(AbstractArrow.PickupStatus.DISALLOWED);
                if (pr instanceof Explosive x) { x.setIsIncendiary(false); x.setYield(0); }
                pr.getPersistentDataContainer().set(tag, PersistentDataType.STRING, s.id());
            }
            case "heal" -> p.heal(clamp(e.strength(), 0.5, 10) * 2);
            case "freeze_water" -> {
                int r = (int) clamp(e.strength(), 1, 4);
                Block feet = p.getLocation().getBlock().getRelative(BlockFace.DOWN);
                for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
                    if (dx * dx + dz * dz > r * r) continue;
                    Block b = feet.getRelative(dx, 0, dz);
                    if (b.getType() == Material.WATER && b.getBlockData() instanceof org.bukkit.block.data.Levelled lv && lv.getLevel() == 0
                            && b.getRelative(BlockFace.UP).getType().isAir()) b.setType(Material.FROSTED_ICE);
                }
            }
            case "particles" -> {
                Particle pt = particle(e.name());
                if (pt == null) return false;
                Location l = t != null ? t.getLocation().add(0, 1, 0) : e.trigger().equals("hold") ? p.getLocation().add(0, 1, 0) : at;
                w.spawnParticle(pt, l, (int) clamp(e.strength(), 1, 100), 0.5, 0.5, 0.5, 0.02);
            }
            case "sound" -> {
                Sound snd = Registry.SOUNDS.get(NamespacedKey.minecraft(e.name().toLowerCase(Locale.ROOT).replace("minecraft:", "")));
                if (snd == null) return false;
                w.playSound(t != null ? t.getLocation() : at, snd, 1f, (float) clamp(e.strength(), 0.5, 2));
            }
            default -> { return false; }            // area, tree, vein, smelt are handled where blocks break
        }
        return true;
    }

    private static PotionEffectType potion(String n) {
        return n == null || n.isBlank() ? null : Registry.MOB_EFFECT.get(NamespacedKey.minecraft(n.toLowerCase(Locale.ROOT).replace("minecraft:", "")));
    }

    /** Particles that need extra data (dust, block, item) are not allowed. */
    private static Particle particle(String n) {
        try {
            Particle p = Particle.valueOf(n.toUpperCase(Locale.ROOT).replace("MINECRAFT:", ""));
            return p.getDataType() == Void.class ? p : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static List<LivingEntity> enemies(Location l, double r, Player p) {
        List<LivingEntity> out = new ArrayList<>();
        for (Entity n : l.getWorld().getNearbyEntities(l, r, r, r)) if (n instanceof Enemy && n instanceof LivingEntity le && n != p) out.add(le);
        return out;
    }

    private void hurt(Player p, LivingEntity t, double dmg) {
        if (dmg <= 0) return;
        busy.add(p.getUniqueId());
        try { t.damage(dmg, p); } finally { busy.remove(p.getUniqueId()); }
    }

    private boolean harvest(Player p, Block center, int r) {
        int n = 0;
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) for (int dy = -1; dy <= 1; dy++) {
            Block b = center.getRelative(dx, dy, dz);
            Material seed = SEED.get(b.getType());
            if (seed == null || !(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) continue;
            boolean replanted = false;
            for (ItemStack drop : b.getDrops(p.getInventory().getItemInMainHand(), p)) {
                if (!replanted && drop.getType() == seed) { drop.setAmount(drop.getAmount() - 1); replanted = true; }
                if (drop.getAmount() > 0) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), drop);
            }
            age.setAge(0);
            b.setBlockData(age);
            n++;
        }
        if (n > 0) {
            p.playSound(p.getLocation(), Sound.ITEM_CROP_PLANT, 1f, 0.8f);
            p.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, center.getLocation().add(0.5, 1, 0.5), 30, r, 0.5, r);
        }
        return n > 0;
    }

    private static boolean tillable(Block b) {
        Material m = b.getType();
        return (m == Material.DIRT || m == Material.GRASS_BLOCK || m == Material.DIRT_PATH || m == Material.COARSE_DIRT)
                && b.getRelative(BlockFace.UP).getType().isAir();
    }

    private static boolean till(Block center, int r) {
        for (int dx = -r; dx <= r; dx++) for (int dz = -r; dz <= r; dz++) {
            Block b = center.getRelative(dx, 0, dz);
            if (tillable(b)) b.setType(b.getType() == Material.COARSE_DIRT ? Material.DIRT : Material.FARMLAND);
        }
        return true;
    }

    // --- Triggers -----------------------------------------------------------------------------------

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) lastFace.put(e.getPlayer().getUniqueId(), e.getBlockFace());
        if (e.getHand() != EquipmentSlot.HAND || (e.getAction() != Action.RIGHT_CLICK_BLOCK && e.getAction() != Action.RIGHT_CLICK_AIR)) return;
        Player p = e.getPlayer();
        Super s = held(p);
        if (s == null) return;
        Block b = e.getClickedBlock();
        Entity look = null;
        Location at = null;
        RayTraceResult r = p.getWorld().rayTrace(p.getEyeLocation(), p.getLocation().getDirection(), 48,
                org.bukkit.FluidCollisionMode.NEVER, true, 0.3, x -> x != p && x instanceof LivingEntity);
        if (r != null) {
            look = r.getHitEntity();
            at = r.getHitPosition().toLocation(p.getWorld());
        }
        if (at == null) at = p.getEyeLocation().add(p.getLocation().getDirection().multiply(20));
        Block target = b != null ? b : r != null ? r.getHitBlock() : null;
        boolean ran = fire(s, "use", new Ctx(p, look, at, b != null ? b : target));
        if (ran && b != null && (SEED.containsKey(b.getType()) || s.has("harvest"))) e.setCancelled(true);
    }

    private void holdTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            Super s = held(p);
            if (s != null) fire(s, "hold", new Ctx(p, null, p.getLocation(), null));
        }
        long now = System.currentTimeMillis();
        noFall.values().removeIf(t -> t < now);
    }

    @EventHandler(ignoreCancelled = true)
    public void onFall(EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && e.getEntity() instanceof Player p
                && noFall.getOrDefault(p.getUniqueId(), 0L) > System.currentTimeMillis()) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p) || e.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        Super s = held(p);
        if (s == null || busy.contains(p.getUniqueId())) return;
        fire(s, "hit", new Ctx(p, e.getEntity(), e.getEntity().getLocation(), null));
    }

    @EventHandler
    public void onProjectile(ProjectileHitEvent e) {
        String id = e.getEntity().getPersistentDataContainer().get(tag, PersistentDataType.STRING);
        if (id == null || !(e.getEntity().getShooter() instanceof Player p) || !supers.containsKey(id)) return;
        Location at = e.getHitEntity() != null ? e.getHitEntity().getLocation()
                : e.getHitBlock() != null ? e.getHitBlock().getLocation().add(0.5, 1, 0.5) : e.getEntity().getLocation();
        fire(supers.get(id), "impact", new Ctx(p, e.getHitEntity(), at, e.getHitBlock()));
        if (e.getEntity() instanceof AbstractArrow) e.getEntity().remove();
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        Super s = held(p);
        if (s == null || busy.contains(p.getUniqueId())) return;
        Block b = e.getBlock();
        fire(s, "mine", new Ctx(p, null, b.getLocation().add(0.5, 0.5, 0.5), b));
        if (p.isSneaking()) return;
        List<Block> more = new ArrayList<>();
        ItemStack tool = p.getInventory().getItemInMainHand();
        for (Ability ab : s.abilities()) {
            if (!ab.trigger().equals("mine")) continue;
            switch (ab.type()) {
                case "area" -> {
                    if (!b.isPreferredTool(tool)) continue;
                    int r = (int) clamp(ab.strength(), 1, 3);
                    float hard = b.getType().getHardness();
                    BlockFace f = lastFace.getOrDefault(p.getUniqueId(), BlockFace.UP);
                    for (int a = -r; a <= r; a++) for (int c = -r; c <= r; c++) {
                        if (a == 0 && c == 0) continue;
                        Block x = switch (f) {
                            case UP, DOWN -> b.getRelative(a, 0, c);
                            case NORTH, SOUTH -> b.getRelative(a, c, 0);
                            default -> b.getRelative(0, c, a);
                        };
                        float h = x.getType().getHardness();
                        if (x.getType().isAir() || h < 0 || h > hard + 3 || !x.isPreferredTool(tool) || x.getState() instanceof Container) continue;
                        more.add(x);
                    }
                }
                case "tree" -> {
                    if (!Tag.LOGS.isTagged(b.getType())) continue;
                    List<Block> logs = Creator.connected(b, x -> Tag.LOGS.isTagged(x.getType()), 512);
                    if (!Creator.naturalTree(b, logs)) continue;
                    more.addAll(logs);
                    Material log = b.getType();
                    Block ground = b.getRelative(BlockFace.DOWN);
                    Bukkit.getScheduler().runTaskLater(plugin, () -> replant(b, ground, log), 2L);
                }
                case "vein" -> {
                    String g = Creator.oreGroup(b.getType());
                    if (g != null) more.addAll(Creator.connected(b, x -> g.equals(Creator.oreGroup(x.getType())), 64));
                }
                default -> { }
            }
        }
        if (more.isEmpty()) return;
        String id = s.id();
        Bukkit.getScheduler().runTask(plugin, () -> {
            busy.add(p.getUniqueId());
            try {
                for (Block x : more) {
                    Super now = held(p);
                    if (!p.isOnline() || now == null || !now.id().equals(id)) break;
                    if (!x.getType().isAir()) p.breakBlock(x);
                }
            } finally {
                busy.remove(p.getUniqueId());
            }
        });
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrops(BlockDropItemEvent e) {
        Super s = held(e.getPlayer());
        if (s == null || !s.has("smelt")) return;
        for (Item it : e.getItems()) {
            ItemStack out = smelted(it.getItemStack().getType());
            if (out == null) continue;
            ItemStack n = out.clone();
            n.setAmount(it.getItemStack().getAmount() * out.getAmount());
            it.setItemStack(n);
        }
    }

    private ItemStack smelted(Material m) {
        if (smeltCache.containsKey(m)) return smeltCache.get(m);
        ItemStack found = null;
        ItemStack probe = new ItemStack(m);
        for (Iterator<Recipe> i = Bukkit.recipeIterator(); i.hasNext(); ) {
            if (i.next() instanceof FurnaceRecipe f && f.getInputChoice().test(probe)) { found = f.getResult(); break; }
        }
        smeltCache.put(m, found);
        return found;
    }

    private static void replant(Block at, Block ground, Material log) {
        if (!at.getType().isAir() || !Tag.DIRT.isTagged(ground.getType())) return;
        String n = log.name();
        if (n.startsWith("STRIPPED_")) return;
        Material sap = n.equals("MANGROVE_LOG") ? Material.MANGROVE_PROPAGULE : Material.matchMaterial(n.replace("_LOG", "_SAPLING"));
        if (sap != null && sap.isBlock()) at.setType(sap);
    }

    // --- Commands -----------------------------------------------------------------------------------

    private boolean op(CommandSourceStack src) {
        if (src.getSender() instanceof Player sp && !sp.isOp()) {
            sp.sendMessage("Only ops can do that.");
            return false;
        }
        return true;
    }

    private Collection<String> ids(String[] args, boolean all) {
        String q = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Super s : families()) if ((all || !s.approved()) && s.family().startsWith(q)) out.add(s.family());
        return out;
    }

    void register(Commands r) {
        r.register("givesuper", "Gives a super tool from the Big Bench (op only): /givesuper <tool|all> [player]", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!op(src)) return;
                String want = args.length > 0 ? Wand.ascii(args[0].toLowerCase(Locale.ROOT)) : "";
                Super fam = families().stream().filter(x -> x.family().equals(want)).findFirst().orElse(null);
                List<Super> list = want.equals("all") ? approvedFamilies() : supers.containsKey(want) ? List.of(supers.get(want))
                        : fam != null ? List.of(fam) : List.of();
                if (list.isEmpty()) { src.getSender().sendMessage("Usage: givesuper <" + String.join("|", supers.keySet()) + "|all> [player]"); return; }
                Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : src.getExecutor() instanceof Player p ? p : null;
                if (t == null) { src.getSender().sendMessage(args.length > 1 ? "Player not found: " + args[1] : "Usage: givesuper <tool> <player>"); return; }
                for (Super s : list) t.getInventory().addItem(item(s));
                t.sendMessage("You got " + String.join(", ", list.stream().map(Super::name).toList()) + "!");
                if (!t.equals(src.getSender())) src.getSender().sendMessage("Gave " + t.getName() + " " + list.size() + " super tool(s).");
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                if (args.length >= 2) return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
                String q = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                List<String> out = new ArrayList<>(ids(args, true));
                for (String k : supers.keySet()) if (k.startsWith(q) && !out.contains(k)) out.add(k);
                out.add("all");
                return out;
            }
        });
        r.register("superreload", "Reloads the super tools and sends the new resource pack (op only)", (BasicCommand) (src, args) -> {
            if (!op(src)) return;
            String msg = load();
            for (Player p : Bukkit.getOnlinePlayers()) refreshItems(p);
            pushPack(Bukkit.getOnlinePlayers(), true);
            src.getSender().sendMessage(msg + ". The resource pack was sent to everyone online.");
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getOpenInventory().getTopInventory().getHolder() instanceof Holder h) draw(h);
        });
        r.register("superlist", "Lists all super tools and whether they are approved", (BasicCommand) (src, args) -> {
            StringBuilder sb = new StringBuilder("Super tools (" + families().size() + "):");
            for (Super s : families())
                sb.append("\n ").append(s.approved() ? "[ok] " : "[waiting] ").append(s.family()).append(" – ").append(s.name().replaceAll(" \\(.*\\)$", ""))
                        .append(" (made by ").append(s.madeBy()).append(")");
            src.getSender().sendMessage(sb.toString());
        });
        r.register("superapprove", "Approves a new super tool so it can be made in the Big Bench (op only)", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!op(src)) return;
                if (args.length == 0 || !new File(dir, args[0].toLowerCase(Locale.ROOT) + ".json").isFile()) {
                    src.getSender().sendMessage("Usage: superapprove <id>. Waiting: " + String.join(", ", ids(new String[0], false)));
                    return;
                }
                String id = args[0].toLowerCase(Locale.ROOT);
                File f = new File(dir, id + ".json");
                try {
                    JsonObject o = JsonParser.parseString(Files.readString(f.toPath(), StandardCharsets.UTF_8)).getAsJsonObject();
                    o.addProperty("approved", true);
                    Files.writeString(f.toPath(), new com.google.gson.GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(o), StandardCharsets.UTF_8);
                } catch (IOException | RuntimeException ex) {
                    src.getSender().sendMessage("Could not approve: " + ex.getMessage());
                    return;
                }
                load();
                Super fam = families().stream().filter(x -> x.family().equals(id)).findFirst().orElse(null);
                if (fam != null) Bukkit.broadcast(Component.text("New super tool in the Big Bench: " + fam.name().replaceAll(" \\(.*\\)$", "") + "! /bb", fam.color()));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) { return ids(args, false); }
        });
        r.register("superdelete", "Removes a super tool from the Big Bench (op only, the file moves to supers/deleted/)", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!op(src)) return;
                String id = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : "";
                File f = new File(dir, id + ".json");
                if (!f.isFile()) { src.getSender().sendMessage("Usage: superdelete <id>"); return; }
                File trash = new File(dir, "deleted");
                trash.mkdirs();
                if (!f.renameTo(new File(trash, id + "-" + System.currentTimeMillis() + ".json"))) {
                    src.getSender().sendMessage("Could not move the file.");
                    return;
                }
                src.getSender().sendMessage("Removed " + id + ". " + load() + ".");
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) { return ids(args, true); }
        });
    }
}
