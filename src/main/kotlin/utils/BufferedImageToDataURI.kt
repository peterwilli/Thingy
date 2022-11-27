package utils

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*
import javax.imageio.ImageIO

fun bufferedImageToBase64(image: BufferedImage, format: String = "PNG"): String {
    val out = ByteArrayOutputStream()
    ImageIO.write(image, format, out)
    val bytes: ByteArray = out.toByteArray()
    return Base64.getEncoder().encodeToString(bytes)
}

fun bufferedImageToDataURI(image: BufferedImage): URI {
    return URI.create("data:image/png;base64,${bufferedImageToBase64(image)}")
}