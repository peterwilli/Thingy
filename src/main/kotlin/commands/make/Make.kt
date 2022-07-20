import commands.make.*
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import discoart.Client
import io.grpc.ManagedChannel
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.Message
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

fun makeCommand(jda: JDA, channel: ManagedChannel) {
    jda.onCommand("make") { event ->
        try {
            val client = Client(channel)
            val params = optionsToParams(event) ?: return@onCommand
            val prompts = event.getOption("prompts")!!.asString
            val replyText = "**Generating image**\n> *${prompts}*"
            event.reply_(replyText).queue()
            coroutineScope {
                var finalImages: List<ByteArray>? = null
                val artCreator = async {
                    finalImages = client.createArt(params)
                }
                val imageProgress = async {
                    while (artCreator.isActive) {
                        val images = client.retrieveArt(params.artID)
                        if (images.isNotEmpty()) {
                            val quilt = makeQuiltFromByteArrayList(images)
                            event.hook.editOriginal(replyText).retainFiles(listOf())
                                .addFile(quilt, "${botName}_progress.jpg").queue()
                        }
                        delay(1000 * 5)
                    }
                }
                artCreator.await()
                event.hook.deleteOriginal().queue()
                if (finalImages != null) {
                    val quilt = makeQuiltFromByteArrayList(finalImages!!)
                    val (upscaleRow, variateRow) = getEditButtons(client, jda, event.user, params)
                    event.textChannel!!.sendMessage("${event.member!!.asMention}, we finished your image!\n> *${prompts}*")
                        .addFile(quilt, "${botName}_final.jpg").setActionRows(upscaleRow, variateRow).queue()
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}