package commands.make

import dev.minn.jda.ktx.interactions.components.button
import discoart.Client
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

fun getEditButtons(client: Client, jda: JDA, user: User, params: CreateArtParameters): Pair<ActionRow, ActionRow> {
    val upscaleButtons = mutableListOf<Button>()
    for (upscaleNumber in 0 until 4) {
        val button = jda.button(
            label = "U${upscaleNumber + 1}",
            style = ButtonStyle.PRIMARY,
            user = user
        ) {
            var upscaleParams = params.copy(artID = params.artID + "_U${upscaleNumber + 1}")
            upscale(client, it, upscaleNumber, upscaleParams)
        }
        upscaleButtons.add(button)
    }
    val variateButtons = mutableListOf<Button>()
    for (variateNumber in 0 until 4) {
        val button = jda.button(
            label = "V${variateNumber + 1}",
            style = ButtonStyle.PRIMARY,
            user = user
        ) {
            var variationParams = params.copy(artID = params.artID + "_V${variateNumber + 1}")
            variate(client, it, variateNumber, variationParams)
        }
        variateButtons.add(button)
    }
    val upscaleRow = ActionRow.of(upscaleButtons)
    val variateRow = ActionRow.of(variateButtons)
    return upscaleRow to variateRow
}