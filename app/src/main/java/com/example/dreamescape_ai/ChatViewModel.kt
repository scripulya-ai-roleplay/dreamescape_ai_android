package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.MessagesApi
import org.openapitools.client.models.ApiResponseMessage
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Message
import org.openapitools.client.models.UserMessageDTO
import java.util.UUID

data class ChatUiState(
    val messages: List<Message> = emptyList(),
    val input: String = "",
    val isLoading: Boolean = false,
    val isSending: Boolean = false,
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
    private val llmModel: LLMModelType = LLMModelType.testing_mock
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
                    messages = response.result.items,
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
                // Reload so the user message and the model's reply are both shown.
                val response = loadMessagesCall(chatId)
                _uiState.value = _uiState.value.copy(
                    messages = response.result.items,
                    isSending = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSending = false,
                    errorMessage = e.message ?: "Failed to send message"
                )
            }
        }
    }
}
