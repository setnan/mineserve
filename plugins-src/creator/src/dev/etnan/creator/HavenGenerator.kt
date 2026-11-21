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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * The land of a Haven world, from the world's own seed: floating islands over an azure sea (surface at y 40) with a
 * floor of sand and prismarine. The main islands float low, under the clouds (y 192): their ground is mostly between
 * y 100 and 140, with rolling hills, terraces and, in the cold parts, peaks up to about y 180. Above them drift small
 * islets, alone or in little groups, that the plugin gives waterfalls (HavenWorld). The islands are not dirt and stone
 * like the overworld: grass and moss over banded pearl rock, with an azure prismarine underside (see layer()).
 * Inside the bigger islands there are caves (lush, crystal or water caves: see caveTheme): winding tunnels and halls, lined with prismarine and lit by the same
 * sea lanterns as the undersides, open to the cliffs and here and there to the sky.
 * Minecraft then places the trees, flowers, ores, coral and waterfalls of the Haven biomes (the haven datapack, gen.py).
 *
 * The biome comes from a temperature and a humidity map in the same 4 x 3 grid as before the worlds had their own
 * seeds: cold glory peaks and silver birch, mild meadows and dawn groves, warm golden plains and oases, hot savanna
 * and cloud jungle.
 */
class HavenGenerator : ChunkGenerator() {

