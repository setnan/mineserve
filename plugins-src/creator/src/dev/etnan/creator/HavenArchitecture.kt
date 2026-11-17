package dev.etnan.creator

import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.block.data.Directional
import org.bukkit.block.data.Bisected
import org.bukkit.block.data.type.Door
import org.bukkit.block.data.type.GlassPane
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Haven's buildings, inspired by Santorini but taller and grander: white pearl (nacre) walls, azure domes, golden
 * windows, doors and flower boxes. Each kind is drawn from a seed, so a half-built building can be drawn again after
 * a restart and the builder carries on where it stopped.
 *
 * A plan is a list of blocks relative to the origin (the middle of the front edge, on the ground), facing south
 * (+z is into the building), from the bottom up, so it rises the way you would build it.
 */
object HavenArchitecture {

    data class Part(val x: Int, val y: Int, val z: Int, val block: BlockData)

    val NACRE: Material = Material.PEARLESCENT_FROGLIGHT
    val DOME: Material = Material.LIGHT_BLUE_CONCRETE
    val GOLD_WINDOW: Material = Material.YELLOW_STAINED_GLASS_PANE
    val GOLD_DOOR: Material = Material.BAMBOO_DOOR
    val BOX: Material = Material.HONEYCOMB_BLOCK
    val FLOWERS = listOf(Material.CORNFLOWER, Material.LILY_OF_THE_VALLEY, Material.DANDELION, Material.AZURE_BLUET, Material.ALLIUM)

    enum class Kind { TEMPLE, CHAPEL, TOWER, PAVILION, HOUSE, BRIDGE }

