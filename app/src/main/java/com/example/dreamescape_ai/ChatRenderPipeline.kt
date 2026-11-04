package com.example.dreamescape_ai

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.Message
import java.util.UUID

/**
 * Context (chat details, scene image) and messages load concurrently, so
 * readiness is derived from which facts have landed — a settable flag would
 * reintroduce the ordering races this pipeline exists to prevent.
 */
enum class ChatRenderPhase { IDLE, LOADING, READY }

/**
 * The raw inputs the chat screen renders from. Written only through
 * [ChatRenderPipeline.update]; anything derived from more than one of these
 * must be computed in [deriveRenderDecision], never assembled at a call site.
 */
data class ChatRenderFacts(
    val contextLoaded: Boolean = false,
    val messagesLoaded: Boolean = false,
    val hasScene: Boolean = false,
    val sceneImageUrl: String? = null,
    val messages: List<Message> = emptyList(),
    val initialMessageId: UUID? = null,
    // The backend reports a chosen greeting only as the chat's
    // initial_message_id, which we don't refetch — so a committed greeting
    // is tracked here for the rest of the session.
    val greetingCommitted: Boolean = false,
    val sceneInitialMessages: List<InitialMessage> = emptyList(),
    val personaName: String? = null
) {
    val phase: ChatRenderPhase
        get() = when {
            contextLoaded && messagesLoaded -> ChatRenderPhase.READY
            contextLoaded || messagesLoaded -> ChatRenderPhase.LOADING
            else -> ChatRenderPhase.IDLE
        }
}

data class ChatRenderDecision(
    val backgroundImageUrl: String?,
    val needsInitialMessage: Boolean
)

/**
 * The single place where facts become what the screen shows. To key the
 * background off the conversation (e.g. the two latest messages), branch on
 * facts.messages here — callers are guaranteed a consistent snapshot.
 */
fun deriveRenderDecision(facts: ChatRenderFacts): ChatRenderDecision = ChatRenderDecision(
    backgroundImageUrl = facts.sceneImageUrl,
    needsInitialMessage = facts.contextLoaded && facts.hasScene &&
        facts.initialMessageId == null && !facts.greetingCommitted
)

/**
 * Serializes every mutation of the render facts and re-derives + publishes
 * inside the same critical section, so updates apply whole and in order even
 * when loads race (e.g. a slow message fetch finishing after the context).
 */
class ChatRenderPipeline(
    private val onRender: (ChatRenderFacts, ChatRenderDecision) -> Unit
) {
    private val mutex = Mutex()
    @Volatile private var facts = ChatRenderFacts()

    /** Best-effort read for guards (e.g. don't load twice); not for rendering. */
    val currentFacts: ChatRenderFacts get() = facts

    suspend fun update(block: (ChatRenderFacts) -> ChatRenderFacts) {
        mutex.withLock {
            val next = block(facts)
            facts = next
            onRender(next, deriveRenderDecision(next))
        }
    }
}
