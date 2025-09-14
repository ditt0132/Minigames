package dittonut.minigames.kkutu

import dittonut.minigames.config
import dittonut.minigames.playSound
import dittonut.minigames.plugin
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.sql.Connection
import java.sql.DriverManager


val kkutuGames = mutableListOf<KkutuGameData>()
val kkutuQueues = mutableListOf<KkutuQueueData>()

lateinit var kkutuDb: Connection

data class KkutuGameData(
    val gameId: Int = kkutuGames.size,
    val players: List<Player>,
    val usedWords: MutableList<String> = mutableListOf(),
    /** 0 means not started yet */
    var round: Int = 0,
    var currentTurn: Player = players.random(),
    val settings: KkutuSettings,
    var roundData: KkutuRoundData = KkutuRoundData(settings.roundTime),
) {
    init {
        require(players.size >= 2) { "Minimum two players required!" }
    }
}

data class KkutuRoundData(
    /** ms */
    var timeLeft: Int,
    /** ms */
    var turnTimeLeft: Int = 10,
)

data class KkutuQueueData(
    val queueId: Int = kkutuQueues.size,
    val host: Player,
    /** including host */
    val players: MutableList<Player> = mutableListOf(host),
    val settings: KkutuSettings = KkutuSettings()
)

data class KkutuSettings(
    var injeong: Boolean = true,
    var gameType: KkutuGameType = KkutuGameType.KO,
    var mannerType: KkutuMannerType = KkutuMannerType.ONE,
    var roundTime: Int = 90,
    var roundCount: Int = 5,
    )

enum class KkutuMannerType(val count: Int, val displayName: String = "${count}개 이상") {
    NONE(0, "매너모드 비활성화"), ONE(1), FIVE(5), TEN(10)
}

enum class KkutuGameType(val displayName: String) {
    KO("한국어 끄투"), // KO_3WORD, KO_REVERSE, KO_WORD todo
}

object KkutuDbManager {
    fun init() {
        val file = File(plugin.dataFolder, "dict.db")
        if (!file.exists()) plugin.getResource("dict.db")?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        if (!::kkutuDb.isInitialized || kkutuDb.isClosed) kkutuDb = DriverManager.getConnection(config.kkutuDbUrl)
    }

    fun close() {
        if (::kkutuDb.isInitialized && !kkutuDb.isClosed) kkutuDb.close()
    }
}

private val RANDOM_WORD_QUERY by lazy {
    kkutuDb.prepareStatement("SELECT id FROM ko ORDER BY RANDOM() LIMIT 1;")
}

object KkutuGameManager {
    val BEAT = listOf(
        "00000000", // 0 (unused)
        "10000000", // 1
        "10001000", // 2
        "10010010", // 3
        "10011010", // 4
        "11011010", // 5
        "11011110", // 6
        "11011111", // 7
        "11111111"  // 8
    )
    fun startGame(queue: KkutuQueueData): KkutuGameData {
        require(queue.players.size >= 2) { "Minimum two players required!" }

        val startWord: String = RANDOM_WORD_QUERY.executeQuery().use { rs ->
            rs.next() // ko table must have id
            rs.getString("id")
        }

        val data = KkutuGameData(
            players = queue.players,
            usedWords = mutableListOf(startWord),
            settings = queue.settings
        )
        kkutuGames += data

        kkutuQueues -= queue

        data.players.forEach { playSound("kkutuGameStart", it) }
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // TODO: actually start round
            startRound(data)
        }, 20L)

        return data
    }

    fun startRound(data: KkutuGameData) {
        data.players.forEach { playSound("kkutuRoundStart", it) }

        TODO()
    }

    fun submitWord(word: String, player: Player, data: KkutuGameData) {
        TODO()
        // play incorrect when failed, else change data, play success sound, wait for it to process, continue to next tick

    }

    fun playSuccessSound(word: String, data: KkutuGameData) {
        val speed = calculateSpeed(data)
        val tick = data.roundData.turnTimeLeft / 96
        val sg = data.roundData.turnTimeLeft / 12
        val beat = BEAT.getOrNull(word.length)
        val taSound = beat ?: if (word.length < 10) "As$speed" else "Al"
        // use beat
        TODO()
    }

    fun calculateSpeed(data: KkutuGameData): Int {
        return TODO()
    }

    fun makeQueue(host: Player): KkutuQueueData {
        val data = KkutuQueueData(host = host)
        kkutuQueues += data
        return data
    }

    fun findQueue(member: Player): KkutuQueueData? =
        kkutuQueues.find {
            it.players.contains(member)
        }

    fun getQueue(id: Int): KkutuQueueData? =
        kkutuQueues.find {
            it.queueId == id
        }
}