    companion object {
        const val SEA = 40
        const val ISLANDS_LOW = 48
        const val ISLANDS_HIGH = 192
        const val CLOUDS = 192
        private const val CELL = 40                       // one group of islets at most per 40 x 40 blocks

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

        /** A number in 0..1 that is always the same for the same place in the same world (thread safe, no state). */
        fun chance(seed: Long, x: Int, y: Int, z: Int): Double {
            var h = seed xor (x * 0x9E3779B97F4A7C15uL.toLong()) xor (y * 0xC2B2AE3D27D4EB4FuL.toLong()) xor (z * 0x165667B19E3779F9L)
            h = (h xor (h ushr 33)) * -0xae502812aa7333L
            h = (h xor (h ushr 33)) * -0x3b314601e57a13adL
            h = h xor (h ushr 33)
            return (h ushr 11).toDouble() / (1L shl 53).toDouble()
        }

        /** Would the cave noise hollow the rock here? (Whether it does also depends on the rock around: see generateNoise.) */
        fun caveAt(n: Noise, x: Int, y: Int, z: Int): Boolean {
            val a = n.tunnelA.noise(x.toDouble(), y * 1.7, z.toDouble(), 0.5, 0.5, true)
            val b = n.tunnelB.noise(x.toDouble(), y * 1.7, z.toDouble(), 0.5, 0.5, true)
            return a * a + b * b < 0.016 || n.cavern.noise(x.toDouble(), y * 1.5, z.toDouble(), 0.5, 0.5, true) > 0.47 ||
                n.hall.noise(x.toDouble(), y * 1.15, z.toDouble(), 0.5, 0.5, true) > 0.56
        }

        /** What kind of cave lies under (x, z): 0 lush (moss, vines and blossoms), 1 crystal (amethyst and calcite), 2 water (prismarine and pools). */
        fun caveTheme(seed: Long, x: Int, z: Int): Int {
            val v = noise(seed).theme.noise(x.toDouble(), z.toDouble(), 0.5, 0.5, true)
            return if (v > 0.2) 1 else if (v < -0.2) 2 else 0
        }

        /** A small island in the sky: the middle of its top and how wide it is. */
        class Islet(val x: Int, val y: Int, val z: Int, val r: Int)

        /**
         * The islets of one cell: none, or one with a radius of 4 to 13 with up to two smaller ones beside it, 24 to
         * 60 blocks above the land (or the gap) under them and always under the clouds.
         */
        fun islets(seed: Long, ci: Int, cj: Int): List<Islet> {
            fun h(k: Int) = chance(seed xor 0x15E7L, ci, k, cj)
            if (h(0) > 0.36) return emptyList()
            val x = ci * CELL + 8 + (h(1) * 24).toInt()
            val z = cj * CELL + 8 + (h(2) * 24).toInt()
            val c = noise(seed).column(x.toDouble(), z.toDouble())
            val ground = if (c.land > 0) c.mid + c.up else c.mid
            val y = minOf(ground + 24 + h(3) * 38, CLOUDS - 8.0).toInt()
            if (y - ground < 18) return emptyList()
            val r = 4 + (h(4) * h(4) * 10).toInt()                        // most are small, a few up to 13
            val out = mutableListOf(Islet(x, y, z, r))
            for (i in 0 until (h(5) * 3).toInt()) {
                val a = h(6 + i * 4) * Math.PI * 2
                val far = r + 5 + h(7 + i * 4) * 8
                val sy = (y + (h(8 + i * 4) - 0.4) * 22).toInt().coerceIn(minOf(ground.toInt() + 14, CLOUDS - 8), CLOUDS - 6)
                out.add(Islet(x + (cos(a) * far).toInt(), sy, z + (sin(a) * far).toInt(), 2 + (h(9 + i * 4) * 3).toInt()))
            }
            return out
        }

        /** The islets that can reach into the square (x0..x1, z0..z1). */
        fun isletsNear(seed: Long, x0: Int, z0: Int, x1: Int, z1: Int): List<Islet> {
            val out = mutableListOf<Islet>()
            for (ci in Math.floorDiv(x0 - 24, CELL)..Math.floorDiv(x1 + 24, CELL)) for (cj in Math.floorDiv(z0 - 24, CELL)..Math.floorDiv(z1 + 24, CELL))
                out.addAll(islets(seed, ci, cj))
            return out
        }

        /** For the log when a Haven is made: how much of it is land, how high the ground lies and how many islets there are. */
        fun survey(seed: Long): String {
            val n = noise(seed)
            var land = 0; var all = 0; var sum = 0.0; var hi = 0.0; var lo = 999.0; var over = 0
            for (x in -1000..1000 step 20) for (z in -1000..1000 step 20) {
                all++
                val c = n.column(x.toDouble(), z.toDouble())
                if (c.land <= 0.1) continue
                land++
                val top = c.mid + c.up
                sum += top; hi = maxOf(hi, top); lo = minOf(lo, top)
                if (top > 150) over++
            }
            var isl = 0
            for (ci in -25 until 25) for (cj in -25 until 25) isl += islets(seed, ci, cj).size
            return "land ${land * 100 / all} %, ground y ${lo.toInt()}..${hi.toInt()} (mean ${(sum / maxOf(land, 1)).toInt()}, ${over * 100 / maxOf(land, 1)} % over 150), $isl islets in 2000 x 2000"
        }

        /** Is there an islet over (x, z)? Portals are not put on them. */
        fun isletOver(seed: Long, x: Int, z: Int) = isletsNear(seed, x, z, x, z).any { (it.x - x) * (it.x - x) + (it.z - z) * (it.z - z) <= (it.r + 2) * (it.r + 2) }
    }

