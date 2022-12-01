package ui

import GetPageCallback
import Paginator
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.MessageCreate
import getAverageColor
import net.coobird.thumbnailator.Thumbnails
import net.dv8tion.jda.api.EmbedBuilder
import net.dv8tion.jda.api.entities.User
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import net.dv8tion.jda.api.utils.FileUpload
import paginator
import utils.bufferedImageToByteArray
import utils.takeSlice
import utils.toThumbnail
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
    user: User,
    title: String,
    quilt: BufferedImage,
    totalImages: Int,
    callback: suspend (btnEvent: ButtonInteractionEvent, index: Int) -> Unit
): Paginator {

    val getPageCallback: GetPageCallback = { index ->
        val page = EmbedBuilder()
        page.setTitle(title)
        val imageSlice = takeSlice(quilt, totalImages, index.toInt())
        page.setFooter("Image ${index + 1} / $totalImages (size: ${imageSlice.width}x${imageSlice.height})")
        page.setImage("attachment://${index + 1}.jpg")
        val previewImage = toThumbnail(imageSlice)
        val avgColor = getAverageColor(previewImage, 0, 0, previewImage.width, previewImage.height)
        page.setColor(avgColor)
        page.build()
        val msgCreate = MessageCreate(
            embeds = listOf(page.build()),
            files = listOf(FileUpload.fromData(bufferedImageToByteArray(previewImage, "jpg"), "1.jpg"))
        )
        msgCreate
    }
    val imageSelector =
        paginator(amountOfPages = totalImages.toLong(), getPage = getPageCallback, expireAfter = 10.minutes)
    imageSelector.injectMessageCallback = { idx, msgEdit ->
        val imageSlice = takeSlice(quilt, totalImages, idx.toInt())
        val previewImage = toThumbnail(imageSlice)
        msgEdit.setFiles(FileUpload.fromData(bufferedImageToByteArray(previewImage, "jpg"), "${idx + 1}.jpg"))
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