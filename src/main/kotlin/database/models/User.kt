package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import database.userDao
import org.jetbrains.annotations.NotNull

@DatabaseTable(tableName = "user")
class User {
    @DatabaseField(generatedId = true)
    var id: Long = 0

    @NotNull
    @DatabaseField(index = true, unique = true)
    var discordUserID: String? = null

    @NotNull
    @DatabaseField(defaultValue = "0")
    var generationsDone: Long = 0

    @DatabaseField()
    var backgroundURL: String? = null

    @DatabaseField()
    var profileTextColor: String? = null

    @DatabaseField()
    var currentChapterId: Long = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(user: String) {
        this.discordUserID = user
    }

    fun updateSelectedChapter(chapterId: Long) {
        val updateBuilder = userDao.updateBuilder()
        updateBuilder.where().eq("id", id)
        updateBuilder.updateColumnValue("currentChapterId", chapterId)
        updateBuilder.update()
    }
}