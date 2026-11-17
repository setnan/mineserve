package dev.etnan.creator

import io.papermc.paper.datacomponent.DataComponentTypes
import net.kyori.adventure.key.Key
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import net.kyori.adventure.text.format.TextDecoration
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.Sound
import org.bukkit.block.Block
import org.bukkit.block.data.Ageable
import org.bukkit.enchantments.Enchantment
import org.bukkit.entity.Enemy
import org.bukkit.entity.Item
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockDropItemEvent
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.event.entity.CreatureSpawnEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.inventory.PrepareSmithingEvent
import org.bukkit.event.player.PlayerFishEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.world.StructureGrowEvent
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.RecipeChoice
import org.bukkit.inventory.ShapedRecipe
import org.bukkit.inventory.ShapelessRecipe
import org.bukkit.inventory.SmithingTransformRecipe
import org.bukkit.inventory.meta.ArmorMeta
import org.bukkit.inventory.meta.trim.ArmorTrim
import org.bukkit.inventory.meta.trim.TrimMaterial
import org.bukkit.inventory.meta.trim.TrimPattern
import org.bukkit.persistence.PersistentDataType

/**
 * The things you find in Haven and what they turn into.
 *
 * Diamond ore there drops diamond shards (nine make a diamond). Havenite debris (the azure-green chiseled copper deep
 * in the islands) drops havenite scrap; four scrap and four gold make a havenite ingot, and an ingot on the smithing
 * table turns a diamond armor piece into havenite (as strong as netherite, see HavenArmor). Amethyst there gives light
 * crystals, which make armor, a sword, shield, bow, fishing rod, horse armor or elytra shine (smithing table again).
 * Clams (sea pickles on the sea floor) and fishing give pearls, and very rarely an azure time pearl. Wheat there is
 * golden barley with twice the harvest, also when its seeds are planted at home. Pearlescent froglight from the
 * mountains is nacre: placed at home it shines and no monsters spawn within 16 blocks. Dawn and silver birch saplings
 * grow Haven trees anywhere.
 */
class HavenItems : Listener {

    companion object { var instance: HavenItems? = null }
    init { instance = this }

    enum class Kind(val base: Material, val title: String, val color: NamedTextColor, val lore: String, val model: String? = null) {
        DIAMOND_SHARD(Material.PRISMARINE_SHARD, "Diamond Shard", NamedTextColor.AQUA, "Nine of these make a diamond"),
        HAVENITE_SCRAP(Material.NETHERITE_SCRAP, "Havenite Scrap", NamedTextColor.DARK_AQUA, "Four of these and four gold make a havenite ingot"),
        HAVENITE_INGOT(Material.NETHERITE_INGOT, "Havenite Ingot", NamedTextColor.AQUA, "Turns diamond armor into havenite on the smithing table"),
        LIGHT_CRYSTAL(Material.AMETHYST_SHARD, "Light Crystal", NamedTextColor.LIGHT_PURPLE, "Makes armor, weapons and tools shine (smithing table)"),
        PEARL(Material.WHITE_DYE, "Pearl", NamedTextColor.WHITE, "A pearl from Haven", "minecraft:snowball"),
        TIME_PEARL(Material.HEART_OF_THE_SEA, "Time Pearl", NamedTextColor.BLUE, "Throw it (right click): everything around stands still for 10 seconds"),
        GOLDEN_BARLEY(Material.WHEAT, "Golden Barley", NamedTextColor.GOLD, "Twice the harvest of wheat"),
        BARLEY_SEEDS(Material.WHEAT_SEEDS, "Golden Barley Seeds", NamedTextColor.GOLD, "Grows golden barley, also at home"),
        NACRE(Material.PEARLESCENT_FROGLIGHT, "Nacre", NamedTextColor.WHITE, "Shines, and no monsters spawn within 16 blocks of it"),
        DAWN_SAPLING(Material.CHERRY_SAPLING, "Dawn Sapling", NamedTextColor.LIGHT_PURPLE, "Grows a pearl-white dawn tree"),
        SILVER_SAPLING(Material.BIRCH_SAPLING, "Silver Birch Sapling", NamedTextColor.GRAY, "Grows a birch with silver leaves"),
        GOLDEN_RAMSHORN(Material.GOAT_HORN, "Golden Ram's Horn", NamedTextColor.GOLD, "Overworld: the Lion comes and fights for you until every enemy is gone. Nether and End: the ground shakes and enemies are flung back"),
    }

