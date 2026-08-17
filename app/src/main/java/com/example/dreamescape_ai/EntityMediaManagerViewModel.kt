package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.MediaApi
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponseMediaAssetDTO
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.MediaLayer
import org.openapitools.client.models.MediaUpdateDTO
import org.openapitools.client.models.ModelApiResponse
import java.util.UUID

/** Draft state inside the per-image editor dialog. */
data class MediaEditorDraft(
    val asset: MediaAssetDTO,
    val caption: String,
    val layer: MediaLayer
)

data class EntityMediaUiState(
    val media: List<MediaAssetDTO> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val pendingDelete: MediaAssetDTO? = null,
    // Non-null while the per-image editor dialog is open.
    val editorDraft: MediaEditorDraft? = null,
    val isUploading: Boolean = false
)

/**
 * Manages the images attached to one character or scene: view/reorder them,
 * annotate each with a short caption, mark them background/foreground, add
 * more, or delete them.
 *
 * Ordering: the backend orders an entity's media by (sort_order, newest
 * first). Fresh uploads have sort_order=0 so they keep landing newest-first —
 * which is why [upload] sends the images in reverse (the first picked image
 * must end up first in the list). A reorder renumbers the whole list 0..n-1
 * and PATCHes every asset whose index actually changed.
 */
class EntityMediaManagerViewModel(
    private val entityType: MediaEntityType,
    private val entityId: UUID,
    private val loadMediaCall: (MediaEntityType, UUID) -> ApiResponseListMediaAssetDTO = { type, id ->
        MediaApi().getMediaForEntityApiV1MediaEntityEntityTypeEntityIdGet(
            entityType = type, entityId = id
        )
    },
    private val deleteMediaCall: (UUID) -> ModelApiResponse = { id ->
        MediaApi().deleteMediaApiV1MediaMediaIdDelete(id)
    },
    private val updateMediaCall: (UUID, MediaUpdateDTO) -> ApiResponseMediaAssetDTO = { id, dto ->
        MediaApi().updateMediaApiV1MediaMediaIdPatch(mediaId = id, mediaUpdateDTO = dto)
    },
    private val uploadMediaCall: (MediaEntityType, UUID, String) -> ApiResponseMediaAssetDTO =
        { type, id, uri ->
            // Only reached from the Activity, which supplies a context-aware upload.
            throw UnsupportedOperationException("uploadMediaCall must be injected by the Activity")
        },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(EntityMediaUiState(isLoading = true))
    val uiState: StateFlow<EntityMediaUiState> = _uiState.asStateFlow()

    init {
        loadMedia()
    }

    fun loadMedia() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val media = loadMediaCall(entityType, entityId).result
                _uiState.value = _uiState.value.copy(media = media, isLoading = false)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load images"
                )
            }
        }
    }

    // ── Delete (popup menu → confirm dialog) ────────────────────────────────────

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
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to delete image"
                )
            }
        }
    }

    // ── Editor (popup menu "View" → dialog) ─────────────────────────────────────

    fun openEditor(asset: MediaAssetDTO) {
        _uiState.value = _uiState.value.copy(
            editorDraft = MediaEditorDraft(
                asset = asset,
                caption = asset.caption.orEmpty(),
                layer = asset.layer ?: MediaLayer.background
            )
        )
    }

    /** Rejects input beyond the 200-character caption limit. */
    fun updateEditorCaption(text: String) {
        val draft = _uiState.value.editorDraft ?: return
        if (text.length > CAPTION_MAX_LENGTH) return
        _uiState.value = _uiState.value.copy(editorDraft = draft.copy(caption = text))
    }

    fun updateEditorLayer(layer: MediaLayer) {
        val draft = _uiState.value.editorDraft ?: return
        _uiState.value = _uiState.value.copy(editorDraft = draft.copy(layer = layer))
    }

    fun closeEditor() {
        _uiState.value = _uiState.value.copy(editorDraft = null)
    }

    /** Saves caption + layer from the editor; a failure keeps the dialog open. */
    fun saveEditor() {
        val draft = _uiState.value.editorDraft ?: return
        viewModelScope.launch(ioDispatcher) {
            try {
                updateMediaCall(
                    draft.asset.id,
                    MediaUpdateDTO(
                        caption = draft.caption,
                        layer = draft.layer,
                        // Keep the asset's current slot when saving from the editor.
                        sortOrder = draft.asset.sortOrder ?: 0
                    )
                )
                _uiState.value = _uiState.value.copy(editorDraft = null)
                loadMedia()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = e.message ?: "Failed to save image"
                )
            }
        }
    }

    // ── Reorder (editor "Move up"/"Move down") ──────────────────────────────────

    /** Moves [asset] one slot towards the start of the list; a no-op at index 0. */
    fun moveUp(asset: MediaAssetDTO) = move(asset, -1)

    /** Moves [asset] one slot towards the end of the list; a no-op at the end. */
    fun moveDown(asset: MediaAssetDTO) = move(asset, +1)

    private fun move(asset: MediaAssetDTO, delta: Int) {
        val media = _uiState.value.media
        val index = media.indexOfFirst { it.id == asset.id }
        val target = index + delta
        if (index == -1 || target < 0 || target >= media.size) return

        val swapped = media.toMutableList()
        val tmp = swapped[index]
        swapped[index] = swapped[target]
        swapped[target] = tmp
        applyOrder(swapped)
    }

    /**
     * Renumbers [ordered] to 0..n-1 and PATCHes every asset whose sort_order
     * actually changed. The first reorder of a legacy list (all sort_order=0)
     * may touch every asset; afterwards a single move touches at most two.
     */
    private fun applyOrder(ordered: List<MediaAssetDTO>) {
        viewModelScope.launch(ioDispatcher) {
            // Optimistic local update so the grid reacts instantly.
            _uiState.value = _uiState.value.copy(media = ordered)
            var failure: Exception? = null
            for ((position, item) in ordered.withIndex()) {
                if ((item.sortOrder ?: 0) == position) continue
                try {
                    val updated = updateMediaCall(
                        item.id,
                        MediaUpdateDTO(sortOrder = position)
                    ).result
                    // Track what the server accepted so a mid-sequence failure
                    // still leaves the visible list consistent with the DB.
                    _uiState.value = _uiState.value.copy(
                        media = _uiState.value.media.map { if (it.id == updated.id) updated else it }
                    )
                } catch (e: Exception) {
                    failure = e
                    break
                }
            }
            if (failure != null) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = failure?.message ?: "Failed to reorder images"
                )
            }
            // Re-sync with the authoritative order (and fresh presigned URLs).
            loadMedia()
        }
    }

    // ── Add images ──────────────────────────────────────────────────────────────

    companion object {
        const val CAPTION_MAX_LENGTH = 200
    }

    /**
     * Uploads [uris] (photo-picker results) in reverse: the backend places
     * fresh uploads (sort_order=0) newest-first, so the first picked image
     * must be uploaded last to end up first in the gallery.
     */
    fun upload(uris: List<String>) {
        if (uris.isEmpty()) return
        if (_uiState.value.isUploading) return
        _uiState.value = _uiState.value.copy(isUploading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                for (uri in uris.asReversed()) {
                    uploadMediaCall(entityType, entityId, uri)
                }
                _uiState.value = _uiState.value.copy(isUploading = false)
                loadMedia()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isUploading = false,
                    errorMessage = e.message ?: "Failed to upload images"
                )
                loadMedia()
            }
        }
    }
}
