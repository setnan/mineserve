package dev.etnan.creator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.type.Light
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.BlockDisplay
import org.bukkit.entity.Display
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerBucketEmptyEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Vector3f
import java.io.File
import java.util.UUID

/**
 * Teleportals: a pair of portals in one world, and you walk straight from one to the other however far apart they are.
 *
 * Build a frame of starstone (HavenItems: star dust and light crystals) shaped like a nether portal, inside at least 2
 * wide and 3 high, and pour a lava bucket into it. Every world (the overworld, the Nether, the End, a Haven) has room
 * for two, and they only work when both stand: one alone is lit but asleep. They never lead to another world. Breaking
 * a frame block closes the portal for good, and it has to be built and lit again.
 *
 * The frame search and the Portal shape are those of the Haven portals (HavenPortals.find). Only starstone a player has
 * set down counts (HavenItems remembers it in the chunk), not froglights from elsewhere. Kept in
 * plugins/Creator/teleportals.yml.
 */
class Teleportals(private val plugin: JavaPlugin, private val frames: HavenPortals, private val items: HavenItems) : Listener {

    companion object { val FRAME = Material.OCHRE_FROGLIGHT }

    private val file = File(plugin.dataFolder, "teleportals.yml")
    private val portals = mutableListOf<HavenPortals.Portal>()
    private val panes = mutableMapOf<String, UUID>()
    private val standing = mutableMapOf<UUID, Int>()
    private val justArrived = mutableSetOf<UUID>()
    private val paneKey = NamespacedKey(plugin, "teleportal_pane")
    private val bright = Particle.DustOptions(Color.fromRGB(0x39, 0xFF, 0x14), 1.2f)
    private val star = Particle.DustOptions(Color.fromRGB(0xFF, 0xE2, 0x8A), 0.9f)

