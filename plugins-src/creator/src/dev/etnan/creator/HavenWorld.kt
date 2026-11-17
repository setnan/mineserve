package dev.etnan.creator

import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.block.data.Waterlogged
import org.bukkit.block.data.type.Farmland
import org.bukkit.block.data.type.SeaPickle
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.AbstractHorse
import org.bukkit.entity.Chicken
import org.bukkit.entity.Enemy
import org.bukkit.entity.Horse
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityMountEvent
import org.bukkit.event.entity.EntityTameEvent
import org.bukkit.event.entity.FoodLevelChangeEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.inventory.EquipmentSlot
import org.bukkit.persistence.PersistentDataType
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import java.io.File

/**
 * The rules in Haven and what new land there gets.
 *
 * Haven is the home of the allays: they live there, in the flower meadows and dawn groves most of all.
 *
 * Rules: no hunger; the water heals you and you can breathe in it; no monsters (only Haven's own creatures); every
 * chicken is a golden chicken; clouds are soft (no fall damage on them); the first time you come you are blessed. A
 * gentle sparkle drifts in the air and you hear wind chimes now and then.
 *
 * Flying horses (white, with "winged" in their name): they only trust golden apples, many of them, before they let you
 * ride. Ridden, jump to take off; they fly where you look while you hold forward and glide down slowly otherwise.
 *
 * New chunks get: drifting clouds and floating stones, barley fields on the golden plains, light crystals in the crystal
 * valley, extra flowers in the flower meadow, clams on the sea floor, golden chickens, flying horses, now and then an
 * angel or a builder, and rarely a magic place (levitation column, time rift, wishing well, gold water, havenite
 * spring) or an angel temple. The regions builders and temples have taken are kept in haven-land.yml.
 */
class HavenWorld(private val items: HavenItems, private val magic: HavenMagic, private val creatures: HavenCreatures) : Listener {

    companion object { var instance: HavenWorld? = null }

    private val blessedKey = Haven.key("haven_blessed")
    private val wingedKey = Haven.key("winged_horse")
    private val applesKey = Haven.key("winged_apples")
    private val file = File(Haven.plugin.dataFolder, "haven-land.yml")
    private val claims = mutableListOf<Pair<String, IntArray>>()   // world, (x, z, radius) of buildings and building sites
    private var ticks = 0L

