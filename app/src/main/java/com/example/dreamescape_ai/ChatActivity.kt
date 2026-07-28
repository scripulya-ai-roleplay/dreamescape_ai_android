package com.example.dreamescape_ai

import android.os.Bundle
import android.content.Context
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
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
                    // The bottom inset (nav bar ∪ IME) is consumed once inside ChatScreen,
                    // so don't let the Scaffold also pad the body for the nav bar.
                    contentWindowInsets = WindowInsets(0, 0, 0, 0),
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

    // The message currently open in the Edit dialog, or the one awaiting delete
    // confirmation. Both are set by tapping a message bubble.
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var pendingDelete by remember { mutableStateOf<Message?>(null) }

    LaunchedEffect(Unit) {
        viewModel.loadMessages()
        viewModel.loadChat()
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

        // A single bottom-inset source — max(navigation bar, IME) — so the input
        // dock lifts flush above the keyboard (no gap) and sits above the gesture/nav
        // bar when it's closed. Combined with adjustResize in the manifest, this is the
        // one place the IME inset is consumed, so it never double-lifts.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.union(WindowInsets.ime))
        ) {
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                )
            }

            Box(modifier = Modifier.weight(1f)) {
                when {
                    uiState.isLoading && uiState.messages.isEmpty() && !uiState.needsInitialMessage -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    uiState.needsInitialMessage -> {
                        // Opening-greeting carousel: the scene's greetings render like
                        // a normal model message, with transparent < / > controls and an
                        // n/total counter. Sending the first reply commits the shown one.
                        LazyColumn(modifier = Modifier.fillMaxSize(), reverseLayout = true) {
                            item {
                                val greeting = uiState.sceneInitialMessages
                                    .getOrNull(uiState.currentInitialMessageIndex)
                                if (greeting != null) {
                                    InitialMessageCarouselItem(
                                        text = greeting.text,
                                        index = uiState.currentInitialMessageIndex,
                                        total = uiState.sceneInitialMessages.size,
                                        enabled = !uiState.isSending,
                                        onPrevious = viewModel::selectPreviousInitialMessage,
                                        onNext = viewModel::selectNextInitialMessage
                                    )
                                }
                            }
                            itemsIndexed(uiState.messages.asReversed()) { index, message ->
                                MessageItem(
                                    message = message,
                                    onEdit = { m -> editingMessage = m },
                                    onDelete = { m -> pendingDelete = m }
                                )
                                if (index < uiState.messages.lastIndex) {
                                    HorizontalDivider(
                                        color = Color.White.copy(alpha = 0.15f),
                                        thickness = 1.dp
                                    )
                                }
                            }
                        }
                    }
                    uiState.messages.isEmpty() -> {
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
                    }
                    else -> {
                        LazyColumn(modifier = Modifier.fillMaxSize(), reverseLayout = true) {
                            // The in-flight reply, streamed token by token. reverseLayout
                            // places this first item at the bottom (newest position); the
                            // growing bubble replaces the "thinking" spinner once tokens
                            // arrive, and is swapped for the real message after the reload.
                            if (uiState.streamingText.isNotEmpty()) {
                                item(key = "streaming-draft") {
                                    MessageItem(
                                        message = Message(
                                            message = uiState.streamingText,
                                            chatId = chatId,
                                            role = ChatRoles.model
                                        ),
                                        // Live chain-of-thought accumulated from `thinking`
                                        // SSE frames; shown behind the collapsible disclosure.
                                        thoughts = uiState.streamingThinking
                                    )
                                }
                            }
                            itemsIndexed(uiState.messages.asReversed()) { index, message ->
                                MessageItem(
                                    message = message,
                                    onEdit = { m -> editingMessage = m },
                                    onDelete = { m -> pendingDelete = m }
                                )
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

            // Thin white line splitting the swipe panel from the text entry panel so
            // the two same-colored bands stay distinguishable (only while the opening
            // greeting carousel is shown).
            if (uiState.needsInitialMessage) {
                HorizontalDivider(
                    color = Color.White.copy(alpha = 0.4f),
                    thickness = 1.dp
                )
            }

            // Quick-action toolbar above the input bar: Continue prompts the model to
            // keep going (posted as a normal message that triggers a streamed reply),
            // Erase removes the most recent message. Both are inert while a generation
            // is in flight and only shown once the thread has at least one message.
            if (uiState.messages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(
                        onClick = viewModel::continueConversation,
                        enabled = !uiState.isSending
                    ) {
                        Text("Continue", color = Color.White)
                    }
                    TextButton(
                        onClick = {
                            uiState.messages.lastOrNull()?.id?.let { viewModel.deleteMessage(it) }
                        },
                        enabled = !uiState.isSending
                    ) {
                        Text("Erase", color = Color.White)
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.45f))
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

        // Edit dialog: pre-filled with the bubble's visible text (unwrapped for model
        // replies), saving plain prose back via the ViewModel.
        editingMessage?.let { message ->
            EditMessageDialog(
                initialText = if (message.role == ChatRoles.user) {
                    message.message
                } else {
                    extractModelMessageText(message.message)
                },
                onConfirm = { newText ->
                    val id = message.id
                    editingMessage = null
                    if (id != null) viewModel.editMessage(id, newText)
                },
                onDismiss = { editingMessage = null }
            )
        }

        // Delete is irreversible on the backend — confirm before sending.
        pendingDelete?.let { message ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete message") },
                text = { Text("Delete this message? This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        val id = message.id
                        pendingDelete = null
                        if (id != null) viewModel.deleteMessage(id)
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) { Text("Cancel") }
                }
            )
        }
    }
}

/**
 * The opening-greeting carousel shown when a chat has no chosen greeting. Renders
 * the current scene greeting identically to a normal model message (same "Model"
 * label, darkened background, markdown + roleplay styling), but adds a transparent
 * `<` / `>` pair and an `n/total` counter to browse the scene's greetings. The
 * shown greeting is committed when the user sends their first reply.
 */
@Composable
fun InitialMessageCarouselItem(
    text: String,
    index: Int,
    total: Int,
    enabled: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit
) {
    val displayText = extractModelMessageText(text)
    Column(modifier = Modifier.fillMaxWidth()) {
        // The greeting, rendered exactly like a normal model message: "Model"
        // label, darkened background, markdown + roleplay styling — no controls.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Model",
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
        }
        // Swipe panel centered under the greeting, on the same darkened background
        // as the messages. Transparent < / > with only the characters visible, and an
        // n/total counter. Disabled (dimmed) at the bounds or while a send is in flight.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.45f))
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "<",
                color = if (index > 0 && enabled) Color.White else Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable { if (index > 0 && enabled) onPrevious() }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
            Text(
                text = "${index + 1}/$total",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(horizontal = 8.dp)
            )
            Text(
                text = ">",
                color = if (index < total - 1 && enabled) Color.White else Color.White.copy(alpha = 0.3f),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier
                    .clickable { if (index < total - 1 && enabled) onNext() }
                    .padding(horizontal = 12.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
fun MessageItem(
    message: Message,
    thoughts: String? = null,
    onEdit: ((Message) -> Unit)? = null,
    onDelete: ((Message) -> Unit)? = null
) {
    val isUser = message.role == ChatRoles.user
    // Model replies arrive as a fenced {"text": ...} envelope; unwrap it to plain
    // prose. User messages render verbatim. Both go through the Markdown renderer,
    // whose custom components then highlight "dialogue" / (asides) and grey *narration*.
    val displayText = if (isUser) message.message else extractModelMessageText(message.message)
    // Live chain-of-thought is passed in for the in-flight streaming bubble; persisted
    // messages carry it as `reasoning`. A non-blank value from either source surfaces the
    // collapsible disclosure at the top of the bubble (collapsed by default).
    val reasoning = thoughts?.takeIf { it.isNotBlank() }
        ?: message.reasoning?.takeIf { it.isNotBlank() }

    // Only persisted messages (those with an id) can be edited/deleted; the in-flight
    // streaming draft passes no callbacks and stays non-interactive.
    val canInteract = onEdit != null && onDelete != null && message.id != null
    var menuExpanded by remember { mutableStateOf(false) }

    // Full-width message with a darkened transparent background so the scene
    // backdrop still shows through; authorship is carried by the You/Model label.
    // Tapping a bubble opens the Edit/Delete menu anchored to it.
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.45f))
            .then(if (canInteract) Modifier.clickable { menuExpanded = true } else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        Text(
            text = if (isUser) "You" else "Model",
            color = Color.White,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold
        )
        if (reasoning != null) {
            Spacer(modifier = Modifier.height(4.dp))
            ThoughtsDisclosure(reasoning)
        }
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

        if (canInteract) {
            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                DropdownMenuItem(
                    text = { Text("Edit") },
                    onClick = {
                        menuExpanded = false
                        onEdit?.invoke(message)
                    }
                )
                DropdownMenuItem(
                    text = {
                        Text(
                            text = "Delete",
                            color = MaterialTheme.colorScheme.error
                        )
                    },
                    onClick = {
                        menuExpanded = false
                        onDelete?.invoke(message)
                    }
                )
            }
        }
    }
}

/**
 * Modal editor for a single message. Pre-filled with the bubble's visible text and
 * saves the trimmed result via [onConfirm] (which routes to the ViewModel); the field
 * grows with the content so long messages stay editable.
 */
@Composable
private fun EditMessageDialog(
    initialText: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initialText) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit message") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                minLines = 3
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = text.isNotBlank()
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

/**
 * The model's chain-of-thought, hidden by default behind a disclosure at the top of a
 * message. A chevron (▶) toggles open/closed; tapping it expands to render the thoughts
 * (muted, to read as meta-commentary rather than in-character prose) and tapping again
 * collapses them back inside the message.
 */
@Composable
private fun ThoughtsDisclosure(thoughts: String, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 90f else 0f,
        label = "thoughts-chevron"
    )
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded },
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = if (expanded) "Hide thoughts" else "Show thoughts",
                tint = ThoughtsColor,
                modifier = Modifier.rotate(rotation)
            )
            Text(
                text = "Thoughts",
                color = ThoughtsColor,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold
            )
        }
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Markdown(
                content = thoughts,
                colors = markdownColor(text = ThoughtsColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp)
            )
        }
    }
}

/** Spoken dialogue — text inside `"…"` or `(…)`. */
private val DialogueColor = Color(0xFFD97918)

/** Muted tone for the model's chain-of-thought, so it reads as meta-commentary. */
private val ThoughtsColor = Color.White.copy(alpha = 0.6f)

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
