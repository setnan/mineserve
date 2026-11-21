package dev.etnan.creator

import io.papermc.paper.command.brigadier.BasicCommand
import io.papermc.paper.command.brigadier.Commands
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.format.NamedTextColor
import org.bukkit.Bukkit
import org.bukkit.Difficulty
import org.bukkit.GameMode
import org.bukkit.Keyed
import org.bukkit.Location
import org.bukkit.Registry
import org.bukkit.World
import org.bukkit.attribute.Attribute
import org.bukkit.configuration.file.YamlConfiguration
import org.bukkit.entity.Player
import org.bukkit.event.Event
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockEvent
import org.bukkit.event.entity.EntityEvent
import org.bukkit.event.entity.EntityTeleportEvent
import org.bukkit.event.hanging.HangingEvent
import org.bukkit.event.inventory.InventoryEvent
import org.bukkit.event.inventory.InventoryMoveItemEvent
import org.bukkit.event.inventory.InventoryPickupItemEvent
import org.bukkit.event.inventory.PrepareItemCraftEvent
import org.bukkit.event.player.PlayerChangedWorldEvent
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.player.PlayerEvent
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.event.player.PlayerKickEvent
import org.bukkit.event.player.PlayerQuitEvent
import org.bukkit.event.player.PlayerTeleportEvent
import org.bukkit.event.server.ServerCommandEvent
import org.bukkit.event.vehicle.VehicleEvent
import org.bukkit.event.weather.WeatherEvent
import org.bukkit.event.world.WorldEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.RegisteredListener
import org.bukkit.plugin.java.JavaPlugin
import java.io.File

/**
 * Pure worlds: plain Minecraft with no cheats, whatever happens. MC_PURE_WORLDS in .env lists them ("globus" if it is
 * not set); each is a family of three (the world, <world>_nether and <world>_the_end, no Haven) with an inventory
 * group of its own, so nothing is carried in or out. The plugin makes them the first time it starts with the name.
 *
 * In a pure world:
 *  - nothing of this plugin happens: every one of its event listeners is deaf there (see [deafen]), its loops over
 *    the players leave out the players there ([players]), nobody has a power or a helper there, and nothing of the
 *    plugin or the datapacks can be crafted;
 *  - no commands, for operators either, except chat and the commands that take you to another world. /<world>
 *    (/globus) brings you back to where you left it;
 *  - everybody is in survival, cannot fly, can be hurt and has the size, speed and reach of a player: checked four
 *    times a second, so a game mode set from the console does not last;
 *  - the console and RCON (the chat bridge, mc.sh) cannot run a command that names the world or a player in it,
 *    except to look, talk, moderate and manage the world with Multiverse.
 *
 * The one setting players have: /peaceful and /hard switch the difficulty of the family (it starts peaceful).
 *
 * Not covered: a console command with a selector (@a, @e) is not looked into.
 */
object Pure {

    @JvmStatic
    val names: Set<String> = (System.getenv("MC_PURE_WORLDS") ?: "globus").split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toSet()

    /** The pure world a world name belongs to (itself, or the world of this nether or end), or null. */
    @JvmStatic
    fun root(name: String?): String? {
        val n = name?.lowercase() ?: return null
        return names.firstOrNull { n == it || n == it + "_nether" || n == it + "_the_end" }
    }

    @JvmStatic fun isPure(w: World?): Boolean = root(w?.name) != null

    /** The players this plugin may do things to: everybody who is not in a pure world. */
    @JvmStatic fun players(): List<Player> = Bukkit.getOnlinePlayers().filter { !isPure(it.world) }

    fun family(root: String): List<World> = listOf(root, root + "_nether", root + "_the_end").mapNotNull { Bukkit.getWorld(it) }

    /** Is this event about a pure world? Then the plugin does not hear it. Coming, going and leaving are always heard. */
    fun skips(e: Event): Boolean = when (e) {
        is PlayerJoinEvent, is PlayerQuitEvent, is PlayerKickEvent, is PlayerChangedWorldEvent -> false
        is PlayerTeleportEvent -> isPure(e.from.world) && isPure(e.to.world)
        is PlayerEvent -> isPure(e.player.world)
        is EntityTeleportEvent -> isPure(e.from.world) && (e.to == null || isPure(e.to!!.world))
        is EntityEvent -> isPure(e.entity.world)
        is BlockEvent -> isPure(e.block.world)
        is WorldEvent -> isPure(e.world)
        is InventoryEvent -> isPure(e.view.player.world)
        is InventoryMoveItemEvent -> isPure(e.source.location?.world)
        is InventoryPickupItemEvent -> isPure(e.item.world)
        is VehicleEvent -> isPure(e.vehicle.world)
        is HangingEvent -> isPure(e.entity.world)
        is WeatherEvent -> isPure(e.world)
        else -> false
    }

