package utils

import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.util.*
import javax.imageio.ImageIO

fun imageFromDataURL(dataURL: String): BufferedImage? {
    val b64 = dataURL.substring(dataURL.indexOf(",") + 1)
    val imageByteArray = Base64.getDecoder().decode(b64)
    val inputStream = ByteArrayInputStream(imageByteArray)
    return ImageIO.read(inputStream)
}