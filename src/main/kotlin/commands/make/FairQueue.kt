package commands.make

import commands.make.diffusion_configs.diffusionConfigInstanceToName
import config
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.TextChannel
import net.dv8tion.jda.api.interactions.InteractionHook
import updateMode

class MemberLimitExceededException(message: String) : Exception(message)

enum class FairQueueType {
    Create, Variate, Upscale
}

data class FairQueueEntry(
    val description: String,
    val type: FairQueueType,
    val owner: String,
    val parameters: List<CreateArtParameters>,
    val progressHook: InteractionHook
) {
    fun getHumanReadableOverview(withDescription: String? = null): String {
        val stringBuilder = StringBuilder()
        stringBuilder.append("**")
        if (withDescription == null) {
            stringBuilder.append(description)
        }
        else {
            stringBuilder.append(withDescription)
        }
        stringBuilder.append("** | ")
        stringBuilder.append(
            "**Preset**: ${diffusionConfigInstanceToName[parameters.first().preset]!!}\n" +
                    "> *${getHumanReadablePrompts()}*\n"
        )
        return stringBuilder.toString()
    }

    fun progressUpdate(message: String) {
        progressHook.editOriginal(message).queue()
    }

    fun progressUpdate(message: String, fileBytes: ByteArray, fileName: String) {
        progressHook.editOriginal(message).retainFiles(listOf()).addFile(fileBytes, fileName).queue()
    }

    fun progressDelete() {
        progressHook.deleteOriginal().queue()
    }

    fun getChannel(): TextChannel {
        return progressHook.interaction.textChannel
    }

    fun getMember(): Member {
        return progressHook.interaction.member!!
    }

    fun getHumanReadablePrompts(): String {
        return parameters.first().prompts.joinToString("|")
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

    fun entryCount(owner: String): Int {
        return queue.count {
            it.owner == owner
        }
    }

    fun deleteLatestEntryByOwner(owner: String) {
        queue.remove(queue.findLast {
            it.owner == owner
        })
    }

    fun addToQueue(entry: FairQueueEntry): String {
        if (updateMode) {
            return "${config.bot.name} is in update mode! New requests are temporarily blocked so we can update the bot, making sure you won't lose any content! Sorry for the inconvenience!"
        }
        val count = entryCount(entry.owner)
        if (count >= config.maxEntriesPerOwner) {
            throw MemberLimitExceededException("${entry.owner} has currently $count items in the queue! Max is ${config.maxEntriesPerOwner}")
        }
        queue.add(entry)
        return entry.getHumanReadableOverview(
            withDescription = "Added to queue"
        )
    }
}