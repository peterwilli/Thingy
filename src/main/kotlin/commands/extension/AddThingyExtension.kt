import database.chapterDao
import database.chapterEntryDao
import database.models.ThingyExtension
import database.thingyExtensionDao
import dev.minn.jda.ktx.events.onCommand
import net.dv8tion.jda.api.JDA
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

fun addThingyExtensionCommand(jda: JDA) {
    jda.onCommand("add_extension") { event ->
        val scriptAttachment = event.getOption("script_file")!!.asAttachment
        event.deferReply().queue()
        val scriptURL = URL(scriptAttachment.url)
        val connection = scriptURL.openConnection()
        val scriptText = BufferedReader(InputStreamReader(connection.getInputStream())).use { inp ->
            inp.readText()
        }
        thingyExtensionDao.create(ThingyExtension(event.user, scriptText))
        event.hook.editOriginal("Done").queue()
    }
}