    /** The plan of a building. For a bridge, [span] is how far it reaches (along +z). */
    fun plan(kind: Kind, seed: Long, span: Int = 0): List<Part> {
        val r = Random(seed)
        val out = linkedMapOf<Triple<Int, Int, Int>, BlockData>()
        fun put(x: Int, y: Int, z: Int, m: Material) { out[Triple(x, y, z)] = m.createBlockData() }
        fun put(x: Int, y: Int, z: Int, d: BlockData) { out[Triple(x, y, z)] = d }
        fun clear(x: Int, y: Int, z: Int) { out[Triple(x, y, z)] = Material.AIR.createBlockData() }

        /** A hollow white box, x0..x1, z0..z1, floor at y0, walls up to y1, flat roof at y1. */
        fun box(x0: Int, x1: Int, z0: Int, z1: Int, y0: Int, y1: Int) {
            for (x in x0..x1) for (z in z0..z1) { put(x, y0, z, NACRE); put(x, y1, z, NACRE) }
            for (y in y0 + 1 until y1) for (x in x0..x1) for (z in z0..z1) {
                if (x == x0 || x == x1 || z == z0 || z == z1) put(x, y, z, NACRE) else clear(x, y, z)
            }
        }

        fun dome(cx: Int, cy: Int, cz: Int, rad: Int, finial: Boolean = true) {
            for (x in -rad..rad) for (z in -rad..rad) for (y in 0..rad) {
                val d = sqrt((x * x + y * y + z * z).toDouble())
                if (d <= rad + 0.5 && d > rad - 0.7) put(cx + x, cy + y, cz + z, DOME)
            }
            if (finial) { put(cx, cy + rad + 1, cz, Material.GOLD_BLOCK); put(cx, cy + rad + 2, cz, Material.LIGHTNING_ROD) }
        }

        fun door(x: Int, y: Int, z: Int, facing: BlockFace = BlockFace.NORTH) {
            for (half in listOf(Bisected.Half.BOTTOM, Bisected.Half.TOP)) {
                val d = GOLD_DOOR.createBlockData() as Door
                d.facing = facing
                d.half = half
                put(x, y + if (half == Bisected.Half.TOP) 1 else 0, z, d)
            }
        }

        fun window(x: Int, y: Int, z: Int) {
            val d = GOLD_WINDOW.createBlockData() as GlassPane
            put(x, y, z, d)
        }

        fun flowerBox(x: Int, y: Int, z: Int) { put(x, y, z, BOX); put(x, y + 1, z, FLOWERS[r.nextInt(FLOWERS.size)]) }

        fun arches(x0: Int, x1: Int, z0: Int, z1: Int, y0: Int, h: Int) {
            for (y in y0 until y0 + h) for (x in x0..x1) for (z in z0..z1) {
                val edgeX = x == x0 || x == x1
                val edgeZ = z == z0 || z == z1
                val corner = edgeX && edgeZ
                val opening = !corner && y < y0 + h - 1 && ((edgeZ && abs(x - (x0 + x1) / 2) <= (x1 - x0) / 2 - 1) || (edgeX && abs(z - (z0 + z1) / 2) <= (z1 - z0) / 2 - 1))
                if (edgeX || edgeZ) { if (opening) clear(x, y, z) else put(x, y, z, NACRE) } else clear(x, y, z)
            }
        }

        when (kind) {
            Kind.TEMPLE, Kind.CHAPEL -> {
                val big = kind == Kind.TEMPLE
                val hw = if (big) 6 + r.nextInt(2) else 3 + r.nextInt(2)
                val d = if (big) 15 + r.nextInt(4) else 9 + r.nextInt(3)
                val h = if (big) 10 + r.nextInt(3) else 6 + r.nextInt(2)
                box(-hw, hw, 0, d, 0, h)
                for (y in 1..3) put(0, y, 0, NACRE)
                door(0, 1, 0)
                if (big) { door(-1, 1, 0); door(1, 1, 0) }
                for (z in 2 until d step 3) for (y in 2..(h - 3)) { window(-hw, y, z); window(hw, y, z) }
                for (x in -hw + 2 until hw - 1 step 3) if (abs(x) > 1) window(x, 3, 0)
                for (x in -hw..hw step 2) if (abs(x) > 2) flowerBox(x, 1, -1)
                // terrace wall around the roof, and the great dome
                for (x in -hw..hw) { put(x, h + 1, 0, NACRE); put(x, h + 1, d, NACRE) }
                for (z in 0..d) { put(-hw, h + 1, z, NACRE); put(hw, h + 1, z, NACRE) }
                val rad = minOf(hw - 1, d / 2 - 1)
                for (y in 1..2) for (x in -rad..rad) for (z in -rad..rad) if (x * x + z * z <= rad * rad) put(x, h + y, d / 2 + z, NACRE)
                dome(0, h + 3, d / 2, rad)
                // bell tower at the front corner
                val tx = hw + 3
                box(tx - 2, tx + 2, 0, 4, 0, h + 4)
                arches(tx - 2, tx + 2, 0, 4, h + 5, 5)
                for (x in tx - 2..tx + 2) for (z in 0..4) put(x, h + 10, z, NACRE)
                put(tx, h + 8, 2, Material.BELL)
                dome(tx, h + 11, 2, 2)
                if (big) {
                    put(0, 1, d - 2, Material.GOLD_BLOCK)
                    put(0, 2, d - 2, Material.CHEST)
                    for (z in 3 until d - 3 step 4) { put(-hw + 2, 1, z, Material.LANTERN); put(hw - 2, 1, z, Material.LANTERN) }
                }
            }
            Kind.TOWER -> {
                val h = 14 + r.nextInt(8)
                box(-2, 2, 0, 4, 0, h)
                door(0, 1, 0)
                for (y in 4 until h step 4) { window(-2, y, 2); window(2, y, 2); window(0, y, 4) }
                for (y in 1 until h) put(0, y, 2, Material.LADDER.createBlockData().also { (it as Directional).facing = BlockFace.NORTH })
                clear(0, h, 2)
                arches(-2, 2, 0, 4, h + 1, 5)
                for (x in -2..2) for (z in 0..4) put(x, h + 6, z, NACRE)
                put(0, h + 4, 2, Material.BELL)
                dome(0, h + 7, 2, 2)
            }
            Kind.PAVILION -> {
                val rad = 4 + r.nextInt(2)
                for (x in -rad..rad) for (z in 0..rad * 2) if (x * x + (z - rad) * (z - rad) <= rad * rad) put(x, 0, z, NACRE)
                val cols = listOf(-rad + 1 to 1, rad - 1 to 1, -rad + 1 to rad * 2 - 1, rad - 1 to rad * 2 - 1, 0 to 0, 0 to rad * 2, -rad to rad, rad to rad)
                for ((x, z) in cols) for (y in 1..5) put(x, y, z, if (y == 5) Material.GOLD_BLOCK else NACRE)
                for (x in -rad..rad) for (z in 0..rad * 2) {
                    val d2 = x * x + (z - rad) * (z - rad)
                    if (d2 <= rad * rad && d2 >= (rad - 1) * (rad - 1)) put(x, 6, z, NACRE)
                }
                dome(0, 6, rad, rad - 1)
                for ((x, z) in listOf(-2 to rad, 2 to rad, 0 to rad - 2, 0 to rad + 2)) flowerBox(x, 1, z)
            }
            Kind.HOUSE -> {
                val w = 3 + r.nextInt(2)
                val d = 6 + r.nextInt(3)
                val h = 5
                box(-w, w, 0, d, 0, h)
                door(0, 1, 0)
                window(-2, 2, 0); window(2, 2, 0); window(-w, 2, d / 2); window(w, 2, d / 2)
                flowerBox(-2, 1, -1); flowerBox(2, 1, -1)
                // a rounded vault on half the roof, a terrace on the other half with a golden rail
                for (x in -w..w) for (y in 1..2) for (z in d / 2..d) {
                    val dd = sqrt((x * x + (y * 2) * (y * 2)).toDouble())
                    if (dd <= w + 0.5) put(x, h + y, z, if (dd > w - 1.0) DOME else NACRE)
                }
                for (x in -w..w) put(x, h + 1, 0, Material.YELLOW_STAINED_GLASS_PANE)
                for (z in 0 until d / 2) { put(-w, h + 1, z, Material.YELLOW_STAINED_GLASS_PANE); put(w, h + 1, z, Material.YELLOW_STAINED_GLASS_PANE) }
                // steps up to the terrace on the side
                for (i in 0 until h) put(w + 1, i, i + 1, NACRE)
                put(1, h + 1, 2, Material.FLOWER_POT)
            }
            Kind.BRIDGE -> {
                val len = maxOf(4, span)
                for (z in 0..len) {
                    val t = z.toDouble() / len
                    val y = (4.0 * 4 * t * (1 - t)).roundToInt()          // an arch, four blocks up in the middle
                    for (x in -1..1) put(x, y, z, NACRE)
                    put(-2, y + 1, z, NACRE); put(2, y + 1, z, NACRE)
                    if (z % 4 == 0) { put(-2, y + 2, z, Material.LANTERN); put(2, y + 2, z, Material.LANTERN) }
                    for (x in -1..1) for (yy in y + 1..y + 3) clear(x, yy, z)
                }
            }
        }
        return out.entries.map { Part(it.key.first, it.key.second, it.key.third, it.value) }
            .sortedWith(compareBy({ it.y }, { abs(it.x) + abs(it.z) }))
    }

    /** How wide and deep the footprint is (half width along x, depth along z), to look for room. */
    fun footprint(kind: Kind): Pair<Int, Int> = when (kind) {
        Kind.TEMPLE -> 12 to 20
        Kind.CHAPEL -> 9 to 13
        Kind.TOWER -> 3 to 5
        Kind.PAVILION -> 6 to 12
        Kind.HOUSE -> 6 to 10
        Kind.BRIDGE -> 2 to 40
    }

    /** Turns a part to face one of four ways (0 = south, 1 = west, 2 = north, 3 = east). */
    fun turn(p: Part, rot: Int): Part {
        val (x, z) = when (rot and 3) { 0 -> p.x to p.z; 1 -> -p.z to p.x; 2 -> -p.x to -p.z; else -> p.z to -p.x }
        val d = p.block.clone()
        if (rot and 3 != 0) d.rotate(when (rot and 3) {
            1 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_90
            2 -> org.bukkit.block.structure.StructureRotation.CLOCKWISE_180
            else -> org.bukkit.block.structure.StructureRotation.COUNTERCLOCKWISE_90 })
        return Part(x, p.y, z, d)
    }
}
