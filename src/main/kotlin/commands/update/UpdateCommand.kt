package commands.update

import config
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import updateMode

fun updateCommand(jda: JDA) {
    jda.onCommand("update") { event ->
        try {
            if(event.user.id != config.bot.ownerId) {
                event.reply_("Only bot admins can use this command!").setEphemeral(true).queue()
                return@onCommand
            }
            updateMode = event.getOption("on")!!.asBoolean
            event.reply_("Update mode set to $updateMode!").setEphemeral(true).queue()
        }
        catch(e: Exception) {
            e.printStackTrace()
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
    }
}