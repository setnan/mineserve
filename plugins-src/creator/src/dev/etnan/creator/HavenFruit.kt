package dev.etnan.creator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.GameMode
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.Levelled
import org.bukkit.block.data.Waterlogged
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.block.Action
import org.bukkit.event.player.PlayerInteractEvent
import java.io.File
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.player.PlayerItemConsumeEvent
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.util.BoundingBox
import java.util.UUID
import kotlin.math.sqrt

/**
 * The two fruits of Haven. Sea fruit (the turquoise blocks on the sea floor) makes whoever eats one as small as a
 * player can be for a minute. Glow fruit (the light green, glowing blocks in the caves) makes them as big as there is
 * room for, up to sixteen times their size, for a minute: in a cave that is not much, out in the open it is all of it,
 * and they keep growing when they come out. A giant steps, jumps and reaches like a giant, and is not hurt by rock
 * around the head. The size is a modifier on top of the player's own size, so it goes away by itself on logout.
 *
 * Both can be planted, in any world: sneak and right click the top of a block with the fruit. Sea fruit goes on the
 * bottom of a sea or lake (a blue coral fan shows where), glow fruit on moss or earth out of the daylight (a fern). After
 * eight to fourteen minutes the sprout is a fruit block, which gives one to three fruits. Sprouts are kept in
 * plugins/Creator/haven-fruit.yml; a sprout that is broken or dies is simply gone.
 */
class HavenFruit(private val items: HavenItems) : Listener {

    companion object {
        const val SECONDS = 60
        const val SMALL = 0.0625
        const val BIG = 16.0
        val STEPS = doubleArrayOf(16.0, 14.0, 12.0, 10.0, 8.0, 6.0, 4.0, 3.0, 2.0, 1.5)
    }

    private class Spell(val until: Long, val big: Boolean, var size: Double, var told: Boolean = false)

    private val spells = HashMap<UUID, Spell>()
    private val scaleKey = Haven.key("fruit_scale")
    private val stepKey = Haven.key("fruit_step")
    private val jumpKey = Haven.key("fruit_jump")
    private val fallKey = Haven.key("fruit_fall")
    private val speedKey = Haven.key("fruit_speed")
    private val digKey = Haven.key("fruit_dig")
    private val hitKey = Haven.key("fruit_hit")
    private val reachKey = Haven.key("fruit_reach")
    private val touchKey = Haven.key("fruit_touch")
    private val turquoise = Particle.DustOptions(Color.fromRGB(0x2A, 0xD4, 0xD0), 1.4f)
    private val green = Particle.DustOptions(Color.fromRGB(0xB6, 0xF5, 0x6A), 1.6f)

    private class Sprout(val world: String, val x: Int, val y: Int, val z: Int, val sea: Boolean, val ripe: Long)

    private val file = File(Haven.plugin.dataFolder, "haven-fruit.yml")
    private val sprouts = mutableListOf<Sprout>()
    private val seabed = setOf(Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.DIRT, Material.CLAY, Material.MUD, Material.PRISMARINE,
        Material.CALCITE, Material.STONE, Material.MOSS_BLOCK)
    private val soil = setOf(Material.MOSS_BLOCK, Material.PALE_MOSS_BLOCK, Material.GRASS_BLOCK, Material.DIRT, Material.COARSE_DIRT, Material.ROOTED_DIRT,
        Material.PODZOL, Material.MUD)

