package commands.make

import config

class MemberLimitExceededException(message: String): Exception(message)

data class FairQueueEntry(val owner: String, val parameters: CreateArtParameters)

class FairQueue(maxEntriesPerOwner: Int) {
    private val queue = mutableListOf<FairQueueEntry>()

    fun next(): FairQueueEntry? {
        if(queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        return null
    }

    fun entryCount(owner: String): Int {
        return queue.count {
            it.owner == owner
        }
    }

    fun addToQueue(entry: FairQueueEntry) {
        val count = entryCount(entry.owner)
        if (count >= config.maxEntriesPerOwner) {
            throw MemberLimitExceededException("${entry.owner} has currently $count items in the queue! Max is ${config.maxEntriesPerOwner}")
        }
        queue.add(entry)
    }
}