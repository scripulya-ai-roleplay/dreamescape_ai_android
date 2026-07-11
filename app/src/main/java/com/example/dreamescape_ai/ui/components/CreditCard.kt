package com.example.dreamescape_ai.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Diamond
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.dreamescape_ai.model.CreditBalance
import com.example.dreamescape_ai.model.CreditType
import com.example.dreamescape_ai.ui.theme.ArcanePurple
import com.example.dreamescape_ai.ui.theme.ArcanePurpleDeep
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.ManaBlueDeep
import com.example.dreamescape_ai.ui.theme.ScripulyaText

private val CreditType.bright: Color
    get() = when (this) {
        CreditType.MANA -> ManaBlue
        CreditType.ELITE -> ArcanePurple
    }

private val CreditType.deep: Color
    get() = when (this) {
        CreditType.MANA -> ManaBlueDeep
        CreditType.ELITE -> ArcanePurpleDeep
    }

/**
 * Large vertical balance card with a glowing gem. Display-only — purchasing /
 * billing is intentionally deferred.
 */
@Composable
fun CreditCard(balance: CreditBalance, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .scripPanel(radius = 24.dp)
            .background(Brush.verticalGradient(listOf(balance.type.deep.copy(alpha = 0.45f), Color.Transparent)))
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Gem(balance.type.bright)

        Text(
            text = balance.type.title,
            color = ScripulyaText.copy(alpha = 0.8f),
            fontWeight = FontWeight.SemiBold,
            style = MaterialTheme.typography.labelLarge,
            letterSpacing = 1.sp
        )
        Text(
            text = balance.amount.toString(),
            color = ScripulyaText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium
        )
        Text(
            text = balance.subtitle,
            color = ScripulyaText.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall
        )
    }
}

@Composable
private fun Gem(tint: Color) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .background(Brush.radialGradient(listOf(tint.copy(alpha = 0.55f), Color.Transparent))),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.Diamond,
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(44.dp)
        )
    }
}
