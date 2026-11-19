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

    enum class Kind { TEMPLE, CHAPEL, TOWER, PAVILION, HOUSE, BRIDGE, CAVE_HOUSE, SEA_DOME, SEA_HOUSE;
        /** Built on the sea floor by the sea builders: a dry room under water. */
        val wet get() = this == SEA_DOME || this == SEA_HOUSE
    }

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
                if (big) {
                    // an open portal, three wide and four high, so the angels can walk in and out
                    for (x in -1..1) for (y in 1..4) clear(x, y, 0)
                } else {
                    for (y in 1..3) put(0, y, 0, NACRE)
                    door(0, 1, 0)
                }
                for (z in 2 until d step 3) for (y in 2..(h - 3)) { window(-hw, y, z); window(hw, y, z) }
                for (x in -hw + 2 until hw - 1 step 3) if (abs(x) > 1) window(x, 3, 0)
                for (x in -hw..hw step 2) if (abs(x) > 2) flowerBox(x, 1, -1)
                // terrace wall around the roof, and the great dome
                for (x in -hw..hw) { put(x, h + 1, 0, NACRE); put(x, h + 1, d, NACRE) }
                for (z in 0..d) { put(-hw, h + 1, z, NACRE); put(hw, h + 1, z, NACRE) }
                val rad = minOf(hw - 1, d / 2 - 1)
                val open = (rad - 1) * (rad - 1)                   // the temple is open up into its dome, a chapel is not
                for (y in 1..2) for (x in -rad..rad) for (z in -rad..rad) if (x * x + z * z <= rad * rad) {
                    if (big && x * x + z * z <= open) clear(x, h + y, d / 2 + z) else put(x, h + y, d / 2 + z, NACRE)
                }
                if (big) for (x in -rad..rad) for (z in -rad..rad) if (x * x + z * z < open) clear(x, h, d / 2 + z)
                dome(0, h + 3, d / 2, rad)
                // bell tower at the front corner
                val tx = hw + 3
                box(tx - 2, tx + 2, 0, 4, 0, h + 4)
                arches(tx - 2, tx + 2, 0, 4, h + 5, 5)
                for (x in tx - 2..tx + 2) for (z in 0..4) put(x, h + 10, z, NACRE)
                put(tx, h + 8, 2, Material.BELL)
                dome(tx, h + 11, 2, 2)
                if (big) {
                    fun hanging(x: Int, y: Int, z: Int) = put(x, y, z, (Material.LANTERN.createBlockData() as org.bukkit.block.data.type.Lantern).also { it.isHanging = true })
                    val cz = d / 2
                    // a chandelier on a chain from the top of the dome
                    for (y in h until h + 3 + rad) put(0, y, cz, Material.IRON_CHAIN)
                    put(0, h - 1, cz, Material.SEA_LANTERN)
                    hanging(0, h - 2, cz)
                    for ((x, z) in listOf(1 to 0, -1 to 0, 0 to 1, 0 to -1)) { put(x, h - 1, cz + z, Material.QUARTZ_SLAB); hanging(x, h - 2, cz + z) }
                    // two rows of pillars: a nave between them, and along each wall a channel of water
                    val col = hw - 2
                    for (z in 3..d - 6 step 3) for (s in listOf(-1, 1)) {
                        for (y in 1 until h) put(s * col, y, z, Material.QUARTZ_PILLAR)
                        put(s * col, 1, z, Material.CHISELED_QUARTZ_BLOCK)
                    }
                    for (z in 1 until d) for (s in listOf(-1, 1)) { put(s * (hw - 1), -1, z, Material.PRISMARINE_BRICKS); put(s * (hw - 1), 0, z, Material.WATER) }
                    // the water comes down the back wall in two falls (HavenWorld.temple sets these running)
                    for (s in listOf(-1, 1)) { put(s * (hw - 1), h - 2, d - 1, Material.PRISMARINE_BRICKS); put(s * (hw - 1), h - 3, d - 1, Material.WATER) }
                    // the floor: an azure runner up the nave and prismarine set into the pearl
                    for (z in 1..d - 6) {
                        put(0, 1, z, Material.LIGHT_BLUE_CARPET)
                        for (x in -col + 1 until col) if (x != 0 && (abs(x) + z) % 4 == 0) put(x, 0, z, Material.PRISMARINE_BRICKS)
                    }
                    // the altar on its dais: two steps up, the chest on top and a light crystal behind it
                    fun stair(x: Int, y: Int, z: Int) = put(x, y, z, (Material.QUARTZ_STAIRS.createBlockData() as Directional).also { it.facing = BlockFace.SOUTH })
                    for (x in -3..3) { stair(x, 1, d - 5); for (z in d - 4 until d) put(x, 1, z, NACRE) }
                    for (x in -2..2) { stair(x, 2, d - 4); for (z in d - 3 until d) put(x, 2, z, NACRE) }
                    put(0, 3, d - 2, Material.CHEST)
                    put(0, 3, d - 1, Material.CHISELED_QUARTZ_BLOCK)
                    put(0, 4, d - 1, Material.AMETHYST_BLOCK)
                    put(0, 5, d - 1, Material.AMETHYST_CLUSTER)
                    for (s in listOf(-1, 1)) { put(s * 2, 3, d - 1, Material.QUARTZ_PILLAR); put(s * 2, 4, d - 1, Material.SEA_LANTERN) }
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
                for ((x, z) in cols) for (y in 1..5) put(x, y, z, if (y == 5) BOX else NACRE)
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
            Kind.CAVE_HOUSE -> {
                // a dwelling hewn into the rock of a cave: only its front shows. The builder digs out what is in the way.
                val w = 3
                val d = 6 + r.nextInt(3)
                val h = 5
                val trim = Material.PRISMARINE_BRICKS
                box(-w, w, 0, d, 0, h)
                for (x in -w..w) put(x, h, 0, trim)
                for (y in 1 until h) { put(-w, y, 0, trim); put(w, y, 0, trim) }
                door(0, 1, 0)
                window(-2, 2, 0); window(2, 2, 0); window(0, 4, 0)
                for (x in -2..2) put(x, h + 1, 0, DOME)                        // a little azure vault over the front
                for (x in -1..1) put(x, h + 2, 0, DOME)
                // the porch: room to walk up to the door, a step, two lamps and flowers
                for (x in -w..w) for (y in 1..4) for (z in -2..-1) clear(x, y, z)
                for (x in -w..w) put(x, 0, -1, Material.SMOOTH_QUARTZ)
                for (s in listOf(-1, 1)) { put(s * w, 1, -1, Material.QUARTZ_PILLAR); put(s * w, 2, -1, Material.SEA_LANTERN) }
                flowerBox(-2, 1, -1); flowerBox(2, 1, -1)
                // inside: a runner, a lamp, books along the back wall, a workbench and a barrel
                for (z in 1 until d) put(0, 1, z, Material.LIGHT_BLUE_CARPET)
                put(0, h - 1, d / 2, (Material.LANTERN.createBlockData() as org.bukkit.block.data.type.Lantern).also { it.isHanging = true })
                for (x in -2..2) if (x != 0) { put(x, 1, d - 1, Material.BOOKSHELF); put(x, 2, d - 1, Material.BOOKSHELF) }
                put(0, 1, d - 1, Material.CRAFTING_TABLE)
                put(-2, 1, 1, Material.BARREL)
                put(2, 1, 1, Material.CARTOGRAPHY_TABLE)
                for (s in listOf(-1, 1)) { put(s * 2, h, 2, Material.SEA_LANTERN); put(s * 2, h, d - 2, Material.SEA_LANTERN) }
            }
            Kind.SEA_DOME -> {
                // A glass dome on the sea floor: dry inside, the sea all round and overhead. The shell is thick enough
                // that no block of the room touches the water, and a door keeps the water out of the doorway.
                val rad = 5 + r.nextInt(2)
                val cz = rad + 1
                val frame = Material.PRISMARINE_BRICKS
                for (x in -rad - 1..rad + 1) for (z in -rad - 1..rad + 1) {
                    val d = sqrt((x * x + z * z).toDouble())
                    if (d > rad + 0.5) continue
                    put(x, -2, cz + z, Material.PRISMARINE); put(x, -1, cz + z, Material.PRISMARINE)
                    put(x, 0, cz + z, if (d > rad - 0.7) frame else if ((abs(x) == 2 && abs(z) == 2) || (x == 0 && z == 0)) Material.SEA_LANTERN else Material.DARK_PRISMARINE)
                    for (y in 1..rad) {
                        val s = sqrt((x * x + y * y + z * z).toDouble())
                        when {
                            s > rad + 0.5 -> {}
                            s <= rad - 0.7 -> clear(x, y, cz + z)
                            y == 1 || x == 0 || z == 0 -> put(x, y, cz + z, frame)       // a ring at the foot and four ribs
                            else -> put(x, y, cz + z, Material.GLASS)
                        }
                    }
                }
                put(0, rad, cz, Material.SEA_LANTERN)                                    // the lamp in the crown
                // the doorway, framed, with a lamp over it
                for (y in 1..3) for (s in listOf(-1, 1)) put(s, y, 1, frame)
                put(0, 3, 1, Material.SEA_LANTERN)
                door(0, 1, 1)
                clear(0, 1, 2); clear(0, 2, 2)
                // inside: a runner from the door, a round rug, a desk with books at the back, and seats to look out from
                for (z in 2 until cz + rad - 2) put(0, 1, z, Material.CYAN_CARPET)
                for (x in -1..1) for (z in -1..1) if (x != 0 || z != 0) put(x, 1, cz + z, Material.LIGHT_BLUE_CARPET)
                put(0, 1, cz + rad - 2, Material.CARTOGRAPHY_TABLE)
                for (s in listOf(-1, 1)) { put(s, 1, cz + rad - 2, Material.BOOKSHELF); put(s, 2, cz + rad - 2, Material.BOOKSHELF) }
                for (s in listOf(-1, 1)) {
                    put(s * (rad - 2), 1, cz, (Material.DARK_PRISMARINE_STAIRS.createBlockData() as Directional).also { it.facing = if (s < 0) BlockFace.WEST else BlockFace.EAST })
                    put(s * (rad - 2), 1, cz + 1, Material.BARREL)
                }
                // outside: four lamp posts in the water
                for ((x, z) in listOf(-rad - 2 to cz, rad + 2 to cz, -3 to -1, 3 to -1)) {
                    for (y in -1..2) put(x, y, z, frame)
                    put(x, 3, z, Material.SEA_LANTERN)
                }
            }
            Kind.SEA_HOUSE -> {
                // A long room of glass in a prismarine frame, with a glass roof: the panorama house.
                val w = 4
                val d = 9 + r.nextInt(3)
                val h = 5
                val frame = Material.PRISMARINE_BRICKS
                for (x in -w..w) for (z in 0..d) {
                    val edgeX = abs(x) == w
                    val edgeZ = z == 0 || z == d
                    put(x, -2, z, Material.PRISMARINE); put(x, -1, z, Material.PRISMARINE)
                    put(x, 0, z, if (edgeX || edgeZ) frame else if (x == 0 && z % 3 == 0) Material.SEA_LANTERN else Material.DARK_PRISMARINE)
                    for (y in 1 until h) when {
                        edgeX && edgeZ -> put(x, y, z, frame)                             // the corner posts
                        edgeX && z % 4 == 0 -> put(x, y, z, frame)                        // and a post every fourth block
                        edgeX || edgeZ -> put(x, y, z, if (y == 1 && edgeX) frame else Material.GLASS)
                        else -> clear(x, y, z)
                    }
                    put(x, h, z, if (edgeX || edgeZ) frame else if (x == 0) Material.SEA_LANTERN.takeIf { z % 4 == 2 } ?: Material.DARK_PRISMARINE else Material.GLASS)
                }
                for (y in 1..3) for (s in listOf(-1, 1)) put(s, y, 0, frame)
                put(0, 3, 0, Material.SEA_LANTERN)
                door(0, 1, 0)
                // inside: a runner, seats along the glass, a table of books and maps at the far end
                for (z in 1 until d - 1) put(0, 1, z, Material.CYAN_CARPET)
                for (z in 2 until d - 1 step 2) for (s in listOf(-1, 1))
                    put(s * (w - 1), 1, z, (Material.DARK_PRISMARINE_STAIRS.createBlockData() as Directional).also { it.facing = if (s < 0) BlockFace.WEST else BlockFace.EAST })
                put(0, 1, d - 1, Material.CARTOGRAPHY_TABLE)
                for (s in listOf(-1, 1)) { put(s, 1, d - 1, Material.BOOKSHELF); put(s * 2, 1, d - 1, Material.BARREL) }
                // outside: lamps at the corners of the roof and two by the door
                for (sx in listOf(-w, w)) for (z in listOf(0, d)) put(sx, h + 1, z, Material.SEA_LANTERN)
                for (s in listOf(-1, 1)) { for (y in -1..1) put(s * 3, y, -2, frame); put(s * 3, 2, -2, Material.SEA_LANTERN) }
            }
            Kind.BRIDGE -> {
                // span holds the length and, from 1000 up, how much higher or lower the far side lies: len + 1000 * (rise + 50)
                val len = maxOf(6, span % 1000)
                val rise = if (span >= 1000) span / 1000 - 50 else 0
                val hw = if (len < 16) 1 else 2                              // long bridges are five wide
                val crown = (len / 8).coerceIn(2, 6)                         // how high the arch rises in the middle
                fun deck(z: Int): Int { val t = z.toDouble() / len; return (rise * t + 4.0 * crown * t * (1 - t)).roundToInt() }
                fun lantern(x: Int, y: Int, z: Int) = put(x, y, z, (Material.LANTERN.createBlockData() as org.bukkit.block.data.type.Lantern).also { it.isHanging = true })
                for (z in 0..len) {
                    val y = deck(z)
                    val t = z.toDouble() / len
                    // the deck with an azure line down the middle, an azure underside like the islands', and ribs that
                    // grow deeper towards the ends, so it springs from the cliffs in an arch
                    for (x in -hw..hw) { put(x, y, z, if (x == 0) Material.PRISMARINE_BRICKS else NACRE); put(x, y - 1, z, Material.PRISMARINE) }
                    val rib = 1 + (5 * (2 * t - 1) * (2 * t - 1)).roundToInt()
                    for (s in listOf(-1, 1)) {
                        put(s * (hw + 1), y, z, NACRE)
                        for (k in 1..rib) put(s * (hw + 1), y - k, z, if (k == rib) Material.PRISMARINE else NACRE)
                        put(s * (hw + 1), y + 1, z, Material.SMOOTH_QUARTZ_SLAB)                 // a low parapet
                        if (z % 5 == 2 && z < len - 1) {                                        // lamp posts
                            put(s * (hw + 1), y + 1, z, Material.QUARTZ_PILLAR); put(s * (hw + 1), y + 2, z, Material.QUARTZ_PILLAR)
                            put(s * (hw + 1), y + 3, z, Material.LANTERN)
                        }
                    }
                    for (x in -hw..hw) for (yy in y + 1..y + 4) clear(x, yy, z)
                }
                // a gate at each end: two pillars, a beam with a lantern under it and a small azure cap
                if (len >= 12) for (z in listOf(1, len - 1)) {
                    val y = deck(z)
                    for (s in listOf(-1, 1)) for (k in 1..5) put(s * (hw + 1), y + k, z, Material.QUARTZ_PILLAR)
                    for (x in -hw - 1..hw + 1) put(x, y + 6, z, NACRE)
                    for (x in -1..1) put(x, y + 7, z, DOME)
                    put(0, y + 8, z, DOME)
                    lantern(0, y + 5, z)
                }
                // on the long ones a domed canopy in the middle, to stop and look out from
                if (len >= 30) {
                    val zc = len / 2
                    val y = deck(zc)
                    for (s in listOf(-1, 1)) for (dz in listOf(-2, 2)) for (k in 1..5) put(s * (hw + 1), y + k, zc + dz, Material.QUARTZ_PILLAR)
                    for (x in -hw - 1..hw + 1) for (dz in -2..2) put(x, y + 6, zc + dz, NACRE)
                    for (x in -hw..hw) for (dz in -1..1) clear(x, y + 6, zc + dz)
                    dome(0, y + 7, zc, hw + 1, finial = false)
                    lantern(0, y + 7 + hw, zc)
                }
            }
        }
        val parts = out.entries.map { Part(it.key.first, it.key.second, it.key.third, it.value) }
        // a bridge grows out from the cliff it starts on; everything else rises from the ground
        // a sea house is built whole first, and then the water is taken out of it from the top down
        if (kind.wet) return parts.sortedWith(compareBy({ it.block.material.isAir }, { if (it.block.material.isAir) -it.y else it.y }, { abs(it.x) + abs(it.z) }))
        return if (kind == Kind.BRIDGE) parts.sortedWith(compareBy({ it.z }, { it.y }, { abs(it.x) }))
            else parts.sortedWith(compareBy({ it.y }, { abs(it.x) + abs(it.z) }))
    }

    /** How wide and deep the footprint is (half width along x, depth along z), to look for room. */
    fun footprint(kind: Kind): Pair<Int, Int> = when (kind) {
        Kind.TEMPLE -> 12 to 20
        Kind.CHAPEL -> 9 to 13
        Kind.TOWER -> 3 to 5
        Kind.PAVILION -> 6 to 12
        Kind.HOUSE -> 6 to 10
        Kind.BRIDGE -> 2 to 40
        Kind.CAVE_HOUSE -> 4 to 9
        Kind.SEA_DOME -> 8 to 14
        Kind.SEA_HOUSE -> 5 to 12
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
