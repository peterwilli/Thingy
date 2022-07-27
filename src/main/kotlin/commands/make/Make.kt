import commands.make.DiffusionConfig
import commands.make.FairQueueEntry
import commands.make.FairQueueType
import commands.make.diffusion_configs.pixelArtHard
import commands.make.optionsToParams
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import utils.anyItemsInString

fun makeCommand(jda: JDA) {
    jda.onCommand("make") { event ->
        try {
            fun createEntry(overridePreset: DiffusionConfig?, hook: InteractionHook): FairQueueEntry {
                var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
                    optionsToParams(event, overridePreset, it)
                }
                return FairQueueEntry("Generating Image", FairQueueType.Create, event.member!!.id, batch, hook)
            }

            fun getStartPrompt(entry: FairQueueEntry): String {
                return "**Added to queue**\n> *${entry.getHumanReadablePrompts()}*"
            }

            val prompts = event.getOption("prompts")!!.asString
            if (event.getOption("preset") == null && anyItemsInString(prompts.lowercase(), listOf("pixel art", "pixelart", "pixel-art"))) {
                val usePresetButton = jda.button(
                    label = "Use PixelArt preset",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        val entry = createEntry(pixelArtHard, it.hook)
                        it.reply_(getStartPrompt(entry)).queue()
                        it.message.delete().queue()
                        queueDispatcher.queue.addToQueue(entry)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        it.reply_("Error! $e").setEphemeral(true).queue()
                    }
                }
                val continueButton = jda.button(
                    label = "Continue",
                    style = ButtonStyle.SECONDARY,
                    user = event.user
                ) {
                    val entry = createEntry(null, it.hook)
                    it.reply_(getStartPrompt(entry)).queue()
                    it.message.delete().queue()
                    queueDispatcher.queue.addToQueue(entry)
                }
                event.reply_("You have 'pixelart' or something similar in your prompt, but are using the default preset. Do you want to try the dedicated pixel art model?").addActionRow(listOf(usePresetButton, continueButton)).queue()
            } else {
                val entry = createEntry(null, event.hook)
                event.reply_(getStartPrompt(entry)).queue()
                queueDispatcher.queue.addToQueue(entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
    }
}