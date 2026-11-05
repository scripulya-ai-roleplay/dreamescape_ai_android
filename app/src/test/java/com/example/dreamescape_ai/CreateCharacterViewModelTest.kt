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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openapitools.client.models.ApiResponseCharacter
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.ModelApiResponse
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CreateCharacterViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        onCreateCharacter: (Character) -> ModelApiResponse = { ModelApiResponse(result = "ok") }
    ): CreateCharacterViewModel {
        return CreateCharacterViewModel(
            createCharacterCall = onCreateCharacter,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state has empty fields`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals("", state.name)
        assertEquals("", state.systemPrompt)
        assertFalse(state.isPublic)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isSuccess)
    }

    @Test
    fun `onNameChanged updates name`() {
        val viewModel = createViewModel()

        viewModel.onNameChanged("Hero")

        assertEquals("Hero", viewModel.uiState.value.name)
    }

    @Test
    fun `onSystemPromptChanged updates systemPrompt`() {
        val viewModel = createViewModel()

        viewModel.onSystemPromptChanged("You are a brave hero")

        assertEquals("You are a brave hero", viewModel.uiState.value.systemPrompt)
    }

    @Test
    fun `onIsPublicChanged updates isPublic`() {
        val viewModel = createViewModel()

        viewModel.onIsPublicChanged(true)

        assertTrue(viewModel.uiState.value.isPublic)
    }

    @Test
    fun `validate returns error when name is blank`() {
        val viewModel = createViewModel()
        viewModel.onSystemPromptChanged("Some prompt")

        val error = viewModel.validate()

        assertEquals("Name is required", error)
    }

    @Test
    fun `validate returns error when systemPrompt is blank`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("Hero")

        val error = viewModel.validate()

        assertEquals("System prompt is required", error)
    }

    @Test
    fun `validate returns null when all fields are valid`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("Hero")
        viewModel.onSystemPromptChanged("You are a brave hero")

        val error = viewModel.validate()

        assertNull(error)
    }

    @Test
    fun `createCharacter sets error when name is blank`() {
        val viewModel = createViewModel()
        viewModel.onSystemPromptChanged("Some prompt")

        viewModel.createCharacter()

        assertEquals("Name is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createCharacter sets error when systemPrompt is blank`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("Hero")

        viewModel.createCharacter()

        assertEquals("System prompt is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onNameChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createCharacter()

        viewModel.onNameChanged("Hero")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onSystemPromptChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.onNameChanged("Hero")
        viewModel.createCharacter()

        viewModel.onSystemPromptChanged("prompt")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onIsPublicChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createCharacter()

        viewModel.onIsPublicChanged(true)

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createCharacter success sets isSuccess to true`() = runTest {
        val viewModel = createViewModel { ModelApiResponse(result = "created") }
        viewModel.onNameChanged("Hero")
        viewModel.onSystemPromptChanged("You are a brave hero")

        viewModel.createCharacter()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createCharacter failure sets error message`() = runTest {
        val viewModel = createViewModel { throw RuntimeException("Network error") }
        viewModel.onNameChanged("Hero")
        viewModel.onSystemPromptChanged("You are a brave hero")

        viewModel.createCharacter()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createCharacter trims name and systemPrompt`() = runTest {
        var capturedCharacter: Character? = null
        val viewModel = createViewModel { character ->
            capturedCharacter = character
            ModelApiResponse(result = "created")
        }
        viewModel.onNameChanged("  Hero  ")
        viewModel.onSystemPromptChanged("  You are a brave hero  ")
        viewModel.onIsPublicChanged(true)

        viewModel.createCharacter()
        advanceUntilIdle()

        assertEquals("Hero", capturedCharacter?.name)
        assertEquals("You are a brave hero", capturedCharacter?.systemPrompt)
        assertTrue(capturedCharacter?.isPublic == true)
    }

    private fun mediaAsset(id: UUID = UUID.randomUUID(), url: String = "http://minio/img.png") =
        MediaAssetDTO(
            id = id,
            url = url,
            contentType = "image/png",
            sizeBytes = 1,
            entityType = MediaEntityType.character,
            entityId = UUID.randomUUID(),
            isPublic = true
        )

    private fun editViewModel(
        editId: UUID = UUID.randomUUID(),
        media: List<MediaAssetDTO> = emptyList(),
        onDeleteMedia: (UUID) -> Unit = {},
        onUploadImage: (String) -> Unit = {}
    ): CreateCharacterViewModel {
        return CreateCharacterViewModel(
            editId = editId,
            updateCharacterCall = { _, _ -> ModelApiResponse(result = "ok") },
            getCharacterCall = {
                ApiResponseCharacter(result = Character(name = "Hero", systemPrompt = "You are a brave hero"))
            },
            listMediaCall = { ApiResponseListMediaAssetDTO(result = media) },
            deleteMediaCall = { id -> onDeleteMedia(id); ModelApiResponse(result = "ok") },
            uploadImage = { _, uri, _ -> onUploadImage(uri) },
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `edit mode loads existing media`() = runTest {
        val asset = mediaAsset()
        val viewModel = editViewModel(media = listOf(asset))
        advanceUntilIdle()

        assertEquals(listOf(asset), viewModel.uiState.value.existingMedia)
        assertEquals("Hero", viewModel.uiState.value.name)
    }

    @Test
    fun `removing an existing image deletes it only on save`() = runTest {
        val asset = mediaAsset()
        val deleted = mutableListOf<UUID>()
        val viewModel = editViewModel(media = listOf(asset), onDeleteMedia = { deleted.add(it) })
        advanceUntilIdle()

        viewModel.onImageRemoved(0)

        assertTrue(viewModel.uiState.value.existingMedia.isEmpty())
        assertTrue(deleted.isEmpty())

        viewModel.createCharacter()
        advanceUntilIdle()

        assertEquals(listOf(asset.id), deleted)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `update uploads only newly picked images`() = runTest {
        val asset = mediaAsset(url = "http://minio/existing.png")
        val uploaded = mutableListOf<String>()
        val viewModel = editViewModel(media = listOf(asset), onUploadImage = { uploaded.add(it) })
        advanceUntilIdle()

        viewModel.onImagesAdded(listOf("content://new-pick"))
        viewModel.createCharacter()
        advanceUntilIdle()

        assertEquals(listOf("content://new-pick"), uploaded)
        assertTrue(viewModel.uiState.value.isSuccess)
    }

    @Test
    fun `removal index after existing media removes the local pick`() = runTest {
        val asset = mediaAsset()
        val viewModel = editViewModel(media = listOf(asset))
        advanceUntilIdle()

        viewModel.onImagesAdded(listOf("content://new-pick"))
        viewModel.onImageRemoved(1)

        assertEquals(listOf(asset), viewModel.uiState.value.existingMedia)
        assertTrue(viewModel.uiState.value.imageUris.isEmpty())
    }
}