    class Noise(private val seed: Long) {
        private fun gen(salt: Long, octaves: Int, scale: Double) = SimplexOctaveGenerator(Random(seed xor salt), octaves).also { it.setScale(scale) }
        val shape = gen(0x5A1E5L, 3, 1.0 / 72)          // the rough 3D shape of the islands
        val mask = gen(0x15_1A_4DL, 2, 1.0 / 260)         // where islands are and where the open sky is
        val detail = gen(0xC0A57L, 2, 1.0 / 64)          // bays and headlands along their edges
        val middle = gen(0x41DL, 2, 1.0 / 340)           // how high an island floats
        val thick = gen(0x7A1CL, 2, 1.0 / 180)           // how thick it is
        val hills = gen(0x4111L, 3, 1.0 / 84)            // hills on top
        val rift = gen(0x41F7L, 2, 1.0 / 150)            // chasms that cut the land into islands
        val ridge = gen(0x91D6EL, 2, 1.0 / 170)          // ridges that become peaks where it is cold
        val terrace = gen(0x7E44L, 1, 1.0 / 230)         // where the land rises in steps
        val floor = gen(0xF100L, 2, 1.0 / 90)            // the sea floor
        val temperature = gen(0x7E3BL, 2, 1.0 / 700)
        val humidity = gen(0x4B1DL, 2, 1.0 / 520)
        val patch = gen(0xBA7CL, 2, 1.0 / 14)            // small patches of sand, rock, mud and such on the surface
        val tint = gen(0x71A7L, 2, 1.0 / 38)             // larger patches of moss and the like
        val vein = gen(0xD10E1L, 2, 1.0 / 18)            // veins and bands in the pearl rock
        val tunnelA = gen(0xCA7E1L, 1, 1.0 / 44)         // caves: winding tunnels where both of these are near zero
        val tunnelB = gen(0xCA7E2L, 1, 1.0 / 44)
        val cavern = gen(0xCA7E3L, 1, 1.0 / 52)          // and halls where this one is high
        val hall = gen(0xCA7E5L, 1, 1.0 / 92)            // and now and then a great hall
        val theme = gen(0xCA7E4L, 1, 1.0 / 150)          // lush caves, crystal caves and water caves
        val sink = gen(0x51C4L, 2, 1.0 / 30)             // where a cave may open to the sky

        /** What is the same all the way up a column: how high the island floats, how deep it hangs, how high it rises. */
        class Col(val mid: Double, val half: Double, val land: Double, val up: Double)

        fun column(x: Double, z: Double): Col {
            fun n2(g: SimplexOctaveGenerator) = g.noise(x, z, 0.5, 0.5, true)
            val chasm = (1 - abs(n2(rift)) / 0.07).coerceAtLeast(0.0) * 0.9
            val land = 0.02 + n2(mask) * 1.2 + n2(detail) * 0.45 - chasm  // above 0: island
            val mid = 112 + 18 * n2(middle)
            val half = 14 + 10 * (n2(thick) + 1)
            val t = (n2(temperature) * 1.8).coerceIn(-0.999, 0.999)
            val cold = 0.3 + 0.7 * ((-t - 0.25) / 0.35).coerceIn(0.0, 1.0)
            val peaks = ((1 - abs(n2(ridge)) - 0.6) / 0.4).coerceAtLeast(0.0).pow(1.4) * 58 * cold
            var up = 4 + 32 * (0.45 + n2(hills) * 2.0).coerceIn(0.0, 1.0).pow(1.6) + peaks
            val tn = n2(terrace)                                           // half the land rises in steps of seven: mesas and ledges
            if (tn > 0.0) up += (Math.round(up / 7) * 7.0 - up) * (tn / 0.15).coerceAtMost(1.0) * 0.9
            up *= land.coerceIn(0.0, 1.0).pow(0.6)                         // the land thins out towards the edge
            return Col(mid, half, land, minOf(up, CLOUDS - 9 - mid).coerceAtLeast(0.0))
        }

