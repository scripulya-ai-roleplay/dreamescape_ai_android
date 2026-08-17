package com.example.dreamescape_ai

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
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
import com.example.dreamescape_ai.ui.components.EngagementBottomBar
import com.example.dreamescape_ai.ui.components.LikeButton
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import org.openapitools.client.models.MediaEntityType
import java.util.UUID
import kotlin.math.abs

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
    var showAttachPicker by remember { mutableStateOf(false) }

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

    // Dismiss the attach-characters picker once the request succeeds.
    LaunchedEffect(uiState.attachSuccess) {
        if (uiState.attachSuccess) {
            showAttachPicker = false
            viewModel.consumeAttachSuccess()
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.verticalScroll(rememberScrollState())
        ) {
            SceneHero(
                title = uiState.scene?.title ?: "Title there",
                imageUrl = uiState.heroImageUrl,
                foregroundUrl = uiState.foregroundImageUrl,
                isLoading = uiState.scene == null && uiState.isLoading,
                onBack = onBack,
                onManageImages = uiState.scene?.id?.takeIf { uiState.isOwner }?.let { sceneUuid ->
                    {
                        context.startActivity(
                            EntityMediaManagerActivity.intent(
                                context,
                                MediaEntityType.scene,
                                sceneUuid,
                                uiState.scene?.title ?: "Images"
                            )
                        )
                    }
                }
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
                isOwner = uiState.isOwner,
                onCharacterClick = { card ->
                    card.character.id?.let { id ->
                        context.startActivity(
                            Intent(context, CharacterPreviewActivity::class.java).apply {
                                putExtra(CharacterPreviewActivity.EXTRA_CHARACTER_ID, id.toString())
                            }
                        )
                    }
                },
                onAddCharacters = {
                    // Load the owner's characters, then let them pick which to add
                    // before the request is sent.
                    viewModel.loadAttachCandidates()
                    showAttachPicker = true
                }
            )

            DescriptionSection(uiState = uiState)

            // Clearance so the description's tail isn't hidden under the pinned bar.
            Spacer(modifier = Modifier.height(96.dp))
        }

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
            },
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        if (showPersonaPicker) {
            PersonaPickerScreen(
                characters = uiState.eligibleCharacters,
                areLoaded = uiState.areEligibleLoaded,
                isCreatingChat = uiState.isCreatingChat,
                onPick = { characterId ->
                    viewModel.selectCharacter(characterId)
                    viewModel.startChat()
                    showPersonaPicker = false
                },
                onDismiss = { showPersonaPicker = false }
            )
        }

        if (showAttachPicker) {
            AttachCharactersPickerScreen(
                candidates = uiState.attachCandidates,
                areLoaded = uiState.areAttachCandidatesLoaded,
                selectedIds = uiState.selectedAttachIds,
                isAttaching = uiState.isAttachingCharacters,
                error = uiState.attachError,
                onToggle = viewModel::toggleAttachSelection,
                onConfirm = viewModel::attachSelectedCharacters,
                onDismiss = { showAttachPicker = false }
            )
        }
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
    foregroundUrl: String?,
    isLoading: Boolean,
    onBack: () -> Unit,
    onManageImages: (() -> Unit)? = null
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

        // The first attached character's foreground image layered over the
        // scene's background (transparent PNG). Fit, not Crop, so it keeps
        // its shape instead of being stretched to the hero bounds.
        if (foregroundUrl != null) {
            AsyncImage(
                model = foregroundUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
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

        // Owner-only entry into the per-entity image manager.
        if (onManageImages != null) {
            IconButton(
                onClick = onManageImages,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding()
                    .padding(8.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.4f))
            ) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "Manage images",
                    tint = Color.White
                )
            }
        }
    }
}

