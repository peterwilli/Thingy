package commands.upscale

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.standardPermissionList
import commands.make.upscale
import commands.make.validatePermissions
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import editMessageToIncludePaginator
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import ui.makeSelectImageFromQuilt
import utils.*
import java.net.URL
import javax.imageio.ImageIO

fun sdUpscaleDefaults(): JsonObject {
    val result = JsonObject()
    result.addProperty("guidance_scale", 9.0)
    result.addProperty("original_image_slice", 8)
    result.addProperty("noise_level", 50)
    result.addProperty("tile_border", 32)
    return result
}

fun upscaleCommand(jda: JDA) {
    jda.onCommand("upscale") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            val user =
                userDao.queryBuilder().selectColumns("id", "currentChapterId").where()
                    .eq("discordUserID", event.user.id)
                    .queryForFirst()
            if (user == null) {
                event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            val usingChapter =
                chapterDao.queryBuilder().selectColumns().where().eq("id", user.currentChapterId).and()
                    .eq("userID", user.id).queryForFirst()

            if (usingChapter == null) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            event.deferReply(true).queue()
            val latestEntry = usingChapter.getLatestEntry()
            val parameters = gson.fromJson(latestEntry.parameters, JsonArray::class.java)
            val image = ImageIO.read(URL(latestEntry.imageURL))

            val quiltSelector = makeSelectImageFromQuilt(
                event.user,
                "Select your image to upscale!",
                image,
                parameters.size()
            ) { btnEvent, chosenImage ->
                val parameterToUpscale = parameters[chosenImage].asJsonObject
                val imageSlice = takeSlice(image, parameters.size(), chosenImage)
                if (imageSlice.width > 1024 || image.height > 1024) {
                    btnEvent.reply_("Sorry! We don't allow upscaling images over 1024 pixels in width or height to prevent gobbling up all computation power! This image is ${imageSlice.width}x${imageSlice.height}! Remember, we upscale 4x in size!")
                        .setEphemeral(true).queue()
                    return@makeSelectImageFromQuilt
                }
                btnEvent.reply_("Sending upscale...").await()
                val base64Image = bufferedImageToBase64(imageSlice)
                val params = event.optionsToJson().withDefaults(sdUpscaleDefaults())
                params.addProperty("prompt", parameterToUpscale.get("prompt").asString)
                params.addProperty("image", base64Image)
                upscale(params, event.user, btnEvent.hook)
            }
            event.hook.editMessageToIncludePaginator(quiltSelector).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}