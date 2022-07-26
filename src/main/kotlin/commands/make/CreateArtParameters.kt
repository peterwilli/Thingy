package commands.make

import alphanumericCharPool
import commands.make.diffusion_configs.diffusionConfigs
import commands.make.diffusion_configs.standardSmall
import config
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import randomString
import java.net.URL
import kotlin.math.pow
import kotlin.random.Random

data class CreateArtParameters(
    val artID: String,
    val seed: Int,
    val prompts: List<String>,
    var initImage: String? = null,
    var ratio: Ratio = Ratio(),
    var preset: DiffusionConfig
)

fun optionsToParams(event: GenericCommandInteractionEvent): CreateArtParameters? {
    val prompts = event.getOption("prompts")!!.asString
    val preset = if (event.getOption("preset") == null) {
        standardSmall
    }
    else {
        val presetStr = event.getOption("preset")!!.asString
        diffusionConfigs[presetStr]!!.first
    }
    val seed = if (event.getOption("seed") == null) {
        Random.nextInt(0, 2.toDouble().pow(32).toInt())
    }
    else {
        event.getOption("seed")!!.asInt
    }

    val params = CreateArtParameters(
        seed = seed,
        artID = "${config.botName}-${randomString(alphanumericCharPool, 32)}",
        prompts = prompts.split("|"),
        preset = preset
    )

    val arOption = event.getOption("ar")
    if (arOption != null) {
        try {
            val split = arOption.asString.split(":")
            params.ratio = Ratio(w = split[0].toInt(), h = split[1].toInt())
        } catch (e: Exception) {
            event.reply_("AR formatting *${arOption.asString}* in prompt *${prompts}* is invalid! Example: 16:9")
                .queue()
            return null
        }
    }

    val initImageOption = event.getOption("init_image")
    if(initImageOption != null) {
        try {
            val imageURL = URL(initImageOption.asString)
            params.initImage = imageURL.toString()
        } catch (e: Exception) {
            event.reply_("Image URL is invalid! Make sure init_image is set to a valid link!")
                .queue()
            return null
        }
    }
    return params
}