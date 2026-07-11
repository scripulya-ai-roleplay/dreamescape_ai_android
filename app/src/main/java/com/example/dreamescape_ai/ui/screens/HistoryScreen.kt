package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.model.HistoryCategory
import com.example.dreamescape_ai.model.HistoryItem
import com.example.dreamescape_ai.ui.components.FilterButton
import com.example.dreamescape_ai.ui.components.HistoryListItem
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Screen 2 — Activity / History. Colorful rounded-square filters at the top
 * (Saved/Likes/Comments/Following) drive a vertical list of history rows.
 * Tapping the active filter again clears it (shows everything).
 */
@Composable
fun HistoryScreen(
    items: List<HistoryItem>,
    onPlay: (HistoryItem) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    var selected by remember { mutableStateOf<HistoryCategory?>(null) }
    val counts = items.groupBy { it.category }.mapValues { it.value.size }
    val visible = items.filter { selected == null || it.category == selected }

    Column(modifier = modifier.fillMaxSize()) {
        // Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = ScripulyaText)
            }
            Text(
                text = "Activity",
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge
            )
        }

        // Filters
        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
        ) {
            items(HistoryCategory.values().toList()) { category ->
                FilterButton(
                    category = category,
                    count = counts[category] ?: 0,
                    selected = selected == category,
                    onClick = {
                        selected = if (selected == category) null else category
                    },
                    modifier = Modifier.width(84.dp)
                )
            }
        }

        // List
        if (visible.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Nothing here yet",
                    color = ScripulyaText.copy(alpha = 0.6f),
                    style = MaterialTheme.typography.bodyLarge
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(visible, key = { it.id }) { item ->
                    HistoryListItem(item = item, onPlay = { onPlay(item) })
                }
            }
        }
    }
}
