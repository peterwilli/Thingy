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
import getDeepFloydJsonDefaults
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

fun makeImageCommand(jda: JDA) {
    jda.onCommand("make_image") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()

            fun createEntry(hook: InteractionHook, params: JsonObject, defaultParams: JsonObject, hiddenParameters: Array<String>, scripts: Array<String>, maxImages: Int, model: String): QueueEntry {
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
                    createEntry(event.hook, params, getDeepFloydJsonDefaults(), sdHiddenParameters, arrayOf("deep_floyd_if_s1", "deep_floyd_if_s2", "deep_floyd_if_s3", "deep_floyd_if_s4"), config.hostConstraints.totalImagesInMakeCommand, model)
                }
                "deliberate" -> {
                    val params = event.optionsToJson().withDefaults(getDeliberateJsonDefaults())
                    createEntry(event.hook, params, getDeliberateJsonDefaults(), sdHiddenParameters, arrayOf("deliberate"), config.hostConstraints.totalImagesInMakeCommand, model)
                }
                "stable_diffusion" -> {
                    val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                    val selectedScript = if(params.get("size").asInt == 512) {
                        "stable_diffusion_512"
                    }
                    else {
                        "stable_diffusion_768"
                    }
                    createEntry(event.hook, params, getSdJsonDefaults(), sdHiddenParameters, arrayOf(selectedScript), config.hostConstraints.totalImagesInMakeCommand, model)
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