@Composable
private fun CharacterCarouselSection(
    characters: List<CharacterCardState>,
    isOwner: Boolean,
    onCharacterClick: (CharacterCardState) -> Unit,
    onAddCharacters: () -> Unit
) {
    // The owner can curate the cast even when it's empty; everyone else only
    // sees the section once it has characters.
    if (characters.isEmpty() && !isOwner) return

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Characters",
            color = MaterialTheme.colorScheme.onBackground,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        if (isOwner) {
            TextButton(onClick = onAddCharacters) {
                Icon(
                    imageVector = Icons.Filled.PersonAdd,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text("Add characters")
            }
        }
    }

    if (characters.isNotEmpty()) {
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            items(characters, key = { it.character.id ?: it.character.name }) { card ->
                CharacterCard(card = card, onClick = { onCharacterClick(card) })
            }
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
    onStartChat: () -> Unit,
    modifier: Modifier = Modifier
) {
    EngagementBottomBar(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
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
}

/**
 * Full-screen persona picker: a horizontal, center-snapping carousel of the
 * characters the user may play as (bookmarked or created), with a leading
 * "no persona" card. The card currently centered is the selection; the confirm
 * button creates the chat with that persona
 * ([org.openapitools.client.models.Chat.userCharacterId], null for "no persona").
 */
@Composable
private fun PersonaPickerScreen(
    characters: List<CharacterCardState>,
    areLoaded: Boolean,
    isCreatingChat: Boolean,
    onPick: (UUID?) -> Unit,
    onDismiss: () -> Unit
) {
    val listState = rememberLazyListState()

    // The "no persona" card lives at index 0, so character cards start at index 1.
    // Whichever card is nearest the viewport center is the active selection.
    val centeredIndex by remember {
        derivedStateOf {
            val center = (listState.layoutInfo.viewportStartOffset +
                listState.layoutInfo.viewportEndOffset) / 2
            listState.layoutInfo.visibleItemsInfo
                .minByOrNull { abs((it.offset + it.size / 2) - center) }
                ?.index
                ?: 0
        }
    }
    val centeredCard = if (centeredIndex == 0) null
        else characters.getOrNull(centeredIndex - 1)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Play as",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center
        ) {
            if (!areLoaded) {
                CircularProgressIndicator()
            } else {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        val cardWidth = 180.dp
                        val sidePadding = (maxWidth - cardWidth) / 2
                        LazyRow(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = sidePadding),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            flingBehavior = rememberSnapFlingBehavior(
                                remember(listState) { SnapLayoutInfoProvider(listState) }
                            )
                        ) {
                            item(key = "none") {
                                PersonaCarouselCard(
                                    name = "No character",
                                    imageUrl = null,
                                    isCentered = centeredIndex == 0
                                )
                            }
                            itemsIndexed(
                                characters,
                                key = { _, card -> card.character.id ?: card.character.name }
                            ) { index, card ->
                                PersonaCarouselCard(
                                    name = card.character.name,
                                    imageUrl = card.imageUrl,
                                    isCentered = centeredIndex == index + 1
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = centeredCard?.character?.name ?: "No character",
                        color = MaterialTheme.colorScheme.onBackground,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (characters.isEmpty()) {
                        Text(
                            text = "You haven't bookmarked or created any characters yet.",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(top = 4.dp, start = 24.dp, end = 24.dp)
                        )
                    }
                }
            }
        }

        Button(
            onClick = { onPick(centeredCard?.character?.id) },
            enabled = areLoaded && !isCreatingChat,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (isCreatingChat) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = if (centeredCard == null) "Start without a character"
                    else "Start Chat as ${centeredCard.character.name}"
                )
            }
        }
    }
}

/** One portrait card in the persona carousel; the centered card is emphasized. */
@Composable
private fun PersonaCarouselCard(
    name: String,
    imageUrl: String?,
    isCentered: Boolean,
    modifier: Modifier = Modifier
) {
    val targetScale by animateFloatAsState(
        targetValue = if (isCentered) 1f else 0.8f,
        label = "personaCardScale"
    )
    val targetAlpha by animateFloatAsState(
        targetValue = if (isCentered) 1f else 0.6f,
        label = "personaCardAlpha"
    )
    Column(
        modifier = modifier
            .width(180.dp)
            .graphicsLayer {
                scaleX = targetScale
                scaleY = targetScale
                alpha = targetAlpha
            },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        val shape = RoundedCornerShape(20.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(0.66f)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .then(
                    if (isCentered) Modifier.border(3.dp, MaterialTheme.colorScheme.primary, shape)
                    else Modifier
                ),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/**
 * Full-screen, multi-select picker of the owner's characters to attach to the
 * scene. Mirrors [PersonaPickerScreen]'s chrome (back button + title + sticky
 * confirm) but uses a scrollable list with per-row selection, since attaching
 * several characters at once is the norm. Sends [onConfirm], which calls
 * `POST /scenes/{id}/characters` with the selected ids.
 */
@Composable
private fun AttachCharactersPickerScreen(
    candidates: List<CharacterCardState>,
    areLoaded: Boolean,
    selectedIds: Set<UUID>,
    isAttaching: Boolean,
    error: String?,
    onToggle: (UUID) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }
            Text(
                text = "Add characters",
                color = MaterialTheme.colorScheme.onBackground,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !areLoaded -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
                candidates.isEmpty() -> Text(
                    text = "You haven't created any characters yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp)
                )
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(candidates, key = { it.character.id ?: it.character.name }) { card ->
                        val id = card.character.id
                        AttachCandidateRow(
                            card = card,
                            selected = id != null && id in selectedIds,
                            onClick = { id?.let(onToggle) }
                        )
                    }
                }
            }
        }

        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        val count = selectedIds.size
        Button(
            onClick = onConfirm,
            enabled = areLoaded && !isAttaching && count > 0,
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            if (isAttaching) {
                CircularProgressIndicator()
            } else {
                Text(
                    text = if (count == 0) "Add characters"
                    else "Add $count character${if (count == 1) "" else "s"}"
                )
            }
        }
    }
}

/** One selectable row in the attach-characters picker; selection is outlined. */
@Composable
private fun AttachCandidateRow(
    card: CharacterCardState,
    selected: Boolean,
    onClick: () -> Unit
) {
    val outline = if (selected) MaterialTheme.colorScheme.primary else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, outline, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
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
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = card.character.name,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Checkbox(checked = selected, onCheckedChange = { onClick() })
    }
}

private fun scenePreviewViewModelFactory(sceneId: UUID): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ScenePreviewViewModel(sceneId = sceneId) as T
        }
    }
