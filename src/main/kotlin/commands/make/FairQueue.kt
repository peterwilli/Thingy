package commands.make

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import database.models.UserChapter
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.FileUpload
import updateMode
import utils.sanitize

class MemberLimitExceededException(message: String) : Exception(message)

enum class FairQueueType {
    DiscoDiffusion, StableDiffusion, Variate, Upscale
}

data class FairQueueEntry(
    val description: String,
    val owner: String,
    val parameters: JsonArray,
    val script: String,
    val progressHook: InteractionHook,
    val chapter: UserChapter?
) {
    fun getHumanReadableOverview(withDescription: String? = null): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("**")
        if (withDescription == null) {
            stringBuilder.append(description)
        } else {
            stringBuilder.append(withDescription)
        }
        stringBuilder.append("** | ")
        for((k, v) in parameters[0].asJsonObject.asMap()) {
            if (k.startsWith("_") || v.toString().length > 1000) {
                continue
            }
            stringBuilder.append("`$k`: *${sanitize(v.toString())}* ")
        }
        return stringBuilder.toString()
    }

    suspend fun progressUpdate(message: String): Message {
        return progressHook.editOriginal(message).await()
    }

    suspend fun progressUpdate(message: String, fileBytes: ByteArray, fileName: String): Message {
        return progressHook.editOriginal(message).setFiles(FileUpload.fromData(fileBytes, fileName)).await()
    }

    fun progressDelete() {
        progressHook.deleteOriginal().queue()
    }

    fun getChannel(): MessageChannel {
        return progressHook.interaction.messageChannel
    }

    fun getMember(): Member {
        return progressHook.interaction.member!!
    }
}

class FairQueue(maxEntriesPerOwner: Int) {
    private val queue = mutableListOf<FairQueueEntry>()

    fun next(): FairQueueEntry? {
        if (queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        return null
    }

    suspend fun updateRanks() {
        val commandsGroup =  queue.groupingBy {
            it.script
        }
        val counts = commandsGroup.eachCount()
        queue.sortByDescending {
            counts[it.script]
        }
        if(queue.size == 1) {
            return
        }
        for ((idx, entry) in queue.withIndex()) {
            entry.progressUpdate("*Queued* **#${idx + 1}** | ${entry.getHumanReadableOverview()}")
        }
    }

    private fun entryCount(owner: String): Int {
        return queue.count {
            it.owner == owner
        }
    }

    fun deleteLatestEntryByOwner(owner: String) {
        queue.remove(queue.findLast {
            it.owner == owner
        })
    }

    suspend fun addToQueue(entry: FairQueueEntry) {
        if (updateMode) {
            entry.progressUpdate("${config.bot.name} is in update mode! New requests are temporarily blocked so we can update the bot, making sure you won't lose any content! Sorry for the inconvenience!")
            return
        }
        val count = entryCount(entry.owner)
        if (count >= config.maxEntriesPerOwner) {
            throw MemberLimitExceededException("${entry.owner} has currently $count items in the queue! Max is ${config.maxEntriesPerOwner}")
        }
        queue.add(entry)
        updateRanks()
    }
}