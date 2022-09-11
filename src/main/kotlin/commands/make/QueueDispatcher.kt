package commands.make

import com.j256.ormlite.misc.TransactionManager
import config
import database.chapterDao
import database.chapterEntryDao
import database.connectionSource
import database.models.ChapterEntry
import database.models.User
import database.models.UserChapter
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import discoart.RetrieveArtResult
import gson
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.StatusException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import queueDispatcher
import utils.peterDate
import java.net.URL
import kotlin.math.max

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
                    try {
                        e.printStackTrace()
                        entry.progressDelete()
                        entry.getChannel()
                            .sendMessage("${entry.getMember().asMention} There's an error in the queue dispatcher: $e")
                            .queue()
                    } catch (e: Exception) {
                        println("Error here means the bot most likely tries to post in a message it doesn't have access to!")
                        e.printStackTrace()
                    }
                }
            }
            delay(1000)
        }
    }

    private suspend fun dispatch(entry: FairQueueEntry) {
        var inProgress: MutableList<Any> = mutableListOf()
        val prompts = entry.getHumanReadablePrompts()
        entry.progressUpdate(entry.getHumanReadableOverview())
        val batch = entry.parameters
        val mockRetrieveArtMap: MutableMap<String, ByteArray> = mutableMapOf()
        var cancelled = false
        coroutineScope {
            var finalImages: List<ByteArray>? = null
            async {
                for (params in batch) {
                    if (cancelled) {
                        break
                    }
                    when (entry.type) {
                        FairQueueType.DiscoDiffusion -> {
                            client.createDiscoDiffusionArt(params)
                        }

                        FairQueueType.StableDiffusion -> {
                            val art = client.createStableDiffusionArt(params)
                            mockRetrieveArtMap[params.artID] = art.first()
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
                        val retrieveArtResult = if (entry.type == FairQueueType.Upscale) {
                            client.retrieveUpscaleArt(params.artID)
                        } else {
                            if (params.stableDiffusionParameters != null) {
                                val art = mockRetrieveArtMap[params.artID]
                                val result = RetrieveArtResult(
                                    statusPercent = if (art == null) {
                                        0.0
                                    } else {
                                        1.0
                                    },
                                    images = if (art == null) {
                                        listOf()
                                    } else {
                                        listOf(art)
                                    },
                                    completed = art != null
                                )
                                result
                            } else {
                                client.retrieveArt(params.artID)
                            }
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
                    println("completedCount: $completedCount, batch.size: ${batch.size}")
                    if (completedCount == batch.size) {
                        finalImages = newImages
                        break
                    }
                    if (newImages.isEmpty() || avgPercentCompleted == lastPercentCompleted) {
                        ticksWithoutUpdate++
                        var imageNotAppearing = 0
                        var imageNotUpdating = 0
                        for (params in batch) {
                            val timeout = if (params.stableDiffusionParameters != null) {
                                config.timeouts.stableDiffusion
                            } else {
                                config.timeouts.discoDiffusion
                            }
                            imageNotAppearing = max(timeout.imageNotAppearing, imageNotAppearing)
                            imageNotUpdating = max(timeout.imageNotUpdating, imageNotUpdating)
                        }
                        val updateThresHold = if (newImages.isEmpty()) {
                            imageNotAppearing
                        } else {
                            imageNotUpdating
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
            if (finalImages != null) {
                println("SJfdsJFD")
                val quilt = makeQuiltFromByteArrayList(finalImages!!)
                val finishMsg = entry.progressUpdate(entry.getHumanReadableOverview(),  quilt,
                    "${config.bot.name}_final.jpg")
                finishMsg.reply_("${entry.getMember().asMention}, we finished your image!\n> *${prompts}*").queue()
                var userScopedID: Long = 0
                if (entry.chapter == null) {
                    val userQuery = userDao.queryBuilder().where().eq("discordUserID", entry.owner)
                    val user = if (userQuery.countOf() == 0L) {
                        val newUser = User(entry.owner)
                        userDao.create(newUser)
                        userQuery.query().first()!!
                    } else {
                        userQuery.query().first()!!
                    }
                    TransactionManager.callInTransaction(connectionSource) {
                        userScopedID = chapterDao.queryBuilder().where().eq("userID", user.id).countOf()
                        val chapter = UserChapter(
                            userID = user.id,
                            userScopedID = userScopedID
                        )
                        chapterDao.create(chapter)
                        val chapterID = chapterDao.queryBuilder().selectColumns("id").countOf()
                        val chapterEntry = ChapterEntry(
                            chapterID = chapterID,
                            serverID = finishMsg.guild!!.id,
                            channelID = finishMsg.channel.id,
                            messageID = finishMsg.id,
                            imageURL = URL(finishMsg.attachments.first().url),
                            parameters = gson.toJson(entry.parameters)
                        )
                        chapterEntryDao.create(chapterEntry)
                    }
                } else {
                    userScopedID = entry.chapter.userScopedID
                    TransactionManager.callInTransaction(connectionSource) {
                        val chapterEntry = ChapterEntry(
                            chapterID = entry.chapter.id,
                            serverID = finishMsg.guild!!.id,
                            channelID = finishMsg.channel.id,
                            messageID = finishMsg.id,
                            imageURL = URL(finishMsg.attachments.first().url),
                            parameters = gson.toJson(entry.parameters)
                        )
                        chapterEntryDao.create(chapterEntry)
                        val updateBuilder = chapterDao.updateBuilder()
                        updateBuilder.where().eq("id", entry.chapter.id)
                        updateBuilder.updateColumnValue("updateTimestamp", peterDate())
                        updateBuilder.update()
                    }
                }
                TransactionManager.callInTransaction(connectionSource) {
                    val user = userDao.queryBuilder().selectColumns("id", "generationsDone").where()
                        .eq("discordUserID", entry.owner).queryForFirst()
                    val updateBuilder = userDao.updateBuilder()
                    updateBuilder.where().eq("id", user.id)
                    updateBuilder.updateColumnValue("generationsDone", user.generationsDone + 1L)
                    updateBuilder.updateColumnValue("currentChapterId", userScopedID)
                    updateBuilder.update()
                }
            }
        }
    }
}