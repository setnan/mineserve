package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Barrel;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Directional;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Magic storage barrel. /storagebarrel [name] places a barrel in front of you. Right-click opens a sorted view
 * with two tabs: Mine (private, the same in every barrel, the default) and Shared (shared by everyone, per barrel name).
 * Click an item in your inventory to put it in, click a stored item to take it out (left = stack, right = 1,
 * shift = 8). Hoppers into the barrel fill the shared store. /fetch <item> [amount] takes from the shared store
 * of a barrel within 6 blocks, otherwise from your own; /fetch mine|shared ... chooses.
 * Items are keyed by their full serialized form (amount 1), so enchanted and named items stay intact.
 * Data from the old Skaper plugin (lager.yml, store ids "felles:"/"mitt:", barrel key skaper:lager) is converted.
 */
final class Storage implements Listener {
    private static final int PAGE = 45;
    private final JavaPlugin plugin;
    private final NamespacedKey key;
    private final File file;
    private final Map<String, Map<String, Long>> stores = new HashMap<>();   // "shared:<name>" | "mine:<uuid>" -> item -> count
    private final Map<String, ItemStack> decoded = new HashMap<>();
    private final List<String> barrels = new ArrayList<>();                    // "world x y z name"
    private static final String SHARED = "shared";
    private static final NamespacedKey OLD_KEY = new NamespacedKey("skaper", "lager");
    private boolean dirty;
    java.util.function.BiConsumer<Player, Block> onUse;                     // a player placed or opened a barrel: their helper remembers it

