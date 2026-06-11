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
import org.openapitools.client.models.ApiResponsePageChat
import org.openapitools.client.models.Chat
import java.util.UUID

data class ChatListUiState(
    val chats: List<Chat> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ChatListViewModel(
    private val userId: UUID = JwtTokenProvider().userId,
    private val searchChatsCall: (userIds: List<UUID>?, offset: Int?, limit: Int?) -> ApiResponsePageChat = { userIds, offset, limit ->
        ChatsApi().searchChatsApiV1ChatsGet(userIds = userIds, offset = offset, limit = limit)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun loadChats() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = searchChatsCall(listOf(userId), 0, 50)
                _uiState.value = _uiState.value.copy(
                    chats = response.result.items,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load chats"
                )
            }
        }
    }
}