    private class Deaf(val inner: RegisteredListener) :
        RegisteredListener(inner.listener, { _, _ -> }, inner.priority, inner.plugin, inner.isIgnoringCancelled) {
        override fun callEvent(event: Event) { if (!skips(event)) inner.callEvent(event) }
    }

    /**
     * Makes every listener of the plugin deaf in the pure worlds: each registered handler is swapped for one that
     * drops the events of those worlds and hands on the rest. Run again after anything registers more listeners.
     */
    @JvmStatic
    fun deafen(plugin: JavaPlugin) {
        for (list in HandlerList.getHandlerLists()) for (rl in list.registeredListeners) {
            if (rl.plugin !== plugin || rl is Deaf || rl.listener is PureGuard) continue
            list.unregister(rl)
            list.register(Deaf(rl))
        }
    }
}

class PureGuard(private val plugin: JavaPlugin) : Listener {

    private val file = File(plugin.dataFolder, "pure.yml")
    private val conf = YamlConfiguration.loadConfiguration(file)
    private val ownKey = org.bukkit.NamespacedKey(plugin, "pure_own")             // what they were before they came in

    /** Commands a player may use in a pure world: chat, and the ones that only take you to another world. */
    private val allowed = mutableSetOf("msg", "tell", "w", "r", "me", "list", "help", "peaceful", "hard", "mvtp")

    /** What a player is, in plain Minecraft: anything else is put back. */
    private val plain = mapOf(
        Attribute.SCALE to 1.0, Attribute.STEP_HEIGHT to 0.6, Attribute.JUMP_STRENGTH to 0.42, Attribute.GRAVITY to 0.08,
        Attribute.SAFE_FALL_DISTANCE to 3.0, Attribute.FALL_DAMAGE_MULTIPLIER to 1.0, Attribute.BLOCK_INTERACTION_RANGE to 4.5,
        Attribute.ENTITY_INTERACTION_RANGE to 3.0, Attribute.MOVEMENT_SPEED to 0.1, Attribute.MAX_HEALTH to 20.0,
        Attribute.ATTACK_DAMAGE to 1.0, Attribute.BLOCK_BREAK_SPEED to 1.0, Attribute.ARMOR to 0.0, Attribute.ARMOR_TOUGHNESS to 0.0,
        Attribute.KNOCKBACK_RESISTANCE to 0.0, Attribute.LUCK to 0.0, Attribute.ATTACK_SPEED to 4.0)

    init {
        // the aliases of commands.yml that only go to a world (/go <world>, /hope, /superworld ...)
        runCatching {
            val aliases = YamlConfiguration.loadConfiguration(File("commands.yml")).getConfigurationSection("aliases")
            aliases?.getKeys(false)?.forEach { k ->
                val cmds = aliases.getStringList(k)
                if (cmds.isNotEmpty() && cmds.all { it.startsWith("mvtp ") }) allowed.add(k.lowercase())
            }
        }
        Bukkit.getScheduler().runTask(plugin, Runnable { Pure.deafen(plugin); worlds() })       // Multiverse has loaded its worlds by now
        Bukkit.getScheduler().runTaskLater(plugin, Runnable { Pure.deafen(plugin) }, 100L)
        Bukkit.getScheduler().runTaskTimer(plugin, Runnable { keep() }, 20L, 5L)
        plugin.logger.info("Pure worlds (no cheats): ${Pure.names.joinToString()}")
    }

    // --- The worlds --------------------------------------------------------------------------------------

