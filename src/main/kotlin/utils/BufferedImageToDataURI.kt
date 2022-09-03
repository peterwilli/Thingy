package utils

import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.net.URI
import java.util.*
import javax.imageio.ImageIO

fun bufferedImageToDataURI(image: BufferedImage): URI {
    val out = ByteArrayOutputStream()
    ImageIO.write(image, "PNG", out)
    val bytes: ByteArray = out.toByteArray()
    val base64bytes: String = Base64.getEncoder().encodeToString(bytes)
    return URI.create("data:image/png;base64,$base64bytes")
}