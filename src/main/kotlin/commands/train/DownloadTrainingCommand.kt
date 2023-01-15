package commands.train

import commands.make.standardPermissionList
import commands.make.validatePermissions
import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.editMessage
import isLettersOrDigits
import miniManual
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.FileUpload
import utils.sendException
import java.util.*

fun downloadTrainingCommand(jda: JDA) {
    jda.onCommand("download_training") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply(true).queue()
            val word = event.getOption("word")!!.asString
            val mode = event.getOption("mode")!!.asString

            if (!(word.isLettersOrDigits() && word.lowercase() == word)) {
                event.hook.editMessage(content = "**Error!** Words need to be fully alphanumeric and lowercase!").queue()
                return@onCommand
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

            if (user == null) {
                event.hook.editMessage(content = "User not found! Did you make art yet? $miniManual").queue()
                return@onCommand
            }

            val embeds = results[user.id]
            if (embeds == null) {
                event.hook.editMessage(content = "No trained models found matching $word!").queue()
                return@onCommand
            }

            val embedEntry = embeds.first()
            val byteArray = Base64.getDecoder().decode(embedEntry.data)
            val embed = EmbedBuilder()
            embed.setTitle("Pretrained model")
            embed.setDescription("Textual Inversion (Boosted by LEAP): $word by ${event.user.asMention}")

            val channel = if (mode == "public") {
                event.messageChannel
            } else {
                event.user.openPrivateChannel().await()
            }

            channel.sendMessageEmbeds(embed.build())
                .setFiles(FileUpload.fromData(byteArray, "${word}_embed.bin"))
                .queue()
            event.hook.editMessage(content = "Done!").queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}