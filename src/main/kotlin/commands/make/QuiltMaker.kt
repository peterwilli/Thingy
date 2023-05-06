package commands.make

import utils.resize
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.math.max

fun makeQuiltFromByteArrayList(images: List<ByteArray>, formatName: String = "jpg"): ByteArray {
    val bufferedImages = images.map { bytes ->
        val inputStream = ByteArrayInputStream(bytes)
        val image = ImageIO.read(inputStream)
        image
    }
    val quilt = makeQuilt(bufferedImages)
    val baos = ByteArrayOutputStream()
    ImageIO.write(quilt, formatName, baos)
    return baos.toByteArray()
}

fun makeQuilt(images: List<BufferedImage>): BufferedImage {
    if (images.size == 1) {
        return images.first()
    }
    var largestWidth = 0
    var largestHeight = 0
    for(image in images) {
        largestWidth = max(image.width, largestWidth)
        largestHeight = max(image.height, largestHeight)
    }
    val resizedImages = images.map {
        it.resize(largestWidth, largestHeight)
    }
    val pic = if (resizedImages.size == 2) {
        BufferedImage(resizedImages[0].width * 2, resizedImages[0].height, BufferedImage.TYPE_INT_RGB)
    } else {
        BufferedImage(resizedImages[0].width * 2, resizedImages[0].height * 2, BufferedImage.TYPE_INT_RGB)
    }
    val g = pic.graphics
    for (y in 0 until 2) {
        for (x in 0 until 2) {
            val index = y * 2 + x
            if (index >= resizedImages.size) {
                break
            }
            val image = resizedImages[index]
            g.drawImage(image, x * image.width, y * image.height, null)
        }
    }
    g.dispose()
    return pic
}
