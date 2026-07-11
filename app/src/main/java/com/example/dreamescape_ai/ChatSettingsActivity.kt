package com.example.dreamescape_ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import org.openapitools.client.models.ControlBehavior
import org.openapitools.client.models.Perspective
import org.openapitools.client.models.Preset
import org.openapitools.client.models.ReasoningEffort
import org.openapitools.client.models.ResponseLength
import org.openapitools.client.models.TokenLimit
import org.openapitools.client.models.Toggle
import java.math.BigDecimal
import java.util.UUID

class ChatSettingsActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId: UUID? = intent.getStringExtra(EXTRA_CHAT_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Chat settings") },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    if (chatId == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Invalid chat.",
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    } else {
                        ChatSettingsScreen(
                            chatId = chatId,
                            modifier = Modifier.padding(innerPadding),
                            onSaved = { finish() }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatSettingsScreen(
    chatId: UUID,
    modifier: Modifier = Modifier,
    viewModel: ChatSettingsViewModel = viewModel(factory = chatSettingsViewModelFactory(chatId)),
    onSaved: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.saved) {
        if (uiState.saved) onSaved()
    }

    val settings = uiState.settings
    if (uiState.isLoading || settings == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
        }

        EnumDropdown(
            label = "Perspective",
            options = Perspective.values().toList(),
            selected = settings.perspective,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(perspective = v) } }
        )

        EnumDropdown(
            label = "Response length",
            options = ResponseLength.values().toList(),
            selected = settings.responseLength,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(responseLength = v) } }
        )

        EnumDropdown(
            label = "Response token limit",
            options = TokenLimit.values().toList(),
            selected = settings.responseTokenLimit,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(responseTokenLimit = v) } }
        )

        EnumDropdown(
            label = "AI control behavior",
            options = ControlBehavior.values().toList(),
            selected = settings.aiControlBehavior,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(aiControlBehavior = v) } }
        )

        EnumDropdown(
            label = "Continue behavior",
            options = ControlBehavior.values().toList(),
            selected = settings.continueBehavior,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(continueBehavior = v) } }
        )

        EnumDropdown(
            label = "Reasoning effort",
            options = ReasoningEffort.values().toList(),
            selected = settings.reasoningEffort,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(reasoningEffort = v) } }
        )

        ToggleRow(
            label = "Reasoning",
            checked = settings.reasoning == Toggle.On,
            onCheckedChange = { v -> viewModel.update { it.copy(reasoning = if (v) Toggle.On else Toggle.Off) } }
        )

        ToggleRow(
            label = "AI media picker",
            checked = settings.aiMediaPicker == Toggle.On,
            onCheckedChange = { v -> viewModel.update { it.copy(aiMediaPicker = if (v) Toggle.On else Toggle.Off) } }
        )

        ToggleRow(
            label = "Character name generator",
            checked = settings.functions.characterNameGenerator ?: true,
            onCheckedChange = { v ->
                viewModel.update { it.copy(functions = it.functions.copy(characterNameGenerator = v)) }
            }
        )

        Text("Temperature", style = MaterialTheme.typography.titleSmall)

        EnumDropdown(
            label = "Temperature preset",
            options = Preset.values().toList(),
            selected = settings.temperature.preset,
            optionText = { it.value },
            onSelect = { v -> viewModel.update { it.copy(temperature = it.temperature.copy(preset = v)) } }
        )

        val tempValue = settings.temperature.value.toFloat()
        Text("Value: ${"%.2f".format(tempValue)}", style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = tempValue,
            onValueChange = { v ->
                viewModel.update {
                    it.copy(temperature = it.temperature.copy(value = BigDecimal.valueOf(v.toDouble())))
                }
            },
            valueRange = 0f..2f,
            steps = 39
        )

        var contextLimit by remember {
            mutableStateOf(settings.contextLimitOverride?.toString() ?: "")
        }
        OutlinedTextField(
            value = contextLimit,
            onValueChange = { input ->
                contextLimit = input.filter { c -> c.isDigit() }
                val parsed = contextLimit.toIntOrNull()
                viewModel.update { it.copy(contextLimitOverride = parsed) }
            },
            label = { Text("Context limit override (optional)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = viewModel::save,
            enabled = !uiState.isSaving,
            modifier = Modifier.fillMaxWidth()
        ) {
            if (uiState.isSaving) {
                CircularProgressIndicator(
                    modifier = Modifier.height(24.dp),
                    strokeWidth = 2.dp
                )
            } else {
                Text("Save settings")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun <T> EnumDropdown(
    label: String,
    options: List<T>,
    selected: T,
    optionText: (T) -> String,
    onSelect: (T) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = optionText(selected),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor()
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(optionText(option)) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

private fun chatSettingsViewModelFactory(chatId: UUID): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatSettingsViewModel(chatId = chatId) as T
        }
    }
