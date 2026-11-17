package dev.etnan.creator

import io.papermc.paper.event.player.PrePlayerAttackEntityEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.entity.Bee
import org.bukkit.entity.Dolphin
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Mannequin
import org.bukkit.entity.Phantom
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.PlayerDeathEvent
import org.bukkit.event.player.PlayerInteractEntityEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.MerchantRecipe
import org.bukkit.persistence.PersistentDataType
import org.bukkit.util.Vector
import java.util.UUID
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Haven's creatures.
 *
 * Angels: big, white and gilded warriors (Mannequins with the angelman and angel_woman skins, one and a half times a
 * player). They trade only for diamonds: havenite scrap, golden apples, pearls, Haven saplings, special books, rarely
 * an elytra or a time pearl. They cannot be hurt. The first blow is a warning (they grow, shine gold, a bell and thunder,
 * their wings spread and you are thrown back); another blow within 30 seconds and they strike you down. In full
 * havenite they never do: they count you as one of them. Aim at one and you are told not to strike it.
 *
 * Builders: small, old, bearded and flying (the wizard and dwarf skins at half size). They put up Haven buildings
 * (temples, towers, bridges, pavilions, houses) block by block because they like to, a few per region. Interrupted, they
 * carry on later; the plan and how far they got are kept on the builder itself. Hit one: poff, it is somewhere else.
 *
 * Sky birds, around players in Haven: golden shoal birds in flocks that turn together, manta birds (azure and pearl),
 * rare sky whales, and dolphin swallows that leap through the clouds and follow players who fly.
 */
class HavenCreatures(private val items: HavenItems, private val armor: HavenArmor) : Listener {

    companion object { var instance: HavenCreatures? = null }

    private val roleKey = Haven.key("haven_role")         // angel | builder | bird
    private val jobKey = Haven.key("builder_job")         // kind;seed;x;y;z;rot;span
    private val doneKey = Haven.key("builder_done")       // how many blocks are placed
    private val warned = mutableMapOf<UUID, Long>()
    private val struck = mutableSetOf<UUID>()
    private var ticks = 0L

