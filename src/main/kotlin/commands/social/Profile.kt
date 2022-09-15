package commands.social

import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.utils.FileUpload
import utils.bufferedImageToByteArray
import utils.imageFromDataURL
import utils.makeProfileCard

fun profileCommand(jda: JDA) {
    jda.onCommand("profile") { event ->
        try {
            val ephemeral = if (event.getOption("ephemeral") == null) {
                false
            } else {
                event.getOption("ephemeral")!!.asBoolean
            }
            val userToGetProfileFrom = if (event.getOption("user") == null) {
                event.user
            } else {
                event.getOption("user")!!.asUser
            }
            val user =
                userDao.queryBuilder().selectColumns("id", "backgroundURL").where()
                    .eq("discordUserID", userToGetProfileFrom.id)
                    .queryForFirst()
            if (user == null) {
                event.reply_("User not found! Did you make art yet? $miniManual")
                    .setEphemeral(true).queue()
                return@onCommand
            }
            val backgroundImage = if (user.backgroundURL != null) {
                imageFromDataURL(user.backgroundURL!!)
            } else {
                null
            }
            val card = makeProfileCard(userToGetProfileFrom, backgroundImage)
            event.reply_("Profile of *${userToGetProfileFrom.asTag}*")
                .setFiles(FileUpload.fromData(bufferedImageToByteArray(card, "png"), "profile.png"))
                .setEphemeral(ephemeral).queue()
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("**Error!** $e").setEphemeral(true).queue()
        }
    }
}