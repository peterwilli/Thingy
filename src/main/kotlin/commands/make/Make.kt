import commands.make.FairQueueEntry
import commands.make.optionsToParams
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA

fun makeCommand(jda: JDA) {
    jda.onCommand("make") { event ->
        try {
            var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
                optionsToParams(event) ?: return@onCommand
            }
            val entry = FairQueueEntry("Generating Image", event.member!!.id, batch, event.hook)
            queueDispatcher.queue.addToQueue(entry)
            event.reply_("**Added to queue**\n> *${entry.parameters.first().prompts}*").queue()
        } catch (e: Exception) {
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
    }
}