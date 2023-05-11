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

fun removeBackgroundImageCommand(jda: JDA) {
    jda.onCommand("remove_background_from_image") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson()

            fun createEntry(hook: InteractionHook, params: JsonObject): QueueEntry {
                val batch = JsonArray()
                batch.add(params)
                return QueueEntry("Remove background from image", event.member!!.id,
                    batch,
                    JsonObject(),
                    sdHiddenParameters,
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "png", arrayOf("remove_bg_image")
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