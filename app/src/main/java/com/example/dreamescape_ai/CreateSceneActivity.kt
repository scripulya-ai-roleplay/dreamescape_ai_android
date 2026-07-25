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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
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

class CreateSceneActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCENE_ID = "extra_scene_id"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val editId: UUID? = intent.getStringExtra(EXTRA_SCENE_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(if (editId == null) "Create Scene" else "Edit Scene") },
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
                    CreateSceneScreen(
                        editId = editId,
                        modifier = Modifier.padding(innerPadding),
                        onSceneCreated = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
fun CreateSceneScreen(
    editId: UUID? = null,
    modifier: Modifier = Modifier,
    onSceneCreated: () -> Unit = {}
) {
    val context = LocalContext.current
    val viewModel: CreateSceneViewModel = viewModel(factory = createSceneViewModelFactory(context, editId))
    val uiState by viewModel.uiState.collectAsState()
    val scrollState = rememberScrollState()
    // Drags that start over a multiline field forward to the page scroll instead
    // of being swallowed by the field's own (empty) scroll container.
    val scrollForwarder = rememberScrollForwarder(scrollState)

    if (uiState.isSuccess) {
        onSceneCreated()
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
            text = if (uiState.isEdit) "Edit Scene" else "Create Scene",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.title,
            onValueChange = viewModel::onTitleChanged,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.description,
            onValueChange = viewModel::onDescriptionChanged,
            label = { Text("Description") },
            modifier = Modifier.fillMaxWidth().nestedScroll(scrollForwarder),
            minLines = 2
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = uiState.backgroundPrompt,
            onValueChange = viewModel::onBackgroundPromptChanged,
            label = { Text("Background Prompt") },
            modifier = Modifier.fillMaxWidth().nestedScroll(scrollForwarder),
            minLines = 3
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Initial Messages",
            style = MaterialTheme.typography.labelLarge
        )

        uiState.initialMessages.forEachIndexed { index, messageText ->
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = messageText,
                    onValueChange = { viewModel.onInitialMessageChanged(index, it) },
                    label = { Text("Initial message ${index + 1}") },
                    modifier = Modifier.weight(1f).nestedScroll(scrollForwarder),
                    minLines = 2
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = { viewModel.removeInitialMessage(index) },
                    // Keep at least one row — a scene must offer a greeting.
                    enabled = uiState.initialMessages.size > 1
                ) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Remove initial message"
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        TextButton(
            onClick = viewModel::addInitialMessage,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("+ Add initial message")
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
            onClick = viewModel::createScene,
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

private fun createSceneViewModelFactory(context: Context, editId: UUID?): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            CreateSceneViewModel(
                editId = editId,
                uploadImage = { id, uri, isPublic ->
                    MediaUploader.uploadUri(context, Uri.parse(uri), MediaEntityType.scene, id, isPublic)
                }
            ) as T
    }
