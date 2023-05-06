package client

import commands.make.makeQuiltFromByteArrayList
import database.models.ChapterEntry
import dev.minn.jda.ktx.messages.reply_
import docarray.Docarray.DocumentProto
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.utils.FileUpload
import redis.clients.jedis.Jedis
import utils.asciiProgressBar
import utils.base64UriToByteArray
import utils.getEta
import java.util.Collections
import kotlin.math.floor


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
        entry.progressHook.editOriginal(entry.getHumanReadableOverview(null)).queue()
        entry.progressUpdate("Queued")
    }

    private fun getProgressText(entry: QueueEntry, eta: Long): String {
        val progressMsg =
            StringBuilder(entry.getHumanReadableOverview())
        progressMsg.append("\n" + asciiProgressBar(entry.getProgress()))
        for((index, status) in entry.currentStatuses!!.withIndex()) {
            if (status.error != null) {
                progressMsg.append("\n**Error** with item `${index + 1}`: `${status.error}`")
            }
        }
        return progressMsg.toString()
    }

    fun getAttachements(entry: QueueEntry): Array<FileUpload> {
        val result = mutableListOf<FileUpload>()
        val updatedDocs = mutableListOf<DocumentProto>()
        for (status in entry.currentStatuses!!) {
            if (status.updatedDoc == null) {
                continue
            }
            updatedDocs.add(status.updatedDoc!!)
        }

        if (entry.chapterType == ChapterEntry.Companion.Type.Image) {
            val prog = entry.getProgress()
            val upload = FileUpload.fromData(makeQuiltFromByteArrayList(updatedDocs.map {
                it.base64UriToByteArray()
            }, "jpg"), if (entry.getProgress() == 1f) {
                "thingy_final.jpg"
            } else {
                "thingy_progress_${floor((prog * 100).toDouble())}_pct.jpg"
            })
            result.add(upload)
        }
        return result.toTypedArray()
    }

    suspend fun updatePreview(entry: QueueEntry, text: String) {
        val uploads = getAttachements(entry)
        entry.progressUpdate(text, uploads)
    }

    suspend fun checkLoop() {
        val entriesDone = mutableListOf<QueueEntry>()
        val entriesUpdated = mutableMapOf<QueueEntry, String>()
        while (true) {
            entriesMutex.withLock {
                println("entries: ${entries.size}")
                for(entry in entries) {
                    val timeSinceLastUpdate = entry.timeSinceLastUpdate
                    val lastPercentCompleted = entry.getProgress()
                    val modified = entry.sync(jedis)
                    if(modified) {
                        val eta = getEta(entry.getProgress(), lastPercentCompleted, timeSinceLastUpdate)
                        entriesUpdated[entry] = getProgressText(entry, eta)
                    }
                    entry.moveToNextScriptIfAny(jedis)
                    if (entry.isDone()) {
                        entriesDone.add(entry)
                        entry.safeGetMessage().reply_("${entry.getMember().asMention}, we finished your entry!\n> *${entry.description}*")
                            .queue()
                    }
                }
                for(entry in entriesDone) {
                    entries.remove(entry)
                    entry.clean(jedis)
                }
                entriesDone.clear()
            }
            for ((entry, text) in entriesUpdated) {
                updatePreview(entry, text)
            }
            entriesUpdated.clear()
            delay(1000)
        }
    }
}