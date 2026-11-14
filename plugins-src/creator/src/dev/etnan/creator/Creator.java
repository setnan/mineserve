package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import java.io.File;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.GameRules;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.BubbleColumn;
import org.bukkit.block.data.type.Ladder;
import org.bukkit.block.data.type.Leaves;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Creator: crafting stations anywhere, powers and lots of extras for a family server.
 * - /craft, /c, /bigbench and /tool_* open crafting stations anywhere (needs creator.craft, given by /creatorpower via chatbro).
 * - Powers toggled per player: /lumberpower (fell whole trees), /minerpower (mine whole ore veins),
 *   /harvestpower (right-click harvest and replant), /magnetpower (pull nearby items), /flypower, /lightpower,
 *   /super (no damage, no hunger; off in the worlds listed in MC_RESTRICTED_WORLDS).
 *   Sneak to switch them off briefly.
 * - Gravestone: on death the drops go into barrels where you died; /grave takes you there.
 * - One sleeping player skips the night in every world.
 * - Storage, Detector, Wand (+ Clones), BigBench (super tools), GlowingCoal, GoldenChicken and XrayGoggles live in their own classes.
 */
public final class Creator extends JavaPlugin implements Listener {
    private static final String CRAFT_PERM = "creator.craft";
    private static final Map<String, String> POWERS = new LinkedHashMap<>();
    static {
        POWERS.put("lumber", "Lumber power: chop one log and the whole tree falls (sneak = just one)");
        POWERS.put("miner", "Miner power: mine one ore and the whole vein goes (sneak = just one)");
        POWERS.put("harvest", "Harvest power: right-click a ripe crop to harvest and replant it");
        POWERS.put("magnet", "Magnet power: items around you are pulled to you (sneak = off)");
        POWERS.put("fly", "Fly power: double-jump to fly, like in creative");
        POWERS.put("light", "Light power: places a lantern where it is dark enough for monsters (uses lanterns from your inventory)");
        POWERS.put("super", "Super: you take no damage and never get hungry (in MC_RESTRICTED_WORLDS only for ops and cheaters)");
    }

    /** Power keys of the old Norwegian plugin (Skaper) in data.yml, read once and then saved under the new key. */
    private static final Map<String, String> OLD_POWER = Map.of("lumber", "hogge", "miner", "gruve", "harvest", "hoste",
            "magnet", "magnet", "fly", "fly", "light", "lys");

    private final Map<String, Set<UUID>> on = new LinkedHashMap<>();
    private final Set<UUID> busy = new HashSet<>();
    private YamlConfiguration data;
    private Storage storage;
    private Detector detector;
    private Wand wand;
    private BigBench bigBench;
    private GlowingCoal glowingCoal;
    private GoldenChicken goldenChicken;
    private Helper helper;
    private Skins skins;
    private XrayGoggles xray;
    private File dataFile;

