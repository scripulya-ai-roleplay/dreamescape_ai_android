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
import org.openapitools.client.models.ApiResponseCharacter
import org.openapitools.client.models.Character
import org.openapitools.client.models.ModelApiResponse
import java.io.IOException
import java.util.UUID

data class CreateCharacterUiState(
    val name: String = "",
    val systemPrompt: String = "",
    val isPublic: Boolean = false,
    val imageUris: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    /** True when editing an existing character rather than creating a new one. */
    val isEdit: Boolean = false,
    /** True while the existing character's fields are being loaded for editing. */
    val isPrefilling: Boolean = false
)

class CreateCharacterViewModel(
    private val editId: UUID? = null,
    private val createCharacterCall: (Character) -> ModelApiResponse = { character ->
        CharactersApi().createCharacterApiV1CharactersPost(character)
    },
    private val updateCharacterCall: (UUID, Character) -> ModelApiResponse = { id, character ->
        CharactersApi().updateCharacterApiV1CharactersUpdateCharacterIdPost(id, character)
    },
    private val getCharacterCall: (UUID) -> ApiResponseCharacter = { id ->
        CharactersApi().getCharacterDetailsApiV1CharactersCharacterIdGet(characterId = id)
    },
    private val uploadImage: (entityId: UUID, uri: String, isPublic: Boolean) -> Unit = { _, _, _ -> },
    private val findCreatedId: (name: String) -> UUID? = { name ->
        CharactersApi().searchCharacterApiV1CharactersGet(
            ownerIds = listOf(JwtTokenProvider().userId), names = listOf(name), limit = 50
        ).result.items.lastOrNull { it.name == name }?.id
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ownerId: UUID = JwtTokenProvider().userId
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCharacterUiState(isEdit = editId != null))
    val uiState: StateFlow<CreateCharacterUiState> = _uiState.asStateFlow()

    init {
        // Edit mode: pull the existing character so the form starts pre-filled.
        if (editId != null) loadExisting(editId)
    }

    private fun loadExisting(id: UUID) {
        _uiState.value = _uiState.value.copy(isPrefilling = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val character = getCharacterCall(id).result
                _uiState.value = _uiState.value.copy(
                    name = character.name,
                    systemPrompt = character.systemPrompt,
                    isPublic = character.isPublic ?: false,
                    isPrefilling = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPrefilling = false,
                    errorMessage = e.message ?: "Failed to load character"
                )
            }
        }
    }

    // Creation progress so a retry after a failed image upload doesn't re-create
    // (and duplicate) the character.
    private var entityCreated: Boolean = false
    private var createdEntityId: UUID? = null
    private val uploadedUris: MutableSet<String> = mutableSetOf()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onSystemPromptChanged(systemPrompt: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = systemPrompt, errorMessage = null)
    }

    fun onIsPublicChanged(isPublic: Boolean) {
        _uiState.value = _uiState.value.copy(isPublic = isPublic, errorMessage = null)
    }

    fun onImagesAdded(uris: List<String>) {
        _uiState.value = _uiState.value.copy(
            imageUris = _uiState.value.imageUris + uris,
            errorMessage = null
        )
    }

    fun onImageRemoved(index: Int) {
        val current = _uiState.value.imageUris.toMutableList()
        if (index !in current.indices) return
        val removed = current.removeAt(index)
        uploadedUris.remove(removed)
        _uiState.value = _uiState.value.copy(imageUris = current, errorMessage = null)
    }

    fun validate(): String? {
        val state = _uiState.value
        return when {
            state.name.isBlank() -> "Name is required"
            state.systemPrompt.isBlank() -> "System prompt is required"
            else -> null
        }
    }

    fun createCharacter() {
        val validationError = validate()
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        val state = _uiState.value
        if (state.isLoading) return

        // When editing, new images attach to the existing character's id.
        if (state.isEdit && editId != null) createdEntityId = editId

        val character = Character(
            id = if (state.isEdit) editId else null,
            ownerId = ownerId,
            name = state.name.trim(),
            systemPrompt = state.systemPrompt.trim(),
            isPublic = state.isPublic
        )

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                if (!entityCreated) {
                    if (state.isEdit && editId != null) {
                        updateCharacterCall(editId, character)
                    } else {
                        // The create endpoint returns no id, so the character must be
                        // created before its images can be attached.
                        createCharacterCall(character)
                    }
                    entityCreated = true
                }

                val pending = _uiState.value.imageUris
                if (pending.isNotEmpty()) {
                    val entityId = createdEntityId
                        ?: findCreatedId(character.name)
                            ?: throw IOException("Created, but couldn't attach images (character id not found)")
                    createdEntityId = entityId
                    // Upload in order: the first uploaded image becomes the preview.
                    for (uri in pending) {
                        if (uri in uploadedUris) continue
                        uploadImage(entityId, uri, state.isPublic)
                        uploadedUris.add(uri)
                    }
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: if (state.isEdit) "Failed to update character"
                    else "Failed to create character"
                )
            }
        }
    }
}
