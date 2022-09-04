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
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.pow
import kotlin.random.Random

fun variateCommand(jda: JDA) {
    jda.onCommand("variate") { event ->
        val chosenImage = event.getOption("index")!!.asInt
        val user = userDao.queryBuilder().selectColumns("id", "currentChapterId").where().eq("discordUserID", event.user.id).queryForFirst()
        if(user == null) {
            event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val usingChapter = chapterDao.queryBuilder().selectColumns("id", "messageID", "channelID", "parameters").where().eq("userScopedID", user.currentChapterId).and().eq("userID", user.id).queryForFirst()
        if(usingChapter == null) {
            event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val latestEntry = usingChapter.getLatestEntry()
        val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)

        if (chosenImage < 1 || chosenImage > parameters.size) {
            event
                .reply_("Index should be between 1 and ${parameters.size}!")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val parameterToVariate = parameters[chosenImage - 1]
        val quilt = ImageIO.read(URL(latestEntry.imageURL))
        val imagesPerRow = ceil(parameters.size / 2.toDouble()).toInt()
        val row = floor((chosenImage - 1) / imagesPerRow.toDouble()).toInt()
        val col = (chosenImage - 1) % imagesPerRow
        val imageWidth = quilt.width / imagesPerRow
        val imageHeight = quilt.height / imagesPerRow
        val sliceImage = quilt.getSubimage(row * imageWidth, col * imageHeight, imageWidth, imageHeight)
        val batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map { _ ->
            val base64Init = bufferedImageToDataURI(sliceImage)
            parameterToVariate.copy(
                seed = Random.nextInt(0, 2.toDouble().pow(32).toInt()),
                artID = "${config.bot.name}-${randomString(alphanumericCharPool, 32)}",
                stableDiffusionParameters = parameterToVariate.stableDiffusionParameters!!.copy(
                    initImage = base64Init,
                    steps = 5
                )
            )
        }
        val fqe = FairQueueEntry(
            "Generating Images",
            FairQueueType.StableDiffusion,
            event.member!!.id,
            batch,
            event.hook,
            usingChapter
        )
        event.reply_(queueDispatcher.queue.addToQueue(fqe)).queue()
    }
}