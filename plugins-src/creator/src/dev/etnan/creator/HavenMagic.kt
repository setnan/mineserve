package dev.etnan.creator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Chicken
import org.bukkit.entity.Item
import org.bukkit.entity.Player
import org.bukkit.event.Listener
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.EnchantmentStorageMeta
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.io.File
import java.util.UUID
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haven's magic places, kept in plugins/Creator/haven-places.yml:
 *
 *   column    a shimmering pillar of pearly light that lifts you, straight up or slanting. It moves now and then, but
 *             never while someone is in it, and never while a golden chicken lives in it (the chickens like it there)
 *   rift      a flickering crack in the air that takes you to the same safe spot on the ground, near or far. Rifts move
 *             now and then
 *   well      the wishing well: throw something in for a blessing (once a game day), the better the gift the better
 *             the blessing, better still with an angel near. Fall in and you are lifted onto a cloud high above,
 *             which you can fly around on until it blinks and fades
 *   gold      gold water: deadly to fall in; what you throw in turns to gold nuggets and the armor you wear to gold.
 *             Havenite is immune, and a full havenite set heals you there like a golden apple
 *   spring    havenite water, very rare: it hurts, but every piece of armor you wear becomes havenite
 *
 * And the rainbow: now and then, for a short while, it points the way to the nearest havenite spring.
 */
class HavenMagic(private val items: HavenItems, private val armor: HavenArmor) : Listener {

    companion object { var instance: HavenMagic? = null }

    data class Place(val world: String, val type: String, var x: Int, var y: Int, var z: Int, var tx: Int = 0, var ty: Int = 0, var tz: Int = 0,
                     var dx: Double = 0.0, var dz: Double = 0.0, var height: Int = 0, var frozenUntil: Long = 0) {
        fun loc(w: World) = Location(w, x + 0.5, y.toDouble(), z + 0.5)
        fun line() = "$type;$x;$y;$z;$tx;$ty;$tz;$dx;$dz;$height;$world"
    }

    private val file = File(Haven.plugin.dataFolder, "haven-places.yml")
    val places = mutableListOf<Place>()
    private val wished = mutableMapOf<UUID, Long>()          // player -> last wish (ms)
    private val riftCooldown = mutableMapOf<UUID, Long>()
    private val clouds = mutableMapOf<UUID, Long>()          // player -> when the cloud fades
    private val rainbows = mutableMapOf<UUID, Pair<Long, Location>>()
    private var ticks = 0L

