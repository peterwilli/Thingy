package commands.make

import config
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import queueDispatcher
import kotlin.math.pow
import kotlin.random.Random

fun variate(
    buttonInteractionEvent: ButtonInteractionEvent,
    imageIndex: Int,
    params: CreateArtParameters
) {
    try {
        var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
            params.copy(
                seed = Random.nextInt(0, 2.toDouble().pow(32).toInt()),
                initImage = "${config.imagesFolder}/${params.artID}/0-done-0.png",
                artID = "${params.artID}_V$it"
            )
        }
        val entry = FairQueueEntry(
            "Making variation of #${imageIndex + 1}",
            FairQueueType.Variate,
            buttonInteractionEvent.member!!.id,
            batch,
            buttonInteractionEvent.hook
        )
        buttonInteractionEvent.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
    } catch (e: Exception) {
        e.printStackTrace()
        buttonInteractionEvent.reply_("Error! $e").setEphemeral(true).queue()
    }
}