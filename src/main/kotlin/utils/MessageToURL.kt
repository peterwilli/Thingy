package utils

import net.dv8tion.jda.api.entities.Message
import java.net.URL

fun messageToURL(msg: Message): URL {
    return URL("https://discord.com/channels/${msg.guild.id}/${msg.channel.id}/${msg.id}")
}