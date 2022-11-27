package commands.make

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import queueDispatcher
import java.awt.image.BufferedImage

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
        "stable_diffusion_upscale",
        hook,
        null
    )
    queueDispatcher.queue.addToQueue(entry)
}