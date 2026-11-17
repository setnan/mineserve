package dev.etnan.creator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.Mob
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import java.util.UUID
import kotlin.random.Random

/**
 * Haven: the shared bits of the Haven dimension (one per world: world_haven, hope_haven and so on) that the other Haven classes use.
 *
 *   HavenPortals    shroomlight portals lit with water, 1 block in Haven = 16 at home
 *   HavenItems      diamond shards, havenite, light crystals, pearls, the time pearl, golden barley, nacre, saplings
 *   HavenArmor      what havenite armor and light crystals do
 *   HavenWorld      the rules there (no hunger, healing water, blessing, golden chickens, flying horses) and what is
 *                   added to new chunks (clouds, floating stones, barley fields, crystals, clams, magic places)
 *   HavenMagic      levitation columns, time rifts, the wishing well, gold water, havenite springs, the rainbow, time pearls
 *   HavenCreatures  angels, builders and the sky birds
 *   HavenArchitecture  the white pearl buildings with azure domes that builders and temples are made of
 */
object Haven {
    lateinit var plugin: JavaPlugin
    lateinit var skins: Skins
    var chickens: GoldenChicken? = null
    val rnd = Random(System.nanoTime())

    /** Every world has its own Haven, <world>_haven (see HavenWorlds). */
    val worlds: List<World> get() = Bukkit.getWorlds().filter { isHaven(it) }
    fun isHaven(w: World?) = w != null && w.name.endsWith("_haven", ignoreCase = true)

    /** The world a nether, end or Haven belongs to: its name without _nether, _the_end or _haven. */
    fun root(w: World) = w.name.removeSuffix("_nether").removeSuffix("_the_end").removeSuffix("_haven")

    /** The Haven of a world (or of its nether or end), or null if it has none. */
    fun havenOf(home: World): World? = if (isHaven(home)) home else Bukkit.getWorld(root(home) + "_haven")

    /** The world a Haven belongs to. */
    fun homeOf(haven: World): World? = Bukkit.getWorld(root(haven))

    fun key(name: String) = NamespacedKey(plugin, name)

    /** Things the time pearl has frozen, until when (ms). Haven's own creatures check this before they move. */
    val frozen = mutableMapOf<UUID, Long>()
    fun isFrozen(e: Entity) = (frozen[e.uniqueId] ?: 0L) > System.currentTimeMillis()

    /** Top solid block at (x, z) that is not water or leaves, or null (open sea or void). */
    fun ground(w: World, x: Int, z: Int): Block? {
        val b = w.getHighestBlockAt(x, z)
        if (b.y <= w.minHeight + 1 || b.isLiquid || b.type.name.endsWith("_LEAVES") || !b.type.isSolid) return null
        return b
    }

    /** A free spot on the ground near (x, z) within r blocks, as a location to stand on. */
    fun safeSpot(w: World, x: Int, z: Int, r: Int): Location? {
        for (i in 0 until 40) {
            val bx = x + Random.nextInt(-r, r + 1)
            val bz = z + Random.nextInt(-r, r + 1)
            val g = ground(w, bx, bz) ?: continue
            val above = g.getRelative(0, 1, 0)
            if (above.isPassable && above.getRelative(0, 1, 0).isPassable && !above.isLiquid) return above.location.add(0.5, 0.0, 0.5)
        }
        return null
    }

    fun near(l: Location, r: Double) = l.world.players.any { it.location.distanceSquared(l) < r * r }

    fun freezeMob(e: Entity, ms: Long) {
        frozen[e.uniqueId] = System.currentTimeMillis() + ms
        if (e is Mob) {
            e.setAI(false)
            Bukkit.getScheduler().runTaskLater(plugin, Runnable { if (e.isValid && !isFrozen(e)) e.setAI(true) }, ms / 50 + 2)
        }
    }

    fun air(m: Material) = m.isAir
    fun inHaven(p: Player) = isHaven(p.world)
}
