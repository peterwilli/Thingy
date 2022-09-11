package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.chapterDao
import database.chapterEntryDao
import database.connectionSource
import org.jetbrains.annotations.NotNull
import utils.peterDate

@DatabaseTable(tableName = "user_chapter")
class UserChapter {
    @DatabaseField(index = true, unique = true)
    var id: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var updateTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var userID: Long = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(userID: Long) {
        this.userID = userID
    }

    fun getEntries(): Array<ChapterEntry> {
        return chapterEntryDao.queryBuilder().orderBy("creationTimestamp", false).selectColumns().where()
            .eq("chapterID", this.id).query().toTypedArray()
    }

    fun getLatestEntry(): ChapterEntry {
        return chapterEntryDao.queryBuilder().limit(1).orderBy("creationTimestamp", false).selectColumns().where()
            .eq("chapterID", this.id).query().first()
    }

    fun delete() {
        TransactionManager.callInTransaction(connectionSource) {
            val dbEntry = chapterEntryDao.deleteBuilder()
            dbEntry.where().eq("chapterID", id)
            chapterEntryDao.delete(dbEntry.prepare())

            val dbChapter = chapterDao.deleteBuilder()
            dbChapter.where().eq("id", id)
            chapterDao.delete(dbChapter.prepare())
        }
    }
}