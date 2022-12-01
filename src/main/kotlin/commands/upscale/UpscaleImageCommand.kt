package commands.upscale

import commands.make.standardPermissionList
import commands.make.upscale
import commands.make.validatePermissions
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import utils.base64ToBufferedImage
import utils.optionsToJson
import utils.sendException
import utils.withDefaults

fun upscaleImageCommand(jda: JDA) {
    jda.onCommand("upscale_image") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(sdUpscaleDefaults())
            val image = base64ToBufferedImage(params.get("image").asString)
            if (image.width > 1024 || image.height > 1024) {
                event.hook.editOriginal("Sorry! We don't allow upscaling images over 1024 pixels in width or height to prevent gobbling up all computation power! This image is ${image.width}x${image.height}! Remember, we upscale 4x in size!").queue()
                return@onCommand
            }
            upscale(params, event.user, event.hook)
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}