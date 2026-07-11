package com.example.dreamescape_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Dreamescape_aiTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    MainScreen(
                        modifier = Modifier.padding(innerPadding),
                        onCreateCharacterClick = {
                            startActivity(Intent(this, CreateCharacterActivity::class.java))
                        },
                        onCreateSceneClick = {
                            startActivity(Intent(this, CreateSceneActivity::class.java))
                        },
                        onSceneListClick = {
                            startActivity(Intent(this, SceneListActivity::class.java))
                        },
                        onCreateChatClick = {
                            // A chat is always created for a scene, so the scene list
                            // doubles as the scene picker that leads into chat creation.
                            startActivity(Intent(this, SceneListActivity::class.java))
                        },
                        onOpenChatClick = {
                            startActivity(Intent(this, ChatListActivity::class.java))
                        },
                        onStoryGalleryClick = {
                            startActivity(Intent(this, StoryGalleryActivity::class.java))
                        },
                        onMediaGalleryClick = {
                            startActivity(Intent(this, MediaGalleryActivity::class.java))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(
    modifier: Modifier = Modifier,
    viewModel: MainViewModel = viewModel(),
    onCreateCharacterClick: () -> Unit = {},
    onCreateSceneClick: () -> Unit = {},
    onSceneListClick: () -> Unit = {},
    onCreateChatClick: () -> Unit = {},
    onOpenChatClick: () -> Unit = {},
    onStoryGalleryClick: () -> Unit = {},
    onMediaGalleryClick: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.checkBackendAvailability()
    }

    MainScreenContent(
        modifier = modifier,
        onCreateCharacterClick = onCreateCharacterClick,
        onCreateSceneClick = onCreateSceneClick,
        onSceneListClick = onSceneListClick,
        onCreateChatClick = onCreateChatClick,
        onOpenChatClick = onOpenChatClick,
        onStoryGalleryClick = onStoryGalleryClick,
        onMediaGalleryClick = onMediaGalleryClick
    )

    if (uiState.showApiUnavailableDialog) {
        ApiUnavailableDialog(
            errorMessage = uiState.errorMessage,
            onRetry = viewModel::checkBackendAvailability,
            onDismiss = viewModel::dismissApiUnavailableDialog
        )
    }
}

@Composable
fun MainScreenContent(
    modifier: Modifier = Modifier,
    onCreateCharacterClick: () -> Unit = {},
    onCreateSceneClick: () -> Unit = {},
    onSceneListClick: () -> Unit = {},
    onCreateChatClick: () -> Unit = {},
    onOpenChatClick: () -> Unit = {},
    onStoryGalleryClick: () -> Unit = {},
    onMediaGalleryClick: () -> Unit = {}
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Top
    ) {
        Greeting(name = "Android")

        Button(
            onClick = onCreateCharacterClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Create Character")
        }

        Button(
            onClick = onCreateSceneClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Create Scene")
        }

        Button(
            onClick = onSceneListClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Scene List")
        }

        Button(
            onClick = onCreateChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Create Chat")
        }

        Button(
            onClick = onOpenChatClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Open Chat")
        }

        Button(
            onClick = onStoryGalleryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Story Gallery")
        }

        Button(
            onClick = onMediaGalleryClick,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
        ) {
            Text("Media Gallery")
        }
    }
}

@Composable
fun ApiUnavailableDialog(
    errorMessage: String?,
    onRetry: () -> Unit = {},
    onDismiss: () -> Unit = {}
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Backend API not accessible") },
        text = {
            Text(
                "Could not reach the backend API at ${DreamescapeApplication.BACKEND_BASE_URL}. " +
                    "Please make sure the backend is running and reachable, then try again." +
                    (errorMessage?.let { "\n\nDetails: $it" } ?: "")
            )
        },
        confirmButton = {
            TextButton(onClick = onRetry) {
                Text("Retry")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Dismiss")
            }
        }
    )
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun MainScreenPreview() {
    Dreamescape_aiTheme {
        MainScreenContent()
    }
}