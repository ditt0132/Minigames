package dittonut.minigames

import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver

fun String.parseMM(vararg resolvers: TagResolver) = mm.deserialize(this, *resolvers)