package utils

import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao


fun checkForEmbeds(prompt: String, userId: Long): Pair<Array<String>, Array<String>> {
    val result = mutableListOf<String>()
    val embeds = mutableListOf<String>()

    val user = userDao.queryBuilder().selectColumns("id").where()
        .eq("discordUserID", userId).queryForFirst()

    val split = prompt.replace(",", "").split(" ")
    for (word in split) {
        val results = chapterEntryDao.queryBuilder().where().eq("metadata", word.lowercase()).and()
            .eq("chapterType", ChapterEntry.Companion.Type.TrainedModel.ordinal).query()
        for (entry in results) {
            if (entry.chapterVisibility == ChapterEntry.Companion.Visibility.Public.ordinal) {
                result.add(entry.metadata!!)
                embeds.add(entry.data)
                continue
            }

            if (user != null) {
                val chapter =
                    chapterDao.queryBuilder().selectColumns().where()
                        .eq("id", entry.chapterID).and().eq("userID", user.id).queryForFirst()
                if (chapter != null) {
                    result.add(entry.metadata!!)
                    embeds.add(entry.data)
                }
            }
        }
    }
    return result.toTypedArray() to embeds.toTypedArray()
}