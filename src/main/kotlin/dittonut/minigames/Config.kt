package dittonut.minigames

import com.charleskorn.kaml.Yaml
import kotlinx.serialization.Serializable
import org.bukkit.Bukkit
import java.io.File

lateinit var config: Config
    private set

@Serializable
data class Config(
    val version: Int = 1,
    val kkutuDbUrl: String = ""
    )

object ConfigManager {
    private val yaml = Yaml()
    val file = File(plugin.dataFolder, "config.yml")

    fun load() {
        config = if (file.exists()) yaml.decodeFromString(Config.serializer(), file.readText())
                 else Config()
        save()
    }

    fun save() {
        val snapshot = config.copy() // safe snapshot
        Bukkit.getScheduler().runTaskAsynchronously(plugin, Runnable {
            file.writeText(yaml.encodeToString(Config.serializer(), snapshot))
        })
    }
}