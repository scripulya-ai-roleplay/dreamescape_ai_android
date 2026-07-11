package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.dreamescape_ai.model.CreditBalance
import com.example.dreamescape_ai.ui.components.CreditCard
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * "Credit Usage" destination. Shows the two balance cards (display-only) plus a
 * note that purchasing / billing is not yet available.
 */
@Composable
fun CreditUsageScreen(
    mana: CreditBalance,
    elite: CreditBalance,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp)
    ) {
        Text(
            text = "Credit Usage",
            color = ScripulyaText,
            fontWeight = FontWeight.Black,
            style = MaterialTheme.typography.headlineMedium,
            modifier = Modifier.padding(top = 4.dp, bottom = 16.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CreditCard(mana, modifier = Modifier.weight(1f))
            CreditCard(elite, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Spend breakdown and top-ups are coming soon. Billing is not enabled yet.",
            color = ScripulyaText.copy(alpha = 0.6f),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.fillMaxWidth().scripPanel(radius = 20.dp).padding(16.dp)
        )
    }
}
