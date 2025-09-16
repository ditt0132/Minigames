package dittonut.minigames.kkutu

import dittonut.minigames.config
import dittonut.minigames.parseMM
import dittonut.minigames.SoundManager
import dittonut.minigames.plugin
import org.bukkit.Bukkit
import org.bukkit.Sound
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

    private val WORD_CHECK_QUERY by lazy {
        kkutuDb.prepareStatement("SELECT EXISTS(SELECT 1 FROM ko WHERE id = ?);")
    }

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

        data.players.forEach { SoundManager.playSound("kkutuGameStart", it) }
        data.players.forEach {
            it.sendMessage("<green>[Kkutu]</green> -> ${data.currentTurn.name}".parseMM()) }
        data.players.forEach {
            it.sendMessage("<green>[Kkutu]</green> $startWord".parseMM()) }
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // TODO: actually start round
            startRound(data)
        }, 20L)

        return data
    }

    fun startRound(data: KkutuGameData) {
        data.players.forEach { SoundManager.playSound("kkutuRoundStart", it) }
        data.roundData.timeLeft = data.settings.roundTime
    }

    fun submitWord(word: String, player: Player, data: KkutuGameData) {
        val lastWord = data.usedWords.last()
        println(lastWord)
        println("recv at submitWord")
        if (!word.startsWith(lastWord.last())) {
            handleIncorrect(word, player, data, "Invalid word")
            return
        }
        if (word.length == 1) {
            handleIncorrect(word, player, data, "Too short!")
            return
        }
        println("valid")
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            println("checking $word")
            WORD_CHECK_QUERY.setString(1, word)
            val rs = WORD_CHECK_QUERY.executeQuery()
            rs.next()
            val exists = rs.getBoolean(1)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!exists) {
                    println("notexists")
                    handleIncorrect(word, player, data, "Unknown word")
                    return@Runnable
                }
                println("exsists")
                handleCorrect(word, player, data)
            })
        })
        // play incorrect when failed, else change data, play success sound, wait for it to process, continue to next tick

    }

    fun handleIncorrect(word: String, player: Player, data: KkutuGameData, reason: String) {
        player.sendMessage("<green>[Kkutu]</green> Word not allowed: $reason".parseMM())
    }

    fun handleCorrect(word: String, player: Player, data: KkutuGameData) {
        data.players.forEach {
            it.sendMessage("<green>[Kkutu]</green> $word".parseMM()) }
        data.usedWords += word

        val index = data.players.indexOf(player)
        data.currentTurn = data.players[(index + 1) % data.players.size]

        // TODO run this after delay
        data.currentTurn.playSound(player, Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f)
        startTurn(data)
        return; // TODO

        val speed = calculateSpeed(data)
        val tick = data.roundData.turnTimeLeft / 96
        val sg = data.roundData.turnTimeLeft / 12
        val beat = BEAT.getOrNull(word.length)
        val taSound = beat ?: if (word.length < 10) "As$speed" else "Al"
        // use beat
        // TODO: wait for beat to play

    }

    /**
     * passing turn to other players is not implemented here!
     * @see handleCorrect
     */
    fun startTurn(data: KkutuGameData) {
        data.currentTurn.sendActionBar("<green>It's your turn!</green>".parseMM())
        data.roundData.turnTimeLeft = 10
    }

    fun calculateSpeed(data: KkutuGameData): Int {
        val rt = data.roundData.timeLeft
        return when {
            rt < 5000 -> 10
            rt < 11000 -> 9
            rt < 18000 -> 8
            rt < 26000 -> 7
            rt < 35000 -> 6
            rt < 45000 -> 5
            rt < 56000 -> 4
            rt < 68000 -> 3
            rt < 81000 -> 2
            rt < 95000 -> 1
            else -> 0
        }
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

    fun findGame(member: Player): KkutuGameData? =
        kkutuGames.find {
            it.players.contains(member)
        }
}