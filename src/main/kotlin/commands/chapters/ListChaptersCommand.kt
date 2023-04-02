package commands.chapters

import com.google.gson.JsonArray
import com.j256.ormlite.misc.TransactionManager
import database.chapterDao
import database.connectionSource
import database.models.ChapterEntry
import database.models.UserChapter
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import replyPaginator
import ui.GetImageCallback
import ui.ImageSliderEntry
import ui.sendImageSlider
import utils.sanitize
import java.net.URL

fun listChaptersCommand(jda: JDA) {
    jda.onCommand("chapters") { event ->
        try {
            val user =
                userDao.queryBuilder().selectColumns("id").where().eq("discordUserID", event.user.id).queryForFirst()
            if (user == null) {
                event.reply_("User not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            var chapterType = if(event.getOption("type") == null) {
                "images"
            }
            else {
                event.getOption("type")!!.asString
            }

            if (chapterType == "images") {
                val chaptersCount =
                    chapterDao.queryBuilder().selectColumns("id").where()
                        .eq("userID", user.id)
                        .and().eq("chapterType", ChapterEntry.Companion.Type.Image.ordinal).countOf()
                if (chaptersCount == 0L) {
                    event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                        .setEphemeral(true).queue()
                    return@onCommand
                }
                var lastSelectedChapter: UserChapter? = null
                val onImage: GetImageCallback = { index ->
                    lastSelectedChapter =
                        chapterDao.queryBuilder().selectColumns().limit(1).offset(index).orderBy("creationTimestamp", false)
                            .where()
                            .eq("userID", user.id)
                            .and().eq("chapterType", ChapterEntry.Companion.Type.Image.ordinal)
                            .queryForFirst()

                    val latestEntry = lastSelectedChapter!!.getLatestEntry()
                    val description = latestEntry.getDescription()
                    ImageSliderEntry(
                        description = description,
                        image = URL(latestEntry.data)
                    )
                }
                val slider = sendImageSlider("My Chapters", chaptersCount, onImage)
                slider.customActionComponents = listOf(jda.button(
                    label = "Select",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    val parameters =
                        gson.fromJson(
                            lastSelectedChapter!!.getLatestEntry().parameters,
                            JsonArray::class.java
                        )
                    user.updateSelectedChapter(lastSelectedChapter!!.id)
                    it.reply_(
                        "${
                            sanitize(parameters[0].asJsonObject.get("prompt").asString)
                        } is now your current chapter! You can use editing commands such as `/upscale`, `/variate` to edit it! Enjoy!"
                    ).setEphemeral(true).queue()
                }, jda.button(
                    label = "\uD83D\uDDD1️",
                    style = ButtonStyle.DANGER,
                    user = event.user
                ) { deleteEvent ->
                    deleteEvent.reply_(
                        "**Are you sure to delete this chapter?** *${
                            sanitize(
                                lastSelectedChapter!!.getLatestEntry().getDescription()
                            )
                        }*"
                    )
                        .setEphemeral(true).addActionRow(listOf(
                            jda.button(
                                label = "Delete!",
                                style = ButtonStyle.DANGER,
                                user = event.user
                            ) {
                                lastSelectedChapter!!.delete()
                                TransactionManager.callInTransaction(connectionSource) {
                                    // If we don't have a chapter selected anymore we likely deleted a selected chapter.
                                    val usingChapter =
                                        chapterDao.queryBuilder().selectColumns().where().eq("id", user.currentChapterId)
                                            .and()
                                            .eq("userID", user.id).queryForFirst()
                                    if (usingChapter == null) {
                                        val possibleLastChapter =
                                            chapterDao.queryBuilder().selectColumns("id").limit(1)
                                                .orderBy("creationTimestamp", false).where().eq("userID", user.id)
                                                .queryForFirst()
                                        if (possibleLastChapter != null) {
                                            user.updateSelectedChapter(possibleLastChapter.id)
                                        }
                                    }
                                }
                                deleteEvent.hook.editMessage(content = "*Deleted!*").setComponents().queue()
                            },
                            jda.button(
                                label = "Keep!",
                                style = ButtonStyle.PRIMARY,
                                user = event.user
                            ) {
                                deleteEvent.hook.editMessage(content = "*Delete canceled*").setComponents().queue()
                            }
                        )).queue()
                })
                event.replyPaginator(slider).setEphemeral(true).queue()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}