    /** Makes the worlds that are missing, gives each family its inventory group, and sets a new family up once. */
    private fun worlds() {
        var waiting = false
        for (root in Pure.names) {
            for ((name, env) in listOf(root to "normal", root + "_nether" to "nether", root + "_the_end" to "the_end")) {
                if (Bukkit.getWorld(name) != null) continue
                waiting = true
                if (conf.getBoolean("asked.$name")) continue
                conf.set("asked.$name", true)
                plugin.logger.info("Pure: creating $name")
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create $name $env")
            }
            val family = Pure.family(root)
            if (family.size == 3 && !conf.getBoolean("ready.$root")) {
                conf.set("ready.$root", true)
                for (w in family) {
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify ${w.name} set gamemode survival")
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify ${w.name} set allow-flight false")
                }
                difficulty(root, Difficulty.PEACEFUL)
                plugin.logger.info("Pure: $root is ready (survival, peaceful; /hard and /peaceful switch)")
            }
            if (family.isNotEmpty()) HavenWorlds.group(family[0], *family.drop(1).toTypedArray())
        }
        runCatching { conf.save(file) }
        if (waiting) Bukkit.getScheduler().runTaskLater(plugin, Runnable { worlds() }, 100L)
    }

    private fun difficulty(root: String, d: Difficulty) {
        for (w in Pure.family(root)) {
            w.difficulty = d
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv modify ${w.name} set difficulty ${d.name.lowercase()}")   // Multiverse keeps it over a restart
        }
    }

    fun register(r: Commands) {
        for (d in listOf(Difficulty.PEACEFUL, Difficulty.HARD)) r.register(d.name.lowercase(),
            "Sets a world without cheats (${Pure.names.joinToString()}) to ${d.name.lowercase()}", BasicCommand { src, args ->
                val root = Pure.root((src.executor as? Player)?.world?.name) ?: Pure.root(args.firstOrNull())
                    ?: return@BasicCommand src.sender.sendMessage("Only in a world without cheats: ${Pure.names.joinToString()}")
                difficulty(root, d)
                val msg = Component.text("$root is now ${d.name.lowercase()}" + if (d == Difficulty.PEACEFUL) ": no monsters" else ": monsters, and they hit hard",
                    if (d == Difficulty.PEACEFUL) NamedTextColor.GREEN else NamedTextColor.RED)
                Pure.family(root).forEach { w -> w.players.forEach { it.sendMessage(msg) } }
                plugin.logger.info("Pure: ${src.sender.name} set $root to ${d.name.lowercase()}")
            })
    }

    // --- Coming and going --------------------------------------------------------------------------------

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onTeleport(e: PlayerTeleportEvent) {
        val from = Pure.root(e.from.world?.name) ?: return
        if (Pure.root(e.to.world?.name) == from) return
        val l = e.from                                                         // leaving: remember where, for the way back
        e.player.persistentDataContainer.set(org.bukkit.NamespacedKey(plugin, "pure_last_$from"), PersistentDataType.STRING,
            "${l.world.name};${l.x};${l.y};${l.z};${l.yaw};${l.pitch}")
    }

    private fun back(p: Player, root: String): Location? {
        val f = p.persistentDataContainer.get(org.bukkit.NamespacedKey(plugin, "pure_last_$root"), PersistentDataType.STRING)?.split(";") ?: return null
        if (f.size != 6) return null
        val w = Bukkit.getWorld(f[0])?.takeIf { Pure.root(it.name) == root } ?: return null
        return runCatching { Location(w, f[1].toDouble(), f[2].toDouble(), f[3].toDouble(), f[4].toFloat(), f[5].toFloat()) }.getOrNull()
    }

    @EventHandler
    fun onWorld(e: PlayerChangedWorldEvent) {
        val p = e.player
        val was = Pure.isPure(e.from)
        val now = Pure.isPure(p.world)
        if (now && !was) { remember(p); plain(p); p.sendMessage(Component.text("${Pure.root(p.world.name)}: plain Minecraft, no cheats and no commands. /peaceful and /hard set the difficulty.", NamedTextColor.GOLD)) }
        if (was && !now) restore(p)
    }

    @EventHandler
    fun onJoin(e: PlayerJoinEvent) { if (Pure.isPure(e.player.world)) plain(e.player) }

    /** What the player was outside (the attribute values other features gave them), kept for when they go back. */
    private fun remember(p: Player) {
        val own = plain.keys.mapNotNull { a -> p.getAttribute(a)?.let { "${a.key.key}=${it.baseValue}" } }
        p.persistentDataContainer.set(ownKey, PersistentDataType.STRING, own.joinToString(";"))
    }

