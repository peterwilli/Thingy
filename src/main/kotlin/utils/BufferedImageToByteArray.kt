package utils

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun bufferedImageToByteArray(image: BufferedImage, formatName: String = "jpg"): ByteArray {
    val baos = ByteArrayOutputStream()
    ImageIO.write(image, formatName, baos)
    return baos.toByteArray()
}