package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.models.ApiResponseImportLorebookResultDTO
import org.openapitools.client.models.ApiResponseImportPreviewDTO
import org.openapitools.client.models.ImportCandidateDTO
import org.openapitools.client.models.ImportLorebookResultDTO
import java.io.File

enum class ImportPhase { IDLE, PREVIEWING, IMPORTING, DONE }

data class ImportSillyTavernUiState(
    val phase: ImportPhase = ImportPhase.IDLE,
    val fileName: String? = null,
    /** Materialized cache file of the picked JSON; kept so import can re-upload it. */
    val file: File? = null,
    val characters: List<ImportCandidateDTO> = emptyList(),
    val scenes: List<ImportCandidateDTO> = emptyList(),
    val selectedCharacterKeys: Set<String> = emptySet(),
    val selectedSceneKeys: Set<String> = emptySet(),
    val otherEntries: Int = 0,
    val skippedEntries: Int = 0,
    /** Off by default: importing images makes the backend fetch external URLs. */
    val importImages: Boolean = false,
    val isPublic: Boolean = false,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val result: ImportLorebookResultDTO? = null,
)

/**
 * Drives the SillyTavern import flow: pick → (client-side validate) → server
 * preview → user selects a subset → import **unlinked** ([linkScenes] = false),
 * leaving characters and scenes independent for the user to link later.
 *
 * API calls are injected (see [CreateCharacterViewModel]) so the logic is
 * unit-testable without the network.
 */
class ImportSillyTavernViewModel(
    private val previewCall: (File) -> ApiResponseImportPreviewDTO = { SillyTavernImporter.preview(it) },
    private val importCall: (
        File, List<String>, Boolean, Boolean, Boolean,
    ) -> ApiResponseImportLorebookResultDTO = { file, keys, isPublic, importImages, linkScenes ->
        SillyTavernImporter.importLorebook(file, keys, isPublic, importImages, linkScenes)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ImportSillyTavernUiState())
    val uiState: StateFlow<ImportSillyTavernUiState> = _uiState.asStateFlow()

    fun onFilePicked(name: String, file: File) {
        viewModelScope.launch(ioDispatcher) {
            // Fast client-side guard before hitting the network.
            val classification = runCatching { SillyTavernFile.classify(file.readBytes()) }
                .getOrElse {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false, errorMessage = "Could not read file: ${it.message}"
                    )
                    return@launch
                }
            when (classification) {
                is SillyTavernFileResult.NotJson ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "This file isn't valid JSON: ${classification.error}"
                    )
                SillyTavernFileResult.NotSillyTavern ->
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "This doesn't look like a SillyTavern file."
                    )
                SillyTavernFileResult.Valid -> loadPreview(name, file)
            }
        }
    }

    private fun loadPreview(name: String, file: File) {
        _uiState.value = _uiState.value.copy(
            phase = ImportPhase.PREVIEWING, fileName = name, file = file,
            isLoading = true, errorMessage = null, result = null,
        )
        viewModelScope.launch(ioDispatcher) {
            try {
                val preview = previewCall(file).result
                if (preview.characters.isEmpty() && preview.scenes.isEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "No importable characters or scenes found in this file.",
                    )
                    return@launch
                }
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    characters = preview.characters,
                    scenes = preview.scenes,
                    // Everything is selected by default; the user trims from there.
                    selectedCharacterKeys = preview.characters.map { it.key }.toSet(),
                    selectedSceneKeys = preview.scenes.map { it.key }.toSet(),
                    otherEntries = preview.otherEntries,
                    skippedEntries = preview.skippedEntries,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, errorMessage = e.message ?: "Failed to read the file"
                )
            }
        }
    }

    fun toggleCharacter(key: String) {
        val current = _uiState.value.selectedCharacterKeys.toMutableSet()
        if (!current.add(key)) current.remove(key)
        _uiState.value = _uiState.value.copy(selectedCharacterKeys = current)
    }

    fun toggleScene(key: String) {
        val current = _uiState.value.selectedSceneKeys.toMutableSet()
        if (!current.add(key)) current.remove(key)
        _uiState.value = _uiState.value.copy(selectedSceneKeys = current)
    }

    fun setAllCharacters(selected: Boolean) {
        _uiState.value = _uiState.value.copy(
            selectedCharacterKeys = if (selected) _uiState.value.characters.map { it.key }.toSet() else emptySet()
        )
    }

    fun setAllScenes(selected: Boolean) {
        _uiState.value = _uiState.value.copy(
            selectedSceneKeys = if (selected) _uiState.value.scenes.map { it.key }.toSet() else emptySet()
        )
    }

    fun setIsPublic(value: Boolean) {
        _uiState.value = _uiState.value.copy(isPublic = value)
    }

    fun setImportImages(value: Boolean) {
        _uiState.value = _uiState.value.copy(importImages = value)
    }

    fun dismissError() {
        _uiState.value = _uiState.value.copy(errorMessage = null)
    }

    /** Returns to the initial picker state so the user can import another file. */
    fun reset() {
        _uiState.value = ImportSillyTavernUiState()
    }

    fun doImport() {
        val state = _uiState.value
        if (state.isLoading) return
        val file = state.file ?: return
        val selectedKeys = state.selectedCharacterKeys + state.selectedSceneKeys
        if (selectedKeys.isEmpty()) {
            _uiState.value = state.copy(errorMessage = "Select at least one character or scene to import.")
            return
        }

        _uiState.value = state.copy(phase = ImportPhase.IMPORTING, isLoading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val result = importCall(
                    file, selectedKeys.toList(), state.isPublic, state.importImages, false,
                ).result
                _uiState.value = _uiState.value.copy(
                    phase = ImportPhase.DONE, isLoading = false, result = result
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false, errorMessage = e.message ?: "Import failed"
                )
            }
        }
    }
}
