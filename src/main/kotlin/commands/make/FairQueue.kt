package commands.make

data class FairQueueEntry(val owner: String, val parameters: CreateArtParameters)
    
class FairQueue(maxEntriesPerOwner: Int) {
    private val queue = mutableListOf<FairQueueEntry>()

    fun next(): FairQueueEntry? {
        if(queue.isNotEmpty()) {
            return queue.removeAt(0)
        }
        return null
    }

    fun addToQueue(entry: FairQueueEntry) {
        queue.add(entry)
    }
}