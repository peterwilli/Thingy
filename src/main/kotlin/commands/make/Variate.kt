package commands.make

import botName
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import hostConstraints
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import kotlin.math.pow
import kotlin.random.Random

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

            var batch: MutableList<CreateArtParameters> = mutableListOf()
            for (i in 0 until hostConstraints.maxSimultaneousMakeRequests) {
                val newParams = params.copy(seed = Random.nextInt(0, 2.toDouble().pow(32).toInt()))
                batch.add(newParams)
            }

            for(params in batch) {
                client.variateArt(params, imageIndex)
            }

            val imageProgress = async {
                while (true) {
                    val newImages = mutableListOf<ByteArray>()
                    var completedCount = 0
                    for(params in batch) {
                        val (images, completed) = client.retrieveArt(params.artID)
                        if (completed) {
                            completedCount++
                        }
                        if (images.isNotEmpty()) {
                            // For now, we use one image per batch to make sure we can let users experiment with different variables simultaneously
                            newImages.add(images.first())
                        }
                    }
                    if (completedCount == batch.size) {
                        finalImages = newImages
                        break
                    }
                    if(newImages.isNotEmpty()) {
                        val quilt = makeQuiltFromByteArrayList(newImages)
                        replyMessage.editOriginal(replyText).retainFiles(listOf())
                            .addFile(quilt, "${botName}_variate_progress.jpg").queue()
                    }
                    delay(1000 * 5)
                }
            }
            imageProgress.await()
            if (finalImages != null) {
                val quilt = makeQuiltFromByteArrayList(finalImages!!, formatName = "png")
                val (upscaleRow, variateRow) = getEditButtons(client, buttonInteractionEvent.jda, buttonInteractionEvent.user, batch)
                buttonInteractionEvent.message.reply_(
                    "${buttonInteractionEvent.member!!.asMention}, we finished making variations to image!\n> *${
                        params.prompts.joinToString(
                            "|"
                        )
                    }*"
                ).addFile(quilt!!, "${botName}_variate.png").setActionRows(upscaleRow, variateRow).queue()
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
    }
}