package commands.make

import config
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import utils.byteArrayImageTo

suspend fun upscale(
    client: Client,
    buttonInteractionEvent: ButtonInteractionEvent,
    imageIndex: Int,
    params: CreateArtParameters
) {
    try {
        coroutineScope {
            val replyText = "**Upscaling image #${imageIndex + 1}**\n> *${params.prompts.joinToString("|")}*"
            val replyMessage = buttonInteractionEvent.reply(replyText).submit().await()
            var finalImage: ByteArray? = null
            client.upscaleArt(params, imageIndex)
            val imageProgress = async {
                while (true) {
                    val (images, completed) = client.retrieveArt(params.artID)
                    if (images.isNotEmpty()) {
                        if (completed) {
                            finalImage = images.first()
                            break
                        }
                        replyMessage.editOriginal(replyText).retainFiles(listOf())
                            .addFile(byteArrayImageTo(images[0], "jpeg"), "${config.botName}_upscale_progress.jpg").queue()
                    }
                    delay(1000 * 20)
                }
            }
            imageProgress.await()
            if (finalImage != null) {
                buttonInteractionEvent.message.reply_(
                    "${buttonInteractionEvent.member!!.asMention}, we finished upscaling your image!\n> *${
                        params.prompts.joinToString(
                            "|"
                        )
                    }*"
                ).addFile(finalImage!!, "${config.botName}_up_${imageIndex + 1}.png").queue()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}