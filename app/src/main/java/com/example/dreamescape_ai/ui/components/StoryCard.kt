package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dreamescape_ai.model.StoryItem
import com.example.dreamescape_ai.model.StoryMetric
import com.example.dreamescape_ai.model.StoryMetricType
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Poster-style story card: full-cover image, top-left content tags, and a
 * bottom gradient scrim holding the title, description, author and stat.
 */
@Composable
fun StoryCard(story: StoryItem, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .width(168.dp)
            .aspectRatio(0.70f)
            .clip(RoundedCornerShape(18.dp))
            .clickable(onClick = onClick)
    ) {
        AsyncImage(
            model = story.coverImageUrl,
            contentDescription = story.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )

        // Legibility scrim + content anchor.
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.45f to Color.Transparent,
                        1.0f to Color(0xCC05071A)
                    )
                )
                .padding(12.dp)
        ) {
            // Top-left indicator tags.
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                story.tags.take(3).forEach { TagChip(it) }
            }

            Spacer(Modifier.weight(1f)) // push the rest to the bottom

            Text(
                text = story.title,
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = story.description,
                color = ScripulyaText.copy(alpha = 0.78f),
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = story.authorHandle,
                    color = ScripulyaText.copy(alpha = 0.7f),
                    style = MaterialTheme.typography.labelSmall
                )
                story.metric?.let { MetricLine(it) }
            }
        }
    }
}

@Composable
private fun MetricLine(metric: StoryMetric) {
    val icon = when (metric.type) {
        StoryMetricType.MESSAGES -> Icons.AutoMirrored.Filled.Chat
        StoryMetricType.LIKES -> Icons.Filled.Favorite
        StoryMetricType.NEW -> Icons.Filled.LocalFireDepartment
    }
    val text = when (metric.type) {
        StoryMetricType.NEW -> "New"
        else -> "${metric.formatted()} ${metric.type.label}"
    }
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(3.dp)) {
        Icon(icon, contentDescription = null, tint = ScripulyaText.copy(alpha = 0.85f), modifier = Modifier.size(13.dp))
        Text(text = text, color = ScripulyaText.copy(alpha = 0.85f), style = MaterialTheme.typography.labelSmall)
    }
}
