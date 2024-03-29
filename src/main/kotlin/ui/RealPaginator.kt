/*
 * Copyright 2020 Florian Spieß
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import net.dv8tion.jda.api.JDA
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel
import net.dv8tion.jda.api.entities.emoji.Emoji
import net.dv8tion.jda.api.events.GenericEvent
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent
import net.dv8tion.jda.api.exceptions.ErrorHandler
import net.dv8tion.jda.api.hooks.EventListener
import net.dv8tion.jda.api.hooks.SubscribeEvent
import net.dv8tion.jda.api.interactions.InteractionHook
import net.dv8tion.jda.api.interactions.callbacks.IReplyCallback
import net.dv8tion.jda.api.interactions.components.ActionComponent
import net.dv8tion.jda.api.interactions.components.ActionRow
import net.dv8tion.jda.api.interactions.components.buttons.Button
import net.dv8tion.jda.api.interactions.components.buttons.ButtonInteraction
import net.dv8tion.jda.api.requests.ErrorResponse
import net.dv8tion.jda.api.requests.restaction.interactions.MessageEditCallbackAction
import net.dv8tion.jda.api.utils.messages.MessageCreateData
import net.dv8tion.jda.api.utils.messages.MessageEditData
import java.security.SecureRandom
import java.util.*
import kotlin.time.Duration

/**
 * Defaults used for paginators.
 *
 * These can be changed but keep in mind they will apply globally.
 */
object PaginatorDefaults {
    /** The default button to go to the previous page */
    var PREV: Button = Button.secondary("prev", Emoji.fromUnicode("⬅️"))

    /** The default button to go to the next page */
    var NEXT: Button = Button.secondary("next", Emoji.fromUnicode("➡️"))
}

typealias GetPageCallback = ((index: Long) -> MessageCreateData)

class Paginator internal constructor(
    private val nonce: String,
    private val amountOfPages: Long,
    internal val getPage: GetPageCallback,
    private val ttl: Duration
) : EventListener {
    private var expiresAt: Long = Math.addExact(System.currentTimeMillis(), ttl.inWholeMilliseconds)

    private var index = 0L
    private val nextPage: MessageCreateData
        get() {
            index = (index + 1) % amountOfPages
            return getPage(index)
        }
    private val prevPage: MessageCreateData
        get() {
            index = (index - 1)
            if (index < 0) {
                index = amountOfPages - 1
            }
            return getPage(index)
        }
    var customActionComponents: List<ActionComponent>? = null
    var filter: (ButtonInteraction) -> Boolean = { true }
    var injectMessageCallback: ((index: Long, messageEdit: MessageEditCallbackAction) -> Unit)? = null

    fun filterBy(filter: (ButtonInteraction) -> Boolean): Paginator {
        this.filter = filter
        return this
    }

    var prev: Button = PaginatorDefaults.PREV
    var next: Button = PaginatorDefaults.NEXT

    internal fun getControls(): ActionRow {
        val controls: MutableList<ActionComponent> = mutableListOf(
            prev.withId("$nonce:prev"),
            next.withId("$nonce:next")
        )
        if (customActionComponents != null) {
            controls.addAll(customActionComponents!!)
        }
        return ActionRow.of(controls)
    }

    fun getIndex(): Long {
        return this.index
    }

    @SubscribeEvent
    override fun onEvent(event: GenericEvent) {
        if (expiresAt < System.currentTimeMillis())
            return unregister(event.jda)
        if (event !is ButtonInteractionEvent) return
        val buttonId = event.componentId
        if (!buttonId.startsWith(nonce) || !filter(event)) return
        expiresAt = Math.addExact(System.currentTimeMillis(), ttl.inWholeMilliseconds)
        val (_, operation) = buttonId.split(":")
        val message = when (operation) {
            "prev" -> {
                event.editMessage(MessageEditData.fromCreateData(prevPage))
            }

            "next" -> {
                event.editMessage(MessageEditData.fromCreateData(nextPage))
            }

            else -> {
                event.editMessage(MessageEditData.fromCreateData(nextPage))
            }
        }
        var messageEdit = message.setComponents(getControls())
        if (injectMessageCallback != null) {
            injectMessageCallback!!(index, messageEdit)
        }
        messageEdit.queue(null, ErrorHandler().handle(ErrorResponse.UNKNOWN_MESSAGE) { unregister(event.jda) })
    }

    private fun unregister(jda: JDA) {
        jda.removeEventListener(this)
    }
}

fun paginator(amountOfPages: Long, getPage: GetPageCallback, expireAfter: Duration): Paginator {
    val nonce = ByteArray(32)
    SecureRandom().nextBytes(nonce)
    return Paginator(Base64.getEncoder().encodeToString(nonce), amountOfPages, getPage, expireAfter)
}

fun MessageChannel.sendPaginator(paginator: Paginator) =
    sendMessage(paginator.also { jda.addEventListener(it) }.getPage(0)).setComponents(paginator.getControls())

fun InteractionHook.sendPaginator(paginator: Paginator) =
    sendMessage(paginator.also { jda.addEventListener(it) }.getPage(0)).setComponents(paginator.getControls())

fun InteractionHook.editMessageToIncludePaginator(paginator: Paginator) =
    this.editOriginal(MessageEditData.fromCreateData(paginator.also { jda.addEventListener(it) }.getPage(0)))
        .setComponents(paginator.getControls())

fun IReplyCallback.replyPaginator(paginator: Paginator) =
    reply(paginator.also { user.jda.addEventListener(it) }.getPage(0)).setComponents(paginator.getControls())