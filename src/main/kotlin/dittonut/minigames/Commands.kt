package dittonut.minigames

import io.papermc.paper.command.brigadier.CommandSourceStack
import org.incendo.cloud.CommandManager
import org.incendo.cloud.kotlin.extension.buildAndRegister

fun registerCommands(manager: CommandManager<CommandSourceStack>) {
    manager.buildAndRegister("minigames") { // minigames config save
        literal("config")
        literal("reload")
        permission("minigames.config.reload")
        handler { ctx ->
            ctx.sender().sender.sendMessage("<green>[Minigames]</green> Reloading config.yml!".parseMM())
            ConfigManager.load()
        }
    }
    manager.buildAndRegister("minigames") { // minigames config save
        literal("config")
        literal("save")
        permission("minigames.config.save")
        handler { ctx ->
            ctx.sender().sender.sendMessage("<green>[Minigames]</green> Saving config.yml!".parseMM())
            ConfigManager.save()
        }
    }
}