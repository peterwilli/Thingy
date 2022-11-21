package commands.art_contest.cancel

import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import queueDispatcher

fun cancelCommand(jda: JDA) {
    jda.onCommand("cancel") { event ->
        queueDispatcher.queue.deleteLatestEntryByOwner(event.member!!.id)
        event.reply_("The last item by you is deleted, if you had at least 1 item in the queue!").queue()
    }
}