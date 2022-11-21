package commands.art_contest

import com.j256.ormlite.misc.TransactionManager
import config
import database.*
import database.models.ArtContestEntry
import database.models.ArtContestVote
import database.models.SharedArtCacheEntry
import dev.minn.jda.ktx.events.listener
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.message.react.MessageReactionAddEvent
import net.dv8tion.jda.api.events.message.react.MessageReactionRemoveEvent
import java.net.URL

private const val maxVotes = 3;

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
            val previousVotes = artContestVoteDao.queryBuilder().selectColumns().where().eq("userID", it.user!!.idLong).query()
            if (previousVotes.size >= maxVotes) {
                for (i in 0..(previousVotes.size - maxVotes)) {
                    val voteToDelete = previousVotes[i]
                    val entryVotedOn = artContestEntryDao.queryBuilder().selectColumns().where().eq("id", voteToDelete.contestEntryID).queryForFirst()
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
            val vote = artContestVoteDao.queryBuilder().selectColumns().where().eq("userID", it.user!!.idLong).and().eq("contestEntryID", entry.id).queryForFirst()
            vote.delete()
        }
    }
}