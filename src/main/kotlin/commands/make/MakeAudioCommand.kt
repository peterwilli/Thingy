import client.QueueEntry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import database.models.ChapterEntry
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.*
import kotlin.math.pow
import kotlin.random.Random

fun makeAudioCommand(jda: JDA) {
    jda.onCommand("make_audio") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getBarkAudioDefaults())
            fun createEntry(hook: InteractionHook, params: JsonObject, model: String, scripts: Array<String>): QueueEntry {
                val batch = JsonArray()
                for (idx in 0 until 4) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return QueueEntry(
                    "Making audio (${audioModels.getKey(model)})",
                    event.member!!.id,
                    batch,
                    getBarkAudioDefaults(),
                    arrayOf(),
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Audio,
                    ChapterEntry.Companion.Visibility.Public,
                    "mp3",
                    scripts,
                    true
                )
            }
            val maybeModel = event.getOption("model")
            val entry = when(val model = maybeModel?.asString ?: "musicgen") {
                "musicgen" -> {
                    createEntry(event.hook, params, model, arrayOf("musicgen"))
                }
                "bark" -> {
                    createEntry(event.hook, params, model, arrayOf("bark"))
                }
                else -> {
                    event.reply_("Unknown model: $model").queue()
                    return@onCommand
                }
            }
            queueClient.uploadEntry(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}