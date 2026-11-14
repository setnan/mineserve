package dev.etnan.creator;

import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Input;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

/** The helper and its owner moving together: merging (the helper inside you, with its size, strength, light and
 *  flight, and its jobs done on autopilot), and bridges or steps under your feet. */
final class Movement {
    final Helper core;

    Movement(Helper core) { this.core = core; }

    /** A block from the bag (cobblestone when it has none) where the owner is about to step: under the next step
     *  when walking straight ahead, as a step up when looking up. Not when looking down, so you can still climb down. */
    void bridge(Player p, Helper.H h) {
        if (p.isFlying() || p.isGliding() || p.isInsideVehicle() || p.isSwimming() || p.isDead() || Helper.dist2(h.w, p.getLocation()) > 24 * 24) return;
        Location l = p.getLocation();
        if (l.getPitch() > 40 || !l.clone().subtract(0, 0.1, 0).getBlock().getType().isSolid()) return;    // only with ground under you
        Vector d = l.getDirection().setY(0);
        if (d.lengthSquared() < 0.01) return;
        Block feet = l.getBlock(), ahead = l.clone().add(d.normalize()).getBlock();
        if (ahead.equals(feet)) return;
        Block spot = l.getPitch() < -30 ? ahead : ahead.getRelative(BlockFace.DOWN);
        if (!(spot.isEmpty() || spot.isLiquid())) return;
        if (l.getPitch() < -30 && !(spot.getRelative(BlockFace.UP).isPassable() && spot.getRelative(0, 2, 0).isPassable())) return;
        Material m = Material.COBBLESTONE;
        Inventory bag = core.bag(p.getUniqueId());
        for (int i = 0; i < bag.getSize(); i++) {
            ItemStack it = bag.getItem(i);
            if (Helper.empty(it) || !it.getType().isBlock() || !it.getType().isOccluding() || it.getType().hasGravity()) continue;
            m = it.getType();
            it.setAmount(it.getAmount() - 1);
            bag.setItem(i, it.getAmount() > 0 ? it : null);
            core.dirty.add(p.getUniqueId());
            break;
        }
        spot.setType(m);
        spot.getWorld().playSound(spot.getLocation(), spot.getBlockSoundGroup().getPlaceSound(), 0.7f, 1f);
        core.swing(p, h);
    }

    boolean merged(Entity e) {
        Helper.H h = core.helpers.get(e.getUniqueId());
        return h != null && h.merged;
    }

    /** The owner's own speed while merged follows the helper's size too. */
    void speedBoost(Player p, boolean on) {
        var a = p.getAttribute(Attribute.MOVEMENT_SPEED);
        if (a == null) return;
        a.removeModifier(core.speedKey);
        double f = core.speed(p.getUniqueId()) - 1;
        if (on && f > 0.01) a.addModifier(new AttributeModifier(core.speedKey, f, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
    }

    /** Merged: a shining being, with light that drifts around the body (no outline), and the helper's own looks. */
    void shine(Player p, boolean on) {
        org.bukkit.scoreboard.Team t = Bukkit.getScoreboardManager().getMainScoreboard().getTeam("helper_shine");
        if (t != null && t.hasEntry(p.getName())) t.removeEntry(p.getName());
        p.setGlowing(false);
        if (on) core.wearSkin(p, true);
    }

    void resize(Player p, Helper.H h, double v, boolean show) {
        UUID id = p.getUniqueId();
        v = Math.max(Helper.MIN_SIZE, Math.min(core.MAX_SIZE, v));
        if (Math.abs(v - 1.0) < 0.1) v = 1.0;
        core.conf.set(core.path(id, "size"), v);
        core.save();
        h.fit = v;
        Helper.set(h.m, Attribute.SCALE, v);
        Helper.set(h.w, Attribute.MOVEMENT_SPEED, 0.38 * core.speed(id));
        if (h.merged) { Helper.set(p, Attribute.SCALE, v); speedBoost(p, true); }
        if (core.tiny(id)) core.forget(p, h);
        if (!show) return;
        Entity who = h.merged ? p : h.m;
        p.spawnParticle(Particle.WITCH, who.getLocation().add(0, who.getHeight() / 2, 0), 30, 0.4, 0.6, 0.4, 0);
        Helper.bar(p, String.format(Locale.ROOT, "Size: %.2f× · hits for %.0f · speed +%.0f %%%s", v, core.damage(id), (core.speed(id) - 1) * 100,
                core.tiny(id) ? " · monsters don't notice " + (h.merged ? "you" : "it") : ""));
    }

    /** The owner gets their own size back and stops glowing (after a split, a logout or a crash while merged). */
    void ownBody(Player p) {
        String k = core.path(p.getUniqueId(), "ownsize");
        if (!core.conf.contains(k)) return;
        Helper.set(p, Attribute.SCALE, core.conf.getDouble(k, 1.0));
        speedBoost(p, false);
        shine(p, false);
        String ov = core.conf.getString(core.path(p.getUniqueId(), "ownskin.value")), os = core.conf.getString(core.path(p.getUniqueId(), "ownskin.signature"));
        core.conf.set(core.path(p.getUniqueId(), "ownskin"), null);
        if (ov != null && p.isOnline()) {
            com.destroystokyo.paper.profile.PlayerProfile prof = p.getPlayerProfile();
            prof.setProperty(new com.destroystokyo.paper.profile.ProfileProperty("textures", ov, os));
            p.setPlayerProfile(prof);
        }
        p.setFlySpeed(0.1f);
        boolean own = core.conf.getBoolean(core.path(p.getUniqueId(), "ownflight"));     // had flight already (fly power)
        core.conf.set(core.path(p.getUniqueId(), "ownflight"), null);
        if (!own && p.getGameMode() != org.bukkit.GameMode.CREATIVE && p.getGameMode() != org.bukkit.GameMode.SPECTATOR) {
            if (p.isFlying()) p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 300, 0));   // a soft landing
            p.setFlying(false);
            p.setAllowFlight(false);
        }
        core.conf.set(k, null);
        core.save();
    }

