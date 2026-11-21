package dev.etnan.creator

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
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
import org.bukkit.entity.Player
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
import kotlin.math.abs

/**
 * Portals to Haven. Each world family has its own Haven (see Haven.havenOf); you go to the Haven of the world you
 * stand in, and back to the world you came from.
 *
 * Build a frame of shroomlight shaped like a nether portal (inside at least 2 wide and 3 high, at most 21) and pour a
 * water bucket into it: the water is used up and the portal opens, azure with glittering gold. Stand in it for a
 * second to travel. One block in Haven is 16 blocks in the world you came from. On the other side the nearest portal
 * is used, or a new one is built on safe ground. From Haven you go back to the world you came from.
 *
 * The inside of a portal is light blocks (you walk through them and they shine) with a thin azure pane drawn over it.
 * Portals are kept in plugins/Creator/haven.yml. Breaking a frame block closes the portal.
 */
class HavenPortals(private val plugin: JavaPlugin) : Listener {

    /** A portal: the inside starts at (x, y, z) and runs [width] along X (alongX) or Z, and [height] up. */
    data class Portal(val world: String, val alongX: Boolean, val x: Int, val y: Int, val z: Int, val width: Int, val height: Int) {
        fun contains(b: Block): Boolean {
            if (b.world.name != world || b.y < y || b.y >= y + height) return false
            return if (alongX) b.z == z && b.x >= x && b.x < x + width else b.x == x && b.z >= z && b.z < z + width
        }

        fun frames(b: Block): Boolean {
            if (b.world.name != world || b.y < y - 1 || b.y > y + height) return false
            return if (alongX) b.z == z && b.x >= x - 1 && b.x <= x + width else b.x == x && b.z >= z - 1 && b.z <= z + width
        }

        fun inside(w: World): List<Block> = (0 until width).flatMap { i -> (0 until height).map { h ->
            if (alongX) w.getBlockAt(x + i, y + h, z) else w.getBlockAt(x, y + h, z + i) } }

        /** Where you stand when you arrive: the middle of the bottom, facing out of the portal. */
        fun arrival(w: World): Location {
            val l = if (alongX) Location(w, x + width / 2.0, y.toDouble(), z + 0.5) else Location(w, x + 0.5, y.toDouble(), z + width / 2.0)
            l.yaw = if (alongX) 0f else -90f
            return l
        }

        fun centre(w: World): Location = arrival(w).add(0.0, height / 2.0, 0.0)
        fun key(): String = "$world;${if (alongX) "x" else "z"};$x;$y;$z;$width;$height"
    }

    private val file = File(plugin.dataFolder, "haven.yml")
    private val portals = mutableListOf<Portal>()
    private val panes = mutableMapOf<String, UUID>()          // portal key -> its azure pane
    private val standing = mutableMapOf<UUID, Int>()          // player -> ticks stood in a portal
    private val justArrived = mutableSetOf<UUID>()            // must step out of the portal before it works again
    private val fromKey = NamespacedKey(plugin, "haven_from")
    private val paneKey = NamespacedKey(plugin, "haven_pane")


