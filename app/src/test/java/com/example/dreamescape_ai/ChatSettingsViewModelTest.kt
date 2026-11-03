package com.example.dreamescape_ai

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.openapitools.client.models.ApiResponseChatSettings
import org.openapitools.client.models.ApiResponseListChatSummary
import org.openapitools.client.models.ApiResponseListSummaryChapter
import org.openapitools.client.models.ChatSummary
import org.openapitools.client.models.ControlBehavior
import org.openapitools.client.models.CreateSummariesDTO
import org.openapitools.client.models.FunctionsSettings
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Perspective
import org.openapitools.client.models.Preset
import org.openapitools.client.models.ReasoningEffort
import org.openapitools.client.models.ResponseLength
import org.openapitools.client.models.SummaryChapter
import org.openapitools.client.models.TemperatureSettings
import org.openapitools.client.models.TokenLimit
import org.openapitools.client.models.Toggle
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class ChatSettingsViewModelTest {

    private val testDispatcher = StandardTestDispatcher()

    private val testChatId = UUID.fromString("00000000-0000-0000-0000-0000000000aa")

    private val chapter1 = SummaryChapter(
        chapterNumber = 1,
        messagesCount = 10,
        fromMessageId = UUID.fromString("00000000-0000-0000-0000-000000000001"),
        untilMessageId = UUID.fromString("00000000-0000-0000-0000-00000000000a"),
        tokensCount = 1500
    )
    private val chapter2 = SummaryChapter(
        chapterNumber = 2,
        messagesCount = 6,
        fromMessageId = UUID.fromString("00000000-0000-0000-0000-00000000000b"),
        untilMessageId = UUID.fromString("00000000-0000-0000-0000-000000000010"),
        tokensCount = 900
    )

    private val settings = org.openapitools.client.models.ChatSettings(
        aiControlBehavior = ControlBehavior.DonQuoteT_Control,
        continueBehavior = ControlBehavior.DonQuoteT_Control,
        perspective = Perspective._2nd_Person,
        temperature = TemperatureSettings(preset = Preset.Mid, value = BigDecimal("0.7")),
        responseLength = ResponseLength.Medium,
        responseTokenLimit = TokenLimit.Capped,
        reasoning = Toggle.Off,
        reasoningEffort = ReasoningEffort.Mid,
        aiMediaPicker = Toggle.Off,
        functions = FunctionsSettings(characterNameGenerator = true),
        contextLimitOverride = null
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
        chapters: List<SummaryChapter> = listOf(chapter1, chapter2),
        chaptersFail: Boolean = false,
        onCreateSummaries: (CreateSummariesDTO) -> ApiResponseListChatSummary = { dto ->
            ApiResponseListChatSummary(
                result = dto.chapters.map { range ->
                    ChatSummary(
                        chatId = dto.chatId,
                        llmModel = dto.llmModel.value,
                        coveredFromAt = OffsetDateTime.now(),
                        coveredUntilAt = OffsetDateTime.now(),
                        messagesCount = 1,
                        sourceTokens = 1,
                        fromMessageId = range.fromMessageId,
                        untilMessageId = range.untilMessageId
                    )
                }
            )
        }
    ): ChatSettingsViewModel {
        val appContext = ApplicationProvider.getApplicationContext<Context>()
        return ChatSettingsViewModel(
            chatId = testChatId,
            appContext = appContext,
            getSettingsCall = { ApiResponseChatSettings(result = settings) },
            upsertSettingsCall = { _, _ -> ApiResponseChatSettings(result = settings) },
            getContextUsageCall = { throw RuntimeException("no usage in tests") },
            listChaptersCall = {
                if (chaptersFail) throw RuntimeException("chapters unavailable")
                ApiResponseListSummaryChapter(result = chapters)
            },
            createSummariesCall = onCreateSummaries,
            ioDispatcher = testDispatcher
        )
    }

    @Test
    fun `chapters load on init`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(listOf(chapter1, chapter2), viewModel.uiState.value.chapters)
    }

    @Test
    fun `chapters stay null when the preview call fails`() = runTest {
        val viewModel = createViewModel(chaptersFail = true)
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.chapters)
    }

    @Test
    fun `summarize sends every planned chapter with the chosen model`() = runTest {
        var sent: CreateSummariesDTO? = null
        val viewModel = createViewModel(onCreateSummaries = { dto ->
            sent = dto
            ApiResponseListChatSummary(
                result = dto.chapters.map { range ->
                    ChatSummary(
                        chatId = dto.chatId,
                        llmModel = dto.llmModel.value,
                        coveredFromAt = OffsetDateTime.now(),
                        coveredUntilAt = OffsetDateTime.now(),
                        messagesCount = 1,
                        sourceTokens = 1,
                        fromMessageId = range.fromMessageId,
                        untilMessageId = range.untilMessageId
                    )
                }
            )
        })
        advanceUntilIdle()

        viewModel.summarize(LLMModelType.qwenMinusMax)
        advanceUntilIdle()

        assertNotNull(sent)
        assertEquals(testChatId, sent!!.chatId)
        assertEquals(LLMModelType.qwenMinusMax, sent!!.llmModel)
        assertEquals(2, sent!!.chapters.size)
        assertEquals(chapter1.fromMessageId, sent!!.chapters[0].fromMessageId)
        assertEquals(chapter1.untilMessageId, sent!!.chapters[0].untilMessageId)
        assertEquals(chapter2.fromMessageId, sent!!.chapters[1].fromMessageId)
        assertEquals(2, viewModel.uiState.value.summarizeQueuedCount)
    }

    @Test
    fun `summarize does nothing when there are no chapters`() = runTest {
        var called = false
        val viewModel = createViewModel(
            chapters = emptyList(),
            onCreateSummaries = { called = true; ApiResponseListChatSummary(result = emptyList()) }
        )
        advanceUntilIdle()

        viewModel.summarize(LLMModelType.qwenMinusMax)
        advanceUntilIdle()

        assertEquals(false, called)
        assertEquals(0, viewModel.uiState.value.summarizeQueuedCount)
    }

    @Test
    fun `summarize failure surfaces an error message`() = runTest {
        val viewModel = createViewModel(onCreateSummaries = {
            throw RuntimeException("backend down")
        })
        advanceUntilIdle()

        viewModel.summarize(LLMModelType.qwenMinusMax)
        advanceUntilIdle()

        assertEquals("backend down", viewModel.uiState.value.errorMessage)
        assertEquals(false, viewModel.uiState.value.isSummarizing)
    }
}
