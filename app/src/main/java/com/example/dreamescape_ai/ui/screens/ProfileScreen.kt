package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dreamescape_ai.model.UserProfile
import com.example.dreamescape_ai.ui.components.CreditCard
import com.example.dreamescape_ai.ui.components.ProfileStat
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.ArcanePurple
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.ScripulyaText

/**
 * Screen 3 — Profile / Dashboard. Scrollable user header (avatar + edit badge,
 * stats), a "playing as" character selector, and the two balance cards.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile,
    onChangeCharacter: () -> Unit,
    onOpenHistory: () -> Unit,
    onSettings: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
            .padding(bottom = 96.dp) // clearance for the floating nav bar
    ) {
        ProfileHeader(profile, onSettings = onSettings, onEditProfile = onEditProfile)
        Spacer(Modifier.height(16.dp))
        CharacterSelector(profile, onChangeCharacter)
        Spacer(Modifier.height(16.dp))
        Text(
            text = "Credit Balances",
            color = ScripulyaText,
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleLarge,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            CreditCard(profile.manaCredits, modifier = Modifier.weight(1f))
            CreditCard(profile.eliteCredits, modifier = Modifier.weight(1f))
        }
        Spacer(Modifier.height(16.dp))
        PillButton(text = "View your activity", onClick = onOpenHistory)
    }
}

@Composable
private fun ProfileHeader(profile: UserProfile, onSettings: () -> Unit, onEditProfile: () -> Unit) {
    Column(modifier = Modifier.fillMaxWidth().scripPanel(radius = 24.dp).padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            IconButton(onClick = onSettings) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = ScripulyaText)
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            AvatarWithBadge(profile.avatarUrl, onEditProfile)
            Text(
                text = profile.username,
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(top = 12.dp)
            )
            Text(
                text = profile.handle,
                color = ManaBlue,
                style = MaterialTheme.typography.labelMedium
            )
            Text(
                text = profile.bio,
                color = ScripulyaText.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, start = 8.dp, end = 8.dp)
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                ProfileStat(profile.followers.toString(), "Followers")
                ProfileStat(profile.storylines.toString(), "Storylines")
                ProfileStat(profile.characters.toString(), "Characters")
            }
        }
    }
}

@Composable
private fun AvatarWithBadge(avatarUrl: String?, onEdit: () -> Unit) {
    Box(contentAlignment = Alignment.BottomEnd) {
        Box(
            modifier = Modifier
                .size(104.dp)
                .clip(CircleShape)
                .background(Brush.linearGradient(listOf(ManaBlue, ArcanePurple)))
                .padding(3.dp)
        ) {
            AsyncImage(
                model = avatarUrl,
                contentDescription = "Avatar",
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(CircleShape)
                    .fillMaxWidth()
            )
        }
        Box(
            modifier = Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(ManaBlue)
                .clickable(onClick = onEdit),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit avatar", tint = Color(0xFF06101F), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun CharacterSelector(profile: UserProfile, onChangeCharacter: () -> Unit) {
    val character = profile.activeCharacter
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .scripPanel(radius = 24.dp)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(ArcanePurple.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            if (character?.portraitUrl != null) {
                AsyncImage(
                    model = character.portraitUrl,
                    contentDescription = character.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.clip(CircleShape)
                )
            } else {
                Icon(Icons.Filled.Person, contentDescription = null, tint = ArcanePurple)
            }
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "You're playing as",
                color = ScripulyaText.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = character?.name ?: "No character",
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        PillButton(text = "Change", onClick = onChangeCharacter, compact = true)
    }
}

@Composable
private fun PillButton(text: String, onClick: () -> Unit, compact: Boolean = false) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(ManaBlue, ArcanePurple)))
            .clickable(onClick = onClick)
            .padding(horizontal = if (compact) 16.dp else 24.dp, vertical = if (compact) 8.dp else 12.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = text,
            color = Color(0xFF06101F),
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.labelLarge
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = Color(0xFF06101F),
            modifier = Modifier.size(18.dp)
        )
    }
}
