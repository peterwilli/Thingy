import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook

fun stableDiffusionCommand(jda: JDA) {
    jda.onCommand("stable_diffusion") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }

            fun createEntry(hook: InteractionHook): FairQueueEntry {
                var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
                    optionsToStableDiffusionParams(event, it)
                }
                return FairQueueEntry(
                    "Generating Image",
                    FairQueueType.StableDiffusion,
                    event.member!!.id,
                    batch,
                    hook,
                    null
                )
            }
            val entry = createEntry(event.hook)
            event.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
    }
}