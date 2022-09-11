package utils

import database.userDao
import net.dv8tion.jda.api.entities.User
import org.atteo.evo.inflector.English
import java.awt.Color
import java.awt.Font
import java.awt.image.BufferedImage

val profileCardHeight = 128
fun makeProfileCard(user: User, overrideBackground: BufferedImage?): BufferedImage {
    val thingyUser = userDao.queryBuilder().selectColumns("id", "generationsDone").where().eq("discordUserID", user.id).queryForFirst()
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
    g.color = Color.WHITE
    g.font = valFont
    g.drawString(user.name, 20, 25)

    fun addLabelAndValue(label: String, value: String) {
        g.drawString(value, 20, 60)
        val valWidth = g.fontMetrics.stringWidth(value)
        g.font = labelFont
        g.drawString(label, valWidth + 40, 60)
    }
    addLabelAndValue(English.plural("Generation", if(thingyUser.generationsDone > 1) 2 else 1), thingyUser.generationsDone.toString())
    return card
}