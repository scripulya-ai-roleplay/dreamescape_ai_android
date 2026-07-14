package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.DiscoveryViewModel
import com.example.dreamescape_ai.model.FeedSection
import com.example.dreamescape_ai.model.StoryItem
import com.example.dreamescape_ai.ui.components.SectionHeader
import com.example.dreamescape_ai.ui.components.StoryCard
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Screen 1 — Home / Discovery. A vertically scrolling page of horizontal
 * story carousels, one per [FeedSection]. The caller filters which sections to
 * show (Home vs Discover), so the same screen backs both tabs.
 *
 * "Recently Released" is special: instead of a carousel it is rendered last as
 * a paginated 2-column waterfall. Its stories arrive in creation order (the
 * backend's natural listing order) and keep streaming in as the user scrolls
 * down — see [DiscoveryViewModel.loadMoreRecent].
 */
@Composable
fun DiscoveryScreen(
    sections: List<FeedSection>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onStoryClick: (StoryItem) -> Unit,
    onOpenHistory: () -> Unit,
    recentHasMore: Boolean = false,
    recentIsLoadingMore: Boolean = false,
    onLoadMoreRecent: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val allEmpty = sections.none { it.stories.isNotEmpty() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && allEmpty -> LoadingState()
            errorMessage != null && allEmpty -> ErrorState(errorMessage, onRetry)
            allEmpty -> EmptyState()
            else -> DiscoveryContent(
                sections = sections,
                onStoryClick = onStoryClick,
                onOpenHistory = onOpenHistory,
                recentHasMore = recentHasMore,
                recentIsLoadingMore = recentIsLoadingMore,
                onLoadMoreRecent = onLoadMoreRecent
            )
        }
    }
}

@Composable
private fun DiscoveryContent(
    sections: List<FeedSection>,
    onStoryClick: (StoryItem) -> Unit,
    onOpenHistory: () -> Unit,
    recentHasMore: Boolean,
    recentIsLoadingMore: Boolean,
    onLoadMoreRecent: () -> Unit
) {
    val carouselSections = sections.filter { it.title != DiscoveryViewModel.SECTION_RECENT }
    val recentSection = sections.firstOrNull { it.title == DiscoveryViewModel.SECTION_RECENT }

    val listState = rememberLazyListState()

    // Pull the next page once the user nears the bottom of the list.
    val nearBottom by remember {
        derivedStateOf {
            val layout = listState.layoutInfo
            val lastVisible = layout.visibleItemsInfo.lastOrNull()?.index ?: return@derivedStateOf false
            lastVisible >= layout.totalItemsCount - 3
        }
    }
    LaunchedEffect(nearBottom, recentSection?.stories?.size) {
        if (nearBottom && recentSection != null && recentHasMore && !recentIsLoadingMore) {
            onLoadMoreRecent()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 96.dp) // clearance for the floating nav bar
    ) {
        item(key = "header") {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 20.dp, end = 20.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Discover",
                    color = ScripulyaText,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Your history",
                    color = ScripulyaText.copy(alpha = 0.7f),
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.clickable(onClick = onOpenHistory)
                )
            }
        }

        carouselSections.filter { it.stories.isNotEmpty() }.forEach { section ->
            item(key = "carousel_${section.title}") {
                Column {
                    SectionHeader(
                        title = section.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 12.dp)
                    )
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 20.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        items(section.stories, key = { it.id }) { story ->
                            StoryCard(
                                story = story,
                                onClick = { onStoryClick(story) },
                                modifier = Modifier.width(168.dp)
                            )
                        }
                    }
                }
            }
        }

        // Recently Released — paginated waterfall pinned to the bottom.
        recentSection?.let { recent ->
            if (recent.stories.isNotEmpty()) {
                item(key = "recent_header") {
                    SectionHeader(
                        title = recent.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 12.dp)
                    )
                }
                items(
                    items = recent.stories.chunked(2),
                    key = { row -> row.first().id }
                ) { row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        row.forEach { story ->
                            StoryCard(
                                story = story,
                                onClick = { onStoryClick(story) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        // Odd row: keep the lone card half-width with a spacer.
                        if (row.size == 1) {
                            Spacer(Modifier.weight(1f))
                        }
                    }
                }
                if (recentIsLoadingMore) {
                    item(key = "recent_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp),
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
private fun LoadingState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator(color = ScripulyaText)
    }
}

@Composable
private fun ErrorState(message: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Couldn't load stories",
            color = ScripulyaText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Text(
            text = message,
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodySmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 8.dp)
        )
        Button(onClick = onRetry) { Text("Retry") }
    }
}

@Composable
private fun EmptyState() {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "No stories yet",
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.bodyLarge
        )
    }
}
