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

        // The chat id is generated client-side so the screen knows which chat to
        // open once the backend confirms creation. The backend persists the id we
        // send because `id` is a writable field of the Chat request body.
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
                createChatCall(chat)
                _uiState.value = _uiState.value.copy(isLoading = false, createdChatId = newChatId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create chat"
                )
            }
        }
    }
}
