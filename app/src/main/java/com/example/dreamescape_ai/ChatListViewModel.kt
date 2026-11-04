package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.SessionManager
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
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
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
 * [sceneId] is null when the scene was deleted — the backend nulls the chat's
 * scene_id (ON DELETE SET NULL) and keeps the chat readable. Such chats form
 * their own "scene-less" group keyed by [listKey].
 *
 * [sceneName], [sceneImageUrl], [latestMessagePreview], [latestMessageDate] and
 * [latestMessageDateByChat] are resolved asynchronously after the groups are
 * built and filled in as each lookup lands.
 */
data class ChatGroup(
    val sceneId: UUID?,
    val chatIds: List<UUID>,
    val chatCount: Int,
    val latestChat: Chat,
    val sceneName: String? = null,
    val sceneImageUrl: String? = null,
    val latestMessagePreview: String? = null,
    val latestMessageDate: OffsetDateTime? = null,
    /** Latest message date per chat — the per-scene list sorts its rows by this. */
    val latestMessageDateByChat: Map<UUID, OffsetDateTime> = emptyMap()
) {
    /** Stable LazyColumn key: scene id, or a sentinel for the scene-less group. */
    val listKey: String get() = sceneId?.toString() ?: "no-scene"
}

class ChatListViewModel(
    private val userId: UUID = SessionManager.userId,
    private val searchChatsCall: (userIds: List<UUID>?, offset: Int?, limit: Int?) -> ApiResponsePageChat = { userIds, offset, limit ->
        ChatsApi().searchChatsApiV1ChatsGet(userIds = userIds, offset = offset, limit = limit)
    },
    // Fetches the scene so its title is known for the row.
    private val getSceneCall: (sceneId: UUID) -> ApiResponseScene = { sceneId ->
        ScenesApi().getSceneDetailsApiV1ScenesSceneIdGet(sceneId = sceneId)
    },
    // Resolves the newest media asset attached to a scene (its preview image).
    private val sceneImageCall: (sceneId: UUID) -> ApiResponseListMediaAssetDTO = { sceneId ->
        MediaApi().getMediaForEntityApiV1MediaEntityEntityTypeEntityIdGet(
            entityType = MediaEntityType.scene,
            entityId = sceneId
        )
    },
    // Fetches one page of messages across a scene's chats; the latest by
    // date_created is used as the row's preview.
    private val latestMessageCall: (chatIds: List<UUID>, offset: Int?, limit: Int?) -> ApiResponsePageMessage =
        { chatIds, offset, limit ->
            MessagesApi().searchMessagesApiV1MessagesGet(chatsIds = chatIds, offset = offset, limit = limit)
        },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatListUiState())
    val uiState: StateFlow<ChatListUiState> = _uiState.asStateFlow()

    fun loadChats() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val chats = fetchAllChats()
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
     * Loads every chat of the user via [fetchAllPages] — a single page would
     * silently drop the newest chats (the backend paginates oldest-first).
     */
    private fun fetchAllChats(): List<Chat> = fetchAllPages { offset, limit ->
        searchChatsCall(listOf(userId), offset, limit).result
            .let { ListingPage(it.items, it.count) }
    }

    /**
     * Collapses [chats] into one group per scene. The backend lists chats
     * oldest-first, so each group's last chat is its newest — that is the
     * [ChatGroup.latestChat] selecting the scene opens. The initial row order
     * follows chat creation; the final order (most recent message first) is
     * applied once message previews resolve in [resolveGroupDetails].
     * Chats whose scene was deleted (null scene_id) share one group.
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
            // Re-sort only after every group resolved: sorting incrementally
            // would make rows jump each time a preview lands. Groups without a
            // dated message (failed lookup, no messages) sink to the bottom,
            // keeping their creation-order tie-break.
            _uiState.value = _uiState.value.copy(
                groups = _uiState.value.groups.sortedWith(
                    compareByDescending<ChatGroup> { it.latestMessageDate != null }
                        .thenByDescending { it.latestMessageDate }
                )
            )
        }
    }

    private suspend fun resolveGroup(group: ChatGroup) {
        val sceneName = group.sceneId?.let { sceneId ->
            runCatching { getSceneCall(sceneId).result.title }.getOrNull()
        }

        val imageUrl = group.sceneId?.let { sceneId ->
            runCatching { sceneImageCall(sceneId).result.firstOrNull()?.url }.getOrNull()
        }

        val messages = if (group.chatIds.isEmpty()) {
            emptyList()
        } else {
            runCatching {
                fetchAllPages { offset, limit ->
                    latestMessageCall(group.chatIds, offset, limit).result
                        .let { ListingPage(it.items, it.count) }
                }
            }.getOrDefault(emptyList())
        }

        val latest = messages
            .sortedWith(compareBy(nullsLast<OffsetDateTime>()) { it.dateCreated })
            .lastOrNull()
        val latestDateByChat = messages
            .filter { it.dateCreated != null }
            .groupBy { it.chatId }
            .mapValues { (_, chatMessages) -> chatMessages.maxOf { it.dateCreated!! } }

        updateGroup(group.sceneId) {
            it.copy(
                sceneName = sceneName,
                sceneImageUrl = imageUrl,
                latestMessagePreview = latest?.let { msg -> truncateForPreview(extractModelMessageText(msg.message)) },
                latestMessageDate = latest?.dateCreated,
                latestMessageDateByChat = latestDateByChat
            )
        }
    }

    private fun updateGroup(sceneId: UUID?, transform: (ChatGroup) -> ChatGroup) {
        _uiState.value = _uiState.value.copy(
            groups = _uiState.value.groups.map {
                if (it.sceneId == sceneId) transform(it) else it
            }
        )
    }
}
