package dev.etnan.creator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
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
import java.util.function.Predicate;
import java.util.regex.Matcher;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Cow;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Sheep;
import org.bukkit.entity.Wolf;
import org.bukkit.inventory.FurnaceInventory;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.Damageable;

/** The work a helper does with you around, and the errands you send it on: mining a vein or a tree, fields and
 *  animals, fishing, crafting, smelting, repairs, tidying the chests, trading, collecting from a place, and looking
 *  for blocks and mobs further away. */
final class Chores {
    final Helper core;

    Chores(Helper core) { this.core = core; }

    void hand(UUID id, Player p, ItemStack it) {
        if (p != null) { Helper.give(p, it); return; }
        Map<Integer, ItemStack> rest = core.bag(id).addItem(it);
        core.dirty.add(id);
        for (Inventory inv : Helper.stores(core.homeNow(id), Helper.HOME)) {
            if (rest.isEmpty()) return;
            rest = inv.addItem(rest.values().iterator().next());
        }
        Location hm = core.homeNow(id);
        if (!rest.isEmpty() && hm != null) hm.getWorld().dropItem(hm, rest.values().iterator().next());
    }

    /** Every kind of thing ends up in the home chest that already has the most of it. Returns how many things were
     *  moved, or -1 when there are not two chests at home. */
    int tidy(UUID id) {
        Location hm = core.homeNow(id);
        return hm == null ? -1 : tidy(Helper.stores(hm, Helper.HOME));
    }

    int tidyStuck;                                                  // after tidy(): things left in a chest they do not belong in

    static String coarse(ItemStack it) {
        Material m = it.getType();
        String n = m.name();
        if (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") || m == Material.ELYTRA) return "armour";
        if (n.endsWith("_SWORD") || m == Material.BOW || m == Material.CROSSBOW || m == Material.TRIDENT || m == Material.SHIELD || n.equals("MACE") || n.endsWith("ARROW")) return "weapons";
        if (m.getMaxDurability() > 0) return "tools";
        if (m == Material.ENCHANTED_BOOK || m == Material.BOOK || m == Material.WRITABLE_BOOK) return "books";
        if (m.isEdible()) return "food";
        if (n.contains("INGOT") || n.contains("NUGGET") || n.startsWith("RAW_") || n.endsWith("_ORE") || m == Material.DIAMOND || m == Material.EMERALD || m == Material.COAL || m == Material.CHARCOAL
                || m == Material.REDSTONE || m == Material.LAPIS_LAZULI || m == Material.QUARTZ || m == Material.AMETHYST_SHARD || m == Material.NETHERITE_SCRAP) return "ore and metal";
        if (!Helper.lootKind(it).equals("other")) return "mob loot";
        if (Tag.LOGS.isTagged(m) || Tag.PLANKS.isTagged(m) || Tag.WOODEN_SLABS.isTagged(m) || Tag.WOODEN_STAIRS.isTagged(m) || Tag.WOODEN_FENCES.isTagged(m) || Tag.WOODEN_DOORS.isTagged(m)) return "wood";
        if (Tag.FLOWERS.isTagged(m) || Tag.SAPLINGS.isTagged(m) || Tag.LEAVES.isTagged(m) || n.endsWith("_SEEDS") || m == Material.WHEAT || m == Material.SUGAR_CANE || m == Material.BAMBOO || m == Material.CACTUS) return "plants";
        if (m.isBlock()) return "blocks";
        return "other";
    }

    /** The narrow kind of a thing: "golden armour", "iron tools" …; for what is not gear, the same as the broad kind. */
    static String fine(ItemStack it) {
        String c = coarse(it), n = it.getType().name();
        if (c.equals("armour") || c.equals("tools") || c.equals("weapons"))
            for (String of : new String[] {"GOLDEN", "LEATHER", "IRON", "CHAINMAIL", "COPPER", "DIAMOND", "NETHERITE", "STONE", "WOODEN"}) if (n.startsWith(of + "_")) return of.toLowerCase(Locale.ROOT) + " " + c;
        return c;
    }

    static boolean fits(String theme, ItemStack it) {
        return theme == null || (theme.startsWith("=") ? it.getType().name().equals(theme.substring(1)) : fine(it).equals(theme) || coarse(it).equals(theme));
    }

    /** What a chest is for: the thing in an item frame on it, or else what at least six in ten of its stacks are
     *  (first the narrow kind, like golden armour, then the broad one, like armour). Null: a chest for anything. */
    static String theme(Inventory inv) {
        Location l = inv.getLocation();
        if (l != null) for (Entity e : l.getWorld().getNearbyEntities(l.clone().add(0.5, 0.5, 0.5), 1.6, 1.2, 1.6))
            if (e instanceof org.bukkit.entity.ItemFrame fr && !Helper.empty(fr.getItem())) { String f = fine(fr.getItem()); return f.equals(coarse(fr.getItem())) ? "=" + fr.getItem().getType().name() : f; }
        Map<String, Integer> narrow = new HashMap<>(), broad = new HashMap<>();
        int stacks = 0;
        for (ItemStack it : inv.getContents()) if (!Helper.empty(it)) { stacks++; narrow.merge(fine(it), 1, Integer::sum); broad.merge(coarse(it), 1, Integer::sum); }
        if (stacks < 5) return null;
        for (Map<String, Integer> kinds : List.of(narrow, broad))
            for (Map.Entry<String, Integer> k : kinds.entrySet()) if (!k.getKey().equals("other") && k.getValue() * 10 >= stacks * 6) return k.getKey();
        return null;
    }

    /** Tidies a set of chests. Every kind of thing ends up in the chest that already has the most of it, and what does
     *  not belong in a chest (leather boots among the golden armour) moves to a chest for its kind, or else to a chest
     *  for anything. Returns how many things were moved, or -1 when there are not two chests. */
    int tidy(List<Inventory> invs) {
        tidyStuck = 0;
        if (invs.size() < 2) return -1;
        int size = invs.size();
        String[] theme = new String[size];
        for (int i = 0; i < size; i++) theme[i] = theme(invs.get(i));
        Map<Material, int[]> have = new HashMap<>();
        for (int i = 0; i < size; i++)
            for (ItemStack it : invs.get(i).getContents()) if (!Helper.empty(it) && fits(theme[i], it)) have.computeIfAbsent(it.getType(), k -> new int[size])[i] += it.getAmount();
        int moved = 0;
        for (int i = 0; i < size; i++) {
            Inventory inv = invs.get(i);
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (Helper.empty(it)) continue;
                int[] n = have.get(it.getType());
                if (n == null) continue;
                int best = 0;
                for (int k = 1; k < n.length; k++) if (n[k] > n[best]) best = k;
                if (best == i || n[best] == n[i] || n[best] == 0) continue;
                Map<Integer, ItemStack> rest = invs.get(best).addItem(it.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                moved += it.getAmount() - left;
                it.setAmount(left);
                inv.setItem(j, left > 0 ? it : null);
            }
        }
        for (int i = 0; i < size; i++) {                                    // what still sits where it does not belong
            if (theme[i] == null) continue;
            Inventory inv = invs.get(i);
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (Helper.empty(it) || fits(theme[i], it)) continue;
                String f = fine(it);
                List<int[]> to = new ArrayList<>();                          // {chest, how well it suits}
                for (int k = 0; k < size; k++) {
                    if (k == i || !fits(theme[k], it)) continue;
                    int score = 1;
                    if (theme[k] != null) score = theme[k].equals(f) || theme[k].startsWith("=") ? 4 : 3;
                    else for (ItemStack c : invs.get(k).getContents()) if (!Helper.empty(c) && fine(c).equals(f)) { score = 2; break; }
                    to.add(new int[] {k, score});
                }
                to.sort((a, b) -> b[1] - a[1]);
                ItemStack rest = it.clone();
                for (int[] k : to) {
                    Map<Integer, ItemStack> left = invs.get(k[0]).addItem(rest);
                    if (left.isEmpty()) { rest = null; break; }
                    rest = left.values().iterator().next();
                }
                int gone = it.getAmount() - (rest == null ? 0 : rest.getAmount());
                moved += gone;
                inv.setItem(j, rest);
                if (rest != null) tidyStuck++;
            }
        }
        return moved;
    }

