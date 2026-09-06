package com.example.dreamescape_ai

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * The user's default persona name (from the Profile tab's picker), or null when
 * none is chosen. Screens not tied to a chat — scene/character previews — read
 * this to render `{{user}}`; chat screens use the chat's own persona instead.
 *
 * Provided once at the app root ([ScripulyaApp]) from [com.example.dreamescape_ai.data.PersonaStore];
 * [staticCompositionLocalOf] because it changes rarely and readers only re-render
 * on an actual persona switch.
 */
val LocalPersonaName = staticCompositionLocalOf<String?> { null }
