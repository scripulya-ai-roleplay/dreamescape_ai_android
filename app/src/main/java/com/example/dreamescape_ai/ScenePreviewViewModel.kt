package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import org.openapitools.client.models.ApiResponseScene
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.Scene
import java.util.UUID

/** A character rendered as a carousel card, with its portrait resolved lazily. */
data class CharacterCardState(
    val character: Character,
    val imageUrl: String? = null,
    val imageResolved: Boolean = false
)

data class ScenePreviewUiState(
    val scene: Scene? = null,
    val heroImageUrl: String? = null,
    val heroImageResolved: Boolean = false,
    val characters: List<CharacterCardState> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class ScenePreviewViewModel(
    private val sceneId: UUID,
    private val getSceneCall: (UUID) -> ApiResponseScene = { id ->
        ScenesApi().getSceneDetailsApiV1ScenesSceneIdGet(sceneId = id)
    },
    private val sceneImageCall: (UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.scene, entityId = entityId, limit = 1)
    },
    // The Scene DTO does not expose its characters, so the carousel is populated
    // from the characters owned by the scene's author — the closest available
    // signal for the cast of a story.
    private val searchCharactersCall: (ownerIds: List<UUID>?) -> ApiResponsePageCharacter = { ownerIds ->
        CharactersApi().searchCharacterApiV1CharactersGet(ownerIds = ownerIds, limit = 50)
    },
    private val characterImageCall: (UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.character, entityId = entityId, limit = 1)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ScenePreviewUiState(isLoading = true))
    val uiState: StateFlow<ScenePreviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val scene = getSceneCall(sceneId).result
                _uiState.value = _uiState.value.copy(scene = scene, isLoading = false)
                resolveHeroImage(scene.id)
                loadCharacters(scene.ownerId)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load scene"
                )
            }
        }
    }

    /**
     * Resolves the scene's title image (first scene media asset URL). Failure
     * leaves the hero without an image rather than failing the whole preview.
     */
    private fun resolveHeroImage(sceneId: UUID?) {
        if (sceneId == null) {
            _uiState.value = _uiState.value.copy(heroImageResolved = true)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val url = try {
                sceneImageCall(sceneId).result.items.firstOrNull()?.url
            } catch (_: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(heroImageUrl = url, heroImageResolved = true)
        }
    }

    private fun loadCharacters(ownerId: UUID) {
        viewModelScope.launch(ioDispatcher) {
            val characters = try {
                searchCharactersCall(listOf(ownerId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            val cards = characters.map { CharacterCardState(character = it) }
            _uiState.value = _uiState.value.copy(characters = cards)
            resolveCharacterImages(cards)
        }
    }

    /**
     * Resolves each character's portrait one at a time, updating cards as each
     * lookup completes. Failures leave the card without an image.
     */
    private fun resolveCharacterImages(cards: List<CharacterCardState>) {
        viewModelScope.launch(ioDispatcher) {
            for (card in cards) {
                val characterId = card.character.id ?: continue
                val url = try {
                    characterImageCall(characterId).result.items.firstOrNull()?.url
                } catch (_: Exception) {
                    null
                }
                updateCharacterImage(characterId, url)
            }
        }
    }

    private fun updateCharacterImage(characterId: UUID, url: String?) {
        _uiState.value = _uiState.value.copy(
            characters = _uiState.value.characters.map {
                if (it.character.id == characterId) it.copy(imageUrl = url, imageResolved = true) else it
            }
        )
    }
}