    /** What it takes to mend a thing: one of this. Null = nothing needed. */
    static Predicate<Material> mendWith(Material m) {
        String n = m.name();
        Material need = n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("SHEARS") || n.equals("SHIELD") || n.equals("FLINT_AND_STEEL") ? Material.IRON_INGOT
                : n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") ? Material.DIAMOND
                : n.startsWith("GOLDEN_") ? Material.GOLD_INGOT
                : n.startsWith("COPPER_") ? Material.COPPER_INGOT
                : n.startsWith("STONE_") ? Material.COBBLESTONE
                : n.startsWith("LEATHER_") ? Material.LEATHER
                : n.equals("BOW") || n.equals("CROSSBOW") || n.equals("FISHING_ROD") ? Material.STRING
                : n.equals("ELYTRA") ? Material.PHANTOM_MEMBRANE : null;
        if (n.startsWith("WOODEN_")) return Tag.PLANKS::isTagged;
        return need == null ? null : x -> x == need;
    }

    static String mendName(Material m) {
        String n = m.name();
        return n.startsWith("WOODEN_") ? "planks" : n.startsWith("IRON_") || n.startsWith("CHAINMAIL_") || n.equals("SHEARS") || n.equals("SHIELD") || n.equals("FLINT_AND_STEEL") ? "iron ingots"
                : n.startsWith("DIAMOND_") || n.startsWith("NETHERITE_") ? "diamonds" : n.startsWith("GOLDEN_") ? "gold ingots" : n.startsWith("COPPER_") ? "copper ingots"
                : n.startsWith("STONE_") ? "cobblestone" : n.startsWith("LEATHER_") ? "leather" : n.equals("ELYTRA") ? "phantom membranes" : "string";
    }

