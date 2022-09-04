package utils

import Paginator
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import paginator
import java.net.URL
import kotlin.time.Duration.Companion.minutes

data class ImageSliderEntry(
    val description: String,
    val image: URL
)

fun sendImageSlider(event: GenericCommandInteractionEvent, title: String, items: List<ImageSliderEntry>): Paginator {
    val pagesAmount = items.size
    println("pagesAmount: $pagesAmount")
    val messages = items.mapIndexed { index, entry ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Page ${index + 1} / $pagesAmount")
        page.setImage(entry.image.toString())
        page.setDescription(entry.description)
        page.setColor(0x33cc33)
        page.build()
    }.toTypedArray()
    return paginator(*messages, expireAfter = 10.minutes)
}

fun sendPagination(
    event: GenericCommandInteractionEvent,
    title: String,
    items: List<String>,
    itemsPerPage: Int
): Paginator {
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
    return paginator(*messages, expireAfter = 10.minutes)
}