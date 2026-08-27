package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.auth.SessionManager
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.models.ApiResponseMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageMediaAssetDTO
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.ModelApiResponse
import java.io.File
import java.util.UUID

data class MediaGalleryUiState(
    val media: List<MediaAssetDTO> = emptyList(),
    val filter: MediaEntityType? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isUploading: Boolean = false,
    val uploadMessage: String? = null,
    val showUploadDialog: Boolean = false,
    val uploadEntityType: MediaEntityType = MediaEntityType.user,
    val uploadEntityId: String = "",
    val uploadIsPublic: Boolean = false,
    val pendingDelete: MediaAssetDTO? = null
)

class MediaGalleryViewModel(
    private val searchMediaCall: (MediaEntityType?, UUID?, Boolean?, Int?, Int?) -> ApiResponsePageMediaAssetDTO = { entityType, entityId, isPublic, limit, offset ->
        MediaApi().searchMediaApiV1MediaGet(entityType = entityType, entityId = entityId, isPublic = isPublic, limit = limit, offset = offset)
    },
    private val deleteMediaCall: (UUID) -> ModelApiResponse = { id ->
        MediaApi().deleteMediaApiV1MediaMediaIdDelete(id)
    },
    private val uploadMediaCall: (File, String, MediaEntityType, UUID, Boolean?) -> ApiResponseMediaAssetDTO = { file, mime, type, id, isPublic ->
        MediaUploader.upload(file, mime, type, id, isPublic)
    },
    private val defaultUserId: UUID = SessionManager.userId,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(MediaGalleryUiState())
    val uiState: StateFlow<MediaGalleryUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    fun setFilter(type: MediaEntityType?) {
        _uiState.value = _uiState.value.copy(filter = type)
        loadMedia()
    }

    fun loadMedia() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val response = searchMediaCall(_uiState.value.filter, null, null, 50, 0)
                _uiState.value = _uiState.value.copy(media = response.result.items, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load media"
                )
            }
        }
    }

    fun requestDelete(asset: MediaAssetDTO) {
        _uiState.value = _uiState.value.copy(pendingDelete = asset)
    }

    fun cancelDelete() {
        _uiState.value = _uiState.value.copy(pendingDelete = null)
    }

    fun confirmDelete() {
        val asset = _uiState.value.pendingDelete ?: return
        _uiState.value = _uiState.value.copy(pendingDelete = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                deleteMediaCall(asset.id)
                loadMedia()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(errorMessage = e.message ?: "Failed to delete media")
            }
        }
    }

    fun openUploadDialog() {
        val type = _uiState.value.uploadEntityType
        val entityId = if (type == MediaEntityType.user) defaultUserId.toString() else _uiState.value.uploadEntityId
        _uiState.value = _uiState.value.copy(showUploadDialog = true, uploadEntityId = entityId, uploadMessage = null)
    }

    fun dismissUploadDialog() {
        _uiState.value = _uiState.value.copy(showUploadDialog = false, uploadMessage = null)
    }

    fun setUploadEntityType(type: MediaEntityType) {
        // Prefill the entity id with the current user when uploading user media.
        val entityId = if (type == MediaEntityType.user) defaultUserId.toString() else ""
        _uiState.value = _uiState.value.copy(uploadEntityType = type, uploadEntityId = entityId)
    }

    fun setUploadEntityId(id: String) {
        _uiState.value = _uiState.value.copy(uploadEntityId = id)
    }

    fun setUploadIsPublic(value: Boolean) {
        _uiState.value = _uiState.value.copy(uploadIsPublic = value)
    }

    fun upload(file: File, mimeType: String) {
        val type = _uiState.value.uploadEntityType
        val entityId = runCatching { UUID.fromString(_uiState.value.uploadEntityId.trim()) }.getOrNull()
        if (entityId == null) {
            _uiState.value = _uiState.value.copy(uploadMessage = "Invalid entity id")
            return
        }
        val isPublic = _uiState.value.uploadIsPublic
        _uiState.value = _uiState.value.copy(isUploading = true, uploadMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                uploadMediaCall(file, mimeType, type, entityId, isPublic)
                _uiState.value = _uiState.value.copy(isUploading = false, showUploadDialog = false)
                loadMedia()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    uploadMessage = e.message ?: "Upload failed"
                )
            }
        }
    }
}
