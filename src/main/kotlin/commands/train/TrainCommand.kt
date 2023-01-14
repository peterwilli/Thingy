package commands.train

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import commands.upscale.sdUpscaleDefaults
import config
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import isLettersOrDigits
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.FileUpload
import queueDispatcher
import utils.*

fun sdTrainDefaults(): JsonObject {
    val result = JsonObject()
    result.addProperty("steps", 100)
    result.addProperty("learning_rate", 2e-3)
    return result
}

val trainHiddenParameters = (0 until 4).map { i ->
    "image_${i + 1}"
}.toTypedArray()

fun trainCommand(jda: JDA) {
    jda.onCommand("train") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().queue()
            val params = event.optionsToJson().withDefaults(sdTrainDefaults())
            val word = params["word"].asString
            if (!(word.isLettersOrDigits() && word.lowercase() == word)) {
                event.hook.editMessage(content = "**Error!** Words need to be fully alphanumeric and lowercase!").queue()
                return@onCommand
            }
            val array = JsonArray()
            params.addProperty("_hf_auth_token", config.bot.hfToken)
            array.add(params)
            val entry = FairQueueEntry(
                "Learning concept",
                event.user.id,
                array,
                trainHiddenParameters,
                "ti_booster",
                event.hook,
                null,
                ChapterEntry.Companion.Type.TrainedModel,
                ChapterEntry.Companion.Visibility.Private
            )
            queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}