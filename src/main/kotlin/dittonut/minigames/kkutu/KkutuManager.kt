package dittonut.minigames.kkutu

import dittonut.minigames.config
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


val kkutuGames = mutableListOf<KkutuGameData>()
val kkutuQueues = mutableListOf<KkutuQueueData>()

lateinit var kkutuDb: Connection

data class KkutuGameData(
    val gameId: Int = kkutuGames.size,
    val players: List<Player>,
    val usedWords: MutableList<String> = mutableListOf(),
    val startWord: String,
    /** 0 indexed */
    val round: Int = 1,
    val currentTurn: Player = players.random(),
    val settings: KkutuSettings
) {
    init {
        require(players.size >= 2) { "Minimum two players required!" }
    }
}

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

enum class KkutuMannerType(val count: Int) {
    NONE(0), ONE(1), FIVE(5), TEN(10)
}

enum class KkutuGameType {
    KO, // KO_3WORD, KO_REVERSE, KO_WORD todo
}

object KkutuDbManager {
    @Throws(SQLException::class)
    fun init() {
        if (!::kkutuDb.isInitialized || kkutuDb.isClosed) kkutuDb = DriverManager.getConnection(config.kkutuDbUrl)
    }

    @Throws(SQLException::class)
    fun close() {
        if (::kkutuDb.isInitialized && !kkutuDb.isClosed) kkutuDb.close()
    }
}

private val RANDOM_WORD_QUERY by lazy {
    kkutuDb.prepareStatement("SELECT id FROM ko ORDER BY RANDOM() LIMIT 1;")
}

object KkutuGameManager {
    fun start(queue: KkutuQueueData): KkutuGameData {
        require(queue.players.size >= 2) { "Minimum two players required!" }

        val startWord: String = RANDOM_WORD_QUERY.executeQuery().use { rs ->
            rs.next() // ko table must have id
            rs.getString("id")
        }

        val data = KkutuGameData(
            players = queue.players,
            startWord = startWord,
            settings = queue.settings
        )
        kkutuGames += data

        return data
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