    init {
        instance = this
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 60L, 1L)
    }

    private fun role(e: Entity) = e.persistentDataContainer.get(roleKey, PersistentDataType.STRING)

    // --- Spawning --------------------------------------------------------------------------------------------

    fun spawnAngel(at: Location): Mannequin = at.world.spawn(at, Mannequin::class.java) { m ->
        dress(m)
        m.setDescription(null)
        m.isInvulnerable = true
        m.isSilent = true
        m.isImmovable = true
        m.setGravity(true)
        m.isPersistent = true
        m.getAttribute(Attribute.SCALE)?.baseValue = 1.5
        m.equipment.setItemInMainHand(ItemStack(Material.GOLDEN_SWORD))
        m.persistentDataContainer.set(roleKey, PersistentDataType.STRING, "angel")
    }

    private val lookKey = Haven.key("angel_look")

    /**
     * An angel's look: man or woman, a hair colour (blond most often; brown, black and, for women, red less often) and
     * white, gold-edged wings (an elytra with its own texture in the pack, so ordinary elytras look as before).
     */
    private fun dress(m: Mannequin) {
        val woman = Haven.rnd.nextBoolean()
        val r = Haven.rnd.nextDouble()
        val hair = if (woman) when { r < 0.55 -> "blond"; r < 0.72 -> "brown"; r < 0.86 -> "black"; else -> "red" }
                   else when { r < 0.6 -> "blond"; r < 0.8 -> "brown"; else -> "black" }
        val slug = (if (woman) "angel_woman_" else "angelman_") + hair
        m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
            .skinPatch { b -> b.body(net.kyori.adventure.key.Key.key("supermann", "entity/skins/$slug"))
                .model(if (woman) org.bukkit.profile.PlayerTextures.SkinModel.SLIM else org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC) }
            .build())
        val wings = ItemStack(Material.ELYTRA)
        wings.setData(io.papermc.paper.datacomponent.DataComponentTypes.EQUIPPABLE,
            io.papermc.paper.datacomponent.item.Equippable.equippable(org.bukkit.inventory.EquipmentSlot.CHEST)
                .assetId(net.kyori.adventure.key.Key.key("haven", "angel_wings")).build())
        m.equipment.setChestplate(wings)
        m.persistentDataContainer.set(lookKey, PersistentDataType.STRING, slug)
    }

    /**
     * A builder: a little craftsman in a white work uniform with azure or green lines, a leather pouch on the left hip,
     * a gold hammer on the back and a white work cap; brown, blond or grey hair.
     */
    private fun dressBuilder(m: Mannequin) {
        val r = Haven.rnd.nextDouble()
        val slug = "builder_" + (if (Haven.rnd.nextBoolean()) "azure" else "green") + "_" +
            when { r < 0.45 -> "brown"; r < 0.8 -> "blond"; else -> "grey" }
        m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
            .skinPatch { b -> b.body(net.kyori.adventure.key.Key.key("supermann", "entity/skins/$slug"))
                .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC) }
            .build())
        m.persistentDataContainer.set(lookKey, PersistentDataType.STRING, slug)
    }

    fun spawnBuilder(at: Location): Mannequin = at.world.spawn(at, Mannequin::class.java) { m ->
        dressBuilder(m)
        m.setDescription(null)
        m.isInvulnerable = true
        m.isSilent = true
        m.isImmovable = true
        m.setGravity(false)
        m.isPersistent = true
        m.getAttribute(Attribute.SCALE)?.baseValue = 0.55
        m.persistentDataContainer.set(roleKey, PersistentDataType.STRING, "builder")
    }

    /** Angels from before they had faces and wings are dressed the moment their chunk loads. */
    @EventHandler
    fun onEntitiesLoad(e: org.bukkit.event.world.EntitiesLoadEvent) {
        if (!Haven.isHaven(e.world)) return
        for (m in e.entities) if (m is Mannequin) {
            val look = m.persistentDataContainer.get(lookKey, PersistentDataType.STRING)
            when (role(m)) {
                "angel" -> if (look == null) dress(m)
                "builder" -> if (look == null || look.count { it == '_' } < 2) dressBuilder(m)   // also the first white ones
            }
        }
    }

    fun angelNear(l: Location, r: Double) = l.world.getNearbyEntitiesByType(Mannequin::class.java, l, r).any { role(it) == "angel" }

    // --- Every tick ----------------------------------------------------------------------------------------------

    private fun tick() {
        ticks++
        for (w in Haven.worlds) if (w.players.isNotEmpty() && ticks % 5 == 0L) {
            for (m in w.getEntitiesByClass(Mannequin::class.java)) {
                if (Haven.isFrozen(m)) continue
                when (role(m)) {
                    "angel" -> angel(m)
                    "builder" -> builder(m)
                }
            }
            for (p in w.players) aimHint(p)
        }
        birdTick()
    }

    // --- Angels ----------------------------------------------------------------------------------------------------

    private fun angel(m: Mannequin) {
        if (!m.persistentDataContainer.has(lookKey)) dress(m)          // angels from before they had faces and wings
        if (ticks % 40 != 0L || Haven.rnd.nextInt(3) != 0) return
        val to = Haven.safeSpot(m.world, m.location.blockX, m.location.blockZ, 6) ?: return
        if (to.distanceSquared(m.location) > 64) return
        to.yaw = Math.toDegrees(atan2(-(to.x - m.location.x), to.z - m.location.z)).toFloat()
        m.teleport(to)
    }

    private fun aimHint(p: Player) {
        val hit = p.world.rayTraceEntities(p.eyeLocation, p.location.direction, 6.0) { it is Mannequin } ?: return
        if (role(hit.hitEntity ?: return) == "angel") p.sendActionBar(Component.text("Angel – do not strike", NamedTextColor.GOLD))
    }

    @EventHandler
    fun onAttack(e: PrePlayerAttackEntityEvent) {
        val m = e.attacked as? Mannequin ?: return
        val p = e.player
        when (role(m)) {
            "angel" -> { e.isCancelled = true; angelHit(m, p) }
            "builder" -> { e.isCancelled = true; poff(m) }
        }
    }

    private fun angelHit(m: Mannequin, p: Player) {
        if (Haven.isFrozen(m)) return
        val w = m.world
        val now = System.currentTimeMillis()
        if (armor.fullSet(p)) {
            w.spawnParticle(Particle.WAX_ON, m.location.add(0.0, 2.0, 0.0), 20, 0.5, 1.0, 0.5, 0.0)
            w.playSound(m.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.4f)
            return
        }
        val last = warned[p.uniqueId] ?: 0
        if (now - last < 30_000) {
            warned.remove(p.uniqueId)
            m.teleport(m.location.setDirection(p.location.toVector().subtract(m.location.toVector())))
            w.playSound(m.location, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 0.8f)
            Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable {
                if (!p.isOnline || p.isDead) return@Runnable
                m.swingMainHand()
                w.strikeLightningEffect(p.location)
                struck.add(p.uniqueId)
                p.health = 0.0
            }, 12L)
            return
        }
        warned[p.uniqueId] = now
        val scale = m.getAttribute(Attribute.SCALE)
        scale?.baseValue = 1.8
        m.isGlowing = true
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { scale?.baseValue = 1.5; m.isGlowing = false }, 50L)
        w.playSound(m.location, Sound.BLOCK_BELL_USE, 1.5f, 0.6f)
        w.playSound(m.location, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.4f, 1.5f)
        val back = m.location.add(0.0, 1.5, 0.0)
        val dir = m.location.direction.setY(0).normalize()
        val side = Vector(-dir.z, 0.0, dir.x)
        for (i in -10..10) {                                     // the wings spreading
            val l = back.clone().subtract(dir.clone().multiply(0.4)).add(side.clone().multiply(i * 0.15)).add(0.0, kotlin.math.abs(i) * 0.06, 0.0)
            w.spawnParticle(Particle.CLOUD, l, 2, 0.02, 0.05, 0.02, 0.0)
        }
        w.spawnParticle(Particle.DUST, back, 30, 0.6, 0.8, 0.6, Particle.DustOptions(Color.fromRGB(0xFF, 0xD7, 0x3A), 1.4f))
        p.velocity = p.location.toVector().subtract(m.location.toVector()).setY(0).normalize().multiply(1.4).setY(0.45)
    }

    @EventHandler
    fun onDeath(e: PlayerDeathEvent) {
        if (struck.remove(e.player.uniqueId)) e.deathMessage(Component.text("${e.player.name} was struck down by an angel", NamedTextColor.GOLD))
    }

    @EventHandler
    fun onTrade(e: PlayerInteractEntityEvent) {
        val m = e.rightClicked as? Mannequin ?: return
        if (role(m) != "angel") return
        e.isCancelled = true
        val r = java.util.Random(m.uniqueId.mostSignificantBits)
        val shop = Bukkit.createMerchant(Component.text("Angel", NamedTextColor.GOLD))
        fun offer(price: Int, what: ItemStack) = MerchantRecipe(what, 9999).apply { addIngredient(ItemStack(Material.DIAMOND, price)) }
        val list = mutableListOf(
            offer(4, items.make(HavenItems.Kind.HAVENITE_SCRAP)),
            offer(2, ItemStack(Material.GOLDEN_APPLE, 2)),
            offer(3, items.make(HavenItems.Kind.PEARL)),
            offer(5, items.make(if (r.nextBoolean()) HavenItems.Kind.DAWN_SAPLING else HavenItems.Kind.SILVER_SAPLING)),
            offer(8, angelBook(r)),
        )
        if (r.nextInt(10) == 0) list.add(offer(48, ItemStack(Material.ELYTRA)))
        if (r.nextInt(5) == 0) list.add(offer(24, items.make(HavenItems.Kind.TIME_PEARL)))
        shop.recipes = list
        e.player.openMerchant(shop, true)
    }

    private fun angelBook(r: java.util.Random): ItemStack {
        val b = ItemStack(Material.ENCHANTED_BOOK)
        val pick = listOf(org.bukkit.enchantments.Enchantment.MENDING to 1, org.bukkit.enchantments.Enchantment.PROTECTION to 4,
            org.bukkit.enchantments.Enchantment.FEATHER_FALLING to 4, org.bukkit.enchantments.Enchantment.LOOTING to 3,
            org.bukkit.enchantments.Enchantment.FORTUNE to 3, org.bukkit.enchantments.Enchantment.SHARPNESS to 5,
            org.bukkit.enchantments.Enchantment.EFFICIENCY to 5, org.bukkit.enchantments.Enchantment.UNBREAKING to 3)[r.nextInt(8)]
        b.editMeta(org.bukkit.inventory.meta.EnchantmentStorageMeta::class.java) { it.addStoredEnchant(pick.first, pick.second, true) }
        return b
    }

    // --- Builders ----------------------------------------------------------------------------------------------------

    private fun poff(m: Mannequin) {
        val w = m.world
        w.spawnParticle(Particle.POOF, m.location.add(0.0, 0.5, 0.0), 20, 0.3, 0.3, 0.3, 0.02)
        w.playSound(m.location, Sound.ENTITY_PUFFER_FISH_BLOW_OUT, 1f, 1.6f)
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val to = m.location.add(cos(a) * (4 + Haven.rnd.nextInt(4)), 1.0, sin(a) * (4 + Haven.rnd.nextInt(4)))
        if (!to.block.isPassable) to.y = (w.getHighestBlockYAt(to) + 1).toDouble()
        m.teleport(to)
        w.spawnParticle(Particle.POOF, to.clone().add(0.0, 0.5, 0.0), 12, 0.2, 0.2, 0.2, 0.02)
    }

    data class Job(val kind: HavenArchitecture.Kind, val seed: Long, val x: Int, val y: Int, val z: Int, val rot: Int, val span: Int) {
        fun line() = "$kind;$seed;$x;$y;$z;$rot;$span"
        companion object {
            fun of(s: String?): Job? = s?.split(";")?.takeIf { it.size == 7 }?.let {
                runCatching { Job(HavenArchitecture.Kind.valueOf(it[0]), it[1].toLong(), it[2].toInt(), it[3].toInt(), it[4].toInt(), it[5].toInt(), it[6].toInt()) }.getOrNull() }
        }
    }

    private val plans = mutableMapOf<String, List<HavenArchitecture.Part>>()
    private fun plan(j: Job) = plans.getOrPut(j.line()) { HavenArchitecture.plan(j.kind, j.seed, j.span).map { HavenArchitecture.turn(it, j.rot) } }

    private fun builder(m: Mannequin) {
        val w = m.world
        if (!Haven.near(m.location, 96.0)) return
        var job = Job.of(m.persistentDataContainer.get(jobKey, PersistentDataType.STRING))
        if (job == null) {
            if (ticks % 200 != 0L) { hover(m); return }
            job = site(m) ?: run { hover(m); return }
            m.persistentDataContainer.set(jobKey, PersistentDataType.STRING, job.line())
            m.persistentDataContainer.set(doneKey, PersistentDataType.INTEGER, 0)
            HavenWorld.instance?.claim(w, job.x, job.z, 40)
            Haven.plugin.logger.info("Haven: a builder starts a ${job.kind.name.lowercase()} at ${job.x} ${job.y} ${job.z}")
        }
        if (ticks % 10 != 0L) return                                          // one block a second: they take their time
        val parts = plan(job)
        var i = m.persistentDataContainer.get(doneKey, PersistentDataType.INTEGER) ?: 0
        while (i < parts.size) {
            val p = parts[i]
            val b = w.getBlockAt(job.x + p.x, job.y + p.y, job.z + p.z)
            i++
            if (!w.isChunkLoaded(b.x shr 4, b.z shr 4)) { i--; break }
            val free = b.type.isAir || b.isReplaceable || b.type.name.endsWith("_LEAVES") || b.isLiquid
            if (p.block.material.isAir) { if (free && !b.type.isAir) b.setType(Material.AIR, false); continue }
            if (!free && b.type != Material.GRASS_BLOCK && b.type != Material.DIRT && b.type != Material.STONE) continue
            b.setBlockData(p.block, false)
            val to = b.location.add(0.5, 1.6, 0.5)
            to.yaw = Math.toDegrees(atan2(-(to.x - m.location.x), to.z - m.location.z)).toFloat()
            if (to.distanceSquared(m.location) > 4) m.teleport(m.location.add(to.toVector().subtract(m.location.toVector()).multiply(0.5)).also { it.yaw = to.yaw })
            m.swingMainHand()
            w.playSound(b.location, b.blockData.soundGroup.placeSound, 0.6f, 1.1f)
            break
        }
        m.persistentDataContainer.set(doneKey, PersistentDataType.INTEGER, i)
        if (i >= parts.size) {
            m.persistentDataContainer.remove(jobKey)
            plans.remove(job.line())
            HavenWorld.instance?.builtHere(job.x, job.z)
            Haven.plugin.logger.info("Haven: a builder finished a ${job.kind.name.lowercase()} at ${job.x} ${job.y} ${job.z}")
        }
    }

    private fun hover(m: Mannequin) {
        val l = m.location
        val bob = sin(ticks / 10.0) * 0.05
        m.teleport(l.add(0.0, bob, 0.0))
        if (Haven.rnd.nextInt(40) == 0) Haven.safeSpot(m.world, l.blockX, l.blockZ, 10)?.let { m.teleport(it.add(0.0, 1.5, 0.0)) }
    }

    /** Somewhere fitting nearby: a flat spot for a building, or the edge of an island across from another one for a bridge. */
    private fun site(m: Mannequin): Job? {
        val w = m.world
        val world = HavenWorld.instance ?: return null
        val kinds = listOf(HavenArchitecture.Kind.CHAPEL, HavenArchitecture.Kind.TOWER, HavenArchitecture.Kind.PAVILION, HavenArchitecture.Kind.HOUSE, HavenArchitecture.Kind.BRIDGE)
        val kind = kinds.random()
        for (attempt in 0 until 30) {
            val x = m.location.blockX + Haven.rnd.nextInt(-40, 41)
            val z = m.location.blockZ + Haven.rnd.nextInt(-40, 41)
            if (!world.free(w, x, z, 40)) continue
            val g = Haven.ground(w, x, z) ?: continue
            if (kind == HavenArchitecture.Kind.BRIDGE) {
                for (rot in 0..3) {
                    val dir = when (rot) { 0 -> Vector(0, 0, 1); 1 -> Vector(-1, 0, 0); 2 -> Vector(0, 0, -1); else -> Vector(1, 0, 0) }
                    // an edge: ground here, air just beyond, ground again 8..36 further at about the same height
                    val beyond = Haven.ground(w, x + dir.blockX * 3, z + dir.blockZ * 3)
                    if (beyond != null && kotlin.math.abs(beyond.y - g.y) < 4) continue
                    for (d in 8..36) {
                        val o = Haven.ground(w, x + dir.blockX * d, z + dir.blockZ * d) ?: continue
                        if (kotlin.math.abs(o.y - g.y) <= 3) return Job(kind, Haven.rnd.nextLong(), x, g.y, z, rot, d)
                        break
                    }
                }
                continue
            }
            val (hw, depth) = HavenArchitecture.footprint(kind)
            val rot = Haven.rnd.nextInt(4)
            var ok = true
            loop@ for (dx in -hw..hw step 2) for (dz in 0..depth step 2) {
                val (tx, tz) = when (rot) { 0 -> dx to dz; 1 -> -dz to dx; 2 -> -dx to -dz; else -> dz to -dx }
                val gg = Haven.ground(w, x + tx, z + tz)
                if (gg == null || kotlin.math.abs(gg.y - g.y) > 2) { ok = false; break@loop }
            }
            if (ok) return Job(kind, Haven.rnd.nextLong(), x, g.y, z, rot, 0)
        }
        return null
    }

    // --- Sky birds -----------------------------------------------------------------------------------------------------

    class Flock(val kind: String, var at: Location, var heading: Vector, val members: List<LivingEntity>, val offsets: List<Vector>, val speed: Double, val owner: UUID) {
        var age = 0
    }

    private val flocks = mutableListOf<Flock>()

    private fun birdTick() {
        if (ticks % 100 == 0L) for (p in Haven.worlds.flatMap { it.players }) {
            val mine = flocks.filter { it.owner == p.uniqueId }
            fun count(k: String) = mine.count { it.kind == k }
            if (count("shoal") < 2) flock(p, "shoal")
            if (count("manta") < 2 && Haven.rnd.nextInt(2) == 0) flock(p, "manta")
            if (count("whale") < 1 && Haven.rnd.nextInt(40) == 0) flock(p, "whale")
            val flying = p.isGliding || p.isFlying || (p.vehicle != null && !p.vehicle!!.isOnGround)
            if (flying && count("dolphins") < 1) flock(p, "dolphins")
        }
        val it = flocks.iterator()
        while (it.hasNext()) {
            val f = it.next()
            val owner = Bukkit.getPlayer(f.owner)
            val w = f.at.world
            f.age++
            if (owner == null || owner.world != w || f.at.distanceSquared(owner.location) > 160.0 * 160 || f.members.none { m -> m.isValid } ||
                (f.kind == "dolphins" && f.age > 1200 && !(owner.isGliding || owner.isFlying))) {
                f.members.forEach { m -> m.remove() }
                it.remove()
                continue
            }
            if (f.members.any { m -> Haven.isFrozen(m) }) continue
            steer(w, f, owner)
            val t = f.age / 8.0
            for ((i, m) in f.members.withIndex()) {
                if (!m.isValid) continue
                val o = f.offsets[i]
                val wobble = Vector(sin(t + i) * 0.4, if (f.kind == "dolphins") sin(t * 0.6 + i) * 3.0 else sin(t * 0.7 + i * 2) * 0.3, cos(t + i * 1.3) * 0.4)
                val l = f.at.clone().add(o).add(wobble)
                l.direction = f.heading
                m.teleport(l)
            }
            if (f.kind == "manta" && f.age % 4 == 0) w.spawnParticle(Particle.DUST, f.at, 2, 0.3, 0.1, 0.3, Particle.DustOptions(Color.fromRGB(0xF4, 0xF0, 0xFF), 1.0f))
            if (f.kind == "whale" && f.age % 200 == 0) w.playSound(f.at, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 3f, 0.5f)
            if (f.kind == "shoal" && f.age % 30 == 0) f.members.firstOrNull()?.let { m -> w.spawnParticle(Particle.WAX_ON, m.location, 6, 1.5, 0.5, 1.5, 0.0) }
        }
    }

    private fun steer(w: World, f: Flock, owner: Player) {
        val h = f.heading
        if (f.kind == "dolphins") {
            val target = owner.location.clone().add(owner.location.direction.multiply(6.0)).add(0.0, -2.0, 0.0)
            val want = target.toVector().subtract(f.at.toVector())
            if (want.lengthSquared() > 0.01) h.add(want.normalize().multiply(0.15)).normalize()
        } else {
            h.rotateAroundY((Haven.rnd.nextDouble() - 0.5) * 0.08)
            val ahead = f.at.clone().add(h.clone().multiply(8.0))
            if (!ahead.block.type.isAir || ahead.y < 80 || ahead.y > 240) h.rotateAroundY(0.35).setY(if (ahead.y < 120) 0.05 else -0.05).normalize()
            val toOwner = owner.location.toVector().subtract(f.at.toVector())
            if (toOwner.lengthSquared() > 90.0 * 90) h.add(toOwner.normalize().multiply(0.1)).normalize()
            h.y = h.y.coerceIn(-0.15, 0.15)
        }
        f.at.add(h.clone().multiply(if (f.kind == "dolphins") maxOf(f.speed, owner.velocity.length() * 1.05) else f.speed))
    }

    private fun flock(p: Player, kind: String) {
        val w = p.world
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val d = if (kind == "dolphins") 10.0 else 40.0 + Haven.rnd.nextInt(40)
        val at = p.location.clone().add(cos(a) * d, 0.0, sin(a) * d)
        at.y = if (kind == "dolphins") p.location.y - 2 else maxOf(p.location.y + 20 + Haven.rnd.nextInt(30), 110.0).coerceAtMost(235.0)
        if (!at.block.type.isAir) return
        val heading = Vector(-sin(a), 0.0, cos(a))
        val members = mutableListOf<LivingEntity>()
        val offsets = mutableListOf<Vector>()
        fun <T : LivingEntity> add(cls: Class<T>, off: Vector, setup: (T) -> Unit) {
            val e = w.spawn(at.clone().add(off), cls) { e ->
                e.setAI(false); e.isInvulnerable = true; e.isSilent = true; e.isPersistent = false; e.setGravity(false); e.isCollidable = false
                e.persistentDataContainer.set(roleKey, PersistentDataType.STRING, "bird")
                setup(e)
            }
            members.add(e); offsets.add(off)
        }
        when (kind) {
            "shoal" -> repeat(8 + Haven.rnd.nextInt(8)) { add(Bee::class.java, Vector(Haven.rnd.nextDouble() * 4 - 2, Haven.rnd.nextDouble() * 2 - 1, Haven.rnd.nextDouble() * 4 - 2)) { } }
            "manta" -> add(Phantom::class.java, Vector()) { e -> e.setShouldBurnInDay(false); e.getAttribute(Attribute.SCALE)?.baseValue = 1.6 }
            "whale" -> add(Phantom::class.java, Vector()) { e -> e.setShouldBurnInDay(false); e.getAttribute(Attribute.SCALE)?.baseValue = 9.0 }
            "dolphins" -> repeat(3 + Haven.rnd.nextInt(3)) { i -> add(Dolphin::class.java, Vector((i - 2) * 2.0, 0.0, Haven.rnd.nextDouble() * 3)) { } }
        }
        val speed = when (kind) { "shoal" -> 0.35; "manta" -> 0.25; "whale" -> 0.08; else -> 0.6 }
        flocks.add(Flock(kind, at, heading, members, offsets, speed, p.uniqueId))
    }

    fun shutdown() { flocks.forEach { f -> f.members.forEach { it.remove() } }; flocks.clear() }
}
