package dittonut.minigames

import dittonut.minigames.kkutu.KkutuGameManager
import dittonut.minigames.kkutu.KkutuGameType
import dittonut.minigames.kkutu.KkutuMannerType
import dittonut.minigames.kkutu.KkutuQueueData
import dittonut.minigames.kkutu.kkutuGames
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.incendo.cloud.CommandManager
import org.incendo.cloud.bukkit.parser.PlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.BooleanParser
import org.incendo.cloud.parser.standard.EnumParser
import org.incendo.cloud.parser.standard.IntegerParser
import org.incendo.cloud.parser.standard.StringParser
import java.time.Instant

fun registerCommands(manager: CommandManager<CommandSourceStack>) {
    manager.buildAndRegister("minigames") { // minigames config reload
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

    // Minigames

    // Kkutu - Queue
    manager.buildAndRegister("kkutu") { // kkutu create
        literal("create")
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = KkutuGameManager.makeQueue(player)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Queue created as id #${queue.queueId}!".parseMM())
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite <name>
        literal("invite")
        required("name", PlayerParser.playerParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler
            val target = ctx.get<Player>("name")

            InviteManager.invites.put(
                InviteInfo("Kkutu", player, target, queue.queueId),
                Instant.now().plusSeconds(config.inviteExpire))

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Sent an invite to <display_name>!".parseMM(
                Placeholder.component("display_name", target.displayName())
            ))
            target.sendMessage(("<green>[Kkutu]</green> Incoming invite from <display_name>, queue #${queue.queueId}!\n\n" +
                    "<green><click:run_command:" +
                    "/kkutu invite_accept Kkutu,${player.name},${target.name},${queue.queueId}>[Accept]</click></green>")
                .parseMM(
                Placeholder.component("display_name", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_accept <id>
        literal("invite_accept")
        required("id", StringParser.greedyStringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            if (KkutuGameManager.findQueue(player) != null) {
                ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Must not in a queue to run this command!".parseMM())
                return@handler
            }
            val invite = validateInvite(ctx) ?: return@handler
            val queue = KkutuGameManager.getQueue(invite.queueId) ?: run {
                ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Queue not found! (maybe already started?)".parseMM())
                return@handler
            }

            if (player != invite.target) {
                ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Cannot accept other's invite!".parseMM())
                return@handler
            }

            InviteManager.invites.remove(invite)
            queue.players += player

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> accepted invite from <display_name>!".parseMM(
                Placeholder.component("display_name", invite.sender.displayName())
            ))
            invite.sender.sendMessage("<green>[Kkutu]</green> <display_name> accepted your invite!".parseMM(
                Placeholder.component("display_name", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_deny <id>
        literal("invite_deny")
        required("id", StringParser.greedyStringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val invite = validateInvite(ctx) ?: return@handler

            InviteManager.invites.remove(invite)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> denied invite from <display_name>!".parseMM(
                Placeholder.component("display_name", invite.sender.displayName())
            ))
            invite.sender.sendMessage("<green>[Kkutu]</green> <display_name> denied your invite!".parseMM(
                Placeholder.component("display_name", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_cancel <id>
        literal("invite_cancel")
        required("id", StringParser.greedyStringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val invite = validateInvite(ctx) ?: return@handler

            InviteManager.invites.remove(invite)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> canceled invite to <display_name>!".parseMM(
                Placeholder.component("display_name", invite.target.displayName())
            ))
            invite.target.sendMessage("<green>[Kkutu]</green> <display_name> canceled invite!".parseMM(
                Placeholder.component("display_name", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu list
        literal("list")
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            validateQueue(ctx) ?: return@handler

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Players:".parseMM())
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu list
        literal("end_all")
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            //todo
            if (player.name == "DittoNut") {
                kkutuGames.clear()
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu list
        literal("info")
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            ctx.sender().sender.sendMessage((
                    "<green>[Kkutu]</green> Settings:\n" +
                    "  <gray>어인정(injeong)</gray>: <green>${queue.settings.injeong}</green>\n" +
                    "  <gray>게임 모드(gameType)</gray>: <green>${queue.settings.gameType.displayName}</green>\n" +
                    "  <gray>매너 모드(mannerType)</gray>: <green>${queue.settings.mannerType.displayName}</green>\n" +
                    "  <gray>라운드 시간(roundTime)</gray>: <green>${queue.settings.roundTime} 밀리초</green>\n" +
                            // todo: maybe put conversion in this, and set?
                    "  <gray>라운드 수(roundCount)</gray>: <green>${queue.settings.roundCount}</green>\n"
            ).parseMM())
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu list
        literal("leave")
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            queue.players -= player

            ctx.sender().sender.sendMessage(("<green>[Kkutu]</green> Left queue #${queue.queueId}!").parseMM())
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu kick <name>
        literal("kick")
        required("name", PlayerParser.playerParser())
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler
            val target = ctx.get<Player>("name")

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to kick player!")) return@handler

            // else
            queue.players.remove(target)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Kicked <display_name> from queue!".parseMM(
                Placeholder.component("display_name", target.displayName())
            ))
            target.sendMessage("<green>[Kkutu]</green> Kicked from queue #${queue.queueId}!".parseMM(
                Placeholder.component("display_name", target.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set injeong <value>
        literal("set")
        literal("injeong")
        required("value", BooleanParser.booleanParser())
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue,
                    "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.injeong
            val new = ctx.get<Boolean>("value")

            queue.settings.injeong = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>injeong</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set gameType <value>
        literal("set")
        literal("gameType")
        required("value", EnumParser.enumParser(KkutuGameType::class.java))
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.gameType
            val new = ctx.get<KkutuGameType>("value")

            queue.settings.gameType = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>gameType</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set mannerType <value>
        literal("set")
        literal("mannerType")
        required("value", EnumParser.enumParser(KkutuMannerType::class.java))
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.mannerType
            val new = ctx.get<KkutuMannerType>("value")

            queue.settings.mannerType = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>mannerType</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set roundTime <value>
        literal("set")
        literal("roundTime")
        required("value", IntegerParser.integerParser(10, 150))
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.roundTime
            val new = ctx.get<Int>("value")

            queue.settings.roundTime = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>roundTime</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set roundCount <value>
        literal("set")
        literal("roundCount")
        required("value", IntegerParser.integerParser(1, 10))
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.roundCount
            val new = ctx.get<Int>("value")

            queue.settings.roundCount = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>roundCount</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu start
        literal("start")
        handler { ctx ->
            validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to start the game!")) return@handler

            if (queue.players.size < 2) {
                ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Must have two or more players in queue to start!".parseMM())
            }

            KkutuGameManager.startGame(queue)

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Kkutu started!".parseMM())
            }
        }
    }

    manager.buildAndRegister("soundplayer") { // kkutu start
        required("name", StringParser.greedyStringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val name = ctx.get<String>("name")

            if (name == "reload") {
                SoundManager.load()
            } else if (name.startsWith("stop")) {
                val arguments = name.split("/")
                SoundManager.stopSound(player, arguments.getOrNull(1)?.takeIf { it.isNotEmpty() })
            }
            else SoundManager.playSound(name, player)
        }
    }
}


private fun validatePlayer(ctx: CommandContext<CommandSourceStack>): Player? {
    val player = ctx.sender().executor as? Player ?: run {
        ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Must be a player to run this command!".parseMM())
        return null
    }
    return player
}

private fun validateQueue(ctx: CommandContext<CommandSourceStack>): KkutuQueueData? {
    val queue = KkutuGameManager.findQueue(ctx.sender().executor as? Player ?: return null) ?: run {
        ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Must be in a queue to run this command!".parseMM())
        return null
    }
    return queue
}

private fun validateInvite(ctx: CommandContext<CommandSourceStack>): InviteInfo? {
    val invite = InviteInfo.fromString(ctx.get("id"))
        .takeIf { InviteManager.invites.contains(it) } ?: run {
        ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Invite not found! (or expired?)".parseMM())
        return null
    }
    return invite
}

private fun validateHost(ctx: CommandContext<CommandSourceStack>, queue: KkutuQueueData,
                         message: String = "<red>[Kkutu]</red> Must be host to do that!"): Boolean {
    if (queue.host.uniqueId != (ctx.sender().executor as? Player ?: return false).uniqueId) {
        ctx.sender().sender.sendMessage(message.parseMM())
        return false
    } else return true
}
