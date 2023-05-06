package commands.train

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.j256.ormlite.misc.TransactionManager
import commands.make.*
import config
import database.chapterDao
import database.chapterEntryDao
import database.connectionSource
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import isLettersOrDigits
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

import utils.*

fun sdTrainDefaults(): JsonObject {
    val result = JsonObject()
    result.addProperty("steps", 100)
    result.addProperty("learning_rate", 2e-3)
    return result
}

val trainHiddenParameters = (0 until 4).map { i ->
    "image_${i + 1}"
}.toTypedArray()

fun trainCommand(jda: JDA) {
    jda.onCommand("train") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().queue()
            val params = event.optionsToJson().withDefaults(sdTrainDefaults())
            val word = params["word"].asString
            if (!(word.isLettersOrDigits() && word.lowercase() == word)) {
                event.hook.editMessage(content = "**Error!** Words need to be fully alphanumeric and lowercase!").queue()
                return@onCommand
            }
            suspend fun dispatch() {
                val array = JsonArray()
                params.addProperty("_hf_auth_token", config.bot.hfToken)
                array.add(params)
                val entry = FairQueueEntry(
                    "Learning concept",
                    event.user.id,
                    array,
                    sdTrainDefaults(),
                    trainHiddenParameters,
                    "ti_booster",
                    event.hook,
                    null,
                    ChapterEntry.Companion.Type.TrainedModel,
                    ChapterEntry.Companion.Visibility.Private,
                    "jpg"
                )
                //queueDispatcher.queue.addToQueue(entry)
            }
            val results = chapterEntryDao.queryBuilder().where().eq("metadata", word.lowercase()).and()
                    .eq("chapterType", ChapterEntry.Companion.Type.TrainedModel.ordinal).query().groupBy {
                    val chapter = chapterDao.queryBuilder().selectColumns("userID").where().eq("id", it.chapterID).queryForFirst()
                    chapter.userID
                }

            val user =
                userDao.queryBuilder().selectColumns("id").where()
                    .eq("discordUserID", event.user.id)
                    .queryForFirst()

            if (user != null && results[user.id] != null && results[user.id]!!.isNotEmpty()) {
                event.hook.editMessage(
                    content = "You already trained $word once before. Do you want to delete it and train a new one?"
                ).setActionRow(listOf(
                    jda.button(
                        label = "Cancel",
                        style = ButtonStyle.PRIMARY,
                        user = event.user
                    ) {
                        it.hook.editMessage(content = "Cancelled!").setComponents(listOf()).queue()
                    },
                    jda.button(
                        label = "Replace model",
                        style = ButtonStyle.DANGER,
                        user = event.user
                    ) {
                        it.hook.editMessage(content = "Working...").setComponents(listOf()).queue {
                            TransactionManager.callInTransaction(connectionSource) {
                                for (entry in results[user.id]!!) {
                                    entry.delete()
                                }
                            }
                            runBlocking {
                                dispatch()
                            }
                        }
                    }
                )).queue()
            }
            else {
                dispatch()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}