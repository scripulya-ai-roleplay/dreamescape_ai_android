package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.model.FeedSection
import com.example.dreamescape_ai.model.StoryItem
import com.example.dreamescape_ai.ui.components.SectionHeader
import com.example.dreamescape_ai.ui.components.StoryCard
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Screen 1 — Home / Discovery. A vertically scrolling page of horizontal
 * story carousels, one per [FeedSection]. The caller filters which sections to
 * show (Home vs Discover), so the same screen backs both tabs. The section list
 * is rendered config-driven to keep the tree flat.
 */
@Composable
fun DiscoveryScreen(
    sections: List<FeedSection>,
    isLoading: Boolean,
    errorMessage: String?,
    onRetry: () -> Unit,
    onStoryClick: (StoryItem) -> Unit,
    onOpenHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val allEmpty = sections.none { it.stories.isNotEmpty() }

    Box(modifier = modifier.fillMaxSize()) {
        when {
            isLoading && allEmpty -> LoadingState()
            errorMessage != null && allEmpty -> ErrorState(errorMessage, onRetry)
            allEmpty -> EmptyState()
            else -> DiscoveryContent(sections, onStoryClick, onOpenHistory)
        }
    }
}

@Composable
private fun DiscoveryContent(
    sections: List<FeedSection>,
    onStoryClick: (StoryItem) -> Unit,
    onOpenHistory: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(bottom = 96.dp) // clearance for the floating nav bar
    ) {
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

        sections.filter { it.stories.isNotEmpty() }.forEach { section ->
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
                    StoryCard(story = story, onClick = { onStoryClick(story) })
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
