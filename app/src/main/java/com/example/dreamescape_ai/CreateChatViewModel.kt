package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.JwtTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.ChatsApi
import org.openapitools.client.models.Chat
import org.openapitools.client.models.ModelApiResponse
import java.util.UUID

data class CreateChatUiState(
    val title: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val createdChatId: UUID? = null
)

class CreateChatViewModel(
    private val sceneId: UUID,
    private val createChatCall: (Chat) -> ModelApiResponse = { chat ->
        ChatsApi().createChatApiV1ChatsPost(chat)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val userId: UUID = JwtTokenProvider().userId,
    private val chatIdProvider: () -> UUID = { UUID.randomUUID() }
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateChatUiState())
    val uiState: StateFlow<CreateChatUiState> = _uiState.asStateFlow()

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title, errorMessage = null)
    }

    fun validate(): String? {
        return if (_uiState.value.title.isBlank()) "Title is required" else null
    }

    fun createChat() {
        val validationError = validate()
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        // A client id is attached to the request body, but the backend generates
        // and persists its OWN id (returned in the response) — it does not honor
        // the one we send. We must navigate using the server id, or subsequent
        // calls (messages, events) would target a non-existent chat and 404.
        val newChatId = chatIdProvider()
        val chat = Chat(
            title = _uiState.value.title.trim(),
            userId = userId,
            sceneId = sceneId,
            id = newChatId
        )

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = createChatCall(chat)
                val serverChatId = extractCreatedChatId(response)
                if (serverChatId == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Chat was created but the response did not include its id."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(isLoading = false, createdChatId = serverChatId)
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create chat"
                )
            }
        }
    }

    /**
     * The create-chat response is `{"result":{"id":"<uuid>"}, "correlation_id": …}`.
     * [ModelApiResponse.result] is untyped (`Any?`), which Moshi deserializes as a
     * `Map<String, Any?>` for a JSON object, so the server-assigned id is pulled
     * out of it. Returns null if the shape is unexpected.
     */
    private fun extractCreatedChatId(response: ModelApiResponse): UUID? {
        val idRaw = (response.result as? Map<*, *>)?.get("id")
        return idRaw?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
    }
}
