package utils

import com.beust.klaxon.Json
import com.google.gson.JsonObject
import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import kotlinx.coroutines.runBlocking
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.events.interaction.command.GenericCommandInteractionEvent
import net.dv8tion.jda.api.interactions.Interaction
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle

fun embedsCallback(jda: JDA, event: GenericCommandInteractionEvent, embeds: Pair<Array<String>, Array<String>>, params: JsonObject, callback: (Boolean, JsonObject, InteractionHook) -> Unit) {
    event.hook.editMessage(
        content = "Hey there! There are pretrained models available for the following words. Want to use them? Words are:\n${
            embeds.first.joinToString(
                "\n"
            )
        }"
    ).setActionRow(listOf(
        jda.button(
            label = "Use pretrained models!",
            style = ButtonStyle.PRIMARY,
            user = event.user
        ) { buttonEvent ->
            buttonEvent.hook.editMessage(content = "Working...").setComponents(listOf()).queue {
                var prompt = params["prompt"].asString
                val jsonEmbeds = params["embeds"].asJsonArray
                for (embed in embeds.first.zip(embeds.second)) {
                    prompt = prompt.replace(Regex("\\b${embed.first}\\b", RegexOption.IGNORE_CASE), "<${embed.first}>")
                    jsonEmbeds.add(embed.second)
                }
                params.addProperty("prompt", prompt)
                params.add("embeds", jsonEmbeds)
                callback(true, params, buttonEvent.hook)
            }
        },
        jda.button(
            label = "Use raw prompts!",
            style = ButtonStyle.SECONDARY,
            user = event.user
        ) { buttonEvent ->
            buttonEvent.hook.editMessage(content = "Working...").setComponents(listOf()).queue {
                callback(false, params, buttonEvent.hook)
            }
        }
    )).queue()
}

fun checkForEmbeds(prompt: String, userId: Long): Pair<Array<String>, Array<String>> {
    val result = mutableListOf<String>()
    val embeds = mutableListOf<String>()

    val user = userDao.queryBuilder().selectColumns("id").where()
        .eq("discordUserID", userId).queryForFirst()

    val split = prompt.replace(",", "").split(" ")
    for (word in split) {
        val results = chapterEntryDao.queryBuilder().where().eq("metadata", word.lowercase()).and()
            .eq("chapterType", ChapterEntry.Companion.Type.TrainedModel.ordinal).query()
        for (entry in results) {
            if (entry.chapterVisibility == ChapterEntry.Companion.Visibility.Public.ordinal) {
                result.add(entry.metadata!!)
                embeds.add(entry.data)
                continue
            }

            if (user != null) {
                val chapter =
                    chapterDao.queryBuilder().selectColumns().where()
                        .eq("id", entry.chapterID).and().eq("userID", user.id).queryForFirst()
                if (chapter != null) {
                    result.add(entry.metadata!!)
                    embeds.add(entry.data)
                }
            }
        }
    }
    return result.toTypedArray() to embeds.toTypedArray()
}