package com.example.dreamescape_ai

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.dreamescape_ai.data.ChatModelStore
import com.example.dreamescape_ai.data.supportsReasoning
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.ChatSettingsApi
import org.openapitools.client.apis.ChatsApi
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.ApiResponseChatSettings
import org.openapitools.client.models.ApiResponseContextUsage
import org.openapitools.client.models.ChatSettings
import org.openapitools.client.models.ContextUsage
import org.openapitools.client.models.ControlBehavior
import org.openapitools.client.models.FunctionsSettings
import org.openapitools.client.models.LLMModelType
import org.openapitools.client.models.Perspective
import org.openapitools.client.models.Preset
import org.openapitools.client.models.ReasoningEffort
import org.openapitools.client.models.ResponseLength
import org.openapitools.client.models.TemperatureSettings
import org.openapitools.client.models.TokenLimit
import org.openapitools.client.models.Toggle
import java.math.BigDecimal
import java.util.UUID

data class ChatSettingsUiState(
    val settings: ChatSettings? = null,
    val selectedModel: LLMModelType = ChatModelStore.DEFAULT_MODEL,
    val contextUsage: ContextUsage? = null,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
)

class ChatSettingsViewModel(
    private val chatId: UUID,
    private val appContext: Context,
    private val getSettingsCall: (UUID) -> ApiResponseChatSettings = { id ->
        ChatSettingsApi().getChatSettingsApiV1ChatsChatIdSettingsGet(id)
    },
    private val upsertSettingsCall: (UUID, ChatSettings) -> ApiResponseChatSettings = { id, settings ->
        ChatSettingsApi().upsertChatSettingsApiV1ChatsChatIdSettingsPut(id, settings)
    },
    private val getContextUsageCall: (UUID) -> ApiResponseContextUsage = { id ->
        ChatsApi().getChatContextUsageApiV1ChatsChatIdContextUsageGet(id)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatSettingsUiState(isLoading = true))
    val uiState: StateFlow<ChatSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
        loadSelectedModel()
        loadContextUsage()
    }

    fun loadSettings() {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                val settings = getSettingsCall(chatId).result
                _uiState.value = _uiState.value.copy(settings = settings, isLoading = false)
            } catch (e: ClientException) {
                // 404 means no settings row exists yet — start from defaults so the
                // user can create them with PUT. Any other client error is real.
                if (e.statusCode == 404) {
                    _uiState.value = _uiState.value.copy(settings = defaultSettings(), isLoading = false)
                } else {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = e.message ?: "Failed to load settings"
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Failed to load settings"
                )
            }
        }
    }

    /**
     * Fetches the token usage of the chat's current context (system prompt +
     * history) with per-model context windows. Non-fatal: the usage bar simply
     * stays hidden if this call fails, so it never blocks editing settings.
     */
    fun loadContextUsage() {
        viewModelScope.launch(ioDispatcher) {
            try {
                val usage = getContextUsageCall(chatId).result
                _uiState.value = _uiState.value.copy(contextUsage = usage)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(contextUsage = null)
            }
        }
    }

    /** Applies an edit to the current settings, producing a new immutable copy. */
    fun update(transform: (ChatSettings) -> ChatSettings) {
        val current = _uiState.value.settings ?: return
        _uiState.value = _uiState.value.copy(settings = transform(current), saved = false)
    }

    /** Loads the persisted model choice and keeps state in sync with the store. */
    private fun loadSelectedModel() {
        viewModelScope.launch(ioDispatcher) {
            ChatModelStore.modelFlow(appContext, chatId).collect { model ->
                applyModel(model)
            }
        }
    }

    /**
     * Selects [model] for this chat: applies it optimistically so the UI reacts
     * instantly, then writes it to the store (which re-confirms via
     * [loadSelectedModel]).
     */
    fun selectModel(model: LLMModelType) {
        applyModel(model)
        viewModelScope.launch(ioDispatcher) {
            ChatModelStore.setModel(appContext, chatId, model)
        }
    }

    /**
     * Records [model] in state. When the model lacks a reasoning mode, reasoning
     * is forced off so the persisted settings stay consistent with what the UI
     * allowed — the model can't honor it anyway.
     */
    private fun applyModel(model: LLMModelType) {
        val current = _uiState.value
        val settings = current.settings
        val reconciledSettings =
            if (!model.supportsReasoning && settings != null && settings.reasoning == Toggle.On) {
                settings.copy(reasoning = Toggle.Off)
            } else {
                settings
            }
        _uiState.value = current.copy(
            selectedModel = model,
            settings = reconciledSettings,
            saved = false
        )
    }

    fun save() {
        val settings = _uiState.value.settings ?: return
        _uiState.value = _uiState.value.copy(isSaving = true, errorMessage = null)
        viewModelScope.launch(ioDispatcher) {
            try {
                upsertSettingsCall(chatId, settings)
                _uiState.value = _uiState.value.copy(isSaving = false, saved = true)
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isSaving = false,
                    errorMessage = e.message ?: "Failed to save settings"
                )
            }
        }
    }

    // Mirrors the backend's DEFAULT_CHAT_SETTINGS (src/application/chats/settings.py).
    // The backend now actually applies these to generation, so the values shown here
    // for a chat with no settings row must match what the backend uses — otherwise the
    // UI misrepresents the current behavior, and saving would silently change it.
    private fun defaultSettings() = ChatSettings(
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
}
