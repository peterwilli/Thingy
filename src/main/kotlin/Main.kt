import com.sksamuel.hoplite.ConfigLoader
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.injectKTX
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
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

suspend fun main(args: Array<String>) {
    config = ConfigLoader().loadConfigOrThrow(args.getOrElse(0) {
        "./config.yml"
    })
    val builder: JDABuilder = JDABuilder.createDefault(config.botToken)
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

    jda.updateCommands {
        slash("make", "I make you a thing!") {
            option<String>("prompts", "prompts to make", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<String>("init_image", "A link to an previous image you wish to use!", required = false)
            option<Int>("seed", "Entropy for the random number generator, use the same seed to replicate results!", required = false)
        }
    }.queue()
}