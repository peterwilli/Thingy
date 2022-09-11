package commands.img2img

import commands.make.FairQueueEntry
import commands.make.FairQueueType
import commands.make.optionsToStableDiffusionParams
import config
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import org.atteo.evo.inflector.English
import queueDispatcher
import kotlin.math.min

fun img2imgCommand(jda: JDA) {
    jda.onCommand("img2img") { event ->
        val strength = if (event.getOption("strength") != null) {
            event.getOption("strength")!!.asDouble
        } else {
            0.75
        }

        val guidanceScale = if (event.getOption("guidance_scale") != null) {
            event.getOption("guidance_scale")!!.asDouble
        } else {
            7.5
        }

        val steps = if (event.getOption("steps") != null) {
            min(event.getOption("steps")!!.asInt, 50)
        } else {
            50
        }
        var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
            val initialParams = optionsToStableDiffusionParams(event, it)
            initialParams.copy(
                stableDiffusionParameters = initialParams.stableDiffusionParameters!!.copy(
                    steps = steps,
                    guidanceScale = guidanceScale,
                    strength = strength
                )
            )
        }
        val entry = FairQueueEntry(
            "Image to ${English.plural("Image", batch.size)}",
            FairQueueType.StableDiffusion,
            event.member!!.id,
            batch,
            event.hook,
            null
        )
        event.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
    }
}