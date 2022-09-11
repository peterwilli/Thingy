package commands.make

import alphanumericCharPool
import config
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import randomString
import utils.bufferedImageToDataURI
import java.net.URI
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.pow
import kotlin.random.Random

data class StableDiffusionParameters(
    val prompt: String,
    var initImage: URI? = null,
    var ratio: Ratio = Ratio(),
    var steps: Int = 50,
    val guidanceScale: Double? = null,
    val strength: Double? = null
)

fun optionsToStableDiffusionParams(
    event: GenericCommandInteractionEvent,
    imageIndex: Int
): DiffusionParameters {
    val prompt = event.getOption("prompt")!!.asString

    val seed = if (event.getOption("seed") == null) {
        Random.nextInt(0, 2.toDouble().pow(32).toInt())
    } else {
        try {
            event.getOption("seed")!!.asInt + imageIndex
        } catch (e: Exception) {
            println("Warning, seed '${event.getOption("seed")!!.asString}' invalid! Using $imageIndex instead...")
            e.printStackTrace()
            imageIndex
        }
    }

    val params = DiffusionParameters(
        seed = seed,
        artID = "${config.bot.name}-${randomString(alphanumericCharPool, 32)}",
        stableDiffusionParameters = StableDiffusionParameters(
            prompt = prompt
        ),
        discoDiffusionParameters = null
    )

    val arOption = event.getOption("ar")
    if (arOption != null) {
        try {
            val split = arOption.asString.split(":")
            params.stableDiffusionParameters!!.ratio = Ratio(w = split[0].toInt(), h = split[1].toInt())
        } catch (e: Exception) {
            throw Exception("AR formatting *${arOption.asString}* in prompt *${prompt}* is invalid! Example: 16:9")
        }
    }

    val attachmentOption = event.getOption("input_image")
    if (attachmentOption != null) {
        val image = ImageIO.read(URL(attachmentOption.asAttachment.url))
        val base64Init = bufferedImageToDataURI(image)
        params.stableDiffusionParameters!!.initImage = base64Init
    }

    return params
}