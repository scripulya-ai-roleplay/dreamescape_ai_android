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
import org.openapitools.client.models.ApiResponsePageScene
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.PageScene
import org.openapitools.client.models.Scene
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SceneListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    private val testScenes = listOf(
        Scene(
            ownerId = testOwnerId,
            title = "Dark Forest",
            backgroundPrompt = "A dark forest",
            initialMessages = listOf(InitialMessage(text = "Welcome")),
            id = UUID.fromString("00000000-0000-0000-0000-000000000010"),
            description = "A mysterious forest"
        ),
        Scene(
            ownerId = testOwnerId,
            title = "Sunny Beach",
            backgroundPrompt = "A sunny beach",
            initialMessages = listOf(InitialMessage(text = "Hello")),
            id = UUID.fromString("00000000-0000-0000-0000-000000000011"),
            description = null
        )
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createResponse(scenes: List<Scene> = testScenes): ApiResponsePageScene {
        return ApiResponsePageScene(
            result = PageScene(
                items = scenes,
                count = scenes.size,
                offset = 0,
                limit = 50
            )
        )
    }

    private fun createViewModel(
        onSearchScenes: (List<String>?, Int?, Int?) -> ApiResponsePageScene = { _, _, _ ->
            createResponse()
        }
    ): SceneListViewModel {
        return SceneListViewModel(
            searchScenesCall = onSearchScenes,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state has empty fields`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(emptyList<Scene>(), state.scenes)
        assertEquals("", state.searchQuery)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadScenes fetches all scenes`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadScenes()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.scenes.size)
        assertEquals("Dark Forest", state.scenes[0].title)
        assertEquals("Sunny Beach", state.scenes[1].title)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadScenes passes null title when search query is empty`() = runTest {
        var capturedTitle: List<String>? = listOf("should be replaced")
        val viewModel = createViewModel { title, _, _ ->
            capturedTitle = title
            createResponse()
        }

        viewModel.loadScenes()
        advanceUntilIdle()

        assertNull(capturedTitle)
    }

    @Test
    fun `onSearchQueryChanged updates query and triggers load`() = runTest {
        var capturedTitle: List<String>? = null
        val viewModel = createViewModel { title, _, _ ->
            capturedTitle = title
            createResponse()
        }

        viewModel.onSearchQueryChanged("Dark")
        advanceUntilIdle()

        assertEquals("Dark", viewModel.uiState.value.searchQuery)
        assertEquals(listOf("Dark"), capturedTitle)
    }

    @Test
    fun `onSearchQueryChanged with blank query passes null title`() = runTest {
        var capturedTitle: List<String>? = listOf("should be replaced")
        val viewModel = createViewModel { title, _, _ ->
            capturedTitle = title
            createResponse()
        }

        viewModel.onSearchQueryChanged("   ")
        advanceUntilIdle()

        assertNull(capturedTitle)
    }

    @Test
    fun `loadScenes sets error message on failure`() = runTest {
        val viewModel = createViewModel { _, _, _ ->
            throw RuntimeException("Network error")
        }

        viewModel.loadScenes()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network error", state.errorMessage)
        assertFalse(state.isLoading)
        assertTrue(state.scenes.isEmpty())
    }

    @Test
    fun `loadScenes clears error on new request`() = runTest {
        var shouldFail = true
        val viewModel = createViewModel { _, _, _ ->
            if (shouldFail) throw RuntimeException("Network error")
            createResponse()
        }

        viewModel.loadScenes()
        advanceUntilIdle()
        assertEquals("Network error", viewModel.uiState.value.errorMessage)

        shouldFail = false
        viewModel.loadScenes()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.scenes.size)
    }

    @Test
    fun `loadScenes returns empty list when no scenes match`() = runTest {
        val viewModel = createViewModel { _, _, _ ->
            createResponse(emptyList())
        }

        viewModel.loadScenes()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.scenes.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }
}
