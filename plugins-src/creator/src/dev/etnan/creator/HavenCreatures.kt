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
 * player). They turn to whoever comes near and greet them, and stroll around where they stand when nobody is close.
 * They trade (right click) only for diamonds: havenite scrap, golden apples, pearls, Haven saplings, special books, rarely
 * an elytra or a time pearl. They cannot be hurt. The first blow is a warning (they grow, shine gold, a bell and thunder,
 * their wings spread and you are thrown back); another blow within 30 seconds and they strike you down. In full
 * havenite they count you as one of them: you are warned and thrown back every time, never struck down. Aim at one and you are told not to strike it.
 *
 * Builders: small, old, bearded and flying (the wizard and dwarf skins at half size). They put up Haven buildings
 * (temples, towers, bridges, pavilions, houses) block by block because they like to, a few per region. Interrupted, they
 * always come back and carry on until the building is finished; the plan and how far they got are kept on the builder
 * itself and in haven-jobs.yml, so another builder finishes it if the first is lost. Hit one: poff, it is somewhere else
 * for a moment.
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
        val sea = m.persistentDataContainer.has(seaKey)                         // the sea builders wear bright blue or green suits
        val slug = "builder_" + (if (sea) "sea" else "") + (if (Haven.rnd.nextBoolean()) "azure" else "green") + "_" +
            when { r < 0.45 -> "brown"; r < 0.8 -> "blond"; else -> "grey" }
        m.setProfile(io.papermc.paper.datacomponent.item.ResolvableProfile.resolvableProfile()
            .skinPatch { b -> b.body(net.kyori.adventure.key.Key.key("supermann", "entity/skins/$slug"))
                .model(org.bukkit.profile.PlayerTextures.SkinModel.CLASSIC) }
            .build())
        m.persistentDataContainer.set(lookKey, PersistentDataType.STRING, slug)
    }

    private val caveKey = Haven.key("builder_cave")        // a builder that lives in the caves and makes cave houses

    private val seaKey = Haven.key("builder_sea")          // a builder that lives in the sea and makes houses under water

    fun spawnBuilder(at: Location, cave: Boolean = false, sea: Boolean = false): Mannequin = at.world.spawn(at, Mannequin::class.java) { m ->
        if (cave) m.persistentDataContainer.set(caveKey, PersistentDataType.BYTE, 1)
        if (sea) m.persistentDataContainer.set(seaKey, PersistentDataType.BYTE, 1)
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

    /** A sea builder shines in the water: a few motes of sea glow drift off its suit (no outline: that shows through walls). */
    private fun shine(m: Mannequin) {
        if (m.isGlowing) m.isGlowing = false                                  // the first ones had an outline
        if (ticks % 8 == 0L) m.world.spawnParticle(Particle.GLOW, m.location.add(0.0, 0.6, 0.0), 2, 0.2, 0.35, 0.2, 0.0)
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
                // one creature that fails must never stop the others (that is how they all once stood still)
                runCatching {
                    when (role(m)) {
                        "angel" -> angel(m)
                        "builder" -> builder(m)
                    }
                }.onFailure { if (ticks % 1200 == 0L) Haven.plugin.logger.warning("Haven: ${role(m)} at ${m.location.toVector()} failed: $it") }
            }
            for (p in w.players) aimHint(p)
        }
        walkTick()
        birdTick()
    }

    // --- Angels ----------------------------------------------------------------------------------------------------

    private val homeKey = Haven.key("angel_home")           // x;y;z of where it stands watch
    private val walks = mutableMapOf<UUID, Location>()      // angels on their way somewhere
    private val greeted = mutableMapOf<UUID, Long>()        // angel + player -> when it last greeted them

    private val rest = mutableMapOf<UUID, Long>()            // angel -> the tick it sets off again

    /**
     * Every quarter second. Angels are alive: as long as a player is within 48 blocks they walk about their temple or
     * the place they keep (up to 12 blocks from it), stop for a few seconds, look around or at whoever is near, and
     * walk on. Someone coming right up to one makes it stop, turn to them and greet them (once a minute each).
     */
    private fun angel(m: Mannequin) {
        if (!m.persistentDataContainer.has(lookKey)) dress(m)          // angels from before they had faces and wings
        val l = m.location
        if (!Haven.near(l, 48.0)) { walks.remove(m.uniqueId); return }
        val p = m.world.players.filter { it.gameMode != org.bukkit.GameMode.SPECTATOR && it.location.distanceSquared(l) < 64 }
            .minByOrNull { it.location.distanceSquared(l) }
        val now = System.currentTimeMillis()
        if (p != null && p.location.distanceSquared(l) < 12) {          // right in front of it: stop and greet
            val id = UUID(m.uniqueId.mostSignificantBits xor p.uniqueId.mostSignificantBits, m.uniqueId.leastSignificantBits xor p.uniqueId.leastSignificantBits)
            if (now - (greeted[id] ?: 0L) > 60_000) {
                greeted[id] = now
                walks.remove(m.uniqueId)
                rest[m.uniqueId] = ticks + 60
                m.world.spawnParticle(Particle.WAX_ON, l.clone().add(0.0, 2.2, 0.0), 12, 0.4, 0.6, 0.4, 0.0)
                p.playSound(l, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f, 1.0f)
                p.sendActionBar(Component.text("The angel greets you. Right click to trade for diamonds.", NamedTextColor.GOLD))
            }
        }
        if (m.uniqueId in walks) return                                 // on its way (walkTick moves it)
        if (p != null) {                                                // standing: its eyes follow whoever is near
            val eyes = l.clone().add(0.0, 1.62 * (m.getAttribute(Attribute.SCALE)?.value ?: 1.0), 0.0)
            val dir = p.eyeLocation.toVector().subtract(eyes.toVector())
            if (dir.lengthSquared() > 0.01) { eyes.direction = dir; face(m, eyes.yaw, eyes.pitch.coerceIn(-45f, 45f)) }
        } else if (Haven.rnd.nextInt(12) == 0) face(m, l.yaw + Haven.rnd.nextInt(-70, 71), 0f)      // or it looks around
        if (ticks < (rest[m.uniqueId] ?: 0L)) return
        val home = m.persistentDataContainer.get(homeKey, PersistentDataType.STRING)?.split(";")?.mapNotNull { it.toDoubleOrNull() }?.takeIf { it.size == 3 }
            ?: listOf(l.x, l.y, l.z).also { m.persistentDataContainer.set(homeKey, PersistentDataType.STRING, it.joinToString(";")) }
        val a = Haven.rnd.nextDouble() * Math.PI * 2
        val d = 3 + Haven.rnd.nextDouble() * 9
        walks[m.uniqueId] = Location(m.world, home[0] + cos(a) * d, l.y, home[2] + sin(a) * d)
        rest[m.uniqueId] = ticks + 20                                   // if it cannot take a step that way: try another soon
    }

    private fun face(m: Mannequin, yaw: Float, pitch: Float) {
        m.setRotation(yaw, pitch)
        m.setBodyYaw(yaw)
    }

    /** Every tick: the angels that are walking take a small step, so they walk and do not jump from place to place. */
    private fun walkTick() {
        if (walks.isEmpty()) return
        val it = walks.entries.iterator()
        while (it.hasNext()) {
            val (id, to) = it.next()
            val m = Bukkit.getEntity(id) as? Mannequin
            if (m == null || !m.isValid) { it.remove(); continue }
            if (Haven.isFrozen(m)) continue
            if (runCatching { step(m, to) }.getOrDefault(false)) rest[id] = ticks + 40 + Haven.rnd.nextInt(100)   // then it stands a few seconds
            else it.remove()
        }
    }

    /** One step towards [to] along the ground (up or down a block at most). False: it is there, or it cannot go on. */
    private fun step(m: Mannequin, to: Location): Boolean {
        val l = m.location
        val dx = to.x - l.x
        val dz = to.z - l.z
        val d = sqrt(dx * dx + dz * dz)
        if (d < 0.3) return false
        val s = minOf(0.13, d)
        val w = m.world
        val by = kotlin.math.floor(l.y + 0.3).toInt()
        // straight ahead, or along a wall that is in the way (so it finds the way in through a portal)
        for ((sx, sz) in listOf(dx / d * s to dz / d * s, Math.signum(dx) * s to 0.0, 0.0 to Math.signum(dz) * s)) {
            if (sx == 0.0 && sz == 0.0) continue
            val nx = l.x + sx
            val nz = l.z + sz
            val bx = kotlin.math.floor(nx).toInt()
            val bz = kotlin.math.floor(nz).toInt()
            if (!w.isChunkLoaded(bx shr 4, bz shr 4)) return false
            for (c in intArrayOf(0, 1, -1)) {
                val feet = w.getBlockAt(bx, by + c, bz)
                if (!feet.getRelative(0, -1, 0).type.isSolid || feet.isLiquid) continue
                if (!(0..2).all { val b = feet.getRelative(0, it, 0); b.isPassable || b.type.name.endsWith("_CARPET") }) continue      // a runner on the floor is no wall
                val next = Location(w, nx, (by + c).toDouble(), nz, Math.toDegrees(atan2(-sx, sz)).toFloat(), 0f)
                m.teleport(next)
                m.setBodyYaw(next.yaw)
                return true
            }
        }
        return false
    }

    private fun aimHint(p: Player) {
        val hit = p.world.rayTraceEntities(p.eyeLocation, p.location.direction, 6.0) { it is Mannequin } ?: return
        if (role(hit.hitEntity ?: return) == "angel") p.sendActionBar(Component.text("Angel: right click to trade, do not strike", NamedTextColor.GOLD))
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
        val kin = armor.fullSet(p)        // in full havenite you are one of them: warned every time, never struck down
        val last = warned[p.uniqueId] ?: 0
        if (!kin && now - last < 30_000) {
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
        if (!kin) warned[p.uniqueId] = now
        walks.remove(m.uniqueId)
        rest[m.uniqueId] = ticks + 60
        m.teleport(m.location.setDirection(p.location.toVector().subtract(m.location.toVector())))
        m.swingMainHand()
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

    /**
     * Unfinished buildings, also kept outside the builders (haven-jobs.yml): the job, the world, how far it has come,
     * whose it is and when it was last worked on. So a building is finished even if its builder is lost: another
     * builder nearby takes it over.
     */
    private class Work(val world: String, var done: Int, var owner: UUID, var at: Long)
    private val jobsFile = java.io.File(Haven.plugin.dataFolder, "haven-jobs.yml")
    private val works = mutableMapOf<String, Work>().also { map ->
        if (jobsFile.exists()) org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(jobsFile).getStringList("jobs").forEach { l ->
            val f = l.split("|")
            if (f.size == 5) runCatching { map[f[0]] = Work(f[1], f[2].toInt(), UUID.fromString(f[3]), f[4].toLong()) }
        }
    }
    private fun saveWorks() = runCatching {
        org.bukkit.configuration.file.YamlConfiguration().also { y -> y.set("jobs", works.map { (k, v) -> "$k|${v.world}|${v.done}|${v.owner}|${v.at}" }) }.save(jobsFile)
    }
    private val repairsKey = Haven.key("builder_repairs")   // how many times it has gone back over a building

    /** A building near this builder that nobody is working on any more (its builder is gone), to carry on with. */
    private fun adopt(m: Mannequin): Job? {
        val now = System.currentTimeMillis()
        for ((line, wk) in works) {
            if (wk.world != m.world.name || wk.owner == m.uniqueId || now - wk.at < 5 * 60_000) continue
            val job = Job.of(line) ?: continue
            val dx = job.x - m.location.blockX
            val dz = job.z - m.location.blockZ
            if (dx * dx + dz * dz > 300 * 300 || !m.world.isChunkLoaded(job.x shr 4, job.z shr 4)) continue
            if (Bukkit.getEntity(wk.owner)?.isValid == true) continue
            wk.owner = m.uniqueId
            m.persistentDataContainer.set(doneKey, PersistentDataType.INTEGER, wk.done)
            Haven.plugin.logger.info("Haven: a builder takes over the ${job.kind.name.lowercase()} at ${job.x} ${job.y} ${job.z} (${wk.done} blocks in)")
            return job
        }
        return null
    }

    /**
     * A builder with a job never gives it up: wherever it is (hit and poffed away, lifted off by a wand, frozen, the
     * server restarted, the players gone and back), it flies back to where it stopped and carries on. When the last
     * block is laid it goes over the building and puts back what is missing (up to three times) before it calls it done.
     */
    private fun builder(m: Mannequin) {
        val w = m.world
        if (!Haven.near(m.location, 96.0)) return
        val pdc = m.persistentDataContainer
        if (pdc.has(seaKey)) shine(m)
        var job = Job.of(pdc.get(jobKey, PersistentDataType.STRING))
        if (job == null) {
            if (ticks % 200 != 0L) { hover(m); return }
            val taken = adopt(m)
            job = taken ?: (if (pdc.has(seaKey)) seaSite(m) else if (pdc.has(caveKey)) caveSite(m) else site(m)) ?: run { hover(m); return }
            pdc.set(jobKey, PersistentDataType.STRING, job.line())
            pdc.remove(repairsKey)
            if (taken == null) {
                pdc.set(doneKey, PersistentDataType.INTEGER, 0)
                HavenWorld.instance?.claim(w, job.x, job.z, if (job.kind == HavenArchitecture.Kind.CAVE_HOUSE) 12 else if (job.kind.wet) 16 else 40)
                Haven.plugin.logger.info("Haven: a builder starts a ${job.kind.name.lowercase()} at ${job.x} ${job.y} ${job.z}")
            }
        }
        val parts = plan(job)
        val wet = job.kind.wet                                                // a sea house: the water is taken out block by block
        val dig = wet || job.kind == HavenArchitecture.Kind.CAVE_HOUSE         // a cave house is hewn out of the rock
        fun soft(t: Material) = natural(t) || dig && rock(t) || wet && growth(t)
        var i = pdc.get(doneKey, PersistentDataType.INTEGER) ?: 0
        val work = works.getOrPut(job.line()) { Work(w.name, i, m.uniqueId, 0L) }
        work.owner = m.uniqueId
        work.at = System.currentTimeMillis()
        // back to where it stopped, if it has been taken or chased away from it
        parts.getOrNull(i.coerceAtMost(parts.size - 1))?.let { p ->
            val at = Location(w, job.x + p.x + 0.5, job.y + p.y + 1.6, job.z + p.z + 0.5)
            val d2 = at.distanceSquared(m.location)
            if (d2 > 36) {
                val go = at.toVector().subtract(m.location.toVector())
                val to = if (d2 > 80 * 80) at else m.location.add(go.normalize().multiply(minOf(1.5, sqrt(d2))))
                to.yaw = Math.toDegrees(atan2(-go.x, go.z)).toFloat()
                if (d2 > 80 * 80) w.spawnParticle(Particle.POOF, m.location.add(0.0, 0.5, 0.0), 12, 0.2, 0.2, 0.2, 0.02)
                m.teleport(to)
                return
            }
        }
        if (ticks % 10 != 0L) return                                          // two blocks a second: they take their time
        while (i < parts.size) {
            val p = parts[i]
            val b = w.getBlockAt(job.x + p.x, job.y + p.y, job.z + p.z)
            i++
            if (!w.isChunkLoaded(b.x shr 4, b.z shr 4)) { i--; break }
            if (b.blockData.matches(p.block)) continue                        // already there (the second time round)
            val free = b.type.isAir || b.isReplaceable || b.type.name.endsWith("_LEAVES") || b.isLiquid
            if (p.block.material.isAir) {
                if (b.type.isAir || !(free || dig && soft(b.type))) continue
                b.setType(Material.AIR, false)
                if (!dig) continue                                            // digging is work: one block at a time, like building
                if (wet) {
                    w.playSound(b.location, Sound.BLOCK_BUBBLE_COLUMN_BUBBLE_POP, 0.8f, 1.0f)
                    w.spawnParticle(Particle.BUBBLE_POP, b.location.add(0.5, 0.5, 0.5), 6, 0.3, 0.3, 0.3, 0.0)
                } else w.playSound(b.location, Sound.BLOCK_CALCITE_BREAK, 0.6f, 1.0f)
            } else {
                if (!free && !soft(b.type)) continue
                b.setBlockData(p.block, false)
                w.playSound(b.location, b.blockData.soundGroup.placeSound, 0.6f, 1.1f)
            }
            val to = b.location.add(0.5, 1.6, 0.5)
            to.yaw = Math.toDegrees(atan2(-(to.x - m.location.x), to.z - m.location.z)).toFloat()
            if (to.distanceSquared(m.location) > 4) m.teleport(m.location.add(to.toVector().subtract(m.location.toVector()).multiply(0.5)).also { it.yaw = to.yaw })
            m.swingMainHand()
            break
        }
        if (i >= parts.size) {
            // done? go over it once more: anything knocked out or never laid is put back
            val round = pdc.get(repairsKey, PersistentDataType.INTEGER) ?: 0
            val missing = if (round < 3) parts.indexOfFirst { p ->
                val b = w.getBlockAt(job.x + p.x, job.y + p.y, job.z + p.z)
                if (p.block.material.isAir) wet && w.isChunkLoaded(b.x shr 4, b.z shr 4) && (b.isLiquid || growth(b.type))   // water that came back in
                else w.isChunkLoaded(b.x shr 4, b.z shr 4) && b.type != p.block.material &&
                    (b.type.isAir || b.isReplaceable || b.isLiquid || soft(b.type))
            } else -1
            if (missing >= 0) { i = missing; pdc.set(repairsKey, PersistentDataType.INTEGER, round + 1) }
        }
        pdc.set(doneKey, PersistentDataType.INTEGER, i)
        work.done = i
        if (i >= parts.size) {
            pdc.remove(jobKey)
            pdc.remove(repairsKey)
            plans.remove(job.line())
            works.remove(job.line())
            saveWorks()
            HavenWorld.instance?.builtHere(job.x, job.z)
            Haven.plugin.logger.info("Haven: a builder finished a ${job.kind.name.lowercase()} at ${job.x} ${job.y} ${job.z}")
        } else if (i % 20 == 0 || work.at - lastSaved > 60_000) { lastSaved = work.at; saveWorks() }
    }

    private var lastSaved = 0L

    /** Haven's own ground, which a building may replace where it stands. */
    private fun natural(t: Material) = t == Material.GRASS_BLOCK || t == Material.MOSS_BLOCK || t == Material.CALCITE || t == Material.SAND ||
        t == Material.MUD || t == Material.YELLOW_TERRACOTTA || t == Material.PALE_MOSS_BLOCK || t == Material.DIRT || t == Material.STONE

    /** The rock of the islands, which a cave builder may dig out. */
    private fun rock(t: Material) = t == Material.DIORITE || t == Material.PRISMARINE || t == Material.DARK_PRISMARINE || t == Material.SMOOTH_SANDSTONE || t == Material.QUARTZ_BLOCK ||
        t == Material.SEA_LANTERN || t == Material.PEARLESCENT_FROGLIGHT || t == Material.SANDSTONE || t == Material.AMETHYST_BLOCK || t.name.endsWith("_ORE")

    /** What grows on the sea floor, which a sea builder clears away (it holds water). */
    private fun growth(t: Material) = t == Material.KELP || t == Material.KELP_PLANT || t == Material.SEAGRASS || t == Material.TALL_SEAGRASS ||
        t == Material.SEA_PICKLE || t.name.contains("CORAL") || t == Material.GRAVEL || t == Material.BUBBLE_COLUMN

    /**
     * Somewhere for a sea house near a sea builder: a fairly even stretch of sea floor, deep enough that the whole
     * house stands under water.
     */
    private fun seaSite(m: Mannequin): Job? {
        val w = m.world
        val world = HavenWorld.instance ?: return null
        val kind = if (Haven.rnd.nextBoolean()) HavenArchitecture.Kind.SEA_DOME else HavenArchitecture.Kind.SEA_HOUSE
        val (hw, depth) = HavenArchitecture.footprint(kind)
        for (attempt in 0 until 30) {
            val x = m.location.blockX + Haven.rnd.nextInt(-24, 25)
            val z = m.location.blockZ + Haven.rnd.nextInt(-24, 25)
            if (!w.isChunkLoaded(x shr 4, z shr 4) || !world.freeToBuild(w, x, z, 16)) continue
            val mid = world.seaFloor(w, x, z) ?: continue
            val rot = Haven.rnd.nextInt(4)
            var top = mid.y
            var ok = true
            loop@ for (dx in -hw..hw step 2) for (dz in -2..depth step 2) {
                val (tx, tz) = when (rot) { 0 -> dx to dz; 1 -> -dz to dx; 2 -> -dx to -dz; else -> dz to -dx }
                val f = if (w.isChunkLoaded((x + tx) shr 4, (z + tz) shr 4)) world.seaFloor(w, x + tx, z + tz) else null
                if (f == null || kotlin.math.abs(f.y - mid.y) > 2) { ok = false; break@loop }
                top = maxOf(top, f.y)
            }
            if (ok && top + 10 < HavenGenerator.SEA) return Job(kind, Haven.rnd.nextLong(), x, top, z, rot, 0)
        }
        return null
    }

    /**
     * Somewhere for a cave house near a cave builder: a spot on the cave floor with open cave in front of the door.
     * The house goes into the rock behind it, so there must be rock enough over it and under it.
     */
    private fun caveSite(m: Mannequin): Job? {
        val w = m.world
        val world = HavenWorld.instance ?: return null
        for (attempt in 0 until 40) {
            val x = m.location.blockX + Haven.rnd.nextInt(-14, 15)
            val z = m.location.blockZ + Haven.rnd.nextInt(-14, 15)
            if (!w.isChunkLoaded(x shr 4, z shr 4) || !world.freeToBuild(w, x, z, 12)) continue
            val floor = world.caveFloor(w, x, z) ?: continue
            for (rot in (0..3).shuffled()) {
                fun at(px: Int, py: Int, pz: Int): org.bukkit.block.Block {
                    val t = HavenArchitecture.turn(HavenArchitecture.Part(px, py, pz, Material.AIR.createBlockData()), rot)
                    return w.getBlockAt(floor.x + t.x, floor.y + t.y, floor.z + t.z)
                }
                if (!at(0, 1, -1).isPassable || !at(0, 2, -1).isPassable || !at(0, 0, -1).type.isSolid) continue
                // rock over the roof and under the floor at the corners and in the middle, and the chunks loaded
                val ok = listOf(-4 to 0, 4 to 0, -4 to 9, 4 to 9, 0 to 5).all { (px, pz) ->
                    val c = at(px, 0, pz)
                    w.isChunkLoaded(c.x shr 4, c.z shr 4) && (Haven.ground(w, c.x, c.z)?.y ?: 0) >= floor.y + 9 && at(px, -2, pz).type.isSolid
                }
                if (ok) return Job(HavenArchitecture.Kind.CAVE_HOUSE, Haven.rnd.nextLong(), floor.x, floor.y, floor.z, rot, 0)
            }
        }
        return null
    }

    private fun hover(m: Mannequin) {
        val l = m.location
        val bob = sin(ticks / 10.0) * 0.05
        m.teleport(l.add(0.0, bob, 0.0))
        if (m.persistentDataContainer.has(caveKey) || m.persistentDataContainer.has(seaKey)) return   // cave and sea builders stay where they live
        if (Haven.rnd.nextInt(40) == 0) Haven.safeSpot(m.world, l.blockX, l.blockZ, 10)?.let { m.teleport(it.add(0.0, 1.5, 0.0)) }
    }

    /** Somewhere fitting nearby: a flat spot for a building, or the edge of an island across from another one for a bridge. */
    private fun site(m: Mannequin): Job? {
        val w = m.world
        val world = HavenWorld.instance ?: return null
        val kinds = listOf(HavenArchitecture.Kind.CHAPEL, HavenArchitecture.Kind.TOWER, HavenArchitecture.Kind.PAVILION, HavenArchitecture.Kind.HOUSE, HavenArchitecture.Kind.BRIDGE)
        val kind = if (Haven.rnd.nextBoolean()) HavenArchitecture.Kind.BRIDGE else kinds.random()      // they love bridges
        for (attempt in 0 until 30) {
            val x = m.location.blockX + Haven.rnd.nextInt(-40, 41)
            val z = m.location.blockZ + Haven.rnd.nextInt(-40, 41)
            if (!world.freeToBuild(w, x, z, 40) || !w.isChunkLoaded(x shr 4, z shr 4)) continue
            val g = Haven.ground(w, x, z) ?: continue
            if (kind == HavenArchitecture.Kind.BRIDGE) {
                for (rot in 0..3) {
                    val dir = when (rot) { 0 -> Vector(0, 0, 1); 1 -> Vector(-1, 0, 0); 2 -> Vector(0, 0, -1); else -> Vector(1, 0, 0) }
                    // walk to the edge of this island, then look across the open air for another island 10 to 64
                    // blocks on, at most a quarter of the way (and ten blocks) higher or lower
                    fun at(d: Int): org.bukkit.block.Block? {
                        val ox = x + dir.blockX * d
                        val oz = z + dir.blockZ * d
                        return if (w.isChunkLoaded(ox shr 4, oz shr 4)) Haven.ground(w, ox, oz) else null
                    }
                    var e = 0
                    var edge = g
                    while (e < 16) { val n = at(e + 1); if (n == null || kotlin.math.abs(n.y - edge.y) > 2) break; edge = n; e++ }
                    if (e >= 16 || !world.freeToBuild(w, edge.x, edge.z, 40)) continue
                    for (d in e + 1..e + 64) {
                        val ox = x + dir.blockX * d
                        val oz = z + dir.blockZ * d
                        if (!w.isChunkLoaded(ox shr 4, oz shr 4)) break
                        val o = Haven.ground(w, ox, oz) ?: continue
                        if (o.y < edge.y - 12) continue                                    // far below: still the gap
                        val span = d - e
                        val rise = o.y - edge.y
                        if (span >= 10 && kotlin.math.abs(rise) <= minOf(10, span / 4)) return Job(kind, Haven.rnd.nextLong(), edge.x, edge.y, edge.z, rot, span + 1000 * (rise + 50))
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

    fun shutdown() { saveWorks(); flocks.forEach { f -> f.members.forEach { it.remove() } }; flocks.clear() }
}
