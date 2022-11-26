import com.google.gson.JsonArray
import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.optionsToJson

fun stableDiffusionCommand(jda: JDA) {
    jda.onCommand("stable_diffusion") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }

            fun createEntry(hook: InteractionHook): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val params = event.optionsToJson()
                    val seed = params["seed"].asLong
                    params.addProperty("seed", seed + idx)
                    batch.add(params)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    getScriptForSize(event.getOption("size")!!.asInt),
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