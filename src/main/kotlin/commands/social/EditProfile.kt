package commands.social

import com.google.gson.JsonArray
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import editMessageToIncludePaginator
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.restaction.WebhookMessageEditAction
import net.dv8tion.jda.api.utils.FileUpload
import ui.makeSelectImageFromQuilt
import utils.*
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

fun setBackgroundCommand(jda: JDA) {
    jda.onCommand("edit_profile") { event ->
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
                event.user,
                "Select image for background use!",
                image,
                parameters.size()
            ) { _, chosenImage ->
                val imageSlice = takeSlice(image, parameters.size(), chosenImage)
                var currentY = 0
                fun getSlicedBG(): BufferedImage {
                    return imageSlice.getSubimage(0, currentY, imageSlice.width, profileCardHeight)
                }

                fun getCroppedCardBGMessage(): WebhookMessageEditAction<Message> {
                    val card = makeProfileCard(event.user, getSlicedBG())
                    return event.hook.editMessage(content = "**Preview!**")
                        .setFiles(FileUpload.fromData(bufferedImageToByteArray(card), "profile.png"))
                }

                fun getFontDropdown() {

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
                            it.editMessage("*Set profile canceled*").setComponents().setEmbeds().setAttachments()
                                .queue()
                        } catch (e: Exception) {
                            e.printStackTrace()
                            it.reply_("Error! $e").setEphemeral(true).queue()
                        }
                    }
                    return listOf(upButton, downButton, okButton, cancelButton).toTypedArray()
                }
                getCroppedCardBGMessage().setActionRow(*getButtons()).queue()
            }
            event.hook.editMessageToIncludePaginator(quiltSelector).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}