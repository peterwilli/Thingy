package database.models

import com.google.gson.JsonArray
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.*
import gson
import org.jetbrains.annotations.NotNull

@DatabaseTable(tableName = "thingy")
class Thingy {
    @NotNull
    @DatabaseField()
    var version: Int = 0

    // ORMLite needs a no-arg constructor
    constructor() {
    }

    companion object {
        fun getCurrent(): Thingy {
            var result = thingyDao.queryBuilder().selectColumns().limit(1).queryForFirst()
            if (result == null) {
                result = Thingy()
                thingyDao.create(result)
            }
            return result
        }
    }

    fun runMigration() {
        if (version == 0) {
            TransactionManager.callInTransaction(connectionSource) {
                // Update all chapters/entries
                val entries = chapterEntryDao.queryBuilder().selectColumns().query()
                for (entry in entries) {
                    val parameters = gson.fromJson(entry.parameters, JsonArray::class.java)
                    for (parameter in parameters) {
                        val jsonObj = parameter.asJsonObject
                        if (jsonObj.has("stableDiffusionParameters")) {
                            for ((key, value) in jsonObj.getAsJsonObject("stableDiffusionParameters").asMap()) {
                                jsonObj.add(key, value)
                            }
                        }
                        jsonObj.remove("stableDiffusionParameters")
                        for ((key, value) in jsonObj.deepCopy().asMap()) {
                            if (value.toString().length > 2048) {
                                jsonObj.remove(key)
                            }
                        }
                    }
                    val updateBuilder = chapterEntryDao.updateBuilder()
                    updateBuilder.where().eq("id", entry.id)
                    updateBuilder.updateColumnValue("parameters", parameters.toString().replace("'", "''"))
                    updateBuilder.update()
                }
                val updateBuilder = thingyDao.updateBuilder()
                updateBuilder.updateColumnValue("version", 1)
                updateBuilder.update()
            }
        }
    }
}