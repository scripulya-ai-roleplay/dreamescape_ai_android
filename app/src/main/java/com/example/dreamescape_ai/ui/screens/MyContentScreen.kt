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
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
    modifier: Modifier = Modifier
) {
    val viewModel: MyContentViewModel = viewModel(factory = myContentViewModelFactory(mode))
    val uiState by viewModel.uiState.collectAsState()
    MyContentScreen(
        uiState = uiState,
        onRetry = viewModel::load,
        onItemClick = onItemClick,
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
    onRetry: () -> Unit,
    onItemClick: (OwnedCard) -> Unit,
    onLoadMore: () -> Unit,
    modifier: Modifier = Modifier
) {
    val gridState = rememberLazyGridState()

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
                    OwnedCardView(
                        title = card.title,
                        subtitle = card.subtitle,
                        imageUrl = card.imageUrl,
                        isPublic = card.isPublic,
                        onClick = { onItemClick(card) }
                    )
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
