package commands.chapters

import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.paginator
import dev.minn.jda.ktx.interactions.components.replyPaginator
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.Button
import utils.sendPagination
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

fun listChaptersCommand(jda: JDA) {
    jda.onCommand("chapters") { event ->
        try {
            val miniManual = "Start by making one using `/make`, `/disco_diffusion`, or `/stable_diffusion`"
            val user = userDao.queryBuilder().selectColumns("id").where().eq("discordUserID", event.user.id).queryForFirst()
            if (user == null) {
                event.reply_("User not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val possibleChapters =
                chapterDao.queryBuilder().selectColumns().where().eq("serverID", event.guild!!.id)
                    .and().eq("userID", event.user.id).query()
            if (possibleChapters.isEmpty()) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val chapterStrings = possibleChapters.map {
                "ID: ${it.userScopedID}"
            }
            sendPagination(event, "My Chapters", chapterStrings, 10).setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}