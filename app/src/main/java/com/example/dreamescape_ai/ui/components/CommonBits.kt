package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Comment
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.model.ContentTag
import com.example.dreamescape_ai.model.HistoryCategory
import com.example.dreamescape_ai.ui.theme.NightOutline
import com.example.dreamescape_ai.ui.theme.NightPanel
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/** A frosted, raised panel: rounded, translucent night surface with a thin outline. */
fun Modifier.scripPanel(
    radius: Dp = 20.dp,
    background: Color = NightPanel.copy(alpha = 0.92f)
): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(background)
    .border(1.dp, NightOutline, RoundedCornerShape(radius))

/** Rounded-square "glass" surface used by the bottom nav and large cards. */
fun Modifier.scripGlass(radius: Dp = 28.dp): Modifier = this
    .clip(RoundedCornerShape(radius))
    .background(NightPanel.copy(alpha = 0.96f))
    .border(1.dp, NightOutline.copy(alpha = 0.6f), RoundedCornerShape(radius))

// ---------------------------------------------------------------------------
//  Top app bar — pill-shaped balance indicator
// ---------------------------------------------------------------------------

/** A pill showing a gem icon + balance value, used in the top app bar. */
@Composable
fun BalancePill(
    icon: ImageVector,
    tint: Color,
    value: String,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(NightPanel.copy(alpha = 0.85f))
            .border(1.dp, NightOutline, CircleShape)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(16.dp))
        Text(
            text = value,
            color = ScripulyaText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
    }
}

// ---------------------------------------------------------------------------
//  Story card — top-left content tag
// ---------------------------------------------------------------------------

@Composable
fun TagChip(tag: ContentTag, modifier: Modifier = Modifier) {
    Text(
        text = tag.label,
        color = Color(0xFF0A0E22),
        fontWeight = FontWeight.Bold,
        style = MaterialTheme.typography.labelSmall,
        modifier = modifier
            .clip(CircleShape)
            .background(tag.color)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    )
}

// ---------------------------------------------------------------------------
//  Section header with optional trailing slot
// ---------------------------------------------------------------------------

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = ScripulyaText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        trailing?.invoke()
    }
}

// ---------------------------------------------------------------------------
//  History filter button (large, colored, rounded-square)
// ---------------------------------------------------------------------------

private val HistoryCategory.icon: ImageVector
    get() = when (this) {
        HistoryCategory.SAVED -> Icons.Filled.Bookmark
        HistoryCategory.LIKES -> Icons.Filled.Favorite
        HistoryCategory.COMMENTS -> Icons.AutoMirrored.Filled.Comment
        HistoryCategory.FOLLOWING -> Icons.Filled.Group
    }

@Composable
fun FilterButton(
    category: HistoryCategory,
    count: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val color = category.color
    Column(
        modifier = modifier
            .scripGlass(radius = 22.dp)
            .then(
                if (selected) Modifier.background(color.copy(alpha = 0.28f))
                else Modifier
            )
            .border(
                width = if (selected) 1.5.dp else 0.dp,
                color = if (selected) color else Color.Transparent,
                shape = RoundedCornerShape(22.dp)
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(
            category.icon,
            contentDescription = category.label,
            tint = if (selected) color else color.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp)
        )
        Text(
            text = category.label,
            color = ScripulyaText,
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelMedium
        )
        Text(
            text = count.toString(),
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}

// ---------------------------------------------------------------------------
//  Profile stat (Followers / Storylines / Characters)
// ---------------------------------------------------------------------------

@Composable
fun ProfileStat(
    value: String,
    label: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = value,
            color = ScripulyaText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge
        )
        Text(
            text = label,
            color = ScripulyaText.copy(alpha = 0.7f),
            style = MaterialTheme.typography.labelSmall
        )
    }
}
