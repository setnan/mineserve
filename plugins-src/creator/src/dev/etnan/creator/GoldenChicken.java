package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityBreedEvent;
import org.bukkit.event.entity.EntityDropItemEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.BlastingRecipe;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.FurnaceRecipe;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Golden Chicken: a chicken that lays golden eggs, which smelt into a gold ingot (furnace or blast furnace) or hatch
 * golden chicks when thrown. You get one by feeding a chicken a golden apple; 2 % of new chickens are golden; two
 * golden parents give a golden chick (one gives 50 %). Golden chickens also follow a player holding glow berries. Golden chickens shed golden feathers (9 = a gold block) and
 * drop raw golden chicken: cooked it doubles your health, and in Golden Chicken Soup it gives max speed and strength.
 * The look is the chicken variant supermann:golden from the supermann datapack (no natural spawns) with textures
 * from golden_chicken_pack.py; without them it is a normal chicken that sparkles.
 */
public final class GoldenChicken implements Listener {
    private static final double NATURAL_CHANCE = 0.02;
    private final NamespacedKey tag, eggTag, featherTag, smeltKey, blastKey, blockKey, shedKey, foodTag, soupKey;
    static final int BOOST_SECS = 300, SOUP_SECS = 300;
    private final Chicken.Variant variant;

    static GoldenChicken instance;                  // for the Magic Wand spell "gold"

    GoldenChicken(JavaPlugin plugin) {
        instance = this;
        tag = new NamespacedKey(plugin, "golden_chicken");
        eggTag = new NamespacedKey(plugin, "golden_egg");
        smeltKey = new NamespacedKey(plugin, "golden_egg_smelt");
        blastKey = new NamespacedKey(plugin, "golden_egg_blast");
        featherTag = new NamespacedKey(plugin, "golden_feather");
        blockKey = new NamespacedKey(plugin, "golden_feather_block");
        shedKey = new NamespacedKey(plugin, "golden_chicken_shed");
        foodTag = new NamespacedKey(plugin, "golden_food");
        soupKey = new NamespacedKey(plugin, "golden_soup");
        Chicken.Variant v = null;
        try {
            v = RegistryAccess.registryAccess().getRegistry(RegistryKey.CHICKEN_VARIANT).get(new NamespacedKey("supermann", "golden"));
        } catch (RuntimeException ignored) { }
        variant = v;
        if (v == null) plugin.getLogger().warning("Chicken variant supermann:golden is missing (supermann datapack), golden chickens look like normal chickens");
        Bukkit.removeRecipe(smeltKey);
        Bukkit.removeRecipe(blastKey);
        Bukkit.addRecipe(new FurnaceRecipe(smeltKey, new ItemStack(Material.GOLD_INGOT), new RecipeChoice.ExactChoice(egg()), 1.0f, 200));
        Bukkit.addRecipe(new BlastingRecipe(blastKey, new ItemStack(Material.GOLD_INGOT), new RecipeChoice.ExactChoice(egg()), 1.0f, 100));
        Bukkit.removeRecipe(blockKey);
        org.bukkit.inventory.ShapedRecipe block = new org.bukkit.inventory.ShapedRecipe(blockKey, new ItemStack(Material.GOLD_BLOCK));
        block.shape("FFF", "FFF", "FFF");
        block.setIngredient('F', new RecipeChoice.ExactChoice(feather()));
        Bukkit.addRecipe(block);
        Bukkit.removeRecipe(soupKey);
        org.bukkit.inventory.ShapelessRecipe soup = new org.bukkit.inventory.ShapelessRecipe(soupKey, food("soup"));
        soup.addIngredient(new RecipeChoice.ExactChoice(food("cooked")));
        soup.addIngredient(Material.BOWL);
        soup.addIngredient(Material.CARROT);
        soup.addIngredient(Material.GLOW_BERRIES);
        Bukkit.addRecipe(soup);
        for (Player p : Pure.players()) { p.discoverRecipe(blockKey); p.discoverRecipe(soupKey); }
        Bukkit.getScheduler().runTaskTimer(plugin, this::sparkle, 40L, 40L);
        Bukkit.getScheduler().runTaskTimer(plugin, this::lure, 10L, 10L);
    }

