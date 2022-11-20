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

@DatabaseTable(tableName = "art_contest_vote")
class ArtContestVote {
    @DatabaseField(generatedId = true, index = true, unique = true)
    var id: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var userID: Long = 0

    @NotNull
    @DatabaseField()
    var contestEntryID: Long = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(userID: Long, contestEntryID: Long) {
        this.userID = userID
        this.contestEntryID = contestEntryID
    }

    fun delete() {
        val dbArtContestVote = artContestVoteDao.deleteBuilder()
        dbArtContestVote.where().eq("id", id)
        artContestVoteDao.delete(dbArtContestVote.prepare())
    }
}