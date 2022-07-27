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

            val prompts = event.getOption("prompts")!!.asString
            if (event.getOption("preset") == null && anyItemsInString(
                    prompts.lowercase(),
                    listOf("pixel art", "pixelart", "pixel-art")
                )
            ) {
                val usePresetButton = jda.button(
                    label = "Use PixelArt preset",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        val entry = createEntry(pixelArtHard, it.hook)
                        it.message.editMessage(it.message.contentRaw).setActionRows(listOf()).queue()
                        it.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
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
                    try {
                        val entry = createEntry(null, it.hook)
                        it.message.editMessage(it.message.contentRaw).setActionRows(listOf()).queue()
                        it.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        it.reply_("Error! $e").setEphemeral(true).queue()
                    }
                }
                event.reply_("You have 'pixelart' or something similar in your prompt, but are using the default preset. Do you want to try the dedicated pixel art model?")
                    .addActionRow(listOf(usePresetButton, continueButton)).queue()
            } else {
                val entry = createEntry(null, event.hook)
                event.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.reply_("Error! $e").setEphemeral(true).queue()
        }
    }
}