package client

import com.j256.ormlite.misc.TransactionManager
import commands.make.makeQuiltFromByteArrayList
import database.chapterDao
import database.chapterEntryDao
import database.connectionSource
import database.models.ChapterEntry
import database.models.User
import database.models.UserChapter
import database.userDao
import dev.minn.jda.ktx.messages.reply_
import docarray.Docarray.DocumentProto
import gson
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.utils.FileUpload
import redis.clients.jedis.Jedis
import utils.asciiProgressBar
import utils.base64UriToByteArray
import utils.getEta
import utils.stripHiddenParameters
import java.net.URL
import java.util.Collections
import kotlin.math.floor


class QueueClient(
    private val jedis: Jedis,
    private val entries: MutableList<QueueEntry> = Collections.synchronizedList(mutableListOf()),
    private val entriesMutex: Mutex = Mutex()
) {

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
        for ((index, status) in entry.currentStatuses!!.withIndex()) {
            if (status.error != null) {
                progressMsg.append("\n**Error** with item `${index + 1}`: `${status.error}`")
            }
        }
        return progressMsg.toString()
    }

    private fun getAttachements(entry: QueueEntry): Array<FileUpload> {
        val result = mutableListOf<FileUpload>()
        val updatedDocs = mutableListOf<DocumentProto>()
        for (status in entry.currentStatuses!!) {
            if (status.updatedDoc == null) {
                continue
            }
            updatedDocs.add(status.updatedDoc!!)
        }
        if (updatedDocs.isEmpty()) {
            return arrayOf()
        }
        if (entry.chapterType == ChapterEntry.Companion.Type.Image) {
            val prog = entry.getProgress()
            val upload = FileUpload.fromData(
                makeQuiltFromByteArrayList(updatedDocs.map {
                    it.base64UriToByteArray()
                }, "jpg"), if (entry.getProgress() == 1f) {
                    "thingy_final.jpg"
                } else {
                    "thingy_progress_${floor((prog * 100).toDouble()).toUInt()}_pct.jpg"
                }
            )
            result.add(upload)
        }
        if (entry.chapterType == ChapterEntry.Companion.Type.Audio) {
            val prog = entry.getProgress()
            if (entry.currentStatuses != null) {
                for (status in entry.currentStatuses!!) {
                    if (!status.isDone(entry)) {
                        continue
                    }

                    val upload = FileUpload.fromData(status.updatedDoc!!.base64UriToByteArray(), if (entry.getProgress() == 1f) {
                        "thingy_final.mp3"
                    } else {
                        "thingy_progress_${floor((prog * 100).toDouble()).toUInt()}_pct.mp3"
                    })
                    result.add(upload)
                }
            }
        }
        return result.toTypedArray()
    }

    suspend fun updatePreview(entry: QueueEntry, text: String) {
        val uploads = getAttachements(entry)
        if (uploads.isEmpty()) {
            entry.progressUpdate(text)
        }
        else {
            entry.progressUpdate(text, uploads)
        }
    }

    suspend fun checkLoop() {
        val entriesUpdated = mutableMapOf<QueueEntry, String>()
        while (true) {
            entriesMutex.withLock {
                println("entries: ${entries.size}")
                for (entry in entries) {
                    val timeSinceLastUpdate = entry.timeSinceLastUpdate
                    val lastPercentCompleted = entry.getProgress()
                    val modified = entry.sync(jedis)
                    if (modified) {
                        val eta = getEta(entry.getProgress(), lastPercentCompleted, timeSinceLastUpdate)
                        entriesUpdated[entry] = getProgressText(entry, eta)
                    }
                    entry.moveToNextScriptIfAny(jedis)
                }
            }
            for ((entry, text) in entriesUpdated) {
                updatePreview(entry, text)
                if (entry.isDone()) {
                    val message = entry.safeGetMessage()
                    message.reply_("${entry.getMember().asMention}, we finished your entry!\n> *${entry.description}*")
                        .queue()
                    maybeSaveChapter(entry, message)

                    entries.remove(entry)
                    entry.clean(jedis)
                }
            }
            entriesUpdated.clear()
            delay(1000)
        }
    }

    private fun maybeSaveChapter(entry: QueueEntry, finishMsg: Message) {
        assert(entry.isDone())
        TransactionManager.callInTransaction(connectionSource) {
            val userQuery = userDao.queryBuilder().where().eq("discordUserID", entry.owner)
            val user = if (userQuery.countOf() == 0L) {
                val newUser = User(entry.owner)
                userDao.create(newUser)
                userQuery.query().first()!!
            } else {
                userQuery.query().first()!!
            }
            val possibleLastChapter =
                chapterDao.queryBuilder().selectColumns("id").orderBy("creationTimestamp", false)
                    .limit(1)
                    .queryForFirst()
            val chapterID = if (possibleLastChapter == null) {
                0
            } else {
                possibleLastChapter.id + 1
            }

            if (entry.shouldSaveChapter || finishMsg.attachments.size > 0) {
                val chapter = UserChapter(
                    id = chapterID,
                    chapterType = entry.chapterType.ordinal,
                    userID = user.id
                )
                chapterDao.create(chapter)
                val chapterEntry = ChapterEntry(
                    chapterID = chapterID,
                    chapterType = entry.chapterType,
                    chapterVisibility = entry.chapterVisibility,
                    serverID = finishMsg.guild.id,
                    channelID = finishMsg.channel.id,
                    messageID = finishMsg.id,
                    data = URL(finishMsg.attachments.first().url).toString(),
                    parameters = gson.toJson(entry.parameters.stripHiddenParameters(entry.hiddenParameters))
                )
                chapterEntryDao.create(chapterEntry)
            }

            val updateBuilder = userDao.updateBuilder()
            updateBuilder.where().eq("id", user.id)
            updateBuilder.updateColumnValue("generationsDone", user.generationsDone + 1L)
            updateBuilder.updateColumnValue("currentChapterId", chapterID)
            updateBuilder.update()
        }
    }
}