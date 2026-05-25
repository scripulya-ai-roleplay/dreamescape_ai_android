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
import org.openapitools.client.models.ApiResponsePageScene
import org.openapitools.client.models.Scene

data class SceneListUiState(
    val scenes: List<Scene> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null
)

class SceneListViewModel(
    private val searchScenesCall: (title: List<String>?, offset: Int?, limit: Int?) -> ApiResponsePageScene = { title, offset, limit ->
        ScenesApi().searchSceneApiV1ScenesGet(title = title, offset = offset, limit = limit)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(SceneListUiState())
    val uiState: StateFlow<SceneListUiState> = _uiState.asStateFlow()

    fun onSearchQueryChanged(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
        loadScenes()
    }

    fun loadScenes() {
        val query = _uiState.value.searchQuery.trim()
        val titleFilter = if (query.isNotEmpty()) listOf(query) else null

        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch(ioDispatcher) {
            try {
                val response = searchScenesCall(titleFilter, 0, 50)
                _uiState.value = _uiState.value.copy(
                    scenes = response.result.items,
                    isLoading = false
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load scenes"
                )
            }
        }
    }
}