    val kindKey = Haven.key("haven_item")
    val havenite = Haven.key("havenite")
    val lit = Haven.key("light_crystal")
    private val plants = Haven.key("haven_plants")   // chunk: "x,y,z,kind;..." for barley, saplings and nacre at home

    fun make(k: Kind, n: Int = 1): ItemStack {
        val it = ItemStack(k.base, n)
        it.editMeta { m ->
            m.itemName(Component.text(k.title, k.color))
            m.lore(listOf(Component.text(k.lore, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false)))
            m.persistentDataContainer.set(kindKey, PersistentDataType.STRING, k.name)
        }
        if (k.model != null) it.setData(DataComponentTypes.ITEM_MODEL, Key.key(k.model))
        if (k == Kind.TIME_PEARL || k == Kind.LIGHT_CRYSTAL || k == Kind.GOLDEN_RAMSHORN) it.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        if (k == Kind.GOLDEN_RAMSHORN) it.setData(DataComponentTypes.RARITY, org.bukkit.inventory.ItemRarity.EPIC)
        return it
    }

    fun kind(it: ItemStack?): Kind? {
        if (it == null || it.type.isAir || !it.hasItemMeta()) return null
        return it.itemMeta.persistentDataContainer.get(kindKey, PersistentDataType.STRING)?.let { runCatching { Kind.valueOf(it) }.getOrNull() }
    }

    fun isHavenite(it: ItemStack?) = it != null && it.hasItemMeta() && it.itemMeta.persistentDataContainer.has(havenite)
    fun isLit(it: ItemStack?) = it != null && it.hasItemMeta() && it.itemMeta.persistentDataContainer.has(lit)

    // --- Recipes -----------------------------------------------------------------------------------------

    private val ours = mutableSetOf<String>()

    fun recipes() {
        fun add(r: org.bukkit.inventory.Recipe, key: String) { Bukkit.removeRecipe(Haven.key(key)); Bukkit.addRecipe(r); ours.add(key) }
        add(ShapedRecipe(Haven.key("haven_diamond"), ItemStack(Material.DIAMOND)).shape("sss", "sss", "sss")
            .setIngredient('s', RecipeChoice.ExactChoice(make(Kind.DIAMOND_SHARD))), "haven_diamond")
        add(ShapelessRecipe(Haven.key("havenite_ingot"), make(Kind.HAVENITE_INGOT)).apply {
            repeat(4) { addIngredient(RecipeChoice.ExactChoice(make(Kind.HAVENITE_SCRAP))) }
            repeat(4) { addIngredient(Material.GOLD_INGOT) }
        }, "havenite_ingot")
        add(ShapelessRecipe(Haven.key("barley_seeds"), make(Kind.BARLEY_SEEDS, 2)).addIngredient(RecipeChoice.ExactChoice(make(Kind.GOLDEN_BARLEY))), "barley_seeds")
        add(ShapelessRecipe(Haven.key("barley_bread"), ItemStack(Material.BREAD, 2)).apply {
            repeat(3) { addIngredient(RecipeChoice.ExactChoice(make(Kind.GOLDEN_BARLEY))) } }, "barley_bread")
        val diamondArmor = listOf(Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS)
        add(SmithingTransformRecipe(Haven.key("havenite_armor"), ItemStack(Material.NETHERITE_CHESTPLATE), RecipeChoice.empty(),
            RecipeChoice.MaterialChoice(diamondArmor), RecipeChoice.ExactChoice(make(Kind.HAVENITE_INGOT)), true), "havenite_armor")
        add(SmithingTransformRecipe(Haven.key("light_crystal"), ItemStack(Material.DIAMOND_SWORD), RecipeChoice.empty(),
            RecipeChoice.MaterialChoice(LIGHTABLE), RecipeChoice.ExactChoice(make(Kind.LIGHT_CRYSTAL)), true), "light_crystal")
    }

    val LIGHTABLE: List<Material> = Material.entries.filter { m ->
        val n = m.name
        !m.isLegacy && (n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS") || n.endsWith("_BOOTS") ||
            n.endsWith("_SWORD") || n.endsWith("_HORSE_ARMOR") || m == Material.SHIELD || m == Material.BOW || m == Material.CROSSBOW ||
            m == Material.FISHING_ROD || m == Material.ELYTRA)
    }

