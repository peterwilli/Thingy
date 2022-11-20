package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.artContestEntryDao
import database.artContestVoteDao
import database.chapterDao
import database.connectionSource
import org.jetbrains.annotations.NotNull
import utils.peterDate
import java.net.URL

@DatabaseTable(tableName = "art_contest_entry")
class ArtContestEntry {
    @DatabaseField(generatedId = true, index = true, unique = true)
    var id: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var userID: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var messageID: Long = 0

    @NotNull
    @DatabaseField()
    lateinit var prompt: String

    @NotNull
    @DatabaseField()
    lateinit var imageURL: String

    @NotNull
    @DatabaseField()
    lateinit var messageLink: String

    @NotNull
    @DatabaseField()
    var index: Int = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(userID: Long, imageURL: URL, prompt: String, index: Int, messageLink: URL, messageID: Long) {
        this.userID = userID
        this.imageURL = imageURL.toString()
        this.prompt = prompt
        this.index = index
        this.messageLink = messageLink.toString()
        this.messageID = messageID;
    }

    fun delete() {
        TransactionManager.callInTransaction(connectionSource) {
            val votes = artContestVoteDao.deleteBuilder()
            votes.where().eq("contestEntryID", this.id)
            artContestVoteDao.delete(votes.prepare())
            val dbArtContestEntry = artContestEntryDao.deleteBuilder()
            dbArtContestEntry.where().eq("id", id)
            artContestEntryDao.delete(dbArtContestEntry.prepare())
        }
    }
}