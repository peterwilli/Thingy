package utils

import java.awt.Image
import java.awt.image.BufferedImage

fun BufferedImage.resize(newW: Int, newH: Int): BufferedImage {
    val tmp = this.getScaledInstance(newW, newH, Image.SCALE_SMOOTH)
    val dimg = BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB)
    val g2d = dimg.createGraphics()
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    return dimg
}

fun BufferedImage.upscaleToMinSize(minSize: Int): BufferedImage {
    val width = this.width
    val height = this.height
    val maxSide = maxOf(width, height)

    if (maxSide < minSize) {
        val scaleFactor = minSize.toDouble() / maxSide.toDouble()
        val newWidth = (width * scaleFactor).toInt()
        val newHeight = (height * scaleFactor).toInt()
        return this.resize(newWidth, newHeight)
    }
    return this
}

fun BufferedImage.resizeKeepRatio(maxSize: Int): BufferedImage {
    var width = this.width
    var height = this.height

    // check which side is larger and scale it down to maxSize
    if (width > height) {
        if (width > maxSize) {
            val ratio = maxSize.toDouble() / width.toDouble()
            width = maxSize
            height = (height * ratio).toInt()
        }
    } else {
        if (height > maxSize) {
            val ratio = maxSize.toDouble() / height.toDouble()
            height = maxSize
            width = (width * ratio).toInt()
        }
    }

    // create new BufferedImage with new dimensions
    return resize(width, height)
}

fun BufferedImage.resizeKeepRatio(maxSize: Int, resizeToSmallest: Boolean = false): BufferedImage {
    var width = this.width
    var height = this.height

    // check which side to resize based on boolean flag
    if (resizeToSmallest) {
        // resize the smallest side to maxSize
        if (width < height) {
            if (width > maxSize) {
                val ratio = maxSize.toDouble() / width.toDouble()
                width = maxSize
                height = (height * ratio).toInt()
            }
        } else {
            if (height > maxSize) {
                val ratio = maxSize.toDouble() / height.toDouble()
                height = maxSize
                width = (width * ratio).toInt()
            }
        }
    } else {
        // resize the largest side to maxSize
        if (width > height) {
            if (width > maxSize) {
                val ratio = maxSize.toDouble() / width.toDouble()
                width = maxSize
                height = (height * ratio).toInt()
            }
        } else {
            if (height > maxSize) {
                val ratio = maxSize.toDouble() / height.toDouble()
                height = maxSize
                width = (width * ratio).toInt()
            }
        }
    }

    // create new BufferedImage with new dimensions
    return resize(width, height)
}