    init {
        if (file.exists()) for (s in YamlConfiguration.loadConfiguration(file).getStringList("sprouts")) {
            val f = s.split(";")
            if (f.size == 6) sprouts.add(Sprout(f[0], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4] == "sea", f[5].toLong()))
        }
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 10L, 10L)
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { ripen() }, 100L, 100L)
    }

    // --- Planting ----------------------------------------------------------------------------------------

    private fun save() {
        val y = YamlConfiguration()
        y.set("sprouts", sprouts.map { "${it.world};${it.x};${it.y};${it.z};${if (it.sea) "sea" else "glow"};${it.ripe}" })
        y.save(file)
    }

    @EventHandler
    fun onPlant(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_BLOCK || !e.player.isSneaking) return
        val item = e.item ?: return
        val sea = when (items.kind(item)) {
            HavenItems.Kind.SEA_FRUIT -> true
            HavenItems.Kind.GLOW_FRUIT -> false
            else -> return
        }
        e.isCancelled = true                                                  // sneaking with a fruit is planting, never eating
        val p = e.player
        val ground = e.clickedBlock ?: return
        val at = ground.getRelative(BlockFace.UP)
        if (e.blockFace != BlockFace.UP) return p.sendActionBar(Component.text("Plant it on top of a block", NamedTextColor.GRAY))
        if (sea) {
            val water = at.type == Material.WATER && (at.blockData as? Levelled)?.level == 0
            if (!water || ground.type !in seabed) return p.sendActionBar(Component.text("Sea fruit grows under water, on the bottom of a sea or lake", NamedTextColor.GRAY))
            at.setBlockData((Material.TUBE_CORAL_FAN.createBlockData() as Waterlogged).apply { isWaterlogged = true }, false)
        } else {
            if (!at.type.isAir || ground.type !in soil) return p.sendActionBar(Component.text("Glow fruit grows on moss or earth", NamedTextColor.GRAY))
            if (at.lightFromSky > 8) return p.sendActionBar(Component.text("Glow fruit grows only out of the daylight: in a cave or under a roof", NamedTextColor.GRAY))
            at.setType(Material.FERN, false)
        }
        if (p.gameMode != GameMode.CREATIVE) item.amount -= 1
        sprouts.removeIf { it.world == at.world.name && it.x == at.x && it.y == at.y && it.z == at.z }
        sprouts.add(Sprout(at.world.name, at.x, at.y, at.z, sea, System.currentTimeMillis() + (8 + Haven.rnd.nextInt(7)) * 60_000L))
        save()
        at.world.playSound(at.location, if (sea) Sound.BLOCK_WET_GRASS_PLACE else Sound.BLOCK_MOSS_PLACE, 1f, 1.2f)
        p.sendActionBar(Component.text("Planted: there is a fruit here in about ten minutes", if (sea) NamedTextColor.AQUA else NamedTextColor.GREEN))
    }

    private var rounds = 0

    /** Sprouts whose time has come turn into fruit, when their chunk is loaded. */
    private fun ripen() {
        val now = System.currentTimeMillis()
        var changed = false
        val it = sprouts.iterator()
        while (it.hasNext()) {
            val s = it.next()
            if (s.ripe > now) continue
            val w = Bukkit.getWorld(s.world) ?: continue
            if (!w.isChunkLoaded(s.x shr 4, s.z shr 4)) continue
            val b = w.getBlockAt(s.x, s.y, s.z)
            it.remove()
            changed = true
            if (b.type != (if (s.sea) Material.TUBE_CORAL_FAN else Material.FERN)) continue
            if (s.sea) items.seaFruit(b) else items.glowFruit(b)
            w.spawnParticle(Particle.DUST, b.location.add(0.5, 0.7, 0.5), 20, 0.4, 0.4, 0.4, if (s.sea) turquoise else green)
            w.playSound(b.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, if (s.sea) 1.5f else 0.9f)
        }
        if (changed) save()
        if (++rounds % 6 == 0) items.strayFruit()
    }

    // --- Eating ------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onEat(e: PlayerItemConsumeEvent) {
        val big = when (items.kind(e.item)) {
            HavenItems.Kind.SEA_FRUIT -> false
            HavenItems.Kind.GLOW_FRUIT -> true
            else -> return
        }
        val p = e.player
        Bukkit.getScheduler().runTask(Haven.plugin, Runnable { if (p.isOnline) start(p, big) })
    }

    fun start(p: Player, big: Boolean) {
        clear(p)
        val s = Spell(System.currentTimeMillis() + SECONDS * 1000L, big, 1.0)
        spells[p.uniqueId] = s
        val at = p.location.add(0.0, 1.0, 0.0)
        if (big) {
            grow(p, s)
            p.world.playSound(at, Sound.ENTITY_PUFFER_FISH_BLOW_UP, 1f, 0.5f)
            p.world.spawnParticle(Particle.DUST, at, 60, 0.6, 1.0, 0.6, green)
        } else {
            size(p, s, SMALL)
            p.world.playSound(at, Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 1f, 1.8f)
            p.world.spawnParticle(Particle.DUST, at, 60, 0.4, 0.8, 0.4, turquoise)
            p.sendActionBar(Component.text("You are tiny for a minute", NamedTextColor.AQUA))
        }
    }

    private fun tick() {
        val now = System.currentTimeMillis()
        val it = spells.entries.iterator()
        while (it.hasNext()) {
            val (id, s) = it.next()
            val p = Bukkit.getPlayer(id)
            if (p == null) { it.remove(); continue }
            if (now >= s.until || p.isDead || Pure.isPure(p.world)) {         // a fruit does nothing in a world without cheats
                it.remove()
                clear(p)
                p.world.playSound(p.location, Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 0.8f, 1f)
                p.sendActionBar(Component.text("You are your own size again", NamedTextColor.GRAY))
                continue
            }
            if (s.big && s.size < BIG) grow(p, s)
            val left = (s.until - now) / 1000
            if (left in 1..5) p.sendActionBar(Component.text("Your own size again in $left", NamedTextColor.GRAY))
        }
    }

    /** As big as there is room for where the player stands: never smaller than now, at most sixteen times. */
    private fun grow(p: Player, s: Spell) {
        val fits = STEPS.firstOrNull { it > s.size && room(p, it) }
        if (fits != null) {
            size(p, s, fits)
            p.sendActionBar(Component.text(if (fits >= BIG) "You are a giant for a minute" else "You grow as far as there is room: come out in the open", NamedTextColor.GREEN))
            s.told = true
        } else if (!s.told) {
            s.told = true
            p.sendActionBar(Component.text("No room to grow here: come out in the open", NamedTextColor.GREEN))
        }
    }

    /** Room for the upper two thirds of a body [size] times the player's own (the legs may stand in a slope). */
    private fun room(p: Player, size: Double): Boolean {
        val own = p.getAttribute(Attribute.SCALE)?.baseValue ?: 1.0
        val l = p.location
        val half = 0.28 * own * size
        val tall = 1.8 * own * size
        if (l.y + tall > l.world.maxHeight) return false
        return !l.world.hasCollisionsIn(BoundingBox(l.x - half, l.y + tall * 0.35, l.z - half, l.x + half, l.y + tall, l.z + half))
    }

    private fun size(p: Player, s: Spell, size: Double) {
        s.size = size
        val own = p.getAttribute(Attribute.SCALE)?.baseValue ?: 1.0
        set(p, Attribute.SCALE, scaleKey, own * size - own)
        if (size <= 1.0) return
        set(p, Attribute.STEP_HEIGHT, stepKey, 0.6 * (size - 1))
        set(p, Attribute.JUMP_STRENGTH, jumpKey, 0.25 * (sqrt(size) - 1))
        set(p, Attribute.SAFE_FALL_DISTANCE, fallKey, 3.0 * size)
        set(p, Attribute.BLOCK_INTERACTION_RANGE, reachKey, 2.0 * (size - 1))
        set(p, Attribute.ENTITY_INTERACTION_RANGE, touchKey, 2.0 * (size - 1))
        // as fast as it is big: a giant sixteen times your size takes strides sixteen times as long. Flying too, as far
        // as the game lets a player fly (ten times the usual speed)
        set(p, Attribute.MOVEMENT_SPEED, speedKey, size - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        set(p, Attribute.BLOCK_BREAK_SPEED, digKey, size - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)   // and digs and hits as hard
        set(p, Attribute.ATTACK_DAMAGE, hitKey, size - 1, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
        if (p.uniqueId !in flew) flew[p.uniqueId] = p.flySpeed
        p.flySpeed = (flew.getValue(p.uniqueId) * size).toFloat().coerceAtMost(1f)
    }

    private var digging = false

    /**
     * A giant's pick, shovel or axe takes a giant's bite: breaking a block takes with it the ball of blocks around it,
     * half as many blocks across as the giant is times bigger (eight across at sixteen times). Every block is asked
     * for like a block the player broke (so whatever guards a block guards it here too, and Haven's own finds drop as
     * usual); chests and other blocks that hold something are left, and so is what cannot be broken. What falls out
     * is gathered in stacks where the giant struck. The tool wears as for one block.
     */
    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.MONITOR)
    fun onDig(e: org.bukkit.event.block.BlockBreakEvent) {
        if (digging) return
        val p = e.player
        val s = spells[p.uniqueId] ?: return
        val across = (s.size / 2).toInt().coerceAtMost(8)
        if (!s.big || across < 2) return
        val r = across / 2.0
        val mid = e.block
        val tool = p.inventory.itemInMainHand
        val got = HashMap<org.bukkit.inventory.ItemStack, Int>()
        digging = true
        try {
            val n = Math.ceil(r).toInt()
            for (dx in -n..n) for (dy in -n..n) for (dz in -n..n) {
                if (dx == 0 && dy == 0 && dz == 0 || dx * dx + dy * dy + dz * dz > r * r) continue
                val b = mid.getRelative(dx, dy, dz)
                if (b.type.isAir || b.isLiquid || b.type.hardness < 0 || b.state is org.bukkit.block.TileState) continue
                val ev = org.bukkit.event.block.BlockBreakEvent(b, p)
                Bukkit.getPluginManager().callEvent(ev)
                if (ev.isCancelled) continue
                if (ev.isDropItems && p.gameMode != GameMode.CREATIVE) for (d in b.getDrops(tool, p)) got.merge(d.asOne(), d.amount) { a, c -> a + c }
                b.setType(Material.AIR, true)
            }
        } finally { digging = false }
        val at = mid.location.add(0.5, 0.5, 0.5)
        for ((item, amount) in got) {
            var left = amount
            while (left > 0) { val k = minOf(left, item.maxStackSize); mid.world.dropItem(at, item.asQuantity(k)); left -= k }
        }
        mid.world.playSound(at, Sound.ENTITY_GENERIC_EXPLODE, 0.5f, 1.6f)
        mid.world.spawnParticle(Particle.BLOCK, at, 60, r / 2, r / 2, r / 2, mid.blockData)
    }

    private val flew = HashMap<java.util.UUID, Float>()                     // how fast each giant flew before it grew

    @EventHandler
    fun onQuit(e: org.bukkit.event.player.PlayerQuitEvent) { if (e.player.uniqueId in spells || e.player.uniqueId in flew) clear(e.player) }

    private fun set(p: Player, a: Attribute, key: org.bukkit.NamespacedKey, amount: Double, op: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER) {
        val inst = p.getAttribute(a) ?: return
        inst.removeModifier(key)
        inst.addTransientModifier(AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY))
    }

    private fun clear(p: Player) {
        spells.remove(p.uniqueId)
        for ((a, k) in listOf(Attribute.SCALE to scaleKey, Attribute.STEP_HEIGHT to stepKey, Attribute.JUMP_STRENGTH to jumpKey,
            Attribute.SAFE_FALL_DISTANCE to fallKey, Attribute.BLOCK_INTERACTION_RANGE to reachKey, Attribute.ENTITY_INTERACTION_RANGE to touchKey,
            Attribute.MOVEMENT_SPEED to speedKey, Attribute.BLOCK_BREAK_SPEED to digKey, Attribute.ATTACK_DAMAGE to hitKey)) p.getAttribute(a)?.removeModifier(k)
        flew.remove(p.uniqueId)?.let { p.flySpeed = it }
    }

    fun shutdown() { spells.keys.toList().forEach { id -> Bukkit.getPlayer(id)?.let { clear(it) } } }

    /** A giant's head is often in the rock, and a tiny one may be caught in a gap when the minute is over. */
    @EventHandler(ignoreCancelled = true)
    fun onHurt(e: EntityDamageEvent) {
        if (e.cause != EntityDamageEvent.DamageCause.SUFFOCATION) return
        val p = e.entity as? Player ?: return
        if (spells.containsKey(p.uniqueId)) e.isCancelled = true
    }
}
