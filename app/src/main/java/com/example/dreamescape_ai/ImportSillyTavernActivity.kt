package com.example.dreamescape_ai

import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.Dreamescape_aiTheme
import com.example.dreamescape_ai.ui.theme.ScripulyaText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.openapitools.client.models.ImportCandidateDTO

class ImportSillyTavernActivity : ComponentActivity() {

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
                            title = { Text("Import from SillyTavern") },
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
                    ImportSillyTavernScreen(
                        modifier = Modifier.padding(innerPadding),
                        onDone = { finish() }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportSillyTavernScreen(
    modifier: Modifier = Modifier,
    onDone: () -> Unit,
    viewModel: ImportSillyTavernViewModel = viewModel(),
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsState()

    val pickFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            // Copying the stream to a cache file is IO; do it off the main thread.
            scope.launch {
                val name = withContext(Dispatchers.IO) { displayName(context, uri) }
                val file = withContext(Dispatchers.IO) { SillyTavernImporter.copyToFile(context, uri) }
                viewModel.onFilePicked(name, file)
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp)
    ) {
        when (uiState.phase) {
            ImportPhase.DONE -> DoneView(uiState, onDone = onDone, onAnother = {
                viewModel.reset()
                pickFile.launch(arrayOf("application/json", "*/*"))
            })
            else -> PreviewView(
                state = uiState,
                onPick = { pickFile.launch(arrayOf("application/json", "*/*")) },
                onToggleCharacter = viewModel::toggleCharacter,
                onToggleScene = viewModel::toggleScene,
                onAllCharacters = viewModel::setAllCharacters,
                onAllScenes = viewModel::setAllScenes,
                onImportImages = viewModel::setImportImages,
                onIsPublic = viewModel::setIsPublic,
                onSelectAttachTarget = viewModel::selectAttachTarget,
                onImport = viewModel::doImport,
            )
        }
    }
}

@Composable
private fun PreviewView(
    state: ImportSillyTavernUiState,
    onPick: () -> Unit,
    onToggleCharacter: (String) -> Unit,
    onToggleScene: (String) -> Unit,
    onAllCharacters: (Boolean) -> Unit,
    onAllScenes: (Boolean) -> Unit,
    onImportImages: (Boolean) -> Unit,
    onIsPublic: (Boolean) -> Unit,
    onSelectAttachTarget: (AttachTarget?) -> Unit,
    onImport: () -> Unit,
) {
    val hasCandidates = state.characters.isNotEmpty() || state.scenes.isNotEmpty()
    val selectedCount = state.selectedCharacterKeys.size + state.selectedSceneKeys.size
    val importEnabled = !state.isLoading && (selectedCount > 0 || state.attaching)

    if (state.isLoading) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        ) {
            CircularProgressIndicator()
            Text("Reading file…", color = ScripulyaText)
        }
    }

    state.errorMessage?.let { msg ->
        Spacer(Modifier.height(8.dp))
        Text(
            text = msg,
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().scripPanel().padding(12.dp)
        )
    }

    Spacer(Modifier.height(8.dp))

    if (!hasCandidates) {
        // Idle / nothing parsed yet.
        Text(
            "Import characters and scenes from a SillyTavern World Info or character-card JSON file. " +
                "You'll choose which to keep, and they're imported unlinked.",
            color = ScripulyaText.copy(alpha = 0.8f),
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.fillMaxWidth().scripPanel().padding(16.dp)
        )
        Spacer(Modifier.height(16.dp))
        Button(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choose JSON file") }
        return
    }

    Text(
        text = state.fileName ?: "File",
        color = ScripulyaText,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(Modifier.height(12.dp))

    CandidateSection(
        title = "Characters",
        candidates = state.characters,
        selectedKeys = state.selectedCharacterKeys,
        onToggle = onToggleCharacter,
        onSelectAll = onAllCharacters,
    )
    Spacer(Modifier.height(12.dp))
    CandidateSection(
        title = "Scenes",
        candidates = state.scenes,
        selectedKeys = state.selectedSceneKeys,
        onToggle = onToggleScene,
        onSelectAll = onAllScenes,
    )

    if (state.otherEntries > 0) {
        Spacer(Modifier.height(8.dp))
        Text(
            "${state.otherEntries} other lore entries are present but aren't selectable.",
            color = ScripulyaText.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }

    if (state.attachTargets.isNotEmpty()) {
        Spacer(Modifier.height(16.dp))
        AttachTargetSection(
            targets = state.attachTargets,
            selected = state.attachToCharacter,
            onSelect = onSelectAttachTarget,
        )
    }

    Spacer(Modifier.height(16.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Switch(checked = state.importImages, onCheckedChange = onImportImages)
        Spacer(Modifier.width(8.dp))
        Text("Import images", color = ScripulyaText)
    }
    Spacer(Modifier.height(8.dp))
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Switch(checked = state.isPublic, onCheckedChange = onIsPublic)
        Spacer(Modifier.width(8.dp))
        Text("Public", color = ScripulyaText)
    }

    Spacer(Modifier.height(20.dp))
    Button(
        onClick = onImport,
        enabled = importEnabled,
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            when {
                state.attaching -> "Add to ${state.attachToCharacter?.name ?: "character"}"
                selectedCount > 0 -> "Import $selectedCount selected"
                else -> "Import selected"
            }
        )
    }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onPick, modifier = Modifier.fillMaxWidth()) { Text("Choose a different file") }
}

