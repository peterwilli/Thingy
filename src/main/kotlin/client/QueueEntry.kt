package client

import alphanumericCharPool
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.protobuf.Struct
import com.google.protobuf.util.JsonFormat
import database.models.ChapterEntry
import database.models.UserChapter
import docarray.Docarray
import docarray.Docarray.DocumentProto
import docarray.documentProto
import eq
import net.dv8tion.jda.api.interactions.InteractionHook
import randomString
import redis.clients.jedis.Jedis
import utils.getResourceAsText
import utils.merge

class QueueEntryStatus(
    var doc: DocumentProto,
    var scriptIndex: Int = 0,
    var progress: Float = 0.0f
) {
    fun isDone(parent: QueueEntry): Boolean {
        return scriptIndex == (parent.scripts.size - 1) && progress == 1.0f
    }
}

class QueueEntry(
    val description: String,
    val owner: String,
    val parameters: JsonArray,
    val defaultParams: JsonObject,
    val hiddenParameters: Array<String>,
    val progressHook: InteractionHook?,
    val chapter: UserChapter?,
    val chapterType: ChapterEntry.Companion.Type,
    val chapterVisibility: ChapterEntry.Companion.Visibility,
    val fileFormat: String,
    val scripts: List<String>,
    val shouldSaveChapter: Boolean = true,
    var currentStatuses: List<QueueEntryStatus>? = null
) {

    fun sync(con: Jedis): Boolean {
        var modified = false
        for(currentStatus in currentStatuses!!) {
            val progress = con.hget("entry:${currentStatus.doc.id}", "progress")
            if (progress != null) {
                val progressFloat = progress.toFloat()
                if(!progressFloat.eq(currentStatus.progress)) {
                    currentStatus.progress = progress.toFloat()
                    modified = true
                }
            }
        }
        return modified
    }

    fun clean(con: Jedis) {
        for(currentStatus in currentStatuses!!) {
            if (currentStatus.isDone(this)) {
                con.del("entry:${currentStatus.doc.id}")
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
        tx.zadd("scriptsInQueue", mapOf(scriptId to 1.0))
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
            if (currentStatus.scriptIndex < scripts.size - 1) {
                currentStatus.scriptIndex += 1
                // Get current output doc to merge with the previous input
                val docBytes = con.hget("entry:${currentStatus.doc!!.id}".toByteArray(), "updatedDoc".toByteArray())
                val doc = DocumentProto.parseFrom(docBytes)
                val mergedDoc = currentStatus.doc.merge(doc)
                currentStatus.doc = mergedDoc
                addDocToRedis(con, mergedDoc, currentStatus.scriptIndex)
            }
        }
    }

    fun isDone(): Boolean {
        return currentStatuses != null && currentStatuses!!.any { currentStatus ->
            println("currentStatus.isDone(this): ${currentStatus.isDone(this)}")
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