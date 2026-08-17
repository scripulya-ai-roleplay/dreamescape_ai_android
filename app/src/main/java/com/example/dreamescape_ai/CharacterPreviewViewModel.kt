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
import org.openapitools.client.models.ApiResponseBookmarkState
import org.openapitools.client.models.ApiResponseCharacter
import org.openapitools.client.models.ApiResponseLikeState
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaEntityType
import java.util.UUID

data class CharacterPreviewUiState(
    val character: Character? = null,
    val portraitUrl: String? = null,
    val portraitResolved: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    // Like / bookmark engagement for the current user.
    val isLiked: Boolean = false,
    val likesCount: Int = 0,
    val isBookmarked: Boolean = false,
    val engagementError: String? = null
)

class CharacterPreviewViewModel(
    private val characterId: UUID,
    private val getCharacterCall: (UUID) -> ApiResponseCharacter = { id ->
        CharactersApi().getCharacterDetailsApiV1CharactersCharacterIdGet(characterId = id)
    },
    private val portraitImageCall: (UUID) -> ApiResponseListMediaAssetDTO = { entityId ->
        MediaApi().getMediaForEntityApiV1MediaEntityEntityTypeEntityIdGet(
            entityType = MediaEntityType.character, entityId = entityId
        )
    },
    // Like / bookmark engagement with this character.
    private val getLikeStateCall: (UUID) -> ApiResponseLikeState = { id ->
        CharactersApi().getCharacterLikeStateApiV1CharactersCharacterIdLikeGet(characterId = id)
    },
    private val setLikeCall: (UUID) -> ApiResponseLikeState = { id ->
        CharactersApi().likeCharacterApiV1CharactersCharacterIdLikePost(characterId = id)
    },
    private val unsetLikeCall: (UUID) -> ApiResponseLikeState = { id ->
        CharactersApi().unlikeCharacterApiV1CharactersCharacterIdLikeDelete(characterId = id)
    },
    private val getBookmarkStateCall: (UUID) -> ApiResponseBookmarkState = { id ->
        CharactersApi().getCharacterBookmarkStateApiV1CharactersCharacterIdBookmarkGet(characterId = id)
    },
    private val setBookmarkCall: (UUID) -> ApiResponseBookmarkState = { id ->
        CharactersApi().bookmarkCharacterApiV1CharactersCharacterIdBookmarkPost(characterId = id)
    },
    private val unsetBookmarkCall: (UUID) -> ApiResponseBookmarkState = { id ->
        CharactersApi().unbookmarkCharacterApiV1CharactersCharacterIdBookmarkDelete(characterId = id)
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
                loadEngagementState(character.id)
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
                portraitImageCall(characterId).result.firstOrNull()?.url
            } catch (_: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(portraitUrl = url, portraitResolved = true)
        }
    }

    /**
     * Like / bookmark state for the current user against this character. Fetched
     * once on load; failures leave both buttons in their default (off) state.
     */
    private fun loadEngagementState(characterId: UUID?) {
        if (characterId == null) return
        viewModelScope.launch(ioDispatcher) {
            val like = try {
                getLikeStateCall(characterId).result
            } catch (_: Exception) {
                null
            }
            val bookmark = try {
                getBookmarkStateCall(characterId).result
            } catch (_: Exception) {
                null
            }
            _uiState.value = _uiState.value.copy(
                isLiked = like?.liked == true,
                likesCount = like?.likesCount ?: 0,
                isBookmarked = bookmark?.bookmarked == true
            )
        }
    }

    /** Toggles the character's like optimistically; reverts to the prior state on failure. */
    fun toggleLike() {
        val previouslyLiked = _uiState.value.isLiked
        _uiState.value = _uiState.value.copy(isLiked = !previouslyLiked, engagementError = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val state =
                    if (previouslyLiked) unsetLikeCall(characterId).result else setLikeCall(characterId).result
                _uiState.value = _uiState.value.copy(isLiked = state.liked, likesCount = state.likesCount)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLiked = previouslyLiked,
                    engagementError = e.message ?: "Failed to update like"
                )
            }
        }
    }

    /** Toggles the character's bookmark optimistically; reverts to the prior state on failure. */
    fun toggleBookmark() {
        val previouslyBookmarked = _uiState.value.isBookmarked
        _uiState.value = _uiState.value.copy(isBookmarked = !previouslyBookmarked, engagementError = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val state =
                    if (previouslyBookmarked) unsetBookmarkCall(characterId).result else setBookmarkCall(characterId).result
                _uiState.value = _uiState.value.copy(isBookmarked = state.bookmarked)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBookmarked = previouslyBookmarked,
                    engagementError = e.message ?: "Failed to update bookmark"
                )
            }
        }
    }
}