    /** Our items never work as the vanilla items they are made from (no conduits from time pearls, no lodestones from havenite). */
    @EventHandler
    fun onCraft(e: PrepareItemCraftEvent) {
        val r = e.recipe as? org.bukkit.Keyed
        if (r != null && r.key.namespace == Haven.plugin.name.lowercase() && r.key.key in ours) return
        if (e.inventory.matrix.any { kind(it) != null && kind(it) != Kind.NACRE && kind(it) != Kind.DAWN_SAPLING && kind(it) != Kind.SILVER_SAPLING })
            e.inventory.result = null
    }

    @EventHandler
    fun onSmith(e: PrepareSmithingEvent) {
        val inv = e.inventory
        val base = inv.inputEquipment
        val add = inv.inputMineral
        val k = kind(add)
        when {
            k == Kind.HAVENITE_INGOT && base != null && base.type.name.startsWith("DIAMOND_") && !isHavenite(base) -> e.result = toHavenite(base)
            k == Kind.LIGHT_CRYSTAL && base != null && base.type in LIGHTABLE && !isLit(base) -> e.result = light(base)
            k != null || kind(base) != null -> e.result = null
        }
    }

    /** A diamond armor piece made into havenite: netherite strength, a pearly quartz trim, and the havenite mark. */
    fun toHavenite(base: ItemStack): ItemStack? {
        val slot = base.type.name.substringAfterLast('_')                    // any armor: leather, gold, iron, diamond, ...
        if (slot !in setOf("HELMET", "CHESTPLATE", "LEGGINGS", "BOOTS")) return null
        val to = Material.matchMaterial("NETHERITE_$slot") ?: return null
        val out = base.withType(to)
        out.editMeta { m ->
            m.itemName(Component.text("Havenite " + slot.lowercase().replaceFirstChar { it.uppercase() }, NamedTextColor.AQUA))
            m.persistentDataContainer.set(havenite, PersistentDataType.BYTE, 1)
            val what = when (slot) {
                "HELMET" -> "Breathe much longer under water"
                "CHESTPLATE" -> "Wings: jump in the air to glide"
                "LEGGINGS" -> "Step up blocks, run faster, swim like a dolphin"
                else -> "Little wings: you drift down instead of falling"
            }
            m.lore(listOf(Component.text(what, NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false),
                Component.text("Full set: sneak and jump to soar", NamedTextColor.DARK_AQUA).decoration(TextDecoration.ITALIC, false)))
            if (m is ArmorMeta) m.trim = ArmorTrim(TrimMaterial.QUARTZ, TrimPattern.FLOW)
        }
        if (slot == "CHESTPLATE") out.setData(DataComponentTypes.GLIDER)
        return out
    }

    fun light(base: ItemStack): ItemStack {
        val out = base.clone()
        out.editMeta { m ->
            m.persistentDataContainer.set(lit, PersistentDataType.BYTE, 1)
            val lore = (m.lore() ?: emptyList()).toMutableList()
            lore.add(Component.text("Light crystal: shines with a warm light", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false))
            m.lore(lore)
        }
        out.setData(DataComponentTypes.ENCHANTMENT_GLINT_OVERRIDE, true)
        return out
    }

    // --- What blocks give in Haven ----------------------------------------------------------------------

    private fun drop(b: Block, vararg items: ItemStack) = items.forEach { b.world.dropItemNaturally(b.location.add(0.5, 0.5, 0.5), it) }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    fun onBreak(e: BlockBreakEvent) {
        val b = e.block
        val tool = e.player.inventory.itemInMainHand
        val fortune = tool.getEnchantmentLevel(Enchantment.FORTUNE)
        val haven = Haven.isHaven(b.world)
        val marked = plantAt(b)
        if (marked != null) forget(b)
        when {
            haven && (b.type == Material.DIAMOND_ORE || b.type == Material.DEEPSLATE_DIAMOND_ORE) && e.player.gameMode != org.bukkit.GameMode.CREATIVE && b.isPreferredTool(tool) -> {
                e.isDropItems = false
                drop(b, make(Kind.DIAMOND_SHARD, 2 + Haven.rnd.nextInt(3 + fortune * 2)))
            }
            haven && b.type == Material.WAXED_OXIDIZED_CHISELED_COPPER && e.player.gameMode != org.bukkit.GameMode.CREATIVE -> {
                e.isDropItems = false
                drop(b, make(Kind.HAVENITE_SCRAP))
                b.world.playSound(b.location, Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 1f, 1.6f)
            }
            haven && b.type == Material.AMETHYST_CLUSTER -> { e.isDropItems = false; drop(b, make(Kind.LIGHT_CRYSTAL, 1 + Haven.rnd.nextInt(1 + fortune))) }
            haven && b.type == Material.SEA_PICKLE -> {
                e.isDropItems = false
                val r = Haven.rnd.nextDouble()
                drop(b, if (r < 0.01) make(Kind.TIME_PEARL) else if (r < 0.3) make(Kind.PEARL) else ItemStack(Material.SEA_PICKLE))
            }
            b.type == Material.WHEAT && (haven || marked == "barley") -> {
                e.isDropItems = false
                val ripe = (b.blockData as Ageable).let { it.age == it.maximumAge }
                drop(b, make(Kind.BARLEY_SEEDS, 1 + if (ripe) Haven.rnd.nextInt(3) else 0))
                if (ripe) drop(b, make(Kind.GOLDEN_BARLEY, 2))
            }
            haven && b.type == Material.PEARLESCENT_FROGLIGHT -> { e.isDropItems = false; drop(b, make(Kind.NACRE)) }
            marked == "nacre" -> { e.isDropItems = false; drop(b, make(Kind.NACRE)) }
        }
    }

