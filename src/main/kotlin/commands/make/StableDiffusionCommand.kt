import com.google.gson.JsonArray
import com.google.gson.JsonObject
import commands.make.*
import database.chapterDao
import database.chapterEntryDao
import database.models.ChapterEntry
import database.userDao
import dev.minn.jda.ktx.coroutines.await
import dev.minn.jda.ktx.events.onCommand
import dev.minn.jda.ktx.interactions.components.button
import dev.minn.jda.ktx.messages.editMessage
import dev.minn.jda.ktx.messages.reply_
import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle
import utils.optionsToJson
import utils.sendException
import utils.withDefaults
import java.util.*
import kotlin.math.pow
import kotlin.random.Random

fun getSdJsonDefaults(): JsonObject {
    val obj = JsonObject()
    obj.addProperty("seed", Random.nextInt(0, 2.toDouble().pow(32).toInt()))
    obj.addProperty("ar", "1:1")
    obj.addProperty("size", 512)
    obj.addProperty("_hf_auth_token", config.bot.hfToken)
    obj.addProperty("guidance_scale", 9.0)
    obj.addProperty("steps", 25)
    obj.add("embeds", JsonArray(0))
    return obj
}

val sdHiddenParameters = arrayOf("embeds")

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

fun stableDiffusionCommand(jda: JDA) {
    jda.onCommand("stable_diffusion") { event ->
        try {
            if (!validatePermissions(event, standardPermissionList)) {
                return@onCommand
            }
            event.deferReply().await()
            val params = event.optionsToJson().withDefaults(getSdJsonDefaults())
            val embeds = checkForEmbeds(event.getOption("prompt")!!.asString, event.user.idLong)

            fun createEntry(hook: InteractionHook): FairQueueEntry {
                var batch = JsonArray()
                for (idx in 0 until config.hostConstraints.totalImagesInMakeCommand) {
                    val clonedParams = params.deepCopy()
                    val seed = clonedParams["seed"].asLong
                    clonedParams.addProperty("seed", seed + idx)
                    batch.add(clonedParams)
                }
                return FairQueueEntry(
                    "Making Images",
                    event.member!!.id,
                    batch,
                    sdHiddenParameters,
                    getScriptForSize(batch[0].asJsonObject.get("size").asInt),
                    hook,
                    null,
                    ChapterEntry.Companion.Type.Image,
                    ChapterEntry.Companion.Visibility.Public
                )
            }

            if (embeds.first.isNotEmpty()) {
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
                    ) {
                        var prompt = params["prompt"].asString
                        val jsonEmbeds = params["embeds"].asJsonArray
                        for (embed in embeds.first.zip(embeds.second)) {
                            prompt = prompt.replace(Regex("\\b${embed.first}\\b", RegexOption.IGNORE_CASE), "<${embed.first}>")
                            jsonEmbeds.add(embed.second)
                        }
                        params.addProperty("prompt", prompt)
                        params.add("embeds", jsonEmbeds)
                        it.hook.editMessage(content = "Working...").setComponents(listOf()).queue()
                        val entry = createEntry(event.hook)
                        queueDispatcher.queue.addToQueue(entry)
                    },
                    jda.button(
                        label = "Use raw prompts!",
                        style = ButtonStyle.SECONDARY,
                        user = event.user
                    ) {
                        it.hook.editMessage(content = "Working...").setComponents(listOf()).queue()
                        val entry = createEntry(event.hook)
                        queueDispatcher.queue.addToQueue(entry)
                    }
                )).queue()
            }
            else {
                val entry = createEntry(event.hook)
                queueDispatcher.queue.addToQueue(entry)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            event.sendException(e)
        }
    }
}