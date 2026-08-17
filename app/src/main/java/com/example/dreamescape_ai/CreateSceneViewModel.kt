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
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ApiResponseListInitialMessage
import org.openapitools.client.models.ApiResponseScene
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.Scene
import java.io.IOException
import java.util.UUID

data class CreateSceneUiState(
    val title: String = "",
    val description: String = "",
    val backgroundPrompt: String = "",
    // A scene offers one or more opening greetings; the form starts with one row.
    val initialMessages: List<String> = listOf(""),
    val imageUris: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false,
    /** True when editing an existing scene rather than creating a new one. */
    val isEdit: Boolean = false,
    /** True while the existing scene's fields are being loaded for editing. */
    val isPrefilling: Boolean = false
)

class CreateSceneViewModel(
    private val editId: UUID? = null,
    private val createSceneCall: (Scene) -> ModelApiResponse = { scene ->
        ScenesApi().createSceneApiV1ScenesPost(scene)
    },
    private val updateSceneCall: (UUID, Scene) -> ModelApiResponse = { id, scene ->
        ScenesApi().updateSceneApiV1ScenesUpdateSceneIdPost(id, scene)
    },
    private val getSceneCall: (UUID) -> ApiResponseScene = { id ->
        ScenesApi().getSceneDetailsApiV1ScenesSceneIdGet(sceneId = id)
    },
    private val getSceneInitialMessagesCall: (UUID) -> ApiResponseListInitialMessage = { id ->
        ScenesApi().getSceneInitialMessagesApiV1ScenesSceneIdInitialMessagesGet(sceneId = id)
    },
    private val uploadImage: (entityId: UUID, uri: String, isPublic: Boolean) -> Unit = { _, _, _ -> },
    private val findCreatedId: (title: String) -> UUID? = { title ->
        ScenesApi().searchSceneApiV1ScenesGet(
            owner = listOf(JwtTokenProvider().userId), title = listOf(title), limit = 50
        ).result.items.lastOrNull { it.title == title }?.id
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ownerId: UUID = JwtTokenProvider().userId
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSceneUiState(isEdit = editId != null))
    val uiState: StateFlow<CreateSceneUiState> = _uiState.asStateFlow()

    init {
        // Edit mode: pull the existing scene so the form starts pre-filled.
        if (editId != null) loadExisting(editId)
    }

    private fun loadExisting(id: UUID) {
        _uiState.value = _uiState.value.copy(isPrefilling = true)
        viewModelScope.launch(ioDispatcher) {
            try {
                val scene = getSceneCall(id).result
                val greetings = runCatching { getSceneInitialMessagesCall(id).result }
                    .getOrDefault(emptyList())
                    .map { it.text }
                    .ifEmpty { listOf("") }
                _uiState.value = _uiState.value.copy(
                    title = scene.title,
                    description = scene.description.orEmpty(),
                    backgroundPrompt = scene.backgroundPrompt,
                    initialMessages = greetings,
                    isPrefilling = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isPrefilling = false,
                    errorMessage = e.message ?: "Failed to load scene"
                )
            }
        }
    }

    // Creation progress so a retry after a failed image upload doesn't re-create
    // (and duplicate) the scene.
    private var entityCreated: Boolean = false
    private var createdEntityId: UUID? = null
    private val uploadedUris: MutableSet<String> = mutableSetOf()

    fun onTitleChanged(title: String) {
        _uiState.value = _uiState.value.copy(title = title, errorMessage = null)
    }

    fun onDescriptionChanged(description: String) {
        _uiState.value = _uiState.value.copy(description = description, errorMessage = null)
    }

    fun onBackgroundPromptChanged(backgroundPrompt: String) {
        _uiState.value = _uiState.value.copy(backgroundPrompt = backgroundPrompt, errorMessage = null)
    }

    fun onInitialMessageChanged(index: Int, text: String) {
        val current = _uiState.value.initialMessages
        if (index !in current.indices) return
        _uiState.value = _uiState.value.copy(
            initialMessages = current.toMutableList().apply { set(index, text) },
            errorMessage = null
        )
    }

    fun addInitialMessage() {
        _uiState.value = _uiState.value.copy(
            initialMessages = _uiState.value.initialMessages + "",
            errorMessage = null
        )
    }

    fun removeInitialMessage(index: Int) {
        val current = _uiState.value.initialMessages
        if (current.size <= 1 || index !in current.indices) return
        _uiState.value = _uiState.value.copy(
            initialMessages = current.toMutableList().apply { removeAt(index) },
            errorMessage = null
        )
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
            state.title.isBlank() -> "Title is required"
            state.backgroundPrompt.isBlank() -> "Background prompt is required"
            state.initialMessages.none { it.isNotBlank() } -> "At least one initial message is required"
            else -> null
        }
    }

    fun createScene() {
        val validationError = validate()
        if (validationError != null) {
            _uiState.value = _uiState.value.copy(errorMessage = validationError)
            return
        }

        val state = _uiState.value
        if (state.isLoading) return

        // When editing, new images attach to the existing scene's id.
        if (state.isEdit && editId != null) createdEntityId = editId

        val scene = Scene(
            id = if (state.isEdit) editId else null,
            ownerId = ownerId,
            title = state.title.trim(),
            description = state.description.trim().ifEmpty { null },
            backgroundPrompt = state.backgroundPrompt.trim(),
            // Blank rows are dropped; the backend requires at least one greeting.
            initialMessages = state.initialMessages
                .mapNotNull { it.trim().takeIf(String::isNotBlank) }
                .map { InitialMessage(text = it) }
        )

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                if (!entityCreated) {
                    if (state.isEdit && editId != null) {
                        updateSceneCall(editId, scene)
                    } else {
                        // The create endpoint returns no id, so the scene must be
                        // created before its images can be attached.
                        createSceneCall(scene)
                    }
                    entityCreated = true
                }

                val pending = _uiState.value.imageUris
                if (pending.isNotEmpty()) {
                    val entityId = createdEntityId
                        ?: findCreatedId(scene.title)
                            ?: throw IOException("Created, but couldn't attach images (scene id not found)")
                    createdEntityId = entityId
                    // The backend serves an entity's media newest-first and every
                    // reader takes the first asset as the preview, so the first
                    // picked image must be uploaded LAST (it ends up newest).
                    // Scenes have no privacy field; their media defaults to public.
                    for (uri in pending.asReversed()) {
                        if (uri in uploadedUris) continue
                        uploadImage(entityId, uri, true)
                        uploadedUris.add(uri)
                    }
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: if (state.isEdit) "Failed to update scene"
                    else "Failed to create scene"
                )
            }
        }
    }
}
