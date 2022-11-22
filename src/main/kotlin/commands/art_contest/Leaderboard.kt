package commands.art_contest

import config
import database.artContestEntryDao
import database.artContestVoteDao
import database.models.ArtContestEntry
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.ReadyEvent
import org.slf4j.LoggerFactory
import utils.sanitize
import java.lang.StringBuilder
import java.time.LocalTime
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.reflect.KClass

var lastHour = 0

fun getTopN(max: Int): Array<Pair<ArtContestEntry, Long>> {
    var lastEntryID: Long = 0
    val result = mutableListOf<Pair<ArtContestEntry, Long>>()
    while(true) {
        val entry =
            artContestEntryDao.queryBuilder().selectColumns().where().ge("id", lastEntryID).queryForFirst() ?: break
        val votesCount = artContestVoteDao.queryBuilder().where().eq("contestEntryID", entry.id).countOf()
        result.add(entry to votesCount)
        if (result.size > max) {
            result.sortByDescending {
                it.second
            }
            result.removeLast()
        }
        lastEntryID = entry.id + 1
    }
    result.sortByDescending {
        it.second
    }
    return result.toTypedArray()
}

fun sendLeaderboard(jda: JDA) {
    val leaderboard = StringBuilder()
    leaderboard.append("**Leaderboard!**\n")
    val topNEntries = getTopN(10)
    for ((i, entry) in topNEntries.withIndex()) {
        val user = userDao.queryBuilder().selectColumns("discordUserID").where()
            .eq("id", entry.first.userID).queryForFirst()
        val discordUser = jda.retrieveUserById(user.discordUserID!!).complete()
        val place = when (i) {
            0 -> {
                ":first_place:"
            }
            1 -> {
                ":second_place:"
            }
            2 -> {
                ":third_place:"
            }
            else -> {
                "#${i + 1}"
            }
        }
        leaderboard.append("$place (${entry.second} votes): *${sanitize(entry.first.prompt)}* by **${discordUser.name}**: ${entry.first.messageLink}\n")
    }
    val channel = jda.getTextChannelById(config.leaderboardChannelID!!)!!
    channel.sendMessage(leaderboard.toString()).queue()
}

fun leaderboardScheduler(jda: JDA, triggerHour: Int) {
    val logger = LoggerFactory.getLogger(KClass::class.java)
    jda.listener<ReadyEvent> { _ ->
        logger.debug("leaderboardTimer scheduler turned on")
        val dayScheduler = Executors.newScheduledThreadPool(1)
        dayScheduler.scheduleAtFixedRate(
            {
                try {
                    val time = LocalTime.now()
                    logger.debug("leaderboardTimer, hour: ${time.hour}")
                    if (lastHour != time.hour && time.hour == triggerHour) {
                        sendLeaderboard(jda)
                    }
                    lastHour = time.hour
                } catch (e: Exception) {
                    logger.error("leaderboardTimer Error! (ignored)")
                    e.printStackTrace()
                }
            },
            0,
            TimeUnit.HOURS.toSeconds(1),
            TimeUnit.SECONDS
        )
    }
}