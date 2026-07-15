package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.JwtTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.ChatsApi
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.MessagesApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ApiResponsePageChat
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.ApiResponseScene
import org.openapitools.client.models.Chat
import org.openapitools.client.models.MediaEntityType
import java.time.OffsetDateTime
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
 *
 * [sceneName], [sceneImageUrl] and [latestMessagePreview] are resolved
 * asynchronously after the groups are built and filled in as each lookup lands.
 */
data class ChatGroup(
    val sceneId: UUID,
    val chatIds: List<UUID>,
    val chatCount: Int,
    val latestChat: Chat,
    val sceneName: String? = null,
    val sceneImageUrl: String? = null,
    val latestMessagePreview: String? = null
)

class ChatListViewModel(
    private val userId: UUID = JwtTokenProvider().userId,
    private val searchChatsCall: (userIds: List<UUID>?, offset: Int?, limit: Int?) -> ApiResponsePageChat = { userIds, offset, limit ->
        ChatsApi().searchChatsApiV1ChatsGet(userIds = userIds, offset = offset, limit = limit)
    },
    // Fetches the scene so its title is known for the row.
    private val getSceneCall: (sceneId: UUID) -> ApiResponseScene = { sceneId ->
        ScenesApi().getSceneDetailsApiV1ScenesSceneIdGet(sceneId = sceneId)
    },
    // Resolves the first media asset attached to a scene (its preview image).
    private val sceneImageCall: (sceneId: UUID) -> ApiResponsePageMediaAssetDTO = { sceneId ->
        MediaApi().searchMediaApiV1MediaGet(
            entityType = MediaEntityType.scene,
            entityId = sceneId,
            limit = 1
        )
    },
    // Fetches a batch of messages across a scene's chats; the latest by
    // date_created is used as the row's preview.
    private val latestMessageCall: (chatIds: List<UUID>) -> ApiResponsePageMessage = { chatIds ->
        MessagesApi().searchMessagesApiV1MessagesGet(chatsIds = chatIds, limit = 50, offset = 0)
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
                val groups = groupByScene(chats)
                _uiState.value = _uiState.value.copy(
                    chats = chats,
                    groups = groups,
                    isLoading = false
                )
                resolveGroupDetails(groups)
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
                ChatGroup(
                    sceneId = latest.sceneId,
                    chatIds = group.mapNotNull { it.id },
                    chatCount = group.size,
                    latestChat = latest
                )
            }

    /**
     * Resolves each group's scene name, preview image and latest-message preview
     * concurrently; each group is updated independently as it resolves. Failures
     * leave the field null — the row falls back to the chat title / chat count.
     */
    private fun resolveGroupDetails(groups: List<ChatGroup>) {
        viewModelScope.launch(ioDispatcher) {
            groups.map { group -> async { resolveGroup(group) } }.awaitAll()
        }
    }

    private suspend fun resolveGroup(group: ChatGroup) {
        val sceneName = runCatching { getSceneCall(group.sceneId).result.title }.getOrNull()

        val imageUrl = runCatching {
            sceneImageCall(group.sceneId).result.items.firstOrNull()?.url
        }.getOrNull()

        val preview = if (group.chatIds.isEmpty()) {
            null
        } else {
            runCatching {
                val latest = latestMessageCall(group.chatIds).result.items
                    .sortedWith(compareBy(nullsLast<OffsetDateTime>()) { it.dateCreated })
                    .lastOrNull()
                latest?.let { msg -> truncateForPreview(extractModelMessageText(msg.message)) }
            }.getOrNull()
        }

        updateGroup(group.sceneId) {
            it.copy(sceneName = sceneName, sceneImageUrl = imageUrl, latestMessagePreview = preview)
        }
    }

    private fun updateGroup(sceneId: UUID, transform: (ChatGroup) -> ChatGroup) {
        _uiState.value = _uiState.value.copy(
            groups = _uiState.value.groups.map {
                if (it.sceneId == sceneId) transform(it) else it
            }
        )
    }
}