@Composable
private fun AttachTargetSection(
    targets: List<AttachTarget>,
    selected: AttachTarget?,
    onSelect: (AttachTarget?) -> Unit,
) {
    Text(
        "Add to an existing character (optional)",
        color = ScripulyaText,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
    Text(
        "Instead of importing as new characters/scenes, append this lorebook's " +
            "content to one of your characters' prompts. Leave unselected for a normal import.",
        color = ScripulyaText.copy(alpha = 0.6f),
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(vertical = 4.dp)
    )
    targets.forEach { target ->
        val checked = selected?.id == target.id
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .scripPanel()
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = { onSelect(target) })
            Spacer(Modifier.width(8.dp))
            Text(target.name, color = ScripulyaText)
        }
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
private fun CandidateSection(
    title: String,
    candidates: List<ImportCandidateDTO>,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit,
    onSelectAll: (Boolean) -> Unit,
) {
    Text(
        "$title (${candidates.size})",
        color = ScripulyaText,
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.titleSmall
    )
    if (candidates.isEmpty()) {
        Text(
            "None found.",
            color = ScripulyaText.copy(alpha = 0.5f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(vertical = 4.dp)
        )
        return
    }
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedButton(onClick = { onSelectAll(true) }, modifier = Modifier.weight(1f)) { Text("All") }
        OutlinedButton(onClick = { onSelectAll(false) }, modifier = Modifier.weight(1f)) { Text("None") }
    }
    candidates.forEach { candidate ->
        val checked = candidate.key in selectedKeys
        Row(
            verticalAlignment = Alignment.Top,
            modifier = Modifier
                .fillMaxWidth()
                .scripPanel()
                .padding(12.dp)
        ) {
            Checkbox(checked = checked, onCheckedChange = { onToggle(candidate.key) })
            Spacer(Modifier.width(8.dp))
            Column {
                Text(candidate.name, color = ScripulyaText, fontWeight = FontWeight.SemiBold)
                if (candidate.contentPreview.isNotBlank()) {
                    Text(
                        candidate.contentPreview,
                        color = ScripulyaText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodySmall,
                        maxLines = 3
                    )
                }
                if (candidate.imageCount > 0) {
                    Text(
                        "${candidate.imageCount} image(s)",
                        color = ScripulyaText.copy(alpha = 0.5f),
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun DoneView(
    state: ImportSillyTavernUiState,
    onDone: () -> Unit,
    onAnother: () -> Unit,
) {
    val result = state.result ?: return
    Text("Import complete", color = ScripulyaText, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.headlineSmall)
    Spacer(Modifier.height(12.dp))
    Column(
        modifier = Modifier.fillMaxWidth().scripPanel().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (result.appendedToCharacterId != null) {
            Text(
                "Lorebook added to ${state.attachToCharacter?.name ?: "your character"}'s prompt.",
                color = ScripulyaText
            )
        }
        if (result.charactersCreated > 0) Text("${result.charactersCreated} character(s) imported", color = ScripulyaText)
        if (result.scenesCreated > 0) Text("${result.scenesCreated} scene(s) imported", color = ScripulyaText)
        if (result.imagesImported > 0) Text("${result.imagesImported} image(s) imported", color = ScripulyaText)
        if (result.imageFailures.isNotEmpty()) {
            Text("${result.imageFailures.size} image(s) failed to import", color = MaterialTheme.colorScheme.error)
        }
        if (result.skippedEntries > 0) {
            Text("${result.skippedEntries} entr${if (result.skippedEntries == 1) "y" else "ies"} skipped", color = ScripulyaText.copy(alpha = 0.6f))
        }
    }
    Spacer(Modifier.height(12.dp))
    if (result.appendedToCharacterId == null) {
        Text(
            "Imported unlinked — link characters to scenes from a scene's characters screen.",
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium
        )
    }
    Spacer(Modifier.height(20.dp))
    Button(onClick = onDone, modifier = Modifier.fillMaxWidth()) { Text("Done") }
    Spacer(Modifier.height(8.dp))
    OutlinedButton(onClick = onAnother, modifier = Modifier.fillMaxWidth()) { Text("Import another file") }
}

private fun displayName(context: Context, uri: Uri): String {
    context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
        val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx) ?: "sillytavern.json"
    }
    return uri.lastPathSegment ?: "sillytavern.json"
}
