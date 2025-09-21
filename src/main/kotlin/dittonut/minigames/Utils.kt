package dittonut.minigames

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver
import kotlin.math.floor
import kotlin.math.pow

fun String.parseMM(vararg resolvers: TagResolver) = mm.deserialize(this, *resolvers)

fun Double.floorTo(decimals: Int): Double {
        require(decimals >= 0) { "decimals must be >= 0" }
        val factor = 10.0.pow(decimals)
        return floor(this * factor) / factor
}


fun Int.msToSec(decimals: Int): Double {
    require(decimals >= 0) { "decimals must be >= 0" }
    val seconds = this / 1000.0
    val factor = 10.0.pow(decimals)
    return floor(seconds * factor) / factor
}