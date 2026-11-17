package dev.etnan.creator

import io.papermc.paper.entity.TeleportFlag
import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Wolf
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The Lion: one in each Haven at most, seen now and then near a player, rare but not very rare. Big, golden and
 * calm; kind, but mighty, and you respect it. An intelligent being: it cannot be tamed or hurt, it cares for
 * players and has a special bond with helpers. Ride it (right click) and your helper comes along. Riding heals you,
 * mends your armor and what you hold, and gives you experience, slowly but surely. You cannot steer it: it sets off
 * by itself to something worth seeing (a wishing well, a havenite spring, gold water, a levitation column, a time
 * rift, a temple or a builder's building, or else the highest island near by), and where it cannot walk it leaps,
 * far and high, landing exactly where it meant to, even on a high island in one bound. Get off once it has brought
 * you somewhere and it is gone the moment you look away.
 */
class TheLion(private val magic: HavenMagic, private val world: HavenWorld, private val helper: Helper) : Listener {

    private val lionKey = Haven.key("the_lion")
    private val gold = Particle.DustOptions(Color.fromRGB(0xFF, 0xC8, 0x3A), 1.4f)
    private val lions = mutableMapOf<String, Wolf>()                 // Haven world -> its lion
    private val trips = mutableMapOf<UUID, Trip>()                     // lion -> where it is taking its rider
    private val seen = mutableMapOf<UUID, MutableList<Pair<Int, Int>>>() // rider -> places already shown
    private val leaving = mutableMapOf<UUID, Long>()                 // lion -> since when it waits to slip away unseen
    private val cooldown = mutableMapOf<String, Long>()              // Haven world -> not before (after it slipped away)
    private val lying = mutableMapOf<UUID, Long>()                   // lion -> lies down until (after being stroked)
    private val alone = mutableMapOf<UUID, Long>()                   // lion -> since when no player has been near
    private var ticks = 0L

    /** A leap in the air: from, to, how long, how high, and how far along it is. */
    /** A leap: a quick, powerful rise to the apex, then a slow, graceful glide down (rise and fall in ticks). */
    class Leap(val from: Location, val to: Location, val rise: Int, val fall: Int, val apex: Double) {
        var t = 0
        val duration get() = rise + fall
    }

    class Trip(val target: Location, val name: String) {
        var guest: UUID? = null                    // the player it came to show something
        var metAt = System.currentTimeMillis()     // when it came; it stays near its guest a while before it leads the way
        val wait = 60_000L + Haven.rnd.nextLong(30_000)
        var hinted = false
        var leap: Leap? = null
        var arrivedAt = 0L
        var lastDistance = Double.MAX_VALUE
        var stuck = 0
    }

    init { Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 100L, 1L) }

    private fun isLion(e: org.bukkit.entity.Entity) = e is Wolf && e.persistentDataContainer.has(lionKey)

    // --- Showing up now and then ----------------------------------------------------------------------------------

    private fun tick() {
        ticks++
        if (warriors.isNotEmpty()) warriorTick()
        for (w in Haven.worlds) {
            val lion = lions[w.name]?.takeIf { it.isValid }
            if (lion == null) { lions.remove(w.name); if (ticks % 1200 == 0L && (cooldown[w.name] ?: 0) < System.currentTimeMillis()) maybeSpawn(w); continue }
            if (ticks % 200 == 0L) {                         // everyone has gone (left Haven, logged out): after three minutes it goes too
                if (lion.passengers.isEmpty() && w.players.none { it.location.distanceSquared(lion.location) < 128.0 * 128 }) {
                    val since = alone.getOrPut(lion.uniqueId) { System.currentTimeMillis() }
                    if (System.currentTimeMillis() - since > 180_000) { vanish(w, lion); continue }
                } else alone.remove(lion.uniqueId)
            }
            // only once it has shown what it wanted to show: gone the moment no one looks
            leaving[lion.uniqueId]?.let { from -> val now = System.currentTimeMillis()
                if (now >= from && lion.passengers.none { it is Player } && (unseen(lion) || now - from > 15_000)) { vanish(w, lion); continue } }
            if (Haven.isFrozen(lion)) continue
            if (ticks % 40 == 0L) care(lion)
            lying[lion.uniqueId]?.let { until ->                     // lying down for you to climb on: it waits
                if (System.currentTimeMillis() < until && lion.passengers.isEmpty()) { lion.pathfinder.stopPathfinding(); continue }
                lying.remove(lion.uniqueId); lion.isSitting = false
            }
            if (ticks % 8 == 0L) w.spawnParticle(Particle.DUST, lion.location.add(lion.location.direction.multiply(1.2)).add(0.0, 2.2, 0.0), 3, 0.7, 0.6, 0.7, gold)
            if (ticks % 4 == 0L) shimmer(lion)
            if (ticks % 2 == 0L && trips[lion.uniqueId]?.leap == null) resize(lion)
            if (ticks % 10 == 0L) glow(lion)
            val rider = lion.passengers.firstOrNull() as? Player
            if (rider != null) ride(lion, rider) else lead(lion)
        }
    }

    private fun maybeSpawn(w: World) {
        if (w.players.isEmpty() || Haven.rnd.nextDouble() > 0.08) return             // about every 12 minutes per Haven
        spawnNear(w.players.random())
    }

    /** Brings the lion to a player in Haven (the one already there comes over). */
    fun spawnNear(p: Player): Boolean {
        val w = p.world
        if (!Haven.isHaven(w)) return false
        lions.remove(w.name)?.let { if (it.isValid) it.remove() }
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val at = Haven.safeSpot(w, p.location.blockX + (cos(a) * 24).toInt(), p.location.blockZ + (sin(a) * 24).toInt(), 10)
            ?: Haven.safeSpot(w, p.location.blockX, p.location.blockZ, 6) ?: return false
        val lion = newLion(at)
        lions[w.name] = lion
        trips.remove(lion.uniqueId)
        choose(lion, p)?.let { it.guest = p.uniqueId; trips[lion.uniqueId] = it }
        w.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.6f)
        Haven.plugin.logger.info("Haven: the Lion is near ${p.name} in ${w.name}")
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { HavenMusic.play(p, HavenMusic.Motif.LION_NEAR) }, 30L)
        return true
    }

    /** The Lion itself: big, golden, maned, silent, unhurtable, without a will of the game's own (no goals). */
    private fun newLion(at: Location): Wolf {
        val lion = at.world.spawn(at, Wolf::class.java) { o ->
            o.isPersistent = false
            o.removeWhenFarAway = false
            o.isInvulnerable = true
            // the wolf's body is the most lion-like there is: a broad, deep upper body for the mane (its coat is in the pack)
            io.papermc.paper.registry.RegistryAccess.registryAccess()
                .getRegistry(io.papermc.paper.registry.RegistryKey.WOLF_VARIANT)
                .get(net.kyori.adventure.key.Key.key("haven", "lion"))?.let { o.variant = it }
            o.isSilent = true                                         // its roars are played by us
            o.customName(Component.text("The Lion", NamedTextColor.GOLD))
            o.isCustomNameVisible = false
            o.getAttribute(Attribute.SCALE)?.baseValue = 3.6          // about three blocks tall: stand on a block to climb on
            o.getAttribute(Attribute.MOVEMENT_SPEED)?.baseValue = 0.42
            o.getAttribute(Attribute.STEP_HEIGHT)?.baseValue = 1.5
            o.persistentDataContainer.set(lionKey, PersistentDataType.BYTE, 1)
        }
        Bukkit.getMobGoals().removeAllGoals(lion)
        return lion
    }

    // --- The golden ram's horn: the Lion fights for you ---------------------------------------------------------

    /** A lion called by the horn: whom it fights for, and how long nothing has been left to fight. */
    class Warrior(val owner: UUID, val since: Long = System.currentTimeMillis()) { var calm = 0; var target: org.bukkit.entity.LivingEntity? = null }

    private val warriors = mutableMapOf<UUID, Pair<Wolf, Warrior>>()   // lion -> (lion, warrior)
    private val hornUsed = mutableMapOf<UUID, Long>()                   // player -> last call
    private val hornKey = Haven.key("haven_item")
    private val BOSSES = setOf(org.bukkit.entity.EntityType.ENDER_DRAGON, org.bukkit.entity.EntityType.WITHER, org.bukkit.entity.EntityType.WARDEN,
        org.bukkit.entity.EntityType.ELDER_GUARDIAN)

    @EventHandler
    fun onHorn(e: org.bukkit.event.player.PlayerInteractEvent) {
        if (e.action != org.bukkit.event.block.Action.RIGHT_CLICK_AIR && e.action != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return
        val item = e.item ?: return
        if (item.itemMeta?.persistentDataContainer?.get(hornKey, PersistentDataType.STRING) != "GOLDEN_RAMSHORN") return
        val p = e.player
        if (p.hasCooldown(item)) return                                   // the horn still sounds from the last time
        if (Haven.isHaven(p.world)) { p.sendActionBar(Component.text("In Haven there is nothing to fight", NamedTextColor.GOLD)); return }
        if (p.world.environment == World.Environment.NETHER || p.world.environment == World.Environment.THE_END) {
            val now = System.currentTimeMillis()
            if (now - (quaked[p.uniqueId] ?: 0) < 60_000) { p.sendActionBar(Component.text("The ground has not settled yet", NamedTextColor.GOLD)); return }
            quaked[p.uniqueId] = now
            Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { if (p.isOnline) quake(p) }, 20L)
            return
        }
        if (warriors.values.any { it.second.owner == p.uniqueId }) { p.sendActionBar(Component.text("The Lion is already fighting for you", NamedTextColor.GOLD)); return }
        val now = System.currentTimeMillis()
        val last = hornUsed[p.uniqueId] ?: 0
        if (now - last < 5 * 60_000) {
            p.sendActionBar(Component.text("The horn is silent – the Lion will hear it again in ${(5 * 60_000 - (now - last)) / 60_000 + 1} min", NamedTextColor.GOLD))
            return
        }
        hornUsed[p.uniqueId] = now
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { if (p.isOnline) summonWarrior(p) }, 30L)   // after the horn's call
    }

    private val quaked = mutableMapOf<UUID, Long>()

    /**
     * In the nether and the end the Lion does not come, but the horn shakes the ground: it rumbles and trembles in
     * waves, and every enemy within 20 blocks is flung back, several blocks, the nearer the farther.
     */
    fun quake(p: Player) {
        val w = p.world
        val c = p.location
        for (wave in 0 until 3) Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable {
            w.playSound(c, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.2f, 0.5f + wave * 0.1f)
            w.playSound(c, Sound.ENTITY_GENERIC_EXPLODE, 1.5f, 0.4f)
            val r = 4.0 + wave * 7
            for (i in 0 until 48) {                                       // the ground cracks and jumps in a ring
                val a = i * Math.PI * 2 / 48
                val at = c.clone().add(cos(a) * r, 0.0, sin(a) * r)
                val ground = at.clone().subtract(0.0, 1.0, 0.0).block
                if (!ground.type.isAir) w.spawnParticle(Particle.BLOCK, at.add(0.0, 0.2, 0.0), 6, 0.4, 0.1, 0.4, 0.1, ground.blockData)
            }
            w.spawnParticle(Particle.DUST, c.clone().add(0.0, 0.5, 0.0), 40, r / 2, 0.3, r / 2, gold)
            for (pl in w.getNearbyPlayers(c, 24.0)) pl.velocity = pl.velocity.add(Vector(0.0, 0.18, 0.0))   // a jolt under your feet
            if (wave == 0) for (e in enemies(c, 20.0)) {
                val away = e.location.toVector().subtract(c.toVector()).setY(0)
                val d = away.length().coerceAtLeast(0.5)
                val push = (if (e.type in BOSSES) 0.8 else 2.8) * (1.4 - d / 20)
                e.velocity = away.normalize().multiply(push).setY(0.6 + push / 6)
                e.damage(4.0, p)
            }
        }, wave * 8L)
        p.sendActionBar(Component.text("The horn shakes the ground!", NamedTextColor.GOLD))
        Haven.plugin.logger.info("Haven: ${p.name} blew the golden ram's horn in ${w.name}: the ground shook")
    }

    /** The Lion comes out of a burst of gold beside you, roars, and goes for every enemy around. */
    fun summonWarrior(p: Player) {
        val back = p.location.direction.setY(0).normalize().multiply(-3.0)
        val at = p.location.clone().add(back).takeIf { fits(it, 3.6) } ?: p.location.clone()
        val lion = newLion(at)
        warriors[lion.uniqueId] = lion to Warrior(p.uniqueId)
        val w = p.world
        w.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.5, 0.0), 80, 1.5, 1.5, 1.5, gold)
        w.spawnParticle(Particle.FLASH, at.clone().add(0.0, 1.5, 0.0), 1, 0.0, 0.0, 0.0, 0.0, Color.fromRGB(0xFF, 0xD7, 0x5A))
        w.playSound(at, Sound.ENTITY_RAVAGER_ROAR, 2f, 0.6f)
        HavenMusic.play(at, HavenMusic.Motif.LION)
        p.sendActionBar(Component.text("The Lion has come to fight for you!", NamedTextColor.GOLD))
        Haven.plugin.logger.info("Haven: ${p.name} blew the golden ram's horn in ${w.name}")
    }

    private fun fits(at: Location, s: Double): Boolean {
        val hw = 0.3 * s
        return !at.world.hasCollisionsIn(org.bukkit.util.BoundingBox(at.x - hw, at.y + 0.05, at.z - hw, at.x + hw, at.y + 0.85 * s, at.z + hw))
    }

    private fun enemies(around: Location, r: Double) = around.world.getNearbyLivingEntities(around, r) {
        it is org.bukkit.entity.Enemy && it.isValid && !it.isDead && !isLion(it) }

    /** Twice a second: find the nearest enemy to its friend, run (or pounce) at it and strike; when none are left, go. */
    private fun warriorTick() {
        for ((id, pair) in warriors.toList()) {
            val (lion, war) = pair
            val p = Bukkit.getPlayer(war.owner)
            if (!lion.isValid || p == null || p.world != lion.world || Haven.isHaven(lion.world) || System.currentTimeMillis() - war.since > 5 * 60_000) {
                departWarrior(id); continue
            }
            if (ticks % 2 == 0L) resize(lion)
            if (ticks % 4 == 0L) shimmer(lion)
            if (ticks % 10 != 0L) continue
            if (lion.location.distanceSquared(p.location) > 64.0 * 64) { lion.teleport(p.location); continue }
            val t = war.target?.takeIf { it.isValid && !it.isDead && it.location.distanceSquared(p.location) < 48.0 * 48 }
                ?: enemies(p.location, 40.0).minByOrNull { it.location.distanceSquared(p.location) }
            war.target = t
            if (t == null) {                                              // nothing left: it stays a moment, then goes
                if (++war.calm == 1) lion.pathfinder.moveTo(p.location, 0.8)
                if (war.calm >= 6) {
                    lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ROAR, 1.5f, 0.9f)
                    p.sendActionBar(Component.text("Every enemy is gone. The Lion bows its head to you.", NamedTextColor.GOLD))
                    departWarrior(id)
                }
                continue
            }
            war.calm = 0
            lion.lookAt(t)
            val scale = lion.getAttribute(Attribute.SCALE)?.baseValue ?: 3.6
            val reach = 1.6 + 0.6 * scale
            val d = lion.location.distance(t.location)
            if (d <= reach) strike(lion, t, scale)
            else {
                val walking = lion.pathfinder.moveTo(t.location, 2.2)
                if ((!walking || d > 10) && lion.isOnGround) {                // a pounce: a mighty bound at it
                    val v = t.location.toVector().subtract(lion.location.toVector())
                    val flat = v.clone().setY(0)
                    val speed = (flat.length() / 10).coerceIn(0.6, 2.4)
                    lion.velocity = flat.normalize().multiply(speed).setY(0.5 + (v.y.coerceAtLeast(0.0) / 8).coerceAtMost(1.2))
                    lion.world.playSound(lion.location, Sound.ENTITY_GOAT_LONG_JUMP, 1.5f, 0.6f)
                }
            }
        }
    }

    /** One blow of the paw: heavy, golden, throwing the enemy back. Bosses take it too, but are not felled at once. */
    private fun strike(lion: Wolf, t: org.bukkit.entity.LivingEntity, scale: Double) {
        val boss = t.type in BOSSES
        t.damage(if (boss) 12.0 + 3 * scale else 18.0 + 4 * scale, lion)
        t.velocity = t.location.toVector().subtract(lion.location.toVector()).setY(0).normalize().multiply(1.2).setY(0.45)
        val at = t.location.add(0.0, t.height / 2, 0.0)
        lion.world.spawnParticle(Particle.SWEEP_ATTACK, at, 2, 0.4, 0.3, 0.4, 0.0)
        lion.world.spawnParticle(Particle.DUST, at, 20, 0.6, 0.6, 0.6, gold)
        lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ATTACK, 1.4f, 0.8f)
        if (Haven.rnd.nextInt(4) == 0) lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ROAR, 1.2f, 0.9f)
    }

    /** Gone in a soft burst of gold. */
    private fun departWarrior(id: UUID) {
        val lion = warriors.remove(id)?.first ?: return
        if (lion.isValid) {
            lion.world.spawnParticle(Particle.DUST, lion.location.add(0.0, 1.5, 0.0), 60, 1.2, 1.2, 1.2, gold)
            lion.world.spawnParticle(Particle.END_ROD, lion.location.add(0.0, 1.5, 0.0), 20, 1.0, 1.0, 1.0, 0.05)
            lion.remove()
        }
        squeeze.remove(id)
    }

    /** /the_lion [player]: bring the lion (operators, for testing). */
    fun register(r: io.papermc.paper.command.brigadier.Commands) {
        r.register("the_lion", "Bring the Lion to you (in Haven); /the_lion horn [player] gives the golden ram's horn", object : io.papermc.paper.command.brigadier.BasicCommand {
            override fun permission() = "creator.haven"
            override fun execute(src: io.papermc.paper.command.brigadier.CommandSourceStack, args: Array<String>) {
                if (args.getOrNull(0) == "horn") {
                    val p = args.getOrNull(1)?.let { Bukkit.getPlayerExact(it) } ?: (src.sender as? Player) ?: return src.sender.sendMessage("Who?")
                    val horn = HavenItems.instance?.make(HavenItems.Kind.GOLDEN_RAMSHORN) ?: return
                    p.inventory.addItem(horn).values.forEach { p.world.dropItem(p.location, it) }
                    return src.sender.sendMessage("${p.name} has the golden ram's horn.")
                }
                val p = args.getOrNull(0)?.let { Bukkit.getPlayerExact(it) } ?: (src.sender as? Player) ?: return src.sender.sendMessage("Who?")
                src.sender.sendMessage(if (spawnNear(p)) "The Lion is near ${p.name}." else "${p.name} is not in Haven (or there is no ground near).")
            }
        })
    }

    /** Without a rider it strolls near the players, and goes to greet their helpers now and then. */
    private fun idle(lion: Wolf) {
        if (ticks % 60 != 0L) return
        val w = lion.world
        val friend = w.players.mapNotNull { helper.helpers[it.uniqueId]?.w }.filter { it.isValid && it.world == w }
            .minByOrNull { it.location.distanceSquared(lion.location) }
        if (friend != null && friend.location.distanceSquared(lion.location) < 40.0 * 40 && Haven.rnd.nextInt(4) == 0) {
            if (friend.location.distanceSquared(lion.location) > 9) lion.pathfinder.moveTo(friend.location, 0.7)
            else { lion.lookAt(friend); w.spawnParticle(Particle.DUST, friend.location.add(0.0, 1.2, 0.0), 12, 0.5, 0.6, 0.5, gold) }
            return
        }
        val near = w.players.minByOrNull { it.location.distanceSquared(lion.location) } ?: return
        val d = near.location.distance(lion.location)
        if (d > 18) lion.pathfinder.moveTo(near.location.clone().add(Vector(Haven.rnd.nextDouble() * 8 - 4, 0.0, Haven.rnd.nextDouble() * 8 - 4)), 0.8)
        else if (Haven.rnd.nextInt(3) == 0) Haven.safeSpot(w, lion.location.blockX, lion.location.blockZ, 6)?.let { lion.pathfinder.moveTo(it, 0.6) }
        else lion.lookAt(near)
    }

    /** Riding the lion heals you, mends your armor and what you hold, and gives experience, slowly but surely. */
    private fun care(lion: Wolf) {
        for (rider in lion.passengers) {
            if (rider is Player) {
                val max = rider.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                if (rider.health < max) rider.health = minOf(max, rider.health + 1.0)
                val inv = rider.inventory
                for (item in inv.armorContents.toList() + inv.itemInMainHand + inv.itemInOffHand) mend(item)
                if (ticks % 80 == 0L) rider.giveExp(1)
                if (ticks % 200 == 0L) rider.world.spawnParticle(Particle.DUST, rider.location.add(0.0, 1.0, 0.0), 6, 0.4, 0.5, 0.4, gold)
            } else if (rider is org.bukkit.entity.LivingEntity) {
                val max = rider.getAttribute(Attribute.MAX_HEALTH)?.value ?: 20.0
                if (rider.health < max) rider.health = minOf(max, rider.health + 1.0)
            }
        }
    }

    private fun mend(item: org.bukkit.inventory.ItemStack?) {
        if (item == null || item.type.isAir || item.type.maxDurability <= 0) return
        item.editMeta(org.bukkit.inventory.meta.Damageable::class.java) { m -> if (m.damage > 0) m.damage = maxOf(0, m.damage - 2) }
    }

    // --- Stroking, riding, and never being hurt ---------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onInteract(e: PlayerInteractEntityEvent) {
        val lion = e.rightClicked as? Wolf ?: return
        if (!isLion(lion)) return
        e.isCancelled = true
        if (e.hand != EquipmentSlot.HAND) return
        if (warriors.containsKey(lion.uniqueId)) { lion.lookAt(e.player); return }   // it is fighting for you: no riding now
        val p = e.player
        if (p.isSneaking) {                                       // stroking: it turns to you, calm and kind, and lies down
            lion.lookAt(p)
            lion.pathfinder.stopPathfinding()
            lion.isSitting = true
            lying[lion.uniqueId] = System.currentTimeMillis() + 12_000
            lion.world.spawnParticle(Particle.DUST, lion.location.add(lion.location.direction.multiply(1.2)).add(0.0, 1.8, 0.0), 6, 0.3, 0.2, 0.3, gold)
            p.sendActionBar(Component.text("The Lion looks at you kindly.", NamedTextColor.GOLD))
            return
        }
        if (lion.passengers.isNotEmpty()) return
        val down = lying.containsKey(lion.uniqueId)
        val small = (lion.getAttribute(Attribute.SCALE)?.baseValue ?: FULL) < 2.4
        if (!down && !small && p.location.y < lion.location.y + 0.9) {     // too tall to climb from the ground
            p.sendActionBar(Component.text("The Lion is too tall – stand on a block, or stroke it (sneak + right click)", NamedTextColor.GOLD))
            return
        }
        if (down) { lying.remove(lion.uniqueId); lion.isSitting = false }
        lion.addPassenger(p)
        leaving.remove(lion.uniqueId)                            // ridden again: it has more to show
        // the helper comes along
        helper.helpers[p.uniqueId]?.w?.takeIf { it.isValid && it.world == lion.world && it.location.distanceSquared(lion.location) < 24.0 * 24 && it.vehicle == null }
            ?.let { lion.addPassenger(it) }
        lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 0.8f)
        p.sendActionBar(Component.text("The Lion knows where to go. Hold on!", NamedTextColor.GOLD))
    }

    @EventHandler
    fun onAttack(e: PrePlayerAttackEntityEvent) {
        if (!isLion(e.attacked)) return
        e.isCancelled = true
        (e.attacked as Wolf).lookAt(e.player)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDamage(e: EntityDamageEvent) {
        if (isLion(e.entity)) e.isCancelled = true
        val p = e.entity as? Player ?: return
        if (e.cause == EntityDamageEvent.DamageCause.FALL && p.vehicle?.let { isLion(it) } == true) e.isCancelled = true
    }

    @EventHandler
    fun onDismount(e: EntityDismountEvent) {
        val lion = e.dismounted as? Wolf ?: return
        if (!isLion(lion)) return
        val trip = trips[lion.uniqueId]
        if (trip?.leap != null) { e.isCancelled = true; return }                            // not in the middle of a leap
        val p = e.entity as? Player ?: return
        Bukkit.getScheduler().runTask(Haven.plugin, Runnable {
            helper.helpers[p.uniqueId]?.w?.let { if (it.vehicle == lion) it.leaveVehicle() }
            if (trip != null && trip.arrivedAt > 0 && lion.passengers.none { it is Player }) leaving[lion.uniqueId] = System.currentTimeMillis()
        })
    }

    /** No one near is looking at it (or can see it). */
    private fun unseen(lion: Wolf): Boolean = lion.world.players.none { p ->
        val to = lion.location.add(0.0, 1.0, 0.0).toVector().subtract(p.eyeLocation.toVector())
        to.lengthSquared() < 80.0 * 80 && p.hasLineOfSight(lion) && p.eyeLocation.direction.dot(to.normalize()) > 0.35
    }

    /** Gone without a trace, and it will be a while before it is seen again. */
    private fun vanish(w: World, lion: Wolf) {
        lion.passengers.forEach { lion.removePassenger(it) }
        lion.remove()
        unlight(lion.uniqueId)
        squeeze.remove(lion.uniqueId)
        lying.remove(lion.uniqueId)
        lions.remove(w.name)
        leaving.remove(lion.uniqueId)
        trips.remove(lion.uniqueId)
        alone.remove(lion.uniqueId)
        cooldown[w.name] = System.currentTimeMillis() + 10 * 60_000
    }

    // --- Carrying its rider --------------------------------------------------------------------------------------------

    private fun ride(lion: Wolf, rider: Player) {
        var trip = trips[lion.uniqueId]
        if (trip == null || (trip.arrivedAt > 0 && System.currentTimeMillis() - trip.arrivedAt > 30_000)) {
            leaving.remove(lion.uniqueId)
            trip = choose(lion, rider) ?: return
            trip.guest = rider.uniqueId
            trips[lion.uniqueId] = trip
            rider.sendActionBar(Component.text("The Lion is taking you to ${trip.name}", NamedTextColor.GOLD))
        }
        if (trip.arrivedAt > 0) return
        trip.leap?.let { leap(lion, trip, it); return }
        val dist = horizontal(lion.location, trip.target)
        if (dist < 4.5 && kotlin.math.abs(lion.location.y - trip.target.y) < 4) {
            trip.arrivedAt = System.currentTimeMillis()
            lion.pathfinder.stopPathfinding()
            lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ROAR, 1f, 1.0f)
            seen.getOrPut(rider.uniqueId) { mutableListOf() }.add(trip.target.blockX to trip.target.blockZ)
            rider.sendActionBar(Component.text("Here: ${trip.name}", NamedTextColor.GOLD))
            HavenMusic.play(lion.location, HavenMusic.Motif.LION)
            return
        }
        if (ticks % 10 != 0L) return
        // walking: watch the progress; when it does not get closer, or the way is not walkable, it leaps
        if (dist < trip.lastDistance - 0.8) { trip.lastDistance = dist; trip.stuck = 0 } else trip.stuck++
        val walking = lion.pathfinder.moveTo(trip.target, 1.6)
        val climb = kotlin.math.abs(trip.target.y - lion.location.y) > 6 && dist < 64
        if (!climb && (!walking || trip.stuck >= 3) && trySqueeze(lion)) { trip.stuck = 0; return }
        if (!walking || trip.stuck >= 3 || climb) {
            planLeap(lion, trip)?.let { trip.leap = it; trip.stuck = 0; lion.pathfinder.stopPathfinding()
                lion.world.playSound(lion.location, Sound.ENTITY_GOAT_LONG_JUMP, 1.5f, 0.6f) }
        }
    }

    /**
     * Without a rider: it stays near its guest for a minute or so, then walks toward what it wants to show, stopping
     * to wait when the guest falls behind. Where only a leap would do, it waits by the guest to be ridden. When the
     * guest has seen the place it slips away unseen, after a while.
     */
    private fun lead(lion: Wolf) {
        val w = lion.world
        val trip = trips[lion.uniqueId] ?: run {
            val p = w.players.minByOrNull { it.location.distanceSquared(lion.location) } ?: return
            choose(lion, p)?.also { it.guest = p.uniqueId; trips[lion.uniqueId] = it } ?: run { idle(lion); return }
        }
        val guest = trip.guest?.let { Bukkit.getPlayer(it) }?.takeIf { it.world == w }
            ?: w.players.minByOrNull { it.location.distanceSquared(lion.location) }?.also { trip.guest = it.uniqueId } ?: return
        if (trip.leap != null) { leap(lion, trip, trip.leap!!); return }                      // finishing a leap it began with a rider
        val now = System.currentTimeMillis()
        if (trip.arrivedAt > 0) {
            if (ticks % 20 == 0L) lion.lookAt(guest)
            if (!leaving.containsKey(lion.uniqueId) && horizontal(guest.location, trip.target) < 14) {
                seen.getOrPut(guest.uniqueId) { mutableListOf() }.add(trip.target.blockX to trip.target.blockZ)
                leaving[lion.uniqueId] = now + 20_000                                           // a little while there with you first
            }
            return
        }
        if (now - trip.metAt < trip.wait) { idle(lion); return }
        if (ticks % 10 != 0L) return
        val behind = guest.location.distance(lion.location)
        if (behind > 22) {                                                                     // waits for you, looking back
            lion.pathfinder.stopPathfinding()
            lion.lookAt(guest)
            if (behind > 48) lion.pathfinder.moveTo(guest.location, 0.9)
            return
        }
        val dist = horizontal(lion.location, trip.target)
        if (dist < 4.5 && kotlin.math.abs(lion.location.y - trip.target.y) < 4) {
            trip.arrivedAt = now
            lion.pathfinder.stopPathfinding()
            lion.world.playSound(lion.location, Sound.ENTITY_RAVAGER_ROAR, 0.8f, 1.0f)
            HavenMusic.play(lion.location, HavenMusic.Motif.LION)
            return
        }
        if (dist < trip.lastDistance - 0.8) { trip.lastDistance = dist; trip.stuck = 0 } else trip.stuck++
        val walking = lion.pathfinder.moveTo(trip.target, 0.9)
        if ((!walking || trip.stuck >= 4) && trySqueeze(lion)) { trip.stuck = 0; return }
        if (!walking || trip.stuck >= 4) {                       // no way on foot, even small: back to the guest, to be ridden
            if (behind > 4) lion.pathfinder.moveTo(guest.location, 0.9) else lion.lookAt(guest)
            if (!trip.hinted) { trip.hinted = true; guest.sendActionBar(Component.text("The Lion wants to take you somewhere. Ride it.", NamedTextColor.GOLD)) }
        }
    }

    private fun horizontal(a: Location, b: Location): Double { val dx = a.x - b.x; val dz = a.z - b.z; return sqrt(dx * dx + dz * dz) }

    /** The nearest place worth seeing that this rider has not been shown, or the highest island near by. */
    private fun choose(lion: Wolf, rider: Player): Trip? {
        val w = lion.world
        val here = lion.location
        val shown = seen[rider.uniqueId] ?: emptyList()
        fun fresh(x: Int, z: Int) = shown.none { (sx, sz) -> (sx - x) * (sx - x) + (sz - z) * (sz - z) < 30 * 30 }
        val names = mapOf("well" to "the wishing well", "spring" to "a havenite spring", "gold" to "the gold water",
            "column" to "a levitation column", "rift" to "a time rift")
        val candidates = mutableListOf<Pair<Location, String>>()
        for (p in magic.places) if (p.world == w.name && fresh(p.x, p.z)) candidates.add(Location(w, p.x + 0.5, p.y.toDouble(), p.z + 0.5) to (names[p.type] ?: p.type))
        for ((x, z) in world.landmarks(w)) if (fresh(x, z)) Haven.ground(w, x, z)?.let { candidates.add(it.location.add(0.5, 1.0, 0.5) to "a temple") }
        val best = candidates.filter { horizontal(it.first, here) in 12.0..900.0 }.minByOrNull { horizontal(it.first, here) }
        if (best != null) {
            val spot = Haven.safeSpot(w, best.first.blockX + 3, best.first.blockZ + 3, 4) ?: best.first
            return Trip(spot, best.second)
        }
        var top: Location? = null
        repeat(40) {
            val a = Haven.rnd.nextDouble() * Math.PI * 2
            val r = 40 + Haven.rnd.nextInt(120)
            Haven.safeSpot(w, here.blockX + (cos(a) * r).toInt(), here.blockZ + (sin(a) * r).toInt(), 6)?.let { if (top == null || it.y > top!!.y) top = it }
        }
        return top?.let { Trip(it, "the highest island near by") }
    }

    /**
     * A leap toward the target: straight onto it when it is close enough (up to 40 blocks away and 32 up), otherwise
     * onto the ground along the way that brings it closest.
     */
    private fun planLeap(lion: Wolf, trip: Trip): Leap? {
        val w = lion.world
        val from = lion.location
        val toTarget = trip.target.toVector().subtract(from.toVector()).setY(0)
        val dist = toTarget.length()
        if (dist < 0.1) return null
        val dir = toTarget.normalize()
        fun ok(l: Location) = l.y - from.y <= 48 && from.y - l.y <= 80
        if (dist <= 64 && ok(trip.target)) return leapTo(from, trip.target)
        var best: Location? = null
        var bestLeft = Double.MAX_VALUE
        for (angle in listOf(0.0, 0.25, -0.25, 0.5, -0.5)) for (d in 8..64 step 4) {
            val v = dir.clone().rotateAroundY(angle).multiply(d)
            val g = Haven.ground(w, (from.x + v.x).toInt(), (from.z + v.z).toInt()) ?: continue
            val land = g.location.add(0.5, 1.0, 0.5)
            if (!ok(land) || !land.block.isPassable || !land.clone().add(0.0, 1.0, 0.0).block.isPassable) continue
            val left = horizontal(land, trip.target)
            if (left < bestLeft - 1) { bestLeft = left; best = land }
        }
        return best?.takeIf { bestLeft < dist - 4 }?.let { leapTo(from, it) }
    }

    private fun leapTo(from: Location, to: Location): Leap {
        val d = horizontal(from, to)
        val apex = maxOf(from.y, to.y) + 8 + d / 3                       // super high
        return Leap(from.clone(), to.clone(), (10 + (apex - from.y) / 3).toInt(), (30 + (apex - to.y) * 1.4).toInt(), apex)
    }

    /**
     * Flies the leap tick by tick, so it lands exactly where it meant to: up fast, slowing toward the top (40 % of
     * the way across), then down slowly, gently and ever slower toward the ground, with gold drifting behind it.
     */
    private fun leap(lion: Wolf, trip: Trip, leap: Leap) {
        leap.t++
        val y0 = leap.from.y; val y1 = leap.to.y; val h = leap.apex
        val across: Double
        val y: Double
        if (leap.t <= leap.rise) {
            val u = leap.t.toDouble() / leap.rise
            across = 0.4 * u
            y = y0 + (h - y0) * (1 - (1 - u) * (1 - u))                     // ease out: a powerful spring, slowing at the top
        } else {
            val u = ((leap.t - leap.rise).toDouble() / leap.fall).coerceAtMost(1.0)
            across = 0.4 + 0.6 * kotlin.math.sin(u * Math.PI / 2)            // drifting on, ever gentler
            y = h - (h - y1) * (1 - kotlin.math.cos(u * Math.PI)) / 2       // a soft glide down, slow at both ends
            if (leap.t % 2 == 0) lion.world.spawnParticle(Particle.DUST, lion.location.add(0.0, 0.8, 0.0), 3, 0.8, 0.5, 0.8, gold)
            if (leap.t % 6 == 0) lion.world.spawnParticle(Particle.END_ROD, lion.location.add(0.0, 0.5, 0.0), 1, 0.6, 0.2, 0.6, 0.01)
        }
        val x = leap.from.x + (leap.to.x - leap.from.x) * across
        val z = leap.from.z + (leap.to.z - leap.from.z) * across
        val yaw = Math.toDegrees(atan2(-(leap.to.x - leap.from.x), leap.to.z - leap.from.z)).toFloat()
        lion.setGravity(false)
        lion.teleport(Location(lion.world, x, y, z, yaw, 0f), TeleportFlag.EntityState.RETAIN_PASSENGERS)
        if (leap.t <= leap.rise && leap.t % 3 == 0) lion.world.spawnParticle(Particle.DUST, lion.location.add(0.0, 1.0, 0.0), 4, 0.6, 0.4, 0.6, gold)
        if (leap.t >= leap.duration) {
            lion.setGravity(true)
            lion.velocity = Vector()
            lion.fallDistance = 0f
            trip.leap = null
            trip.lastDistance = horizontal(lion.location, trip.target)
            lion.world.playSound(lion.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 0.7f)    // it lands softly
            lion.world.spawnParticle(Particle.DUST, lion.location.add(0.0, 0.3, 0.0), 30, 1.4, 0.2, 1.4, gold)
        }
    }

    // --- Its size follows the room it has ------------------------------------------------------------------------

    private val FULL = 3.6
    private val SMALL = 0.9
    private val squeeze = mutableMapOf<UUID, Long>()      // lion -> until when it keeps itself small (to find a way through)

    private fun fits(lion: Wolf, at: Location, s: Double): Boolean {
        val hw = 0.3 * s
        return !lion.world.hasCollisionsIn(org.bukkit.util.BoundingBox(at.x - hw, at.y + 0.05, at.z - hw, at.x + hw, at.y + 0.85 * s, at.z + hw))
    }

    /**
     * Its full size where there is room; smaller where it is tight, so it comes through anywhere: under trees, through
     * narrow gaps and low arches. It shrinks quickly where it must and grows back slowly, majestically, in the open.
     */
    private fun resize(lion: Wolf) {
        val a = lion.getAttribute(Attribute.SCALE) ?: return
        val here = lion.location
        val next = lion.pathfinder.currentPath?.nextPoint
        val spots = mutableListOf(here)
        if (next != null) {
            spots.add(next.clone().add(0.5, 0.0, 0.5))
            val dir = next.toVector().add(Vector(0.5, 0.0, 0.5)).subtract(here.toVector()).setY(0)
            if (dir.lengthSquared() > 0.01) spots.add(here.clone().add(dir.normalize().multiply(1.5)))
        }
        var target = FULL
        while (target > SMALL && spots.any { !fits(lion, it, target) }) target -= 0.3
        if ((squeeze[lion.uniqueId] ?: 0) > System.currentTimeMillis()) target = minOf(target, SMALL + 0.3)
        target = target.coerceAtLeast(SMALL)
        val cur = a.baseValue
        val now = if (target < cur) maxOf(target, cur - 0.25) else minOf(target, cur + 0.06)
        if (kotlin.math.abs(now - cur) > 0.001) {
            a.baseValue = now
            if (ticks % 6 == 0L) lion.world.spawnParticle(Particle.DUST, here.clone().add(0.0, 0.5 * now, 0.0), 3, 0.3 * now, 0.3 * now, 0.3 * now, glint)
        }
    }

    /** No way through as it is: it makes itself small for a while and tries again (not over and over). */
    private fun trySqueeze(lion: Wolf): Boolean {
        val now = System.currentTimeMillis()
        val until = squeeze[lion.uniqueId] ?: 0
        if (until > now || now - until < 10_000) return false
        if ((lion.getAttribute(Attribute.SCALE)?.baseValue ?: FULL) <= SMALL + 0.4) return false
        squeeze[lion.uniqueId] = now + 12_000
        lion.pathfinder.stopPathfinding()
        return true
    }

    // --- A faint golden shine ---------------------------------------------------------------------------------------

    private val glint = Particle.DustOptions(Color.fromRGB(0xFF, 0xE4, 0x8C), 0.6f)
    private val lights = mutableMapOf<UUID, org.bukkit.block.Block>()

    /** Tiny golden glints here and there on its coat, now and then: it shines faintly, it does not sparkle. */
    private fun shimmer(lion: Wolf) {
        val b = lion.boundingBox
        val r = Haven.rnd
        lion.world.spawnParticle(Particle.DUST, b.minX + r.nextDouble() * b.widthX, b.minY + r.nextDouble() * b.height,
            b.minZ + r.nextDouble() * b.widthZ, 1, 0.0, 0.0, 0.0, 0.0, glint)
        if (r.nextInt(12) == 0) lion.world.spawnParticle(Particle.WAX_ON, b.centerX, b.maxY, b.centerZ, 1, b.widthX / 3, 0.2, b.widthZ / 3, 0.0)
    }

    /** A soft light goes with it (an invisible light block, level 9, where there is air). */
    private fun glow(lion: Wolf) {
        val at = lion.location.add(0.0, 1.5, 0.0).block
        if (lights[lion.uniqueId] == at) return
        unlight(lion.uniqueId)
        if (at.type.isAir) {
            at.setType(org.bukkit.Material.LIGHT, false)
            (at.blockData as? org.bukkit.block.data.type.Light)?.let { it.level = 9; at.setBlockData(it, false) }
            lights[lion.uniqueId] = at
        }
    }

    private fun unlight(id: UUID) { lights.remove(id)?.let { if (it.type == org.bukkit.Material.LIGHT) it.setType(org.bukkit.Material.AIR, false) } }

    fun shutdown() { warriors.keys.toList().forEach { departWarrior(it) }; lights.keys.toList().forEach { unlight(it) }; lions.values.forEach { it.remove() }; lions.clear() }
}
