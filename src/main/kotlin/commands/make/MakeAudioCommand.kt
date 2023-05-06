import client.QueueEntry
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

fun makeAudioCommand(jda: JDA) {
    jda.onCommand("make_audio") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getBarkAudioDefaults())
            fun createEntry(hook: InteractionHook, params: JsonObject): QueueEntry {
                val batch = JsonArray()
                for (idx in 0 until 4) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return QueueEntry(
                    "Making audio",
                    event.member!!.id,
                    batch,
                    getBarkAudioDefaults(),
                    arrayOf(),
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Audio,
                    ChapterEntry.Companion.Visibility.Public,
                    "mp3",
                    arrayOf("bark"),
                    false
                )
            }
            val entry = createEntry(event.hook, params)
            queueClient.uploadEntry(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}