    init {
        instance = this
        load()
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 40L, 2L)
    }

    // --- Making places (called by HavenWorld for new chunks) ------------------------------------------------

    fun add(p: Place) { places.add(p); save() }

    fun makeColumn(w: World, x: Int, z: Int): Boolean {
        val g = Haven.ground(w, x, z) ?: return false
        val slant = Haven.rnd.nextInt(3)
        val ang = Haven.rnd.nextDouble() * Math.PI * 2
        add(Place(w.name, "column", x, g.y + 1, z, dx = if (slant == 0) 0.0 else cos(ang) * 0.5, dz = if (slant == 0) 0.0 else sin(ang) * 0.5, height = 24 + Haven.rnd.nextInt(30)))
        return true
    }

    fun makeRift(w: World, x: Int, z: Int): Boolean {
        val g = Haven.ground(w, x, z) ?: return false
        val p = Place(w.name, "rift", x, g.y + 2, z)
        retarget(w, p)
        add(p)
        return true
    }

    private fun retarget(w: World, p: Place) {
        val far = Haven.rnd.nextBoolean()
        val r = if (far) 300 + Haven.rnd.nextInt(500) else 30 + Haven.rnd.nextInt(80)
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val l = Haven.safeSpot(w, p.x + (cos(a) * r).toInt(), p.z + (sin(a) * r).toInt(), 24)
            ?: Haven.safeSpot(w, p.x, p.z, 40) ?: return
        p.tx = l.blockX; p.ty = l.blockY; p.tz = l.blockZ
    }

    fun makeWell(w: World, x: Int, z: Int): Boolean {
        val g = Haven.ground(w, x, z) ?: return false
        val y = g.y
        for (i in -2..2) for (j in -2..2) {
            val edge = abs(i) == 2 || abs(j) == 2
            w.getBlockAt(x + i, y, z + j).type = if (edge) Material.QUARTZ_BRICKS else Material.WATER
            w.getBlockAt(x + i, y - 1, z + j).type = Material.QUARTZ_BRICKS
            w.getBlockAt(x + i, y + 1, z + j).type = if (edge && abs(i) == 2 && abs(j) == 2) Material.GOLD_BLOCK else if (edge) Material.QUARTZ_SLAB else Material.AIR
            for (k in 2..5) if (!(abs(i) == 2 && abs(j) == 2)) w.getBlockAt(x + i, y + k, z + j).type = Material.AIR
        }
        for ((i, j) in listOf(-2 to -2, 2 to -2, -2 to 2, 2 to 2)) for (k in 2..4) w.getBlockAt(x + i, y + k, z + j).type = Material.QUARTZ_PILLAR
        for (i in -2..2) for (j in -2..2) w.getBlockAt(x + i, y + 5, z + j).type = if (abs(i) == 2 || abs(j) == 2) Material.GOLD_BLOCK else Material.LIGHT_BLUE_STAINED_GLASS
        w.getBlockAt(x, y + 4, z).type = Material.IRON_CHAIN
        w.getBlockAt(x, y + 3, z).type = Material.LANTERN
        add(Place(w.name, "well", x, y, z))
        return true
    }

    fun makePool(w: World, x: Int, z: Int, type: String): Boolean {
        val g = Haven.ground(w, x, z) ?: return false
        val y = g.y
        val r = if (type == "spring") 1 else 2
        for (i in -r - 1..r + 1) for (j in -r - 1..r + 1) {
            val inside = i * i + j * j <= r * r
            val rim = !inside && i * i + j * j <= (r + 1) * (r + 1) + 1
            if (inside) { w.getBlockAt(x + i, y, z + j).type = Material.WATER; w.getBlockAt(x + i, y - 1, z + j).type = if (type == "gold") Material.GOLD_BLOCK else Material.PRISMARINE_BRICKS }
            else if (rim) w.getBlockAt(x + i, y, z + j).type = if (type == "gold") Material.RAW_GOLD_BLOCK else Material.WAXED_OXIDIZED_CUT_COPPER
            for (k in 1..3) if (inside || rim) w.getBlockAt(x + i, y + k, z + j).let { if (!it.type.isAir && !it.type.name.endsWith("_LEAVES")) it.type = Material.AIR }
        }
        add(Place(w.name, type, x, y, z, height = r))
        return true
    }

    fun freezeSites(at: Location, ms: Long) {
        for (p in places) if (p.type == "column" && p.world == at.world.name && p.loc(at.world).distanceSquared(at) < 32.0 * 32) p.frozenUntil = System.currentTimeMillis() + ms
    }

    // --- Every other tick ------------------------------------------------------------------------------------

    private val pearlLight = Particle.DustOptions(Color.fromRGB(0xF4, 0xF0, 0xFF), 1.3f)
    private val azure = Particle.DustOptions(Color.fromRGB(0x2A, 0xA7, 0xFF), 1.1f)
    private val goldDust = Particle.DustOptions(Color.fromRGB(0xFF, 0xD7, 0x3A), 1.1f)
    private val teal = Particle.DustOptions(Color.fromRGB(0x3F, 0xE0, 0xC0), 1.2f)

    private fun tick() {
        ticks++
        val now = System.currentTimeMillis()
        clouds.entries.removeIf { (id, until) -> cloud(Bukkit.getPlayer(id), until, now) }
        for (w in Haven.worlds) tick(w, now)
    }

    private fun tick(w: World, now: Long) {
        val players = w.players
        if (players.isEmpty()) return
        for (p in places) {
            if (p.world != w.name) continue
            val at = p.loc(w)
            if (players.none { it.location.distanceSquared(at) < 64.0 * 64 }) continue
            when (p.type) {
                "column" -> column(w, p, now)
                "rift" -> rift(w, p, now)
                "well" -> well(w, p, now)
                "gold", "spring" -> pool(w, p)
            }
        }
        if (ticks % 10 == 0L) for (pl in players) rainbow(pl, now)
        if (ticks % 3000 == 0L) wander(w)
    }

    private fun inColumn(p: Place, l: Location): Boolean {
        val up = l.y - p.y
        if (up < -0.5 || up > p.height) return false
        val cx = p.x + 0.5 + p.dx * up
        val cz = p.z + 0.5 + p.dz * up
        return (l.x - cx) * (l.x - cx) + (l.z - cz) * (l.z - cz) < 1.6 * 1.6
    }

    private fun column(w: World, p: Place, now: Long) {
        val frozen = p.frozenUntil > now
        if (ticks % 2 == 0L) for (i in 0 until 4) {
            val up = Haven.rnd.nextDouble() * p.height
            val l = Location(w, p.x + 0.5 + p.dx * up, p.y + up, p.z + 0.5 + p.dz * up)
            w.spawnParticle(Particle.DUST, l, 2, 0.5, 0.2, 0.5, pearlLight)
            if (Haven.rnd.nextInt(6) == 0) w.spawnParticle(Particle.END_ROD, l, 1, 0.4, 0.4, 0.4, 0.0)
        }
        if (frozen) return
        for (e in w.getNearbyEntities(p.loc(w).add(p.dx * p.height / 2, p.height / 2.0, p.dz * p.height / 2), 4.0 + abs(p.dx) * p.height, p.height / 2.0 + 2, 4.0 + abs(p.dz) * p.height)) {
            if (!inColumn(p, e.location)) continue
            if (e is Chicken) { e.setGravity(false); e.velocity = Vector(0.0, 0.0, 0.0); continue }   // it likes it here
            if (e is Player && e.isFlying) continue
            e.velocity = Vector(p.dx * 0.45, 0.42, p.dz * 0.45)
            e.fallDistance = 0f
            // a moment of levitation, unseen: without it the server takes the lift for flying and kicks the player
            if (e is Player) e.addPotionEffect(PotionEffect(PotionEffectType.LEVITATION, 12, 0, true, false, false))
            if (e is Player && ticks % 20 == 0L) e.playSound(e.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.5f, 1.8f)
        }
    }

    private fun anchored(w: World, p: Place) = w.getNearbyEntities(p.loc(w).add(0.0, p.height / 2.0, 0.0), 3.0 + abs(p.dx) * p.height, p.height / 2.0 + 2, 3.0 + abs(p.dz) * p.height)
        .any { (it is Chicken && Haven.chickens?.isGolden(it) == true && inColumn(p, it.location)) || (it is Player && inColumn(p, it.location)) }

    /** Every ten minutes or so, some columns and rifts move (not columns someone or a golden chicken is in). */
    private fun wander(w: World) {
        var changed = false
        for (p in places) {
            if (p.world != w.name || Haven.rnd.nextDouble() > 0.3) continue
            val chunkLoaded = w.isChunkLoaded(p.x shr 4, p.z shr 4)
            if (p.type == "column") {
                if (chunkLoaded && anchored(w, p)) continue
                if (!chunkLoaded) continue
                val nx = p.x + Haven.rnd.nextInt(-40, 41)
                val nz = p.z + Haven.rnd.nextInt(-40, 41)
                if (!w.isChunkLoaded(nx shr 4, nz shr 4)) continue
                val g = Haven.ground(w, nx, nz) ?: continue
                p.x = nx; p.z = nz; p.y = g.y + 1
                changed = true
            } else if (p.type == "rift") {
                if (!chunkLoaded) continue
                if (Haven.rnd.nextBoolean()) retarget(w, p) else {
                    val nx = p.x + Haven.rnd.nextInt(-60, 61)
                    val nz = p.z + Haven.rnd.nextInt(-60, 61)
                    if (!w.isChunkLoaded(nx shr 4, nz shr 4)) continue
                    val g = Haven.ground(w, nx, nz) ?: continue
                    p.x = nx; p.z = nz; p.y = g.y + 2
                }
                changed = true
            }
        }
        if (changed) save()
    }

    private fun rift(w: World, p: Place, now: Long) {
        val at = p.loc(w)
        w.spawnParticle(Particle.DUST, at, 6, 0.15, 0.9, 0.15, azure)
        w.spawnParticle(Particle.DUST, at, 3, 0.2, 1.0, 0.2, goldDust)
        if (ticks % 4 == 0L) w.spawnParticle(Particle.REVERSE_PORTAL, at, 4, 0.1, 0.8, 0.1, 0.02)
        if (ticks % 30 == 0L) w.playSound(at, Sound.BLOCK_NOTE_BLOCK_HAT, 0.3f, 2f)
        for (pl in w.getNearbyPlayers(at, 1.4, 1.6)) {
            if ((riftCooldown[pl.uniqueId] ?: 0) > now) continue
            if (p.ty == 0) retarget(w, p)
            val to = Location(w, p.tx + 0.5, p.ty.toDouble(), p.tz + 0.5, pl.location.yaw, pl.location.pitch)
            riftCooldown[pl.uniqueId] = now + 4000
            w.playSound(at, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.6f)
            pl.teleportAsync(to).thenRun { w.spawnParticle(Particle.REVERSE_PORTAL, to, 40, 0.5, 1.0, 0.5, 0.1); w.playSound(to, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.4f) }
        }
    }

    private fun well(w: World, p: Place, now: Long) {
        val at = p.loc(w).add(0.0, 0.8, 0.0)
        if (ticks % 6 == 0L) w.spawnParticle(Particle.END_ROD, at, 2, 1.2, 0.5, 1.2, 0.01)
        if (ticks % 4 == 0L) w.spawnParticle(Particle.DUST, at, 3, 1.0, 0.2, 1.0, azure)
        for (e in w.getNearbyEntities(p.loc(w), 1.6, 1.2, 1.6)) {
            if (e is Item) {
                val thrower = e.thrower?.let { Bukkit.getPlayer(it) } ?: continue
                val gift = e.itemStack
                e.remove()
                wish(thrower, gift, p, now)
            } else if (e is Player && e.location.block.type == Material.WATER && (clouds[e.uniqueId] ?: 0) < now) {
                lift(e, now)
            }
        }
    }

    private fun value(it: ItemStack): Int {
        val k = items.kind(it)
        return when {
            k == HavenItems.Kind.TIME_PEARL -> 5
            k == HavenItems.Kind.HAVENITE_INGOT || k == HavenItems.Kind.HAVENITE_SCRAP || k == HavenItems.Kind.PEARL -> 3
            it.type == Material.DIAMOND || it.type == Material.DIAMOND_BLOCK || it.type == Material.EMERALD || k == HavenItems.Kind.DIAMOND_SHARD && it.amount >= 9 -> 2
            it.type.name.contains("GOLD") || it.type.name.contains("IRON") || k != null -> 1
            else -> 0
        }
    }

    private fun wish(pl: Player, gift: ItemStack, p: Place, now: Long) {
        val w = pl.world
        val at = p.loc(w).add(0.0, 1.0, 0.0)
        if ((wished[pl.uniqueId] ?: 0) > now - 20 * 60_000) {            // one game day (20 minutes)
            pl.sendActionBar(Component.text("The water is still. Come back tomorrow.", NamedTextColor.AQUA))
            w.dropItem(at, gift)
            return
        }
        wished[pl.uniqueId] = now
        var tier = value(gift)
        if (HavenCreatures.instance?.angelNear(at, 16.0) == true) tier++
        w.playSound(at, Sound.BLOCK_BELL_RESONATE, 1f, 1.6f)
        w.playSound(at, Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f)
        HavenMusic.play(pl, HavenMusic.Motif.WELL)
        w.spawnParticle(Particle.DUST, at, 80, 1.5, 1.5, 1.5, goldDust)
        w.spawnParticle(Particle.END_ROD, at, 40, 1.0, 1.5, 1.0, 0.05)
        val blessing = when {
            tier >= 5 && Haven.rnd.nextInt(25) == 0 -> "the golden ram's horn" to items.make(HavenItems.Kind.GOLDEN_RAMSHORN)
            tier >= 5 -> listOf("time" to items.make(HavenItems.Kind.TIME_PEARL), "havenite" to items.make(HavenItems.Kind.HAVENITE_INGOT)).random()
            tier == 4 -> listOf("havenite" to items.make(HavenItems.Kind.HAVENITE_SCRAP, 2), "book" to book(), "diamonds" to ItemStack(Material.DIAMOND, 12)).random()
            tier == 3 -> listOf("pearl" to items.make(HavenItems.Kind.PEARL, 2), "diamonds" to ItemStack(Material.DIAMOND, 6), "book" to book(), "havenite" to items.make(HavenItems.Kind.HAVENITE_SCRAP)).random()
            tier == 2 -> listOf("diamonds" to ItemStack(Material.DIAMOND, 3), "gold" to ItemStack(Material.GOLD_INGOT, 16), "pearl" to items.make(HavenItems.Kind.PEARL)).random()
            tier == 1 -> listOf("gold" to ItemStack(Material.GOLD_INGOT, 6), "apples" to ItemStack(Material.GOLDEN_APPLE, 2)).random()
            else -> null
        }
        // always a blessing on the body too: the better the gift, the longer and stronger
        val secs = 60 * (1 + tier)
        pl.health = pl.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: pl.health
        pl.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 20 * secs, 0))
        if (tier >= 1) { pl.addPotionEffect(PotionEffect(PotionEffectType.HASTE, 20 * secs, 1)); pl.addPotionEffect(PotionEffect(PotionEffectType.LUCK, 20 * secs, 1)) }
        if (tier >= 2) pl.addPotionEffect(PotionEffect(PotionEffectType.STRENGTH, 20 * secs, 0))
        if (tier >= 3) pl.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * secs, 0))
        if (blessing != null) pl.world.dropItem(pl.location, blessing.second)
        pl.sendActionBar(Component.text("The well blesses you" + if (blessing != null) " (${blessing.first})" else "", NamedTextColor.GOLD))
        Haven.plugin.logger.info("Haven: ${pl.name} wished with ${gift.type} (tier $tier): ${blessing?.first ?: "a blessing"}")
    }

    private fun book(): ItemStack {
        val b = ItemStack(Material.ENCHANTED_BOOK)
        val pick = listOf(Enchantment.MENDING to 1, Enchantment.UNBREAKING to 3, Enchantment.PROTECTION to 4, Enchantment.FEATHER_FALLING to 4,
            Enchantment.LOOTING to 3, Enchantment.FORTUNE to 3, Enchantment.SHARPNESS to 5, Enchantment.EFFICIENCY to 5, Enchantment.SILK_TOUCH to 1,
            Enchantment.RESPIRATION to 3, Enchantment.DEPTH_STRIDER to 3, Enchantment.POWER to 5, Enchantment.INFINITY to 1).random()
        b.editMeta(EnchantmentStorageMeta::class.java) { it.addStoredEnchant(pick.first, pick.second, true) }
        return b
    }

    /** Fell into the well: up onto a cloud high above, fly it around for a minute, then it blinks and fades. */
    private fun lift(pl: Player, now: Long) {
        val w = pl.world
        var top = pl.location.blockY
        for (dx in -32..32 step 8) for (dz in -32..32 step 8) top = maxOf(top, w.getHighestBlockYAt(pl.location.blockX + dx, pl.location.blockZ + dz))
        val to = pl.location.clone().also { it.y = minOf(w.maxHeight - 4.0, top + 25.0) }
        clouds[pl.uniqueId] = now + 60_000
        pl.teleportAsync(to).thenRun {
            pl.allowFlight = true
            pl.isFlying = true
            pl.sendActionBar(Component.text("A cloud carries you. Fly it around!", NamedTextColor.WHITE))
            w.playSound(to, Sound.ENTITY_BREEZE_WIND_BURST, 1f, 0.8f)
        }
    }

    /** Draws the cloud under a player riding one; true when it is gone. */
    private fun cloud(pl: Player?, until: Long, now: Long): Boolean {
        if (pl == null || !pl.isOnline) return true
        val left = until - now
        val blink = left < 10_000 && (left / 400) % 2 == 0L
        if (!blink) pl.world.spawnParticle(Particle.CLOUD, pl.location.add(0.0, -0.4, 0.0), 12, 0.8, 0.1, 0.8, 0.0)
        if (left < 10_000 && left > 9_800) pl.sendActionBar(Component.text("The cloud is fading...", NamedTextColor.GRAY))
        if (left > 0) { if (!pl.allowFlight) { pl.allowFlight = true; pl.isFlying = true }; return false }
        if (pl.gameMode == org.bukkit.GameMode.SURVIVAL || pl.gameMode == org.bukkit.GameMode.ADVENTURE) { pl.isFlying = false; pl.allowFlight = false }
        pl.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 20 * 20, 0))
        pl.world.spawnParticle(Particle.CLOUD, pl.location, 40, 1.0, 0.3, 1.0, 0.05)
        return true
    }

    private fun pool(w: World, p: Place) {
        val at = p.loc(w).add(0.0, 0.9, 0.0)
        val gold = p.type == "gold"
        if (ticks % 3 == 0L) w.spawnParticle(Particle.DUST, at, 4, p.height * 0.7, 0.2, p.height * 0.7, if (gold) goldDust else teal)
        if (ticks % 7 == 0L) w.spawnParticle(Particle.WAX_ON, at, 2, p.height * 0.7, 0.2, p.height * 0.7, 0.0)
        if (ticks % 10 != 0L) return
        for (e in w.getNearbyEntities(p.loc(w), p.height + 0.6, 1.5, p.height + 0.6)) {
            if (e.location.block.type != Material.WATER) continue
            if (e is Item && gold && e.itemStack.type != Material.GOLD_NUGGET && items.kind(e.itemStack) == null) {
                e.itemStack = ItemStack(Material.GOLD_NUGGET, e.itemStack.amount)
                w.spawnParticle(Particle.WAX_ON, e.location, 8, 0.2, 0.2, 0.2, 0.0)
                w.playSound(e.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 2f)
                continue
            }
            val pl = e as? Player ?: continue
            if (gold) {
                if (armor.fullSet(pl)) {
                    if (!pl.hasPotionEffect(PotionEffectType.ABSORPTION)) pl.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 0))
                    pl.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 20 * 5, 1))
                    continue
                }
                val inv = pl.inventory
                val armorNow = inv.armorContents
                var turned = false
                for (i in armorNow.indices) {
                    val a = armorNow[i] ?: continue
                    if (items.isHavenite(a) || a.type.name.startsWith("GOLDEN_")) continue
                    val slot = a.type.name.substringAfter('_', "")
                    val to = Material.matchMaterial("GOLDEN_$slot") ?: continue
                    armorNow[i] = a.withType(to)
                    turned = true
                }
                if (turned) { inv.armorContents = armorNow; pl.sendActionBar(Component.text("Your armor turned to gold!", NamedTextColor.GOLD)) }
                if (armor.pieces(pl) == 0) pl.damage(4.0)
            } else {
                if (!armor.fullSet(pl)) pl.damage(3.0)
                val inv = pl.inventory
                val armorNow = inv.armorContents
                var made = 0
                for (i in armorNow.indices) {
                    val a = armorNow[i] ?: continue
                    if (!items.isHavenite(a)) items.toHavenite(a)?.let { armorNow[i] = it; made++ }      // every armor piece
                }
                if (made > 0) {
                    inv.armorContents = armorNow
                    w.playSound(pl.location, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.4f)
                    w.spawnParticle(Particle.END_ROD, pl.location.add(0.0, 1.0, 0.0), 60, 0.5, 1.0, 0.5, 0.05)
                    pl.sendActionBar(Component.text("Your armor became havenite!", NamedTextColor.AQUA))
                }
            }
        }
    }

    /** Now and then a rainbow shows the way to the nearest havenite spring, for a minute and a half. */
    private fun rainbow(pl: Player, now: Long) {
        val w = pl.world
        val r = rainbows[pl.uniqueId]
        if (r == null || r.first < now) {
            rainbows.remove(pl.uniqueId)
            if (Haven.rnd.nextDouble() > 0.0015) return                    // about once every 20 minutes
            val spring = places.filter { it.type == "spring" && it.world == w.name }.minByOrNull { it.loc(w).distanceSquared(pl.location) } ?: return
            rainbows[pl.uniqueId] = (now + 90_000) to spring.loc(w)
            pl.sendActionBar(Component.text("A rainbow! Follow it.", NamedTextColor.LIGHT_PURPLE))
            return
        }
        val target = r.second
        val dir = target.toVector().subtract(pl.location.toVector()).setY(0)
        if (dir.lengthSquared() < 1) return
        dir.normalize()
        val colours = listOf(0xFF3B3B, 0xFF9A2E, 0xFFE23B, 0x4BE04B, 0x2AA7FF, 0x6A4BFF, 0xC04BFF)
        val base = pl.location.clone().add(dir.clone().multiply(6.0))
        val side = Vector(-dir.z, 0.0, dir.x)
        for (i in 0..24) {
            val t = i / 24.0
            val along = dir.clone().multiply(t * 40)
            val up = sin(t * Math.PI) * 18
            for ((c, col) in colours.withIndex()) {
                val l = base.clone().add(along).add(0.0, up + 4 + c * 0.6, 0.0).add(side.clone().multiply(0.0))
                w.spawnParticle(Particle.DUST, l, 1, 0.1, 0.1, 0.1, Particle.DustOptions(Color.fromRGB(col), 1.6f))
            }
        }
    }

    // --- Saving ------------------------------------------------------------------------------------------------

    private fun load() {
        if (!file.exists()) return
        for (s in YamlConfiguration.loadConfiguration(file).getStringList("places")) {
            val f = s.split(";")
            if (f.size < 10) continue
            places.add(Place(f.getOrElse(10) { "world_haven_haven" }, f[0], f[1].toInt(), f[2].toInt(), f[3].toInt(), f[4].toInt(), f[5].toInt(), f[6].toInt(), f[7].toDouble(), f[8].toDouble(), f[9].toInt()))
        }
    }

    fun save() {
        val y = YamlConfiguration()
        y.set("places", places.map { it.line() })
        y.save(file)
    }

    fun nearest(type: String, l: Location) = places.filter { it.type == type && it.world == l.world.name }.minByOrNull { it.loc(l.world).distanceSquared(l) }
    fun distanceTo(type: String, l: Location) = nearest(type, l)?.let { sqrt(it.loc(l.world).distanceSquared(l)) } ?: Double.MAX_VALUE
}
