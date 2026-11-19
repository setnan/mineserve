package dev.etnan.creator

import io.papermc.paper.entity.TeleportFlag
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.entity.Sheep
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockShearEntityEvent
import org.bukkit.event.entity.EntityDeathEvent
import org.bukkit.event.entity.EntityDismountEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Transformation
import org.bukkit.util.Vector
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.util.UUID

/**
 * The sheep of Haven have magic wool: whatever wool they give there (sheared or not) is magic wool, and five of it
 * around a light crystal make a flying carpet.
 *
 * Right click with the carpet to sit on it, in any world. It flies where you look while you hold forward (faster with
 * sprint), drifts sideways and backwards with the other keys, rises with jump and hovers when you let go. Sneak, or
 * right click again, to step off: you float down. The carpet is woven of block displays (purple with a gold border and tassels) that the player rides and the plugin moves;
 * it is never saved with the world, and the item stays in the inventory all the time, so nothing is lost in a crash.
 */
class HavenCarpet(private val items: HavenItems) : Listener {

    private class Ride(val parts: List<UUID>, var v: Vector = Vector(), var used: Long = System.currentTimeMillis())

    /**
     * The carpet, two wide and three long, in the display's own space (z is forward): an ornate purple weave of six
     * tiles, a border of gold all round, and a gold tassel hanging from each corner. Every part is its own block
     * display at the same spot, moved together; the player rides the first.
     */
    private val WEAVE: List<Triple<Material, Vector3f, Vector3f>> = buildList {
        for (x in listOf(-1f, 0f)) for (z in listOf(-1.5f, -0.5f, 0.5f))
            add(Triple(Material.PURPLE_GLAZED_TERRACOTTA, Vector3f(x, -0.08f, z), Vector3f(1f, 0.06f, 1f)))
        for (x in listOf(-1.12f, 1f)) add(Triple(Material.GOLD_BLOCK, Vector3f(x, -0.085f, -1.62f), Vector3f(0.12f, 0.07f, 3.24f)))
        for (z in listOf(-1.62f, 1.5f)) add(Triple(Material.GOLD_BLOCK, Vector3f(-1f, -0.085f, z), Vector3f(2f, 0.07f, 0.12f)))
        for (x in listOf(-1.14f, 1.02f)) for (z in listOf(-1.64f, 1.52f))
            add(Triple(Material.YELLOW_WOOL, Vector3f(x, -0.3f, z), Vector3f(0.12f, 0.22f, 0.12f)))
    }

    private val rides = HashMap<UUID, Ride>()
    private fun gone(r: Ride) = r.parts.forEach { Bukkit.getEntity(it)?.remove() }
    private val key = Haven.key("flying_carpet")
    private val glitter = Particle.DustOptions(Color.fromRGB(0xD8, 0xB4, 0xFF), 0.8f)

