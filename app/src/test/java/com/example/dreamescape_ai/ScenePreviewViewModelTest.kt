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
import org.openapitools.client.models.ApiResponseListCharacter
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponseScene
import org.openapitools.client.models.Character
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.MediaLayer
import org.openapitools.client.models.Scene
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ScenePreviewViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val sceneId = UUID.fromString("00000000-0000-0000-0000-0000000000a1")
    private val ownerId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val firstCharacterId = UUID.fromString("00000000-0000-0000-0000-0000000000c1")
    private val secondCharacterId = UUID.fromString("00000000-0000-0000-0000-0000000000c2")

    private val testScene = Scene(
        id = sceneId,
        ownerId = ownerId,
        title = "Test Scene",
        backgroundPrompt = "prompt",
        initialMessages = listOf(InitialMessage(text = "hello"))
    )

    private fun media(
        id: Int,
        entityId: UUID,
        layer: MediaLayer? = MediaLayer.background,
        url: String = "http://example.com/$id.png"
    ) = MediaAssetDTO(
        id = UUID.fromString("00000000-0000-0000-0000-0000000000$id"),
        url = url,
        contentType = "image/png",
        sizeBytes = 10,
        entityType = MediaEntityType.scene,
        entityId = entityId,
        isPublic = true,
        sortOrder = 0,
        caption = null,
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
        sceneMedia: List<MediaAssetDTO> = listOf(media(1, sceneId), media(2, sceneId)),
        firstCharacterMedia: List<MediaAssetDTO> = emptyList(),
        secondCharacterMedia: List<MediaAssetDTO> = emptyList(),
        characters: List<Character> = listOf(
            Character(name = "First", systemPrompt = "p", id = firstCharacterId, ownerId = ownerId),
            Character(name = "Second", systemPrompt = "p", id = secondCharacterId, ownerId = ownerId)
        )
    ): ScenePreviewViewModel {
        val mediaByCharacter = mapOf(
            firstCharacterId to firstCharacterMedia,
            secondCharacterId to secondCharacterMedia
        )
        return ScenePreviewViewModel(
            sceneId = sceneId,
            getSceneCall = { ApiResponseScene(result = testScene) },
            sceneImageCall = { _ -> ApiResponseListMediaAssetDTO(result = sceneMedia) },
            getSceneCharactersCall = { _ -> ApiResponseListCharacter(result = characters) },
            characterImageCall = { characterId ->
                ApiResponseListMediaAssetDTO(result = mediaByCharacter[characterId] ?: emptyList())
            },
            userId = ownerId,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `hero image prefers the first background-layer asset`() = runTest {
        val sceneMedia = listOf(
            media(1, sceneId, layer = MediaLayer.foreground),
            media(2, sceneId, layer = MediaLayer.background),
            media(3, sceneId, layer = MediaLayer.background)
        )
        val viewModel = createViewModel(sceneMedia = sceneMedia)
        advanceUntilIdle()

        assertEquals("http://example.com/2.png", viewModel.uiState.value.heroImageUrl)
        assertTrue(viewModel.uiState.value.heroImageResolved)
    }

    @Test
    fun `hero image falls back to the first asset when only foreground exists`() = runTest {
        val sceneMedia = listOf(media(1, sceneId, layer = MediaLayer.foreground))
        val viewModel = createViewModel(sceneMedia = sceneMedia)
        advanceUntilIdle()

        assertEquals("http://example.com/1.png", viewModel.uiState.value.heroImageUrl)
    }

    @Test
    fun `hero image falls back to first asset for legacy null layers`() = runTest {
        // Pre-layering clients may deserialize without a layer; null means
        // background-era data, so any asset is acceptable as the hero.
        val sceneMedia = listOf(media(1, sceneId, layer = null))
        val viewModel = createViewModel(sceneMedia = sceneMedia)
        advanceUntilIdle()

        assertEquals("http://example.com/1.png", viewModel.uiState.value.heroImageUrl)
    }

    @Test
    fun `foreground overlay uses the first attached character's first foreground asset`() = runTest {
        val viewModel = createViewModel(
            firstCharacterMedia = listOf(
                media(11, firstCharacterId, layer = MediaLayer.background),
                media(12, firstCharacterId, layer = MediaLayer.foreground)
            ),
            secondCharacterMedia = listOf(media(21, secondCharacterId, layer = MediaLayer.foreground))
        )
        advanceUntilIdle()

        assertEquals("http://example.com/12.png", viewModel.uiState.value.foregroundImageUrl)
        assertTrue(viewModel.uiState.value.foregroundResolved)
    }

    @Test
    fun `foreground overlay is null when the first character has no foreground asset`() = runTest {
        val viewModel = createViewModel(
            firstCharacterMedia = listOf(media(11, firstCharacterId, layer = MediaLayer.background)),
            secondCharacterMedia = listOf(media(21, secondCharacterId, layer = MediaLayer.foreground))
        )
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.foregroundImageUrl)
    }

    @Test
    fun `foreground overlay is null when the scene has no characters`() = runTest {
        val viewModel = createViewModel(characters = emptyList())
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.foregroundImageUrl)
        assertTrue(viewModel.uiState.value.foregroundResolved)
    }

    @Test
    fun `owner flag follows the scene owner`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isOwner)
    }

    @Test
    fun `non-owner sees no owner flag`() = runTest {
        val otherUser = UUID.fromString("99999999-9999-9999-9999-999999999999")
        val viewModel = ScenePreviewViewModel(
            sceneId = sceneId,
            getSceneCall = { ApiResponseScene(result = testScene) },
            sceneImageCall = { _ -> ApiResponseListMediaAssetDTO(result = emptyList()) },
            getSceneCharactersCall = { _ -> ApiResponseListCharacter(result = emptyList()) },
            characterImageCall = { _ -> ApiResponseListMediaAssetDTO(result = emptyList()) },
            userId = otherUser,
            ioDispatcher = testDispatcher
        )
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isOwner)
    }
}
