package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Public
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.NightPanel
import com.example.dreamescape_ai.ui.theme.ScripulyaPink
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Poster-style preview for an owned scene or character. Mirrors the discovery
 * [StoryCard] look, but swaps the stat line for a Private/Public badge pinned
 * to the top-right corner. A long press opens the per-card action menu
 * (View / Edit / Delete) — see [onLongClick].
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OwnedCard(
    title: String,
    subtitle: String,
    imageUrl: String?,
    isPublic: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onLongClick: (() -> Unit)? = null
) {
    Box(
        modifier = modifier
            .aspectRatio(0.70f)
            .clip(RoundedCornerShape(18.dp))
            .background(NightPanel)
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        if (imageUrl != null) {
            AsyncImage(
                model = imageUrl,
                contentDescription = title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = null,
                    tint = ScripulyaText.copy(alpha = 0.35f)
                )
            }
        }

        // Legibility scrim + text anchored to the bottom.
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
            Spacer(Modifier.weight(1f))
            Text(
                text = title,
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    color = ScripulyaText.copy(alpha = 0.78f),
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        PrivacyBadge(
            isPublic = isPublic,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(8.dp)
        )
    }
}

@Composable
private fun PrivacyBadge(isPublic: Boolean, modifier: Modifier = Modifier) {
    val label: String
    val tint: Color
    val icon: ImageVector
    if (isPublic) {
        label = "Public"; tint = ManaBlue; icon = Icons.Filled.Public
    } else {
        label = "Private"; tint = ScripulyaPink; icon = Icons.Filled.Lock
    }
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Color.Black.copy(alpha = 0.45f))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Text(
            text = label,
            color = tint,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold
        )
    }
}
