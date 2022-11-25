import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.sendException
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
                var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map { idx ->
                    val params = optionsToStableDiffusionParams(event, idx)
                    if (idx == 0) {
                        params
                    }
                    else {
                        params.copy(
                            stableDiffusionParameters = params.stableDiffusionParameters!!.copy(
                                prompt = magicPromptResult[idx - 1]
                            )
                        )
                    }
                }
                return FairQueueEntry(
                    "Making Images",
                    FairQueueType.StableDiffusion,
                    event.member!!.id,
                    batch,
                    hook,
                    null
                )
            }

            val entry = createEntry(event.hook)
            event.hook.editOriginal(queueDispatcher.queue.addToQueue(entry)).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}