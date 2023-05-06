package client

import SMLBLAKE3
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import database.models.ChapterEntry
import database.models.UserChapter
import kotlinx.coroutines.*
import net.dv8tion.jda.api.interactions.InteractionHook
import redis.clients.jedis.Jedis
import redis.clients.jedis.JedisPooled
import utils.getResourceAsText
import java.util.Collections


class NewClient(private val jedis: Jedis, private val entries: MutableList<QueueEntry> = Collections.synchronizedList(mutableListOf())) {
    fun uploadEntry(entry: QueueEntry) {
        val docs = entry.batchToDocs()
        entry.currentStatuses = docs.map { doc ->
            QueueEntryStatus(doc, -1)
        }
        entry.moveToNextScriptIfAny(jedis)
        entries.add(entry)
    }

    suspend fun checkLoop() {
        while (true) {
            for(entry in entries) {
                val modified = entry.sync(jedis)
                if(modified) {
                    // TODO: update message based on Entry
                }
                entry.moveToNextScriptIfAny(jedis)
            }
            entries.retainAll {
                it.clean(jedis)
                !it.isDone()
            }
            println("entries: ${entries.size}")
            delay(1000)
        }
    }
}