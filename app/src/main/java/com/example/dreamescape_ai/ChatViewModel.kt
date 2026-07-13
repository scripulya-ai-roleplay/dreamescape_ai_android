package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.Call
import okhttp3.Request
import org.json.JSONObject
import org.openapitools.client.apis.MessagesApi
import org.openapitools.client.infrastructure.ApiClient
import org.openapitools.client.models.ApiResponseMessage
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Message
import org.openapitools.client.models.MessageStatus
import org.openapitools.client.models.UserMessageDTO
import java.time.OffsetDateTime
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
    val isThinking: Boolean = false,
    val thinkingSeconds: Int = 0,
    val errorMessage: String? = null
)

class ChatViewModel(
    private val chatId: UUID,
    private val loadMessagesCall: (chatId: UUID) -> ApiResponsePageMessage = { id ->
        MessagesApi().searchMessagesApiV1MessagesGet(chatsIds = listOf(id), limit = 100, offset = 0)
    },
    private val sendMessageCall: (UserMessageDTO) -> ApiResponseMessage = { dto ->
        MessagesApi().createMessageApiV1MessagesPost(dto)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    // TEMPORARY: pin to the latest Z.ai GLM model (glm-5.2) to smoke-test the system end-to-end.
    private val llmModel: LLMModelType = LLMModelType.glmMinus5Period2,
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

    fun sendMessage() {
        val text = _uiState.value.input.trim()
        if (text.isEmpty()) {
            return
        }

        val dto = UserMessageDTO(
            chatId = chatId,
            message = text,
            role = ChatRoles.user,
            llmModel = llmModel
        )

        _uiState.value = _uiState.value.copy(isSending = true, input = "", errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                sendMessageCall(dto)
                // Reload so the user message (and any pending model message) is shown.
                val response = loadMessagesCall(chatId)
                val messages = response.result.items.sortedChronologically()
                _uiState.value = _uiState.value.copy(messages = messages, isSending = false)

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
                    errorMessage = e.message ?: "Failed to send message"
                )
            }
        }
    }

    private var eventsCall: Call? = null
    private var thinkingJob: Job? = null

    override fun onCleared() {
        super.onCleared()
        eventsCall?.cancel()
        thinkingJob?.cancel()
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
     * Frames are `event: message` / `event: error` with a `data: {"message": …}`
     * JSON payload; `: keepalive` lines are comments. On connect the server emits
     * the latest model message as a reconciliation frame, so we only reload on a
     * terminal (completed/failed) frame for a message we don't already show: the
     * pending message we're waiting on ([pendingId]), or — if no pending message
     * was visible yet — any new id absent from [knownIds]. The thinking timer is
     * stopped when the stream ends (reply delivered, closed, or canceled).
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

        viewModelScope.launch(ioDispatcher) {
            var eventName = ""
            val data = StringBuilder()
            var replyHandled = false
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
                                if (data.isNotEmpty() &&
                                    isTerminalReply(eventName, data.toString(), knownIds, pendingId)
                                ) {
                                    replyHandled = true
                                    reloadAfterReply()
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
                // If the stream ended without a terminal frame (close, cancel,
                // or a reply that slipped through), reconcile once more so the
                // user needn't re-enter the chat to see the reply.
                if (!replyHandled) reloadAfterReply()
            }
        }
    }

    private fun isTerminalReply(
        eventName: String,
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
                _uiState.value = _uiState.value.copy(messages = response.result.items.sortedChronologically())
            } catch (_: Exception) {
                // Best-effort refresh; the user can reopen the chat to retry.
            }
        }
    }

    private companion object {
        const val THINKING_TICK_MS = 1000L
    }
}
