package dev.etnan.creator;

import io.papermc.paper.command.brigadier.BasicCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.AbstractVillager;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.util.RayTraceResult;

/** Everything a player or the console tells a helper: chat (its name, a full stop, talk mode), the /helper
 *  command, orders for a helper whose owner is logged out, the settings, and the helper command on the console. */
final class Orders {
    final Helper core;

    Orders(Helper core) { this.core = core; }

    /** The orders that only change what the helper is like or remembers: these work with the owner away, straight
     *  into helpers.yml. {@code raw} keeps the capitals (for names). False when it is not one of them. */
    boolean settings(UUID id, String raw, String t, Consumer<String> tell) {
        String[] args = raw.split("\\s+");
        for (String[] w : Helper.WORDS) if (t.matches(w[1])) { args = w[0].split(" "); break; }
        Matcher nm = Pattern.compile("(?i)(?:your name is|you are called|call yourself|du heter|heter|kall deg)\\s+(.{2,32})").matcher(raw);
        if (nm.matches()) args = ("name " + nm.group(1)).split("\\s+");
        String sub = args[0].toLowerCase(Locale.ROOT);
        String rest = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length)).replace('|', ' ').trim();
        String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
        Helper.H h = core.helpers.get(id);                                            // the figure on watch, if there is one
        switch (sub) {
            case "name" -> {
                if (rest.length() < 2) { tell.accept("A name needs at least two letters: name <name>."); return true; }
                String name = rest.length() > 32 ? rest.substring(0, 32) : rest;
                core.conf.set(core.path(id, "name"), name);
                core.names.put(id, name.toLowerCase(Locale.ROOT));
                if (h != null) h.m.customName(Component.text(name));
                tell.accept("I am now called " + name + ".");
            }
            case "personality", "personlighet" -> tell.accept(core.personality(id, rest));
            case "callme" -> {
                if (rest.isEmpty()) { tell.accept("callme <name>"); return true; }
                core.conf.set(core.path(id, "nick"), rest.length() > 24 ? rest.substring(0, 24) : rest);
                tell.accept("I will call you " + rest + ".");
            }
            case "skin" -> {
                if (rest.isEmpty()) { tell.accept("skin <name>. Skins: " + String.join(", ", core.skins.names())); return true; }
                String want = rest.replace(' ', '_'), sl = Skins.slug(want);
                if (core.skins.packProfile(sl) != null) core.conf.set(core.path(id, "skin"), "pack:" + sl);
                else if (want.matches("[A-Za-z0-9_]{3,16}")) core.conf.set(core.path(id, "skin"), want);
                else { tell.accept("No skin called " + want + ". Skins: " + String.join(", ", core.skins.names())); return true; }
                if (h != null) {
                    ResolvableProfile rp = core.skins.packProfile(sl);
                    h.m.setProfile(rp != null ? rp : ResolvableProfile.resolvableProfile().name(want).build());
                }
                tell.accept(Helper.look(want) + " (I now look like " + want + ".)");
            }
            case "quiet", "torch", "light", "bridge", "pickup", "show" -> {
                boolean def = sub.equals("pickup");
                boolean v = a1.equals("on") || !a1.equals("off") && !core.flag(id, sub, def);
                core.conf.set(core.path(id, sub), v);
                if (sub.equals("show") && h != null) h.m.setVisibleByDefault(v);
                tell.accept(sub + " is now " + (v ? "on" : "off") + ".");
            }
            case "passive", "fight" -> {
                core.conf.set(core.path(id, "passive"), sub.equals("passive"));
                if (h != null && sub.equals("passive")) h.w.setTarget(null);
                tell.accept(sub.equals("passive") ? "I will not fight." : "I fight again.");
            }
            case "style" -> {
                if (!List.of("sword", "bow", "shield").contains(a1)) { tell.accept("style sword | bow | shield"); return true; }
                core.conf.set(core.path(id, "style"), a1);
                tell.accept("I fight with the " + a1 + " now.");
            }
            case "big", "small", "normal" -> {
                double v = sub.equals("normal") ? 1.0 : Math.max(Helper.MIN_SIZE, Math.min(core.MAX_SIZE, core.size(id) * (sub.equals("big") ? 1.5 : 1 / 1.5)));
                if (Math.abs(v - 1.0) < 0.1) v = 1.0;
                core.conf.set(core.path(id, "size"), v);
                if (h != null) { Helper.set(h.m, Attribute.SCALE, v); Helper.set(h.w, Attribute.MOVEMENT_SPEED, 0.38 * core.speed(id)); }
                tell.accept("My size is now " + Math.round(v * 100) + " % of normal.");
            }
            case "sethome", "remember" -> {
                boolean home = sub.equals("sethome");
                int at = home ? 1 : 2;                                    // sethome x y z [world] | remember <name> x y z [world]
                if (args.length < at + 3 || !args[at].matches("-?\\d+") || !args[at + 1].matches("-?\\d+") || !args[at + 2].matches("-?\\d+")) {
                    tell.accept(home ? "While you are not in the world I need the spot: sethome <x> <y> <z> [world]." : "While you are not in the world I need the spot: remember <name> <x> <y> <z> [world].");
                    return true;
                }
                Location old = core.home(id);
                World w = args.length > at + 3 ? Bukkit.getWorld(args[at + 3]) : old != null ? old.getWorld() : Bukkit.getWorlds().get(0);
                if (w == null) { tell.accept("There is no world called " + args[at + 3] + "."); return true; }
                Location l = new Location(w, Integer.parseInt(args[at]) + 0.5, Integer.parseInt(args[at + 1]), Integer.parseInt(args[at + 2]) + 0.5);
                String k = home ? "home" : "places." + Helper.slug(args[1]);
                core.conf.set(core.path(id, k), Helper.spot(l));
                if (home) { core.awayWork.awayLet(id); core.awayOre.remove(id); core.awayScan.remove(id); core.awayWork.yards.remove(id); }
                tell.accept((home ? "Home" : "The place " + Helper.slug(args[1]).replace('_', ' ')) + " is now " + w.getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ".");
            }
            case "places" -> {
                ConfigurationSection sec = core.conf.getConfigurationSection(core.path(id, "places"));
                List<String> known = new ArrayList<>();
                Location hm = core.home(id);
                if (hm != null) known.add("home (" + hm.getWorld().getName() + " " + hm.getBlockX() + " " + hm.getBlockY() + " " + hm.getBlockZ() + ")");
                if (sec != null) for (String k : sec.getKeys(false)) {
                    Location l = Helper.place(sec.getString(k));
                    known.add(k.replace('_', ' ') + (l == null ? "" : " (" + l.getWorld().getName() + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ() + ")"));
                }
                tell.accept(known.isEmpty() ? "I don't remember any places yet." : "I remember: " + String.join(", ", known) + ".");
            }
            case "forget" -> {
                String k = Helper.slug(rest);
                boolean had = !k.isEmpty() && core.conf.contains(core.path(id, "places." + k));
                core.conf.set(core.path(id, "places." + k), null);
                tell.accept(had ? "Forgotten: " + rest + "." : "I don't know a place called " + rest + ".");
            }
            case "sum" -> { tell.accept(core.awayWork.sum(id)); return true; }
            case "finds" -> { tell.accept(core.awayWork.finds(id)); return true; }
            case "bag" -> {
                Map<String, Integer> in = new java.util.TreeMap<>();
                for (ItemStack it : core.bag(id).getContents()) if (!Helper.empty(it)) in.merge(Helper.word(it.getType()), it.getAmount(), Integer::sum);
                List<String> out = new ArrayList<>();
                in.forEach((k, v) -> out.add(v + " " + k));
                tell.accept(out.isEmpty() ? "My bag is empty." : "In my bag: " + String.join(", ", out) + ".");
            }
            case "routines" -> {
                List<String> rs = core.routineList(id);
                StringBuilder sb = new StringBuilder(rs.isEmpty() ? "I have no routines." : "My routines:");
                for (int i = 0; i < rs.size(); i++) sb.append(" ").append(i + 1).append(") ").append(rs.get(i).replace("|", ": ")).append(i + 1 < rs.size() ? ";" : ".");
                tell.accept(sb.toString());
            }
            case "routine" -> {
                List<String> rs = core.routineList(id);
                if (a1.equals("clear") || a1.equals("reset")) { core.conf.set(core.path(id, "routines"), null); tell.accept("All " + rs.size() + " routines are gone."); }
                else if (a1.equals("remove") || a1.equals("delete")) {
                    int n = args.length < 3 || !args[2].matches("\\d{1,2}") ? 0 : Integer.parseInt(args[2]);
                    if (n < 1 || n > rs.size()) { tell.accept("Which one? I have " + rs.size() + "."); return true; }
                    String gone = rs.remove(n - 1);
                    core.conf.set(core.path(id, "routines"), rs);
                    tell.accept("Removed: " + gone.replace("|", ": ") + ".");
                } else {
                    boolean every = a1.equals("every") && args.length > 3 && args[2].matches("\\d{1,3}") && Integer.parseInt(args[2]) > 0;
                    if (!every && !Helper.TIMES.containsKey(a1) || args.length < (every ? 4 : 3)) { tell.accept("routine <morning|noon|evening|night|every <minutes>> <orders> | routine remove <number> | routine clear"); return true; }
                    if (rs.size() >= 10) { tell.accept("Ten routines is all I can keep in my head. Remove one first."); return true; }
                    String orders = String.join(" ", Arrays.copyOfRange(args, every ? 3 : 2, args.length)).replace('|', ' ').trim();
                    rs.add((every ? "every " + args[2] : a1) + "|" + orders);
                    core.conf.set(core.path(id, "routines"), rs);
                    tell.accept("Okay: " + (every ? "every " + args[2] + " minutes" : "every " + a1) + " I will: " + orders + ".");
                }
            }
            case "dismiss" -> { core.conf.set(core.path(id, "active"), false); core.despawn(id, false); core.awayWork.awayLet(id); tell.accept("I went home. Nothing happens until I am called again (summon)."); }
            case "summon" -> { core.conf.set(core.path(id, "active"), true); tell.accept("I am back on duty" + (core.flag(id, "watch", false) ? "." : ", but with no job for when you are away (say fish, farm, smelt, mine <ore>, explore or guard).")); }
            case "watch" -> {
                if (a1.equals("off")) return offline(id, "stop", tell);
                if (Helper.AWAY_JOBS.contains(a1)) return offline(id, a1.equals("mobfarm") ? "mobfarm" : a1.equals("farm") && args.length > 2 ? "jobplace " + String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : a1.equals("mine") ? "mine " + String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : a1, tell);
                core.conf.set(core.path(id, "watch"), true);
                tell.accept("I stay at home while you are away" + (core.conf.getString(core.path(id, "away.job"), "").isEmpty() ? " and guard." : " and " + core.conf.getString(core.path(id, "away.job")) + "."));
            }
            default -> { return false; }
        }
        core.save();
        return true;
    }

    /** An order for a helper whose owner is logged out. Everything happens at home. False when it is not something
     *  it can do without the owner in the world. */
    boolean offline(UUID id, String words, Consumer<String> tell) {
        String t = Helper.POLITE.matcher(words.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim().toLowerCase(Locale.ROOT);
        if (core.builder.buildOrder(id, null, t, tell)) return true;
        if (t.matches(".*\\b(joke|jokes|vits|vitser|spøk)\\b.*")) { tell.accept(core.joke(id)); return true; }
        if (settings(id, words.trim(), t, tell)) return true;
        Location hm = core.home(id);
        Matcher m = Pattern.compile("(?:trade for|trade|buy me|buy|kjøp meg|kjøp|handle|bytt til deg)\\s+(.{1,40})").matcher(t);
        if (m.matches()) {
            if (hm == null) { tell.accept("I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)."); return true; }
            if (!core.trade(id, null, hm, m.group(1), false, tell)) { }
            return true;
        }
        if (t.matches("trade|trades|handel|handle|kjøp|what can you buy|hva kan du kjøpe")) {
            if (hm == null) { tell.accept("I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)."); return true; }
            List<String> offers = new ArrayList<>();
            for (Entity e : hm.getWorld().getNearbyEntities(hm, Helper.HOME, 8, Helper.HOME))
                if (e instanceof AbstractVillager v) for (MerchantRecipe r : v.getRecipes()) {
                    if (r.getUses() >= r.getMaxUses() || offers.size() >= 40) continue;
                    List<String> cost = new ArrayList<>();
                    for (ItemStack c : r.getIngredients()) if (!Helper.empty(c)) cost.add(c.getAmount() + " " + Helper.word(c.getType()));
                    offers.add(r.getResult().getAmount() + " " + Helper.word(r.getResult().getType()) + (Helper.magic(r.getResult()) == null ? "" : " (" + Helper.magic(r.getResult()) + ")") + " for " + String.join(" + ", cost));
                }
            tell.accept(offers.isEmpty() ? "There is no villager with anything to sell within 100 blocks of home." : "The villagers at home sell: " + String.join("; ", offers) + ". Say trade and the item, like trade bread 3.");
            return true;
        }
        if (t.matches("explore|go exploring|utforsk|dra på oppdagelsesferd|oppdag")) {
            core.conf.set(core.path(id, "watch"), true);
            core.conf.set(core.path(id, "away.job"), "explore");
            for (String k : List.of("full", "why", "ms")) core.conf.set(core.path(id, "away." + k), null);
            core.save();
            tell.accept("I will explore the land around home while you are away, and remember the buildings I find." + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : ""));
            return true;
        }
        m = Pattern.compile("(?:mine|dig for|dig|find|look for|finn|let etter|grav etter|grav|hugg)\\s+(.{1,30})").matcher(t);
        if (m.matches()) {
            String target = Explorer.oreTarget(m.group(1));
            if (target == null) { tell.accept("I can dig for ores while you are away: diamond, coal, iron, gold, copper, redstone, lapis, emerald, quartz, ancient debris, obsidian. Not " + m.group(1) + "."); return true; }
            core.conf.set(core.path(id, "watch"), true);
            core.conf.set(core.path(id, "away.job"), "mine");
            core.conf.set(core.path(id, "away.target"), target);
            for (String k : List.of("full", "why", "ms")) core.conf.set(core.path(id, "away." + k), null);
            core.awayOre.remove(id);
            core.awayScan.remove(id);
            core.save();
            tell.accept("I will dig for " + target.toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home while you are away." + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : ""));
            return true;
        }
        m = Pattern.compile("(?:craft|make|lag|lage)\\s+(.{1,40})").matcher(t);
        if (m.matches()) { core.craft(id, null, hm, m.group(1), false, tell); return true; }
        m = Pattern.compile("(?:collect|collect from|hent alt fra|hent fra)\\s+(.{1,30})").matcher(t);
        Matcher m2 = Helper.COLLECT_WORDS.matcher(t);
        if (m.matches() || m2.matches()) {
            String where = m.matches() ? m.group(1) : m2.group(1);
            Location l = core.placeNamed(id, where);
            if (l == null) { tell.accept("I don't know a place called " + where + "."); return true; }
            int moved = 0;
            for (Inventory inv : Helper.stores(l))
                for (int j = 0; j < inv.getSize(); j++) {
                    ItemStack it = inv.getItem(j);
                    if (Helper.empty(it)) continue;
                    int before = it.getAmount();
                    Map<Integer, ItemStack> rest;
                    if (hm != null && core.myBarrel(id, hm.getWorld()) != null) { core.storage.putMine(id, List.of(it.clone())); rest = Map.of(); }
                    else {
                        rest = core.bag(id).addItem(it.clone());
                        for (Inventory to : Helper.stores(hm, Helper.HOME)) { if (rest.isEmpty()) break; rest = to.addItem(rest.values().iterator().next()); }
                    }
                    int left = rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount();
                    moved += before - left;
                    it.setAmount(left);
                    inv.setItem(j, left > 0 ? it : null);
                }
            core.dirty.add(id);
            tell.accept(moved == 0 ? "There was nothing to bring from " + where + " (or everything at home is full)." : "I brought " + moved + (moved == 1 ? " thing" : " things") + " from " + where + " home.");
            return true;
        }
        if (t.matches("(?:tidy|tidy up|sort|sort the chests|rydd|rydd opp|rydd kistene|sorter|sorter kistene)")) {
            int n = core.tidy(id);
            tell.accept(n < 0 ? (hm == null ? "I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : "I need at least two chests within 100 blocks of home.")
                    : n == 0 ? "The chests at home are tidy already." : "I tidied the chests at home: " + n + (n == 1 ? " thing" : " things") + " moved to where the rest of its kind is.");
            return true;
        }
        if (t.matches("(?:unload|empty your bag|empty the bag|tøm|tøm sekken)") && hm != null && core.myBarrel(id, hm.getWorld()) != null) {
            int moved = core.storage.putMine(id, core.bag(id));
            core.dirty.add(id);
            tell.accept("I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag in your own store in the magic barrel.");
            return true;
        }
        if (t.matches("(?:unload|empty your bag|empty the bag|tøm|tøm sekken)")) {
            Inventory bag = core.bag(id);
            int moved = 0;
            for (int i = 0; i < bag.getSize(); i++) {
                ItemStack it = bag.getItem(i);
                if (Helper.empty(it)) continue;
                int before = it.getAmount();
                for (Inventory inv : Helper.stores(hm, Helper.HOME)) {
                    Map<Integer, ItemStack> rest = inv.addItem(it.clone());
                    it.setAmount(rest.isEmpty() ? 0 : rest.values().iterator().next().getAmount());
                    if (it.getAmount() == 0) break;
                }
                moved += before - it.getAmount();
                bag.setItem(i, it.getAmount() > 0 ? it : null);
            }
            core.dirty.add(id);
            tell.accept("I put " + moved + (moved == 1 ? " thing" : " things") + " from my bag in the chests at home.");
            return true;
        }
        if (t.matches("(?:smelt now|smelt once|smelt|smelt nå)") && hm != null && core.conf.getString(core.path(id, "away.job"), "").equals("smelt")) {
            tell.accept("I took " + core.awayWork.awaySmelt(id, hm) + " things out of the furnaces at home and filled them again.");
            return true;
        }
        Matcher pj = Pattern.compile("jobplace (.+)").matcher(t);
        String spot = pj.matches() ? pj.group(1) : null;
        Matcher mfo = Pattern.compile("(?:run |drive |driv |kjør |work (?:at |on )?|jobb (?:på |i |ved )?)?(?:the )?mob ?farm(?:en)?\\b\\s*(.*)").matcher(t);
        if (spot == null && mfo.matches()) {
            String pick = core.farmPick(id, mfo.group(1));
            if (pick.startsWith("?")) { tell.accept(pick.substring(1)); return true; }
            spot = pick;
            if (core.conf.contains(core.path(id, "places." + spot))) core.farmAdd(id, spot);
        }
        if (spot == null) {
            Matcher fm = Pattern.compile("(?:farm|work|harvest|jobb|høst|arbeid)(?: at| on| in| på| i| ved)?(?: the)? (.{2,30})").matcher(t);
            if (fm.matches() && !Helper.slug(fm.group(1)).matches("home|hjemme|hjem|at_home|around_the_house") && core.conf.contains(core.path(id, "places." + Helper.slug(fm.group(1))))) spot = Helper.slug(fm.group(1));
        }
        if (spot != null) {
            core.conf.set(core.path(id, "watch"), true);
            core.conf.set(core.path(id, "away.job"), "place");
            core.conf.set(core.path(id, "away.place"), spot);
            core.conf.set(core.path(id, "away.full"), null);
            core.conf.set(core.path(id, "away.ms"), null);
            core.save();
            core.awayWork.yards.remove(id);
            core.awayWork.mobYards.remove(id);
            tell.accept("I will work at " + spot + " while you are away. " + "I look at what is there: fields I harvest and sow again, a mob farm I run (call the monsters, kill what comes to me, keep the drops and the experience for you), and where there is only water I fish."
                    + (core.placeNamed(id, spot) != null ? "" : " But I know no place called " + spot + " yet: stand there and say remember " + spot + ", or give me remember " + spot + " <x> <y> <z> [world].")
                    + (core.conf.getBoolean(core.path(id, "active")) ? "" : " But I am dismissed, so nothing happens until I am called."));
            return true;
        }
        String job = t.matches("(?:work|work at home|work around the house|do the chores|chores|jobb|jobb hjemme|arbeid hjemme|hjemmearbeid|gjør husarbeid)") ? "home" : t.matches(".*\\b(fish|fisk)\\w*.*") ? "fish" : t.matches(".*\\b(farm|harvest|høst)\\w*.*") ? "farm" : t.matches(".*\\bsmelt\\w*.*") ? "smelt"
                : t.matches(".*\\b(guard|vokt|pass på)\\w*.*") ? "guard" : t.matches(".*\\b(stop|stopp|off|av|nothing|ingenting)\\b.*") ? "off" : null;
        if (job == null) return false;
        core.conf.set(core.path(id, "watch"), job.equals("off") ? null : true);
        core.conf.set(core.path(id, "away.job"), job.equals("off") || job.equals("guard") ? null : job);
        core.conf.set(core.path(id, "away.rest"), job.equals("off") || job.equals("guard") ? true : null);          // told to do nothing: no chores by itself
        core.conf.set(core.path(id, "away.full"), null);
        core.conf.set(core.path(id, "away.ms"), null);                              // a new order: the clock starts again
        core.save();
        tell.accept((job.equals("off") ? "I do nothing while you are away." : job.equals("mobfarm") ? "I will work at the mob farm while you are away: call the monsters, kill what comes to me, and keep the drops and the experience for you."
                        + (core.conf.contains(core.path(id, "places.mobfarm")) ? "" : " But I do not know where it is yet: stand in the killing room and say remember mobfarm, or give me remember mobfarm <x> <y> <z> [world].")
                : "I will " + (job.equals("home") ? "work" : job) + " at home while you are away.")
                + (hm == null ? " But I have no home yet (stand at home in the game and say sethome, or give me sethome <x> <y> <z>)." : "")
                + (core.conf.getBoolean(core.path(id, "active")) ? "" : " But I am dismissed, so nothing happens until I am called."));
        return true;
    }

    /** From the server console or RCON: helper pause | resume | save | status | tell <player> <words>. */
    void console(org.bukkit.command.CommandSender to, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pause", "resume" -> {
                core.conf.set("settings.paused", sub.equals("pause") ? true : null);
                core.dirty.addAll(core.bags.keySet());
                core.flushBags();
                core.save();
                to.sendMessage(sub.equals("pause") ? "Helpers are paused: no work or guarding while their owners are away. Everything is saved. (helper resume)" : "Helpers work again.");
            }
            case "save" -> {
                core.dirty.addAll(core.bags.keySet());
                core.flushBags();
                core.save();
                to.sendMessage("Helper bags and progress are saved.");
            }
            case "status" -> {
                StringBuilder sb = new StringBuilder(core.paused() ? "Helpers are PAUSED." : "Helpers are running.");
                sb.append(" Work away from the owner stops after ").append(core.conf.getDouble("settings.away-hours", 2)).append(" hours.");
                for (String k : core.conf.getKeys(false)) {
                    UUID id;
                    try { id = UUID.fromString(k); } catch (IllegalArgumentException e) { continue; }
                    if (!core.conf.getBoolean(core.path(id, "active"))) continue;
                    String who = Bukkit.getOfflinePlayer(id).getName();
                    boolean on = Bukkit.getPlayer(id) != null;
                    String job = core.conf.getString(core.path(id, "away.job"), "guard");
                    String did = core.awayWork.awayDid(id);
                    sb.append("\n").append(who == null ? k : who).append(" (").append(core.name(id)).append("): ").append(on ? "online" : "offline")
                            .append(", when away: ").append(core.flag(id, "watch", false) ? job : "nothing").append(core.home(id) == null ? ", NO HOME SET" : "");
                    if (!on && core.flag(id, "watch", false)) sb.append(", worked ").append(core.conf.getLong(core.path(id, "away.ms"), 0) / 60_000).append(" min")
                            .append(did.isEmpty() ? "" : ", " + did).append(core.conf.getBoolean(core.path(id, "away.full"), false) ? ", STOPPED: chests full" : "");
                }
                to.sendMessage(sb.toString());
            }
            case "named" -> {                                             // helper named <helper name> <words>
                if (args.length < 3) { to.sendMessage("Usage: helper named <helper name> <words>"); return; }
                List<String> owners = new ArrayList<>();
                for (Map.Entry<UUID, String> n : core.names.entrySet())
                    if (n.getValue().equalsIgnoreCase(args[1])) { String who = Bukkit.getOfflinePlayer(n.getKey()).getName(); if (who != null) owners.add(who); }
                if (owners.size() != 1) { to.sendMessage(owners.isEmpty() ? "No helper is called " + args[1] + "." : "Several helpers are called " + args[1] + " (" + String.join(", ", owners) + "): use helper tell <player> <words>."); return; }
                String[] as = args.clone();
                as[0] = "tell";
                as[1] = owners.get(0);
                console(to, as);
            }
            case "tell" -> {
                if (args.length < 3) { to.sendMessage("Usage: helper tell <player> <words>"); return; }
                String words = String.join(" ", Arrays.copyOfRange(args, 2, args.length));
                Player pl = Bukkit.getPlayerExact(args[1]);
                if (pl != null) {
                    pl.sendMessage(Component.text("[console] " + core.name(pl.getUniqueId()) + ", " + words, NamedTextColor.GRAY));
                    talk(pl, words);
                    to.sendMessage("Told " + pl.getName() + "'s helper: " + words + " (the answer shows in the game)");
                    return;
                }
                org.bukkit.OfflinePlayer off = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (off == null || !core.conf.contains(off.getUniqueId().toString())) { to.sendMessage(args[1] + " has no helper (check the spelling)."); return; }
                UUID id = off.getUniqueId();
                String who = off.getName();
                if (!offline(id, words, said -> { said = core.voice(id, said); to.sendMessage(who + "'s helper: " + said); core.awayLog(id, "(from the console: " + words + ") " + said); }))
                    if (!words.trim().toLowerCase(Locale.ROOT).matches("help|hjelp|\\?|what can you do|hva kan du")) to.sendMessage(who + "'s helper: " + core.voice(id, core.shrug(id, words)));
                    else to.sendMessage(who + "'s helper: " + core.doing(id) + " What works while you are away:\n"
                            + "  jobs while away: mobfarm [name], farm <place> (at a saved place: fields, mob farm or fishing, whatever is there), work (a bit of everything at home), fish, farm, smelt, mine <ore>, explore, guard, stop\n"
                            + "  at once: joke, build house <x> <y> <z> [world], undo build; at home: sum, finds, tidy, unload, trade, trade <item> [times], craft <item> [n], collect <place>, bag\n"
                            + "  settings: personality <name> [name] | reset, name <name>, skin <name>, callme <name>, big, small, normal, style sword|bow|shield, passive, fight, quiet, torch, light, bridge, pickup, show [on|off]\n"
                            + "  places: sethome <x> <y> <z> [world], remember <name> <x> <y> <z> [world], places, forget <name>\n"
                            + "  routines: routine <morning|noon|evening|night|every N> <orders>, routines, routine remove <n>, routine clear\n"
                            + "  dismiss, summon. See also: helper status");
                core.flushBags();
            }
            default -> to.sendMessage("helper pause | resume | save | status | tell <player> <words>");
        }
    }

    /** Free text for the helper: the words it knows become a command, the rest goes to the chat bridge (which follows
     *  the server log, asks its model and answers with /helper say or another /helper command). */
    void talk(Player p, String text) {
        String clean = Helper.POLITE.matcher(text.replaceAll("[.!?\\s]+$", "").trim()).replaceAll("").trim();
        String t = clean.toLowerCase(Locale.ROOT);
        if (t.isEmpty()) { command(p, new String[] {"come"}); return; }
        if (core.builder.buildOrder(p.getUniqueId(), p, t, said -> core.say(p, said))) return;
        Matcher gf = Pattern.compile("(?:get|fetch|bring|grab|hent|ta med)(?: me| meg| us| oss)?(?: (?:some|the|noen|litt|all the|all))? (.+? (?:from|fra) .+)").matcher(t);
        if (gf.matches()) { command(p, ("fetch " + gf.group(1)).split(" ")); return; }
        Matcher mk = Pattern.compile("(?:mine|dig|dig up|grav|grav opp|hent)(?: (?:only|just|bare|kun|some|litt|the|all|all the|alt|all))* ([a-zæøå_ ]+?)(?:,? (?:not|and not|but not|ikke|og ikke|men ikke|here|her|please) .*)?").matcher(t);
        if (mk.matches() && Helper.mineKind(mk.group(1)) != null) { command(p, ("mine " + mk.group(1).trim()).split(" ")); return; }
        Matcher mfw = Pattern.compile("(?:run |drive |driv |kjør |work (?:at |on )?|jobb (?:på |i |ved )?)?(?:the )?mob ?farm(?:en)?\\b\\s*(.*)").matcher(t);
        if (mfw.matches()) { command(p, mfw.group(1).isEmpty() ? new String[] {"mobfarm"} : ("mobfarm " + mfw.group(1)).split(" ")); return; }
        Matcher pm = Pattern.compile("(?:personality|personlighet)\\b\\s*(.*)|(reset) (?:your )?personality|(nullstill) personlighet(?:en)?").matcher(t);
        if (pm.matches()) { core.say(p, core.personality(p.getUniqueId(), pm.group(1) != null ? pm.group(1) : "reset")); return; }
        Matcher fp = Pattern.compile("(?:farm|work|harvest|jobb|høst|arbeid)(?: at| on| in| på| i| ved)?(?: the)? (.{2,30})").matcher(t);
        if (fp.matches() && !Helper.slug(fp.group(1)).matches("home|hjemme|hjem|at_home|around_the_house") && core.conf.contains(core.path(p.getUniqueId(), "places." + Helper.slug(fp.group(1))))) {
            command(p, new String[] {"watch", "farm", Helper.slug(fp.group(1))});
            return;
        }
        for (String[] w : Helper.WORDS)
            if (t.matches(w[1])) { command(p, w[0].split(" ")); return; }
        Helper.H here = core.helpers.get(p.getUniqueId());
        Matcher m = Helper.FETCH_WORDS.matcher(t);
        if (m.matches() && here != null && core.fetch(p, here, m.group(1), true)) return;
        m = Helper.FIND_WORDS.matcher(t);
        if (m.matches() && core.find(p, m.group(1), true)) return;
        m = Helper.COLLECT_WORDS.matcher(t);
        if (m.matches() && core.placeNamed(p.getUniqueId(), m.group(1)) != null) { command(p, ("collect " + m.group(1)).split(" ")); return; }
        m = Helper.ROUTINE_WORDS.matcher(t);
        if (m.matches()) { command(p, ("routine " + Helper.TIME_WORDS.getOrDefault(m.group(1), m.group(1)) + " " + m.group(2)).split(" ")); return; }
        m = Helper.EVERY_WORDS.matcher(t);
        if (m.matches()) { command(p, ("routine every " + m.group(1) + " " + m.group(2)).split(" ")); return; }
        m = Helper.UNROUTINE_WORDS.matcher(t);
        if (m.matches()) { command(p, new String[] {"routine", "remove", m.group(1)}); return; }
        m = Helper.GIVE_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"give", m.group(1)}); return; }
        m = Helper.TRADE_WORDS.matcher(t);
        if (m.matches() && here != null && core.trade(p, here, m.group(1), true)) return;
        m = Helper.CALLME_WORDS.matcher(t);
        if (m.matches()) { command(p, ("callme " + clean.substring(clean.length() - m.group(1).length())).split(" ")); return; }
        m = Helper.JOIN_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"join", m.group(1)}); return; }
        m = Helper.REMEMBER_WORDS.matcher(t);
        if (m.matches()) { command(p, ("remember " + m.group(1)).split(" ")); return; }
        m = Helper.GOTO_WORDS.matcher(t);
        if (m.matches() && core.placeNamed(p.getUniqueId(), m.group(1)) != null) { command(p, ("goto " + m.group(1)).split(" ")); return; }
        m = Helper.SMELT_WORDS.matcher(t);
        if (m.matches() && here != null && Material.matchMaterial(Helper.slug(m.group(1)).toUpperCase(Locale.ROOT)) != null) { command(p, ("smelt " + m.group(1)).split(" ")); return; }
        m = Helper.CRAFT_WORDS.matcher(t);
        if (m.matches() && here != null && core.craft(p, here, m.group(1), true)) return;
        m = Helper.HELP_WORDS.matcher(t);
        if (m.matches() && Bukkit.getPlayer(m.group(1)) != null) { command(p, new String[] {"help", m.group(1)}); return; }
        m = Helper.SKIN_WORDS.matcher(t);
        if (m.matches()) { command(p, ("skin " + m.group(1)).split(" ")); return; }
        m = Helper.NAME_WORDS.matcher(t);
        if (m.matches()) { command(p, ("name " + clean.substring(clean.length() - m.group(1).length())).split(" ")); return; }
        long now = System.currentTimeMillis();
        if (now - core.asked.getOrDefault(p.getUniqueId(), 0L) < 3000) { Helper.bar(p, "One thing at a time…"); return; }
        core.asked.put(p.getUniqueId(), now);
        if (!core.helpers.containsKey(p.getUniqueId())) command(p, new String[] {"summon"});
        String name = core.name(p.getUniqueId());
        core.plugin.getLogger().info("HELPER_ASK " + p.getName() + "|" + name.replace('|', ' ') + "|" + (core.context(p) + core.personaNote(p.getUniqueId())).replace('|', ' ') + "|" + text.replaceAll("\\s+", " "));
        Helper.bar(p, name + " is thinking…");
    }

    static final String USAGE = "/helper follow | stay | come | guard | passive | fight | mine | farm | fish | torch | light | bridge | home | sethome | unload | fetch <item> [n] | find <thing> | ride | show | merge | split | big | small | normal | help <player> | attack | join <player> | craft <item> [n] | smelt [item] | remember <name> | goto <name> | places | watch | dance | pickup | bag | unequip | stop"
            + " | quiet | skin <name> | name <name> | dismiss. Right-click it: stay/follow, or give it the armour or weapon in your hand."
            + " Sneak + right-click: its bag. Say its name in chat and it comes; its name and a question, and it answers.";

    void register(Commands r) {
        r.register("helper", "Your helper: /helper [follow|stay|come|guard|mine|farm|torch|bag|skin <name>|name <name>|dismiss|…]", new BasicCommand() {
            @Override
            public void execute(CommandSourceStack src, String[] args) {
                if (!(src.getExecutor() instanceof Player p)) { console(src.getSender(), args); return; }
                command(p, args);
            }

            @Override
            public Collection<String> suggest(CommandSourceStack src, String[] args) {
                if (args.length <= 1) {
                    String a = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
                    return Helper.SUBS.stream().filter(s -> s.startsWith(a)).toList();
                }
                if (args.length == 2 && (args[0].equalsIgnoreCase("goto") || args[0].equalsIgnoreCase("forget")) && src.getExecutor() instanceof Player pl) {
                    ConfigurationSection sec = core.conf.getConfigurationSection(core.path(pl.getUniqueId(), "places"));
                    return sec == null ? List.of() : new ArrayList<>(sec.getKeys(false));
                }
                if (args.length == 2 && args[0].equalsIgnoreCase("watch")) return List.of("on", "off", "guard", "fish", "farm", "smelt", "mine", "explore", "home", "mobfarm");
                if (args.length == 2 && args[0].equalsIgnoreCase("style")) return List.of("sword", "bow", "shield");
                if (args.length == 2 && args[0].equalsIgnoreCase("routine")) return List.of("morning", "noon", "evening", "night", "every", "remove", "clear");
                if (args.length == 2 && (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("join") || args[0].equalsIgnoreCase("give")))
                    return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(n -> !n.equals(src.getSender().getName())).toList();
                if (args.length == 2 && args[0].equalsIgnoreCase("skin")) {
                    List<String> s = new ArrayList<>(core.skins.names());
                    Bukkit.getOnlinePlayers().forEach(pl -> s.add(pl.getName()));
                    String part = args[1].toLowerCase(Locale.ROOT);          // "boy" also finds blueboy and coolboy
                    s.removeIf(n -> !n.toLowerCase(Locale.ROOT).contains(part));
                    return s;
                }
                return List.of();
            }
        });
    }

    /** Turns a yes/no setting around (or sets it with on/off) and returns the new value. */
    boolean toggle(UUID id, String k, boolean def, String[] args) {
        boolean v = args.length > 1 ? args[1].equalsIgnoreCase("on") : !core.flag(id, k, def);
        core.conf.set(core.path(id, k), v);
        core.save();
        return v;
    }

    void command(Player p, String[] args) {
        UUID id = p.getUniqueId();
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String rest = args.length < 2 ? "" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
        Helper.H h = core.helpers.get(id);
        if (h == null && List.of("sleep", "wake", "follow", "stay", "guard", "mine", "farm", "fish", "bag", "stop", "home", "unload", "fetch", "ride", "merge", "split", "big", "small", "normal", "max", "min", "defend", "help", "attack", "join", "craft", "smelt", "goto", "dance", "repair", "collect", "trade", "give", "scout", "wayback", "hide", "tag", "treasure", "hint").contains(sub)) {
            p.sendMessage("You have no helper here: /helper");
            return;
        }
        if (h != null && h.merged && List.of("stay", "guard", "ride", "come", "show", "help", "join", "dance", "hide", "tag").contains(sub)) {
            core.say(p, "We are one right now. Say split first.");
            return;
        }
        switch (sub) {
            case "help" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                if (t == null) { core.say(p, args.length < 2 ? "Who should I help? Say help and a player's name." : "I can't find " + args[1] + "."); return; }
                if (t == p) { core.stay(p, h, false); return; }
                core.release(h);
                h.ward = t.getUniqueId();
                t.showEntity(core.plugin, h.m);
                h.w.teleport(t.getLocation());
                core.say(p, "I will look after " + t.getName() + " until you call me back.");
                t.sendMessage(Component.text("<" + core.name(id) + "> " + p.getName() + " sent me to look after you."));
            }
            case "big" -> core.resize(p, h, core.size(id) * 1.5, true);
            case "small" -> core.resize(p, h, core.size(id) / 1.5, true);
            case "normal" -> core.resize(p, h, 1.0, true);
            case "defend" -> {
                boolean on = !(args.length > 1 && args[1].equalsIgnoreCase("off"));
                if (h.merged) { h.defend = on; core.say(p, on ? "I strike what comes near us. Say defend off and the fighting is yours again." : "The fighting is yours again."); }
                else { core.conf.set(core.path(id, "passive"), !on); core.save(); core.say(p, on ? "I keep the monsters off you." : "I leave the monsters alone."); }
            }
            case "max" -> core.resize(p, h, core.MAX_SIZE, true);
            case "min" -> core.resize(p, h, Helper.MIN_SIZE, true);
            case "merge" -> core.merge(p, h);
            case "split" -> core.split(p, h);
            case "", "summon" -> {
                if (h != null && (sub.isEmpty() || h.merged)) { p.sendMessage(Component.text(USAGE, NamedTextColor.GOLD)); return; }
                core.conf.set(core.path(id, "active"), true);
                core.save();
                core.spawn(p, null);
                p.sendMessage(Component.text("Your helper is here! Right-click it to make it wait or follow. /helper for more.", NamedTextColor.GOLD));
            }
            case "follow" -> core.stay(p, h, false);
            case "stay" -> core.stay(p, h, true);
            case "mobfarm" -> core.mobfarm(p, h, rest);
            case "continue" -> core.carryOn(p, h);
            case "rescue" -> core.rescue(p, h);
            case "stop" -> {
                boolean worked = h.job != Helper.Job.NONE;
                core.stay(p, h, false);
                if (worked) core.say(p, "Okay, I stopped.");
            }
            case "come" -> {
                if (h == null) { command(p, new String[] {"summon"}); return; }
                h.w.teleport(p.getLocation());
                core.stay(p, h, false);
            }
            case "guard" -> {
                core.release(h);
                h.stance = Helper.Stance.GUARD;
                h.anchor = h.w.getLocation();
                Helper.bar(p, "Your helper guards this spot");
            }
            case "passive", "fight" -> {
                core.conf.set(core.path(id, "passive"), sub.equals("passive"));
                core.save();
                if (h != null && sub.equals("passive")) h.w.setTarget(null);
                Helper.bar(p, sub.equals("passive") ? "Your helper will not fight" : "Your helper fights for you");
            }
            case "mine" -> core.startMine(p, h, rest);
            case "farm" -> core.startFarm(p, h);
            case "fish" -> core.startFish(p, h);
            case "light" -> Helper.bar(p, toggle(id, "light", false, args) ? "Your helper carries a light" : "Your helper put the light out");
            case "bridge" -> Helper.bar(p, toggle(id, "bridge", false, args) ? "Your helper builds under your feet (look up for steps)" : "No more bridge building");
            case "show" -> {
                boolean v = toggle(id, "show", false, args);
                if (h != null) h.m.setVisibleByDefault(v);
                Helper.bar(p, v ? "Everyone can see your helper" : "Only you can see your helper");
            }
            case "sethome" -> {
                Location l = p.getLocation();
                core.conf.set(core.path(id, "home"), Helper.spot(l));
                core.save();
                core.awayWork.yards.remove(id);
                core.say(p, "Got it, this is home. I bring my things to the chests here.");
            }
            case "home" -> {
                Location l = core.home(id);
                if (l == null) { core.say(p, "I don't know where home is. Stand there and say sethome."); return; }
                core.travel(p, h, l);
                core.say(p, "Home again!");
            }
            case "unload" -> core.unload(p, h, false);
            case "fetch" -> core.fetch(p, h, rest, false);
            case "find" -> core.find(p, rest, false);
            case "ride" -> core.startRide(p, h);
            case "torch" -> Helper.bar(p, toggle(id, "torch", false, args) ? "Your helper puts torches where it is dark" : "No more torches");
            case "pickup" -> Helper.bar(p, toggle(id, "pickup", true, args) ? "Your helper picks things up" : "Your helper leaves things on the ground");
            case "talk" -> {
                boolean on = args.length > 1 ? args[1].equalsIgnoreCase("on") : !core.talking.contains(id);
                if (on) core.talking.add(id); else core.talking.remove(id);
                core.conf.set(core.path(id, "talk"), on ? true : null);                 // kept over logouts and restarts
                core.save();
                p.sendMessage(Component.text(on ? "Talk mode: everything you write now goes to " + core.name(id) + " and nobody else sees it. Say \"h\" or \"talk off\" to chat with the others again."
                        : "Talk mode is off: your chat goes to everyone again. A line that starts with a full stop still goes to " + core.name(id) + ".", NamedTextColor.GRAY));
            }
            case "quiet" -> Helper.bar(p, toggle(id, "quiet", false, args) ? "Your helper keeps quiet" : "Your helper talks again");
            case "bag", "chest" -> p.openInventory(core.bag(id));
            case "unequip" -> {
                ItemStack[] g = core.gear(id);
                boolean any = false;
                for (int i = 0; i < g.length; i++) { any |= !Helper.empty(g[i]); Helper.give(p, g[i]); g[i] = null; }
                core.store(id, "gear", g);
                core.save();
                if (h != null) core.dress(id, h);
                Helper.bar(p, any ? "You got its gear back" : "It has none of your gear");
            }
            case "hello" -> {
                if (h == null) command(p, new String[] {"summon"});
                h = core.helpers.get(id);
                if (h != null && !h.merged) h.dance = 80;              // a wave, then a little dance
                core.say(p, "Hi " + core.nick(p) + "!");
            }
            case "dance" -> h.dance = 50;
            case "sleep" -> core.sleep(p, h);
            case "wake" -> { boolean was = h.sleep || h.bedGo != null; core.stay(p, h, false); if (was) core.say(p, "I am up! What do we do?"); }
            case "scout" -> core.scout(p, h);
            case "wayback" -> core.wayback(p, h);
            case "hide" -> core.startGame(p, h, true);
            case "tag" -> core.startGame(p, h, false);
            case "treasure" -> core.startTreasure(p, h);
            case "hint" -> core.say(p, h.treasure == null ? "There is no treasure hidden right now. Say treasure and I bury one." : core.clue(p, h));
            case "style" -> {
                String st = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (!List.of("sword", "bow", "shield").contains(st)) { p.sendMessage("Usage: /helper style <sword|bow|shield>"); return; }
                core.conf.set(core.path(id, "style"), st);
                core.save();
                if (h != null) { h.foe = null; h.w.setTarget(null); }
                Helper.bar(p, st.equals("bow") ? "Your helper shoots from where it stands" : st.equals("shield") ? "Your helper draws the monsters onto itself" : "Your helper fights up close");
            }
            case "build" -> { if (!core.builder.buildOrder(id, p, String.join(" ", args).toLowerCase(Locale.ROOT), said -> core.say(p, said))) core.say(p, "I can build a little house: build house (here, in front of you), build house <x> <y> <z>, and undo build."); }
            case "sum" -> core.say(p, core.awayWork.sum(id));
            case "log" -> core.say(p, core.conf.getString(core.path(id, "away.report"), "").isEmpty() ? "Nothing to tell from the last time you were away." : core.conf.getString(core.path(id, "away.report")));
            case "finds" -> core.say(p, core.awayWork.finds(id));
            case "shear" -> core.woolTrip(p, () -> { });
            case "tidy" -> {
                List<Inventory> here = Helper.stores(p.getLocation(), 16);               // the chests where its owner stands, or else those at home
                Location hm = core.home(id);
                boolean atHome = hm != null && hm.getWorld() == p.getWorld() && hm.distanceSquared(p.getLocation()) <= Helper.HOME * Helper.HOME;
                boolean near = here.size() >= 2 && !atHome;
                int n = near ? core.tidy(here) : core.tidy(id);
                core.say(p, n < 0 ? (here.size() == 1 ? "There is only one chest here, and I" : "I see no chests here, and I") + " need at least two chests within 100 blocks of home to tidy there (say sethome)."
                        : n == 0 ? "The chests " + (near ? "here" : "at home") + " are tidy already."
                        : "Done: " + n + (n == 1 ? " thing" : " things") + " in the chests " + (near ? "here" : "at home") + " moved to where the rest of its kind is."
                        + (core.tidyStuck > 0 && n >= 0 ? " " + core.tidyStuck + (core.tidyStuck == 1 ? " thing sits" : " things sit") + " in a chest for something else and I found no better chest: an empty chest there would help." : ""));
            }
            case "repair" -> core.repair(p, h);
            case "collect" -> core.collect(p, h, rest);
            case "trade" -> core.trade(p, h, rest, false);
            case "give" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                ItemStack it = p.getInventory().getItemInMainHand();
                if (t == null || t == p) { core.say(p, args.length < 2 ? "Who should get it? Say give and a player's name." : "I can't find " + args[1] + "."); return; }
                if (Helper.empty(it)) { core.say(p, "Hold what I should bring to " + t.getName() + " in your hand."); return; }
                String what = it.getAmount() + " " + Helper.word(it.getType());
                p.getInventory().setItemInMainHand(null);
                Helper.give(t, it);
                core.zip(p, h);
                t.playSound(t.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.6f, 1f);
                core.say(p, "I brought " + what + " to " + t.getName() + ".");
                t.sendMessage(Component.text("<" + core.name(id) + "> " + p.getName() + " sent you " + what + "."));
            }
            case "routines" -> {
                List<String> rs = core.routineList(id);
                if (rs.isEmpty()) { core.say(p, "I have no routines. Say for example: every morning farm and smelt."); return; }
                StringBuilder sb = new StringBuilder("My routines:");
                for (int i = 0; i < rs.size(); i++) sb.append(" ").append(i + 1).append(") ").append(rs.get(i).replace("|", ": ")).append(i + 1 < rs.size() ? ";" : ".");
                core.say(p, sb + " Remove one with: remove routine <number>. Remove all with: clear routines.");
            }
            case "routine" -> {
                List<String> rs = core.routineList(id);
                String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (a1.equals("clear") || a1.equals("reset")) {
                    core.conf.set(core.path(id, "routines"), null);
                    core.save();
                    if (h != null) h.ran.clear();
                    core.say(p, rs.isEmpty() ? "I had no routines." : "All " + rs.size() + " routines are gone. A fresh start!");
                    return;
                }
                if (a1.equals("remove") || a1.equals("delete")) {
                    int n = args.length < 3 || !args[2].matches("\\d{1,2}") ? 0 : Integer.parseInt(args[2]);
                    if (n < 1 || n > rs.size()) { core.say(p, rs.isEmpty() ? "I have no routines." : "Which one? I have " + rs.size() + ". Say routines to see them."); return; }
                    String gone = rs.remove(n - 1);
                    core.conf.set(core.path(id, "routines"), rs);
                    core.save();
                    core.say(p, "Removed: " + gone.replace("|", ": ") + ".");
                    return;
                }
                boolean every = a1.equals("every") && args.length > 3 && args[2].matches("\\d{1,3}") && Integer.parseInt(args[2]) > 0;
                if (!every && !Helper.TIMES.containsKey(a1) || args.length < (every ? 4 : 3)) {
                    p.sendMessage("Usage: /helper routine <morning|noon|evening|night|every <minutes>> <orders>, /helper routine remove <number>, /helper routine clear");
                    return;
                }
                if (rs.size() >= 10) { core.say(p, "Ten routines is all I can keep in my head. Remove one first."); return; }
                String orders = String.join(" ", Arrays.copyOfRange(args, every ? 3 : 2, args.length)).replace('|', ' ').trim();
                rs.add((every ? "every " + args[2] : a1) + "|" + orders);
                core.conf.set(core.path(id, "routines"), rs);
                core.save();
                core.say(p, "Okay: " + (every ? "every " + args[2] + " minutes" : "every " + a1) + " I will: " + orders + ". (Where I am standing then.)");
            }
            case "callme" -> {
                String nick = rest.replace('|', ' ').trim();
                if (nick.isEmpty()) { p.sendMessage("Usage: /helper callme <name>"); return; }
                if (nick.length() > 24) nick = nick.substring(0, 24);
                core.conf.set(core.path(id, "nick"), nick);
                core.save();
                core.say(p, "Okay, " + nick + " it is!");
            }
            case "attack" -> {
                Location eye = p.getEyeLocation();
                RayTraceResult hit = p.getWorld().rayTraceEntities(eye, eye.getDirection(), 48, 0.4,
                        e -> e instanceof LivingEntity && !(e instanceof Player) && !(e instanceof Mannequin) && core.ownerOf(e) == null);
                if (hit == null || !(hit.getHitEntity() instanceof LivingEntity le)) { core.say(p, "Look at what I should attack."); return; }
                String what = le.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
                if (h.merged) { le.damage(core.damage(id), p); p.swingMainHand(); return; }
                core.release(h);
                h.ordered = le.getUniqueId();
                if (Helper.dist2(h.w, le.getLocation()) > 12 * 12) h.w.teleport(le.getLocation());
                h.w.setTarget(le);
                Helper.bar(p, "Your helper goes for the " + what);
            }
            case "join" -> {
                Player t = args.length < 2 ? null : Bukkit.getPlayer(args[1]);
                Helper.H o = t == null || t == p ? null : core.helpers.get(t.getUniqueId());
                if (o == null || o.merged || (o.job == Helper.Job.NONE && o.stance != Helper.Stance.GUARD)) {
                    core.say(p, t == null ? "Whose helper should I join? Say join and a player's name." : t.getName() + "'s helper is not doing a job I can join.");
                    return;
                }
                core.release(h);
                h.anchor = o.anchor;
                h.done = h.stuck = h.cast = 0;
                if (o.job == Helper.Job.NONE) h.stance = Helper.Stance.GUARD;
                else { h.job = o.job; h.queue = o.queue; }             // one shared list: they split the work
                h.w.teleport(o.w.getLocation());
                core.say(p, "I am working with " + t.getName() + "'s helper now. Say stop to get me back.");
                t.sendMessage(Component.text("<" + core.name(t.getUniqueId()) + "> " + p.getName() + "'s helper " + core.name(id) + " is helping me."));
            }
            case "craft" -> core.craft(p, h, rest, false);
            case "smelt" -> core.smelt(p, h, rest);
            case "watch" -> {
                String a1 = args.length < 2 ? "" : args[1].toLowerCase(Locale.ROOT);
                if (!Helper.AWAY_JOBS.contains(a1)) {
                    boolean on = toggle(id, "watch", false, args);
                    String job = core.conf.getString(core.path(id, "away.job"), "");
                    Helper.bar(p, !on ? "Your helper leaves with you when you log out" : job.isEmpty() ? "Your helper guards home while you are logged out" : "Your helper will " + job + " at home while you are logged out");
                    return;
                }
                String at = a1.equals("mobfarm") ? "mobfarm" : a1.equals("farm") && args.length > 2 ? String.join(" ", Arrays.copyOfRange(args, 2, args.length)) : null;
                if (at != null) {                                         // work at a saved place
                    if (core.placeNamed(id, at) == null) { core.say(p, "I know no place called " + at + ". Stand there and say remember " + at + " first."); return; }
                    core.conf.set(core.path(id, "watch"), true);
                    core.conf.set(core.path(id, "away.job"), "place");
                    core.conf.set(core.path(id, "away.place"), at);
                    core.save();
                    core.awayWork.yards.remove(id);
                    core.awayWork.mobYards.remove(id);
                    core.say(p, "When you log out I will work at " + at + ". " + "I look at what is there: fields I harvest and sow again, a mob farm I run (call the monsters, kill what comes to me, keep the drops and the experience for you), and where there is only water I fish.");
                    return;
                }
                if (a1.equals("mine")) {
                    String target = args.length < 3 ? null : Explorer.oreTarget(String.join(" ", Arrays.copyOfRange(args, 2, args.length)));
                    if (target == null) { core.say(p, "Dig for what? Say for example: watch mine diamond. I know diamond, coal, iron, gold, copper, redstone, lapis, emerald, quartz, ancient debris and obsidian."); return; }
                    core.conf.set(core.path(id, "away.target"), target);
                    core.awayOre.remove(id);
                    core.awayScan.remove(id);
                }
                core.conf.set(core.path(id, "watch"), true);
                core.conf.set(core.path(id, "away.job"), a1.equals("guard") ? null : a1);
                core.conf.set(core.path(id, "away.rest"), a1.equals("guard") ? true : null);
                core.save();
                String hours = String.valueOf(core.conf.getDouble("settings.away-hours", 2)).replaceAll("\\.0$", "");
                core.say(p, core.home(id) == null ? "I need a home for that first. Stand at home and say sethome."
                        : a1.equals("guard") ? "When you log out I stay and guard home."
                        : "When you log out I will " + (a1.equals("fish") ? "fish in the water at home" : a1.equals("farm") ? "harvest and replant the fields at home"
                                : a1.equals("mine") ? "dig for " + core.conf.getString(core.path(id, "away.target"), "ore").toLowerCase(Locale.ROOT).replace('_', ' ') + " within 128 blocks of home"
                                : a1.equals("mobfarm") ? "work at the mob farm" + (core.conf.contains(core.path(id, "places.mobfarm")) ? "" : " (stand in its killing room and say remember mobfarm first)") + ": call the monsters, kill what comes to me and keep the drops and the experience for you"
                                : a1.equals("home") ? "work at home: fish, tend the fields, feed and shear the animals, slaughter when there are more than twelve grown ones of a kind, keep the furnaces going and the doors and gates shut"
                                : a1.equals("explore") ? "explore the land around home and remember the buildings I find" : "keep the furnaces at home going, with ore and fuel from the chests")
                                + ", for up to " + hours + " hours, also when nobody else is here. It goes in the chests at home, and I tell you how it went when you are back.");
            }
            case "pause", "resume" -> {
                if (!p.isOp()) { core.say(p, "Only an operator can do that."); return; }
                console(p, new String[] {sub});
            }
            case "remember" -> {
                String k = Helper.slug(rest);
                if (k.isEmpty()) { p.sendMessage("Usage: /helper remember <name>"); return; }
                core.conf.set(core.path(id, "places." + k), Helper.spot(p.getLocation()));
                core.save();
                core.say(p, "I will remember this place as " + k.replace('_', ' ') + ".");
            }
            case "forget" -> {
                String k = Helper.slug(rest);
                boolean had = !k.isEmpty() && core.conf.contains(core.path(id, "places." + k));
                core.conf.set(core.path(id, "places." + k), null);
                core.save();
                core.say(p, had ? "Forgotten." : "I don't know a place called " + rest + ".");
            }
            case "places" -> {
                ConfigurationSection sec = core.conf.getConfigurationSection(core.path(id, "places"));
                List<String> known = sec == null ? new ArrayList<>() : new ArrayList<>(sec.getKeys(false));
                if (core.home(id) != null) known.add(0, "home");
                core.say(p, known.isEmpty() ? "I don't remember any places yet. Say: remember this place as the mine." : "I remember: " + String.join(", ", known).replace('_', ' ') + ".");
            }
            case "goto" -> {
                Location l = core.placeNamed(id, rest);
                if (l == null) { core.say(p, "I don't know a place called " + rest + "."); return; }
                core.travel(p, h, l);
                core.say(p, "Here we are!");
            }
            case "skin" -> {
                if (args.length < 2) { p.sendMessage("Usage: /helper skin <skin from skins/ or a Minecraft player name>"); return; }
                String want = String.join("_", Arrays.copyOfRange(args, 1, args.length));
                String s = Skins.slug(want);
                if (core.skins.packProfile(s) != null) core.conf.set(core.path(id, "skin"), "pack:" + s);
                else if (args.length == 2 && args[1].matches("[A-Za-z0-9_]{3,16}")) core.conf.set(core.path(id, "skin"), args[1]);
                else {
                    List<String> have = core.skins.names();
                    p.sendMessage("No skin called " + want + ". " + (have.isEmpty() ? "No skins yet" : "Skins: " + String.join(", ", have))
                            + ". New PNG files in skins/ on the server are ready about 20 s after you save them. A Minecraft player name (3-16 letters) works too.");
                    return;
                }
                core.save();
                if (h != null) h.m.setProfile(core.profile(p));
                if (core.skins.packProfile(s) != null && core.skins.signed(s) == null) core.plugin.getLogger().info("HELPER_SIGN " + p.getName() + "|" + s);   // ready for the next merge
                if (h != null && h.merged) core.wearSkin(p, false);
                core.say(p, Helper.look(want));
            }
            case "name" -> {
                String name = rest.replace('|', ' ').trim();
                if (name.length() < 2) { p.sendMessage("Usage: /helper name <name> (at least two letters)"); return; }
                if (name.length() > 32) name = name.substring(0, 32);
                core.conf.set(core.path(id, "name"), name);
                core.save();
                core.names.put(id, name.toLowerCase(Locale.ROOT));
                if (h != null) h.m.customName(core.title(p));
                p.sendMessage(Component.text("Your helper is now called " + name + ". Say \"" + name + "\" in chat and it comes.", NamedTextColor.GOLD));
            }
            case "dismiss", "off" -> {
                core.conf.set(core.path(id, "active"), false);
                core.save();
                core.despawn(id, true);
                p.sendMessage(Component.text("Your helper went home. /helper brings it back.", NamedTextColor.GOLD));
            }
            case "say" -> { if (!rest.isEmpty()) core.say(p, rest, false); }
            case "personality" -> core.say(p, core.personality(id, rest));
            case "buildplan" -> core.builder.buildPlan(id, said -> core.say(p, said));
            case "ask" -> talk(p, rest);
            default -> talk(p, String.join(" ", args));
        }
    }
}
