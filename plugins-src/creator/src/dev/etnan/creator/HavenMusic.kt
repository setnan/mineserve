package dev.etnan.creator

import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Sound
import org.bukkit.entity.Player
import java.util.UUID
import kotlin.math.pow

/**
 * Short musical motifs for Haven's special moments, played with note block sounds (no files): arriving through a
 * portal, a wish at the well, coming near a temple, the Lion appearing and the Lion arriving where it wanted to take
 * you. Notes are counted like note blocks: 0 is F#3, 12 is F#4, 24 is F#5.
 */
class HavenMusic(private val world: HavenWorld) {

    /** One note: when (ticks from the start), on what, which note, how loud. */
    data class Note(val at: Int, val sound: Sound, val n: Int, val volume: Float = 0.8f)

    enum class Motif(val notes: List<Note>) {
        /** Through the portal into Haven: a rising, open arpeggio, a bell at the top, then a shimmer. */
        ARRIVAL(listOf(
            Note(0, Sound.BLOCK_NOTE_BLOCK_CHIME, 8), Note(4, Sound.BLOCK_NOTE_BLOCK_CHIME, 12), Note(8, Sound.BLOCK_NOTE_BLOCK_CHIME, 15),
            Note(12, Sound.BLOCK_NOTE_BLOCK_CHIME, 20), Note(12, Sound.BLOCK_NOTE_BLOCK_BELL, 8, 0.6f),
            Note(18, Sound.BLOCK_NOTE_BLOCK_CHIME, 24, 0.5f), Note(18, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 18, 0.6f))),
        /** A wish at the well: a sparkle falling down the harp and a bright note to close it. */
        WELL(listOf(
            Note(0, Sound.BLOCK_NOTE_BLOCK_HARP, 20), Note(3, Sound.BLOCK_NOTE_BLOCK_HARP, 17), Note(6, Sound.BLOCK_NOTE_BLOCK_HARP, 15),
            Note(9, Sound.BLOCK_NOTE_BLOCK_HARP, 12), Note(14, Sound.BLOCK_NOTE_BLOCK_PLING, 24, 0.6f), Note(14, Sound.BLOCK_NOTE_BLOCK_CHIME, 20, 0.5f))),
        /** Near a temple: three slow bell chords, rising, with a flute over them. */
        TEMPLE(listOf(
            Note(0, Sound.BLOCK_NOTE_BLOCK_BELL, 8, 0.5f), Note(0, Sound.BLOCK_NOTE_BLOCK_BELL, 12, 0.5f), Note(0, Sound.BLOCK_NOTE_BLOCK_BELL, 15, 0.5f),
            Note(0, Sound.BLOCK_NOTE_BLOCK_FLUTE, 20, 0.5f),
            Note(16, Sound.BLOCK_NOTE_BLOCK_BELL, 10, 0.5f), Note(16, Sound.BLOCK_NOTE_BLOCK_BELL, 13, 0.5f), Note(16, Sound.BLOCK_NOTE_BLOCK_BELL, 17, 0.5f),
            Note(16, Sound.BLOCK_NOTE_BLOCK_FLUTE, 22, 0.5f),
            Note(32, Sound.BLOCK_NOTE_BLOCK_BELL, 12, 0.5f), Note(32, Sound.BLOCK_NOTE_BLOCK_BELL, 15, 0.5f), Note(32, Sound.BLOCK_NOTE_BLOCK_BELL, 20, 0.5f),
            Note(32, Sound.BLOCK_NOTE_BLOCK_FLUTE, 24, 0.5f))),
        /** The Lion is near: a low, warm call and an answer above it. */
        LION_NEAR(listOf(
            Note(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 8, 0.7f), Note(8, Sound.BLOCK_NOTE_BLOCK_FLUTE, 15, 0.6f),
            Note(14, Sound.BLOCK_NOTE_BLOCK_FLUTE, 20, 0.6f))),
        /** The Lion has brought you there: the lion's theme, low and mighty, then bright, a fanfare of bells. */
        LION(listOf(
            Note(0, Sound.BLOCK_NOTE_BLOCK_BASS, 8, 1f), Note(0, Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO, 8, 0.6f),
            Note(6, Sound.BLOCK_NOTE_BLOCK_BELL, 8), Note(12, Sound.BLOCK_NOTE_BLOCK_BELL, 15),
            Note(18, Sound.BLOCK_NOTE_BLOCK_BELL, 20), Note(18, Sound.BLOCK_NOTE_BLOCK_BASS, 8, 1f),
            Note(26, Sound.BLOCK_NOTE_BLOCK_BELL, 17), Note(30, Sound.BLOCK_NOTE_BLOCK_BELL, 20),
            Note(36, Sound.BLOCK_NOTE_BLOCK_CHIME, 24, 0.6f), Note(36, Sound.BLOCK_NOTE_BLOCK_BELL, 20), Note(36, Sound.BLOCK_NOTE_BLOCK_BASS, 8, 1f))),
    }

    private val templeHeard = mutableMapOf<UUID, Long>()

    init {
        instance = this
        Bukkit.getScheduler().runTaskTimer(Haven.plugin, Runnable { temples() }, 100L, 40L)
    }

    /** Coming within 28 blocks of a temple (or another great building): the temple chords, once in ten minutes. */
    private fun temples() {
        val now = System.currentTimeMillis()
        for (w in Haven.worlds) {
            val marks = world.landmarks(w)
            if (marks.isEmpty()) continue
            for (p in w.players) {
                if ((templeHeard[p.uniqueId] ?: 0) > now - 10 * 60_000) continue
                val near = marks.any { (x, z) -> val dx = p.location.x - x; val dz = p.location.z - z; dx * dx + dz * dz < 28.0 * 28 }
                if (near) { templeHeard[p.uniqueId] = now; play(p, Motif.TEMPLE) }
            }
        }
    }

    companion object {
        var instance: HavenMusic? = null

        private fun pitch(n: Int) = 2.0.pow((n - 12) / 12.0).toFloat()

        /** Only this player hears it. */
        fun play(p: Player, m: Motif) = schedule(m) { note -> if (p.isOnline) p.playSound(p.location, note.sound, note.volume, pitch(note.n)) }

        /** Everyone near the place hears it. */
        fun play(at: Location, m: Motif) = schedule(m) { note -> at.world.playSound(at, note.sound, note.volume * 1.5f, pitch(note.n)) }

        private fun schedule(m: Motif, sound: (Note) -> Unit) {
            for ((t, notes) in m.notes.groupBy { it.at })
                Bukkit.getScheduler().runTaskLater(Haven.plugin, Runnable { notes.forEach(sound) }, t.toLong().coerceAtLeast(1L))
        }
    }
}