    Storage(JavaPlugin plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "storage");
        this.file = new File(plugin.getDataFolder(), "storage.yml");
        load();
        Bukkit.getScheduler().runTaskTimer(plugin, this::sweep, 40L, 10L);
        Bukkit.getScheduler().runTaskTimer(plugin, () -> { if (dirty) save(); }, 200L, 200L);
    }

    // --- Storage ------------------------------------------------------------------------------------

    /** Old Norwegian names: store "felles:x" -> "shared:x", "mitt:u" -> "mine:u", barrel name "felles" -> "shared". */
    private static String newStoreId(String id) {
        if (id.startsWith("felles:")) return "shared:" + newBarrelName(id.substring(7));
        if (id.startsWith("mitt:")) return "mine:" + id.substring(5);
        return id;
    }

    private static String newBarrelName(String name) {
        return name.equals("felles") ? SHARED : name;
    }

    private void load() {
        File source = file.exists() ? file : new File(plugin.getDataFolder(), "lager.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(source);
        ConfigurationSection s = y.getConfigurationSection("storage");
        if (s == null) s = y.getConfigurationSection("lager");
        if (s != null)
            for (String store : s.getKeys(false)) {
                Map<String, Long> m = store(newStoreId(store.replace('~', ':')));
                ConfigurationSection items = s.getConfigurationSection(store);
                for (String k : items.getKeys(false))
                    m.merge(k.replace('-', '/').replace('_', '+').replace('~', '='), items.getLong(k), Long::sum);
            }
        List<String> list = y.isList("barrels") ? y.getStringList("barrels") : y.getStringList("tonner");
        for (String b : list) {
            String[] a = b.split(" ", 5);
            barrels.add(a.length == 5 ? a[0] + " " + a[1] + " " + a[2] + " " + a[3] + " " + newBarrelName(a[4]) : b);
        }
        if (source != file) dirty = true;      // write storage.yml with the new names on the next save
    }

    void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (var e : stores.entrySet())
            for (var i : e.getValue().entrySet())
                if (i.getValue() > 0)   // base64 has / + = which YAML keys and paths dislike
                    y.set("storage." + e.getKey().replace(':', '~') + "." + i.getKey().replace('/', '-').replace('+', '_').replace('=', '~'), i.getValue());
        y.set("barrels", barrels);
        try { y.save(file); dirty = false; } catch (IOException ex) { plugin.getLogger().warning("Could not save storage.yml: " + ex.getMessage()); }
    }

    private Map<String, Long> store(String id) {
        return stores.computeIfAbsent(id, k -> new LinkedHashMap<>());
    }

    private static String keyOf(ItemStack it) {
        ItemStack one = it.clone();
        one.setAmount(1);
        return Base64.getEncoder().encodeToString(one.serializeAsBytes());
    }

    private ItemStack itemOf(String k) {
        return decoded.computeIfAbsent(k, x -> ItemStack.deserializeBytes(Base64.getDecoder().decode(x))).clone();
    }

    private void put(String store, ItemStack it) {
        if (it == null || it.getType().isAir()) return;
        store(store).merge(keyOf(it), (long) it.getAmount(), Long::sum);
        dirty = true;
    }

    /** Gives up to n of item k from the store to the player; what doesn't fit stays in the store. Returns the number given. */
    private long take(Player p, String store, String k, long n) {
        Map<String, Long> m = store(store);
        long have = m.getOrDefault(k, 0L);
        long want = Math.min(n, have);
        if (want <= 0) return 0;
        ItemStack proto = itemOf(k);
        long given = 0;
        while (given < want) {
            int chunk = (int) Math.min(proto.getMaxStackSize(), want - given);
            ItemStack s = proto.clone();
            s.setAmount(chunk);
            var left = p.getInventory().addItem(s);
            int back = left.values().stream().mapToInt(ItemStack::getAmount).sum();
            given += chunk - back;
            if (back > 0) break;
        }
        if (have - given <= 0) m.remove(k); else m.put(k, have - given);
        dirty = true;
        return given;
    }

    // --- Barrels ------------------------------------------------------------------------------------

    private String nameOf(Block b) {
        if (b == null || b.getType() != Material.BARREL || !(b.getState() instanceof Barrel br)) return null;
        var pdc = br.getPersistentDataContainer();
        String name = pdc.get(key, PersistentDataType.STRING);
        if (name == null && (name = pdc.get(OLD_KEY, PersistentDataType.STRING)) != null) {   // barrel from the old plugin
            name = newBarrelName(name);
            pdc.remove(OLD_KEY);
            pdc.set(key, PersistentDataType.STRING, name);
            br.customName(Component.text("Storage: " + name));
            br.update();
        }
        return name;
    }

    private String nearbyBarrel(Player p) {
        Location l = p.getLocation();
        for (String s : barrels) {
            String[] a = s.split(" ", 5);
            if (!a[0].equals(l.getWorld().getName())) continue;
            double dx = Integer.parseInt(a[1]) + 0.5 - l.getX(), dy = Integer.parseInt(a[2]) + 0.5 - l.getY(), dz = Integer.parseInt(a[3]) + 0.5 - l.getZ();
            if (dx * dx + dy * dy + dz * dz <= 36) return a[4];
        }
        return null;
    }

    /** Is there a storage barrel in this world, its nether or its end? Your own store is the same in all of them. */
    boolean barrelIn(World w) {
        if (w == null) return false;
        String realm = w.getName().replaceFirst("_(nether|the_end)$", "");
        for (String s : barrels) if (s.split(" ", 2)[0].replaceFirst("_(nether|the_end)$", "").equals(realm)) return true;
        return false;
    }

    /** Is there a storage barrel within r blocks of l? */
    boolean barrelNear(Location l, int r) {
        if (l == null || l.getWorld() == null) return false;
        for (String s : barrels) {
            String[] a = s.split(" ", 5);
            if (!a[0].equals(l.getWorld().getName())) continue;
            double dx = Integer.parseInt(a[1]) + 0.5 - l.getX(), dy = Integer.parseInt(a[2]) + 0.5 - l.getY(), dz = Integer.parseInt(a[3]) + 0.5 - l.getZ();
            if (dx * dx + dy * dy + dz * dz <= (double) r * r) return true;
        }
        return false;
    }

    /** Is this block a magic storage barrel? */
    boolean isBarrel(Block b) { return nameOf(b) != null; }

    /** Takes up to n of the first item in the player's own store that ok accepts (plain items only, nothing named
     *  or enchanted), or null when there is none. */
    ItemStack takeMine(UUID id, java.util.function.Predicate<Material> ok, int n) {
        Map<String, Long> st = store("mine:" + id);
        for (Map.Entry<String, Long> e : st.entrySet()) {
            ItemStack it = itemOf(e.getKey());
            if (!ok.test(it.getType()) || it.hasItemMeta() && !it.getItemMeta().equals(new ItemStack(it.getType()).getItemMeta())) continue;
            int got = (int) Math.min(n, Math.min(e.getValue(), it.getMaxStackSize()));
            if (got <= 0) continue;
            if (e.getValue() - got <= 0) st.remove(e.getKey()); else e.setValue(e.getValue() - got);
            dirty = true;
            it.setAmount(got);
            return it;
        }
        return null;
    }

    /** How many of these kinds of item the player has in their own store. */
    long countMine(UUID id, java.util.Set<Material> kinds) {
        long n = 0;
        for (Map.Entry<String, Long> e : store("mine:" + id).entrySet()) if (kinds.contains(itemOf(e.getKey()).getType())) n += e.getValue();
        return n;
    }

    /** Puts these things in the player's own store (the Mine tab), and returns how many things that was. */
    int putMine(UUID id, Collection<ItemStack> items) {
        int n = 0;
        for (ItemStack it : items) if (it != null && !it.getType().isAir()) { n += it.getAmount(); put("mine:" + id, it); }
        return n;
    }

    /** Puts the whole inventory in the player's own store (the Mine tab), and returns how many things that was. */
    int putMine(UUID id, Inventory from) {
        int n = 0;
        for (int i = 0; i < from.getSize(); i++) {
            ItemStack it = from.getItem(i);
            if (it == null || it.getType().isAir()) continue;
            n += it.getAmount();
            put("mine:" + id, it);
            from.setItem(i, null);
        }
        return n;
    }

    void place(org.bukkit.entity.Entity p, String name) {
        Block b = p.getLocation().getBlock().getRelative(p.getFacing());
        if (!(b.isEmpty() || b.isReplaceable())) b = b.getRelative(BlockFace.UP);
        if (!(b.isEmpty() || b.isReplaceable())) { p.sendMessage("No room in front of you. Stand in front of a free block."); return; }
        b.setType(Material.BARREL);
        if (b.getBlockData() instanceof Directional d) { d.setFacing(BlockFace.UP); b.setBlockData(d); }
        Barrel br = (Barrel) b.getState();
        br.getPersistentDataContainer().set(key, PersistentDataType.STRING, name);
        br.customName(Component.text("Storage: " + name));
        br.update();
        barrels.add(b.getWorld().getName() + " " + b.getX() + " " + b.getY() + " " + b.getZ() + " " + name);
        dirty = true;
        if (onUse != null && p instanceof Player pl) onUse.accept(pl, b);
        p.sendMessage("Storage barrel \"" + name + "\" placed. Right-click to put things in and take them out. The Shared tab is for everyone, Mine is just yours.");
    }

    /** Moves anything hoppers put into storage barrels into the shared store, and forgets barrels that are gone. */
    private void sweep() {
        barrels.removeIf(s -> {
            String[] a = s.split(" ", 5);
            World w = Bukkit.getWorld(a[0]);
            if (w == null) return false;
            int x = Integer.parseInt(a[1]), y = Integer.parseInt(a[2]), z = Integer.parseInt(a[3]);
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return false;
            Block b = w.getBlockAt(x, y, z);
            if (!a[4].equals(nameOf(b))) { dirty = true; return true; }
            Inventory inv = ((Barrel) b.getState()).getInventory();
            for (int i = 0; i < inv.getSize(); i++) {
                ItemStack it = inv.getItem(i);
                if (it != null && !it.getType().isAir()) { put("shared:" + a[4], it); inv.setItem(i, null); }
            }
            return false;
        });
    }

    // --- View ---------------------------------------------------------------------------------------

    private static final class View implements InventoryHolder {
        final String barrel;
        boolean mine;
        int page;
        boolean byCount;
        List<String> keys = List.of();
        Inventory inv;

        View(String barrel) { this.barrel = barrel; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private String storeId(View v, Player p) {
        return v.mine ? "mine:" + p.getUniqueId() : "shared:" + v.barrel;
    }

    private void open(Player p, String barrel) {
        open(p, barrel, true);                                              // your own tab first; Shared is one click away
    }

    private void open(Player p, String barrel, boolean mine) {
        View v = new View(barrel);
        v.mine = mine;
        v.inv = Bukkit.createInventory(v, 54, Component.text("Storage"));
        render(v, p);
        p.openInventory(v.inv);
    }

    private static ItemStack button(Material m, String name, NamedTextColor c, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(Component.text(name, c).decoration(TextDecoration.ITALIC, false));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(Component.text(s, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void render(View v, Player p) {
        Map<String, Long> m = store(storeId(v, p));
        List<String> keys = new ArrayList<>(m.keySet());
        Comparator<String> byName = Comparator.comparing(k -> itemOf(k).getType().getKey().getKey());
        keys.sort(v.byCount ? Comparator.<String>comparingLong(k -> -m.get(k)).thenComparing(byName) : byName);
        v.keys = keys;
        int pages = Math.max(1, (keys.size() + PAGE - 1) / PAGE);
        v.page = Math.min(v.page, pages - 1);
        v.inv.clear();
        for (int i = 0; i < PAGE && v.page * PAGE + i < keys.size(); i++) {
            String k = keys.get(v.page * PAGE + i);
            ItemStack shown = itemOf(k);
            long n = m.get(k);
            shown.setAmount((int) Math.max(1, Math.min(n, shown.getMaxStackSize())));
            ItemMeta meta = shown.getItemMeta();
            List<Component> lore = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
            lore.add(Component.text("Amount: " + String.format(Locale.ROOT, "%,d", n), NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
            lore.add(Component.text("Left-click: stack · Right-click: 1 · Shift: 8", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            shown.setItemMeta(meta);
            v.inv.setItem(i, shown);
        }
        long total = m.values().stream().mapToLong(Long::longValue).sum();
        v.inv.setItem(45, button(Material.ARROW, "◀ Previous page", NamedTextColor.WHITE, "Page " + (v.page + 1) + " of " + pages));
        v.inv.setItem(46, button(v.mine ? Material.CHEST : Material.ENDER_CHEST, v.mine ? "Shared \"" + v.barrel + "\"" : "▶ Shared \"" + v.barrel + "\" (open)",
                v.mine ? NamedTextColor.GRAY : NamedTextColor.GREEN, "Shared with all players"));
        v.inv.setItem(47, button(v.mine ? Material.ENDER_CHEST : Material.CHEST, v.mine ? "▶ My storage (open)" : "My storage",
                v.mine ? NamedTextColor.GREEN : NamedTextColor.GRAY, "Just yours, the same in every storage barrel"));
        v.inv.setItem(49, button(Material.HOPPER, "Put everything in", NamedTextColor.AQUA, "Your whole inventory except the hotbar", "goes into the open storage"));
        v.inv.setItem(51, button(Material.COMPARATOR, "Sort: " + (v.byCount ? "amount" : "name"), NamedTextColor.WHITE, "Click to switch"));
        v.inv.setItem(52, button(Material.PAPER, keys.size() + " kinds, " + String.format(Locale.ROOT, "%,d", total) + " items", NamedTextColor.WHITE,
                "Click an item in your inventory to put it in", "Shift-click puts in all of the same kind"));
        v.inv.setItem(53, button(Material.ARROW, "Next page ▶", NamedTextColor.WHITE, "Page " + (v.page + 1) + " of " + pages));
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        String name = nameOf(e.getClickedBlock());
        if (name == null || e.getPlayer().isSneaking() && e.getItem() != null) return;   // sneak + item = place blocks against it
        e.setCancelled(true);
        if (onUse != null) onUse.accept(e.getPlayer(), e.getClickedBlock());
        open(e.getPlayer(), name);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View v) || !(e.getWhoClicked() instanceof Player p)) return;
        e.setCancelled(true);
        String store = storeId(v, p);
        Inventory clicked = e.getClickedInventory();
        if (clicked == null) return;
        if (clicked != v.inv) {                      // player's own inventory: put in
            ItemStack it = e.getCurrentItem();
            if (it == null || it.getType().isAir()) return;
            if (e.isShiftClick()) {
                for (int i = 0; i < p.getInventory().getStorageContents().length; i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (s != null && s.isSimilar(it)) { put(store, s); p.getInventory().setItem(i, null); }
                }
            } else {
                put(store, it);
                clicked.setItem(e.getSlot(), null);
            }
        } else {
            int slot = e.getRawSlot();
            if (slot < PAGE) {
                int idx = v.page * PAGE + slot;
                if (idx >= v.keys.size()) return;
                String k = v.keys.get(idx);
                long n = e.isShiftClick() ? 8 : e.getClick() == ClickType.RIGHT ? 1 : itemOf(k).getMaxStackSize();
                if (take(p, store, k, n) == 0) p.sendMessage("No room in your inventory.");
            } else if (slot == 45) {
                v.page = Math.max(0, v.page - 1);
            } else if (slot == 53) {
                v.page++;
            } else if (slot == 46) {
                v.mine = false; v.page = 0;
            } else if (slot == 47) {
                v.mine = true; v.page = 0;
            } else if (slot == 49) {
                for (int i = 9; i < 36; i++) {
                    ItemStack s = p.getInventory().getItem(i);
                    if (s != null && !s.getType().isAir()) { put(store, s); p.getInventory().setItem(i, null); }
                }
            } else if (slot == 51) {
                v.byCount = !v.byCount;
            }
        }
        render(v, p);
    }

    // --- Commands -----------------------------------------------------------------------------------

    void register(Commands r) {
        r.register("storagebarrel", "Places a magic storage barrel in front of you: /storagebarrel [name]", (BasicCommand) (src, args) -> {
            var p = src.getExecutor();
            if (p == null) { src.getSender().sendMessage("Must be run by a player"); return; }
            String name = args.length > 0 ? args[0].toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "") : SHARED;
            place(p, name.isEmpty() ? SHARED : name);
        });
        r.register("dump", "Empties your inventory (except the hotbar) into the shared storage: the barrel next to you, else \"shared\"", List.of("deposit"),
                (BasicCommand) (src, args) -> dump(src, args.length > 0 && args[0].equalsIgnoreCase("mine")));
        // /pc = personal chest, /cc = common chest: without arguments the view opens anywhere, with arguments they fetch
        r.register("pc", "Opens your own storage anywhere, /pc <item> [amount] fetches from it", (BasicCommand) (src, args) -> {
            if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
            if (args.length == 0) { String b = nearbyBarrel(p); open(p, b == null ? SHARED : b, true); return; }
            String[] a = new String[args.length + 1]; a[0] = "mine"; System.arraycopy(args, 0, a, 1, args.length);
            fetch(src, a);
        });
        r.register("cc", "Opens the shared storage (the barrel next to you, else \"shared\") anywhere, /cc <item> [amount] fetches from it", (BasicCommand) (src, args) -> {
            if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
            if (args.length == 0) { String b = nearbyBarrel(p); open(p, b == null ? SHARED : b, false); return; }
            String[] a = new String[args.length + 1]; a[0] = SHARED; System.arraycopy(args, 0, a, 1, args.length);
            fetch(src, a);
        });
        r.register("dumpmine", "Empties your inventory (except the hotbar) into your own storage", (BasicCommand) (src, args) -> dump(src, true));
        r.register("fetch", "Fetches from storage: /fetch [mine|shared] <item> [amount]", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) { fetch(src, args); }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                if (!(src.getExecutor() instanceof Player p)) return List.of();
                List<String> out = new ArrayList<>();
                boolean first = args.length <= 1;
                if (first) { out.add("mine"); out.add(SHARED); }
                String bar = nearbyBarrel(p);
                for (String store : new String[]{"mine:" + p.getUniqueId(), bar == null ? null : "shared:" + bar})
                    if (store != null)
                        for (String k : store(store).keySet()) {
                            String id = itemOf(k).getType().getKey().getKey();
                            if (!out.contains(id)) out.add(id);
                        }
                String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
                out.removeIf(s -> !s.startsWith(last));
                return out;
            }
        });
    }

    /** Slots 9-35 into your own store (mine) or the shared store of a barrel within 6 blocks, else the store "shared". */
    private void dump(CommandSourceStack src, boolean mine) {
        if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
        String bar = nearbyBarrel(p);
        if (bar == null) bar = SHARED;
        String store = mine ? "mine:" + p.getUniqueId() : "shared:" + bar;
        long n = 0;
        int stacks = 0;
        for (int i = 9; i < 36; i++) {
            ItemStack it = p.getInventory().getItem(i);
            if (it == null || it.getType().isAir()) continue;
            n += it.getAmount(); stacks++;
            put(store, it);
            p.getInventory().setItem(i, null);
        }
        String where = mine ? "your storage" : "the shared storage \"" + bar + "\"";
        p.sendMessage(n == 0 ? "Your inventory is already empty (the hotbar is left alone)."
                : "Put " + n + " items (" + stacks + " stacks) in " + where + ". The hotbar is left alone." + (mine ? "" : " /dumpmine uses your own storage."));
    }

    private void fetch(CommandSourceStack src, String[] args) {
        if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
        List<String> a = new ArrayList<>(List.of(args));
        String bar = nearbyBarrel(p);
        String store;
        if (!a.isEmpty() && a.get(0).equalsIgnoreCase("mine")) { a.remove(0); store = "mine:" + p.getUniqueId(); }
        else if (!a.isEmpty() && a.get(0).equalsIgnoreCase(SHARED)) {
            a.remove(0);
            if (bar == null) bar = SHARED;
            store = "shared:" + bar;
        } else store = bar != null ? "shared:" + bar : "mine:" + p.getUniqueId();
        if (a.isEmpty()) { p.sendMessage("Usage: /fetch [mine|shared] <item> [amount], e.g. /fetch diamond 32"); return; }
        long n = 64;
        if (a.size() > 1) {
            try { n = Long.parseLong(a.get(a.size() - 1)); a.remove(a.size() - 1); } catch (NumberFormatException ignored) { }
        }
        String q = String.join("_", a).toLowerCase(Locale.ROOT).replace("minecraft:", "");
        String best = null;
        for (String k : store(store).keySet()) {
            String id = itemOf(k).getType().getKey().getKey();
            if (id.equals(q)) { best = k; break; }
            if (best == null && id.contains(q)) best = k;
        }
        String where = store.startsWith("mine") ? "your storage" : "the shared storage \"" + bar + "\"";
        if (best == null) { p.sendMessage("Could not find \"" + q + "\" in " + where + ". Press Tab for suggestions."); return; }
        long got = take(p, store, best, n);
        p.sendMessage(got == 0 ? "No room in your inventory." : "Fetched " + got + " " + itemOf(best).getType().getKey().getKey() + " from " + where + ".");
    }
}
