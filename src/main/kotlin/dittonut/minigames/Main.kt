package dittonut.minigames

import dittonut.minigames.kkutu.KkutuDbManager
import dittonut.minigames.kkutu.KkutuGameData
import net.kyori.adventure.text.minimessage.MiniMessage
import org.bukkit.plugin.java.JavaPlugin
import org.incendo.cloud.execution.ExecutionCoordinator
import org.incendo.cloud.paper.PaperCommandManager
import java.util.logging.Logger


lateinit var plugin: Main
    private set
lateinit var logger: Logger
    private set
lateinit var mm: MiniMessage
    private set

class Main : JavaPlugin() {
    override fun onEnable() {
        // Plugin startup logic
        plugin = this
        dittonut.minigames.logger = logger

        if (!dataFolder.exists()) dataFolder.mkdirs()
        ConfigManager.load()
        server.scheduler.runTaskTimer(this, Runnable {
            ConfigManager.save()
        }, 0L, 600L) // 600틱 = 30초

        val commandManager = PaperCommandManager.builder()
            .executionCoordinator(ExecutionCoordinator.simpleCoordinator())
            .buildOnEnable(this)
        registerCommands(commandManager)

        server.pluginManager.registerEvents(Events(), this)

        mm = MiniMessage.miniMessage()

        // Minigame init - Kkutu
        KkutuDbManager.init()
    }

    override fun onDisable() {
        // Plugin shutdown logic
        ConfigManager.save()
    }
}
