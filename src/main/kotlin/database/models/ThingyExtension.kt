package database.models

import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.table.DatabaseTable
import net.dv8tion.jda.api.entities.User
import org.jetbrains.annotations.NotNull

@DatabaseTable(tableName = "thingy_extension")
class ThingyExtension {
    @DatabaseField(generatedId = true)
    var id: Long = 0

    @NotNull
    @DatabaseField()
    lateinit var code: String

    @NotNull
    @DatabaseField()
    lateinit var owner: String

    @NotNull
    @DatabaseField(index = true)
    var enabled: Boolean = false


    // ORMLite needs a no-arg constructor
    constructor() {
    }

    constructor(owner: User, code: String) {
        this.owner = owner.id
        this.code = code
    }
}