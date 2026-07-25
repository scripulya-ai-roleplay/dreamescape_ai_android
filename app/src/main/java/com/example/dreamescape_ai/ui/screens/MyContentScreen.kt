package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.dreamescape_ai.MyContentUiState
import com.example.dreamescape_ai.MyContentViewModel
import com.example.dreamescape_ai.OwnedCard
import com.example.dreamescape_ai.OwnedMode
import com.example.dreamescape_ai.myContentViewModelFactory
import com.example.dreamescape_ai.ui.components.OwnedCard as OwnedCardView
import com.example.dreamescape_ai.ui.theme.ScripulyaText
import com.example.dreamescape_ai.ui.theme.nightSkyGradient

/**
 * Thin host that owns the [MyContentViewModel] for the given [mode] and wires it
 * to [MyContentScreen]. Activities render this inside their Scaffold.
 */
@Composable
fun MyContentHost(
    mode: OwnedMode,
    onItemClick: (OwnedCard) -> Unit,
    onEdit: (OwnedCard) -> Unit,
    modifier: Modifier = Modifier
) {
    val viewModel: MyContentViewModel = viewModel(factory = myContentViewModelFactory(mode))
    val uiState by viewModel.uiState.collectAsState()
    MyContentScreen(
        uiState = uiState,
        noun = if (mode == OwnedMode.CHARACTERS) "Character" else "Scene",
        onRetry = viewModel::load,
        onItemClick = onItemClick,
        onEdit = onEdit,
        onDelete = { card -> viewModel.deleteItem(card.id) },
        onLoadMore = viewModel::loadMore,
        modifier = modifier
    )
}

/**
 * Reusable 2-column "waterfall" of the user's owned scenes or characters — the
 * same layout as discovery's Recently Released, but each preview carries a
 * Private/Public badge. Paginates via [onLoadMore] as the user nears the bottom.
 */
@Composable
fun MyContentScreen(
    uiState: MyContentUiState,
    noun: String,
    onRetry: () -> Unit,
    onItemClick: (OwnedCard) -> Unit,
    onEdit: (OwnedCard) -> Unit,
    onDelete: (OwnedCard) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

    // The card whose tap action menu is open, and the card awaiting a
    // delete confirmation. Held by id/object rather than index so pagination
    // can't shift them out from under the menu.
    var menuCardId by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<OwnedCard?>(null) }

    val nearBottom by remember {
        derivedStateOf {
            val layout = gridState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 4
        }
    }
    LaunchedEffect(nearBottom, uiState.items.size) {
        if (nearBottom && uiState.hasMore && !uiState.isLoadingMore) onLoadMore()
    }

    Box(modifier = modifier.fillMaxSize().nightSkyGradient()) {
        when {
            uiState.isLoading && uiState.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = ScripulyaText)
                }

            uiState.errorMessage != null && uiState.items.isEmpty() ->
                ErrorState(message = uiState.errorMessage!!, onRetry = onRetry)

            uiState.items.isEmpty() ->
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Nothing here yet",
                        color = ScripulyaText.copy(alpha = 0.6f),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

            else -> LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                state = gridState,
                contentPadding = PaddingValues(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(uiState.items, key = { it.id }) { card ->
                    Box {
                        OwnedCardView(
                            title = card.title,
                            subtitle = card.subtitle,
                            imageUrl = card.imageUrl,
                            isPublic = card.isPublic,
                            onClick = { menuCardId = card.id }
                        )
                        // Tap action menu, anchored to this card.
                        DropdownMenu(
                            expanded = menuCardId == card.id,
                            onDismissRequest = { menuCardId = null }
                        ) {
                            DropdownMenuItem(
                                text = { Text("View $noun") },
                                onClick = {
                                    menuCardId = null
                                    onItemClick(card)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Edit $noun") },
                                onClick = {
                                    menuCardId = null
                                    onEdit(card)
                                }
                            )
                            DropdownMenuItem(
                                text = {
                                    Text(
                                        text = "Delete $noun",
                                        color = MaterialTheme.colorScheme.error
                                    )
                                },
                                onClick = {
                                    menuCardId = null
                                    pendingDelete = card
                                }
                            )
                        }
                    }
                }
                if (uiState.isLoadingMore) {
                    item(span = { GridItemSpan(2) }) {
                        Box(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = ScripulyaText)
                        }
                    }
                }
            }
        }

        // Confirm before deleting — it's irreversible on the backend.
        pendingDelete?.let { card ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Delete $noun") },
                text = { Text("Delete \"${card.title}\"? This can't be undone.") },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(card)
                        pendingDelete = null
                    }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = message,
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
    }
}
