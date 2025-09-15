package dittonut.minigames

import org.bukkit.entity.Player

val sounds = mapOf<String, String>(
    Pair("As0", "")
)

fun playSound(id: String, to: Player) {
    to.sendMessage("와 방금 당신은 $id 소리를 들으셨어요!!!!")
}