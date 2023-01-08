package commands.social

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import config
import database.chapterDao
import database.models.SharedArtCacheEntry
import database.sharedArtCacheEntryDao
import database.userDao
import dev.minn.jda.ktx.coroutines.await
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
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import ui.makeSelectImageFromQuilt
import utils.*
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import kotlin.concurrent.thread

private const val imageFilename = "final.jpg"
private const val imageFilenamePreview = "final.jpg"

private fun makeShareEmbed(img: BufferedImage, author: User, parameters: JsonObject, preview: Boolean): MessageEmbed {
    val embed = EmbedBuilder()
    embed.setImage("attachment://${
        if(preview) {
            imageFilenamePreview
        }
        else {
            imageFilename
        }
    }")
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
                    .eq("id", user.currentChapterId).and().eq("userID", user.id).queryForFirst()
            if (usingChapter == null) {
                event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }

            event.deferReply(true).queue()
            val latestEntry = usingChapter.getLatestEntry()
            val image = ImageIO.read(URL(latestEntry.data))
            val parameters = gson.fromJson(latestEntry.parameters, JsonArray::class.java)
            val quiltSelector = makeSelectImageFromQuilt(
                event.user,
                "Select image for sharing",
                image,
                parameters.size()
            ) { _, chosenImage ->
                val possibleCacheEntry =
                    sharedArtCacheEntryDao.queryBuilder().selectColumns().where().eq("userID", usingChapter.userID)
                        .and().eq("imageURL", latestEntry.data).and().eq("index", chosenImage).queryForFirst()
                if (possibleCacheEntry != null) {
                    event.hook.editMessage(content = "**Sorry!** but you shared this image before! We don't allow sharing images more than twice! The message is previously shared here: ${possibleCacheEntry.messageLink}", components = listOf(), embeds = listOf())
                        .queue()
                    return@makeSelectImageFromQuilt
                }
                event.hook.editMessage(content = "Processing image... Please wait...", components = listOf(), embeds = listOf()).await()
                val imageSlice = takeSlice(image, parameters.size(), chosenImage)
                val imageSliceThumbnail = toThumbnail(takeSlice(image, parameters.size(), chosenImage))
                val shareChannel = jda.getTextChannelById(config.shareChannelID)!!
                val embed = makeShareEmbed(imageSliceThumbnail, event.user, parameters[0].asJsonObject, true)
                val okButton = jda.button(
                    label = "Fire away!",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        it.editMessage("*Sharing...*").setComponents().setEmbeds().setAttachments().queue { shareMsg ->
                            val finalEmbed = makeShareEmbed(imageSlice, event.user, parameters[0].asJsonObject, false)
                            shareChannel.sendMessageEmbeds(finalEmbed)
                                .setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice, "jpg"), imageFilename))
                                .queue { sharedMsg ->
                                    val messageLink = messageToURL(sharedMsg)
                                    shareMsg.editOriginal("**Shared!** $messageLink").queue()
                                    val cacheEntry =
                                        SharedArtCacheEntry(
                                            usingChapter.userID,
                                            URL(latestEntry.data),
                                            chosenImage,
                                            messageLink
                                        )
                                    sharedArtCacheEntryDao.create(cacheEntry)
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
                thread {
                    event.hook.editMessage(content = "**Preview!**", embeds = listOf(embed))
                        .setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSliceThumbnail, "jpg"), imageFilenamePreview))
                        .setActionRow(okButton, cancelButton).queue()
                }
            }
            event.hook.editMessageToIncludePaginator(quiltSelector).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}