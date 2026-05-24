package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.ScenesApi
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.Scene
import java.util.UUID

data class CreateSceneUiState(
    val title: String = "",
    val description: String = "",
    val backgroundPrompt: String = "",
    val initialMessageText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class CreateSceneViewModel(
    private val createSceneCall: (Scene) -> ModelApiResponse = { scene ->
        ScenesApi().createSceneApiV1ScenesPost(scene)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val ownerId: UUID = UUID.randomUUID()
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateSceneUiState())
    val uiState: StateFlow<CreateSceneUiState> = _uiState.asStateFlow()

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
                createSceneCall(scene)
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
