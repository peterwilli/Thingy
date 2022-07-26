import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import io.grpc.ManagedChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA

fun makeCommand(jda: JDA, channel: ManagedChannel) {
    val queue = FairQueue(1)
    jda.onCommand("make") { event ->
        try {
            val client = Client(channel)
            var batch: MutableList<CreateArtParameters> = mutableListOf()
            var inProgress: MutableList<CreateArtParameters> = mutableListOf()
            for (i in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                val params = optionsToParams(event) ?: return@onCommand
                batch.add(params)
            }
            val prompts = event.getOption("prompts")!!.asString
            val replyText = "**Generating image**\n> *${prompts}*"
            event.reply_(replyText).queue()
            coroutineScope {
                var finalImages: List<ByteArray>? = null
                async {
                    for(params in batch) {
                        client.createArt(params)
                        inProgress.add(params)
                        while (inProgress.size >= config.hostConstraints.maxSimultaneousMakeRequests) {
                            delay(1000)
                        }
                        // Make sure GPU is cleaned up
                        delay(1000 * 5)
                    }
                }
                val imageProgress = async {
                    while (true) {
                        val newImages = mutableListOf<ByteArray>()
                        var completedCount = 0
                        for(params in batch) {
                            val (images, completed) = client.retrieveArt(params.artID)
                            if (completed) {
                                completedCount++
                                inProgress.remove(params)
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
                            event.hook.editOriginal(replyText).retainFiles(listOf())
                                .addFile(quilt, "${config.botName}_progress.jpg").queue()
                        }
                        delay(1000 * 5)
                    }
                }

                imageProgress.await()
                event.hook.deleteOriginal().queue()
                if (finalImages != null) {
                    val quilt = makeQuiltFromByteArrayList(finalImages!!)
                    val (upscaleRow, variateRow) = getEditButtons(client, jda, event.user, batch)
                    event.textChannel!!.sendMessage("${event.member!!.asMention}, we finished your image!\n> *${prompts}*")
                        .addFile(quilt, "${config.botName}_final.jpg").setActionRows(upscaleRow, variateRow).queue()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}