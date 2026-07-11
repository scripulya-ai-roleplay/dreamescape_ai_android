package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageScene
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.Scene
import java.util.UUID

/** A scene rendered as a story card, with its title image resolved lazily. */
data class StoryCardState(
    val scene: Scene,
    val imageUrl: String? = null,
    val imageResolved: Boolean = false
)

data class StoryGalleryUiState(
    val stories: List<StoryCardState> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class StoryGalleryViewModel(
    private val searchScenesCall: (title: List<String>?, offset: Int?, limit: Int?) -> ApiResponsePageScene = { title, offset, limit ->
        ScenesApi().searchSceneApiV1ScenesGet(title = title, offset = offset, limit = limit)
    },
    private val sceneImageCall: (entityId: UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.scene, entityId = entityId, limit = 1)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(StoryGalleryUiState())
    val uiState: StateFlow<StoryGalleryUiState> = _uiState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadStories()
    }

    fun loadStories() {
        val query = _uiState.value.searchQuery.trim()
        val titleFilter = if (query.isNotEmpty()) listOf(query) else null

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = searchScenesCall(titleFilter, 0, 50)
                val cards = response.result.items.map { StoryCardState(scene = it) }
                _uiState.value = _uiState.value.copy(stories = cards, isLoading = false)
                resolveImages(cards)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load stories"
                )
            }
        }
    }

    /**
     * Resolves the title image (first scene media asset URL) for each story,
     * updating cards one at a time as each lookup completes. Failures leave the
     * card with no image rather than failing the whole gallery.
     */
    private fun resolveImages(cards: List<StoryCardState>) {
        viewModelScope.launch(ioDispatcher) {
            for (card in cards) {
                val sceneId = card.scene.id ?: continue
                val url = try {
                    sceneImageCall(sceneId).result.items.firstOrNull()?.url
                } catch (_: Exception) {
                    null
                }
                updateImage(sceneId, url)
            }
        }
    }

    private fun updateImage(sceneId: UUID, url: String?) {
        _uiState.value = _uiState.value.copy(
            stories = _uiState.value.stories.map {
                if (it.scene.id == sceneId) it.copy(imageUrl = url, imageResolved = true) else it
            }
        )
    }
}
