package commands.social

import commands.make.DiffusionParameters
import database.chapterDao
import database.models.SharedArtCacheEntry
import database.sharedArtCacheEntryDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.utils.FileUpload
import org.atteo.evo.inflector.English
import ui.makeSelectImageFromQuilt
import utils.*
import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

fun setBackgroundCommand(jda: JDA) {
    jda.onCommand("set_background") { event ->
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

            val latestEntry = usingChapter.getLatestEntry()
            val image = ImageIO.read(URL(latestEntry.imageURL))
            val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)
            makeSelectImageFromQuilt(
                event,
                event.user,
                "Select image for background use!",
                image,
                parameters.size
            ) { _, chosenImage ->
                val imageSlice = takeSlice(image, parameters.size, chosenImage)
                var currentY = 0
                fun getSlicedBG(): BufferedImage {
                    return imageSlice.getSubimage(0, currentY, imageSlice.width, profileCardHeight)
                }
                fun getCroppedCardBGMessage(): WebhookMessageEditAction<Message> {
                    val card = makeProfileCard(event.user, getSlicedBG())
                    return event.hook.editMessage(content = "**Preview!**")
                        .setFiles(FileUpload.fromData(bufferedImageToByteArray(card), "profile.png"))
                }
                fun getButtons(): Array<Button> {
                    val upButton = jda.button(
                        label = "Up",
                        style = ButtonStyle.SECONDARY,
                        user = event.user
                    ) {
                        currentY += 10
                        currentY = currentY.coerceIn(0..imageSlice.height - profileCardHeight)
                        getCroppedCardBGMessage().setActionRow(*getButtons()).queue()
                    }.withDisabled(currentY == imageSlice.height - profileCardHeight)
                    val downButton = jda.button(
                        label = "Down",
                        style = ButtonStyle.SECONDARY,
                        user = event.user
                    ) {
                        currentY -= 10
                        currentY = currentY.coerceIn(0..imageSlice.height - profileCardHeight)
                        getCroppedCardBGMessage().setActionRow(*getButtons()).queue()
                    }.withDisabled(currentY == 0)
                    val okButton = jda.button(
                        label = "Save profile!",
                        style = ButtonStyle.PRIMARY,
                        user = event.user
                    ) {
                        try {
                            val updateBuilder = userDao.updateBuilder()
                            updateBuilder.where().eq("id", user.id)
                            updateBuilder.updateColumnValue("backgroundURL", bufferedImageToDataURI(getSlicedBG()))
                            updateBuilder.update()
                            it.editMessage("**Set profile done!**").setComponents().setEmbeds().setAttachments().queue()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            it.reply_("Error! $e").setEphemeral(true).queue()
                        }
                    }
                    val cancelButton = jda.button(
                        label = "Cancel!",
                        style = ButtonStyle.DANGER,
                        user = event.user
                    ) {
                        try {
                            it.editMessage("*Set profile canceled*").setComponents().setEmbeds().setAttachments().queue()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            it.reply_("Error! $e").setEphemeral(true).queue()
                        }
                    }
                    return listOf(upButton, downButton, okButton, cancelButton).toTypedArray()
                }
                getCroppedCardBGMessage().setActionRow(*getButtons()).queue()
            }.setEphemeral(true).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}