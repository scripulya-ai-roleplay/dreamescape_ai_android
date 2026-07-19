package com.example.dreamescape_ai.data

import org.openapitools.client.models.LLMModelType

/**
 * Static per-model facts surfaced in the model picker card: reasoning support,
 * prompt/context caching support, and the context-window size in tokens.
 *
 * The backend (`scripulya_agent`) applies only `temperature`/`maxTokens`
 * uniformly and has no notion of per-model features, so these live client-side.
 * The values are best-effort real-world specs — adjust as providers change them.
 */
data class ModelSpec(
    val reasoning: Boolean,
    val caching: Boolean,
    val contextTokens: Int
)

val LLMModelType.spec: ModelSpec
    get() = when (this) {
        LLMModelType.testing_mock -> ModelSpec(reasoning = false, caching = false, contextTokens = 8_192)
        LLMModelType.geminiMinus3MinusFlashMinusPreview -> ModelSpec(true, true, 1_000_000)
        LLMModelType.geminiMinus2Period5MinusPro -> ModelSpec(true, true, 1_000_000)
        LLMModelType.claudeMinusSonnetMinus4Minus20250514 -> ModelSpec(true, true, 200_000)
        LLMModelType.claudeMinusHaikuMinus4Minus20250514 -> ModelSpec(true, true, 200_000)
        LLMModelType.qwenMinusPlus -> ModelSpec(false, false, 131_072)
        LLMModelType.qwenMinusTurbo -> ModelSpec(false, false, 1_000_000)
        LLMModelType.qwenMinusMax -> ModelSpec(false, false, 32_768)
        LLMModelType.glmMinus5Period2 -> ModelSpec(true, true, 131_072)
        LLMModelType.glmMinus4Period6 -> ModelSpec(true, true, 200_000)
        LLMModelType.glmMinus4Period5 -> ModelSpec(true, true, 131_072)
    }

/** Whether the model exposes a reasoning/thinking mode — gates the reasoning
 *  controls in chat settings. */
val LLMModelType.supportsReasoning: Boolean
    get() = spec.reasoning

/** Compact token-count formatting: 1_000_000 -> "1M", 131_072 -> "131K", 512 -> "512". */
fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> "${tokens / 1_000_000}M"
    tokens >= 1_000 -> "${tokens / 1_000}K"
    else -> tokens.toString()
}
