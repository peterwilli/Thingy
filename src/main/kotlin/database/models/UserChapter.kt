package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import database.chapterDao
import database.userDao
import org.jetbrains.annotations.NotNull

@DatabaseTable(tableName = "user_chapter")
class UserChapter {
    @DatabaseField(generatedId = true)
    var id: Long = 0

    @NotNull
    @DatabaseField()
    var userScopedID: Long? = null

    @NotNull
    @DatabaseField(index = true)
    var userID: Long? = null

    @NotNull
    @DatabaseField(index = true)
    var serverID: String? = null

    @NotNull
    @DatabaseField()
    var channelID: String? = null

    @NotNull
    @DatabaseField()
    var parameters: String? = null

    @NotNull
    @DatabaseField()
    var messageID: String? = null

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(userID: Long, serverID: String, channelID: String, messageID: String, userScopedID: Long, parameters: String) {
        this.userID = userID
        this.serverID = serverID
        this.channelID = channelID
        this.messageID = messageID
        this.userScopedID = userScopedID
        this.parameters = parameters
    }
}