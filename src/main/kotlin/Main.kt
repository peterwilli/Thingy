import com.google.protobuf.Struct
import commands.make.DiffusionConfig
import commands.make.diffusion_configs.standardSmall
import dev.minn.jda.ktx.interactions.commands.option
import dev.minn.jda.ktx.interactions.commands.slash
import dev.minn.jda.ktx.interactions.commands.updateCommands
import dev.minn.jda.ktx.jdabuilder.injectKTX
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.JDABuilder
import net.dv8tion.jda.api.entities.Activity
import net.dv8tion.jda.api.utils.Compression
import net.dv8tion.jda.api.utils.cache.CacheFlag
import org.apache.commons.text.CaseUtils
import utils.camelToSnakeCase
import kotlin.reflect.full.memberProperties
import kotlin.time.Duration


fun main(args: Array<String>) {
    val builder: JDABuilder = JDABuilder.createDefault(args[0])
    builder.apply {
        injectKTX(timeout = Duration.INFINITE)
    }
    builder.disableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
    builder.setBulkDeleteSplittingEnabled(false)
    builder.setCompression(Compression.ZLIB)
    builder.setActivity(Activity.competing("Your prompts suck!"))
    val channel = ManagedChannelBuilder.forAddress("localhost", 51001).maxInboundMessageSize(1024 * 1024 * 1024).usePlaintext().build()
    val jda = builder.build()
    initCommands(jda, channel)
}

fun initCommands(jda: JDA, channel: ManagedChannel) {
    makeCommand(jda, channel)

    jda.updateCommands {
        slash("make", "I make you a thing!") {
            option<String>("prompts", "prompts to make", required = true)
            option<String>("ar", "aspect ratio (i.e 16:9)", required = false)
            option<String>("init_image", "A link to an previous image you wish to use!", required = false)
            option<Int>("seed", "Entropy for the random number generator, use the same seed to replicate results!", required = false)
        }
    }.queue()
}