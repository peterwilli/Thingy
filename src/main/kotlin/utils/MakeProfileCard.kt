package utils

import database.userDao
import net.dv8tion.jda.api.entities.User
import org.atteo.evo.inflector.English
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage
import java.net.URL
import javax.imageio.ImageIO

val profileCardHeight = 128
fun makeProfileCard(user: User, overrideBackground: BufferedImage?): BufferedImage {
    val thingyUser = userDao.queryBuilder().selectColumns("id", "generationsDone").where().eq("discordUserID", user.id)
        .queryForFirst()
    val pfp = ImageIO.read(URL(user.avatarUrl)).resize(64, 64)
    val card = BufferedImage(512, profileCardHeight, BufferedImage.TYPE_INT_RGB)
    val labelFont = Font("Arial", Font.PLAIN, 16)
    val valFont = Font("Arial", Font.BOLD, 18)
    val g = card.graphics

    fun drawBackground(bg: BufferedImage) {
        g.drawImage(bg, 0, 0, card.width, card.height, null)
    }
    if (overrideBackground != null) {
        drawBackground(overrideBackground)
    }
    g.drawImage(pfp, 10, 10, null)
    val pfpSideX = pfp.width + 10
    g.color = Color.WHITE
    g.font = valFont
    g.drawString(user.name, pfpSideX + 10, 25)

    fun addLabelAndValue(x: Int, y: Int, label: String, value: String) {
        g.drawString(value, x, y)
        val valWidth = g.fontMetrics.stringWidth(value)
        g.font = labelFont
        g.drawString(label, x + valWidth + 10, y)
    }
    addLabelAndValue(
        pfpSideX + 10,
        60,
        English.plural("Generation", if (thingyUser.generationsDone > 1) 2 else 1),
        thingyUser.generationsDone.toString()
    )
    return card
}