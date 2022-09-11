import java.awt.Color
import java.awt.image.BufferedImage

fun getAverageColor(
    bi: BufferedImage, x0: Int, y0: Int, w: Int, h: Int
): Color {
    val x1 = x0 + w
    val y1 = y0 + h
    var sumr: Long = 0
    var sumg: Long = 0
    var sumb: Long = 0
    for (x in x0 until x1) {
        for (y in y0 until y1) {
            val pixel = Color(bi.getRGB(x, y))
            sumr += pixel.getRed()
            sumg += pixel.getGreen()
            sumb += pixel.getBlue()
        }
    }
    val num = w * h
    return Color((sumr / num).toInt(), (sumg / num).toInt(), (sumb / num).toInt())
}