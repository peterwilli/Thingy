package commands.make

import botName
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent

suspend fun variate(
    client: Client,
    buttonInteractionEvent: ButtonInteractionEvent,
    imageIndex: Int,
    params: CreateArtParameters
) {
    try {
        coroutineScope {
            val replyText = "**Making variation for image #${imageIndex + 1}**\n> *${params.prompts.joinToString("|")}*"
            val replyMessage = buttonInteractionEvent.reply(replyText).submit().await()
            var finalImages: List<ByteArray>? = null
            val artCreator = async {
                finalImages = client.variateArt(params, imageIndex)
            }
            val imageProgress = async {
                while (artCreator.isActive) {
                    val images = client.retrieveArt(params.artID)
                    if (images.isEmpty()) {
                        delay(1000 * 5)
                    } else {
                        val quilt = makeQuiltFromByteArrayList(images)
                        replyMessage.editOriginal(replyText).retainFiles(listOf())
                            .addFile(quilt, "${botName}_variate_${imageIndex}_progress.jpg").queue()
                        delay(1000 * 20)
                    }
                }
            }
            artCreator.await()
            if (finalImages != null) {
                val quilt = makeQuiltFromByteArrayList(finalImages!!)
                val (upscaleRow, variateRow) = getEditButtons(client, buttonInteractionEvent.jda, buttonInteractionEvent.user, params)
                buttonInteractionEvent.message.reply_(
                    "${buttonInteractionEvent.member!!.asMention}, we finished making variations to image!\n> *${
                        params.prompts.joinToString(
                            "|"
                        )
                    }*"
                ).addFile(quilt!!, "${botName}_variate_${imageIndex}.png").setActionRows(upscaleRow, variateRow).queue()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}