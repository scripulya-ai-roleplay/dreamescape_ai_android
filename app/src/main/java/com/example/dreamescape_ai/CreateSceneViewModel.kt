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
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.Scene
import java.io.IOException
import java.util.UUID

data class CreateSceneUiState(
    val title: String = "",
    val description: String = "",
    val backgroundPrompt: String = "",
    val initialMessageText: String = "",
    val imageUris: List<String> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class CreateSceneViewModel(
    private val createSceneCall: (Scene) -> ModelApiResponse = { scene ->
        ScenesApi().createSceneApiV1ScenesPost(scene)
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

    private val _uiState = MutableStateFlow(CreateSceneUiState())
    val uiState: StateFlow<CreateSceneUiState> = _uiState.asStateFlow()

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

    fun onInitialMessageTextChanged(initialMessageText: String) {
        _uiState.value = _uiState.value.copy(initialMessageText = initialMessageText, errorMessage = null)
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
            state.initialMessageText.isBlank() -> "Initial message text is required"
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

        val scene = Scene(
            ownerId = ownerId,
            title = state.title.trim(),
            description = state.description.trim().ifEmpty { null },
            backgroundPrompt = state.backgroundPrompt.trim(),
            initialMessageText = state.initialMessageText.trim()
        )

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                // The create endpoint returns no id, so the scene must be
                // created before its images can be attached.
                if (!entityCreated) {
                    createSceneCall(scene)
                    entityCreated = true
                }

                val pending = _uiState.value.imageUris
                if (pending.isNotEmpty()) {
                    val entityId = createdEntityId
                        ?: findCreatedId(scene.title)
                            ?: throw IOException("Created, but couldn't attach images (scene id not found)")
                    createdEntityId = entityId
                    // Upload in order: the first uploaded image becomes the preview.
                    // Scenes have no privacy field; their media defaults to public.
                    for (uri in pending) {
                        if (uri in uploadedUris) continue
                        uploadImage(entityId, uri, true)
                        uploadedUris.add(uri)
                    }
                }

                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create scene"
                )
            }
        }
    }
}
