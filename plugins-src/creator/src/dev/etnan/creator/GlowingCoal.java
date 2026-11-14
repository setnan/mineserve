package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.Commands;
import java.util.List;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.BlastFurnace;
import org.bukkit.block.Smoker;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.FurnaceBurnEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Glowing Coal: 1 coal (or charcoal) + 1 redstone in a crafting table. In a furnace it burns as long as 64 coal
 * (102400 ticks; the furnace stores the time as an int, so it survives restarts). Blast furnaces and smokers burn
 * twice as fast, as with normal fuel. It is a vanilla coal with custom_model_data "glowing_coal" (glowing_coal_pack.py).
 */
final class GlowingCoal implements Listener {
    static final int COAL_TICKS = 1600, COUNT = 64;
    private final NamespacedKey tag, recipeKey;

    GlowingCoal(JavaPlugin plugin) {
        tag = new NamespacedKey(plugin, "glowing_coal");
        recipeKey = new NamespacedKey(plugin, "glowing_coal");
        ShapelessRecipe r = new ShapelessRecipe(recipeKey, item());
        r.addIngredient(new RecipeChoice.MaterialChoice(Material.COAL, Material.CHARCOAL));
        r.addIngredient(Material.REDSTONE);
        Bukkit.removeRecipe(recipeKey);
        Bukkit.addRecipe(r);
        for (Player p : Bukkit.getOnlinePlayers()) p.discoverRecipe(recipeKey);
    }

    ItemStack item() {
        ItemStack it = new ItemStack(Material.COAL);
        ItemMeta m = it.getItemMeta();
        m.displayName(Component.text("Glowing Coal", TextColor.color(0xFF7A1A)).decoration(TextDecoration.ITALIC, false));
        m.lore(List.of(
                Component.text("Burns as long as 64 normal coal", NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Made from 1 coal and 1 redstone", NamedTextColor.DARK_GRAY).decoration(TextDecoration.ITALIC, false)));
        m.setEnchantmentGlintOverride(true);
        m.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        CustomModelDataComponent c = m.getCustomModelDataComponent();
        c.setStrings(List.of("glowing_coal"));
        m.setCustomModelDataComponent(c);
        it.setItemMeta(m);
        return it;
    }

    private boolean is(ItemStack it) {
        return it != null && it.getType() == Material.COAL && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(tag, PersistentDataType.BYTE);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBurn(FurnaceBurnEvent e) {
        if (!is(e.getFuel())) return;
        int ticks = COAL_TICKS * COUNT;
        if (e.getBlock().getState() instanceof BlastFurnace || e.getBlock().getState() instanceof Smoker) ticks /= 2;
        e.setBurnTime(ticks);
    }

    /** Glowing coal is not used up as plain coal in other recipes (torches, coal blocks …). */
    @EventHandler
    public void onCraft(PrepareItemCraftEvent e) {
        if (e.getRecipe() != null && e.getRecipe().getResult().isSimilar(item())) return;
        for (ItemStack it : e.getInventory().getMatrix())
            if (is(it)) { e.getInventory().setResult(null); return; }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipe(recipeKey);
    }

    void register(Commands r) {
        r.register("giveglowingcoal", "Gives glowing coal (op only): /giveglowingcoal [amount] [player]", (BasicCommand) (src, args) -> {
            if (src.getSender() instanceof Player sp && !sp.isOp()) { sp.sendMessage("Op only. Craft it yourself: 1 coal + 1 redstone."); return; }
            int n = 1;
            try { if (args.length > 0) n = Math.max(1, Math.min(64, Integer.parseInt(args[0]))); } catch (NumberFormatException ignored) { }
            Player t = args.length > 1 ? Bukkit.getPlayerExact(args[1]) : src.getExecutor() instanceof Player p ? p : null;
            if (t == null) { src.getSender().sendMessage("Usage: giveglowingcoal [amount] <player>"); return; }
            ItemStack it = item();
            it.setAmount(n);
            t.getInventory().addItem(it);
            t.sendMessage("You got " + n + " glowing coal.");
        });
    }
}
