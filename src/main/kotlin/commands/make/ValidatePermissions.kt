package commands.make

import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.Permission
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.internal.utils.PermissionUtil

fun validatePermissions(event: GenericCommandInteractionEvent): Boolean {
    val permsToCheck = listOf(Permission.VIEW_CHANNEL, Permission.MESSAGE_SEND)
    val permsMissing = mutableListOf<Permission>()
    for (perm in permsToCheck) {
        if (!PermissionUtil.checkPermission(event.textChannel, event.guild!!.selfMember, perm)) {
            permsMissing.add(perm)
        }
    }
    if (permsMissing.isNotEmpty()) {
        event.reply_(
            "Sorry, required permissions are missing for meto work in this channel! Permission missing: ${
                permsMissing.joinToString(
                    ", "
                )
            }"
        ).setEphemeral(true).queue()
        return false
    }
    return true
}