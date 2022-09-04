package commands.chapters

import commands.make.DiffusionParameters
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.paginator
import dev.minn.jda.ktx.interactions.components.replyPaginator
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import utils.ImageSliderEntry
import utils.sendImageSlider
import utils.sendPagination
import java.net.URL
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun listChaptersCommand(jda: JDA) {
    jda.onCommand("chapters") { event ->
        try {
            val user = userDao.queryBuilder().selectColumns("id").where().eq("discordUserID", event.user.id).queryForFirst()
            if (user == null) {
                event.reply_("User not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val possibleChapters =
                chapterDao.queryBuilder().orderBy("creationTimestamp", false).selectColumns().where().eq("userID", user.id).query()
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
            sendImageSlider(event, "My Chapters", chapterEntries).setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}