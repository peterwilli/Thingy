import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.standardPermissionList
import commands.make.validatePermissions
import database.models.ChapterEntry
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import utils.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max
import kotlin.math.pow
import kotlin.random.Random

fun getEditImageJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("steps", 20)
    obj.addProperty("guidance_scale", 9.0)
    obj.addProperty("input_scale", 1.0)
    return obj
}

fun editImageCommand(jda: JDA) {
    jda.onCommand("edit_image") { event ->
        event.reply_("Todo!").setEphemeral(true).queue()
        /*
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val attachment = event.getOption("image")!!.asAttachment
            if (!attachment.isImage) {
                event.reply_(
                    "Only images are supported!"
                ).setEphemeral(true).queue()
                return@onCommand
            }
            val defaults = getEditImageJsonDefaults()
            val params = event.optionsToJson {
                var image = ImageIO.read(ByteArrayInputStream(it))
                image = image.upscaleToMinSize(768)
                image = image.resizeKeepRatio(768, true)
                if (max(image.width, image.height) > 1024) {
                    image = image.resizeKeepRatio(768, false)
                }
                val baos = ByteArrayOutputStream()
                ImageIO.write(image, "jpg", baos)
                baos.toByteArray()
            }.withDefaults(defaults)
            params.addProperty("original_url", attachment.url)
            defaults.addProperty("seed", 0)

            fun createEntry(hook: InteractionHook, params: JsonObject): FairQueueEntry {
                val batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return FairQueueEntry(
                    "Editing image",
                    event.member!!.id,
                    batch,
                    defaults,
                    sdHiddenParameters,
                    "edit_image",
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg"
                )
            }

            val entry = createEntry(event.hook, params)
//            //queueDispatcher.queue.addToQueue(entry)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
         */
    }
}