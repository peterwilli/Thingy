import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import database.models.ChapterEntry
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.*
import kotlin.math.pow
import kotlin.random.Random

fun getEditImageJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("steps", 25)
    obj.addProperty("negative_prompt", defaultNegative)
    return obj
}

fun editImageCommand(jda: JDA) {
    jda.onCommand("edit_image") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val defaults = getEditImageJsonDefaults()
            val params = event.optionsToJson().withDefaults(defaults)
            defaults.addProperty("seed", 0)

            fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                val batch = JsonArray()
                batch.add(params)
                return FairQueueEntry(
                    "Editing image",
                    event.member!!.id,
                    batch,
                    defaults,
                    sdHiddenParameters,
                    "edit_image",
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg"
                )
            }
            val entry = createEntry(event.hook, params)
            queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}