package dev.etnan.creator

import net.kyori.adventure.util.TriState
import org.bukkit.Bukkit
import org.bukkit.GameRule
import org.bukkit.World
import org.bukkit.WorldCreator
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.WorldLoadEvent
import java.io.File
import kotlin.math.PI
import kotlin.math.sin

/**
 * Every world has its own Haven, the way it has its own nether and end: <world>_haven, made the first time the world
 * is loaded, also for worlds created later. Each Haven gets a seed of its own (kept in plugins/Creator/haven-worlds.yml)
 * and joins the Multiverse-Inventories group of its world, so you keep your inventory going there and back.
 *
 * It also keeps the sun up: time stands still and the plugin swings the sun slowly from noon to a sunset glow, back
 * over noon to a sunrise glow and back again, a full swing in 20 minutes. Things grow twice as fast (random tick
 * speed 6).
 */
class HavenWorlds : Listener {

    private val file = File(Haven.plugin.dataFolder, "haven-worlds.yml")
    private val seeds = mutableMapOf<String, Long>()

    init {
        instance = this
        if (file.exists()) YamlConfiguration.loadConfiguration(file).let { y ->
            y.getConfigurationSection("seeds")?.getKeys(false)?.forEach { seeds[it] = y.getLong("seeds.$it") } }
        // after every plugin is enabled and Multiverse has loaded its worlds
        Bukkit.getScheduler().runTask(Haven.plugin, Runnable { Bukkit.getWorlds().toList().forEach { ensure(it) } })
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { sun() }, 1L, 1L)
    }

    companion object {
        private var instance: HavenWorlds? = null
        /** Puts the members into the inventory group of home (making one if it has none): for families without a Haven too. */
        fun group(home: World, vararg members: World) { instance?.sort(home, *members) }
    }

    private fun isHome(w: World) = !Haven.isHaven(w) && !Pure.isPure(w) && w.environment == World.Environment.NORMAL &&
        !w.name.endsWith("_nether") && !w.name.endsWith("_the_end")

    @EventHandler
    fun onLoad(e: WorldLoadEvent) {
        val w = e.world
        if (Haven.isHaven(w)) { rules(w); return }
        if (w.name.endsWith("_nether") || w.name.endsWith("_the_end")) {
            Bukkit.getScheduler().runTask(Haven.plugin, Runnable { Bukkit.getWorld(Haven.root(w))?.let { sort(it, w) } })
            return
        }
        if (isHome(w)) Bukkit.getScheduler().runTask(Haven.plugin, Runnable { if (w.isValidWorld()) ensure(w) })
    }

    private fun World.isValidWorld() = Bukkit.getWorld(uid) != null

    /** The Haven of a home world, made (or loaded) if it is not there yet. */
    fun ensure(home: World): World? {
        if (!isHome(home)) return null
        val name = home.name + "_haven"
        val haven = Bukkit.getWorld(name) ?: run {
            val seed = seeds[name.lowercase()] ?: java.util.Random().nextLong().also { seeds[name.lowercase()] = it; save() }
            val w = WorldCreator(name).environment(World.Environment.NORMAL).seed(seed).generator(HavenGenerator())
                .generateStructures(false).keepSpawnLoaded(TriState.FALSE).createWorld() ?: return null
            Haven.plugin.logger.info("Haven: ${w.name} for ${home.name} (seed $seed): ${HavenGenerator.survey(seed)}")
            w
        }
        rules(haven)
        sort(home, haven)
        return haven
    }

    private fun rules(w: World) {
        w.setGameRule(org.bukkit.GameRules.ADVANCE_TIME, false)
        w.setGameRule(org.bukkit.GameRules.ADVANCE_WEATHER, false)
        w.setGameRule(org.bukkit.GameRules.RANDOM_TICK_SPEED, 6)
        w.setStorm(false)
        w.isThundering = false
    }

    /**
     * Keeps every world family in one Multiverse-Inventories group: the world, its nether, its end and its Haven share
     * everything, and no two families share. A family without a group gets its own (named after the world, sharing
     * all); a nether, end or Haven made later joins its world's group. By reflection: no hard dependency.
     */
    private fun sort(home: World, vararg members: World) {
        val inv = Bukkit.getPluginManager().getPlugin("Multiverse-Inventories") ?: return
        runCatching {
            val cl = inv.javaClass.classLoader
            val api = cl.loadClass("org.mvplugins.multiverse.inventories.MultiverseInventoriesApi").getMethod("get").invoke(null)
            val mgrType = cl.loadClass("org.mvplugins.multiverse.inventories.profile.group.WorldGroupManager")
            val groupType = cl.loadClass("org.mvplugins.multiverse.inventories.profile.group.WorldGroup")
            val sharesType = cl.loadClass("org.mvplugins.multiverse.inventories.share.Shares")
            val mgr = api.javaClass.getMethod("getWorldGroupManager").invoke(api)
            var groups = (mgrType.getMethod("getGroupsForWorld", String::class.java).invoke(mgr, home.name) as List<*>).filterNotNull()
            if (groups.isEmpty()) {
                val name = home.name.lowercase()
                val g = mgrType.getMethod("getGroup", String::class.java).invoke(mgr, name)
                    ?: mgrType.getMethod("newEmptyGroup", String::class.java).invoke(mgr, name)
                val all = cl.loadClass("org.mvplugins.multiverse.inventories.share.Sharables").getMethod("allOf").invoke(null)
                sharesType.getMethod("mergeShares", sharesType).invoke(groupType.getMethod("getShares").invoke(g), all)
                for (n in listOf(home.name, home.name + "_nether", home.name + "_the_end"))
                    if (Bukkit.getWorld(n) != null) groupType.getMethod("addWorld", String::class.java).invoke(g, n)
                mgrType.getMethod("updateGroup", groupType).invoke(mgr, g)
                Haven.plugin.logger.info("Haven: new inventory group $name for ${home.name} (shares all)")
                groups = listOf(g)
            }
            for (g in groups) for (w in members) {
                val has = groupType.getMethod("getConfigWorlds").invoke(g) as Set<*>
                val applicable = groupType.getMethod("getApplicableWorlds").invoke(g) as Set<*>
                if ((has + applicable).any { it.toString().equals(w.name, true) }) continue
                groupType.getMethod("addWorld", String::class.java).invoke(g, w.name)
                mgrType.getMethod("updateGroup", groupType).invoke(mgr, g)
                Haven.plugin.logger.info("Haven: ${w.name} joins the inventory group ${groupType.getMethod("getName").invoke(g)}")
            }
        }.onFailure { Haven.plugin.logger.warning("Haven: could not sort ${members.joinToString { it.name }} into an inventory group: $it") }
    }

    /** Noon at 6000, the sunset glow at 12400, the sunrise glow at 23600 (-400): a slow swing between them. */
    private fun sun() {
        val phase = (System.currentTimeMillis() / 50 % 24000) / 24000.0
        val t = (6000 + 6400 * sin(2 * PI * phase)).toLong()
        val time = ((t % 24000) + 24000) % 24000
        for (w in Haven.worlds) if (w.time != time) w.time = time
    }

    private fun save() {
        val y = YamlConfiguration()
        seeds.forEach { (k, v) -> y.set("seeds.$k", v) }
        y.save(file)
    }
}
