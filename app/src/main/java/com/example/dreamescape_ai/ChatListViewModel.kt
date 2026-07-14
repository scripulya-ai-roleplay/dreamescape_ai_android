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
    val groups: List<ChatGroup> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

/**
 * One row per scene in the messages section. The individual chats for a scene
 * are collapsed into a single group; selecting it opens [latestChat] (the most
 * recently created chat for that scene).
 */
data class ChatGroup(
    val sceneId: UUID,
    val title: String,
    val chatCount: Int,
    val latestChat: Chat
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
                val chats = response.result.items
                _uiState.value = _uiState.value.copy(
                    chats = chats,
                    groups = groupByScene(chats),
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

    /**
     * Collapses [chats] into one group per scene, ordered most-recently-active
     * first. The backend lists chats oldest-first, so each group's last chat is
     * its newest — that is the [ChatGroup.latestChat] selecting the scene opens.
     */
    private fun groupByScene(chats: List<Chat>): List<ChatGroup> =
        chats.groupBy { it.sceneId }.values
            .sortedByDescending { group -> chats.indexOf(group.last()) }
            .map { group ->
                val latest = group.last()
                ChatGroup(latest.sceneId, latest.title, group.size, latest)
            }
}
