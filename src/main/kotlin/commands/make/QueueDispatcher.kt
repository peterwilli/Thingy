import commands.make.*
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

class QueueDispatcher(private val jda: JDA) {
    val queue = FairQueue(config.maxEntriesPerOwner)
    private var queueStarted = false
    val channel: ManagedChannel = ManagedChannelBuilder.forAddress(config.grpcServer.host, config.grpcServer.port)
        .maxInboundMessageSize(1024 * 1024 * 1024).usePlaintext().build()
    val client = Client(channel)

    suspend fun startQueueDispatcher() {
        queueStarted = true
        while (queueStarted) {
            val entry = queue.next()
            if (entry != null) {
                try {
                    dispatch(entry)
                } catch (e: StatusException) {
                    e.printStackTrace()
                    entry.progressDelete()
                    entry.getChannel()
                        .sendMessage("${entry.getMember().asMention} Connection to the DiscoArt server has failed, it's likely that the bot is offline, we're sorry, please try again later!")
                        .queue()
                } catch (e: Exception) {
                    e.printStackTrace()
                    entry.progressDelete()
                    entry.getChannel()
                        .sendMessage("${entry.getMember().asMention} There's an error in the queue dispatcher: $e")
                        .queue()
                }
            }
            delay(1000)
        }
    }

    private suspend fun dispatch(entry: FairQueueEntry) {
        var inProgress: MutableList<CreateArtParameters> = mutableListOf()
        val prompts = entry.getHumanReadablePrompts()
        entry.progressUpdate(entry.getHumanReadableOverview())
        val batch = entry.parameters
        var cancelled = false
        coroutineScope {
            var finalImages: List<ByteArray>? = null
            async {
                for (params in batch) {
                    if (cancelled) {
                        break
                    }
                    when (entry.type) {
                        FairQueueType.Create -> {
                            client.createArt(params)
                        }
                        FairQueueType.Variate -> {
                            client.variateArt(params)
                        }
                        FairQueueType.Upscale -> {
                            client.upscaleArt(params)
                        }
                    }
                    inProgress.add(params)
                    while (inProgress.size >= config.hostConstraints.maxSimultaneousMakeRequests) {
                        delay(1000)
                    }
                    // Make sure GPU is cleaned up
                    delay(1000)
                }
            }
            val imageProgress = async {
                var ticksWithoutUpdate = 0
                var lastPercentCompleted: Double = 0.0
                while (true) {
                    val newImages = mutableListOf<ByteArray>()
                    var completedCount = 0
                    var avgPercentCompleted: Double = 0.0
                    for (params in batch) {
                        val retrieveArtResult = if(entry.type == FairQueueType.Upscale) {
                            client.retrieveUpscaleArt(params.artID)
                        }
                        else {
                            client.retrieveArt(params.artID)
                        }
                        if (retrieveArtResult.completed) {
                            completedCount++
                            inProgress.remove(params)
                        }
                        avgPercentCompleted += retrieveArtResult.statusPercent
                        if (retrieveArtResult.images.isNotEmpty()) {
                            // For now, we use one image per batch to make sure we can let users experiment with different variables simultaneously
                            val image = retrieveArtResult.images.first()
                            newImages.add(image)
                        }
                    }
                    avgPercentCompleted /= batch.size
                    if (completedCount == batch.size) {
                        finalImages = newImages
                        break
                    }
                    if (newImages.isEmpty() || avgPercentCompleted == lastPercentCompleted) {
                        ticksWithoutUpdate++
                        val updateThresHold = if (newImages.isEmpty()) {
                            config.timeouts.imageNotAppearing
                        } else {
                            config.timeouts.imageNotUpdating
                        }
                        if (ticksWithoutUpdate > updateThresHold) {
                            inProgress.clear()
                            cancelled = true
                            val tryAgainButton = jda.button(
                                label = "Try again",
                                style = ButtonStyle.PRIMARY,
                                user = entry.getMember().user
                            ) {
                                try {
                                    val tryAgainEntry = entry.copy(progressHook = it.hook)
                                    queueDispatcher.queue.addToQueue(tryAgainEntry)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    it.reply_("Error! $e").setEphemeral(true).queue()
                                }
                            }
                            entry.getChannel()
                                .sendMessage("${entry.getMember().asMention}, Sorry, generation for this prompt has timed out!\n> *${prompts}*")
                                .setActionRow(listOf(tryAgainButton))
                                .queue()
                            break
                        }
                    } else {
                        lastPercentCompleted = avgPercentCompleted
                        ticksWithoutUpdate = 0
                        val quilt = makeQuiltFromByteArrayList(newImages)
                        entry.progressUpdate(entry.getHumanReadableOverview(), quilt, "${config.bot.name}_progress.jpg")
                    }
                    delay(1000 * 5)
                }
            }
            imageProgress.await()
            entry.progressDelete()
            if (finalImages != null) {
                val quilt = makeQuiltFromByteArrayList(finalImages!!)
                val (upscaleRow, variateRow) = getEditButtons(client, jda, entry.getMember().user, batch)
                var finishMsg = entry.getChannel()
                    .sendMessage("${entry.getMember().asMention}, we finished your image!\n> *${prompts}*")
                    .addFile(quilt, "${config.bot.name}_final.jpg")
                if (entry.type != FairQueueType.Upscale) {
                    finishMsg = finishMsg.setActionRows(upscaleRow, variateRow)
                }
                finishMsg.queue()
            }
        }
    }
}