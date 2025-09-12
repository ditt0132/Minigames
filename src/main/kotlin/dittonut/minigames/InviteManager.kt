package dittonut.minigames

import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.time.Instant
import java.util.UUID

// A file that handles invites, for any minigames
data class InviteInfo(
    val gameName: String,
    val sender: Player,
    val target: Player,
    val queueId: Int
) {
    fun asString(): String = "${gameName},${sender.name},${target.name},${queueId}"
    companion object {
        fun fromString(str: String): InviteInfo? {
            val parts = str.split(",")
            if (parts.size != 4) return null  // gameName,senderName,targetName,queueId
            val gameName = parts[0]
            val sender = Bukkit.getPlayerExact(parts[1]) ?: return null
            val target = Bukkit.getPlayerExact(parts[2]) ?: return null
            val queueId = parts[3].toIntOrNull() ?: return null
            return InviteInfo(gameName, sender, target, queueId)
        }
    }
}

object InviteManager {
    val invites = mutableMapOf<InviteInfo, Instant>()

    fun tick() {
        val expired = invites.entries.filter { Instant.now().isAfter(it.value) }
        expired.forEach {
            it.key.sender.sendMessage("<green>[${it.key.gameName}]</green> invite to <target> has been expired!"
                .parseMM(Placeholder.component("target", it.key.target.displayName())))
        }
        expired.forEach {
            it.key.sender.sendMessage("<green>[${it.key.gameName}</green> invite from <sender> has been expired!"
                .parseMM(Placeholder.component("target", it.key.sender.displayName())))
        }
        expired.forEach { invites.remove(it.key) }
    }
}