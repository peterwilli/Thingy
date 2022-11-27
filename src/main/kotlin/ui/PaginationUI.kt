package ui

import GetPageCallback
import Paginator
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.MessageCreate
import getAverageColor
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import paginator
import utils.bufferedImageToByteArray
import utils.takeSlice
import java.awt.image.BufferedImage
import java.net.URL
import kotlin.time.Duration.Companion.minutes

data class ImageSliderEntry(
    val description: String,
    val image: URL
)

typealias GetImageCallback = ((index: Long) -> ImageSliderEntry)

fun sendImageSlider(title: String, amountOfImages: Long, onImage: GetImageCallback): Paginator {
    val getPageCallback: GetPageCallback = { index ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Page ${index + 1} / $amountOfImages")
        val entry = onImage(index)
        page.setImage(entry.image.toString())
        page.setDescription(entry.description)
        page.setColor(0x33cc33)
        MessageCreate(embeds = listOf(page.build()))
    }
    return paginator(amountOfImages, getPage = getPageCallback, expireAfter = 10.minutes)
}

fun makeSelectImageFromQuilt(
    event: GenericCommandInteractionEvent,
    user: User,
    title: String,
    quilt: BufferedImage,
    totalImages: Int,
    callback: suspend (btnEvent: ButtonInteractionEvent, index: Int) -> Unit
): Paginator {
    val getPageCallback: GetPageCallback = { index ->
        val page = EmbedBuilder()
        page.setTitle(title)
        page.setFooter("Image ${index + 1} / $totalImages")
        page.setImage("attachment://${index + 1}.jpg")
        val imageSlice = takeSlice(quilt, totalImages, index.toInt())
        val avgColor = getAverageColor(imageSlice, 0, 0, imageSlice.width, imageSlice.height)
        page.setColor(avgColor)
        page.build()
        val msgCreate = MessageCreate(
            embeds = listOf(page.build()),
            files = listOf(FileUpload.fromData(bufferedImageToByteArray(imageSlice, "jpg"), "1.jpg"))
        )
        msgCreate
    }
    val imageSelector =
        paginator(amountOfPages = totalImages.toLong(), getPage = getPageCallback, expireAfter = 10.minutes)
    imageSelector.injectMessageCallback = { idx, msgEdit ->
        val imageSlice = takeSlice(quilt, totalImages, idx.toInt())
        msgEdit.setFiles(FileUpload.fromData(bufferedImageToByteArray(imageSlice, "jpg"), "${idx + 1}.jpg"))
    }
    imageSelector.customActionComponents = listOf(user.jda.button(
        label = "Select",
        style = ButtonStyle.PRIMARY,
        user = user
    ) { btnEvent ->
        callback(btnEvent, imageSelector.getIndex().toInt())
    })
    return imageSelector
}