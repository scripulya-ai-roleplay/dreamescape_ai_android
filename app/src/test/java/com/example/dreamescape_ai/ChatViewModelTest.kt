package com.example.dreamescape_ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.flowOf
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
import org.json.JSONObject
import com.example.dreamescape_ai.ChatViewModel.StreamAction
import org.openapitools.client.models.ApiResponseCharacter
import org.openapitools.client.models.ApiResponseChat
import org.openapitools.client.models.ApiResponseListInitialMessage
import org.openapitools.client.models.ApiResponseMessage
import org.openapitools.client.models.ApiResponsePageMessage
import org.openapitools.client.models.Chat
import org.openapitools.client.models.Character
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.InitialMessage
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Message
import org.openapitools.client.models.ModelApiResponse
import org.openapitools.client.models.PageMessage
import org.openapitools.client.models.SendMessageRequest
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
        onSendMessage: (SendMessageRequest) -> ApiResponseMessage = { messageResponse(it.message) }
    ): ChatViewModel {
        return ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = onLoadMessages,
            sendMessageCall = onSendMessage,
            ioDispatcher = testDispatcher,
            modelFlow = flowOf(LLMModelType.testing_mock),
            waitForReply = { _, _ -> }
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
        var captured: SendMessageRequest? = null
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

    @Test
    fun `sendMessage seeds the displayed greeting before sending and clears the gate`() = runTest {
        val greetingOne = UUID.fromString("00000000-0000-0000-0000-000000000b01")
        val greetingTwo = UUID.fromString("00000000-0000-0000-0000-000000000b02")
        val greetings = listOf(
            InitialMessage(text = "Greeting one", id = greetingOne),
            InitialMessage(text = "Greeting two", id = greetingTwo)
        )

        var seededId: UUID? = null
        var sentDto: SendMessageRequest? = null
        val viewModel = ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = { createPage(emptyList()) },
            sendMessageCall = { dto ->
                sentDto = dto
                messageResponse(dto.message)
            },
            // A chat whose greeting is not yet chosen → needsInitialMessage.
            getChatCall = {
                ApiResponseChat(
                    result = Chat(
                        title = "Chat",
                        userId = UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        sceneId = UUID.fromString("00000000-0000-0000-0000-0000000000dd"),
                        id = testChatId,
                        initialMessageId = null
                    )
                )
            },
            // Avoid hitting the network from a unit test; loadChat tolerates the failure.
            sceneImageCall = { throw RuntimeException("no network in tests") },
            getSceneInitialMessagesCall = { ApiResponseListInitialMessage(result = greetings) },
            chooseInitialMessageCall = { _, id ->
                seededId = id
                ModelApiResponse(result = null)
            },
            ioDispatcher = testDispatcher,
            modelFlow = flowOf(LLMModelType.testing_mock),
            waitForReply = { _, _ -> }
        )

        viewModel.loadChat()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.needsInitialMessage)
        assertEquals(0, viewModel.uiState.value.currentInitialMessageIndex)

        // Browse to the second greeting — the one that should be committed.
        viewModel.selectNextInitialMessage()
        assertEquals(1, viewModel.uiState.value.currentInitialMessageIndex)

        viewModel.onInputChanged("Hello")
        viewModel.sendMessage()
        advanceUntilIdle()

        assertEquals(greetingTwo, seededId) // committed the displayed greeting
        assertEquals("Hello", sentDto?.message) // then sent the reply
        assertFalse(viewModel.uiState.value.needsInitialMessage) // gate cleared after reload
    }

    @Test
    fun `loadChat with deleted scene resolves no background and no greetings`() = runTest {
        var sceneLookups = 0
        val viewModel = ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = { createPage(emptyList()) },
            sendMessageCall = { messageResponse(it.message) },
            // A chat whose scene was deleted → scene_id null, read-only.
            getChatCall = {
                ApiResponseChat(
                    result = Chat(
                        title = "Chat",
                        userId = UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        sceneId = null,
                        id = testChatId,
                        initialMessageId = null
                    )
                )
            },
            sceneImageCall = { _ ->
                sceneLookups++
                throw RuntimeException("no network in tests")
            },
            getSceneInitialMessagesCall = { _ ->
                sceneLookups++
                throw RuntimeException("no network in tests")
            },
            ioDispatcher = testDispatcher,
            modelFlow = flowOf(LLMModelType.testing_mock),
            waitForReply = { _, _ -> }
        )

        viewModel.loadChat()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.sceneImageUrl)
        assertTrue(viewModel.uiState.value.sceneImageResolved)
        // A scene-less chat cannot offer greetings; the gate must not open.
        assertFalse(viewModel.uiState.value.needsInitialMessage)
        assertEquals(0, sceneLookups) // no scene-dependent lookups were made
    }

    @Test
    fun `loadChat resolves the persona name from the chat's user character`() = runTest {
        val personaId = UUID.fromString("00000000-0000-0000-0000-0000000000ee")
        var lookedUpId: UUID? = null
        val viewModel = ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = { createPage(emptyList()) },
            sendMessageCall = { messageResponse(it.message) },
            getChatCall = {
                ApiResponseChat(
                    result = Chat(
                        title = "Chat",
                        userId = UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        sceneId = UUID.fromString("00000000-0000-0000-0000-0000000000dd"),
                        id = testChatId,
                        initialMessageId = null,
                        userCharacterId = personaId
                    )
                )
            },
            getCharacterCall = { id ->
                lookedUpId = id
                ApiResponseCharacter(
                    result = Character(name = "Kael", systemPrompt = "A bard.")
                )
            },
            sceneImageCall = { throw RuntimeException("no network in tests") },
            getSceneInitialMessagesCall = { throw RuntimeException("no network in tests") },
            ioDispatcher = testDispatcher,
            modelFlow = flowOf(LLMModelType.testing_mock),
            waitForReply = { _, _ -> }
        )

        viewModel.loadChat()
        advanceUntilIdle()

        assertEquals(personaId, lookedUpId)
        assertEquals("Kael", viewModel.uiState.value.personaName)
    }

    @Test
    fun `loadChat without persona leaves the name null`() = runTest {
        val viewModel = ChatViewModel(
            chatId = testChatId,
            loadMessagesCall = { createPage(emptyList()) },
            sendMessageCall = { messageResponse(it.message) },
            getChatCall = {
                ApiResponseChat(
                    result = Chat(
                        title = "Chat",
                        userId = UUID.fromString("00000000-0000-0000-0000-0000000000cc"),
                        sceneId = null,
                        id = testChatId,
                        initialMessageId = null
                    )
                )
            },
            getCharacterCall = { throw RuntimeException("must not be called without a persona") },
            sceneImageCall = { throw RuntimeException("no network in tests") },
            getSceneInitialMessagesCall = { throw RuntimeException("no network in tests") },
            ioDispatcher = testDispatcher,
            modelFlow = flowOf(LLMModelType.testing_mock),
            waitForReply = { _, _ -> }
        )

        viewModel.loadChat()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.personaName)
    }

    // ---- per-token streaming (word-by-word reveal of token / generation_* / message frames) ----

    private fun tokenFrame(chunk: String): String =
        JSONObject().put("text", chunk).toString()

    private fun messagePayload(id: UUID, status: String): String =
        JSONObject().put("message", JSONObject().put("id", id.toString()).put("status", status)).toString()

    @Test
    fun `nextRevealBoundary advances one whitespace-delimited word at a time`() {
        val text = "Hello world foo"
        assertEquals(5, nextRevealBoundary(text, 0))
        assertEquals(11, nextRevealBoundary(text, 5))
        assertEquals(15, nextRevealBoundary(text, 11))
        assertEquals(15, nextRevealBoundary(text, 15)) // nothing left
    }

    @Test
    fun `nextRevealBoundary skips trailing and leading whitespace to the end`() {
        assertEquals(11, nextRevealBoundary("Hi there   ", 8)) // trailing spaces -> end
        assertEquals(4, nextRevealBoundary("  Hi", 0)) // leading spaces skipped, then word
        assertEquals(0, nextRevealBoundary("", 0))
    }

    @Test
    fun `the pump reveals streamed tokens one word per step`() {
        val viewModel = createViewModel()

        // Only JSON scaffolding has arrived — nothing to reveal yet.
        viewModel.handleStreamFrame("token", tokenFrame("```json\n{"), emptySet(), null)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("", viewModel.uiState.value.streamingText)

        // Inner text begins to arrive; the pump uncovers it word by word.
        viewModel.handleStreamFrame("token", tokenFrame("\n  \"text\": \"He looked"), emptySet(), null)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He", viewModel.uiState.value.streamingText)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He looked", viewModel.uiState.value.streamingText)
        // Caught up but generation isn't done — idles on the revealed text.
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He looked", viewModel.uiState.value.streamingText)

        // More tokens extend the buffer; the pump keeps draining one word at a time.
        viewModel.handleStreamFrame("token", tokenFrame(" at you"), emptySet(), null)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He looked at", viewModel.uiState.value.streamingText)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He looked at you", viewModel.uiState.value.streamingText)
    }

    @Test
    fun `handleStreamFrame treats generation lifecycle markers as continue`() {
        val viewModel = createViewModel()
        val marker = JSONObject().put("request_id", "00000000-0000-0000-0000-0000000000ff").toString()

        assertEquals(StreamAction.CONTINUE, viewModel.handleStreamFrame("generation_start", marker, emptySet(), null))
        assertEquals(StreamAction.CONTINUE, viewModel.handleStreamFrame("generation_done", marker, emptySet(), null))
        assertEquals("", viewModel.uiState.value.streamingText)
    }

    @Test
    fun `thinking frames accumulate into streamingThinking without touching the answer`() {
        val viewModel = createViewModel()

        // Same {"text": ...} shape as a token frame; reasoning chunks stream verbatim.
        assertEquals(
            StreamAction.CONTINUE,
            viewModel.handleStreamFrame("thinking", tokenFrame("Let me consider"), emptySet(), null)
        )
        assertEquals("Let me consider", viewModel.uiState.value.streamingThinking)
        // Thinking must not leak into the answer buffer / reveal.
        assertEquals("", viewModel.uiState.value.streamingText)

        // Further thinking chunks append (no word-by-word reveal — surfaced as-is).
        assertEquals(
            StreamAction.CONTINUE,
            viewModel.handleStreamFrame("thinking", tokenFrame(" the options"), emptySet(), null)
        )
        assertEquals("Let me consider the options", viewModel.uiState.value.streamingThinking)
    }

    @Test
    fun `a terminal message with streamed text defers the swap until the reveal catches up`() = runTest {
        val pendingId = UUID.fromString("00000000-0000-0000-0000-0000000000ee")
        val viewModel = createViewModel()
        viewModel.handleStreamFrame("token", tokenFrame("\n  \"text\": \"He looked"), emptySet(), null)

        // Terminal arrives while text remains buffered -> deferred, not an immediate reload.
        assertEquals(
            StreamAction.CONTINUE,
            viewModel.handleStreamFrame("message", messagePayload(pendingId, "completed"), emptySet(), pendingId)
        )

        // Reveal the remaining words, then finalize once caught up.
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He", viewModel.uiState.value.streamingText)
        assertEquals(false, viewModel.pumpReveal())
        assertEquals("He looked", viewModel.uiState.value.streamingText)
        assertEquals(true, viewModel.pumpReveal()) // caught up + complete -> swap
        advanceUntilIdle() // run the reload the swap scheduled

        assertEquals("", viewModel.uiState.value.streamingText)
    }

    @Test
    fun `a terminal message with no streamed text reloads immediately`() {
        val pendingId = UUID.fromString("00000000-0000-0000-0000-0000000000ee")
        val viewModel = createViewModel()
        assertEquals(
            StreamAction.RELOAD,
            viewModel.handleStreamFrame("message", messagePayload(pendingId, "completed"), emptySet(), pendingId)
        )
    }

    @Test
    fun `handleStreamFrame continues for non-terminal pending message frames`() {
        val pendingId = UUID.fromString("00000000-0000-0000-0000-0000000000ee")
        val viewModel = createViewModel()
        assertEquals(
            StreamAction.CONTINUE,
            viewModel.handleStreamFrame("message", messagePayload(pendingId, "pending"), emptySet(), pendingId)
        )
    }
}