    init {
        if (file.exists()) for (s in YamlConfiguration.loadConfiguration(file).getStringList("portals")) {
            val f = s.split(";")
            if (f.size == 7) portals.add(HavenPortals.Portal(f[0], f[1] == "x", f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt()))
        }
        plugin.logger.info("Teleportals: ${portals.size}")
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 40L, 5L)
    }

    private fun save() {
        val y = YamlConfiguration()
        y.set("portals", portals.map { it.key() })
        y.save(file)
    }

    private fun other(p: HavenPortals.Portal) = portals.firstOrNull { it.world == p.world && it.key() != p.key() }

    private fun frame(p: HavenPortals.Portal, w: World): List<Block> {
        fun at(i: Int, h: Int) = if (p.alongX) w.getBlockAt(p.x + i, p.y + h, p.z) else w.getBlockAt(p.x, p.y + h, p.z + i)
        return (0 until p.width).flatMap { listOf(at(it, -1), at(it, p.height)) } + (0 until p.height).flatMap { listOf(at(-1, it), at(p.width, it)) }
    }

    // --- Lighting one ------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onPour(e: PlayerBucketEmptyEvent) {
        if (e.bucket != Material.LAVA_BUCKET) return
        val at = e.blockClicked.getRelative(e.blockFace)
        val p = frames.find(at, FRAME) ?: return
        e.isCancelled = true
        val w = at.world
        val pl = e.player
        if (portals.any { it.key() == p.key() }) return
        if (frame(p, w).any { items.plantAt(it) != "gate" })
            return pl.sendActionBar(Component.text("Only starstone you have made and set down yourself can hold a teleportal", NamedTextColor.RED))
        if (portals.count { it.world == p.world } >= 2)
            return pl.sendActionBar(Component.text("This world already has its two teleportals: break one of them first", NamedTextColor.RED))
        if (pl.gameMode != GameMode.CREATIVE) {
            val hand = e.hand
            Bukkit.getScheduler().runTask(plugin, Runnable { pl.inventory.setItem(hand, ItemStack(Material.BUCKET)) })
        }
        portals.add(p)
        val light = (Material.LIGHT.createBlockData() as Light).apply { level = 15 }
        p.inside(w).forEach { it.setBlockData(light, false) }
        redraw(p.world)
        save()
        val paired = other(p) != null
        w.playSound(p.centre(w), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, if (paired) 1.8f else 1.0f)
        w.playSound(p.centre(w), Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1f, 1.2f)
        pl.sendActionBar(Component.text(if (paired) "Both teleportals are awake: walk in" else "The teleportal is lit. It wakes when this world has a second one",
            NamedTextColor.GREEN))
        plugin.logger.info("${pl.name} lit a teleportal at ${p.key()}" + if (paired) " (the pair is complete)" else "")
    }

    private fun close(p: HavenPortals.Portal) {
        portals.removeIf { it.key() == p.key() }
        Bukkit.getWorld(p.world)?.let { w ->
            p.inside(w).filter { it.type == Material.LIGHT }.forEach { it.setType(Material.AIR, false) }
            w.playSound(p.centre(w), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 0.8f)
        }
        redraw(p.world)
        save()
        plugin.logger.info("The teleportal at ${p.key()} is closed")
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) = broken(listOf(e.block))

    @EventHandler(ignoreCancelled = true)
    fun onExplode(e: EntityExplodeEvent) = broken(e.blockList())

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(e: BlockExplodeEvent) = broken(e.blockList())

    private fun broken(blocks: List<Block>) {
        for (b in blocks) {
            if (b.type != FRAME && b.type != Material.LIGHT) continue
            portals.filter { it.frames(b) }.forEach { close(it) }
        }
    }

    // --- The pane: bright green when the pair is awake, grey while one waits for the other ------------------------

    private fun redraw(world: String) {
        for (p in portals.filter { it.world == world }) panes.remove(p.key())?.let { Bukkit.getEntity(it)?.remove() }
        panes.keys.filter { k -> portals.none { it.key() == k } }.forEach { k -> panes.remove(k)?.let { Bukkit.getEntity(it)?.remove() } }
    }

    private fun pane(p: HavenPortals.Portal, w: World) {
        panes[p.key()]?.let { id -> if (Bukkit.getEntity(id)?.isValid == true) return }
        val base = if (p.alongX) Location(w, p.x.toDouble(), p.y.toDouble(), p.z + 0.4375) else Location(w, p.x + 0.4375, p.y.toDouble(), p.z.toDouble())
        val d = w.spawn(base, BlockDisplay::class.java) { d ->
            d.block = (if (other(p) != null) Material.LIME_STAINED_GLASS else Material.LIGHT_GRAY_STAINED_GLASS).createBlockData()
            val scale = if (p.alongX) Vector3f(p.width.toFloat(), p.height.toFloat(), 0.125f) else Vector3f(0.125f, p.height.toFloat(), p.width.toFloat())
            d.transformation = Transformation(Vector3f(), AxisAngle4f(), scale, AxisAngle4f())
            d.brightness = Display.Brightness(15, 15)
            d.isPersistent = false
            d.persistentDataContainer.set(paneKey, PersistentDataType.STRING, p.key())
        }
        panes[p.key()] = d.uniqueId
    }

    fun shutdown() { panes.values.forEach { Bukkit.getEntity(it)?.remove() }; panes.clear() }

    // --- Walking through -----------------------------------------------------------------------------------

    private fun tick() {
        for (pl in Bukkit.getOnlinePlayers()) {
            val p = portals.firstOrNull { it.contains(pl.location.block) || it.contains(pl.eyeLocation.block) }
            if (p == null) { standing.remove(pl.uniqueId); justArrived.remove(pl.uniqueId); continue }
            if (pl.uniqueId in justArrived) continue
            val to = other(p)
            val t = standing.merge(pl.uniqueId, 5) { a, b -> a + b }!!
            if (to == null) {
                if (t == 5) pl.sendActionBar(Component.text("This teleportal sleeps until the world has a second one", NamedTextColor.GRAY))
                continue
            }
            if (t == 5) pl.playSound(pl.location, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.4f)
            if (t < 20 && pl.gameMode != GameMode.CREATIVE) continue
            standing.remove(pl.uniqueId)
            justArrived.add(pl.uniqueId)
            val w = pl.world
            val goal = to.arrival(w)
            goal.pitch = pl.location.pitch
            w.spawnParticle(Particle.DUST, pl.location.add(0.0, 1.0, 0.0), 30, 0.4, 0.8, 0.4, bright)
            pl.teleportAsync(goal).thenRun {
                w.playSound(goal, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.3f)
                w.spawnParticle(Particle.DUST, goal.clone().add(0.0, 1.0, 0.0), 30, 0.4, 0.8, 0.4, bright)
            }
        }
        for (p in portals) {
            val w = Bukkit.getWorld(p.world) ?: continue
            if (!w.isChunkLoaded(p.x shr 4, p.z shr 4)) continue
            val c = p.centre(w)
            if (w.players.none { it.location.distanceSquared(c) < 48.0 * 48 }) continue
            pane(p, w)
            val sx = if (p.alongX) p.width / 2.5 else 0.1
            val sz = if (p.alongX) 0.1 else p.width / 2.5
            if (other(p) != null) {
                w.spawnParticle(Particle.DUST, c, 6, sx, p.height / 2.5, sz, bright)
                w.spawnParticle(Particle.DUST, c, 3, sx, p.height / 2.5, sz, star)
                if (Math.random() < 0.3) w.spawnParticle(Particle.END_ROD, c, 1, sx, p.height / 2.5, sz, 0.01)
            } else if (Math.random() < 0.3) w.spawnParticle(Particle.DUST, c, 2, sx, p.height / 2.5, sz, star)
        }
    }
}
