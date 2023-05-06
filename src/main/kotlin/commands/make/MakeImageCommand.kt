package commands.make

import client.QueueEntry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import database.models.ChapterEntry
import defaultNegative
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import getDeliberateJsonDefaults
import getKey
import getSdJsonDefaults
import imageModels
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import queueClient
import sdHiddenParameters
import utils.optionsToJson
import utils.sendException
import utils.withDefaults
import kotlin.math.pow
import kotlin.random.Random

fun getDeepFloydJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("noise_level", 100)
    obj.addProperty("steps", 50)
    obj.addProperty("negative_prompt", defaultNegative)
    obj.add("embeds", JsonArray(0))
    return obj
}

fun makeImageCommand(jda: JDA) {
    jda.onCommand("make_image") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()

            fun createEntry(hook: InteractionHook, params: JsonObject, defaultParams: JsonObject, hiddenParameters: Array<String>, scripts: ArrayList<String>, maxImages: Int, model: String): QueueEntry {
                val batch = JsonArray()
                for (idx in 0 until maxImages) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return QueueEntry("Making Images (${imageModels.getKey(model)})", event.member!!.id,
                    batch,
                    defaultParams,
                    hiddenParameters,
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg", scripts)
            }
            val maybeModel = event.getOption("model")
            val entry = when(val model = maybeModel?.asString ?: "deliberate") {
                "deep_floyd_if" -> {
                    val params = event.optionsToJson().withDefaults(getDeepFloydJsonDefaults())
                    createEntry(event.hook, params, getDeepFloydJsonDefaults(), sdHiddenParameters, arrayListOf("deep_floyd_if"), 1, model)
                }
                "deliberate" -> {
                    val params = event.optionsToJson().withDefaults(getDeliberateJsonDefaults())
                    createEntry(event.hook, params, getDeliberateJsonDefaults(), sdHiddenParameters, arrayListOf("deliberate"), config.hostConstraints.totalImagesInMakeCommand, model)
                }
                "stable_diffusion" -> {
                    val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                    createEntry(event.hook, params, getSdJsonDefaults(), sdHiddenParameters, arrayListOf("deliberate"), config.hostConstraints.totalImagesInMakeCommand, model)
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