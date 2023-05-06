package commands.make

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import database.models.ChapterEntry
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.InteractionHook


val upscaleHiddenParameters = arrayOf("image")

suspend fun upscale(
    params: JsonObject,
    user: User,
    hook: InteractionHook
) {
    val array = JsonArray()
    params.addProperty("_hf_auth_token", config.bot.hfToken)
    array.add(params)
    val entry = FairQueueEntry(
        "Upscaling image",
        user.id,
        array,
        JsonObject(),
        upscaleHiddenParameters,
        "stable_diffusion_upscale",
        hook,
        null,
        ChapterEntry.Companion.Type.Image,
        ChapterEntry.Companion.Visibility.Public,
        "jpg"
    )
    //queueDispatcher.queue.addToQueue(entry)
}