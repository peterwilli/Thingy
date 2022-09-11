package ui

import Paginator
import dev.minn.jda.ktx.interactions.components.button
import getAverageColor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.requests.restaction.interactions.ReplyCallbackAction
import net.dv8tion.jda.api.utils.FileUpload
import paginator
import replyPaginator
import utils.bufferedImageToByteArray
import utils.takeSlice
import java.awt.image.BufferedImage
import java.net.URL
import kotlin.time.Duration.Companion.minutes

data class ImageSliderEntry(
    val description: String,
    val image: URL
)

fun sendImageSlider(title: String, items: List<ImageSliderEntry>): Paginator {
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
    return paginator(*messages, expireAfter = 10.minutes)
}

fun makeSelectImageFromQuilt(event: GenericCommandInteractionEvent, user: User, title: String, quilt: BufferedImage, totalImages: Int, callback: (btnEvent: ButtonInteractionEvent, index: Int) -> Unit): ReplyCallbackAction {
    val messages = (0 until totalImages).map { index ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Image ${index + 1} / $totalImages")
        page.setImage("attachment://${index + 1}.jpg")
        val imageSlice = takeSlice(quilt, totalImages, index)
        val avgColor = getAverageColor(imageSlice, 0, 0, imageSlice.width, imageSlice.height)
        page.setColor(avgColor)
        page.build()
    }.toTypedArray()
    val imageSelector = paginator(*messages, expireAfter = 10.minutes)
    imageSelector.injectMessageCallback = { idx, msgEdit ->
        val imageSlice = takeSlice(quilt, totalImages, idx)
        msgEdit.setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice, "jpg"), "${idx + 1}.jpg"))
    }
    imageSelector.customActionComponents = listOf(user.jda.button(
        label = "Select",
        style = ButtonStyle.PRIMARY,
        user = user
    ) { btnEvent ->
        callback(btnEvent, imageSelector.getIndex())
    })
    val firstSlice = takeSlice(quilt, totalImages, 0)
    return event.replyPaginator(imageSelector).setFiles(FileUpload.fromData(bufferedImageToByteArray(firstSlice, "jpg"), "1.jpg"))
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