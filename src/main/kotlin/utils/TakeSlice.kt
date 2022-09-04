package utils

import commands.make.DiffusionParameters
import database.models.ChapterEntry
import gson
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO
import kotlin.math.ceil
import kotlin.math.floor

fun takeSlice(entry: ChapterEntry, parameters: Array<DiffusionParameters>, index: Int): BufferedImage {
    val quilt = ImageIO.read(URL(entry.imageURL))
    val imagesPerRow = ceil(parameters.size / 2.toDouble()).toInt()
    val row = floor(index / imagesPerRow.toDouble()).toInt()
    val col = index % imagesPerRow
    val imageWidth = quilt.width / imagesPerRow
    val imageHeight = quilt.height / imagesPerRow
    return quilt.getSubimage(row * imageWidth, col * imageHeight, imageWidth, imageHeight)
}