    init {
        instance = this
        if (file.exists()) YamlConfiguration.loadConfiguration(file).getStringList("claims").forEach { s ->
            val f = s.split(",")
            if (f.size >= 3) claims.add(f.getOrElse(3) { "world_haven_haven" } to intArrayOf(f[0].toInt(), f[1].toInt(), f[2].toInt())) }
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 40L, 1L)
    }

    fun free(w: World, x: Int, z: Int, r: Int) = claims.none { (n, c) -> n == w.name && (c[0] - x) * (c[0] - x) + (c[1] - z) * (c[1] - z) < (c[2] + r) * (c[2] + r) }
    fun claim(w: World, x: Int, z: Int, r: Int) { claims.add(w.name to intArrayOf(x, z, r)); save() }
    fun builtHere(x: Int, z: Int) { save() }
    /** Temples and builders' buildings in a world: places worth seeing (x, z). */
    fun landmarks(w: World): List<Pair<Int, Int>> = claims.filter { (n, c) -> n == w.name && c[2] >= 30 }.map { (_, c) -> c[0] to c[1] }
    private fun save() { YamlConfiguration().also { it.set("claims", claims.map { (n, c) -> c.joinToString(",") + "," + n }) }.save(file) }

    // --- Rules ---------------------------------------------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onHunger(e: FoodLevelChangeEvent) {
        if (Haven.isHaven(e.entity.world) && e.foodLevel < e.entity.foodLevel) e.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onSpawn(e: CreatureSpawnEvent) {
        if (!Haven.isHaven(e.location.world)) return
        val en = e.entity
        if (en is Enemy && e.spawnReason != CreatureSpawnEvent.SpawnReason.CUSTOM && e.spawnReason != CreatureSpawnEvent.SpawnReason.COMMAND &&
            e.spawnReason != CreatureSpawnEvent.SpawnReason.SPAWNER_EGG) { e.isCancelled = true; return }
        if (en is Chicken) Bukkit.getScheduler().runTask(Haven.plugin, Runnable { if (en.isValid) Haven.chickens?.makeGolden(en) })
    }

    @EventHandler(ignoreCancelled = true)
    fun onFall(e: EntityDamageEvent) {
        if (e.cause != EntityDamageEvent.DamageCause.FALL || !Haven.isHaven(e.entity.world)) return
        val under = e.entity.location.block.getRelative(0, -1, 0).type
        if (under == Material.WHITE_WOOL || under == Material.CALCITE) e.isCancelled = true      // clouds and floating stones are soft
        if (e.entity is AbstractHorse && e.entity.persistentDataContainer.has(wingedKey)) e.isCancelled = true
    }

    @EventHandler
    fun onArrive(e: PlayerChangedWorldEvent) {
        val p = e.player
        if (!Haven.isHaven(p.world) || p.persistentDataContainer.has(blessedKey)) return
        p.persistentDataContainer.set(blessedKey, PersistentDataType.BYTE, 1)
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable {
            p.health = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH)?.value ?: p.health
            p.foodLevel = 20
            p.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 20 * 30, 1))
            p.addPotionEffect(PotionEffect(PotionEffectType.ABSORPTION, 20 * 120, 1))
            p.world.spawnParticle(Particle.END_ROD, p.location.add(0.0, 1.0, 0.0), 80, 0.8, 1.5, 0.8, 0.08)
            p.world.spawnParticle(Particle.DUST, p.location.add(0.0, 1.0, 0.0), 60, 1.0, 1.5, 1.0, Particle.DustOptions(Color.fromRGB(0xFF, 0xD7, 0x3A), 1.5f))
            p.playSound(p.location, Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f)
            p.showTitle(net.kyori.adventure.title.Title.title(Component.text("Haven", NamedTextColor.GOLD), Component.text("You are blessed", NamedTextColor.AQUA)))
        }, 30L)
    }

    private val motes = Particle.DustOptions(Color.fromRGB(0xFF, 0xF1, 0xC8), 0.7f)

    private fun tick() {
        ticks++
        for (w in Haven.worlds) for (p in w.players) {
            if (ticks % 10 == 0L) {
                if (p.isInWater) {
                    p.remainingAir = p.maximumAir
                    p.addPotionEffect(PotionEffect(PotionEffectType.REGENERATION, 60, 0, true, false))
                }
                w.spawnParticle(Particle.DUST, p.location.add(0.0, 2.0, 0.0), 3, 8.0, 4.0, 8.0, motes)
                if (Haven.rnd.nextInt(20) == 0) w.spawnParticle(Particle.END_ROD, p.location.add(0.0, 3.0, 0.0), 1, 6.0, 3.0, 6.0, 0.005)
            }
            if (ticks % 200 == 0L && Haven.rnd.nextInt(3) == 0) {
                val s = listOf(Sound.BLOCK_AMETHYST_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_CHIME, Sound.BLOCK_NOTE_BLOCK_BELL, Sound.ENTITY_PARROT_AMBIENT).random()
                p.playSound(p.location.add(Haven.rnd.nextDouble() * 16 - 8, 4.0, Haven.rnd.nextDouble() * 16 - 8), s, 0.35f, 0.8f + Haven.rnd.nextFloat())
            }
            if (ticks % 20 == 0L && biome(p.location) == "rainbow_falls" && nearFalls(p.location)) {
                p.addPotionEffect(PotionEffect(PotionEffectType.SPEED, 20 * 10, 1, true, true))
                p.addPotionEffect(PotionEffect(PotionEffectType.JUMP_BOOST, 20 * 10, 1, true, true))
                val colours = listOf(0xFF3B3B, 0xFFE23B, 0x4BE04B, 0x2AA7FF, 0xC04BFF)
                for ((i, c) in colours.withIndex()) w.spawnParticle(Particle.DUST, p.location.add(0.0, 2.0 + i * 0.25, 0.0), 6, 1.5, 0.05, 1.5, Particle.DustOptions(Color.fromRGB(c), 1.2f))
            }
            if (ticks % 2400 == 0L) allaysNear(p)
            val horse = p.vehicle as? AbstractHorse
            if (horse != null && horse.persistentDataContainer.has(wingedKey)) fly(p, horse)
        }
    }

    private fun nearFalls(l: Location): Boolean {
        for (dx in -2..2) for (dy in -1..3) for (dz in -2..2) {
            val b = l.block.getRelative(dx, dy, dz)
            if (b.type == Material.WATER && (b.blockData as? org.bukkit.block.data.Levelled)?.level?.let { it >= 8 } == true) return true
        }
        return false
    }

    fun biome(l: Location) = l.world.getBiome(l).key.key

    // --- Allays: Haven is their home ---------------------------------------------------------------------------------

    private fun allays(at: Location, n: Int) = repeat(n) {
        at.world.spawn(at.clone().add(Haven.rnd.nextDouble() * 4 - 2, 1.5 + Haven.rnd.nextDouble() * 2, Haven.rnd.nextDouble() * 4 - 2), org.bukkit.entity.Allay::class.java)
    }

    /** Every two minutes or so: if there are few allays around a player, a few more come drifting in. */
    private fun allaysNear(p: Player) {
        val near = p.world.getNearbyEntitiesByType(org.bukkit.entity.Allay::class.java, p.location, 48.0).size
        if (near >= 4 || Haven.rnd.nextDouble() > 0.4) return
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val spot = Haven.safeSpot(p.world, p.location.blockX + (kotlin.math.cos(a) * 24).toInt(), p.location.blockZ + (kotlin.math.sin(a) * 24).toInt(), 8) ?: return
        allays(spot, 1 + Haven.rnd.nextInt(2))
    }

    // --- Flying horses ---------------------------------------------------------------------------------------------

    private fun spawnWingedHorse(l: Location) = l.world.spawn(l, Horse::class.java) { h ->
        h.color = Horse.Color.WHITE
        h.style = Horse.Style.WHITE
        h.customName(Component.text("Winged Horse", NamedTextColor.WHITE))
        h.isCustomNameVisible = false
        h.persistentDataContainer.set(wingedKey, PersistentDataType.INTEGER, 6 + Haven.rnd.nextInt(7))   // golden apples it needs
    }

    @EventHandler(ignoreCancelled = true)
    fun onFeed(e: PlayerInteractEntityEvent) {
        val h = e.rightClicked as? AbstractHorse ?: return
        if (!h.persistentDataContainer.has(wingedKey) || h.isTamed) return
        val hand = if (e.hand == EquipmentSlot.OFF_HAND) e.player.inventory.itemInOffHand else e.player.inventory.itemInMainHand
        e.isCancelled = true
        if (hand.type != Material.GOLDEN_APPLE) { e.player.sendActionBar(Component.text("It only trusts golden apples.", NamedTextColor.GOLD)); return }
        if (e.player.gameMode != org.bukkit.GameMode.CREATIVE) hand.amount -= 1
        val need = h.persistentDataContainer.get(wingedKey, PersistentDataType.INTEGER) ?: 8
        val had = (h.persistentDataContainer.get(applesKey, PersistentDataType.INTEGER) ?: 0) + 1
        h.persistentDataContainer.set(applesKey, PersistentDataType.INTEGER, had)
        h.world.playSound(h.location, Sound.ENTITY_HORSE_EAT, 1f, 1.2f)
        if (had >= need) {
            h.isTamed = true
            h.owner = e.player
            h.inventory.saddle = org.bukkit.inventory.ItemStack(Material.SADDLE)
            h.world.spawnParticle(Particle.HEART, h.location.add(0.0, 1.8, 0.0), 8, 0.5, 0.3, 0.5)
            e.player.sendActionBar(Component.text("The winged horse trusts you. Ride it and jump to fly.", NamedTextColor.GOLD))
        } else {
            h.world.spawnParticle(Particle.HAPPY_VILLAGER, h.location.add(0.0, 1.8, 0.0), 5, 0.4, 0.3, 0.4)
            e.player.sendActionBar(Component.text("It eats, and looks at you. ($had)", NamedTextColor.YELLOW))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onMount(e: EntityMountEvent) {
        val h = e.mount as? AbstractHorse ?: return
        if (h.persistentDataContainer.has(wingedKey) && !h.isTamed) {
            e.isCancelled = true
            (e.entity as? Player)?.sendActionBar(Component.text("It shies away. Bring it golden apples.", NamedTextColor.GOLD))
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onTame(e: EntityTameEvent) {
        if (e.entity.persistentDataContainer.has(wingedKey) && (e.entity as? AbstractHorse)?.isTamed != true) e.isCancelled = true
    }

    private val wing = Particle.DustOptions(Color.WHITE, 1.3f)

    private fun fly(p: Player, h: AbstractHorse) {
        val input = p.currentInput
        val airborne = !h.isOnGround
        if (input.isJump && (airborne || h.isOnGround)) {
            val v = p.location.direction.multiply(if (input.isForward) 0.9 else 0.3)
            v.y = maxOf(v.y, 0.45)
            h.velocity = v
        } else if (airborne) {
            val v = if (input.isForward) p.location.direction.multiply(if (input.isSprint) 1.2 else 0.85) else h.velocity.multiply(0.92)
            v.y = if (input.isSneak) -0.5 else maxOf(v.y, -0.12)
            h.velocity = v
            h.fallDistance = 0f
        }
        if (airborne) {
            val side = Vector(-p.location.direction.z, 0.0, p.location.direction.x).normalize()
            val flap = sin(ticks / 3.0) * 0.4
            for (s in listOf(-1.0, 1.0)) w(h.location.add(side.clone().multiply(s * 1.0)).add(0.0, 1.4 + flap, 0.0))
            if (ticks % 12 == 0L) h.world.playSound(h.location, Sound.ENTITY_ENDER_DRAGON_FLAP, 0.3f, 1.8f)
        }
    }

    private fun w(l: Location) = l.world.spawnParticle(Particle.DUST, l, 3, 0.3, 0.1, 0.3, wing)
    private fun sin(x: Double) = kotlin.math.sin(x)

    // --- New land ----------------------------------------------------------------------------------------------------

    @EventHandler
    fun onChunk(e: ChunkLoadEvent) {
        if (!e.isNewChunk || !Haven.isHaven(e.world)) return
        val c = e.chunk
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { if (c.isLoaded) decorate(c) }, 2L)
    }

    private fun decorate(c: Chunk) {
        val w = c.world
        val r = Haven.rnd
        val bx = c.x shl 4
        val bz = c.z shl 4
        val mid = Haven.ground(w, bx + 8, bz + 8)
        val biome = if (mid != null) biome(mid.location) else ""

        if (r.nextDouble() < 0.12) cloud(w, bx + r.nextInt(16), 64 + r.nextInt(16), bz + r.nextInt(16))
        if (r.nextDouble() < 0.10) stone(w, bx + r.nextInt(16), 90 + r.nextInt(120), bz + r.nextInt(16))
        clams(w, bx, bz)
        when (biome) {
            "golden_plains" -> if (r.nextDouble() < 0.45) field(w, bx + 4 + r.nextInt(8), bz + 4 + r.nextInt(8))
            "crystal_valley" -> crystals(w, bx, bz)
            "flower_meadow" -> flowers(w, bx, bz)
        }
        if (mid == null) return
        val top = mid.location.add(0.5, 1.0, 0.5)
        if (r.nextDouble() < 0.10) repeat(2 + r.nextInt(3)) { w.spawn(top, Chicken::class.java).also { Haven.chickens?.makeGolden(it) } }
        if (r.nextDouble() < (if (biome == "flower_meadow" || biome == "dawn_grove") 0.25 else 0.12)) allays(top, 1 + r.nextInt(3))
        if (biome in listOf("golden_plains", "golden_savanna", "flower_meadow", "glory_peaks") && r.nextDouble() < 0.05) repeat(1 + r.nextInt(2)) { spawnWingedHorse(top) }
        if (r.nextDouble() < 0.008) creatures.spawnAngel(top)
        if (r.nextDouble() < 0.006 && free(w, bx + 8, bz + 8, 200)) { creatures.spawnBuilder(top.clone().add(0.0, 2.0, 0.0)); claim(w, bx + 8, bz + 8, 10) }
        val roll = r.nextDouble()
        when {
            roll < 0.012 -> magic.makeColumn(w, bx + 8, bz + 8)
            roll < 0.020 -> magic.makeRift(w, bx + 8, bz + 8)
            roll < 0.026 -> magic.makePool(w, bx + 8, bz + 8, "gold")
            roll < 0.029 && magic.distanceTo("well", top) > 400 -> magic.makeWell(w, bx + 8, bz + 8)
            roll < 0.0293 && magic.distanceTo("spring", top) > 800 -> magic.makePool(w, bx + 8, bz + 8, "spring")
            roll < 0.034 && free(w, bx + 8, bz + 8, 60) -> temple(w, bx + 8, bz + 8)
        }
    }

    private fun cloud(w: World, x: Int, y: Int, z: Int) {
        val rx = 4 + Haven.rnd.nextInt(8)
        val rz = 4 + Haven.rnd.nextInt(8)
        for (dx in -rx..rx) for (dz in -rz..rz) for (dy in -1..1) {
            val f = (dx * dx).toDouble() / (rx * rx) + (dz * dz).toDouble() / (rz * rz) + dy * dy * 0.6
            if (f > 1 || Haven.rnd.nextDouble() < 0.12) continue
            val b = w.getBlockAt(x + dx, y + dy, z + dz)
            if (b.type.isAir && w.isChunkLoaded(b.x shr 4, b.z shr 4)) b.setType(Material.WHITE_WOOL, false)
        }
    }

    private fun stone(w: World, x: Int, y: Int, z: Int) {
        val b = w.getBlockAt(x, y, z)
        if (!b.type.isAir || !b.getRelative(0, -3, 0).type.isAir) return
        b.setType(Material.CALCITE, false)
        if (Haven.rnd.nextBoolean()) b.getRelative(1, 0, 0).let { if (it.type.isAir) it.setType(Material.CALCITE, false) }
    }

    private fun clams(w: World, bx: Int, bz: Int) {
        repeat(3) {
            val x = bx + Haven.rnd.nextInt(16)
            val z = bz + Haven.rnd.nextInt(16)
            for (y in 31 downTo 3) {
                val b = w.getBlockAt(x, y, z)
                if (b.type != Material.WATER) { if (b.type.isSolid && b.getRelative(0, 1, 0).type == Material.WATER) {
                    val d = Material.SEA_PICKLE.createBlockData() as SeaPickle
                    d.pickles = 1 + Haven.rnd.nextInt(3)
                    (d as Waterlogged).isWaterlogged = true
                    b.getRelative(0, 1, 0).setBlockData(d, false)
                }; break }
            }
        }
    }

    private fun field(w: World, x: Int, z: Int) {
        val g = Haven.ground(w, x, z) ?: return
        val y = g.y
        for (dx in -4..4) for (dz in -4..4) {
            val gg = Haven.ground(w, x + dx, z + dz) ?: continue
            if (gg.y != y || (gg.type != Material.GRASS_BLOCK && gg.type != Material.DIRT)) continue
            if (dx == 0 && dz == 0) { gg.setType(Material.WATER, false); continue }
            gg.setBlockData((Material.FARMLAND.createBlockData() as Farmland).apply { moisture = maximumMoisture }, false)
            gg.getRelative(0, 1, 0).setBlockData((Material.WHEAT.createBlockData() as Ageable).apply { age = maximumAge }, false)
        }
    }

    private fun crystals(w: World, bx: Int, bz: Int) {
        repeat(10 + Haven.rnd.nextInt(10)) {
            val g = Haven.ground(w, bx + Haven.rnd.nextInt(16), bz + Haven.rnd.nextInt(16)) ?: return@repeat
            val above = g.getRelative(0, 1, 0)
            if (!above.type.isAir && !above.isReplaceable) return@repeat
            if (Haven.rnd.nextInt(4) == 0) { g.setType(Material.AMETHYST_BLOCK, false) }
            if (Haven.rnd.nextInt(3) == 0) g.setType(Material.CALCITE, false)
            above.setType(Material.AMETHYST_CLUSTER, false)
        }
    }

    private fun flowers(w: World, bx: Int, bz: Int) {
        val kinds = listOf(Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.DANDELION, Material.AZURE_BLUET, Material.OXEYE_DAISY, Material.SUNFLOWER)
        repeat(30) {
            val g = Haven.ground(w, bx + Haven.rnd.nextInt(16), bz + Haven.rnd.nextInt(16)) ?: return@repeat
            if (g.type != Material.GRASS_BLOCK) return@repeat
            val above = g.getRelative(0, 1, 0)
            if (!above.type.isAir) return@repeat
            val k = kinds.random()
            if (k == Material.SUNFLOWER) {
                if (!above.getRelative(0, 1, 0).type.isAir) return@repeat
                val lower = Material.SUNFLOWER.createBlockData() as org.bukkit.block.data.Bisected
                lower.half = org.bukkit.block.data.Bisected.Half.BOTTOM
                above.setBlockData(lower, false)
                val upper = Material.SUNFLOWER.createBlockData() as org.bukkit.block.data.Bisected
                upper.half = org.bukkit.block.data.Bisected.Half.TOP
                above.getRelative(0, 1, 0).setBlockData(upper, false)
            } else above.setType(k, false)
        }
    }

    /** An angel temple: the great domed temple, with treasure inside and two angels at the door. */
    fun temple(w: World, x: Int, z: Int): Boolean {
        val g = Haven.ground(w, x, z) ?: return false
        val rot = Haven.rnd.nextInt(4)
        val plan = HavenArchitecture.plan(HavenArchitecture.Kind.TEMPLE, Haven.rnd.nextLong()).map { HavenArchitecture.turn(it, rot) }
        for (p in plan) {
            val b = w.getBlockAt(x + p.x, g.y + p.y, z + p.z)
            b.setBlockData(p.block, false)
            if (p.block.material == Material.CHEST) (b.state as? org.bukkit.block.Chest)?.let { treasure(it) }
        }
        // fill under the floor down to the ground so it does not float
        for (p in plan) if (p.y == 0) {
            var b = w.getBlockAt(x + p.x, g.y - 1, z + p.z)
            var n = 0
            while ((b.type.isAir || b.isReplaceable || b.isLiquid) && n++ < 12) { b.setType(HavenArchitecture.NACRE, false); b = b.getRelative(0, -1, 0) }
        }
        val door = HavenArchitecture.turn(HavenArchitecture.Part(0, 1, -2, Material.AIR.createBlockData()), rot)
        repeat(2) { creatures.spawnAngel(Location(w, x + door.x + 0.5 + it * 2 - 1, g.y + 1.0, z + door.z + 0.5)) }
        claim(w, x, z, 30)
        Haven.plugin.logger.info("Haven: an angel temple at $x ${g.y} $z")
        return true
    }

    private fun treasure(c: org.bukkit.block.Chest) {
        val inv = c.blockInventory
        val r = Haven.rnd
        inv.addItem(org.bukkit.inventory.ItemStack(Material.DIAMOND, 3 + r.nextInt(6)))
        inv.addItem(org.bukkit.inventory.ItemStack(Material.GOLD_INGOT, 8 + r.nextInt(16)))
        inv.addItem(org.bukkit.inventory.ItemStack(Material.GOLDEN_APPLE, 1 + r.nextInt(3)))
        inv.addItem(items.make(HavenItems.Kind.LIGHT_CRYSTAL, 1 + r.nextInt(3)))
        if (r.nextInt(2) == 0) inv.addItem(items.make(HavenItems.Kind.PEARL, 1 + r.nextInt(2)))
        if (r.nextInt(3) == 0) inv.addItem(items.make(HavenItems.Kind.HAVENITE_SCRAP))
        if (r.nextInt(12) == 0) inv.addItem(items.make(HavenItems.Kind.TIME_PEARL))
        if (r.nextInt(8) == 0) inv.addItem(org.bukkit.inventory.ItemStack(Material.ELYTRA))
        if (r.nextInt(40) == 0) inv.addItem(items.make(HavenItems.Kind.GOLDEN_RAMSHORN))      // one of Haven's rarest treasures
    }
}
