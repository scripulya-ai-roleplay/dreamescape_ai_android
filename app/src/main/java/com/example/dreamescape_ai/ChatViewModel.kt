package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import org.openapitools.client.apis.ChatsApi
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.MessagesApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.models.ApiResponseChat
import org.openapitools.client.models.ApiResponseListInitialMessage
import org.openapitools.client.models.ApiResponseMessage
import org.openapitools.client.models.BodyChooseChatInitialMessageApiV1ChatsChatIdInitialMessagePost
import org.openapitools.client.models.BodyUpdateMessageApiV1MessagesMessageIdPut
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.Message
import org.openapitools.client.models.MessageStatus
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.SendMessageRequest
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingSeconds: Int = 0,
    val errorMessage: String? = null,
    val sceneImageUrl: String? = null,
    val sceneImageResolved: Boolean = false,
    // The chat's chosen opening greeting, if any. null until the user picks one.
    val initialMessageId: UUID? = null,
    // True while the chat has no greeting — the scene's greetings are shown as a
    // browsable carousel and the first send commits the displayed one.
    val needsInitialMessage: Boolean = false,
    val sceneInitialMessages: List<InitialMessage> = emptyList(),
    val currentInitialMessageIndex: Int = 0,
    val isChoosingInitialMessage: Boolean = false,
    // Incrementally-accumulated model reply while per-token SSE frames stream in.
    // Shown as a transient bubble that is replaced by the authoritative message once
    // the terminal "message" event lands (or cleared on stream close). Empty unless a
    // generation is actively streaming.
    val streamingText: String = "",
    // Incrementally-accumulated model "thoughts" (chain-of-thought) from `thinking`
    // SSE frames. Surfaced behind a collapsible disclosure on the streaming bubble, and
    // replaced by the persisted message's `reasoning` once the authoritative message lands.
    val streamingThinking: String = ""
)

