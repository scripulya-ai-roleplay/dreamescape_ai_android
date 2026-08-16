package com.example.dreamescape_ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.openapitools.client.infrastructure.Serializer
import org.openapitools.client.models.ApiResponseContextUsage
import org.openapitools.client.models.LLMModelType

/**
 * Reproduces the phone-side parse of the live backend response for
 * GET /chats/{id}/context-usage (bytes captured from the running backend),
 * then the exact model-match the usage bar performs in the UI.
 */
class ContextUsageDeserializeTest {

    // Shape from the deployed backend after the "improved token counting"
    // refactor: system_prompt_tokens -> cards_tokens, plus estimated on both
    // levels and usable_tokens per model.
    private val liveResponse = """
        {"result":{"cards_tokens":521,"history_tokens":523,"history_messages_count":3,
        "total_tokens":1044,"estimated":true,"models":[
        {"llm_model":"gemini-3-flash-preview","context_window_tokens":1048576,"usable_tokens":939622,"remaining_tokens":938578,"fits":true,"estimated":true},
        {"llm_model":"gemini-3.1-pro-preview","context_window_tokens":1048576,"usable_tokens":939622,"remaining_tokens":938578,"fits":true,"estimated":true},
        {"llm_model":"claude-sonnet-4-20250514","context_window_tokens":200000,"usable_tokens":175904,"remaining_tokens":174860,"fits":true,"estimated":true},
        {"llm_model":"claude-haiku-4-20250514","context_window_tokens":200000,"usable_tokens":175904,"remaining_tokens":174860,"fits":true,"estimated":true},
        {"llm_model":"qwen-plus","context_window_tokens":1000000,"usable_tokens":895904,"remaining_tokens":894860,"fits":true,"estimated":true},
        {"llm_model":"qwen-turbo","context_window_tokens":1000000,"usable_tokens":895904,"remaining_tokens":894860,"fits":true,"estimated":true},
        {"llm_model":"qwen-max","context_window_tokens":262144,"usable_tokens":231833,"remaining_tokens":230789,"fits":true,"estimated":true},
        {"llm_model":"glm-5.2","context_window_tokens":1000000,"usable_tokens":895904,"remaining_tokens":894860,"fits":true,"estimated":true},
        {"llm_model":"glm-4.6","context_window_tokens":200000,"usable_tokens":175904,"remaining_tokens":174860,"fits":true,"estimated":true},
        {"llm_model":"glm-4.5","context_window_tokens":128000,"usable_tokens":111104,"remaining_tokens":110060,"fits":true,"estimated":true}]},
        "correlation_id":"test"}
    """.trimIndent()

    @Test
    fun `live context-usage response deserializes and model match works`() {
        val parsed = Serializer.moshi.adapter(ApiResponseContextUsage::class.java).fromJson(liveResponse)

        assertNotNull(parsed)
        assertEquals(1044, parsed!!.result.totalTokens)
        assertEquals(521, parsed.result.cardsTokens)
        assertEquals(10, parsed.result.models.size)

        // The phone's stored model choice for its chats is gemini-3.1-pro-preview.
        val selected = LLMModelType.geminiMinus3Period1MinusProMinusPreview
        val match = parsed.result.models.firstOrNull { it.llmModel == selected }
        assertNotNull("usage bar found no entry for $selected", match)
        assertEquals(1_048_576, match!!.contextWindowTokens)
        assertTrue(match.fits)
        assertEquals(938_578, match.remainingTokens)
    }
}