    void merge(Player p, Helper.H h) {
        if (h.merged) { core.say(p, "We already are one."); return; }
        core.release(h);
        h.merged = true;
        h.goal = null;
        h.w.setAware(false);
        h.w.setTarget(null);
        h.m.setVisibleByDefault(false);
        p.hideEntity(core.plugin, h.w);
        p.hideEntity(core.plugin, h.m);
        var own = p.getAttribute(Attribute.SCALE);
        core.conf.set(core.path(p.getUniqueId(), "ownsize"), own == null ? 1.0 : own.getBaseValue());
        core.conf.set(core.path(p.getUniqueId(), "ownflight"), p.getAllowFlight());
        core.save();
        Helper.set(p, Attribute.SCALE, core.size(p.getUniqueId()));                // the owner takes the helper's size and speed, and shines
        speedBoost(p, true);
        if (core.tiny(p.getUniqueId())) core.forget(p, h);
        shine(p, true);
        Location l = p.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.TOTEM_OF_UNDYING, l, 60, 0.4, 0.8, 0.4, 0.3);
        p.playSound(l, Sound.BLOCK_BEACON_ACTIVATE, 0.8f, 1.4f);
        core.say(p, "We are one now. Nothing can hurt you, and you hit as hard as I do. You can fly: double-tap jump. Tell me what to do and your body does it; move or swing and you are in control again. Say split to let me out.");
    }

    void split(Player p, Helper.H h) {
        if (!h.merged) { core.say(p, "We are not merged."); return; }
        core.release(h);
        h.merged = false;
        h.goal = null;
        if (h.gliding) { h.gliding = false; p.setGliding(false); }
        ownBody(p);
        h.w.setAware(true);
        h.w.teleport(p.getLocation().add(p.getLocation().getDirection().setY(0).normalize().multiply(-1.5)));
        h.m.setVisibleByDefault(core.flag(p.getUniqueId(), "show", false));
        p.showEntity(core.plugin, h.w);
        p.showEntity(core.plugin, h.m);
        Location l = h.w.getLocation().add(0, 1, 0);
        p.spawnParticle(Particle.CLOUD, l, 25, 0.3, 0.6, 0.3, 0.03);
        p.playSound(l, Sound.BLOCK_BEACON_DEACTIVATE, 0.8f, 1.4f);
        core.say(p, "I am out again.");
    }

    /** The owner takes over from the autopilot. */
    void control(Player p, Helper.H h) {
        h.job = Helper.Job.NONE;
        h.queue = new ArrayDeque<>();
        h.goal = null;
        Helper.bar(p, "You are in control again");
    }

    /** Every tick while merged: the hidden wolf stays on the owner (everything else measures from it), a movement
     *  key ends the job, and otherwise the owner is walked towards where the job needs them. */
    void autopilot(Player p, Helper.H h) {
        Location at = p.getLocation();
        if (h.w.getWorld() != at.getWorld() || h.w.getLocation().distanceSquared(at) > 0.01) h.w.teleport(at);
        if (!p.getAllowFlight()) p.setAllowFlight(true);                   // merged you fly (the fly power turns it off on world changes)
        float fly = (float) Math.min(0.3, 0.1 * core.speed(p.getUniqueId()));
        if (Math.abs(p.getFlySpeed() - fly) > 0.001) p.setFlySpeed(fly);
        Input keys = p.getCurrentInput();                                  // sprint forward in the air: glide where you look
        if (!h.gliding && p.isFlying() && keys.isSprint() && keys.isForward()) {
            h.gliding = true;
            p.setFlying(false);
        }
        if (h.gliding) {
            if (!keys.isSprint() || p.isOnGround() || p.isInWater()) {
                h.gliding = false;
                p.setGliding(false);
                if (!p.isOnGround()) p.setFlying(true);                    // back to hovering
            } else {
                if (!p.isGliding()) p.setGliding(true);
                p.setVelocity(at.getDirection().multiply(1.1 * core.speed(p.getUniqueId())));
            }
        }
        if (h.job == Helper.Job.NONE) return;
        Input in = p.getCurrentInput();
        if (in.isForward() || in.isBackward() || in.isLeft() || in.isRight()) { control(p, h); return; }
        if (h.goal == null || h.goal.getWorld() != at.getWorld()) return;
        Vector d = h.goal.toVector().subtract(at.toVector()).setY(0);
        if (d.lengthSquared() < 1) return;
        boolean moved = h.last == null || h.last.getWorld() != at.getWorld() || h.last.distanceSquared(at) > 0.0004;
        h.still = moved ? 0 : h.still + 1;
        h.last = at;
        d.normalize().multiply(0.24);
        d.setY(h.still > 3 && p.isOnGround() ? 0.42 : p.getVelocity().getY());     // something in the way: jump
        p.setVelocity(d);
    }

    /** Merged, monsters within four blocks are struck twice a second (the helper fighting through its owner). */
    void aura(Player p, Helper.H h) {
        if (!h.defend || core.flag(p.getUniqueId(), "passive", false) || p.isDead()) return;     // merged, the owner fights for themself unless they say defend
        for (Entity e : p.getNearbyEntities(4, 3, 4))
            if (core.foe(e) && e instanceof LivingEntity le) {
                le.damage(core.damage(p.getUniqueId()), p);
                p.swingMainHand();
                return;
            }
    }
}
