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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.ScripulyaText
import com.example.dreamescape_ai.ui.theme.ScripulyaTextDim
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.filled.Forum
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.dreamescape_ai.ui.theme.BlueVoid
import com.example.dreamescape_ai.ui.theme.NightOutline
import org.openapitools.client.models.Chat

class ChatListActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text("Chats") },
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
                    ChatListScreen(
                        modifier = Modifier.padding(innerPadding),
                        onChatClick = { chat ->
                            chat.id?.let { chatId ->
                                startActivity(
                                    Intent(this, ChatActivity::class.java).apply {
                                        putExtra(ChatActivity.EXTRA_CHAT_ID, chatId.toString())
                                        putExtra(ChatActivity.EXTRA_CHAT_TITLE, chat.title)
                                    }
                                )
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun ChatListScreen(
    modifier: Modifier = Modifier,
    viewModel: ChatListViewModel = viewModel(),
    onChatClick: (Chat) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    // When non-null, a full-screen overlay lists every individual chat for that
    // scene. The group view collapses a scene's chats into one row, so this is
    // where the user actually sees and picks a specific chat.
    var selectedGroup by remember { mutableStateOf<ChatGroup?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadChats()
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            if (uiState.isLoading) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator()
                }
            } else if (uiState.chats.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No chats found",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.groups, key = { it.listKey }) { group ->
                        ChatGroupItem(
                            group = group,
                            onClick = { onChatClick(group.latestChat) },
                            onViewAll = { selectedGroup = group }
                        )
                    }
                }
            }
        }

        selectedGroup?.let { group ->
            SceneChatsOverlay(
                group = group,
                chats = uiState.chats,
                onBack = { selectedGroup = null },
                onChatClick = { chat ->
                    selectedGroup = null
                    onChatClick(chat)
                },
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

@Composable
fun ChatGroupItem(
    group: ChatGroup,
    onClick: () -> Unit = {},
    onViewAll: () -> Unit = {}
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .scripPanel(radius = 18.dp)
    ) {
        // Main row: tapping it opens the scene's most recently created chat.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(ManaBlue.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                val imageUrl = group.sceneImageUrl
                if (imageUrl != null) {
                    AsyncImage(
                        model = imageUrl,
                        contentDescription = group.sceneName,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Chat,
                        contentDescription = null,
                        tint = ManaBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = group.sceneName ?: group.latestChat.title,
                    color = ScripulyaText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                val preview = group.latestMessagePreview
                if (preview != null) {
                    Text(
                        text = preview,
                        color = ScripulyaTextDim,
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (group.chatCount == 1) "1 chat" else "${group.chatCount} chats",
                        color = ScripulyaTextDim,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = ScripulyaTextDim,
                modifier = Modifier.size(20.dp)
            )
        }

        // Bottom button: opens the per-scene chat list. Only meaningful when more
        // than one chat exists — with a single chat the main row already opens it.
        if (group.chatCount > 1) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(NightOutline.copy(alpha = 0.6f))
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onViewAll() }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Forum,
                    contentDescription = null,
                    tint = ManaBlue,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "View all ${group.chatCount} chats",
                    color = ManaBlue,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = ScripulyaTextDim,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

/**
 * Full-screen overlay listing every individual chat for one scene. Reached from a
 * [ChatGroupItem]'s "View all" button — the group view collapses a scene's chats
 * into one row, so this is where the user actually sees and opens each one.
 */
@Composable
private fun SceneChatsOverlay(
    group: ChatGroup,
    chats: List<Chat>,
    onBack: () -> Unit,
    onChatClick: (Chat) -> Unit,
    modifier: Modifier = Modifier
) {
    // The backend lists chats oldest-first, so reversing puts the newest first —
    // matching how the group picks its "latest" chat.
    val sceneChats = remember(chats, group.sceneId) {
        chats.filter { it.sceneId == group.sceneId }.asReversed()
    }
    val overlayTitle = group.sceneName ?: group.latestChat.title
    Column(modifier = modifier.fillMaxSize().background(BlueVoid)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ScripulyaText
                )
            }
            Text(
                text = overlayTitle,
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (sceneChats.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No chats",
                    color = ScripulyaText.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                itemsIndexed(sceneChats) { _, chat ->
                    IndividualChatRow(chat = chat, onClick = { onChatClick(chat) })
                }
            }
        }
    }
}

@Composable
private fun IndividualChatRow(chat: Chat, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scripPanel(radius = 16.dp)
            .clickable { onClick() }
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.Chat,
            contentDescription = null,
            tint = ManaBlue,
            modifier = Modifier.size(22.dp)
        )
        Text(
            text = chat.title,
            color = ScripulyaText,
            fontWeight = FontWeight.Medium,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = ScripulyaTextDim,
            modifier = Modifier.size(20.dp)
        )
    }
}
