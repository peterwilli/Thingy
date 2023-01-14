package database.models

import com.google.gson.JsonArray
import com.j256.ormlite.field.DatabaseField
import com.j256.ormlite.misc.TransactionManager
import com.j256.ormlite.table.DatabaseTable
import database.*
import gson
import org.jetbrains.annotations.NotNull

private const val LATEST_VERSION = 3

@DatabaseTable(tableName = "thingy")
class Thingy {
    @NotNull
    @DatabaseField()
    var version: Int = 2

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
        while (version < LATEST_VERSION) {
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
            if (version == 1) {
                TransactionManager.callInTransaction(connectionSource) {
                    chapterEntryDao.executeRaw("ALTER TABLE `chapter_entry` ADD COLUMN metadata TEXT;")
                    chapterEntryDao.executeRaw("ALTER TABLE `chapter_entry` ADD COLUMN chapterVisibility INT NOT NULL default 0;")
                    chapterEntryDao.executeRaw("ALTER TABLE `chapter_entry` ADD COLUMN chapterType INT NOT NULL default 0;")
                    chapterEntryDao.executeRaw("ALTER TABLE `chapter_entry` RENAME COLUMN imageURL TO data;")
                    chapterDao.executeRaw("ALTER TABLE `user_chapter` ADD COLUMN chapterType INT;")
                    val updateBuilder = thingyDao.updateBuilder()
                    updateBuilder.updateColumnValue("version", 2)
                    updateBuilder.update()
                }
            }
            if (version == 2) {
                TransactionManager.callInTransaction(connectionSource) {
                    val entries = chapterEntryDao.queryBuilder().selectColumns("chapterID", "metadata").where().eq("chapterType", ChapterEntry.Companion.Type.TrainedModel.ordinal).query()
                    val entriesToOwner = entries.groupBy {
                        val chapter = chapterDao.queryBuilder().selectColumns("userID").where().eq("id", it.chapterID).queryForFirst()
                        chapter.userID
                    }
                    for ((owner, entries) in entriesToOwner) {
                        val entriesToWords = entries.groupBy {
                            it.metadata!!
                        }
                        for((word, entries) in entriesToWords) {
                            if (entries.size > 1) {
                                println("${entries.size} duplicate entries for '$word' owned by $owner, leaving 1...")
                            }
                            for (entry in entries.take(entries.size - 1)) {
                                entry.delete()
                            }
                        }
                    }
                    val updateBuilder = thingyDao.updateBuilder()
                    updateBuilder.updateColumnValue("version", 3)
                    updateBuilder.update()
                }
            }
            version = getCurrent().version
        }
    }
}