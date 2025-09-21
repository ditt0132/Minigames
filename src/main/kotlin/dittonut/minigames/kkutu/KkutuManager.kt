package dittonut.minigames.kkutu

import dittonut.minigames.config
import dittonut.minigames.parseMM
import dittonut.minigames.SoundManager
import dittonut.minigames.msToSec
import dittonut.minigames.plugin
import net.kyori.adventure.title.Title
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.io.File
import java.sql.Connection
import java.sql.DriverManager
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit


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
    var timerFuture: ScheduledFuture<*>? = null
) {
    init {
        require(players.size >= 2) { "Minimum two players required!" }
    }
}

data class KkutuRoundData(
    /** ms */
    @Volatile var timeLeft: Int,
    /** ms */
    @Volatile var turnTimeLeft: Int = 10_000,
    @Volatile var timerTicking: Boolean = false
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
    /** ms */
    var roundTime: Int = 90_000,
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
    kkutuDb.prepareStatement("SELECT id FROM ko WHERE LENGTH(id) = ? ORDER BY RANDOM() LIMIT 1;")
}

object KkutuGameManager {
    val executor: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

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

    fun startGame(queue: KkutuQueueData) {
        require(queue.players.size >= 2) { "Minimum two players required!" }

        RANDOM_WORD_QUERY.setInt(1, queue.settings.roundCount)
        val startWord: String = RANDOM_WORD_QUERY.executeQuery().use { rs ->
            rs.next() // ko table must have id, ignoring exception
            rs.getString("id")
        }

        val data = KkutuGameData(
            players = queue.players,
            usedWords = mutableListOf(startWord),
            settings = queue.settings
        )

        kkutuGames += data
        kkutuQueues -= queue
        data.round += 1

        SoundManager.playSound("kkutuGameStart", data.players)
        Bukkit.getScheduler().runTaskLater(plugin, Runnable {
            // TODO: actually start round
            startRound(data, startWord)
        }, 20L) // in ticks

        val task = Runnable {
            if (!data.roundData.timerTicking) return@Runnable
            if (data.roundData.timeLeft < -30_000) data.timerFuture?.cancel(false) // if code didn't cancel executor
            data.roundData.timeLeft -= 10 //ms
            data.roundData.turnTimeLeft -= 10 //ms
        }

        data.timerFuture = executor.scheduleAtFixedRate(task, 1_000, 10, TimeUnit.MILLISECONDS)
    }

    fun startRound(data: KkutuGameData, startWord: String) {
        require(startWord.length == data.settings.roundCount)

        SoundManager.playSound("kkutuRoundStart", data.players) {
            Bukkit.getScheduler().runTask(plugin, Runnable {
                startTurn(data, startWord)
            })
        }

        data.roundData.timeLeft = data.settings.roundTime
    }

