import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import database.models.ChapterEntry
import dev.minn.jda.ktx.events.onCommand
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.*
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
            val params = event.optionsToJson().withDefaults(getSdJsonDefaults())

            suspend fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                val magicPromptResult =
                    client.magicPrompt(prompt, config.hostConstraints.totalImagesInMakeCommand - 1, Random.nextDouble())

                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val newParams = params.deepCopy()
                    val seed = newParams["seed"].asLong
                    newParams.addProperty("seed", seed + idx)
                    if (idx > 0) {
                        newParams.addProperty("prompt", magicPromptResult[idx - 1])
                    }
                    batch.add(newParams)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    getSdJsonDefaults(),
                    sdHiddenParameters,
                    getScriptForSize(batch[0].asJsonObject.get("size").asInt),
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public
                )
            }

            val embeds = checkForEmbeds(prompt, event.user.idLong)
            if (embeds.first.isNotEmpty()) {
                embedsCallback(jda, event, embeds, params) { _, params, hook ->
                    runBlocking {
                        val entry = createEntry(hook, params)
                        queueDispatcher.queue.addToQueue(entry)
                    }
                }
            }
            else {
                val entry = createEntry(event.hook, params)
                queueDispatcher.queue.addToQueue(entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}