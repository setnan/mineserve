package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
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
import org.bukkit.FluidCollisionMode;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Openable;
import org.bukkit.block.data.type.Door;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.IronGolem;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Tameable;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Vector;

/**
 * Magic Wand: a craftable wand (blaze rods, diamonds, eye of ender) with spells chosen by /wand <spell> (alias /wa).
 * Right-click casts, sneak + right-click cycles to the next spell. The tip colour follows the spell
 * (custom_model_data strings "magic_wand" + spell name, models in resourcepack-extra from wand_pack.py).
 * Wands from the old Skaper plugin (Norwegian spell names, skaper:stav keys) are converted on join.
 */
final class Wand implements Listener {
    private final JavaPlugin plugin;
    private final NamespacedKey tag, modeKey, recipeKey, lampKey, cloneKey, helperKey;
    private final Clones clones;
    private final WandMobs mobs;
    private static final NamespacedKey OLD_TAG = new NamespacedKey("skaper", "stav"), OLD_MODE = new NamespacedKey("skaper", "stav_modus"),
            OLD_LAMP = new NamespacedKey("skaper", "stav_lykt"), OLD_CLONE = new NamespacedKey("skaper", "stav_klon");

    /** spell -> [description] (no cooldowns: every spell can be cast as often as you like) */
    private static final Map<String, Object[]> SPELLS = new LinkedHashMap<>();
    static {
        sp("floor", "Floor or ceiling inside the frame (the block in your off hand)");
        sp("wall", "Extends the surface you point at with the same block (up to 64)");
        sp("sow", "Sows 5×5 with seeds from your inventory");
        sp("friendly", "Turns a monster into a friendly animal");
        sp("hostile", "Turns an animal into a monster");
        sp("variant", "Next look of the animal you point at: sheep colour, cat, wolf, horse, parrot, frog, axolotl, cow, pig, chicken …");
        sp("gold", "Turns a chicken into a Golden Chicken (lays golden eggs)");
        sp("zombify", "Villager <-> zombie villager (cured by you = cheap trades), piglin <-> zombified piglin, hoglin <-> zoglin, horse <-> zombie horse, zombie -> villager");
        sp("tame", "Tames a wolf, cat, parrot, horse, llama, fox or ocelot at once");
        sp("ally", "The mob fights monsters for you for 60 s and never hurts players");
        sp("pet", "The mob follows you for good as your pet (cast again to let it go)");
        sp("ride", "You ride the mob: WASD to steer, space = jump/up, sneak = off. Flying mobs fly where you look");
        sp("outfit", "Gives the villager you point at the next look (plains, desert, jungle, savanna, snow, swamp, taiga)");
        sp("big", "Makes what you point at bigger (up to 3×), with more health");
        sp("small", "Makes what you point at smaller (down to ¼), with less health");
        sp("blink", "Teleports you up to 15 blocks forward");
        sp("lift", "What you point at floats up");
        sp("fetch", "Pulls what you point at to you");
        sp("freeze", "What you point at stands completely still for 10 s");
        sp("disarm", "The monster drops what it is holding");
        sp("shield", "A shield around you for 10 s");
        sp("duplicate", "Doubles what you point at: a mob, an item on the ground, a whole tree or a block (/wand undo for blocks and trees)");
        sp("guardian", "Summons an iron golem that guards you for 60 s");
        sp("invisible", "Invisible for 30 s");
        sp("light", "Turns the wand's lantern off or on (glows while you hold the wand)");
        sp("radar", "Monsters within 32 blocks glow through walls");
        sp("reveal", "Players within 100 blocks glow through walls");
        sp("open", "Opens or closes doors and trapdoors, iron ones too");
        sp("water", "Water where you point");
        sp("fire", "Fire where you point");
        sp("repair", "Repairs what you hold in your off hand");
        sp("day", "Turns night into day");
    }

    /** Norwegian spell names stored on wands from the old Skaper plugin. */
    private static final Map<String, String> OLD_SPELLS = Map.ofEntries(
            Map.entry("flate", "floor"), Map.entry("vegg", "wall"), Map.entry("så", "sow"), Map.entry("snill", "friendly"),
            Map.entry("slem", "hostile"), Map.entry("stor", "big"), Map.entry("liten", "small"), Map.entry("løft", "lift"),
            Map.entry("hent", "fetch"), Map.entry("frys", "freeze"), Map.entry("avvæpne", "disarm"), Map.entry("skjold", "shield"),
            Map.entry("klon", "duplicate"), Map.entry("vokter", "guardian"), Map.entry("usynlig", "invisible"), Map.entry("lys", "light"),
            Map.entry("avslør", "reveal"), Map.entry("åpne", "open"), Map.entry("vann", "water"), Map.entry("ild", "fire"),
            Map.entry("reparer", "repair"), Map.entry("dag", "day"));

    private static void sp(String k, String d) { SPELLS.put(k, new Object[]{d}); }

    private static final List<org.bukkit.entity.Villager.Type> OUTFITS = List.of(org.bukkit.entity.Villager.Type.PLAINS,
            org.bukkit.entity.Villager.Type.DESERT, org.bukkit.entity.Villager.Type.JUNGLE, org.bukkit.entity.Villager.Type.SAVANNA,
            org.bukkit.entity.Villager.Type.SNOW, org.bukkit.entity.Villager.Type.SWAMP, org.bukkit.entity.Villager.Type.TAIGA);
    private static final List<String> OUTFIT_NAMES = List.of("plains", "desert", "jungle", "savanna", "snow", "swamp", "taiga");

