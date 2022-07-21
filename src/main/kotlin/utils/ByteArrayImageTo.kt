package utils

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun byteArrayImageTo(imageByteArray: ByteArray, convertTo: String): ByteArray {
    val inputStream = ByteArrayInputStream(imageByteArray)
    val bufferedImage = ImageIO.read(inputStream)
    val baos = ByteArrayOutputStream()
    ImageIO.write(bufferedImage, convertTo, baos)
    return baos.toByteArray()
}