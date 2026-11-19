package dev.etnan.creator

import com.destroystokyo.paper.event.player.PlayerJumpEvent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.Tag
import org.bukkit.attribute.Attribute
import org.bukkit.attribute.AttributeModifier
import org.bukkit.block.Block
import org.bukkit.block.data.type.Light
import org.bukkit.entity.AbstractHorse
import org.bukkit.entity.AbstractArrow
import org.bukkit.entity.Enemy
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.entity.EntityDamageByEntityEvent
import org.bukkit.event.entity.EntityDamageEvent
import org.bukkit.event.entity.EntityShootBowEvent
import org.bukkit.event.entity.ProjectileHitEvent
import org.bukkit.inventory.EquipmentSlotGroup
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.UUID

/**
 * What havenite armor and light crystals do.
 *
 * Havenite: the helmet lets you stay much longer under water, the chestplate has wings (glide like an elytra), the
 * leggings step up whole blocks, run faster and swim like a dolphin, and the boots let you drift down instead of
 * falling. The full set: you jump about three blocks high, and sneak and jump shoots you high up to drift slowly
 * down; once more only after you have touched the ground. Light crystals: armor gives a warm light around you (more pieces, more light), a sword hurts
 * the undead more, a shield flashes and dazzles monsters when it blocks, arrows shine and leave light where they
 * land, horse armor and elytra leave a golden trail.
 */
class HavenArmor(private val items: HavenItems) : Listener {

    private val stepKey = Haven.key("havenite_step")
    private val speedKey = Haven.key("havenite_speed")
    private val jumpKey = Haven.key("havenite_jump")
    private val told = mutableSetOf<UUID>()
    private val soaring = mutableSetOf<UUID>()
    private val lights = mutableMapOf<UUID, Block>()
    private val gold = Particle.DustOptions(Color.fromRGB(0xFF, 0xD7, 0x3A), 1.0f)
    private val warm = Particle.DustOptions(Color.fromRGB(0xFF, 0xE4, 0xA8), 0.8f)

