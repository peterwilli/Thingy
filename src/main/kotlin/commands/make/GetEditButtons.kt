package commands.make

import dev.minn.jda.ktx.interactions.components.button
import discoart.Client
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

fun getEditButtons(
    client: Client,
    jda: JDA,
    user: User,
    paramsList: List<DiscoDiffusionParameters>
): Pair<ActionRow, ActionRow> {
    val upscaleButtons = mutableListOf<Button>()
    for ((upscaleNumber, params) in paramsList.withIndex()) {
        val button = jda.button(
            label = "U${upscaleNumber + 1}",
            style = ButtonStyle.PRIMARY,
            user = user
        ) {
            upscale(it, upscaleNumber, params)
        }
        upscaleButtons.add(button)
    }
    val variateButtons = mutableListOf<Button>()
    for ((variateNumber, params) in paramsList.withIndex()) {
        val button = jda.button(
            label = "V${variateNumber + 1}",
            style = ButtonStyle.PRIMARY,
            user = user
        ) {
            variate(it, variateNumber, params)
        }
        variateButtons.add(button)
    }
    val upscaleRow = ActionRow.of(upscaleButtons)
    val variateRow = ActionRow.of(variateButtons)
    return upscaleRow to variateRow
}