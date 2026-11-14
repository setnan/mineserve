package dev.etnan.creator;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Container;
import org.bukkit.block.data.Ageable;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mannequin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Clones of a player (Mannequin with the player's skin) standing in a row beside them. They follow the player,
 * repeat block breaks (harvest) and block placements at their own offset, and hit monsters near them.
 */
final class Clones implements Listener {
    static final int MAX = 20, MAX_SECS = 600;
    private final JavaPlugin plugin;

    private static final class Clone {
        Mannequin m;
        int dx, dz;         // fixed block offset from the player
        int hits, lastHitTick = -100;
    }

    static final int LIVES = 3;   // the third hit makes a clone go poof

    private static final class Group {
        final List<Clone> clones = new ArrayList<>();
        final List<ItemStack> bag = new ArrayList<>();   // collected loot, handed over when the clones go poof
        int xp;
        long until;
    }

    private final Map<UUID, Group> groups = new HashMap<>();
    private int tick;
    /** monster hit by a clone -> [player uuid, time] so its loot and XP go straight to the player */
    private final Map<UUID, Object[]> hitBy = new HashMap<>();

    Clones(JavaPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    boolean summon(Player p, int n, int secs) {
        dismiss(p, false);
        n = Math.max(1, Math.min(MAX, n));
        secs = Math.max(1, Math.min(MAX_SECS, secs));
        Vector dir = p.getLocation().getDirection();
        boolean alongX = Math.abs(dir.getX()) > Math.abs(dir.getZ());   // looking along X -> row along Z
        Group g = new Group();
        g.until = System.currentTimeMillis() + secs * 1000L;
        ResolvableProfile prof = ResolvableProfile.resolvableProfile(p.getPlayerProfile());
        for (int i = 0; i < n; i++) {
            int side = i % 2 == 0 ? 1 : -1, k = (i / 2 + 1) * 2 * side;
            Clone c = new Clone();
            c.dx = alongX ? 0 : k;
            c.dz = alongX ? k : 0;
            Location at = spot(p, c);
            c.m = p.getWorld().spawn(at, Mannequin.class, m -> {
                m.setProfile(prof);
                m.setDescription(Component.text("Clone", NamedTextColor.AQUA));
                m.customName(Component.text(p.getName(), NamedTextColor.AQUA));
                m.setGravity(false);
                m.setPersistent(false);
                m.setSilent(true);
                m.setCollidable(false);
            });
            dress(p, c.m);
            at.getWorld().spawnParticle(Particle.CLOUD, at.clone().add(0, 1, 0), 15, 0.3, 0.6, 0.3, 0.02);
            g.clones.add(c);
        }
        groups.put(p.getUniqueId(), g);
        p.playSound(p.getLocation(), Sound.ENTITY_ILLUSIONER_MIRROR_MOVE, 1f, 1f);
        p.sendActionBar(Component.text(n + (n == 1 ? " clone for " : " clones for ") + secs + " seconds", NamedTextColor.AQUA));
        return true;
    }

    /** Where a clone stands: the player's position + offset, moved up or down to the ground nearby. */
    private Location spot(Player p, Clone c) {
        Location l = p.getLocation().add(c.dx, 0, c.dz);
        Block b = l.getBlock();
        for (int i = 0; i < 4 && !(b.isPassable() && b.getRelative(0, 1, 0).isPassable()); i++) { b = b.getRelative(0, 1, 0); l.add(0, 1, 0); }
        if (p.isOnGround())
            for (int i = 0; i < 4 && b.getRelative(0, -1, 0).isPassable(); i++) { b = b.getRelative(0, -1, 0); l.add(0, -1, 0); }
        if (l.getBlockY() != p.getLocation().getBlockY()) l.setY(b.getY());
        return l;
    }

    private static void dress(Player p, Mannequin m) {
        EntityEquipment e = m.getEquipment(), pe = p.getEquipment();
        e.setItemInMainHand(pe.getItemInMainHand().clone());
        e.setItemInOffHand(pe.getItemInOffHand().clone());
        e.setHelmet(pe.getHelmet() == null ? null : pe.getHelmet().clone());
        e.setChestplate(pe.getChestplate() == null ? null : pe.getChestplate().clone());
        e.setLeggings(pe.getLeggings() == null ? null : pe.getLeggings().clone());
        e.setBoots(pe.getBoots() == null ? null : pe.getBoots().clone());
        // no setDropChance: a Mannequin is not a Mob (Paper throws), and clones never die normally anyway
    }

    private void tick() {
        tick++;
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(groups.keySet())) {
            Player p = Bukkit.getPlayer(id);
            Group g = groups.get(id);
            if (p == null || g.until < now || p.isDead()) { if (p != null) dismiss(p, true); else { remove(g); groups.remove(id); } continue; }
            if (tick % 200 == 0) hitBy.values().removeIf(v -> now - (long) v[1] > 10_000);
            if (g.until - now < 3000 && tick % 20 == 0)
                p.sendActionBar(Component.text("The clones vanish in " + ((g.until - now) / 1000 + 1) + " s", NamedTextColor.GRAY));
            for (Clone c : g.clones) {
                if (!c.m.isValid()) continue;
                Location to = spot(p, c);
                to.setYaw(p.getLocation().getYaw());
                to.setPitch(p.getLocation().getPitch());
                c.m.teleport(to);
                c.m.setPose(p.isSneaking() ? org.bukkit.entity.Pose.SNEAKING : org.bukkit.entity.Pose.STANDING, true);
                if (tick % 20 == 0) dress(p, c.m);
                if (tick % 10 == 0) fight(p, c);
            }
        }
    }

    private void fight(Player p, Clone c) {
        LivingEntity best = null;
        double bd = 6 * 6;
        for (Entity e : c.m.getNearbyEntities(6, 4, 6))
            if (e instanceof Enemy && e instanceof LivingEntity le && !le.isDead()) {
                double d = e.getLocation().distanceSquared(c.m.getLocation());
                if (d < bd) { bd = d; best = le; }
            }
        if (best == null) return;
        Location look = c.m.getLocation().setDirection(best.getLocation().toVector().subtract(c.m.getLocation().toVector()));
        c.m.setRotation(look.getYaw(), look.getPitch());
        c.m.swingMainHand();
        hitBy.put(best.getUniqueId(), new Object[]{p.getUniqueId(), System.currentTimeMillis()});
        if (best instanceof org.bukkit.entity.Mob mob && !(mob.getTarget() instanceof Mannequin)) mob.setTarget(c.m);   // monsters go for the clones
        best.damage(5, p);
        best.setVelocity(best.getLocation().toVector().subtract(c.m.getLocation().toVector()).setY(0).normalize().multiply(0.4).setY(0.25));
    }

    // --- Copying the player -------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Group g = groups.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        Player p = e.getPlayer();
        Block o = e.getBlock();
        Material type = o.getType();
        boolean ripe = o.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge();
        boolean crop = o.getBlockData() instanceof Ageable;
        ItemStack tool = p.getInventory().getItemInMainHand();
        for (Clone c : g.clones) {
            Block b = o.getRelative(c.dx, 0, c.dz);
            if (b.getType() != type || b.getType().getHardness() < 0 || b.getState() instanceof Container) continue;
            if (crop && ripe && !(b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge())) continue;
            if (p.getGameMode() != GameMode.CREATIVE) g.bag.addAll(b.getDrops(tool, p));
            b.getWorld().spawnParticle(Particle.BLOCK, b.getLocation().add(0.5, 0.5, 0.5), 12, 0.3, 0.3, 0.3, b.getBlockData());
            b.setType(Material.AIR);
            c.m.swingMainHand();
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Group g = groups.get(e.getPlayer().getUniqueId());
        if (g == null) return;
        Player p = e.getPlayer();
        Block o = e.getBlockPlaced();
        Material item = e.getItemInHand().getType();
        boolean free = p.getGameMode() == GameMode.CREATIVE;
        for (Clone c : g.clones) {
            Block b = o.getRelative(c.dx, 0, c.dz);
            if (!(b.isEmpty() || b.isReplaceable() && !b.isLiquid())) continue;
            if (!free) {
                if (!p.getInventory().containsAtLeast(new ItemStack(item), 1)) { p.sendActionBar(Component.text("Out of " + item.getKey().getKey(), NamedTextColor.RED)); break; }
                p.getInventory().removeItem(new ItemStack(item, 1));
            }
            b.setBlockData(o.getBlockData().clone());
            c.m.swingMainHand();
        }
    }

    /** Clones never really take damage: each hit (max one per 10 ticks, so lava = a few jolts) counts, on hit LIVES they go poof. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onHurt(org.bukkit.event.entity.EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Mannequin m)) return;
        for (var en : groups.entrySet())
            for (Clone c : en.getValue().clones) {
                if (c.m != m) continue;
                e.setCancelled(true);
                m.setFireTicks(0);
                if (e instanceof org.bukkit.event.entity.EntityDamageByEntityEvent be) {
                    Entity d = be.getDamager();
                    if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
                    if (d.getUniqueId().equals(en.getKey()) || d instanceof Mannequin) return;   // the owner and other clones don't count
                }
                if (tick - c.lastHitTick < 10) return;
                c.lastHitTick = tick;
                c.hits++;
                m.playHurtAnimation(0);
                m.getWorld().playSound(m.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.6f, 1.2f);
                if (c.hits >= LIVES) {
                    poof(c);
                    en.getValue().clones.remove(c);
                    Player p = Bukkit.getPlayer(en.getKey());
                    if (p != null) {
                        if (en.getValue().clones.isEmpty()) dismiss(p, true);   // last one gone: hand over the bag now
                        else p.sendActionBar(Component.text("Poof! A clone is gone (" + en.getValue().clones.size() + " left)", NamedTextColor.GRAY));
                    }
                }
                return;
            }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDeath(org.bukkit.event.entity.EntityDeathEvent e) {
        Object[] h = hitBy.remove(e.getEntity().getUniqueId());
        if (h == null || System.currentTimeMillis() - (long) h[1] > 10_000) return;
        Player p = Bukkit.getPlayer((UUID) h[0]);
        if (p == null || !p.getWorld().equals(e.getEntity().getWorld())) return;
        Group g = groups.get(p.getUniqueId());
        if (g != null) { g.bag.addAll(e.getDrops()); g.xp += e.getDroppedExp(); }
        else {
            for (ItemStack d : e.getDrops()) p.getInventory().addItem(d).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
            if (e.getDroppedExp() > 0) p.giveExp(e.getDroppedExp(), true);
        }
        e.getDrops().clear();
        e.setDroppedExp(0);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { dismiss(e.getPlayer(), false); }

    @EventHandler
    public void onWorld(PlayerChangedWorldEvent e) { dismiss(e.getPlayer(), true); }

    void dismiss(Player p, boolean tell) {
        Group g = groups.remove(p.getUniqueId());
        if (g == null) return;
        remove(g);
        // hand over everything the clones gathered
        Map<String, Integer> sum = new java.util.TreeMap<>();
        for (ItemStack d : g.bag) {
            sum.merge(d.getType().getKey().getKey().replace('_', ' '), d.getAmount(), Integer::sum);
            if (p.isDead()) p.getWorld().dropItemNaturally(p.getLocation(), d);
            else p.getInventory().addItem(d).values().forEach(x -> p.getWorld().dropItemNaturally(p.getLocation(), x));
        }
        if (g.xp > 0) p.giveExp(g.xp, true);
        if (!tell && sum.isEmpty()) return;
        StringBuilder sb = new StringBuilder("Poof! ");
        if (sum.isEmpty() && g.xp == 0) sb.append("The clones are gone.");
        else {
            sb.append("The clones gave you ");
            List<String> parts = new ArrayList<>();
            sum.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8).forEach(en -> parts.add(en.getValue() + " " + en.getKey()));
            if (sum.size() > 8) parts.add("…");
            if (g.xp > 0) parts.add(g.xp + " XP");
            sb.append(String.join(", ", parts)).append(".");
        }
        p.sendMessage(Component.text(sb.toString(), NamedTextColor.AQUA));
    }

    private static void remove(Group g) {
        for (Clone c : g.clones) poof(c);
    }

    private static void poof(Clone c) {
            if (c.m.isValid()) {
                Location l = c.m.getLocation().add(0, 1, 0);
                l.getWorld().spawnParticle(Particle.POOF, l, 30, 0.35, 0.7, 0.35, 0.03);
                l.getWorld().spawnParticle(Particle.CLOUD, l, 10, 0.3, 0.6, 0.3, 0.01);
                l.getWorld().playSound(l, Sound.BLOCK_FIRE_EXTINGUISH, 0.35f, 1.8f);
                c.m.remove();
            }
    }

    void shutdown() {
        for (UUID id : new ArrayList<>(groups.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null) dismiss(p, false); else remove(groups.remove(id));
        }
    }
}