        /**
         * Positive inside an island. Under its middle a deep, rough lens (the underside); over it a landscape that
         * rises to mid + up, a little rough too, so there are hollows, ledges and overhangs.
         */
        fun density(c: Col, x: Double, y: Double, z: Double): Double {
            val s = shape.noise(x, y, z, 0.5, 0.5, true)
            var d = if (y <= c.mid) {
                val dy = (y - c.mid) / c.half * 0.75
                c.land - 0.9 * dy * dy + 0.6 * s
            } else {
                val t = (y - c.mid) / maxOf(c.up, 1.5)
                val base = c.land + 0.6 * s * (1 - 0.6 * minOf(t, 1.0))
                if (base > 0) base * (1 - t) else base
            }
            if (y < ISLANDS_LOW + 12) d -= (ISLANDS_LOW + 12 - y) * 0.08   // nothing hangs down into the sea
            return d
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
        val skin = Array(16) { Array(16) { BooleanArray(h + 1) } }           // close to the outside: cliff faces
        val d = Array(5) { i -> Array(5) { k ->
            val c = n.column((bx + i * 4).toDouble(), (bz + k * 4).toDouble())
            DoubleArray(ny) { j -> n.density(c, (bx + i * 4).toDouble(), (ISLANDS_LOW + j * 8).toDouble(), (bz + k * 4).toDouble()) } } }
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
                        if (v > 0) { solid[i * 4 + xx][k * 4 + zz][j * 8 + yy] = true; if (v < 0.2) skin[i * 4 + xx][k * 4 + zz][j * 8 + yy] = true }
                    }
                }
            }
        }
        // the islets in the sky: a slightly domed top and a ragged cone hanging under it
        val isletFrom = Array(16) { IntArray(16) { Int.MAX_VALUE } }       // the lowest islet block of each column
        for (isl in isletsNear(seed, bx, bz, bx + 15, bz + 15)) for (x in 0..15) for (z in 0..15) {
            val wx = bx + x
            val wz = bz + z
            val dx = wx - isl.x
            val dz = wz - isl.z
            if (abs(dx) > isl.r + 3 || abs(dz) > isl.r + 3) continue
            val rr = isl.r * (0.85 + 0.25 * n.patch.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true))
            val dd = sqrt((dx * dx + dz * dz).toDouble()) / rr
            if (dd >= 1) continue
            val top = isl.y + (1.6 * (1 - dd)).toInt()
            val deep = 1 + (isl.r * 1.7 * (1 - dd).pow(1.3)).toInt() + (chance(seed, wx, -7, wz) * 2).toInt()
            for (y in top - deep..top) if (y - ISLANDS_LOW in 0..h) solid[x][z][y - ISLANDS_LOW] = true
            isletFrom[x][z] = minOf(isletFrom[x][z], top - deep)
        }
        // where the cave noise would hollow the rock, one block out past the chunk on every side (to line the walls)
        var lo = h + 1
        var hi = -1
        for (x in 0..15) for (z in 0..15) for (y in 0..h) if (solid[x][z][y]) { if (y < lo) lo = y; if (y > hi) hi = y }
        val hollow = Array(18) { Array(18) { BooleanArray(h + 1) } }
        for (i in 0..17) for (k in 0..17) for (y in lo..hi) hollow[i][k][y] = caveAt(n, bx - 1 + i, ISLANDS_LOW + y, bz - 1 + k)
        // the layers, column by column: how deep under the top each block is and how far above the underside. Caves
        // are cut where there is rock enough around them: at least four blocks of roof (none where a sinkhole opens
        // them to the sky) and three of floor over the underside, never in the islets.
        val mat = Array(16) { Array(16) { arrayOfNulls<Material>(h + 1) } }
        val cave = Array(16) { Array(16) { BooleanArray(h + 1) } }
        val below = IntArray(h + 1)
        for (x in 0..15) for (z in 0..15) {
            val col = solid[x][z]
            val wx = bx + x
            val wz = bz + z
            val biome = biomeName(seed, wx, wz)
            val p = n.patch.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true)
            val q = n.tint.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true)
            val sink = n.sink.noise(wx.toDouble(), wz.toDouble(), 0.5, 0.5, true) > 0.5
            var run = 0
            for (y in 0..h) { run = if (col[y]) run + 1 else 0; below[y] = run - 1 }
            var depth = -1
            for (y in h downTo 0) {
                if (!col[y]) { depth = -1; continue }
                depth++
                val wy = ISLANDS_LOW + y
                if (hollow[x + 1][z + 1][y] && wy < isletFrom[x][z] && below[y] >= 3 && (depth >= 4 || sink)) { cave[x][z][y] = true; continue }
                mat[x][z][y] = layer(seed, biome, p, q, depth, below[y], wx, wy, wz, n, wy >= isletFrom[x][z], skin[x][z][y])
            }
        }
        // the caves are lined: moss underfoot, and in the walls and roofs the azure prismarine and glowing sea lanterns
        // of the islands' undersides, so they are full of light
        fun open(x: Int, y: Int, z: Int): Boolean = y in 0..h && (if (x in 0..15 && z in 0..15) cave[x][z][y] else hollow[x + 1][z + 1][y])
        for (x in 0..15) for (z in 0..15) for (y in 0..h) {
            var m = mat[x][z][y] ?: continue
            val turf = m == Material.GRASS_BLOCK || (y < h && !solid[x][z][y + 1])           // the land's own top stays as it is
            if (!turf) {
                val r = chance(seed xor 0xCA7EL, bx + x, y, bz + z)
                val theme = caveTheme(seed, bx + x, bz + z)
                if (open(x, y + 1, z)) m = when (theme) {                                    // underfoot
                    1 -> if (r < 0.45) Material.CALCITE else if (r < 0.75) Material.AMETHYST_BLOCK else if (r < 0.9) Material.MOSS_BLOCK else m
                    2 -> if (r < 0.5) Material.PRISMARINE else if (r < 0.72) Material.DARK_PRISMARINE else if (r < 0.9) Material.MOSS_BLOCK else m
                    else -> if (r < 0.82) Material.MOSS_BLOCK else if (r < 0.9) Material.PRISMARINE else m
                }
                else if (open(x, y - 1, z) || open(x + 1, y, z) || open(x - 1, y, z) || open(x, y, z + 1) || open(x, y, z - 1)) m = when (theme) {   // walls and roofs
                    1 -> if (r < 0.07) Material.PEARLESCENT_FROGLIGHT else if (r < 0.5) Material.AMETHYST_BLOCK else if (r < 0.8) Material.CALCITE else m
                    2 -> if (r < 0.09) Material.SEA_LANTERN else if (r < 0.55) Material.PRISMARINE else if (r < 0.78) Material.DARK_PRISMARINE else m
                    else -> if (r < 0.06) Material.SEA_LANTERN else if (r < 0.45) Material.MOSS_BLOCK else if (r < 0.7) Material.PRISMARINE else m
                }
            }
            data.setBlock(x, ISLANDS_LOW + y, z, m)
        }
    }

    /**
     * Haven's earth, from the top down: the biome's own top (grass with patches of moss, sand, mud, golden clay and
     * such), moss instead of dirt, more moss in the first blocks of rock and on the cliff faces, then pearl rock in
     * bands (calcite with diorite veins and a band of the biome's own colour: golden sandstone, azure prismarine or
     * moss), in the glory peaks very rarely raw gold deep inside, and azure prismarine with glowing sea lanterns on the underside you see
     * from below, fading into the rock above it.
     */
    private fun layer(seed: Long, biome: String, p: Double, q: Double, depth: Int, above: Int, x: Int, y: Int, z: Int, n: Noise, islet: Boolean, face: Boolean): Material {
        val r = chance(seed, x, y, z)
        if (depth > 0) {
            if (above <= 1) return if (above == 0 && r < 0.025) Material.SEA_LANTERN else Material.PRISMARINE
            if (above <= 4 && r < (5 - above) / 5.0) return Material.PRISMARINE
        }
        val bare = biome == "glory_peaks" && !islet && y > 152 + p * 8       // over the tree line: bare, pale rock
        if (depth == 0) return when (biome) {
            "glory_peaks" -> when {
                bare -> if (r < 0.0004) Material.RAW_GOLD_BLOCK else if (r < 0.06) Material.QUARTZ_BLOCK else if (q > 0.2) Material.SMOOTH_SANDSTONE else Material.CALCITE
                p < -0.6 -> Material.CALCITE
                q > 0.5 -> Material.MOSS_BLOCK
                else -> Material.GRASS_BLOCK
            }
            "crystal_valley" -> if (p > 0.6) (if (r < 0.35) Material.AMETHYST_BLOCK else Material.CALCITE) else if (q > 0.45) Material.MOSS_BLOCK else Material.GRASS_BLOCK
            "golden_plains" -> if (p > 0.55) Material.SAND else Material.GRASS_BLOCK
            "golden_savanna" -> if (p > 0.55) Material.SAND else if (q > 0.5) Material.YELLOW_TERRACOTTA else Material.GRASS_BLOCK
            "oasis" -> if (p > 0.5) Material.SAND else if (p > 0.38) Material.MUD else Material.GRASS_BLOCK
            "cloud_jungle" -> if (p > 0.6) Material.MUD else if (q > 0.25) Material.MOSS_BLOCK else Material.GRASS_BLOCK
            "golden_forest" -> if (p > 0.62) Material.HONEYCOMB_BLOCK else if (q > 0.5) Material.MOSS_BLOCK else Material.GRASS_BLOCK
            "silver_birch" -> if (p > 0.6) Material.PALE_MOSS_BLOCK else if (q > 0.5) Material.MOSS_BLOCK else Material.GRASS_BLOCK
            "rainbow_falls" -> if (p > 0.6) Material.PRISMARINE else if (q > 0.4) Material.MOSS_BLOCK else Material.GRASS_BLOCK
            "flower_meadow" -> Material.GRASS_BLOCK
            else -> if (q > 0.55) Material.MOSS_BLOCK else Material.GRASS_BLOCK
        }
        if (depth <= 2) return when (biome) {
            "glory_peaks" -> if (bare || p < -0.6) Material.CALCITE else Material.MOSS_BLOCK
            "crystal_valley" -> if (p > 0.6) Material.CALCITE else Material.MOSS_BLOCK
            "golden_plains", "golden_savanna" -> if (p > 0.55) Material.SANDSTONE else Material.MOSS_BLOCK
            "oasis" -> if (p > 0.5) Material.SAND else if (p > 0.38) Material.MUD else Material.MOSS_BLOCK
            "golden_forest" -> if (p > 0.62) Material.HONEYCOMB_BLOCK else Material.MOSS_BLOCK
            else -> Material.MOSS_BLOCK
        }
        if (depth > 10 && biome == "glory_peaks" && r < 1.0 / 25000) return Material.RAW_GOLD_BLOCK      // a rare find
        if (!bare && (depth <= 6 && r < 0.55 || face && r < 0.65)) return Material.MOSS_BLOCK      // cliffs are overgrown, not white
        val v = n.vein.noise(x.toDouble(), y.toDouble(), z.toDouble(), 0.5, 0.5, true)
        return when {
            Math.floorMod(y + (v * 6).toInt(), 12) >= 10 -> when (biome) {
                "glory_peaks", "golden_plains", "golden_savanna", "golden_forest" -> Material.SMOOTH_SANDSTONE
                "crystal_valley", "rainbow_falls", "oasis" -> Material.PRISMARINE
                else -> Material.MOSS_BLOCK
            }
            v > 0.45 -> Material.DIORITE
            v < -0.6 && r < 0.3 -> Material.QUARTZ_BLOCK
            else -> Material.CALCITE
        }
    }

    private fun lerp(t: Double, a: Double, b: Double) = a + (b - a) * t

    override fun getDefaultBiomeProvider(info: WorldInfo): BiomeProvider = Biomes

    override fun getFixedSpawnLocation(world: World, random: Random): Location = Location(world, 0.5, SEA + 1.0, 0.5)

    object Biomes : BiomeProvider() {
        override fun getBiome(info: WorldInfo, x: Int, y: Int, z: Int): Biome = byName[biomeName(info.seed, x, z)] ?: Biome.PLAINS
        override fun getBiomes(info: WorldInfo): List<Biome> = BIOMES.ifEmpty { listOf(Biome.PLAINS) }
    }
}