    private fun restore(p: Player) {
        val own = p.persistentDataContainer.get(ownKey, PersistentDataType.STRING) ?: return
        p.persistentDataContainer.remove(ownKey)
        for (f in own.split(";")) {
            val (k, v) = f.split("=").takeIf { it.size == 2 } ?: continue
            val a = plain.keys.firstOrNull { it.key.key == k } ?: continue
            p.getAttribute(a)?.baseValue = v.toDoubleOrNull() ?: continue
        }
    }

    // --- No cheats ---------------------------------------------------------------------------------------

    private fun keep() { for (p in Bukkit.getOnlinePlayers()) if (Pure.isPure(p.world)) plain(p) }

    /** Survival, on the ground, mortal, and the size, speed and reach of a player. */
    private fun plain(p: Player) {
        if (p.gameMode != GameMode.SURVIVAL) p.gameMode = GameMode.SURVIVAL
        if (p.allowFlight) { p.isFlying = false; p.allowFlight = false }
        if (p.isInvulnerable) p.isInvulnerable = false
        if (p.flySpeed != 0.1f) p.flySpeed = 0.1f
        if (p.walkSpeed != 0.2f) p.walkSpeed = 0.2f
        for (a in Registry.ATTRIBUTE) {
            val inst = p.getAttribute(a) ?: continue
            for (m in inst.modifiers) if (m.key.namespace != "minecraft") inst.removeModifier(m)
            val base = plain[a] ?: continue
            if (inst.baseValue != base) inst.baseValue = base
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    fun onCommand(e: PlayerCommandPreprocessEvent) {
        val p = e.player
        val label = e.message.removePrefix("/").trim().substringBefore(' ').substringAfter(':').lowercase()
        if (label in Pure.names) {                                             // /globus: into the world, back where you left it
            e.isCancelled = true
            if (Pure.root(p.world.name) == label) return p.sendMessage(Component.text("You are in $label", NamedTextColor.GRAY))
            val w = Bukkit.getWorld(label) ?: return p.sendMessage(Component.text("$label is not loaded yet", NamedTextColor.RED))
            p.teleportAsync(back(p, label) ?: w.spawnLocation)
            return
        }
        if (!Pure.isPure(p.world) || label in allowed) return
        e.isCancelled = true
        p.sendMessage(Component.text("No commands in ${Pure.root(p.world.name)}: this world has no cheats. Go to another world first (/go <world>).", NamedTextColor.RED))
    }

    /** Nothing of the plugin or the datapacks is crafted in a pure world: only Minecraft's own recipes. */
    @EventHandler(priority = EventPriority.HIGHEST)
    fun onCraft(e: PrepareItemCraftEvent) {
        if (!Pure.isPure(e.view.player.world)) return
        val key = (e.recipe as? Keyed)?.key ?: return
        if (key.namespace != "minecraft") e.inventory.result = null
    }

    private val looking = setOf("data", "tell", "tellraw", "msg", "w", "say", "title", "bossbar", "kick", "ban", "pardon", "op", "deop",
        "whitelist", "list", "mv", "mvtp", "mvinv", "lp", "peaceful", "hard")

    /**
     * The console and RCON: a command that names a pure world or a player who is in one is not run, unless it only
     * looks (data get), talks, moderates or manages the world with Multiverse.
     */
    @EventHandler(priority = EventPriority.LOWEST)
    fun onRcon(e: org.bukkit.event.server.RemoteServerCommandEvent) = onConsole(e)     // RCON has a handler list of its own

    @EventHandler(priority = EventPriority.LOWEST)
    fun onConsole(e: ServerCommandEvent) {
        val cmd = e.command.trim().removePrefix("/")
        val words = cmd.split(Regex("\\s+"))
        val first = words.firstOrNull()?.substringAfter(':')?.lowercase() ?: return
        if (first in looking && !(first == "data" && words.getOrNull(1) != "get")) return
        val low = cmd.lowercase()
        fun named(n: String) = Regex("(?<![a-z0-9_])" + Regex.escape(n.lowercase()) + "(?![a-z0-9_])").containsMatchIn(low)
        val what = Pure.names.firstOrNull { named(it) || named(it + "_nether") || named(it + "_the_end") }
            ?: Bukkit.getOnlinePlayers().firstOrNull { Pure.isPure(it.world) && named(it.name) }?.let { "${it.name} (in ${Pure.root(it.world.name)})" }
            ?: return
        e.isCancelled = true
        e.sender.sendMessage("Not run: $what is in a world without cheats")
        plugin.logger.info("Pure: stopped a console command about $what: ${cmd.take(80)}")
    }
}
