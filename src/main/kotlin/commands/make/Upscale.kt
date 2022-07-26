package commands.make

import config
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import queueDispatcher
import utils.byteArrayImageTo
import kotlin.math.pow
import kotlin.random.Random

suspend fun upscale(
    buttonInteractionEvent: ButtonInteractionEvent,
    imageIndex: Int,
    params: CreateArtParameters
) {
    try {
        val upscaleParams = params.copy(
            initImage = "${config.imagesFolder}/${params.artID.substring(0..params.artID.length - 4)}/0-done-0.png"
        )
        val entry = FairQueueEntry("Upscaling ${imageIndex + 1}", FairQueueType.Upscale, buttonInteractionEvent.member!!.id, listOf(upscaleParams), buttonInteractionEvent.hook)
        queueDispatcher.queue.addToQueue(entry)
        buttonInteractionEvent.reply_("**Added to queue**\n> *${entry.getHumanReadablePrompts()}*").queue()
    } catch (e: Exception) {
        e.printStackTrace()
        buttonInteractionEvent.reply_("Error! $e").setEphemeral(true).queue()
    }
}