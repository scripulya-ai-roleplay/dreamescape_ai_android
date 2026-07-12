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
import org.openapitools.client.models.ApiResponseCharacter
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaEntityType
import java.util.UUID

data class CharacterPreviewUiState(
    val character: Character? = null,
    val portraitUrl: String? = null,
    val portraitResolved: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class CharacterPreviewViewModel(
    private val characterId: UUID,
    private val getCharacterCall: (UUID) -> ApiResponseCharacter = { id ->
        CharactersApi().getCharacterDetailsApiV1CharactersCharacterIdGet(characterId = id)
    },
    private val portraitImageCall: (UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.character, entityId = entityId, limit = 1)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(CharacterPreviewUiState(isLoading = true))
    val uiState: StateFlow<CharacterPreviewUiState> = _uiState.asStateFlow()

    init {
        load()
    }

    fun load() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val character = getCharacterCall(characterId).result
                _uiState.value = _uiState.value.copy(character = character, isLoading = false)
                resolvePortraitImage(character.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load character"
                )
            }
        }
    }

    /**
     * Resolves the character's portrait (first character media asset URL).
     * Failure leaves the hero without an image rather than failing the preview.
     */
    private fun resolvePortraitImage(characterId: UUID?) {
        if (characterId == null) {
            _uiState.value = _uiState.value.copy(portraitResolved = true)
            return
        }
        viewModelScope.launch(ioDispatcher) {
            val url = try {
                portraitImageCall(characterId).result.items.firstOrNull()?.url
            } catch (_: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(portraitUrl = url, portraitResolved = true)
        }
    }
}
