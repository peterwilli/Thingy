import com.google.gson.JsonArray
import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.optionsToJson
import utils.sendException
import utils.withDefaults
import kotlin.random.Random

fun makeCommand(jda: JDA) {
    jda.onCommand("make") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().queue()
            val client = jcloudClient.currentClient()
            val prompt = event.getOption("prompt")!!.asString
            val magicPromptResult = client.magicPrompt(prompt, config.hostConstraints.totalImagesInMakeCommand - 1, Random.nextDouble())

            fun createEntry(hook: InteractionHook): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                    val seed = params["seed"].asLong
                    params.addProperty("seed", seed + idx)
                    if (idx > 0) {
                        params.addProperty("prompt", magicPromptResult[idx - 1])
                    }
                    batch.add(params)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    getScriptForSize(batch[0].asJsonObject.get("size").asInt),
                    hook,
                    null
                )
            }

            val entry = createEntry(event.hook)
            queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}