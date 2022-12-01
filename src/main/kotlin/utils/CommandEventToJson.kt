package utils

import com.google.gson.JsonObject
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.commands.OptionType
import org.apache.commons.io.IOUtils
import java.net.URL
import java.util.*


fun GenericCommandInteractionEvent.optionsToJson(): JsonObject {
    val result = JsonObject()
    for (option in this.options) {
        when (option.type) {
            OptionType.NUMBER -> result.addProperty(option.name, option.asDouble)
            OptionType.STRING -> result.addProperty(option.name, option.asString)
            OptionType.BOOLEAN -> result.addProperty(option.name, option.asBoolean)
            OptionType.ATTACHMENT -> {
                val connection = URL(option.asAttachment.url).openConnection()
                connection.getInputStream().use {
                    val bytes: ByteArray = IOUtils.toByteArray(it)
                    val base64 = Base64.getEncoder().encodeToString(bytes)
                    result.addProperty(option.name, base64)
                }
            }

            OptionType.INTEGER -> result.addProperty(option.name, option.asInt)
        }
    }
    return result
}