    /** Saplings from Haven's trees are Haven saplings. */
    @EventHandler(ignoreCancelled = true)
    fun onLeafDrop(e: BlockDropItemEvent) {
        if (!Haven.isHaven(e.block.world)) return
        for (i in e.items) {
            val it = i.itemStack
            if (it.type == Material.CHERRY_SAPLING && kind(it) == null) i.itemStack = make(Kind.DAWN_SAPLING, it.amount)
            if (it.type == Material.PALE_OAK_SAPLING) i.itemStack = make(Kind.SILVER_SAPLING, it.amount)
        }
    }

    // --- Planting at home: barley, Haven saplings and nacre are remembered in the chunk --------------------

    private fun pos(b: Block) = "${b.x},${b.y},${b.z}"

    private fun list(b: Block): MutableMap<String, String> =
        (b.chunk.persistentDataContainer.get(plants, PersistentDataType.STRING) ?: "").split(";").filter { it.contains('=') }
            .associate { it.substringBefore('=') to it.substringAfter('=') }.toMutableMap()

    private fun store(b: Block, m: Map<String, String>) =
        b.chunk.persistentDataContainer.set(plants, PersistentDataType.STRING, m.entries.joinToString(";") { "${it.key}=${it.value}" })

    fun plantAt(b: Block): String? = list(b)[pos(b)]
    private fun forget(b: Block) { val m = list(b); if (m.remove(pos(b)) != null) store(b, m) }
    private fun remember(b: Block, what: String) { val m = list(b); m[pos(b)] = what; store(b, m) }

    @EventHandler(ignoreCancelled = true)
    fun onPlace(e: BlockPlaceEvent) {
        when (kind(e.itemInHand)) {
            Kind.BARLEY_SEEDS -> remember(e.blockPlaced, "barley")
            Kind.DAWN_SAPLING -> remember(e.blockPlaced, "dawn")
            Kind.SILVER_SAPLING -> remember(e.blockPlaced, "silver")
            Kind.NACRE -> if (!Haven.isHaven(e.blockPlaced.world)) remember(e.blockPlaced, "nacre")
            null -> {}
            else -> if (e.itemInHand.type.isBlock) { e.isCancelled = true }     // pearls and such are not blocks to set down
        }
    }

    @EventHandler(ignoreCancelled = true)
    fun onGrow(e: StructureGrowEvent) {
        val b = e.location.block
        val what = plantAt(b) ?: return
        forget(b)
        val swap = when (what) {
            "dawn" -> mapOf(Material.CHERRY_LOG to Material.STRIPPED_PALE_OAK_LOG, Material.CHERRY_WOOD to Material.STRIPPED_PALE_OAK_WOOD)
            "silver" -> mapOf(Material.BIRCH_LEAVES to Material.PALE_OAK_LEAVES)
            else -> return
        }
        for (s in e.blocks) swap[s.type]?.let { to ->
            val d = to.createBlockData()
            if (d is org.bukkit.block.data.Orientable && s.blockData is org.bukkit.block.data.Orientable) d.axis = (s.blockData as org.bukkit.block.data.Orientable).axis
            if (d is org.bukkit.block.data.type.Leaves) d.isPersistent = false
            s.blockData = d
        }
    }

