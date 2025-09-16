package dittonut.minigames

import cz.koca2000.nbs4j.Song
import org.bukkit.Sound
import org.bukkit.SoundCategory
import org.bukkit.entity.Player
import java.io.File
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.pow

var sounds = mapOf<String, String>(
    Pair("test", "test")
)
lateinit var songs: Map<String, Song>
    private set

private val songsDir: File by lazy {
    File(plugin.dataFolder, config.songsDir) //TODO: auto copy
}

object SoundManager {
    fun playSound(id: String, to: Player) {
        to.sendMessage("와 방금 당신은 $id 소리를 들으셨어요!!!!")
        val song = songs[id]
        song ?: return

        val executor = Executors.newSingleThreadScheduledExecutor()
        var currentTick = 0L // todo: maybe try -1?
        val tempo = song.getTempo(0) // float, ticks per second

        fun scheduleNextTick() {
            val nextTick = song.getNextNonEmptyTick(currentTick.toInt())
            if (nextTick < 0) {
                executor.shutdown()
                return
            }

            val delayMs = ((nextTick - currentTick) * 1000f / tempo).toLong()

            // todo: what happened to first note?

            executor.schedule({
                song.layers.forEach { layer ->
                    val note = layer.notes[nextTick]
                    if (note == null) {
                        //println("empty")
                        return@forEach
                    }
                    val key = note.key
                    val volume = note.volume
                    val instrument = note.instrument

                    val pitch = toPitch(key)?.toFloat()

                    if (pitch == null) {
                        println("outoF")
                        return@forEach
                    }

                    to.playSound(to, Sound.BLOCK_NOTE_BLOCK_HARP,
                        SoundCategory.MASTER,
                        volume / 100f,
                         pitch,
                        0L)

                    println("Tick $nextTick - Key: $key, Volume: $volume, Instrument: $instrument")
                }

                currentTick = nextTick.toLong() //+ 1L
                scheduleNextTick()
            }, delayMs, TimeUnit.MILLISECONDS)
        }

        scheduleNextTick()
    }

    fun load() {
        val loaded = mutableMapOf<String, Song>()

        sounds.forEach {
            val song = Song.fromFile(File(songsDir, "${it.key}.nbs"))
                .freezeSong()
            loaded.put(it.key, song)
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
        println("마크 범위 벗어남: key=$key (useCount=$useCount) -> pitch=$pitch")
        return null
    }
    return pitch
}
