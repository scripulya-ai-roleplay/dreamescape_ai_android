package com.example.dreamescape_ai

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.dreamescape_ai.data.ChatModelStore
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import com.mikepenz.markdown.compose.components.MarkdownComponent
import com.mikepenz.markdown.compose.components.markdownComponents
import com.mikepenz.markdown.compose.elements.MarkdownText
import com.mikepenz.markdown.m3.Markdown
import com.mikepenz.markdown.m3.markdownColor
import com.mikepenz.markdown.utils.buildMarkdownAnnotatedString
import org.openapitools.client.models.ChatRoles
import org.openapitools.client.models.Message
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

class ChatActivity : ComponentActivity() {

    companion object {
        const val EXTRA_CHAT_ID = "extra_chat_id"
        const val EXTRA_CHAT_TITLE = "extra_chat_title"
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val chatId: UUID? = intent.getStringExtra(EXTRA_CHAT_ID)?.let {
            runCatching { UUID.fromString(it) }.getOrNull()
        }
        val chatTitle: String = intent.getStringExtra(EXTRA_CHAT_TITLE)?.takeIf { it.isNotBlank() }
            ?: "Chat"

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(chatTitle) },
                            navigationIcon = {
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            },
                            actions = {
                                chatId?.let { id ->
                                    IconButton(onClick = {
                                        startActivity(
                                            Intent(this@ChatActivity, ChatSettingsActivity::class.java).apply {
                                                putExtra(ChatSettingsActivity.EXTRA_CHAT_ID, id.toString())
                                            }
                                        )
                                    }) {
                                        Icon(
                                            imageVector = Icons.Filled.Settings,
                                            contentDescription = "Chat settings"
                                        )
                                    }
                                }
                            }
                        )
                    }
                ) { innerPadding ->
                    if (chatId == null) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                                .padding(16.dp)
                        ) {
                            Text(
                                text = "Invalid chat.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        ChatScreen(
                            chatId = chatId,
                            modifier = Modifier.padding(innerPadding)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChatScreen(
    chatId: UUID,
    modifier: Modifier = Modifier,
    viewModel: ChatViewModel = viewModel(
        factory = chatViewModelFactory(chatId, LocalContext.current.applicationContext)
    )
) {
    val uiState by viewModel.uiState.collectAsState()

    LaunchedEffect(Unit) {
        viewModel.loadMessages()
        viewModel.loadSceneImage()
    }

    Box(modifier = modifier.fillMaxSize()) {
        // Scene preview rendered as the chat background.
        val backgroundImageUrl = uiState.sceneImageUrl
        if (backgroundImageUrl != null) {
            AsyncImage(
                model = backgroundImageUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            // Dark scrim so the messages stay legible over any backdrop.
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.55f))
            )
        }

        Column(modifier = Modifier.fillMaxSize()) {
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                if (uiState.isLoading && uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else if (uiState.messages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No messages yet. Say hello!",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(uiState.messages) { index, message ->
                            MessageItem(message = message)
                            if (index < uiState.messages.lastIndex) {
                                HorizontalDivider(
                                    color = Color.White.copy(alpha = 0.15f),
                                    thickness = 1.dp
                                )
                            }
                        }
                    }
                }
            }

            if (uiState.isThinking) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.width(16.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Model is thinking… ${uiState.thinkingSeconds}s",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = uiState.input,
                    onValueChange = viewModel::onInputChanged,
                    label = { Text("Message") },
                    modifier = Modifier.weight(1f),
                    enabled = !uiState.isSending
                )

                Spacer(modifier = Modifier.width(8.dp))

                IconButton(
                    onClick = viewModel::sendMessage,
                    enabled = !uiState.isSending && uiState.input.isNotBlank()
                ) {
                    if (uiState.isSending) {
                        CircularProgressIndicator(modifier = Modifier.width(24.dp))
                    } else {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send"
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun MessageItem(message: Message) {
    val isUser = message.role == ChatRoles.user
    // Model replies arrive as a fenced {"text": ...} envelope; unwrap it to plain
    // prose. User messages render verbatim. Both go through the Markdown renderer,
    // whose custom components then highlight "dialogue" / (asides) and grey *narration*.
    val displayText = if (isUser) message.message else extractModelMessageText(message.message)

    // Full-width message with a darkened transparent background so the scene
    // backdrop still shows through; authorship is carried by the You/Model label.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (isUser) "You" else "Model",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(2.dp))
        Markdown(
            content = displayText,
            colors = markdownColor(text = Color.White),
            components = markdownComponents(
                paragraph = roleplayParagraph,
                text = roleplayText
            ),
            modifier = Modifier.fillMaxWidth()
        )

        val createdText = message.dateCreated?.let { formatMessageTime(it) }
        if (createdText != null) {
            val isEdited = message.dateEdited != null &&
                message.dateEdited != message.dateCreated
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (isEdited) {
                    "$createdText \u00b7 edited ${formatMessageTime(message.dateEdited!!)}"
                } else {
                    createdText
                },
                color = Color.White.copy(alpha = 0.7f),
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

/** Spoken dialogue — text inside `"…"` or `(…)`. */
private val DialogueColor = Color(0xFFD97918)

/** Narration / stage directions — text inside `*…*` (markdown emphasis). */
private val NarrationColor = Color(0xFFB0B0B0)

/** Span applied to narration: `*…*` and the inner text of `(…)`. */
private val narrationSpan = SpanStyle(color = NarrationColor, fontStyle = FontStyle.Italic)

/** Matches `"…"` (group 1) and `(…)` (group 2); only the inner text is colored. */
private val inlineHighlight = Regex("\"([^\"]*)\"|\\(([^)]*)\\)")

/**
 * Layers roleplay styling on top of the markdown-rendered [source], keeping all of
 * markdown's own formatting (bold, lists, links, headings, …) intact:
 *  - `*narration*` reaches us as an italic span → styled [narrationSpan] (grey);
 *  - the inner text of `(…)` is styled the same way as narration;
 *  - the inner text of `"…"` → [DialogueColor] (orange).
 *
 * Quote and parenthesis delimiters keep the base color.
 */
private fun highlightRoleplay(source: AnnotatedString): AnnotatedString = buildAnnotatedString {
    append(source)
    // `*…*` is markdown emphasis → restyle every italic span as narration.
    source.spanStyles.forEach { range ->
        if (range.item.fontStyle == FontStyle.Italic) {
            addStyle(narrationSpan, range.start, range.end)
        }
    }
    for (match in inlineHighlight.findAll(source.text)) {
        val quote = match.groups[1]
        val paren = match.groups[2]
        when {
            quote != null && quote.value.isNotEmpty() ->
                addStyle(SpanStyle(color = DialogueColor), quote.range.first, quote.range.last + 1)
            paren != null && paren.value.isNotEmpty() ->
                addStyle(narrationSpan, paren.range.first, paren.range.last + 1)
        }
    }
}

/**
 * Paragraph / text components that route the model reply through [highlightRoleplay]
 * after the markdown renderer has produced its annotated string — so formatting is
 * kept while dialogue and narration pick up their colors.
 */
private val roleplayParagraph: MarkdownComponent = { model ->
    val style = model.typography.paragraph
    val styled = buildAnnotatedString {
        pushStyle(style.toSpanStyle())
        buildMarkdownAnnotatedString(model.content, model.node)
        pop()
    }
    MarkdownText(highlightRoleplay(styled), modifier = Modifier, style = style)
}

private val roleplayText: MarkdownComponent = { model ->
    val style = model.typography.text
    // getUnescapedTextInNode is internal to the library; slicing the source between
    // the node's offsets gives the raw text, which is correct for a plain TEXT token.
    val raw = model.content.substring(model.node.startOffset, model.node.endOffset)
    val styled = buildAnnotatedString { append(raw) }
    MarkdownText(highlightRoleplay(styled), modifier = Modifier, style = style)
}

private val messageTimeFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMM d, yyyy HH:mm")

private fun formatMessageTime(dateTime: OffsetDateTime): String =
    dateTime.atZoneSameInstant(ZoneId.systemDefault()).format(messageTimeFormatter)

private fun chatViewModelFactory(chatId: UUID, appContext: Context): ViewModelProvider.Factory =
    object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return ChatViewModel(
                chatId = chatId,
                modelFlow = ChatModelStore.modelFlow(appContext, chatId)
            ) as T
        }
    }