    init { Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 5L, 1L) }

    // --- Magic wool ----------------------------------------------------------------------------------------

    private fun magic(list: List<ItemStack>) = list.map { if (it.type.name.endsWith("_WOOL") && items.kind(it) == null) items.make(HavenItems.Kind.MAGIC_WOOL, it.amount) else it }

    @EventHandler(ignoreCancelled = true)
    fun onShear(e: org.bukkit.event.player.PlayerShearEntityEvent) {
        if (e.entity is Sheep && Haven.isHaven(e.entity.world)) e.drops = magic(e.drops)
    }

    @EventHandler(ignoreCancelled = true)
    fun onShear(e: BlockShearEntityEvent) {
        if (e.entity is Sheep && Haven.isHaven(e.entity.world)) e.drops = magic(e.drops)
    }

    @EventHandler(ignoreCancelled = true)
    fun onDeath(e: EntityDeathEvent) {
        if (e.entity !is Sheep || !Haven.isHaven(e.entity.world)) return
        val drops = magic(e.drops)
        e.drops.clear()
        e.drops.addAll(drops)
    }

    // --- The carpet ----------------------------------------------------------------------------------------

    @EventHandler
    fun onUse(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        if (items.kind(e.item) != HavenItems.Kind.FLYING_CARPET) return
        e.isCancelled = true
        val p = e.player
        val r = rides[p.uniqueId]
        if (r != null) { if (System.currentTimeMillis() - r.used > 400) land(p) ; return }
        if (p.isInsideVehicle) return p.sendActionBar(Component.text("Step off what you are riding first", NamedTextColor.GRAY))
        val at = p.location
        at.pitch = 0f
        val parts = WEAVE.map { (m, from, size) ->
            p.world.spawn(at, BlockDisplay::class.java) { d ->
                d.block = m.createBlockData()
                d.transformation = Transformation(from, AxisAngle4f(), size, AxisAngle4f())
                d.teleportDuration = 2
                d.isPersistent = false
                d.persistentDataContainer.set(key, org.bukkit.persistence.PersistentDataType.BYTE, 1)
            }
        }
        if (!parts[0].addPassenger(p)) { parts.forEach { it.remove() }; return }
        rides[p.uniqueId] = Ride(parts.map { it.uniqueId })
        p.world.playSound(at, Sound.BLOCK_WOOL_PLACE, 1f, 1.4f)
        p.world.playSound(at, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f)
        p.sendActionBar(Component.text("Forward flies where you look, jump rises, sneak steps off", NamedTextColor.LIGHT_PURPLE))
    }

    private fun tick() {
        val it = rides.entries.iterator()
        while (it.hasNext()) {
            val (id, r) = it.next()
            val p = Bukkit.getPlayer(id)
            val d = Bukkit.getEntity(r.parts[0])
            if (p == null || d == null || !d.isValid || p.vehicle != d || !holds(p)) {
                it.remove()
                gone(r)
                if (p != null) off(p)
                continue
            }
            fly(p, d, r)
        }
    }

    private fun holds(p: Player) = items.kind(p.inventory.itemInMainHand) == HavenItems.Kind.FLYING_CARPET ||
        items.kind(p.inventory.itemInOffHand) == HavenItems.Kind.FLYING_CARPET ||
        p.inventory.contents.any { items.kind(it) == HavenItems.Kind.FLYING_CARPET }

    private fun fly(p: Player, d: Entity, r: Ride) {
        val input = p.currentInput
        val look = p.location.direction
        val flat = Vector(look.x, 0.0, look.z).let { if (it.lengthSquared() < 1e-6) Vector(0, 0, 1) else it.normalize() }
        val left = Vector(flat.z, 0.0, -flat.x)
        val want = Vector()
        if (input.isForward) want.add(look.clone().multiply(if (input.isSprint) 0.85 else 0.5))
        if (input.isBackward) want.add(flat.clone().multiply(-0.25))
        if (input.isLeft) want.add(left.clone().multiply(0.3))
        if (input.isRight) want.add(left.clone().multiply(-0.3))
        if (input.isJump) want.y += 0.35
        r.v = r.v.multiply(0.8).add(want.multiply(0.2))
        if (r.v.lengthSquared() < 1e-5) r.v = Vector()

        val at = d.location
        val w = at.world
        fun free(l: Location) = l.y > w.minHeight + 1 && l.y < w.maxHeight + 40 &&
            (0..1).all { h -> l.clone().add(0.0, h.toDouble(), 0.0).block.isPassable }
        var to = at.clone().add(r.v)
        if (!free(to)) {
            val level = at.clone().add(r.v.x, 0.0, r.v.z)
            val up = at.clone().add(0.0, r.v.y, 0.0)
            when {
                free(level) -> { to = level; r.v.y = 0.0 }
                free(up) -> { to = up; r.v.x = 0.0; r.v.z = 0.0 }
                else -> { to = at.clone(); r.v = Vector() }
            }
        }
        to.yaw = p.location.yaw
        to.pitch = 0f
        d.teleport(to, TeleportFlag.EntityState.RETAIN_PASSENGERS)
        for (i in 1 until r.parts.size) Bukkit.getEntity(r.parts[i])?.teleport(to)
        p.fallDistance = 0f
        if (r.v.lengthSquared() > 0.01 && Haven.rnd.nextInt(3) == 0) w.spawnParticle(Particle.DUST, to.clone().subtract(flat.multiply(1.0)), 2, 0.5, 0.05, 0.5, glitter)
    }

    private fun land(p: Player) {
        val r = rides.remove(p.uniqueId) ?: return
        gone(r)
        off(p)
    }

    /** Off the carpet in the air: you float down. */
    private fun off(p: Player) {
        p.fallDistance = 0f
        if (!(p as Entity).isOnGround) p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 15, 0, true, false))
        p.world.playSound(p.location, Sound.BLOCK_WOOL_BREAK, 0.8f, 1.2f)
    }

    @EventHandler
    fun onDismount(e: EntityDismountEvent) {
        val p = e.entity as? Player ?: return
        val r = rides[p.uniqueId] ?: return
        if (e.dismounted.uniqueId != r.parts[0]) return
        rides.remove(p.uniqueId)
        Bukkit.getScheduler().runTask(Haven.plugin, Runnable { gone(r); if (p.isOnline) off(p) })
    }

    @EventHandler
    fun onQuit(e: PlayerQuitEvent) { rides.remove(e.player.uniqueId)?.let { gone(it) } }

    fun shutdown() { rides.values.forEach { gone(it) }; rides.clear() }
}
