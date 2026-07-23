package com.example.dreamescape_ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.openapitools.client.models.LLMModelType
import java.util.UUID

/** Per-process DataStore remembering the selected LLM model for each chat. */
private val Context.chatModelDataStore by preferencesDataStore(name = "chat_model_prefs")

/**
 * Persists the user's chosen [LLMModelType] per chat locally.
 *
 * The backend's `ChatSettings` has no model field — the model is carried
 * per-message in `SendMessageRequest.llmModel`. So the choice is stored here and
 * read both by the chat-settings screen (to show the picker) and by the chat
 * (to send messages).
 */
object ChatModelStore {

    /** Fallback when a chat has no persisted choice — preserves the prior
     *  hardcoded behavior of always sending glm-5.2. */
    val DEFAULT_MODEL: LLMModelType = LLMModelType.glmMinus5Period2

    private fun key(chatId: UUID) = stringPreferencesKey("model_$chatId")

    /** Emits the persisted model for [chatId], or [DEFAULT_MODEL] when unset. */
    fun modelFlow(context: Context, chatId: UUID): Flow<LLMModelType> =
        context.chatModelDataStore.data.map { prefs ->
            prefs[key(chatId)]
                ?.let { LLMModelType.decode(it) }
                ?: DEFAULT_MODEL
        }

    /** Persists [model] as the chosen model for [chatId]. */
    suspend fun setModel(context: Context, chatId: UUID, model: LLMModelType) {
        context.chatModelDataStore.edit { it[key(chatId)] = model.value }
    }
}

/** Human-friendly label for the model picker (codegen mangles enum names). */
val LLMModelType.displayName: String
    get() = when (this) {
        LLMModelType.testing_mock -> "Mock"
        LLMModelType.geminiMinus3MinusFlashMinusPreview -> "Gemini 3 Flash"
        LLMModelType.geminiMinus2Period5MinusPro -> "Gemini 2.5 Pro"
        LLMModelType.claudeMinusSonnetMinus4Minus20250514 -> "Claude Sonnet 4"
        LLMModelType.claudeMinusHaikuMinus4Minus20250514 -> "Claude Haiku 4"
        LLMModelType.qwenMinusPlus -> "Qwen Plus"
        LLMModelType.qwenMinusTurbo -> "Qwen Turbo"
        LLMModelType.qwenMinusMax -> "Qwen Max"
        LLMModelType.glmMinus5Period2 -> "GLM 5.2"
        LLMModelType.glmMinus4Period6 -> "GLM 4.6"
        LLMModelType.glmMinus4Period5 -> "GLM 4.5"
    }
