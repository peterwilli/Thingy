package commands.make

import client.QueueEntry
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import database.models.ChapterEntry
import defaultNegative
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import getDeepFloydJsonDefaults
import getKadinskyMakeImageJsonDefaults
import getKey
import getPhotonJsonDefaults
import getSdJsonDefaults
import imageModels
import kadinskyHiddenParameters
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
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

            fun createEntry(
                hook: InteractionHook,
                params: JsonObject,
                defaultParams: JsonObject,
                hiddenParameters: Array<String>,
                scripts: Array<String>,
                maxImages: Int,
                model: String
            ): QueueEntry {
                val batch = JsonArray()
                for (idx in 0 until maxImages) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return QueueEntry(
                    "Making Images (${imageModels.getKey(model)})", event.member!!.id,
                    batch,
                    defaultParams,
                    hiddenParameters,
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg", scripts
                )
            }

            suspend fun doGenerate(model: String) {
                val entry = when (model) {
                    "kadinsky" -> {
                        val params = event.optionsToJson().withDefaults(getKadinskyMakeImageJsonDefaults())
                        var task = "prompt"
                        for(i in 0 until 4) {
                            if (params.has("fuse_image_${i + 1}")) {
                                task = "interpolate"
                                break
                            }
                        }
                        if (task == "interpolate") {
                            if (params.has("fuse_weights")) {
                                params.addProperty("weights", params.get("fuse_weights").asString)
                            }
                        }
                        params.addProperty("task", task)
                        params.addProperty("size", params.get("size").asInt)
                        createEntry(
                            event.hook,
                            params,
                            getKadinskyMakeImageJsonDefaults(),
                            kadinskyHiddenParameters,
                            arrayOf("kadinsky_prior", "kadinsky_gen"),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    "deep_floyd_if" -> {
                        val params = event.optionsToJson().withDefaults(getDeepFloydJsonDefaults())
                        createEntry(
                            event.hook,
                            params,
                            getDeepFloydJsonDefaults(),
                            sdHiddenParameters,
                            arrayOf("deep_floyd_if_s1", "deep_floyd_if_s2", "deep_floyd_if_s3", "deep_floyd_if_s4"),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    "deliberate" -> {
                        val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                        createEntry(
                            event.hook,
                            params,
                            getSdJsonDefaults(),
                            sdHiddenParameters,
                            arrayOf("deliberate"),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    "photon" -> {
                        val params = event.optionsToJson().withDefaults(getPhotonJsonDefaults())
                        createEntry(
                            event.hook,
                            params,
                            getPhotonJsonDefaults(),
                            sdHiddenParameters,
                            arrayOf("photon"),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    "stable_diffusion" -> {
                        val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                        val selectedScript = if (params.get("size").asInt == 512) {
                            "stable_diffusion_512"
                        } else {
                            "stable_diffusion_768"
                        }
                        createEntry(
                            event.hook,
                            params,
                            getSdJsonDefaults(),
                            sdHiddenParameters,
                            arrayOf(selectedScript),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    "sd_xl" -> {
                        val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                        createEntry(
                            event.hook,
                            params,
                            getSdJsonDefaults(),
                            sdHiddenParameters,
                            arrayOf("sd_xl"),
                            config.hostConstraints.totalImagesInMakeCommand,
                            model
                        )
                    }

                    else -> {
                        event.hook.editMessage(content = "Unknown model: $model").queue()
                        return
                    }
                }
                queueClient.uploadEntry(entry)
            }

            val prompt = event.getOption("prompt")!!.asString
            val maybeModel = event.getOption("model")
            val model = maybeModel?.asString ?: "deliberate"
            var has_fuse = false
            for(i in 0 until 4) {
                if (event.getOption("fuse_image_${i + 1}") != null) {
                    has_fuse = true
                    break
                }
            }
            if (model != "photon" && !has_fuse && prompt.contains(Regex("realistic|photo|realism", RegexOption.IGNORE_CASE))) {
                val yesBtn = jda.button(
                    label = "Yes",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    event.hook.editMessage(content = "Model selected", components = listOf()).await()
                    doGenerate("photon")
                }
                val noBtn = jda.button(
                    label = "No",
                    style = ButtonStyle.SECONDARY,
                    user = event.user
                ) {
                    event.hook.editMessage(content = "Model selected", components = listOf()).await()
                    doGenerate(model)
                }
                event.hook.editMessage(
                    content = "It seems you try to make a realistic photo, but the model you try to use isn't good at realistic photos, do you want to switch to a more suited model?",
                    components = listOf(
                        ActionRow.of(
                            yesBtn,
                            noBtn
                        )
                    )
                ).queue()
            }
            else if (has_fuse && model != "kadinsky") {
                val yesBtn = jda.button(
                    label = "Yes",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    event.hook.editMessage(content = "Model selected", components = listOf()).await()
                    doGenerate("kadinsky")
                }
                val noBtn = jda.button(
                    label = "No",
                    style = ButtonStyle.SECONDARY,
                    user = event.user
                ) {
                    event.hook.editMessage(content = "Model selected", components = listOf()).await()
                    doGenerate(model)
                }
                event.hook.editMessage(
                    content = "It seems you try to make a fusion without the supported model for it, do you want to switch to a more supported model?",
                    components = listOf(
                        ActionRow.of(
                            yesBtn,
                            noBtn
                        )
                    )
                ).queue()
            }
            else {
                doGenerate(model)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}