package commands.make

import com.google.gson.JsonArray
import config
import database.models.ChapterEntry
import database.models.UserChapter
import dev.minn.jda.ktx.coroutines.await
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.FileUpload
import updateMode
import utils.sanitize
import utils.stripHiddenParameters

class MemberLimitExceededException(message: String) : Exception(message)

data class FairQueueEntry(
    val description: String,
    val owner: String,
    val parameters: JsonArray,
    val hiddenParameters: Array<String>,
    val script: String,
    val progressHook: InteractionHook,
    val chapter: UserChapter?,
    val chapterType: ChapterEntry.Companion.Type,
    val chapterVisibility: ChapterEntry.Companion.Visibility
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
        for ((k, v) in parameters[0].asJsonObject.stripHiddenParameters(this.hiddenParameters).asMap()) {
            if (v.toString().length > 1000) {
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

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as FairQueueEntry

        if (description != other.description) return false
        if (owner != other.owner) return false
        if (parameters != other.parameters) return false
        if (!hiddenParameters.contentEquals(other.hiddenParameters)) return false
        if (script != other.script) return false
        if (progressHook != other.progressHook) return false
        if (chapter != other.chapter) return false

        return true
    }

    override fun hashCode(): Int {
        var result = description.hashCode()
        result = 31 * result + owner.hashCode()
        result = 31 * result + parameters.hashCode()
        result = 31 * result + hiddenParameters.contentHashCode()
        result = 31 * result + script.hashCode()
        result = 31 * result + progressHook.hashCode()
        result = 31 * result + (chapter?.hashCode() ?: 0)
        return result
    }
}

class FairQueue {
    private val queue = mutableListOf<FairQueueEntry>()

    fun next(): FairQueueEntry? {
        if (queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        return null
    }

    suspend fun updateRanks() {
        val commandsGroup = queue.groupingBy {
            it.script
        }
        val counts = commandsGroup.eachCount()
        queue.sortByDescending {
            counts[it.script]
        }
        if (queue.size == 1) {
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