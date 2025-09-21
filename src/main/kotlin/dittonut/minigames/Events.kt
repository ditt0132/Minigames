package dittonut.minigames

import dittonut.minigames.kkutu.KkutuGameManager
import io.papermc.paper.event.player.ChatEvent
import net.kyori.adventure.text.Component
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerChatEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.event.player.PlayerQuitEvent

class Events : Listener {
    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        // TODO
    }

    val plainTextSerializer = PlainTextComponentSerializer.plainText()
    /**
     * Kkutu word submit handler
     */
    @EventHandler
    fun onChat(event: ChatEvent) {
        println("receiv")
        val data = KkutuGameManager.findGame(event.player) ?: return
        println("ingame")
        val content = plainTextSerializer.serialize(event.message())
        println(content)
        val stripped = content.removePrefix("!")
        println(stripped)
        if (content.startsWith("!")) {
            event.message(Component.text(stripped))
            return;
        }
        if (data.currentTurn == event.player) {
            println("submit")
            KkutuGameManager.submitWord(stripped, event.player, data)
        }
        // else keep default, just chat
    }

    fun onQuit(event: PlayerQuitEvent) {
        SoundManager.executor.execute {
            SoundManager.stopSound(event.player)
        }
    }
}