class ChatViewModel(
    private val chatId: UUID,
    private val loadMessagesCall: (chatId: UUID) -> ApiResponsePageMessage = { id ->
        MessagesApi().searchMessagesApiV1MessagesGet(chatsIds = listOf(id), limit = 100, offset = 0)
    },
    // Fetches the chat so its scene_id is known; used to resolve the scene's
    // preview image for the chat background.
    private val getChatCall: (chatId: UUID) -> ApiResponseChat = { id ->
        ChatsApi().getChatDetailsApiV1ChatsChatIdGet(chatId = id)
    },
    // Resolves the newest media asset attached to a scene (its preview image).
    private val sceneImageCall: (sceneId: UUID) -> ApiResponseListMediaAssetDTO = { sceneId ->
        MediaApi().getMediaForEntityApiV1MediaEntityEntityTypeEntityIdGet(
            entityType = MediaEntityType.scene,
            entityId = sceneId
        )
    },
    private val sendMessageCall: (SendMessageRequest) -> ApiResponseMessage = { dto ->
        MessagesApi().createMessageApiV1MessagesPost(dto)
    },
    // Replaces a stored message's text in place (no regeneration). User messages hold
    // plain prose; model replies hold a {"text": ...} envelope — the editor saves the
    // plain text back, so an edited model reply loses its envelope and renders verbatim.
    private val updateMessageCall: (UUID, String) -> ModelApiResponse = { id, text ->
        MessagesApi().updateMessageApiV1MessagesMessageIdPut(
            messageId = id,
            bodyUpdateMessageApiV1MessagesMessageIdPut =
            BodyUpdateMessageApiV1MessagesMessageIdPut(updatedText = text)
        )
    },
    private val deleteMessageCall: (UUID) -> ModelApiResponse = { id ->
        MessagesApi().deleteMessageApiV1MessagesMessageIdDelete(messageId = id)
    },
    // The scene's opening greetings, shown as a carousel until one is chosen.
    private val getSceneInitialMessagesCall: (UUID) -> ApiResponseListInitialMessage = { sceneId ->
        ScenesApi().getSceneInitialMessagesApiV1ScenesSceneIdInitialMessagesGet(sceneId = sceneId)
    },
    // Seeds the chosen greeting as the chat's first model message (once per chat).
    private val chooseInitialMessageCall: (chatId: UUID, initialMessageId: UUID) -> ModelApiResponse =
        { chatId, initialMessageId ->
            ChatsApi().chooseChatInitialMessageApiV1ChatsChatIdInitialMessagePost(
                chatId = chatId,
                bodyChooseChatInitialMessageApiV1ChatsChatIdInitialMessagePost =
                BodyChooseChatInitialMessageApiV1ChatsChatIdInitialMessagePost(
                    initialMessageId = initialMessageId
                )
            )
        },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    /**
     * Source of the LLM model used for outgoing messages. Defaults to a glm-5.2
     * flow; ChatActivity supplies the per-chat persisted choice from ChatModelStore.
     */
    private val modelFlow: Flow<LLMModelType> = flowOf(LLMModelType.glmMinus5Period2),
    /**
     * Invoked after a message is sent to wait for the model's reply. Null (the
     * default) uses the real SSE + thinking-timer implementation; tests inject a
     * no-op so they don't touch the network.
     */
    private val waitForReply: ((Set<UUID>, UUID?) -> Unit)? = null
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatUiState())
    val uiState: StateFlow<ChatUiState> = _uiState.asStateFlow()

    fun onInputChanged(text: String) {
        _uiState.value = _uiState.value.copy(input = text, errorMessage = null)
    }

    fun loadMessages() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = loadMessagesCall(chatId)
                _uiState.value = _uiState.value.copy(
                    messages = response.result.items.sortedChronologically(),
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load messages"
                )
            }
        }
    }

    /**
     * Orders messages in chronological order by [Message.dateCreated].
     * Messages without a creation timestamp are placed last while preserving their relative order.
     */
    private fun List<Message>.sortedChronologically(): List<Message> =
        sortedWith(compareBy(nullsLast<OffsetDateTime>()) { it.dateCreated })

    /**
     * Loads the chat's context once: resolves the scene's preview image for the
     * background, and determines whether an opening greeting still needs to be
     * chosen. A chat with no `initial_message_id` can't send — the backend raises
     * InitialMessageRequiredException — so the scene's greetings are fetched here
     * to drive the greeting carousel. Failures leave the background empty and the
     * gate open rather than blocking the screen.
     */
    fun loadChat() {
        if (_uiState.value.sceneImageResolved) return
        viewModelScope.launch(ioDispatcher) {
            val chat = try {
                getChatCall(chatId).result
            } catch (_: Exception) {
                _uiState.value = _uiState.value.copy(sceneImageResolved = true)
                return@launch
            }
            // A null sceneId means the scene was deleted; the chat is read-only
            // and has no background or greetings to resolve.
            val sceneId = chat.sceneId
            val imageUrl = if (sceneId == null) {
                null
            } else {
                try {
                    sceneImageCall(sceneId).result.firstOrNull()?.url
                } catch (_: Exception) {
                    null
                }
            }
            val needsGreeting = chat.initialMessageId == null && sceneId != null
            val greetings = if (needsGreeting) {
                try {
                    getSceneInitialMessagesCall(sceneId).result
                } catch (_: Exception) {
                    emptyList()
                }
            } else {
                emptyList()
            }
            _uiState.value = _uiState.value.copy(
                sceneImageUrl = imageUrl,
                sceneImageResolved = true,
                initialMessageId = chat.initialMessageId,
                needsInitialMessage = needsGreeting,
                sceneInitialMessages = greetings,
                currentInitialMessageIndex = 0
            )
        }
    }

    /** Cycles the greeting carousel to the previous opening message (clamped). */
    fun selectPreviousInitialMessage() {
        if (!_uiState.value.needsInitialMessage) return
        _uiState.value = _uiState.value.copy(
            currentInitialMessageIndex = (_uiState.value.currentInitialMessageIndex - 1).coerceAtLeast(0)
        )
    }

    /** Cycles the greeting carousel to the next opening message (clamped). */
    fun selectNextInitialMessage() {
        val count = _uiState.value.sceneInitialMessages.size
        if (!_uiState.value.needsInitialMessage || count == 0) return
        _uiState.value = _uiState.value.copy(
            currentInitialMessageIndex = (_uiState.value.currentInitialMessageIndex + 1).coerceAtMost(count - 1)
        )
    }

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) {
            return
        }
        _uiState.value = _uiState.value.copy(input = "")
        send(text)
    }

    /**
     * Asks the model to keep going: posts a "Continue" trigger as a normal user
     * message (so it shows in the thread) which produces a streamed model reply.
     * There's no dedicated backend endpoint, so this reuses the ordinary send path.
     */
    fun continueConversation() {
        if (_uiState.value.isSending) return
        send(CONTINUE_PROMPT)
    }

    /**
     * Shared send path for [sendMessage] and [continueConversation]: marks the thread
     * as sending, seeds the opening greeting if none is chosen yet, posts [text],
     * reloads, and waits for the streamed model reply.
     */
    private fun send(text: String) {
        _uiState.value = _uiState.value.copy(
            isSending = true,
            errorMessage = null,
            streamingText = ""
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                // Seed the opening greeting first if none is chosen yet — the
                // backend rejects messages until one is picked (choosing is
                // once-per-chat). The greeting currently shown in the carousel is
                // committed, then this reply follows it.
                if (_uiState.value.needsInitialMessage) {
                    val greetingId = _uiState.value.sceneInitialMessages
                        .getOrNull(_uiState.value.currentInitialMessageIndex)?.id
                    if (greetingId == null) {
                        _uiState.value = _uiState.value.copy(
                            isSending = false,
                            errorMessage = "No opening message available for this scene"
                        )
                        return@launch
                    }
                    _uiState.value = _uiState.value.copy(isChoosingInitialMessage = true)
                    chooseInitialMessageCall(chatId, greetingId)
                    // needsInitialMessage stays true until the seeded greeting is
                    // reloaded below, so the carousel doesn't drop out (or duplicate
                    // the greeting) during the in-flight send.
                    _uiState.value = _uiState.value.copy(isChoosingInitialMessage = false)
                }

                val dto = SendMessageRequest(
                    chatId = chatId,
                    message = text,
                    llmModel = modelFlow.first()
                )
                sendMessageCall(dto)
                // Reload so the user message (and any pending model message) is shown.
                val response = loadMessagesCall(chatId)
                val messages = response.result.items.sortedChronologically()
                // The seeded greeting now appears in the list; clear the carousel.
                _uiState.value = _uiState.value.copy(
                    messages = messages,
                    isSending = false,
                    needsInitialMessage = false
                )

                // Wait for the model's reply: shows a "thinking" timer (so the user can
                // see the app is generating, not silently stuck) and reloads when the
                // reply lands via the SSE event stream.
                val knownIds = messages.mapNotNull { it.id }.toSet()
                val pendingId = messages.lastOrNull { it.role == ChatRoles.model }
                    ?.takeIf { it.status == MessageStatus.pending }?.id
                val wait: (Set<UUID>, UUID?) -> Unit = waitForReply
                    ?: { ids, pid -> startWaitingForReply(ids, pid) }
                wait(knownIds, pendingId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    isChoosingInitialMessage = false,
                    errorMessage = e.message ?: "Failed to send message"
                )
            }
        }
    }

    /** Replaces a stored message's text with [newText] (plain prose) and reloads. */
    fun editMessage(messageId: UUID, newText: String) {
        val text = newText.trim()
        if (text.isEmpty()) return
        viewModelScope.launch(ioDispatcher) {
            try {
                updateMessageCall(messageId, text)
                val response = loadMessagesCall(chatId)
                _uiState.value = _uiState.value.copy(
                    messages = response.result.items.sortedChronologically(),
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to edit message"
                )
            }
        }
    }

    /** Permanently removes a message and reloads the thread. */
    fun deleteMessage(messageId: UUID) {
        viewModelScope.launch(ioDispatcher) {
            try {
                deleteMessageCall(messageId)
                val response = loadMessagesCall(chatId)
                _uiState.value = _uiState.value.copy(
                    messages = response.result.items.sortedChronologically(),
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete message"
                )
            }
        }
    }

    private var eventsCall: Call? = null
    private var thinkingJob: Job? = null

    // --- per-token streaming state ---
    // The SSE loop appends raw token chunks to [streamingRaw] as fast as they
    // arrive (bursty). A separate reveal pump drains it word-by-word at a fixed
    // cadence so the bubble fills one word at a time regardless of token batching.
    private var streamingRaw = StringBuffer()
    // Accumulates `thinking` chunks in parallel to [streamingRaw]; surfaced verbatim
    // (no word-by-word reveal) as [ChatUiState.streamingThinking].
    private var thinkingRaw = StringBuffer()
    private var revealedLength = 0
    @Volatile private var streamComplete = false
    private val streamFinalized = AtomicBoolean(false)
    private var revealJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        eventsCall?.cancel()
        thinkingJob?.cancel()
        revealJob?.cancel()
    }

    /**
     * Begins waiting for the model's reply: starts the "thinking" timer so the
     * UI shows the app is generating, then opens the SSE event stream.
     */
    private fun startWaitingForReply(knownIds: Set<UUID>, pendingId: UUID?) {
        startThinking()
        connectEvents(knownIds, pendingId)
    }

    /**
     * Runs a per-second "thinking" counter while the model is generating, so the
     * user can tell the app is alive and working rather than silently errored.
     * Stopped by [stopThinking] (on reply, stream close, or cancel).
     */
    private fun startThinking() {
        thinkingJob?.cancel()
        _uiState.value = _uiState.value.copy(isThinking = true, thinkingSeconds = 0)
        thinkingJob = viewModelScope.launch(ioDispatcher) {
            while (coroutineContext.isActive) {
                delay(THINKING_TICK_MS)
                _uiState.value = _uiState.value.copy(
                    thinkingSeconds = _uiState.value.thinkingSeconds + 1
                )
            }
        }
    }

    private fun stopThinking() {
        thinkingJob?.cancel()
        thinkingJob = null
        if (_uiState.value.isThinking) {
            _uiState.value = _uiState.value.copy(isThinking = false)
        }
    }

    /**
     * Opens the SSE stream of model-message lifecycle events for this chat.
     *
     * Frame types (the event name precedes a `data:` JSON payload; `: keepalive`
     * lines are comments):
     *  - `token` — a chunk of the reply as it generates; appended to the raw buffer.
     *    The reveal pump uncovers it one word at a time. On the first token the
     *    "thinking" spinner hands off to the growing bubble.
     *  - `thinking` — a chunk of the model's chain-of-thought (only when the chat has
     *    reasoning on). Same `{"text": ...}` shape as `token`; accumulated verbatim into
     *    the thinking buffer and surfaced behind the message's collapsible disclosure.
     *  - `generation_start` / `generation_done` — lifecycle markers; the token
     *    frames and the terminal `message` drive the UI, so these are no-ops here.
     *  - `message` — the authoritative persisted message. On connect the latest
     *    model message is emitted as a reconcile frame; we only act on a terminal
     *    (completed/failed) frame for a message we don't already show: the pending
     *    message we're waiting on ([pendingId]), or — if none was visible yet — any
     *    new id absent from [knownIds].
     *
     * The word-by-word reveal is decoupled from token arrival: the SSE loop only
     * feeds the buffer, and [startRevealPump] drains it at a fixed cadence. The
     * bubble is swapped for the real message ([finalizeStream]) only once the reveal
     * has caught up — so if tokens never arrive (or the stream drops), the whole
     * message is rendered directly and the UI never stalls on a partial bubble.
     */
    private fun connectEvents(knownIds: Set<UUID>, pendingId: UUID?) {
        eventsCall?.cancel()
        // SSE streams sit idle between events. The shared client's default 10s
        // read timeout is shorter than the server's 15s keepalive, so it would
        // cut the stream before the reply (or even a keepalive) ever arrived.
        // Derive a no-read-timeout client from the authed shared one so the
        // stream survives until the reply or a keepalive lands.
        val sseClient = ApiClient.defaultClient.newBuilder()
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
        val request = Request.Builder()
            .url("${DreamescapeApplication.BACKEND_BASE_URL}/api/v1/chats/$chatId/events")
            .header("Accept", "text/event-stream")
            .build()
        val call = sseClient.newCall(request)
        eventsCall = call

        resetStreamState()
        startRevealPump()

        viewModelScope.launch(ioDispatcher) {
            var eventName = ""
            val data = StringBuilder()
            try {
                call.execute().use { response ->
                    if (!response.isSuccessful) return@launch
                    val source = response.body?.source() ?: return@launch
                    while (coroutineContext.isActive) {
                        val line = source.readUtf8Line() ?: break
                        when {
                            line.startsWith("event:") -> eventName = line.removePrefix("event:").trim()
                            line.startsWith("data:") -> {
                                if (data.isNotEmpty()) data.append('\n')
                                data.append(line.removePrefix("data:").trim())
                            }
                            line.isEmpty() -> {
                                // A terminal reply with no streamed text reloads at
                                // once (whole-message fallback); anything else just
                                // feeds the buffer / defers to the reveal pump.
                                if (data.isNotEmpty() &&
                                    handleStreamFrame(eventName, data.toString(), knownIds, pendingId) ==
                                    StreamAction.RELOAD
                                ) {
                                    return@launch
                                }
                                data.setLength(0)
                                eventName = ""
                            }
                            // lines starting with ':' are keepalive comments — ignored
                        }
                    }
                }
            } catch (_: Exception) {
                // Stream failed or was canceled; the reconcile in finally still
                // picks up any reply that landed.
            } finally {
                stopThinking()
                // If the stream ended without the pump finalizing (close, cancel, or
                // a reply that slipped through), render the whole message now.
                if (!streamFinalized.get()) finalizeStream()
            }
        }
    }

    /**
     * Dispatches one fully-read SSE frame. `token` text is appended to the raw
     * buffer (the reveal pump surfaces it word-by-word) and the thinking spinner is
     * dropped; the generation lifecycle markers are ignored. A `message` frame that
     * is our terminal reply returns [StreamAction.RELOAD] when nothing was streamed
     * (render the whole message now), or marks the stream complete and returns
     * [StreamAction.CONTINUE] so the reveal pump can finish before the swap.
     */
    internal fun handleStreamFrame(
        eventName: String,
        payload: String,
        knownIds: Set<UUID>,
        pendingId: UUID?
    ): StreamAction {
        return when (eventName) {
            "token" -> {
                parseTokenText(payload)?.let { chunk -> if (chunk.isNotEmpty()) streamingRaw.append(chunk) }
                stopThinking()
                StreamAction.CONTINUE
            }
            // The model's chain-of-thought, streamed before/alongside the answer tokens.
            // Same {"text": ...} shape as `token`; accumulated verbatim into the thinking
            // buffer (no word-by-word reveal) and surfaced behind the message's disclosure.
            "thinking" -> {
                parseTokenText(payload)?.let { chunk -> if (chunk.isNotEmpty()) thinkingRaw.append(chunk) }
                _uiState.value = _uiState.value.copy(streamingThinking = thinkingRaw.toString())
                StreamAction.CONTINUE
            }
            "generation_start", "generation_done" -> StreamAction.CONTINUE
            else -> when {
                !isTerminalReply(payload, knownIds, pendingId) -> StreamAction.CONTINUE
                streamingRaw.isNotEmpty() -> {
                    // Let the word-by-word reveal finish, then the pump swaps it in.
                    streamComplete = true
                    StreamAction.CONTINUE
                }
                else -> StreamAction.RELOAD
            }
        }
    }

    /** Pulls the `text` field out of a `token` frame's payload, or null if absent. */
    private fun parseTokenText(payload: String): String? {
        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        return if (json.has("text")) json.optString("text") else null
    }

    /** Clears the streaming buffer and reveal state for a fresh generation. */
    private fun resetStreamState() {
        revealJob?.cancel()
        revealJob = null
        streamingRaw = StringBuffer()
        thinkingRaw = StringBuffer()
        revealedLength = 0
        streamComplete = false
        streamFinalized.set(false)
        if (_uiState.value.streamingText.isNotEmpty() || _uiState.value.streamingThinking.isNotEmpty()) {
            _uiState.value = _uiState.value.copy(streamingText = "", streamingThinking = "")
        }
    }

    /**
     * Drains the streamed buffer one word at a time at [WORD_REVEAL_MS] cadence,
     * decoupled from how fast tokens arrive. Once every available word is uncovered
     * and the stream is complete, the bubble is swapped for the real message.
     */
    private fun startRevealPump() {
        revealJob?.cancel()
        revealJob = viewModelScope.launch(ioDispatcher) {
            while (coroutineContext.isActive) {
                delay(WORD_REVEAL_MS)
                if (pumpReveal()) break
            }
        }
    }

    /**
     * One reveal step: uncover the next whitespace-delimited word if any remains;
     * otherwise, once generation is done, finalize. Returns true once finalized.
     */
    internal fun pumpReveal(): Boolean {
        val available = streamingDisplayText(streamingRaw.toString())
        if (revealedLength > available.length) revealedLength = available.length
        return if (revealedLength < available.length) {
            revealedLength = nextRevealBoundary(available, revealedLength)
            _uiState.value = _uiState.value.copy(streamingText = available.take(revealedLength))
            false
        } else if (streamComplete) {
            finalizeStream()
            true
        } else {
            false
        }
    }

    /** Swaps the streaming bubble for the real message and tears the stream down. Idempotent. */
    private fun finalizeStream() {
        // The reveal pump and the SSE reader's finally can both race to finalize
        // (e.g. a deferred swap whose stream then drops); only the first wins.
        if (!streamFinalized.compareAndSet(false, true)) return
        revealJob?.cancel()
        revealJob = null
        eventsCall?.cancel()
        reloadAfterReply()
    }

    internal enum class StreamAction { CONTINUE, RELOAD }

    private fun isTerminalReply(
        payload: String,
        knownIds: Set<UUID>,
        pendingId: UUID?
    ): Boolean {
        val message = try {
            JSONObject(payload).optJSONObject("message")
        } catch (_: Exception) {
            null
        } ?: return false
        val status = message.optString("status")
        if (status != "completed" && status != "failed") return false
        val id = runCatching { UUID.fromString(message.optString("id")) }.getOrNull() ?: return false
        return (pendingId != null && id == pendingId) ||
            (pendingId == null && id !in knownIds)
    }

    private fun reloadAfterReply() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val response = loadMessagesCall(chatId)
                // Swap the streaming bubble for the authoritative message in the same
                // update so the reply never flickers out between the two. On failure the
                // streamed text is kept as the best available rendering of the reply.
                _uiState.value = _uiState.value.copy(
                    messages = response.result.items.sortedChronologically(),
                    streamingText = "",
                    streamingThinking = ""
                )
            } catch (_: Exception) {
                // Best-effort refresh; the user can reopen the chat to retry.
            }
        }
    }

    private companion object {
        const val THINKING_TICK_MS = 1000L
        const val WORD_REVEAL_MS = 100L
        // The user message posted by the Continue quick-action to prompt another reply.
        const val CONTINUE_PROMPT = "Continue"
    }
}

/**
 * Returns the index in [text] at the end of the next whitespace-delimited word
 * starting at [from] (skipping any leading whitespace) — i.e. advancing the
 * word-by-word reveal by one word. Returns [text].length when nothing remains.
 */
internal fun nextRevealBoundary(text: String, from: Int): Int {
    if (from >= text.length) return text.length
    var i = from
    while (i < text.length && text[i].isWhitespace()) i++
    if (i >= text.length) return text.length
    while (i < text.length && !text[i].isWhitespace()) i++
    return i
}