    /** Nacre at home: no monsters spawn within 16 blocks of it. */
    @EventHandler(ignoreCancelled = true)
    fun onSpawn(e: CreatureSpawnEvent) {
        if (e.entity !is Enemy || Haven.isHaven(e.location.world)) return
        if (e.spawnReason != CreatureSpawnEvent.SpawnReason.NATURAL && e.spawnReason != CreatureSpawnEvent.SpawnReason.REINFORCEMENTS &&
            e.spawnReason != CreatureSpawnEvent.SpawnReason.PATROL) return
        val l = e.location
        for (cx in ((l.blockX - 16) shr 4)..((l.blockX + 16) shr 4)) for (cz in ((l.blockZ - 16) shr 4)..((l.blockZ + 16) shr 4)) {
            if (!l.world.isChunkLoaded(cx, cz)) continue
            val s = l.world.getChunkAt(cx, cz).persistentDataContainer.get(plants, PersistentDataType.STRING) ?: continue
            for (entry in s.split(";")) {
                if (!entry.endsWith("=nacre")) continue
                val (x, y, z) = entry.substringBefore('=').split(",").map { it.toInt() }
                if ((x - l.x) * (x - l.x) + (y - l.y) * (y - l.y) + (z - l.z) * (z - l.z) < 16.0 * 16) { e.isCancelled = true; return }
            }
        }
    }

    // --- Pearls from fishing, time pearls --------------------------------------------------------------

    @EventHandler(ignoreCancelled = true)
    fun onFish(e: PlayerFishEvent) {
        val rod = e.player.inventory.itemInMainHand.takeIf { it.type == Material.FISHING_ROD } ?: e.player.inventory.itemInOffHand
        val litRod = isLit(rod)
        if (e.state == PlayerFishEvent.State.FISHING && litRod) e.hook.setWaitTime(40, 300)       // the glowing lure brings fish faster
        if (e.state != PlayerFishEvent.State.CAUGHT_FISH) return
        val haven = Haven.isHaven(e.player.world)
        var chance = if (haven) 1.0 / 30 else 1.0 / 150
        if (litRod) chance *= 1.5
        val r = Haven.rnd.nextDouble()
        val prize = when {
            haven && r < 0.0015 -> make(Kind.TIME_PEARL)
            r < chance -> make(Kind.PEARL)
            else -> return
        }
        (e.caught as? Item)?.itemStack = prize
        e.player.sendActionBar(Component.text(if (kind(prize) == Kind.TIME_PEARL) "A time pearl!" else "A pearl!", NamedTextColor.AQUA))
    }

    @EventHandler
    fun onUse(e: PlayerInteractEvent) {
        if (e.action != Action.RIGHT_CLICK_AIR && e.action != Action.RIGHT_CLICK_BLOCK) return
        val it = e.item ?: return
        if (kind(it) != Kind.TIME_PEARL) return
        e.isCancelled = true
        if (e.player.gameMode != org.bukkit.GameMode.CREATIVE) it.amount -= 1
        timeStop(e.player, e.player.location)
    }

    /** Everything around stands still for 10 seconds: in Haven all creatures (angels too), elsewhere monsters. Never players. */
    fun timeStop(by: Player, at: Location) {
        val haven = Haven.isHaven(at.world)
        val w = at.world
        w.playSound(at, Sound.BLOCK_BEACON_DEACTIVATE, 1.5f, 0.5f)
        w.playSound(at, Sound.BLOCK_BELL_RESONATE, 1.5f, 1.5f)
        w.spawnParticle(Particle.DUST, at.clone().add(0.0, 1.0, 0.0), 300, 8.0, 3.0, 8.0, Particle.DustOptions(org.bukkit.Color.fromRGB(0x2A, 0xA7, 0xFF), 1.5f))
        var n = 0
        for (en in w.getNearbyEntities(at, 16.0, 16.0, 16.0)) {
            if (en is Player || en.location.distanceSquared(at) > 16.0 * 16) continue
            if (en !is LivingEntity) continue
            if (!haven && en !is Enemy) continue
            Haven.freezeMob(en, 10_000)
            en.velocity = org.bukkit.util.Vector()
            n++
        }
        Haven.frozen.keys.removeIf { (Haven.frozen[it] ?: 0) < System.currentTimeMillis() - 60_000 }
        HavenMagic.instance?.freezeSites(at, 10_000)
        by.sendActionBar(Component.text("Time stands still ($n)", NamedTextColor.BLUE))
    }
}