    init { Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { tick() }, 20L, 5L) }

    fun pieces(p: Player) = p.inventory.armorContents.count { items.isHavenite(it) }
    fun fullSet(p: Player) = pieces(p) == 4

    private fun tick() {
        for (p in Bukkit.getOnlinePlayers()) {
            val inv = p.inventory
            // helmet: air runs out about five times slower
            if (items.isHavenite(inv.helmet) && p.isInWater && p.remainingAir < p.maximumAir) p.remainingAir = minOf(p.maximumAir, p.remainingAir + 4)
            // leggings: step up blocks, a little faster, swim like a dolphin
            val legs = items.isHavenite(inv.leggings)
            modifier(p, Attribute.STEP_HEIGHT, stepKey, if (legs) 0.5 else null)
            modifier(p, Attribute.MOVEMENT_SPEED, speedKey, if (legs) 0.1 else null, AttributeModifier.Operation.MULTIPLY_SCALAR_1)
            // the full set: a much higher jump (and sneak + jump to soar, see onJump)
            val full = fullSet(p)
            modifier(p, Attribute.JUMP_STRENGTH, jumpKey, if (full) 0.3 else null)
            if (!full) told.remove(p.uniqueId)
            else if (told.add(p.uniqueId)) p.sendActionBar(net.kyori.adventure.text.Component.text("Full havenite: you jump high. Hold sneak and jump to soar.", net.kyori.adventure.text.format.NamedTextColor.AQUA))
            if (legs && p.isInWater) p.addPotionEffect(PotionEffect(PotionEffectType.DOLPHINS_GRACE, 40, 0, true, false))
            // boots: drift down instead of falling
            if (items.isHavenite(inv.boots) && p.fallDistance > 3 && !p.isGliding && !p.isFlying)
                p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 15, 0, true, false))
            if (p.uniqueId in soaring) {
                if ((p as org.bukkit.entity.Entity).isOnGround && p.velocity.y <= 0.01) soaring.remove(p.uniqueId)
                else p.world.spawnParticle(Particle.DUST, p.location.add(0.0, 0.8, 0.0), 3, 0.4, 0.3, 0.4, gold)
            }
            // light crystals: a warm light that follows you
            val litPieces = inv.armorContents.count { items.isLit(it) }
            shine(p, litPieces)
            val chest = inv.chestplate
            if (p.isGliding && items.isLit(chest)) p.world.spawnParticle(Particle.DUST, p.location, 4, 0.2, 0.2, 0.2, warm)
            val horse = p.vehicle as? AbstractHorse
            if (horse != null) {
                val armor = horse.inventory.getItem(1)
                val shines = items.isLit(armor)
                if (horse.isGlowing != shines) horse.isGlowing = shines
                if (shines) horse.world.spawnParticle(Particle.DUST, horse.location.add(0.0, 0.6, 0.0), 6, 0.4, 0.3, 0.4, gold)
            }
        }
    }

    private fun modifier(p: Player, a: Attribute, key: org.bukkit.NamespacedKey, amount: Double?, op: AttributeModifier.Operation = AttributeModifier.Operation.ADD_NUMBER) {
        val inst = p.getAttribute(a) ?: return
        val has = inst.getModifier(key) != null
        if (amount == null) { if (has) inst.removeModifier(key); return }
        if (!has) inst.addTransientModifier(AttributeModifier(key, amount, op, EquipmentSlotGroup.ANY))
    }

    /** Light blocks at the player's head, level 6 to 15 for one to four shining pieces. */
    private fun shine(p: Player, n: Int) {
        val old = lights[p.uniqueId]
        val at = p.eyeLocation.block
        if (n == 0) { if (old != null) { clear(old); lights.remove(p.uniqueId) }; return }
        if (old == at) return
        if (old != null) clear(old)
        if (at.type.isAir) {
            at.setBlockData((Material.LIGHT.createBlockData() as Light).apply { level = minOf(15, 3 + n * 3) }, false)
            lights[p.uniqueId] = at
        } else lights.remove(p.uniqueId)
    }

    private fun clear(b: Block) { if (b.type == Material.LIGHT) b.setType(Material.AIR, false) }

    fun shutdown() { lights.values.forEach { clear(it) }; lights.clear() }

    @EventHandler
    fun onJump(e: PlayerJumpEvent) {
        if (e.player.isSneaking || e.player.currentInput.isSneak) soar(e.player)
    }

    /**
     * The same from the keys themselves: sneak and jump held together while standing on the ground. The jump event
     * alone did not always come (in the first test nobody with a full set could soar).
     */
    @EventHandler
    fun onInput(e: org.bukkit.event.player.PlayerInputEvent) {
        if (e.input.isJump && e.input.isSneak && (e.player as org.bukkit.entity.Entity).isOnGround) soar(e.player)
    }

    private fun soar(p: Player) {
        if (p.uniqueId in soaring || !fullSet(p)) return
        soaring.add(p.uniqueId)
        Bukkit.getScheduler().runTask(Haven.plugin, Runnable {
            val flat = p.location.direction.setY(0)                       // looking straight up or down: straight up
            if (flat.lengthSquared() > 1e-4) flat.normalize().multiply(0.6)
            p.velocity = flat.setY(2.2)
            p.addPotionEffect(PotionEffect(PotionEffectType.SLOW_FALLING, 200, 0, true, false))
        })
        p.world.playSound(p.location, Sound.ITEM_ELYTRA_FLYING, 0.6f, 1.6f)
        p.world.playSound(p.location, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.2f, 1.2f)
    }

    @EventHandler(ignoreCancelled = true)
    fun onFall(e: EntityDamageEvent) {
        val p = e.entity as? Player ?: return
        if (e.cause == EntityDamageEvent.DamageCause.FALL && items.isHavenite(p.inventory.boots)) e.isCancelled = true
    }

    @EventHandler(ignoreCancelled = true)
    fun onHit(e: EntityDamageByEntityEvent) {
        val by = e.damager as? Player
        if (by != null && items.isLit(by.inventory.itemInMainHand) && by.inventory.itemInMainHand.type.name.endsWith("_SWORD")
            && Tag.ENTITY_TYPES_SENSITIVE_TO_SMITE.isTagged(e.entity.type)) {
            e.damage *= 1.5
            e.entity.world.spawnParticle(Particle.DUST, e.entity.location.add(0.0, 1.0, 0.0), 12, 0.3, 0.5, 0.3, warm)
        }
        val p = e.entity as? Player ?: return
        if (!p.isBlocking) return
        val shield = if (p.inventory.itemInOffHand.type == Material.SHIELD) p.inventory.itemInOffHand else p.inventory.itemInMainHand
        if (!items.isLit(shield)) return
        p.world.spawnParticle(Particle.FLASH, p.eyeLocation, 1, 0.0, 0.0, 0.0, 0.0, Color.WHITE)
        p.world.playSound(p.location, Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 1f, 1.8f)
        for (m in p.getNearbyEntities(6.0, 4.0, 6.0)) if (m is Enemy && m is LivingEntity) {
            m.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, 50, 3))
            m.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, 50, 1))
            m.addPotionEffect(PotionEffect(PotionEffectType.BLINDNESS, 50, 0))
        }
    }

    private val litArrow = Haven.key("lit_arrow")

    @EventHandler(ignoreCancelled = true)
    fun onShoot(e: EntityShootBowEvent) {
        if (!items.isLit(e.bow)) return
        val a = e.projectile as? AbstractArrow ?: return
        a.isGlowing = true
        a.persistentDataContainer.set(litArrow, org.bukkit.persistence.PersistentDataType.BYTE, 1)
    }

    @EventHandler
    fun onLand(e: ProjectileHitEvent) {
        val a = e.entity as? AbstractArrow ?: return
        if (!a.persistentDataContainer.has(litArrow)) return
        val at: Location = e.hitBlock?.let { hb -> e.hitBlockFace?.let { hb.getRelative(it).location } } ?: a.location
        val b = at.block
        if (!b.type.isAir) return
        b.setBlockData((Material.LIGHT.createBlockData() as Light).apply { level = 14 }, false)
        b.world.spawnParticle(Particle.DUST, b.location.add(0.5, 0.5, 0.5), 10, 0.3, 0.3, 0.3, warm)
        Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { clear(b) }, 20L * 60)
    }
}
