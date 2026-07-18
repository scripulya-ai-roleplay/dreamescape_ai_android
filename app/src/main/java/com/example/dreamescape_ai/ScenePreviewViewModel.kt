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
import org.openapitools.client.apis.CharactersApi
import org.openapitools.client.apis.ChatsApi
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ApiResponseBookmarkState
import org.openapitools.client.models.ApiResponseLikeState
import org.openapitools.client.models.ApiResponseListCharacter
import org.openapitools.client.models.ApiResponsePageCharacter
import org.openapitools.client.models.ApiResponsePageChat
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.ApiResponseScene
import org.openapitools.client.models.AttachCharactersDTO
import org.openapitools.client.models.Character
import org.openapitools.client.models.Chat
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.ModelApiResponse
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
    val errorMessage: String? = null,
    val isCreatingChat: Boolean = false,
    val chatCreationError: String? = null,
    val createdChatId: UUID? = null,
    val createdChatTitle: String? = null,
    // Like / bookmark engagement for the current user.
    val isLiked: Boolean = false,
    val likesCount: Int = 0,
    val isBookmarked: Boolean = false,
    val engagementError: String? = null,
    // Persona picker: characters the user may play as (bookmarked + created).
    val eligibleCharacters: List<CharacterCardState> = emptyList(),
    val areEligibleLoaded: Boolean = false,
    val selectedCharacterId: UUID? = null,
    // Whether the current user owns this scene — only owners may attach characters.
    val isOwner: Boolean = false,
    // "Add characters" picker: characters the owner may attach to this scene.
    val attachCandidates: List<CharacterCardState> = emptyList(),
    val areAttachCandidatesLoaded: Boolean = false,
    val selectedAttachIds: Set<UUID> = emptySet(),
    val isAttachingCharacters: Boolean = false,
    val attachError: String? = null,
    val attachSuccess: Boolean = false
)

