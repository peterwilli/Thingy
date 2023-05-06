package client

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import redis.clients.jedis.Jedis
import utils.asciiProgressBar
import utils.getEta
import java.util.Collections


class QueueClient(private val jedis: Jedis, private val entries: MutableList<QueueEntry> = Collections.synchronizedList(mutableListOf()), private val entriesMutex: Mutex = Mutex()) {

    suspend fun uploadEntry(entry: QueueEntry) {
        val docs = entry.batchToDocs()
        entry.currentStatuses = docs.map { doc ->
            QueueEntryStatus(doc, -1)
        }
        entry.moveToNextScriptIfAny(jedis)
        entriesMutex.withLock {
            entries.add(entry)
        }
    }

    suspend fun getProgressText(entry: QueueEntry, eta: Long) {
        val progressMsg =
            StringBuilder(entry.getHumanReadableOverview())
        progressMsg.append("\n" + asciiProgressBar(entry.getProgress()))
        for((index, status) in entry.currentStatuses!!.withIndex()) {
            if (status.error != null) {
                progressMsg.append("\n**Error** with item `${index + 1}`: `${status.error}`")
            }
        }
        entry.progressUpdate(progressMsg.toString())
    }

    suspend fun checkLoop() {
        val entriesDone = mutableListOf<QueueEntry>()
        while (true) {
            entriesMutex.withLock {
                for(entry in entries) {
                    val timeSinceLastUpdate = entry.timeSinceLastUpdate
                    val lastPercentCompleted = entry.getProgress()
                    val modified = entry.sync(jedis)
                    if(modified) {
                        val eta = getEta(entry.getProgress(), lastPercentCompleted, timeSinceLastUpdate)
                        getProgressText(entry, eta)
                    }
                    entry.moveToNextScriptIfAny(jedis)
                    if (entry.isDone()) {
                        entriesDone.add(entry)
                    }
                }
                for(entry in entriesDone) {
                    entries.remove(entry)
                    entry.clean(jedis)
                }
                entriesDone.clear()
            }
            println("entries: ${entries.size}")
            delay(1000)
        }
    }
}