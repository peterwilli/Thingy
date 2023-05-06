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

fun getBarkAudioDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.add("embeds", JsonArray(0))
    return obj
}

fun makeAudioCommand(jda: JDA) {
    jda.onCommand("make_audio") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getBarkAudioDefaults())

            fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                val batch = JsonArray()
                for (idx in 0 until 1) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return FairQueueEntry(
                    "Making audio",
                    event.member!!.id,
                    batch,
                    getBarkAudioDefaults(),
                    arrayOf(),
                    "bark",
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Audio,
                    ChapterEntry.Companion.Visibility.Public,
                    "mp3",
                    false
                )
            }

            val entry = createEntry(event.hook, params)
            //queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}