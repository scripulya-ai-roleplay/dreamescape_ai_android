package com.example.dreamescape_ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.dreamescape_ai.DEFAULT_USER_PLACEHOLDER
import com.example.dreamescape_ai.PersonaCard
import com.example.dreamescape_ai.ProfileUiState
import com.example.dreamescape_ai.data.PersonaSelection
import com.example.dreamescape_ai.model.UserProfile
import com.example.dreamescape_ai.ui.components.CreditCard
import com.example.dreamescape_ai.ui.components.ProfileStat
import com.example.dreamescape_ai.ui.components.scripPanel
import com.example.dreamescape_ai.ui.theme.ArcanePurple
import com.example.dreamescape_ai.ui.theme.ManaBlue
import com.example.dreamescape_ai.ui.theme.ScripulyaText
import java.util.UUID

/**
 * Screen 3 — Profile / Dashboard. Scrollable user header (avatar + edit badge,
 * stats), a "playing as" persona selector backed by [ProfileUiState], and the
 * two balance cards.
 */
@Composable
fun ProfileScreen(
    profile: UserProfile,
    uiState: ProfileUiState,
    onOpenPersonaPicker: () -> Unit,
    onSelectPersona: (UUID?, String?) -> Unit,
    onDismissPersonaPicker: () -> Unit,
    onOpenHistory: () -> Unit,
    onSettings: () -> Unit,
    onEditProfile: () -> Unit,
    modifier: Modifier = Modifier
) {
    // The picker renders as a full-screen overlay inside the tab (same pattern
    // as the scene preview's persona picker), so no navigation is involved.
    var showPersonaPicker by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 96.dp) // clearance for the floating nav bar
        ) {
            ProfileHeader(profile, onSettings = onSettings, onEditProfile = onEditProfile)
            Spacer(Modifier.height(16.dp))
            PersonaSelector(
                selection = uiState.selectedPersona,
                onChangeCharacter = {
                    onOpenPersonaPicker()
                    showPersonaPicker = true
                }
            )
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

        if (showPersonaPicker) {
            PersonaPickerOverlay(
                uiState = uiState,
                onPick = { id, name ->
                    onSelectPersona(id, name)
                    showPersonaPicker = false
                },
                onDismiss = {
                    showPersonaPicker = false
                    onDismissPersonaPicker()
                }
            )
        }
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
private fun PersonaSelector(
    selection: PersonaSelection,
    onChangeCharacter: () -> Unit
) {
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
            Icon(Icons.Filled.Person, contentDescription = null, tint = ArcanePurple)
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "You're playing as",
                color = ScripulyaText.copy(alpha = 0.6f),
                style = MaterialTheme.typography.labelSmall
            )
            Text(
                text = selection.characterName ?: DEFAULT_USER_PLACEHOLDER,
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

/**
 * Full-screen persona picker: a "You" (no persona) card followed by every
 * character the user created or bookmarked. Picking one persists it as the
 * default persona for new chats; picking "You" clears it.
 */
@Composable
private fun PersonaPickerOverlay(
    uiState: ProfileUiState,
    onPick: (UUID?, String?) -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 4.dp, top = 4.dp, bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onDismiss) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = ScripulyaText
                )
            }
            Text(
                text = "Play as",
                color = ScripulyaText,
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleMedium
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when {
                !uiState.areCharactersLoaded -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = ScripulyaText)
                }
                uiState.errorMessage != null -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = uiState.errorMessage ?: "",
                        color = ScripulyaText.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(24.dp)
                    )
                }
                else -> LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item(key = "you") {
                        PersonaRow(
                            name = DEFAULT_USER_PLACEHOLDER,
                            imageUrl = null,
                            selected = uiState.selectedPersona.characterId == null,
                            onClick = { onPick(null, null) }
                        )
                    }
                    items(
                        uiState.characters,
                        key = { it.character.id ?: it.character.name }
                    ) { card ->
                        PersonaRow(
                            name = card.character.name,
                            imageUrl = card.imageUrl,
                            selected = uiState.selectedPersona.characterId != null &&
                                uiState.selectedPersona.characterId == card.character.id,
                            onClick = {
                                onPick(card.character.id, card.character.name)
                            }
                        )
                    }
                    if (uiState.characters.isEmpty()) {
                        item(key = "empty") {
                            Text(
                                text = "You haven't created or bookmarked any characters yet.",
                                color = ScripulyaText.copy(alpha = 0.6f),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/** One selectable row in the persona picker; selection is outlined. */
@Composable
private fun PersonaRow(
    name: String,
    imageUrl: String?,
    selected: Boolean,
    onClick: () -> Unit
) {
    val outline = if (selected) ManaBlue else Color.Transparent
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(2.dp, outline, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (imageUrl != null) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        Text(
            text = name,
            color = ScripulyaText,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = "Selected",
                tint = ManaBlue
            )
        }
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
