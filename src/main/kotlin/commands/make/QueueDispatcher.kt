import commands.make.*
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA

class QueueDispatcher(val jda: JDA) {
    val queue = FairQueue(config.maxEntriesPerOwner)
    private var queueStarted = false
    val channel = ManagedChannelBuilder.forAddress(config.grpcServer.host, config.grpcServer.port).maxInboundMessageSize(1024 * 1024 * 1024).usePlaintext().build()
    val client = Client(channel)

    suspend fun startQueueDispatcher() {
        queueStarted = true
        while(queueStarted) {
            val entry = queue.next()
            if (entry != null) {
                dispatch(entry)
            }
            delay(1000)
        }
    }

    suspend fun dispatch(entry: FairQueueEntry) {
        var inProgress: MutableList<CreateArtParameters> = mutableListOf()
        val prompts = entry.parameters.first().prompts.joinToString("|")
        val replyText = "**${entry.what}**\n> *$prompts*\n"
        entry.progressUpdate(replyText)
        val batch = entry.parameters
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
                        entry.progressUpdate(replyText, quilt, "${config.botName}_progress.jpg")
                    }
                    delay(1000 * 5)
                }
            }

            imageProgress.await()
            entry.progressDelete()
            if (finalImages != null) {
                val quilt = makeQuiltFromByteArrayList(finalImages!!)
                val (upscaleRow, variateRow) = getEditButtons(client, jda, entry.getMember().user, batch)
                entry.getChannel().sendMessage("${entry.getMember().asMention}, we finished your image!\n> *${prompts}*")
                    .addFile(quilt, "${config.botName}_final.jpg").setActionRows(upscaleRow, variateRow).queue()
            }
        }
    }
}