package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.JwtTokenProvider
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.CharactersApi
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ApiResponsePageCharacter
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageScene
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.Scene
import java.util.UUID

/** Which kind of owned content a [MyContentViewModel] lists. */
enum class OwnedMode { CHARACTERS, SCENES }

/**
 * A preview card for one of the user's own scenes or characters. [isPublic]
 * comes from the Character DTO; scenes have no such field yet and default to
 * `true` (see [Scene.isPublicPlaceholder]).
 */
data class OwnedCard(
    val id: String,
    val title: String,
    val subtitle: String,
    val imageUrl: String?,
    val imageResolved: Boolean = false,
    val isPublic: Boolean
)

data class MyContentUiState(
    val items: List<OwnedCard> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val offset: Int = 0,
    val hasMore: Boolean = false
)

/**
 * Backs the "My Characters" / "My Scenes" waterfalls. Fetches the current
 * user's own content (filtered by owner via the JWT `sub` claim — see
 * [JwtTokenProvider.userId]), maps it to [OwnedCard]s, resolves each item's
 * cover image lazily, and paginates like the discovery feed.
 */
class MyContentViewModel(
    private val mode: OwnedMode,
    private val ownerId: UUID = JwtTokenProvider().userId,
    private val searchScenes: (owner: List<UUID>, offset: Int?, limit: Int?) -> ApiResponsePageScene =
        { owner, offset, limit ->
            ScenesApi().searchSceneApiV1ScenesGet(owner = owner, offset = offset, limit = limit)
        },
    private val searchCharacters: (ownerIds: List<UUID>, offset: Int?, limit: Int?) -> ApiResponsePageCharacter =
        { ownerIds, offset, limit ->
            CharactersApi().searchCharacterApiV1CharactersGet(ownerIds = ownerIds, offset = offset, limit = limit)
        },
    private val sceneCover: (sceneId: UUID) -> ApiResponsePageMediaAssetDTO = { id ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.scene, entityId = id, limit = 1)
    },
    private val characterPortrait: (characterId: UUID) -> ApiResponsePageMediaAssetDTO = { id ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.character, entityId = id, limit = 1)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MyContentUiState(isLoading = true))
    val uiState: StateFlow<MyContentUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val page = fetchPage(0)
                _uiState.value = MyContentUiState(
                    items = page,
                    isLoading = false,
                    offset = page.size,
                    hasMore = page.size >= PAGE_SIZE
                )
                resolveImages(page)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load"
                )
            }
        }
    }

    fun loadMore() {
        val current = _uiState.value
        if (!current.hasMore || current.isLoadingMore) return
        _uiState.value = current.copy(isLoadingMore = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val offset = current.offset
                val page = fetchPage(offset)
                _uiState.value = _uiState.value.copy(
                    items = current.items + page,
                    offset = offset + page.size,
                    hasMore = page.size >= PAGE_SIZE,
                    isLoadingMore = false
                )
                resolveImages(page)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingMore = false)
            }
        }
    }

    /** Fetches one page of owned items for the current mode and maps to cards. */
    private suspend fun fetchPage(offset: Int): List<OwnedCard> = when (mode) {
        OwnedMode.SCENES -> {
            val scenes = searchScenes(listOf(ownerId), offset, PAGE_SIZE).result.items
            scenes.map { it.toCard() }
        }
        OwnedMode.CHARACTERS -> {
            val characters = searchCharacters(listOf(ownerId), offset, PAGE_SIZE).result.items
            characters.map { it.toCard() }
        }
    }

    private fun Scene.toCard(): OwnedCard = OwnedCard(
        id = (id ?: ownerId.toString()).toString(),
        title = title,
        subtitle = description?.takeIf { it.isNotBlank() } ?: backgroundPrompt,
        imageUrl = null, // resolved lazily by resolveImages()
        // Scenes expose no is_public field in the API; treat as public until one exists.
        isPublic = SCENE_IS_PUBLIC_PLACEHOLDER
    )

    private fun Character.toCard(): OwnedCard = OwnedCard(
        id = (id ?: ownerId.toString()).toString(),
        title = name,
        subtitle = systemPrompt,
        imageUrl = null, // resolved lazily by resolveImages()
        isPublic = isPublic ?: false
    )

    /** Resolves each card's cover image one at a time as its media lookup lands. */
    private fun resolveImages(cards: List<OwnedCard>) {
        viewModelScope.launch(ioDispatcher) {
            for (card in cards) {
                val entityId = runCatching { UUID.fromString(card.id) }.getOrNull() ?: continue
                val url = try {
                    when (mode) {
                        OwnedMode.SCENES -> sceneCover(entityId).result.items.firstOrNull()?.url
                        OwnedMode.CHARACTERS -> characterPortrait(entityId).result.items.firstOrNull()?.url
                    }
                } catch (_: Exception) {
                    null
                }
                if (url != null) applyImage(card.id, url)
            }
        }
    }

    private fun applyImage(cardId: String, url: String) {
        _uiState.value = _uiState.value.copy(
            items = _uiState.value.items.map {
                if (it.id == cardId) it.copy(imageUrl = url, imageResolved = true) else it
            }
        )
    }

    companion object {
        const val PAGE_SIZE = 20

        /** Scenes expose no `is_public` field in the API; treat as public until one exists. */
        private const val SCENE_IS_PUBLIC_PLACEHOLDER = true
    }
}

fun myContentViewModelFactory(mode: OwnedMode): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return MyContentViewModel(mode = mode) as T
        }
    }
