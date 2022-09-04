package utils

import dev.minn.jda.ktx.interactions.components.paginator
import dev.minn.jda.ktx.interactions.components.replyPaginator
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import java.net.URL
import kotlin.math.ceil
import kotlin.time.Duration.Companion.minutes

data class ImageSliderEntry(
    val description: String,
    val image: URL
)

fun sendImageSlider(event: GenericCommandInteractionEvent, title: String, items: List<ImageSliderEntry>): ReplyCallbackAction {
    val pagesAmount = items.size
    val messages = items.mapIndexed { index, entry ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Page ${index + 1} / $pagesAmount")
        page.setImage(entry.image.toString())
        page.setDescription(entry.description)
        page.setColor(0x33cc33)
        page.build()
    }.toTypedArray()
    val pagination = paginator(*messages, expireAfter = 10.minutes)
    pagination.addPages(*messages)
    return event.replyPaginator(pagination)
}

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