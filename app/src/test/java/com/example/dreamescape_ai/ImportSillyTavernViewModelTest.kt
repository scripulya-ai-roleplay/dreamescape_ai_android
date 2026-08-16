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
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.openapitools.client.models.ApiResponseImportLorebookResultDTO
import org.openapitools.client.models.ApiResponseImportPreviewDTO
import org.openapitools.client.models.ApiResponsePageCharacter
import org.openapitools.client.models.ImportCandidateDTO
import org.openapitools.client.models.ImportLorebookResultDTO
import org.openapitools.client.models.ImportPreviewDTO
import org.openapitools.client.models.PageCharacter
import java.io.File
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ImportSillyTavernViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun previewResult(vararg candidates: ImportCandidateDTO): ApiResponseImportPreviewDTO {
        val characters = candidates.filter { it.group.equals("character", ignoreCase = true) }
        val scenes = candidates.filter { it.group.equals("location", ignoreCase = true) }
        return ApiResponseImportPreviewDTO(
            result = ImportPreviewDTO(
                characters = characters,
                scenes = scenes,
                otherEntries = 0,
                skippedEntries = 0,
                worldContextPreview = "",
            )
        )
    }

    private fun candidate(key: String, name: String, group: String): ImportCandidateDTO =
        ImportCandidateDTO(
            key = key, uid = null, name = name, group = group,
            contentPreview = "", contentLength = 10, imageCount = 0,
        )

    private fun characterPage(vararg names: Pair<UUID, String>): ApiResponsePageCharacter =
        ApiResponsePageCharacter(
            result = PageCharacter(
                items = names.map { (id, name) -> org.openapitools.client.models.Character(id = id, name = name, systemPrompt = "") },
                count = names.size, offset = 0, limit = 100,
            )
        )

    private fun createViewModel(
        preview: (File) -> ApiResponseImportPreviewDTO = { previewResult(candidate("0", "X", "character")) },
        importCall: (File, List<String>, Boolean, Boolean, Boolean, UUID?) -> ApiResponseImportLorebookResultDTO =
            { _, _, _, _, _, _ ->
                ApiResponseImportLorebookResultDTO(
                    result = ImportLorebookResultDTO(
                        charactersCreated = 1, scenesCreated = 0, imagesImported = 0,
                        imageFailures = emptyList(), characterIds = emptyList(),
                        sceneIds = emptyList(), skippedEntries = 0,
                    )
                )
            },
        searchCharacters: (List<UUID>, Int?, Int?) -> ApiResponsePageCharacter = { _, _, _ -> characterPage() },
    ): ImportSillyTavernViewModel = ImportSillyTavernViewModel(
        previewCall = preview,
        importCall = importCall,
        searchCharactersCall = searchCharacters,
        ownerId = UUID.randomUUID(),
        ioDispatcher = testDispatcher,
    )

    /** A lorebook file that passes the client-side SillyTavern guard. */
    private fun lorebookFile(): File =
        File.createTempFile("stbook", ".json").apply {
            writeText("""{"entries":{"0":{"comment":"x","content":"y","group":"character"}}}""")
        }

    @Test
    fun `attach targets are loaded after a successful preview`() = runTest(testDispatcher) {
        val targetId = UUID.randomUUID()
        val viewModel = createViewModel(
            searchCharacters = { owners, _, _ ->
                assertEquals(1, owners.size) // only the user's own characters
                characterPage(targetId to "Azua")
            }
        )
        viewModel.onFilePicked("book.json", lorebookFile())
        advanceUntilIdle()

        assertEquals(listOf(AttachTarget(targetId, "Azua")), viewModel.uiState.value.attachTargets)
    }

    @Test
    fun `selecting an attach target twice clears it`() = runTest(testDispatcher) {
        val target = AttachTarget(UUID.randomUUID(), "Azua")
        val viewModel = createViewModel()
        viewModel.selectAttachTarget(target)
        assertNotNull(viewModel.uiState.value.attachToCharacter)
        viewModel.selectAttachTarget(target)
        assertNull(viewModel.uiState.value.attachToCharacter)
    }

    @Test
    fun `attach import sends the character id and no keys needed`() = runTest(testDispatcher) {
        val targetId = UUID.randomUUID()
        var capturedId: UUID? = null
        var capturedKeys: List<String>? = null
        val viewModel = createViewModel(
            importCall = { _, keys, _, _, _, attachTo ->
                capturedKeys = keys
                capturedId = attachTo
                ApiResponseImportLorebookResultDTO(
                    result = ImportLorebookResultDTO(
                        charactersCreated = 0, scenesCreated = 0, imagesImported = 0,
                        imageFailures = emptyList(), characterIds = listOf(targetId),
                        sceneIds = emptyList(), skippedEntries = 0,
                        appendedToCharacterId = targetId,
                    )
                )
            }
        )
        viewModel.onFilePicked("book.json", lorebookFile())
        advanceUntilIdle()
        viewModel.selectAttachTarget(AttachTarget(targetId, "Azua"))
        viewModel.doImport()
        advanceUntilIdle()

        assertEquals(targetId, capturedId)
        // The previewed entry stays selected; the backend appends just it.
        // (No selection at all would append every entry.)
        assertEquals(listOf("0"), capturedKeys)
        assertEquals(ImportPhase.DONE, viewModel.uiState.value.phase)
    }

    @Test
    fun `import without selection or attach target is blocked`() = runTest(testDispatcher) {
        val viewModel = createViewModel()
        viewModel.onFilePicked("book.json", lorebookFile())
        advanceUntilIdle()
        viewModel.setAllCharacters(false)
        viewModel.setAllScenes(false)
        viewModel.doImport()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.errorMessage!!.contains("Select at least one"))
        assertFalse(viewModel.uiState.value.isLoading)
    }
}
