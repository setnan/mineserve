package dev.etnan.creator

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.generator.BiomeProvider
import org.bukkit.generator.ChunkGenerator
import org.bukkit.generator.WorldInfo
import org.bukkit.util.noise.SimplexOctaveGenerator
import java.util.Random
import java.util.concurrent.ConcurrentHashMap

/**
 * The land of a Haven world, from the world's own seed: floating islands between about y 60 and 240 over an azure
 * sea (surface at y 40) with a floor of sand and prismarine. The islands are not dirt and stone like the overworld:
 * grass on moss over pearl-white calcite, with an azure prismarine underside (see layer()). Minecraft then places the
 * trees, flowers, ores, coral and waterfalls of the Haven biomes (the haven datapack, gen.py).
 *
 * The biome comes from a temperature and a humidity map in the same 4 x 3 grid as before the worlds had their own
 * seeds: cold glory peaks and silver birch, mild meadows and dawn groves, warm golden plains and oases, hot savanna
 * and cloud jungle.
 */
class HavenGenerator : ChunkGenerator() {

    companion object {
        const val SEA = 40
        const val ISLANDS_LOW = 56
        const val ISLANDS_HIGH = 248

        private val GRID = listOf(
            listOf("glory_peaks", "silver_birch", "crystal_valley"),
            listOf("flower_meadow", "dawn_grove", "rainbow_falls"),
            listOf("golden_plains", "golden_forest", "oasis"),
            listOf("golden_savanna", "glory_peaks", "cloud_jungle"))

        val BIOMES: List<Biome> by lazy {
            val reg = RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME)
            GRID.flatten().distinct().mapNotNull { reg.get(NamespacedKey("haven", it)) }
        }
        private val byName: Map<String, Biome> by lazy { BIOMES.associateBy { it.key.key } }

        private val noises = ConcurrentHashMap<Long, Noise>()
        fun noise(seed: Long): Noise = noises.getOrPut(seed) { Noise(seed) }

