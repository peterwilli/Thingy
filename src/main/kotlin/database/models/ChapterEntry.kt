package database.models

import com.google.gson.JsonArray
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.chapterDao
import database.chapterEntryDao
import database.connectionSource
import gson
import org.jetbrains.annotations.NotNull
import utils.peterDate
import java.net.URL

@DatabaseTable(tableName = "chapter_entry")
class ChapterEntry {
    @DatabaseField(generatedId = true)
    var id: Long = 0

    @NotNull
    @DatabaseField()
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var chapterID: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var chapterType: Int = 0

    @NotNull
    @DatabaseField(index = true)
    var chapterVisibility: Int = 0

    @DatabaseField(index = true)
    var metadata: String? = null

    @NotNull
    @DatabaseField(index = true)
    lateinit var serverID: String

    @NotNull
    @DatabaseField()
    lateinit var channelID: String

    @NotNull
    @DatabaseField()
    lateinit var parameters: String

    @NotNull
    @DatabaseField()
    lateinit var messageID: String

    @NotNull
    @DatabaseField()
    lateinit var data: String

    companion object {
        enum class Type {
            Image,
            TrainedModel,
            Audio
        }

        enum class Visibility {
            Private,
            Public
        }
    }

    // ORMLite needs a no-arg constructor
    constructor()

    constructor(
        chapterID: Long,
        chapterType: Type,
        chapterVisibility: Visibility,
        data: String,
        serverID: String,
        channelID: String,
        messageID: String,
        parameters: String
    ) {
        this.chapterID = chapterID
        this.chapterType = chapterType.ordinal
        this.chapterVisibility = chapterVisibility.ordinal
        this.data = data
        this.serverID = serverID
        this.channelID = channelID
        this.messageID = messageID
        this.parameters = parameters
    }

    fun delete() {
        TransactionManager.callInTransaction(connectionSource) {
            val dbEntry = chapterEntryDao.deleteBuilder()
            dbEntry.where().eq("id", id)
            chapterEntryDao.delete(dbEntry.prepare())

            // Clear empty chapters (if any)
            val otherEntries = chapterEntryDao.queryBuilder().selectColumns("id").where().eq("chapterID", this.chapterID).countOf()
            if (otherEntries > 0) {
                val chapterEntry = chapterDao.deleteBuilder()
                chapterEntry.where().eq("id", this.chapterID)
                chapterDao.delete(chapterEntry.prepare())
            }
        }
    }

    fun getDescription(): String {
        val parsedParameters = gson.fromJson(parameters, JsonArray::class.java)
        val firstParams = parsedParameters[0].asJsonObject

        val description = if(firstParams.has("prompt")) {
            firstParams.get("prompt").asString
        }
        else if(firstParams.has("instructions")) {
            firstParams.get("instructions").asString
        }
        else {
            "No prompt"
        }
        return description
    }
}