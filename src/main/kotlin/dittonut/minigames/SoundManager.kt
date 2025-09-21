package dittonut.minigames

import cz.koca2000.nbs4j.Song
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit
import kotlin.math.pow


lateinit var songs: Map<String, Song>
    private set

private val songsDir: File by lazy {
    File(plugin.dataFolder, config.songsDir) //TODO: auto copy
}

data class SongPlayback(
    val id: String,
    val song: Song,
    var currentTick: Long
)

object SoundManager {
    val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

    private val activeSongs = mutableMapOf<Player, MutableList<SongPlayback>>()

    fun stopSound(player: Player, id: String? = null) {
        executor.execute {
            if (id == null) activeSongs.remove(player)
            else activeSongs[player]?.removeIf { it.id == id }
        }
    }

    fun playSound(id: String, to: List<Player>, endCallback: () -> Unit = {}) {
        val song = songs[id]
        song ?: return

        val playback = SongPlayback(id, song, -1L)

        fun scheduleNextTick() {
            val nextTick = song.getNextNonEmptyTick(playback.currentTick.toInt())
            if (nextTick < 0) {
                to.forEach { activeSongs[it]?.remove(playback) }
                endCallback()
                return
            }

            val delayMs = ((nextTick - playback.currentTick) * 1000f
                    / song.getTempo(playback.currentTick.toInt())).toLong()

            executor.schedule({
                val activePlayers = to.filter { activeSongs[it]?.contains(playback) == true }

                if (activePlayers.isEmpty()) {
                    endCallback()
                    return@schedule
                }

                activePlayers.forEach { player ->
                    song.layers.forEach { layer ->
                        val note = layer.notes[nextTick] ?: return@forEach

                        player.playSound(
                            player,
                            toSound(note.instrument),
                            SoundCategory.MASTER,
                            note.volume / 100f,
                            toPitch(note.key)?.toFloat() ?: return@forEach,
                            0L
                        )
                    }
                }


                playback.currentTick = nextTick.toLong()
                scheduleNextTick()
            }, delayMs, TimeUnit.MILLISECONDS)
        }

        executor.execute {
            to.forEach { it ->
                activeSongs.computeIfAbsent(it) { mutableListOf() }.add(playback)
            }
            scheduleNextTick()
        }
    }

    fun playSound(id: String, to: Player, endCallback: () -> Unit = {}) = playSound(id, listOf(to), endCallback)

    fun load() {
        val loaded = mutableMapOf<String, Song>()

        songsDir.listFiles { _, name -> name.endsWith(".nbs") }.forEach {
            val song = Song.fromFile(it)
                .freezeSong()
            loaded.put(it.name.removeSuffix(".nbs"), song)
            println("loading ${it.name} complete!")
        }

        songs = loaded.toMap()
    }
}

private fun toPitch(key: Int): Double? {
    require(key in 0..87) { "key는 0..87 범위여야 합니다" }
    val fsharp3Index = 33 // A0=0 기준 F#3 인덱스
    val useCount = key - fsharp3Index
    val pitch = 2.0.pow((useCount - 12) / 12.0)
    if (useCount < 0 || useCount > 24) {
        //println("마크 범위 벗어남: key=$key (useCount=$useCount) -> pitch=$pitch")
        return null
    }
    return pitch
}

private fun toSound(instrument: Int): Sound {
    require(instrument in 0..15)
    return when (instrument) {
        0 -> Sound.BLOCK_NOTE_BLOCK_HARP
        1 -> Sound.BLOCK_NOTE_BLOCK_BASS
        2 -> Sound.BLOCK_NOTE_BLOCK_BASEDRUM
        3 -> Sound.BLOCK_NOTE_BLOCK_SNARE
        4 -> Sound.BLOCK_NOTE_BLOCK_HAT
        5 -> Sound.BLOCK_NOTE_BLOCK_GUITAR
        6 -> Sound.BLOCK_NOTE_BLOCK_FLUTE
        7 -> Sound.BLOCK_NOTE_BLOCK_BELL
        8 -> Sound.BLOCK_NOTE_BLOCK_CHIME
        9 -> Sound.BLOCK_NOTE_BLOCK_XYLOPHONE
        10 -> Sound.BLOCK_NOTE_BLOCK_IRON_XYLOPHONE
        11 -> Sound.BLOCK_NOTE_BLOCK_COW_BELL
        12 -> Sound.BLOCK_NOTE_BLOCK_DIDGERIDOO
        13 -> Sound.BLOCK_NOTE_BLOCK_BIT
        14 -> Sound.BLOCK_NOTE_BLOCK_BANJO
        15 -> Sound.BLOCK_NOTE_BLOCK_PLING
        else -> error("unreachable")
    }
}