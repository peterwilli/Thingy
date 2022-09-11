package utils

import java.awt.image.BufferedImage
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max

fun takeSlice(quilt: BufferedImage, totalImages: Int, index: Int): BufferedImage {
    // TODO: Nasty fix, will likely not work with grids of 5 images.
    val imagesPerRow = if(totalImages <= 2) {
        totalImages
    }
    else {
        ceil(totalImages / 2.toDouble()).toInt()
    }
    val imagesPerCol = if(totalImages <= 2) {
        0
    } else {
        ceil(totalImages / 2.toDouble()).toInt()
    }
    val row = index % imagesPerRow
    val col = if(imagesPerCol == 0) {
        0
    }
    else {
        floor(index / imagesPerCol.toDouble()).toInt()
    }
    val imageWidth = quilt.width / imagesPerRow
    val imageHeight = if (imagesPerCol == 0) {
        quilt.height
    }
    else {
        quilt.height / imagesPerCol
    }
    return quilt.getSubimage(row * imageWidth, col * imageHeight, imageWidth, imageHeight)
}