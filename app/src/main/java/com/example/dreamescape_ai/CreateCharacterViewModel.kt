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
import org.openapitools.client.models.Character
import org.openapitools.client.models.ModelApiResponse

data class CreateCharacterUiState(
    val name: String = "",
    val systemPrompt: String = "",
    val isPublic: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isSuccess: Boolean = false
)

class CreateCharacterViewModel(
    private val createCharacterCall: (Character) -> ModelApiResponse = { character ->
        CharactersApi().createCharacterApiV1CharactersPost(character)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(CreateCharacterUiState())
    val uiState: StateFlow<CreateCharacterUiState> = _uiState.asStateFlow()

    fun onNameChanged(name: String) {
        _uiState.value = _uiState.value.copy(name = name, errorMessage = null)
    }

    fun onSystemPromptChanged(systemPrompt: String) {
        _uiState.value = _uiState.value.copy(systemPrompt = systemPrompt, errorMessage = null)
    }

    fun onIsPublicChanged(isPublic: Boolean) {
        _uiState.value = _uiState.value.copy(isPublic = isPublic, errorMessage = null)
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
        val character = Character(
            name = state.name.trim(),
            systemPrompt = state.systemPrompt.trim(),
            isPublic = state.isPublic
        )

        _uiState.value = state.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                createCharacterCall(character)
                _uiState.value = _uiState.value.copy(isLoading = false, isSuccess = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to create character"
                )
            }
        }
    }
}
