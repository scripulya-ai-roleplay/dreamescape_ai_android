package com.example.dreamescape_ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.SessionManager
import com.example.dreamescape_ai.data.PersonaSelection
import com.example.dreamescape_ai.data.PersonaStore
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.CharactersApi
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageCharacter
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaEntityType
import java.util.UUID

/** One character row in the persona picker, with its portrait resolved lazily. */
data class PersonaCard(
    val character: Character,
    val imageUrl: String? = null,
    val imageResolved: Boolean = false
)

data class ProfileUiState(
    // Characters the user may play as: created by them or bookmarked by them.
    val characters: List<PersonaCard> = emptyList(),
    val areCharactersLoaded: Boolean = false,
    // The persisted default persona (null id = play as "You").
    val selectedPersona: PersonaSelection = PersonaSelection(null, null),
    val isSelecting: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Backs the Profile tab's persona picker. Lists the characters the user created
 * or bookmarked (the same eligibility rule as the scene-preview persona picker),
 * resolves their portraits, and persists the chosen default persona via
 * [PersonaStore] so new chats start with it.
 */
class ProfileViewModel(
    private val appContext: Context,
    private val searchOwnedCharactersCall: (List<UUID>) -> ApiResponsePageCharacter = { userIds ->
        CharactersApi().searchCharacterApiV1CharactersGet(ownerIds = userIds, limit = 50)
    },
    private val searchBookmarkedCharactersCall: (List<UUID>) -> ApiResponsePageCharacter = { userIds ->
        CharactersApi().searchCharacterApiV1CharactersGet(bookmarkedBy = userIds, limit = 50)
    },
    private val characterImageCall: (UUID) -> ApiResponseListMediaAssetDTO = { entityId ->
        MediaApi().getMediaForEntityApiV1MediaEntityEntityTypeEntityIdGet(
            entityType = MediaEntityType.character, entityId = entityId
        )
    },
    private val userId: UUID = SessionManager.userId,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState: StateFlow<ProfileUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch(ioDispatcher) {
            PersonaStore.personaFlow(appContext).collect { selection ->
                _uiState.value = _uiState.value.copy(selectedPersona = selection)
            }
        }
    }

    /**
     * Characters the user may play as: those they created plus those they
     * bookmarked, deduped by id (bookmarks first). Called when the picker
     * opens; portraits resolve lazily afterwards.
     */
    fun loadCharacters() {
        if (_uiState.value.areCharactersLoaded) return
        viewModelScope.launch(ioDispatcher) {
            val owned = try {
                searchOwnedCharactersCall(listOf(userId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            val bookmarked = try {
                searchBookmarkedCharactersCall(listOf(userId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            if (owned.isEmpty() && bookmarked.isEmpty()) {
                val failed = try {
                    searchOwnedCharactersCall(listOf(userId))
                    false
                } catch (_: Exception) {
                    true
                }
                if (failed) {
                    _uiState.value = _uiState.value.copy(
                        areCharactersLoaded = true,
                        errorMessage = "Failed to load characters"
                    )
                    return@launch
                }
            }
            val merged = (bookmarked + owned).distinctBy { it.id ?: it.name }
            val cards = merged.map { PersonaCard(character = it) }
            _uiState.value = _uiState.value.copy(
                characters = cards,
                areCharactersLoaded = true,
                errorMessage = null
            )
            resolveImages(cards)
        }
    }

    /** Persists the picked persona (null = "You"); refreshes via the store flow. */
    fun selectPersona(characterId: UUID?, characterName: String?) {
        if (_uiState.value.isSelecting) return
        _uiState.value = _uiState.value.copy(isSelecting = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                PersonaStore.setPersona(
                    appContext,
                    PersonaSelection(characterId = characterId, characterName = characterName)
                )
            } catch (_: Exception) {
                // DataStore writes only fail on disk/IO errors; nothing to recover.
            }
            _uiState.value = _uiState.value.copy(isSelecting = false)
        }
    }

    /** Clears the transient load error once the picker has been dismissed. */
    fun consumePickerError() {
        if (_uiState.value.errorMessage != null) {
            _uiState.value = _uiState.value.copy(errorMessage = null)
        }
    }

    /** Resolves each card's portrait one at a time as its media lookup lands. */
    private fun resolveImages(cards: List<PersonaCard>) {
        viewModelScope.launch(ioDispatcher) {
            for (card in cards) {
                val characterId = card.character.id ?: continue
                val url = try {
                    characterImageCall(characterId).result.firstOrNull()?.url
                } catch (_: Exception) {
                    null
                }
                if (url != null) applyImage(characterId, url)
            }
        }
    }

    private fun applyImage(characterId: UUID, url: String) {
        _uiState.value = _uiState.value.copy(
            characters = _uiState.value.characters.map {
                if (it.character.id == characterId) it.copy(imageUrl = url, imageResolved = true) else it
            }
        )
    }
}
