package dev.etnan.creator;

import io.papermc.paper.registry.RegistryAccess;
import io.papermc.paper.registry.RegistryKey;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.DyeColor;
import org.bukkit.Input;
import org.bukkit.Keyed;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.EntitiesLoadEvent;
import org.bukkit.event.world.EntitiesUnloadEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

/**
 * Magic Wand spells that change mobs: variant, gold, zombify, tame, ally, pet, ride, and the health that goes with
 * big/small. Called from Wand.cast with the mob the player points at.
 *
 * ally  fights for you for 60 s (never hurts players)       pet   follows you for good (cast again to release it)
 * ride  you sit on it and steer with WASD, space = up/jump, sneak = off; flying mobs fly where you look
 */
final class WandMobs implements Listener {
    static final int ALLY_SECS = 60;
    private final JavaPlugin plugin;
    private final NamespacedKey petKey, helperKey, sizeKey;
    private final Map<UUID, Object[]> allies = new HashMap<>();     // mob -> [owner uuid, until millis]
    private final Set<UUID> pets = new HashSet<>();                 // loaded mobs with the pet_owner tag
    private final Map<UUID, Object[]> rides = new HashMap<>();      // player -> [mob, was aware, had gravity]
    private int tick;

    WandMobs(JavaPlugin plugin) {
        this.plugin = plugin;
        petKey = new NamespacedKey(plugin, "pet_owner");
        helperKey = new NamespacedKey(plugin, "helper_owner");
        sizeKey = new NamespacedKey(plugin, "wand_size");
        for (var w : Bukkit.getWorlds()) for (Entity e : w.getEntities()) if (isPet(e)) pets.add(e.getUniqueId());
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    private boolean isHelper(Entity e) { return e.getPersistentDataContainer().has(helperKey, PersistentDataType.STRING); }

    private boolean isPet(Entity e) { return e.getPersistentDataContainer().has(petKey, PersistentDataType.STRING); }

    private UUID petOwner(Entity e) {
        String s = e.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        return s == null ? null : UUID.fromString(s);
    }

    private static void bar(Player p, String text, boolean ok) {
        p.sendActionBar(Component.text(text, ok ? NamedTextColor.GOLD : NamedTextColor.RED));
    }

    private static String nice(Object o) {
        String s = o instanceof Keyed k ? k.getKey().getKey() : o.toString();
        return s.toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private static void puff(Entity e, Particle particle) {
        e.getWorld().spawnParticle(particle, e.getLocation().add(0, e.getHeight() / 2, 0), 25, 0.4, 0.6, 0.4, 0.02);
    }

    // --- Size -> health ---------------------------------------------------------------------------------

    /** Big mobs take more, small ones less: max health follows the scale (3x bigger = 3x the health). */
    void sizeHealth(LivingEntity t, double scale) {
        AttributeInstance mh = t.getAttribute(Attribute.MAX_HEALTH);
        if (mh == null) return;
        double ratio = t.getHealth() / mh.getValue();
        mh.removeModifier(sizeKey);
        if (Math.abs(scale - 1) > 0.01) mh.addModifier(new AttributeModifier(sizeKey, scale - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1));
        t.setHealth(Math.max(0.5, Math.min(mh.getValue(), ratio * mh.getValue())));
    }

    // --- variant ---------------------------------------------------------------------------------------

    private static <E extends Enum<E>> E next(E cur) {
        E[] all = cur.getDeclaringClass().getEnumConstants();
        return all[(cur.ordinal() + 1) % all.length];
    }

    /** Next entry in a data-driven registry (vanilla looks only; our own like supermann:golden are skipped). */
    private static <T extends Keyed> T next(RegistryKey<T> key, T cur) {
        List<T> all = new ArrayList<>();
        for (T t : RegistryAccess.registryAccess().getRegistry(key)) if (t.getKey().getNamespace().equals("minecraft")) all.add(t);
        all.sort(Comparator.comparing(t -> t.getKey().getKey()));
        return all.get((all.indexOf(cur) + 1) % all.size());
    }

    private static final List<Villager.Type> VILLAGER_TYPES = List.of(Villager.Type.PLAINS, Villager.Type.DESERT, Villager.Type.JUNGLE,
            Villager.Type.SAVANNA, Villager.Type.SNOW, Villager.Type.SWAMP, Villager.Type.TAIGA);

    boolean variant(Player p, LivingEntity t) {
        Object now;
        if (t instanceof Sheep s) { s.setColor(next(s.getColor() == null ? DyeColor.WHITE : s.getColor())); now = s.getColor(); }
        else if (t instanceof Shulker s) { s.setColor(next(s.getColor() == null ? DyeColor.PURPLE : s.getColor())); now = s.getColor(); }
        else if (t instanceof Cat c) { c.setCatType(next(RegistryKey.CAT_VARIANT, c.getCatType())); now = c.getCatType(); }
        else if (t instanceof Wolf w) { w.setVariant(next(RegistryKey.WOLF_VARIANT, w.getVariant())); now = w.getVariant(); }
        else if (t instanceof Frog f) { f.setVariant(next(RegistryKey.FROG_VARIANT, f.getVariant())); now = f.getVariant(); }
        else if (t instanceof Chicken c) {
            if (!c.getVariant().getKey().getNamespace().equals("minecraft")) { bar(p, "Golden chickens stay golden", false); return false; }
            c.setVariant(next(RegistryKey.CHICKEN_VARIANT, c.getVariant())); now = c.getVariant();
        }
        else if (t instanceof MushroomCow m) { m.setVariant(next(m.getVariant())); now = m.getVariant(); }
        else if (t instanceof Cow c) { c.setVariant(next(RegistryKey.COW_VARIANT, c.getVariant())); now = c.getVariant(); }
        else if (t instanceof Pig g) { g.setVariant(next(RegistryKey.PIG_VARIANT, g.getVariant())); now = g.getVariant(); }
        else if (t instanceof Horse h) {
            h.setColor(next(h.getColor()));
            if (h.getColor().ordinal() == 0) h.setStyle(next(h.getStyle()));
            now = nice(h.getColor()) + ", " + nice(h.getStyle());
        }
        else if (t instanceof Llama l) { l.setColor(next(l.getColor())); now = l.getColor(); }
        else if (t instanceof Parrot q) { q.setVariant(next(q.getVariant())); now = q.getVariant(); }
        else if (t instanceof Axolotl a) { a.setVariant(next(a.getVariant())); now = a.getVariant(); }
        else if (t instanceof Fox f) { f.setFoxType(next(f.getFoxType())); now = f.getFoxType(); }
        else if (t instanceof Rabbit r) { r.setRabbitType(next(r.getRabbitType())); now = r.getRabbitType(); }
        else if (t instanceof TropicalFish f) {
            var rnd = java.util.concurrent.ThreadLocalRandom.current();
            DyeColor[] cs = DyeColor.values();
            TropicalFish.Pattern[] ps = TropicalFish.Pattern.values();
            f.setPattern(ps[rnd.nextInt(ps.length)]); f.setBodyColor(cs[rnd.nextInt(cs.length)]); f.setPatternColor(cs[rnd.nextInt(cs.length)]);
            now = f.getPattern();
        }
        else if (t instanceof Villager v) { v.setVillagerType(VILLAGER_TYPES.get((VILLAGER_TYPES.indexOf(v.getVillagerType()) + 1) % VILLAGER_TYPES.size())); now = v.getVillagerType(); }
        else if (t instanceof ZombieVillager z) { z.setVillagerType(VILLAGER_TYPES.get((VILLAGER_TYPES.indexOf(z.getVillagerType()) + 1) % VILLAGER_TYPES.size())); now = z.getVillagerType(); }
        else { bar(p, "That one has only one look", false); return false; }
        puff(t, Particle.HAPPY_VILLAGER);
        bar(p, "Look: " + nice(now), true);
        return true;
    }

    // --- gold ------------------------------------------------------------------------------------------

    boolean gold(Player p, LivingEntity t) {
        if (!(t instanceof Chicken c)) { bar(p, "Point at a chicken", false); return false; }
        if (!c.getVariant().getKey().getNamespace().equals("minecraft")) { bar(p, "It is already golden", false); return false; }
        if (GoldenChicken.instance == null) return false;
        GoldenChicken.instance.makeGolden(c);
        puff(c, Particle.WAX_ON);
        c.getWorld().playSound(c.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        bar(p, "A Golden Chicken! It lays golden eggs.", true);
        return true;
    }

    // --- zombify / cure --------------------------------------------------------------------------------

    boolean zombify(Player p, LivingEntity t) {
        LivingEntity out;
        if (t instanceof ZombieVillager z) {
            z.setConversionPlayer(p);                                  // cured by you: you get the cheap trades
            z.setConversionTime(1);
            bar(p, "Cured! It turns back into a villager", true);
            puff(t, Particle.HEART);
            return true;
        }
        if (t instanceof Villager v) out = v.zombify();
        else if (t instanceof PigZombie) out = replace(t, Piglin.class);
        else if (t instanceof Piglin || t instanceof PiglinBrute) out = replace(t, PigZombie.class);
        else if (t instanceof Zoglin) out = replace(t, Hoglin.class);
        else if (t instanceof Hoglin) out = replace(t, Zoglin.class);
        else if (t instanceof ZombieHorse) out = replace(t, Horse.class);
        else if (t instanceof Horse) out = replace(t, ZombieHorse.class);
        else if (t instanceof Husk || (t instanceof Zombie && !(t instanceof Drowned))) out = replace(t, Villager.class);
        else { bar(p, "Works on villagers, zombies, piglins, hoglins and horses", false); return false; }
        if (out == null) return false;
        out.getWorld().playSound(out.getLocation(), Sound.ENTITY_ZOMBIE_VILLAGER_CONVERTED, 1f, 1f);
        puff(out, Particle.WITCH);
        bar(p, "It became a " + nice(out.getType()), true);
        return true;
    }

    private <T extends LivingEntity> T replace(LivingEntity old, Class<T> type) {
        Location at = old.getLocation();
        T n = old.getWorld().spawn(at, type, e -> {
            e.customName(old.customName());
            e.setCustomNameVisible(old.isCustomNameVisible());
            if (old instanceof Ageable a && e instanceof Ageable b && !a.isAdult()) b.setBaby();
            if (e instanceof Mob m) m.setPersistent(old instanceof Mob om && om.isPersistent() || old.customName() != null);
        });
        old.remove();
        return n;
    }

    // --- tame ------------------------------------------------------------------------------------------

    boolean tame(Player p, LivingEntity t) {
        if (t instanceof Tameable tm) {
            if (tm.isTamed() && tm.getOwnerUniqueId() != null && !tm.getOwnerUniqueId().equals(p.getUniqueId())) {
                bar(p, "It already belongs to " + Bukkit.getOfflinePlayer(tm.getOwnerUniqueId()).getName(), false);
                return false;
            }
            tm.setTamed(true);
            tm.setOwner(p);
            if (t instanceof Sittable s) s.setSitting(false);
        } else if (t instanceof Fox f) {
            f.setFirstTrustedPlayer(p);
        } else if (t instanceof Ocelot o) {
            o.setTrusting(true);
        } else {
            bar(p, "That one cannot be tamed (try pet)", false);
            return false;
        }
        puff(t, Particle.HEART);
        bar(p, "Tamed! It is yours now", true);
        return true;
    }

    // --- ally (60 s) -----------------------------------------------------------------------------------

    /** The outline of an ally is dark green (the colour of its team), not the plain white. */
    private static void outline(Mob m, boolean on) {
        org.bukkit.scoreboard.Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        org.bukkit.scoreboard.Team t = sb.getTeam("wand_ally");
        String who = m.getUniqueId().toString();
        if (on) {
            if (t == null) { t = sb.registerNewTeam("wand_ally"); t.color(NamedTextColor.DARK_GREEN); }
            if (sb.getEntryTeam(who) == null) t.addEntry(who);
        } else if (t != null && t.hasEntry(who)) t.removeEntry(who);
        m.setGlowing(on);
    }

    boolean ally(Player p, LivingEntity t) {
        if (!(t instanceof Mob m) || isHelper(t)) { bar(p, "Point at a mob", false); return false; }
        allies.put(m.getUniqueId(), new Object[]{p.getUniqueId(), System.currentTimeMillis() + ALLY_SECS * 1000L});
        m.setTarget(null);
        outline(m, true);
        puff(m, Particle.TOTEM_OF_UNDYING);
        bar(p, "It fights for you for " + ALLY_SECS + " s", true);
        return true;
    }

    private UUID allyOwner(Entity e) {
        Object[] a = allies.get(e.getUniqueId());
        return a == null ? null : (UUID) a[0];
    }

    /** Mobs that are on a player's side: allies, pets and helpers. They never go for players or each other. */
    private boolean friendly(Entity e) {
        return e != null && (allies.containsKey(e.getUniqueId()) || isPet(e) || isHelper(e));
    }

    // --- pet (for good) --------------------------------------------------------------------------------

    boolean pet(Player p, LivingEntity t) {
        if (!(t instanceof Mob m) || isHelper(t)) { bar(p, "Point at a mob", false); return false; }
        UUID owner = petOwner(m);
        if (owner != null && !owner.equals(p.getUniqueId())) {
            bar(p, "It is already " + Bukkit.getOfflinePlayer(owner).getName() + "'s pet", false);
            return false;
        }
        if (owner != null) {
            m.getPersistentDataContainer().remove(petKey);
            pets.remove(m.getUniqueId());
            puff(m, Particle.CLOUD);
            bar(p, "It is free again", true);
            return true;
        }
        m.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, p.getUniqueId().toString());
        m.setPersistent(true);
        m.setRemoveWhenFarAway(false);
        m.setTarget(null);
        pets.add(m.getUniqueId());
        puff(m, Particle.HEART);
        bar(p, "Your pet now! It follows you (cast pet on it again to let it go)", true);
        return true;
    }

    @EventHandler
    public void onLoad(EntitiesLoadEvent e) { for (Entity x : e.getEntities()) if (isPet(x)) pets.add(x.getUniqueId()); }

    @EventHandler
    public void onUnload(EntitiesUnloadEvent e) { for (Entity x : e.getEntities()) pets.remove(x.getUniqueId()); }

    // --- ride ------------------------------------------------------------------------------------------

    private static boolean flies(Entity e) {
        return e instanceof Flying || e instanceof Bee || e instanceof Parrot || e instanceof Allay || e instanceof Vex
                || e instanceof Bat || e instanceof Blaze || e instanceof EnderDragon || e instanceof Wither || e instanceof Breeze;
    }

    boolean ride(Player p, LivingEntity t) {
        if (t instanceof Player || isHelper(t)) { bar(p, "Point at a mob", false); return false; }
        if (!t.getPassengers().isEmpty()) { bar(p, "Someone is already riding it", false); return false; }
        if (p.isInsideVehicle()) p.leaveVehicle();
        boolean aware = !(t instanceof Mob m) || m.isAware();
        boolean gravity = t.hasGravity();
        if (!t.addPassenger(p)) { bar(p, "It does not want to be ridden", false); return false; }
        if (t instanceof Mob m) m.setAware(false);                  // it goes where you steer, not where it wants
        if (flies(t)) t.setGravity(false);
        rides.put(p.getUniqueId(), new Object[]{t, aware, gravity});
        bar(p, "WASD to steer, space = " + (flies(t) ? "up" : "jump") + ", sneak = get off", true);
        return true;
    }

    private void endRide(UUID id) {
        Object[] r = rides.remove(id);
        if (r == null) return;
        LivingEntity t = (LivingEntity) r[0];
        if (!t.isValid()) return;
        if (t instanceof Mob m) m.setAware((boolean) r[1]);
        t.setGravity((boolean) r[2]);
    }

    private void steer(Player p, LivingEntity t) {
        Input in = p.getCurrentInput();
        Location look = p.getLocation();
        t.setRotation(look.getYaw(), 0);
        Vector fwd = look.getDirection();
        boolean fly = flies(t);
        if (!fly) fwd.setY(0);
        if (fwd.lengthSquared() > 0) fwd.normalize();
        Vector side = new Vector(-fwd.getZ(), 0, fwd.getX());
        if (side.lengthSquared() > 0) side.normalize();
        Vector move = new Vector();
        if (in.isForward()) move.add(fwd);
        if (in.isBackward()) move.subtract(fwd.clone().multiply(0.5));
        if (in.isLeft()) move.subtract(side);
        if (in.isRight()) move.add(side);
        double speed = (fly ? 0.55 : 0.32) * (in.isSprint() ? 1.6 : 1) * Math.max(0.7, Math.min(2, t.getAttribute(Attribute.SCALE) == null ? 1 : t.getAttribute(Attribute.SCALE).getValue()));
        if (move.lengthSquared() > 0) move.normalize().multiply(speed);
        Vector v = t.getVelocity();
        if (fly) {
            double y = in.isJump() ? 0.4 : (move.lengthSquared() > 0 ? move.getY() : 0);
            t.setVelocity(new Vector(move.getX(), y, move.getZ()));
        } else {
            double y = v.getY();
            if (in.isJump() && t.isOnGround()) y = 0.6;
            t.setVelocity(new Vector(move.getX(), y, move.getZ()));
        }
    }

    // --- Every tick ------------------------------------------------------------------------------------

    private void tick() {
        tick++;
        for (UUID id : new ArrayList<>(rides.keySet())) {
            Player p = Bukkit.getPlayer(id);
            LivingEntity t = (LivingEntity) rides.get(id)[0];
            if (p == null || !t.isValid() || p.getVehicle() != t) { endRide(id); continue; }
            steer(p, t);
        }
        if (tick % 10 != 0) return;
        long now = System.currentTimeMillis();
        for (UUID id : new ArrayList<>(allies.keySet())) {
            Entity e = Bukkit.getEntity(id);
            Object[] a = allies.get(id);
            if (!(e instanceof Mob m) || !m.isValid() || (long) a[1] < now) {
                allies.remove(id);
                if (e instanceof Mob m2 && m2.isValid()) { outline(m2, false); m2.setTarget(null); }
                continue;
            }
            hunt(m, 16);
        }
        for (UUID id : new ArrayList<>(pets)) {
            Entity e = Bukkit.getEntity(id);
            if (!(e instanceof Mob m) || !m.isValid() || rides.values().stream().anyMatch(r -> r[0] == m)) continue;
            Player owner = Bukkit.getPlayer(petOwner(m));
            if (owner == null || owner.getWorld() != m.getWorld()) continue;
            if (m.getTarget() instanceof Player) m.setTarget(null);
            double d = m.getLocation().distanceSquared(owner.getLocation());
            if (d > 24 * 24) m.teleport(owner.getLocation());
            else if (d > 3.5 * 3.5) m.getPathfinder().moveTo(owner, 1.3);
            else if (m.getTarget() == null) m.getPathfinder().stopPathfinding();
        }
    }

    /** Makes the mob attack the nearest monster around it that is not on a player's side. */
    private void hunt(Mob m, int range) {
        if (m.getTarget() != null && !m.getTarget().isDead() && !(m.getTarget() instanceof Player) && !friendly(m.getTarget())) return;
        LivingEntity best = null;
        double bd = range * range;
        for (Entity e : m.getNearbyEntities(range, 6, range))
            if (e instanceof Enemy && e instanceof LivingEntity le && !le.isDead() && !friendly(e)) {
                double d = e.getLocation().distanceSquared(m.getLocation());
                if (d < bd) { bd = d; best = le; }
            }
        m.setTarget(best);
    }

    // --- Allies, pets and helpers never hurt players or each other --------------------------------------

    @EventHandler(ignoreCancelled = true)
    public void onTarget(EntityTargetEvent e) {
        if (!friendly(e.getEntity()) || e.getTarget() == null) return;
        if (e.getTarget() instanceof Player || friendly(e.getTarget())) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        Entity d = e.getDamager();
        if (d instanceof org.bukkit.entity.Projectile pr && pr.getShooter() instanceof Entity sh) d = sh;
        if (friendly(d) && (e.getEntity() instanceof Player || friendly(e.getEntity()))) e.setCancelled(true);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) { endRide(e.getPlayer().getUniqueId()); }

    void shutdown() {
        for (UUID id : new ArrayList<>(rides.keySet())) endRide(id);
        for (UUID id : allies.keySet()) if (Bukkit.getEntity(id) instanceof Mob m) outline(m, false);
        allies.clear();
    }
}
