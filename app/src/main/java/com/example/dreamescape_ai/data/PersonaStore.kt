package com.example.dreamescape_ai.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID

/** Per-process DataStore remembering the user's default persona. */
private val Context.personaDataStore by preferencesDataStore(name = "persona_prefs")

/** The chosen default persona: its id (null = play as "You") plus a cached name. */
data class PersonaSelection(
    val characterId: UUID?,
    val characterName: String?
) {
    /** True when a persona is chosen (vs. the "You" default). */
    val hasPersona: Boolean get() = characterId != null
}

/**
 * Persists the user's default persona locally.
 *
 * The backend has no "default persona" concept — the persona is chosen per chat
 * (`Chat.userCharacterId`). This store holds the Profile tab's selection so new
 * chats can be created with it without asking every time; the name is cached
 * alongside the id so `{{user}}` rendering and the Profile row don't need a
 * character lookup before the first frame.
 */
object PersonaStore {

    private val idKey = stringPreferencesKey("persona_character_id")
    private val nameKey = stringPreferencesKey("persona_character_name")

    /** Emits the persisted selection, or "no persona" when unset. */
    fun personaFlow(context: Context): Flow<PersonaSelection> =
        context.personaDataStore.data.map { prefs ->
            PersonaSelection(
                characterId = prefs[idKey]?.let { runCatching { UUID.fromString(it) }.getOrNull() },
                characterName = prefs[nameKey]
            )
        }

    /** Persists [selection] as the default persona (id null clears it). */
    suspend fun setPersona(context: Context, selection: PersonaSelection) {
        context.personaDataStore.edit { prefs ->
            if (selection.characterId == null) {
                prefs.remove(idKey)
                prefs.remove(nameKey)
            } else {
                prefs[idKey] = selection.characterId.toString()
                if (selection.characterName != null) prefs[nameKey] = selection.characterName
                else prefs.remove(nameKey)
            }
        }
    }
}
