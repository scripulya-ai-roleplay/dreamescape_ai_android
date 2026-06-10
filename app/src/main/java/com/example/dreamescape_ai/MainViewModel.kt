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

data class MainUiState(
    val isLoading: Boolean = false,
    val isApiAvailable: Boolean = false,
    val showApiUnavailableDialog: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Drives the main screen. On startup it requests data from the backend API to
 * verify that it is reachable. When the request fails (for example because the
 * backend is not running) the UI is asked to show an error pop-up.
 */
class MainViewModel(
    private val fetchInitialData: () -> Unit = {
        // Lightweight request used to confirm the backend is reachable on startup.
        ScenesApi().searchSceneApiV1ScenesGet(limit = 1)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MainUiState())
    val uiState: StateFlow<MainUiState> = _uiState.asStateFlow()

    fun checkBackendAvailability() {
        _uiState.value = _uiState.value.copy(
            isLoading = true,
            showApiUnavailableDialog = false,
            errorMessage = null
        )

        viewModelScope.launch(ioDispatcher) {
            try {
                fetchInitialData()
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isApiAvailable = true,
                    showApiUnavailableDialog = false,
                    errorMessage = null
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isApiAvailable = false,
                    showApiUnavailableDialog = true,
                    errorMessage = e.message ?: "Backend API is not accessible"
                )
            }
        }
    }

    fun dismissApiUnavailableDialog() {
        _uiState.value = _uiState.value.copy(showApiUnavailableDialog = false)
    }
}
