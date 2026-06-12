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
import org.openapitools.client.models.ApiResponseMessage
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Message
import org.openapitools.client.models.PageMessage
import org.openapitools.client.models.UserMessageDTO
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testChatId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    private val testMessages = listOf(
        Message(
            message = "Hello",
            chatId = testChatId,
            role = ChatRoles.user,
            id = UUID.fromString("00000000-0000-0000-0000-000000000101")
        ),
        Message(
            message = "Hi, how can I help?",
            chatId = testChatId,
            role = ChatRoles.model,
            id = UUID.fromString("00000000-0000-0000-0000-000000000102")
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

    private fun createPage(messages: List<Message> = testMessages): ApiResponsePageMessage {
        return ApiResponsePageMessage(
            result = PageMessage(
                items = messages,
                count = messages.size,
                offset = 0,
                limit = 100
            )
        )
    }

    private fun messageResponse(text: String): ApiResponseMessage {
        return ApiResponseMessage(
            result = Message(
                message = text,
                chatId = testChatId,
                role = ChatRoles.user,
                id = UUID.randomUUID()
            )
        )
    }

    private fun createViewModel(
        onLoadMessages: (UUID) -> ApiResponsePageMessage = { createPage() },
        onSendMessage: (UserMessageDTO) -> ApiResponseMessage = { messageResponse(it.message) }
    ): ChatViewModel {
        return ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = onLoadMessages,
            sendMessageCall = onSendMessage,
            ioDispatcher = testDispatcher,
            llmModel = LLMModelType.testing_mock
        )
    }

    @Test
    fun `initial state is empty`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertTrue(state.messages.isEmpty())
        assertEquals("", state.input)
        assertFalse(state.isLoading)
        assertFalse(state.isSending)
        assertNull(state.errorMessage)
    }

    @Test
    fun `onInputChanged updates input`() {
        val viewModel = createViewModel()

        viewModel.onInputChanged("Hello there")

        assertEquals("Hello there", viewModel.uiState.value.input)
    }

    @Test
    fun `loadMessages fetches messages for the chat`() = runTest {
        var capturedChatId: UUID? = null
        val viewModel = createViewModel(onLoadMessages = { id ->
            capturedChatId = id
            createPage()
        })

        viewModel.loadMessages()
        advanceUntilIdle()

        assertEquals(testChatId, capturedChatId)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `loadMessages sorts messages chronologically by dateCreated`() = runTest {
        val older = OffsetDateTime.parse("2024-01-01T10:00:00Z")
        val middle = OffsetDateTime.parse("2024-01-01T11:00:00Z")
        val newer = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        val unordered = listOf(
            Message(
                message = "newest",
                chatId = testChatId,
                role = ChatRoles.model,
                id = UUID.fromString("00000000-0000-0000-0000-000000000201"),
                dateCreated = newer
            ),
            Message(
                message = "oldest",
                chatId = testChatId,
                role = ChatRoles.user,
                id = UUID.fromString("00000000-0000-0000-0000-000000000202"),
                dateCreated = older
            ),
            Message(
                message = "middle",
                chatId = testChatId,
                role = ChatRoles.model,
                id = UUID.fromString("00000000-0000-0000-0000-000000000203"),
                dateCreated = middle
            )
        )
        val viewModel = createViewModel(onLoadMessages = { createPage(unordered) })

        viewModel.loadMessages()
        advanceUntilIdle()

        assertEquals(
            listOf("oldest", "middle", "newest"),
            viewModel.uiState.value.messages.map { it.message }
        )
    }

    @Test
    fun `sendMessage returns messages sorted chronologically by dateCreated`() = runTest {
        val older = OffsetDateTime.parse("2024-01-01T10:00:00Z")
        val newer = OffsetDateTime.parse("2024-01-01T12:00:00Z")
        var messagesToReturn = emptyList<Message>()
        val viewModel = createViewModel(
            onLoadMessages = { createPage(messagesToReturn) },
            onSendMessage = { dto ->
                messagesToReturn = listOf(
                    Message(
                        message = "Reply",
                        chatId = dto.chatId,
                        role = ChatRoles.model,
                        id = UUID.randomUUID(),
                        dateCreated = newer
                    ),
                    Message(
                        message = dto.message,
                        chatId = dto.chatId,
                        role = ChatRoles.user,
                        id = UUID.randomUUID(),
                        dateCreated = older
                    )
                )
                messageResponse(dto.message)
            }
        )
        viewModel.onInputChanged("Hello")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(
            listOf("Hello", "Reply"),
            viewModel.uiState.value.messages.map { it.message }
        )
    }

    @Test
    fun `loadMessages sets error message on failure`() = runTest {
        val viewModel = createViewModel(onLoadMessages = { throw RuntimeException("Network error") })

        viewModel.loadMessages()
        advanceUntilIdle()

        assertEquals("Network error", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isLoading)
        assertTrue(viewModel.uiState.value.messages.isEmpty())
    }

    @Test
    fun `sendMessage does nothing when input is blank`() = runTest {
        var sendCalled = false
        val viewModel = createViewModel(onSendMessage = {
            sendCalled = true
            messageResponse(it.message)
        })
        viewModel.onInputChanged("   ")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertFalse(sendCalled)
    }

    @Test
    fun `sendMessage posts user message dto with trimmed text and reloads`() = runTest {
        var captured: UserMessageDTO? = null
        var messagesToReturn = emptyList<Message>()
        val viewModel = createViewModel(
            onLoadMessages = { createPage(messagesToReturn) },
            onSendMessage = { dto ->
                captured = dto
                messagesToReturn = listOf(
                    Message(message = dto.message, chatId = dto.chatId, role = ChatRoles.user, id = UUID.randomUUID()),
                    Message(message = "Reply", chatId = dto.chatId, role = ChatRoles.model, id = UUID.randomUUID())
                )
                messageResponse(dto.message)
            }
        )
        viewModel.onInputChanged("  Hello  ")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(testChatId, captured?.chatId)
        assertEquals("Hello", captured?.message)
        assertEquals(ChatRoles.user, captured?.role)
        assertEquals(LLMModelType.testing_mock, captured?.llmModel)
        assertEquals("", viewModel.uiState.value.input)
        assertEquals(2, viewModel.uiState.value.messages.size)
        assertFalse(viewModel.uiState.value.isSending)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `sendMessage sets error message on failure`() = runTest {
        val viewModel = createViewModel(onSendMessage = { throw RuntimeException("Send failed") })
        viewModel.onInputChanged("Hello")

        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals("Send failed", viewModel.uiState.value.errorMessage)
        assertFalse(viewModel.uiState.value.isSending)
    }
}
