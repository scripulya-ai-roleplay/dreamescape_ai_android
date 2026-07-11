package com.example.dreamescape_ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.openapitools.client.apis.ChatSettingsApi
import org.openapitools.client.infrastructure.ClientException
import org.openapitools.client.models.ApiResponseChatSettings
import org.openapitools.client.models.ChatSettings
import org.openapitools.client.models.ControlBehavior
import org.openapitools.client.models.FunctionsSettings
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
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val errorMessage: String? = null,
    val saved: Boolean = false
)

class ChatSettingsViewModel(
    private val chatId: UUID,
    private val getSettingsCall: (UUID) -> ApiResponseChatSettings = { id ->
        ChatSettingsApi().getChatSettingsApiV1ChatsChatIdSettingsGet(id)
    },
    private val upsertSettingsCall: (UUID, ChatSettings) -> ApiResponseChatSettings = { id, settings ->
        ChatSettingsApi().upsertChatSettingsApiV1ChatsChatIdSettingsPut(id, settings)
    },
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChatSettingsUiState(isLoading = true))
    val uiState: StateFlow<ChatSettingsUiState> = _uiState.asStateFlow()

    init {
        loadSettings()
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

    /** Applies an edit to the current settings, producing a new immutable copy. */
    fun update(transform: (ChatSettings) -> ChatSettings) {
        val current = _uiState.value.settings ?: return
        _uiState.value = _uiState.value.copy(settings = transform(current), saved = false)
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

    private fun defaultSettings() = ChatSettings(
        aiControlBehavior = ControlBehavior.Control,
        continueBehavior = ControlBehavior.Control,
        perspective = Perspective._3rd_Person,
        temperature = TemperatureSettings(preset = Preset.Mid, value = BigDecimal("0.7")),
        responseLength = ResponseLength.Medium,
        responseTokenLimit = TokenLimit.High,
        reasoning = Toggle.On,
        reasoningEffort = ReasoningEffort.Mid,
        aiMediaPicker = Toggle.Off,
        functions = FunctionsSettings(characterNameGenerator = true),
        contextLimitOverride = null
    )
}