    // monster -> friendly and back
    private static final Map<String, String> NICE = new HashMap<>(), MEAN = new HashMap<>();
    static {
        for (String[] p : new String[][]{{"zombie", "villager"}, {"zombie_villager", "villager"}, {"husk", "villager"}, {"drowned", "dolphin"},
                {"creeper", "pig"}, {"skeleton", "sheep"}, {"stray", "sheep"}, {"bogged", "sheep"}, {"wither_skeleton", "sheep"},
                {"spider", "cat"}, {"cave_spider", "cat"}, {"witch", "villager"}, {"pillager", "villager"}, {"vindicator", "villager"},
                {"evoker", "villager"}, {"slime", "frog"}, {"magma_cube", "frog"}, {"phantom", "parrot"}, {"enderman", "allay"},
                {"blaze", "chicken"}, {"ghast", "happy_ghast"}, {"silverfish", "rabbit"}, {"endermite", "rabbit"}, {"guardian", "squid"},
                {"hoglin", "pig"}, {"zoglin", "pig"}, {"piglin_brute", "pig"}, {"zombified_piglin", "pig"}, {"breeze", "allay"}, {"vex", "allay"}})
            NICE.put(p[0], p[1]);
        for (String[] p : new String[][]{{"pig", "creeper"}, {"sheep", "skeleton"}, {"cow", "zombie"}, {"mooshroom", "zombie"},
                {"chicken", "blaze"}, {"villager", "zombie_villager"}, {"cat", "spider"}, {"rabbit", "silverfish"}, {"squid", "guardian"},
                {"frog", "slime"}, {"parrot", "phantom"}, {"allay", "vex"}, {"dolphin", "drowned"}, {"horse", "zombie_horse"},
                {"goat", "ravager"}, {"fox", "spider"}, {"happy_ghast", "ghast"}, {"wandering_trader", "witch"}, {"iron_golem", "ravager"}})
            MEAN.put(p[0], p[1]);
    }

    private static final Set<EntityType> BOSSES = Set.of(EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN);

    private final Map<UUID, List<Object[]>> undo = new HashMap<>();          // [Block, BlockData]
    private final Map<UUID, Long> lightUntil = new HashMap<>();
    private final Map<UUID, Block> lightAt = new HashMap<>();

    Wand(JavaPlugin plugin) {
        this.plugin = plugin;
        tag = new NamespacedKey(plugin, "wand");
        modeKey = new NamespacedKey(plugin, "wand_mode");
        lampKey = new NamespacedKey(plugin, "wand_light");
        cloneKey = new NamespacedKey(plugin, "wand_clone");          // only removed from old wands now
        helperKey = new NamespacedKey(plugin, "helper_owner");       // set by Helper on its wolf and figure
        clones = new Clones(plugin);
        Bukkit.getPluginManager().registerEvents(clones, plugin);
        mobs = new WandMobs(plugin);
        Bukkit.getPluginManager().registerEvents(mobs, plugin);
        recipeKey = new NamespacedKey(plugin, "magic_wand");
        ShapedRecipe r = new ShapedRecipe(recipeKey, item("floor"));
        r.shape(" DE", " BD", "B  ");
        r.setIngredient('E', Material.ENDER_EYE);
        r.setIngredient('D', Material.DIAMOND);
        r.setIngredient('B', Material.BLAZE_ROD);
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
        Bukkit.getScheduler().runTaskTimer(plugin, this::lightTick, 20L, 5L);
    }

    // --- Item ---------------------------------------------------------------------------------------

    ItemStack item(String mode) {
        ItemStack it = new ItemStack(Material.BLAZE_ROD);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Magic Wand", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.setEnchantmentGlintOverride(false);
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        setMode(it, mode);
        return it;
    }