    fun submitWord(word: String, player: Player, data: KkutuGameData) {
        val lastChar = data.usedWords.last().last().toString()
        if (!word.startsWith(lastChar) && (dueum(lastChar)?.let { !word.startsWith(it) } ?: false)) {
            handleIncorrect(word, player, data, "Invalid word")
            return
        }
        if (word.length == 1) {
            handleIncorrect(word, player, data, "Too short")
            return
        }
        if (data.usedWords.contains(word)) {
            handleIncorrect(word, player, data, "Already used")
            return
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            WORD_CHECK_QUERY.setString(1, word)
            val rs = WORD_CHECK_QUERY.executeQuery()
            rs.next()
            val exists = rs.getBoolean(1)
            Bukkit.getScheduler().runTask(plugin, Runnable {
                if (!exists) {
                    handleIncorrect(word, player, data, "Unknown word")
                    return@Runnable
                }
                handleCorrect(word, player, data)
            })
        })
    }

    fun handleIncorrect(word: String, player: Player, data: KkutuGameData, reason: String) {
        // play sound
        player.sendMessage("<green>[Kkutu]</green> Word not allowed: $reason".parseMM())
    }

    fun handleCorrect(word: String, player: Player, data: KkutuGameData) {
        data.players.forEach {
            it.sendMessage("<green>[Kkutu]</green> $word".parseMM()) }

        data.usedWords += word

        val index = data.players.indexOf(player)
        data.currentTurn = data.players[(index + 1) % data.players.size]
        data.roundData.timerTicking = false

        val speed = calculateSpeed(data)
        val tick = data.roundData.turnTimeLeft / 96
        val sg = data.roundData.turnTimeLeft / 12
        val beatString = BEAT.getOrNull(word.length)
        val taSound = if (word.length < 10) "As$speed" else "As1" //todo replace with Al
        val kktSound = "K$speed"

        data.players.forEach {
            SoundManager.stopSound(it, "T$speed")
        }

        if (beatString != null) {
            println(beatString)
            val beat = beatString.split("")

            var titleWord = ""
            var titleIdx = 0

            for ((idx, chr) in beat.withIndex()) {
                if (chr != "1") continue
                executor.schedule({
                    titleWord += word[titleIdx++]
                    // title things too
                    data.players.forEach {
                        it.showTitle(
                            Title.title(
                                titleWord.parseMM(), constructSubtitles(data).parseMM(),
                                0, 20_000, 0
                            )
                        )
                    }
                    SoundManager.playSound(taSound, data.players)
                }, idx * tick.toLong(), TimeUnit.MILLISECONDS)
            }
        } else {
            var titleWord = ""

            for ((idx, chr) in word.withIndex()) {
                titleWord += chr
                executor.schedule({
                    data.players.forEach {
                        it.showTitle(
                            Title.title(
                                titleWord.parseMM(), constructSubtitles(data).parseMM(),
                                0, 20_000, 0
                            )
                        )
                    }
                    SoundManager.playSound(taSound, data.players)
                }, idx * sg / word.length.toLong(), TimeUnit.MILLISECONDS)
            }
        }
        // last KX sound
        executor.schedule({
            SoundManager.playSound(kktSound, data.players) {
                Bukkit.getScheduler().runTaskLater(plugin, Runnable {
                    startTurn(data)
                }, 10L)
            }
        }, sg.toLong() + 150L, TimeUnit.MILLISECONDS)
    }

    /**
     * passing turn to other players is not implemented here!
     * @see handleCorrect
     */
    fun startTurn(data: KkutuGameData, lastWord: String = data.usedWords.last()) {
        data.currentTurn.sendActionBar("<green>It's your turn!</green>".parseMM())
        data.roundData.turnTimeLeft = 10_000 // ms // TODO: calculate this, instead of 10_000ms
        data.roundData.timerTicking = true

        data.players.forEach {
            it.sendMessage("<green>[Kkutu]</green> -> ${data.currentTurn.name}".parseMM()) }

        data.players.forEach { SoundManager.playSound("T${calculateSpeed(data)}", it) }

        val lastChar = lastWord.last().toString() // last word's last char
        val title = dueum(lastChar)?.let { "$lastChar ($it)" } ?: lastChar

        data.players.forEach {
            it.showTitle(
                Title.title(
                    title.parseMM(), constructSubtitles(data).parseMM(),
                    0, 20_000, 0
                )
            )
        }
    }

    fun calculateSpeed(data: KkutuGameData): Int {
        val rt = data.roundData.timeLeft
        return when {
            rt < 5_000 -> 10
            rt < 11_000 -> 9
            rt < 18_000 -> 8
            rt < 26_000 -> 7
            rt < 35_000 -> 6
            rt < 45_000 -> 5
            rt < 56_000 -> 4
            rt < 68_000 -> 3
            rt < 81_000 -> 2
            rt < 95_000 -> 1
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

fun dueum(s: String?): String? {
    if (s.isNullOrEmpty()) return null
    val c = s[0].code
    if (c < '가'.code || c > '힣'.code) return null

    val base = (c - '가'.code) / 28 // hangul jongseong length
    val newChar = when (base) {
        48, 54, 59, 62 -> c + 5292      // 녀, 뇨, 뉴, 니
        107, 111, 112, 117, 122, 125 -> c + 3528  // 랴, 려, 례, 료, 류, 리
        105, 106, 113, 116, 118, 123 -> c - 1764  // 라, 래, 로, 뢰, 루, 르
        else -> return null
    }
    return newChar.toChar() + s.substring(1)
}

private fun constructSubtitles(data: KkutuGameData): String {
    return "${data.round}R " +
            "| ${data.roundData.turnTimeLeft.msToSec(2)} " +
            "/ ${data.roundData.timeLeft.msToSec(2)} " +
            "| ${data.currentTurn.name}"
}