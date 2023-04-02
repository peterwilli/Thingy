import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.FairQueueEntry
import commands.make.getScriptForSize
import commands.make.standardPermissionList
import commands.make.validatePermissions
import database.models.ChapterEntry
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.*
import kotlin.math.pow
import kotlin.random.Random

fun getSdJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("size", 512)
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("guidance_scale", 9.0)
    obj.addProperty("steps", 25)
    obj.addProperty("negative_prompt", "out of frame, lowres, text, error, cropped, worst quality, low quality, jpeg artifacts, ugly, duplicate, morbid, mutilated, out of frame, extra fingers, mutated hands, poorly drawn hands, poorly drawn face, mutation, deformed, blurry, dehydrated, bad anatomy, bad proportions, extra limbs, cloned face, disfigured, gross proportions, malformed limbs, missing arms, missing legs, extra arms, extra legs, fused fingers, too many fingers, long neck, username, watermark, signature,")
    obj.add("embeds", JsonArray(0))
    return obj
}

val sdHiddenParameters = arrayOf("embeds")

fun stableDiffusionCommand(jda: JDA) {
    jda.onCommand("stable_diffusion") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
            val embeds = checkForEmbeds(event.getOption("prompt")!!.asString, event.user.idLong)

            fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    getSdJsonDefaults(),
                    sdHiddenParameters,
                    getScriptForSize(batch[0].asJsonObject.get("size").asInt),
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg"
                )
            }

            if (embeds.first.isNotEmpty()) {
                embedsCallback(jda, event, embeds, params) { _, params, hook ->
                    val entry = createEntry(hook, params)
                    runBlocking {
                        queueDispatcher.queue.addToQueue(entry)
                    }
                }
            }
            else {
                val entry = createEntry(event.hook, params)
                queueDispatcher.queue.addToQueue(entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}