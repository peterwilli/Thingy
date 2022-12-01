package commands.img2img

import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent

fun img2imgCommand(jda: JDA) {
    fun onCommand(event: GenericCommandInteractionEvent) {
        /*
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return
            }

            val strength = if (event.getOption("strength") != null) {
                event.getOption("strength")!!.asInt
            } else {
                75
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

            event.deferReply().queue()

            var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
                val initialParams = optionsToStableDiffusionParams(event, it)
                gson.toJson(initialParams.copy(
                    stableDiffusionParameters = initialParams.stableDiffusionParameters!!.copy(
                        steps = steps,
                        guidanceScale = guidanceScale,
                        strength = strength
                    )
                ))
            }
            val entry = FairQueueEntry(
                "Image to ${English.plural("Image", batch.size)}",
                FairQueueType.StableDiffusion,
                event.member!!.id,
                batch,
                event.hook,
                null
            )
            event.hook.editOriginal(queueDispatcher.queue.addToQueue(entry)).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
         */
    }

    jda.onCommand("img2img") { event ->
        onCommand(event)
    }
    jda.onCommand("link2img") { event ->
        onCommand(event)
    }
}