    void repair(Player p, Helper.H h) {
        List<Inventory> pay = List.of(core.bag(p.getUniqueId()), p.getInventory());
        var inv = p.getInventory();
        ItemStack[] worn = inv.getArmorContents();
        List<ItemStack> all = new ArrayList<>(Arrays.asList(worn));
        ItemStack main = inv.getItemInMainHand(), off = inv.getItemInOffHand();
        all.add(main);
        all.add(off);
        int fixed = 0;
        Set<String> missing = new java.util.LinkedHashSet<>();
        for (ItemStack it : all) {
            if (Helper.empty(it) || !(it.getItemMeta() instanceof Damageable d) || !d.hasDamage()) continue;
            Predicate<Material> need = mendWith(it.getType());
            if (need != null) {
                if (Helper.count(pay, need) < 1) { missing.add(mendName(it.getType())); continue; }
                Helper.take(pay, need, 1);
            }
            d.setDamage(0);
            it.setItemMeta(d);
            fixed++;
        }
        inv.setArmorContents(worn);
        inv.setItemInMainHand(main);
        inv.setItemInOffHand(off);
        if (fixed > 0) {
            core.dirty.add(p.getUniqueId());
            core.swing(p, h);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_USE, 0.5f, 1.3f);
        }
        core.say(p, (fixed == 0 && missing.isEmpty() ? "Nothing you hold or wear needs mending." : fixed == 0 ? "I could not mend anything." : "I mended " + fixed + (fixed == 1 ? " thing." : " things."))
                + (missing.isEmpty() ? "" : " For the rest I need " + String.join(" and ", missing) + "."));
    }

    /** An errand: everything in the chests at a remembered place comes back in the bag (as much as fits). */
    void collect(Player p, Helper.H h, String where) {
        Location l = core.placeNamed(p.getUniqueId(), where);
        if (l == null) { core.say(p, "I don't know a place called " + where + "."); return; }
        List<Inventory> from = Helper.stores(l);
        if (from.isEmpty()) { core.say(p, "There are no chests within eight blocks of " + where + "."); return; }
        Inventory bag = core.bag(p.getUniqueId());
        int moved = 0;
        boolean more = false;
        for (Inventory inv : from)
            for (int j = 0; j < inv.getSize(); j++) {
                ItemStack it = inv.getItem(j);
                if (Helper.empty(it)) continue;
                Map<Integer, ItemStack> rest = bag.addItem(it.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                moved += it.getAmount() - left;
                more |= left > 0;
                it.setAmount(left);
                inv.setItem(j, left > 0 ? it : null);
            }
        core.dirty.add(p.getUniqueId());
        core.zip(p, h);
        core.say(p, moved == 0 ? (more ? "My bag is full, I could not bring anything." : "The chests at " + where + " are empty.")
                : "I went to " + where + " and brought back " + moved + (moved == 1 ? " thing." : " things.") + (more ? " There is more, but my bag is full." : ""));
    }

    /** Buys from a villager within 16 blocks, as many times as asked and affordable. False when nobody sells it. */
    boolean trade(Player p, Helper.H h, String what, boolean quiet) {
        return trade(p.getUniqueId(), p, p.getLocation(), what, quiet, t -> core.say(p, t));
    }

    /** {@code p} is null when the owner is logged out: then the villagers at home, paid from the bag and the chests there. */
    boolean trade(UUID id, Player p, Location at, String what, boolean quiet, Consumer<String> tell) {
        int times = 1;
        Matcher num = Helper.NUMBER.matcher(what);
        if (num.find()) { times = Math.max(1, Math.min(64, Integer.parseInt(num.group(1)))); what = what.substring(0, num.start()) + what.substring(num.end()); }
        String term = Helper.slug(what).toUpperCase(Locale.ROOT);
        if (term.isEmpty()) { if (!quiet) tell.accept("Trade for what? Say trade and an item, like trade bread 3."); return false; }
        if (term.endsWith("S") && term.length() > 3) term = term.substring(0, term.length() - 1);
        List<Inventory> pay = new ArrayList<>(List.of(core.bag(id)));
        if (p != null) pay.add(p.getInventory()); else pay.addAll(Helper.stores(core.homeNow(id), Helper.HOME));
        String price = null;
        int reach = p != null ? 16 : Helper.HOME;
        for (Entity e : at.getWorld().getNearbyEntities(at, reach, 8, reach)) {
            if (!(e instanceof AbstractVillager v)) continue;
            for (MerchantRecipe r : v.getRecipes()) {
                ItemStack out = r.getResult();
                String spell = Helper.magic(out);                                   // "trade mending" finds the book with mending
                if (!out.getType().name().contains(term) && (spell == null || !spell.toUpperCase(Locale.ROOT).replace(' ', '_').contains(term))) continue;
                List<ItemStack> cost = new ArrayList<>(r.getIngredients());
                cost.removeIf(Helper::empty);
                List<String> parts = new ArrayList<>();
                int can = Math.min(times, r.getMaxUses() - r.getUses());
                for (ItemStack c : cost) {
                    parts.add(c.getAmount() + " " + Helper.word(c.getType()));
                    can = Math.min(can, Helper.count(pay, x -> x == c.getType()) / c.getAmount());
                }
                if (price == null) price = String.join(" and ", parts);
                if (can <= 0) continue;
                for (ItemStack c : cost) Helper.take(pay, x -> x == c.getType(), c.getAmount() * can);
                for (int i = 0; i < can; i++) hand(id, p, out.clone());
                r.setUses(r.getUses() + can);
                core.xp(id, can * (3 + ThreadLocalRandom.current().nextInt(4)));
                core.dirty.add(id);
                if (p != null) { Helper.H h = core.helpers.get(id); if (h != null) core.swing(p, h); }
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_VILLAGER_YES, 0.8f, 1f);
                tell.accept("Deal! " + out.getAmount() * can + " " + Helper.word(out.getType()) + " for " + String.join(" and ", parts) + (can > 1 ? ", " + can + " times." : "."));
                return true;
            }
        }
        if (price == null) { if (!quiet) tell.accept("No villager close by sells " + term.toLowerCase(Locale.ROOT).replace('_', ' ') + "."); return false; }
        tell.accept("I can't pay for that, or the villager is sold out. It costs " + price + ".");
        return true;
    }

    static List<RecipeChoice> ingredients(Recipe r) {
        List<RecipeChoice> need = new ArrayList<>();
        if (r instanceof ShapelessRecipe sl) need.addAll(sl.getChoiceList());
        else if (r instanceof ShapedRecipe sh)
            for (String row : sh.getShape()) for (char c : row.toCharArray()) {
                RecipeChoice ch = sh.getChoiceMap().get(c);
                if (ch != null) need.add(ch);
            }
        need.removeIf(java.util.Objects::isNull);
        return need;
    }

    static String describe(List<RecipeChoice> need) {
        Map<String, Integer> n = new java.util.LinkedHashMap<>();
        for (RecipeChoice c : need) {
            String name = c instanceof RecipeChoice.MaterialChoice mc && !mc.getChoices().isEmpty() ? mc.getChoices().get(0).name()
                    : c instanceof RecipeChoice.ExactChoice ec && !ec.getChoices().isEmpty() ? ec.getChoices().get(0).getType().name() : "something";
            n.merge(name.toLowerCase(Locale.ROOT).replace('_', ' '), 1, Integer::sum);
        }
        List<String> parts = new ArrayList<>();
        n.forEach((k, v) -> parts.add(v + " " + k));
        return String.join(", ", parts);
    }

    /** "torch 16", "iron pickaxe": made from the bag, the chests here and at home and the owner's own inventory,
     *  one crafting step (it does not make the planks for the sticks). False when the word is not an item. */
    boolean craft(Player p, Helper.H h, String what, boolean quiet) {
        return craft(p.getUniqueId(), p, h.w.getLocation(), what, quiet, t -> core.say(p, t));
    }

    /** The bed there is wool for (three of one colour). When there is too little, the sheep within 48 blocks are sheared. */
    Material bedFor(UUID id, Player p, Location here) {
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        if (p != null) from.add(p.getInventory());
        if (here != null) from.addAll(Helper.stores(here));
        from.addAll(Helper.stores(core.homeNow(id), Helper.HOME));
        Location c = here != null ? here : p != null ? p.getLocation() : core.homeNow(id);
        List<Sheep> flock = new ArrayList<>();
        if (c != null) for (Entity e : c.getWorld().getNearbyEntities(c, 48, 24, 48)) if (e instanceof Sheep sh && sh.isAdult() && !sh.isSheared()) flock.add(sh);
        for (int round = 0; ; round++) {
            for (Material w : Material.values()) {
                if (!Tag.WOOL.isTagged(w) || Helper.count(from, k -> k == w) < 3) continue;
                Material bed = Helper.mat(w.name().replace("_WOOL", "_BED"));
                if (bed != null) return bed;
            }
            if (round >= flock.size()) return null;
            Sheep sh = flock.get(round);
            sh.setSheared(true);
            org.bukkit.DyeColor col = sh.getColor();
            Material wool = Helper.mat((col == null ? "WHITE" : col.name()) + "_WOOL");
            core.bag(id).addItem(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
            core.dirty.add(id);
            sh.getWorld().playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
        }
    }

    /** {@code p} is null when the owner is logged out: then from the bag and the chests at home, and into them. */
    boolean craft(UUID id, Player p, Location here, String what, boolean quiet, Consumer<String> tell) {
        int amount = 1;
        Matcher num = Helper.NUMBER.matcher(what);
        if (num.find()) { amount = Math.max(1, Math.min(2304, Integer.parseInt(num.group(1)))); what = what.substring(0, num.start()) + what.substring(num.end()); }
        String term = Helper.ARTICLE.matcher(what.trim().toLowerCase(Locale.ROOT)).replaceFirst("").trim().toUpperCase(Locale.ROOT).replaceAll("[\\s-]+", "_");
        if (term.equals("BED") || term.equals("SENG")) {                   // any colour: the wool there is, or what the sheep close by give
            Material bed = bedFor(id, p, here);
            if (bed == null && p != null && woolTried.add(id)) {           // no wool, no sheep close by: she goes and finds some
                woolTrip(p, () -> { try { craft(id, p, here, "bed", quiet, tell); } finally { woolTried.remove(id); } });
                return true;
            }
            if (bed == null) { if (!quiet) tell.accept("A bed takes three wool of one colour and three planks, and I could not get three wool of one colour."); return false; }
            term = bed.name();
        }
        Material m = term.isEmpty() ? null : Material.matchMaterial(term);
        if (m == null && term.endsWith("S")) m = Material.matchMaterial(term.substring(0, term.length() - 1));
        if (m == null && term.endsWith("ES")) m = Material.matchMaterial(term.substring(0, term.length() - 2));
        if (m == null || !m.isItem()) { if (!quiet) tell.accept("I don't know what " + what.trim() + " is."); return false; }
        String word = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        List<Inventory> from = new ArrayList<>(List.of(core.bag(id)));
        if (p != null) from.add(p.getInventory());
        if (here != null) from.addAll(Helper.stores(here));
        from.addAll(Helper.stores(core.homeNow(id), Helper.HOME));
        String needs = null;
        for (Recipe r : Bukkit.getRecipesFor(new ItemStack(m))) {
            List<RecipeChoice> need = ingredients(r);
            if (need.isEmpty()) continue;
            if (needs == null) needs = describe(need);
            int per = r.getResult().getAmount(), times = (amount + per - 1) / per, made = 0;
            ItemStack[][] have = new ItemStack[from.size()][];
            int[][] left = new int[from.size()][];
            for (int i = 0; i < from.size(); i++) {
                have[i] = from.get(i).getStorageContents();
                left[i] = new int[have[i].length];
                for (int j = 0; j < have[i].length; j++) left[i][j] = Helper.empty(have[i][j]) ? 0 : have[i][j].getAmount();
            }
            rounds:
            for (; made < times; made++) {
                int[][] trial = new int[left.length][];
                for (int i = 0; i < left.length; i++) trial[i] = left[i].clone();
                for (RecipeChoice c : need) {
                    boolean got = false;
                    find:
                    for (int i = 0; i < have.length; i++) for (int j = 0; j < have[i].length; j++)
                        if (trial[i][j] > 0 && c.test(have[i][j])) { trial[i][j]--; got = true; break find; }
                    if (!got) break rounds;
                }
                left = trial;
            }
            if (made == 0) continue;
            for (int i = 0; i < have.length; i++) for (int j = 0; j < have[i].length; j++) {
                if (Helper.empty(have[i][j]) || left[i][j] == have[i][j].getAmount()) continue;
                ItemStack it = have[i][j];
                it.setAmount(left[i][j]);
                from.get(i).setItem(j, left[i][j] > 0 ? it : null);
            }
            ItemStack out = r.getResult();
            int total = made * per;
            for (int n = total; n > 0; n -= out.getMaxStackSize()) { ItemStack g = out.clone(); g.setAmount(Math.min(n, out.getMaxStackSize())); hand(id, p, g); }
            core.dirty.add(id);
            if (p != null) { Helper.H h = core.helpers.get(id); if (h != null) core.swing(p, h); p.playSound(p.getLocation(), Sound.BLOCK_SMITHING_TABLE_USE, 0.6f, 1.2f); }
            tell.accept("I made " + total + " " + word + "." + (made < times ? " That is all I had enough for." : ""));
            return true;
        }
        tell.accept(needs == null ? "I don't know how to make " + word + "." : "I don't have what it takes to make " + word + ". One needs " + needs + ".");
        return true;
    }

    /** Takes what is done out of the furnaces (here, or else at home), and loads the empty ones from the bag: ores
     *  and raw food, or only the item named. Fuel from the bag too, but only real fuel, never planks or tools. */
    void smelt(Player p, Helper.H h, String only) {
        UUID id = p.getUniqueId();
        List<FurnaceInventory> fs = Helper.furnaces(h.w.getLocation());
        if (fs.isEmpty()) fs = Helper.furnaces(core.home(id), Helper.HOME);
        if (fs.isEmpty()) { core.say(p, "There is no furnace here or at home."); return; }
        String term = Helper.slug(only).toUpperCase(Locale.ROOT);
        Inventory bag = core.bag(id);
        int took = 0, put = 0;
        boolean noFuel = false;
        for (FurnaceInventory f : fs) {
            ItemStack done = f.getResult();
            if (!Helper.empty(done)) {
                int before = done.getAmount();
                Map<Integer, ItemStack> rest = bag.addItem(done.clone());
                int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                done.setAmount(left);
                f.setResult(left > 0 ? done : null);
                took += before - left;
            }
            if (Helper.empty(f.getSmelting()))
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (Helper.empty(it) || !f.canSmelt(it)) continue;
                    String n = it.getType().name();
                    if (term.isEmpty() ? !(n.startsWith("RAW_") || n.endsWith("_ORE") || n.equals("ANCIENT_DEBRIS") || it.getType().isEdible()) : !n.contains(term)) continue;
                    f.setSmelting(it.clone());
                    bag.setItem(i, null);
                    put += it.getAmount();
                    break;
                }
            if (Helper.empty(f.getSmelting()) && Helper.empty(f.getResult()) && core.storage != null) {   // raw ore from your own store in the magic barrel
                ItemStack ore = core.storage.takeMine(id, m -> Helper.RAW_ORES.contains(m) && (term.isEmpty() || m.name().contains(term)), 64);
                if (ore != null) { f.setSmelting(ore); put += ore.getAmount(); }
            }
            if (!Helper.empty(f.getSmelting()) && Helper.empty(f.getFuel())) {
                noFuel = true;
                for (int i = 0; i < bag.getSize(); i++) {
                    ItemStack it = bag.getItem(i);
                    if (Helper.empty(it) || !Helper.FUELS.contains(it.getType())) continue;
                    f.setFuel(it.clone());
                    bag.setItem(i, null);
                    noFuel = false;
                    break;
                }
                if (noFuel && core.storage != null) {                       // from your own store in the magic barrel
                    ItemStack one = core.storage.takeMine(id, m -> Helper.FUELS.contains(m) && m != Material.LAVA_BUCKET, 8);
                    if (one != null) { f.setFuel(one); noFuel = false; }
                }
            }
        }
        core.dirty.add(id);
        core.zip(p, h);
        core.say(p, (took + put == 0 ? "Nothing was done yet, and I have nothing to put in." : "I took out " + took + " and put in " + put + ".")
                + (noFuel ? " A furnace has no fuel: give me coal, charcoal, blaze rods or a lava bucket, or put some in your magic barrel." : ""));
    }

    /** While farming: one sheep sheared, one cow milked (with a bucket from the bag) or one animal fed (with food
     *  from the bag) every other second. No feeding when there are already twelve of that animal around. */
    void animals(Player p, Helper.H h) {
        Inventory bag = core.bag(p.getUniqueId());
        Collection<Entity> around = h.anchor.getWorld().getNearbyEntities(h.anchor, Helper.FARM_RANGE, 4, Helper.FARM_RANGE);
        for (Entity e : around) {
            if (!(e instanceof Animals a) || !a.isAdult() || core.ownerOf(e) != null) continue;
            if (a instanceof Sheep sh && sh.readyToBeSheared()) { sh.shear(); core.swing(p, h); return; }
            if (a instanceof Cow && bag.contains(Material.BUCKET) && System.currentTimeMillis() - h.said.getOrDefault("milk", 0L) > 60_000) {
                h.said.put("milk", System.currentTimeMillis());
                bag.removeItem(new ItemStack(Material.BUCKET));
                core.keep(p, h, List.of(new ItemStack(Material.MILK_BUCKET)));
                e.getWorld().playSound(e.getLocation(), Sound.ENTITY_COW_MILK, 0.8f, 1f);
                core.swing(p, h);
                return;
            }
            if (!a.canBreed() || a.isLoveMode() || around.stream().filter(o -> o.getType() == a.getType()).count() >= 12) continue;
            for (int i = 0; i < bag.getSize(); i++) {
                ItemStack it = bag.getItem(i);
                if (Helper.empty(it) || !a.isBreedItem(it)) continue;
                it.setAmount(it.getAmount() - 1);
                bag.setItem(i, it.getAmount() > 0 ? it : null);
                core.dirty.add(p.getUniqueId());
                a.setLoveModeTicks(600);
                a.setBreedCause(p.getUniqueId());
                e.getWorld().spawnParticle(Particle.HEART, e.getLocation().add(0, e.getHeight(), 0), 5, 0.3, 0.3, 0.3);
                core.swing(p, h);
                return;
            }
        }
    }

    static final Map<String, String> MINE_NB = Map.ofEntries(Map.entry("jord", "dirt"), Map.entry("grus", "gravel"), Map.entry("stein", "stone"), Map.entry("leire", "clay"),
            Map.entry("kull", "coal_ore"), Map.entry("jern", "iron_ore"), Map.entry("gull", "gold_ore"), Map.entry("kobber", "copper_ore"), Map.entry("diamant", "diamond_ore"),
            Map.entry("diamanter", "diamond_ore"), Map.entry("snø", "snow_block"), Map.entry("coal", "coal_ore"), Map.entry("iron", "iron_ore"), Map.entry("gold", "gold_ore"),
            Map.entry("copper", "copper_ore"), Map.entry("diamond", "diamond_ore"), Map.entry("diamonds", "diamond_ore"), Map.entry("redstone", "redstone_ore"), Map.entry("grass", "grass_block"));

    /** "sand", "red sand", "grus": the block that is meant, or null. */
    static final Set<String> WOOD_WORDS = Set.of("wood", "tree", "trees", "a tree", "log", "logs", "timber", "ved", "tre", "trær", "et tre", "tømmer");

    static Material mineKind(String what) {
        String w = what.trim().toLowerCase(Locale.ROOT).replace(' ', '_');
        if (w.isEmpty()) return null;
        w = MINE_NB.getOrDefault(w, w);
        for (String t : new String[] {w, w.endsWith("s") ? w.substring(0, w.length() - 1) : w, w + "_block"}) {
            Material m = Material.matchMaterial(t);
            if (m != null && m.isBlock() && !m.isAir()) return m;
        }
        return null;
    }

    void startMine(Player p, Helper.H h) { startMine(p, h, ""); }

    /** Mine what its owner looks at, or, with a word ("mine sand"), the nearest of that kind and nothing else. */
    void startMine(Player p, Helper.H h, String what) {
        Block t = p.getTargetBlockExact(6);
        boolean wood = WOOD_WORDS.contains(what.trim().toLowerCase(Locale.ROOT));
        Material want = wood ? null : mineKind(what);
        if (wood && (t == null || !Tag.LOGS.isTagged(t.getType()))) {       // any tree: the nearest log within 24 blocks
            t = null;
            double bd = Double.MAX_VALUE;
            Block c = p.getLocation().getBlock();
            for (int x = -24; x <= 24; x++) for (int y = -8; y <= 16; y++) for (int z = -24; z <= 24; z++) {
                Block b = c.getRelative(x, y, z);
                double d = x * x + y * y * 4 + z * z;
                if (d < bd && Tag.LOGS.isTagged(b.getType())) { bd = d; t = b; }
            }
            if (t == null) { core.say(p, "I see no trees within 24 blocks."); return; }
        }
        if (!what.isBlank() && !wood && want == null) { core.say(p, "I do not know what " + what.trim() + " is. Look at a block and say mine, or say mine sand, mine gravel, mine clay…"); return; }
        if (want != null && (t == null || !Helper.vein(t.getType()).equals(Helper.vein(want)))) {       // not looking at it: the nearest one
            t = null;
            double bd = Double.MAX_VALUE;
            Block c = p.getLocation().getBlock();
            for (int x = -16; x <= 16; x++) for (int y = -8; y <= 8; y++) for (int z = -16; z <= 16; z++) {
                Block b = c.getRelative(x, y, z);
                double d = x * x + y * y * 4 + z * z;
                if (d < bd && Helper.vein(b.getType()).equals(Helper.vein(want))) { bd = d; t = b; }
            }
            if (t == null) {                                               // none close by: as far as it takes
                Set<Material> same = new HashSet<>();
                for (Material k : Material.values()) if (!k.isLegacy() && k.isBlock() && Helper.vein(k).equals(Helper.vein(want))) same.add(k);
                seekBlock(p, same, Helper.word(want), b -> { if (core.helpers.get(p.getUniqueId()) == h) mineFrom(p, h, b); });
                return;
            }
        }
        mineFrom(p, h, t);
    }

    final Set<UUID> oreSeek = new HashSet<>();

    static final int ORE_RANGE = 1024, ORE_NEW = 400;     // how far it looks, and how many chunks of new land it may make on the way

    /** No ore close by: the land around is searched, nearest first and a little at a time; new land is made when needed. */
    void seekBlock(Player p, Set<Material> kinds, String name, Consumer<Block> then) {
        UUID id = p.getUniqueId();
        if (!oreSeek.add(id)) { core.say(p, "I am still looking."); return; }
        core.say(p, "I see no " + name + " close by. I look further away. One moment.");
        int[] made = {0};
        World w = p.getWorld();
        Location from = p.getLocation();
        int px = from.getBlockX(), pz = from.getBlockZ();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (px - ORE_RANGE) >> 4; cx <= (px + ORE_RANGE) >> 4; cx++) for (int cz = (pz - ORE_RANGE) >> 4; cz <= (pz + ORE_RANGE) >> 4; cz++) chunks.add(new int[] {cx, cz});
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz)));
        int[] at = {0};
        Block[] best = {null};
        double[] bd = {Double.MAX_VALUE};
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(core.plugin, () -> {
            try {
                for (int n = 0; n < 4 && at[0] < chunks.size(); n++) {
                    int[] c = chunks.get(at[0]++);
                    double near = Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz) - 12;
                    if (near > ORE_RANGE || near * near > bd[0]) { at[0] = chunks.size(); break; }      // nothing nearer can come now
                    if (!w.isChunkGenerated(c[0], c[1])) { if (made[0]++ >= ORE_NEW) continue; n = 4; }       // new land: one chunk a tick
                    org.bukkit.ChunkSnapshot snap = w.getChunkAt(c[0], c[1]).getChunkSnapshot();
                    for (int bx = 0; bx < 16; bx++) for (int bz = 0; bz < 16; bz++) {
                        int top = snap.getHighestBlockYAt(bx, bz);
                        for (int y = w.getMinHeight(); y <= top; y++) {
                            if (!kinds.contains(snap.getBlockType(bx, y, bz))) continue;
                            double dx = c[0] * 16 + bx - px, dy = y - from.getY(), dz = c[1] * 16 + bz - pz, d = dx * dx + dy * dy + dz * dz;
                            if (d < bd[0] && dx * dx + dz * dz <= ORE_RANGE * ORE_RANGE) { bd[0] = d; best[0] = w.getBlockAt(c[0] * 16 + bx, y, c[1] * 16 + bz); }
                        }
                    }
                }
                if (at[0] < chunks.size()) return;
                task[0].cancel();
                oreSeek.remove(id);
                if (!p.isOnline()) return;
                if (best[0] == null) { core.say(p, "I find no " + name + " within " + ORE_RANGE + " blocks. It may not exist in this world."); return; }
                core.say(p, "The nearest " + name + " is " + (int) Math.sqrt(bd[0]) + " blocks away, at " + best[0].getX() + " " + best[0].getY() + " " + best[0].getZ() + ".");
                then.accept(best[0]);
            } catch (RuntimeException e) {
                core.plugin.getLogger().warning("Ore search stopped: " + e);
                task[0].cancel();
                oreSeek.remove(id);
            }
        }, 1L, 1L);
    }

    final Set<UUID> mobSeek = new HashSet<>(), woolTried = new HashSet<>();

    static final Map<String, EntityType> MOB_FOR = Map.ofEntries(Map.entry("wool", EntityType.SHEEP), Map.entry("mutton", EntityType.SHEEP), Map.entry("ull", EntityType.SHEEP),
            Map.entry("sau", EntityType.SHEEP), Map.entry("sauer", EntityType.SHEEP), Map.entry("leather", EntityType.COW), Map.entry("beef", EntityType.COW), Map.entry("milk", EntityType.COW),
            Map.entry("ku", EntityType.COW), Map.entry("kyr", EntityType.COW), Map.entry("lær", EntityType.COW), Map.entry("feather", EntityType.CHICKEN), Map.entry("egg", EntityType.CHICKEN),
            Map.entry("høne", EntityType.CHICKEN), Map.entry("høner", EntityType.CHICKEN), Map.entry("fjær", EntityType.CHICKEN), Map.entry("pork", EntityType.PIG), Map.entry("porkchop", EntityType.PIG),
            Map.entry("gris", EntityType.PIG), Map.entry("griser", EntityType.PIG), Map.entry("hest", EntityType.HORSE), Map.entry("hester", EntityType.HORSE));

    static EntityType mobFor(String term) {
        EntityType t = MOB_FOR.get(term);
        if (t != null) return t;
        try { t = EntityType.valueOf(term.toUpperCase(Locale.ROOT)); } catch (IllegalArgumentException e) { return null; }
        return t.isAlive() && t != EntityType.PLAYER ? t : null;
    }

    /** The nearest living thing of a kind: those already around first, then the land outwards, a few chunks at a time
     *  (their animals have to be read in before they can be seen). {@code then} gets null when there is none. */
    void seekMob(Player p, EntityType type, java.util.function.Predicate<Entity> ok, Consumer<Entity> then) {
        UUID id = p.getUniqueId();
        World w = p.getWorld();
        Location from = p.getLocation();
        Entity near = null;
        for (Entity e : w.getEntities()) if (e.getType() == type && e.isValid() && ok.test(e) && (near == null || e.getLocation().distanceSquared(from) < near.getLocation().distanceSquared(from))) near = e;
        if (near != null) { then.accept(near); return; }
        if (!mobSeek.add(id)) { core.say(p, "I am still looking."); return; }
        String name = type.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        core.say(p, "I see no " + name + " around here. I look further away. This can take a few minutes.");
        int px = from.getBlockX(), pz = from.getBlockZ();
        List<int[]> chunks = new ArrayList<>();
        for (int cx = (px - ORE_RANGE) >> 4; cx <= (px + ORE_RANGE) >> 4; cx++) for (int cz = (pz - ORE_RANGE) >> 4; cz <= (pz + ORE_RANGE) >> 4; cz++) if (!w.isChunkLoaded(cx, cz)) chunks.add(new int[] {cx, cz});
        chunks.sort(Comparator.comparingDouble(c -> Math.hypot(c[0] * 16 + 8 - px, c[1] * 16 + 8 - pz)));
        int[] at = {0, 0, 0};                                              // next chunk, ticks waited, new land made
        List<int[]> batch = new ArrayList<>();
        org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(core.plugin, () -> {
            try {
                if (batch.isEmpty()) {
                    while (batch.size() < 9 && at[0] < chunks.size()) {
                        int[] c = chunks.get(at[0]++);
                        if (!w.isChunkGenerated(c[0], c[1]) && at[2]++ >= ORE_NEW) continue;
                        w.addPluginChunkTicket(c[0], c[1], core.plugin);
                        batch.add(c);
                    }
                    at[1] = 0;
                    if (batch.isEmpty()) { task[0].cancel(); mobSeek.remove(id); if (p.isOnline()) then.accept(null); }
                    return;
                }
                boolean ready = true;
                for (int[] c : batch) if (!w.isChunkLoaded(c[0], c[1]) || !w.getChunkAt(c[0], c[1]).isEntitiesLoaded()) ready = false;
                if (!ready && at[1]++ < 30) return;
                Entity hit = null;
                for (int[] c : batch) for (Entity e : w.getChunkAt(c[0], c[1]).getEntities())
                    if (e.getType() == type && e.isValid() && ok.test(e) && (hit == null || e.getLocation().distanceSquared(from) < hit.getLocation().distanceSquared(from))) hit = e;
                for (int[] c : batch) {
                    if (hit != null && hit.getLocation().getBlockX() >> 4 == c[0] && hit.getLocation().getBlockZ() >> 4 == c[1]) {       // it stays loaded for a minute, for what comes next
                        Bukkit.getScheduler().runTaskLater(core.plugin, () -> w.removePluginChunkTicket(c[0], c[1], core.plugin), 1200L);
                    } else w.removePluginChunkTicket(c[0], c[1], core.plugin);
                }
                batch.clear();
                if (hit == null) return;
                task[0].cancel();
                mobSeek.remove(id);
                if (p.isOnline()) then.accept(hit);
            } catch (RuntimeException e) {
                core.plugin.getLogger().warning("Search for " + name + " stopped: " + e);
                for (int[] c : batch) w.removePluginChunkTicket(c[0], c[1], core.plugin);
                task[0].cancel();
                mobSeek.remove(id);
            }
        }, 2L, 2L);
    }

    /** No wool: she finds the nearest sheep, goes there, shears the flock and comes back. */
    void woolTrip(Player p, Runnable then) {
        UUID id = p.getUniqueId();
        seekMob(p, EntityType.SHEEP, e -> e instanceof Sheep sh && sh.isAdult() && !sh.isSheared(), e -> {
            if (e == null) { core.say(p, "I found no sheep with wool on within " + ORE_RANGE + " blocks."); then.run(); return; }
            Helper.H h = core.helpers.get(id);
            Location at = e.getLocation();
            boolean goes = h != null && !h.merged && h.w != null && !h.w.isDead() && at.distanceSquared(p.getLocation()) > 24 * 24;
            if (goes) h.w.teleport(at);
            int n = 0;
            for (Entity o : at.getWorld().getNearbyEntities(at, 24, 12, 24)) if (o instanceof Sheep sh && sh.isAdult() && !sh.isSheared() && n < 8) {
                sh.setSheared(true);
                org.bukkit.DyeColor col = sh.getColor();
                Material wool = Helper.mat((col == null ? "WHITE" : col.name()) + "_WOOL");
                core.bag(id).addItem(new ItemStack(wool == null ? Material.WHITE_WOOL : wool, 1 + ThreadLocalRandom.current().nextInt(3)));
                sh.getWorld().playSound(sh.getLocation(), Sound.ENTITY_SHEEP_SHEAR, 1f, 1f);
                n++;
            }
            core.dirty.add(id);
            core.say(p, "I found sheep " + (int) at.distance(p.getLocation()) + " blocks away, at " + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ() + ", and sheared " + n + ". The wool is in my bag.");
            Bukkit.getScheduler().runTaskLater(core.plugin, () -> {
                Helper.H back = core.helpers.get(id);
                if (goes && p.isOnline() && back != null && back.w != null && !back.w.isDead()) back.w.teleport(p.getLocation());
                then.run();
            }, goes ? 60L : 1L);
        });
    }

    void mineFrom(Player p, Helper.H h, Block t) {
        if (t == null || t.getType().isAir()) { p.sendMessage("Look at the block your helper should mine (within 6 blocks), or name it: mine sand."); return; }
        if (t.getType().getHardness() < 0 || t.getState() instanceof Container) { core.say(p, "I can't mine that."); return; }
        String kind = Helper.vein(t.getType());
        core.release(h);
        Set<Block> seen = new HashSet<>(List.of(t));
        Deque<Block> open = new ArrayDeque<>(List.of(t));
        while (!open.isEmpty() && h.queue.size() < Helper.VEIN_MAX) {             // the connected blocks of the same kind, nearest first
            Block b = open.poll();
            h.queue.add(b);
            for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
                Block n = b.getRelative(x, y, z);
                if (seen.add(n) && Helper.vein(n.getType()).equals(kind) && n.getLocation().distanceSquared(t.getLocation()) < 24 * 24) open.add(n);
            }
        }
        if (!Tag.LOGS.isTagged(t.getType())) {                              // tidy work: layer by layer from the top, row by row, back and forth
            List<Block> rows = new ArrayList<>(h.queue);
            rows.sort(Comparator.<Block>comparingInt(b -> -b.getY()).thenComparingInt(Block::getX).thenComparingInt(b -> (b.getX() & 1) == 0 ? b.getZ() : -b.getZ()));
            h.queue = new ArrayDeque<>(rows);
        }
        h.job = Helper.Job.MINE;
        h.done = h.stuck = 0;
        core.say(p, "On it: " + h.queue.size() + " " + (h.queue.size() == 1 ? "block" : "blocks") + " of " + Helper.word(t.getType()) + ", and nothing else.");
    }

    void startFarm(Player p, Helper.H h) {
        core.release(h);
        h.job = Helper.Job.FARM;
        h.anchor = h.w.getLocation();
        h.done = h.stuck = 0;
        core.say(p, "I will harvest and replant around here. Say stop when you want me back.");
    }

    void startFish(Player p, Helper.H h) {
        Block c = h.w.getLocation().getBlock(), water = null;
        double best = Double.MAX_VALUE;
        for (int x = -8; x <= 8; x++) for (int y = -4; y <= 1; y++) for (int z = -8; z <= 8; z++) {
            Block b = c.getRelative(x, y, z);
            double d = x * x + y * y + z * z;
            if (d < best && b.getType() == Material.WATER && b.getRelative(BlockFace.UP).getType().isAir()) { best = d; water = b; }
        }
        if (water == null) { core.say(p, "I need water close by to fish."); return; }
        core.release(h);
        h.job = Helper.Job.FISH;
        h.anchor = water.getLocation().add(0.5, 0.9, 0.5);
        h.done = h.stuck = h.cast = 0;
        core.say(p, "Gone fishing! Say stop when you want me back.");
    }

    static final Material[] JUNK = {Material.LILY_PAD, Material.LILY_PAD, Material.BOWL, Material.LEATHER, Material.LEATHER_BOOTS, Material.ROTTEN_FLESH,
            Material.STICK, Material.STRING, Material.BONE, Material.BONE, Material.INK_SAC, Material.TRIPWIRE_HOOK, Material.BAMBOO};

    /** One catch, with the odds of a rod with luck of the sea III: 11.3 % treasure (an enchanted book, bow or rod,
     *  a name tag, a saddle or a nautilus shell), 4.2 % junk, the rest fish. Rolled here: the game's own fishing
     *  tables want the rod that was cast, and there is no way to hand them one. */
    Collection<ItemStack> fishLoot(Location at) {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        int roll = r.nextInt(1000);
        if (roll < 113) {
            java.util.Random rnd = new java.util.Random();
            try {
                switch (r.nextInt(6)) {
                    case 0: return List.of(new ItemStack(Material.BOOK).enchantWithLevels(30, true, rnd));
                    case 1: return List.of(new ItemStack(Material.BOW).enchantWithLevels(22 + r.nextInt(9), true, rnd));
                    case 2: return List.of(new ItemStack(Material.FISHING_ROD).enchantWithLevels(22 + r.nextInt(9), true, rnd));
                    case 3: return List.of(new ItemStack(Material.NAME_TAG));
                    case 4: return List.of(new ItemStack(Material.SADDLE));
                    default: return List.of(new ItemStack(Material.NAUTILUS_SHELL));
                }
            } catch (RuntimeException ex) {
                return List.of(new ItemStack(Material.NAME_TAG));
            }
        }
        if (roll < 155) return List.of(new ItemStack(JUNK[r.nextInt(JUNK.length)]));
        int f = r.nextInt(100);
        return List.of(new ItemStack(f < 60 ? Material.COD : f < 85 ? Material.SALMON : f < 98 ? Material.PUFFERFISH : Material.TROPICAL_FISH));
    }

    void fish(Player p, Helper.H h) {
        Wolf w = h.w;
        if (h.anchor.getBlock().getType() != Material.WATER) { core.release(h); core.say(p, "The water is gone, so I stopped fishing."); return; }
        double dx = h.anchor.getX() - w.getX(), dz = h.anchor.getZ() - w.getZ();
        if (dx * dx + dz * dz > 3.5 * 3.5 && h.stuck < 8) {              // walk to the shore; after four seconds it casts from where it is
            core.go(h, h.anchor, 1.2);
            h.stuck++;
            h.cast = 0;
            return;
        }
        if (h.cast == 0) {
            w.getPathfinder().stopPathfinding();
            h.cast = 10 + ThreadLocalRandom.current().nextInt(21);        // a bite after 5 to 15 seconds
            core.swing(p, h);
            p.playSound(w.getLocation(), Sound.ENTITY_FISHING_BOBBER_THROW, 0.5f, 1f);
            return;
        }
        if (core.tick % 20 == 0) p.spawnParticle(Particle.FISHING, h.anchor, 3, 0.15, 0, 0.15, 0.01);
        if (--h.cast > 0) return;
        h.stuck = 0;
        core.swing(p, h);
        p.spawnParticle(Particle.SPLASH, h.anchor, 15, 0.2, 0.05, 0.2, 0.05);
        p.playSound(h.anchor, Sound.ENTITY_FISHING_BOBBER_SPLASH, 0.4f, 1f);
        Collection<ItemStack> loot = fishLoot(h.anchor);
        for (ItemStack i : loot) {
            Helper.bar(p, core.name(p.getUniqueId()) + " caught " + i.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            String magic = Helper.magic(i);
            if (magic != null) { core.say(p, "Look what I caught: " + Helper.word(i.getType()) + " with " + magic + "!"); core.awayLog(p.getUniqueId(), "FOUND " + Helper.word(i.getType()).toUpperCase(Locale.ROOT) + ": " + magic); }
        }
        core.keep(p, h, loot);
        core.xp(p.getUniqueId(), 1 + ThreadLocalRandom.current().nextInt(6));
        h.done++;
        if (core.bag(p.getUniqueId()).firstEmpty() == -1 && !core.unload(p, h, true)) {
            int n = h.done;
            core.release(h);
            core.say(p, "My bag is full, so I stopped fishing. I caught " + n + (n == 1 ? " thing." : " things."));
        }
    }

    Collection<ItemStack> harvest(Block b) {
        Material t = b.getType();
        if (b.getBlockData() instanceof Ageable a && Helper.SEEDS.containsKey(t)) {
            List<ItemStack> d = new ArrayList<>(b.getDrops(Helper.ench(Material.NETHERITE_HOE)));     // fortune III
            for (ItemStack i : d) if (i.getType() == Helper.SEEDS.get(t)) { i.setAmount(i.getAmount() - 1); break; }   // one goes back in the ground
            d.removeIf(Helper::empty);
            Helper.crack(b);
            a.setAge(0);
            b.setBlockData(a);
            return d;
        }
        if (t == Material.SUGAR_CANE) {
            List<Block> canes = new ArrayList<>();
            for (Block c = b; c.getType() == Material.SUGAR_CANE; c = c.getRelative(BlockFace.UP)) canes.add(c);
            Helper.crack(b);
            for (int i = canes.size() - 1; i >= 0; i--) canes.get(i).setType(Material.AIR);
            return List.of(new ItemStack(Material.SUGAR_CANE, canes.size()));
        }
        Collection<ItemStack> d = Helper.drops(b);
        Helper.crack(b);
        b.setType(Material.AIR);
        return d;
    }

    void work(Player p, Helper.H h) {
        Wolf w = h.w;
        if (h.job == Helper.Job.FISH) { fish(p, h); return; }
        if (h.job == Helper.Job.FARM && h.queue.isEmpty() && core.tick % 40 == 0) {
            List<Block> found = new ArrayList<>();
            Block c = h.anchor.getBlock();
            for (int x = -Helper.FARM_RANGE; x <= Helper.FARM_RANGE; x++) for (int y = -2; y <= 3; y++) for (int z = -Helper.FARM_RANGE; z <= Helper.FARM_RANGE; z++) {
                Block b = c.getRelative(x, y, z);
                if (Helper.ripe(b)) found.add(b);
            }
            Location at = w.getLocation();
            found.sort(Comparator.comparingDouble(b -> b.getLocation().distanceSquared(at)));
            h.queue.addAll(found);
            animals(p, h);
        }
        for (int n = 0; n < 2; n++) {                                     // up to four blocks a second
            Block b = h.queue.peek();
            while (b != null && (h.job == Helper.Job.FARM ? !Helper.ripe(b) : b.getType().isAir())) { h.queue.poll(); b = h.queue.peek(); }
            if (b == null) break;
            Location l = b.getLocation().add(0.5, 0, 0.5);
            double dx = l.getX() - w.getX(), dz = l.getZ() - w.getZ();
            boolean reach = dx * dx + dz * dz <= 3.5 * 3.5 && Math.abs(l.getY() - w.getY()) <= 12;
            if (!reach && h.stuck < 8) {                                  // walk there; after four seconds it reaches anyway
                core.go(h, l, 1.3);
                h.stuck++;
                return;
            }
            boolean gone = !reach && h.job == Helper.Job.MINE && !h.merged && (dx * dx + dz * dz > 12 * 12 || Math.abs(l.getY() - w.getY()) > 12);   // far away or deep down: she goes there
            h.queue.poll();
            h.stuck = 0;
            if (h.job == Helper.Job.MINE) { Material held = Helper.toolFor(b.getType()); if (held != null) core.wield(h, held); }
            core.swing(p, h);
            core.keep(p, h, h.job == Helper.Job.FARM ? harvest(b) : Helper.mineBlock(b));
            h.done++;
            if (gone) { w.teleport(l); h.anchor = l; }                    // into the hole she just made, and on from there
        }
        if (!h.queue.isEmpty()) return;
        if (h.job == Helper.Job.MINE) {
            int n = h.done;
            core.release(h);
            boolean away = !h.merged && (w.getWorld() != p.getWorld() || w.getLocation().distanceSquared(p.getLocation()) > 20 * 20);
            if (away) w.teleport(p.getLocation());                        // back from the mine
            core.say(p, "Done! " + n + (n == 1 ? " block" : " blocks") + "." + (away ? " I am back." : ""));
        } else if (!h.merged) core.walk(h, h.anchor, Helper.dist2(w, h.anchor), 5, 1.0);
    }
}
