package commands.variate

import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA

fun variateCommand(jda: JDA) {
    jda.onCommand("variate") { event ->
        /*
        val user =
            userDao.queryBuilder().selectColumns("id", "currentChapterId").where().eq("discordUserID", event.user.id)
                .queryForFirst()
        if (user == null) {
            event.reply_("User '${event.user.id}' not found! Did you make art yet? $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }

        val usingChapter =
            chapterDao.queryBuilder().selectColumns().where().eq("id", user.currentChapterId).and()
                .eq("userID", user.id).queryForFirst()

        if (usingChapter == null) {
            event.reply_("Sorry, we couldn't find any chapters! $miniManual")
                .setEphemeral(true).queue()
            return@onCommand
        }
        event.deferReply(true).queue()

        val latestEntry = usingChapter.getLatestEntry()
        val parameters = gson.fromJson(latestEntry.parameters, Array<DiffusionParameters>::class.java)
        val image = ImageIO.read(URL(latestEntry.imageURL))

        val strength = if (event.getOption("strength") != null) {
            event.getOption("strength")!!.asInt
        } else {
            50
        }

        val guidanceScale = if (event.getOption("guidance_scale") != null) {
            event.getOption("guidance_scale")!!.asDouble
        } else {
            0.5
        }

        val steps = if (event.getOption("steps") != null) {
            min(event.getOption("steps")!!.asInt, 50)
        } else {
            50
        }

        val quiltSelector = makeSelectImageFromQuilt(
            event,
            event.user,
            "Select your favorite to variate!",
            image,
            parameters.size
        ) { btnEvent, chosenImage ->
            val parameterToVariate = parameters[chosenImage]
            val imageSlice = takeSlice(image, parameters.size, chosenImage)
            val batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map { _ ->
                val base64Init = bufferedImageToDataURI(imageSlice)
                parameterToVariate.copy(
                    seed = Random.nextInt(0, 2.toDouble().pow(32).toInt()),
                    artID = "${config.bot.name}-${randomString(alphanumericCharPool, 32)}",
                    stableDiffusionParameters = parameterToVariate.stableDiffusionParameters!!.copy(
                        initImage = base64Init,
                        steps = steps,
                        guidanceScale = guidanceScale,
                        strength = strength
                    )
                )
            }
            val fqe = FairQueueEntry(
                "Variating Image",
                FairQueueType.StableDiffusion,
                event.member!!.id,
                batch,
                btnEvent.hook,
                usingChapter
            )
            btnEvent.reply_(//queueDispatcher.queue.addToQueue(fqe)).queue()
        }
        event.hook.editMessageToIncludePaginator(quiltSelector).queue()
         */
    }
}