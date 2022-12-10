package commands.art_contest

import com.j256.ormlite.misc.TransactionManager
import config
import database.artContestEntryDao
import database.artContestVoteDao
import database.connectionSource
import database.models.ArtContestVote
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.listener
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent

private const val MAX_VOTES = 3;

private suspend fun removeTooManyVotes(jda: JDA) {
    val channel = jda.getTextChannelById(config.artContestChannelID!!)!!
    val usersCount = mutableMapOf<String, Int>()
    for (message in channel.iterableHistory) {
        for (reaction in message.reactions) {
            val users = reaction.retrieveUsers().await()
            for(user in users) {
                if(user.isBot) {
                    continue
                }
                val currentCount = usersCount.getOrDefault(user.id, 0)
                usersCount[user.id] = currentCount + 1
                if (usersCount[user.id]!! > MAX_VOTES) {
                    reaction.removeReaction(user).await()
                    println("[removeTooManyVotes] Removing ${user.name} because they are above MAX_VOTES ($MAX_VOTES)")
                }
            }
        }
    }
}

suspend fun syncVotes(jda: JDA) {
    removeTooManyVotes(jda)
    TransactionManager.callInTransaction(connectionSource) {
        val votesDelete = artContestVoteDao.deleteBuilder()
        artContestVoteDao.delete(votesDelete.prepare())

        runBlocking {
            val channel = jda.getTextChannelById(config.artContestChannelID!!)!!
            for (message in channel.iterableHistory) {
                val entry = artContestEntryDao.queryBuilder().selectColumns("id").where().eq("messageID", message.idLong)
                    .queryForFirst()
                for (reaction in message.reactions) {
                    val users = reaction.retrieveUsers().await()
                    for(user in users) {
                        if(user.isBot) {
                            continue
                        }
                        val vote =
                            ArtContestVote(
                                user!!.idLong, entry.id
                            )
                        artContestVoteDao.create(vote)
                    }
                }
            }
        }
    }
}

fun voteReactionWatcher(jda: JDA) {
    jda.listener<MessageReactionAddEvent> {
        if (it.user!!.isBot) {
            return@listener
        }
        val artContestChannel = jda.getTextChannelById(config.artContestChannelID!!)!!
        if (it.channel != artContestChannel) {
            return@listener
        }
        TransactionManager.callInTransaction(connectionSource) {
            val previousVotes =
                artContestVoteDao.queryBuilder().selectColumns().where().eq("userID", it.user!!.idLong).query()
            if (previousVotes.size >= MAX_VOTES) {
                for (i in 0..(previousVotes.size - MAX_VOTES)) {
                    val voteToDelete = previousVotes[i]
                    val entryVotedOn =
                        artContestEntryDao.queryBuilder().selectColumns().where().eq("id", voteToDelete.contestEntryID)
                            .queryForFirst()
                    voteToDelete.delete()
                    val message = artContestChannel.retrieveMessageById(entryVotedOn.messageID).complete()
                    message.removeReaction(defaultVoteEmoji, it.user!!).queue()
                }
            }
            val entry = artContestEntryDao.queryBuilder().selectColumns("id").where().eq("messageID", it.messageIdLong)
                .queryForFirst()

            val vote =
                ArtContestVote(
                    it.user!!.idLong, entry.id
                )
            artContestVoteDao.create(vote)
        }
    }

    jda.listener<MessageReactionRemoveEvent> {
        if (it.user!!.isBot) {
            return@listener
        }
        val artContestChannel = jda.getTextChannelById(config.artContestChannelID!!)!!
        if (it.channel != artContestChannel) {
            return@listener
        }
        TransactionManager.callInTransaction(connectionSource) {
            val entry = artContestEntryDao.queryBuilder().selectColumns("id").where().eq("messageID", it.messageIdLong)
                .queryForFirst()
            artContestVoteDao.queryBuilder().selectColumns().where().eq("userID", it.user!!.idLong).and()
                .eq("contestEntryID", entry.id).queryForFirst()?.delete()
        }
    }
}