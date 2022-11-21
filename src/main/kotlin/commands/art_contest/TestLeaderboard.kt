package commands.art_contest

import commands.make.standardPermissionList
import commands.make.validatePermissions
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import kotlinx.coroutines.delay
import net.dv8tion.jda.api.JDA

fun testLeaderboardCommand(jda: JDA) {
    jda.onCommand("test_leaderboard") { event ->
        if (!validatePermissions(event, standardPermissionList)) {
            return@onCommand
        }
        event.reply_("Test is starting in a few seconds...").setEphemeral(true).queue()
        delay(2000)
        sendLeaderboard(event.jda)
    }
}