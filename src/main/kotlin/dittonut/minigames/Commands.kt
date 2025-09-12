package dittonut.minigames

import dittonut.minigames.kkutu.KkutuGameData
import dittonut.minigames.kkutu.KkutuGameManager
import dittonut.minigames.kkutu.KkutuGameType
import dittonut.minigames.kkutu.KkutuMannerType
import dittonut.minigames.kkutu.KkutuQueueData
import dittonut.minigames.kkutu.KkutuSettings
import io.papermc.paper.command.brigadier.CommandSourceStack
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.entity.Player
import org.incendo.cloud.CommandManager
import org.incendo.cloud.bukkit.parser.PlayerParser
import org.incendo.cloud.context.CommandContext
import org.incendo.cloud.kotlin.extension.buildAndRegister
import org.incendo.cloud.parser.standard.BooleanParser
import org.incendo.cloud.parser.standard.EnumParser
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

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Sent an invite to <displayName>!".parseMM(
                Placeholder.component("displayName", target.displayName())
            ))
            target.sendMessage("<green>[Kkutu]</green> Incoming invite from <displayName>, queue #${queue.queueId}!".parseMM(
                Placeholder.component("displayName", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_accept <id>
        literal("invite_accept")
        required("id", StringParser.stringParser())
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

            InviteManager.invites.remove(invite)
            queue.players += player

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> accepted invite from <displayName>!".parseMM(
                Placeholder.component("displayName", invite.sender.displayName())
            ))
            invite.sender.sendMessage("<green>[Kkutu]</green> <displayName> accepted your invite!".parseMM(
                Placeholder.component("displayName", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_deny <id>
        literal("invite_deny")
        required("id", StringParser.stringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val invite = validateInvite(ctx) ?: return@handler

            InviteManager.invites.remove(invite)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> denied invite from <displayName>!".parseMM(
                Placeholder.component("displayName", invite.sender.displayName())
            ))
            invite.sender.sendMessage("<green>[Kkutu]</green> <displayName> denied your invite!".parseMM(
                Placeholder.component("displayName", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu invite_cancel <id>
        literal("invite_cancel")
        required("id", StringParser.stringParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val invite = validateInvite(ctx) ?: return@handler

            InviteManager.invites.remove(invite)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> canceled invite to <displayName>!".parseMM(
                Placeholder.component("displayName", invite.target.displayName())
            ))
            invite.target.sendMessage("<green>[Kkutu]</green> <displayName> canceled invite!".parseMM(
                Placeholder.component("displayName", player.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu kick <name>
        literal("kick")
        required("name", PlayerParser.playerParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler
            val target = ctx.get<Player>("name")

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to kick player!")) return@handler

            // else
            queue.players.remove(target)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Kicked <displayName> from queue!".parseMM(
                Placeholder.component("displayName", target.displayName())
            ))
            target.sendMessage("<green>[Kkutu]</green> Kicked from queue #${queue.queueId}!".parseMM(
                Placeholder.component("displayName", target.displayName())
            ))
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set injeong <value>
        literal("set")
        literal("injeong")
        required("value", BooleanParser.booleanParser())
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

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
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.injeong
            val new = ctx.get<Boolean>("value")

            queue.settings.injeong = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>injeong</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu set mannerType <value>
        literal("set")
        literal("mannerType")
        required("value", EnumParser.enumParser(KkutuMannerType::class.java))
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to change settings!")) return@handler

            val prev = queue.settings.injeong
            val new = ctx.get<Boolean>("value")

            queue.settings.injeong = new

            queue.players.forEach {
                it.sendMessage("<green>[Kkutu]</green> Setting changed: <green>injeong</green>: $prev to $new!".parseMM())
            }
        }
    }

    manager.buildAndRegister("kkutu") { // kkutu start
        literal("start")
        handler { ctx ->
            val player = validatePlayer(ctx) ?: return@handler
            val queue = validateQueue(ctx) ?: return@handler
            val target = ctx.get<Player>("name")

            if (!validateHost(ctx, queue, "<red>[Kkutu]</red> Must be host to start the game!")) return@handler

            if (queue.players.size < 2) {
                ctx.sender().sender.sendMessage("<red>[Kkutu]</red> Must have two or more players in queue to start!".parseMM())
            }

            KkutuGameManager.start(queue)

            ctx.sender().sender.sendMessage("<green>[Kkutu]</green> Sent an invite to <displayName>!".parseMM(
                Placeholder.component("displayName", target.displayName())
            ))
            target.sendMessage("<green>[Kkutu]</green> Incoming invite from <displayName>, queue #${queue.queueId}!".parseMM(
                Placeholder.component("displayName", player.displayName())
            ))
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
