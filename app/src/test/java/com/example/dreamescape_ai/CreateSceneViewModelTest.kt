package com.example.dreamescape_ai

import com.example.dreamescape_ai.auth.SessionManager
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
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.Scene
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CreateSceneViewModelTest {

    private val testDispatcher = StandardTestDispatcher()
    private val testOwnerId = UUID.fromString("00000000-0000-0000-0000-000000000001")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        onCreateScene: (Scene) -> ModelApiResponse = { ModelApiResponse(result = "ok") }
    ): CreateSceneViewModel {
        return CreateSceneViewModel(
            createSceneCall = onCreateScene,
            ioDispatcher = testDispatcher,
            ownerId = testOwnerId
        )
    }

    @Test
    fun `initial state has empty fields`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals("", state.title)
        assertEquals("", state.description)
        assertEquals("", state.backgroundPrompt)
        assertEquals(listOf(""), state.initialMessages)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertFalse(state.isSuccess)
    }

    @Test
    fun `onTitleChanged updates title`() {
        val viewModel = createViewModel()

        viewModel.onTitleChanged("Dark Forest")

        assertEquals("Dark Forest", viewModel.uiState.value.title)
    }

    @Test
    fun `onDescriptionChanged updates description`() {
        val viewModel = createViewModel()

        viewModel.onDescriptionChanged("A mysterious forest")

        assertEquals("A mysterious forest", viewModel.uiState.value.description)
    }

    @Test
    fun `onBackgroundPromptChanged updates backgroundPrompt`() {
        val viewModel = createViewModel()

        viewModel.onBackgroundPromptChanged("You are in a dark forest")

        assertEquals("You are in a dark forest", viewModel.uiState.value.backgroundPrompt)
    }

    @Test
    fun `onInitialMessageChanged updates initialMessages`() {
        val viewModel = createViewModel()

        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        assertEquals(listOf("Welcome to the forest"), viewModel.uiState.value.initialMessages)
    }

    @Test
    fun `validate returns error when title is blank`() {
        val viewModel = createViewModel()
        viewModel.onBackgroundPromptChanged("Some prompt")
        viewModel.onInitialMessageChanged(0,"Some message")

        val error = viewModel.validate()

        assertEquals("Title is required", error)
    }

    @Test
    fun `validate returns error when backgroundPrompt is blank`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onInitialMessageChanged(0,"Some message")

        val error = viewModel.validate()

        assertEquals("Background prompt is required", error)
    }

    @Test
    fun `validate returns error when no initial message is set`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("Some prompt")

        val error = viewModel.validate()

        assertEquals("At least one initial message is required", error)
    }

    @Test
    fun `validate returns null when all required fields are valid`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("You are in a dark forest")
        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        val error = viewModel.validate()

        assertNull(error)
    }

    @Test
    fun `createScene sets error when title is blank`() {
        val viewModel = createViewModel()
        viewModel.onBackgroundPromptChanged("Some prompt")
        viewModel.onInitialMessageChanged(0,"Some message")

        viewModel.createScene()

        assertEquals("Title is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createScene sets error when backgroundPrompt is blank`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onInitialMessageChanged(0,"Some message")

        viewModel.createScene()

        assertEquals("Background prompt is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `createScene sets error when no initial message is set`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("Some prompt")

        viewModel.createScene()

        assertEquals("At least one initial message is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `onTitleChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createScene()

        viewModel.onTitleChanged("Dark Forest")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onDescriptionChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createScene()

        viewModel.onDescriptionChanged("A mysterious forest")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onBackgroundPromptChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createScene()

        viewModel.onBackgroundPromptChanged("Some prompt")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `onInitialMessageChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createScene()

        viewModel.onInitialMessageChanged(0,"Some message")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createScene success sets isSuccess to true`() = runTest {
        val viewModel = createViewModel { ModelApiResponse(result = "created") }
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("You are in a dark forest")
        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        viewModel.createScene()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createScene failure sets error message`() = runTest {
        val viewModel = createViewModel { throw RuntimeException("Network error") }
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("You are in a dark forest")
        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        viewModel.createScene()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isSuccess)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createScene trims fields and passes correct data`() = runTest {
        var capturedScene: Scene? = null
        val viewModel = createViewModel { scene ->
            capturedScene = scene
            ModelApiResponse(result = "created")
        }
        viewModel.onTitleChanged("  Dark Forest  ")
        viewModel.onDescriptionChanged("  A mysterious forest  ")
        viewModel.onBackgroundPromptChanged("  You are in a dark forest  ")
        viewModel.onInitialMessageChanged(0,"  Welcome to the forest  ")

        viewModel.createScene()
        advanceUntilIdle()

        assertEquals("Dark Forest", capturedScene?.title)
        assertEquals("A mysterious forest", capturedScene?.description)
        assertEquals("You are in a dark forest", capturedScene?.backgroundPrompt)
        assertEquals(
            listOf("Welcome to the forest"),
            capturedScene?.initialMessages?.map { it.text }
        )
        assertEquals(testOwnerId, capturedScene?.ownerId)
    }

    @Test
    fun `createScene uses the JWT issued user id as owner by default`() = runTest {
        // Regression test: the owner id must come from the JWT token's subject
        // (the authenticated user), not a randomly generated UUID.
        var capturedScene: Scene? = null
        val viewModel = CreateSceneViewModel(
            createSceneCall = { scene ->
                capturedScene = scene
                ModelApiResponse(result = "created")
            },
            ioDispatcher = testDispatcher
        )
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("You are in a dark forest")
        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        viewModel.createScene()
        advanceUntilIdle()

        assertEquals(SessionManager.DEFAULT_USER_ID, capturedScene?.ownerId)
    }

    @Test
    fun `createScene sends null description when empty`() = runTest {
        var capturedScene: Scene? = null
        val viewModel = createViewModel { scene ->
            capturedScene = scene
            ModelApiResponse(result = "created")
        }
        viewModel.onTitleChanged("Dark Forest")
        viewModel.onBackgroundPromptChanged("You are in a dark forest")
        viewModel.onInitialMessageChanged(0,"Welcome to the forest")

        viewModel.createScene()
        advanceUntilIdle()

        assertNull(capturedScene?.description)
    }
}
