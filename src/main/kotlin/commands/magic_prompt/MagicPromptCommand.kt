import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import utils.messageToURL

fun magicPromptCommand(jda: JDA) {
    jda.onCommand("magic_prompt") { event ->
        event.deferReply().queue()
        try {
            val client = jcloudClient.currentClient()
            val amount = if (event.getOption("amount") == null) {
                5
            } else {
                event.getOption("amount")!!.asInt
            }
            val variation = if (event.getOption("variation") == null) {
                0.3
            } else {
                event.getOption("variation")!!.asInt / 100.0
            }
            val result = client.magicPrompt(event.getOption("start")!!.asString, amount, variation)
            if (result.isEmpty()) {
                event.hook.editOriginal("Failed :(").queue()
                event.messageChannel.sendMessage("${event.user.asMention} Magic Prompt failed! Sorry! Please try again later...").queue()
            }
            else {
                val firstText = result.first()
                val magicMessage = event.messageChannel.sendMessage(firstText).await()
                for (text in result.sliceArray(1 until result.size)) {
                    event.messageChannel.sendMessage(text).queue()
                }
                event.hook.editOriginal("Done! See ${messageToURL(magicMessage)}").queue()
                magicMessage.reply_("${event.user.asMention} Your magic prompts are done! We added them as single messages so it's easy to copy paste on phone! They are yours to do with as you wish!").queue()
            }
        }
        catch(e: Exception) {
            e.printStackTrace()
            event.hook.editOriginal("Error! $e").queue()
        }
    }
}