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
import org.junit.Before
import org.junit.Test
import org.openapitools.client.models.Chat
import org.openapitools.client.models.ModelApiResponse
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CreateChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testSceneId = UUID.fromString("00000000-0000-0000-0000-000000000010")
    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val fixedChatId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        onCreateChat: (Chat) -> ModelApiResponse = { ModelApiResponse(result = "ok") }
    ): CreateChatViewModel {
        return CreateChatViewModel(
            sceneId = testSceneId,
            createChatCall = onCreateChat,
            ioDispatcher = testDispatcher,
            userId = testUserId,
            chatIdProvider = { fixedChatId }
        )
    }

    @Test
    fun `initial state has empty fields`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals("", state.title)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
        assertNull(state.createdChatId)
    }

    @Test
    fun `onTitleChanged updates title`() {
        val viewModel = createViewModel()

        viewModel.onTitleChanged("My Chat")

        assertEquals("My Chat", viewModel.uiState.value.title)
    }

    @Test
    fun `onTitleChanged clears error message`() {
        val viewModel = createViewModel()
        viewModel.createChat()
        assertEquals("Title is required", viewModel.uiState.value.errorMessage)

        viewModel.onTitleChanged("My Chat")

        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `validate returns error when title is blank`() {
        val viewModel = createViewModel()

        assertEquals("Title is required", viewModel.validate())
    }

    @Test
    fun `validate returns null when title is provided`() {
        val viewModel = createViewModel()
        viewModel.onTitleChanged("My Chat")

        assertNull(viewModel.validate())
    }

    @Test
    fun `createChat sets error when title is blank`() {
        val viewModel = createViewModel()

        viewModel.createChat()

        assertEquals("Title is required", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.createdChatId)
    }

    @Test
    fun `createChat success exposes created chat id`() = runTest {
        // The backend returns its own server-generated id in `result.id`; the app
        // must navigate with that id, not the client-generated one.
        val viewModel = createViewModel {
            ModelApiResponse(result = mapOf("id" to fixedChatId.toString()))
        }
        viewModel.onTitleChanged("My Chat")

        viewModel.createChat()
        advanceUntilIdle()

        assertEquals(fixedChatId, viewModel.uiState.value.createdChatId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createChat failure sets error message`() = runTest {
        val viewModel = createViewModel { throw RuntimeException("Network error") }
        viewModel.onTitleChanged("My Chat")

        viewModel.createChat()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.createdChatId)
        assertFalse(viewModel.uiState.value.isLoading)
        assertEquals("Network error", viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `createChat builds chat with trimmed title, scene, user and generated id`() = runTest {
        var capturedChat: Chat? = null
        val viewModel = createViewModel { chat ->
            capturedChat = chat
            ModelApiResponse(result = "created")
        }
        viewModel.onTitleChanged("  My Chat  ")

        viewModel.createChat()
        advanceUntilIdle()

        assertEquals("My Chat", capturedChat?.title)
        assertEquals(testSceneId, capturedChat?.sceneId)
        assertEquals(testUserId, capturedChat?.userId)
        assertEquals(fixedChatId, capturedChat?.id)
    }
}
