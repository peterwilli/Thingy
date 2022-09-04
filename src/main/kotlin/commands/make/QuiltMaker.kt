package commands.make

import java.awt.Color
import java.awt.Font
import java.awt.Graphics
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO

fun makeQuiltFromByteArrayList(images: List<ByteArray>, formatName: String = "jpg"): ByteArray {
    val bufferedImages = images.mapIndexed { i, bytes ->
        val inputStream = ByteArrayInputStream(bytes)
        val image = ImageIO.read(inputStream)
        val font = Font("Arial", Font.BOLD, 60)
        val g: Graphics = image.graphics
        g.font = font
        g.color = Color.WHITE
        g.drawString(i.toString(), 10, 70)
        g.font = font
        g.color = Color.BLACK
        g.drawString(i.toString(), 10, 85)
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
    val pic = if (images.size == 2) {
        BufferedImage(images[0].width * 2, images[0].height, BufferedImage.TYPE_INT_RGB)
    } else {
        BufferedImage(images[0].width * 2, images[0].height * 2, BufferedImage.TYPE_INT_RGB)
    }
    val g = pic.graphics
    for (y in 0 until 2) {
        for (x in 0 until 2) {
            val index = y * 2 + x
            if (index >= images.size) {
                break
            }
            val image = images[index]
            g.drawImage(image, x * image.width, y * image.height, null)
        }
    }
    g.dispose()
    return pic
}
