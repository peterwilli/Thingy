package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import org.jetbrains.annotations.NotNull
import utils.peterDate
import java.net.URL

@DatabaseTable(tableName = "shared_art_cache_entry")
class SharedArtCacheEntry {
    @NotNull
    @DatabaseField(index = true)
    var creationTimestamp: Long = peterDate()

    @NotNull
    @DatabaseField(index = true)
    var userID: Long = 0

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

    constructor(userID: Long, imageURL: URL, index: Int, messageLink: URL) {
        this.userID = userID
        this.imageURL = imageURL.toString()
        this.index = index
        this.messageLink = messageLink.toString()
    }
}