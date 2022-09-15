package commands.chapters

import commands.make.DiffusionParameters
import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import replyPaginator
import ui.GetImageCallback
import ui.ImageSliderEntry
import ui.sendImageSlider
import java.net.URL

fun rollbackChapterCommand(jda: JDA) {
    jda.onCommand("rollback") { event ->
        try {
            val user =
                userDao.queryBuilder().selectColumns("id", "currentChapterId").where()
                    .eq("discordUserID", event.user.id)
                    .queryForFirst()
            if (user == null) {
                event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            val usingChapter =
                chapterDao.queryBuilder().selectColumns().where().eq("id", user.currentChapterId).and()
                    .eq("userID", user.id).queryForFirst()
            if (usingChapter == null) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            val entryCount = usingChapter.getEntryCount()
            var lastEntry: ChapterEntry? = null
            val onImage: GetImageCallback = { index ->
                lastEntry = usingChapter.getEntryAtIndex(index)
                val parameters = gson.fromJson(lastEntry!!.parameters, Array<DiffusionParameters>::class.java)
                ImageSliderEntry(
                    description = parameters.first().getPrompt() ?: "No prompt",
                    image = URL(lastEntry!!.imageURL)
                )
            }
            val slider = sendImageSlider("Rollback to", entryCount, onImage)
            slider.customActionComponents = listOf(jda.button(
                label = "Rollback",
                style = ButtonStyle.PRIMARY,
                user = event.user
            ) {
                val entryToRollBackTo = lastEntry!!
                val db = chapterEntryDao.deleteBuilder()
                db.where().eq("chapterID", entryToRollBackTo.chapterID).and().gt("creationTimestamp", entryToRollBackTo.creationTimestamp)
                chapterEntryDao.delete(db.prepare())
                it.reply_("Rolled back! You can use editing commands such as `/upscale`, `/variate` to edit it! Enjoy!").setEphemeral(true).queue()
            })
            event.replyPaginator(slider).setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}