    init {
        load()
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { tick() }, 40L, 5L)
    }

    // --- Opening a portal ------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onPour(e: PlayerBucketEmptyEvent) {
        if (e.bucket != Material.WATER_BUCKET) return
        val at = e.blockClicked.getRelative(e.blockFace)
        val p = find(at) ?: return
        e.isCancelled = true
        if (e.player.gameMode != GameMode.CREATIVE) {
            val hand = e.hand
            Bukkit.getScheduler().runTask(plugin, Runnable { e.player.inventory.setItem(hand, ItemStack(Material.BUCKET)) })
        }
        open(p)
        at.world.playSound(p.centre(at.world), Sound.BLOCK_BEACON_ACTIVATE, 1.2f, 1.4f)
        at.world.playSound(p.centre(at.world), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.5f, 0.8f)
        plugin.logger.info("${e.player.name} opened a Haven portal at ${p.key()}")
    }

    fun empty(b: Block) = b.type.isAir || b.type == Material.WATER || b.type == Material.LIGHT

    /** A finished frame (of shroomlight, or of [frame]) around this block, along X or Z, or null. */
    fun find(at: Block, frame: Material = Material.SHROOMLIGHT): Portal? {
        if (!empty(at)) return null
        for (alongX in listOf(true, false)) {
            val dx = if (alongX) 1 else 0
            val dz = if (alongX) 0 else 1
            var bottom = at
            var n = 0
            while (empty(bottom.getRelative(0, -1, 0)) && n++ < 21) bottom = bottom.getRelative(0, -1, 0)
            if (bottom.getRelative(0, -1, 0).type != frame) continue
            var left = bottom
            n = 0
            while (empty(left.getRelative(-dx, 0, -dz)) && n++ < 21) left = left.getRelative(-dx, 0, -dz)
            if (left.getRelative(-dx, 0, -dz).type != frame) continue
            var width = 1
            while (width <= 21 && empty(left.getRelative(dx * width, 0, dz * width))) width++
            if (left.getRelative(dx * width, 0, dz * width).type != frame || width < 2 || width > 21) continue
            var height = 0
            rows@ while (height < 22) {
                val row = (0 until width).map { left.getRelative(dx * it, height, dz * it) }
                if (row.all { it.type == frame }) break@rows
                if (!row.all { empty(it) }) { height = -1; break@rows }
                if (left.getRelative(-dx, height, -dz).type != frame || left.getRelative(dx * width, height, dz * width).type != frame) { height = -1; break@rows }
                height++
            }
            if (height < 3 || height > 21) continue
            if ((0 until width).any { left.getRelative(dx * it, -1, dz * it).type != frame }) continue
            return Portal(at.world.name, alongX, left.x, left.y, left.z, width, height)
        }
        return null
    }

    private fun open(p: Portal) {
        val w = Bukkit.getWorld(p.world) ?: return
        portals.removeIf { it.key() == p.key() }
        portals.add(p)
        val light = (Material.LIGHT.createBlockData() as Light).apply { level = 15 }
        p.inside(w).forEach { it.setBlockData(light, false) }
        pane(p, w)
        save()
    }

    private fun close(p: Portal) {
        portals.remove(p)
        val w = Bukkit.getWorld(p.world)
        if (w != null) {
            p.inside(w).filter { it.type == Material.LIGHT }.forEach { it.setType(Material.AIR, false) }
            w.playSound(p.centre(w), Sound.BLOCK_BEACON_DEACTIVATE, 1f, 1.4f)
        }
        panes.remove(p.key())?.let { Bukkit.getEntity(it)?.remove() }
        save()
    }

    /** The thin azure pane over the inside, shining at full brightness. Not saved with the world: drawn again when needed. */
    private fun pane(p: Portal, w: World) {
        panes[p.key()]?.let { id -> if (Bukkit.getEntity(id)?.isValid == true) return }
        val base = if (p.alongX) Location(w, p.x.toDouble(), p.y.toDouble(), p.z + 0.4375) else Location(w, p.x + 0.4375, p.y.toDouble(), p.z.toDouble())
        val d = w.spawn(base, BlockDisplay::class.java) { d ->
            d.block = Material.LIGHT_BLUE_STAINED_GLASS.createBlockData()
            val scale = if (p.alongX) Vector3f(p.width.toFloat(), p.height.toFloat(), 0.125f) else Vector3f(0.125f, p.height.toFloat(), p.width.toFloat())
            d.transformation = Transformation(Vector3f(), AxisAngle4f(), scale, AxisAngle4f())
            d.brightness = Display.Brightness(15, 15)
            d.isPersistent = false
            d.persistentDataContainer.set(paneKey, PersistentDataType.STRING, p.key())
        }
        panes[p.key()] = d.uniqueId
    }

    @EventHandler(ignoreCancelled = true)
    fun onBreak(e: BlockBreakEvent) = broken(listOf(e.block))

    @EventHandler(ignoreCancelled = true)
    fun onExplode(e: EntityExplodeEvent) = broken(e.blockList())

    @EventHandler(ignoreCancelled = true)
    fun onBlockExplode(e: BlockExplodeEvent) = broken(e.blockList())

    private fun broken(blocks: List<Block>) {
        for (b in blocks) {
            if (b.type != Material.SHROOMLIGHT && b.type != Material.LIGHT) continue
            portals.filter { it.frames(b) }.forEach { close(it) }
        }
    }

    // --- Travelling ------------------------------------------------------------------------------------

    private fun tick() {
        for (pl in Pure.players()) {
            val p = portals.firstOrNull { it.contains(pl.location.block) || it.contains(pl.eyeLocation.block) }
            if (p == null) { standing.remove(pl.uniqueId); justArrived.remove(pl.uniqueId); continue }
            if (pl.uniqueId in justArrived) continue
            val t = standing.merge(pl.uniqueId, 5) { a, b -> a + b }!!
            if (t == 5) pl.playSound(pl.location, Sound.BLOCK_PORTAL_TRIGGER, 0.4f, 1.8f)
            if (t >= 20 || pl.gameMode == GameMode.CREATIVE) { standing.remove(pl.uniqueId); travel(pl, p) }
        }
        val shown = portals.filter { p -> Bukkit.getWorld(p.world)?.let { w -> w.isChunkLoaded(p.x shr 4, p.z shr 4) && w.players.any { it.location.distanceSquared(p.centre(w)) < 48.0 * 48 } } == true }
        for (p in shown) {
            val w = Bukkit.getWorld(p.world)!!
            pane(p, w)
            sparkle(p, w)
        }
    }

    private val azure = Particle.DustOptions(Color.fromRGB(0x2A, 0xA7, 0xFF), 1.2f)
    private val gold = Particle.DustOptions(Color.fromRGB(0xFF, 0xD7, 0x3A), 0.9f)

    private fun sparkle(p: Portal, w: World) {
        val c = p.centre(w)
        val spreadX = if (p.alongX) p.width / 2.5 else 0.1
        val spreadZ = if (p.alongX) 0.1 else p.width / 2.5
        w.spawnParticle(Particle.DUST, c, 6, spreadX, p.height / 2.5, spreadZ, azure)
        w.spawnParticle(Particle.DUST, c, 3, spreadX, p.height / 2.5, spreadZ, gold)
        if (Math.random() < 0.5) w.spawnParticle(Particle.WAX_ON, c, 2, spreadX, p.height / 2.5, spreadZ, 0.0)
        if (Math.random() < 0.3) w.spawnParticle(Particle.END_ROD, c, 1, spreadX, p.height / 2.5, spreadZ, 0.01)
    }

    private fun travel(pl: Player, from: Portal) {
        val here = pl.world
        val toHaven = !Haven.isHaven(here)
        val dest: World = if (toHaven) Haven.havenOf(here) ?: run { pl.sendActionBar(net.kyori.adventure.text.Component.text("This world has no Haven.")); return }
            else pl.persistentDataContainer.get(fromKey, PersistentDataType.STRING)?.let { Bukkit.getWorld(it) }?.takeIf { Haven.havenOf(it) == here }
                ?: Haven.homeOf(here) ?: Bukkit.getWorlds()[0]
        if (toHaven) pl.persistentDataContainer.set(fromKey, PersistentDataType.STRING, here.name)
        val scale = if (toHaven) 1.0 / 16 else 16.0
        val tx = (pl.location.x * scale).toInt()
        val tz = (pl.location.z * scale).toInt()
        val range = if (toHaven) 16 else 128
        val target = portals.filter { it.world == dest.name }
            .minByOrNull { val a = it.arrival(dest); (a.x - tx) * (a.x - tx) + (a.z - tz) * (a.z - tz) }
            ?.takeIf { val a = it.arrival(dest); abs(a.x - tx) <= range && abs(a.z - tz) <= range }
            ?: build(dest, tx, tz)
        justArrived.add(pl.uniqueId)
        pl.teleportAsync(target.arrival(dest)).thenRun {
            dest.playSound(target.centre(dest), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1.5f, 1.2f)
            if (toHaven) dest.spawnParticle(Particle.END_ROD, pl.location.add(0.0, 1.0, 0.0), 40, 0.6, 1.0, 0.6, 0.05)
            if (toHaven) HavenMusic.play(pl, HavenMusic.Motif.ARRIVAL)
        }
        plugin.logger.info("${pl.name} went through a Haven portal to ${dest.name} ${target.key()}")
    }

    /** A new portal on safe ground near (x, z): a 4 x 5 shroomlight frame, on a small quartz floor where there is no ground. */
    private fun build(w: World, x: Int, z: Int): Portal {
        val spot = ground(w, x, z)
        val gx = spot?.x ?: x
        val gz = spot?.z ?: z
        val gy = spot?.y ?: (if (Haven.isHaven(w)) 100 else (w.seaLevel + 10))
        if (spot == null) for (i in -1..4) for (j in -2..2) w.getBlockAt(gx + i, gy - 1, gz + j).type = Material.SMOOTH_QUARTZ
        for (i in 0..3) for (h in 0..4) {
            val b = w.getBlockAt(gx + i, gy + h, gz)
            b.type = if (i == 0 || i == 3 || h == 0 || h == 4) Material.SHROOMLIGHT else Material.AIR
        }
        for (i in 0..3) for (j in listOf(-1, 1)) for (h in 1..3) w.getBlockAt(gx + i, gy + h, gz + j).let { if (!it.isPassable) it.type = Material.AIR }
        val p = Portal(w.name, true, gx + 1, gy + 1, gz, 2, 3)
        open(p)
        return p
    }

    /** Solid, dry ground with room for the frame and a step in front and behind, searched outwards from (x, z). */
    private fun ground(w: World, x: Int, z: Int): Block? {
        for (r in 0..48 step 4) for (dx in -r..r step 4) for (dz in -r..r step 4) {
            if (abs(dx) != r && abs(dz) != r) continue
            val top = surface(w, x + dx, z + dz) ?: continue
            val y = top.y + 1
            val ok = (0..3).all { i -> val below = w.getBlockAt(top.x + i, top.y, top.z)
                below.type.isSolid && !below.isLiquid && !Tag.isLeaves(below.type) &&
                (0..4).all { h -> w.getBlockAt(top.x + i, y + h, top.z).isPassable && !w.getBlockAt(top.x + i, y + h, top.z).isLiquid } }
            if (ok) return w.getBlockAt(top.x, y, top.z)
        }
        return null
    }

    private object Tag { fun isLeaves(m: Material) = m.name.endsWith("_LEAVES") }

    private fun surface(w: World, x: Int, z: Int): Block? {
        if (w.environment == World.Environment.NETHER) {
            for (y in 100 downTo 32) {
                val b = w.getBlockAt(x, y, z)
                if (b.type.isSolid && (1..5).all { b.getRelative(0, it, 0).isPassable }) return b
            }
            return null
        }
        if (Haven.isHaven(w) && HavenGenerator.isletOver(w.seed, x, z)) return null      // on the land, not on an islet in the sky
        val b = w.getHighestBlockAt(x, z)
        return if (b.y <= w.minHeight + 1 || b.isLiquid) null else b
    }

    // --- /haven for operators ---------------------------------------------------------------------------

    fun register(r: Commands) {
        r.register("haven", "Haven portals: list, portal <world|hope_haven> <x> <z>, close <n>, go [player], temple (builds one in front of you), fruit (gives you both fruits), kit (starstone, lava, a flying carpet), seabuilder (one where you stand)", object : BasicCommand {
            override fun permission() = "creator.haven"
            override fun execute(src: CommandSourceStack, args: Array<String>) {
                val to = src.sender
                when (args.firstOrNull()?.lowercase()) {
                    "portal" -> {
                        val w = args.getOrNull(1)?.let { n -> Bukkit.getWorld(n) ?: Bukkit.getWorld(n + "_haven") }
                            ?: return to.sendMessage("No such world.")
                        val p = build(w, args.getOrNull(2)?.toIntOrNull() ?: 0, args.getOrNull(3)?.toIntOrNull() ?: 0)
                        to.sendMessage("Portal in ${w.name} at ${p.x} ${p.y} ${p.z}.")
                    }
                    "close" -> {
                        val p = args.getOrNull(1)?.toIntOrNull()?.let { portals.getOrNull(it - 1) } ?: return to.sendMessage("Which one? See /haven for the numbers.")
                        Bukkit.getWorld(p.world)?.let { w -> for (i in -1..p.width) for (h in -1..p.height) (if (p.alongX) w.getBlockAt(p.x + i, p.y + h, p.z) else w.getBlockAt(p.x, p.y + h, p.z + i)).let { if (it.type == Material.SHROOMLIGHT) it.type = Material.AIR } }
                        close(p)
                        to.sendMessage("Closed and removed " + p.key() + ".")
                    }
                    "fruit" -> {
                        val pl = to as? Player ?: return to.sendMessage("Only in the game.")
                        HavenItems.instance?.let { pl.inventory.addItem(it.make(HavenItems.Kind.SEA_FRUIT, 4), it.make(HavenItems.Kind.GLOW_FRUIT, 4)) }
                        to.sendMessage("Four sea fruit and four glow fruit.")
                    }
                    "kit" -> {
                        val pl = to as? Player ?: return to.sendMessage("Only in the game.")
                        HavenItems.instance?.let { pl.inventory.addItem(it.make(HavenItems.Kind.STARSTONE, 20), it.make(HavenItems.Kind.FLYING_CARPET), it.make(HavenItems.Kind.MAGIC_WOOL, 5), it.make(HavenItems.Kind.STAR_DUST, 5), it.make(HavenItems.Kind.LIGHT_CRYSTAL, 5), org.bukkit.inventory.ItemStack(Material.LAVA_BUCKET, 1)) }
                        to.sendMessage("Starstone for two teleportals, lava, a flying carpet, and wool, dust and crystals to try the recipes.")
                    }
                    "temple" -> {
                        val pl = to as? Player ?: return to.sendMessage("Only in the game.")
                        if (!Haven.isHaven(pl.world)) return to.sendMessage("Only in a Haven.")
                        val at = pl.location.add(pl.location.direction.setY(0).multiply(16.0))
                        to.sendMessage(if (HavenWorld.instance?.temple(pl.world, at.blockX, at.blockZ) == true) "An angel temple at ${at.blockX} ${at.blockZ}." else "No ground there.")
                    }
                    "seabuilder" -> {
                        val pl = to as? Player ?: return to.sendMessage("Only in the game.")
                        if (!Haven.isHaven(pl.world)) return to.sendMessage("Only in a Haven.")
                        HavenCreatures.instance?.spawnBuilder(pl.location.add(0.0, 1.0, 0.0), sea = true)
                        to.sendMessage("A sea builder. It looks for sea floor within 24 blocks and starts within ten seconds.")
                    }
                    "go" -> {
                        val pl = args.getOrNull(1)?.let { Bukkit.getPlayerExact(it) } ?: (to as? Player) ?: return to.sendMessage("Who?")
                        val p = portals.firstOrNull { it.world == pl.world.name } ?: build(pl.world, pl.location.blockX + 2, pl.location.blockZ)
                        travel(pl, p)
                    }
                    else -> {
                        to.sendMessage("Haven worlds: ${Haven.worlds.joinToString { it.name + " (" + (Haven.homeOf(it)?.name ?: "?") + ")" }}. ${portals.size} portals:")
                        portals.forEachIndexed { i, p -> to.sendMessage("  ${i + 1}. " + p.key()) }
                    }
                }
            }
        })
    }

    // --- Saving ----------------------------------------------------------------------------------------

    private fun load() {
        if (!file.exists()) return
        val y = YamlConfiguration.loadConfiguration(file)
        for (s in y.getStringList("portals")) {
            val f = s.split(";")
            if (f.size == 7) portals.add(Portal(f[0], f[1] == "x", f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt()))
        }
        // panes from before a restart that were not removed (they are not saved, but just in case)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            for (w in Bukkit.getWorlds()) for (d in w.getEntitiesByClass(BlockDisplay::class.java))
                if (d.persistentDataContainer.has(paneKey) && d.uniqueId !in panes.values) d.remove()
        }, 60L)
        plugin.logger.info("Haven: ${portals.size} portals")
    }

    private fun save() {
        val y = YamlConfiguration()
        y.set("portals", portals.map { it.key() })
        y.save(file)
    }
}
