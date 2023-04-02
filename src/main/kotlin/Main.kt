import com.sksamuel.hoplite.ConfigLoader
import commands.art_contest.*
import commands.art_contest.cancel.cancelCommand
import commands.chapters.listChaptersCommand
import commands.chapters.rollbackChapterCommand
import commands.img2img.img2imgCommand
import commands.make.QueueDispatcher
import commands.social.profileCommand
import commands.social.serverStatsCommand
import commands.social.setBackgroundCommand
import commands.social.shareCommand
import commands.train.downloadTrainingCommand
import commands.train.trainCommand
import commands.update.updateCommand
import commands.upscale.upscaleCommand
import commands.upscale.upscaleImageCommand
import commands.variate.variateCommand
import database.createDefaultDatabaseEntries
import database.initDatabase
import database.models.Thingy
import dev.minn.jda.ktx.interactions.commands.choice
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
import net.dv8tion.jda.api.interactions.commands.build.SlashCommandData
import net.dv8tion.jda.api.requests.GatewayIntent
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import utils.JCloudClient
import kotlin.math.pow
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
    try {
        Thingy.getCurrent().runMigration()
    }
    catch(e: Exception) {
        e.printStackTrace()
    }
    createDefaultDatabaseEntries()
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
    makeCommand(jda)
    stableDiffusionCommand(jda)
    cancelCommand(jda)
    updateCommand(jda)
    listChaptersCommand(jda)
    variateCommand(jda)
    shareCommand(jda)
    rollbackChapterCommand(jda)
    img2imgCommand(jda)
    profileCommand(jda)
    upscaleCommand(jda)
    upscaleImageCommand(jda)
    magicPromptCommand(jda)
    downloadTrainingCommand(jda)
    setBackgroundCommand(jda)
    serverStatsCommand(jda)
    removeFromContestCommand(jda)
    submitToContestCommand(jda)
    voteReactionWatcher(jda)
    trainCommand(jda)
    deliberateCommand(jda)
    editImageCommand(jda)
    removeBackgroundImageCommand(jda)

    if (config.leaderboardChannelID != null) {
        leaderboardScheduler(jda, 9)
        testLeaderboardCommand(jda)
    }

    fun seed(data: SlashCommandData): SlashCommandData {
        data.option<Int>(
            "seed",
            "Entropy for the random number generator, use the same seed to replicate results!",
            required = false,
            builder = {
                this.setMaxValue(2.0.pow(32.0).toLong())
                this.setMinValue(0)
            }
        )
        return data
    }

    fun sdGuidanceScale(data: SlashCommandData): SlashCommandData {
        data.option<Double>("guidance_scale", "How much guidance to the prompt?", required = false) {
            this.setMinValue(0.0)
            this.setMaxValue(100.0)
        }
        return data
    }

    fun sdSize(data: SlashCommandData): SlashCommandData {
        data.option<Int>("size", "Image square size (Default: Normal)", required = false) {
            choice("Small", 256)
            choice("Normal", 512)
            choice("Big", 768)
        }
        return data
    }

    fun sdUpscale(data: SlashCommandData): SlashCommandData {
        data.option<Int>(
            "original_image_slice",
            "How much guidance from the full image?",
            required = false,
            builder = {
                this.setMaxValue(32)
                this.setMinValue(0)
            }
        )
        data.option<Int>(
            "tile_border",
            "How much guessing around tiles? (More means less seams)",
            required = false,
            builder = {
                this.setMaxValue(32)
                this.setMinValue(0)
            }
        )
        data.option<Double>(
            "noise_level",
            "More noise means less of the original image is left and more details are \"imagined\"",
            required = false,
            builder = {
                this.setMaxValue(100.0)
                this.setMinValue(0.0)
            }
        )
        return data
    }

    fun sdSteps(data: SlashCommandData): SlashCommandData {
        return data.option<Int>(
            "steps",
            "Higher steps typically lead to a better image (default 25)",
            required = false
        ) {
            this.setMinValue(1)
            this.setMaxValue(100)
        }
    }

    jda.updateCommands {
        slash("make", "The easiest way to get started! Just type some text and let it go! \uD83E\uDDCA") {
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("negative_prompt", "Things you dont want in the image i. 'too many fingers'")
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            sdSize(this)
            sdGuidanceScale(this)
            sdSteps(this)
            seed(this)
        }
        slash("stable_diffusion", "Making things with Stable Diffusion!") {
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("negative_prompt", "Things you dont want in the image i. 'too many fingers'")
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            sdSize(this)
            sdGuidanceScale(this)
            sdSteps(this)
            seed(this)
        }
        slash("deliberate", "Making things with Deliberate!") {
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("negative_prompt", "Things you dont want in the image i. 'too many fingers'")
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            sdGuidanceScale(this)
            sdSteps(this)
            seed(this)
        }
        slash("upscale", "Upscale your precious creations!") {
            sdGuidanceScale(this)
            sdUpscale(this)
        }
        slash("upscale_image", "Upscale any image you want (Max 1024x1024 accepted)!") {
            option<Attachment>("image", "Image to upscale", required = true)
            option<String>("prompt", "Describe your original image, the better you can describe it, the better the results", required = true)
            sdGuidanceScale(this)
            sdUpscale(this)
        }
        slash("edit_image", "Want to make changes to an existing image? Here you go!") {
            option<Attachment>("image", "Image to edit", required = true)
            option<String>("instructions", "Tell the AI what you want, i.e 'make me look ugly'", required = true)
            option<Double>("input_scale", "How much the input is preserved. Default: 1.0") {
                this.setMaxValue(1.0)
                this.setMinValue(0.0)
            }
            seed(this)
            sdGuidanceScale(this)
            sdSteps(this)
        }
        slash("remove_background_from_image", "Turn images into transparent PNGs!") {
            option<Attachment>("image", "Image to remove background from", required = true)
        }
        slash("train", "How cool would it be to have yourself in our AI? Your pet?") {
            option<String>("word", "Word to assign your concept to. Example: 'peters_dog'", required = true)
            for(i in 0 until 4) {
                option<Attachment>("image_${i + 1}", "Image ${i + 1} to train on", required = i == 0)
            }
            option<Int>(
                "steps",
                "Higher steps typically lead to better learning",
                required = false
            ) {
                this.setMinValue(50)
                this.setMaxValue(150)
            }
            option<Double>(
                "learning_rate",
                "Higher learning rate could improve the concept, but too high will divert it",
                required = false
            ) {
                this.setMinValue(1e-4)
                this.setMaxValue(1e-2)
            }
        }
        slash("download_training", "Download your models made with Thingy!") {
            option<String>("word", "Word to assign your concept to. Example: 'peters_dog'", required = true)
            option<String>("mode", "Public or DMs?", required = true) {
                choice("Public", "public")
                choice("DMs", "dm")
            }
        }
        slash("img2img", "Make an existing image into your prompt!") {
            option<Attachment>("input_image", "Initial image", required = true)
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<Double>("strength", "How strong the change needs to be?", required = false)
            option<Int>("steps", "How much steps from the original image?", required = false) {
                this.setMaxValue(100)
                this.setMinValue(1)
            }
            sdGuidanceScale(this)
            seed(this)
            sdSize(this)
        }
        slash("link2img", "Make an existing image into your prompt!") {
            option<String>("input_image_url", "Link to initial image", required = true)
            option<String>("prompt", "Prompt to make i.e 'Monkey holding a beer'", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            seed(this)
            sdGuidanceScale(this)
            sdSize(this)
            option<Int>("strength", "How strong the change needs to be (In %)?", required = false) {
                this.setMinValue(0)
                this.setMaxValue(100)
            }
            option<Int>("steps", "How much steps from the original image?", required = false)
        }
        slash("magic_prompt", "Need help spicing up your prompt?") {
            option<String>("start", "Beginning of your prompt!", required = true)
            option<Int>("amount", "How many do you want? (Default: 5)", required = false) {
                this.setMaxValue(10)
                this.setMinValue(1)
            }
            option<Int>(
                "variation",
                "How much variation %? (Next word will be less related to previous word) (Default: 30)",
                required = false
            ) {
                this.setMaxValue(100)
                this.setMinValue(1)
            }
            seed(this)
        }
        slash("update", "[Admin only] Update mode: Prevents new images from being created for updating the bot") {
            option<Boolean>("on", "Turn update mode on or off", required = true)
        }
        slash("cancel", "Cancel latest item (by you) in the queue")
        slash("chapters", "Show your previous work!") {
            option<String>("type", "What kind of work do you want to see? (default: Images)", required = false) {
                choice("Images", "images")
                choice("Models", "pretrained_models")
            }
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
        if (config.artContestChannelID != null) {
            slash("submit_to_contest", "Submit art to contest!") {
            }
            slash("remove_from_contest", "Remove (your) art from contest!") {
            }
        }
        if (config.leaderboardChannelID != null) {
            slash("test_leaderboard", "Testing leaderboard! (Admin only)") {
                defaultPermissions = DefaultMemberPermissions.enabledFor(Permission.MODERATE_MEMBERS)
            }
        }
    }.queue()
}