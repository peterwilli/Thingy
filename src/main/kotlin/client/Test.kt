package client

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import database.models.ChapterEntry
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import net.dv8tion.jda.api.interactions.InteractionHook
import redis.clients.jedis.Jedis

suspend fun main() {
    val client = NewClient(Jedis("localhost", 6379))
    coroutineScope {
        async {
            client.checkLoop()
        }

        val paramsTest = JsonArray()
        val param = JsonObject()
        param.addProperty("test", "lol")
        paramsTest.add(param)
        val entry = QueueEntry("stuff", "peter", paramsTest, JsonObject(), arrayOf(), null, null, ChapterEntry.Companion.Type.Image, ChapterEntry.Companion.Visibility.Private, "png", listOf("test"), false)
        client.uploadEntry(entry)
    }
}