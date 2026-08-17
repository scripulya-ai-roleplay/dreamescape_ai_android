package com.example.dreamescape_ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponseMediaAssetDTO
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.MediaLayer
import org.openapitools.client.models.MediaUpdateDTO
import org.openapitools.client.models.ModelApiResponse
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class EntityMediaManagerViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val entityId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    private fun assetId(id: Int): UUID =
        UUID.fromString(String.format("00000000-0000-0000-0000-%012d", id))

    private fun asset(
        id: Int,
        sortOrder: Int? = 0,
        caption: String? = null,
        layer: MediaLayer? = MediaLayer.background
    ) = MediaAssetDTO(
        id = assetId(id),
        url = "http://example.com/$id.png",
        contentType = "image/png",
        sizeBytes = 10,
        entityType = MediaEntityType.scene,
        entityId = entityId,
        isPublic = true,
        sortOrder = sortOrder,
        caption = caption,
        layer = layer
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        media: List<MediaAssetDTO> = listOf(asset(1), asset(2), asset(3)),
        updates: MutableList<Pair<UUID, MediaUpdateDTO>> = mutableListOf()
    ): EntityMediaManagerViewModel {
        var current = media
        return EntityMediaManagerViewModel(
            entityType = MediaEntityType.scene,
            entityId = entityId,
            // Like the backend, the listing comes back ordered by sort_order.
            loadMediaCall = { _, _ -> ApiResponseListMediaAssetDTO(result = current.sortedBy { it.sortOrder ?: 0 }) },
            deleteMediaCall = { id ->
                current = current.filterNot { it.id == id }
                ModelApiResponse(result = "ok")
            },
            updateMediaCall = { id, dto ->
                updates += id to dto
                // Mirror the server: apply the patch to the in-memory list.
                current = current.map {
                    if (it.id == id) it.copy(
                        sortOrder = dto.sortOrder ?: it.sortOrder,
                        caption = dto.caption ?: it.caption,
                        layer = dto.layer ?: it.layer
                    ) else it
                }
                ApiResponseMediaAssetDTO(result = current.first { it.id == id })
            },
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `loadMedia populates the list in server order`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(
            listOf(assetId(1), assetId(2), assetId(3)),
            viewModel.uiState.value.media.map { it.id }
        )
    }

    @Test
    fun `delete flow requests confirmation then deletes and reloads`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        val target = viewModel.uiState.value.media.first()
        viewModel.requestDelete(target)
        assertEquals(target.id, viewModel.uiState.value.pendingDelete?.id)

        viewModel.cancelDelete()
        assertNull(viewModel.uiState.value.pendingDelete)

        viewModel.requestDelete(target)
        viewModel.confirmDelete()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.pendingDelete)
        assertEquals(2, viewModel.uiState.value.media.size)
    }

    @Test
    fun `openEditor seeds draft and saveEditor patches caption and layer`() = runTest {
        val updates = mutableListOf<Pair<UUID, MediaUpdateDTO>>()
        val viewModel = createViewModel(updates = updates)
        advanceUntilIdle()

        val second = viewModel.uiState.value.media[1]
        viewModel.openEditor(second)
        assertEquals(second.id, viewModel.uiState.value.editorDraft?.asset?.id)
        // A null caption seeds the text field as an empty string.
        assertEquals("", viewModel.uiState.value.editorDraft?.caption)
        assertEquals(MediaLayer.background, viewModel.uiState.value.editorDraft?.layer)

        viewModel.updateEditorCaption("a caption")
        viewModel.updateEditorLayer(MediaLayer.foreground)
        viewModel.saveEditor()
        advanceUntilIdle()

        assertEquals(1, updates.size)
        val (patchedId, dto) = updates.single()
        assertEquals(second.id, patchedId)
        assertEquals("a caption", dto.caption)
        assertEquals(MediaLayer.foreground, dto.layer)
        assertNull(viewModel.uiState.value.editorDraft)
    }

    @Test
    fun `editor caption rejects input beyond 200 characters`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.openEditor(viewModel.uiState.value.media.first())
        viewModel.updateEditorCaption("x".repeat(200))
        assertEquals(200, viewModel.uiState.value.editorDraft?.caption?.length)

        viewModel.updateEditorCaption("x".repeat(201))
        assertEquals(200, viewModel.uiState.value.editorDraft?.caption?.length)
    }

    @Test
    fun `moveUp on the first asset is a no-op`() = runTest {
        val updates = mutableListOf<Pair<UUID, MediaUpdateDTO>>()
        val viewModel = createViewModel(updates = updates)
        advanceUntilIdle()

        viewModel.moveUp(viewModel.uiState.value.media.first())
        advanceUntilIdle()

        assertTrue(updates.isEmpty())
    }

    @Test
    fun `moveDown renumbers and patches only assets whose order changed`() = runTest {
        // Legacy list: every asset carries sort_order=0, so the first move
        // must establish the full 0..n-1 numbering.
        val updates = mutableListOf<Pair<UUID, MediaUpdateDTO>>()
        val media = listOf(asset(1, sortOrder = 0), asset(2, sortOrder = 0), asset(3, sortOrder = 0))
        val viewModel = createViewModel(media = media, updates = updates)
        advanceUntilIdle()

        viewModel.moveDown(viewModel.uiState.value.media[0]) // 1,2,3 -> 2,1,3
        advanceUntilIdle()

        // The asset landing at index 0 already carries sort_order=0, so only
        // the other two need a patch.
        assertEquals(2, updates.size)
        // …and afterwards a follow-up move touches at most two.
        updates.clear()
        viewModel.moveDown(viewModel.uiState.value.media[0])
        advanceUntilIdle()
        assertTrue(updates.size <= 2)
    }

    @Test
    fun `subsequent move swaps exactly two sort orders`() = runTest {
        // Fully renumbered list already at 0..n-1.
        val updates = mutableListOf<Pair<UUID, MediaUpdateDTO>>()
        val media = listOf(asset(1, sortOrder = 0), asset(2, sortOrder = 1), asset(3, sortOrder = 2))
        val viewModel = createViewModel(media = media, updates = updates)
        advanceUntilIdle()

        viewModel.moveUp(viewModel.uiState.value.media[2]) // 1,2,3 -> 1,3,2
        advanceUntilIdle()

        assertEquals(2, updates.size)
        // Asset originally at index 2 (id 3) moves to 1; asset at index 1 (id 2) moves to 2.
        val expected = mapOf(assetId(3) to 1, assetId(2) to 2)
        assertEquals(expected, updates.associate { (id, dto) -> id to dto.sortOrder })
    }

    @Test
    fun `upload sends uris reversed so the first picked lands first`() = runTest {
        val uploaded = mutableListOf<String>()
        var current = listOf<MediaAssetDTO>()
        val viewModel = EntityMediaManagerViewModel(
            entityType = MediaEntityType.scene,
            entityId = entityId,
            loadMediaCall = { _, _ -> ApiResponseListMediaAssetDTO(result = current) },
            deleteMediaCall = { ModelApiResponse(result = "ok") },
            updateMediaCall = { _, _ -> throw IllegalStateException("no patch expected") },
            uploadMediaCall = { _, _, uri ->
                uploaded += uri
                current = current + asset(current.size + 1)
                ApiResponseMediaAssetDTO(result = current.last())
            },
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        viewModel.upload(listOf("uri-a", "uri-b", "uri-c"))
        advanceUntilIdle()

        assertEquals(listOf("uri-c", "uri-b", "uri-a"), uploaded)
    }
}
