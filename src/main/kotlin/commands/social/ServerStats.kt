package commands.social

import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA

fun serverStatsCommand(jda: JDA) {
    jda.onCommand("stats") { event ->
        try {
            val embedBuilder = EmbedBuilder()
            embedBuilder.setTitle("Bot stats!")
            embedBuilder.addField(
                "Total generations (all servers)",
                userDao.queryBuilder().selectColumns("generationsDone").query().map {
                    it.generationsDone
                }.sum().toString(),
                false
            )
            embedBuilder.setColor(0x33cc33)
            val embed = embedBuilder.build()
            event.replyEmbeds(embed).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}