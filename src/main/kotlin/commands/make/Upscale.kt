package commands.make

import config
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import queueDispatcher

fun upscale(
    buttonInteractionEvent: ButtonInteractionEvent,
    imageIndex: Int,
    params: CreateArtParameters
) {
    try {
        val upscaleParams = params.copy(
            initImage = "${config.imagesFolder}/${params.artID.substring(0..params.artID.length - 4)}/0-done-0.png",
            artID = "${params.artID}_U"
        )
        val entry = FairQueueEntry(
            "Upscaling #${imageIndex + 1}",
            FairQueueType.Upscale,
            buttonInteractionEvent.member!!.id,
            listOf(upscaleParams),
            buttonInteractionEvent.hook
        )
        buttonInteractionEvent.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
    } catch (e: Exception) {
        e.printStackTrace()
        buttonInteractionEvent.reply_("Error! $e").setEphemeral(true).queue()
    }
}