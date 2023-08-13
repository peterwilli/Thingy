import client.QueueEntry
import com.google.gson.JsonArray
import database.chapterDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import ui.makeSelectImageFromQuilt
import utils.bufferedImageToByteArray
import utils.optionsToJson
import utils.takeSlice
import utils.withDefaults
import java.net.URL
import java.util.*
import javax.imageio.ImageIO

fun upscaleImageCommand(jda: JDA) {
    jda.onCommand("upscale") { event ->
        event.deferReply(true).queue()
        val user = userDao.queryBuilder().selectColumns("id", "currentChapterId").where()
            .eq("discordUserID", event.user.id).queryForFirst()

        if (user == null) {
            event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val usingChapter =
            chapterDao.queryBuilder().selectColumns().where()
                .eq("id", user.currentChapterId).and().eq("userID", user.id).queryForFirst()
        if (usingChapter == null) {
            event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        if (usingChapter.chapterType == ChapterEntry.Companion.Type.Image.ordinal) {
            val latestEntry = usingChapter.getLatestEntry()
            val image = ImageIO.read(URL(latestEntry.data))
            val parameters = gson.fromJson(latestEntry.parameters, JsonArray::class.java)
            val quiltSelector = makeSelectImageFromQuilt(
                event.user,
                "Select image for upscaling",
                image,
                parameters.size()
            ) { _, chosenImage ->
                event.hook.editMessage(content = "Processing image... Please wait...", components = listOf(), embeds = listOf()).await()
                val imageSlice = takeSlice(image, parameters.size(), chosenImage)
                val imageB64 = Base64.getEncoder().encodeToString(bufferedImageToByteArray(imageSlice, "png"))
                val batch = JsonArray()
                val params = event.optionsToJson().withDefaults(getSDUpscaleJsonDefaults())
                params.addProperty("image", imageB64)
                val maybeOverride = event.getOption("prompt_override")
                if(maybeOverride == null) {
                    val parsedParameters = gson.fromJson(parameters, JsonArray::class.java)
                    val prompt = parsedParameters[chosenImage].asJsonObject["prompt"].asString
                    params.addProperty("prompt", prompt)
                } else {
                    params.addProperty("prompt", maybeOverride.asString)
                    params.remove("prompt_override")
                }
                batch.add(params)
                val entry = QueueEntry(
                    "Upscale Image", event.member!!.id,
                    batch,
                    getSDUpscaleJsonDefaults(),
                    sdHiddenParameters,
                    event.hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public,
                    "jpg", arrayOf("stable_diffusion_upscale")
                )
                queueClient.uploadEntry(entry)
            }
            event.hook.editMessageToIncludePaginator(quiltSelector).queue()
        } else {
            event.hook.editOriginal("Sorry, you can't upscale a ${usingChapter.chapterType}! Use `/chapters` to select an image chapter!")
                .queue()
        }
    }
}