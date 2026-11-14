package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.entity.BlockDisplay;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.inventory.meta.components.EquippableComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

/**
 * X-Ray Goggles: worn on the head, ores, ancient debris, chests and spawners within 12 blocks get a coloured
 * outline that shows through rock. The outlines are glowing BlockDisplays that only the wearer can see.
 * The item is paper with an equippable component (head slot, own model and a blue screen tint from the pack,
 * xray_pack.py). Recipe: iron, redstone, iron / tinted glass, diamond, tinted glass.
 */
final class XrayGoggles implements Listener {
    static final int RADIUS = 12, MAX = 400;
    private final JavaPlugin plugin;
    private final NamespacedKey tag, recipeKey;
    private final Map<UUID, Map<Block, BlockDisplay>> shown = new HashMap<>();
    private static final Map<Material, Color> COLORS = new HashMap<>();

    private static void color(Color c, Material... ms) {
        for (Material m : ms) COLORS.put(m, c);
    }

    static {
        color(Color.fromRGB(0x40E0F0), Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE);
        color(Color.fromRGB(0x30E060), Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE);
        color(Color.fromRGB(0xFFD020), Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE, Material.NETHER_GOLD_ORE);
        color(Color.fromRGB(0xE8C8A8), Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE);
        color(Color.fromRGB(0xF07830), Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE);
        color(Color.fromRGB(0xFF2020), Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE);
        color(Color.fromRGB(0x3050FF), Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE);
        color(Color.fromRGB(0x505050), Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE);
        color(Color.fromRGB(0xFFFFFF), Material.NETHER_QUARTZ_ORE);
        color(Color.fromRGB(0xA05030), Material.ANCIENT_DEBRIS);
        color(Color.fromRGB(0xFF9F40), Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL);
        color(Color.fromRGB(0xC040FF), Material.SPAWNER, Material.TRIAL_SPAWNER, Material.VAULT);
    }

    XrayGoggles(JavaPlugin plugin) {
        this.plugin = plugin;
        tag = new NamespacedKey(plugin, "xray_goggles");
        recipeKey = new NamespacedKey(plugin, "xray_goggles");
        ShapedRecipe r = new ShapedRecipe(recipeKey, item());
        r.shape("IRI", "TDT");
        r.setIngredient('I', Material.IRON_INGOT);
        r.setIngredient('R', Material.REDSTONE);
        r.setIngredient('T', Material.TINTED_GLASS);
        r.setIngredient('D', Material.DIAMOND);
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 10L);
    }

    ItemStack item() {
        ItemStack it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("X-Ray Goggles", TextColor.color(0x60E0FF)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Wear them: ores, chests and spawners", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("within " + RADIUS + " blocks glow through rock.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Diamond = cyan, emerald = green, gold = yellow, iron = beige, coal = grey …", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setMaxStackSize(1);
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        EquippableComponent eq = m.getEquippable();
        eq.setSlot(EquipmentSlot.HEAD);
        eq.setModel(new NamespacedKey("supermann", "xray_goggles"));
        eq.setCameraOverlay(new NamespacedKey("supermann", "misc/xray"));
        eq.setEquipSound(Sound.ITEM_ARMOR_EQUIP_GENERIC);
        eq.setSwappable(true);
        m.setEquippable(eq);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("xray_goggles"));
        m.setCustomModelDataComponent(c);
        it.setItemMeta(m);
        return it;
    }

    private boolean wearing(Player p) {
        ItemStack h = p.getInventory().getHelmet();
        return h != null && h.getType() == Material.PAPER && h.hasItemMeta()
                && h.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (wearing(p)) update(p);
            else clear(p);
        }
    }

    private void update(Player p) {
        Map<Block, BlockDisplay> mine = shown.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Block c = p.getLocation().getBlock();
        // drop outlines that are out of range, mined, or in another world
        for (Iterator<Map.Entry<Block, BlockDisplay>> i = mine.entrySet().iterator(); i.hasNext(); ) {
            var e = i.next();
            Block b = e.getKey();
            if (!b.getWorld().equals(c.getWorld()) || b.getLocation().distanceSquared(c.getLocation()) > (RADIUS + 2) * (RADIUS + 2)
                    || !COLORS.containsKey(b.getType()) || !e.getValue().isValid()) {
                e.getValue().remove();
                i.remove();
            }
        }
        int r2 = RADIUS * RADIUS;
        for (int dx = -RADIUS; dx <= RADIUS; dx++) for (int dy = -RADIUS; dy <= RADIUS; dy++) for (int dz = -RADIUS; dz <= RADIUS; dz++) {
            if (dx * dx + dy * dy + dz * dz > r2 || mine.size() >= MAX) continue;
            Block b = c.getRelative(dx, dy, dz);
            Color col = COLORS.get(b.getType());
            if (col == null || mine.containsKey(b)) continue;
            mine.put(b, outline(p, b, col));
        }
    }

    private BlockDisplay outline(Player p, Block b, Color col) {
        Location l = b.getLocation();
        return b.getWorld().spawn(l, BlockDisplay.class, d -> {
            d.setBlock(b.getBlockData());
            d.setTransformation(new Transformation(new Vector3f(0.01f, 0.01f, 0.01f), new AxisAngle4f(),
                    new Vector3f(0.98f, 0.98f, 0.98f), new AxisAngle4f()));
            d.setGlowing(true);
            d.setGlowColorOverride(col);
            d.setPersistent(false);
            d.setVisibleByDefault(false);
            p.showEntity(plugin, d);
        });
    }

    private void clear(Player p) {
        Map<Block, BlockDisplay> mine = shown.remove(p.getUniqueId());
        if (mine != null) for (BlockDisplay d : mine.values()) d.remove();
    }

    void shutdown() {
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        clear(e.getPlayer());
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(recipeKey);
    }

    void register(Commands r) {
        r.register("givexray", "Gives X-ray goggles (op only): /givexray [player]", (BasicCommand) (src, args) -> {
            if (src.getSender() instanceof Player sp && !sp.isOp()) {
                sp.sendMessage("Op only. Craft them: iron, redstone, iron / tinted glass, diamond, tinted glass.");
                return;
            }
            Player t = args.length > 0 ? Bukkit.getPlayerExact(args[0]) : src.getExecutor() instanceof Player p ? p : null;
            if (t == null) { src.getSender().sendMessage("Usage: givexray <player>"); return; }
            t.getInventory().addItem(item());
            t.sendMessage("You got X-ray goggles! Right-click to put them on.");
        });
    }
}
