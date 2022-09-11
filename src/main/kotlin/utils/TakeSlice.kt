package utils

import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor

fun takeSlice(quilt: BufferedImage, totalImages: Int, index: Int): BufferedImage {
    val imagesPerRow = ceil(totalImages / 2.toDouble()).toInt()
    val row = index % imagesPerRow
    val col = floor(index / imagesPerRow.toDouble()).toInt()
    val imageWidth = quilt.width / imagesPerRow
    val imageHeight = quilt.height / imagesPerRow
    return quilt.getSubimage(row * imageWidth, col * imageHeight, imageWidth, imageHeight)
}