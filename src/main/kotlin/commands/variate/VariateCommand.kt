package commands.variate

import alphanumericCharPool
import commands.make.DiffusionParameters
import commands.make.FairQueueEntry
import commands.make.FairQueueType
import commands.make.optionsToStableDiffusionParams
import config
import database.chapterDao
import database.userDao
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.generics.getChannel
import dev.minn.jda.ktx.messages.reply_
import gson
import miniManual
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.TextChannel
import queueDispatcher
import randomString
import utils.bufferedImageToDataURI
import utils.makeSelectImageFromQuilt
import utils.takeSlice
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

fun variateCommand(jda: JDA) {
    jda.onCommand("variate") { event ->
        val user =
            userDao.queryBuilder().selectColumns("id", "currentChapterId").where().eq("discordUserID", event.user.id)
                .queryForFirst()
        if (user == null) {
            event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val usingChapter =
            chapterDao.queryBuilder().selectColumns().where().eq("userScopedID", user.currentChapterId).and()
                .eq("userID", user.id).queryForFirst()
        if (usingChapter == null) {
            event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val latestEntry = usingChapter.getLatestEntry()
        val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)
        val image = ImageIO.read(URL(latestEntry.imageURL))
        makeSelectImageFromQuilt(
            event,
            event.user,
            "Select your favorite to variate!",
            image,
            parameters.size
        ) { chosenImage ->
            val parameterToVariate = parameters[chosenImage]
            val imageSlice = takeSlice(image, parameters.size, chosenImage)
            val batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map { _ ->
                val base64Init = bufferedImageToDataURI(imageSlice)
                parameterToVariate.copy(
                    seed = Random.nextInt(0, 2.toDouble().pow(32).toInt()),
                    artID = "${config.bot.name}-${randomString(alphanumericCharPool, 32)}",
                    stableDiffusionParameters = parameterToVariate.stableDiffusionParameters!!.copy(
                        initImage = base64Init,
                        steps = 50
                    )
                )
            }
            val fqe = FairQueueEntry(
                "Variating Image",
                FairQueueType.StableDiffusion,
                event.member!!.id,
                batch,
                event.hook,
                usingChapter
            )
            event.hook.editOriginal(queueDispatcher.queue.addToQueue(fqe)).setComponents().setEmbeds().setAttachments()
                .queue()
        }.setEphemeral(true).queue()
    }
}