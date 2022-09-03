package utils

import dev.minn.jda.ktx.interactions.components.Paginator
import dev.minn.jda.ktx.interactions.components.paginator
import dev.minn.jda.ktx.interactions.components.replyPaginator
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.time.Duration
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes

fun sendPagination(event: GenericCommandInteractionEvent, title: String, items: List<String>, itemsPerPage: Int): ReplyCallbackAction {
    val chunks = items.chunked(itemsPerPage)
    val pagesAmount = chunks.size
    val messages = (0 until pagesAmount).map { pageNumber ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Page ${pageNumber + 1} / $pagesAmount")
        page.setDescription(chunks[pageNumber].joinToString("\n"))
        page.setColor(0x33cc33)
        page.build()
    }.toTypedArray()
    val pagination = paginator(*messages, expireAfter = 10.minutes)
    pagination.addPages(*messages)
    return event.replyPaginator(pagination)
}