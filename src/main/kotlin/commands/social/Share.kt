package commands.social

import commands.make.DiffusionParameters
import config
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import utils.bufferedImageToByteArray
import utils.makeSelectImageFromQuilt
import utils.messageToURL
import utils.takeSlice
import java.awt.Color
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

private val imageFilename = "final.png"

fun getAverageColor(
    bi: BufferedImage, x0: Int, y0: Int, w: Int, h: Int
): Color {
    val x1 = x0 + w
    val y1 = y0 + h
    var sumr: Long = 0
    var sumg: Long = 0
    var sumb: Long = 0
    for (x in x0 until x1) {
        for (y in y0 until y1) {
            val pixel = Color(bi.getRGB(x, y))
            sumr += pixel.getRed()
            sumg += pixel.getGreen()
            sumb += pixel.getBlue()
        }
    }
    val num = w * h
    return Color((sumr / num).toInt(), (sumg / num).toInt(), (sumb / num).toInt())
}

private fun makeShareEmbed(img: BufferedImage, author: User, parameters: Array<DiffusionParameters>): MessageEmbed {
    val embed = EmbedBuilder()
    embed.setImage("attachment://$imageFilename")
    embed.setTitle(parameters.first().getPrompt())
    embed.setDescription("by ${author.asMention}")
    val avgColor = getAverageColor(img, 0, 0, img.width, img.height)
    embed.setColor(avgColor)
    return embed.build()
}

fun shareCommand(jda: JDA) {
    jda.onCommand("share") { event ->
        try {
            val user = userDao.queryBuilder().selectColumns("id", "currentChapterId").where()
                .eq("discordUserID", event.user.id).queryForFirst()
            if (user == null) {
                event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            val usingChapter =
                chapterDao.queryBuilder().selectColumns().where()
                    .eq("userScopedID", user.currentChapterId).and().eq("userID", user.id).queryForFirst()
            if (usingChapter == null) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            val latestEntry = usingChapter.getLatestEntry()
            val image = ImageIO.read(URL(latestEntry.imageURL))
            val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)
            makeSelectImageFromQuilt(event, event.user, "Select image for sharing", image, parameters.size) { chosenImage ->
                val imageSlice = takeSlice(image, parameters.size, chosenImage)
                val shareChannel = jda.getTextChannelById(config.shareChannelID)!!
                val embed = makeShareEmbed(imageSlice, event.user, parameters)
                val okButton = jda.button(
                    label = "Fire away!",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        it.editMessage("*Sharing...*").setComponents().setEmbeds().setAttachments().queue { shareMsg ->
                            shareChannel.sendMessageEmbeds(embed)
                                .setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice), imageFilename))
                                .queue { sharedMsg ->
                                    shareMsg.editOriginal("**Shared!** ${messageToURL(sharedMsg)}").queue()
                                }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                        it.reply_("Error! $e").setEphemeral(true).queue()
                    }
                }
                val cancelButton = jda.button(
                    label = "Don't share!",
                    style = ButtonStyle.DANGER,
                    user = event.user
                ) {
                    try {
                        it.editMessage("*Share canceled*").setComponents().setEmbeds().setAttachments().queue()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        it.reply_("Error! $e").setEphemeral(true).queue()
                    }
                }
                event.hook.editMessage(content = "**Preview!**", embeds = listOf(embed))
                    .setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice), imageFilename))
                    .setActionRow(okButton, cancelButton).queue()
            }.setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}