package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import net.dv8tion.jda.api.entities.User
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
    var generationsDone: Long? = null

    @DatabaseField()
    var currentChapterId: Long? = null

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(user: User) {
        this.discordUserID = user.id
    }
}