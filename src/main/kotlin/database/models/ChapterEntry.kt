package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
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
    lateinit var imageURL: String

    companion object {
        enum class Type {
            Image,
            TrainedModel
        }

        enum class Visibility {
            Private,
            Public
        }
    }

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(
        chapterID: Long,
        chapterType: Type,
        chapterVisibility: Visibility,
        imageURL: URL,
        serverID: String,
        channelID: String,
        messageID: String,
        parameters: String
    ) {
        this.chapterID = chapterID
        this.chapterType = chapterType.ordinal
        this.chapterVisibility = chapterVisibility.ordinal
        this.imageURL = imageURL.toString()
        this.serverID = serverID
        this.channelID = channelID
        this.messageID = messageID
        this.parameters = parameters
    }
}