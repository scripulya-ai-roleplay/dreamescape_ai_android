package com.example.dreamescape_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.components.BookmarkButton
import com.example.dreamescape_ai.ui.components.LikeButton
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import java.util.UUID

class ScenePreviewActivity : ComponentActivity() {

    companion object {
        const val EXTRA_SCENE_ID = "extra_scene_id"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val sceneId: UUID? = intent.getStringExtra(EXTRA_SCENE_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }

        setContent {
            Dreamescape_aiTheme {
                // No top bar: the hero image is the top of the screen, so we drop
                // the default scaffold insets and let the hero go edge-to-edge.
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets(0, 0, 0, 0)
                ) { innerPadding ->
                    if (sceneId == null) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No scene selected.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        ScenePreviewScreen(
                            sceneId = sceneId,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScenePreviewScreen(
    sceneId: UUID,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
    viewModel: ScenePreviewViewModel = viewModel(
        factory = scenePreviewViewModelFactory(sceneId)
    )
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showPersonaPicker by remember { mutableStateOf(false) }

    // When the ViewModel finishes creating a chat, navigate to it. Keyed on the
    // chat id so it fires once per created chat and is skipped while null.
    LaunchedEffect(uiState.createdChatId) {
        val chatId = uiState.createdChatId ?: return@LaunchedEffect
        context.startActivity(
            Intent(context, ChatActivity::class.java).apply {
                putExtra(ChatActivity.EXTRA_CHAT_ID, chatId.toString())
                putExtra(ChatActivity.EXTRA_CHAT_TITLE, uiState.createdChatTitle ?: "Chat")
            }
        )
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState())
    ) {
        SceneHero(
            title = uiState.scene?.title ?: "Title there",
            imageUrl = uiState.heroImageUrl,
            isLoading = uiState.scene == null && uiState.isLoading,
            onBack = onBack
        )

        val errorMessage = uiState.errorMessage
        if (uiState.scene == null && errorMessage != null) {
            Text(
                text = errorMessage,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(16.dp)
            )
        }

        CharacterCarouselSection(
            characters = uiState.characters,
            onCharacterClick = { card ->
                card.character.id?.let { id ->
                    context.startActivity(
                        Intent(context, CharacterPreviewActivity::class.java).apply {
                            putExtra(CharacterPreviewActivity.EXTRA_CHARACTER_ID, id.toString())
                        }
                    )
                }
            }
        )

        DescriptionSection(uiState = uiState)

        StartChatAndEngagementSection(
            isLiked = uiState.isLiked,
            likesCount = uiState.likesCount,
            isBookmarked = uiState.isBookmarked,
            engagementError = uiState.engagementError,
            isCreatingChat = uiState.isCreatingChat,
            chatCreationError = uiState.chatCreationError,
            onToggleLike = viewModel::toggleLike,
            onToggleBookmark = viewModel::toggleBookmark,
            onStartChat = {
                // Load the user's playable characters, then let them pick a persona
                // (or start without one) before the chat is created.
                viewModel.loadEligibleCharacters()
                showPersonaPicker = true
            }
        )

        Spacer(modifier = Modifier.navigationBarsPadding())
    }

    if (showPersonaPicker) {
        PersonaPickerSheet(
            characters = uiState.eligibleCharacters,
            selectedCharacterId = uiState.selectedCharacterId,
            onPick = { characterId ->
                viewModel.selectCharacter(characterId)
                viewModel.startChat()
                showPersonaPicker = false
            },
            onDismiss = { showPersonaPicker = false }
        )
    }
}

/**
 * Top header: a large, full-bleed hero image with the title overlaid at the
 * bottom-left and a floating back button at the top-left.
 */
@Composable
private fun SceneHero(
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

        // Bottom scrim so the title stays legible over any image.
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
private fun CharacterCarouselSection(
    characters: List<CharacterCardState>,
    onCharacterClick: (CharacterCardState) -> Unit
) {
    if (characters.isEmpty()) return

    Text(
        text = "Characters",
        color = MaterialTheme.colorScheme.onBackground,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
    )

    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        items(characters, key = { it.character.id ?: it.character.name }) { card ->
            CharacterCard(card = card, onClick = { onCharacterClick(card) })
        }
    }
}

/** A small, vertically-oriented (portrait) rectangular card for one character. */
@Composable
private fun CharacterCard(card: CharacterCardState, onClick: () -> Unit = {}) {
    Column(
        modifier = Modifier
            .width(120.dp)
            .clickable { onClick() }
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            val url = card.imageUrl
            if (url != null) {
                AsyncImage(
                    model = url,
                    contentDescription = card.character.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(36.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(6.dp))

        Text(
            text = card.character.name,
            style = MaterialTheme.typography.bodySmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DescriptionSection(uiState: ScenePreviewUiState) {
    val scene = uiState.scene
    val description = scene?.description?.takeIf { it.isNotBlank() }
        ?: scene?.backgroundPrompt?.takeIf { it.isNotBlank() }
        ?: "Description of the story"

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

/**
 * The bottom action row: a like toggle (with count) and a bookmark toggle sit
 * next to the Start Chat button, which opens the persona picker.
 */
@Composable
private fun StartChatAndEngagementSection(
    isLiked: Boolean,
    likesCount: Int,
    isBookmarked: Boolean,
    engagementError: String?,
    isCreatingChat: Boolean,
    chatCreationError: String?,
    onToggleLike: () -> Unit,
    onToggleBookmark: () -> Unit,
    onStartChat: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 24.dp, start = 16.dp, end = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LikeButton(isLiked = isLiked, likesCount = likesCount, onClick = onToggleLike)
        BookmarkButton(isBookmarked = isBookmarked, onClick = onToggleBookmark)
        Button(
            onClick = onStartChat,
            enabled = !isCreatingChat,
            modifier = Modifier.weight(1f)
        ) {
            if (isCreatingChat) {
                CircularProgressIndicator()
            } else {
                Text("Start Chat")
            }
        }
    }

    val error = engagementError ?: chatCreationError
    if (error != null) {
        Text(
            text = error,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
    }
}

/**
 * Bottom sheet listing the characters the user may play as (bookmarked or
 * created), plus a "no persona" option. Picking one creates the chat with that
 * [org.openapitools.client.models.Chat.userCharacterId].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PersonaPickerSheet(
    characters: List<CharacterCardState>,
    selectedCharacterId: UUID?,
    onPick: (UUID?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState()
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Text(
            text = "Play as",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(start = 24.dp, top = 4.dp, bottom = 4.dp)
        )
        LazyColumn(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 420.dp)
        ) {
            item(key = "none") {
                PersonaPickerRow(
                    title = "Start without a character",
                    portraitUrl = null,
                    isSelected = selectedCharacterId == null,
                    onClick = { onPick(null) }
                )
            }
            if (characters.isEmpty()) {
                item(key = "empty") {
                    Text(
                        text = "You haven't bookmarked or created any characters yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp)
                    )
                }
            }
            items(characters, key = { it.character.id ?: it.character.name }) { card ->
                PersonaPickerRow(
                    title = card.character.name,
                    portraitUrl = card.imageUrl,
                    isSelected = card.character.id == selectedCharacterId,
                    onClick = { card.character.id?.let(onPick) }
                )
            }
        }
        Spacer(Modifier.navigationBarsPadding())
    }
}

@Composable
private fun PersonaPickerRow(
    title: String,
    portraitUrl: String?,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (portraitUrl != null) {
                AsyncImage(
                    model = portraitUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f)
        )
        if (isSelected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

private fun scenePreviewViewModelFactory(sceneId: UUID): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScenePreviewViewModel(sceneId = sceneId) as T
        }
    }
