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
import org.openapitools.client.models.Character
import org.openapitools.client.models.ModelApiResponse

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
}
