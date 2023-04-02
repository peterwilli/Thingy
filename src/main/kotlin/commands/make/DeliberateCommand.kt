import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import utils.*
import java.util.*
import kotlin.math.pow
import kotlin.random.Random

fun getDeliberateJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("guidance_scale", 7.5)
    obj.addProperty("steps", 25)
    obj.addProperty("negative_prompt", defaultNegative)
    obj.add("embeds", JsonArray(0))
    return obj
}

fun deliberateCommand(jda: JDA) {
    jda.onCommand("deliberate") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getDeliberateJsonDefaults())
            // val embeds = checkForEmbeds(event.getOption("prompt")!!.asString, event.user.idLong)

            fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return FairQueueEntry(
                    "Making Images (Deliberate)",
                    event.member!!.id,
                    batch,
                    getDeliberateJsonDefaults(),
                    sdHiddenParameters,
                    "deliberate",
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg"
                )
            }

            /*if (embeds.first.isNotEmpty()) {
                embedsCallback(jda, event, embeds, params) { _, params, hook ->
                    val entry = createEntry(hook, params)
                    runBlocking {
                        queueDispatcher.queue.addToQueue(entry)
                    }
                }
            }
            else {*/
                val entry = createEntry(event.hook, params)
                queueDispatcher.queue.addToQueue(entry)
            //}
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}