        /** The biome name at (x, z) in a Haven with this seed. */
        fun biomeName(seed: Long, x: Int, z: Int): String {
            val n = noise(seed)
            val t = (n.temperature.noise(x.toDouble(), z.toDouble(), 0.5, 0.5, true) * 1.8).coerceIn(-0.999, 0.999)
            val h = (n.humidity.noise(x.toDouble(), z.toDouble(), 0.5, 0.5, true) * 1.8).coerceIn(-0.999, 0.999)
            val ti = ((t + 1) / 2 * 4).toInt().coerceIn(0, 3)
            val hi = if (h < -0.3) 0 else if (h < 0.3) 1 else 2
            return GRID[ti][hi]
        }
    }

    class Noise(private val seed: Long) {
        private fun gen(salt: Long, octaves: Int, scale: Double) = SimplexOctaveGenerator(Random(seed xor salt), octaves).also { it.setScale(scale) }
        val shape = gen(0x5A1E5L, 3, 1.0 / 72)          // the rough 3D shape of the islands
        val mask = gen(0x15_1A_4DL, 2, 1.0 / 260)         // where islands are and where the open sky is
        val middle = gen(0x41DL, 2, 1.0 / 340)           // how high an island floats
        val thick = gen(0x7A1CL, 2, 1.0 / 180)           // how thick it is
        val floor = gen(0xF100L, 2, 1.0 / 90)            // the sea floor
        val temperature = gen(0x7E3BL, 2, 1.0 / 700)
        val humidity = gen(0x4B1DL, 2, 1.0 / 520)
        val patch = gen(0xBA7CL, 2, 1.0 / 14)            // patches of sand, calcite, mud and such on the surface
        val vein = gen(0xD10E1L, 2, 1.0 / 18)            // diorite veins in the pearl rock

        /** Positive inside an island. Lens shaped: flat-ish on top, deeper underneath. */
        fun density(x: Double, y: Double, z: Double): Double {
            val m = mask.noise(x, z, 0.5, 0.5, true)
            val mid = 150 + 55 * middle.noise(x, z, 0.5, 0.5, true)
            val half = 16 + 14 * (thick.noise(x, z, 0.5, 0.5, true) + 1)
            var dy = (y - mid) / half
            dy *= if (dy > 0) 1.7 else 0.75
            val lens = 1 - dy * dy
            return lens * 0.9 + shape.noise(x, y, z, 0.5, 0.5, true) * 0.6 + m * 1.1 - 0.5
        }
    }

    override fun shouldGenerateNoise() = false
    override fun shouldGenerateSurface() = false
    override fun shouldGenerateCaves() = false
    override fun shouldGenerateDecorations() = true
    override fun shouldGenerateMobs() = true
    override fun shouldGenerateStructures() = false

    override fun generateNoise(info: WorldInfo, random: Random, cx: Int, cz: Int, data: ChunkData) {
        val seed = info.seed
        val n = noise(seed)
        val bx = cx shl 4
        val bz = cz shl 4
        // the sea and its floor: bedrock, stone, a little prismarine, then sand with patches of prismarine and calcite
        for (x in 0..15) for (z in 0..15) {
            val wx = bx + x
            val wz = bz + z
            val f = (12 + 7 * n.floor.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true)).toInt()
            data.setRegion(x, info.minHeight, z, x + 1, f - 4, z + 1, Material.STONE)
            for (y in info.minHeight until info.minHeight + 5) if (y == info.minHeight || chance(seed, wx, y, wz) < 0.5 - (y - info.minHeight) * 0.1)
                data.setBlock(x, y, z, Material.BEDROCK)
            data.setRegion(x, f - 4, z, x + 1, f - 1, z + 1, Material.PRISMARINE)
            val p = n.patch.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true)
            val top = if (p > 0.45) Material.PRISMARINE else if (p < -0.5) Material.CALCITE else Material.SAND
            data.setRegion(x, f - 1, z, x + 1, f + 1, z + 1, top)
            data.setRegion(x, f + 1, z, x + 1, SEA + 1, z + 1, Material.WATER)
        }
        // the islands: density on a 4 x 8 x 4 grid, filled in between
        val ny = (ISLANDS_HIGH - ISLANDS_LOW) / 8 + 1
        val h = ISLANDS_HIGH - ISLANDS_LOW
        val solid = Array(16) { Array(16) { BooleanArray(h + 1) } }
        val d = Array(5) { i -> Array(5) { k -> DoubleArray(ny) { j -> n.density((bx + i * 4).toDouble(), (ISLANDS_LOW + j * 8).toDouble(), (bz + k * 4).toDouble()) } } }
        for (i in 0 until 4) for (k in 0 until 4) for (j in 0 until ny - 1) {
            for (yy in 0 until 8) {
                val ty = yy / 8.0
                for (xx in 0 until 4) {
                    val tx = xx / 4.0
                    for (zz in 0 until 4) {
                        val tz = zz / 4.0
                        fun at(a: Int, b: Int, c: Int) = d[i + a][k + c][j + b]
                        val v = lerp(ty,
                            lerp(tx, lerp(tz, at(0, 0, 0), at(0, 0, 1)), lerp(tz, at(1, 0, 0), at(1, 0, 1))),
                            lerp(tx, lerp(tz, at(0, 1, 0), at(0, 1, 1)), lerp(tz, at(1, 1, 0), at(1, 1, 1))))
                        if (v > 0) solid[i * 4 + xx][k * 4 + zz][j * 8 + yy] = true
                    }
                }
            }
        }
        // the layers, column by column: how deep under the top each block is and how far above the underside
        val below = IntArray(h + 1)
        for (x in 0..15) for (z in 0..15) {
            val col = solid[x][z]
            val wx = bx + x
            val wz = bz + z
            val biome = biomeName(seed, wx, wz)
            val p = n.patch.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true)
            var run = 0
            for (y in 0..h) { run = if (col[y]) run + 1 else 0; below[y] = run - 1 }
            var depth = -1
            for (y in h downTo 0) {
                if (!col[y]) { depth = -1; continue }
                depth++
                val wy = ISLANDS_LOW + y
                data.setBlock(x, wy, z, layer(seed, biome, p, depth, below[y], wx, wy, wz, n))
            }
        }
    }

    /**
     * Haven's earth, from the top down: grass (or the biome's own top), one or two layers of moss instead of dirt,
     * pearl-white calcite with diorite veins and now and then raw gold deep inside, and azure prismarine with glowing
     * sea lanterns on the underside you see from below.
     */
    private fun layer(seed: Long, biome: String, p: Double, depth: Int, above: Int, x: Int, y: Int, z: Int, n: Noise): Material {
        val r = chance(seed, x, y, z)
        if (above <= 1 && depth > 0) return if (above == 0 && r < 0.025) Material.SEA_LANTERN else Material.PRISMARINE
        if (depth == 0) return when (biome) {
            "glory_peaks" -> if (p > 0.15) Material.GRASS_BLOCK else if (r < 0.03) Material.RAW_GOLD_BLOCK else if (r < 0.08) Material.QUARTZ_BLOCK else Material.CALCITE
            "crystal_valley" -> if (p > 0.45) (if (r < 0.2) Material.AMETHYST_BLOCK else Material.CALCITE) else Material.GRASS_BLOCK
            "golden_plains", "golden_savanna" -> if (p > 0.55) Material.SAND else Material.GRASS_BLOCK
            "oasis" -> if (p > 0.5) Material.SAND else if (p > 0.38) Material.MUD else Material.GRASS_BLOCK
            else -> Material.GRASS_BLOCK
        }
        if (depth <= 2) return when (biome) {
            "glory_peaks" -> if (p > 0.15 && depth == 1) Material.MOSS_BLOCK else if (r < 0.04) Material.RAW_GOLD_BLOCK else Material.CALCITE
            "crystal_valley" -> if (p > 0.45) Material.CALCITE else Material.MOSS_BLOCK
            "golden_plains", "golden_savanna" -> if (p > 0.55) Material.SANDSTONE else Material.MOSS_BLOCK
            "oasis" -> if (p > 0.5) Material.SAND else if (p > 0.38) Material.MUD else Material.MOSS_BLOCK
            "golden_forest" -> if (p > 0.4) Material.HONEYCOMB_BLOCK else Material.MOSS_BLOCK
            else -> Material.MOSS_BLOCK
        }
        val goldChance = if (biome == "glory_peaks") 1.0 / 80 else 1.0 / 400
        if (depth > 10 && r < goldChance) return Material.RAW_GOLD_BLOCK
        val v = n.vein.noise(x.toDouble(), y.toDouble(), z.toDouble(), 0.5, 0.5, true)
        return when {
            v > 0.45 -> Material.DIORITE
            v < -0.6 && r < 0.3 -> Material.QUARTZ_BLOCK
            else -> Material.CALCITE
        }
    }

    /** A number in 0..1 that is always the same for the same place in the same world (thread safe, no state). */
    private fun chance(seed: Long, x: Int, y: Int, z: Int): Double {
        var h = seed xor (x * 0x9E3779B97F4A7C15uL.toLong()) xor (y * 0xC2B2AE3D27D4EB4FuL.toLong()) xor (z * 0x165667B19E3779F9L)
        h = (h xor (h ushr 33)) * -0xae502812aa7333L
        h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
        h = h xor (h ushr 33)
        return (h ushr 11).toDouble() / (1L shl 53).toDouble()
    }

    private fun lerp(t: Double, a: Double, b: Double) = a + (b - a) * t

    override fun getDefaultBiomeProvider(info: WorldInfo): BiomeProvider = Biomes

    override fun getFixedSpawnLocation(world: World, random: Random): Location = Location(world, 0.5, SEA + 1.0, 0.5)

    object Biomes : BiomeProvider() {
        override fun getBiome(info: WorldInfo, x: Int, y: Int, z: Int): Biome = byName[biomeName(info.seed, x, z)] ?: Biome.PLAINS
        override fun getBiomes(info: WorldInfo): List<Biome> = BIOMES.ifEmpty { listOf(Biome.PLAINS) }
    }
}
