package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dreamescape_ai.ui.theme.ArcanePurple
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Persistent top app bar: SCRIPULYA AI wordmark on the left, two pill-shaped
 * balance indicators (Mana / Arcane credits) and a notification bell on the right.
 * Transparent so the starry background shows through.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScripulyaTopBar(
    manaCredits: String,
    arcaneCredits: String,
    onNotifications: () -> Unit,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        modifier = modifier,
        title = { ScripulyaWordmark() },
        actions = {
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                BalancePill(icon = Icons.Filled.Diamond, tint = ManaBlue, value = manaCredits)
                BalancePill(icon = Icons.Filled.Diamond, tint = ArcanePurple, value = arcaneCredits)
                IconButton(onClick = onNotifications) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = "Notifications",
                        tint = ScripulyaText
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            navigationIconContentColor = ScripulyaText,
            titleContentColor = ScripulyaText,
            actionIconContentColor = ScripulyaText
        )
    )
}

@Composable
private fun ScripulyaWordmark() {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Icon(
            imageVector = Icons.Filled.Diamond,
            contentDescription = null,
            tint = ManaBlue,
            modifier = Modifier.size(20.dp)
        )
        Text(
            text = "SCRIPULYA",
            color = ScripulyaText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium,
            letterSpacing = 1.sp
        )
        Text(
            text = "AI",
            color = ManaBlue,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.titleMedium
        )
    }
}
