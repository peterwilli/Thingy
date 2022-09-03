import commands.make.*
import commands.make.diffusion_configs.catchRecommendedConfig
import commands.make.diffusion_configs.disco.discoDiffusionConfigs
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

fun discoDiffusionCommand(jda: JDA) {
    jda.onCommand("disco_diffusion") { event ->
        try {
            if (!validatePermissions(event)) {
                return@onCommand
            }
            val prompts = event.getOption("prompts")!!.asString
            fun createEntry(overridePreset: DiscoDiffusionConfig?, hook: InteractionHook): FairQueueEntry {
                var batch = (0 until config.hostConstraints.totalImagesInMakeCommand).map {
                    optionsToDiscoDiffusionParams(event, overridePreset, it)
                }
                return FairQueueEntry(
                    "Generating Image",
                    FairQueueType.DiscoDiffusion,
                    event.member!!.id,
                    batch,
                    hook,
                    null
                )
            }

            val catchResult = catchRecommendedConfig(prompts)
            if (event.getOption("preset") == null && catchResult != null) {
                val (wordCaught, recommendedPreset) = catchResult
                val newPreset = discoDiffusionConfigs[recommendedPreset]!!.first
                val presetDesc = discoDiffusionConfigs[recommendedPreset]!!.second
                val usePresetButton = jda.button(
                    label = "Use $presetDesc",
                    style = ButtonStyle.PRIMARY,
                    user = event.user
                ) {
                    try {
                        val entry = createEntry(newPreset, it.hook)
                        it.message.editMessage(it.message.contentRaw).setActionRow(listOf()).queue()
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
                        it.message.editMessage(it.message.contentRaw).setActionRow(listOf()).queue()
                        it.reply_(queueDispatcher.queue.addToQueue(entry)).queue()
                    } catch (e: Exception) {
                        e.printStackTrace()
                        it.reply_("Error! $e").setEphemeral(true).queue()
                    }
                }
                event.reply_("You have '$wordCaught' in your prompt, but are using the default preset. Do you want to try a more suitable preset?")
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