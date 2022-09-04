package database.models

import com.j256.ormlite.field.DataType
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import database.chapterDao
import database.chapterEntryDao
import database.userDao
import org.jetbrains.annotations.NotNull
import utils.peterDate
import java.util.*

@DatabaseTable(tableName = "user_chapter")
class UserChapter {
    @DatabaseField(generatedId = true)
    var id: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var updateTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField()
    var userScopedID: Long = 0

    @NotNull
    @DatabaseField(index = true)
    var userID: Long = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(userID: Long, userScopedID: Long) {
        this.userID = userID
        this.userScopedID = userScopedID
    }

    fun getEntries(): Array<ChapterEntry> {
        return chapterEntryDao.queryBuilder().orderBy("creationTimestamp", false).selectColumns().where()
            .eq("chapterID", this.id).query().toTypedArray()
    }

    fun getLatestEntry(): ChapterEntry {
        return chapterEntryDao.queryBuilder().limit(1).orderBy("creationTimestamp", false).selectColumns().where()
            .eq("chapterID", this.id).query().first()
    }
}