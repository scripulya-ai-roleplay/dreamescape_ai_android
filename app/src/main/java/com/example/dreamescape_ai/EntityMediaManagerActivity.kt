package com.example.dreamescape_ai

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import org.openapitools.client.models.MediaAssetDTO
import org.openapitools.client.models.MediaEntityType
import org.openapitools.client.models.MediaLayer
import java.util.UUID

/**
 * Per-entity image manager: all images attached to one character or scene,
 * each with a small View/Delete popup, an editor dialog (caption, layer,
 * move up/down), and an add-images button. Reached from the preview screens.
 */
class EntityMediaManagerActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ENTITY_TYPE = "extra_entity_type"
        const val EXTRA_ENTITY_ID = "extra_entity_id"
        const val EXTRA_TITLE = "extra_title"

        fun intent(context: Context, entityType: MediaEntityType, entityId: UUID, title: String): Intent =
            Intent(context, EntityMediaManagerActivity::class.java).apply {
                putExtra(EXTRA_ENTITY_TYPE, entityType.value)
                putExtra(EXTRA_ENTITY_ID, entityId.toString())
                putExtra(EXTRA_TITLE, title)
            }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val entityType = intent.getStringExtra(EXTRA_ENTITY_TYPE)
            ?.let { value -> MediaEntityType.values().firstOrNull { it.value == value } }
        val entityId = intent.getStringExtra(EXTRA_ENTITY_ID)
            ?.let { runCatching { UUID.fromString(it) }.getOrNull() }
        val title = intent.getStringExtra(EXTRA_TITLE) ?: "Images"

        val context = this

        setContent {
            Dreamescape_aiTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        TopAppBar(
                            title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
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
                    if (entityType == null || entityId == null) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No entity selected.",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyLarge
                            )
                        }
                    } else {
                        EntityMediaManagerScreen(
                            entityType = entityType,
                            entityId = entityId,
                            modifier = Modifier.fillMaxSize().padding(innerPadding),
                            viewModelFactory = remember(entityType, entityId) {
                                entityMediaManagerViewModelFactory(context, entityType, entityId)
                            }
                        )
                    }
                }
            }
        }
    }
}

private fun entityMediaManagerViewModelFactory(
    context: Context,
    entityType: MediaEntityType,
    entityId: UUID
): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return EntityMediaManagerViewModel(
            entityType = entityType,
            entityId = entityId,
            // Scenes publish their media; character visibility follows the
            // character's is_public flag — matching the create/edit upload flows.
            uploadMediaCall = { type, id, uri ->
                MediaUploader.uploadUri(
                    context,
                    Uri.parse(uri),
                    type,
                    id,
                    isPublic = type != MediaEntityType.character
                )
            }
        ) as T
    }
}

@Composable
fun EntityMediaManagerScreen(
    entityType: MediaEntityType,
    entityId: UUID,
    modifier: Modifier = Modifier,
    viewModelFactory: ViewModelProvider.Factory,
    viewModel: EntityMediaManagerViewModel = viewModel(factory = viewModelFactory)
) {
    val uiState by viewModel.uiState.collectAsState()

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris ->
        if (uris.isNotEmpty()) viewModel.upload(uris.map { it.toString() })
    }

    Column(modifier = modifier.padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.errorMessage != null) {
                Text(
                    text = uiState.errorMessage!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            TextButton(
                enabled = !uiState.isUploading,
                onClick = {
                    picker.launch(
                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                    )
                }
            ) {
                if (uiState.isUploading) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Uploading…")
                } else {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Add images")
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        when {
            uiState.isLoading && uiState.media.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            uiState.media.isEmpty() -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No images yet", style = MaterialTheme.typography.bodyLarge)
                }
            }
            else -> {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.media, key = { it.id }) { asset ->
                        MediaTile(
                            asset = asset,
                            onView = { viewModel.openEditor(asset) },
                            onDelete = { viewModel.requestDelete(asset) }
                        )
                    }
                }
            }
        }
    }

    // Delete confirmation
    uiState.pendingDelete?.let { asset ->
        AlertDialog(
            onDismissRequest = { viewModel.cancelDelete() },
            title = { Text("Delete image?") },
            text = { Text("This image will be permanently removed.") },
            confirmButton = {
                TextButton(onClick = { viewModel.confirmDelete() }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.cancelDelete() }) { Text("Cancel") }
            }
        )
    }

    // Per-image editor
    uiState.editorDraft?.let { draft ->
        val index = uiState.media.indexOfFirst { it.id == draft.asset.id }
        MediaEditorDialog(
            draft = draft,
            isFirst = index <= 0,
            isLast = index == -1 || index >= uiState.media.size - 1,
            onCaptionChange = viewModel::updateEditorCaption,
            onLayerChange = viewModel::updateEditorLayer,
            onMoveUp = { viewModel.moveUp(draft.asset) },
            onMoveDown = { viewModel.moveDown(draft.asset) },
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::saveEditor
        )
    }
}

/** One grid cell: the image plus an overflow menu with View and Delete. */
@Composable
private fun MediaTile(
    asset: MediaAssetDTO,
    onView: () -> Unit,
    onDelete: () -> Unit
) {
    var menuOpen by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Box {
            AsyncImage(
                model = asset.url,
                contentDescription = asset.caption,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
            if (asset.layer == MediaLayer.foreground) {
                Text(
                    text = "Foreground",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        .clip(CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            if (asset.caption != null) {
                Text(
                    text = asset.caption!!,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.7f))
                        .clip(CircleShape)
                        .padding(horizontal = 8.dp, vertical = 2.dp)
                )
            }
            IconButton(
                onClick = { menuOpen = true },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = "Image menu",
                    tint = MaterialTheme.colorScheme.onPrimary
                )
            }
            // The small popup with exactly the two per-image actions.
            DropdownMenu(
                expanded = menuOpen,
                onDismissRequest = { menuOpen = false }
            ) {
                DropdownMenuItem(
                    text = { Text("View") },
                    onClick = {
                        menuOpen = false
                        onView()
                    }
                )
                DropdownMenuItem(
                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                    onClick = {
                        menuOpen = false
                        onDelete()
                    }
                )
            }
        }
    }
}

/** Editor: full image, caption (≤200), layer toggle, move up/down. */
@Composable
private fun MediaEditorDialog(
    draft: MediaEditorDraft,
    isFirst: Boolean,
    isLast: Boolean,
    onCaptionChange: (String) -> Unit,
    onLayerChange: (MediaLayer) -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onDismiss: () -> Unit,
    onSave: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit image") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                AsyncImage(
                    model = draft.asset.url,
                    contentDescription = draft.caption,
                    // Fit (not Crop) so transparent foreground PNGs keep their shape.
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )

                OutlinedTextField(
                    value = draft.caption,
                    onValueChange = onCaptionChange,
                    label = { Text("Caption") },
                    supportingText = {
                        Text("${draft.caption.length}/${EntityMediaManagerViewModel.CAPTION_MAX_LENGTH}")
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Layer", style = MaterialTheme.typography.labelLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = draft.layer == MediaLayer.background,
                        onClick = { onLayerChange(MediaLayer.background) },
                        label = { Text("Background") }
                    )
                    FilterChip(
                        selected = draft.layer == MediaLayer.foreground,
                        onClick = { onLayerChange(MediaLayer.foreground) },
                        label = { Text("Foreground") }
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(enabled = !isFirst, onClick = onMoveUp) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Up")
                    }
                    OutlinedButton(enabled = !isLast, onClick = onMoveDown) {
                        Icon(Icons.Filled.ArrowDownward, contentDescription = null)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Down")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onSave) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
