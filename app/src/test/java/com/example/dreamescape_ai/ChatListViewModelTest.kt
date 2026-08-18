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
import org.openapitools.client.models.ApiResponsePageChat
import org.openapitools.client.models.Chat
import org.openapitools.client.models.PageChat
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ChatListViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testUserId = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val testSceneId = UUID.fromString("00000000-0000-0000-0000-000000000002")

    private val testChats = listOf(
        Chat(
            title = "First Chat",
            userId = testUserId,
            sceneId = testSceneId,
            id = UUID.fromString("00000000-0000-0000-0000-000000000010")
        ),
        Chat(
            title = "Second Chat",
            userId = testUserId,
            sceneId = testSceneId,
            id = UUID.fromString("00000000-0000-0000-0000-000000000011")
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

    private fun createResponse(chats: List<Chat> = testChats): ApiResponsePageChat {
        return ApiResponsePageChat(
            result = PageChat(
                items = chats,
                count = chats.size,
                offset = 0,
                limit = 50
            )
        )
    }

    private fun createViewModel(
        onSearchChats: (List<UUID>?, Int?, Int?) -> ApiResponsePageChat = { _, _, _ ->
            createResponse()
        }
    ): ChatListViewModel {
        return ChatListViewModel(
            userId = testUserId,
            searchChatsCall = onSearchChats,
            // The per-group scene/image/message lookups are exercised elsewhere;
            // stubbed out here so these tests stay hermetic and offline.
            getSceneCall = { throw IllegalStateException("not used in tests") },
            sceneImageCall = { throw IllegalStateException("not used in tests") },
            latestMessageCall = { throw IllegalStateException("not used in tests") },
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `initial state has empty fields`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(emptyList<Chat>(), state.chats)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadChats fetches chats`() = runTest {
        val viewModel = createViewModel()

        viewModel.loadChats()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.chats.size)
        assertEquals("First Chat", state.chats[0].title)
        assertEquals("Second Chat", state.chats[1].title)
        assertFalse(state.isLoading)
        assertNull(state.errorMessage)
    }

    @Test
    fun `loadChats filters by current user id`() = runTest {
        var capturedUserIds: List<UUID>? = null
        val viewModel = createViewModel { userIds, _, _ ->
            capturedUserIds = userIds
            createResponse()
        }

        viewModel.loadChats()
        advanceUntilIdle()

        assertEquals(listOf(testUserId), capturedUserIds)
    }

    @Test
    fun `loadChats sets error message on failure`() = runTest {
        val viewModel = createViewModel { _, _, _ ->
            throw RuntimeException("Network error")
        }

        viewModel.loadChats()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Network error", state.errorMessage)
        assertFalse(state.isLoading)
        assertTrue(state.chats.isEmpty())
    }

    @Test
    fun `loadChats clears error on new request`() = runTest {
        var shouldFail = true
        val viewModel = createViewModel { _, _, _ ->
            if (shouldFail) throw RuntimeException("Network error")
            createResponse()
        }

        viewModel.loadChats()
        advanceUntilIdle()
        assertEquals("Network error", viewModel.uiState.value.errorMessage)

        shouldFail = false
        viewModel.loadChats()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.errorMessage)
        assertEquals(2, viewModel.uiState.value.chats.size)
    }

    @Test
    fun `loadChats returns empty list when no chats match`() = runTest {
        val viewModel = createViewModel { _, _, _ ->
            createResponse(emptyList())
        }

        viewModel.loadChats()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.chats.isEmpty())
        assertFalse(viewModel.uiState.value.isLoading)
        assertNull(viewModel.uiState.value.errorMessage)
    }

    @Test
    fun `chats with deleted scene form their own group and do not resolve scene details`() = runTest {
        val scenelessChat = Chat(
            title = "Orphaned Chat",
            userId = testUserId,
            sceneId = null,
            id = UUID.fromString("00000000-0000-0000-0000-000000000012")
        )
        val viewModel = createViewModel { _, _, _ ->
            createResponse(testChats + scenelessChat)
        }

        viewModel.loadChats()
        advanceUntilIdle()

        val groups = viewModel.uiState.value.groups
        assertEquals(2, groups.size)
        val orphanGroup = groups.first { it.sceneId == null }
        assertEquals(1, orphanGroup.chatCount)
        assertEquals(scenelessChat.id, orphanGroup.chatIds.single())
        assertEquals("no-scene", orphanGroup.listKey)
    }
}
