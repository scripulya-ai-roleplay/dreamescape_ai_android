package com.example.dreamescape_ai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.ui.components.ImagePickerSection
import com.example.dreamescape_ai.ui.components.rememberScrollForwarder
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import org.openapitools.client.models.MediaEntityType
import java.util.UUID

class CreateCharacterActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val editId: UUID? = intent.getStringExtra(EXTRA_CHARACTER_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(if (editId == null) "Create Character" else "Edit Character") },
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
                    CreateCharacterScreen(
                        editId = editId,
                        modifier = Modifier.padding(innerPadding),
                        onCharacterCreated = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateCharacterScreen(
    editId: UUID? = null,
    modifier: Modifier = Modifier,
    onCharacterCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: CreateCharacterViewModel = viewModel(factory = createCharacterViewModelFactory(context, editId))
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    // Drags that start over a multiline field forward to the page scroll instead
    // of being swallowed by the field's own (empty) scroll container.
    val scrollForwarder = rememberScrollForwarder(scrollState)

    if (uiState.isSuccess) {
        onCharacterCreated()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        if (uiState.isPrefilling) {
            CircularProgressIndicator()
        }

        Text(
            text = if (uiState.isEdit) "Edit Character" else "Create Character",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.name,
            onValueChange = viewModel::onNameChanged,
            label = { Text("Name") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.systemPrompt,
            onValueChange = viewModel::onSystemPromptChanged,
            label = { Text("System Prompt") },
            modifier = Modifier.fillMaxWidth().nestedScroll(scrollForwarder),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = uiState.isPublic,
                onCheckedChange = viewModel::onIsPublicChanged
            )
            Text(text = "Public")
        }

        Spacer(modifier = Modifier.height(16.dp))

        ImagePickerSection(
            imageUris = uiState.imageUris,
            onAddImages = viewModel::onImagesAdded,
            onRemoveImage = viewModel::onImageRemoved
        )

        Spacer(modifier = Modifier.height(16.dp))

        if (uiState.errorMessage != null) {
            Text(
                text = uiState.errorMessage!!,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Button(
            onClick = viewModel::createCharacter,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading && !uiState.isPrefilling
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text(if (uiState.isEdit) "Update" else "Create")
            }
        }
    }
}

private fun createCharacterViewModelFactory(context: Context, editId: UUID?): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CreateCharacterViewModel(
                editId = editId,
                uploadImage = { id, uri, isPublic ->
                    MediaUploader.uploadUri(context, Uri.parse(uri), MediaEntityType.character, id, isPublic)
                }
            ) as T
    }
