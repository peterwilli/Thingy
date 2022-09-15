package utils

import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction
import org.apache.commons.lang3.exception.ExceptionUtils

fun GenericCommandInteractionEvent.replyOrEdit(message: String, ephemeral: Boolean = false) {
    if (isAcknowledged) {
        this.hook.editMessage(content = message).queue()
    } else {
        this.reply_(message).setEphemeral(ephemeral)
    }
}

fun GenericCommandInteractionEvent.writeNewMessageInSameChannel(message: String): MessageCreateAction {
    return this.messageChannel.sendMessage(message)
}

fun GenericCommandInteractionEvent.sendException(e: Exception) {
    this.replyOrEdit("*Errored* :(")
    this.writeNewMessageInSameChannel(
        "**Error! **Command: `${this.commandString}`\nAuthor: ${this.user.asMention}\n```${
            ExceptionUtils.getMessage(
                e
            )
        }\n${ExceptionUtils.getStackTrace(e)}```"
    ).queue()
}