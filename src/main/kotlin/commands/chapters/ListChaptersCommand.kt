package commands.chapters

import commands.make.DiffusionParameters
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import replyPaginator
import ui.ImageSliderEntry
import ui.sendImageSlider
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
            val possibleChapters =
                chapterDao.queryBuilder().orderBy("creationTimestamp", false).selectColumns().where()
                    .eq("userID", user.id).query()
            if (possibleChapters.isEmpty()) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val chapterEntries = possibleChapters.map {
                val latestEntry = it.getLatestEntry()
                val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)
                ImageSliderEntry(
                    description = parameters.first().getPrompt() ?: "No prompt",
                    image = URL(latestEntry.imageURL)
                )
            }
            val slider = sendImageSlider("My Chapters", chapterEntries)
            slider.customActionComponents = listOf(jda.button(
                label = "Select",
                style = ButtonStyle.PRIMARY,
                user = event.user
            ) {
                val chapter = possibleChapters[slider.getIndex()]
                val parameters = gson.fromJson(chapter.getLatestEntry().parameters, Array<DiffusionParameters>::class.java)

                val updateBuilder = userDao.updateBuilder()
                updateBuilder.where().eq("id", chapter.userID)
                updateBuilder.updateColumnValue("currentChapterId", chapter.userScopedID)
                updateBuilder.update()

                it.reply_("${parameters.first().getPrompt()} is now your current chapter! You can use editing commands such as `/upscale`, `/variate` to edit it! Enjoy!").setEphemeral(true).queue()
            })
            event.replyPaginator(slider).setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}