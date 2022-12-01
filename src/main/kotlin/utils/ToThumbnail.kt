package utils

import net.coobird.thumbnailator.Thumbnails
import java.awt.image.BufferedImage

fun toThumbnail(image: BufferedImage): BufferedImage {
    return Thumbnails.of(image).size(512, 512).outputFormat("jpg").asBufferedImage()
}