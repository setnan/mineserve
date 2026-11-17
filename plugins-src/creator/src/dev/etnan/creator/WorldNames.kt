package dev.etnan.creator

import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerCommandPreprocessEvent
import org.bukkit.event.server.ServerCommandEvent

/**
 * New worlds always get lower-case names: `/new Skog`, `/mv create Skog normal` and `mineserve new-world Skog` all make
 * the world `skog` (and with it `skog_nether`, `skog_the_end` and `skog_haven`). Existing worlds keep their names.
 */
class WorldNames : Listener {

    private val create = Regex("""^(/?)((?:multiverse-core:)?mv\s+create|mvcreate|new)(\s+)(\S+)(.*)$""", RegexOption.IGNORE_CASE)

    fun normalise(command: String): String {
        val m = create.matchEntire(command) ?: return command
        val (slash, verb, space, name, rest) = m.destructured
        val lower = name.lowercase()
        return if (lower == name) command else "$slash$verb$space$lower$rest"
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onPlayer(e: PlayerCommandPreprocessEvent) {
        val n = normalise(e.message)
        if (n != e.message) { e.message = n; e.player.sendMessage("World names are lower case: ${create.matchEntire(n)!!.groupValues[4]}") }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    fun onConsole(e: ServerCommandEvent) {
        e.command = normalise(e.command)
    }
}
