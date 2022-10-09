import com.sksamuel.hoplite.ConfigLoader
import commands.cancel.cancelCommand
import commands.chapters.listChaptersCommand
import commands.chapters.rollbackChapterCommand
import commands.img2img.img2imgCommand
import commands.make.QueueDispatcher
import commands.social.profileCommand
import commands.social.serverStatsCommand
import commands.social.setBackgroundCommand
import commands.social.shareCommand
import commands.update.updateCommand
import commands.variate.variateCommand
import database.initDatabase
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.injectKTX
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.entities.Message.Attachment
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.commands.DefaultMemberPermissions
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import script.ThingyExtensionScript
import script.ThingyExtensionScriptEvaluationConfiguration
import script.runScripts
import utils.JCloudClient
import kotlin.script.experimental.api.ScriptDiagnostic
import kotlin.script.experimental.host.toScriptSource
import kotlin.script.experimental.jvmhost.BasicJvmScriptingHost
import kotlin.script.experimental.jvmhost.createJvmCompilationConfigurationFromTemplate
import kotlin.time.Duration

lateinit var config: Config
lateinit var queueDispatcher: QueueDispatcher
lateinit var jcloudClient: JCloudClient
var updateMode = false

suspend fun main(args: Array<String>) {
    config = ConfigLoader().loadConfigOrThrow(args.getOrElse(0) {
        "./config.yml"
    })
    jcloudClient = JCloudClient()
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
        runScripts(jda)
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
    img2imgCommand(jda)
    profileCommand(jda)
    setBackgroundCommand(jda)
    serverStatsCommand(jda)
    addThingyExtensionCommand(jda)

    jda.updateCommands {
        slash("stable_diffusion", "Making things with Stable Diffusion!") {
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<Double>("guidance_scale", "How much guidance to the prompt?", required = false)
            option<Int>(
                "seed",
                "Entropy for the random number generator, use the same seed to replicate results!",
                required = false
            )
        }
        slash("img2img", "Make an existing image into your prompt!") {
            option<Attachment>("input_image", "Initial image", required = true)
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<Int>(
                "seed",
                "Entropy for the random number generator, use the same seed to replicate results!",
                required = false
            )
            option<Double>("strength", "How strong the change needs to be?", required = false)
            option<Double>("guidance_scale", "How much guidance to the prompt?", required = false)
            option<Int>("steps", "How much steps from the original image?", required = false)
        }
        slash("link2img", "Make an existing image into your prompt!") {
            option<String>("input_image_url", "Link to initial image", required = true)
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<Int>(
                "seed",
                "Entropy for the random number generator, use the same seed to replicate results!",
                required = false
            )
            option<Double>("strength", "How strong the change needs to be?", required = false)
            option<Double>("guidance_scale", "How much guidance to the prompt?", required = false)
            option<Int>("steps", "How much steps from the original image?", required = false)
        }
        slash("update", "[Mod only] Update mode: Prevents new images from being created for updating the bot") {
            option<Boolean>("on", "Turn update mode on or off", required = true)
            defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
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
        slash("profile", "Get someone's (Or your!) stats!") {
            option<User>("user", "Get stats by user (you if not defined)", required = false)
            option<Boolean>("ephemeral", "Send only to you?", required = false)
        }
        slash("stats", "See server and bot stats!") {
        }
        slash("edit_profile", "Set profile background!") {
        }
        slash("add_extension", "[Admin only] Add an extension for more functionality!") {
            option<Attachment>("script_file", "Script file", required = true)
            defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.ADMINISTRATOR)
        }
    }.queue()
}