class ScenePreviewViewModel(
    private val sceneId: UUID,
    private val getSceneCall: (UUID) -> ApiResponseScene = { id ->
        ScenesApi().getSceneDetailsApiV1ScenesSceneIdGet(sceneId = id)
    },
    private val sceneImageCall: (UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.scene, entityId = entityId, limit = 1)
    },
    // The scene's cast: characters attached to it (POST /scenes/{id}/characters),
    // read back through GET /scenes/{id}/characters — the source of truth, not the
    // owner's whole roster.
    private val getSceneCharactersCall: (sceneId: UUID) -> ApiResponseListCharacter = { id ->
        ScenesApi().getSceneCharactersApiV1ScenesSceneIdCharactersGet(sceneId = id)
    },
    private val characterImageCall: (UUID) -> ApiResponsePageMediaAssetDTO = { entityId ->
        MediaApi().searchMediaApiV1MediaGet(entityType = MediaEntityType.character, entityId = entityId, limit = 1)
    },
    // Like / bookmark engagement with this scene.
    private val getLikeStateCall: (UUID) -> ApiResponseLikeState = { id ->
        ScenesApi().getSceneLikeStateApiV1ScenesSceneIdLikeGet(sceneId = id)
    },
    private val setLikeCall: (UUID) -> ApiResponseLikeState = { id ->
        ScenesApi().likeSceneApiV1ScenesSceneIdLikePost(sceneId = id)
    },
    private val unsetLikeCall: (UUID) -> ApiResponseLikeState = { id ->
        ScenesApi().unlikeSceneApiV1ScenesSceneIdLikeDelete(sceneId = id)
    },
    private val getBookmarkStateCall: (UUID) -> ApiResponseBookmarkState = { id ->
        ScenesApi().getSceneBookmarkStateApiV1ScenesSceneIdBookmarkGet(sceneId = id)
    },
    private val setBookmarkCall: (UUID) -> ApiResponseBookmarkState = { id ->
        ScenesApi().bookmarkSceneApiV1ScenesSceneIdBookmarkPost(sceneId = id)
    },
    private val unsetBookmarkCall: (UUID) -> ApiResponseBookmarkState = { id ->
        ScenesApi().unbookmarkSceneApiV1ScenesSceneIdBookmarkDelete(sceneId = id)
    },
    // Characters the user may play as: bookmarked by them, or created by them.
    private val searchBookmarkedCharactersCall: (List<UUID>) -> ApiResponsePageCharacter = { userIds ->
        CharactersApi().searchCharacterApiV1CharactersGet(bookmarkedBy = userIds, limit = 50)
    },
    private val searchOwnedCharactersCall: (List<UUID>) -> ApiResponsePageCharacter = { userIds ->
        CharactersApi().searchCharacterApiV1CharactersGet(ownerIds = userIds, limit = 50)
    },
    // Attaches characters to this scene (POST /scenes/{id}/characters). Owner-only
    // on the backend (403 otherwise); the picker is only surfaced to the owner.
    private val attachCharactersCall: (sceneId: UUID, characterIds: List<UUID>) -> ModelApiResponse = { id, ids ->
        ScenesApi().attachCharactersToSceneApiV1ScenesSceneIdCharactersPost(
            sceneId = id,
            attachCharactersDTO = AttachCharactersDTO(characterIds = ids)
        )
    },
    private val userId: UUID = JwtTokenProvider().userId,
    private val searchChatsCall: (userIds: List<UUID>?, offset: Int?, limit: Int?) -> ApiResponsePageChat = { userIds, offset, limit ->
        ChatsApi().searchChatsApiV1ChatsGet(userIds = userIds, offset = offset, limit = limit)
    },
    private val createChatCall: (Chat) -> ModelApiResponse = { chat ->
        ChatsApi().createChatApiV1ChatsPost(chat)
    },
    private val chatIdProvider: () -> UUID = { UUID.randomUUID() },
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
                _uiState.value = _uiState.value.copy(
                    scene = scene,
                    isLoading = false,
                    isOwner = scene.ownerId == userId
                )
                resolveHeroImage(scene.id)
                loadCharacters()
                loadEngagementState(scene.id)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load scene"
                )
            }
        }
    }

    /**
     * Creates a chat for this scene with a generic name ("Chat #N", where N is
     * derived from how many chats the user already has) and exposes the
     * server-assigned chat id via [ScenePreviewUiState.createdChatId] so the
     * screen can navigate to it. Mirrors CreateChatViewModel: the backend mints
     * its own id, so the id attached to the request body is ignored.
     */
    fun startChat() {
        if (_uiState.value.isCreatingChat) return
        _uiState.value = _uiState.value.copy(isCreatingChat = true, chatCreationError = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val title = "Chat #${countExistingChats() + 1}"
                val chat = Chat(
                    title = title,
                    userId = userId,
                    sceneId = sceneId,
                    // Persona chosen in the picker (null = play as a generic user).
                    userCharacterId = _uiState.value.selectedCharacterId,
                    id = chatIdProvider()
                )
                val response = createChatCall(chat)
                val serverChatId = extractCreatedChatId(response)
                if (serverChatId == null) {
                    _uiState.value = _uiState.value.copy(
                        isCreatingChat = false,
                        chatCreationError = "Chat was created but the response did not include its id."
                    )
                } else {
                    _uiState.value = _uiState.value.copy(
                        isCreatingChat = false,
                        createdChatId = serverChatId,
                        createdChatTitle = title
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isCreatingChat = false,
                    chatCreationError = e.message ?: "Failed to create chat"
                )
            }
        }
    }

    /** How many chats the user already has; failures fall back to 0. */
    private suspend fun countExistingChats(): Int =
        try {
            searchChatsCall(listOf(userId), 0, 100).result.items.size
        } catch (_: Exception) {
            0
        }

    /**
     * Like / bookmark state for the current user against this scene. Fetched
     * once on load so the buttons reflect the server's truth; failures leave
     * both buttons in their default (off) state rather than blocking the screen.
     */
    private fun loadEngagementState(sceneId: UUID?) {
        if (sceneId == null) return
        viewModelScope.launch(ioDispatcher) {
            val like = try {
                getLikeStateCall(sceneId).result
            } catch (_: Exception) {
                null
            }
            val bookmark = try {
                getBookmarkStateCall(sceneId).result
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

    /** Toggles the scene's like optimistically; reverts to the prior state on failure. */
    fun toggleLike() {
        val sceneId = _uiState.value.scene?.id ?: return
        val previouslyLiked = _uiState.value.isLiked
        _uiState.value = _uiState.value.copy(isLiked = !previouslyLiked, engagementError = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val state = if (previouslyLiked) unsetLikeCall(sceneId).result else setLikeCall(sceneId).result
                _uiState.value = _uiState.value.copy(isLiked = state.liked, likesCount = state.likesCount)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLiked = previouslyLiked,
                    engagementError = e.message ?: "Failed to update like"
                )
            }
        }
    }

    /** Toggles the scene's bookmark optimistically; reverts to the prior state on failure. */
    fun toggleBookmark() {
        val sceneId = _uiState.value.scene?.id ?: return
        val previouslyBookmarked = _uiState.value.isBookmarked
        _uiState.value = _uiState.value.copy(isBookmarked = !previouslyBookmarked, engagementError = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val state =
                    if (previouslyBookmarked) unsetBookmarkCall(sceneId).result else setBookmarkCall(sceneId).result
                _uiState.value = _uiState.value.copy(isBookmarked = state.bookmarked)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isBookmarked = previouslyBookmarked,
                    engagementError = e.message ?: "Failed to update bookmark"
                )
            }
        }
    }

    /**
     * Characters the user may play as in this scene: those they bookmarked plus
     * those they created, deduped by id (bookmarks first). Loaded lazily when
     * the picker opens; portraits resolve via the shared [updateCharacterImage].
     */
    fun loadEligibleCharacters() {
        if (_uiState.value.areEligibleLoaded) return
        viewModelScope.launch(ioDispatcher) {
            val bookmarked = try {
                searchBookmarkedCharactersCall(listOf(userId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            val owned = try {
                searchOwnedCharactersCall(listOf(userId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            val merged = (bookmarked + owned).distinctBy { it.id ?: it.name }
            val cards = merged.map { CharacterCardState(character = it) }
            _uiState.value = _uiState.value.copy(eligibleCharacters = cards, areEligibleLoaded = true)
            resolveCharacterImages(cards)
        }
    }

    /** Selects the character to play as (null = start the chat with no persona). */
    fun selectCharacter(characterId: UUID?) {
        _uiState.value = _uiState.value.copy(selectedCharacterId = characterId)
    }

    /**
     * Characters the owner may attach to this scene: those they created. Loaded
     * lazily when the "Add characters" picker opens; portraits resolve via the
     * shared [updateCharacterImage] (which also refreshes this list).
     */
    fun loadAttachCandidates() {
        if (_uiState.value.areAttachCandidatesLoaded) return
        viewModelScope.launch(ioDispatcher) {
            val owned = try {
                searchOwnedCharactersCall(listOf(userId)).result.items
            } catch (_: Exception) {
                emptyList()
            }
            val cards = owned.map { CharacterCardState(character = it) }
            _uiState.value = _uiState.value.copy(
                attachCandidates = cards,
                areAttachCandidatesLoaded = true
            )
            resolveCharacterImages(cards)
        }
    }

    /** Toggles membership of [characterId] in the attach selection. */
    fun toggleAttachSelection(characterId: UUID) {
        val current = _uiState.value.selectedAttachIds
        _uiState.value = _uiState.value.copy(
            selectedAttachIds = if (characterId in current) current - characterId else current + characterId
        )
    }

    /**
     * Attaches the selected characters to this scene via
     * `POST /scenes/{id}/characters`. Idempotent on the backend (re-adding an
     * already-attached character is a no-op). The endpoint is owner-only; the
     * picker is only shown to owners, so a 403 here is unexpected.
     */
    fun attachSelectedCharacters() {
        val sceneId = _uiState.value.scene?.id ?: return
        val selected = _uiState.value.selectedAttachIds
        if (selected.isEmpty()) {
            _uiState.value = _uiState.value.copy(attachError = "Select at least one character")
            return
        }
        if (_uiState.value.isAttachingCharacters) return
        _uiState.value = _uiState.value.copy(
            isAttachingCharacters = true,
            attachError = null,
            attachSuccess = false
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                attachCharactersCall(sceneId, selected.toList())
                _uiState.value = _uiState.value.copy(
                    isAttachingCharacters = false,
                    attachSuccess = true,
                    selectedAttachIds = emptySet()
                )
                // Refresh the carousel so the just-attached characters appear
                // without forcing the user to reopen the scene.
                loadCharacters()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isAttachingCharacters = false,
                    attachError = e.message ?: "Failed to add characters"
                )
            }
        }
    }

    /** Resets the transient attach-success flag once the picker has dismissed. */
    fun consumeAttachSuccess() {
        _uiState.value = _uiState.value.copy(attachSuccess = false)
    }

    /**
     * The create-chat response is `{"result":{"id":"<uuid>"}, ...}`. The result
     * is untyped (`Any?`), which Moshi deserializes as a `Map<String, Any?>` for
     * a JSON object, so the server-assigned id is pulled out of it.
     */
    private fun extractCreatedChatId(response: ModelApiResponse): UUID? {
        val idRaw = (response.result as? Map<*, *>)?.get("id")
        return idRaw?.toString()?.let { runCatching { UUID.fromString(it) }.getOrNull() }
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

    private fun loadCharacters() {
        viewModelScope.launch(ioDispatcher) {
            val characters = try {
                getSceneCharactersCall(sceneId).result
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
        val current = _uiState.value
        // A resolved portrait applies wherever the character appears — the cast
        // carousel, the persona-picker list, and the attach-characters picker all
        // reference the same ids.
        _uiState.value = current.copy(
            characters = current.characters.map {
                if (it.character.id == characterId) it.copy(imageUrl = url, imageResolved = true) else it
            },
            eligibleCharacters = current.eligibleCharacters.map {
                if (it.character.id == characterId) it.copy(imageUrl = url, imageResolved = true) else it
            },
            attachCandidates = current.attachCandidates.map {
                if (it.character.id == characterId) it.copy(imageUrl = url, imageResolved = true) else it
            }
        )
    }
}