    @Override
    public void onEnable() {
        migrateFromSkaper();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        for (String p : POWERS.keySet()) {
            Set<UUID> s = new HashSet<>();
            for (String id : data.getStringList("powers." + p)) s.add(UUID.fromString(id));
            String old = OLD_POWER.get(p);                       // data.yml from the old Norwegian plugin
            if (old != null) for (String id : data.getStringList("krefter." + old)) s.add(UUID.fromString(id));
            on.put(p, s);
        }
        ConfigurationSection graves = data.getConfigurationSection("graver");
        if (graves != null) {
            for (String k : graves.getKeys(false)) if (!data.contains("graves." + k)) data.set("graves." + k, graves.getString(k));
            data.set("graver", null);
        }
        data.set("krefter", null);
        save();
        getServer().getPluginManager().registerEvents(this, this);
        storage = new Storage(this);
        getServer().getPluginManager().registerEvents(storage, this);
        detector = new Detector(this);
        getServer().getPluginManager().registerEvents(detector, this);
        wand = new Wand(this);
        getServer().getPluginManager().registerEvents(wand, this);
        bigBench = new BigBench(this);
        getServer().getPluginManager().registerEvents(bigBench, this);
        glowingCoal = new GlowingCoal(this);
        getServer().getPluginManager().registerEvents(glowingCoal, this);
        goldenChicken = new GoldenChicken(this);
        getServer().getPluginManager().registerEvents(goldenChicken, this);
        xray = new XrayGoggles(this);
        getServer().getPluginManager().registerEvents(xray, this);
        skins = new Skins(this);
        getServer().getPluginManager().registerEvents(skins, this);
        helper = new Helper(this, skins);
        helper.useStorage(storage);
        storage.onUse = helper::barrelUsed;
        getServer().getPluginManager().registerEvents(helper, this);
        for (World w : Bukkit.getWorlds()) sleepRule(w);
        Bukkit.getScheduler().runTaskTimer(this, this::magnet, 20L, 4L);
        Bukkit.getScheduler().runTaskTimer(this, this::lys, 20L, 10L);

        getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, e -> {
            var r = e.registrar();
            helper.register(r);
            skins.register(r);
            r.register("craft", "Crafting table (creator power)", List.of("c"), station("crafting table", p -> p.openWorkbench(null, true)));
            r.register("bigbench", "Big Bench: 5x5 crafting for super tools (creator power)", List.of("bb", "tool_bigbench"), station("Big Bench", bigBench::open));
            r.register("tool_anvil", "Anvil (creator power)", station("anvil", p -> p.openAnvil(null, true)));
            r.register("tool_smithing", "Smithing table (creator power)", station("smithing table", p -> p.openSmithingTable(null, true)));
            r.register("tool_stonecutter", "Stonecutter (creator power)", station("stonecutter", p -> p.openStonecutter(null, true)));
            r.register("tool_grindstone", "Grindstone (creator power)", station("grindstone", p -> p.openGrindstone(null, true)));
            r.register("tool_loom", "Loom (creator power)", station("loom", p -> p.openLoom(null, true)));
            r.register("tool_cartography", "Cartography table (creator power)", station("cartography table", p -> p.openCartographyTable(null, true)));
            r.register("tool_enderchest", "Your ender chest (creator power)", station("ender chest", p -> p.openInventory(p.getEnderChest())));
            r.register("tool_trash", "Trash can: what you put here disappears (creator power)",
                    station("trash can", p -> p.openInventory(Bukkit.createInventory(null, 27, Component.text("Trash (deleted when you close it)")))));
            for (String p : POWERS.keySet()) {
                String cmd = p.equals("super") ? "super" : p + "power";        // /super, not /superpower
                r.register(cmd, POWERS.get(p), toggle(p, true));
                r.register(cmd + "off", "Turns off " + cmd, toggle(p, false));
            }
            r.register("tpa", "Asks to teleport to a player: /tpa <player>", new BasicCommand() {
                @Override
                public void execute(CommandSourceStack src, String[] args) { tpa(src, args); }

                @Override
                public java.util.Collection<String> suggest(CommandSourceStack src, String[] args) {
                    String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase();
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> n.toLowerCase().startsWith(last)).toList();
                }
            });
            r.register("tpaccept", "Accepts a teleport request", (BasicCommand) (src, args) -> tpaAnswer(src, true));
            r.register("tpdeny", "Denies a teleport request", (BasicCommand) (src, args) -> tpaAnswer(src, false));
            r.register("pos", "Shares your position in chat", (BasicCommand) (src, args) -> {
                if (!(src.getExecutor() instanceof Player p)) return;
                Location l = p.getLocation();
                Component msg = Component.text(p.getName() + " is at " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()
                        + " in " + l.getWorld().getName() + " ", NamedTextColor.GOLD)
                        .append(Component.text("[Ask to come there]", NamedTextColor.AQUA)
                                .clickEvent(ClickEvent.runCommand("/tpa " + p.getName()))
                                .hoverEvent(HoverEvent.showText(Component.text("Sends /tpa " + p.getName()))));
                Bukkit.broadcast(msg);
            });
            r.register("powers", "Shows which powers you have on", (BasicCommand) (src, args) -> {
                if (!(src.getExecutor() instanceof Player p)) return;
                p.sendMessage("Powers (type the name to turn on, name + off to turn off):");
                for (String k : POWERS.keySet())
                    p.sendMessage((on.get(k).contains(p.getUniqueId()) ? " [on] " : " [off] ") + "/" + (k.equals("super") ? "super" : k + "power") + " – " + POWERS.get(k));
                p.sendMessage((p.hasPermission(CRAFT_PERM) ? " [on] " : " [off] ")
                        + "/creatorpower – /c, /bigbench, /tool_anvil, /tool_smithing, /tool_stonecutter, /tool_grindstone, /tool_loom, /tool_cartography, /tool_enderchest, /tool_trash");
            });
            storage.register(r);
            detector.register(r);
            wand.register(r);
            bigBench.register(r);
            glowingCoal.register(r);
            goldenChicken.register(r);
            xray.register(r);
            r.register("elevator", "Builds a water elevator N blocks up from where you stand", (BasicCommand) (src, args) -> elevatorCmd(src, args, true));
            r.register("elevatordown", "Builds a water elevator N blocks down (stops above bedrock)", (BasicCommand) (src, args) -> elevatorCmd(src, args, false));
            r.register("grave", "Teleports you to your gravestone (where you last died)", (BasicCommand) (src, args) -> {
                if (src.getExecutor() instanceof Player p) grave(p);
            });
        });
    }

    @Override
    public void onDisable() {
        if (storage != null) storage.save();
        if (wand != null) wand.shutdown();
        if (bigBench != null) bigBench.shutdown();
        if (xray != null) xray.shutdown();
        if (helper != null) helper.shutdown();
    }

    /** The plugin used to be called Skaper: copy its data files once (Storage converts lager.yml itself). */
    private void migrateFromSkaper() {
        File old = new File(getDataFolder().getParentFile(), "Skaper");
        if (!old.isDirectory()) return;
        getDataFolder().mkdirs();
        for (String[] f : new String[][]{{"data.yml", "data.yml"}, {"lager.yml", "lager.yml"}}) {
            File from = new File(old, f[0]), to = new File(getDataFolder(), f[1]);
            if (!from.isFile() || to.exists()) continue;
            try {
                java.nio.file.Files.copy(from.toPath(), to.toPath());
                getLogger().info("Copied " + from + " to " + to);
            } catch (IOException ex) {
                getLogger().warning("Could not copy " + from + ": " + ex.getMessage());
            }
        }
        if (old.renameTo(new File(old.getParentFile(), "Skaper.migrated"))) getLogger().info("Old data folder renamed to Skaper.migrated");
    }

    // --- Stations -----------------------------------------------------------------------------------

    /** Opens a station for the player; from the console: <command> <player>. */
    private BasicCommand station(String name, Consumer<Player> open) {
        return (CommandSourceStack src, String[] args) -> {
            CommandSender sender = src.getSender();
            Player target;
            if (args.length > 0 && !(sender instanceof Player)) {
                target = Bukkit.getPlayerExact(args[0]);
                if (target == null) { sender.sendMessage("Player not found: " + args[0]); return; }
            } else if (src.getExecutor() instanceof Player p) {
                if (!p.hasPermission(CRAFT_PERM)) {
                    p.sendMessage("You don't have creator power. Type /creatorpower first, then the " + name + " works anywhere.");
                    return;
                }
                target = p;
            } else {
                sender.sendMessage("Usage: <command> <player>");
                return;
            }
            open.accept(target);
        };
    }

    // --- Powers -------------------------------------------------------------------------------------

    private BasicCommand toggle(String power, boolean enable) {
        return (src, args) -> {
            Player p = src.getExecutor() instanceof Player pl ? pl
                    : args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
            if (p == null) { src.getSender().sendMessage("Usage: " + (power.equals("super") ? "super" : power + "power") + " <player>"); return; }
            Set<UUID> s = on.get(power);
            if (enable) s.add(p.getUniqueId()); else s.remove(p.getUniqueId());
            save();
            if (power.equals("fly")) applyFly(p);
            if (power.equals("super") && enable && superWorks(p)) { p.setHealth(p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue()); p.setFoodLevel(20); p.setSaturation(20f); p.setFireTicks(0); }
            if (power.equals("super") && enable && !superWorks(p)) p.sendMessage("(Super does not work in this world, only elsewhere.)");
            String cmd = power.equals("super") ? "super" : power + "power";
            String name = power.equals("super") ? "Super" : Character.toUpperCase(power.charAt(0)) + power.substring(1) + " power";
            p.sendMessage(enable ? name + " on! " + POWERS.get(power).substring(POWERS.get(power).indexOf(':') + 2)
                    + ". /" + cmd + "off turns it off." : name + " off.");
        };
    }

    private boolean has(String power, Player p) {
        return on.get(power).contains(p.getUniqueId());
    }

    private void save() {
        for (var e : on.entrySet()) data.set("powers." + e.getKey(), e.getValue().stream().map(UUID::toString).toList());
        try { data.save(dataFile); } catch (IOException ex) { getLogger().warning("Could not save data.yml: " + ex.getMessage()); }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (busy.contains(p.getUniqueId()) || p.isSneaking()) return;
        Block b = e.getBlock();
        Material m = b.getType();
        String tool = p.getInventory().getItemInMainHand().getType().name();
        List<Block> more;
        String need;
        if (has("lumber", p) && Tag.LOGS.isTagged(m) && tool.endsWith("_AXE")) {
            more = connected(b, x -> Tag.LOGS.isTagged(x.getType()), 256);
            if (!naturalTree(b, more)) return;   // a log house has no natural leaves: leave it alone
            need = "_AXE";
        } else if (has("miner", p) && oreGroup(m) != null && tool.endsWith("_PICKAXE")) {
            String g = oreGroup(m);
            more = connected(b, x -> g.equals(oreGroup(x.getType())), 64);
            need = "_PICKAXE";
        } else {
            return;
        }
        if (more.isEmpty()) return;
        Bukkit.getScheduler().runTask(this, () -> {
            busy.add(p.getUniqueId());
            try {
                for (Block x : more) {
                    if (!p.isOnline() || !p.getInventory().getItemInMainHand().getType().name().endsWith(need)) break;
                    if (x.getType().isAir()) continue;
                    p.breakBlock(x);   // uses the tool: drops, durability and enchantments as normal
                }
            } finally {
                busy.remove(p.getUniqueId());
            }
        });
    }

    static List<Block> connected(Block start, java.util.function.Predicate<Block> match, int max) {
        List<Block> out = new ArrayList<>();
        Set<Block> seen = new HashSet<>(List.of(start));
        ArrayDeque<Block> q = new ArrayDeque<>(List.of(start));
        while (!q.isEmpty() && out.size() < max) {
            Block c = q.poll();
            for (int dx = -1; dx <= 1; dx++) for (int dy = -1; dy <= 1; dy++) for (int dz = -1; dz <= 1; dz++) {
                Block n = c.getRelative(dx, dy, dz);
                if (seen.add(n) && match.test(n)) { out.add(n); q.add(n); }
            }
        }
        out.sort((a, b2) -> Integer.compare(a.getY(), b2.getY()));
        return out;
    }

    static boolean naturalTree(Block start, List<Block> logs) {
        List<Block> all = new ArrayList<>(logs);
        all.add(start);
        for (Block l : all)
            for (BlockFace f : new BlockFace[]{BlockFace.UP, BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
                BlockData d = l.getRelative(f).getBlockData();
                if (d instanceof Leaves lv && !lv.isPersistent()) return true;
            }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static final Tag<Material>[] ORE_TAGS = new Tag[]{Tag.COAL_ORES, Tag.IRON_ORES, Tag.COPPER_ORES, Tag.GOLD_ORES,
            Tag.REDSTONE_ORES, Tag.LAPIS_ORES, Tag.DIAMOND_ORES, Tag.EMERALD_ORES};

    static String oreGroup(Material m) {
        for (Tag<Material> t : ORE_TAGS) if (t.isTagged(m)) return t.getKey().getKey();
        if (m == Material.NETHER_QUARTZ_ORE || m == Material.NETHER_GOLD_ORE || m == Material.ANCIENT_DEBRIS) return m.name();
        return null;
    }

    private static final Map<Material, Material> SEED = Map.of(Material.WHEAT, Material.WHEAT_SEEDS,
            Material.CARROTS, Material.CARROT, Material.POTATOES, Material.POTATO,
            Material.BEETROOTS, Material.BEETROOT_SEEDS, Material.NETHER_WART, Material.NETHER_WART);

    @EventHandler(ignoreCancelled = false)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND || e.getClickedBlock() == null) return;
        Player p = e.getPlayer();
        Block b = e.getClickedBlock();
        Material seed = SEED.get(b.getType());
        if (seed == null || !has("harvest", p) || p.isSneaking()) return;
        if (!(b.getBlockData() instanceof Ageable age) || age.getAge() < age.getMaximumAge()) return;
        e.setCancelled(true);
        boolean replanted = false;
        for (ItemStack drop : b.getDrops(p.getInventory().getItemInMainHand(), p)) {
            if (!replanted && drop.getType() == seed) {
                drop.setAmount(drop.getAmount() - 1);
                replanted = true;
            }
            if (drop.getAmount() > 0) b.getWorld().dropItemNaturally(b.getLocation().add(0.5, 0.3, 0.5), drop);
        }
        age.setAge(0);
        b.setBlockData(age);
    }

    // --- Teleport requests ----------------------------------------------------------------------------

    private final Map<UUID, UUID> tpaFrom = new java.util.HashMap<>();     // target -> requester
    private final Map<UUID, Long> tpaUntil = new java.util.HashMap<>();

    private void tpa(CommandSourceStack src, String[] args) {
        if (!(src.getExecutor() instanceof Player p)) return;
        Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : null;
        if (t == null) { p.sendMessage("Usage: /tpa <player> (must be online)"); return; }
        if (t.equals(p)) { p.sendMessage("You are already where you are."); return; }
        tpaFrom.put(t.getUniqueId(), p.getUniqueId());
        tpaUntil.put(t.getUniqueId(), System.currentTimeMillis() + 60_000);
        p.sendMessage("Asked " + t.getName() + " if you can come. They have 60 seconds to answer.");
        t.sendMessage(Component.text(p.getName() + " wants to teleport to you. ", NamedTextColor.GOLD)
                .append(Component.text("[Yes]", NamedTextColor.GREEN).clickEvent(ClickEvent.runCommand("/tpaccept"))
                        .hoverEvent(HoverEvent.showText(Component.text("/tpaccept"))))
                .append(Component.text(" "))
                .append(Component.text("[No]", NamedTextColor.RED).clickEvent(ClickEvent.runCommand("/tpdeny"))
                        .hoverEvent(HoverEvent.showText(Component.text("/tpdeny")))));
    }

    private void tpaAnswer(CommandSourceStack src, boolean yes) {
        if (!(src.getExecutor() instanceof Player t)) return;
        UUID from = tpaFrom.remove(t.getUniqueId());
        Long until = tpaUntil.remove(t.getUniqueId());
        Player p = from == null ? null : Bukkit.getPlayer(from);
        if (p == null || until == null || until < System.currentTimeMillis()) { t.sendMessage("No request waiting (they last 60 seconds)."); return; }
        if (yes) {
            p.teleport(t.getLocation());
            p.sendMessage(t.getName() + " said yes. Welcome!");
            t.sendMessage(p.getName() + " is here now.");
        } else {
            p.sendMessage(t.getName() + " said no.");
            t.sendMessage("Denied.");
        }
    }

    // --- Super: no damage and no hunger, except in the restricted (survival) worlds ---------------------

    /** Worlds where Super does nothing: MC_RESTRICTED_WORLDS from .env (the mc container gets it through env_file). */
    private static final Set<String> RESTRICTED = new HashSet<>();
    static {
        String w = System.getenv("MC_RESTRICTED_WORLDS");
        if (w != null) for (String x : w.split(",")) if (!x.isBlank()) RESTRICTED.add(x.trim().toLowerCase(java.util.Locale.ROOT));
    }

    /** True in the worlds listed in MC_RESTRICTED_WORLDS (and their _nether / _the_end). */
    static boolean restricted(World world) {
        String w = world.getName().toLowerCase(java.util.Locale.ROOT);
        for (String r : RESTRICTED) if (w.equals(r) || w.startsWith(r + "_")) return true;
        return false;
    }

    /** Restricted for this player: a restricted world, unless they have creator.restricted.bypass (ops by default,
     *  and the temporary cheat group: ./mc.sh cheat <name>). */
    static boolean restricted(Player p) {
        return restricted(p.getWorld()) && !p.hasPermission("creator.restricted.bypass");
    }

    private boolean superWorks(Player p) {
        return !restricted(p) && has("super", p);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onSuperDamage(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !superWorks(p)) return;
        var cause = e.getCause();   // the void and /kill still work, so nobody gets stuck falling forever
        if (cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.VOID || cause == org.bukkit.event.entity.EntityDamageEvent.DamageCause.KILL) return;
        e.setCancelled(true);
        p.setFireTicks(0);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSuperHunger(org.bukkit.event.entity.FoodLevelChangeEvent e) {
        if (e.getEntity() instanceof Player p && superWorks(p) && e.getFoodLevel() < p.getFoodLevel()) e.setCancelled(true);
    }

    // --- Fly power and light power -------------------------------------------------------------------------

    private void applyFly(Player p) {
        if (p.getGameMode() == org.bukkit.GameMode.CREATIVE || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) return;
        boolean on = has("fly", p);
        p.setAllowFlight(on);
        if (!on) p.setFlying(false);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(this, () -> applyFly(p), 5L);
        // The permission used to be skaper.craft (old plugin name): move it over in LuckPerms once.
        if (p.hasPermission("skaper.craft") && !p.hasPermission(CRAFT_PERM) && Bukkit.getPluginManager().getPlugin("LuckPerms") != null) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getName() + " permission set " + CRAFT_PERM + " true");
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "lp user " + p.getName() + " permission unset skaper.craft");
        }
    }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) { Bukkit.getScheduler().runTaskLater(this, () -> applyFly(e.getPlayer()), 5L); }

    @EventHandler
    public void onMode(PlayerGameModeChangeEvent e) { Bukkit.getScheduler().runTaskLater(this, () -> applyFly(e.getPlayer()), 2L); }

    private final Map<UUID, Long> noLantern = new java.util.HashMap<>();

    /** Places a lantern behind the player where block light is 0 and the sky is (mostly) hidden: caves and indoors. */
    private void lys() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!has("light", p) || p.isDead() || p.isFlying() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) continue;
            Block feet = p.getLocation().getBlock();
            if (feet.getLightFromBlocks() > 0 || feet.getLightFromSky() >= 8) continue;
            Block spot = null;
            BlockFace back = p.getFacing().getOppositeFace();
            for (Block c : new Block[]{feet.getRelative(back), feet.getRelative(back).getRelative(back), feet.getRelative(rightOf(back)), feet.getRelative(rightOf(back).getOppositeFace())}) {
                if (c.isEmpty() && !c.isLiquid() && c.getRelative(BlockFace.DOWN).getType().isSolid() && c.getRelative(BlockFace.DOWN).getType().isOccluding()) { spot = c; break; }
            }
            if (spot == null) continue;
            boolean free = p.getGameMode() == org.bukkit.GameMode.CREATIVE;
            if (!free && !p.getInventory().containsAtLeast(new ItemStack(Material.LANTERN), 1)) {
                long now = System.currentTimeMillis();
                if (noLantern.getOrDefault(p.getUniqueId(), 0L) < now) {
                    p.sendMessage("Light power: out of lanterns (craft more, or /lightpoweroff).");
                    noLantern.put(p.getUniqueId(), now + 60_000);
                }
                continue;
            }
            if (!free) p.getInventory().removeItem(new ItemStack(Material.LANTERN, 1));
            spot.setType(Material.LANTERN);
        }
    }

    private static BlockFace rightOf(BlockFace f) {
        return switch (f) { case NORTH -> BlockFace.EAST; case EAST -> BlockFace.SOUTH; case SOUTH -> BlockFace.WEST; default -> BlockFace.NORTH; };
    }

    private void magnet() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!has("magnet", p) || p.isSneaking() || p.isDead()) continue;
            for (Entity en : p.getNearbyEntities(8, 4, 8)) {
                if (!(en instanceof Item it) || it.getPickupDelay() > 0) continue;
                if (p.getUniqueId().equals(it.getThrower())) continue;   // things you threw away stay put
                it.teleport(p.getLocation());
            }
        }
    }

    // --- Gravestone ---------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        List<ItemStack> items = new ArrayList<>();
        for (ItemStack i : e.getDrops()) if (i != null && !i.getType().isAir()) items.add(i);
        if (items.isEmpty()) return;
        Player p = e.getPlayer();
        Location l = p.getLocation();
        World w = l.getWorld();
        int y = Math.max(w.getMinHeight() + 1, Math.min(l.getBlockY(), w.getMaxHeight() - 4));
        Block b = w.getBlockAt(l.getBlockX(), y, l.getBlockZ());
        for (int i = 0; i < 30 && !(b.isEmpty() || b.isLiquid() || b.isReplaceable()); i++) b = b.getRelative(BlockFace.UP);
        int barrels = (items.size() + 26) / 27;
        Block top = b;
        int idx = 0;
        for (int n = 0; n < barrels; n++) {
            Block cell = b.getRelative(0, n, 0);
            cell.setType(Material.BARREL);
            if (cell.getState() instanceof Container c) {
                c.customName(Component.text("Gravestone: " + p.getName()));
                c.update();
                Inventory inv = ((Container) cell.getState()).getInventory();
                for (int k = 0; k < 27 && idx < items.size(); k++) inv.addItem(items.get(idx++));
            }
            top = cell;
        }
        e.getDrops().clear();
        data.set("graves." + p.getUniqueId(), w.getName() + " " + top.getX() + " " + top.getY() + " " + top.getZ());
        save();
        p.sendMessage("Your things are in a gravestone (barrel) at " + b.getX() + " " + b.getY() + " " + b.getZ()
                + " in " + w.getName() + ". Type /grave to go there.");
    }

    private void grave(Player p) {
        String g = data.getString("graves." + p.getUniqueId());
        if (g != null) {
            String[] s = g.split(" ");
            World w = Bukkit.getWorld(s[0]);
            if (w != null) {
                p.teleport(new Location(w, Integer.parseInt(s[1]) + 0.5, Integer.parseInt(s[2]) + 1, Integer.parseInt(s[3]) + 0.5));
                p.sendMessage("Here is your gravestone.");
                return;
            }
        }
        Location d = p.getLastDeathLocation();
        if (d == null || d.getWorld() == null || d.getY() < d.getWorld().getMinHeight()) {
            p.sendMessage("No grave found.");
            return;
        }
        p.teleport(d.add(0.5, 0, 0.5));
        p.sendMessage("This is where you last died.");
    }

    // --- Elevator -------------------------------------------------------------------------------------

    private void elevatorCmd(CommandSourceStack src, String[] args, boolean up) {
        if (src.getExecutor() == null) { src.getSender().sendMessage("Must be run by a player"); return; }
        Entity p = src.getExecutor();
        int n;
        try { n = args.length > 0 ? Integer.parseInt(args[0]) : -1; } catch (NumberFormatException e) { n = -1; }
        if (n < 3 || n > 400) { p.sendMessage("Usage: /" + (up ? "elevator" : "elevatordown") + " <height 3–400>, e.g. /" + (up ? "elevator" : "elevatordown") + " 50"); return; }
        buildElevator(p, n, up);
    }

    /**
     * Two bubble-column shafts in front of the player, glass column between them right in front of the player:
     * left (seen from the player) soul sand = up, right magma = down. Doorways with ladders to the left and right
     * of the player at both ends keep the water in. Landing with floor at both ends.
     * Local frame: lat (+ = right), fwd (+ = forward, 0 = the player's row), absolute y.
     */
    private void buildElevator(Entity p, int n, boolean up) {
        World w = p.getWorld();
        Block base = p.getLocation().getBlock();
        BlockFace f = p.getFacing();
        int fx = f.getModX(), fz = f.getModZ();
        int rx = -fz, rz = fx;   // right of the facing direction
        int py = base.getY();
        int bot, top;
        if (up) {
            bot = py;
            top = Math.min(py + n, w.getMaxHeight() - 4);
        } else {
            top = py;
            bot = py - n;
            while (bot < top - 2 && (bot - 2 < w.getMinHeight() || bedrockNear(w, base, fx, fz, rx, rz, bot))) bot++;
            if (top - bot < 3) { p.sendMessage("Bedrock is right below you, no room for an elevator."); return; }
        }
        java.util.function.BiFunction<int[], Integer, Block> at = (lf, y) ->
                w.getBlockAt(base.getX() + lf[0] * rx + lf[1] * fx, y, base.getZ() + lf[0] * rz + lf[1] * fz);
        // 0) Is there water or lava around a landing? Then it gets glass walls and roof (dry air pocket).
        java.util.Map<Integer, Boolean> wet = new java.util.HashMap<>();
        for (int end : new int[]{bot, top}) {
            boolean liquid = false;
            for (int y = end - 1; y <= end + 3 && !liquid; y++)
                for (int lat = -3; lat <= 3 && !liquid; lat++)
                    for (int fwd = -3; fwd <= 0 && !liquid; fwd++)
                        liquid = isLiquid(at.apply(new int[]{lat, fwd}, y));
            wet.put(end, liquid);
        }
        // 1) Glass shell: lat -2..2, fwd 0..2, from bot-2 to top+2
        for (int y = bot - 2; y <= top + 2; y++)
            for (int lat = -2; lat <= 2; lat++)
                for (int fwd = 0; fwd <= 2; fwd++)
                    at.apply(new int[]{lat, fwd}, y).setType(Material.GLASS, false);
        // 2) Landings at both ends: floor at end-1, air at end..end+2, lat -2..2, fwd -2..0 (fwd 0 only the centre)
        for (int end : new int[]{bot, top}) {
            for (int lat = -2; lat <= 2; lat++)
                for (int fwd = -2; fwd <= 0; fwd++) {
                    if (fwd == 0 && Math.abs(lat) == 2) continue;      // ladder supports stay glass
                    at.apply(new int[]{lat, fwd}, end - 1).setType(Material.SMOOTH_STONE, false);
                    for (int y = end; y <= end + 2; y++)
                        if (!(fwd == 0 && lat != 0 && y == end + 2)) at.apply(new int[]{lat, fwd}, y).setType(Material.AIR, false);
                }
        }
        // 2b) Air pocket: glass walls (lat ±3, fwd -3), roof (end+3) and floor edge around wet landings
        int pockets = 0;
        for (int end : new int[]{bot, top}) {
            if (!wet.get(end)) continue;
            pockets++;
            for (int y = end - 1; y <= end + 3; y++)
                for (int lat = -3; lat <= 3; lat++)
                    for (int fwd = -3; fwd <= 2; fwd++) {
                        boolean edge = Math.abs(lat) == 3 || fwd == -3 || y == end + 3 || y == end - 1;
                        boolean shell = fwd >= 0 && Math.abs(lat) <= 2 && y <= top + 2;   // already built (shafts, ladders, glass)
                        boolean floor = y == end - 1 && fwd <= 0 && Math.abs(lat) <= 2;
                        if (edge && !shell && !floor) at.apply(new int[]{lat, fwd}, y).setType(Material.GLASS, false);
                    }
            // Exit in the back wall: ladders keep water and lava out, but you can walk through them
            for (int y = end; y <= end + 1; y++) {
                Block b = at.apply(new int[]{0, -3}, y);
                b.setType(Material.LADDER, false);
                Ladder l = (Ladder) b.getBlockData();
                l.setFacing(faceOf(rx, rz));   // hangs on the glass to the left
                l.setWaterlogged(false);
                b.setBlockData(l, false);
            }
        }
        // 3) Shafts: soul sand (left, up) and magma (right, down) at bot-1, bubble columns bot..top+1
        for (int lat : new int[]{-1, 1}) {
            boolean down = lat == 1;
            at.apply(new int[]{lat, 1}, bot - 1).setType(down ? Material.MAGMA_BLOCK : Material.SOUL_SAND, false);
            for (int y = bot; y <= top + 1; y++) {
                Block b = at.apply(new int[]{lat, 1}, y);
                b.setType(Material.BUBBLE_COLUMN, false);
                BubbleColumn bc = (BubbleColumn) b.getBlockData();
                bc.setDrag(down);
                b.setBlockData(bc, false);
            }
            // 4) Doorways with ladders at both ends (feet and head height), hung on the glass at lat ±2
            BlockFace ladderFacing = lat > 0 ? faceOf(-rx, -rz) : faceOf(rx, rz);
            for (int end : new int[]{bot, top})
                for (int y = end; y <= end + 1; y++) {
                    Block b = at.apply(new int[]{lat, 0}, y);
                    b.setType(Material.LADDER, false);
                    Ladder l = (Ladder) b.getBlockData();
                    l.setFacing(ladderFacing);
                    l.setWaterlogged(false);
                    b.setBlockData(l, false);
                }
        }
        int h = top - bot;
        p.sendMessage("Elevator built: " + h + " blocks " + (up ? "up" : "down")
                + ((!up && h < n) ? " (stopped above bedrock)" : "") + ". The left shaft (soul sand) takes you up, the right one (magma) takes you down."
                + (pockets > 0 ? " Found water or lava, so " + (pockets == 2 ? "both ends" : "one end") + " got a dry air pocket of glass with an exit through the ladder in the back wall." : ""));
    }

    private static boolean isLiquid(Block b) {
        Material m = b.getType();
        if (m == Material.WATER || m == Material.LAVA || m == Material.BUBBLE_COLUMN || m == Material.KELP
                || m == Material.KELP_PLANT || m == Material.SEAGRASS || m == Material.TALL_SEAGRASS) return true;
        return b.getBlockData() instanceof org.bukkit.block.data.Waterlogged wl && wl.isWaterlogged();
    }

    private static boolean bedrockNear(World w, Block base, int fx, int fz, int rx, int rz, int bot) {
        for (int y = bot - 2; y <= bot + 2; y++)
            for (int lat = -2; lat <= 2; lat++)
                for (int fwd = -2; fwd <= 2; fwd++)
                    if (w.getBlockAt(base.getX() + lat * rx + fwd * fx, y, base.getZ() + lat * rz + fwd * fz).getType() == Material.BEDROCK)
                        return true;
        return false;
    }

    private static BlockFace faceOf(int dx, int dz) {
        for (BlockFace bf : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST})
            if (bf.getModX() == dx && bf.getModZ() == dz) return bf;
        return BlockFace.NORTH;
    }

    // --- Sleep ----------------------------------------------------------------------------------------

    @EventHandler
    public void onWorldLoad(WorldLoadEvent e) {
        sleepRule(e.getWorld());
    }

    @SuppressWarnings("deprecation")
    private void sleepRule(World w) {
        w.setGameRule(GameRules.PLAYERS_SLEEPING_PERCENTAGE, 1);
    }
}
