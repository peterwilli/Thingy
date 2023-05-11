package client

import alphanumericCharPool
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import database.models.ChapterEntry
import database.models.UserChapter
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.messages.reply_
import docarray.Docarray
import docarray.Docarray.DocumentProto
import docarray.documentProto
import eq
import net.dv8tion.jda.api.entities.Member
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.utils.FileUpload
import randomString
import redis.clients.jedis.Jedis
import utils.*
import java.net.URL

class QueueEntryStatus(
    var doc: DocumentProto,
    var scriptIndex: Int = 0,
    var progress: Float = 0.0f,
    var error: String? = null,
    var updatedDoc: DocumentProto? = null
) {
    fun isDone(parent: QueueEntry): Boolean {
        return (scriptIndex == (parent.scripts.size - 1) && progress == 1.0f) || error != null
    }
}

class QueueEntry(
    val description: String,
    val owner: String,
    val parameters: JsonArray,
    val defaultParams: JsonObject,
    val hiddenParameters: Array<String>,
    val progressHook: InteractionHook,
    val chapter: UserChapter?,
    val chapterType: ChapterEntry.Companion.Type,
    val chapterVisibility: ChapterEntry.Companion.Visibility,
    val fileFormat: String,
    val scripts: Array<String>,
    val shouldSaveChapter: Boolean = true,
    var currentStatuses: List<QueueEntryStatus>? = null,
    var timeSinceLastUpdate: Long = 0
) {
    private var newMessage: Message? = null

    suspend fun safeGetMessage(): Message {
        return if (newMessage == null) {
            progressHook.retrieveOriginal().await()
        } else {
            newMessage!!
        }
    }

    fun getMember(): Member {
        return progressHook.interaction.member!!
    }

    suspend fun progressUpdate(message: String): Message {
        newMessage = if (newMessage == null) {
            val originalMessage = progressHook.retrieveOriginal().await()
            originalMessage.reply_(message).await()
        } else {
            newMessage!!.editMessage(message).await()
        }
        return newMessage!!
    }

    suspend fun progressUpdate(message: String, uploads: Array<FileUpload>): Message {
        newMessage = if (newMessage == null) {
            val originalMessage = progressHook.retrieveOriginal().await()
            originalMessage.reply_(message, files = uploads.toList()).await()
        } else {
            newMessage!!.editMessage(message).setFiles(*uploads).await()
        }
        return newMessage!!
    }

    fun getProgress(): Float {
        if (currentStatuses == null) {
            return 0f
        }
        val batchSize = parameters.size()
        var accumulatedProgress = 0f
        for (status in currentStatuses!!) {
            accumulatedProgress += if(status.error == null) {
                status.progress
            }
            else {
                1f
            }
            accumulatedProgress += (1f * status.scriptIndex)
        }
        return (accumulatedProgress / batchSize.toDouble() / scripts.size.toDouble()).toFloat()
    }

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
            if (v.isJsonNull) {
                continue
            }
            if (v.toString().length > 1000) {
                continue
            }
            try {
                val url = URL(v.asString)
                continue
            }
            catch (_: Exception) {
            }
            if (defaultParams.has(k)) {
                if (v == defaultParams.get(k)) {
                    continue
                }
            }
            stringBuilder.append("`$k`: *${sanitize(v.asString)}* ")
        }
        for ((k, v) in parameters[0].asJsonObject.stripHiddenParameters(this.hiddenParameters).asMap()) {
            try {
                val url = URL(v.asString)
                stringBuilder.append("`$k`: $url ")
            }
            catch (_: Exception) {
            }
        }
        return stringBuilder.toString()
    }

    fun sync(con: Jedis) {
        for(currentStatus in currentStatuses!!) {
            val progress = con.hget("entry:${currentStatus.doc.id}", "progress")
            if (progress != null) {
                val progressFloat = progress.toFloat()
                if(!progressFloat.eq(currentStatus.progress)) {
                    currentStatus.progress = progress.toFloat()
                }
            }
            val updatedDoc = con.hget("entry:${currentStatus.doc.id}".toByteArray(), "updatedDoc".toByteArray())
            if (updatedDoc != null) {
                val newUpdatedDoc = DocumentProto.parseFrom(updatedDoc)
                if (newUpdatedDoc.id !=  currentStatus.doc.id) {
                    currentStatus.updatedDoc = newUpdatedDoc
                }
            }
            val error = con.hget("entry:${currentStatus.doc.id}", "error")
            if (error != null) {
                currentStatus.error = error
            }
        }
    }

    private fun addDocToRedis(con: Jedis, doc: DocumentProto, scriptIndex: Int) {
        val scriptId = initScript(con, scripts[scriptIndex])
        val tx = con.multi()
        val docId = doc.id
        val hashKey = "entry:$docId"
        tx.hset(hashKey, "progress", "0.0")
        tx.hset(hashKey.toByteArray(), "doc".toByteArray(), doc.toByteArray())
        tx.hset(hashKey, "scriptID", scriptId)
        tx.lpush("queue:todo:$scriptId", docId)
        tx.zincrby("scriptsInQueue", 1.0, scriptId)
        tx.exec()
    }


    private fun initScript(con: Jedis, name: String): String {
        val scriptContents = getResourceAsText("/scripts/$name.py")
        val hasher = SMLBLAKE3.newInstance()
        hasher.update(scriptContents.toByteArray())
        val scriptId = hasher.hexdigest(16)
        if(!con.exists(scriptId)) {
            con.hset("scripts", scriptId, scriptContents)
        }
        return scriptId
    }

    fun moveToNextScriptIfAny(con: Jedis) {
        for(currentStatus in currentStatuses!!) {
            if (currentStatus.scriptIndex == -1) {
                currentStatus.scriptIndex = 0
                @Suppress("KotlinConstantConditions")
                addDocToRedis(con, currentStatus.doc, currentStatus.scriptIndex)
            }
            if(currentStatus.progress < 1.0f) {
                continue
            }
            val docIDToDelete = currentStatus.doc.id
            if (currentStatus.scriptIndex < scripts.size - 1) {
                currentStatus.scriptIndex += 1
                currentStatus.progress = 0f
                // Get current output doc to merge with the previous input
                val docBytes = con.hget("entry:${currentStatus.doc.id}".toByteArray(), "updatedDoc".toByteArray())
                val doc = DocumentProto.parseFrom(docBytes)
                val mergedDoc = currentStatus.doc.merge(doc)
                currentStatus.doc = mergedDoc
                addDocToRedis(con, mergedDoc, currentStatus.scriptIndex)
            }
            con.del("entry:$docIDToDelete")
        }
    }

    fun isDone(): Boolean {
        return currentStatuses != null && currentStatuses!!.all { currentStatus ->
            currentStatus.isDone(this)
        }
    }

    fun batchToDocs(): Array<Docarray.DocumentProto> {
        return parameters.map { params ->
            val structBuilder = Struct.newBuilder()
            JsonFormat.parser().ignoringUnknownFields().merge(params.toString(), structBuilder)
            documentProto {
                id = randomString(alphanumericCharPool, 32)
                tags = structBuilder.build()
            }
        }.toTypedArray()
    }
}