    private void setMode(ItemStack it, String mode) {
        ItemMeta m = it.getItemMeta();
        m.getPersistentDataContainer().set(modeKey, PersistentDataType.STRING, mode);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("magic_wand", ascii(mode)));
        m.setItemModel(null);
        m.setCustomModelDataComponent(c);
        m.lore(List.of(
                Component.text("Spell: " + mode, NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false),
                Component.text((String) SPELLS.get(mode)[0], NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Right-click: cast · Sneak + right-click: next · /wa <spell>", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        it.setItemMeta(m);
    }

    /** Lower-case ASCII form of a name, used for model strings and command matching. */
    static String ascii(String s) {
        return s.toLowerCase(Locale.ROOT).replace("å", "a").replace("ø", "o").replace("æ", "ae");
    }

    private boolean isWand(ItemStack it) {
        if (it == null || it.getType() != Material.BLAZE_ROD || !it.hasItemMeta()) return false;
        var pdc = it.getItemMeta().getPersistentDataContainer();
        return pdc.has(tag, PersistentDataType.BYTE) || pdc.has(OLD_TAG, PersistentDataType.BYTE);
    }

    private String modeOf(ItemStack it) {
        var pdc = it.getItemMeta().getPersistentDataContainer();
        String m = pdc.get(modeKey, PersistentDataType.STRING);
        if (m == null) { String old = pdc.get(OLD_MODE, PersistentDataType.STRING); if (old != null) m = OLD_SPELLS.get(old); }
        return m != null && SPELLS.containsKey(m) ? m : "floor";
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(recipeKey);
        for (ItemStack it : e.getPlayer().getInventory().getContents()) migrate(it);
        for (ItemStack it : e.getPlayer().getEnderChest().getContents()) migrate(it);
    }

    /** Converts wands from the old Skaper plugin (skaper:stav* keys, Norwegian spell, item_model) to the new keys and look. */
    private void migrate(ItemStack it) {
        if (!isWand(it)) return;
        ItemMeta m = it.getItemMeta();
        var pdc = m.getPersistentDataContainer();
        boolean old = pdc.has(OLD_TAG, PersistentDataType.BYTE);
        if (!old && !m.hasItemModel()) return;
        String mode = modeOf(it);
        if (old) {
            if (pdc.has(OLD_LAMP, PersistentDataType.BYTE)) pdc.set(lampKey, PersistentDataType.BYTE, (byte) 1);
            for (NamespacedKey k : List.of(OLD_TAG, OLD_MODE, OLD_LAMP, OLD_CLONE)) pdc.remove(k);
            pdc.set(tag, PersistentDataType.BYTE, (byte) 1);
            m.displayName(Component.text("Magic Wand", NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        }
        m.setItemModel(null);
        it.setItemMeta(m);
        setMode(it, mode);
    }

    // --- Commands -----------------------------------------------------------------------------------

    void register(Commands r) {
        BasicCommand cmd = new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (args.length >= 1 && args[0].equalsIgnoreCase("give")) { give(src, args.length > 1 ? args[1] : null); return; }
                if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
                if (args.length >= 1 && args[0].equalsIgnoreCase("undo")) { undo(p); return; }
                ItemStack w = p.getInventory().getItemInMainHand();
                if (args.length == 0 || !isWand(w)) {
                    if (!isWand(w)) p.sendMessage("Hold the Magic Wand in your hand. Recipe: _ diamond eye_of_ender / _ blaze_rod diamond / blaze_rod _ _.");
                    StringBuilder sb = new StringBuilder("Spells:");
                    for (var e : SPELLS.entrySet()) sb.append("\n  ").append(e.getKey()).append(" – ").append(e.getValue()[0]);
                    sb.append("\n  undo – undoes the last floor, wall or duplicated tree/block");
                    p.sendMessage(sb.toString());
                    return;
                }
                if (args.length >= 2 && args[0].equalsIgnoreCase("light")) {
                    String v = args[1].toLowerCase(Locale.ROOT);
                    if (v.equals("off") || v.equals("on")) { setLamp(p, w, v.equals("on")); return; }
                }
                String want = ascii(args[0]);
                String mode = SPELLS.containsKey(want) ? want : OLD_SPELLS.get(args[0].toLowerCase(Locale.ROOT));
                if (mode == null) for (var en : OLD_SPELLS.entrySet()) if (ascii(en.getKey()).equals(want)) mode = en.getValue();
                if (mode == null) { p.sendMessage("Unknown spell \"" + args[0] + "\". Press Tab for suggestions."); return; }
                setMode(w, mode);
                p.sendActionBar(Component.text("Magic Wand: " + mode, NamedTextColor.GOLD));
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                String q = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                if (args.length >= 2 && args[0].equalsIgnoreCase("light")) return List.of("on", "off");
                List<String> out = new ArrayList<>();
                for (String k : SPELLS.keySet()) if (k.startsWith(q)) out.add(k);
                if ("undo".startsWith(q)) out.add("undo");
                return out;
            }
        };
        r.register("wand", "The Magic Wand: /wand <spell>, /wand undo", List.of("wa"), cmd);
        r.register("clones", "Clones of you that do what you do: /clones <count> <seconds>, /clones off", (BasicCommand) new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
                if (args.length >= 1 && args[0].equalsIgnoreCase("off")) { clones.dismiss(p, true); return; }
                int[] nt = parseClones(p, args);
                if (nt != null) clones.summon(p, nt[0], nt[1]);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                return args.length <= 1 ? List.of("5", "10", "20", "off") : List.of("10", "40", "100", "300");
            }
        });
        r.register("givewand", "Gives you (or a player) a Magic Wand (op only)", (BasicCommand) (src, args) -> give(src, args.length > 0 ? args[0] : null));
    }

    /** Parses "<count> [t<secs>|<secs>]" (default 5 clones for 10 s); null after a usage message. */
    private int[] parseClones(Player p, String[] args) {
        int n = 5, t = 10, plain = 0;
        for (String arg : args) {
            String a = arg.toLowerCase(Locale.ROOT).replace("s", "");
            try {
                if (a.startsWith("t")) t = Integer.parseInt(a.substring(1));
                else if (plain++ == 0) n = Integer.parseInt(a);
                else t = Integer.parseInt(a);
            } catch (NumberFormatException ex) { p.sendMessage("Usage: /clones <count> <seconds>, e.g. /clones 10 100 (or /clones 10 t100)"); return null; }
        }
        return new int[]{Math.max(1, Math.min(Clones.MAX, n)), Math.max(1, Math.min(Clones.MAX_SECS, t))};
    }

    private void give(CommandSourceStack src, String name) {
        if (src.getSender() instanceof Player sp && !sp.isOp()) {
            sp.sendMessage("Only ops can give wands. Craft one: _ diamond eye_of_ender / _ blaze_rod diamond / blaze_rod _ _.");
            return;
        }
        Player t = name != null ? Bukkit.getPlayerExact(name) : src.getExecutor() instanceof Player p ? p : null;
        if (t == null) { src.getSender().sendMessage(name == null ? "Usage: givewand <player>" : "Could not find " + name); return; }
        t.getInventory().addItem(item("floor"));
        t.sendMessage("You got a Magic Wand! /wa <spell> chooses, right-click casts, sneak + right-click switches.");
        if (!t.equals(src.getSender())) src.getSender().sendMessage("Gave " + t.getName() + " a Magic Wand.");
    }

    // --- Casting ------------------------------------------------------------------------------------

