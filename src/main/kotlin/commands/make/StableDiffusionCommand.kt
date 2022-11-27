import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.optionsToJson
import utils.sendException
import utils.withDefaults
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
    return obj
}

fun stableDiffusionCommand(jda: JDA) {
    jda.onCommand("stable_diffusion") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            fun createEntry(hook: InteractionHook): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
                    val seed = params["seed"].asLong
                    params.addProperty("seed", seed + idx)
                    batch.add(params)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    getScriptForSize(batch[0].asJsonObject.get("size").asInt),
                    hook,
                    null
                )
            }

            val entry = createEntry(event.hook)
            queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}