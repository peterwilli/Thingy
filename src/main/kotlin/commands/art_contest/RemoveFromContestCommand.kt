package commands.art_contest

import GetPageCallback
import Paginator
import config
import database.artContestEntryDao
import database.models.ArtContestEntry
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.MessageCreate
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import editMessageToIncludePaginator
import getAverageColor
import miniManual
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import paginator
import utils.bufferedImageToByteArray
import utils.sendException
import java.net.URL
import javax.imageio.ImageIO
import kotlin.time.Duration.Companion.minutes

fun makeSelectArtContestEntry(entries: List<ArtContestEntry>): Paginator {
    val getPageCallback: GetPageCallback = { index ->
        val page = EmbedBuilder()
        val entry = entries[index.toInt()]
        page.setTitle(entry.prompt.take(255))
        page.setFooter("Entry ${index + 1} / ${entries.size}")
        page.setImage("attachment://${index + 1}.jpg")
        val image = ImageIO.read(URL(entry.imageURL))
        val avgColor = getAverageColor(image, 0, 0, image.width, image.height)
        page.setColor(avgColor)
        page.build()
        val msgCreate = MessageCreate(
            embeds = listOf(page.build()),
            files = listOf(FileUpload.fromData(bufferedImageToByteArray(image, "jpg"), "${index + 1}.jpg"))
        )
        msgCreate
    }
    val imageSelector =
        paginator(amountOfPages = entries.size.toLong(), getPage = getPageCallback, expireAfter = 10.minutes)
    imageSelector.injectMessageCallback = { index, msgEdit ->
        val entry = entries[index.toInt()]
        val image = ImageIO.read(URL(entry.imageURL))
        msgEdit.setFiles(FileUpload.fromData(bufferedImageToByteArray(image, "jpg"), "${index + 1}.jpg"))
    }
    return imageSelector
}

fun removeFromContestCommand(jda: JDA) {
    jda.onCommand("remove_from_contest") { event ->
        try {
            val user = userDao.queryBuilder().selectColumns("id", "currentChapterId").where()
                .eq("discordUserID", event.user.id).queryForFirst()
            if (user == null) {
                event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val memberEntries = artContestEntryDao.queryBuilder().selectColumns().where().eq("userID", user.id).query()
            if (memberEntries.isEmpty()) {
                event.reply_("No art contest entries found yet! Want to participate? Run `/submit_to_contest` after making some art!")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            event.deferReply().setEphemeral(true).queue()
            val imageSelector = makeSelectArtContestEntry(memberEntries)
            imageSelector.customActionComponents = listOf(jda.button(
                label = "Delete",
                style = ButtonStyle.DANGER,
                user = event.user
            ) { btnEvent ->
                btnEvent.reply_("Are you sure to delete this entry from the contest? **You will also lose your votes and cannot be undone!**")
                    .setEphemeral(true).addActionRow(listOf(
                        jda.button(
                            label = "Delete!",
                            style = ButtonStyle.DANGER,
                            user = event.user
                        ) {
                            val memberEntryToDelete = memberEntries[imageSelector.getIndex().toInt()]
                            val channel = jda.getTextChannelById(config.artContestChannelID!!)
                            if (channel == null) {
                                btnEvent.hook.editMessage(content = "Can't find art channel! Art entry not deleted.")
                                    .setComponents().queue()
                            } else {
                                val messageId = memberEntryToDelete.messageLink.split("/").last()
                                channel.deleteMessageById(messageId).queue()
                                memberEntryToDelete.delete()
                                btnEvent.hook.editMessage(content = "*Deleted!*").setComponents().queue()
                            }
                        },
                        jda.button(
                            label = "Keep!",
                            style = ButtonStyle.PRIMARY,
                            user = event.user
                        ) {
                            btnEvent.hook.editMessage(content = "*Delete canceled*").setComponents().queue()
                        }
                    )).queue()
            })
            event.hook.editMessageToIncludePaginator(imageSelector).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    };
}
