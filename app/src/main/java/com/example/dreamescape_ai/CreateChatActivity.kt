package com.example.dreamescape_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import java.util.UUID

class CreateChatActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCENE_ID = "extra_scene_id"
        const val EXTRA_SCENE_TITLE = "extra_scene_title"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sceneId: UUID? = intent.getStringExtra(EXTRA_SCENE_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        val sceneTitle: String? = intent.getStringExtra(EXTRA_SCENE_TITLE)

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("New Chat") },
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
                    if (sceneId == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "No scene selected. A chat must be created for a scene.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        CreateChatScreen(
                            sceneId = sceneId,
                            sceneTitle = sceneTitle,
                            modifier = Modifier.padding(innerPadding),
                            onChatCreated = { chatId, chatTitle ->
                                startActivity(
                                    Intent(this, ChatActivity::class.java).apply {
                                        putExtra(ChatActivity.EXTRA_CHAT_ID, chatId.toString())
                                        putExtra(ChatActivity.EXTRA_CHAT_TITLE, chatTitle)
                                    }
                                )
                                finish()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CreateChatScreen(
    sceneId: UUID,
    sceneTitle: String?,
    modifier: Modifier = Modifier,
    viewModel: CreateChatViewModel = viewModel(
        factory = createChatViewModelFactory(sceneId)
    ),
    onChatCreated: (UUID, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(uiState.createdChatId) {
        val createdChatId = uiState.createdChatId
        if (createdChatId != null) {
            onChatCreated(createdChatId, uiState.title.trim())
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = "Create Chat",
            style = MaterialTheme.typography.headlineMedium
        )

        if (!sceneTitle.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Scene: $sceneTitle",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = uiState.title,
            onValueChange = viewModel::onTitleChanged,
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
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
            onClick = viewModel::createChat,
            modifier = Modifier.fillMaxWidth(),
            enabled = !uiState.isLoading
        ) {
            if (uiState.isLoading) {
                CircularProgressIndicator()
            } else {
                Text("Create")
            }
        }
    }
}

private fun createChatViewModelFactory(sceneId: UUID): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CreateChatViewModel(sceneId = sceneId) as T
        }
    }