    @EventHandler
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND || !isWand(e.getItem())) return;
        e.setCancelled(true);
        use(e.getPlayer(), e.getItem());
    }

    /** Right-clicking a mob up close (no air/block click then) casts too, so the wand works on helpers and animals next to you. */
    @EventHandler(priority = org.bukkit.event.EventPriority.LOWEST)
    public void onUseEntity(org.bukkit.event.player.PlayerInteractEntityEvent e) {
        ItemStack it = e.getPlayer().getInventory().getItemInMainHand();
        if (e.getHand() != EquipmentSlot.HAND || !isWand(it)) return;
        e.setCancelled(true);
        use(e.getPlayer(), it);
    }

    private void use(Player p, ItemStack w) {
        String mode = modeOf(w);
        if (p.isSneaking()) {
            List<String> keys = new ArrayList<>(SPELLS.keySet());
            String next = keys.get((keys.indexOf(mode) + 1) % keys.size());
            setMode(w, next);
            p.sendActionBar(Component.text("Magic Wand: " + next + " – " + SPELLS.get(next)[0], NamedTextColor.GOLD));
            p.playSound(p.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.7f, 1.6f);
            return;
        }
        tryCast(p, mode);
    }

    /** Casts with effects and error handling (right-click and /clones). No cooldown. */
    private void tryCast(Player p, String mode) {
        boolean ok;
        try {
            ok = cast(p, mode);
        } catch (RuntimeException ex) {
            plugin.getLogger().warning("Magic Wand " + mode + ": " + ex);
            p.sendMessage("The spell failed.");
            ok = false;
        }
        if (ok) {
            Location eye = p.getEyeLocation();
            p.getWorld().spawnParticle(Particle.FLAME, eye.add(eye.getDirection().multiply(0.8)), 6, 0.1, 0.1, 0.1, 0.02);
            p.playSound(p.getLocation(), Sound.ENTITY_EVOKER_CAST_SPELL, 0.6f, 1.4f);
        }
    }

    private RayTraceResult blockHit(Player p, double range) {
        return p.rayTraceBlocks(range, FluidCollisionMode.NEVER);
    }

    private LivingEntity living(Player p, double range, boolean players) {
        RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), range, 0.4,
                e -> e != p && e instanceof LivingEntity && (players || !(e instanceof Player)));
        if (r == null || r.getHitEntity() == null) return null;
        RayTraceResult wall = blockHit(p, range);
        if (wall != null && wall.getHitPosition().distanceSquared(p.getEyeLocation().toVector())
                < r.getHitPosition().distanceSquared(p.getEyeLocation().toVector())) return null;
        return (LivingEntity) r.getHitEntity();
    }

    private boolean cast(Player p, String mode) {
        switch (mode) {
            case "floor": return floor(p);
            case "wall": return wall(p);
            case "sow": return sow(p);
            case "friendly": case "hostile": return morph(p, mode.equals("friendly"));
            case "outfit": {
                LivingEntity t = living(p, 32, false);
                if (!(t instanceof org.bukkit.entity.Villager) && !(t instanceof org.bukkit.entity.ZombieVillager)) {
                    p.sendActionBar(Component.text("Point at a villager", NamedTextColor.RED)); return false;
                }
                org.bukkit.entity.Villager.Type cur = t instanceof org.bukkit.entity.Villager v ? v.getVillagerType()
                        : ((org.bukkit.entity.ZombieVillager) t).getVillagerType();
                int i = (OUTFITS.indexOf(cur) + 1) % OUTFITS.size();
                org.bukkit.entity.Villager.Type next = OUTFITS.get(i);
                if (t instanceof org.bukkit.entity.Villager v) v.setVillagerType(next);
                else ((org.bukkit.entity.ZombieVillager) t).setVillagerType(next);
                t.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, t.getLocation().add(0, t.getHeight() / 2, 0), 20, 0.4, 0.6, 0.4, 0);
                p.sendActionBar(Component.text("Look: " + OUTFIT_NAMES.get(i), NamedTextColor.GOLD));
                return true;
            }
            case "big": case "small": {
                LivingEntity t = living(p, 32, true);
                if (t == null) { p.sendActionBar(Component.text("Point at an animal, monster or player", NamedTextColor.RED)); return false; }
                AttributeInstance a = t.getAttribute(Attribute.SCALE);
                if (a == null) return false;
                double v = mode.equals("big") ? Math.min(3.0, a.getBaseValue() * 1.5) : Math.max(0.25, a.getBaseValue() / 1.5);
                if (Math.abs(v - 1.0) < 0.1) v = 1.0;
                a.setBaseValue(v);
                mobs.sizeHealth(t, v);                         // bigger takes more, smaller takes less
                t.getWorld().spawnParticle(Particle.WITCH, t.getLocation().add(0, t.getHeight() / 2, 0), 30, 0.4, 0.6, 0.4, 0);
                p.sendActionBar(Component.text(String.format(Locale.ROOT, "Size: %.2f×", v), NamedTextColor.GOLD));
                return true;
            }
            case "blink": {
                Location eye = p.getEyeLocation();
                Vector dir = eye.getDirection();
                RayTraceResult r = blockHit(p, 15);
                double dist = r == null ? 15 : r.getHitPosition().distance(eye.toVector()) - 1.0;
                if (dist < 1.5) { p.sendActionBar(Component.text("No room", NamedTextColor.RED)); return false; }
                Location to = eye.clone().add(dir.multiply(dist)).subtract(0, 1.62, 0);
                for (int i = 0; i < 3 && !(to.getBlock().isPassable() && to.clone().add(0, 1, 0).getBlock().isPassable()); i++) to.add(0, 1, 0);
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.2);
                p.teleport(to);
                p.setFallDistance(0);
                p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, to.clone().add(0, 1, 0), 40, 0.3, 0.6, 0.3, 0.05);
                p.playSound(to, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
                return true;
            }
            case "lift": {
                LivingEntity t = living(p, 32, true);
                if (t == null) return miss(p);
                t.addPotionEffect(new PotionEffect(PotionEffectType.LEVITATION, 60, 1));
                return true;
            }
            case "fetch": {
                RayTraceResult r = p.getWorld().rayTraceEntities(p.getEyeLocation(), p.getEyeLocation().getDirection(), 32, 0.6,
                        e -> e != p && !(e instanceof Player));
                if (r == null || r.getHitEntity() == null) return miss(p);
                Entity t = r.getHitEntity();
                Location to = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(1.5));
                to.setYaw(t.getLocation().getYaw());
                t.teleport(to);
                t.getWorld().spawnParticle(Particle.ENCHANT, to.clone().add(0, 0.5, 0), 30, 0.3, 0.3, 0.3, 0.5);
                return true;
            }
            case "freeze": {
                LivingEntity t = living(p, 32, false);
                if (!(t instanceof Mob m)) return miss(p);
                m.setAI(false);
                m.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0));
                m.getWorld().spawnParticle(Particle.SNOWFLAKE, m.getLocation().add(0, 1, 0), 40, 0.4, 0.8, 0.4, 0);
                Bukkit.getScheduler().runTaskLater(plugin, () -> { if (m.isValid()) m.setAI(true); }, 200L);
                return true;
            }
            case "disarm": {
                LivingEntity t = living(p, 32, false);
                if (t == null || t.getEquipment() == null) return miss(p);
                ItemStack h = t.getEquipment().getItemInMainHand();
                if (h.getType().isAir()) { p.sendActionBar(Component.text("It is not holding anything", NamedTextColor.GRAY)); return false; }
                t.getEquipment().setItemInMainHand(null);
                t.getWorld().dropItemNaturally(t.getLocation().add(0, 1, 0), h);
                return true;
            }
            case "shield": {
                p.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 200, 3));
                for (int i = 0; i < 20; i++) {
                    final int n = i;
                    Bukkit.getScheduler().runTaskLater(plugin, () -> {
                        if (!p.isOnline()) return;
                        p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 12, 1.2, 1.0, 1.2, 0);
                        for (Entity e : p.getNearbyEntities(4, 3, 4))
                            if (e instanceof Enemy && e instanceof LivingEntity)
                                e.setVelocity(e.getLocation().toVector().subtract(p.getLocation().toVector()).setY(0).normalize().multiply(0.9).setY(0.3));
                    }, n * 10L);
                }
                return true;
            }
            case "guardian": {
                Location at = p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(2));
                IronGolem g = p.getWorld().spawn(at, IronGolem.class, x -> {
                    x.setPlayerCreated(true);
                    x.customName(Component.text("Guardian (" + p.getName() + ")", NamedTextColor.AQUA));
                    x.setGlowing(true);
                    x.setPersistent(false);
                });
                p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, at.clone().add(0, 1.5, 0), 60, 0.5, 1, 0.5, 0.3);
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (g.isValid()) { g.getWorld().spawnParticle(Particle.CLOUD, g.getLocation().add(0, 1.5, 0), 40, 0.5, 1, 0.5, 0.05); g.remove(); }
                }, 1200L);
                return true;
            }
            case "duplicate": return duplicate(p);
            case "variant": case "gold": case "zombify": case "tame": case "ally": case "pet": case "ride": {
                LivingEntity t = living(p, 32, false);
                if (t == null) { p.sendActionBar(Component.text("Point at a mob", NamedTextColor.RED)); return false; }
                return switch (mode) {
                    case "variant" -> mobs.variant(p, t);
                    case "gold" -> mobs.gold(p, t);
                    case "zombify" -> mobs.zombify(p, t);
                    case "tame" -> mobs.tame(p, t);
                    case "ally" -> mobs.ally(p, t);
                    case "pet" -> mobs.pet(p, t);
                    default -> mobs.ride(p, t);
                };
            }
            case "invisible":
                p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 600, 0));
                return true;
            case "light":
                setLamp(p, p.getInventory().getItemInMainHand(), !lampOn(p.getInventory().getItemInMainHand()));
                return true;
            case "radar": {
                int n = 0;
                for (Entity e : p.getNearbyEntities(32, 32, 32))
                    if (e instanceof Enemy && e instanceof LivingEntity le) { le.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0)); n++; }
                p.sendActionBar(Component.text(n + " monsters within 32 blocks", NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            case "reveal": {
                int n = 0;
                for (Player o : p.getWorld().getPlayers())
                    if (o != p && o.getLocation().distanceSquared(p.getLocation()) < 100 * 100) { o.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 200, 0)); n++; }
                p.sendActionBar(Component.text(n + (n == 1 ? " player revealed" : " players revealed"), NamedTextColor.LIGHT_PURPLE));
                return true;
            }
            case "open": {
                RayTraceResult r = blockHit(p, 16);
                if (r == null || !(r.getHitBlock().getBlockData() instanceof Openable o)) { p.sendActionBar(Component.text("Point at a door or trapdoor", NamedTextColor.RED)); return false; }
                Block b = r.getHitBlock();
                boolean open = !o.isOpen();
                o.setOpen(open);
                b.setBlockData(o);
                if (o instanceof Door d) {
                    Block other = b.getRelative(d.getHalf() == org.bukkit.block.data.Bisected.Half.BOTTOM ? BlockFace.UP : BlockFace.DOWN);
                    if (other.getBlockData() instanceof Door od) { od.setOpen(open); other.setBlockData(od); }
                }
                p.playSound(b.getLocation(), open ? Sound.BLOCK_IRON_DOOR_OPEN : Sound.BLOCK_IRON_DOOR_CLOSE, 1f, 1f);
                return true;
            }
            case "water": case "fire": {
                RayTraceResult r = blockHit(p, 32);
                if (r == null || r.getHitBlockFace() == null) return miss(p);
                Block b = r.getHitBlock().getRelative(r.getHitBlockFace());
                if (!b.isEmpty()) return miss(p);
                if (mode.equals("water")) {
                    if (p.getWorld().isUltraWarm()) { p.sendActionBar(Component.text("Water evaporates here", NamedTextColor.RED)); return false; }
                    b.setType(Material.WATER);
                } else b.setType(Material.FIRE);
                return true;
            }
            case "repair": {
                ItemStack off = p.getInventory().getItemInOffHand();
                if (!(off.getItemMeta() instanceof Damageable d) || !d.hasDamage()) { p.sendActionBar(Component.text("Hold something worn in your off hand", NamedTextColor.RED)); return false; }
                d.setDamage(0);
                off.setItemMeta(d);
                p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.6f, 1.4f);
                return true;
            }
            case "day": {
                World w = p.getWorld();
                if (w.getTime() < 12500) { p.sendActionBar(Component.text("It is already day", NamedTextColor.GRAY)); return false; }
                w.setTime(1000);
                Bukkit.broadcast(Component.text(p.getName() + " conjured up the day in " + w.getName() + ".", NamedTextColor.GOLD));
                return true;
            }
            default: return false;
        }
    }

    private boolean miss(Player p) {
        p.sendActionBar(Component.text("Missed – point at something", NamedTextColor.RED));
        return false;
    }

    // --- Morph --------------------------------------------------------------------------------------

    private boolean morph(Player p, boolean nice) {
        LivingEntity t = living(p, 32, false);
        if (t == null) return miss(p);
        if (BOSSES.contains(t.getType())) { p.sendActionBar(Component.text("Bosses are too strong for the wand", NamedTextColor.RED)); return false; }
        if (t instanceof Tameable tm && tm.isTamed()) { p.sendActionBar(Component.text("Tame animals are left alone", NamedTextColor.RED)); return false; }
        String from = t.getType().getKey().getKey();
        String to = (nice ? NICE : MEAN).get(from);
        if (to == null) { p.sendActionBar(Component.text("No transformation for " + from, NamedTextColor.GRAY)); return false; }
        Location l = t.getLocation();
        Component name = t.customName();
        if (!nice && t instanceof Villager v) {
            v.zombify();
        } else {
            EntityType type = Registry.ENTITY_TYPE.get(NamespacedKey.minecraft(to));
            if (type == null || type.getEntityClass() == null) return false;
            t.remove();
            Entity n = l.getWorld().spawnEntity(l, type);
            if (name != null) n.customName(name);
            if (n instanceof Mob m) m.setPersistent(true);
        }
        l.getWorld().spawnParticle(nice ? Particle.HEART : Particle.ANGRY_VILLAGER, l.clone().add(0, 1, 0), 12, 0.5, 0.6, 0.5, 0);
        l.getWorld().spawnParticle(Particle.WITCH, l.clone().add(0, 1, 0), 30, 0.4, 0.6, 0.4, 0);
        return true;
    }

    // --- Building -----------------------------------------------------------------------------------

    private static final int MAX_FLOOR = 4096;

    /** Floor (click the ground: the air layer above, enclosed by walls, decides the shape) or ceiling/layer (click a side). */
    private boolean floor(Player p) {
        ItemStack mat = p.getInventory().getItemInOffHand();
        if (mat.getType().isAir() || !mat.getType().isBlock() || !mat.getType().isSolid()) {
            p.sendActionBar(Component.text("Hold the block you want to use in your off hand (F swaps hands)", NamedTextColor.RED));
            return false;
        }
        RayTraceResult r = blockHit(p, 48);
        if (r == null || r.getHitBlockFace() == null) return miss(p);
        Block hit = r.getHitBlock();
        BlockFace face = r.getHitBlockFace();
        Block start = hit.getRelative(face);
        if (face == BlockFace.DOWN || !start.isEmpty()) { p.sendActionBar(Component.text("Point at the ground inside the room (floor) or at the wall from inside (ceiling)", NamedTextColor.RED)); return false; }
        // flood the air layer at start.y, bounded by anything solid
        List<Block> region = new ArrayList<>();
        Set<Block> seen = new HashSet<>(List.of(start));
        ArrayDeque<Block> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty()) {
            Block c = q.poll();
            region.add(c);
            if (region.size() > MAX_FLOOR) {
                p.sendMessage("The frame is not closed (or over " + MAX_FLOOR + " blocks). Fill holes in the walls at that height and try again.");
                return false;
            }
            for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                Block n = c.getRelative(f);
                if (seen.add(n) && (n.isEmpty() || n.isReplaceable() && !n.isLiquid())) q.add(n);
            }
        }
        List<Block> targets = new ArrayList<>();
        for (Block c : region) targets.add(face == BlockFace.UP ? c.getRelative(BlockFace.DOWN) : c);
        return place(p, targets, mat.getType(), face == BlockFace.UP ? "Floor" : "Ceiling");
    }

    /** Extends the clicked surface: every connected block of the same type on that plane gets a copy in front of it. */
    private boolean wall(Player p) {
        RayTraceResult r = blockHit(p, 32);
        if (r == null || r.getHitBlockFace() == null) return miss(p);
        Block hit = r.getHitBlock();
        BlockFace face = r.getHitBlockFace();
        Material m = hit.getType();
        List<Block> targets = new ArrayList<>();
        Set<Block> seen = new HashSet<>(List.of(hit));
        ArrayDeque<Block> q = new ArrayDeque<>(List.of(hit));
        List<BlockFace> sides = new ArrayList<>();
        for (BlockFace f : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN})
            if (f != face && f != face.getOppositeFace()) sides.add(f);
        while (!q.isEmpty() && targets.size() < 64) {
            Block c = q.poll();
            Block front = c.getRelative(face);
            if (!(front.isEmpty() || front.isReplaceable() && !front.isLiquid())) continue;
            targets.add(front);
            for (BlockFace f : sides) {
                Block n = c.getRelative(f);
                if (seen.add(n) && n.getType() == m) q.add(n);
            }
        }
        if (targets.isEmpty()) return miss(p);
        return place(p, targets, m, "Wall");
    }

    private boolean place(Player p, List<Block> targets, Material m, String what) {
        boolean free = p.getGameMode() == GameMode.CREATIVE;
        int have = free ? Integer.MAX_VALUE : count(p, m);
        if (have < targets.size()) {
            p.sendMessage(what + ": needs " + targets.size() + " " + m.getKey().getKey() + ", you have " + have + ".");
            return false;
        }
        List<Object[]> u = new ArrayList<>();
        for (Block b : targets) {
            u.add(new Object[]{b, b.getBlockData().clone()});
            b.setType(m);
        }
        if (!free) p.getInventory().removeItem(new ItemStack(m, targets.size()));
        undo.put(p.getUniqueId(), u);
        p.sendActionBar(Component.text(what + ": " + targets.size() + " blocks. /wand undo undoes it.", NamedTextColor.GOLD));
        return true;
    }

    private static int count(Player p, Material m) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (it != null && it.getType() == m && !it.hasItemMeta()) n += it.getAmount();
        return n;
    }

    private void undo(Player p) {
        List<Object[]> u = undo.remove(p.getUniqueId());
        if (u == null) { p.sendMessage("Nothing to undo."); return; }
        Material m = null;
        int back = 0;
        if (u.get(0).length == 3) {                        // from duplicate: no refund, only blocks that are still the copy
            for (Object[] o : u) {
                Block b = (Block) o[0];
                if (b.getType() == ((BlockData) o[2]).getMaterial()) { b.setBlockData((BlockData) o[1]); back++; }
            }
            p.sendMessage("Undid " + back + " duplicated blocks.");
            return;
        }
        for (Object[] o : u) {
            Block b = (Block) o[0];
            if (m == null) m = b.getType();
            if (b.getType() == m) { b.setBlockData((BlockData) o[1]); back++; }
        }
        if (m != null && p.getGameMode() != GameMode.CREATIVE && back > 0) {
            var left = p.getInventory().addItem(new ItemStack(m, back));
            left.values().forEach(i -> p.getWorld().dropItemNaturally(p.getLocation(), i));
        }
        p.sendMessage("Undid " + back + " blocks" + (p.getGameMode() != GameMode.CREATIVE ? " (the blocks are back in your inventory)." : "."));
    }

    // --- Duplicate ----------------------------------------------------------------------------------

    private static final int TREE_MAX = 4000;

    /** Doubles what the player points at: the nearest entity (mob, item, armour stand …), otherwise a tree or a
     *  block; pointing at nothing doubles the item in the off hand. */
    private boolean duplicate(Player p) {
        Location eye = p.getEyeLocation();
        RayTraceResult er = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 32, 0.3,
                e -> e != p && !(e instanceof Player) && !(e instanceof org.bukkit.entity.Mannequin)
                        && !e.getPersistentDataContainer().has(helperKey, PersistentDataType.STRING));
        RayTraceResult br = blockHit(p, 32);
        boolean entityFirst = er != null && er.getHitEntity() != null && (br == null
                || er.getHitPosition().distanceSquared(eye.toVector()) < br.getHitPosition().distanceSquared(eye.toVector()));
        if (entityFirst) {
            Entity e = er.getHitEntity();
            if (e instanceof Item it) {
                ItemStack copy = it.getItemStack().clone();
                e.getWorld().dropItem(e.getLocation(), copy);
                p.sendActionBar(Component.text("Duplicated " + copy.getAmount() + " " + copy.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GOLD));
            } else {
                Entity c = e.copy(e.getLocation().add(0.3, 0, 0.3));
                if (c == null) { p.sendActionBar(Component.text("That cannot be duplicated", NamedTextColor.RED)); return false; }
                p.sendActionBar(Component.text("Duplicated " + e.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GOLD));
            }
            e.getWorld().spawnParticle(Particle.END_ROD, e.getLocation().add(0, e.getHeight() / 2, 0), 25, 0.4, 0.5, 0.4, 0.02);
            return true;
        }
        if (br != null && br.getHitBlock() != null) {
            Block b = br.getHitBlock();
            if (org.bukkit.Tag.LOGS.isTagged(b.getType())) return duplicateTree(p, b);
            if (b.getType().getHardness() < 0) { p.sendActionBar(Component.text("That block cannot be duplicated", NamedTextColor.RED)); return false; }
            BlockFace face = br.getHitBlockFace() == null ? BlockFace.UP : br.getHitBlockFace();
            Block to = b.getRelative(face);
            if (!(to.isEmpty() || to.isReplaceable())) { p.sendActionBar(Component.text("No room next to it", NamedTextColor.RED)); return false; }
            BlockData old = to.getBlockData().clone();
            b.getState().copy(to.getLocation()).update(true, false);
            undo.put(p.getUniqueId(), new ArrayList<>(List.<Object[]>of(new Object[]{to, old, to.getBlockData().clone()})));
            to.getWorld().spawnParticle(Particle.END_ROD, to.getLocation().add(0.5, 0.5, 0.5), 15, 0.3, 0.3, 0.3, 0.02);
            p.sendActionBar(Component.text("Duplicated " + b.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GOLD));
            return true;
        }
        ItemStack off = p.getInventory().getItemInOffHand();
        if (off.getType().isAir()) {
            p.sendActionBar(Component.text("Point at a mob, an item, a tree or a block (or hold an item in your off hand)", NamedTextColor.RED));
            return false;
        }
        p.getInventory().addItem(off.clone()).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
        p.sendActionBar(Component.text("Duplicated " + off.getAmount() + " " + off.getType().getKey().getKey().replace('_', ' '), NamedTextColor.GOLD));
        return true;
    }

    /** Copies the whole tree (connected logs, then the leaves around them) and plants the copy beside it. */
    private boolean duplicateTree(Player p, Block start) {
        Set<Block> logs = new HashSet<>();
        ArrayDeque<Block> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty() && logs.size() < 600) {
            Block b = q.poll();
            if (!logs.add(b)) continue;
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block n = b.getRelative(dx, dy, dz);
                if (!logs.contains(n) && org.bukkit.Tag.LOGS.isTagged(n.getType())
                        && Math.abs(n.getX() - start.getX()) <= 12 && Math.abs(n.getZ() - start.getZ()) <= 12) q.add(n);
            }
        }
        int x0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y0 = Integer.MAX_VALUE, y1 = Integer.MIN_VALUE, z0 = Integer.MAX_VALUE, z1 = Integer.MIN_VALUE;
        for (Block b : logs) {
            x0 = Math.min(x0, b.getX()); x1 = Math.max(x1, b.getX()); y0 = Math.min(y0, b.getY());
            y1 = Math.max(y1, b.getY()); z0 = Math.min(z0, b.getZ()); z1 = Math.max(z1, b.getZ());
        }
        List<Block> parts = new ArrayList<>(logs);
        World w = start.getWorld();
        for (int x = x0 - 4; x <= x1 + 4; x++) for (int y = y0; y <= y1 + 4; y++) for (int z = z0 - 4; z <= z1 + 4; z++) {
            Block b = w.getBlockAt(x, y, z);
            if (org.bukkit.Tag.LEAVES.isTagged(b.getType()) || b.getType() == Material.VINE || b.getType() == Material.BEE_NEST
                    || b.getType() == Material.COCOA) parts.add(b);
            if (parts.size() > TREE_MAX) break;
        }
        // beside the tree, to the side the player is not looking along
        var dir = p.getLocation().getDirection();
        boolean alongX = Math.abs(dir.getX()) > Math.abs(dir.getZ());
        int dx = alongX ? 0 : (x1 - x0 + 9) * (dir.getZ() > 0 ? -1 : 1);
        int dz = alongX ? (z1 - z0 + 9) * (dir.getX() > 0 ? 1 : -1) : 0;
        // put the copy on the ground there
        Block base = null;
        for (Block b : logs) if (b.getY() == y0) { base = b; break; }
        int bx = base.getX() + dx, bz = base.getZ() + dz, ground = Integer.MIN_VALUE;
        for (int y = y0 + 6; y >= y0 - 12; y--) {
            Material m = w.getBlockAt(bx, y, bz).getType();
            if (m.isSolid() && !org.bukkit.Tag.LEAVES.isTagged(m) && !org.bukkit.Tag.LOGS.isTagged(m)) { ground = y; break; }
        }
        int dy = ground == Integer.MIN_VALUE ? 0 : ground + 1 - y0;
        List<Object[]> u = new ArrayList<>();
        for (Block b : parts) {
            Block to = b.getRelative(dx, dy, dz);
            if (!(to.isEmpty() || to.isReplaceable() || org.bukkit.Tag.LEAVES.isTagged(to.getType()))) continue;
            BlockData old = to.getBlockData().clone();
            to.setBlockData(b.getBlockData().clone(), false);
            u.add(new Object[]{to, old, to.getBlockData().clone()});
        }
        if (u.isEmpty()) { p.sendActionBar(Component.text("No room for a copy of the tree", NamedTextColor.RED)); return false; }
        undo.put(p.getUniqueId(), u);
        Location at = w.getBlockAt(bx, y0 + dy, bz).getLocation().add(0.5, 1, 0.5);
        w.spawnParticle(Particle.HAPPY_VILLAGER, at, 60, 1.5, 2, 1.5, 0);
        p.sendActionBar(Component.text("Duplicated the tree (" + u.size() + " blocks). /wand undo removes the copy.", NamedTextColor.GOLD));
        return true;
    }

    // --- Seeds --------------------------------------------------------------------------------------

    private static final Map<Material, Material> CROPS = Map.of(Material.WHEAT_SEEDS, Material.WHEAT, Material.CARROT, Material.CARROTS,
            Material.POTATO, Material.POTATOES, Material.BEETROOT_SEEDS, Material.BEETROOTS, Material.NETHER_WART, Material.NETHER_WART);

    private boolean sow(Player p) {
        RayTraceResult r = blockHit(p, 16);
        if (r == null) return miss(p);
        Block c = r.getHitBlock();
        if (c.getType() != Material.FARMLAND && c.getType() != Material.SOUL_SAND) c = c.getRelative(BlockFace.DOWN);
        Material seed = null;
        ItemStack off = p.getInventory().getItemInOffHand();
        if (CROPS.containsKey(off.getType())) seed = off.getType();
        else for (Material s : CROPS.keySet()) if (p.getInventory().contains(s)) { seed = s; break; }
        if (seed == null) { p.sendActionBar(Component.text("No seeds in your inventory", NamedTextColor.RED)); return false; }
        Material soil = seed == Material.NETHER_WART ? Material.SOUL_SAND : Material.FARMLAND;
        boolean free = p.getGameMode() == GameMode.CREATIVE;
        int planted = 0;
        for (int dx = -2; dx <= 2; dx++)
            for (int dz = -2; dz <= 2; dz++) {
                Block s = c.getRelative(dx, 0, dz);
                Block above = s.getRelative(BlockFace.UP);
                if (s.getType() != soil || !above.isEmpty()) continue;
                if (!free && !p.getInventory().containsAtLeast(new ItemStack(seed), 1)) break;
                above.setType(CROPS.get(seed));
                if (above.getBlockData() instanceof Ageable a) { a.setAge(0); above.setBlockData(a); }
                if (!free) p.getInventory().removeItem(new ItemStack(seed, 1));
                planted++;
            }
        if (planted == 0) { p.sendActionBar(Component.text("No free farmland here", NamedTextColor.GRAY)); return false; }
        p.sendActionBar(Component.text("Sowed " + planted, NamedTextColor.GREEN));
        return true;
    }

    private boolean lampOn(ItemStack it) {
        return isWand(it) && !it.getItemMeta().getPersistentDataContainer().has(lampKey, PersistentDataType.BYTE);
    }

    private void setLamp(Player p, ItemStack it, boolean on) {
        if (!isWand(it)) return;
        ItemMeta m = it.getItemMeta();
        if (on) m.getPersistentDataContainer().remove(lampKey);
        else m.getPersistentDataContainer().set(lampKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        p.sendActionBar(Component.text("The wand's lantern is " + (on ? "on" : "off"), on ? NamedTextColor.YELLOW : NamedTextColor.GRAY));
    }

    // --- Light --------------------------------------------------------------------------------------

    private void lightTick() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            Block old = lightAt.get(id);
            boolean on = !p.isDead() && (lightUntil.getOrDefault(id, 0L) > now
                    || lampOn(p.getInventory().getItemInMainHand()) || lampOn(p.getInventory().getItemInOffHand()));
            Block want = on ? p.getLocation().getBlock().getRelative(BlockFace.UP) : null;
            if (old != null && !old.equals(want) && old.getType() == Material.LIGHT) old.setType(Material.AIR);
            if (old != null && !old.equals(want)) lightAt.remove(id);
            if (want != null && (want.isEmpty() || want.getType() == Material.LIGHT)) {
                if (want.getType() != Material.LIGHT) {
                    want.setType(Material.LIGHT);
                }
                lightAt.put(id, want);

            }
            if (!on) lightUntil.remove(id);
        }
    }

    void shutdown() {
        clones.shutdown();
        mobs.shutdown();
        for (Block b : lightAt.values()) if (b.getType() == Material.LIGHT) b.setType(Material.AIR);
    }
}
