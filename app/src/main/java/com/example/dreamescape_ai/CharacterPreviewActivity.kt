package com.example.dreamescape_ai

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.components.BookmarkButton
import com.example.dreamescape_ai.ui.components.EngagementBottomBar
import com.example.dreamescape_ai.ui.components.LikeButton
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import java.util.UUID

class CharacterPreviewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHARACTER_ID = "extra_character_id"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val characterId: UUID? = intent.getStringExtra(EXTRA_CHARACTER_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    if (characterId == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No character selected.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        CharacterPreviewScreen(
                            characterId = characterId,
                            onBack = { finish() },
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun CharacterPreviewScreen(
    characterId: UUID,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: CharacterPreviewViewModel = viewModel(
        factory = characterPreviewViewModelFactory(characterId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(modifier = modifier) {
        Column(
            modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())
        ) {
            CharacterHero(
                title = uiState.character?.name ?: "Name there",
                imageUrl = uiState.portraitUrl,
                isLoading = uiState.character == null && uiState.isLoading,
                onBack = onBack
            )

            val errorMessage = uiState.errorMessage
            if (uiState.character == null && errorMessage != null) {
                Text(
                    text = errorMessage,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(16.dp)
                )
            }

            CharacterDescriptionSection(uiState = uiState)

            // Clearance so the description's tail isn't hidden under the pinned bar.
            Spacer(modifier = Modifier.height(96.dp))
        }

        CharacterEngagementSection(
            isLiked = uiState.isLiked,
            likesCount = uiState.likesCount,
            isBookmarked = uiState.isBookmarked,
            errorMessage = uiState.engagementError,
            onToggleLike = viewModel::toggleLike,
            onToggleBookmark = viewModel::toggleBookmark,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun CharacterEngagementSection(
    isLiked: Boolean,
    likesCount: Int,
    isBookmarked: Boolean,
    errorMessage: String?,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    modifier: Modifier = Modifier
) {
    EngagementBottomBar(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LikeButton(isLiked = isLiked, likesCount = likesCount, onClick = onToggleLike)
            BookmarkButton(isBookmarked = isBookmarked, onClick = onToggleBookmark)
        }

        if (errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp)
            )
        }
    }
}

/**
 * Top header: a large, full-bleed portrait with the character name overlaid at
 * the bottom-left and a floating back button at the top-left.
 */
@Composable
private fun CharacterHero(
    title: String,
    imageUrl: String?,
    isLoading: Boolean,
    onBack: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(300.dp)
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        // Bottom scrim so the name stays legible over any image.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.55f to Color.Transparent,
                        1.0f to Color.Black.copy(alpha = 0.7f)
                    )
                )
        )

        Text(
            text = title,
            color = Color.White,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.headlineSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
        )

        IconButton(
            onClick = onBack,
            modifier = Modifier
                .align(Alignment.TopStart)
                .statusBarsPadding()
                .padding(8.dp)
                .clip(CircleShape)
                .background(Color.Black.copy(alpha = 0.4f))
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back",
                tint = Color.White
            )
        }
    }
}

@Composable
private fun CharacterDescriptionSection(uiState: CharacterPreviewUiState) {
    val description = uiState.character?.systemPrompt?.takeIf { it.isNotBlank() }
        ?: "No description available."

    Text(
        text = "Description",
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp)
    )

    Text(
        text = description,
        color = MaterialTheme.colorScheme.onBackground,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.padding(horizontal = 16.dp)
    )
}

private fun characterPreviewViewModelFactory(characterId: UUID): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return CharacterPreviewViewModel(characterId = characterId) as T
        }
    }
