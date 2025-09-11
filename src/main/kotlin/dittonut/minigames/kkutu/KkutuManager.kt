package dittonut.minigames.kkutu

import dittonut.minigames.config
import org.bukkit.entity.Player
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException


val kkutuGames = mutableListOf<KkutuGameData>()

lateinit var kkutuDb: Connection

data class KkutuGameData(
    val gameId: Int = kkutuGames.size,
    val players: List<Player>,
    val usedWords: MutableList<String> = mutableListOf(),
    val startWord: String,
    /** 0 indexed */
    val round: Int = 1,
    val currentTurn: Player = players[0]
) {
    init {
        require(players.size >= 2) { "Minimum two players required!" }
    }
}

data class KkutuQueueData(
    val host: Player,
    /** including host */
    val players: MutableList<Player> = mutableListOf(host),

)

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
    kkutuDb.prepareStatement("SELECT id FROM 테이블명 ORDER BY RANDOM() LIMIT 1;")
}

fun startKkutu(players: List<Player>): KkutuGameData {
    require(players.size >= 2) { "Minimum two players required!" }

    val startWord: String = RANDOM_WORD_QUERY.executeQuery().use { rs ->
        rs.next() // ko table must have id
        rs.getString("id")
    }

    return KkutuGameData(
        players = players,
        startWord = startWord
    )
}