    ItemStack feather() {
        ItemStack it = new ItemStack(Material.FEATHER);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Golden Feather", TextColor.color(0xFFD23F)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("From a golden chicken. 9 golden feathers in a crafting table make a gold block.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(featherTag, PersistentDataType.BYTE, (byte) 1);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("golden_feather"));
        m.setCustomModelDataComponent(c);
        it.setItemMeta(m);
        return it;
    }

    /** Food from the golden chicken: "raw" (raw chicken), "cooked" (cooked chicken) or "soup" (stew). */
    ItemStack food(String kind) {
        Material mat = switch (kind) { case "raw" -> Material.CHICKEN; case "cooked" -> Material.COOKED_CHICKEN; default -> Material.MUSHROOM_STEW; };
        String name = switch (kind) { case "raw" -> "Raw Golden Chicken"; case "cooked" -> "Cooked Golden Chicken"; default -> "Golden Chicken Soup"; };
        String lore = switch (kind) {
            case "raw" -> "Cook it in a furnace, smoker or campfire.";
            case "cooked" -> "Double health (10 extra hearts) for 5 minutes. Or make Golden Chicken Soup: + bowl + carrot + glow berries.";
            default -> "The magic potion: max speed and strength for 5 minutes!";
        };
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text(name, TextColor.color(0xFFD23F)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text(lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(foodTag, PersistentDataType.STRING, kind);
        if (!kind.equals("raw")) m.setEnchantmentGlintOverride(true);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of(kind.equals("soup") ? "golden_soup" : "golden_chicken_" + kind));
        m.setCustomModelDataComponent(c);
        it.setItemMeta(m);
        return it;
    }

    private String foodOf(ItemStack it) {
        return it == null || !it.hasItemMeta() ? null : it.getItemMeta().getPersistentDataContainer().get(foodTag, PersistentDataType.STRING);
    }

    /** Raw golden chicken cooks into cooked golden chicken (furnace, smoker, campfire), not plain cooked chicken. */
    @EventHandler(ignoreCancelled = true)
    public void onCook(org.bukkit.event.block.BlockCookEvent e) {
        if ("raw".equals(foodOf(e.getSource()))) e.setResult(food("cooked"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onEat(org.bukkit.event.player.PlayerItemConsumeEvent e) {
        String kind = foodOf(e.getItem());
        if (kind == null || kind.equals("raw")) return;
        Player p = e.getPlayer();
        if (kind.equals("cooked")) {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.HEALTH_BOOST, BOOST_SECS * 20, 4));
            Bukkit.getScheduler().runTask(Bukkit.getPluginManager().getPlugin("Creator"), () -> {
                var max = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
                if (max != null) p.setHealth(max.getValue());
            });
            p.sendActionBar(Component.text("Double health for 5 minutes!", TextColor.color(0xFFD23F)));
        } else {
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.SPEED, SOUP_SECS * 20, 4));
            p.addPotionEffect(new org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.STRENGTH, SOUP_SECS * 20, 4));
            p.getWorld().spawnParticle(Particle.WAX_ON, p.getLocation().add(0, 1, 0), 40, 0.5, 0.8, 0.5);
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
            p.sendActionBar(Component.text("The magic potion works! Max speed and strength for 5 minutes.", TextColor.color(0xFFD23F)));
        }
    }

    private boolean isFeather(ItemStack it) {
        return it != null && it.getType() == Material.FEATHER && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(featherTag, PersistentDataType.BYTE);
    }

    ItemStack egg() {
        ItemStack it = new ItemStack(Material.EGG);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Golden Egg", TextColor.color(0xFFD23F)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(Component.text("Laid by a golden chicken. Smelt it into a gold ingot, or throw it: a golden chick may hatch.", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)));
        m.getPersistentDataContainer().set(eggTag, PersistentDataType.BYTE, (byte) 1);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("golden_egg"));
        m.setCustomModelDataComponent(c);
        it.setItemMeta(m);
        return it;
    }

    boolean isEgg(ItemStack it) {
        return it != null && it.getType() == Material.EGG && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(eggTag, PersistentDataType.BYTE);
    }

    boolean isGolden(Entity e) {
        return e instanceof Chicken && e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    void makeGolden(Chicken c) {
        c.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        if (variant != null) c.setVariant(variant);
        c.customName(Component.text("Golden Chicken", TextColor.color(0xFFD23F)));
        c.setCustomNameVisible(false);
        c.setRemoveWhenFarAway(false);
        c.getWorld().spawnParticle(Particle.WAX_ON, c.getLocation().add(0, 0.5, 0), 25, 0.4, 0.4, 0.4);
        c.getWorld().playSound(c.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f);
    }

    // --- How you get one ----------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onFeed(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !(e.getRightClicked() instanceof Chicken c) || isGolden(c)) return;
        Player p = e.getPlayer();
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand.getType() != Material.GOLDEN_APPLE) return;
        e.setCancelled(true);
        if (p.getGameMode() != GameMode.CREATIVE) hand.setAmount(hand.getAmount() - 1);
        makeGolden(c);
        p.sendActionBar(Component.text("The chicken became a golden chicken! It lays golden eggs.", TextColor.color(0xFFD23F)));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Chicken c)) return;
        CreatureSpawnEvent.SpawnReason r = e.getSpawnReason();
        if (r == CreatureSpawnEvent.SpawnReason.BREEDING || r == CreatureSpawnEvent.SpawnReason.CUSTOM
                || r == CreatureSpawnEvent.SpawnReason.COMMAND) return;
        if (ThreadLocalRandom.current().nextDouble() < NATURAL_CHANCE) makeGolden(c);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onBreed(EntityBreedEvent e) {
        if (!(e.getEntity() instanceof Chicken baby)) return;
        int golden = (isGolden(e.getMother()) ? 1 : 0) + (isGolden(e.getFather()) ? 1 : 0);
        if (golden == 2 || (golden == 1 && ThreadLocalRandom.current().nextBoolean())) makeGolden(baby);
    }

    // --- Eggs ---------------------------------------------------------------------------------------

    /** A golden chicken lays golden eggs instead of normal ones. */
    @EventHandler(ignoreCancelled = true)
    public void onLay(EntityDropItemEvent e) {
        if (!isGolden(e.getEntity())) return;
        Item it = e.getItemDrop();
        Material m = it.getItemStack().getType();
        if (m != Material.EGG && m != Material.BLUE_EGG && m != Material.BROWN_EGG) return;
        it.setItemStack(egg());
        it.getWorld().spawnParticle(Particle.WAX_ON, it.getLocation(), 12, 0.2, 0.2, 0.2);
    }

    /** A thrown golden egg hatches like a normal egg (vanilla odds: 1 in 8, sometimes 4), but the chicks are golden. */
    @EventHandler
    public void onHatch(org.bukkit.event.player.PlayerEggThrowEvent e) {
        if (!isEgg(e.getEgg().getItem())) return;
        if (!e.isHatching()) return;
        int n = e.getNumHatches();
        e.setHatching(false);
        org.bukkit.Location l = e.getEgg().getLocation();
        for (int i = 0; i < n; i++) {
            Chicken c = l.getWorld().spawn(l, Chicken.class);
            c.setBaby();
            makeGolden(c);
        }
    }

    /** Golden chickens drop golden feathers instead of normal ones when they die. */
    @EventHandler
    public void onDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        if (!isGolden(e.getEntity())) return;
        for (int i = 0; i < e.getDrops().size(); i++) {
            ItemStack d = e.getDrops().get(i);
            ItemStack f = d.getType() == Material.FEATHER ? feather() : d.getType() == Material.CHICKEN ? food("raw")
                    : d.getType() == Material.COOKED_CHICKEN ? food("cooked") : null;
            if (f != null) {
                f.setAmount(d.getAmount());
                e.getDrops().set(i, f);
            }
        }
    }

    /** Golden feathers only make gold blocks, they are not used up as plain feathers (arrows, books …). */
    @EventHandler
    public void onCraft(org.bukkit.event.inventory.PrepareItemCraftEvent e) {
        if (e.getRecipe() instanceof org.bukkit.Keyed k && (k.getKey().equals(blockKey) || k.getKey().equals(soupKey))) return;
        for (ItemStack it : e.getInventory().getMatrix())
            if (isFeather(it) || isEgg(it)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler
    public void onJoin(org.bukkit.event.player.PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(blockKey);
        e.getPlayer().discoverRecipe(soupKey);
    }

    /** Golden chickens also follow a player who holds glow berries (besides the seeds every chicken follows). */
    private void lure() {
        for (Player p : Pure.players()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            if (p.getInventory().getItemInMainHand().getType() != Material.GLOW_BERRIES && p.getInventory().getItemInOffHand().getType() != Material.GLOW_BERRIES) continue;
            for (Entity e : p.getNearbyEntities(10, 5, 10)) {
                if (!(e instanceof Chicken c) || !isGolden(c) || c.isLeashed()) continue;
                if (c.getLocation().distanceSquared(p.getLocation()) > 2.5 * 2.5) c.getPathfinder().moveTo(p, 1.1);
                c.lookAt(p);
            }
        }
    }

    /** Every 2 s: some sparkle, and grown golden chickens shed a golden feather every 10–20 minutes. */
    private void sparkle() {
        long now = System.currentTimeMillis();
        for (World w : Bukkit.getWorlds())
            for (Chicken c : w.getEntitiesByClass(Chicken.class)) {
                if (!isGolden(c)) continue;
                if (ThreadLocalRandom.current().nextInt(3) == 0)
                    w.spawnParticle(Particle.WAX_ON, c.getLocation().add(0, 0.6, 0), 3, 0.25, 0.25, 0.25);
                if (!c.isAdult()) continue;
                Long next = c.getPersistentDataContainer().get(shedKey, PersistentDataType.LONG);
                if (next == null) {
                    c.getPersistentDataContainer().set(shedKey, PersistentDataType.LONG, now + shedDelay());
                } else if (next <= now) {
                    w.dropItemNaturally(c.getLocation(), feather());
                    w.playSound(c.getLocation(), Sound.ENTITY_CHICKEN_AMBIENT, 1f, 1.3f);
                    c.getPersistentDataContainer().set(shedKey, PersistentDataType.LONG, now + shedDelay());
                }
            }
    }

    private static long shedDelay() {
        return ThreadLocalRandom.current().nextLong(10 * 60_000L, 20 * 60_000L);
    }

    // --- Commands -----------------------------------------------------------------------------------

    void register(Commands r) {
        r.register("goldenchicken", "Spawns a golden chicken where you stand (op only)", (BasicCommand) (src, args) -> {
            if (!(src.getExecutor() instanceof Player p)) { src.getSender().sendMessage("Must be run by a player"); return; }
            if (!p.isOp()) { p.sendMessage("Op only. Feed a chicken a golden apple to make a golden chicken."); return; }
            makeGolden(p.getWorld().spawn(p.getLocation(), Chicken.class));
        });
        r.register("givegoldenfood", "Gives golden chicken food (op only): /givegoldenfood <raw|cooked|soup> [player]", (BasicCommand) (src, args) -> {
            if (src.getSender() instanceof Player sp && !sp.isOp()) { sp.sendMessage("Op only."); return; }
            String k = args.length > 0 ? args[0].toLowerCase() : "";
            if (!List.of("raw", "cooked", "soup").contains(k)) { src.getSender().sendMessage("Usage: givegoldenfood <raw|cooked|soup> [player]"); return; }
            Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : src.getExecutor() instanceof Player p ? p : null;
            if (t == null) { src.getSender().sendMessage("Player not found"); return; }
            t.getInventory().addItem(food(k));
        });
        r.register("givegoldenegg", "Gives golden eggs (op only): /givegoldenegg [amount] [player]", (BasicCommand) (src, args) -> {
            if (src.getSender() instanceof Player sp && !sp.isOp()) { sp.sendMessage("Op only."); return; }
            int n = 1;
            try { if (args.length > 0) n = Math.max(1, Math.min(16, Integer.parseInt(args[0]))); } catch (NumberFormatException ignored) { }
            Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : src.getExecutor() instanceof Player p ? p : null;
            if (t == null) { src.getSender().sendMessage("Usage: givegoldenegg [amount] <player>"); return; }
            ItemStack it = egg();
            it.setAmount(n);
            t.getInventory().addItem(it);
        });
    }
}
