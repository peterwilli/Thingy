package commands.make

import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import org.atteo.evo.inflector.English

val standardPermissionList = listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)

fun validatePermissions(event: GenericCommandInteractionEvent, permsToCheck: List<Permission>): Boolean {
    val permsMissing = mutableListOf<Permission>()
    for (perm in permsToCheck) {
        if (!event.guild!!.selfMember.hasPermission(event.guildChannel, perm)) {
            permsMissing.add(perm)
        }
    }
    if (permsMissing.isNotEmpty()) {
        event.reply_(
            "Sorry, required permissions are missing for me to work in this channel! ${
                English.plural(
                    "Permission",
                    permsMissing.size
                )
            } missing: `${
                permsMissing.joinToString(
                    ", "
                )
            }`"
        ).setEphemeral(true).queue()
        return false
    }
    return true
}