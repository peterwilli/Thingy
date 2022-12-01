package commands.art_contest

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.standardPermissionList
import commands.make.validatePermissions
import config
import database.artContestEntryDao
import database.chapterDao
import database.models.ArtContestEntry
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import editMessageToIncludePaginator
import getAverageColor
import gson
import miniManual
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.MessageEmbed
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import ui.makeSelectImageFromQuilt
import utils.bufferedImageToByteArray
import utils.messageToURL
import utils.sendException
import utils.takeSlice
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

val defaultVoteEmoji = Emoji.fromFormatted("\uD83D\uDC32")
private const val maxPicsPerMember = 2
private const val imageFilename = "final.png"

private fun makeShareEmbed(img: BufferedImage, author: User, parameters: JsonObject): MessageEmbed {
    val embed = EmbedBuilder()
    embed.setImage("attachment://$imageFilename")
    // Discord has a max title length for embeds of 256. We take 250 to be on the safe side.
    val maxPromptLength = 250
    val prompt = parameters.get("prompt").asString
    val title = if (prompt.length > maxPromptLength) {
        "${prompt.take(maxPromptLength)}..."
    } else {
        prompt
    }
    embed.setTitle(title)
    val description = StringBuilder()
    if (prompt.length > maxPromptLength) {
        // Add the remainder
        description.append("...${prompt.substring(maxPromptLength until prompt.length)}\n")
    }
    description.append("by ${author.asMention}")
    embed.setDescription(description.toString())
    val avgColor = getAverageColor(img, 0, 0, img.width, img.height)
    embed.setColor(avgColor)
    return embed.build()
}

fun submitToContestCommand(jda: JDA) {
    jda.onCommand("submit_to_contest") { event ->
        if (!validatePermissions(event, standardPermissionList)) {
            return@onCommand
        }
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
                    .eq("id", user.currentChapterId).and().eq("userID", user.id).queryForFirst()
            if (usingChapter == null) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            event.deferReply(true).queue()
            val latestEntry = usingChapter.getLatestEntry()
            val image = ImageIO.read(URL(latestEntry.imageURL))
            val parameters = gson.fromJson(latestEntry.parameters, JsonArray::class.java)
            val quiltSelector = makeSelectImageFromQuilt(
                event,
                event.user,
                "Select image for the contest",
                image,
                parameters.size()
            ) { _, chosenImage ->
                val memberEntries =
                    artContestEntryDao.queryBuilder().selectColumns().where().eq("userID", usingChapter.userID).query()
                if (memberEntries.size >= maxPicsPerMember) {
                    event.hook.editMessage(content = "There's a maximum of $maxPicsPerMember entries per member! Remove a submission with `/remove_from_contest`, then you can add a new piece!")
                        .setComponents().setEmbeds().setAttachments().queue()
                    return@makeSelectImageFromQuilt
                }
                val possibleCacheEntry =
                    artContestEntryDao.queryBuilder().selectColumns().where().eq("userID", usingChapter.userID)
                        .and().eq("originalImageURL", latestEntry.imageURL).and().eq("index", chosenImage)
                        .queryForFirst()
                if (possibleCacheEntry != null) {
                    event.hook.editMessage(content = "**Sorry!** but you shared this image before! We don't allow sharing images more than twice! The message is previously shared here: ${possibleCacheEntry.messageLink}")
                        .setComponents().setEmbeds().setAttachments().queue()
                    return@makeSelectImageFromQuilt
                }
                val imageSlice = takeSlice(image, parameters.size(), chosenImage)
                val shareChannel = jda.getTextChannelById(config.artContestChannelID!!)!!
                val embed = makeShareEmbed(imageSlice, event.user, parameters[0].asJsonObject)
                val okButton = jda.button(
                    label = "Fire away!",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        it.editMessage("*Sharing...*").setComponents().setEmbeds().setAttachments().queue { shareMsg ->
                            shareChannel.sendMessageEmbeds(embed)
                                .setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice, "png"), imageFilename))
                                .queue { sharedMsg ->
                                    sharedMsg.addReaction(defaultVoteEmoji).queue()
                                    val messageLink = messageToURL(sharedMsg)
                                    shareMsg.editOriginal("**Shared!** $messageLink").queue()
                                    val entry =
                                        ArtContestEntry(
                                            usingChapter.userID,
                                            URL(sharedMsg.embeds.first().image!!.url),
                                            URL(latestEntry.imageURL),
                                            parameters.get(0).asJsonObject.get("prompt").asString,
                                            chosenImage,
                                            messageLink,
                                            sharedMsg.idLong
                                        )
                                    artContestEntryDao.create(entry)
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
            }
            event.hook.editMessageToIncludePaginator(quiltSelector).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}