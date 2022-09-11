import com.sksamuel.hoplite.ConfigLoader
import commands.cancel.cancelCommand
import commands.chapters.listChaptersCommand
import commands.chapters.rollbackChapterCommand
import commands.make.QueueDispatcher
import commands.make.diffusion_configs.disco.discoDiffusionConfigs
import commands.social.shareCommand
import commands.update.updateCommand
import commands.variate.variateCommand
import database.initDatabase
import dev.minn.jda.ktx.interactions.commands.choice
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.injectKTX
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import kotlin.time.Duration

lateinit var config: Config
lateinit var queueDispatcher: QueueDispatcher
var updateMode = false

suspend fun main(args: Array<String>) {
    config = ConfigLoader().loadConfigOrThrow(args.getOrElse(0) {
        "./config.yml"
    })
    initDatabase()
    val builder: JDABuilder = JDABuilder.createDefault(config.bot.token)
    builder.apply {
        injectKTX(timeout = Duration.INFINITE)
    }
    builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
    builder.setBulkDeleteSplittingEnabled(false)
    builder.setCompression(Compression.ZLIB)
    builder.setActivity(Activity.competing("Your prompts suck!"))
    coroutineScope {
        val jda = builder.build()
        queueDispatcher = QueueDispatcher(jda)
        async {
            queueDispatcher.startQueueDispatcher()
        }
        initCommands(jda)
    }
}

fun initCommands(jda: JDA) {
    discoDiffusionCommand(jda)
    stableDiffusionCommand(jda)
    cancelCommand(jda)
    updateCommand(jda)
    listChaptersCommand(jda)
    variateCommand(jda)
    shareCommand(jda)
    rollbackChapterCommand(jda)

    jda.updateCommands {
        slash("stable_diffusion", "Making things with Stable Diffusion!") {
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<String>("init_image", "A link to an image you wish to use as start!", required = false)
            option<Int>(
                "seed",
                "Entropy for the random number generator, use the same seed to replicate results!",
                required = false
            )
        }

        slash("disco_diffusion", "Making things with Disco Diffusion!") {
            option<String>(
                "prompts",
                "prompts to make (weighted prompts can be separated by |, i.e 'Landscape:100|JPEG artifacts:-50')",
                required = true
            )
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<String>("init_image", "A link to an image you wish to use as start!", required = false)
            option<Int>(
                "skip_steps",
                "Use with init_image. Number of steps to skip. More skips means closer to your init image.",
                required = false
            )
            option<String>(
                "preset",
                "A custom configuration pack (any other parameters will override the preset!)",
                required = false
            ) {
                for (k in discoDiffusionConfigs.keys) {
                    choice(discoDiffusionConfigs[k]!!.second, k)
                }
            }
            option<Boolean>("horizontal_symmetry", "Make the image horizontally symmetric!", required = false)
            option<Boolean>("vertical_symmetry", "Make the image vertically symmetric!", required = false)
            option<Double>(
                "symmetry_intensity",
                "100% means fully symmetric, anything below that will reduce the effect.",
                required = false
            )
            option<Int>(
                "seed",
                "Entropy for the random number generator, use the same seed to replicate results!",
                required = false
            )
        }
        slash("update", "[Admin only] Update mode: Prevents new images from being created for updating the bot") {
            option<Boolean>("on", "Turn update mode on or off", required = true)
        }
        slash("cancel", "Cancel latest item (by you) in the queue")

        slash("chapters", "Show your previous work!") {
        }
        slash("rollback", "Like your old results better? Use this to bring them back!") {
        }
        slash("variate", "Make variations of your previous prompt!") {
            option<Double>("strength", "How strong the change needs to be?", required = false)
            option<Double>("guidance_scale", "How much guidance of the original image?", required = false)
            option<Int>("steps", "How much steps from the original image?", required = false)
        }
        slash("share", "Share your favorite image!") {
        }
    }.queue()
}