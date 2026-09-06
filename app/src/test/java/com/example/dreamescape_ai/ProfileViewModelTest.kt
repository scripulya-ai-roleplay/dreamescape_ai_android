package com.example.dreamescape_ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.dreamescape_ai.data.PersonaSelection
import com.example.dreamescape_ai.data.PersonaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.openapitools.client.models.ApiResponseListMediaAssetDTO
import org.openapitools.client.models.ApiResponsePageCharacter
import org.openapitools.client.models.Character
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.PageCharacter
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ProfileViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val ownedCharacter = Character(
        name = "Kael",
        systemPrompt = "A bard.",
        id = UUID.fromString("00000000-0000-0000-0000-0000000000aa"),
        ownerId = testUserId
    )
    private val bookmarkedCharacter = Character(
        name = "Aria",
        systemPrompt = "A knight.",
        id = UUID.fromString("00000000-0000-0000-0000-0000000000bb")
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        // Robolectric reuses one application across tests, so the singleton
        // DataStore carries personas between them — reset to a clean slate.
        kotlinx.coroutines.runBlocking {
            PersonaStore.setPersona(
                ApplicationProvider.getApplicationContext(),
                PersonaSelection(characterId = null, characterName = null)
            )
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun pageOf(characters: List<Character>): ApiResponsePageCharacter =
        ApiResponsePageCharacter(
            result = PageCharacter(
                items = characters,
                count = characters.size,
                offset = 0,
                limit = 50
            )
        )

    @Test
    fun `loadCharacters merges owned and bookmarked deduped by id`() = runTest {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        var ownedQueried: List<UUID>? = null
        var bookmarkedQueried: List<UUID>? = null
        val viewModel = ProfileViewModel(
            appContext = appContext,
            searchOwnedCharactersCall = { ids ->
                ownedQueried = ids
                pageOf(listOf(ownedCharacter))
            },
            searchBookmarkedCharactersCall = { ids ->
                bookmarkedQueried = ids
                pageOf(listOf(bookmarkedCharacter, ownedCharacter))
            },
            characterImageCall = { ApiResponseListMediaAssetDTO(result = emptyList()) },
            userId = testUserId,
            ioDispatcher = testDispatcher
        )

        viewModel.loadCharacters()
        advanceUntilIdle()

        assertEquals(listOf(testUserId), ownedQueried)
        assertEquals(listOf(testUserId), bookmarkedQueried)
        assertTrue(viewModel.uiState.value.areCharactersLoaded)
        assertEquals(2, viewModel.uiState.value.characters.size)
        assertEquals(listOf("Aria", "Kael"), viewModel.uiState.value.characters.map { it.character.name })
    }

    @Test
    fun `loadCharacters sets error when both lookups fail`() = runTest {
        val viewModel = ProfileViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            searchOwnedCharactersCall = { throw RuntimeException("backend down") },
            searchBookmarkedCharactersCall = { throw RuntimeException("backend down") },
            characterImageCall = { ApiResponseListMediaAssetDTO(result = emptyList()) },
            userId = testUserId,
            ioDispatcher = testDispatcher
        )

        viewModel.loadCharacters()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.areCharactersLoaded)
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.characters.isEmpty())
    }

    @Test
    fun `selectPersona persists the selection through the store`() = runBlocking {
        // Real dispatcher: the write round-trips through DataStore's IO threads,
        // which a paused test scheduler would deadlock against runBlocking.
        val viewModel = ProfileViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            searchOwnedCharactersCall = { pageOf(emptyList()) },
            searchBookmarkedCharactersCall = { pageOf(emptyList()) },
            characterImageCall = { ApiResponseListMediaAssetDTO(result = emptyList()) },
            userId = testUserId,
            ioDispatcher = Dispatchers.Default
        )

        viewModel.selectPersona(ownedCharacter.id, ownedCharacter.name)

        withTimeout(5_000) {
            while (viewModel.uiState.value.selectedPersona.characterId == null) delay(50)
        }
        assertEquals(
            PersonaSelection(ownedCharacter.id, "Kael"),
            viewModel.uiState.value.selectedPersona
        )
    }

    @Test
    fun `selectPersona with null clears the selection`() = runBlocking {
        val viewModel = ProfileViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            searchOwnedCharactersCall = { pageOf(emptyList()) },
            searchBookmarkedCharactersCall = { pageOf(emptyList()) },
            characterImageCall = { ApiResponseListMediaAssetDTO(result = emptyList()) },
            userId = testUserId,
            ioDispatcher = Dispatchers.Default
        )

        viewModel.selectPersona(ownedCharacter.id, ownedCharacter.name)
        withTimeout(5_000) {
            while (viewModel.uiState.value.selectedPersona.characterId == null) delay(50)
        }

        viewModel.selectPersona(null, null)
        // Starting from the seen "Kael" state, a return to null proves the
        // clear actually wrote (a failed write would leave "Kael" in place).
        withTimeout(5_000) {
            while (viewModel.uiState.value.selectedPersona.hasPersona) delay(50)
        }
        assertNull(viewModel.uiState.value.selectedPersona.characterId)
        assertNull(viewModel.uiState.value.selectedPersona.characterName)
    }

    @Test
    fun `resolveImages updates the matching card`() = runTest {
        val portraitId = ownedCharacter.id!!
        val viewModel = ProfileViewModel(
            appContext = ApplicationProvider.getApplicationContext(),
            searchOwnedCharactersCall = { pageOf(listOf(ownedCharacter)) },
            searchBookmarkedCharactersCall = { pageOf(emptyList()) },
            characterImageCall = { id ->
                ApiResponseListMediaAssetDTO(
                    result = listOf(
                        MediaAssetDTO(
                            id = UUID.fromString("00000000-0000-0000-0000-0000000000ff"),
                            url = "http://example.com/p$id",
                            contentType = "image/png",
                            sizeBytes = 10,
                            entityType = MediaEntityType.character,
                            entityId = id,
                            isPublic = true,
                            sortOrder = 0,
                            caption = null,
                            layer = null
                        )
                    )
                )
            },
            userId = testUserId,
            ioDispatcher = testDispatcher
        )

        viewModel.loadCharacters()
        advanceUntilIdle()

        val card = viewModel.uiState.value.characters.first { it.character.id == portraitId }
        assertEquals("http://example.com/p$portraitId", card.imageUrl)
        assertTrue(card.imageResolved)
    }
}
